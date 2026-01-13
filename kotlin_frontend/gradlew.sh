#!/usr/bin/env sh
# Compatibility wrapper for environments that call `./gradlew.sh` instead of `./gradlew`.
set -eu
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec "$SCRIPT_DIR/gradlew" "$@"
