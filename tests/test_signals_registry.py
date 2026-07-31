"""Tests that signal registry sources are consistent."""

import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

from signals_tool import _parse_const, _parse_java_registry, _parse_signals_md


def test_signals_yaml_exists_and_has_expected_count():
    yaml_path = ROOT / "signals.yaml"
    assert yaml_path.exists(), "signals.yaml not found"
    data = yaml.safe_load(yaml_path.read_text(encoding="utf-8"))
    assert len(data["signals"]) == 137


def test_java_registry_matches_yaml_keys():
    java_rows = _parse_java_registry()
    yaml_path = ROOT / "signals.yaml"
    signals = yaml.safe_load(yaml_path.read_text(encoding="utf-8"))["signals"]
    # Exclude synthetic keys that have no CAN signal mapping in the Java registry
    yaml_keys = {s["key"] for s in signals if s["key"] and not s.get("synthetic")}
    java_keys = {r["key"] for r in java_rows if r["key"]}
    assert yaml_keys == java_keys, f"Java registry keys differ from signals.yaml"


def test_const_keys_match_yaml_keys():
    const = _parse_const()
    yaml_path = ROOT / "signals.yaml"
    signals = yaml.safe_load(yaml_path.read_text(encoding="utf-8"))["signals"]
    yaml_keys = {s["key"] for s in signals if s["key"]}
    const_keys = set(const["numeric"]) | set(const["enum"]) | set(const["binary"])
    assert yaml_keys == const_keys, f"const.py keys differ from signals.yaml"


def test_signals_md_parses_to_same_count():
    md_rows = _parse_signals_md()
    assert len(md_rows) == 137


def test_binary_sensor_truthy_covers_labels():
    """Every meaningful enum label of a binary sensor must be truthy or explicitly falsy."""
    yaml_path = ROOT / "signals.yaml"
    signals = yaml.safe_load(yaml_path.read_text(encoding="utf-8"))["signals"]
    const = _parse_const()
    binary_on_map = const["binary_on_map"]

    falsy = {
        "off", "closed", "unlocked", "unbuckled", "no warning", "normal", "fresh",
        "stop", "hidden", "disconnected", "cancelled", "invalid", "—", "undefined",
        "inactive", "error", "pending", "state3", "starting", "storage error",
        "done", "aborted", "ready", "disabled", "cancelled/invalid", "closed/off",
        "未定义",
    }

    for s in signals:
        key = s.get("key")
        if s.get("ha_type") != "binary_sensor" or not key:
            continue
        labels = {str(v).lower() for v in s.get("labels", {}).values()}
        truthy = set(binary_on_map.get(key, []))
        for label in labels:
            if label in falsy:
                assert label not in truthy, f"{key}: falsy label '{label}' must not be in truthy"
                continue
            assert label in truthy, (
                f"{key}: enum label '{label}' is not covered by BINARY_ON_MAP "
                f"truthy list {sorted(truthy)}. Add it to signals.yaml truthy or falsy set."
            )
