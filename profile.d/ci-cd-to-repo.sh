#!/usr/bin/env bash
# Best-effort helper for CI shells that source scripts from a project-local profile.d directory.

set +e

if [[ -f "./gradlew" ]]; then
  return 0 2>/dev/null || exit 0
fi

for d in \
  "/workspace/online-shopping-platform-303988-303997" \
  "/repo/online-shopping-platform-303988-303997" \
  "/app/online-shopping-platform-303988-303997" \
  "/src/online-shopping-platform-303988-303997" \
  "/mnt/online-shopping-platform-303988-303997"; do
  if [[ -d "$d" && -f "$d/gradlew" ]]; then
    cd "$d" || true
    break
  fi
done

return 0 2>/dev/null || exit 0
