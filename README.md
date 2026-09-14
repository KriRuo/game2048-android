# 2048 (Android, Kotlin + Jetpack Compose)

A native Android implementation of the classic 2048 sliding-tile puzzle, built with Kotlin
and Jetpack Compose (Material 3), with a clean, warm "Claude"-inspired visual style and a
modern, animated game feel.

## Features

- Swipe up/down/left/right to slide and merge tiles on a 4x4 board.
- Every tile is individually animated: tiles slide smoothly to their new cell, a merge pops
  with a quick bounce, and a freshly spawned tile fades/scales in with a springy overshoot.
- A subtle "shake" + haptic tick when you swipe into a wall and nothing can move; a distinct,
  slightly stronger haptic tick on a merge vs. a plain slide.
- A floating "+N" score popup on the score chip whenever you gain points.
- Score and best-score tracking; best score persists across app restarts (`SharedPreferences`).
- Win banner at 2048 with an option to keep playing past it; both it and the game-over
  banner fade/scale in and out instead of snapping.
- Warm, restrained "Claude"-style palette: cream paper background, a clay/terracotta accent
  (`ClaudeAccent`, `#D97757`) for the wordmark and buttons, and a tile color ramp that warms
  from soft cream up through clay to a deep terracotta at 2048+, in both light and dark mode.
- Game logic is a pure, dependency-free `Game2048Engine` (in `logic/GameLogic.kt`) that
  tracks a stable id per tile through every slide and merge, so the UI can animate individual
  tiles rather than snapping a raw value grid into place. Unit tested in
  `app/src/test/kotlin/.../Game2048EngineTest.kt`. The algorithm (including the id-tracking
  through merges) was additionally cross-checked against an independent Python
  re-implementation -- known board/merge cases, plus 500 randomized-game invariant
  simulations -- before being trusted here, since this project was assembled in a sandbox
  without an Android SDK to compile and run the Kotlin directly.

## Project structure

```
app/
  src/main/kotlin/com/example/game2048/
    MainActivity.kt          - Activity entry point
    GameScreen.kt             - Compose UI: board, animated tiles, swipe gestures, overlays
    GameViewModel.kt          - Holds GameUiState, forwards swipes to the engine, persists best score
    logic/GameLogic.kt        - Pure game engine (tiles with stable ids, moves, merging, win/lose)
    ui/theme/                 - Compose Material3 theme: warm "Claude"-style palette & typography
  src/test/kotlin/.../logic/  - JUnit tests for Game2048Engine
```

## Opening the project

1. Install [Android Studio](https://developer.android.com/studio) (this project targets
   compileSdk/targetSdk 35, minSdk 24 — Android Studio will prompt to install any missing
   SDK platform/build-tools on first sync).
2. Open the `game2048/` folder as a project (`File > Open`).
3. Let Gradle sync finish (this needs an internet connection to download the Android
   Gradle Plugin, Kotlin, and AndroidX/Compose dependencies the first time).
4. Run the `app` configuration on an emulator or a connected device.

## Running the unit tests

In Android Studio: right-click `app/src/test/kotlin/.../logic/Game2048EngineTest.kt` and
choose "Run", or from a terminal once the project has synced at least once:

```
./gradlew test
```

## Notes on how this was built

This project was generated in a cloud sandbox that has Java and Gradle but no Android SDK
and no network access to Maven/Google's package repositories — so the Gradle wrapper here
was generated locally (jar included), but the project itself could not be compiled or run
end-to-end in that sandbox. Everything was written carefully by hand against current
Android/Compose/Kotlin APIs, and the core game algorithm was independently verified in
Python (see above), but you should expect to do the first Gradle sync/build yourself in
Android Studio, and to fix up any small API-version mismatches Android Studio's sync
flags, before you can run it on a device.
