# CI convenience: some container images source ~/.profile or /etc/profile.d scripts in login shells.
# While we cannot control CI mounts/working dirs from within the repo, this provides a best-effort
# auto-cd into the repository when this file is sourced by the shell.

# If a gradlew is already present in current directory, do nothing.
if [ -f "./gradlew" ]; then
  return 0 2>/dev/null || exit 0
fi

# Try common repo locations.
for d in \
  "/workspace/online-shopping-platform-303988-303997" \
  "/repo/online-shopping-platform-303988-303997" \
  "/app/online-shopping-platform-303988-303997" \
  "/src/online-shopping-platform-303988-303997" \
  "/mnt/online-shopping-platform-303988-303997" \
  "$HOME/online-shopping-platform-303988-303997" \
  "$(pwd)/online-shopping-platform-303988-303997"; do
  if [ -d "$d" ] && [ -f "$d/gradlew" ]; then
    cd "$d" || true
    break
  fi
done

return 0 2>/dev/null || exit 0
