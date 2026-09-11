"""CARTelemetry integration for Home Assistant."""

import logging
from datetime import timedelta
from pathlib import Path
import shutil
import urllib.request
import voluptuous as vol

from homeassistant.components.http import HomeAssistantView
from homeassistant.components.persistent_notification import async_create
from homeassistant.const import Platform
from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.helpers import config_validation as cv
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.helpers.event import async_track_time_interval
from homeassistant.helpers import entity_registry
from homeassistant.helpers.entity_registry import RegistryEntryDisabler
from homeassistant.util import dt as dt_util

from . import core
from .const import API_VERSION, DOMAIN, INTEGRATION_VERSION
from .core import BatchValidationError, QueueFullError

PLATFORMS = [
    Platform.SENSOR,
    Platform.BINARY_SENSOR,
    Platform.DEVICE_TRACKER,
    Platform.SWITCH,
    Platform.NUMBER,
    Platform.SELECT,
    Platform.BUTTON,
    Platform.LOCK,
    Platform.CLIMATE,
    Platform.ALARM_CONTROL_PANEL,
]

SERVICE_DEVELOPER_INFO = "developer_info"
SERVICE_SEND_COMMAND = "send_command"
SIGNAL_VEHICLE_DATA_UPDATED = f"{DOMAIN}_updated"

MAX_COMMAND_QUEUE = 50

try:
    from homeassistant.const import MAJOR_VERSION, MINOR_VERSION

    _SUPPORTS_ASYNC_SET_TIMESTAMP = core.supports_async_set_timestamp(
        MAJOR_VERSION, MINOR_VERSION
    )
except ImportError:
    _SUPPORTS_ASYNC_SET_TIMESTAMP = False


def async_replay_state(entity, timestamp):
    """Write entity state at the snapshot's original collection time.

    Home Assistant 2024.6+ accepts a ``timestamp`` argument on
    ``states.async_set``, so replaying a telemetry batch stores each
    intermediate value in history at its real time instead of collapsing
    the batch into the current time. Older versions fall back to the plain
    ``async_write_ha_state`` (current time), preserving the old behavior.
    """
    if _SUPPORTS_ASYNC_SET_TIMESTAMP:
        calc = entity._async_calculate_state()
        entity.hass.states.async_set(
            entity.entity_id,
            calc.state,
            calc.attributes,
            force_update=entity.force_update,
            timestamp=timestamp,
        )
    else:
        entity.async_write_ha_state()


async def async_enqueue_command(hass: HomeAssistant, car_name: str, command: str, params: dict | None = None) -> str:
    """Enqueue a command for the Android app to execute.

    Returns the generated command_id.
    Raises ValueError if the integration is not loaded, the car is unknown,
    or the command queue is full.
    """
    if DOMAIN not in hass.data:
        raise ValueError("Integration not loaded")

    entry_id = None
    for eid, store in hass.data[DOMAIN].items():
        if eid.startswith("_"):
            continue
        if store.get("car_name") == car_name:
            entry_id = eid
            break

    if entry_id is None:
        raise ValueError(f"Unknown car_name: {car_name}")

    store = hass.data[DOMAIN][entry_id]
    commands = store.setdefault("commands", [])

    try:
        entry, created = core.enqueue_command(commands, command, params, dt_util.utcnow(), MAX_COMMAND_QUEUE)
    except QueueFullError as err:
        raise ValueError(str(err)) from err

    if not created:
        _LOGGER.debug("Deduplicating command %s for %s", command, car_name)
        return entry["id"]

    _LOGGER.info("Enqueued command %s for %s: %s (queue size: %s)", entry["id"], car_name, command, len(commands))
    return entry["id"]


_LOGGER = logging.getLogger(__name__)

# Track REST view registration across config entry reloads. HA has no public API
# to unregister a view, so we must avoid duplicate registration when the last
# entry is unloaded and a new one is added.
_VIEW_REGISTERED = False

