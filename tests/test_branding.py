import subprocess
import sys
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent


def run(root: Path):
    return subprocess.run(
        [sys.executable, str(ROOT / "scripts" / "check_branding.py"), "--root", str(root)],
        capture_output=True, text=True,
    )


def test_main_repo_branding():
    r = run(ROOT)
    assert r.returncode == 0, r.stderr


def test_public_repo_branding():
    pub = ROOT / "car2hass-public"
    if not pub.is_dir():
        pytest.skip("public repo checkout not present")
    r = run(pub)
    assert r.returncode == 0, r.stderr
