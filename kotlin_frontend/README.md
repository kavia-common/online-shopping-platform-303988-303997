# kotlin_frontend (Android Views/XML)

This module implements **lazy loading / infinite scroll** for the product list using **AndroidX Paging 3** (no Jetpack Compose).

## Features implemented
- Infinite scroll / paging (efficient incremental fetch)
- Loading states:
  - Fullscreen initial loading (when list empty)
  - Footer loading (when appending)
- Error states:
  - Fullscreen initial error with Retry
  - Footer append error with Retry
- Pull-to-refresh (SwipeRefreshLayout) wired to `PagingDataAdapter.refresh()`
- Empty state when no items are returned

## Where to look
- `ui/ProductListActivity.kt` – wires UI, refresh/retry, load state handling
- `ui/ProductListViewModel.kt` – Pager config (pageSize, prefetchDistance, etc.)
- `data/ProductPagingSource.kt` – page fetching logic
- `data/ProductRepository.kt` – currently a stub data source (replace with real API later)

## Note about Gradle wrapper
This repo includes `gradlew` scripts and `gradle-wrapper.properties`, but **does not** include `gradle-wrapper.jar`.
If CI/build requires it, add the standard Gradle wrapper jar at:
`gradle/wrapper/gradle-wrapper.jar`
(Usually generated via `gradle wrapper` in a full Android project environment.)

### If you see `./gradlew: Permission denied`
Some CI environments do not preserve the executable bit. Use:
- `sh ./gradlew <task>`
or the provided helper:
- `sh ci-gradle.sh <task>`
