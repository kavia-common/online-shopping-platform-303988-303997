# CI Fix: `./gradlew` not found (exit 127)

If CI reports:

```
bash: line 1: ./gradlew: No such file or directory
```

it means the CI container's **working directory** does not contain a `gradlew` script.

## Where the real Gradle wrapper is

This repo's Android project is located at:

- `kotlin_frontend/`

The Gradle wrapper is:

- `kotlin_frontend/gradlew`

## Required CI setup

Ensure the CI command runs from the correct directory, e.g.:

- `cd kotlin_frontend && ./gradlew check`

Or configure Docker to use the correct working directory:

- `docker run -w /path/to/repo/kotlin_frontend ... ./gradlew check`

## Helper script

Repo root helper:

- `./run-gradle-check.sh`
