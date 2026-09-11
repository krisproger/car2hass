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
