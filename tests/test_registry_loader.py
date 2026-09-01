# tests/test_registry_loader.py
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "scripts"))
from registry_loader import load_sensors, load_commands, load_profiles, sensor_keys

ASSETS = os.path.join(os.path.dirname(__file__), "..",
                      "Car2Hass", "app", "src", "main", "assets")

def test_load_sensors():
    sensors = load_sensors(ASSETS)
    assert "speed" in sensors
    assert sensors["speed"]["type"] in ("num", "enum", "gps")

def test_load_commands():
    commands = load_commands(ASSETS)
    assert "ac_on" in commands
    assert commands["ac_on"]["state_sensor"] == "ac_state"

def test_load_profiles():
    profiles = load_profiles(ASSETS)
    assert "byd_generic" in profiles
    assert set(profiles["byd_generic"]["expected_sensors"]) == sensor_keys(ASSETS)
