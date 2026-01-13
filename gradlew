#!/usr/bin/env sh
# Gradle wrapper shim for CI when invoked from the workspace root.
#
# PUBLIC_INTERFACE
# CI entrypoint: forwards all arguments to the actual wrapper in kotlin_frontend.

set -eu

TARGET="./kotlin_frontend/gradlew"

if [ ! -f "$TARGET" ]; then
  echo "Error: expected gradle wrapper at $TARGET but it was not found." >&2
  exit 127
fi

chmod +x "$TARGET" 2>/dev/null || true
exec "$TARGET" "$@"
