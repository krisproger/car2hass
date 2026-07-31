"""Number platform for diplus2hass — numeric vehicle command setters."""

from homeassistant.components.number import NumberEntity
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo
from homeassistant.helpers.restore_state import RestoreEntity

from . import SIGNAL_VEHICLE_DATA_UPDATED, async_enqueue_command
from .commands import NUMBER_COMMANDS, NumberCommand
from .const import DOMAIN, CONF_CAR_NAME, INTEGRATION_VERSION


async def async_setup_entry(hass, config_entry, async_add_entities):
    """Set up number entities from config entry."""
    car_name = config_entry.data.get(CONF_CAR_NAME, "BYD Vehicle")
    numbers = [
        DiplusNumber(cmd, config_entry, car_name)
        for cmd in NUMBER_COMMANDS
    ]
    async_add_entities(numbers)


class DiplusNumber(NumberEntity, RestoreEntity):
    """A number input that sends a numeric DiPlus command."""

    def __init__(self, cmd: NumberCommand, config_entry, car_name):
        self._cmd = cmd
        self._entry_id = config_entry.entry_id
        self._car_name = car_name
        self._attr_name = f"{car_name} {cmd.name}"
        self._attr_unique_id = f"{config_entry.entry_id}_number_{cmd.key}"
        self._attr_should_poll = False
        self._attr_native_min_value = cmd.min_value
        self._attr_native_max_value = cmd.max_value
        self._attr_native_step = cmd.step
        self._attr_native_unit_of_measurement = cmd.unit
        self._attr_icon = cmd.icon
        self._attr_native_value = None
        self._attr_available = False
        self._sw_version = INTEGRATION_VERSION

    @property
    def device_info(self):
        return DeviceInfo(
            identifiers={(DOMAIN, self._entry_id)},
            name=self._car_name,
            manufacturer="BYD",
            model="DiPlus-to-hass",
            sw_version=self._sw_version,
        )

    async def async_added_to_hass(self):
        """Restore previous value and register update listener."""
        await super().async_added_to_hass()

        last_state = await self.async_get_last_state()
        if last_state and last_state.state not in (None, "unknown", "unavailable"):
            try:
                self._attr_native_value = float(last_state.state)
                self._attr_available = True
            except (ValueError, TypeError):
                self._attr_native_value = None

        async def update():
            store = self.hass.data.get(DOMAIN, {}).get(self._entry_id, {})
            signals = store.get("data", {}).get("signals", {})
            av = store.get("app_version", "")
            if av:
                self._sw_version = av
            if self._cmd.state_signal:
                raw = signals.get(self._cmd.state_signal)
                if raw is not None:
                    try:
                        self._attr_native_value = float(raw)
                        self._attr_available = True
                    except (ValueError, TypeError):
                        pass
            elif signals:
                self._attr_available = True
            self.async_write_ha_state()

        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_VEHICLE_DATA_UPDATED, update)
        )
        await update()

    async def async_set_native_value(self, value: float):
        """Send the command with the selected numeric value."""
        await async_enqueue_command(
            self.hass, self._car_name, self._cmd.command_template,
            params={"value": value},
        )
        self._attr_native_value = value
        self.async_write_ha_state()
