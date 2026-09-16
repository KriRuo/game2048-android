package com.example.game2048

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import com.example.game2048.logic.ThemeUnlocks
import com.example.game2048.logic.TilePalette
import com.example.game2048.ui.theme.LocalIsDarkTheme
import com.example.game2048.ui.theme.LocalPaletteColors
import com.example.game2048.ui.theme.paletteColorsFor

@Composable
internal fun ThemePickerDialog(
    currentPalette: TilePalette,
    level: Int,
    selectedBoardSize: BoardSizeOption,
    gamesPlayed: Int,
    highestTileEver: Int,
    totalMerges: Long,
    onSelect: (TilePalette) -> Unit,
    onSelectBoardSize: (BoardSizeOption) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Customize") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TilePalette.entries.forEach { palette ->
                    val unlocked = ThemeUnlocks.isUnlocked(palette, level)
                    val swatchColor = paletteColorsFor(palette, isDark).accent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .then(if (unlocked) Modifier.clickable { onSelect(palette) } else Modifier)
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (unlocked) swatchColor else swatchColor.copy(alpha = 0.35f))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = palette.displayName,
                                fontWeight = FontWeight.SemiBold,
                                color = if (unlocked) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                }
                            )
                            if (!unlocked) {
                                Text(
                                    text = "🔒 Unlocks at Level ${palette.unlockLevel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                )
                            }
                        }
                        if (palette == currentPalette) {
                            Text(
                                text = "✓",
                                color = LocalPaletteColors.current.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Board size",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                BoardSizeOption.entries.forEach { option ->
                    val unlocked = BoardSizeUnlocks.isUnlocked(option, level)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .then(if (unlocked) Modifier.clickable { onSelectBoardSize(option) } else Modifier)
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

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Your stats",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                StatRow("🎮 Games played", gamesPlayed.toString())
                StatRow("🏆 Highest tile", highestTileEver.toString())
                StatRow("🔗 Total merges", totalMerges.toString())
            }
        }
    )
}

/** One row in the "Your stats" section of [ThemePickerDialog]. */
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
