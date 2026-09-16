package com.example.game2048

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game2048.logic.BoardSizeOption
import com.example.game2048.logic.BoardSizeUnlocks
import com.example.game2048.ui.theme.LocalPaletteColors

/** Board size picker -- one of the three Start Screen customize dialogs (see also
 *  [ThemePickerDialog], [StatsDialog]). Selecting a size here only takes effect on the
 *  next New Game (see [GameUiState.selectedBoardSize]); since this now only lives on
 *  the Start Screen, that's every time before Play anyway. */
@Composable
internal fun BoardSizePickerDialog(
    selectedBoardSize: BoardSizeOption,
    level: Int,
    onSelect: (BoardSizeOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Board Size") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                BoardSizeOption.entries.forEach { option ->
                    val unlocked = BoardSizeUnlocks.isUnlocked(option, level)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .then(if (unlocked) Modifier.clickable { onSelect(option) } else Modifier)
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    LocalPaletteColors.current.accent.copy(alpha = if (unlocked) 0.18f else 0.08f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${option.size}²",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (unlocked) {
                                    LocalPaletteColors.current.accent
                                } else {
                                    LocalPaletteColors.current.accent.copy(alpha = 0.4f)
                                }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.displayName,
                                fontWeight = FontWeight.SemiBold,
                                color = if (unlocked) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                }
                            )
                            if (!unlocked) {
                                Text(
                                    text = "🔒 Unlocks at Level ${option.unlockLevel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                )
                            } else if (option != BoardSizeOption.CLASSIC) {
                                // Still true even though this dialog now only lives on the Start
                                // Screen: picking a size here doesn't touch a game already in
                                // progress -- Play resumes it as-is -- only the *next* New Game.
                                Text(
                                    text = "Applies on your next New Game",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                )
                            }
                        }
                        if (option == selectedBoardSize) {
                            Text(
                                text = "✓",
                                color = LocalPaletteColors.current.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    )
}