# Safety limits for the incoming REST payload
MAX_BATCH_SNAPSHOTS = 1000

# Sliding-window rate limit for the telemetry POST endpoint (per car)
RATE_LIMIT_WINDOW_SECONDS = 60.0
RATE_LIMIT_MAX_REQUESTS = 100

_BATCH_SCHEMA = vol.Schema(
    {
        vol.Required("car_name"): cv.string,
        vol.Optional("vvn", default=""): cv.string,
        vol.Optional("firmware", default=""): cv.string,
        vol.Optional("app_version", default=""): cv.string,
        vol.Optional("batch", default=[]): vol.All(
            cv.ensure_list,
            vol.Length(max=MAX_BATCH_SNAPSHOTS),
        ),
    },
    extra=vol.ALLOW_EXTRA,
)

_COMMAND_SCHEMA = vol.Schema(
    {
        vol.Required("car_name"): cv.string,
        vol.Required("command"): cv.string,
        vol.Optional("params", default=None): vol.Any(dict, None),
    }
)

_ACK_SCHEMA = vol.Schema(
    {
        vol.Required("command_id"): cv.string,
        vol.Optional("status", default="ok"): cv.string,
        vol.Optional("message", default=""): cv.string,
    }
)


_HEARTBEAT_URL = "https://mytechnic.ru/cartelemetry/api/version/index.php"


async def _heartbeat_ping(hass: HomeAssistant, entry_id: str) -> None:
    """One anonymous contact with the site so unique HA installs are counted."""
    try:
        def _do() -> None:
            req = urllib.request.Request(_HEARTBEAT_URL)
            req.add_header("User-Agent", f"CARTelemetry-Integration/{INTEGRATION_VERSION}")
            req.add_header("X-Car2Hass-Id", f"int:{entry_id}")
            with urllib.request.urlopen(req, timeout=10) as resp:
                resp.read()

        await hass.async_add_executor_job(_do)
    except Exception:  # noqa: BLE001 - site may be unreachable; ignore silently
        pass


async def async_setup_entry(hass: HomeAssistant, entry):
    """Set up diplus2hass from a config entry."""
    hass.data.setdefault(DOMAIN, {})
    hass.data[DOMAIN][entry.entry_id] = {
        "data": {},
        "car_name": entry.data.get("car_name", "byd_car"),
        "vvn": "",
        "firmware": "",
        "last_seen": None,
        "commands": [],
    }

    # Register REST API endpoints once globally
    await _install_card(hass)
    global _VIEW_REGISTERED
    if not _VIEW_REGISTERED:
        hass.http.register_view(VehicleDataView)
        hass.http.register_view(VehicleCommandsView)
        hass.http.register_view(VehicleInfoView)
        # Deprecated: kept for pre-3.0 APKs; remove after transition period.
        hass.http.register_view(VehicleDataLegacyView)
        hass.http.register_view(VehicleCommandsLegacyView)
        _VIEW_REGISTERED = True

    # Register developer info service
    async def handle_developer_info(call):
        info_lines = []
        total_signals = 0
        for entry_id, store in hass.data.get(DOMAIN, {}).items():
            # Skip internal keys (e.g. "_view_registered", "_rate_limits") —
            # only per-vehicle stores carry a "data" dict.
            if entry_id.startswith("_"):
                continue
            signals = store.get("data", {}).get("signals", {})
            total_signals += len(signals)
            info_lines.append(
                f"<b>{store.get('car_name', 'unknown')}</b><br>"
                f"VVIN: {store.get('vvn', '—')} | FW: {store.get('firmware', '—')}<br>"
                f"Last seen: {store.get('last_seen', '—')} | Signals: {len(signals)}"
            )
        message = "<br><br>".join(info_lines) if info_lines else "No vehicles registered."
        async_create(
            hass,
            message,
            title="CARTelemetry Developer Info",
            notification_id=f"{DOMAIN}_developer_info",
        )

    if not hass.services.has_service(DOMAIN, SERVICE_DEVELOPER_INFO):
        hass.services.async_register(
            DOMAIN, SERVICE_DEVELOPER_INFO, handle_developer_info,
            schema=vol.Schema({})
        )

    # Register send_command service
    async def handle_send_command(call: ServiceCall):
        car_name = call.data.get("car_name")
        command = call.data.get("command")
        params = call.data.get("params")
        cmd_id = await async_enqueue_command(hass, car_name, command, params)
        return {"command_id": cmd_id}

    if not hass.services.has_service(DOMAIN, SERVICE_SEND_COMMAND):
        hass.services.async_register(
            DOMAIN, SERVICE_SEND_COMMAND, handle_send_command,
            schema=_COMMAND_SCHEMA
        )

    # Forward setup to platforms
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)

    # Anonymous daily heartbeat so the site can count unique HA installs.
    entry_id = entry.entry_id

    async def _daily_ping(_now=None) -> None:
        await _heartbeat_ping(hass, entry_id)

    hass.async_create_task(_daily_ping())
    entry.async_on_unload(
        async_track_time_interval(hass, _daily_ping, timedelta(hours=24))
    )

    return True


