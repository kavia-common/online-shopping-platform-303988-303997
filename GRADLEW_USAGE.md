# Gradle wrapper usage

This repository includes multiple `gradlew` entrypoints to support different CI working directories:

- `./gradlew` (repo root) → delegates to `kotlin_frontend/gradlew`
- `kotlin_frontend/gradlew` → wrapper bootstrap / system gradle fallback
- `kotlin_frontend/app/gradlew` → delegates to `../gradlew`
- Workspace root `./gradlew` (outside repo) → delegates into `online-shopping-platform-303988-303997/gradlew`

## If CI strips execute permissions

Run the wrapper via `sh`:

```sh
sh ./gradlew test
```

## Important limitation

If the CI job runs **outside** the checked-out repository (or the repo is not mounted),
no repository change can make `./gradlew` exist in that external filesystem location.
The CI runner must execute within the checkout directory.
