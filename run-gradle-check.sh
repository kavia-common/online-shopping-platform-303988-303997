#!/bin/sh
# Stable CI entrypoint for running Gradle checks, independent of current working directory.

set -e

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# Always run from the repo root so relative Gradle paths resolve.
cd "$SCRIPT_DIR"

# Ensure gradlew is executable (best effort).
chmod +x ./gradlew 2>/dev/null || true

exec ./gradlew check
