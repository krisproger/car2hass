#!/bin/bash
# Build the MkDocs manual into docs/diplus2hass/manual/
set -euo pipefail
cd "$(dirname "$0")"
../.venv/bin/mkdocs build "$@"
echo "Manual built into $(cd ../docs/diplus2hass/manual && pwd)"
