package com.example.game2048

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.game2048.logic.BoardSizeOption
import com.example.game2048.logic.GameMode
import com.example.game2048.logic.TilePalette
import com.example.game2048.ui.theme.LocalPaletteColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Landing screen: pick Original or Extended, then Play. The only two ways back here are
 *  finishing a game (its overlay's button goes home, not straight into a new one) or tapping
 *  the board screen's Home icon -- picking a mode is otherwise never possible mid-game. */
@Composable
internal fun StartScreen(
    uiState: GameUiState,
    onSelectGameMode: (GameMode) -> Unit,
    onSelectPalette: (TilePalette) -> Unit,
    onSelectBoardSize: (BoardSizeOption) -> Unit,
    onPlay: () -> Unit,
    onWelcomeDismissed: () -> Unit,
    onDebugResetWelcome: () -> Unit
) {
    val accent = LocalPaletteColors.current.accent
    var showThemePicker by remember { mutableStateOf(false) }
    var showBoardSizePicker by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    // Evaluated once, the first time this composable enters composition (i.e. once per real
    // app launch, or whenever the player navigates back here from a game -- see
    // GameUiState.hasSeenWelcome for why that's safe): auto-open the walkthrough exactly once
    // per install. Re-openable any time after that via the "?" icon below.
    var showWelcome by remember { mutableStateOf(!uiState.hasSeenWelcome) }
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
                ScoreChip(
                    label = "BEST",
                    value = uiState.game.best,
                    // Debug backdoor: 5 quick taps resets the "seen" flag for WelcomeDialog so
                    // it can be tested again without clearing app data -- see
                    // GameViewModel.onDebugResetWelcome. Unrelated to onDebugJumpToLevel30's
                    // same-shaped gesture on the in-game SCORE chip.
                    onSecretTap = onDebugResetWelcome
                )
            }
            if (uiState.currentStreak >= 1) {
                Text(
                    text = "🔥 ${uiState.currentStreak}-day streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Row(
                modifier = Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StartScreenIconButton(
                    label = "🎨",
                    contentDescription = "Theme",
                    onClick = { showThemePicker = true }
                )
                StartScreenIconButton(
                    label = "${uiState.selectedBoardSize.size}×${uiState.selectedBoardSize.size}",
                    contentDescription = "Board size",
                    onClick = { showBoardSizePicker = true }
                )
                StartScreenIconButton(
                    label = "📊",
                    contentDescription = "Stats",
                    onClick = { showStats = true }
                )
                StartScreenIconButton(
                    label = "❓",
                    contentDescription = "How to Play",
                    onClick = { showWelcome = true }
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

    if (showThemePicker) {
        ThemePickerDialog(
            currentPalette = uiState.selectedPalette,
            level = uiState.level,
            onSelect = onSelectPalette,
            onDismiss = { showThemePicker = false }
        )
    }
    if (showBoardSizePicker) {
        BoardSizePickerDialog(
            selectedBoardSize = uiState.selectedBoardSize,
            level = uiState.level,
            onSelect = onSelectBoardSize,
            onDismiss = { showBoardSizePicker = false }
        )
    }
    if (showStats) {
        StatsDialog(
            gamesPlayed = uiState.gamesPlayed,
            highestTileEver = uiState.highestTileEver,
            totalMerges = uiState.totalMerges,
            onDismiss = { showStats = false }
        )
    }
    if (showWelcome) {
        WelcomeDialog(
            onDismiss = {
                showWelcome = false
                onWelcomeDismissed()
            }
        )
    }
}

/** One of the three customize entry points on [StartScreen] (theme / board size / stats) --
 *  a small outlined icon button, matching the style [Header]/[Sidebar] used for their icon-only
 *  actions. [label] is either a single emoji or, for the board-size button, the currently
 *  selected size (e.g. "8×8") so the active choice is visible without opening the picker. */
@Composable
private fun StartScreenIconButton(label: String, contentDescription: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalPaletteColors.current.accent),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.semantics { this.contentDescription = contentDescription }
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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

/** One-line description of a [GameMode], shown on [StartModeCard] -- the only place mode is
 *  chosen now (see [StartScreen]). */
private fun modeDescription(mode: GameMode): String = when (mode) {
    GameMode.ORIGINAL -> "Classic rules: swipe to move, no Undo, no Jokers."
    GameMode.EXTENDED -> "Adds Undo plus the Teleport, Swap, Rotate, Double, and Bomb Jokers."
}