async def async_unload_entry(hass: HomeAssistant, entry):
    """Unload a config entry."""
    await hass.config_entries.async_unload_platforms(entry, PLATFORMS)

    if DOMAIN in hass.data and entry.entry_id in hass.data[DOMAIN]:
        hass.data[DOMAIN].pop(entry.entry_id)

    # Only remove shared resources when no entries remain.
    # _VIEW_REGISTERED is intentionally kept; HA has no public API to unregister
    # a view, and setting it to False here would cause a duplicate registration
    # error if the user adds the integration again without restarting HA.
    if hass.data.get(DOMAIN) and all(k.startswith("_") for k in hass.data[DOMAIN]):
        hass.services.async_remove(DOMAIN, SERVICE_DEVELOPER_INFO)
        hass.services.async_remove(DOMAIN, SERVICE_SEND_COMMAND)
        hass.data.pop(DOMAIN, None)
    return True


class VehicleDataView(HomeAssistantView):
    """REST API endpoint to receive vehicle telemetry from the Android app."""

    url = "/api/cartelemetry"
    name = "api:cartelemetry"
    requires_auth = True

    async def post(self, request):
        """Handle POST request with vehicle data batch."""
        hass = request.app["hass"]
        try:
            return await self._handle_post(hass, request)
        except Exception as err:  # noqa: BLE001
            # Catch-all so any unexpected failure returns a JSON error and its
            # traceback lands in the HA log instead of aiohttp's opaque 500 page.
            _LOGGER.exception("api:cartelemetry unhandled error")
            return self.json(
                {"status": "error", "message": f"{type(err).__name__}: {err}"},
                status=500,
            )

    async def _handle_post(self, hass, request):
        try:
            raw = await request.json()
            data = _BATCH_SCHEMA(raw)
        except vol.Invalid as err:
            return self.json(
                {"status": "error", "message": f"invalid payload: {err}"},
                status=400,
            )
        except ValueError:
            return self.json(
                {"status": "error", "message": "invalid json"},
                status=400,
            )

        car_name = data["car_name"]
        vvn = data["vvn"]
        firmware = data["firmware"]
        app_version = data["app_version"]
        batch = data["batch"]
        _LOGGER.debug("api:cartelemetry POST car_name=%s batch=%d", car_name, len(batch))

        if DOMAIN not in hass.data:
            _LOGGER.warning("api:cartelemetry: integration not loaded (DOMAIN missing)")
            return self.json(
                {"status": "error", "message": "integration not loaded"},
                status=500,
            )

        entry_id = self._resolve_entry_id(hass, car_name)
        _LOGGER.debug("api:cartelemetry entry_id for %s -> %s", car_name, entry_id)
        if entry_id is None:
            return self.json(
                {"status": "error", "message": f"unknown car_name: {car_name}"},
                status=404,
            )

        try:
            return await self._process_batch(hass, entry_id, car_name, vvn,
                                             firmware, app_version, batch,
                                             data.get("ts"))
        except Exception as err:  # noqa: BLE001
            _LOGGER.exception("api:cartelemetry failed (car_name=%s)", car_name)
            return self.json(
                {"status": "error", "message": f"{type(err).__name__}: {err}"},
                status=500,
            )

    async def _process_batch(self, hass, entry_id, car_name, vvn,
                             firmware, app_version, batch, ts):
        buckets = hass.data[DOMAIN].setdefault("_rate_limits", {})
        if not core.check_rate_limit(
            buckets, entry_id, dt_util.utcnow().timestamp(),
            RATE_LIMIT_WINDOW_SECONDS, RATE_LIMIT_MAX_REQUESTS,
        ):
            return self.json(
                {"status": "error", "message": "rate limit exceeded"},
                status=429,
            )

        store = hass.data[DOMAIN][entry_id]
        store["car_name"] = car_name
        store["vvn"] = vvn
        store["firmware"] = firmware
        store["app_version"] = app_version

        # Validate snapshot shapes, then aggregate all snapshots in the batch,
        # keeping the latest value per signal and the most recent valid GPS
        # position. Sorting is chronological so later snapshots override
        # earlier ones regardless of batch order.
        try:
            sorted_batch = core.validate_batch(batch)
        except BatchValidationError as err:
            return self.json(
                {"status": "error", "message": str(err)},
                status=400,
            )

        agg = core.aggregate_batch(sorted_batch)
        latest_signals = agg["latest_signals"]

        # Expose app_version as a regular sensor in addition to device info.
        if app_version:
            latest_signals["app_version"] = app_version

        # Variant 1 sensor availability: remember every signal ever received so
        # its sensor can be enabled once and never lost; unseen ones stay under
        # the HA "disabled" spoiler (entity_registry_enabled_default=False).
        old_seen = set(store["data"].get("seen_signals", set())) if store.get("data") else set()
        seen = old_seen | set(latest_signals.keys())
        disabled = set(agg.get("disabled_signals") or set())

        store["data"] = {
            "timestamp": agg["timestamp"],
            "ts": ts if ts is not None else agg["timestamp"],
            "latitude": agg["latitude"],
            "longitude": agg["longitude"],
            "accuracy": agg["accuracy"],
            "fix_timestamp": agg["fix_timestamp"],
            "signals": latest_signals,
            "seen_signals": seen,
            "disabled_signals": disabled,
            # Full chronological batch so platforms can replay intermediate
            # values / GPS points instead of only the final aggregated state.
            "batch": sorted_batch,
            # Pre-grouped per-signal values / GPS points: entities do O(1)
            # lookups instead of scanning the whole batch.
            "signal_index": core.build_signal_index(sorted_batch),
            "gps_track": core.build_gps_track(sorted_batch),
        }
        store["last_seen"] = dt_util.utcnow().isoformat()

        async_dispatcher_send(hass, SIGNAL_VEHICLE_DATA_UPDATED)
        newly = set(latest_signals.keys()) - old_seen
        if newly:
            await _async_enable_sensors(hass, entry_id, newly)
        await _async_sync_entity_availability(hass, entry_id, seen, disabled)

        return self.json({
            "status": "ok",
            "car_name": car_name,
            "snapshots": len(batch),
            "signals": len(latest_signals),
        })

    def _resolve_entry_id(self, hass: HomeAssistant, car_name: str):
        """Find config entry id by car_name."""
        for entry_id, store in hass.data.get(DOMAIN, {}).items():
            if not isinstance(entry_id, str) or entry_id.startswith("_"):
                continue
            if isinstance(store, dict) and store.get("car_name") == car_name:
                return entry_id
        return None


