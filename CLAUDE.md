# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Native Android 2048 in Kotlin + Jetpack Compose (Material 3), `applicationId`
`com.kriruo.game2048`. compileSdk/targetSdk 35, minSdk 24, JVM target 17.

## Current status — where to pick up

`master` is in a good, verified state: CI (`test` + `build`) green, all 91 JUnit tests pass,
working tree clean. One thing is still outstanding:

- **PR #1** (`claude/chat-session-m8ggzs`) is still open and needs attention before it's
  mergeable. It adds `TilePattern` as a second cosmetic axis past Level 30
  (Solid/Stripes/Camo/Bubbles) plus a combined Appearance dialog replacing `ThemePickerDialog`.
  Its own description says it was never actually build-verified (the session that wrote it
  couldn't reach `dl.google.com` to fetch the Android Gradle Plugin), and its base commit
  predates the entire Firebase backend below plus everything shipped since -- it needs a rebase
  onto current `master`, a real `./gradlew test assembleDebug` pass, and a fresh look at whether
  its changes still make sense next to what's landed since, before it's ready for a merge
  decision.

Everything else -- the Firebase backend (PR #2), the crash-on-launch found during phone testing
(root-caused to a missing `isCoreLibraryDesugaringEnabled`, see `app/build.gradle.kts`, and
confirmed fixed on-device), and a real sign-up/sign-in + cross-device sync round-trip -- is
merged and verified. Since PR #2 merged, the following has landed directly on `master`
(`CHANGELOG.md` has the full dated list -- this is just what changes how you'd work here):

- **Auth polish**: password reset (`AuthRepository.sendPasswordResetEmail`), sign-in/up/reset
  error messages mapped from `FirebaseAuthException` codes instead of Firebase's raw exception
  text, and a fix for a real data-integrity bug where signing out never cleared local stats, so
  a second brand-new account on the same device could silently inherit the first account's
  progress (see `KEY_LAST_SYNCED_UID` in `GameViewModel.kt`).
- **`GameMode` is now two fields, not one** -- see "Architecture" below. Closed a real bug where
  picking a different mode on the Start Screen could retroactively grant Jokers on a board
  already in progress.
- Each Joker now gives **1 use per game**, down from 2 (`MAX_TELEPORTS`/`MAX_SWAPS`/`MAX_BOMBS`/
  `MAX_DOUBLES`/`MAX_ROTATES` in `GameViewModel.kt`).
- `CloudProgress` carries `appVersionName`/`appVersionCode` now, so a Firestore query against
  `users/{uid}` can answer "which build is this account on" directly -- see `CHANGELOG.md`'s own
  header for why that's not redundant with Analytics' automatic (but anonymous) version
  dimension.
- `AppAnalytics.setUserId()` ties Crashlytics reports and Analytics events to the signed-in uid;
  new `sign_up`/`sign_in`/`game_over`/`joker_used` events joined the original
  `game_started`/`level_up`/`streak_milestone`/`*_unlocked` ones.
- **Daily Challenge**: a new mode (`logic/DailyChallengeTracker.kt`, `DailyChallengeScreen.kt`)
  -- one fixed-seed, 30-move-capped board shared by every player on a given calendar day, one
  attempt, no Undo/Jokers, +50 XP for completing it. Reachable from a new card on `StartScreen`.
  Local-only for now (not synced to Firestore).
- `CHANGELOG.md` now exists, keyed by `versionCode` (see its own header for why) -- add an entry
  there for any user-facing or architecturally-notable change, the same turn you make it.

**Recipe for sending a test APK**: check for a pre-existing SDK at `/home/user/android-sdk`
first (present in at least one recent sandbox) before installing a fresh one -- if it's missing,
install via `sdkmanager` (cmdline-tools, `platform-tools`, `platforms;android-35`,
`build-tools;35.0.0`; needs `yes | sdkmanager --licenses` first). Either way, write
`sdk.dir=<path>` to `local.properties` (gitignored, so this is needed once per fresh sandbox).
Then: `firebase_update_environment` (project_dir/active_project/active_user_account) →
`firebase_get_sdk_config` for the Android app ID → write that JSON to `app/google-services.json`
(gitignored — delete it again after the build, never commit it) → `./gradlew assembleDebug` →
`SendUserFile` the resulting APK. Firebase CLI account: `kristoffer.ruohonen@gmail.com` for
project `game2048-47897` (see "Firebase backend" below).

## Commands

```
./gradlew test                 # run all 91 JUnit tests (83 pure logic/ tests + GameViewModelTest,
                                # which uses Robolectric -- see "Architecture" -- no device/emulator needed)
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

Firebase's own automatic startup hook (`FirebaseInitProvider`) is deliberately removed in
`AndroidManifest.xml` (`tools:node="remove"`) even though `google-services.json` is present in
a real build -- it runs before any app code at all (before `MainActivity`, before
`GameViewModel`), so a bad interaction there crashes the app before a single screen is drawn,
with no try/catch of ours able to catch it (this is exactly what caused a real crash-on-launch
during phone testing). `AppAnalytics.init()` is the *only* place Firebase actually initializes
instead, reading the plugin-generated config via `FirebaseOptions.fromResource(context)` and
calling `FirebaseApp.initializeApp(...)` manually, entirely inside its own try/catch, after the
app has already started.

## Firebase backend

Project `game2048-47897`. Auth + Firestore are entirely optional cloud sync layered on top of
local play: the game is always fully playable signed-out, and every function in the three files
below fails soft (no-ops, returns null) rather than throwing when Firebase isn't configured or a
call fails. Analytics/Crashlytics don't need any of Auth/Firestore's setup below to work, but
`AppAnalytics.setUserId()` *does* tie both of them to the signed-in uid once there is one (see
`GameViewModel`'s `AuthRepository.currentUserId` collector) -- see "Architecture" for the event
catalog.

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
  field list (a subset of `GameViewModel`'s locally-persisted lifetime stats/preferences, plus
  `appVersionName`/`appVersionCode` from `BuildConfig` so a query can answer "which build is
  this account on" per-account, not just anonymously the way Analytics' own version dimension
  does).
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
  91 JUnit tests, with the HTML report uploaded as a workflow artifact) and `build`
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

### What a Claude Code session can and can't do here

The goal is minimal human touch -- read this before assuming something needs KriRuo, or
silently working around a limitation a future session will just hit again.

- **Can't dispatch a `workflow_dispatch` workflow** (`build-test-apk.yml` with a ref,
  `release-build.yml`) via the GitHub MCP tools -- `actions_run_trigger`'s `run_workflow` 403s
  with "Resource not accessible by integration." Confirmed by actually trying it (2026-09-20),
  not assumed. This is a permission on the Claude GitHub App installation for this repo
  (Settings → Integrations → Applications → the app's Actions permission), not something a
  session can grant itself. Until that's raised, triggering either workflow needs KriRuo to
  click "Run workflow" in the Actions tab -- everything *after* that (reading run status,
  downloading artifacts, checking logs) a session can do unaided via the `mcp__github__actions_*`
  tools and the `GH_TOKEN`/`GITHUB_TOKEN` already exported in the shell environment (confirmed
  working: used to poll a run to completion via `curl` without needing `gh`, which isn't
  installed here).
- **Can** deploy Firebase config (`firebase deploy --only firestore`/`--only auth`) directly via
  the `mcp__plugin_firebase_firebase__firebase_deploy` tool once `firebase_update_environment`
  points at the right project/account -- see "Firebase backend" above. Nothing about this needs
  KriRuo today, but it's also not automated in CI (no workflow deploys `firestore.rules`
  automatically on a change to `master`) -- a session doing this today does it ad hoc, on
  request, which works but is easy to forget. Worth a `firebase-deploy.yml` triggered on changes
  to `firestore.rules`/`firebase.json`, using a Firebase CI token or service-account key as a
  GitHub secret, if this comes up again.
- **Genuinely needs KriRuo, not just "hasn't been automated yet"**: the Play Console account
  itself ($25, identity verification), the first manual `.aab` upload to it (Google's own
  requirement, not a tooling gap), any billing-plan decision (see the Blaze-vs-Spark discussion
  in git history), and any GitHub App/org permission change like the one above.
- **Not yet automated, and genuinely could be** (beyond the Play Developer API upload already
  called out above): Gradle dependency updates (no Dependabot/Renovate config exists --
  version bumps like the Firebase BoM pin only happen if someone notices), a code-quality gate
  in `ci.yml` (Android Lint/ktlint/detekt -- today only tests + a compile check run, so a
  non-crashing issue passes through silently), and cleanup of `build-test-apk.yml`'s ad-hoc
  `test-<ref>-<run#>` GitHub Releases (no expiry, accumulate indefinitely).

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

**`GameViewModel` is covered by `GameViewModelTest`** via Robolectric (`testImplementation
"org.robolectric:robolectric:4.13"`, `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk =
[34])`), which runs an `AndroidViewModel` as a plain JVM unit test -- `RuntimeEnvironment
.getApplication()` gives a real (test) `Application`/`SharedPreferences` with no device/emulator
needed, so it runs in `ci.yml` like everything else. This is the pattern to extend for any
future `GameViewModel`-level behavior that the pure `logic/` package's tests can't reach --
notably, it's what would have caught the mode/board-size "Play resumes the wrong board" bugs
fixed this session, since those were `GameViewModel`/`Game2048App` wiring bugs, not engine bugs.
`isIncludeAndroidResources = true` under `android.testOptions.unitTests` is required for
Robolectric to resolve the merged manifest/resources.

**Analytics event catalog** (`AppAnalytics.kt`, all no-ops without `google-services.json`):
`game_started`, `level_up`, `streak_milestone`, `theme_unlocked`/`pattern_unlocked`/
`board_size_unlocked`, `sign_up`, `sign_in`, `game_over` (score + level), `joker_used` (which
Joker). `AppAnalytics.setUserId()` ties all of these, plus Crashlytics crash reports, to the
signed-in Firebase Auth uid (never PII) once `GameViewModel`'s sign-in collector sees one, so a
specific tester's bug report can be correlated to a crash or an event stream instead of staying
anonymous per device.

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
- `DailyChallengeTracker` — the daily challenge's completion state (last/best score) and its
  per-day seed derivation; see the Daily Challenge paragraph below.

`GameMode` (ORIGINAL vs. EXTENDED) changes which actions the UI exposes (Undo, Jokers). It's two
separate fields on `GameUiState`, not one: `gameMode` is locked to whichever board is actually
active (set once, at New Game) and is what actually gates Undo/Jokers; `selectedGameMode` is
just the Start Screen's preference for the *next* New Game, same pattern as
`selectedBoardSize`/`selectedPalette`. `Game2048App`'s `onPlay` compares the two and starts a
fresh board whenever they differ, the same way it already does for a game-over board -- so
picking a different mode can never retroactively grant (or take away) Jokers/Undo on a board
already in progress. The active board's mode is persisted on its own (`KEY_ACTIVE_GAME_MODE`,
written at New Game) rather than recomputed from the current preference on every launch.

**Daily Challenge** is a separate, smaller game mode living almost entirely outside the above:
`GameViewModel` keeps its board (`dailyChallengeGame`) and a separately-seeded `Game2048Engine`
instance (`Game2048Engine(random = Random(DailyChallengeTracker.seedFor(epochDay)))`) entirely
apart from the regular, persisted `game`/`engine` -- same local-calendar-day convention as
`StreakTracker`, but every player gets an identical board and spawn sequence for the same day,
capped at `DailyChallengeTracker.MOVE_CAP` moves, no Undo/Jokers, one attempt per day. Its board
isn't persisted across a process restart (killing the app mid-attempt just restarts today's
identical seed from scratch) -- same not-a-big-deal tradeoff `MAX_UNDOS` already makes.
Deliberately not folded into the existing streak (see `CHANGELOG.md`'s entry for why) and not
synced to Firestore yet.

**Compose UI is split by concern**, not by screen-per-file convenience — each file below owns
one piece of the visual/interaction surface: `MainActivity` (entry point) → `GameScreen`
(Start vs. Game vs. Daily Challenge nav + in-game layout) → `StartScreen` (mode/theme/board-
size/stats/account entry points) / `GameChrome` (header/sidebar + score chip) / `GameBoardUi`
(grid, animated tiles, swipe gestures -- its `Board` composable is `internal`, reused as-is by
`DailyChallengeScreen`) / `JokerUi` (aiming banner + action bar) / `GameOverlays` (combo popup,
streak banner, win/game-over overlay) / `DailyChallengeScreen` (the daily challenge's own small
screen -- see "Architecture" above), with `ThemePickerDialog`, `BoardSizePickerDialog`,
`StatsDialog`, `WelcomeDialog` (first-run "How to Play" walkthrough), `DailyRewardDialog`
(claimable streak bonus-XP), and `AccountDialog` (sign up/in/out, password reset -- opened from
the ☁️/🔒 icon) as the picker/info dialogs opened from `StartScreen`, plus `ConfirmNewGameDialog`
opened from the in-game New Game button. `ui/theme/` holds the Material3 theme wiring (colors
per palette, typography).

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
