package com.example.game2048

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game2048.logic.BoardSizeOption
import com.example.game2048.logic.BoardSizeUnlocks
import com.example.game2048.logic.Direction
import com.example.game2048.logic.ThemeUnlocks
import com.example.game2048.logic.Tile
import com.example.game2048.logic.TileMovement
import com.example.game2048.logic.TilePalette
import com.example.game2048.ui.theme.LocalIsDarkTheme
import com.example.game2048.ui.theme.LocalPaletteColors
import com.example.game2048.ui.theme.paletteColorsFor
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Minimum drag distance (in px) before a gesture is treated as a directional swipe. */
private const val SWIPE_THRESHOLD_PX = 80f

private const val SLIDE_DURATION_MS = 140
private const val MERGE_POP_UP_MS = 90
private const val MERGE_POP_DOWN_MS = 110
private const val SCORE_POPUP_LIFETIME_MS = 700L
private const val COMBO_POPUP_LIFETIME_MS = 900L

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // Landscape gets its own layout (sidebar + board side by side) rather than reusing the
    // portrait Column: stacking the header above the board there left only a short sliver of
    // height for the (necessarily square) board, so it rendered tiny with huge empty gutters
    // on either side while the header/buttons sat stranded in the corners.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (maxWidth > maxHeight) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Sidebar(
                    score = uiState.game.score,
                    best = uiState.game.best,
                    scoreGainedThisMove = if (uiState.moveToken > 0) uiState.lastScoreGained else 0,
                    moveToken = uiState.moveToken,
                    currentStreak = uiState.currentStreak,
                    level = uiState.level,
                    levelProgress = uiState.levelProgress,
                    selectedPalette = uiState.selectedPalette,
                    selectedBoardSize = uiState.selectedBoardSize,
                    onNewGame = viewModel::onNewGame,
                    onSelectPalette = viewModel::onSelectPalette,
                    onSelectBoardSize = viewModel::onSelectBoardSize,
                    modifier = Modifier.padding(end = 24.dp)
                )
                BoardArea(uiState = uiState, viewModel = viewModel, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Header(
                    score = uiState.game.score,
                    best = uiState.game.best,
                    scoreGainedThisMove = if (uiState.moveToken > 0) uiState.lastScoreGained else 0,
                    moveToken = uiState.moveToken,
                    currentStreak = uiState.currentStreak,
                    level = uiState.level,
                    levelProgress = uiState.levelProgress,
                    selectedPalette = uiState.selectedPalette,
                    selectedBoardSize = uiState.selectedBoardSize,
                    onNewGame = viewModel::onNewGame,
                    onSelectPalette = viewModel::onSelectPalette,
                    onSelectBoardSize = viewModel::onSelectBoardSize
                )
                BoardArea(
                    uiState = uiState,
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 24.dp)
                )
            }
        }
    }
}

/** The square board plus everything overlaid on it (combo popup, game-over/win banners,
 *  streak celebration). Sized to whichever of the space it's given is smaller, so it's
 *  always a full square that fits -- callers give it either the width-minus-sidebar
 *  (landscape) or the width (portrait) as the constraining dimension. */
