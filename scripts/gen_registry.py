# scripts/gen_registry.py
import re, json, os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(REPO, "Car2Hass", "app", "src", "main")
ASSETS = os.path.join(APP, "assets")
JAVA = os.path.join(APP, "java", "com", "car2hass")

SENSOR_LABELS = {"en": {}, "ru": {}}


def _load_sensor_labels():
    for lang, folder in (("en", "values"), ("ru", "values-ru")):
        path = os.path.join(APP, "res", folder, "strings.xml")
        try:
            text = open(path, encoding="utf-8").read()
        except OSError:
            continue
        for key, value in re.findall(
                r'<string name="sensor_([a-z0-9_]+)">(.*?)</string>', text):
            SENSOR_LABELS[lang].setdefault(key, value)


_load_sensor_labels()

GPS_SENSORS = [
    ("location_lat", "Latitude", "gps", None),
    ("location_lon", "Longitude", "gps", None),
    ("location_speed", "GPS speed", "gps", "km/h"),
    ("location_bearing", "Bearing / azimuth", "gps", "deg"),
    ("location_altitude", "Altitude", "gps", "m"),
    ("location_accuracy", "Location accuracy", "gps", "m"),
    ("location_provider", "Location provider", "gps", None),
]

# Android OS device sensors, emitted by the always-on system channel worker
# (SystemWorker). They are device-level, not GPS-related.
DEVICE_SENSORS = [
    ("device_battery", "Device battery level", "device", "%"),
    ("device_pressure", "Barometric pressure", "device", "hPa"),
    ("system_media_volume", "System media volume", "device", "%"),
]

# Locally computed aggregate sensors (refreshDerivedSensors in TelemetryService):
# never read from a channel, always available while the app runs.
DERIVED_SENSORS = [
    ("windows_state", "Windows open count", "num", None),
    ("doors_state", "Doors open count", "num", None),
    ("windows_all_state", "Windows all closed", "enum", None),
    ("doors_all_state", "Doors all closed", "enum", None),
    ("doors_all_lock", "Doors all locked", "enum", None),
]

# Universal sensors read from Voyah (not in the shared CAN SIGNAL_REGISTRY).
VOYAH_ONLY_SENSORS = [
    ("battery_remaining_charge_time", "Battery remaining charge time", "num", "min"),
    ("avg_speed", "Average speed", "num", "km/h"),
    ("avg_power", "Average power", "num", "kW"),
    ("energy_recovery_gear", "Energy recovery gear", "enum", None),
    ("energy_flow", "Energy flow", "enum", None),
    ("charge_voltage", "Onboard charger output voltage", "num", "V"),
    ("charge_current", "Onboard charger charge current", "num", "A"),
    ("ac_charge_state", "AC charging state", "enum", None),
    ("dc_charge_state", "DC charging state", "enum", None),
    ("ac_charge_connected", "AC charging connector status", "enum", None),
    ("dc_charge_connected", "DC charging connector status", "enum", None),
    ("charge_port_flap", "Charging port cap status", "enum", None),
    ("wireless_charge_state", "Wireless phone charger status", "enum", None),
    ("trip_distance", "Trip distance", "num", "km"),
    ("pm25", "Cabin PM2.5 particulate level", "num", "µg/m³"),
    ("clean_air_level", "Air purification level", "enum", None),
    ("trans_oil_temp", "Transmission oil temperature", "num", "°C"),
    ("ambient_light_color", "Ambient light colour", "enum", None),
    ("ambient_light_brightness", "Ambient light brightness", "num", "%"),
    ("ready_lamp", "EV ready indicator", "enum", None),
    ("window_fl_position", "Front-left window position", "num", "%"),
    ("window_fr_position", "Front-right window position", "num", "%"),
    ("window_rl_position", "Rear-left window position", "num", "%"),
    ("window_rr_position", "Rear-right window position", "num", "%"),
    ("vehicle_locked", "Vehicle locked feedback", "enum", None),
    ("driver_seat_massage", "Driver seat massage", "enum", None),
    ("passenger_seat_massage", "Passenger seat massage", "enum", None),
    ("rear_left_seat_massage", "Rear-left seat massage", "enum", None),
    ("rear_right_seat_massage", "Rear-right seat massage", "enum", None),
]

