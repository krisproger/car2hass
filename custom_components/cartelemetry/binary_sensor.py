"""Binary sensor platform for diplus2hass — door, seatbelt, light states."""

from homeassistant.components.binary_sensor import BinarySensorEntity
from homeassistant.const import Platform
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.restore_state import RestoreEntity
from datetime import timedelta

from homeassistant.helpers.event import async_track_point_in_utc_time
from homeassistant.util import dt as dt_util
from homeassistant.util.dt import utcnow

from .const import (
    DOMAIN,
    BINARY_SENSORS,
    BINARY_ON_MAP,
    CONF_CAR_NAME,
    INTEGRATION_VERSION,
    GEOFENCE_KEY_PREFIX,
    GEOFENCE_NAME_SUFFIX,
    GEOFENCE_ON_VALUES,
    ONLINE_OFFLINE_SECONDS,
)
from .device_info import build_device_info
from . import core
from . import SIGNAL_VEHICLE_DATA_UPDATED, async_replay_state


async def async_setup_entry(hass, config_entry, async_add_entities):
    """Set up binary sensors from config entry."""
    car_name = config_entry.data.get(CONF_CAR_NAME, "BYD Vehicle")
    sensors = []
    for signal_key, cfg in BINARY_SENSORS.items():
        sensors.append(CarTelemetryBinarySensor(signal_key, cfg, config_entry, car_name))
    async_add_entities(sensors)

    # Dynamic geofence sensors. The Android app reports virtual zone states as
    # geo_<zoneId> keys ("inside"/"outside") which only appear in telemetry
    # after the app evaluates its zones, so entities are created on the fly as
    # new keys show up in the aggregated signals. The friendly name comes from
    # the companion geo_<zoneId>_name key, falling back to the zone id.
    known_geo_keys = set()

    async def discover_geofences():
        store = hass.data.get(DOMAIN, {}).get(config_entry.entry_id, {})
        signals = store.get("data", {}).get("signals", {})
        new_entities = []
        for key in core.find_geofence_keys(signals):
            if key in known_geo_keys:
                continue
            known_geo_keys.add(key)
            zone_name = core.geofence_zone_name(signals, key)
            cfg = {"name": f"Geofence {zone_name}", "device_class": "presence"}
            new_entities.append(CarTelemetryBinarySensor(key, cfg, config_entry, car_name))
        if new_entities:
            async_add_entities(new_entities)

    await discover_geofences()
    config_entry.async_on_unload(
        async_dispatcher_connect(hass, SIGNAL_VEHICLE_DATA_UPDATED, discover_geofences)
    )