@Composable
private fun BoardArea(uiState: GameUiState, viewModel: GameViewModel, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        Box(modifier = Modifier.size(minOf(maxWidth, maxHeight)).align(Alignment.TopCenter)) {
            Board(
                boardSize = uiState.game.boardSize,
                tiles = uiState.game.tiles,
                previousTilesById = uiState.previousTilesById,
                movements = uiState.lastMovements,
                mergedTileIds = uiState.lastMergedTileIds,
                spawnedTileIds = uiState.lastSpawnedTileIds,
                moveToken = uiState.moveToken,
                invalidMoveToken = uiState.invalidMoveToken,
                onSwipe = viewModel::onSwipe
            )

            ComboPopup(
                comboCount = uiState.lastMergedTileIds.size,
                moveToken = uiState.moveToken,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.game.isGameOver,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220)),
                exit = fadeOut(tween(120))
            ) {
                GameOverlay(
                    title = "Game Over",
                    buttonLabel = "Continue Your Journey",
                    onButtonClick = viewModel::onNewGame
                ) {
                    val accent = LocalPaletteColors.current.accent
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Level ${uiState.level}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                    LinearProgressIndicator(
                        progress = { uiState.levelProgress },
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .width(160.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                        drawStopIndicator = {}
                    )
                    if (uiState.level > uiState.levelAtGameStart) {
                        Text(
                            text = "🎉 Leveled up!",
                            modifier = Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    ThemeUnlocks.newlyUnlocked(uiState.levelAtGameStart, uiState.level)?.let { unlocked ->
                        Text(
                            text = "🎨 New theme unlocked: ${unlocked.displayName}!",
                            modifier = Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    BoardSizeUnlocks.newlyUnlocked(uiState.levelAtGameStart, uiState.level)?.let { unlocked ->
                        Text(
                            text = "📐 ${unlocked.displayName} unlocked! Select it from Customize.",
                            modifier = Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                // Not when the game is also over: both overlays would stack and render on top
                // of each other, and "Keep Going" would be a lie -- there are no moves left.
                visible = uiState.game.hasWon && !uiState.game.continuePastWin && !uiState.game.isGameOver,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220)),
                exit = fadeOut(tween(120))
            ) {
                GameOverlay(
                    title = "You made 2048!",
                    buttonLabel = "Keep Going",
                    onButtonClick = viewModel::onContinuePastWin
                )
            }

            StreakMilestoneBanner(
                milestone = uiState.justReachedMilestone,
                onShown = viewModel::onMilestoneBannerShown
            )
        }
    }
}

@Composable
private fun Header(
    score: Int,
    best: Int,
    scoreGainedThisMove: Int,
    moveToken: Long,
    currentStreak: Int,
    level: Int,
    levelProgress: Float,
    selectedPalette: TilePalette,
    selectedBoardSize: BoardSizeOption,
    onNewGame: () -> Unit,
    onSelectPalette: (TilePalette) -> Unit,
    onSelectBoardSize: (BoardSizeOption) -> Unit
) {
    val accent = LocalPaletteColors.current.accent
    var showThemePicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreChip(label = "SCORE", value = score, scoreGainedThisMove = scoreGainedThisMove, moveToken = moveToken)
            ScoreChip(label = "BEST", value = best)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { showThemePicker = true },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
        ) {
            Text("🎨 Customize", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.width(10.dp))
        OutlinedButton(
            onClick = onNewGame,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
        ) {
            Text("New Game", fontWeight = FontWeight.SemiBold)
        }
    }

    if (showThemePicker) {
        ThemePickerDialog(
            currentPalette = selectedPalette,
            level = level,
            selectedBoardSize = selectedBoardSize,
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
private fun Sidebar(
    score: Int,
    best: Int,
    scoreGainedThisMove: Int,
    moveToken: Long,
    currentStreak: Int,
    level: Int,
    levelProgress: Float,
    selectedPalette: TilePalette,
    selectedBoardSize: BoardSizeOption,
    onNewGame: () -> Unit,
    onSelectPalette: (TilePalette) -> Unit,
    onSelectBoardSize: (BoardSizeOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalPaletteColors.current.accent
    var showThemePicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(150.dp)) {
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
        ScoreChip(label = "SCORE", value = score, scoreGainedThisMove = scoreGainedThisMove, moveToken = moveToken)
        Spacer(modifier = Modifier.height(10.dp))
        ScoreChip(label = "BEST", value = best)

        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(
            onClick = { showThemePicker = true },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🎨 Customize", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedButton(
            onClick = onNewGame,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("New Game", fontWeight = FontWeight.SemiBold)
        }
    }

    if (showThemePicker) {
        ThemePickerDialog(
            currentPalette = selectedPalette,
            level = level,
            selectedBoardSize = selectedBoardSize,
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
private fun ThemePickerDialog(
    currentPalette: TilePalette,
    level: Int,
    selectedBoardSize: BoardSizeOption,
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            }
        }
    )
}

@Composable
private fun ScoreChip(
    label: String,
    value: Int,
    scoreGainedThisMove: Int = 0,
    moveToken: Long = -1
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

    Box {
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

/** Shows a transient "x3 Combo!" callout when a single move merges more than one pair. */
@Composable
private fun ComboPopup(comboCount: Int, moveToken: Long, modifier: Modifier = Modifier) {
    var visibleCombo by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(moveToken) {
        if (moveToken > 0 && comboCount >= 2) {
            visibleCombo = comboCount
            delay(COMBO_POPUP_LIFETIME_MS)
            visibleCombo = null
        } else {
            // Covers New Game resetting moveToken back to 0 while a popup from the
            // previous game was still showing/fading -- otherwise it's stuck forever,
            // since the branch above (which is what normally clears it) never runs.
            visibleCombo = null
        }
    }

    AnimatedVisibility(
        visible = visibleCombo != null,
        modifier = modifier,
        enter = fadeIn(tween(120)) + scaleIn(
            initialScale = 0.6f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.8f, animationSpec = tween(220))
    ) {
        Text(
            text = "×${visibleCombo ?: 0} Combo!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = LocalPaletteColors.current.accent
        )
    }
}

private const val STREAK_MILESTONE_BANNER_LIFETIME_MS = 2200L

/** Full-screen celebration shown once, the first time a streak milestone (3, 7, 14, ...
 *  days) is reached, then auto-dismisses via [onShown]. */
@Composable
private fun StreakMilestoneBanner(milestone: Int?, onShown: () -> Unit) {
    var shownMilestone by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(milestone) {
        if (milestone != null) {
            shownMilestone = milestone
            delay(STREAK_MILESTONE_BANNER_LIFETIME_MS)
            shownMilestone = null
            onShown()
        }
    }

    AnimatedVisibility(
        visible = shownMilestone != null,
        enter = fadeIn(tween(200)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        ),
        exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.9f, animationSpec = tween(220))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "🔥", fontSize = 48.sp)
                Text(
                    text = "${shownMilestone ?: 0}-Day Streak!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalPaletteColors.current.accent
                )
            }
        }
    }
}

@Composable
private fun Board(
    boardSize: Int,
    tiles: List<Tile>,
    previousTilesById: Map<Int, Tile>,
    movements: List<TileMovement>,
    mergedTileIds: Set<Int>,
    spawnedTileIds: Set<Int>,
    moveToken: Long,
    invalidMoveToken: Long,
    onSwipe: (Direction) -> Unit
) {
    val palette = LocalPaletteColors.current
    val haptics = LocalHapticFeedback.current
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(invalidMoveToken) {
        if (invalidMoveToken > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            shakeOffset.animateTo(9f, tween(45))
            shakeOffset.animateTo(-9f, tween(90))
            shakeOffset.animateTo(5f, tween(90))
            shakeOffset.animateTo(0f, tween(70))
        }
    }
    LaunchedEffect(moveToken) {
        if (moveToken > 0) {
            haptics.performHapticFeedback(
                if (mergedTileIds.isNotEmpty()) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        }
    }

    data class GhostTileData(
        val id: Int,
        val value: Int,
        val fromRow: Int,
        val fromCol: Int,
        val toRow: Int,
        val toCol: Int,
        val key: String
    )

    val ghostTiles = remember { mutableStateListOf<GhostTileData>() }
    LaunchedEffect(moveToken) {
        if (moveToken > 0) {
            val newGhosts = movements.filter { it.isConsumedByMerge }.mapNotNull { m ->
                previousTilesById[m.tileId]?.let { prev ->
                    GhostTileData(m.tileId, prev.value, m.fromRow, m.fromCol, m.toRow, m.toCol, "ghost-${m.tileId}-$moveToken")
                }
            }
            ghostTiles.clear()
            ghostTiles.addAll(newGhosts)
            delay(SLIDE_DURATION_MS.toLong() + 20)
            ghostTiles.clear()
        }
    }

    val spacing = 8.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .offset(x = shakeOffset.value.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.boardFrame)
            .pointerInput(Unit) {
                var dragAmountX = 0f
                var dragAmountY = 0f
                detectDragGestures(
                    onDragStart = {
                        dragAmountX = 0f
                        dragAmountY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAmountX += dragAmount.x
                        dragAmountY += dragAmount.y
                    },
                    onDragEnd = {
                        val absX = abs(dragAmountX)
                        val absY = abs(dragAmountY)
                        if (maxOf(absX, absY) >= SWIPE_THRESHOLD_PX) {
                            val direction = if (absX > absY) {
                                if (dragAmountX > 0) Direction.RIGHT else Direction.LEFT
                            } else {
                                if (dragAmountY > 0) Direction.DOWN else Direction.UP
                            }
                            onSwipe(direction)
                        }
                    }
                )
            }
    ) {
        val cellSize = (maxWidth - spacing * (boardSize + 1)) / boardSize

        fun xFor(col: Int): Dp = spacing + (cellSize + spacing) * col
        fun yFor(row: Int): Dp = spacing + (cellSize + spacing) * row

        // Static empty-cell backdrop.
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                Box(
                    modifier = Modifier
                        .offset(x = xFor(c), y = yFor(r))
                        .size(cellSize)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.emptyCell)
                )
            }
        }

        // Tiles sliding away into a merge: rendered briefly, then removed.
        for (ghost in ghostTiles) {
            key(ghost.key) {
                GhostTile(
                    value = ghost.value,
                    fromX = xFor(ghost.fromCol),
                    fromY = yFor(ghost.fromRow),
                    toX = xFor(ghost.toCol),
                    toY = yFor(ghost.toRow),
                    cellSize = cellSize
                )
            }
        }

        // Live tiles.
        for (tile in tiles) {
            key(tile.id) {
                AnimatedTile(
                    tile = tile,
                    x = xFor(tile.col),
                    y = yFor(tile.row),
                    cellSize = cellSize,
                    isMerged = moveToken > 0 && tile.id in mergedTileIds,
                    isSpawned = tile.id in spawnedTileIds,
                    moveToken = moveToken
                )
            }
        }
    }
}

