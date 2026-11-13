# AGENTS.md

## Repo location (important)

- This is the **`dev` branch worktree** at `C:\Users\Arthur\GitHub\CofCup_dev`; the `main` checkout lives at `C:\Users\Arthur\GitHub\CofCup`. Work in the checkout that matches your branch, and don't switch branches in the other one (both share the same `.git`, so a branch can only be checked out in one worktree at a time).

## Build & test

- CLI builds **do work locally**: the machine has JDK 17 (Temurin), Gradle 9.6.0 (`C:\Gradle\...`, `gradle` on PATH) and the Android SDK (`C:\Users\Arthur\AppData\Local\Android\Sdk`, via `local.properties`). The Gradle **wrapper is committed** (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), so `.\gradlew :app:assembleDebug` works; the installed `gradle` works too. (Full machine/toolchain details live in the global `~/.config/opencode/AGENTS.md`; the emulator AVD `cofcup_test` is used for manual runs.)
- CI (`.github/workflows/build.yml`) installs JDK 17 (Temurin) + Android SDK + Gradle 9.6.0, then runs:
  - `gradle :app:assembleDebug`
  - `gradle :app:testDebugUnitTest`
- Unit tests (pure JUnit, `app/src/test`, run all): `CuppingMathTest`,
  `CupColorsTest`, `HistoryCodecTest`, `BlindRedactionTest`, `PlanConversionTest`,
  `CupMatchTest`, `CupRatingTest`, `ShareCodecTest`. A single class can be
  selected with `--tests "app.cupnlog.CuppingMathTest"`. `HistoryCodecTest` and
  `ShareCodecTest` use the test-only `org.json:json` dependency (same API as the
  Android runtime).

## Toolchain quirks

- **AGP 9.5.1 / Gradle 9.6 / compileSdk 37 / built-in Kotlin**: there is intentionally **no `org.jetbrains.kotlin.android` plugin**. Do not add it — KGP-android requires opting out of the new DSL (`android.builtInKotlin=false` + `android.newDsl=false`) and would break sync.
- The Compose compiler **must** stay explicitly applied (`libs.plugins.compose.compiler`, `org.jetbrains.kotlin.plugin.compose`), and its `kotlin = "use"` version (2.4.21 in `gradle/libs.versions.toml`) must be bumped together with any Kotlin version change.
- All versions live in `gradle/libs.versions.toml`; Compose artifacts resolve through the BOM `2026.09.00` (no versions on Compose deps). ZXing is split on purpose: `com.google.zxing:core` (pure Java, QR BitMatrix → Canvas, no camera deps) for generation and `com.journeyapps:zxing-android-embedded` (own surface, handles the CAMERA runtime permission) for the in-app scanner.

## Domain invariants — cupping maths

- Every displayed amount must come from `data/CuppingMath.kt` (SCA ratio 8.25 g / 150 ml = 0.055 g/ml; water = cups × cup size). Don't recompute inline in UI.
- Rounding is **two decimals, half-up** via `BigDecimal.setScale(2, HALF_UP)` (`round2`), per user decision. Do not change precision without asking.
- Unit tests in `CuppingMathTest.kt` encode exact rounded values. When adding tests, note `BigDecimal(Double)` uses the exact binary value — e.g. `8.255` is not representable and rounds *down*; use representable cases (8.25, 13.75) or deltas.
- The setup screen and the start-check screen must **not** show a "Coffee total / total to grind" line — the user removed it on purpose (twice). The "Coffee total" line on the past-session detail screen was removed later too (2026-11); no screen shows a coffee total anymore — don't reintroduce it without asking.

## Cupping timeline & session rules

- `0:00` in `data/CuppingTimeline.kt` = the moment water first hits the grounds; the user starts the timer itself ("Start cupping" then big Start on the live screen). Step windows are in seconds in that file — keep edits there, not in the screens.
- A session is saved to history **only** when the Done step is reached (`onCuppingComplete`); leaving earlier discards it. A run without a saved setup behind it gets a fresh `UUID`; a run from a planned setup reuses that row's id (converted in place, see Persistence below).
- "Start Cupping" on Home runs the timer with no cups (`cupCount = 0`); such a session is stored in history as an **Empty Cupping Session**. Back from an unstarted session (or confirmed "End & discard") always lands on Home.
- The live timer increments `extraOffsetMs` on skip; elapsed time = `SystemClock.elapsedRealtime() - start + offset`. Keep this model when touching timers.
- Step chimes fire only when the **timer itself** enters a new step (`stepChimes` flow → sound + optional vibration); skipped steps stay silent (`announcedStepStartSec` guards it). Don't chime on skip.