# Sensors supplied outside the CAN SIGNAL_REGISTRY: app metadata injected by
# TelemetryService (app_version, Wi-Fi via WifiManager), the HA-side connectivity
# flag (online), the real OBD VIN (ObdWorker mode 09 PID 02 / UDS F190) and the
# traction (HV) pack measurements of the BYD Cloud source.
EXTRA_SENSORS = [
    # (key, english, type, unit, channel, descriptor)
    ("app_version", "App version", "enum", None, "system", {"field": "app_version"}),
    ("wifi_ssid", "WiFi SSID", "enum", None, "system", {"field": "wifi_ssid"}),
    ("wifi_bssid", "WiFi BSSID", "enum", None, "system", {"field": "wifi_bssid"}),
    ("wifi_rssi", "WiFi RSSI", "num", "dBm", "system", {"field": "wifi_rssi"}),
    ("online", "Online", "enum", None, "system", {"field": "online"}),
    ("vin", "VIN", "enum", None, "obd", {"pid": "0902"}),
    ("traction_battery_voltage", "Traction battery voltage", "num", "V",
     "byd_cloud", {"field": "traction_battery_voltage"}),
    ("traction_battery_current", "Traction battery current", "num", "A",
     "byd_cloud", {"field": "traction_battery_current"}),
]

# Standard OBD-II mode-01 PIDs for keys already present in SIGNAL_REGISTRY.
# Sync with ObdPidCodec.PID_TO_KEY (vehicle/ObdPidCodec.java).
OBD_PIDS = {
    "engine_rpm": "010C",
    "speed": "010D",
    "engine_coolant_temp": "0105",
    "accel_pedal": "0111",
    "engine_load": "0104",
    "intake_air_temp": "010F",
    "maf": "0110",
    "fuel_level": "012F",
    "ambient_temp": "0146",
    "engine_oil_temp": "015C",
    "fuel_rate": "015E",
}

# Sensors readable on any car (system/device + cross-brand), excluded from
# car-profile scoring (see car_profiles.json generic_sensors).
GENERIC_SENSORS = [
    "device_battery", "device_pressure", "system_media_volume",
    "location_lat", "location_lon", "location_speed", "location_bearing",
    "location_altitude", "location_accuracy", "location_provider",
    "screen_width", "screen_height", "ui_config_version", "weather",
    "wifi_state", "bluetooth_state", "bluetooth_signal",
    "month", "day", "hour", "minute", "second",
    "soc", "range", "engine_coolant_temp",
    "app_version", "wifi_ssid", "wifi_bssid", "wifi_rssi", "online",
    "vin", "traction_battery_voltage", "traction_battery_current",
]

