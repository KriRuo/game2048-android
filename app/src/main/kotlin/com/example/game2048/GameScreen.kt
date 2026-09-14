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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.example.game2048.logic.BOARD_SIZE
import com.example.game2048.logic.Direction
import com.example.game2048.logic.Tile
import com.example.game2048.logic.TileMovement
import com.example.game2048.ui.theme.ClaudeAccent
import com.example.game2048.ui.theme.DarkBoardFrame
import com.example.game2048.ui.theme.DarkEmptyCell
import com.example.game2048.ui.theme.DarkSurfaceChip
import com.example.game2048.ui.theme.LightBoardFrame
import com.example.game2048.ui.theme.LightEmptyCell
import com.example.game2048.ui.theme.LightSurfaceChip
import com.example.game2048.ui.theme.LocalIsDarkTheme
import com.example.game2048.ui.theme.tileColor
import com.example.game2048.ui.theme.tileTextColor
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
    val isDark = LocalIsDarkTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Header(
            score = uiState.game.score,
            best = uiState.game.best,
            scoreGainedThisMove = if (uiState.moveToken > 0) uiState.lastScoreGained else 0,
            moveToken = uiState.moveToken,
            onNewGame = viewModel::onNewGame
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Board(
                tiles = uiState.game.tiles,
                previousTilesById = uiState.previousTilesById,
                movements = uiState.lastMovements,
                mergedTileIds = uiState.lastMergedTileIds,
                spawnedTileIds = uiState.lastSpawnedTileIds,
                moveToken = uiState.moveToken,
                invalidMoveToken = uiState.invalidMoveToken,
                isDark = isDark,
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
                    buttonLabel = "Try Again",
                    onButtonClick = viewModel::onNewGame
                )
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.game.hasWon && !uiState.game.continuePastWin,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220)),
                exit = fadeOut(tween(120))
            ) {
                GameOverlay(
                    title = "You made 2048!",
                    buttonLabel = "Keep Going",
                    onButtonClick = viewModel::onContinuePastWin
                )
            }
        }
    }
}

@Composable
private fun Header(
    score: Int,
    best: Int,
    scoreGainedThisMove: Int,
    moveToken: Long,
    onNewGame: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "2048",
            style = MaterialTheme.typography.headlineLarge,
            color = ClaudeAccent
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ScoreChip(label = "SCORE", value = score, scoreGainedThisMove = scoreGainedThisMove, moveToken = moveToken)
            ScoreChip(label = "BEST", value = best)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(
            onClick = onNewGame,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ClaudeAccent)
        ) {
            Text("New Game", fontWeight = FontWeight.SemiBold)
        }
    }
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
        }
    }

    Box {
        val isDark = LocalIsDarkTheme.current
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) DarkSurfaceChip else LightSurfaceChip)
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
                color = ClaudeAccent,
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
            color = ClaudeAccent
        )
    }
}

@Composable
private fun Board(
    tiles: List<Tile>,
    previousTilesById: Map<Int, Tile>,
    movements: List<TileMovement>,
    mergedTileIds: Set<Int>,
    spawnedTileIds: Set<Int>,
    moveToken: Long,
    invalidMoveToken: Long,
    isDark: Boolean,
    onSwipe: (Direction) -> Unit
) {
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
            .background(if (isDark) DarkBoardFrame else LightBoardFrame)
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
        val cellSize = (maxWidth - spacing * (BOARD_SIZE + 1)) / BOARD_SIZE

        fun xFor(col: Int): Dp = spacing + (cellSize + spacing) * col
        fun yFor(row: Int): Dp = spacing + (cellSize + spacing) * row

        // Static empty-cell backdrop.
        for (r in 0 until BOARD_SIZE) {
            for (c in 0 until BOARD_SIZE) {
                Box(
                    modifier = Modifier
                        .offset(x = xFor(c), y = yFor(r))
                        .size(cellSize)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) DarkEmptyCell else LightEmptyCell)
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
                    cellSize = cellSize,
                    isDark = isDark
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
                    moveToken = moveToken,
                    isDark = isDark
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
    isDark: Boolean
) {
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
            .background(tileColor(tile.value, isDark)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tile.value.toString(),
            color = tileTextColor(tile.value, isDark),
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
    cellSize: Dp,
    isDark: Boolean
) {
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
            .background(tileColor(value, isDark)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value.toString(),
            color = tileTextColor(value, isDark),
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
private fun GameOverlay(title: String, buttonLabel: String, onButtonClick: () -> Unit) {
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
            OutlinedButton(
                modifier = Modifier.padding(top = 18.dp),
                onClick = onButtonClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ClaudeAccent)
            ) {
                Text(buttonLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
