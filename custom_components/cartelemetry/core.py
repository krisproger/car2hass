"""Pure business logic for diplus2hass — no Home Assistant imports.

Everything in this module is stdlib-only so it can be unit-tested without
installing Home Assistant. `__init__.py` delegates to these functions.
"""

import asyncio
from datetime import datetime, timedelta
import uuid

try:  # Package import (Home Assistant runtime)
    from .const import GEOFENCE_KEY_PREFIX, GEOFENCE_NAME_SUFFIX
    from .const import (
        CONF_HISTORY_REPLAY,
        DEFAULT_HISTORY_REPLAY,
        CONF_ADAPTIVE_BACKLOG,
        DEFAULT_ADAPTIVE_BACKLOG,
        CONF_BACKLOG_THRESHOLD,
        DEFAULT_BACKLOG_THRESHOLD,
        CONF_BACKLOG_REPLAY,
        DEFAULT_BACKLOG_REPLAY,
    )
except ImportError:  # Direct module import (unit tests without Home Assistant)
    from const import GEOFENCE_KEY_PREFIX, GEOFENCE_NAME_SUFFIX
    from const import (
        CONF_HISTORY_REPLAY,
        DEFAULT_HISTORY_REPLAY,
        CONF_ADAPTIVE_BACKLOG,
        DEFAULT_ADAPTIVE_BACKLOG,
        CONF_BACKLOG_THRESHOLD,
        DEFAULT_BACKLOG_THRESHOLD,
        CONF_BACKLOG_REPLAY,
        DEFAULT_BACKLOG_REPLAY,
    )

COMMAND_TIMEOUT = timedelta(minutes=1)
DEFAULT_MAX_QUEUE = 50

# Yield to the HA event loop every this many history replays, sleeping this long,
# so a large offline batch writes its FULL history without blocking the loop (no
# dropped points and no track jitter). A 500-point backlog thus spreads over ~0.6 s.
# Legacy fallback used only when no ReplayThrottle is wired into the store.
HISTORY_REPLAY_CHUNK = 25
HISTORY_REPLAY_YIELD_SEC = 0.03

# Aggregate history-replay write rate per car (state writes/second). A shared
# token bucket bounds the total load one car puts on the HA event loop/recorder,
# regardless of how many entity listeners are awake. The initial bucket is full,
# so the first ~rate live writes are immediate; only a backlog is paced.
REPLAY_MAX_WRITES_PER_SEC = 500

# Coalesce rapid telemetry POSTs (retries/backlog) into one entity fan-out per
# this window. store["data"] already holds the latest values, so intermediate
# batches do not need their own fan-out.
DISPATCH_DEBOUNCE_SEC = 0.25


class ReplayThrottle:
    """Cooperative token bucket shared by all entities of one car.

    ``acquire()`` must be awaited before every history-replay state write. The
    bucket refills at ``max_per_sec`` and is serialized by a lock, so the
    aggregate write rate of one car cannot exceed the configured rate even when
    hundreds of entity listeners are replayed concurrently.
    """

    def __init__(self, max_per_sec: int = REPLAY_MAX_WRITES_PER_SEC):
        self._rate = max(1, int(max_per_sec))
        self._allowance = float(self._rate)
        self._last = None
        self._lock = asyncio.Lock()

    async def acquire(self) -> None:
        async with self._lock:
            loop = asyncio.get_running_loop()
            now = loop.time()
            if self._last is None:
                self._last = now
            self._allowance = min(
                float(self._rate), self._allowance + (now - self._last) * self._rate
            )
            self._last = now
            if self._allowance < 1.0:
                await asyncio.sleep((1.0 - self._allowance) / self._rate)
                now = loop.time()
                self._allowance = min(
                    float(self._rate),
                    self._allowance + (now - self._last) * self._rate,
                )
                self._last = now
            self._allowance -= 1.0

# A sensor stays "available" only while telemetry keeps arriving; after this
# many seconds without a batch it is reported unavailable (honest liveness).
SIGNAL_STALE_SECONDS = 180

# Bounds on a single snapshot's signal map (defensive against oversized POSTs).
MAX_SIGNAL_KEYS_PER_SNAPSHOT = 1000
MAX_SIGNAL_KEY_LEN = 64
MAX_SIGNAL_VALUE_LEN = 1024

DEVICE_CLASS_CAR = "car"
DEVICE_CLASS_PHONE = "phone"
PHONE_SIGNAL_PREFIX = "phone_"

_DEVICE_CLASSES = (DEVICE_CLASS_CAR, DEVICE_CLASS_PHONE)


class BatchValidationError(ValueError):
    """Raised when a telemetry batch contains malformed snapshots."""


class QueueFullError(ValueError):
    """Raised when the command queue is at capacity."""