# Voyah VehicleState parameter names for existing integration keys only
# (source: info/brands/voyah/VOYAH_FIRMWARE_ANALYSIS.md, section 4).
# Sync with VoyahChannel.VOYAH_PARAMS (vehicle/VoyahChannel.java).
VOYAH_PARAMS = {
    "engine_coolant_temp": "ENG_COOLANT_TEMP",
    "soc": "BMS_SOC_DISPLAY",
    "range": "PDCM_REMAINING_MILEAGE_STANDARD",
    "powertrain_mode": "TravelProgramme",
    "low_beam": "HEAD_LIGHT_STATUS",
    "high_beam": "HIGH_BEAM",
    "drl": "DaytimeRunninglights",
    "front_fog": "FRONT_FOG_LIGHT",
    "rear_fog": "REAR_FOG_LIGHT",
    "brake_pedal": "BRAKE_PEDAL_STATUS",
    "accel_pedal": "ACCEL_PEDAL_POSITION",
    "charging_state": "CHARGE_STATE",
    "charge_gun_state": "CHARGE_GUN_UNLOCK_SET",
    "ac_state": "AC_CLIMATE_SW_REQ",
    "front_wiper_speed": "FRONT_WIPER",
    "rear_left_door": "DOOR_POSITION_STATUS_RL",
    "rear_right_door": "DOOR_POSITION_STATUS_RR",
    "rear_left_door_lock": "DOOR_WORK_STATUS_RL",
    "rear_right_door_lock": "DOOR_WORK_STATUS_RR",
    "driver_seat_heat": "FRONT_SEAT_HEATING_SWITCH_LEFT",
    "passenger_seat_heat": "FRONT_SEAT_HEATING_SWITCH_RIGHT",
    "driver_seat_vent": "FRONT_SEAT_VENTILATION_SWITCH_LEFT",
    "passenger_seat_vent": "FRONT_SEAT_VENTILATION_SWITCH_RIGHT",
    "rear_left_seat_heat": "REAR_SEAT_HEATING_SWITCH_LEFT",
    "rear_right_seat_heat": "REAR_SEAT_HEATING_SWITCH_RIGHT",
    "steering_wheel_heat": "STEERING_WHEEL_HEATING_SWITCH",
    "rear_defrost": "REAR_WINDOWN_HEAT_STATUS",
    "charge_rate": "CHARGE_RATE",
    "mirror_fold": "REAR_MIRROR_FOLD_SET",
    "speed": "GW_ESC_VEHSPD",
    "gear": "TGS_LEVER",
    "driver_seatbelt": "acu_driverSeatBeltSts",
    "passenger_seatbelt": "acu_passengerSeatBeltSts",
    "sunroof": "SUNROOF_OPEN_PERCENT",
    "sunshade": "ROLL_OPEN_PERCENT",
    "window_fl": "DRIVER_WINDOW_CONTROL",
    "window_fr": "PAS_WIDOW_CONTROL",
    "window_rl": "LEFT_BACK_WINDOW_CONTROL",
    "window_rr": "RIGHT_BACK_WINDOW_CONTROL",
    "fuel_charge_flap": "FUEL_PORT_CAP_STS",
    "sidelights": "POSITION_LAMP_SWITCH",
    "left_turn": "LEFT_DIRECTION_LIGHT",
    "right_turn": "RIGHT_DIRECTION_LIGHT",
    "hazard": "WARNING_LIGHT",
    "cruise_switch": "CRUISE_CONTROL",
    "acc_cruise_state": "IACC_OR_ACC_STATUS",
    "lane_keep_state": "LKSStatus",
    "auto_hold": "EPB_PARK_STATUS",
    "total_energy": "ENERGY_CON_SUM_AV",
    "drive_mode": "DRIVING_MODE_SET",
    "battery_remaining_charge_time": "BMS_REMAIN_CHARGE_TIME",
    "avg_speed": "AVG_SPEED",
    "avg_power": "AVG_POWER",
    "energy_recovery_gear": "ENERGY_RECOVERY_GEAR",
    "energy_flow": "ENERGY_FLOW",
    "charge_voltage": "OBC_CHARGE_VOLTAGE",
    "charge_current": "OBC_CHARGE_CURRENT",
    "ac_charge_state": "BMS_AC_CHARGE_STAT",
    "dc_charge_state": "BMS_DC_CHARGE_STAT",
    "ac_charge_connected": "AC_CHARG_CONNECT_STS",
    "dc_charge_connected": "DC_CHARG_CONNECT_STS",
    "charge_port_flap": "CHRG_PORT_CAP_STS",
    "wireless_charge_state": "WCM_CHARGE_STATUS",
    "trip_distance": "ODO_THISTIME",
    "pm25": "PM25_STS",
    "clean_air_level": "CLEAN_AIR_LEVEL",
    "trans_oil_temp": "TRANS_OIL_TEMP",
    "ambient_light_color": "VEHICLE_AMBIENT_LIGHT_COLOR",
    "ambient_light_brightness": "VEHICLE_AMBIENT_LIGHT_BRIGHTNESS_LEVEL",
    "ready_lamp": "READY_LAMP",
    "window_fl_position": "BCM_FLWindowHorizontalSts",
    "window_fr_position": "BCM_FRWindowHorizontalSts",
    "window_rl_position": "BCM_RLWindowHorizontalSts",
    "window_rr_position": "BCM_RRWindowHorizontalSts",
    "vehicle_locked": "VEHICLE_LOCKED_FED",
    "driver_seat_massage": "FRONT_SEAT_MASS_SWITCH_LEFT",
    "passenger_seat_massage": "FRONT_SEAT_MASS_SWITCH_RIGHT",
    "rear_left_seat_massage": "REAR_SEAT_MASS_SWITCH_LEFT",
    "rear_right_seat_massage": "REAR_SEAT_MASS_SWITCH_RIGHT",
}

