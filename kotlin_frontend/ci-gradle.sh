#!/usr/bin/env sh
set -eu

# CI-safe Gradle wrapper invocation.
# Some CI environments/checkers mount the workspace without preserving executable bits,
# causing `./gradlew: Permission denied`. Invoking via `sh` avoids needing +x.
#
# Usage examples:
#   sh ci-gradle.sh tasks
#   sh ci-gradle.sh :app:assembleDebug

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

sh ./gradlew "$@"
