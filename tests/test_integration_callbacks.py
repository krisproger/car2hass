"""Static checks for HA dispatcher/registry API correctness (no HA import)."""

from pathlib import Path

ROOT = Path(__file__).parent.parent / "custom_components" / "cartelemetry"


def _decorator_before(source: str, needle: str) -> str:
    idx = source.find(needle)
    assert idx != -1, f"{needle!r} not found"
    lines = source[:idx].rstrip().splitlines()
    return lines[-1].strip() if lines else ""


def test_discover_phone_is_callback():
    """discover_phone runs via the dispatcher; without @callback HA runs it in an
    executor thread and async_add_entities fails with 'loop is not the running loop'."""
    for name in ("sensor.py", "binary_sensor.py"):
        src = (ROOT / name).read_text(encoding="utf-8")
        assert _decorator_before(src, "def discover_phone(") == "@callback", name
        assert "from homeassistant.core import callback" in src, name


def test_device_tracker_uses_identifier_lookup():
    """async_get_device is deprecated in HA 2027.8; use the identifier lookup."""
    src = (ROOT / "device_tracker.py").read_text(encoding="utf-8")
    assert "async_get_device_by_identifier(" in src
    assert "async_get_device({" not in src