def parse_signal_registry():
    text = open(os.path.join(JAVA, "CANDataReader.java"), encoding="utf-8").read()
    block = re.search(r"SIGNAL_REGISTRY\s*=\s*\{(.*?)\};", text, re.S).group(1)
    return re.findall(r'\{\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"(num|enum)"\s*\}', block)

def parse_native_signals():
    text = open(os.path.join(JAVA, "NativeSignalMap.java"), encoding="utf-8").read()
    out = {}
    for m in re.finditer(
        r'm\.put\(\s*"([^"]+)"\s*,\s*new FidEntry\(\s*"[^"]+"\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([^,]+?)\s*,\s*([\d.]+)\s*\)\s*\)',
        text):
        key, device, fid, transact, decoder, scale = m.groups()
        out[key] = {"device": int(device), "fid": int(fid),
                    "transact": int(transact), "decoder": decoder.strip(),
                    "scale": float(scale)}
    return out

# Universal core sensors — the open contract every source/app should provide
# when possible; the rest of the registry is source-specific (extended).
CORE_SENSORS = {
    "speed", "soc", "range", "power_state", "charging_state", "engine_rpm", "gear",
    "location_lat", "location_lon", "location_speed", "location_bearing",
    "location_altitude", "location_accuracy", "location_provider", "device_battery",
    "driver_door", "passenger_door", "rear_left_door", "rear_right_door",
    "bonnet", "trunk", "sunroof",
    "window_fl", "window_fr", "window_rl", "window_rr",
    "driver_door_lock", "passenger_door_lock", "rear_left_door_lock",
    "rear_right_door_lock", "remote_lock_state",
    "ac_state", "outside_temp",
}

# Canonical channel priority order (sync with SourceManager/ResearchUiModel).
CHANNELS_PRIORITY = ["system", "dumpsys", "adb", "diplus", "voyah",
                     "obd", "diplus_push", "byd_cloud"]

def build_sensors():
    rows = parse_signal_registry()
    native = parse_native_signals()
    sensors = []
    for chinese, english, key, stype in rows:
        channels = {
            "diplus": {"name": chinese},
            "adb": native.get(key),
            "dumpsys": None,
            "system": None,
            "obd": None,
            "diplus_push": None,
            "byd_cloud": None,
        }
        if key in OBD_PIDS:
            channels["obd"] = {"pid": OBD_PIDS[key]}
        voyah = VOYAH_PARAMS.get(key)
        expected = ["byd_generic"]
        if voyah:
            channels["voyah"] = {"vs": voyah}
            expected.append("voyah_generic")
        sensors.append({
            "key": key, "label_en": SENSOR_LABELS["en"].get(key, english),
            "label_ru": SENSOR_LABELS["ru"].get(key, english),
            "type": stype, "unit": None,
            "core": key in CORE_SENSORS,
            "channels": channels, "expected_on": expected,
        })
    for key, english, stype, unit in GPS_SENSORS + DEVICE_SENSORS + DERIVED_SENSORS:
        sensors.append({
            "key": key, "label_en": SENSOR_LABELS["en"].get(key, english),
            "label_ru": SENSOR_LABELS["ru"].get(key, english),
            "type": stype, "unit": unit,
            "core": key in CORE_SENSORS,
            "channels": {k: None for k in
                ["diplus", "adb", "dumpsys", "obd", "voyah", "diplus_push", "byd_cloud"]}
                | {"system": {"field": key}},
            "expected_on": ["system"],
        })
    for key, english, stype, unit in VOYAH_ONLY_SENSORS:
        vs = VOYAH_PARAMS.get(key)
        sensors.append({
            "key": key, "label_en": SENSOR_LABELS["en"].get(key, english),
            "label_ru": SENSOR_LABELS["ru"].get(key, english),
            "type": stype, "unit": unit,
            "core": False,
            "channels": {k: None for k in
                ["diplus", "adb", "dumpsys", "obd", "diplus_push", "byd_cloud", "system"]}
                | {"voyah": ({"vs": vs} if vs else None)},
            "expected_on": ["voyah_generic"],
        })
    for key, english, stype, unit, channel, descriptor in EXTRA_SENSORS:
        channels = {k: None for k in
            ["diplus", "adb", "dumpsys", "obd", "voyah", "diplus_push", "byd_cloud", "system"]}
        channels[channel] = descriptor
        sensors.append({
            "key": key, "label_en": SENSOR_LABELS["en"].get(key, english),
            "label_ru": SENSOR_LABELS["ru"].get(key, english),
            "type": stype, "unit": unit,
            "core": key in CORE_SENSORS,
            "channels": channels,
            "expected_on": ["system" if channel == "system" else "byd_generic"],
        })
    return sensors

