#!/usr/bin/env sh
set -eu

# Shim for analyzers that run from app/src.
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR/../.."

sh ./ci-gradle.sh "$@"
