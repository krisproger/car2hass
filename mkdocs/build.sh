#!/bin/bash
# Build the MkDocs manual into docs/cartelemetry/manual/
set -euo pipefail
cd "$(dirname "$0")"
# Local dev uses the project venv; CI installs mkdocs into the runner PATH.
# Invoke via the venv python (the console-script shebang pins an absolute
# path that breaks if the project folder moves).
if [ -x ../.venv/bin/python ]; then
    ../.venv/bin/python -m mkdocs build "$@"
else
    mkdocs build "$@"
fi
echo "Manual built into $(cd ../docs/cartelemetry/manual && pwd)"
