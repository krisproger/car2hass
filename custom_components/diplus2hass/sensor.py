"""Sensor platform for diplus2hass — numeric vehicle signals."""

from homeassistant.components.sensor import SensorEntity
from homeassistant.const import Platform
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo
from homeassistant.helpers.restore_state import RestoreEntity

from .const import DOMAIN, NUMERIC_SENSORS, ENUM_SENSORS, CONF_CAR_NAME, INTEGRATION_VERSION
from . import SIGNAL_VEHICLE_DATA_UPDATED, async_replay_state


async def async_setup_entry(hass, config_entry, async_add_entities):
    """Set up sensors from config entry."""
    car_name = config_entry.data.get(CONF_CAR_NAME, "BYD Vehicle")
    sensors = []
    for signal_key, cfg in NUMERIC_SENSORS.items():
        sensors.append(DiplusSensor(signal_key, cfg, config_entry, car_name))
    for signal_key, cfg in ENUM_SENSORS.items():
        sensors.append(DiplusSensor(signal_key, cfg, config_entry, car_name))

    async_add_entities(sensors)


class DiplusSensor(SensorEntity, RestoreEntity):
    """Represents a numeric vehicle signal."""

    def __init__(self, signal_key, config, config_entry, car_name):
        self._signal_key = signal_key
        self._config = config
        self._entry_id = config_entry.entry_id
        self._car_name = car_name
        self._is_numeric = signal_key in NUMERIC_SENSORS
        self._attr_name = f"{car_name} {config['name']}"
        self._attr_unique_id = f"{config_entry.entry_id}_{signal_key}"
        self._attr_native_unit_of_measurement = config.get("unit") or None
        self._attr_device_class = config.get("device_class")
        self._attr_state_class = config.get("state_class")
        self._attr_icon = config.get("icon")
        self._attr_should_poll = False
        self._attr_native_value = None
        self._attr_extra_state_attributes = {}
        self._attr_available = False
        self._sw_version = INTEGRATION_VERSION

    def _coerce_value(self, raw):
        """Convert a raw string for state storage.

        Numeric sensors must never carry a non-numeric state ('∞', 'NaN',
        '—' etc.) — HA raises ValueError for state_class=measurement. Such
        values become None (unknown) and the raw string is kept in the
        raw_value attribute. Enum sensors keep the raw string.
        """
        try:
            value = float(raw)
        except (ValueError, TypeError):
            if self._is_numeric:
                attrs = dict(self._attr_extra_state_attributes)
                attrs["raw_value"] = raw
                self._attr_extra_state_attributes = attrs
                return None
            return raw
        # A numeric value arrived again — drop the stale raw_value so it no
        # longer misleads (see review #10).
        if self._is_numeric and "raw_value" in self._attr_extra_state_attributes:
            attrs = dict(self._attr_extra_state_attributes)
            attrs.pop("raw_value", None)
            self._attr_extra_state_attributes = attrs
        return value

    def _restore_value(self, state_str):
        """Try to restore a numeric/string value from a saved state."""
        if state_str in (None, "unknown", "unavailable"):
            return None
        try:
            return float(state_str)
        except (ValueError, TypeError):
            # A numeric sensor must never restore a non-numeric state ('∞'
            # etc. stored before the coercion fix) — HA would raise on read.
            return None if self._is_numeric else state_str

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
        """Restore previous state and register update via dispatcher."""
        await super().async_added_to_hass()

        last_state = await self.async_get_last_state()
        if last_state and last_state.state not in (None, "unknown", "unavailable"):
            self._attr_native_value = self._restore_value(last_state.state)
            self._attr_available = True

        async def update():
            store = self.hass.data.get(DOMAIN, {}).get(self._entry_id, {})
            data = store.get("data", {})
            batch = data.get("batch", [])
            written = False

            # Replay the chronological batch so intermediate values are recorded
            # in HA history at their collection time instead of being collapsed
            # into the final value.
            # Prefer the pre-grouped per-signal index (O(1) lookup); fall back
            # to scanning the batch for payloads stored before the index existed.
            signal_index = data.get("signal_index")
            if signal_index is not None:
                values = signal_index.get(self._signal_key, ())
            else:
                values = (
                    (snapshot.get("t", 0), snapshot.get("s", {}).get(self._signal_key))
                    for snapshot in batch
                )
            for t, raw in values:
                if raw is not None:
                    self._attr_available = True
                    new_value = self._coerce_value(raw)
                    if new_value != self._attr_native_value:
                        self._attr_native_value = new_value
                        async_replay_state(self, t)
                        written = True

            # Fallback for non-batch updates (compatibility / empty batch).
            if not batch:
                raw = data.get("signals", {}).get(self._signal_key)
                if raw is not None:
                    self._attr_available = True
                    self._attr_native_value = self._coerce_value(raw)

            av = store.get("app_version", "")
            if av:
                self._sw_version = av
            last_seen = store.get("last_seen")
            if last_seen:
                self._attr_extra_state_attributes = {
                    **self._attr_extra_state_attributes,
                    "last_seen": last_seen,
                }
            if not written:
                self.async_write_ha_state()

        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_VEHICLE_DATA_UPDATED, update)
        )
        await update()