async def _async_enable_sensors(hass: HomeAssistant, entry_id: str, signal_keys: set[str]) -> None:
    """Enable newly-seen sensor entities (Variant 1 availability).

    Sensors are created disabled-by-default (entity_registry_enabled_default=False),
    so unseen signals stay under the HA "disabled" spoiler. As soon as a signal is
    received the integration enables its entity; a user-disabled entity is kept off.
    """
    reg = entity_registry.async_get(hass)
    for key in signal_keys:
        unique = f"{entry_id}_{key}"
        entity_id = reg.async_get_entity_id(Platform.SENSOR, DOMAIN, unique)
        if entity_id is None:
            entity_id = reg.async_get_entity_id(Platform.BINARY_SENSOR, DOMAIN, unique)
        if entity_id is None:
            continue
        entry = reg.async_get(entity_id)
        if entry is None or entry.disabled_by != RegistryEntryDisabler.INTEGRATION:
            continue
        reg.async_enable(entity_id)


async def _async_sync_entity_availability(
    hass: HomeAssistant, entry_id: str, seen: set[str], disabled: set[str]
) -> None:
    """Deactivate entities for disabled signals and their dependent commands.

    Disabled signals (user unchecked them in the app) are deactivated in HA so
    they move under the "disabled" spoiler; dependent command entities are
    deactivated too. A user's own disable choice is always respected.
    """
    from .const import COMMAND_DEPENDS_ON

    reg = entity_registry.async_get(hass)

    def _set_disabled(unique: str, platforms: list) -> None:
        for pl in platforms:
            eid = reg.async_get_entity_id(pl, DOMAIN, unique)
            if eid is None:
                continue
            entry = reg.async_get(eid)
            if entry is None or entry.disabled_by == RegistryEntryDisabler.USER:
                continue
            reg.async_update_entity(eid, disabled_by=RegistryEntryDisabler.INTEGRATION)

    def _set_enabled(unique: str, platforms: list) -> None:
        for pl in platforms:
            eid = reg.async_get_entity_id(pl, DOMAIN, unique)
            if eid is None:
                continue
            entry = reg.async_get(eid)
            if entry is None or entry.disabled_by != RegistryEntryDisabler.INTEGRATION:
                continue
            reg.async_enable(eid)

    sensor_platforms = [Platform.SENSOR, Platform.BINARY_SENSOR]
    command_platforms = [Platform.SWITCH, Platform.BUTTON, Platform.NUMBER]

    # Sensors: deactivate disabled ones, re-enable enabled-and-seen ones.
    for key in seen:
        unique = f"{entry_id}_{key}"
        if key in disabled:
            _set_disabled(unique, sensor_platforms)
        else:
            _set_enabled(unique, sensor_platforms)

    # Commands that depend on a deactivated sensor follow it.
    def _command_targets(cmd_key: str) -> list[tuple[str, Platform]]:
        out = [(f"{entry_id}_{pl}_{cmd_key}", pl) for pl in command_platforms]
        if cmd_key == "doors_lock":
            out.append((f"{entry_id}_doors_lock", Platform.LOCK))
        return out

    for cmd_key, sensor_key in COMMAND_DEPENDS_ON.items():
        if sensor_key in disabled:
            for unique, pl in _command_targets(cmd_key):
                _set_disabled(unique, [pl])
        elif sensor_key in seen:
            for unique, pl in _command_targets(cmd_key):
                _set_enabled(unique, [pl])


