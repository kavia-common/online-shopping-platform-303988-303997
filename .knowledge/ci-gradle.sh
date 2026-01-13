#!/usr/bin/env sh
set -eu

# Shim for CI analyzers that execute from online-shopping-platform-303988-303997/.knowledge/.
DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR/.."

sh ./ci-gradle.sh "$@"
