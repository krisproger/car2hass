"""Pure business logic for diplus2hass — no Home Assistant imports.

Everything in this module is stdlib-only so it can be unit-tested without
installing Home Assistant. `__init__.py` delegates to these functions.
"""

from datetime import datetime, timedelta
import uuid

try:  # Package import (Home Assistant runtime)
    from .const import GEOFENCE_KEY_PREFIX, GEOFENCE_NAME_SUFFIX
except ImportError:  # Direct module import (unit tests without Home Assistant)
    from const import GEOFENCE_KEY_PREFIX, GEOFENCE_NAME_SUFFIX

COMMAND_TIMEOUT = timedelta(minutes=1)
DEFAULT_MAX_QUEUE = 50


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
        if not isinstance(snapshot.get("s", {}), dict) or not isinstance(snapshot.get("g", {}), dict):
            raise BatchValidationError("snapshot s and g must be objects")
        valid.append(snapshot)
    return sorted(valid, key=lambda s: s.get("t", 0))


def aggregate_batch(sorted_batch: list) -> dict:
    """Aggregate a chronologically sorted batch.

    Returns the latest value per signal, the last valid GPS fix, and the
    maximum snapshot timestamp.
    """
    latest_signals: dict = {}
    last_lat = None
    last_lon = None
    last_accuracy = 0
    last_timestamp = 0
    for snapshot in sorted_batch:
        timestamp = snapshot.get("t", 0)
        gps = snapshot.get("g", {})
        signals = snapshot.get("s", {})

        try:
            lat = float(gps.get("lat")) if gps.get("lat") is not None else None
            lon = float(gps.get("lon")) if gps.get("lon") is not None else None
        except (ValueError, TypeError):
            lat = None
            lon = None

        if lat is not None and lon is not None:
            last_lat = lat
            last_lon = lon
            last_accuracy = gps.get("a", 0)

        if timestamp > last_timestamp:
            last_timestamp = timestamp

        latest_signals.update(signals)

    return {
        "latest_signals": latest_signals,
        "latitude": last_lat,
        "longitude": last_lon,
        "accuracy": last_accuracy,
        "timestamp": last_timestamp,
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


def build_signal_index(sorted_batch: list) -> dict:
    """Map each signal key to its chronological list of non-None values.

    Lets entities replay only the values relevant to them instead of
    scanning the entire batch. Intermediate values are preserved, so HA
    history granularity is unchanged.
    """
    index: dict = {}
    for snapshot in sorted_batch:
        for key, value in snapshot.get("s", {}).items():
            if value is None:
                continue
            index.setdefault(key, []).append(value)
    return index


def build_gps_track(sorted_batch: list) -> list:
    """Chronological list of (lat, lon, accuracy) for snapshots with valid GPS."""
    track = []
    for snapshot in sorted_batch:
        gps = snapshot.get("g", {})
        try:
            lat = gps.get("lat")
            lon = gps.get("lon")
            if lat is None or lon is None:
                continue
            track.append((float(lat), float(lon), float(gps.get("a", 0) or 0)))
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
