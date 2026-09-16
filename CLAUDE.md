# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Native Android 2048 in Kotlin + Jetpack Compose (Material 3), `applicationId`
`com.kriruo.game2048`. compileSdk/targetSdk 35, minSdk 24, JVM target 17.

## Commands

```
./gradlew test                 # run all 73 JUnit tests (logic package only, no Android deps)
./gradlew test --tests "com.example.game2048.logic.Game2048EngineTest"   # single test class
./gradlew assembleDebug        # debug APK
./gradlew assembleRelease      # release APK (unsigned unless keystore.properties is present)
```

There is no CI configured — verification is `./gradlew test` plus manual install/play on a
device or the `game2048test` emulator (see local machine notes below). In day-to-day
development this repo is typically built where the Android SDK/Gradle are available, not
necessarily on the machine running Claude Code.

Release signing reads an optional, gitignored `keystore.properties` at the repo root
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) — never commit a keystore or its
credentials.

## Architecture

**Engine/UI split is the core design decision.** `logic/GameLogic.kt` (`Game2048Engine`) is a
pure, dependency-free Kotlin class with no Android imports — this is what makes it unit
testable and what was cross-checked against an independent Python re-implementation early on.
Every board/progression rule change belongs in `logic/`, not in Compose UI code, and new
engine behavior should get a JUnit test under `app/src/test/kotlin/.../logic/` alongside it.

**Stable tile ids drive animation.** `Tile.id` stays constant across slides/merges
(`GameState.nextTileId` threads monotonically through the immutable `GameState` so the engine
itself has no mutable fields). `Game2048Engine.move()` returns a `MoveResult` with per-tile
`TileMovement`s, merged-tile ids, and the spawned-tile id — the Compose layer in
`GameBoardUi.kt` uses these to animate individual tiles (slide/merge-bounce/spawn) instead of
snapping a raw value grid. Joker actions (`teleportTile`/`swapTiles`/`bombTile`/`doubleTile`/
`rotateBoard`) follow the same pattern via `JokerResult`.

**State flow:** `GameViewModel` (`AndroidViewModel`) owns a single `MutableStateFlow<GameUiState>`,
forwards swipes and Joker taps to `Game2048Engine`, and persists best score, in-progress
board, streak, and level to `SharedPreferences` (`GameStateSerializer.kt` handles the
`GameState` (de)serialization). There is no repository/DB layer — SharedPreferences is the
only persistence.

**Progression systems are independent, pure, and separately unit tested** — same pattern as
the engine, each with no Android or Compose dependency:
- `LevelTracker` — cumulative score across all games → player Level (triangular XP curve).
- `StreakTracker` — daily streak state machine (local calendar days, not UTC) + milestone
  detection (3/7/14/30/50/100/200/365).
- `BoardSizeOption` — board size tiers (Classic 4×4 / Big 5×5 / Mega 6×6 / Giant 8×8) and their
  unlock levels; `boardSize` lives on `GameState` itself since every row/col bound in the
  engine depends on it.
- `ThemeUnlocks` — tile color palette definitions (Clay/Meadow/Midnight/Berry/Cyber) and their
  unlock levels.

`GameMode` (ORIGINAL vs. EXTENDED) only changes which actions the UI exposes (Undo, Jokers);
switching modes mid-game never touches the board in progress.

**Compose UI is split by concern**, not by screen-per-file convenience — each file below owns
one piece of the visual/interaction surface: `MainActivity` (entry point) → `GameScreen`
(Start vs. Game nav + in-game layout) → `StartScreen` (mode/theme/board-size/stats entry
points) / `GameChrome` (header/sidebar + score chip) / `GameBoardUi` (grid, animated tiles,
swipe gestures) / `JokerUi` (aiming banner + action bar) / `GameOverlays` (combo popup, streak
banner, win/game-over overlay), with `ThemePickerDialog`, `BoardSizePickerDialog`,
`StatsDialog`, and `WelcomeDialog` (first-run "How to Play" walkthrough) as the picker/info
dialogs opened from `StartScreen`. `ui/theme/` holds the Material3 theme wiring (colors per
palette, typography).

## Local machine notes

- Android SDK at a non-default path: `C:\Users\ruoho\Android\Sdk`; AVD name `game2048test`.
- JDK 17 at `C:\Users\ruoho\dev-tools\jdk-17.0.20.1+1` — full local builds and tests work.
- No global git identity is set on this machine; this repo's local git user is
  `KriRuo` / `kristoffer.ruohonen@gmail.com`.
- Preferred workflow for refactors: branch + small commits, compiling/testing and capturing an
  emulator screenshot after each step.
