# scripts/registry_loader.py
import json, os

def _path(assets, name):
    return os.path.join(assets, name)

def load_sensors(assets="Car2Hass/app/src/main/assets"):
    with open(_path(assets, "sensors_registry.json"), encoding="utf-8") as f:
        return {s["key"]: s for s in json.load(f)["sensors"]}

def load_commands(assets="Car2Hass/app/src/main/assets"):
    with open(_path(assets, "commands_registry.json"), encoding="utf-8") as f:
        return {c["id"]: c for c in json.load(f)["commands"]}

def load_profiles(assets="Car2Hass/app/src/main/assets"):
    with open(_path(assets, "car_profiles.json"), encoding="utf-8") as f:
        return {p["id"]: p for p in json.load(f)["profiles"]}

def sensor_keys(assets="Car2Hass/app/src/main/assets"):
    return set(load_sensors(assets).keys())
