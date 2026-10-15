"""Static checks for the HA integration REST views (no homeassistant import)."""

import ast
from pathlib import Path

ROOT = Path(__file__).parent.parent
INIT = ROOT / "custom_components" / "cartelemetry" / "__init__.py"


def _class_methods(source: str, class_name: str) -> set[str]:
    tree = ast.parse(source)
    for node in ast.walk(tree):
        if isinstance(node, ast.ClassDef) and node.name == class_name:
            return {
                n.name
                for n in node.body
                if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef))
            }
    return set()


def test_commands_view_has_get_and_post():
    """/api/cartelemetry/commands must expose GET (poll) and POST (ack)."""
    source = INIT.read_text(encoding="utf-8")
    methods = _class_methods(source, "VehicleCommandsView")
    assert {"get", "post"} <= methods, f"VehicleCommandsView missing handlers: {methods}"


def test_legacy_commands_view_is_alias_only():
    """Legacy /api/byd_diplus/commands must not duplicate handlers (inherits them)."""
    source = INIT.read_text(encoding="utf-8")
    methods = _class_methods(source, "VehicleCommandsLegacyView")
    assert methods == set(), f"VehicleCommandsLegacyView should only override url/name: {methods}"


def test_data_view_has_post():
    source = INIT.read_text(encoding="utf-8")
    methods = _class_methods(source, "VehicleDataView")
    assert "post" in methods, f"VehicleDataView missing post: {methods}"


def test_info_view_has_get():
    """/api/cartelemetry/info must expose GET (version handshake)."""
    source = INIT.read_text(encoding="utf-8")
    methods = _class_methods(source, "VehicleInfoView")
    assert "get" in methods, f"VehicleInfoView missing get: {methods}"


INTEGRATION = ROOT / "custom_components" / "cartelemetry"
SENSOR = INTEGRATION / "sensor.py"
BINARY = INTEGRATION / "binary_sensor.py"


def test_payload_parsing_runs_in_executor():
    """Batch JSON parse+validation must be off the HA event loop."""
    source = INIT.read_text(encoding="utf-8")
    assert "def _parse_payload(" in source
    assert "async_add_executor_job(_parse_payload" in source
    assert "body = await request.read()" in source


def test_availability_sync_is_delta_gated():
    """The registry availability scan is skipped when the signature is unchanged."""
    source = INIT.read_text(encoding="utf-8")
    assert "_avail_cache" in source
    assert "availability_signature" in source


def test_replay_writes_are_throttled():
    """Entities must acquire the shared per-car throttle before replay writes."""
    for path in (SENSOR, BINARY, INTEGRATION / "device_tracker.py"):
        source = path.read_text(encoding="utf-8")
        assert 'store.get("replay")' in source, path.name
        assert "throttle.acquire()" in source, path.name


def test_available_uses_cached_last_seen():
    """available must not parse last_seen on every read."""
    for path in (SENSOR, BINARY):
        source = path.read_text(encoding="utf-8")
        assert "_last_seen_dt" in source, path.name
        assert "parse_datetime" not in source.split("def available", 1)[1].split("def ", 1)[0], path.name


def test_dispatch_is_debounced_and_cancelled():
    """Fan-out is coalesced via _schedule_data_dispatch and cancelled on unload."""
    source = INIT.read_text(encoding="utf-8")
    assert "def _schedule_data_dispatch(" in source
    assert "hass.loop.call_later(" in source
    assert "_dispatch_cancel()" in source or "cancel()" in source


def test_command_entities_skip_noop_writes():
    """Command-backed entities write state only when something changed."""
    for name in ("switch.py", "number.py", "select.py", "button.py",
                 "lock.py", "climate.py", "alarm_control_panel.py"):
        source = (INTEGRATION / name).read_text(encoding="utf-8")
        assert "before !=" in source, name
