#!/bin/bash
# Build the MkDocs manual into docs/cartelemetry/manual/
set -euo pipefail
cd "$(dirname "$0")"
../.venv/bin/mkdocs build "$@"
echo "Manual built into $(cd ../docs/cartelemetry/manual && pwd)"
