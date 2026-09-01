# tests/test_registry_schema.py
import os, sys, json, re
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "scripts"))
from registry_loader import load_sensors, load_commands, load_profiles, sensor_keys

ASSETS = os.path.join(os.path.dirname(__file__), "..",
                      "Car2Hass", "app", "src", "main", "assets")

def test_unique_sensor_keys():
    sensors = load_sensors(ASSETS)
    assert len(sensors) == len({s["key"] for s in sensors.values()})

def test_unique_command_ids():
    commands = load_commands(ASSETS)
    assert len(commands) == len({c["id"] for c in commands.values()})

def test_state_sensor_references_exist():
    sensors = load_sensors(ASSETS)
    commands = load_commands(ASSETS)
    for cid, c in commands.items():
        ss = c.get("state_sensor")
        assert ss is None or ss in sensors, f"{cid}: state_sensor {ss} missing"

def test_every_sensor_has_at_least_one_channel():
    sensors = load_sensors(ASSETS)
    for k, s in sensors.items():
        ch = s.get("channels", {})
        assert any(v is not None for v in ch.values()), f"{k}: no channel"

def test_profile_sensors_exist():
    sensors = load_sensors(ASSETS)
    profiles = load_profiles(ASSETS)
    for pid, p in profiles.items():
        for sk in p.get("expected_sensors", []):
            assert sk in sensors, f"profile {pid}: sensor {sk} missing"

def test_consistency_with_signal_registry():
    repo = os.path.dirname(os.path.dirname(__file__))
    src = os.path.join(repo, "Car2Hass", "app", "src", "main", "java",
                       "com", "car2hass", "CANDataReader.java")
    text = open(src, encoding="utf-8").read()
    block = re.search(r"SIGNAL_REGISTRY\s*=\s*\{(.*?)\};", text, re.S).group(1)
    keys = set(re.findall(r'\{\s*"[^"]+"\s*,\s*"[^"]+"\s*,\s*"([^"]+)"\s*,', block))
    missing = keys - sensor_keys(ASSETS)
    assert not missing, f"SIGNAL_REGISTRY keys missing from registry: {missing}"

def test_system_sensors_present():
    sensors = load_sensors(ASSETS)
    for k in ("location_lat", "location_lon", "location_speed",
              "location_bearing", "location_altitude"):
        assert k in sensors, f"{k} missing"
        assert sensors[k]["channels"].get("system") is not None
        assert sensors[k]["expected_on"] == ["system"]

def test_channels_priority_matches_union():
    root = json.load(open(os.path.join(ASSETS, "sensors_registry.json"), encoding="utf-8"))
    prio = root.get("channels_priority")
    assert isinstance(prio, list) and len(prio) == len(set(prio))
    union = set()
    for s in root["sensors"]:
        union |= set(s.get("channels", {}).keys())
    assert set(prio) == union, f"channels_priority {set(prio)} != union {union}"


def test_core_sensors_subset():
    root = json.load(open(os.path.join(ASSETS, "sensors_registry.json"), encoding="utf-8"))
    core = [s["key"] for s in root["sensors"] if s.get("core")]
    assert len(core) >= 25, f"core too small: {len(core)}"
    assert {"speed", "soc", "power_state", "location_lat"} <= set(core)