@Composable
private fun AnimatedTile(
    tile: Tile,
    x: Dp,
    y: Dp,
    cellSize: Dp,
    isMerged: Boolean,
    isSpawned: Boolean,
    moveToken: Long
) {
    val palette = LocalPaletteColors.current
    val animatedX by animateDpAsState(
        targetValue = x,
        animationSpec = tween(SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
        label = "tileX"
    )
    val animatedY by animateDpAsState(
        targetValue = y,
        animationSpec = tween(SLIDE_DURATION_MS, easing = FastOutSlowInEasing),
        label = "tileY"
    )

    val scale = remember { Animatable(if (isSpawned) 0f else 1f) }

    LaunchedEffect(moveToken) {
        if (isSpawned) {
            if (moveToken > 0) delay(SLIDE_DURATION_MS.toLong())
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 380f))
        } else if (isMerged) {
            delay(SLIDE_DURATION_MS.toLong())
            scale.animateTo(1.16f, tween(MERGE_POP_UP_MS))
            scale.animateTo(1f, tween(MERGE_POP_DOWN_MS))
        } else if (scale.value != 1f) {
            // A fast follow-up swipe can cancel this tile's pop-in/merge animation
            // mid-flight, freezing `scale` at a tiny value forever since neither
            // branch above would otherwise run again for it. Snap it back to full
            // size whenever this tile isn't the one animating this move.
            scale.snapTo(1f)
        }
    }

    Box(
        modifier = Modifier
            .offset(x = animatedX, y = animatedY)
            .size(cellSize)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(10.dp))
            .background(palette.tileColor(tile.value)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tile.value.toString(),
            color = palette.tileTextColor(tile.value),
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeFor(tile.value)
        )
    }
}

