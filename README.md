# 2048 (Android, Kotlin + Jetpack Compose)

A native Android implementation of the classic 2048 sliding-tile puzzle, built with Kotlin
and Jetpack Compose (Material 3), with a clean, warm "Claude"-inspired visual style and a
modern, animated game feel.

## Features

### Core gameplay
- Swipe up/down/left/right to slide and merge tiles.
- Every tile is individually animated: tiles slide smoothly to their new cell, a merge pops
  with a quick bounce, and a freshly spawned tile fades/scales in with a springy overshoot.
- A subtle "shake" + haptic tick when you swipe into a wall and nothing can move; a distinct,
  slightly stronger haptic tick on a merge vs. a plain slide.
- A floating "+N" score popup on the score chip whenever you gain points.
- Score and best-score tracking; best score persists across app restarts, and the whole
  in-progress board (tiles, score, streak, level) is saved/restored across a full app kill.
- Win banner at 2048 with an option to keep playing past it; both it and the game-over
  banner fade/scale in and out instead of snapping.
- Tapping the in-game New Game button confirms first ("Start New Game?"), so an accidental tap
  can't silently wipe out a board in progress; skipped once the game has already ended.
- Extended tile font sizing and color ramps that keep working cleanly all the way to
  4096/8192+ (not just 2048), so the bigger boards below don't flatten out visually.

### Start Screen
A Start Screen (with an animated, glowing constellation flourish) is the app's front door
every time it opens, and is where all customization now lives (moved off the board screen so
it's a deliberate before-you-play step, not a mid-game distraction):
- **Mode picker** — Original vs. Extended; switching mid-game only changes which actions are
  exposed, it never touches the board in progress.
  - **Original** — classic 2048 rules: swipe only, no Undo, no Jokers.
  - **Extended** (default) — adds Undo and the five Jokers below.
- **🎨 Theme icon** — opens the palette picker.
- **Board size icon** — shows the currently selected size (e.g. "8×8") and opens the size
  picker; updates live the moment you pick a different (unlocked) size.
- **📊 Stats icon** — opens lifetime stats (games played, highest tile, total merges).
- **❓ How to Play icon** — reopens the first-run walkthrough (below) any time.

### First-run walkthrough
A short, five-page "How to Play" carousel (goal & merging, Original vs. Extended, Jokers,
Level/unlocks, and the customize icons/streaks) auto-opens once, the very first time the app
is opened, and is otherwise skippable. It's reopenable any time afterward from the Start
Screen's ❓ icon.

### Undo & Jokers (Extended mode)
- **Undo** — revert the single most recent move, 3 uses per game.
- **Teleport** (1/game) — tap a tile, then tap an empty cell to move it there.
- **Swap** (1/game) — tap two tiles to exchange their positions.
- **Bomb** (1/game) — tap a tile to remove it outright.
- **Double** (1/game) — tap a tile to double its value in place (scored like a merge).
- **Rotate** (1/game) — rotate the whole board 90° clockwise instantly, no target needed.

### Progression
- **Player Level** — derived from cumulative score across every game ever played (a
  triangular XP curve, so early levels come quickly and later ones take longer); never resets
  when a board does. The Start Screen shows exactly where you stand -- a progress bar plus
  "1,234 / 2,000 XP to Level N" -- rather than just the Level number.
- **Board sizes**, unlocked by Level and selectable from the Start Screen's board-size icon:
  Classic 4×4 (default), Big 5×5 (Level 10), Mega 6×6 (Level 15), Giant 8×8 (Level 20).
- **Color palettes**, unlocked by Level and selectable from the Start Screen's Theme icon:
  Clay (default), Meadow (Level 3), Midnight (Level 6), Berry (Level 10), and Cyber (Level 30)
  — Cyber breaks from the others' single-hue ramp with a neon cyan → violet → magenta
  progression.
- A debug shortcut (tap the score chip 5× quickly) jumps straight to Level 30, mainly to
  reach Cyber/Mega without grinding. A second one, tapping the Start Screen's BEST chip 5×
  quickly, resets the first-run walkthrough's "seen" flag so it can be tested again (force-stop
  and relaunch to see it auto-open) without clearing app data.
- **Daily streak** tracking (based on local calendar days, not UTC), with its own celebration
  banner the first time it crosses 3, 7, 14, 30, 50, 100, 200, or 365 days -- and, every day the
  streak advances, a claimable "Day N streak!" reward (bonus XP, scaling with streak length up
  to a 10-day cap) shown right on the Start Screen so showing up daily visibly speeds up leveling.
- Lifetime stats shown via the Start Screen's Stats icon: games played, highest tile ever
  reached, and total merges.
- **Daily Challenge** — a card on the Start Screen opens one fixed-seed, 30-move-capped board
  shared by every player on a given calendar day, one attempt per day, no Undo/Jokers regardless
  of your own mode. Completing it (win, loss, or running out the move cap) earns a flat +50 XP
  bonus. Entirely separate from the daily streak above — its own local score/best history, no
  leaderboard.

### Optional cloud sync (sign in)
Tap the 🔒/☁️ icon on the Start Screen to create an account (email/password) and sync lifetime
progress (score, streaks, unlocks, preferences) across devices — entirely optional, the game is
always fully playable signed out. Includes password reset. Whichever device (or the cloud) has
more lifetime progress wins on sign-in; the other side is brought up to match. Backed by
Firebase Auth + Cloud Firestore; see `CLAUDE.md`'s "Firebase backend" section for the schema and
security rules if you're standing up your own Firebase project for this repo.

### Layout & polish
- Responsive portrait layout that fits on real device screens without scrolling, plus a
  dedicated landscape layout (sidebar + board side by side) rather than a stretched portrait one.
