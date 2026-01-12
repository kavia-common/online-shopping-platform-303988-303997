# Kotlin Frontend – Static Analysis Report

Container: `kotlin_frontend`  
Date: 2026-01-12

This report captures a **non-destructive** static analysis run (no auto-fixes applied).

## Tools Run

### Android Gradle Lint
Command:
- `sh ci-gradle.sh :app:lintDebug`

Generated reports:
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/lint-results-debug.txt`
- `app/build/reports/lint-results-debug.xml`

Result summary (from `lint-results-debug.txt`):
- **0 errors**
- **39 warnings**
- **6 unique issue types**

### ktlint
Command attempted:
- `sh ci-gradle.sh :app:ktlintCheck`

Result:
- Not configured (`ktlintCheck` task not found).

### detekt
Command attempted:
- `sh ci-gradle.sh :app:detekt`

Result:
- Not configured (`detekt` task not found).

---

## Android Lint Findings (Warnings)

### 1) `HardcodedText` (24 warnings)
**Impact:** Localization/i18n and maintainability.

Primary affected files:
- `app/src/main/res/layout/activity_product_list.xml` (most warnings)
- `app/src/main/res/layout/item_load_state_footer.xml`
- `app/src/main/res/layout/item_product.xml`

Typical message:
- “Hardcoded string ..., should use @string resource”

---

### 2) `GradleDependency` (11 warnings)
**Impact:** Dependencies are behind latest stable releases; may miss bug fixes/performance updates.

Affected file:
- `kotlin_frontend/app/build.gradle`

Examples flagged by lint (current → newer available):
- `androidx.core:core-ktx` 1.12.0 → 1.17.0
- `androidx.appcompat:appcompat` 1.6.1 → 1.7.1
- `com.google.android.material:material` 1.11.0 → 1.13.0
- `androidx.constraintlayout:constraintlayout` 2.1.4 → 2.2.1
- `androidx.recyclerview:recyclerview` 1.3.2 → 1.4.0
- `androidx.swiperefreshlayout:swiperefreshlayout` 1.1.0 → 1.2.0
- `androidx.lifecycle:lifecycle-viewmodel-ktx` 2.7.0 → 2.10.0
- `androidx.lifecycle:lifecycle-runtime-ktx` 2.7.0 → 2.10.0
- `androidx.paging:paging-runtime-ktx` 3.2.1 → 3.3.6
- `androidx.test.ext:junit` 1.1.5 → 1.3.0
- `androidx.test.espresso:espresso-core` 3.5.1 → 3.7.0

---

### 3) `MissingApplicationIcon` (1 warning)
**Impact:** App manifest does not explicitly set an icon.

Affected file:
- `app/src/main/AndroidManifest.xml`

---

### 4) `ObsoleteSdkInt` (1 warning)
**Impact:** Redundant API gating.

Affected file:
- `app/src/main/res/values/themes.xml`

---

### 5) `OldTargetApi` (1 warning)
**Impact:** Lint warning about target SDK not being the latest.

Affected file:
- `kotlin_frontend/app/build.gradle`

---

### 6) `Overdraw` (1 warning)
**Impact:** Potential performance inefficiency (may be a false positive).

Affected file:
- `app/src/main/res/layout/activity_product_list.xml`

---

## If You Want ktlint/detekt (Minimal Gradle Additions)

**Not applied in this analysis step**—these are suggested minimal changes.

### ktlint
- Add buildscript classpath in `kotlin_frontend/build.gradle`:
  - `classpath("org.jlleitschuh.gradle:ktlint-gradle:12.1.1")`
- Apply plugin in `kotlin_frontend/app/build.gradle`:
  - `id "org.jlleitschuh.gradle.ktlint"`

### detekt
- Add buildscript classpath in `kotlin_frontend/build.gradle`:
  - `classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")`
- Apply plugin in `kotlin_frontend/app/build.gradle`:
  - `id "io.gitlab.arturbosch.detekt"`
