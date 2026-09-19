# Changelog

Entries here are keyed by **`versionCode`** (see `app/build.gradle.kts`), not `versionName`
(a static `"1.0"` until someone bumps it by hand) — this is the same identifier
`CloudSyncRepository` writes to every signed-in account's Firestore doc as `appVersionCode`, and
that Firebase Analytics/Crashlytics already tag every event/crash with automatically. So when a
bug report says "I'm seeing X", checking their account's (or their crash's) version against a
dated entry below tells you exactly what build they're on, without reconstructing git history.

Local and CI (`ci.yml`) builds never pass `-PversionCode`, so they're always `versionCode = 1` —
only a `release-build.yml` run produces a real, meaningful number (it passes `github.run_number`
for `-PversionCode`, so numbers only start moving once that workflow is actually run; see
"Play Store rollout" in `CLAUDE.md`). Add a new `## versionCode N` section here as part of the
same change that triggers the next `release-build.yml` run, moving `Unreleased` entries into it.

## Unreleased

- Tie Crashlytics crash reports to the signed-in account (`AppAnalytics.setUserId`, called from
  `GameViewModel`'s existing sign-in/out collector) — crashes were previously anonymous per
  device with no way to correlate a specific tester's bug report to a dashboard entry.
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
