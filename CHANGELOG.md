# Changelog

Entries here are keyed by **`versionCode`** (see `app/build.gradle.kts`), not `versionName`
(a static `"1.0"` until someone bumps it by hand) — this is the same identifier
`CloudSyncRepository` writes to every signed-in account's Firestore doc as `appVersionCode`, and
that Crashlytics automatically tags every crash report with. So when a bug report says "I'm
seeing X", checking their account's (or their crash's) `versionCode` against a dated entry below
tells you exactly what build they're on, without reconstructing git history. Firebase Analytics'
own automatic app-version dimension is `versionName`, not `versionCode` — since `versionName`
stays static at `"1.0"` here, Analytics events can't currently be matched to a specific entry
this way; log `versionCode` as an explicit event parameter first if that's ever needed.

Local and CI (`ci.yml`) builds never pass `-PversionCode`, so they're always `versionCode = 1` —
only a `release-build.yml` run produces a real, meaningful number (it passes `github.run_number`
for `-PversionCode`, so numbers only start moving once that workflow is actually run; see
"Play Store rollout" in `CLAUDE.md`). Add a new `## versionCode N` section here as part of the
same change that triggers the next `release-build.yml` run, moving `Unreleased` entries into it.

## Unreleased

- Redesign the Start Screen's information hierarchy so Play is the obvious first tap instead of
  competing with five other elements at similar visual weight: a large filled `PlayCard`
  (replacing the outlined Play button plus the two full-size Original/Extended mode cards)
  shows the active mode/board size as a small "Extended · 4×4 ˅" control that opens a new compact
  `GameModePickerDialog`; the verbose streak line ("Day 3 — back already? Look at you.") is now a
  small "🔥 3 day streak" near the bottom; Daily Challenge and the theme/board-size/stats/help/
  account utility icons are unchanged functionally but visually de-emphasized (a slimmer row,
  smaller icons). No state, navigation, or persistence logic changed — `selectedGameMode`,
  `selectedBoardSize`, and every existing callback are reused as-is.
- Fix Play not starting a fresh board when only the board-size preference changed (e.g. picking
  8x8 after finishing/leaving a 4x4 game resumed the old 4x4 board instead of starting a new
  8x8 one, until the in-game New Game button was tapped separately). `Game2048App`'s onPlay now
  compares the resolved size preference (`GameViewModel.resolvedBoardSize()`, accounting for
  unlock level the same way `onNewGame` does) against the in-progress board's actual size, the
  same way it already does for a mismatched game mode.
- Add a Daily Challenge: a fixed-seed, move-capped (30 moves) board that's identical for every
  player on a given calendar day, one attempt per day, no Undo/Jokers regardless of the player's
  own mode, +50 XP for completing it. Reachable from a new card on the Start Screen
  (`DailyChallengeTracker`, `DailyChallengeScreen`, `GameViewModel.onStartDailyChallenge`/
  `onDailyChallengeSwipe`). Local-only for now — score/best aren't synced to Firestore yet.
- Tie Crashlytics crash reports and Analytics events to the signed-in account
  (`AppAnalytics.setUserId`, called from `GameViewModel`'s existing sign-in/out collector) —
  previously both were anonymous per device, with no way to correlate a specific tester's bug
  report to a crash, or to answer cross-device questions like "do people who sign in come back
  more?"
- Log `sign_up`/`sign_in`/`game_over` (with score and level)/`joker_used` (with which Joker)
  analytics events, alongside the existing `game_started`/`level_up`/`streak_milestone`/
  `*_unlocked` events — answers product questions like "do people who sign in come back more?"
  or "which Jokers actually get used?" that weren't answerable before.
- Track which app version each synced account is running (`CloudProgress.appVersionName`/
  `appVersionCode`).
- Limit each Joker to a single use per game; stop a mode switch from granting Jokers on an
  in-progress board.
- Make sign-in/sign-up/reset error messages user-friendly.
- Fix crash-on-launch on API 24/25 devices (missing core library desugaring for Firestore/Auth's
  gRPC dependency on `java.time`); add an on-device crash-capture dialog as a fallback way to get
  a stack trace off a device without `adb`.
- Add password reset; fix account-switch progress bleed between signed-in accounts.
- Add Firebase Auth (Email/Password) + Firestore cloud sync, and Firebase Analytics/Crashlytics
  (both off by default — no-op without `app/google-services.json`).