async def _async_enable_sensors(hass: HomeAssistant, entry_id: str, signal_keys: set[str]) -> None:
    """Enable newly-seen sensor entities (Variant 1 availability).

    Sensors are created disabled-by-default (entity_registry_enabled_default=False),
    so unseen signals stay under the HA "disabled" spoiler. As soon as a signal is
    received the integration enables its entity; a user-disabled entity is kept off.
    """
    reg = entity_registry.async_get(hass)
    for key in signal_keys:
        unique = f"{entry_id}_{key}"
        entity_id = reg.async_get_entity_id(Platform.SENSOR, DOMAIN, unique)
        if entity_id is None:
            entity_id = reg.async_get_entity_id(Platform.BINARY_SENSOR, DOMAIN, unique)
        if entity_id is None:
            continue
        entry = reg.async_get(entity_id)
        if entry is None or entry.disabled_by != RegistryEntryDisabler.INTEGRATION:
            continue
        reg.async_enable(entity_id)


class VehicleCommandsView(HomeAssistantView):
    """REST API endpoint for the Android app to poll and acknowledge commands."""

    url = "/api/cartelemetry/commands"
    name = "api:cartelemetry_commands"
    requires_auth = True

    async def get(self, request):
        """Return pending commands for a given car_name and mark them delivered."""
        hass = request.app["hass"]
        car_name = request.query.get("car_name")

        if not car_name:
            return self.json(
                {"status": "error", "message": "missing car_name"},
                status=400,
            )

        entry_id = self._resolve_entry_id(hass, car_name)
        if entry_id is None:
            return self.json(
                {"status": "error", "message": f"unknown car_name: {car_name}"},
                status=404,
            )

        store = hass.data[DOMAIN][entry_id]
        commands = store.setdefault("commands", [])
        pending = []
        now = dt_util.utcnow()
        now_iso = now.isoformat()
        # Drop commands that were already acknowledged or that have not been
        # processed by the Android app within the timeout window (1 minute after
        # delivery or creation). This prevents the queue from filling up with
        # stale commands when the app is offline or fails to acknowledge.
        expired = [cmd for cmd in commands if core.is_command_expired(cmd, now)]
        if expired:
            _LOGGER.warning(
                "Expiring %s unacknowledged command(s) for %s", len(expired), car_name
            )
        commands[:] = [
            cmd for cmd in commands
            if not core.is_command_expired(cmd, now)
        ]
        for cmd in commands:
            if not cmd.get("delivered"):
                cmd["delivered"] = True
                cmd["delivered_at"] = now_iso
                pending.append(cmd)

        return self.json({
            "status": "ok",
            "car_name": car_name,
            "commands": pending,
        })

    async def post(self, request):
        """Acknowledge command execution result from the Android app."""
        hass = request.app["hass"]

        try:
            raw = await request.json()
            data = _ACK_SCHEMA(raw)
        except vol.Invalid as err:
            return self.json(
                {"status": "error", "message": f"invalid payload: {err}"},
                status=400,
            )
        except ValueError:
            return self.json(
                {"status": "error", "message": "invalid json"},
                status=400,
            )

        command_id = data["command_id"]
        status = data.get("status", "ok")
        message = data.get("message", "")

        updated = False
        for store in hass.data.get(DOMAIN, {}).values():
            if isinstance(store, dict) and "commands" in store:
                commands = store["commands"]
                for i, cmd in enumerate(commands):
                    if cmd.get("id") == command_id:
                        cmd["status"] = status
                        cmd["message"] = message
                        cmd["ack_at"] = dt_util.utcnow().isoformat()
                        updated = True
                        # Remove the command from the queue once acknowledged to
                        # prevent unbounded growth of the buffer.
                        commands.pop(i)
                        break
                if updated:
                    break

        if not updated:
            return self.json(
                {"status": "error", "message": f"unknown command_id: {command_id}"},
                status=404,
            )

        _LOGGER.info("Command %s acknowledged: %s (%s)", command_id, status, message)
        return self.json({
            "status": "ok",
            "command_id": command_id,
        })

    def _resolve_entry_id(self, hass: HomeAssistant, car_name: str):
        """Find config entry id by car_name."""
        for entry_id, store in hass.data.get(DOMAIN, {}).items():
            if not isinstance(entry_id, str) or entry_id.startswith("_"):
                continue
            if isinstance(store, dict) and store.get("car_name") == car_name:
                return entry_id
        return None


