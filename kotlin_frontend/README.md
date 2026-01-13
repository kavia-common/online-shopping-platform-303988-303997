# Kotlin Frontend (Android)

This container contains the Android (Kotlin, Views/XML) client for the online shopping platform.

## CI / Local build note (Gradle wrapper)

If CI fails with:

```
bash: line 1: ./gradlew: No such file or directory
```

it usually means the job is invoking `./gradlew` from a directory that does **not** contain the repository checkout (or is not running from a folder that contains a `gradlew` script).

Recommended:
- Ensure the CI step runs from the repository checkout root, or from:
  - `online-shopping-platform-303988-303997/` (workspace root), or
  - `online-shopping-platform-303988-303997/kotlin_frontend/` (Android project root)
- Prefer running from the repository root:

```
sh ./ci-gradle.sh
```

This script is intended to locate and invoke the correct Gradle wrapper in a consistent way.

Note: The repository includes several `gradlew` shim scripts in common subdirectories to increase compatibility, but CI must still execute within the checked-out workspace.

## Project structure

- `app/` - Android application module
- `src/main/` - Source sets, resources, manifest
- `gradle/` - Gradle wrapper and configuration

## Developer notes

- UI is implemented with traditional Android Views using XML layouts under `app/src/main/res/layout/`
- Networking and repositories live under `app/src/main/java/...`