class CarTelemetryBinarySensor(BinarySensorEntity, RestoreEntity):
    """Represents a binary vehicle signal (door, seatbelt, light, etc)."""

    def __init__(self, signal_key, config, config_entry, car_name):
        self._signal_key = signal_key
        self._config = config
        self._entry_id = config_entry.entry_id
        self._car_name = car_name
        self._attr_name = f"{car_name} {config['name']}"
        self._attr_unique_id = f"{config_entry.entry_id}_{signal_key}"
        self._attr_device_class = config.get("device_class")
        self._attr_should_poll = False
        self._attr_is_on = None
        self._attr_available = False
        self._attr_extra_state_attributes = {}
        self._sw_version = INTEGRATION_VERSION
        self._unsub_timer = None
        # Zone name for dynamic geofence entities; re-checked on each update so a
        # rename in the app propagates to the entity name without a reload.
        self._zone_name = None
        self._is_geofence = (
            signal_key.startswith(GEOFENCE_KEY_PREFIX)
            and not signal_key.endswith(GEOFENCE_NAME_SUFFIX)
        )

    @property
    def device_info(self):
        return build_device_info(self._entry_id, self._car_name, self._sw_version)

    async def async_added_to_hass(self):
        """Restore previous state and register update via dispatcher."""
        await super().async_added_to_hass()

        last_state = await self.async_get_last_state()
        if last_state and last_state.state in ("on", "off"):
            self._attr_is_on = last_state.state == "on"
            self._attr_available = True

        async def update():
            store = self.hass.data.get(DOMAIN, {}).get(self._entry_id, {})
            data = store.get("data", {})
            batch = data.get("batch", [])
            written = False

            # Dynamic geofence entities: keep the friendly name in sync when the
            # zone is renamed in the app — geo_<id>_name changes arrive live in
            # the batch, so update the entity name without a reload.
            if self._is_geofence:
                name_val = data.get("signals", {}).get(
                    self._signal_key + GEOFENCE_NAME_SUFFIX
                )
                if isinstance(name_val, str) and name_val.strip():
                    new_name = f"{self._car_name} Geofence {name_val.strip()}"
                    if new_name != self._attr_name:
                        self._attr_name = new_name
                        written = True

            if self._signal_key == "online":
                last_seen_raw = store.get("last_seen")
                if last_seen_raw:
                    last_seen = dt_util.parse_datetime(last_seen_raw)
                    if last_seen:
                        age = (utcnow() - last_seen).total_seconds()
                        self._attr_is_on = age < ONLINE_OFFLINE_SECONDS
                        self._attr_available = True
                        self._schedule_online_timeout()
                    else:
                        self._attr_is_on = False
                        self._attr_available = True
                else:
                    self._attr_is_on = False
                    self._attr_available = True
            else:
                # Prefer the pre-grouped per-signal index (O(1) lookup); fall
                # back to scanning the batch, then to the aggregated signals.
                signal_index = data.get("signal_index")
                if signal_index is not None:
                    values = signal_index.get(self._signal_key, ())
                elif batch:
                    values = (
                        (snapshot.get("t", 0), snapshot.get("s", {}).get(self._signal_key))
                        for snapshot in batch
                    )
                else:
                    values = ((0, data.get("signals", {}).get(self._signal_key)),)
                for t, raw in values:
                    if raw is not None:
                        raw_lower = str(raw).lower()
                        if raw_lower == "invalid":
                            # DiPlus reports "invalid" (无效) when there is no
                            # data for this signal. Mark the entity unavailable
                            # instead of mapping to OFF, which would look like a
                            # disengaged lock / unbuckled belt.
                            if self._attr_available or self._attr_is_on is not None:
                                self._attr_is_on = None
                                self._attr_available = False
                                written = True
                            continue
                        self._attr_available = True
                        new_state = self._value_to_bool(raw_lower)
                        if new_state != self._attr_is_on:
                            self._attr_is_on = new_state
                            async_replay_state(self, t)
                            written = True

            av = store.get("app_version", "")
            if av:
                self._sw_version = av
            last_seen = store.get("last_seen")
            if last_seen:
                extra = dict(getattr(self, "_attr_extra_state_attributes", {}))
                extra["last_seen"] = last_seen
                self._attr_extra_state_attributes = extra
            if not written:
                self.async_write_ha_state()

        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_VEHICLE_DATA_UPDATED, update)
        )
        await update()

    async def _online_timeout(self, _now):
        self._unsub_timer = None
        store = self.hass.data.get(DOMAIN, {}).get(self._entry_id, {})
        last_seen_raw = store.get("last_seen")
        if last_seen_raw:
            last_seen = dt_util.parse_datetime(last_seen_raw)
            if last_seen:
                age = (utcnow() - last_seen).total_seconds()
                self._attr_is_on = age < ONLINE_OFFLINE_SECONDS
            else:
                self._attr_is_on = False
        else:
            self._attr_is_on = False
        self.async_write_ha_state()

    def _schedule_online_timeout(self):
        if self._unsub_timer is not None:
            self._unsub_timer()
            self._unsub_timer = None
        self._unsub_timer = async_track_point_in_utc_time(
            self.hass, self._online_timeout, utcnow() + timedelta(seconds=ONLINE_OFFLINE_SECONDS)
        )

    async def async_will_remove_from_hass(self):
        if self._unsub_timer is not None:
            self._unsub_timer()
            self._unsub_timer = None
        await super().async_will_remove_from_hass()

    def _value_to_bool(self, str_val):
        """Map translated Android enum strings to binary sensor state."""
        if self._signal_key.startswith(GEOFENCE_KEY_PREFIX):
            # Dynamic geofence zones: "inside" = on, anything else = off.
            return str_val in GEOFENCE_ON_VALUES
        truthy = BINARY_ON_MAP.get(self._signal_key, BINARY_ON_MAP.get("_default_", []))
        return str_val in truthy
