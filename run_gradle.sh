#!/usr/bin/env sh
# Helper script for CI: run Gradle wrapper from anywhere in the mounted repo.
# Usage: sh run_gradle.sh <gradle-args...>
# Example: sh run_gradle.sh check

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

# Prefer kotlin_frontend wrapper.
if [ -f "$SCRIPT_DIR/kotlin_frontend/gradlew" ]; then
  exec sh "$SCRIPT_DIR/kotlin_frontend/gradlew" "$@"
fi

# Fallback to repo root wrapper (shim).
if [ -f "$SCRIPT_DIR/gradlew" ]; then
  exec sh "$SCRIPT_DIR/gradlew" "$@"
fi

echo "ERROR: Could not find gradlew within repository (expected under kotlin_frontend/ or repo root)." >&2
exit 127
