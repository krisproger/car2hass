import importlib.util
import pathlib

spec = importlib.util.spec_from_file_location(
    "prune_versions",
    pathlib.Path(__file__).resolve().parents[1] / "scripts" / "prune_versions.py",
)
pv = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pv)


def test_channel_of():
    assert pv.channel_of("car2hass-v3.5.4-beta.5.apk") == "beta"
    assert pv.channel_of("cartelemetry-v3.5.3.zip") == "stable"


def test_select_keeps_two_per_channel_per_family():
    files = [
        "car2hass-v3.5.4-beta.5.apk", "car2hass-v3.5.4-beta.4.apk",
        "car2hass-v3.5.4-beta.3.apk", "car2hass-v3.5.3.apk",
        "car2hass-v3.5.2.apk", "car2hass-v3.5.1.apk",
        "cartelemetry-v3.5.4-beta.5.zip", "cartelemetry-v3.5.4-beta.4.zip",
        "cartelemetry-v3.5.4-beta.3.zip",
    ]
    keep, delete = pv.select(files, 2)
    assert set(keep) == {
        "car2hass-v3.5.4-beta.5.apk", "car2hass-v3.5.4-beta.4.apk",
        "car2hass-v3.5.3.apk", "car2hass-v3.5.2.apk",
        "cartelemetry-v3.5.4-beta.5.zip", "cartelemetry-v3.5.4-beta.4.zip",
    }
    assert "car2hass-v3.5.4-beta.3.apk" in delete
    assert "car2hass-v3.5.1.apk" in delete
    assert "cartelemetry-v3.5.4-beta.3.zip" in delete


def test_select_keeps_newest_by_version():
    keep, _ = pv.select(["car2hass-v3.5.9.apk", "car2hass-v3.5.10.apk"], 1)
    assert keep == ["car2hass-v3.5.10.apk"]


def test_parse_artifacts():
    listing = "diplus.1.3.8-beta18.apk\ncar2hass-v3.5.3.apk\nindex.php\nfoo.zip\nREADME\n"
    assert pv.parse_artifacts(listing) == ["car2hass-v3.5.3.apk", "diplus.1.3.8-beta18.apk", "foo.zip"]



def test_trim_registry_keeps_two_newest():
    text = (
        "<?php\n$RELEASES = [\n"
        "    '3.5.3' => [\n        'apk' => 'a',\n    ],\n"
        "    '3.5.2' => [\n        'apk' => 'b',\n    ],\n"
        "    '3.5.1' => [\n        'apk' => 'c',\n    ],\n"
        "];\n"
    )
    out, dropped = pv.trim_registry(text, 2)
    assert dropped == 1
    assert "'3.5.3'" in out and "'3.5.2'" in out and "'3.5.1'" not in out


def test_trim_registry_handles_both_channels():
    text = (
        "$RELEASES = [\n    '3.5.3' => [\n        'x',\n    ],\n];\n"
        "$BETA_RELEASES = [\n"
        "    '3.5.4-beta.5' => [\n        'x',\n    ],\n"
        "    '3.5.4-beta.4' => [\n        'x',\n    ],\n"
        "    '3.5.4-beta.3' => [\n        'x',\n    ],\n"
        "];\n"
    )
    out, dropped = pv.trim_registry(text, 2)
    assert dropped == 1
    assert "'3.5.4-beta.5'" in out and "'3.5.4-beta.4'" in out
    assert "'3.5.4-beta.3'" not in out