@Composable
private fun GhostTile(
    value: Int,
    fromX: Dp,
    fromY: Dp,
    toX: Dp,
    toY: Dp,
    cellSize: Dp
) {
    val palette = LocalPaletteColors.current
    // Animate as plain Float (dp magnitude) rather than Animatable<Dp, _> to avoid depending
    // on the exact name/location of Compose's Dp vector-converter across versions.
    val x = remember { Animatable(fromX.value) }
    val y = remember { Animatable(fromY.value) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { x.animateTo(toX.value, tween(SLIDE_DURATION_MS, easing = FastOutSlowInEasing)) }
        launch { y.animateTo(toY.value, tween(SLIDE_DURATION_MS, easing = FastOutSlowInEasing)) }
        launch {
            delay((SLIDE_DURATION_MS - 50).coerceAtLeast(0).toLong())
            alpha.animateTo(0f, tween(70))
        }
    }

    Box(
        modifier = Modifier
            .offset(x = x.value.dp, y = y.value.dp)
            .size(cellSize)
            .graphicsLayer { this.alpha = alpha.value }
            .clip(RoundedCornerShape(10.dp))
            .background(palette.tileColor(value)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            color = palette.tileTextColor(value),
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeFor(value)
        )
    }
}

private fun fontSizeFor(value: Int) = when {
    value < 100 -> 28.sp
    value < 1000 -> 24.sp
    else -> 19.sp
}

@Composable
private fun GameOverlay(
    title: String,
    buttonLabel: String,
    onButtonClick: () -> Unit,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            extraContent?.invoke(this)
            OutlinedButton(
                modifier = Modifier.padding(top = 18.dp),
                onClick = onButtonClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalPaletteColors.current.accent)
            ) {
                Text(buttonLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
