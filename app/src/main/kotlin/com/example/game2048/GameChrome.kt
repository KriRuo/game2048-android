package com.example.game2048

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game2048.logic.BoardSizeOption
import com.example.game2048.logic.TilePalette
import com.example.game2048.ui.theme.LocalPaletteColors
import kotlinx.coroutines.delay

private const val SCORE_POPUP_LIFETIME_MS = 700L

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun Header(
    score: Int,
    best: Int,
    scoreGainedThisMove: Int,
    moveToken: Long,
    currentStreak: Int,
    level: Int,
    levelProgress: Float,
    selectedPalette: TilePalette,
    selectedBoardSize: BoardSizeOption,
    gamesPlayed: Int,
    highestTileEver: Int,
    totalMerges: Long,
    onNewGame: () -> Unit,
    onSelectPalette: (TilePalette) -> Unit,
    onSelectBoardSize: (BoardSizeOption) -> Unit,
    onDebugJumpToLevel30: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val accent = LocalPaletteColors.current.accent
    var showThemePicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = onNavigateHome,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .padding(end = 10.dp)
                    .semantics { contentDescription = "Home" }
            ) {
                Text("🏠", fontSize = 18.sp, maxLines = 1)
            }
            Column {
                Text(
                    text = "2048",
                    style = MaterialTheme.typography.headlineLarge,
                    color = accent
                )
                Text(
                    text = "Lv. $level",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                LinearProgressIndicator(
                    progress = { levelProgress },
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .width(70.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                    drawStopIndicator = {}
                )
                if (currentStreak >= 1) {
                    Text(
                        text = "🔥 $currentStreak-day streak",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreChip(
                label = "SCORE",
                value = score,
                scoreGainedThisMove = scoreGainedThisMove,
                moveToken = moveToken,
                onSecretTap = onDebugJumpToLevel30
            )
            ScoreChip(label = "BEST", value = best)
        }
    }

    // Customize/Undo are icon-only (secondary actions); New Game keeps its full label as the
    // primary action. Narrow enough that all three reliably fit one line even on a dense phone
    // -- a real Samsung previously squeezed "New Game" so hard its Text wrapped letter-by-letter
    // when all three were full-width buttons. FlowRow (wraps a whole button to a new line rather
    // than letting an individual one collapse) and maxLines = 1 (hard guarantee against that
    // specific failure) are kept as a safety net regardless.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, alignment = Alignment.End),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = { showThemePicker = true },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.semantics { contentDescription = "Customize" }
        ) {
            Text("🎨", fontSize = 18.sp, maxLines = 1)
        }
        OutlinedButton(
            onClick = onNewGame,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
        ) {
            Text("New Game", fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }

    if (showThemePicker) {
        ThemePickerDialog(
            currentPalette = selectedPalette,
            level = level,
            selectedBoardSize = selectedBoardSize,
            gamesPlayed = gamesPlayed,
            highestTileEver = highestTileEver,
            totalMerges = totalMerges,
            onSelect = {
                onSelectPalette(it)
                showThemePicker = false
            },
            onSelectBoardSize = onSelectBoardSize,
            onDismiss = { showThemePicker = false }
        )
    }
}

/** Landscape counterpart to [Header]: the same wordmark/level/streak/scores/buttons, but
 *  stacked into a narrow vertical strip instead of spread across the full width, so the
 *  board gets the rest of the (short, wide) screen instead of a squeezed sliver below it. */
@Composable
internal fun Sidebar(
    score: Int,
    best: Int,
    scoreGainedThisMove: Int,
    moveToken: Long,
    currentStreak: Int,
    level: Int,
    levelProgress: Float,
    selectedPalette: TilePalette,
    selectedBoardSize: BoardSizeOption,
    gamesPlayed: Int,
    highestTileEver: Int,
    totalMerges: Long,
    onNewGame: () -> Unit,
    onSelectPalette: (TilePalette) -> Unit,
    onSelectBoardSize: (BoardSizeOption) -> Unit,
    onDebugJumpToLevel30: () -> Unit,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalPaletteColors.current.accent
    var showThemePicker by remember { mutableStateOf(false) }

    // fillMaxHeight + verticalScroll: three buttons plus the wordmark/level/streak/scores no
    // longer reliably fit a short landscape screen's height (the row that added Undo was the
    // first to actually overflow it) -- unlike a Row, Column content that's taller than its
    // parent isn't clipped or scrollable by default, it just silently renders past the bottom
    // of the screen. Scrolling keeps every button reachable instead of losing the last one off
    // the edge on shorter devices.
    Column(
        modifier = modifier
            .width(150.dp)
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
    ) {
        OutlinedButton(
            onClick = onNavigateHome,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.semantics { contentDescription = "Home" }
        ) {
            Text("🏠", fontSize = 18.sp, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "2048",
            style = MaterialTheme.typography.headlineMedium,
            color = accent
        )
        Text(
            text = "Lv. $level",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        LinearProgressIndicator(
            progress = { levelProgress },
            modifier = Modifier
                .padding(top = 3.dp)
                .width(70.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = accent,
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
            drawStopIndicator = {}
        )
        if (currentStreak >= 1) {
            Text(
                text = "🔥 $currentStreak-day streak",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        ScoreChip(
            label = "SCORE",
            value = score,
            scoreGainedThisMove = scoreGainedThisMove,
            moveToken = moveToken,
            onSecretTap = onDebugJumpToLevel30
        )
        Spacer(modifier = Modifier.height(10.dp))
        ScoreChip(label = "BEST", value = best)

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = { showThemePicker = true },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🎨 Customize", fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onNewGame,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("New Game", fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }

    if (showThemePicker) {
        ThemePickerDialog(
            currentPalette = selectedPalette,
            level = level,
            selectedBoardSize = selectedBoardSize,
            gamesPlayed = gamesPlayed,
            highestTileEver = highestTileEver,
            totalMerges = totalMerges,
            onSelect = {
                onSelectPalette(it)
                showThemePicker = false
            },
            onSelectBoardSize = onSelectBoardSize,
            onDismiss = { showThemePicker = false }
        )
    }
}

@Composable
internal fun ScoreChip(
    label: String,
    value: Int,
    scoreGainedThisMove: Int = 0,
    moveToken: Long = -1,
    // Debug backdoor: 5 quick taps (within 2s of each other) jumps straight to Level 30.
    // Only wired up on the SCORE chip, not BEST -- see the call sites.
    onSecretTap: (() -> Unit)? = null
) {
    var poppedDelta by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(moveToken) {
        if (moveToken > 0 && scoreGainedThisMove > 0) {
            poppedDelta = scoreGainedThisMove
            delay(SCORE_POPUP_LIFETIME_MS)
            poppedDelta = null
        } else {
            // Covers New Game resetting moveToken back to 0 while a popup from the
            // previous game was still showing/fading -- otherwise it's stuck forever,
            // since the branch above (which is what normally clears it) never runs.
            poppedDelta = null
        }
    }

    var tapCount by remember { mutableStateOf(0) }
    var lastTapAtMs by remember { mutableStateOf(0L) }

    Box(
        modifier = if (onSecretTap != null) {
            Modifier.clickable {
                val now = System.currentTimeMillis()
                tapCount = if (now - lastTapAtMs <= 2000L) tapCount + 1 else 1
                lastTapAtMs = now
                if (tapCount >= 5) {
                    tapCount = 0
                    onSecretTap()
                }
            }
        } else {
            Modifier
        }
    ) {
        val palette = LocalPaletteColors.current
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(palette.surfaceChip)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
            )
            Text(
                text = value.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        AnimatedVisibility(
            visible = poppedDelta != null,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-18).dp),
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.7f),
            exit = fadeOut(tween(200))
        ) {
            Text(
                text = "+${poppedDelta ?: 0}",
                color = palette.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
