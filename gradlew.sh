#!/usr/bin/env bash
set -euo pipefail

# Compatibility shim: some CI scripts may invoke `bash gradlew.sh` or similar.
# Forward to the repo-root ./gradlew shim (which forwards to kotlin_frontend/gradlew).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/gradlew" "$@"
