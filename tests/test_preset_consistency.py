"""Dashboard preset consistency checks.

The on-phone "Charge port" tile and the HA binary sensor for charging_state
both rely on truthy lists that must cover the real translated values the car
produces. This test derives the possible values from signals.yaml (the single
source of truth) and asserts the presets and the generated BINARY_ON_MAP still
cover them, so a value like "DC gun" or "Ready" can never silently fall out of
a truthy list again.

Also guards the preset catalogue against duplicate ids and keeps the bundled
asset, the OTA file on the site and the meta version in sync.
"""

import ast
import json
import re
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
PRESETS = ROOT / "DiPlus-to-hass" / "app" / "src" / "main" / "assets" / "dashboard_presets.json"
SITE_PRESETS = ROOT / "docs" / "diplus2hass" / "dashboard_presets.json"
SITE_META = ROOT / "docs" / "diplus2hass" / "dashboard_presets.meta.json"
SIGNALS = ROOT / "signals.yaml"
CONST = ROOT / "custom_components" / "diplus2hass" / "const.py"


def _load_signals():
    return yaml.safe_load(SIGNALS.read_text(encoding="utf-8"))["signals"]


def _signal_labels(signals, key):
    for s in signals:
        if s.get("key") == key:
            return s.get("labels") or {}
    raise KeyError(key)


def _preset(doc, preset_id):
    for p in doc["presets"]:
        if p["id"] == preset_id:
            return p
    raise KeyError(preset_id)


def _binary_on_map():
    text = CONST.read_text(encoding="utf-8")
    match = re.search(r"BINARY_ON_MAP[^\n]*=\s*(\{.*?\})", text, re.S)
    assert match is not None, "BINARY_ON_MAP literal not found in const.py"
    return ast.literal_eval(match.group(1))


def test_charge_gun_preset_covers_plugged_values():
    doc = json.loads(PRESETS.read_text(encoding="utf-8"))
    preset = _preset(doc, "charge_gun")
    truthy = {v.lower() for v in preset["state"]["truthy"]}
    signals = _load_signals()

    gun = _signal_labels(signals, "charge_gun_state")
    plugged = {gun[k].lower() for k in gun if str(k) not in ("0", "1")}
    assert plugged <= truthy, (
        f"charge_gun preset truthy is missing plugged charge_gun_state values: "
        f"{plugged - truthy}"
    )

    # charge_gun preset no longer includes charging_state — it only uses
    # charge_gun_state to detect whether the gun is physically plugged in.
    # charging_state values (Ready, started, done) are intentionally excluded
    # because they trigger even when no gun is present (e.g. car ON, Ready).
    sensors = preset.get("sensors", [])
    assert "charging_state" not in sensors, (
        "charge_gun preset should not include charging_state (causes false positives)"
    )


def test_ha_charging_state_binary_map_covers_real_values():
    """While actively charging the car reports Ready (1) or started (2), so both
    must be truthy for the battery_charging sensor. 'done' (3) means charging has
    finished and is intentionally NOT truthy (sensor goes OFF when no longer
    charging)."""
    signals = _load_signals()
    charging = _signal_labels(signals, "charging_state")
    active = {charging[k] for k in charging if str(k) in ("1", "2")}
    on_map = _binary_on_map()["charging_state"]
    assert active <= set(on_map), (
        f"BINARY_ON_MAP['charging_state'] misses real values: {active - set(on_map)}"
    )


def test_no_duplicate_preset_ids():
    doc = json.loads(PRESETS.read_text(encoding="utf-8"))
    ids = [p["id"] for p in doc["presets"]]
    assert len(ids) == len(set(ids)), f"duplicate preset ids: {ids}"
    assert "close_windows" not in ids, "close_windows is a duplicate of windows"
    assert "child_lock_left" not in ids and "child_lock_right" not in ids, (
        "child_lock_left/right are merged into child_locks"
    )
    assert "child_locks" in ids, "merged child_locks preset is missing"


def test_bundled_asset_matches_site_and_meta_version():
    asset = json.loads(PRESETS.read_text(encoding="utf-8"))
    site = json.loads(SITE_PRESETS.read_text(encoding="utf-8"))
    assert asset == site, "bundled presets and the site OTA file have drifted"
    meta = json.loads(SITE_META.read_text(encoding="utf-8"))
    assert meta["version"] == asset["version"], "preset meta version out of sync"
