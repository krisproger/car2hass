"""Voyah registry mapping: channels.voyah.vs must reference real VehicleState
parameters from info/brands/voyah/VOYAH_FIRMWARE_ANALYSIS.md (section 4 table)."""

import json
import os
import re

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(REPO, "Car2Hass", "app", "src", "main", "assets")
MD = os.path.join(REPO, "info", "brands", "voyah", "VOYAH_FIRMWARE_ANALYSIS.md")


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
        if not v:
            continue
        if "vs" in v:
            mapped.append((s["key"], v["vs"]))
            assert v["vs"] in known, f"{s['key']}: {v['vs']} not in VehicleState table"
        else:
            mapped.append((s["key"], v.get("method") or v.get("ac")))
            assert v.get("method") or v.get("ac"), f"{s['key']}: empty voyah descriptor"
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


def _gen_registry_params():
    text = open(os.path.join(REPO, "scripts", "gen_registry.py"), encoding="utf-8").read()
    block = re.search(r"VOYAH_PARAMS = \{(.*?)\n\}", text, re.S).group(1)
    return dict(re.findall(r'"([^"]+)":\s*"([^"]+)"', block))


def _java_channel_params():
    text = open(os.path.join(REPO, "Car2Hass", "app", "src", "main", "java",
                             "com", "car2hass", "vehicle", "VoyahChannel.java"),
                encoding="utf-8").read()
    block = re.search(r"Map<String, String> m = new HashMap<>\(\);(.*?)VOYAH_PARAMS =",
                      text, re.S).group(1)
    return dict(re.findall(r'm\.put\("([^"]+)",\s*"([^"]+)"\)', block))


def test_voyah_maps_are_in_sync():
    """gen_registry.py and VoyahChannel.java must expose the same key -> param map."""
    gen = _gen_registry_params()
    java = _java_channel_params()
    assert gen == java, (
        f"only in gen_registry: {sorted(set(gen) - set(java))}; "
        f"only in VoyahChannel: {sorted(set(java) - set(gen))}; "
        f"differing: {sorted(k for k in set(gen) & set(java) if gen[k] != java[k])}"
    )


def _gen_registry_map(var_name):
    text = open(os.path.join(REPO, "scripts", "gen_registry.py"), encoding="utf-8").read()
    block = re.search(var_name + r" = \{(.*?)\n\}", text, re.S).group(1)
    return dict(re.findall(r'"([^"]+)":\s*"([^"]+)"', block))


def _java_channel_map(var_name):
    text = open(os.path.join(REPO, "Car2Hass", "app", "src", "main", "java",
                             "com", "car2hass", "vehicle", "VoyahChannel.java"),
                encoding="utf-8").read()
    before = text[:text.index(var_name + " =")]
    start = before.rindex("Map<String, String> m = new HashMap<>();")
    block = before[start:]
    return dict(re.findall(r'm\.put\("([^"]+)",\s*"([^"]+)"\)', block))


def test_voyah_method_maps_are_in_sync():
    """gen_registry.py and VoyahChannel.java must agree on method/AC getters."""
    assert _gen_registry_map("VOYAH_METHOD_PARAMS") == _java_channel_map("VOYAH_METHODS")
    assert _gen_registry_map("VOYAH_AC_PARAMS") == _java_channel_map("VOYAH_AC_METHODS")


def test_voyah_temperature_descriptors():
    """outside_temp via the no-arg ICanBusService getter, cabin_temp via AirCondition."""
    reg = json.load(open(os.path.join(ASSETS, "sensors_registry.json"), encoding="utf-8"))
    by_key = {s["key"]: s for s in reg["sensors"]}
    assert by_key["outside_temp"]["channels"]["voyah"] == {"method": "getAmbientTemperature"}
    assert by_key["cabin_temp"]["channels"]["voyah"] == {"ac": "getAirTempInCar"}
    assert "voyah_generic" in by_key["outside_temp"]["expected_on"]
    assert "voyah_generic" in by_key["cabin_temp"]["expected_on"]


def test_voyah_corrected_mappings():
    """The issue #84 corrections must stay in place (status params, not control)."""
    gen = _gen_registry_params()
    assert gen["drive_mode"] == "BCM_DRIVEMODE_CHANGE"
    assert gen["window_fl"] == "BCM_FLWindowHorizontalSts"
    assert gen["window_fr"] == "BCM_FRWindowHorizontalSts"
    assert gen["window_rl"] == "BCM_RLWindowHorizontalSts"
    assert gen["window_rr"] == "BCM_RRWindowHorizontalSts"
    assert gen["fuel_charge_flap"] == "FUEL_PORT_CAP_STS"
    assert gen["charge_port_flap"] == "CHRG_PORT_CAP_STS"
    assert gen["rear_left_door"] == "DOOR_POSITION_STATUS_RL"
    assert gen["rear_right_door"] == "DOOR_POSITION_STATUS_RR"


def test_charge_port_flap_has_labels():
    """charge_port_flap must decode to open/closed (was labels: {} -> raw number)."""
    reg = json.load(open(os.path.join(ASSETS, "sensors_registry.json"), encoding="utf-8"))
    flap = next(s for s in reg["sensors"] if s["key"] == "charge_port_flap")
    assert flap["channels"]["voyah"] == {"vs": "CHRG_PORT_CAP_STS"}


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
