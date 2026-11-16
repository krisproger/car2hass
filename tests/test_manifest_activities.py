"""Static checks for activities launched by the app."""

import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).parent.parent
MANIFEST = ROOT / "Car2Hass" / "app" / "src" / "main" / "AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}name"


def _activities() -> set[str]:
    tree = ET.parse(MANIFEST)
    return {
        node.get(ANDROID)
        for node in tree.getroot().iter("activity")
        if node.get(ANDROID)
    }


def test_endpoints_activity_is_declared():
    assert ".EndpointsActivity" in _activities()
