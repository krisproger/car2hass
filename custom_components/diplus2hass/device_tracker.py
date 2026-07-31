"""Device tracker platform for diplus2hass — GPS location."""

try:
    from homeassistant.components.device_tracker.TrackerEntity import TrackerEntity
    from homeassistant.components.device_tracker.const import SourceType
except ImportError:
    from homeassistant.components.device_tracker import TrackerEntity, SourceType
from homeassistant.const import Platform
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity import DeviceInfo
from homeassistant.helpers.restore_state import RestoreEntity

from .const import DOMAIN, CONF_CAR_NAME, INTEGRATION_VERSION
from . import SIGNAL_VEHICLE_DATA_UPDATED


async def async_setup_entry(hass, config_entry, async_add_entities):
    """Set up device tracker from config entry."""
    async_add_entities([VehicleTracker(config_entry)])


class VehicleTracker(TrackerEntity, RestoreEntity):
    """Track vehicle location via GPS data from the Android app."""

    def __init__(self, config_entry):
        self._entry_id = config_entry.entry_id
        self._car_name = config_entry.data.get(CONF_CAR_NAME, "BYD Vehicle")
        self._attr_name = f"{self._car_name} Location"
        self._attr_unique_id = f"{config_entry.entry_id}_tracker"
        self._attr_should_poll = False
        self._latitude = None
        self._longitude = None
        self._accuracy = 0
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

    @property
    def latitude(self):
        return self._latitude

    @property
    def longitude(self):
        return self._longitude

    @property
    def location_accuracy(self):
        return self._accuracy

    @property
    def source_type(self):
        return SourceType.GPS

    async def async_added_to_hass(self):
        """Restore previous location and register update via dispatcher."""
        await super().async_added_to_hass()

        last_state = await self.async_get_last_state()
        if last_state:
            attrs = last_state.attributes
            try:
                lat = attrs.get("latitude")
                lon = attrs.get("longitude")
                if lat is not None and lon is not None:
                    self._latitude = float(lat)
                    self._longitude = float(lon)
                    self._accuracy = float(attrs.get("gps_accuracy", 0))
                    self._attr_available = True
            except (ValueError, TypeError):
                pass

        async def update():
            store = self.hass.data.get(DOMAIN, {}).get(self._entry_id, {})
            data = store.get("data", {})
            batch = data.get("batch", [])
            written = False

            # Replay the chronological batch so the map shows the actual path
            # instead of jumping straight to the latest coordinate.
            # Prefer the pre-built gps_track; fall back to scanning the batch
            # for payloads stored before the track existed.
            gps_track = data.get("gps_track")
            if gps_track is not None:
                points = gps_track
            else:
                points = []
                for snapshot in batch:
                    gps = snapshot.get("g", {})
                    lat = gps.get("lat")
                    lon = gps.get("lon")
                    if lat is not None and lon is not None:
                        points.append((lat, lon, gps.get("a", 0)))
            for lat, lon, acc in points:
                self._latitude = float(lat)
                self._longitude = float(lon)
                self._accuracy = float(acc)
                self._attr_available = True
                self.async_write_ha_state()
                written = True

            # Fallback for non-batch updates.
            if not batch:
                lat = data.get("latitude")
                lon = data.get("longitude")
                if lat is not None and lon is not None:
                    self._latitude = lat
                    self._longitude = lon
                    self._accuracy = data.get("accuracy", 0)
                    self._attr_available = True

            av = store.get("app_version", "")
            if av:
                self._sw_version = av
            last_seen = store.get("last_seen")
            if last_seen:
                extra = dict(getattr(self, "_attr_extra_state_attributes", {}))
                extra["last_seen"] = last_seen
                raw_speed = data.get("signals", {}).get("speed")
                if raw_speed is not None:
                    try:
                        extra["speed"] = float(raw_speed)
                    except (ValueError, TypeError):
                        pass
                self._attr_extra_state_attributes = extra
            if not written:
                self.async_write_ha_state()

        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_VEHICLE_DATA_UPDATED, update)
        )
        await update()
