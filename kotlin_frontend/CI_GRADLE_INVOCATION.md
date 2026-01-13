# CI Gradle Invocation (Kotlin Frontend)

If CI fails with:

```
bash: line 1: ./gradlew: No such file or directory
```

it means the command is executed from a working directory that does not contain `gradlew`, or the repository is not mounted at that path inside Docker.

## Recommended invocations (robust)

From the repository root:

- Unix/macOS/Linux:
  - `sh kotlin_frontend/gradlew check`
- Windows:
  - `kotlin_frontend\gradlew.bat check`

Alternatively, use the helper script:

- `sh run_gradle.sh check`

## Required CI assumptions

- The repository must be mounted into the container.
- The working directory must be within the mounted repository, or you must call `gradlew` via an explicit path as shown above.
