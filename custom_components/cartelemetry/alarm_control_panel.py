"""Alarm control panel platform for diplus2hass — sentry mode."""

from homeassistant.components.alarm_control_panel import (
    AlarmControlPanelEntity,
    AlarmControlPanelState,
)
from homeassistant.components.alarm_control_panel.const import (
    AlarmControlPanelEntityFeature,
)
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.restore_state import RestoreEntity

from . import SIGNAL_VEHICLE_DATA_UPDATED, async_enqueue_command
from .const import DOMAIN

from .const import CONF_CAR_NAME, INTEGRATION_VERSION
from .device_info import build_device_info


async def async_setup_entry(hass, config_entry, async_add_entities):
    """Set up the alarm panel entity from config entry."""
    car_name = config_entry.data.get(CONF_CAR_NAME, "BYD Vehicle")
    async_add_entities([CarTelemetryAlarmPanel(config_entry, car_name)])


class CarTelemetryAlarmPanel(AlarmControlPanelEntity, RestoreEntity):
    """Sentry mode presented as an alarm panel.

    State feedback comes from the numeric `sentry_state` signal. DiPlus does
    not publish labels for it, so the mapping is a documented heuristic:
    0 means disarmed, any other value means armed (armed_away).
    """

    _attr_code_arm_required = False
    _attr_supported_features = AlarmControlPanelEntityFeature.ARM_AWAY

    def __init__(self, config_entry, car_name):
        self._entry_id = config_entry.entry_id
        self._car_name = car_name
        self._attr_name = f"{car_name} Sentry mode"
        self._attr_unique_id = f"{config_entry.entry_id}_sentry_alarm"
        self._attr_should_poll = False
        self._attr_state = None
        self._attr_available = False
        self._sw_version = INTEGRATION_VERSION

    @property
    def device_info(self):
        build_device_info(self._entry_id, self._car_name, self._sw_version)

    async def async_added_to_hass(self):
        """Restore previous state and register update listener."""
        await super().async_added_to_hass()

        last_state = await self.async_get_last_state()
        if last_state and last_state.state in (AlarmControlPanelState.DISARMED, AlarmControlPanelState.ARMED_AWAY):
            self._attr_state = last_state.state
            self._attr_available = True

        async def update():
            store = self.hass.data.get(DOMAIN, {}).get(self._entry_id, {})
            signals = store.get("data", {}).get("signals", {})
            av = store.get("app_version", "")
            if av:
                self._sw_version = av
            raw = signals.get("sentry_state")
            if raw is not None:
                self._attr_available = True
                try:
                    armed = float(raw) != 0
                except (ValueError, TypeError):
                    armed = str(raw).lower() not in ("0", "off", "disarmed", "")
                self._attr_state = (
                    AlarmControlPanelState.ARMED_AWAY if armed else AlarmControlPanelState.DISARMED
                )
            self.async_write_ha_state()

        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_VEHICLE_DATA_UPDATED, update)
        )
        await update()

    async def async_alarm_arm_away(self, code=None):
        """Arm sentry mode (engine-off sentry)."""
        await async_enqueue_command(
            self.hass, self._car_name, "sentry", params={"value": "engine_off_on"},
        )
        self._attr_state = AlarmControlPanelState.ARMED_AWAY
        self.async_write_ha_state()

    async def async_alarm_disarm(self, code=None):
        """Disarm sentry mode."""
        await async_enqueue_command(
            self.hass, self._car_name, "sentry", params={"value": "engine_off_off"},
        )
        self._attr_state = AlarmControlPanelState.DISARMED
        self.async_write_ha_state()
