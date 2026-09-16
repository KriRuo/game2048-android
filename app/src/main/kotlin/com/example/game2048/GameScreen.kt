package com.example.game2048

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.game2048.logic.BoardSizeOption
import com.example.game2048.logic.GameMode
import com.example.game2048.logic.TilePalette
import com.example.game2048.ui.theme.LocalPaletteColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private const val SCORE_POPUP_LIFETIME_MS = 700L

private enum class AppScreen { START, GAME }

/** True entry point: owns which of [AppScreen]'s two screens is showing. Always launches on
 *  [AppScreen.START] -- Start is meant to be the app's actual front door every time it opens,
 *  not something only some launches see depending on board state, so there's no "resume
 *  straight to the board" special case here. Deciding this once per process (via
 *  [rememberSaveable], not derived every recomposition) is also what makes Original vs.
 *  Extended a real up-front choice instead of something [Header]'s old in-game dialog let you
 *  silently flip mid-board -- see [StartScreen] and [GameScreen]'s Home button.
 *
 *  [rememberSaveable], not plain [remember]: this Activity isn't configured to handle
 *  orientation changes itself (no `android:configChanges` in the manifest), so Android's
 *  default behavior on rotation is to destroy and recreate it -- which tears down and rebuilds
 *  the whole Compose tree. Plain `remember` state doesn't survive that and would silently reset
 *  to START, bouncing the player out of a game in progress just by rotating the screen.
 *  `rememberSaveable` persists across that recreation the same way `uiState` already does via
 *  the ViewModel surviving it. */
@Composable
fun Game2048App(viewModel: GameViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var screen by rememberSaveable { mutableStateOf(AppScreen.START) }

    when (screen) {
        AppScreen.START -> StartScreen(
            uiState = uiState,
            onSelectGameMode = viewModel::onSelectGameMode,
            onPlay = {
                // A game-over board can't be "resumed" -- start fresh in whichever mode was
                // just picked. An in-progress (or brand new, unplayed) board is left alone so
                // Play always means "go look at the board that's already there."
                if (uiState.game.isGameOver) viewModel.onNewGame()
                screen = AppScreen.GAME
            }
        )
        AppScreen.GAME -> GameScreen(viewModel = viewModel, onNavigateHome = { screen = AppScreen.START })
    }
}

/** Landing screen: pick Original or Extended, then Play. The only two ways back here are
 *  finishing a game (its overlay's button goes home, not straight into a new one) or tapping
 *  the board screen's Home icon -- picking a mode is otherwise never possible mid-game. */
