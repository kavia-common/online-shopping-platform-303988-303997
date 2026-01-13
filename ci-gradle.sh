#!/usr/bin/env sh
# CI helper to execute Gradle wrapper even if the caller's working directory is unpredictable.
#
# Usage:
#   sh ./ci-gradle.sh check
#   sh ./ci-gradle.sh test
#
# This script searches common locations for `gradlew` within this repo.
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

# Candidate gradlew paths within this repository.
CANDIDATES="
$SCRIPT_DIR/gradlew
$SCRIPT_DIR/kotlin_frontend/gradlew
$SCRIPT_DIR/kotlin_frontend/app/gradlew
"

for g in $CANDIDATES; do
  if [ -f "$g" ]; then
    chmod +x "$g" 2>/dev/null || true
    exec "$g" "$@"
  fi
done

echo "ERROR: Could not find gradlew in expected repo locations." >&2
echo "Checked:" >&2
for g in $CANDIDATES; do
  echo " - $g" >&2
done
exit 127
