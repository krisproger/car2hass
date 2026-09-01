"""Button platform for diplus2hass — one-shot vehicle commands."""

from homeassistant.components.button import ButtonEntity
from homeassistant.helpers.dispatcher import async_dispatcher_connect

from . import SIGNAL_VEHICLE_DATA_UPDATED, async_enqueue_command
from .commands import BUTTON_COMMANDS, ButtonCommand
from .const import DOMAIN

from .const import CONF_CAR_NAME, INTEGRATION_VERSION
from .device_info import build_device_info


async def async_setup_entry(hass, config_entry, async_add_entities):
    """Set up button entities from config entry."""
    car_name = config_entry.data.get(CONF_CAR_NAME, "BYD Vehicle")
    buttons = [
        CarTelemetryButton(cmd, config_entry, car_name)
        for cmd in BUTTON_COMMANDS
    ]
    async_add_entities(buttons)


class CarTelemetryButton(ButtonEntity):
    """A button that sends a one-shot DiPlus command."""

    def __init__(self, cmd: ButtonCommand, config_entry, car_name):
        self._cmd = cmd
        self._entry_id = config_entry.entry_id
        self._car_name = car_name
        self._attr_name = f"{car_name} {cmd.name}"
        self._attr_unique_id = f"{config_entry.entry_id}_button_{cmd.key}"
        self._attr_should_poll = False
        self._attr_icon = cmd.icon
        self._attr_available = False
        self._sw_version = INTEGRATION_VERSION

    @property
    def device_info(self):
        build_device_info(self._entry_id, self._car_name, self._sw_version)

    async def async_added_to_hass(self):
        """Register update listener to set availability."""
        async def update():
            store = self.hass.data.get(DOMAIN, {}).get(self._entry_id, {})
            signals = store.get("data", {}).get("signals", {})
            av = store.get("app_version", "")
            if av:
                self._sw_version = av
            if signals:
                self._attr_available = True
            self.async_write_ha_state()

        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_VEHICLE_DATA_UPDATED, update)
        )
        await update()

    async def async_press(self):
        """Send the command when the button is pressed."""
        await async_enqueue_command(self.hass, self._car_name, self._cmd.command)
