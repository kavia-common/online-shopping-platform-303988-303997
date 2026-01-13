# Online Shopping Platform (Android Kotlin Frontend)

This repository contains the Android (Kotlin, Views/XML) frontend for an e-commerce demo application.

## Modules

- `kotlin_frontend/` — Android application (traditional Views + XML layouts)

## Development

### Prerequisites

- Android Studio (recommended)
- JDK (compatible with the Gradle wrapper used by the project)

### Build / Test

From the repository root:

```sh
cd online-shopping-platform-303988-303997
./gradlew tasks
./gradlew test
./gradlew check
```

## CI / Gradle wrapper note (important)

Some CI analyzers run Gradle from an unexpected working directory and may fail with:

```
bash: line 1: ./gradlew: No such file or directory
```

If you see this, use the repository helper script instead of calling `./gradlew` directly:

```sh
sh ./ci-gradle.sh check
```

This helper searches for the correct Gradle wrapper within the repo and forwards all arguments.

## Project Notes

- UI is implemented with traditional Android Views and `res/layout/*.xml` (no Jetpack Compose).
- The app is designed for demo purposes and may include frontend-only behaviors for missing backend capabilities.

