"""Climate platform for diplus2hass — unified vehicle A/C entity."""

from homeassistant.components.climate import ClimateEntity
from homeassistant.components.climate.const import (
    ClimateEntityFeature,
    HVACAction,
    HVACMode,
)
from homeassistant.const import UnitOfTemperature
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.restore_state import RestoreEntity

from . import SIGNAL_VEHICLE_DATA_UPDATED, async_enqueue_command
from .const import DOMAIN

from .const import CONF_CAR_NAME, BINARY_ON_MAP, INTEGRATION_VERSION
from .device_info import build_device_info

_FAN_MODES = [str(i) for i in range(1, 10)]


async def async_setup_entry(hass, config_entry, async_add_entities):
    """Set up the climate entity from config entry."""
    car_name = config_entry.data.get(CONF_CAR_NAME, "BYD Vehicle")
    async_add_entities([CarTelemetryClimate(config_entry, car_name)])


class CarTelemetryClimate(ClimateEntity, RestoreEntity):
    """Vehicle A/C as a single thermostat entity.

    State feedback: ac_state (on/off), cabin_temp, ac_set_temp, fan_speed.
    Commands: ac_on/ac_off, ac_temp (°C), ac_fan (1-9). Fine-grained controls
    (airflow mode, recirculation, seat heating/ventilation, defrost) remain
    available as the existing select/switch/number entities.
    """

    _attr_hvac_modes = [HVACMode.OFF, HVACMode.HEAT_COOL]
    _attr_fan_modes = _FAN_MODES
    _attr_min_temp = 16
    _attr_max_temp = 30
    _attr_target_temperature_step = 0.5
    _attr_temperature_unit = UnitOfTemperature.CELSIUS
    _attr_supported_features = (
        ClimateEntityFeature.TARGET_TEMPERATURE
        | ClimateEntityFeature.FAN_MODE
        | ClimateEntityFeature.TURN_ON
        | ClimateEntityFeature.TURN_OFF
    )
    _enable_turn_on_off_backwards_compatibility = False

    def __init__(self, config_entry, car_name):
        self._entry_id = config_entry.entry_id
        self._car_name = car_name
        self._attr_name = f"{car_name} Climate"
        self._attr_unique_id = f"{config_entry.entry_id}_climate"
        self._attr_should_poll = False
        self._attr_hvac_mode = None
        self._attr_hvac_action = None
        self._attr_current_temperature = None
        self._attr_target_temperature = None
        self._attr_fan_mode = None
        self._attr_available = False
        self._sw_version = INTEGRATION_VERSION

    @property
    def device_info(self):
        build_device_info(self._entry_id, self._car_name, self._sw_version)

    async def async_added_to_hass(self):
        """Restore previous state and register update listener."""
        await super().async_added_to_hass()

        last_state = await self.async_get_last_state()
        if last_state and last_state.state in (HVACMode.OFF, HVACMode.HEAT_COOL):
            self._attr_hvac_mode = HVACMode(last_state.state)
            self._attr_available = True
            target = last_state.attributes.get("temperature")
            if target is not None:
                try:
                    self._attr_target_temperature = float(target)
                except (ValueError, TypeError):
                    pass

        async def update():
            store = self.hass.data.get(DOMAIN, {}).get(self._entry_id, {})
            signals = store.get("data", {}).get("signals", {})
            av = store.get("app_version", "")
            if av:
                self._sw_version = av

            ac_raw = signals.get("ac_state")
            if ac_raw is not None:
                self._attr_available = True
                truthy = BINARY_ON_MAP.get("ac_state", ["on", "open"])
                ac_on = str(ac_raw).lower() in truthy
                self._attr_hvac_mode = HVACMode.HEAT_COOL if ac_on else HVACMode.OFF
                self._attr_hvac_action = HVACAction.COOLING if ac_on else HVACAction.OFF

            cabin = signals.get("cabin_temp")
            if cabin is not None:
                try:
                    self._attr_current_temperature = float(cabin)
                except (ValueError, TypeError):
                    pass

            target = signals.get("ac_set_temp")
            if target is not None:
                try:
                    self._attr_target_temperature = float(target)
                except (ValueError, TypeError):
                    pass

            fan = signals.get("fan_speed")
            if fan is not None:
                try:
                    level = int(float(fan))
                    if 1 <= level <= 9:
                        self._attr_fan_mode = str(level)
                except (ValueError, TypeError):
                    pass

            self.async_write_ha_state()

        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_VEHICLE_DATA_UPDATED, update)
        )
        await update()

    async def async_set_hvac_mode(self, hvac_mode):
        """Turn the A/C on (HEAT_COOL) or off."""
        if hvac_mode == HVACMode.OFF:
            await async_enqueue_command(self.hass, self._car_name, "ac_off")
        else:
            await async_enqueue_command(self.hass, self._car_name, "ac_on")
        self._attr_hvac_mode = hvac_mode
        self.async_write_ha_state()

    async def async_turn_on(self):
        """Turn the A/C on."""
        await async_enqueue_command(self.hass, self._car_name, "ac_on")
        self._attr_hvac_mode = HVACMode.HEAT_COOL
        self.async_write_ha_state()

    async def async_turn_off(self):
        """Turn the A/C off."""
        await async_enqueue_command(self.hass, self._car_name, "ac_off")
        self._attr_hvac_mode = HVACMode.OFF
        self.async_write_ha_state()

    async def async_set_temperature(self, **kwargs):
        """Set the target cabin temperature (16-30 °C, 0.5 step)."""
        temperature = kwargs.get("temperature")
        if temperature is None:
            return
        temperature = max(self._attr_min_temp, min(self._attr_max_temp, float(temperature)))
        await async_enqueue_command(
            self.hass, self._car_name, "ac_temp", params={"value": temperature},
        )
        self._attr_target_temperature = temperature
        self.async_write_ha_state()

    async def async_set_fan_mode(self, fan_mode):
        """Set the fan level (1-9)."""
        try:
            level = int(fan_mode)
        except (ValueError, TypeError):
            return
        if not 1 <= level <= 9:
            return
        await async_enqueue_command(
            self.hass, self._car_name, "ac_fan", params={"value": level},
        )
        self._attr_fan_mode = str(level)
        self.async_write_ha_state()
