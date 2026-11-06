# AGENTS.md

## Repo location (important)

- OpenCode sessions here often start in `C:\Users\Arthur\Documents\Default Project`, which is **empty**. The actual repo is **`C:\Users\Arthur\GitHub\CofCup`** — work there.

## Build & test

- This dev machine (Windows) has **no Java, Gradle, or Android SDK**; there are also **no committed wrapper JAR/scripts** (only `gradle/wrapper/gradle-wrapper.properties`). So `./gradlew` does not exist and CLI builds fail locally. Verification options: Android Studio (syncs Gradle from the wrapper properties) or the CI workflow.
- CI (`.github/workflows/build.yml`) installs JDK 17 (Temurin) + Android SDK + Gradle 9.6.0, then runs:
  - `gradle :app:assembleDebug`
  - `gradle :app:testDebugUnitTest`
- Single unit test class: `gradle :app:testDebugUnitTest --tests "app.cofcup.CuppingMathTest"` (the only unit-test module is `app/src/test`).

## Toolchain quirks

- **AGP 9.5.1 / Gradle 9.6 / compileSdk 37 / built-in Kotlin**: there is intentionally **no `org.jetbrains.kotlin.android` plugin**. Do not add it — KGP-android requires opting out of the new DSL (`android.builtInKotlin=false` + `android.newDsl=false`) and would break sync.
- The Compose compiler **must** stay explicitly applied (`libs.plugins.compose.compiler`, `org.jetbrains.kotlin.plugin.compose`), and its `kotlin = "use"` version (2.4.21 in `gradle/libs.versions.toml`) must be bumped together with any Kotlin version change.
- All versions live in `gradle/libs.versions.toml`; Compose artifacts resolve through the BOM `2026.09.00` (no versions on Compose deps).

## Domain invariants — cupping maths

- Every displayed amount must come from `data/CuppingMath.kt` (SCA ratio 8.25 g / 150 ml = 0.055 g/ml; water = cups × cup size). Don't recompute inline in UI.
- Rounding is **two decimals, half-up** via `BigDecimal.setScale(2, HALF_UP)` (`round2`), per user decision. Do not change precision without asking.
- Unit tests in `CuppingMathTest.kt` encode exact rounded values. When adding tests, note `BigDecimal(Double)` uses the exact binary value — e.g. `8.255` is not representable and rounds *down*; use representable cases (8.25, 13.75) or deltas.
- The setup screen and the start-check screen must **not** show a "Coffee total / total to grind" line — the user removed it on purpose (twice). The only remaining total is on the past-session detail screen; don't reintroduce it elsewhere.

## Cupping timeline & session rules

- `0:00` in `data/CuppingTimeline.kt` = the moment water first hits the grounds; the user starts the timer itself ("Start cupping" then big Start on the live screen). Step windows are in seconds in that file — keep edits there, not in the screens.
- A session is saved to history **only** when the Done step is reached (`onCuppingComplete`); leaving earlier discards it. Every session gets a fresh `UUID`.
- The live timer increments `extraOffsetMs` on skip; elapsed time = `SystemClock.elapsedRealtime() - start + offset`. Keep this model when touching timers.

## Persistence & UI structure

- History is a JSON array in **DataStore** (`CuppingRepository`, store `cofcup_store`, key `history_v1`) parsed with `org.json`; decode failures return an empty list; capped at 200 sessions. There is no Room/DB and no serialization plugin — don't add either for this.
- Screens are one file per screen in `ui/`, orchestrated by a single `CuppingViewModel` (`Screen` sealed class + `handleBack()`); no navigation-compose dependency.
- Manifest locks the activity to portrait and the session screen forces keep-screen-on — both intentional for a hands-on timer.

## Commit hygiene

- The user tests manually and often has **uncommitted local fixes** in the tree. Commit those as their own commit **before** starting your changes (see `4d388fd`), then commit your work separately.
- Repo-local git identity is a placeholder (`Arthur` / noreply email) set only after the initial commit; keep using it unless the user changes it.
