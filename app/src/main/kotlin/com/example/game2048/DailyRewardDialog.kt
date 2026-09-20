package com.example.game2048

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Claimable "you showed up today" reward, shown on [StartScreen] whenever
 *  [GameUiState.pendingDailyReward] is set (see [GameViewModel.buildInitialState]) -- after
 *  [WelcomeDialog] if that's also showing, so a brand-new install sees the walkthrough first.
 *  [onClaim] is wired to *both* the button and the dialog's dismiss request, so tapping outside
 *  it still grants the reward rather than silently losing it -- this is pure upside with no
 *  choice to make, not a real confirm/cancel decision. */
@Composable
internal fun DailyRewardDialog(streakDay: Int, rewardXp: Int, onClaim: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClaim,
        title = { Text("Day $streakDay streak! 🔥") },
        text = { Text(dailyRewardMessage(streakDay, rewardXp)) },
        confirmButton = {
            TextButton(onClick = onClaim) { Text("Claim +$rewardXp XP") }
        }
    )
}

/** Scales with [streakDay] the same way [StreakTracker.dailyBonusXp]'s reward amount does, so
 *  the copy's energy tracks the actual milestone rather than saying the same thing on day 1
 *  and day 30. Day 1 gets its own line since "back already" doesn't make sense on a first
 *  visit -- [StartScreen] shows a plain "N day streak" instead, without this escalating tone. */
private fun dailyRewardMessage(streakDay: Int, rewardXp: Int): String = when {
    streakDay <= 1 -> "Nice start. Take $rewardXp XP to kick things off."
    streakDay < 7 -> "Back already? Look at you. Take $rewardXp XP."
    streakDay < 30 -> "Day $streakDay and still going. Take $rewardXp XP."
    else -> "Day $streakDay. Absolutely unstoppable. Take $rewardXp XP."
}
