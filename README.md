# CofCup ☕

A free, open-source Android app that guides you through a **coffee cupping session**, step by step, with timers.

## What it does

1. **Set up** — pick your cup size (ml) and how many coffees to cup (1–12).
   The app will tell you how much coffee you will need to grind for each cup according to the SCA ratio, 8.25 g per 150 ml → *5.5 g per 100 ml*, (e.g. exactly `8.25 g` for a 150 ml cup)

2. **Name the coffees** — You can associate a **number** and a **colour** to each coffee and give it a name to be able to find which cup had which coffee at the end of the session.

3. **Start cupping** — This will guide you through a typical cupping session with pouring, breaking the crust, scraping the top of the cup and then tasting.

4. **Done** — check the coffee list you defined earlier to identify the cups by colour/number.

5. **History** — finished sessions are saved; go back to any previous session's coffee list, and delete sessions you don't need from the history list.

## The cupping timeline used (0:00 = first water hits the grounds)

| Time | Step |
|---|---|
| 0:00 → 4:00 | Fill the cups and let steep (crust forms, don't touch) |
| 4:00 → 5:00 | Break the crust (3 pushes with a spoon, smell the aromas) |
| 5:00 → 6:00 | Let grounds settle |
| 6:00 → 8:00 | Clean the top of the cup (skim foam & floaters, then cool to ~70 °C) |
| 8:00 → 15:00 | First cupping round (slurp; flavour, acidity, body) |
| 15:00 → 22:00 | Second cupping round (taste as cups cool; sweetness opens up) |
| 22:00 → 30:00 | Optional third cupping round (near room temperature; optional) |
| Done | Cupping complete — identify the coffees |

Timers follow the [SCA cupping protocol](https://sca.coffee/research/cupping-protocol): water at ~93 °C, a 4-minute undisturbed steep, the crust broken at the 4-minute mark, tasting starting ~8 min after the pour at ~70 °C, and further passes as the cups cool. See also [Gota's summary of the SCA protocol](https://gota.cafe/en/learn/cupping/sca-protocol) and [Conscious Cup's step table](https://consciouscup.com/pages/brew-methods/cupping).

## Tech stack

- Kotlin + **Jetpack Compose** (Material 3), no Internet permission required — fully offline
- Jetpack ViewModel + coroutines for the timers
- AndroidX DataStore (JSON payload) for the session history — no external serialization plugin needed
- **AGP 9.5** with built-in Kotlin support (no `kotlin-android` plugin), Gradle 9.6, `compileSdk 37`, `minSdk 26`

## Building

Requirements: **JDK 17**, an **Android SDK (API 37)** and a recent **Android Studio** supporting AGP 9.5 (Android Studio 2026.2.2 "Rabbit 2" or newer).

1. Open this folder in Android Studio → let it sync (it downloads Gradle 9.6 via `gradle/wrapper/gradle-wrapper.properties`).
2. Run the `app` configuration on a device/emulator.

From the command line (if you have Gradle 9.6 and the SDK configured):

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest   # runs the ratio/timeline maths unit tests
```

> The wrapper JAR is not committed (generated projects are lighter); Android Studio picks the Gradle version from `gradle-wrapper.properties`, and CI installs Gradle explicitly. Run `gradle wrapper` locally if you want `./gradlew` scripts.

## Project logo & identity

The launcher icon is a cupping bowl with steam, and the palette is coffee-brown inspired. Screens are portrait-only since the app is a session timer.

## Roadmap

- Sound/vibration cues when a step changes
- Skip setting up, some people want only the timer
- Per-coffee tasting notes (rating each cup, saved with the history)
- Screen to freely walk the timeline while keeping the session alive mid-cupping (wizard re-entry)
- Localisation (English UI today)

## Contributing

Issues and PRs are welcome — the codebase is intentionally small: one module (`:app`), a small data layer (`data/`), and one file per screen (`ui/`). The cupping ratio/timeline maths lives in `data/CuppingMath.kt` / `data/CuppingTimeline.kt` and is unit-tested.

## License

[MIT](LICENSE) — © 2026 Arthur
