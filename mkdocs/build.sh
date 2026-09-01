#!/bin/bash
# Build the MkDocs manual into docs/cartelemetry/manual/
set -euo pipefail
cd "$(dirname "$0")"
# Local dev uses the project venv; CI installs mkdocs into the runner PATH.
if [ -x ../.venv/bin/mkdocs ]; then
    ../.venv/bin/mkdocs build "$@"
else
    mkdocs build "$@"
fi
echo "Manual built into $(cd ../docs/cartelemetry/manual && pwd)"
