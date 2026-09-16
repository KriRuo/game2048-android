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
        text = {
            Text(
                "Great, you showed up today! Claim $rewardXp bonus XP toward your next " +
                    "Level -- come back tomorrow for more."
            )
        },
        confirmButton = {
            TextButton(onClick = onClaim) { Text("Claim +$rewardXp XP") }
        }
    )
}
