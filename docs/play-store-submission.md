# Play Store submission notes

Everything needed to fill in the Play Console, written against what the app **actually does**
as of the "Drop accounts and cloud sync, gate analytics behind explicit consent" change. If the
app's data handling changes, update this file, `docs/privacy-policy.html` and the Data Safety
form together — Google requires the form and the policy to agree, and a mismatch is a rejection.

---

## 1. Privacy policy URL

`docs/privacy-policy.html` is written to be served by GitHub Pages straight from this repo:

1. Repo **Settings → Pages**
2. Source: **Deploy from a branch**, branch `master`, folder **`/docs`**
3. The URL becomes `https://kriruo.github.io/game2048-android/privacy-policy.html`

**Before publishing:** replace the contact-email placeholder at the bottom of that file. Google
requires a working contact route, and a policy with a placeholder in it will fail review.

---

## 2. Data Safety form answers

Reach it via **Play Console → App content → Data safety**.

**Does your app collect or share any of the required user data types?** → **Yes**
(Analytics and Crashlytics both transmit off-device. "No" would be false even though nothing
identifies the player.)

**Is all of the user data collected by your app encrypted in transit?** → **Yes**
(Firebase uses HTTPS.)

**Do you provide a way for users to request that their data is deleted?** → **Yes**
(The in-app opt-out stops collection; the policy gives a contact route for deletion requests.)

### Data types to declare

| Category | Data type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|---|
| App activity | App interactions | Yes | No | **Yes** (user can decline) | Analytics |
| App info & performance | Crash logs | Yes | No | **Yes** | Crash reporting |
| App info & performance | Diagnostics | Yes | No | **Yes** | Crash reporting, Analytics |
| Device or other IDs | Device or other IDs | Yes | No | **Yes** | Analytics |

Mark every row **"Users can choose whether this data is collected"** — that is exactly what the
first-run consent dialog provides, and it is the honest answer.

**Do NOT tick:** Name, Email address, User IDs, Address, Phone number, Race/ethnicity, Political
or religious beliefs, Sexual orientation, Other personal info, Financial info, Health, Fitness,
Messages, Photos, Videos, Audio, Files, Calendar, Contacts, App performance→other, Location,
Web browsing, Purchase history, Search history, Installed apps.

> The "Device or other IDs" row is the one people miss. Firebase Analytics generates a random
> app-instance ID, which counts as a device identifier even though it is not tied to a person.

---

## 3. Content rating questionnaire

**App content → Content rating.** Category: **Game**. Answer no to every content question —
violence, sexuality, language, controlled substances, gambling (there is no simulated gambling;
Jokers are not wagering), user interaction, personal information sharing, location sharing.
Expected outcome: PEGI 3 / ESRB Everyone / equivalent.

---

## 4. Store listing copy

**App name (30 chars max)**
```
2048 — Levels & Jokers
```

**Short description (80 chars max)**
```
Classic 2048, plus levels, daily streaks, jokers and boards up to 8x8.
```

**Full description (4000 chars max)**
```
Slide, merge, and chase that 2048 tile — then keep going.

This is the 2048 you know, built properly for Android and quietly deeper than
the browser version you remember. Every tile slides and merges with real
animation, so a good run feels as good as it looks.

CLASSIC OR EXTENDED
Play Original for the pure, unforgiving puzzle. Or play Extended, which adds an
Undo and five Jokers for when a board goes wrong:

• Teleport — move any tile to an empty cell
• Swap — exchange two tiles to unlock a merge
• Bomb — remove a tile that's in the way
• Double — double a tile's value
• Rotate — turn the whole board 90 degrees

A well-timed Joker can rescue a board that looked finished.

KEEP PLAYING, KEEP UNLOCKING
Every point you score counts toward your player Level — and it never resets when
a game does. Levelling up unlocks bigger boards (5x5, 6x6 and a genuinely
ridiculous 8x8) and new colour themes, from warm Clay through to neon Cyber.

DAILY CHALLENGE
One board, the same for everyone, every day. A short, sharp run with a move
limit and a score to beat.

STREAKS
Play on consecutive days to build a streak and earn bonus XP, with milestones
along the way.

NO NONSENSE
No ads. No purchases. No sign-up. No account. Play offline, forever.
Optional anonymous crash and usage reporting, which you're asked about once on
first launch and can switch off at any time.
```

**Category:** Games → Puzzle
**Tags:** puzzle, casual, number game, offline
**Contains ads:** No
**In-app purchases:** No

---

## 5. Assets still needed

| Asset | Requirement |
|---|---|
| App icon | 512 × 512 PNG, 32-bit |
| Feature graphic | 1024 × 500 PNG or JPEG, no transparency |
| Phone screenshots | 2–8, min 320px on the short side, 16:9 or 9:16 |

Good screenshot candidates: the Start Screen showing Level and streak; a mid-game 4×4 board with
a high tile; the Joker action bar mid-aim; an 8×8 board; the Daily Challenge screen.

---

## 6. The part that takes weeks

This account is a **personal** Play Console account created after 13 November 2023, so before it
can publish publicly Google requires a **closed test with at least 12 testers, opted in
continuously for 14 days**, who actually install and use the app. An email address on the tester
list that never clicked the opt-in link does not count.

Start recruiting before the code is finished — it is the longest item by far, and nothing about
the app can shorten it. Organisation accounts are exempt, but require business verification
(D-U-N-S number), which is a larger and slower piece of paperwork than the test.

**Order of operations:** create the developer account (£/$25, plus identity verification that
can take days) → fill the forms above → upload a bundle to a closed testing track → send testers
the opt-in link → wait out the 14 days → apply for production access.
