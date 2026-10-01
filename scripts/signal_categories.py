# Ordered signal-category taxonomy. Single source of truth for labels and
# category assignment; consumed by gen_registry.py and signals_tool.py.

CATEGORIES = [
    ("drive", "Drive", "Движение"),
    ("battery", "Battery & charging", "Батарея и зарядка"),
    ("climate", "Climate", "Климат"),
    ("access", "Doors, windows, locks", "Двери, окна, замки"),
    ("lights", "Lights", "Свет"),
    ("tyres", "Tyres & chassis", "Шины и шасси"),
    ("safety", "Safety & ADAS", "Безопасность и ADAS"),
    ("sentry", "Sentry & recording", "Охрана и запись"),
    ("media", "Media & screen", "Мультимедиа и экран"),
    ("location", "Location", "Геолокация"),
    ("device", "Device & system", "Устройство и система"),
    ("phone", "Phone", "Телефон"),
    ("other", "Other", "Прочее"),
]
CATEGORY_KEYS = [k for k, _, _ in CATEGORIES]

_EN = {k: en for k, en, _ in CATEGORIES}
_RU = {k: ru for k, _, ru in CATEGORIES}


def label_en(key):
    return _EN.get(key, "Other")


def label_ru(key):
    return _RU.get(key, "Прочее")


# Explicit category for keys the prefix rules would misplace.
EXPLICIT = {
    "media_volume": "media", "navigation_volume": "media", "system_media_volume": "media",
    "screen_width": "media", "screen_height": "media", "ui_config_version": "media",
    "bluetooth_state": "media", "bluetooth_signal": "media",
    "wifi_state": "media", "wifi_ssid": "media", "wifi_bssid": "media", "wifi_rssi": "media",
    "online": "media",
    "device_battery": "device", "device_pressure": "device", "app_version": "device",
    "vin": "device", "month": "device", "day": "device", "hour": "device", "minute": "device",
    "lane_curvature": "safety", "distance_to_car_ahead": "safety", "lead_car_start_state": "safety",
    "surround_view_state": "safety",
    "lane_keep_state": "drive", "acc_cruise_state": "drive", "cruise_switch": "drive",
    "sentry_state": "sentry", "parked_recording_switch": "sentry", "parked_sentry_alarm": "sentry",
    "last_sentry_trigger_time": "sentry", "last_clip_start_time": "sentry", "last_clip_end_time": "sentry",
    "dashcam_state": "sentry", "ai_person_confidence": "sentry", "ai_vehicle_confidence": "sentry",
    "weather": "access", "rain_amount": "access", "last_wiper_time": "access",
    "front_wiper_speed": "access", "wiper_mode": "access", "mirror_fold": "access",
    "vehicle_locked": "access", "remote_lock_state": "access",
    "doors_state": "access", "doors_all_state": "access", "doors_all_lock": "access",
    "windows_state": "access", "windows_all_state": "access",
    "footwell_light": "lights", "ambient_light_color": "lights", "ambient_light_brightness": "lights",
    "temp_unit": "climate", "pm25": "climate", "clean_air_level": "climate", "rear_defrost": "climate",
    "second": "battery", "fuel_level": "battery", "total_fuel": "battery",
    "fuel_charge_flap": "battery", "charge_port_flap": "battery",
    "power_state": "drive", "ready_lamp": "drive",
}


def category_for(key):
    """Curated category for a signal key (used to seed signals.yaml)."""
    if key in EXPLICIT:
        return EXPLICIT[key]
    if key.startswith("phone_"):
        return "phone"
    if key.startswith("location_"):
        return "location"
    if key.startswith("tyre_"):
        return "tyres"
    if key.startswith("radar_") or key.endswith("_approach_warning"):
        return "safety"
    if key.startswith(("sentry_", "parked_")):
        return "sentry"
    if "seat_heat" in key or "seat_vent" in key or "seat_massage" in key \
            or key.startswith("ac_") or "temp" in key:
        return "climate"
    if key.startswith(("battery_", "cell_", "charge_", "energy_", "traction_battery", "charging_")) \
            or key in {"soc", "range"}:
        return "battery"
    if "seatbelt" in key or "door" in key or "window" in key or "lock" in key \
            or key in {"sunroof", "sunshade", "bonnet", "trunk"}:
        return "access"
    if "light" in key or key in {"low_beam", "high_beam", "front_fog", "rear_fog", "drl",
                                 "hazard", "turn_signal", "left_turn", "right_turn", "sidelights"}:
        return "lights"
    return "drive"
