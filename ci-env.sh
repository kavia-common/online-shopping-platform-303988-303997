#!/usr/bin/env bash
# This file is intended to be sourced by CI entrypoints (if supported).
# It attempts to switch to the repository directory so relative commands like ./gradlew work.

set -euo pipefail

# If we're already in a directory containing a gradlew, do nothing.
if [[ -f "./gradlew" ]]; then
  return 0 2>/dev/null || exit 0
fi

# Try common relative locations from where CI might start.
for d in \
  "/workspace/online-shopping-platform-303988-303997" \
  "/repo/online-shopping-platform-303988-303997" \
  "/app/online-shopping-platform-303988-303997" \
  "/src/online-shopping-platform-303988-303997" \
  "/mnt/online-shopping-platform-303988-303997" \
  "$(pwd)/online-shopping-platform-303988-303997" \
  "$(pwd)/.."; do
  if [[ -d "$d" && -f "$d/gradlew" ]]; then
    cd "$d"
    break
  fi
done

# Final: if repo root has kotlin_frontend/gradlew, cd there.
if [[ -f "./gradlew" ]]; then
  return 0 2>/dev/null || exit 0
fi

return 0 2>/dev/null || exit 0
