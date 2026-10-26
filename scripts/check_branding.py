#!/usr/bin/env python3
"""Fail when the retired project name appears outside the allowlist.

The project was renamed DiPlus-to-hass -> Car2Hass / CARTelemetry in 3.0.
Historical migration/changelog notes and archived research may keep the old
name; everything current (README, license, UI, tests, site) must not.
"""

from __future__ import annotations

import argparse
import os
import re
import sys

PATTERN = re.compile(r"diplus[-_ ]?to[-_ ]?hass|diplustohass", re.I)

SKIP_DIRS = {
    ".git", ".gradle", "build", "node_modules", ".venv", "__pycache__",
    ".idea", ".pytest_cache", "dist", "captures", "car2hass-public",
}

# Intentional historical references (migration guide, changelog, archives).
ALLOW_PREFIXES = (
    "archive/",
    "info/old/",
    "info/ANALYSIS_DIPlus_BYDConnect.md",
    "info/BYDMate_HA_integration_plan.md",
    "BYDMate/",
    "docs/superpowers/",
    "Car2Hass/docs/superpowers/",
    ".superpowers/",
    "mkdocs/docs/advanced/migration-3-0.md",
    "mkdocs/docs/changelog.md",
    "CHANGELOG.md",
    # The checker and its test contain the pattern by design.
    "scripts/check_branding.py",
    "tests/test_branding.py",
    # Generated MkDocs copies of the pages above.
    "docs/cartelemetry/manual/advanced/migration-3-0/",
    "docs/cartelemetry/manual/changelog/",
    "docs/cartelemetry/manual/search/search_index.json",
)

TEXT_EXT = {
    ".md", ".txt", ".html", ".htm", ".php", ".js", ".json", ".css", ".yml",
    ".yaml", ".py", ".java", ".kt", ".kts", ".xml", ".gradle", ".sh", ".bat",
    ".properties", ".gitignore", ".conf",
}


def allowed(rel: str) -> bool:
    return any(rel == p or rel.startswith(p) for p in ALLOW_PREFIXES)


def scan(root: str) -> list[str]:
    hits: list[str] = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in filenames:
            path = os.path.join(dirpath, name)
            rel = os.path.relpath(path, root).replace(os.sep, "/")
            if allowed(rel):
                continue
            ext = os.path.splitext(name)[1].lower()
            if ext not in TEXT_EXT and name not in {".gitignore", "LICENSE"}:
                continue
            try:
                if os.path.getsize(path) > 2_000_000:
                    continue
                with open(path, encoding="utf-8", errors="ignore") as f:
                    for no, line in enumerate(f, 1):
                        if PATTERN.search(line):
                            hits.append(f"{rel}:{no}: {line.strip()[:200]}")
            except OSError:
                continue
    return hits


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=".", help="repository root to scan")
    args = ap.parse_args()
    hits = scan(os.path.abspath(args.root))
    if hits:
        print("Retired project name found outside the allowlist:", file=sys.stderr)
        for h in hits:
            print("  " + h, file=sys.stderr)
        return 1
    print(f"OK: no retired branding in {args.root}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