## Blind tasting rules

- In a blind session the **only** visible identity is the cup's left-to-right position ("Cup 1…N"). Names, colours and labels are private metadata — keep them out of labels, content descriptions and instruction copy on every pre-reveal surface. `data/Blind.kt` is the only route through which blind surfaces render cup identity; use its helpers (`blindedByPosition()`, `BlindCupDisplay`).
- End-of-session identification (`data/CupMatch.kt`): the user assigns each cup position the label that was under it; confirming a complete bijection reorders the coffees so position and identity agree, then reveals. `revealedAtMs` is stamped exactly once — a later re-match only re-permutes.
- Shared blind setups (`ShareCodec`) carry the **full coffee identities** so the joiner can run the same identify flow; the joiner's copy stays blind because `kind` marks the row blind, not because data is withheld. Don't "fix" this back to position-only payloads.

## Share / join (QR & links)

- `data/ShareCodec.kt` encodes everything moved between devices. The QR encodes an **https App Link whose query string is the whole payload** (`?v=1&d=base64url(deflate(json))`) — no backend; treat that as a hard design decision.
- Three incoming forms decode identically everywhere: the https App Link, `cofcup://import?v=1&d=…`, and the bare `d=` code. All payloads are **untrusted**: strict version/kind checks, bounded sizes, decompression-bomb cap. Validation never auto-applies anything — the caller always shows a confirm preview (`ImportPreview`) first.
- `kind` discriminates payloads (`"setup"`, `"blind_setup"`); `"ratings"` / `"group_results"` are phase 4 and rejected here.

## Persistence & UI structure

- History is a JSON array in **DataStore** (`CuppingRepository`, store `cupnlog_store`, key `history_v1`) parsed with `org.json`; decode failures return an empty list; capped at 200 sessions (`MAX_STORED_SESSIONS`). The same store holds the chime toggles (`sound_on_next_step` on by default, `vibrate_on_next_step` off) and the profile (`my_id` — random UUID generated on first use, `my_name`). There is no Room/DB and no serialization plugin — don't add either for this.
- The history list doubles as the **planned-setups store**: rows are `SessionStatus.PLANNED` or `DONE` in the same array. Running a planned setup converts that row **in place** on Done (same id/`createdAtMs`, status → DONE) via `plannedSessionId`/`savedSessionId` — it doesn't spawn a second row.
- Screens are one file per screen in `ui/`, orchestrated by a single `CuppingViewModel` (`Screen` sealed interface + `handleBack()`); no navigation-compose dependency. Multi-step screens carry a `returnTo` so back stays sensible when opened from multiple places (StartCheck, IdentifyCups, ShareSetup, ImportPreview). Settings is only reachable from the live-session screens (gear icon; back returns to LiveSession). Ratings has two entry modes (BY_CUP / BY_CATEGORY) that are session-scoped view-model state, never persisted; leaving Ratings after Done re-persists the edits.
- On Home the launcher icon is rendered via AndroidView + ImageView: `painterResource()` would **crash** on the adaptive `mipmap` icon (this exact crash was fixed once — don't revert it).
- Manifest: portrait-locked `singleTask` activity; deep links `cofcup://import` and the autoVerify https App Link land in `MainActivity` (the scanner screen never saves anything itself — payloads go through the view model's confirm preview); permissions are VIBRATE (chime) and CAMERA (in-app QR scanner), with `android.hardware.camera` marked `required="false"`. The session screen sets `view.keepScreenOn`. All intentional — ask before changing.

## Commit hygiene

- The user tests manually and often has **uncommitted local fixes** in the tree. Commit those as their own commit **before** starting your changes (see the "manual testing fixes" commit), then commit your work separately.
- Repo-local git identity is `arthurdehgan` / `28764091+arthurdehgan@users.noreply.github.com` — keep using it unless the user changes it.
