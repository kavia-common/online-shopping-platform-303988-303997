#!/usr/bin/env sh
# CI helper: run Gradle wrapper for kotlin_frontend reliably from any working directory
# inside the repo. Prefer calling this script from CI:
#   sh kotlin_frontend/ci-gradle.sh check

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if [ -f "$SCRIPT_DIR/gradlew" ]; then
  exec sh "$SCRIPT_DIR/gradlew" "$@"
fi

echo "ERROR: kotlin_frontend/gradlew not found. Ensure repository is mounted correctly." >&2
exit 127