# Deprecated: legacy paths for APKs older than 3.0; remove after transition.
class VehicleDataLegacyView(VehicleDataView):
    url = "/api/byd_diplus"
    name = "api:byd_diplus_legacy"


class VehicleCommandsLegacyView(VehicleCommandsView):
    url = "/api/byd_diplus/commands"
    name = "api:byd_diplus_legacy_commands"


class VehicleInfoView(HomeAssistantView):
    """Reports integration/API version so the app can check compatibility."""

    url = "/api/cartelemetry/info"
    name = "api:cartelemetry_info"
    requires_auth = True

    async def get(self, request):
        """Return version + capabilities for the Android app."""
        return self.json({
            "status": "ok",
            "integration_version": INTEGRATION_VERSION,
            "api_version": API_VERSION,
            "capabilities": {"batch": True, "commands": True},
        })


_STATIC_REGISTERED = False


def _copy_card_assets(hass) -> bool:
    """Copy the bundled card assets into www/community (blocking I/O → executor)."""
    global _STATIC_REGISTERED
    if _STATIC_REGISTERED:
        return True
    _STATIC_REGISTERED = True
    src = Path(__file__).parent / "www"
    try:
        dst = Path(hass.config.path("www")) / "community" / "cartelemetry-card"
        dst.mkdir(parents=True, exist_ok=True)
        for f in src.iterdir():
            if f.is_file():
                shutil.copy2(f, dst / f.name)
        icons_src = src / "icons"
        if icons_src.is_dir():
            (dst / "icons").mkdir(parents=True, exist_ok=True)
            for icon in icons_src.glob("*.svg"):
                shutil.copy2(icon, dst / "icons" / icon.name)
        assets_src = src / "assets"
        if assets_src.is_dir():
            (dst / "assets").mkdir(parents=True, exist_ok=True)
            for asset in assets_src.iterdir():
                if asset.is_file():
                    shutil.copy2(asset, dst / "assets" / asset.name)
        return True
    except Exception as err:  # noqa: BLE001 - fall back to the legacy static path
        _LOGGER.warning("www/community copy failed (%s) — falling back to /cartelemetry", err)
        return False


