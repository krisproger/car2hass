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
from homeassistant.util import dt as dt_util

from . import core
from .const import DOMAIN, INTEGRATION_VERSION
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
    _install_card(hass)
    global _VIEW_REGISTERED
    if not _VIEW_REGISTERED:
        hass.http.register_view(VehicleDataView)
        hass.http.register_view(VehicleCommandsView)
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

        if DOMAIN not in hass.data:
            return self.json(
                {"status": "error", "message": "integration not loaded"},
                status=500,
            )

        entry_id = self._resolve_entry_id(hass, car_name)
        if entry_id is None:
            return self.json(
                {"status": "error", "message": f"unknown car_name: {car_name}"},
                status=404,
            )

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

        store["data"] = {
            "timestamp": agg["timestamp"],
            "ts": data.get("ts", agg["timestamp"]),
            "latitude": agg["latitude"],
            "longitude": agg["longitude"],
            "accuracy": agg["accuracy"],
            "fix_timestamp": agg["fix_timestamp"],
            "signals": latest_signals,
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

        return self.json({
            "status": "ok",
            "car_name": car_name,
            "snapshots": len(batch),
            "signals": len(latest_signals),
        })

    def _resolve_entry_id(self, hass: HomeAssistant, car_name: str):
        """Find config entry id by car_name."""
        for entry_id, store in hass.data.get(DOMAIN, {}).items():
            if entry_id.startswith("_"):
                continue
            if store.get("car_name") == car_name:
                return entry_id
        return None


class VehicleCommandsView(HomeAssistantView):
    """REST API endpoint for the Android app to poll and acknowledge commands."""

    url = "/api/cartelemetry/commands"
    name = "api:cartelemetry_commands"
    requires_auth = True


# Deprecated: legacy paths for APKs older than 3.0; remove after transition.
class VehicleDataLegacyView(VehicleDataView):
    url = "/api/byd_diplus"
    name = "api:byd_diplus_legacy"


class VehicleCommandsLegacyView(VehicleCommandsView):
    url = "/api/byd_diplus/commands"
    name = "api:byd_diplus_legacy_commands"

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
            if entry_id.startswith("_"):
                continue
            if store.get("car_name") == car_name:
                return entry_id
        return None

_STATIC_REGISTERED = False


def _install_card(hass):
    """Copy the bundled card assets into www/community and register the resource."""
    global _STATIC_REGISTERED
    if _STATIC_REGISTERED:
        return
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
    except Exception as err:  # noqa: BLE001 - fall back to the legacy static path
        _LOGGER.warning("www/community copy failed (%s) — falling back to /cartelemetry", err)
        try:
            hass.http.register_static_path("/cartelemetry", str(src), cache_headers=False)
        except Exception as e2:  # noqa: BLE001
            _LOGGER.debug("static path fallback: %s", e2)
        return

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
