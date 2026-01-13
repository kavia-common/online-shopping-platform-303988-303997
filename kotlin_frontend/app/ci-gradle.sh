#!/usr/bin/env sh
set -eu

# Shim for CI/checkers that run from kotlin_frontend/app/.
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR/.."

sh ./ci-gradle.sh "$@"
