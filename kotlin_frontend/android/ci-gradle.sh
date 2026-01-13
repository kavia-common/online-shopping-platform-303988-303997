#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REAL_CI="$(cd "${SCRIPT_DIR}/.." && pwd)/ci-gradle.sh"

if [[ ! -x "${REAL_CI}" ]]; then
  echo "Error: Expected CI script at ${REAL_CI} but it was not found or not executable." >&2
  exit 127
fi

exec "${REAL_CI}" "$@"
