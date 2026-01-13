# CI / Docker note: `./gradlew` not found (exit 127)

If CI reports:

- `bash: line 1: ./gradlew: No such file or directory`

it means the Docker/CI job is running in a directory that does **not** contain a `gradlew` file.

## Where the real Android project lives

- `online-shopping-platform-303988-303997/kotlin_frontend/`

## Gradle wrapper shims provided in this repo

To accommodate different CI mount/workdir layouts, the repo includes multiple entrypoints:

- Repo root: `./gradlew`  
  Delegates to: `online-shopping-platform-303988-303997/kotlin_frontend/gradlew`

- Workspace root: `online-shopping-platform-303988-303997/gradlew`  
  Delegates to: `online-shopping-platform-303988-303997/kotlin_frontend/gradlew`

- Alternate mount root: `kotlin_frontend/gradlew`  
  Delegates to: `online-shopping-platform-303988-303997/kotlin_frontend/gradlew`

## Required CI fix

Update the CI job to set the working directory (or run `cd`) into one of the directories above **before** invoking `./gradlew`.

If CI still fails, the runner is starting in a directory outside the mounted repository.
