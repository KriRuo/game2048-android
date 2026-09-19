package com.example.game2048

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.game2048.logic.DailyChallengeTracker
import com.example.game2048.ui.theme.LocalPaletteColors

/**
 * Today's fixed-seed, single-attempt puzzle -- see [DailyChallengeTracker] and
 * [GameViewModel.onStartDailyChallenge]/[GameViewModel.onDailyChallengeSwipe]. Reachable from
 * [StartScreen]'s Daily Challenge card.
 *
 * Deliberately its own small screen rather than a variant of [GameScreen]: no Jokers/Undo, no
 * sidebar/landscape split, and no persistence of an in-progress attempt across a process
 * restart -- a killed app loses whatever attempt was underway, the same not-a-big-deal-either-
 * way tradeoff [MAX_UNDOS] already makes, since re-entering just starts today's (identical,
 * still-seeded) board fresh again.
 */
@Composable
internal fun DailyChallengeScreen(
    uiState: GameUiState,
    viewModel: GameViewModel,
    onNavigateHome: () -> Unit
) {
    val accent = LocalPaletteColors.current.accent
    var showStartConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
            Text(
                text = "Daily Challenge",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            // Balances the Home button's width so the title reads as centered.
            Spacer(modifier = Modifier.size(48.dp))
        }

        val game = uiState.dailyChallengeGame
        when {
            uiState.dailyChallengeCompletedToday -> DailyChallengeResult(
                score = uiState.dailyChallengeLastScore,
                bestScore = uiState.dailyChallengeBestScore
            )
            game == null -> DailyChallengeIntro(onStartRequested = { showStartConfirm = true })
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DailyChallengeStatChip(label = "SCORE", value = game.score)
                    DailyChallengeStatChip(label = "MOVES LEFT", value = uiState.dailyChallengeMovesRemaining)
                }
                Spacer(modifier = Modifier.height(16.dp))
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val boardSize = minOf(maxWidth, maxHeight)
                    Box(
                        modifier = Modifier
                            .size(boardSize)
                            .align(Alignment.TopCenter)
                    ) {
                        Board(
                            boardSize = game.boardSize,
                            tiles = game.tiles,
                            previousTilesById = uiState.dailyChallengePreviousTilesById,
                            movements = uiState.dailyChallengeLastMovements,
                            mergedTileIds = uiState.dailyChallengeLastMergedTileIds,
                            spawnedTileIds = uiState.dailyChallengeLastSpawnedTileIds,
                            moveToken = uiState.dailyChallengeMoveToken,
                            invalidMoveToken = uiState.dailyChallengeInvalidMoveToken,
                            onSwipe = viewModel::onDailyChallengeSwipe
                        )
                    }
                }
            }
        }
    }

    if (showStartConfirm) {
        AlertDialog(
            onDismissRequest = { showStartConfirm = false },
            title = { Text("Start today's challenge?") },
            text = { Text("One attempt per day -- once you make a move, this is it until tomorrow.") },
            confirmButton = {
                TextButton(onClick = {
                    showStartConfirm = false
                    viewModel.onStartDailyChallenge()
                }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showStartConfirm = false }) { Text("Not yet") }
            }
        )
    }
}

@Composable
private fun DailyChallengeIntro(onStartRequested: () -> Unit) {
    val accent = LocalPaletteColors.current.accent
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = "One board. Everyone plays the same one today.",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "${DailyChallengeTracker.MOVE_CAP} moves, one shot -- make them count. " +
            "No Undo, no Jokers. Completing it earns +${DailyChallengeTracker.COMPLETION_BONUS_XP} XP.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    )
    Spacer(modifier = Modifier.height(24.dp))
    OutlinedButton(
        onClick = onStartRequested,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
    ) {
        Text(
            text = "Start Challenge",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        )
    }
}

@Composable
private fun DailyChallengeResult(score: Int, bestScore: Int) {
    val accent = LocalPaletteColors.current.accent
    Spacer(modifier = Modifier.height(32.dp))
    Text(
        text = "Today's run is done.",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = accent
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(text = "Score: $score", style = MaterialTheme.typography.bodyLarge)
    Text(
        text = "Your best: $bestScore",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "+${DailyChallengeTracker.COMPLETION_BONUS_XP} XP earned today",
        fontWeight = FontWeight.Bold,
        color = accent
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = "Come back tomorrow for a new board.",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )
}

@Composable
private fun DailyChallengeStatChip(label: String, value: Int) {
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
}
