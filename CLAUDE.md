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

Everything else -- the Firebase backend (PR #2) and the crash-on-launch found during phone
testing (root-caused to a missing `isCoreLibraryDesugaringEnabled`, see `app/build.gradle.kts`,
and confirmed fixed on-device) -- is merged and verified. Since PR #2 merged, the following has
landed directly on `master` (`CHANGELOG.md` has the full dated list -- this is just what changes
how you'd work here):

- **Accounts and cloud sync are gone again** (Play Store prep): Firebase Auth and Cloud Firestore
  were pulled back out of the app entirely -- `AuthRepository.kt`, `CloudSyncRepository.kt` (and
  the `CloudProgress` model it carried) and `AccountDialog.kt` deleted, the whole auth/sync
  surface on `GameViewModel` and `AppAnalytics` with them, and `firebase-auth`/
  `firebase-firestore`/`kotlinx-coroutines-play-services` dropped from `app/build.gradle.kts`.
  The reason is the Data Safety declaration and privacy policy Google requires for a store
  listing: an email/password account drags email addresses and a persistent player identity into
  both, and a single-player puzzle gets far too little back for that. Progress is device-local
  again, so `SharedPreferences` is once more the only persistence. Treat this as deferred rather
  than abandoned -- the Firebase-side config was deliberately left in place, dormant (see
  "Firebase backend" below), so cloud save can return in a later release without re-deriving it.
  Analytics and Crashlytics were not touched and are still fully wired up.
- **`GameMode` is now two fields, not one** -- see "Architecture" below. Closed a real bug where
  picking a different mode on the Start Screen could retroactively grant Jokers on a board
  already in progress.
- Each Joker now gives **1 use per game**, down from 2 (`MAX_TELEPORTS`/`MAX_SWAPS`/`MAX_BOMBS`/
  `MAX_DOUBLES`/`MAX_ROTATES` in `GameViewModel.kt`).
- New `game_over`/`joker_used` analytics events joined the original
  `game_started`/`level_up`/`streak_milestone`/`*_unlocked` ones. Everything Analytics and
  Crashlytics report is anonymous per install -- there is no longer a uid to attribute any of it
  to, and deliberately so; see "Architecture" for the full event catalog.
- **Daily Challenge**: a new mode (`logic/DailyChallengeTracker.kt`, `DailyChallengeScreen.kt`)
  -- one fixed-seed, 100-move-capped board shared by every player on a given calendar day, one
  attempt, no Undo/Jokers, +50 XP for completing it. Reachable from a new card on `StartScreen`.
  Local-only, like every other piece of progress the app stores.
- `CHANGELOG.md` now exists, keyed by `versionCode` (see its own header for why) -- add an entry
  there for any user-facing or architecturally-notable change, the same turn you make it.

### Pending action items for KriRuo

Everything automatable in the DevOps setup is done (see "What a Claude Code session can and
can't do here" under CI/CD) -- what's left is genuinely human-only, roughly in priority order:

1. **Verify the 4 release-signing secrets exist**: `RELEASE_KEYSTORE_BASE64`,
   `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` under Settings →
   Secrets and variables → Actions -- `release-build.yml` needs them and was confirmed working
   end-to-end earlier, but worth a quick check if it ever goes red.
2. **Generate and add `FIREBASE_TOKEN`** -- no longer on the critical path, since the app itself
   no longer talks to Auth or Firestore and nothing `firebase-deploy.yml` deploys is read by a
   client today. `firebase login:ci` (as `kristoffer.ruohonen@gmail.com`) plus the token as a
   repo secret would turn that workflow on, auto-deploying `firestore.rules`/Auth config on a
   `master` push that touches them; worth doing if and when cloud save comes back, not before.
3. **Create the Google Play Console account** ($25, identity verification) -- blocks everything
   below. See "Play Store rollout" for the full store-listing checklist (privacy policy,
   screenshots, content rating, data safety form) once the account exists.
4. **Generate and add `PLAY_SERVICE_ACCOUNT_JSON`** once an app listing exists in Play Console
   (Setup → API access → create a service account with publish rights → download its JSON key).
   Turns on the dormant upload step in `release-build.yml` (internal testing track) with no
   further code changes.
5. **Grant the Claude GitHub App `Actions: write` permission** (Settings → Integrations →
   Applications → the app's Actions permission), if you want a session to be able to trigger
   `release-build.yml`/`build-test-apk.yml` itself instead of you clicking "Run workflow"
   manually in the Actions tab. Purely a convenience item, not a blocker for anything above.

### Proposed feature (paused, pending engagement data): Daily Challenge expansion

Discussed but deliberately not built yet: 6 additional challenge archetypes beyond the current
flat score-attack (Tile Target, Speed Run, Merge Count, Ascending Row, Cluster Match, rotating
per-day via a shuffled 6-day cycle seeded off `epochDay`) plus a per-day Firestore leaderboard.
The 6 archetypes are pure `logic/` work and could still ship on their own. The leaderboard half
(`dailyLeaderboards/{epochDay}/entries/{uid}`, gated on `request.auth != null` for both read and
write, entries validated but not server-verified -- scores honor-system/spoofable without a paid
Cloud Function, out of scope given the Spark-plan-only constraint elsewhere in this doc) now
presupposes that accounts and Firestore come back at all, since both were dropped for the Play
Store release, so it is blocked on that decision first and on engagement data only after it.
Paused originally because there was no data on whether the *existing* Daily Challenge gets used
at all before committing that much engineering to it --
`AppAnalytics.logDailyChallengeCompleted(score)` (the `daily_challenge_completed` event) was
added for exactly this reason. Revisit the 6-types-plus-leaderboard bundle once that event shows
real engagement; a leaderboard specifically needs a critical mass of players to not feel worse
than having none (a "you're #2 of 3" empty-feeling leaderboard is worse UX than no leaderboard).
What *did* ship now: `DailyChallengeTracker.MOVE_CAP` raised from 30 to 100 (30 ended before the
board got interesting) and the `daily_challenge_completed` analytics event itself.

### Proposed feature (not started): Push notifications

Re-engagement nudges via Firebase Cloud Messaging — not scoped or built yet, just captured here
so a future session (or KriRuo) has a starting point instead of re-deriving it. Candidate
triggers: "your streak resets at midnight" (purely local — no server needed, just a scheduled
local notification via `WorkManager`/`AlarmManager` computed from `StreakTracker`'s existing
local-calendar-day logic), "you haven't played in N days" (needs a source of truth for *last
played*; with Auth and Firestore out of the app there is no server-side state at all any more, so
this is necessarily local-only per-device like the streak reminder unless cloud save returns),
and "a new
Daily Challenge is up" (same local-only option, since the challenge's own seed is already
locally derivable from the date — no server round-trip needed to know today's challenge exists).
None of these strictly require Firebase Cloud Messaging or a server component; the local-only
versions are the cheaper starting point and fit the app's now entirely local, account-free design
better than a push-from-server approach would. If cross-device or truly
server-triggered notifications are wanted later, that needs a Cloud Function (or Firebase's own
Cloud Messaging campaign scheduling) plus the Android `POST_NOTIFICATIONS` runtime permission
(API 33+) and a `firebase-messaging` dependency, none of which exist in this repo yet.

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

Firebase (Analytics and Crashlytics — all that is left of it, see "Firebase backend" below)
follows the same optional-file pattern as release signing: an optional, gitignored
`app/google-services.json`. No file present means the `com.google.gms.google-services` Gradle
plugin is simply never applied (see the `apply(plugin = ...)` conditional in
`app/build.gradle.kts` — it can't live in the `plugins {}` block itself, which is statically
evaluated before the rest of the script and can't reference a `val` or even `java.io.File`),
`BuildConfig.FIREBASE_ENABLED` is `false`, and every call into `AppAnalytics` no-ops or fails
soft — true for every fresh clone and for CI today, verified by building with the file
removed. Fetch a real one via
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

Project `game2048-47897`. What the app actually uses today is **Analytics and Crashlytics, and
nothing else** — both optional, both anonymous per install, both no-ops without
`app/google-services.json` (see "Commands" above), and neither needs any per-player setup at all.

**Both are off until the player says yes.** `AndroidManifest.xml` ships
`firebase_analytics_collection_enabled` and `firebase_crashlytics_collection_enabled` as
`false`, so collection can't start merely because Firebase initialized;
`AppAnalytics.applyConsent()` is the only thing that ever turns it on, called from
`AppAnalytics.init()` with the stored answer and again from
`GameViewModel.onAnalyticsConsentChanged()`. The answer lives in `KEY_ANALYTICS_CONSENT` and is
deliberately **tri-state** — `true`, `false`, or absent — because "hasn't been asked" has to
keep re-prompting while "said no" must not. `AnalyticsConsentDialog` asks on first launch,
gating `WelcomeDialog` behind it (nothing may be collected before the question is answered, and
two stacked first-run modals is a mess), and the same decision is permanently re-settable via
the switch in `StatsDialog` — which is the app's only `Switch`, put there rather than behind a
fifth Start Screen icon because Your Stats is already the screen about the player's own data.
This is what makes the Play Data Safety declaration's "users can choose" answer honest; see
`docs/play-store-submission.md`.

Accounts and cloud sync used to live here too — Firebase Auth (Email/Password) plus a
`users/{uid}` Firestore document holding a snapshot of lifetime progress — and were removed
wholesale ahead of the first Play Store release. The trade wasn't close: an email/password
account pulls email addresses and a persistent player identity into Google's Data Safety
declaration and into the privacy policy that has to back it, and a single-player puzzle whose
entire state fits in `SharedPreferences` gets very little in return. Dropping Auth and Firestore
shrinks both documents to "app-usage analytics and crash reports, anonymous". Progress is
device-local again, exactly as "Architecture" describes it.

This is deferred, not abandoned, and the Firebase-side half is deliberately still committed:
`firestore.rules`, `firestore.indexes.json`, `firebase.json`, `.firebaserc` and
`.github/workflows/firebase-deploy.yml` all remain in the repo, dormant — nothing the app reads
goes through any of them today. They're kept so that bringing cloud save back is a matter of
writing a client again rather than re-deriving a schema, a rules file and a deploy pipeline, so
don't "tidy them up" by deleting them. Two details in there are load-bearing for any future
client: the Firestore database is named **`game2048-db`**, not `(default)` (this project has no
default database, so access must go through the `FirebaseFirestore.getInstance("game2048-db")`
overload, and `firebase.json`'s `firestore` block needs its explicit `"database": "game2048-db"`
for CLI commands to target the right one), and `firestore.rules` already enforces the sync model
that went with it — `users/{uid}` readable/writable only by `request.auth.uid == uid`, every
write schema-validated, and `cumulativeScore`/`bestScore`/`highestTileEver`/`totalMerges` unable
to decrease versus the stored document, with no `list` and no `delete` rule at all.

**Local dev / CI setup**: `.firebaserc` + `firebase.json` are committed (project ID and this
config aren't secret); `app/google-services.json` is not (see above) — fetch your own via the
Firebase CLI, logged in as an account with access to project `game2048-47897`.

## CI/CD

Six workflows under `.github/workflows/`:

- **`ci.yml`** — runs on every push to `master` and every pull request targeting `master`.
  Three independent jobs, each its own status check on the PR: `test` (`./gradlew test`, the
  91 JUnit tests, with the HTML report uploaded as a workflow artifact), `build`
  (`./gradlew assembleDebug`, a compile-only sanity check), and `lint` (`./gradlew lintDebug`,
  HTML report uploaded as an artifact). A red check here is what used to require asking Claude
  to run tests/build manually — it's now automatic and visible directly on the PR/commit.
  `lintDebug`'s default `abortOnError` only fails the job on Error-severity findings — as of
  2026-09-20 this repo sits at 40 Warning + 2 Information findings (mostly `GradleDependency`,
  `ApplySharedPref`, `UnusedResources`) and 0 errors, so the gate starts green; it exists to
  catch a *new* Error-severity issue, not to enforce zero warnings.

- **`build-test-apk.yml`** — publishes a debug APK as a **GitHub Release** so invited
  collaborators can install a test build on a phone without a local Android toolchain:
  - On every push to `master`: builds and updates a single rolling release tagged
    `latest-master` (same URL always has the newest build —
    `https://github.com/KriRuo/game2048-android/releases/tag/latest-master`).
  - On manual trigger (Actions tab → "Build test APK" → "Run workflow", optionally naming a
    branch/PR ref): builds that ref and publishes a separate release tagged
    `test-<ref>-<run#>`, so ad-hoc test builds don't clobber `latest-master`.
  - Both releases are marked as debug/unsigned builds not meant for wider distribution.

- **`cleanup-test-releases.yml`** — scheduled (weekly, Mondays 03:00 UTC) plus manually
  dispatchable, prunes old ad-hoc `test-<ref>-<run#>` GitHub Releases created by
  `build-test-apk.yml`'s manual-trigger path, keeping the 10 most recent and never touching
  `latest-master`. Uses the workflow's own built-in `GITHUB_TOKEN` — no secret to configure.

- **`firebase-deploy.yml`** — runs on push to `master` when `firestore.rules`,
  `firestore.indexes.json`, `firebase.json`, or `.firebaserc` change (plus manual dispatch),
  and deploys Firestore rules + Auth config via `firebase-tools`. Effectively dormant since Auth
  and Firestore were removed from the app — nothing it deploys is read by a client any more — but
  kept wired up for the same reason those config files are (see "Firebase backend" above). Needs
  a `FIREBASE_TOKEN`
  secret (generate with `firebase login:ci`, add under Settings → Secrets and variables →
  Actions) — until that secret exists the job no-ops with a `::warning::` annotation instead of
  failing, so this workflow is safe to have merged before the secret is added.

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
  deletes both before the job ends. Secrets reach those steps through `env:` rather than being
  interpolated into the `run:` script: the shell doesn't re-expand a variable's *value*, so a
  password containing `$` or a backtick survives, where the old inlined heredoc mangled it.
  It also writes `app/google-services.json` from `GOOGLE_SERVICES_JSON_BASE64` and passes
  `-PrequireFirebase=true`. That pairing is load-bearing: before it, a CI bundle built without
  that file compiled and ran fine but shipped `FIREBASE_ENABLED=false`, so it collected nothing
  at all, silently — with the Play upload step already wired to publish it. `app/build.gradle.kts`
  now turns that into a hard build failure, while a fresh clone doing a local `assembleRelease`
  (no such flag) still works. Unit tests run before the bundle, since this path can publish.
  Output is uploaded as a workflow artifact named
  `app-release-bundle`, and — new this session — also has a dormant Play Store upload step
  (`r0adkll/upload-google-play@v1`, targeting the `internal` track) gated on a
  `PLAY_SERVICE_ACCOUNT_JSON` secret via `env.HAS_PLAY_CREDENTIALS` (checked as an env var
  rather than the secret directly in `if:`, which is more reliable in Actions). That secret
  doesn't exist yet — see "Play Store rollout" below — so today this step is a clean no-op and
  the artifact upload is still the only real output; once the secret is added, no workflow
  change is needed for uploads to start happening automatically.

- **`dependabot.yml`** — weekly version-bump PRs for the `gradle` (AGP/Kotlin/Compose/Firebase
  BoM, etc.) and `github-actions` ecosystems. Purely additive — opens PRs against `master`,
  doesn't merge anything itself; `ci.yml`'s checks still gate them like any other PR.

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
4. **Add `GOOGLE_SERVICES_JSON_BASE64` as a repo secret** (`base64 -w0 app/google-services.json`).
   Without it `release-build.yml` now fails outright rather than doing what it used to: quietly
   producing a bundle with `FIREBASE_ENABLED=false` that collected nothing. See "CI/CD" above.
5. ~~Create a Firebase project~~ — done: project `game2048-47897`. Only Analytics and Crashlytics
   are used by the app now; the Firestore database and Auth provider still exist server-side but
   nothing calls them (see "Firebase backend" above). Every dev machine needs its own
   `app/google-services.json` via the Firebase CLI, since that file isn't committed.
6. Store listing requirements — all drafted in `docs/play-store-submission.md`, which carries the
   exact Data Safety answers, the listing copy, and the asset sizes still needed. The privacy
   policy is `docs/privacy-policy.html`, ready to serve via GitHub Pages (Settings → Pages →
   branch `master`, folder `/docs`); **its contact-email placeholder must be filled in first.**
   Data Safety is "yes, data is collected": Analytics app-interaction events, Crashlytics crash
   logs and diagnostics, and a Firebase-generated device identifier — all marked optional, since
   the first-run consent dialog lets players decline. No personal data, no accounts.
7. **Closed testing is the long pole:** a personal Play Console account created after
   2023-11-13 must run a closed test with **12+ testers opted in continuously for 14 days**, who
   actually install and use the app, before it can apply for production access. Start recruiting
   before the code is ready — nothing about the app shortens this.
8. First `.aab` upload to Play Console must be manual (Google requires this before any API
   automation can target that app listing) — grab the artifact from a `release-build.yml` run.

**Next technical step once an app exists in Play Console:**
- ~~Add Play Developer API upload to CI~~ — done: `release-build.yml` already has a
  `r0adkll/upload-google-play@v1` step targeting the **internal testing track**, gated on a
  `PLAY_SERVICE_ACCOUNT_JSON` secret that doesn't exist yet (see "CI/CD" above) — so the only
  remaining step, once an app exists in Play Console, is generating that service-account JSON
  key in Play Console (Setup → API access) and adding it as that secret. No workflow code
  change needed at that point.

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
  points at the right project/account -- see "Firebase backend" above. This is still useful for
  an immediate, ad-hoc deploy mid-session, but `firebase-deploy.yml` now also auto-deploys on
  every push to `master` that touches `firestore.rules`/`firestore.indexes.json`/
  `firebase.json`/`.firebaserc`, once its `FIREBASE_TOKEN` secret exists (see "CI/CD" above) --
  that token is the one piece KriRuo needs to generate (`firebase login:ci`, logged in as an
  account with access to project `game2048-47897`) and add under Settings → Secrets and
  variables → Actions; a session should not attempt to mint or store that token itself.
- **Genuinely needs KriRuo, not just "hasn't been automated yet"**: the Play Console account
  itself ($25, identity verification), the first manual `.aab` upload to it (Google's own
  requirement, not a tooling gap), any billing-plan decision (see the Blaze-vs-Spark discussion
  in git history), any GitHub App/org permission change like the one above, generating and
  adding the `FIREBASE_TOKEN` secret (a session could technically run `firebase login:ci`
  itself, but that would mint a long-lived credential tied to KriRuo's Firebase account without
  him watching it happen -- better for him to run it), and generating/adding the
  `PLAY_SERVICE_ACCOUNT_JSON` secret once an app exists in Play Console (Play Console → Setup →
  API access; a service-account key with publish rights on a specific app, same reasoning as
  the Firebase token).
- **Done this session, previously "not yet automated"**: Dependabot (`.github/dependabot.yml`,
  weekly, `gradle` + `github-actions` ecosystems), a lint gate in `ci.yml` (`lintDebug`, see
  "CI/CD" above), a scheduled cleanup of `build-test-apk.yml`'s ad-hoc `test-<ref>-<run#>`
  GitHub Releases (`cleanup-test-releases.yml`), a `firebase-deploy.yml` for Firestore
  rules/Auth config (dormant until `FIREBASE_TOKEN` exists), and the Play Store upload step
  itself in `release-build.yml` (dormant until `PLAY_SERVICE_ACCOUNT_JSON` exists). What's left
  genuinely not automated: an Android instrumented-test (`androidTest`)/Compose UI test suite
  (still zero — `GameViewModelTest` covers ViewModel wiring via Robolectric, but no test drives
  actual Compose UI), and ktlint/detekt style enforcement (Android Lint's `lintDebug` catches
  correctness/a11y/perf issues, not Kotlin style).

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
`board_size_unlocked`, `game_over` (score + level), `joker_used` (which Joker),
`daily_challenge_completed` (score) -- this last one exists specifically to answer "does anyone
actually play the Daily Challenge?" before investing further in it (more challenge types, a
leaderboard); see the Daily Challenge paragraph below. Both these events and Crashlytics crash
reports are **anonymous per install**: no user id is ever set on either, because there are no
accounts to set one from since Auth was removed. That means a specific tester's crash can't be
correlated back to them by identity -- a real loss, and the deliberate price of keeping the Play
Store Data Safety declaration free of personal data. Isolate a tester's stream by build (the
releases `build-test-apk.yml` publishes) or by asking them, not by reintroducing an identifier.

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
Deliberately not folded into the existing streak (see `CHANGELOG.md`'s entry for why); its
score/best history, like every other piece of progress, never leaves the device.

**Compose UI is split by concern**, not by screen-per-file convenience — each file below owns
one piece of the visual/interaction surface: `MainActivity` (entry point) → `GameScreen`
(Start vs. Game vs. Daily Challenge nav + in-game layout) → `StartScreen` (mode/theme/board-
size/stats entry points) / `GameChrome` (header/sidebar + score chip) / `GameBoardUi`
(grid, animated tiles, swipe gestures -- its `Board` composable is `internal`, reused as-is by
`DailyChallengeScreen`) / `JokerUi` (aiming banner + action bar) / `GameOverlays` (combo popup,
streak banner, win/game-over overlay) / `DailyChallengeScreen` (the daily challenge's own small
screen -- see "Architecture" above), with `ThemePickerDialog`, `BoardSizePickerDialog`,
`StatsDialog`, `WelcomeDialog` (first-run "How to Play" walkthrough), and `DailyRewardDialog`
(claimable streak bonus-XP) as the picker/info dialogs opened from `StartScreen` -- its
`UtilityRow` is four icons wide again (Theme 🎨, board size, Stats 📊, How to Play ❓) now that the
☁️/🔒 account icon is gone -- plus `ConfirmNewGameDialog` opened from the in-game New Game
button. `ui/theme/` holds the Material3 theme wiring (colors per palette, typography).

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
