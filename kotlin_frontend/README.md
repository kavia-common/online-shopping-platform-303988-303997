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
  - **Swipe left/right** on an item row to **delete** it
    - A Snackbar offers **Undo** (restores the previous quantity)
  - Use **Remove** for a single item
  - Use **Clear cart** to remove all items
  - Apply a **coupon code** to see a **Discount** line and updated **Total**
  - Tap **Checkout** to place an order using the backend Order APIs

### Cart persistence + backend sync (per user)
Cart is now **backend-synced** and persisted **per user identity** (temporary email):
- The app will prompt for an **email** the first time you try to add/view cart.
- That email is stored locally and sent to the backend on cart requests (currently as `?email=...`).
- The cart is still cached locally using **SharedPreferences + JSON** as an **offline fallback**.

#### Offline fallback
Cart actions are **optimistic**:
- UI updates immediately and is saved locally.
- The app then attempts to sync with the backend.
- If the network call fails, the local cart remains and a transient error is shown.

### Coupons / discounts (new)
The cart supports applying/removing a **coupon code**:
- Coupon state is persisted locally (SharedPreferences) so it survives process death.
- Totals now include:
  - Subtotal
  - Discount (when a coupon is applied)
  - Total (subtotal - discount; tax is currently a placeholder)

#### Saved coupons + suggestions (polished UX)
The app stores a small list of **recently used** coupon codes locally on-device:
- Stored via `SavedCouponsStore` (SharedPreferences + Moshi JSON)
- Most-recent-first, de-duped by code (case-insensitive)
- Default limit: **8** codes (edit `SavedCouponsStore.DEFAULT_MAX_ITEMS`)

In **Cart** and **Checkout**:
- The coupon field is an **autocomplete dropdown** (Material exposed dropdown using `AutoCompleteTextView`)
- Suggestions are sourced from:
  - local saved coupons (always)
  - (future) server suggestions if/when the backend adds them — the UI and repository are designed to merge them, but currently the flow is local-only
- Selecting a suggestion fills the field and attempts to validate/apply immediately.

#### Clearer coupon validation feedback
- **Rule violations** (min subtotal, category restrictions, usage exhausted, expired, invalid code) are shown as **inline errors/helper text** under the coupon field.
- **Transient failures** (network/server operational issues) still appear as **Snackbars** with **Retry**.
- The typed coupon code is preserved in the field when validation fails.

#### Backend validation behavior
When an identity email is available, the app will **attempt** to validate/apply the coupon with backend endpoints if they exist.

**Important:** Coupon requests now include the **current cart line items** so the backend can enforce:
- minimum subtotal rules
- category-specific eligibility
- usage limits

Endpoints:
- `POST /api/coupons/validate?email=...`  
  body: `{ "code": "...", "items": [{ "productId": "...", "category": "...", "unitPrice": 12.34, "qty": 2 }] }`
- `POST /api/carts/coupon?email=...`  
  body: `{ "code": "...", "items": [...] }`
- `DELETE /api/carts/coupon?email=...`  
  typically without a body; if supported, the app may send `{ "code": "...", "items": [...] }`

If these endpoints are not present yet (404) or network fails, the app falls back gracefully:
- Coupon can remain applied as **Pending server validation** (UI continues to show totals)
- If a hard failure happens during apply/remove, the app rolls back and shows a Snackbar with Retry.

#### Emulator + localhost reminder
If you run the backend on your development machine and test on an Android emulator, `http://localhost:3010` will point to the emulator itself. In that case, you typically want:
- `http://10.0.2.2:3010`

#### Migration (local -> backend)
When you set your email identity for the first time, any existing local cart items are migrated to the backend:
- Merge behavior: quantities are **summed per productId**.

### Cart persistence
Cart data is stored locally on-device using **SharedPreferences + JSON**, so it survives app restarts and process death.

## UI polish (animations, chips, illustrated states)
This module includes subtle UI polish while preserving Paging 3 behavior (no full-screen flicker during refresh):
- **Cart swipe-to-delete** using `ItemTouchHelper` with **Undo** via Snackbar (restores previous quantity).
- **RecyclerView animations**:
  - lightweight per-item **appear** (fade + small translate) on bind
  - default add/remove animations via `DefaultItemAnimator` with `supportsChangeAnimations=false` to reduce flicker on quantity updates
- **Crossfades** between list/empty containers on the Cart screen (and list/loading/empty/error containers elsewhere).
- **Chip styling** tuned to the Ocean Professional theme:
  - price preset chips use `Widget.KotlinFrontend.Chip.Choice`
  - active filter chips use `Widget.KotlinFrontend.Chip.Filter`
- **Illustrated empty/error states** using lightweight `VectorDrawable`s.

### Adjust animation durations
Edit these integer resources:
- `app/src/main/res/values/styles.xml`
  - `anim_crossfade_duration_ms`
  - `anim_item_appear_duration_ms`

### Edit the Cart empty illustration
- Cart empty illustration vector:
  - `app/src/main/res/drawable/illustration_empty_cart.xml`
- Cart empty UI copy/CTA:
  - `app/src/main/res/layout/activity_cart.xml` (`emptyState` container)

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

## Checkout flow (new)

From the Cart screen:
- Tap **Checkout**
- Fill required fields (name, email, shipping address)
- Payment is a **mock** toggle: keep **Mock payment success** enabled to proceed
- Tap **Place order**
  - On success the cart is cleared locally and an **Order confirmation** screen is shown
  - Tap **View orders** to open the Orders list
  - When returning to Cart, a Snackbar confirms the order (with a quick link to Orders)

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
