package com.example.game2048

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Lifetime stats -- one of the three Start Screen customize dialogs (see also
 *  [ThemePickerDialog], [BoardSizePickerDialog]). Never reset by New Game.
 *
 *  Also the permanent home of the analytics opt-out first asked by [AnalyticsConsentDialog]:
 *  this is already the screen about the player's own data, so the switch belongs here rather
 *  than behind a fifth Start Screen icon. */
@Composable
internal fun StatsDialog(
    gamesPlayed: Int,
    highestTileEver: Int,
    totalMerges: Long,
    analyticsConsentGranted: Boolean?,
    onAnalyticsConsentChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Your Stats") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StatRow("🎮 Games played", gamesPlayed.toString())
                StatRow("🏆 Highest tile", highestTileEver.toString())
                StatRow("🔗 Total merges", totalMerges.toString())
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📈 Share anonymous data",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "Crash reports and how far you get. Never anything that identifies you.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                    Switch(
                        checked = analyticsConsentGranted == true,
                        onCheckedChange = onAnalyticsConsentChanged
                    )
                }
            }
        }
    )
}

/** One row in [StatsDialog]. */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
