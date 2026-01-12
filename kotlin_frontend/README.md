# kotlin_frontend (Android Views/XML)

This module implements **lazy loading / infinite scroll** for the product list using **AndroidX Paging 3** (no Jetpack Compose).

## Features implemented
- Infinite scroll / paging (efficient incremental fetch)
- Shopping cart (local, persistent):
  - Add to cart from product rows (both paged list and grouped category previews)
  - Cart screen grouped by category with quantity controls, item subtotals, and cart subtotal
  - Remove single item, clear cart, and a stub Checkout action
  - Cart persists across app restarts/process death via SharedPreferences + JSON
- Category browsing (chips) + optional grouped view:
  - Horizontally scrollable **category chips** (RecyclerView) with **snap-to-item** fling behavior: **All (grouped)** + each backend category
  - Chips show **category icons** (known categories mapped to vector drawables; unknown categories use a generic icon)
  - Selecting a category switches to the existing **Paging 3** list filtered by that category
  - Selecting **All (grouped)** shows **category sections** with a styled header (icon + divider) and a **See all** action
  - **Lazy loading per section**: each category section fetches only a small preview page when it becomes visible (prevents over-fetching)
- Search + filter integrated with paging and grouping:
  - Search field (debounced ~300ms)
  - Price filter (chips)
  - Search/filter changes recreate the `PagingSource` and refresh results efficiently
  - In grouped mode, the current search/filter state is applied to each category preview query as well
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
- Use the **category chips**:
  - **All (grouped)**: shows category sections with small previews (lazy loaded on scroll)
  - Tap a category: shows the **paged list** for that category (infinite scroll)
  - Tap the “x” on the active **Category: …** chip to clear and return to **All (grouped)**
- Pick a price range chip (or select “Any”).
- Active search/filters appear as **removable chips** under the search bar (tap the “x” to remove).
- Use **Presets** to:
  - **Save preset**: store the current search + filters under a name (saved locally on-device).
  - **Apply** a preset: restores its query + filters and refreshes the list.
  - **Delete** a preset you no longer need.
- Use **Clear all** to reset both query and filters.
- Pull-to-refresh will reload using the current search + filters.

### Cart usage
- Tap **Add to cart** on any product row to add 1 item.
- Use the **+ / -** controls on the product row to adjust the quantity in your cart.
- Tap the **Cart** icon in the top toolbar to open the cart.
- In the cart:
  - Items are **grouped by category**
  - Use **+ / -** to update quantity (decrement to 0 removes the item)
  - Use **Remove** for a single item
  - Use **Clear cart** to remove all items
  - Tap **Checkout** to see a stub action (not implemented)

### Cart persistence
Cart data is stored locally on-device using **SharedPreferences + JSON**, so it survives app restarts and process death.

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

### Category icons (chips + section headers)
- Icon mapping is implemented in:
  - `app/src/main/java/com/example/kotlinfrontend/ui/CategoryIconMapper.kt`
- Vector drawable icons live in:
  - `app/src/main/res/drawable/` (e.g., `ic_category_electronics.xml`, `ic_category_generic.xml`)
- Chip icon tint is controlled by:
  - `app/src/main/res/color/kf_chip_choice_icon_tint.xml`

## Where to look
- `ui/ProductListActivity.kt` – UI wiring for search/filters, refresh/retry, load state handling + crossfades
- `ui/ProductListViewModel.kt` – StateFlow for search/filter and `Flow<PagingData<Product>>` via `Pager`
- `ui/UiPolish.kt` – crossfade + subtle appear animation helpers
- `data/ProductPagingSource.kt` – loads pages using query + filters
- `data/ProductRepository.kt` – fake data source that applies search/filter locally (ready to swap with API later)
- `model/ProductFilter.kt` – filter state model

## Backend API configuration

This frontend is wired to call the Spring Boot APIs at:

- Base URL: `http://localhost:3010` (default)
- Products: `GET /api/products` with paging + search/filters
- Orders:
  - `POST /api/orders` (create)
  - `GET /api/orders` (paged history, optional filters)
  - `GET /api/orders/{id}` (details)
  - `POST /api/orders/{id}/pay|ship|deliver|cancel` (transitions)

To change the base URL, edit:
- `app/src/main/java/com/example/kotlinfrontend/network/ApiConfig.kt`

## Orders screen (filters + chips)

From the Products screen:
- Tap **Create order (sample)** to create an order using a fixed payload (for end-to-end wiring).
- Tap **Orders** to open the order history screen (Paging 3 list).

### Filters (polished UX)
The Orders screen now mirrors the Product list paging UX:
- **Status filter** (choice chips): Pending, Paid, Shipped, Delivered, Cancelled (or Any).
- **Customer email filter** (optional): typing is **debounced** (~300ms) before it affects paging.
- **Date range filter** (optional): simple **From/To** date pickers (yyyy-MM-dd).

Active filters appear as **removable chips** under the controls, and **Clear all** resets status/email/date filters.
Pull-to-refresh uses `PagingDataAdapter.refresh()` and will update smoothly without full-screen flicker.

### Backend verification via curl (examples)

Create an order:
```bash
curl -X POST http://localhost:3010/api/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "guest@example.com",
    "items": [
      { "productId": "sample-product-1", "quantity": 1 },
      { "productId": "sample-product-2", "quantity": 2 }
    ]
  }'
```

List orders (page 0, size 20):
```bash
curl "http://localhost:3010/api/orders?page=0&size=20&sort=createdAt,desc"
```

### Note about Android emulator + localhost
If you run the backend on your development machine and test on an Android emulator, `http://localhost:3010` will point to the emulator itself. In that case, you typically want:
- `http://10.0.2.2:3010`

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
