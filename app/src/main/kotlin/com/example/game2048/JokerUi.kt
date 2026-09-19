package com.example.game2048

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game2048.logic.Joker
import com.example.game2048.ui.theme.LocalPaletteColors

/** Instructional pill shown over the board while a Joker is active, telling the player what to
 *  tap next, plus a way to back out without spending the allowance. */
@Composable
internal fun JokerBanner(joker: Joker, hasPicked: Boolean, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPaletteColors.current
    val instruction = when (joker) {
        Joker.TELEPORT -> if (!hasPicked) "Tap a tile to teleport" else "Tap an empty cell to move it there"
        Joker.SWAP -> if (!hasPicked) "Tap a tile to swap" else "Tap another tile to swap with"
        Joker.BOMB -> "Tap a tile to remove it"
        Joker.DOUBLE -> "Tap a tile to double it"
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surfaceChip)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = instruction,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            // Weighted so this wraps instead of pushing the ✕ off-screen when the banner sits
            // somewhere narrower than its natural single-line width (e.g. the landscape Sidebar).
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = "✕",
            fontWeight = FontWeight.Bold,
            color = palette.accent,
            modifier = Modifier.clickable(onClick = onCancel)
        )
    }
}

/** Undo/Teleport/Swap, grouped into one flat rounded bar below the board (rather than each as
 *  its own outlined button up in the header) -- mirrors play2048.co/plus's bottom action tray. */
@Composable
internal fun JokerActionBar(
    canUndo: Boolean,
    undosRemaining: Int,
    activeJoker: Joker?,
    teleportsRemaining: Int,
    swapsRemaining: Int,
    bombsRemaining: Int,
    doublesRemaining: Int,
    rotatesRemaining: Int,
    onUndo: () -> Unit,
    onStartTeleport: () -> Unit,
    onStartSwap: () -> Unit,
    onStartBomb: () -> Unit,
    onStartDouble: () -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPaletteColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surfaceChip)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        JokerActionButton(
            icon = "↩️",
            enabled = canUndo,
            isActive = false,
            remaining = undosRemaining,
            max = MAX_UNDOS,
            contentDescription = "Undo, $undosRemaining left",
            onClick = onUndo
        )
        JokerActionButton(
            icon = "🌀",
            enabled = teleportsRemaining > 0,
            isActive = activeJoker == Joker.TELEPORT,
            remaining = teleportsRemaining,
            max = MAX_TELEPORTS,
            contentDescription = "Teleport, $teleportsRemaining left",
            onClick = onStartTeleport
        )
        JokerActionButton(
            icon = "🔀",
            enabled = swapsRemaining > 0,
            isActive = activeJoker == Joker.SWAP,
            remaining = swapsRemaining,
            max = MAX_SWAPS,
            contentDescription = "Swap, $swapsRemaining left",
            onClick = onStartSwap
        )
        JokerActionButton(
            icon = "🔁",
            enabled = rotatesRemaining > 0,
            isActive = false,
            remaining = rotatesRemaining,
            max = MAX_ROTATES,
            contentDescription = "Rotate board, $rotatesRemaining left",
            onClick = onRotate
        )
        JokerActionButton(
            icon = "✨",
            enabled = doublesRemaining > 0,
            isActive = activeJoker == Joker.DOUBLE,
            remaining = doublesRemaining,
            max = MAX_DOUBLES,
            contentDescription = "Double, $doublesRemaining left",
            onClick = onStartDouble
        )
        JokerActionButton(
            icon = "💣",
            enabled = bombsRemaining > 0,
            isActive = activeJoker == Joker.BOMB,
            remaining = bombsRemaining,
            max = MAX_BOMBS,
            contentDescription = "Bomb, $bombsRemaining left",
            onClick = onStartBomb
        )
    }
}

/** One flat icon tile in [JokerActionBar], with a row of small dashes underneath standing in
 *  for a numeric badge -- [remaining] of [max] filled in accent, the rest faded. */
@Composable
private fun JokerActionButton(
    icon: String,
    enabled: Boolean,
    isActive: Boolean,
    remaining: Int,
    max: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    val palette = LocalPaletteColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isActive) palette.accent.copy(alpha = 0.25f) else palette.emptyCell)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = icon,
                fontSize = 19.sp,
                modifier = Modifier.alpha(if (enabled) 1f else 0.35f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(max) { i ->
                Box(
                    modifier = Modifier
                        .size(width = 8.dp, height = 3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(if (i < remaining) palette.accent else palette.accent.copy(alpha = 0.2f))
                )
            }
        }
    }
}
