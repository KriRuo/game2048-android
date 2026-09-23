package com.example.game2048

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * First-run ask for anonymous analytics/crash reporting. Shown before [WelcomeDialog] and before
 * anything is collected -- Firebase ships disabled via the manifest and is only ever switched on
 * by an explicit yes here (see [AppAnalytics.applyConsent]).
 *
 * Deliberately not dismissable by tapping outside: "didn't answer" and "said no" are different
 * states, and leaving the question unanswered means asking again on the next launch. Both
 * buttons are plain text of equal weight, since nudging toward yes is exactly the dark pattern
 * a consent prompt is supposed to avoid.
 */
@Composable
internal fun AnalyticsConsentDialog(onAnswer: (granted: Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { /* answer required -- see class doc */ },
        confirmButton = {
            TextButton(onClick = { onAnswer(true) }) { Text("Allow") }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(false) }) { Text("No thanks") }
        },
        title = { Text("Help improve the game?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "This game can send anonymous data about how it's played -- things like " +
                        "which level you reach and whether the app crashes."
                )
                Text(
                    "No accounts, no name, no email, nothing that identifies you. It's only " +
                        "used to work out which parts of the game need fixing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                Text(
                    "The game plays exactly the same either way, and you can change your mind " +
                        "any time under Your Stats.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    )
}
