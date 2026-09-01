"""Version consistency across the app, the integration, the docs and the site.

Single source of truth: ``custom_components/cartelemetry/manifest.json`` (the
Home Assistant integration version). The Android app shares the same version
name; the values in ``build.gradle.kts`` / ``build_apk.sh`` are fallbacks
only — the ``VERSION_NAME`` environment variable wins at build time.

These tests fail as soon as any of the places drifts out of sync, so a
release bump cannot leave a stale version behind.
"""

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "custom_components" / "cartelemetry" / "manifest.json"
APP_DIR = ROOT / "Car2Hass"


def _manifest_version():
    return json.loads(MANIFEST.read_text(encoding="utf-8"))["version"]


def test_const_py_matches_manifest():
    text = (MANIFEST.parent / "const.py").read_text(encoding="utf-8")
    match = re.search(r'INTEGRATION_VERSION\s*=\s*"([^"]+)"', text)
    assert match is not None, "INTEGRATION_VERSION not found in const.py"
    assert match.group(1) == _manifest_version()


def test_gradle_fallback_matches_manifest():
    text = (APP_DIR / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    match = re.search(r'getenv\("VERSION_NAME"\)\s*\?:\s*"([^"]+)"', text)
    assert match is not None, "VERSION_NAME fallback not found in build.gradle.kts"
    assert match.group(1) == _manifest_version()


def test_build_apk_fallback_matches_manifest():
    text = (APP_DIR / "build_apk.sh").read_text(encoding="utf-8")
    match = re.search(r"VERSION_NAME=\$\{VERSION_NAME:-([^}]+)\}", text)
    assert match is not None, "VERSION_NAME fallback not found in build_apk.sh"
    assert match.group(1) == _manifest_version()


def test_readme_latest_release_matches_manifest():
    text = (ROOT / "README.md").read_text(encoding="utf-8")
    match = re.search(r"Latest release — v([0-9][0-9.]*)", text)
    assert match is not None, "Latest release version not found in README.md"
    assert match.group(1) == _manifest_version()


def _current_release_text(text: str, archive_marker: str) -> str:
    """Keep only the part of the document describing the CURRENT release.

    README / site / manual keep a legitimate archive of previous versions
    below a marker — those links must NOT be forced to the current version.
    """
    idx = text.find(archive_marker)
    return text if idx == -1 else text[:idx]


def test_readme_download_links_match_manifest():
    text = _current_release_text(
        (ROOT / "README.md").read_text(encoding="utf-8"),
        "**Archive of previous versions",
    )
    links = set(re.findall(r"(?:car2hass|cartelemetry)-v([0-9][0-9.]*)\.(?:apk|zip)", text))
    assert links == {_manifest_version()}, f"stale download links: {links}"


def test_site_index_matches_manifest():
    text = _current_release_text(
        (ROOT / "docs" / "cartelemetry" / "index.php").read_text(encoding="utf-8"),
        '<details class="archive-block">',
    )
    links = set(re.findall(r"(?:car2hass|cartelemetry)-v([0-9][0-9.]*)\.(?:apk|zip)", text))
    assert links == {_manifest_version()}, f"stale site download links: {links}"


def test_site_download_php_matches_manifest():
    text = (ROOT / "docs" / "cartelemetry" / "download.php").read_text(encoding="utf-8")
    links = set(re.findall(r"(?:car2hass|cartelemetry)-v([0-9][0-9.]*)\.(?:apk|zip)", text))
    assert links == {_manifest_version()}, f"stale download.php entries: {links}"


def test_manual_changelog_current_matches_manifest():
    text = (ROOT / "mkdocs" / "docs" / "changelog.md").read_text(encoding="utf-8")
    match = re.search(r"^## v([0-9][0-9.]*) \(текущая\)", text, re.MULTILINE)
    assert match is not None, "current changelog entry not found"
    assert match.group(1) == _manifest_version()


def test_manual_download_links_match_manifest():
    markers = {"index.md": '??? note "Архив', "quickstart.md": None}
    for page, marker in markers.items():
        text = (ROOT / "mkdocs" / "docs" / page).read_text(encoding="utf-8")
        if marker:
            text = _current_release_text(text, marker)
        links = set(re.findall(r"(?:car2hass|cartelemetry)-v([0-9][0-9.]*)\.(?:apk|zip)", text))
        assert links == {_manifest_version()}, f"stale links in {page}: {links}"


def test_android_manifest_has_no_hardcoded_version():
    """AndroidManifest.xml must not pin a stale versionCode/versionName.

    Both build paths inject the version at build time: Gradle defaultConfig
    (build_counter.txt + VERSION_NAME) and aapt2 --version-code/--version-name
    in build_apk.sh. A hardcoded value here is junk that can only drift.
    """
    text = (APP_DIR / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
    assert "android:versionCode" not in text
    assert "android:versionName" not in text
