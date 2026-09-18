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

GitHub Actions CI is configured (see below) — locally, verification is still `./gradlew test`
plus manual install/play on a device or the `game2048test` emulator (see local machine notes
below). In day-to-day development this repo is typically built where the Android SDK/Gradle
are available, not necessarily on the machine running Claude Code.

Release signing reads an optional, gitignored `keystore.properties` at the repo root
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) — never commit a keystore or its
credentials.

Firebase (Analytics, Crashlytics, Auth, Firestore) follows the same optional-file pattern as
release signing: an optional, gitignored `app/google-services.json`. No file present means the
`com.google.gms.google-services` Gradle plugin is simply never applied (see the `apply(plugin =
...)` conditional in `app/build.gradle.kts` — it can't live in the `plugins {}` block itself,
which is statically evaluated before the rest of the script and can't reference a `val` or even
`java.io.File`), `BuildConfig.FIREBASE_ENABLED` is `false`, and every call into
`AppAnalytics`/`AuthRepository`/`CloudSyncRepository` no-ops or fails soft — true for every
fresh clone and for CI today, verified by building with the file removed. Fetch a real one via
`firebase apps:sdkconfig ANDROID <APP_ID> --project <PROJECT_ID>` (see "Firebase backend"
below) rather than the Firebase Console UI.

## Firebase backend

Project `game2048-47897` (Firebase Auth + Cloud Firestore only — Analytics/Crashlytics don't
need any of this, see above). Entirely optional cloud sync layered on top of local play: the
game is always fully playable signed-out, and every function in the three files below fails
soft (no-ops, returns null) rather than throwing when Firebase isn't configured or a call fails.

- **Auth** (`AuthRepository.kt`): Email/Password only, enabled via `firebase.json`'s `auth`
  block + `firebase deploy --only auth` (not via the Console). `AccountDialog.kt` (opened from
  the ☁️/🔒 icon on `StartScreen`) is the only UI — sign up, sign in, sign out, inline error
  text. `GameViewModel` exposes `signedInUserId`/`authBusy`/`authError` and reacts to sign-in
  from anywhere (including Firebase silently restoring a previous session on app open, not just
  an in-dialog action) via a `viewModelScope` collector on `AuthRepository.currentUserId` set up
  in `init {}`.
