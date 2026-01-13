#!/usr/bin/env sh
# Convenience wrapper to run Gradle from any working directory inside this repo.
# Usage: ./online-shopping-platform-303988-303997/kotlin_frontend/run-gradle.sh <gradle-args...>

set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec sh "$SCRIPT_DIR/gradlew" "$@"
