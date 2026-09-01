"""Switch platform for diplus2hass — paired on/off vehicle commands."""

from homeassistant.components.switch import SwitchEntity
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.restore_state import RestoreEntity

from . import SIGNAL_VEHICLE_DATA_UPDATED, async_enqueue_command
from .commands import SWITCH_COMMANDS, SwitchCommand
from .const import DOMAIN

from .const import CONF_CAR_NAME, BINARY_ON_MAP, INTEGRATION_VERSION
from .device_info import build_device_info


async def async_setup_entry(hass, config_entry, async_add_entities):
    """Set up switch entities from config entry."""
    car_name = config_entry.data.get(CONF_CAR_NAME, "BYD Vehicle")
    switches = [
        CarTelemetrySwitch(cmd, config_entry, car_name)
        for cmd in SWITCH_COMMANDS
    ]
    async_add_entities(switches)


class CarTelemetrySwitch(SwitchEntity, RestoreEntity):
    """A switch that sends paired on/off DiPlus commands."""

    def __init__(self, cmd: SwitchCommand, config_entry, car_name):
        self._cmd = cmd
        self._entry_id = config_entry.entry_id
        self._car_name = car_name
        self._attr_name = f"{car_name} {cmd.name}"
        self._attr_unique_id = f"{config_entry.entry_id}_switch_{cmd.key}"
        self._attr_should_poll = False
        self._attr_is_on = None
        self._attr_available = False
        self._attr_icon = cmd.icon_off
        self._sw_version = INTEGRATION_VERSION

    @property
    def device_info(self):
        return build_device_info(self._entry_id, self._car_name, self._sw_version)

    async def async_added_to_hass(self):
        """Restore previous state and register update listener."""
        await super().async_added_to_hass()

        last_state = await self.async_get_last_state()
        if last_state and last_state.state in ("on", "off"):
            self._attr_is_on = last_state.state == "on"
            self._attr_icon = self._cmd.icon_on if self._attr_is_on else self._cmd.icon_off
            self._attr_available = True

        async def update():
            store = self.hass.data.get(DOMAIN, {}).get(self._entry_id, {})
            signals = store.get("data", {}).get("signals", {})
            av = store.get("app_version", "")
            if av:
                self._sw_version = av

            if self._cmd.state_signal:
                raw = signals.get(self._cmd.state_signal)
                if raw is not None:
                    self._attr_available = True
                    self._attr_is_on = self._state_to_bool(str(raw).lower())
                    self._attr_icon = self._cmd.icon_on if self._attr_is_on else self._cmd.icon_off
            else:
                # No telemetry feedback; mark available once any data is received.
                if signals:
                    self._attr_available = True

            self.async_write_ha_state()

        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_VEHICLE_DATA_UPDATED, update)
        )
        await update()

    async def async_turn_on(self, **kwargs):
        """Send the turn-on command."""
        await async_enqueue_command(self.hass, self._car_name, self._cmd.turn_on_cmd)
        # Optimistically update state if no telemetry feedback is available.
        if self._cmd.state_signal is None:
            self._attr_is_on = True
            self._attr_icon = self._cmd.icon_on
            self.async_write_ha_state()

    async def async_turn_off(self, **kwargs):
        """Send the turn-off command."""
        await async_enqueue_command(self.hass, self._car_name, self._cmd.turn_off_cmd)
        if self._cmd.state_signal is None:
            self._attr_is_on = False
            self._attr_icon = self._cmd.icon_off
            self.async_write_ha_state()

    def _state_to_bool(self, str_val):
        """Map a telemetry signal value to a boolean state."""
        signal_key = self._cmd.state_signal
        truthy = BINARY_ON_MAP.get(signal_key)
        if not truthy:
            truthy = ["on", "open", "true", "1", "locked", "enabled", "active",
                      "running", "starting", "hazard"]
        return str_val in truthy
