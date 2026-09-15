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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game2048.logic.BoardSizeOption
import com.example.game2048.logic.BoardSizeUnlocks
import com.example.game2048.logic.Direction
import com.example.game2048.logic.GameMode
import com.example.game2048.logic.Joker
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

/** Vertical space [BoardArea] reserves below the square board for [JokerActionBar] (its own
 *  height plus the gap above it) so the board shrinks to make room rather than the bar
 *  overflowing or overlapping it. */
private val JOKER_BAR_RESERVED_HEIGHT = 96.dp
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
                    gameMode = uiState.gameMode,
                    gamesPlayed = uiState.gamesPlayed,
                    highestTileEver = uiState.highestTileEver,
                    totalMerges = uiState.totalMerges,
                    onNewGame = viewModel::onNewGame,
                    onSelectPalette = viewModel::onSelectPalette,
                    onSelectBoardSize = viewModel::onSelectBoardSize,
                    onSelectGameMode = viewModel::onSelectGameMode,
                    onDebugJumpToLevel30 = viewModel::onDebugJumpToLevel30,
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
                    gameMode = uiState.gameMode,
                    gamesPlayed = uiState.gamesPlayed,
                    highestTileEver = uiState.highestTileEver,
                    totalMerges = uiState.totalMerges,
                    onNewGame = viewModel::onNewGame,
                    onSelectPalette = viewModel::onSelectPalette,
                    onSelectBoardSize = viewModel::onSelectBoardSize,
                    onSelectGameMode = viewModel::onSelectGameMode,
                    onDebugJumpToLevel30 = viewModel::onDebugJumpToLevel30
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
    // Extended mode reserves a strip below the board for the Joker action bar; Original mode
    // (no bar) lets the board claim the full square again.
    val showJokerBar = uiState.gameMode == GameMode.EXTENDED
    BoxWithConstraints(modifier = modifier) {
        val reservedForBar = if (showJokerBar) JOKER_BAR_RESERVED_HEIGHT else 0.dp
        val boardSize = minOf(maxWidth, (maxHeight - reservedForBar).coerceAtLeast(0.dp))
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(boardSize)) {
                Board(
                    boardSize = uiState.game.boardSize,
                    tiles = uiState.game.tiles,
                    previousTilesById = uiState.previousTilesById,
                    movements = uiState.lastMovements,
                    mergedTileIds = uiState.lastMergedTileIds,
                    spawnedTileIds = uiState.lastSpawnedTileIds,
                    moveToken = uiState.moveToken,
                    invalidMoveToken = uiState.invalidMoveToken,
                    // Swiping while a Joker is being aimed would both move the board out from
                    // under the player's pick and silently waste the gesture -- disabled instead
                    // so the only way off a Joker is finishing the pick or Cancel.
                    onSwipe = if (uiState.activeJoker == null) viewModel::onSwipe else { _ -> },
                    activeJoker = uiState.activeJoker,
                    jokerFirstTileId = uiState.jokerFirstTileId,
                    onJokerTileTap = viewModel::onJokerTileTapped,
                    onJokerCellTap = viewModel::onJokerCellTapped
                )

                if (uiState.activeJoker != null) {
                    JokerBanner(
                        joker = uiState.activeJoker,
                        hasPicked = uiState.jokerFirstTileId != null,
                        onCancel = viewModel::onCancelJoker,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 20.dp)
                    )
                }

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
                    // Not when the game is also over: both overlays would stack and render on
                    // top of each other, and "Keep Going" would be a lie -- there are no moves left.
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
            if (showJokerBar) {
                Spacer(modifier = Modifier.height(12.dp))
                JokerActionBar(
                    canUndo = uiState.undoState != null && uiState.undosRemaining > 0,
                    undosRemaining = uiState.undosRemaining,
                    activeJoker = uiState.activeJoker,
                    teleportsRemaining = uiState.teleportsRemaining,
                    swapsRemaining = uiState.swapsRemaining,
                    bombsRemaining = uiState.bombsRemaining,
                    doublesRemaining = uiState.doublesRemaining,
                    rotatesRemaining = uiState.rotatesRemaining,
                    onUndo = viewModel::onUndo,
                    onStartTeleport = viewModel::onStartTeleport,
                    onStartSwap = viewModel::onStartSwap,
                    onStartBomb = viewModel::onStartBomb,
                    onStartDouble = viewModel::onStartDouble,
                    onRotate = viewModel::onRotate,
                    modifier = Modifier.width(boardSize)
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    gameMode: GameMode,
    gamesPlayed: Int,
    highestTileEver: Int,
    totalMerges: Long,
    onNewGame: () -> Unit,
    onSelectPalette: (TilePalette) -> Unit,
    onSelectBoardSize: (BoardSizeOption) -> Unit,
    onSelectGameMode: (GameMode) -> Unit,
    onDebugJumpToLevel30: () -> Unit
) {
    val accent = LocalPaletteColors.current.accent
    var showThemePicker by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { showModePicker = true },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier
                    .padding(end = 10.dp)
                    .semantics { contentDescription = "Game menu" }
            ) {
                Text("☰", fontSize = 18.sp, maxLines = 1)
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

    if (showModePicker) {
        GameModeDialog(
            currentMode = gameMode,
            onSelect = {
                onSelectGameMode(it)
                showModePicker = false
            },
            onDismiss = { showModePicker = false }
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
    gameMode: GameMode,
    gamesPlayed: Int,
    highestTileEver: Int,
    totalMerges: Long,
    onNewGame: () -> Unit,
    onSelectPalette: (TilePalette) -> Unit,
    onSelectBoardSize: (BoardSizeOption) -> Unit,
    onSelectGameMode: (GameMode) -> Unit,
    onDebugJumpToLevel30: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalPaletteColors.current.accent
    var showThemePicker by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }

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
            onClick = { showModePicker = true },
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.semantics { contentDescription = "Game menu" }
        ) {
            Text("☰", fontSize = 18.sp, maxLines = 1)
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

    if (showModePicker) {
        GameModeDialog(
            currentMode = gameMode,
            onSelect = {
                onSelectGameMode(it)
                showModePicker = false
            },
            onDismiss = { showModePicker = false }
        )
    }
}

@Composable
private fun GameModeDialog(
    currentMode: GameMode,
    onSelect: (GameMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Choose game") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                GameMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(mode) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = mode.displayName, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (mode == GameMode.ORIGINAL) {
                                    "Classic rules: swipe to move, no Undo, no Jokers."
                                } else {
                                    "Adds Undo plus the Teleport, Swap, Rotate, Double, and Bomb Jokers."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                        if (mode == currentMode) {
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
private fun ThemePickerDialog(
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

@Composable
private fun ScoreChip(
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

/** Instructional pill shown over the board while a Joker is active, telling the player what to
 *  tap next, plus a way to back out without spending the allowance. */
@Composable
private fun JokerBanner(joker: Joker, hasPicked: Boolean, onCancel: () -> Unit, modifier: Modifier = Modifier) {
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
            color = MaterialTheme.colorScheme.onBackground
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
private fun JokerActionBar(
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
    onSwipe: (Direction) -> Unit,
    activeJoker: Joker? = null,
    jokerFirstTileId: Int? = null,
    onJokerTileTap: (Int) -> Unit = {},
    onJokerCellTap: (Int, Int) -> Unit = { _, _ -> }
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

        // Static empty-cell backdrop. Highlighted as valid Teleport drop targets once a tile
        // has been picked -- Swap never targets a cell, only a second tile.
        val isPickingTeleportTarget = activeJoker == Joker.TELEPORT && jokerFirstTileId != null
        val occupied = tiles.map { it.row to it.col }.toSet()
        for (r in 0 until boardSize) {
            for (c in 0 until boardSize) {
                val isValidDropTarget = isPickingTeleportTarget && (r to c) !in occupied
                Box(
                    modifier = Modifier
                        .offset(x = xFor(c), y = yFor(r))
                        .size(cellSize)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isValidDropTarget) palette.accent.copy(alpha = 0.28f) else palette.emptyCell)
                        .then(
                            if (isValidDropTarget) {
                                Modifier.clickable { onJokerCellTap(r, c) }
                            } else {
                                Modifier
                            }
                        )
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

        // Live tiles. Every tile is tappable while a Joker is active: Teleport re-picks which
        // tile will move, Swap picks the first tile then completes on a second, different one.
        for (tile in tiles) {
            key(tile.id) {
                AnimatedTile(
                    tile = tile,
                    x = xFor(tile.col),
                    y = yFor(tile.row),
                    cellSize = cellSize,
                    isMerged = moveToken > 0 && tile.id in mergedTileIds,
                    isSpawned = tile.id in spawnedTileIds,
                    moveToken = moveToken,
                    isJokerPicked = activeJoker != null && tile.id == jokerFirstTileId,
                    isJokerSelectable = activeJoker != null,
                    onJokerTap = if (activeJoker != null) ({ onJokerTileTap(tile.id) }) else null
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
    moveToken: Long,
    isJokerPicked: Boolean = false,
    isJokerSelectable: Boolean = false,
    onJokerTap: (() -> Unit)? = null
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
            .background(palette.tileColor(tile.value))
            .then(
                if (isJokerPicked) {
                    Modifier.border(3.dp, palette.accent, RoundedCornerShape(10.dp))
                } else if (isJokerSelectable) {
                    Modifier.border(1.dp, palette.accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .then(if (onJokerTap != null) Modifier.clickable(onClick = onJokerTap) else Modifier),
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