def validate_batch(batch: list) -> list:
    """Validate snapshot shapes and return the batch sorted chronologically.

    Raises BatchValidationError with a user-facing message on the first
    malformed snapshot.
    """
    valid = []
    for snapshot in batch:
        if not isinstance(snapshot, dict):
            raise BatchValidationError("batch item must be an object")
        s_map = snapshot.get("s", {})
        g_map = snapshot.get("g", {})
        if not isinstance(s_map, dict) or not isinstance(g_map, dict):
            raise BatchValidationError("snapshot s and g must be objects")
        # Bound per-snapshot size so a single POST cannot exhaust memory/CPU
        # before aggregation (keys and string values are otherwise unbounded).
        if len(s_map) > MAX_SIGNAL_KEYS_PER_SNAPSHOT:
            raise BatchValidationError("snapshot has too many signals")
        for key, value in s_map.items():
            if not isinstance(key, str) or len(key) > MAX_SIGNAL_KEY_LEN:
                raise BatchValidationError("snapshot signal key invalid")
            if isinstance(value, str) and len(value) > MAX_SIGNAL_VALUE_LEN:
                raise BatchValidationError("snapshot signal value too long")
        # t (epoch seconds) must sort numerically; tolerate a malformed string
        # instead of letting the sorted() below raise a raw TypeError.
        t = snapshot.get("t", 0)
        if isinstance(t, str):
            try:
                snapshot = {**snapshot, "t": float(t)}
            except ValueError:
                raise BatchValidationError("snapshot t must be numeric")
        elif not isinstance(t, (int, float)) or isinstance(t, bool):
            raise BatchValidationError("snapshot t must be numeric")
        valid.append(snapshot)
    return sorted(valid, key=lambda s: s.get("t", 0))


def _str_or_none(value):
    """Return a stripped string for a payload field, or None when absent."""
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def display_name(name: str, unit: str | None) -> str:
    """Entity name without a trailing unit parenthetical.

    The registry names often carry the unit (e.g. "Speed (km/h)") while HA shows
    the unit separately, producing "Speed (km/h) km/h". Strip the parenthetical
    only when it equals the sensor's unit, so non-unit parentheses are kept.
    """
    if name and unit and name.endswith(f"({unit})"):
        return name[: -(len(unit) + 2)].strip()
    return name


def parse_device(dev) -> dict:
    """Normalize the top-level ``dev`` descriptor carried by a telemetry POST.

    The Android app sends ``dev: {class, id, name}`` for a phone and omits it
    for a car. Anything absent or malformed is treated as the car device so
    pre-phone payloads keep their exact meaning.
    """
    device = {
        "class": DEVICE_CLASS_CAR,
        "id": None,
        "name": None,
        "manufacturer": None,
        "model": None,
    }
    if not isinstance(dev, dict):
        return device
    cls = dev.get("class")
    if isinstance(cls, str) and cls.strip().lower() in _DEVICE_CLASSES:
        device["class"] = cls.strip().lower()
    for field in ("id", "name", "manufacturer", "model"):
        device[field] = _str_or_none(dev.get(field))
    return device


def is_phone_signal(key) -> bool:
    """Return True for a phone-device signal key (``phone_`` prefix)."""
    return isinstance(key, str) and key.startswith(PHONE_SIGNAL_PREFIX)


def route_device_signals(sorted_batch: list, latest_signals: dict, device_class: str) -> dict:
    """Select the per-device signal maps for one payload.

    This is the real selection path used by the ingest endpoint: the returned
    ``signals`` / ``signal_index`` maps are what the car entities read, and
    ``phone_signals`` / ``phone_signal_index`` are what the phone entities read.

    ``phone_*`` keys belong to the phone device only when the payload is in
    phone mode; in car mode they are ignored entirely (neither car nor phone).
    Non-``phone_*`` keys always belong to the car.
    """
    phone_mode = device_class == DEVICE_CLASS_PHONE
    car_signals: dict = {}
    phone_signals: dict = {}
    for key, value in (latest_signals or {}).items():
        if is_phone_signal(key):
            if phone_mode:
                phone_signals[key] = value
        else:
            car_signals[key] = value

    car_index: dict = {}
    phone_index: dict = {}
    for key, samples in build_signal_index(sorted_batch).items():
        if is_phone_signal(key):
            if phone_mode:
                phone_index[key] = samples
        else:
            car_index[key] = samples

    return {
        "signals": car_signals,
        "signal_index": car_index,
        "phone_signals": phone_signals,
        "phone_signal_index": phone_index,
    }


def extract_delta(snapshot: dict) -> tuple[bool, dict]:
    """Return (is_delta, channels) for a snapshot's optional delta fields.

    ``channels`` maps a channel name to the signal keys it changed, or ``{}``
    for a plain full snapshot.
    """
    ch = snapshot.get("ch")
    channels = {}
    if isinstance(ch, dict):
        for name, keys in ch.items():
            channels[str(name)] = list(keys) if isinstance(keys, (list, tuple)) else []
    return (bool(snapshot.get("delta")), channels)


