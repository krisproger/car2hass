"""Shared DeviceInfo builder for all entity platforms."""

from homeassistant.helpers.device_registry import DeviceInfo

from .const import DOMAIN, INTEGRATION_VERSION


def build_device_info(entry_id: str, car_name: str, sw_version: str,
                      via_device_id: str | None = None) -> DeviceInfo:
    """Device block shared by every platform.

    sw_version tracks the Android app version reported in the payload; until
    the first packet arrives it falls back to the integration version.
    ``via_device_id`` links a helper device (e.g. the tracker) under the main
    car device so both show as one car.
    """
    return DeviceInfo(
        identifiers={(DOMAIN, entry_id)},
        name=car_name,
        manufacturer="BYD",
        model="Car2Hass",
        sw_version=sw_version or INTEGRATION_VERSION,
        via_device_id=via_device_id,
    )


def build_phone_device_info(entry_id: str, device_id: str | None,
                            device_name: str | None, sw_version: str,
                            manufacturer: str | None = None,
                            model: str | None = None) -> DeviceInfo:
    """Device block for the phone device living in the same config entry.

    The identifier is namespaced with ``:phone:<device_id>`` so it can never
    collide with the car device (``entry_id``) or the tracker
    (``entry_id_tracker``). Manufacturer/model are best-effort from the
    payload; the app version drives sw_version.
    """
    ident = f"{entry_id}:phone:{device_id}" if device_id else f"{entry_id}:phone"
    return DeviceInfo(
        identifiers={(DOMAIN, ident)},
        name=device_name or "Phone",
        manufacturer=manufacturer or "Android",
        model=model or "Car2Hass",
        sw_version=sw_version or INTEGRATION_VERSION,
    )
