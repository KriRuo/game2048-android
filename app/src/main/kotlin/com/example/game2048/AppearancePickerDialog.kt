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
import com.example.game2048.logic.PatternUnlocks
import com.example.game2048.logic.ThemeUnlocks
import com.example.game2048.logic.TilePalette
import com.example.game2048.logic.TilePattern
import com.example.game2048.ui.theme.LocalIsDarkTheme
import com.example.game2048.ui.theme.LocalPaletteColors
import com.example.game2048.ui.theme.paletteColorsFor

/** Combined Theme + Pattern picker -- one of the two Start Screen customize dialogs (see also
 *  [BoardSizePickerDialog], [StatsDialog]). Theme and Pattern share one dialog/icon because
 *  they're independent, composable axes -- a pattern draws as an overlay on top of whichever
 *  palette is active rather than rivaling it -- unlike Board Size, which changes how the game
 *  itself plays and stays its own entry point. */
@Composable
internal fun AppearancePickerDialog(
    currentPalette: TilePalette,
    currentPattern: TilePattern,
    level: Int,
    onSelectPalette: (TilePalette) -> Unit,
    onSelectPattern: (TilePattern) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Appearance") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                SectionHeader("Theme")
                TilePalette.entries.forEach { palette ->
                    val unlocked = ThemeUnlocks.isUnlocked(palette, level)
                    val swatchColor = paletteColorsFor(palette, isDark).accent
                    UnlockableRow(
                        swatch = {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (unlocked) swatchColor else swatchColor.copy(alpha = 0.35f))
                            )
                        },
                        name = palette.displayName,
                        unlocked = unlocked,
                        unlockLevel = palette.unlockLevel,
                        isSelected = palette == currentPalette,
                        onClick = { onSelectPalette(palette) }
                    )
                }
                SectionHeader("Pattern", modifier = Modifier.padding(top = 12.dp))
                // Previewed against the currently-selected palette's own tile color/text tint --
                // the same pair AnimatedTile/GhostTile use -- so a swatch here shows the actual
                // combo the player would get, live as they change Theme above.
                val previewBackground = LocalPaletteColors.current.tileColor(64)
                val previewTint = LocalPaletteColors.current.tileTextColor(64)
                TilePattern.entries.forEach { pattern ->
                    val unlocked = PatternUnlocks.isUnlocked(pattern, level)
                    UnlockableRow(
                        swatch = {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(previewBackground.copy(alpha = if (unlocked) 1f else 0.35f))
                                    .tilePattern(pattern, previewTint)
                            )
                        },
                        name = pattern.displayName,
                        unlocked = unlocked,
                        unlockLevel = pattern.unlockLevel,
                        isSelected = pattern == currentPattern,
                        onClick = { onSelectPattern(pattern) }
                    )
                }
            }
        }
    )
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = LocalPaletteColors.current.accent,
        modifier = modifier.padding(bottom = 4.dp, top = 4.dp)
    )
}

/** Shared row shape for an unlockable option: swatch, name, "unlocks at level" note when locked,
 *  and a checkmark on the current selection -- previously duplicated between this file's Theme
 *  section and [BoardSizePickerDialog]'s rows. */
@Composable
private fun UnlockableRow(
    swatch: @Composable () -> Unit,
    name: String,
    unlocked: Boolean,
    unlockLevel: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (unlocked) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        swatch()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontWeight = FontWeight.SemiBold,
                color = if (unlocked) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                }
            )
            if (!unlocked) {
                Text(
                    text = "🔒 Unlocks at Level $unlockLevel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
        }
        if (isSelected) {
            Text(
                text = "✓",
                color = LocalPaletteColors.current.accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