async def _register_card_resources(hass):
    """Register the lovelace resource modules (event-loop safe)."""
    url = "/local/community/cartelemetry-card/car-card.js"
    try:
        lovelace = hass.data.get("lovelace")
        resources = getattr(lovelace, "resources", None)
        if resources is None and isinstance(lovelace, dict):
            resources = lovelace.get("resources")
        if resources is not None:
            items = resources.async_items() or []
            for item in items:
                old = getattr(item, "url", None) or item.get("url")
                if old == "/cartelemetry/car-card.js":
                    try:
                        resources.async_delete_item(item)
                    except Exception:  # noqa: BLE001
                        pass
            wanted = [
                "/local/community/cartelemetry-card/car-card.js",
                "/local/community/cartelemetry-card/vehicle-card.js",
            ]
            for u in wanted:
                if not any(getattr(i, "url", None) == u or i.get("url") == u for i in items):
                    hass.async_create_task(
                        resources.async_create_item({"res_type": "module", "url": u})
                    )
            _LOGGER.info("CARTelemetry lovelace resources registered: %s", ", ".join(wanted))
    except Exception as err:  # noqa: BLE001
        _LOGGER.warning("lovelace resource registration failed: %s", err)


async def _install_card(hass):
    """Install card assets (in executor) and register lovelace resources (async)."""
    ok = await hass.async_add_executor_job(_copy_card_assets, hass)
    if not ok:
        try:
            hass.http.register_static_path(
                "/cartelemetry", str(Path(__file__).parent / "www"), cache_headers=False
            )
        except Exception as e2:  # noqa: BLE001
            _LOGGER.debug("static path fallback: %s", e2)
    await _register_card_resources(hass)
