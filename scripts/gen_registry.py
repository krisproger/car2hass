# scripts/gen_registry.py
import re, json, os

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP = os.path.join(REPO, "Car2Hass", "app", "src", "main")
ASSETS = os.path.join(APP, "assets")
JAVA = os.path.join(APP, "java", "com", "car2hass")

GPS_SENSORS = [
    ("location_lat", "Latitude", "gps", None),
    ("location_lon", "Longitude", "gps", None),
    ("location_speed", "GPS speed", "gps", "km/h"),
    ("location_bearing", "Bearing / azimuth", "gps", "deg"),
    ("location_altitude", "Altitude", "gps", "m"),
    ("location_accuracy", "Location accuracy", "gps", "m"),
    ("location_provider", "Location provider", "gps", None),
    ("device_battery", "Device battery level", "gps", "%"),
    ("device_pressure", "Barometric pressure", "gps", "hPa"),
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
    "device_battery", "device_pressure",
    "location_lat", "location_lon", "location_speed", "location_bearing",
    "location_altitude", "location_accuracy", "location_provider",
    "screen_width", "screen_height", "ui_config_version", "weather",
    "wifi_state", "bluetooth_state", "bluetooth_signal",
    "month", "day", "hour", "minute", "second",
    "soc", "range", "engine_coolant_temp",
]

# Voyah VehicleState parameter names for existing integration keys only
# (source: info/apk/Voyah/VOYAH_FIRMWARE_ANALYSIS.md, section 4).
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
CHANNELS_PRIORITY = ["diplus", "adb", "dumpsys", "system",
                     "obd", "diplus_push", "byd_cloud", "voyah"]

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
            "key": key, "label_en": english, "label_ru": english,
            "type": stype, "unit": None,
            "core": key in CORE_SENSORS,
            "channels": channels, "expected_on": expected,
        })
    for key, english, stype, unit in GPS_SENSORS:
        sensors.append({
            "key": key, "label_en": english, "label_ru": english,
            "type": stype, "unit": unit,
            "core": key in CORE_SENSORS,
            "channels": {k: None for k in
                ["diplus", "adb", "dumpsys", "obd", "voyah", "diplus_push", "byd_cloud"]}
                | {"system": {"field": key}},
            "expected_on": ["system"],
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