@Composable
private fun StartScreen(
    uiState: GameUiState,
    onSelectGameMode: (GameMode) -> Unit,
    onPlay: () -> Unit
) {
    val accent = LocalPaletteColors.current.accent
    // BoxWithConstraints, not a fixed-size flourish: sizing it (and the gaps around it) as a
    // fraction of whatever height is actually available is what makes this fit a real range of
    // screens/font scales without scrolling, rather than fitting only the one screen size this
    // was eyeballed against. verticalScroll stays on as a last-resort safety net -- e.g. a
    // maxed-out system font size -- but the layout is meant to never need it in practice.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The app draws edge-to-edge (enableEdgeToEdge() in MainActivity), so without this
            // Play can end up sitting under -- or right against -- a gesture nav bar/cutout on
            // devices where that inset is taller than the emulator's.
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // 24% of the available height, clamped to a sane range -- shrinks on short screens
        // instead of pushing everything below it off-screen.
        val orbitDiameter = (maxHeight * 0.24f).coerceIn(110.dp, 200.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "2048",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ScoreChip(label = "LEVEL", value = uiState.level)
                ScoreChip(label = "BEST", value = uiState.game.best)
            }
            if (uiState.currentStreak >= 1) {
                Text(
                    text = "🔥 ${uiState.currentStreak}-day streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(
                text = "CHOOSE HOW YOU WANT TO PLAY",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = accent,
                modifier = Modifier.padding(top = 18.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GameMode.entries.forEach { mode ->
                    StartModeCard(
                        mode = mode,
                        selected = mode == uiState.gameMode,
                        onClick = { onSelectGameMode(mode) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            OrbitVariantWeb(diameter = orbitDiameter)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onPlay,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Play",
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

/** A point in a shared, roughly unit-scale 3D space (before projection) -- used by
 *  [OrbitVariantWeb], adapted from the perspective-projection technique in
 *  github.com/jakubantalik/libraries.dev's thinking-orbs engine (its `makeProj`): a real
 *  rotation of the whole point cloud around an axis each frame, not just a fixed visual tilt,
 *  is what actually sells the 3D read. */
private data class Vec3(val x: Float, val y: Float, val z: Float) {
    fun rotateX(radians: Double): Vec3 {
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return Vec3(x, y * c - z * s, y * s + z * c)
    }
    fun rotateY(radians: Double): Vec3 {
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        return Vec3(x * c + z * s, y, -x * s + z * c)
    }
}

/** Perspective-projects a [Vec3] (coordinates roughly in [-1, 1]) to a 2D offset scaled by
 *  [scale] px, plus a depth in [0, 1] where 1 = nearest the viewer -- for depth-based
 *  size/alpha falloff, the same trick the reference engine uses. */
private fun project3D(p: Vec3, scale: Float, focal: Float = 2.6f): Triple<Float, Float, Float> {
    val factor = focal / (focal + p.z)
    val depth = (1f - p.z) / 2f
    return Triple(p.x * factor * scale, p.y * factor * scale, depth)
}

private fun distance3D(a: Vec3, b: Vec3): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val dz = a.z - b.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/** [StartScreen]'s flourish: nodes on a Fibonacci-lattice sphere, rotating in true 3D and
 *  connected by lines when close enough -- a rotating constellation. Adapted from the "web"
 *  mode of github.com/jakubantalik/libraries.dev's thinking-orbs engine (its 30-node version
 *  also has Perlin-noise wobble and traveling "signal packets", dropped here for a lighter
 *  decorative version). Tuned deliberately dim/slow/soft-edged -- glowing orbs rather than
 *  flat dots, low alpha throughout, a lazy spin -- so it reads as something glimpsed in the
 *  background rather than a bright, busy diagram. [diameter] is caller-controlled (see
 *  [StartScreen]) rather than fixed, so it can shrink to fit a shorter screen. */
@Composable
private fun OrbitVariantWeb(modifier: Modifier = Modifier, diameter: Dp = 220.dp) {
    val accent = LocalPaletteColors.current.accent
    val transition = rememberInfiniteTransition(label = "web3d")
    val spin by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing)), label = "webSpin"
    )
    val nodeCount = 28
    val nodes = remember {
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        (0 until nodeCount).map { i ->
            val y = 1f - (i / (nodeCount - 1f)) * 2f
            val radiusAtY = sqrt((1f - y * y).coerceAtLeast(0f))
            val theta = goldenAngle * i
            Vec3((cos(theta) * radiusAtY).toFloat(), y, (sin(theta) * radiusAtY).toFloat())
        }
    }

    Canvas(modifier = modifier.size(diameter)) {
        val scale = size.minDimension * 0.42f
        val center = Offset(size.width / 2f, size.height / 2f)
        val spinRad = Math.toRadians(spin.toDouble())

        val projected = nodes.map { n ->
            val world = n.rotateY(spinRad)
            val (px, py, depth) = project3D(world, scale)
            Triple(center + Offset(px, py), depth, world)
        }

        val connectThreshold = 0.7f
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val d = distance3D(nodes[i], nodes[j])
                if (d < connectThreshold) {
                    val depthAvg = (projected[i].second + projected[j].second) / 2f
                    val edgeAlpha = ((1f - d / connectThreshold) * (0.05f + 0.09f * depthAvg)).coerceIn(0f, 0.16f)
                    drawLine(
                        color = accent.copy(alpha = edgeAlpha),
                        start = projected[i].first,
                        end = projected[j].first,
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
        projected.forEach { (pos, depth, _) ->
            val glowAlpha = 0.10f + 0.22f * depth
            val glowRadius = (5f + 5f * depth).dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = glowAlpha), accent.copy(alpha = 0f)),
                    center = pos,
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = pos
            )
        }
    }
}


/** One selectable mode option on [StartScreen]. */
@Composable
private fun StartModeCard(mode: GameMode, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPaletteColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accent.copy(alpha = 0.15f) else palette.surfaceChip)
            .then(
                if (selected) {
                    Modifier.border(2.dp, palette.accent, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mode.displayName,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = modeDescription(mode),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (selected) {
            Text(text = "✓", color = palette.accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel(), onNavigateHome: () -> Unit) {
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
                    gamesPlayed = uiState.gamesPlayed,
                    highestTileEver = uiState.highestTileEver,
                    totalMerges = uiState.totalMerges,
                    onNewGame = viewModel::onNewGame,
                    onSelectPalette = viewModel::onSelectPalette,
                    onSelectBoardSize = viewModel::onSelectBoardSize,
                    onDebugJumpToLevel30 = viewModel::onDebugJumpToLevel30,
                    onNavigateHome = onNavigateHome,
                    modifier = Modifier.padding(end = 24.dp)
                )
                BoardArea(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateHome = onNavigateHome,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
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
                    gamesPlayed = uiState.gamesPlayed,
                    highestTileEver = uiState.highestTileEver,
                    totalMerges = uiState.totalMerges,
                    onNewGame = viewModel::onNewGame,
                    onSelectPalette = viewModel::onSelectPalette,
                    onSelectBoardSize = viewModel::onSelectBoardSize,
                    onDebugJumpToLevel30 = viewModel::onDebugJumpToLevel30,
                    onNavigateHome = onNavigateHome
                )
                BoardArea(
                    uiState = uiState,
                    viewModel = viewModel,
                    onNavigateHome = onNavigateHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 24.dp)
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

/** One-line description of a [GameMode], shown on [StartModeCard] -- the only place mode is
 *  chosen now (see [StartScreen]). */
private fun modeDescription(mode: GameMode): String = when (mode) {
    GameMode.ORIGINAL -> "Classic rules: swipe to move, no Undo, no Jokers."
    GameMode.EXTENDED -> "Adds Undo plus the Teleport, Swap, Rotate, Double, and Bomb Jokers."
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