def merge_signals(previous: dict, delta: dict) -> dict:
    """Overlay a delta signal map on the previous full state (returns a new dict).

    A key with a ``None`` value removes the signal from the state.
    """
    out = dict(previous or {})
    for key, value in (delta or {}).items():
        if value is None:
            out.pop(key, None)
        else:
            out[key] = value
    return out


def update_channel_last_seen(prev: dict, ch: dict, now_iso: str) -> dict:
    """Return a copy of prev with only the channels present in ``ch`` refreshed."""
    out = dict(prev or {})
    for channel in (ch or {}):
        out[str(channel)] = now_iso
    return out


def aggregate_batch(sorted_batch: list) -> dict:
    """Aggregate a chronologically sorted batch.

    Returns the latest value per signal, the last valid GPS fix, and the
    maximum snapshot timestamp.
    """
    latest_signals: dict = {}
    disabled_signals: set[str] = set()
    last_lat = None
    last_lon = None
    last_accuracy = 0
    last_timestamp = 0
    last_fix_time = 0
    for snapshot in sorted_batch:
        timestamp = snapshot.get("t", 0)
        gps = snapshot.get("g", {})
        signals = snapshot.get("s", {})
        d = snapshot.get("d")
        if isinstance(d, list):
            disabled_signals.update(str(x) for x in d if isinstance(x, str))

        try:
            lat = float(gps.get("lat")) if gps.get("lat") is not None else None
            lon = float(gps.get("lon")) if gps.get("lon") is not None else None
        except (ValueError, TypeError):
            lat = None
            lon = None

        if lat is not None and lon is not None:
            last_lat = lat
            last_lon = lon
            try:
                last_accuracy = float(gps.get("a", 0))
            except (ValueError, TypeError):
                last_accuracy = 0
            # The app reports the GPS fix time as g.t; fall back to the snapshot
            # collection time for payloads from older app versions.
            try:
                fix_time = gps.get("t", timestamp)
                last_fix_time = float(fix_time) if fix_time is not None else float(timestamp)
            except (ValueError, TypeError):
                last_fix_time = float(timestamp)

        if timestamp > last_timestamp:
            last_timestamp = timestamp

        latest_signals.update(signals)

    return {
        "latest_signals": latest_signals,
        "latitude": last_lat,
        "longitude": last_lon,
        "accuracy": last_accuracy,
        "timestamp": last_timestamp,
        "fix_timestamp": last_fix_time,
        "seen_signals": set(latest_signals.keys()),
        "disabled_signals": disabled_signals,
    }


def _parse_iso(value):
    """Parse an ISO-8601 timestamp, tolerating a trailing 'Z'."""
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except (ValueError, AttributeError):
        return None


def is_command_expired(cmd: dict, now: datetime) -> bool:
    """Return True if a command is stuck unprocessed for too long."""
    delivered_at = cmd.get("delivered_at")
    if delivered_at:
        delivered = _parse_iso(delivered_at)
        if delivered and (now - delivered) > COMMAND_TIMEOUT:
            return True

    created = cmd.get("created")
    if created:
        created_dt = _parse_iso(created)
        if created_dt and (now - created_dt) > COMMAND_TIMEOUT:
            return True

    return False


def enqueue_command(commands: list, command: str, params, now: datetime, max_queue: int = DEFAULT_MAX_QUEUE):
    """Enqueue a command into `commands` (list is mutated in place).

    Returns (entry, created_new). Deduplicates identical undelivered
    commands and prunes expired entries before the capacity check.
    Raises QueueFullError when the queue is at capacity.
    """
    commands[:] = [c for c in commands if not is_command_expired(c, now)]

    for cmd in commands:
        if cmd["command"] == command and cmd.get("params") == params and not cmd.get("delivered"):
            return cmd, False

    if len(commands) >= max_queue:
        raise QueueFullError("Command queue is full")

    entry = {
        "id": str(uuid.uuid4()),
        "command": command,
        "params": params,
        "created": now.isoformat(),
        "delivered": False,
        "status": "pending",
        "message": "",
    }
    commands.append(entry)
    return entry, True


def availability_signature(seen, disabled):
    """Immutable signature of the availability set for change detection.

    The registry-backed availability sync only needs to run when the set of
    seen/disabled signals changes; comparing signatures lets the caller skip
    the O(|seen|) registry scan on every telemetry message.
    """
    return (frozenset(seen), frozenset(disabled))


