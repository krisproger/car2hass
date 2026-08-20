"""Static checks for the new HA platforms (climate, lock, alarm).

Home Assistant is not installed in the dev environment, so these tests parse
the platform sources with `ast` and verify the required structure exists.
"""

import ast
from pathlib import Path

PLATFORM_DIR = (
    Path(__file__).resolve().parent.parent
    / "custom_components" / "diplus2hass"
)


def _method_names(path):
    tree = ast.parse(path.read_text(encoding="utf-8"))
    return {node.name for node in ast.walk(tree)
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))}


def _init_source():
    return (PLATFORM_DIR / "__init__.py").read_text(encoding="utf-8")


def test_lock_platform_structure():
    names = _method_names(PLATFORM_DIR / "lock.py")
    assert "async_setup_entry" in names
    assert "async_lock" in names
    assert "async_unlock" in names
    assert "Platform.LOCK" in _init_source()


def test_climate_platform_structure():
    names = _method_names(PLATFORM_DIR / "climate.py")
    assert "async_setup_entry" in names
    assert "async_set_hvac_mode" in names
    assert "async_set_temperature" in names
    assert "async_set_fan_mode" in names
    assert "Platform.CLIMATE" in _init_source()


def test_alarm_platform_structure():
    names = _method_names(PLATFORM_DIR / "alarm_control_panel.py")
    assert "async_setup_entry" in names
    assert "async_alarm_arm_away" in names
    assert "async_alarm_disarm" in names
    assert "Platform.ALARM_CONTROL_PANEL" in _init_source()


def test_binary_sensor_initializes_extra_state_attributes():
    """DiplusBinarySensor must not read _attr_extra_state_attributes before it
    is set — the pre-fix code crashed with AttributeError on every dispatch
    when last_seen was present (errors.log from УД, 2026-07-31)."""
    src = (PLATFORM_DIR / "binary_sensor.py").read_text(encoding="utf-8")
    # __init__ must seed the dict (mirrors sensor.py)...
    assert "self._attr_extra_state_attributes = {}" in src
    # ...and the last_seen merge must not unpack an unset attribute.
    assert 'getattr(self, "_attr_extra_state_attributes", {})' in src


def test_binary_sensor_creates_all_binary_sensors_statically():
    """Every BINARY_SENSORS entry must be created at setup (no lazy/invalid
    skipping) — T9 made lock/seatbelt entities disappear from HA because the
    car reports 'invalid' for them. Regression guard for the revert."""
    src = (PLATFORM_DIR / "binary_sensor.py").read_text(encoding="utf-8")
    assert "INVALID_AWARE_SIGNALS" not in src
    assert "discover_lazy" not in src
    # Static loop appends every sensor without filtering.
    assert "for signal_key, cfg in BINARY_SENSORS.items():" in src


def test_binary_sensor_invalid_means_unavailable():
    """'invalid' (无效 = no data) must be handled in update() as unavailable
    rather than mapped to OFF (which looked like an open lock / unbuckled
    belt)."""
    src = (PLATFORM_DIR / "binary_sensor.py").read_text(encoding="utf-8")
    assert 'raw_lower == "invalid"' in src
    assert "self._attr_available = False" in src
