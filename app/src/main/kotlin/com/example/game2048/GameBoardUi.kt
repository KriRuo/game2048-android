package com.example.game2048

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import com.example.game2048.logic.BoardSizeUnlocks
import com.example.game2048.logic.Direction
import com.example.game2048.logic.GameMode
import com.example.game2048.logic.Joker
import com.example.game2048.logic.ThemeUnlocks
import com.example.game2048.logic.Tile
import com.example.game2048.logic.TileMovement
import com.example.game2048.ui.theme.LocalPaletteColors
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Vertical space [BoardArea] reserves below the square board for [JokerActionBar] (its own
 *  height plus the gap above it) so the board shrinks to make room rather than the bar
 *  overflowing or overlapping it. */
private val JOKER_BAR_RESERVED_HEIGHT = 96.dp

/** Minimum drag distance (in px) before a gesture is treated as a directional swipe. */
private const val SWIPE_THRESHOLD_PX = 80f

private const val SLIDE_DURATION_MS = 140
private const val MERGE_POP_UP_MS = 90
private const val MERGE_POP_DOWN_MS = 110

/** The square board plus everything overlaid on it (combo popup, game-over/win banners,
 *  streak celebration). Sized to whichever of the space it's given is smaller, so it's
 *  always a full square that fits -- callers give it either the width-minus-sidebar
 *  (landscape) or the width (portrait) as the constraining dimension. */
@Composable
internal fun BoardArea(
    uiState: GameUiState,
    viewModel: GameViewModel,
    onNavigateHome: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                        // Goes home rather than starting a new game directly: game-over is one
                        // of the two moments (the other is the Home icon) where the player is
                        // meant to reconsider Original vs. Extended, per StartScreen.
                        buttonLabel = "Play Again",
                        onButtonClick = onNavigateHome
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
    value < 1_000 -> 24.sp
    value < 10_000 -> 19.sp
    value < 100_000 -> 15.sp
    value < 1_000_000 -> 12.sp
    else -> 10.sp
}