def select_replay_mode(options: dict, batch_points: int) -> str:
    """Return how to replay a batch's history: "full" or "latest".

    - history_replay off -> "latest"
    - adaptive_backlog on and the batch exceeds the threshold -> the configured
      backlog mode ("throttled" keeps full history at a bounded rate, "latest"
      keeps only the latest value per signal)
    - otherwise "full" (replay every intermediate value)
    """
    options = options or {}
    if not options.get(CONF_HISTORY_REPLAY, DEFAULT_HISTORY_REPLAY):
        return "latest"
    if (
        options.get(CONF_ADAPTIVE_BACKLOG, DEFAULT_ADAPTIVE_BACKLOG)
        and batch_points
        > int(options.get(CONF_BACKLOG_THRESHOLD, DEFAULT_BACKLOG_THRESHOLD))
    ):
        if options.get(CONF_BACKLOG_REPLAY, DEFAULT_BACKLOG_REPLAY) == "latest":
            return "latest"
    return "full"


def check_rate_limit(buckets: dict, key: str, now: float, window: float, max_requests: int) -> bool:
    """Sliding-window rate limiter.

    `buckets` maps a key (e.g. entry_id) to a list of request timestamps.
    Returns True and records the request when under the limit; returns
    False when the limit has been reached within the window.
    """
    bucket = buckets.setdefault(key, [])
    cutoff = now - window
    bucket[:] = [t for t in bucket if t > cutoff]
    if len(bucket) >= max_requests:
        return False
    bucket.append(now)
    return True


def drop_rate_limit(buckets: dict, key: str) -> None:
    """Forget the rate-limit history of a car (e.g. when its entry unloads)."""
    buckets.pop(key, None)


# The ``timestamp`` argument of ``hass.states.async_set`` was introduced in
# Home Assistant 2024.6. Older versions cannot backfill history points.
ASYNC_SET_TIMESTAMP_MIN_VERSION = (2024, 6)


def supports_async_set_timestamp(major: int, minor: int) -> bool:
    """Return True when hass.states.async_set accepts a timestamp.

    Determined purely from the Home Assistant (major, minor) version so the
    caller can fall back to the plain async_write_ha_state behavior on older
    releases.
    """
    return (major, minor) >= ASYNC_SET_TIMESTAMP_MIN_VERSION


def build_signal_index(sorted_batch: list) -> dict:
    """Map each signal key to its chronological list of (timestamp, value).

    Lets entities replay only the values relevant to them instead of
    scanning the entire batch. Each entry carries the snapshot timestamp so
    entities can write the state into HA history at the original collection
    time instead of collapsing the batch into the current time.
    """
    index: dict = {}
    for snapshot in sorted_batch:
        timestamp = snapshot.get("t", 0)
        for key, value in snapshot.get("s", {}).items():
            if value is None:
                continue
            index.setdefault(key, []).append((timestamp, value))
    return index


def build_gps_track(sorted_batch: list) -> list:
    """Chronological list of (timestamp, lat, lon, accuracy) for valid GPS.

    Each point is attributed to the GPS fix time (``g.t``) when the app
    provided one, falling back to the snapshot collection time so older
    payloads keep working. Points whose fix time is not strictly newer than
    the previous one are skipped — a re-sent/stale snapshot must not make the
    tracker jump back to an earlier position.
    """
    track = []
    last_t = None
    for snapshot in sorted_batch:
        gps = snapshot.get("g", {})
        try:
            lat = gps.get("lat")
            lon = gps.get("lon")
            if lat is None or lon is None:
                continue
            fix_time = gps.get("t")
            snap_time = snapshot.get("t", 0)
            try:
                t = float(fix_time) if fix_time is not None else float(snap_time)
            except (ValueError, TypeError):
                t = float(snap_time)
            if last_t is not None and t <= last_t:
                continue
            last_t = t
            track.append(
                (
                    t,
                    float(lat),
                    float(lon),
                    float(gps.get("a", 0) or 0),
                )
            )
        except (ValueError, TypeError):
            continue
    return track


def find_geofence_keys(signals: dict) -> list:
    """Return sorted dynamic geofence binary-sensor keys from a signals map.

    The Android app reports virtual geofence zone states as ``geo_<zoneId>``
    keys ("inside"/"outside") plus optional ``geo_<zoneId>_name`` companion
    keys carrying the zone name. Companion keys are excluded here.
    """
    return sorted(
        key
        for key in signals
        if isinstance(key, str)
        and key.startswith(GEOFENCE_KEY_PREFIX)
        and not key.endswith(GEOFENCE_NAME_SUFFIX)
    )


def geofence_zone_name(signals: dict, key: str) -> str:
    """Resolve the friendly zone name for a ``geo_<zoneId>`` key.

    Falls back to the raw zone id when the app did not send a
    ``geo_<zoneId>_name`` companion value.
    """
    name = signals.get(key + GEOFENCE_NAME_SUFFIX)
    if isinstance(name, str) and name.strip():
        return name.strip()
    return key[len(GEOFENCE_KEY_PREFIX):]
