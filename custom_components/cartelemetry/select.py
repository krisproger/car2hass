"""Select platform for diplus2hass — enum vehicle command setters."""

from homeassistant.components.select import SelectEntity
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.restore_state import RestoreEntity

from . import SIGNAL_VEHICLE_DATA_UPDATED, async_enqueue_command
from .commands import SELECT_COMMANDS, SelectCommand
from .const import DOMAIN

from .const import CONF_CAR_NAME, INTEGRATION_VERSION
from .device_info import build_device_info


async def async_setup_entry(hass, config_entry, async_add_entities):
    """Set up select entities from config entry."""
    car_name = config_entry.data.get(CONF_CAR_NAME, "BYD Vehicle")
    selects = [
        CarTelemetrySelect(cmd, config_entry, car_name)
        for cmd in SELECT_COMMANDS
    ]
    async_add_entities(selects)


class CarTelemetrySelect(SelectEntity, RestoreEntity):
    """A select input that sends an enum DiPlus command."""

    def __init__(self, cmd: SelectCommand, config_entry, car_name):
        self._cmd = cmd
        self._entry_id = config_entry.entry_id
        self._car_name = car_name
        self._attr_name = f"{car_name} {cmd.name}"
        self._attr_unique_id = f"{config_entry.entry_id}_select_{cmd.key}"
        self._attr_should_poll = False
        self._attr_options = list(cmd.options.keys())
        self._attr_icon = cmd.icon
        self._attr_current_option = None
        self._attr_available = False
        self._sw_version = INTEGRATION_VERSION
        # Map HA option label -> Android value id
        self._option_to_value = dict(cmd.options)
        self._value_to_option = {v: k for k, v in cmd.options.items()}

    @property
    def device_info(self):
        build_device_info(self._entry_id, self._car_name, self._sw_version)

    async def async_added_to_hass(self):
        """Restore previous option and register update listener."""
        await super().async_added_to_hass()

        last_state = await self.async_get_last_state()
        if last_state and last_state.state in self._option_to_value:
            self._attr_current_option = last_state.state
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
                    value_id = self._cmd.state_map.get(str(raw).strip().lower())
                    option = self._value_to_option.get(value_id) if value_id else None
                    if option is not None:
                        self._attr_current_option = option
                        self._attr_available = True
            elif signals:
                self._attr_available = True
            self.async_write_ha_state()

        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_VEHICLE_DATA_UPDATED, update)
        )
        await update()

    async def async_select_option(self, option: str):
        """Send the command with the selected enum value."""
        value = self._option_to_value.get(option)
        if value is None:
            return
        await async_enqueue_command(
            self.hass, self._car_name, self._cmd.command_template,
            params={"value": value},
        )
        self._attr_current_option = option
        self.async_write_ha_state()
