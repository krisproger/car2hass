"""Voyah registry mapping: channels.voyah.vs must reference real VehicleState
parameters from info/apk/Voyah/VOYAH_FIRMWARE_ANALYSIS.md (section 4 table)."""

import json
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "Car2Hass", "app", "src", "main", "assets")
MD = os.path.join(REPO, "info", "apk", "Voyah", "VOYAH_FIRMWARE_ANALYSIS.md")


def _voyah_table_params():
    text = open(MD, encoding="utf-8").read()
    return set(re.findall(r"^\| \d+ \| `([A-Za-z0-9_]+)` \|", text, re.M))


def test_voyah_descriptors_reference_known_params():
    reg = json.load(open(os.path.join(ASSETS, "sensors_registry.json"), encoding="utf-8"))
    known = _voyah_table_params()
    assert known, "Voyah parameter table not parsed from MD"
    mapped = []
    for s in reg["sensors"]:
        v = s["channels"].get("voyah")
        if v:
            mapped.append((s["key"], v["vs"]))
            assert v["vs"] in known, f"{s['key']}: {v['vs']} not in VehicleState table"
    assert mapped, "no voyah mappings generated"


def test_voyah_profile_exists():
    prof = json.load(open(os.path.join(ASSETS, "car_profiles.json"), encoding="utf-8"))
    ids = [p["id"] for p in prof["profiles"]]
    assert "voyah_generic" in ids
    voyah = next(p for p in prof["profiles"] if p["id"] == "voyah_generic")
    assert voyah["base_channels"] == ["voyah"]
    reg = json.load(open(os.path.join(ASSETS, "sensors_registry.json"), encoding="utf-8"))
    keys = {s["key"] for s in reg["sensors"]}
    for k in voyah["expected_sensors"]:
        assert k in keys


def test_obd_pids_match_codec_catalog():
    reg = json.load(open(os.path.join(ASSETS, "sensors_registry.json"), encoding="utf-8"))
    java = open(os.path.join(REPO, "Car2Hass", "app", "src", "main", "java",
                             "com", "car2hass", "vehicle", "ObdPidCodec.java"),
                encoding="utf-8").read()
    catalog = dict(re.findall(r'm\.put\("([0-9A-F]{4})",\s*"([a-z_0-9]+)"\)', java))
    assert catalog, "ObdPidCodec catalog not parsed"
    by_key = {}
    for s in reg["sensors"]:
        o = s["channels"].get("obd")
        if o:
            by_key[s["key"]] = o["pid"]
    for pid, key in catalog.items():
        assert by_key.get(key) == pid, f"registry obd[{key}]={by_key.get(key)} != codec {pid}"