- **Firestore** (`CloudSyncRepository.kt`): the database is named **`game2048-db`**, NOT
  `(default)` — this project has no default database, so every access must go through the
  `FirebaseFirestore.getInstance("game2048-db")` overload, and `firebase.json`'s `firestore`
  block needs an explicit `"database": "game2048-db"` for CLI commands (`deploy`,
  `firestore:databases:*`) to target the right one. Enterprise edition, `eur3` (Europe
  multi-region), delete-protection enabled. One collection: `users/{uid}`, one document per
  signed-in player, written as a full (non-merge) `.set()` — see `CloudProgress` for the exact
  field list (a subset of `GameViewModel`'s locally-persisted lifetime stats/preferences).
- **Sync policy**: on sign-in, `GameViewModel.applyCloudProgressIfSignedIn()` pulls the cloud
  document and compares `cumulativeScore` — whichever side (device or cloud) has more lifetime
  progress wins wholesale (adopted entirely, not field-by-field merged), and the other side gets
  pushed up to match. Every progress-changing local write (`persist()`, plus the three
  `onSelect*` preference setters, which don't go through `persist()`) triggers a best-effort
  `syncToCloudIfSignedIn()` push afterward. This is deliberately simple (whole-snapshot
  last-write-wins-by-score, no per-field timestamps) rather than a general conflict-resolution
  system — fine for a single-player game with no concurrent-device-editing scenario to speak of.
- **Security rules** (`firestore.rules`, deployed via `firebase deploy --only firestore`):
  `users/{uid}` is readable/writable only by `request.auth.uid == uid`; every write is
  schema-validated (exact field set via `hasOnly`+`hasAll`, every field type-checked, the three
  `*Id` fields constrained to their real enum values); updates additionally enforce that
  `cumulativeScore`/`bestScore`/`highestTileEver`/`totalMerges` can't decrease versus the
  currently-stored document, backing up the client-side "adopt the larger" sync policy above
  server-side. No `list` (nothing ever queries the collection, only gets a known uid) and no
  `delete` (a player's synced progress is never client-erasable).
- **Local dev / CI setup**: `.firebaserc` + `firebase.json` are committed (project ID and this
  config aren't secret); `app/google-services.json` is not (see above) — fetch your own via the
  Firebase CLI, logged in as an account with access to project `game2048-47897`.

## CI/CD

Two workflows under `.github/workflows/`:

- **`ci.yml`** — runs on every push to `master` and every pull request targeting `master`.
  Two independent jobs, each its own status check on the PR: `test` (`./gradlew test`, the
  73 JUnit tests, with the HTML report uploaded as a workflow artifact) and `build`
  (`./gradlew assembleDebug`, a compile-only sanity check). A red check here is what used to
  require asking Claude to run tests/build manually — it's now automatic and visible directly
  on the PR/commit.

- **`build-test-apk.yml`** — publishes a debug APK as a **GitHub Release** so invited
  collaborators can install a test build on a phone without a local Android toolchain:
  - On every push to `master`: builds and updates a single rolling release tagged
    `latest-master` (same URL always has the newest build —
    `https://github.com/KriRuo/game2048-android/releases/tag/latest-master`).
  - On manual trigger (Actions tab → "Build test APK" → "Run workflow", optionally naming a
    branch/PR ref): builds that ref and publishes a separate release tagged
    `test-<ref>-<run#>`, so ad-hoc test builds don't clobber `latest-master`.
  - Both releases are marked as debug/unsigned builds not meant for wider distribution.

**Branch protection on `master`** (set up manually in Settings → Branches, not via a file in
this repo): requires the `test` and `build` status checks from `ci.yml` to pass before a PR
can be merged, and requires the branch to be up to date with `master` first. Admins are not
blocked from bypassing it, so direct pushes to `master` (the workflow used so far in this
repo's early history) still work.

- **`release-build.yml`** — manual-only (`workflow_dispatch`), builds a **signed** release
  `.aab` via `./gradlew bundleRelease` using a release keystore that lives only as GitHub
  Actions secrets (never committed): `RELEASE_KEYSTORE_BASE64` (the keystore file, base64),
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. The workflow writes
  `app/game2048-release.jks` and a root `keystore.properties` from those secrets, builds, then
  deletes both before the job ends. Output is uploaded as a workflow artifact named
  `app-release-bundle` (not published anywhere — there's no Play Console listing yet to push
  to). This is prep for eventual Play Store upload automation (Play Developer API), not that
  automation itself.

### Play Store rollout — where to pick up

A release keystore already exists (generated once, delivered to KriRuo directly — not in this
repo or its history) and `release-build.yml` can already turn it into a signed `.aab`. What's
left, roughly in order:

**KriRuo (account/human side, can't be done from a session):**
1. Back up `game2048-release.jks` + its passwords outside GitHub, if not already done.
2. Add the 4 `RELEASE_KEYSTORE_*`/`RELEASE_KEY_*` secrets under Settings → Secrets and
   variables → Actions, if not already done (the `release-build.yml` workflow will fail
   without them — check that first if it's red).
3. Create the Google Play Console account ($25, identity verification).
4. ~~Create a Firebase project~~ — done: project `game2048-47897`, Firestore (`game2048-db`,
   Enterprise, `eur3`) and Email/Password Auth are both live — see "Firebase backend" above.
   Every dev machine (and CI, if a future workflow needs it) still needs its own
   `app/google-services.json` fetched via the Firebase CLI, since that file isn't committed.
5. Store listing requirements: privacy policy URL, app icon/feature graphic/screenshots,
   content rating questionnaire, data safety form — no longer "no data collected": Firebase
   Analytics collects app-usage events, and signed-in players' game progress (scores, streaks,
   preferences — no PII beyond the email/password they signed up with) syncs to Firestore.
6. First `.aab` upload to Play Console must be manual (Google requires this before any API
   automation can target that app listing) — grab the artifact from a `release-build.yml` run.

**Next technical step once an app exists in Play Console:**
- Add Play Developer API upload to CI (e.g. `r0adkll/upload-google-play` action) using a
  service-account JSON key, targeting the **internal testing track** first (no review wait,
  good for KriRuo's invited testers) before ever touching `production`.

`versionCode` is already handled: `app/build.gradle.kts` reads it from a `-PversionCode=<n>`
Gradle property (defaulting to `1` for local/unspecified builds), and `release-build.yml`
passes `github.run_number` for that property — so every bundle built by that workflow gets a
strictly increasing versionCode automatically. `versionName` is still the static `"1.0"`;
bump it by hand in `app/build.gradle.kts` when you actually want the version string to move.

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
`StatsDialog`, `WelcomeDialog` (first-run "How to Play" walkthrough), and `DailyRewardDialog`
(claimable streak bonus-XP) as the picker/info dialogs opened from `StartScreen`, plus
`ConfirmNewGameDialog` opened from the in-game New Game button. `ui/theme/` holds the Material3
theme wiring (colors per palette, typography).

**Streaks and Levels are directly connected**: `StreakTracker.dailyBonusXp(streakDay)` (pure,
capped at 10 days' worth) is added straight to cumulative score via
`GameViewModel.onClaimDailyReward()` once the player confirms `DailyRewardDialog` -- computed
once per new streak day in `buildInitialState()` and never recomputed, so an unclaimed reward is
simply gone if the app closes first rather than persisting or stacking (same reasoning as
`MAX_UNDOS` not surviving a process restart). `LevelTracker.xpProgress(cumulativeScore)` exposes
the raw earned/span numbers (not just the `levelProgress` fraction) for the Start Screen's
"X / Y XP to Level N" display.

## Local machine notes

- Android SDK at a non-default path: `C:\Users\ruoho\Android\Sdk`; AVD name `game2048test`.
- JDK 17 at `C:\Users\ruoho\dev-tools\jdk-17.0.20.1+1` — full local builds and tests work.
- No global git identity is set on this machine; this repo's local git user is
  `KriRuo` / `kristoffer.ruohonen@gmail.com`.
- Preferred workflow for refactors: branch + small commits, compiling/testing and capturing an
  emulator screenshot after each step.
