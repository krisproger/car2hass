"""Basic sanity tests for cartelemetry constants."""

import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT / "custom_components" / "cartelemetry"))

from const import (
    BINARY_ON_MAP,
    BINARY_SENSORS,
    DOMAIN,
    ENUM_SENSORS,
    GEOFENCE_KEY_PREFIX,
    GEOFENCE_NAME_SUFFIX,
    GEOFENCE_ON_VALUES,
    NUMERIC_SENSORS,
)


def test_domain():
    assert DOMAIN == "cartelemetry"


def test_numeric_sensors_have_icons():
    for key, cfg in NUMERIC_SENSORS.items():
        assert "icon" in cfg, f"NUMERIC_SENSORS.{key} missing icon"
        assert "name" in cfg, f"NUMERIC_SENSORS.{key} missing name"


def test_enum_sensors_have_icons():
    for key, cfg in ENUM_SENSORS.items():
        assert "icon" in cfg, f"ENUM_SENSORS.{key} missing icon"
        assert "name" in cfg, f"ENUM_SENSORS.{key} missing name"


def test_binary_sensors_have_device_class():
    for key, cfg in BINARY_SENSORS.items():
        assert "device_class" in cfg, f"BINARY_SENSORS.{key} missing device_class"
        assert "name" in cfg, f"BINARY_SENSORS.{key} missing name"


def test_binary_on_map_covers_all_binary_sensors():
    missing = [key for key in BINARY_SENSORS if key not in BINARY_ON_MAP]
    assert not missing, f"BINARY_ON_MAP missing entries for: {missing}"


def test_default_binary_truthy_values_are_lowercase():
    for value in BINARY_ON_MAP.get("_default_", []):
        assert value == value.lower(), f"Default truthy value not lowercase: {value}"


def test_geofence_constants():
    assert GEOFENCE_KEY_PREFIX == "geo_"
    assert GEOFENCE_NAME_SUFFIX == "_name"
    assert GEOFENCE_ON_VALUES == ["inside"]
