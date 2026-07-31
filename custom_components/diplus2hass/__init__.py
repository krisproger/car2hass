"""DiPlus-to-hass integration for Home Assistant."""

import logging
import voluptuous as vol

from homeassistant.components.http import HomeAssistantView
from homeassistant.components.persistent_notification import async_create
from homeassistant.const import Platform
from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.helpers import config_validation as cv
from homeassistant.helpers.dispatcher import async_dispatcher_send
from homeassistant.util import dt as dt_util

from . import core
from .const import DOMAIN
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


def _is_command_expired(cmd: dict, now) -> bool:
    """Return True if a command is stuck unprocessed for too long."""
    return core.is_command_expired(cmd, now)


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
    global _VIEW_REGISTERED
    if not _VIEW_REGISTERED:
        hass.http.register_view(VehicleDataView)
        hass.http.register_view(VehicleCommandsView)
        _VIEW_REGISTERED = True

    # Register developer info service
    async def handle_developer_info(call):
        info_lines = []
        total_signals = 0
        for entry_id, store in hass.data.get(DOMAIN, {}).items():
            if entry_id == "_view_registered":
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
            title="DiPlus-to-hass Developer Info",
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

    url = "/api/byd_diplus"
    name = "api:byd_diplus"
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

    url = "/api/byd_diplus/commands"
    name = "api:byd_diplus_commands"
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
        expired = [cmd for cmd in commands if _is_command_expired(cmd, now)]
        if expired:
            _LOGGER.warning(
                "Expiring %s unacknowledged command(s) for %s", len(expired), car_name
            )
        commands[:] = [
            cmd for cmd in commands
            if not _is_command_expired(cmd, now)
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
