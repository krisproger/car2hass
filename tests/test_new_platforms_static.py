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