- Warm, restrained "Claude"-style visual language across every palette: a clay/terracotta
  accent (`ClaudeAccent`, `#D97757`) in the default Clay palette, consistent in both light and
  dark mode.

### Under the hood
- Game logic is a pure, dependency-free `Game2048Engine` (in `logic/GameLogic.kt`) that
  tracks a stable id per tile through every slide, merge, and Joker action, so the UI can
  animate individual tiles rather than snapping a raw value grid into place. Progression logic
  (`LevelTracker`, `StreakTracker`, `ThemeUnlocks`, `BoardSizeOption`) is similarly pure and
  independently unit tested.
- 83 JUnit tests across 7 files under `app/src/test/kotlin/.../logic/` cover the engine
  (including Jokers and board-size variants), level curve, streak transitions, daily-challenge
  seeding/completion, theme/board-size unlock rules, and save-state (de)serialization.
- Play Store upload-ready: real `applicationId` (`com.kriruo.game2048`), an optional release
  signing config read from a gitignored `keystore.properties`, and R8 minification/resource
  shrinking enabled for release builds.

## Project structure

The Compose UI is split by concern rather than living in one file:

```
app/
  src/main/kotlin/com/example/game2048/
    MainActivity.kt                - Activity entry point
    Game2048Application.kt          - Application class; installs a crash-capture fallback dialog
    GameScreen.kt                    - App nav (Start vs. Game vs. Daily Challenge) + in-game layout
    StartScreen.kt                    - Landing screen: mode picker, customize/account icons, orbit flourish
    GameChrome.kt                      - In-game Header (portrait) / Sidebar (landscape) + ScoreChip
    GameBoardUi.kt                       - The board itself: tile grid, animated tiles, swipe gestures
    JokerUi.kt                            - Joker aiming banner + bottom action bar
    GameOverlays.kt                        - Combo popup, streak milestone banner, win/game-over overlay
    DailyChallengeScreen.kt                 - Daily Challenge's own small screen (start/play/result)
    ThemePickerDialog.kt                      - Palette picker (opened from StartScreen's Theme icon)
    BoardSizePickerDialog.kt                   - Board size picker (opened from StartScreen's size icon)
    StatsDialog.kt                               - Lifetime stats (opened from StartScreen's Stats icon)
    WelcomeDialog.kt                               - First-run "How to Play" walkthrough (opened from StartScreen's ? icon)
    DailyRewardDialog.kt                            - Claimable "Day N streak!" bonus-XP reward (opened from StartScreen)
    AccountDialog.kt                                 - Sign up/in/out + password reset (opened from StartScreen's ☁️/🔒 icon)
    ConfirmNewGameDialog.kt                           - "Start New Game?" confirmation (opened from the in-game New Game button)
    AppAnalytics.kt                                    - Firebase Analytics/Crashlytics wrapper (no-ops without config)
    AuthRepository.kt                                   - Firebase Auth (Email/Password) wrapper (no-ops without config)
    CloudSyncRepository.kt                               - Firestore cloud-sync wrapper (no-ops without config)
    GameViewModel.kt                                      - Holds GameUiState, forwards swipes/Jokers to the engine, persists all state
    logic/GameLogic.kt                                     - Pure game engine (tiles with stable ids, moves, merging, Jokers, win/lose)
    logic/BoardSizeOption.kt                                - Board size tiers (Classic/Big/Mega/Giant) and their unlock levels
    logic/LevelTracker.kt                                    - Cumulative-score → player Level curve
    logic/StreakTracker.kt                                    - Daily streak state machine + milestone detection
    logic/DailyChallengeTracker.kt                             - Daily Challenge completion state + per-day seed derivation
    logic/ThemeUnlocks.kt                                       - Tile color palette definitions and unlock levels
    logic/GameStateSerializer.kt                                 - Encodes/decodes GameState for SharedPreferences persistence
    ui/theme/                                                     - Compose Material3 theme: per-palette colors & typography
  src/test/kotlin/.../logic/                                      - 83 JUnit tests across 7 files (engine, level, streak, daily
                                                                     challenge, unlocks, serialization)
```

## Opening the project

1. Install [Android Studio](https://developer.android.com/studio) (this project targets
   compileSdk/targetSdk 35, minSdk 24 — Android Studio will prompt to install any missing
   SDK platform/build-tools on first sync).
2. Open this repository's root folder as a project (`File > Open`).
3. Let Gradle sync finish (this needs an internet connection to download the Android
   Gradle Plugin, Kotlin, and AndroidX/Compose dependencies the first time).
4. Run the `app` configuration on an emulator or a connected device.

## Running the unit tests

In Android Studio: right-click `app/src/test/kotlin/.../logic/` and choose "Run", or from a
terminal once the project has synced at least once:

```
./gradlew test
```

## Build & verification status

This app builds and has been installed and played on a real device across many rounds of
changes — it isn't just unit-tested in isolation. GitHub Actions CI (`.github/workflows/ci.yml`)
runs the full JUnit suite plus a debug-build sanity check on every push to `master` and every
pull request; a separate workflow (`build-test-apk.yml`) publishes a rolling debug-APK GitHub
Release on every push to `master` so testers can install the latest build without a local
Android toolchain. See `CLAUDE.md`'s "CI/CD" section for details, and `CHANGELOG.md` for a
dated history of what's shipped. If you're setting this up fresh in Android Studio for the
first time, the initial Gradle sync still needs an internet connection to fetch the Android
Gradle Plugin, Kotlin, and AndroidX/Compose dependencies, but no source changes should be needed
to get it running.

The core game algorithm was additionally cross-checked early on against an independent Python
re-implementation — known board/merge cases plus 500 randomized-game invariant simulations —
before the Jokers, board-size tiers, and progression systems were layered on top of it.
