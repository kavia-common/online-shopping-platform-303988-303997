# kotlin_frontend (Android Views/XML)

This module implements **lazy loading / infinite scroll** for the product list using **AndroidX Paging 3** (no Jetpack Compose).

## Features implemented
- Infinite scroll / paging (efficient incremental fetch)
- Search + filter integrated with paging:
  - Search field (debounced ~300ms)
  - Category filter (dropdown)
  - Price filter (chips)
  - Search/filter changes recreate the `PagingSource` and refresh results efficiently
- Loading states:
  - Fullscreen initial loading (when list empty)
  - Footer loading (when appending)
  - Inline “Updating results…” spinner when a new query loads while list already has items
- Error states:
  - Fullscreen initial error with Retry
  - Footer append error with Retry
- Pull-to-refresh (SwipeRefreshLayout) wired to `PagingDataAdapter.refresh()`:
  - Re-runs the current query + filters
- Empty state that reflects active search/filter (e.g., “No results for ‘<query>’”)

## How to use (in the app)
- Type in the search box to update results (debounced).
- Choose a category from the dropdown.
- Pick a price range chip (or select “Any”).
- Active search/filters appear as **removable chips** under the search bar (tap the “x” to remove).
- Use **Presets** to:
  - **Save preset**: store the current search + filters under a name (saved locally on-device).
  - **Apply** a preset: restores its query + filters and refreshes the list.
  - **Delete** a preset you no longer need.
- Use **Clear all** to reset both query and filters.
- Pull-to-refresh will reload using the current search + filters.

## UI polish (animations, chips, illustrated states)
This module includes subtle UI polish while preserving Paging 3 behavior (no full-screen flicker during refresh):
- **RecyclerView animations**: lightweight item appear animation + diff-friendly `DefaultItemAnimator` with `supportsChangeAnimations=false`.
- **Crossfades** between list/loading/empty/error containers.
- **Chip styling** tuned to the Ocean Professional theme:
  - price preset chips use `Widget.KotlinFrontend.Chip.Choice`
  - active filter chips use `Widget.KotlinFrontend.Chip.Filter`
- **Illustrated empty/error states** using lightweight `VectorDrawable`s.

### Adjust animation durations
Edit these integer resources:
- `app/src/main/res/values/styles.xml`
  - `anim_crossfade_duration_ms`
  - `anim_item_appear_duration_ms`

### Adjust chip styles
Edit:
- `app/src/main/res/values/styles.xml` (`Widget.KotlinFrontend.Chip.Choice`, `Widget.KotlinFrontend.Chip.Filter`)
- `app/src/main/res/color/kf_chip_*.xml` for stateful colors (checked/disabled/ripple)

## Where to look
- `ui/ProductListActivity.kt` – UI wiring for search/filters, refresh/retry, load state handling + crossfades
- `ui/ProductListViewModel.kt` – StateFlow for search/filter and `Flow<PagingData<Product>>` via `Pager`
- `ui/UiPolish.kt` – crossfade + subtle appear animation helpers
- `data/ProductPagingSource.kt` – loads pages using query + filters
- `data/ProductRepository.kt` – fake data source that applies search/filter locally (ready to swap with API later)
- `model/ProductFilter.kt` – filter state model

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