def build_profiles(sensor_keys):
    voyah_keys = sorted(VOYAH_PARAMS.keys())
    return [
        {"id": "byd_generic", "label": "BYD (generic)", "key_channel": "diplus",
         "expected_sensors": list(sensor_keys), "base_channels": ["diplus", "adb"]},
        {"id": "song_pro_2022", "label": "BYD Song Pro 2022", "key_channel": "diplus",
         "expected_sensors": list(sensor_keys), "base_channels": ["diplus", "adb"]},
        {"id": "voyah_generic", "label": "Voyah (read-only)", "key_channel": "voyah",
         "expected_sensors": voyah_keys, "base_channels": ["voyah"]},
    ]

def build_commands():
    nc = json.load(open(os.path.join(ASSETS, "native_commands.json"), encoding="utf-8"))
    scm = json.load(open(os.path.join(ASSETS, "sensor_command_map.json"), encoding="utf-8"))
    cmd_to_sensor = {}
    for m in scm.get("mappings", []):
        sk = m["sensor_key"]
        for c in m["commands"]:
            cmd_to_sensor[c["command_id"]] = sk
    NATIVE_KEYS = ("dev", "fid", "value", "valueMap", "valueExpr", "min", "max", "verify")

    def norm_native(n):
        if isinstance(n, list):
            return [{k: e.get(k) for k in NATIVE_KEYS} for e in n]
        if isinstance(n, dict):
            return {k: n.get(k) for k in NATIVE_KEYS}
        return None

    def native_has_param(n):
        entries = n if isinstance(n, list) else ([n] if isinstance(n, dict) else [])
        return any(("valueExpr" in e or "min" in e) for e in entries)

    commands = []
    for cid, spec in nc.get("commands", {}).items():
        native = spec.get("native")
        diplus = spec.get("diplus")
        param = None
        if native_has_param(native):
            entries = native if isinstance(native, list) else ([native] if isinstance(native, dict) else [])
            mins = [e.get("min") for e in entries if e.get("min") is not None]
            maxs = [e.get("max") for e in entries if e.get("max") is not None]
            param = {"min": min(mins) if mins else None,
                     "max": max(maxs) if maxs else None, "step": 1}
        channels = {"diplus": None, "adb": None}
        if diplus and diplus.get("chinese"):
            channels["diplus"] = {"command": diplus["chinese"]}
        if native:
            channels["adb"] = norm_native(native)
        commands.append({
            "id": cid,
            "label_en": cid,
            "label_ru": cid,
            "state_sensor": cmd_to_sensor.get(cid),
            "param": param,
            "channels": channels,
        })
    return commands

def main():
    sensors = build_sensors()
    keys = [s["key"] for s in sensors]
    out_s = {"version": 1, "channels_priority": CHANNELS_PRIORITY, "sensors": sensors}
    out_p = {"version": 2, "generic_sensors": GENERIC_SENSORS, "profiles": build_profiles(keys)}
    out_c = {"version": 1, "commands": build_commands()}
    with open(os.path.join(ASSETS, "sensors_registry.json"), "w", encoding="utf-8") as f:
        json.dump(out_s, f, ensure_ascii=False, indent=2)
    with open(os.path.join(ASSETS, "car_profiles.json"), "w", encoding="utf-8") as f:
        json.dump(out_p, f, ensure_ascii=False, indent=2)
    with open(os.path.join(ASSETS, "commands_registry.json"), "w", encoding="utf-8") as f:
        json.dump(out_c, f, ensure_ascii=False, indent=2)
    print(f"sensors={len(sensors)} profiles={len(out_p['profiles'])} commands={len(out_c['commands'])}")

if __name__ == "__main__":
    main()
