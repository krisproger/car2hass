"""Lock platform for diplus2hass — vehicle door lock."""

from homeassistant.components.lock import LockEntity
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.restore_state import RestoreEntity

from . import SIGNAL_VEHICLE_DATA_UPDATED, async_enqueue_command
from .const import DOMAIN

from .const import CONF_CAR_NAME, BINARY_ON_MAP, INTEGRATION_VERSION
from .device_info import build_device_info


async def async_setup_entry(hass, config_entry, async_add_entities):
    """Set up the lock entity from config entry."""
    car_name = config_entry.data.get(CONF_CAR_NAME, "BYD Vehicle")
    async_add_entities([CarTelemetryLock(config_entry, car_name)])


class CarTelemetryLock(LockEntity, RestoreEntity):
    """Vehicle central lock, fed by the remote_lock_state signal."""

    def __init__(self, config_entry, car_name):
        self._entry_id = config_entry.entry_id
        self._car_name = car_name
        self._attr_name = f"{car_name} Doors lock"
        self._attr_unique_id = f"{config_entry.entry_id}_doors_lock"
        self._attr_should_poll = False
        self._attr_is_locked = None
        self._attr_available = False
        self._sw_version = INTEGRATION_VERSION

    @property
    def device_info(self):
        build_device_info(self._entry_id, self._car_name, self._sw_version)

    async def async_added_to_hass(self):
        """Restore previous state and register update listener."""
        await super().async_added_to_hass()

        last_state = await self.async_get_last_state()
        if last_state and last_state.state in ("locked", "unlocked"):
            self._attr_is_locked = last_state.state == "locked"
            self._attr_available = True

        async def update():
            store = self.hass.data.get(DOMAIN, {}).get(self._entry_id, {})
            signals = store.get("data", {}).get("signals", {})
            av = store.get("app_version", "")
            if av:
                self._sw_version = av
            raw = signals.get("remote_lock_state")
            if raw is not None:
                self._attr_available = True
                truthy = BINARY_ON_MAP.get("remote_lock_state", ["locked"])
                self._attr_is_locked = str(raw).lower() in truthy
            self.async_write_ha_state()

        self.async_on_remove(
            async_dispatcher_connect(self.hass, SIGNAL_VEHICLE_DATA_UPDATED, update)
        )
        await update()

    async def async_lock(self, **kwargs):
        """Send the doors_lock command."""
        await async_enqueue_command(self.hass, self._car_name, "doors_lock")
        # Optimistic: telemetry confirms within one flush cycle (~12 s).
        self._attr_is_locked = True
        self.async_write_ha_state()

    async def async_unlock(self, **kwargs):
        """Send the doors_unlock command."""
        await async_enqueue_command(self.hass, self._car_name, "doors_unlock")
        self._attr_is_locked = False
        self.async_write_ha_state()
