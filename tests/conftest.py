"""Shared test configuration: make the integration modules importable.

`custom_components/diplus2hass/__init__.py` imports Home Assistant, which is
not installed in the dev environment, so tests import the pure submodules
(const, core, commands) directly from the integration directory instead of
through the package.
"""

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "custom_components" / "cartelemetry"))
sys.path.insert(0, str(ROOT))
