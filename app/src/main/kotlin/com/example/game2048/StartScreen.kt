package com.example.game2048

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game2048.logic.BoardSizeOption
import com.example.game2048.logic.DailyChallengeTracker
import com.example.game2048.logic.GameMode
import com.example.game2048.logic.LevelTracker
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
    onDebugResetWelcome: () -> Unit,
    onClaimDailyReward: () -> Unit,
    onAnalyticsConsentChanged: (Boolean) -> Unit,
    onOpenDailyChallenge: () -> Unit
) {
    val accent = LocalPaletteColors.current.accent
    var showThemePicker by remember { mutableStateOf(false) }
    var showBoardSizePicker by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    // Evaluated once, the first time this composable enters composition (i.e. once per real
    // app launch, or whenever the player navigates back here from a game -- see
    // GameUiState.hasSeenWelcome for why that's safe): auto-open the walkthrough exactly once
    // per install. Re-openable any time after that via the "?" icon below.
    var showWelcome by remember { mutableStateOf(!uiState.hasSeenWelcome) }
    // Only auto-opens immediately if Welcome isn't also about to show -- a brand-new install
    // has both hasSeenWelcome == false and a pending reward (day 1 of the streak), and stacking
    // two modals on first launch would be a mess. If Welcome is showing, its onDismiss below
    // opens this one right after instead.
    var showDailyReward by remember {
        mutableStateOf(uiState.hasSeenWelcome && uiState.pendingDailyReward != null)
    }
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
            // Makes the LEVEL chip's number concrete: exactly how much of the current level's
            // XP span is earned, and how much more the next level needs -- not just a bar.
            val xp = LevelTracker.xpProgress(uiState.cumulativeScore)
            LinearProgressIndicator(
                progress = { uiState.levelProgress },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(140.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                drawStopIndicator = {}
            )
            Text(
                text = "${xp.earnedInLevel} / ${xp.spanForLevel} XP to Level ${uiState.level + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 3.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            OrbitVariantWeb(diameter = orbitDiameter)
            Spacer(modifier = Modifier.height(16.dp))
            PlayCard(
                selectedGameMode = uiState.selectedGameMode,
                selectedBoardSize = uiState.selectedBoardSize,
                onPlay = onPlay,
                onOpenModePicker = { showModePicker = true }
            )
            DailyChallengeCard(
                completedToday = uiState.dailyChallengeCompletedToday,
                lastScore = uiState.dailyChallengeLastScore,
                bestScore = uiState.dailyChallengeBestScore,
                onClick = onOpenDailyChallenge,
                modifier = Modifier.padding(top = 14.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            UtilityRow(
                selectedBoardSize = uiState.selectedBoardSize,
                onTheme = { showThemePicker = true },
                onBoardSize = { showBoardSizePicker = true },
                onStats = { showStats = true },
                onHelp = { showWelcome = true }
            )
            if (uiState.currentStreak >= 1) {
                Text(
                    text = "🔥 ${uiState.currentStreak} day streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }

    if (showModePicker) {
        GameModePickerDialog(
            selectedGameMode = uiState.selectedGameMode,
            onSelect = onSelectGameMode,
            onDismiss = { showModePicker = false }
        )
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
            analyticsConsentGranted = uiState.analyticsConsentGranted,
            onAnalyticsConsentChanged = onAnalyticsConsentChanged,
            onDismiss = { showStats = false }
        )
    }
    // Gates everything else on first launch: nothing is collected until this is answered, so it
    // has to come before the walkthrough rather than stacking on top of it.
    if (uiState.analyticsConsentGranted == null) {
        AnalyticsConsentDialog(onAnswer = onAnalyticsConsentChanged)
    } else if (showWelcome) {
        WelcomeDialog(
            onDismiss = {
                showWelcome = false
                onWelcomeDismissed()
                if (uiState.pendingDailyReward != null) showDailyReward = true
            }
        )
    }
    if (showDailyReward) {
        val reward = uiState.pendingDailyReward
        if (reward != null) {
            DailyRewardDialog(
                streakDay = uiState.currentStreak,
                rewardXp = reward,
                onClaim = {
                    showDailyReward = false
                    onClaimDailyReward()
                }
            )
        }
    }
}

/** The dominant call-to-action on [StartScreen] -- a large filled card (not the old outlined
 *  button) so Play reads as the obvious first tap, with the active [selectedGameMode]/
 *  [selectedBoardSize] shown as a small secondary control underneath rather than as the two
 *  large Original/Extended cards this replaced. Tapping that control (not the card body) opens
 *  [GameModePickerDialog] -- board size still has its own picker via the utility row below. */
@Composable
private fun PlayCard(
    selectedGameMode: GameMode,
    selectedBoardSize: BoardSizeOption,
    onPlay: () -> Unit,
    onOpenModePicker: () -> Unit
) {
    val accent = LocalPaletteColors.current.accent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accent)
            .clickable(onClick = onPlay)
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "PLAY",
            color = MaterialTheme.colorScheme.background,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            letterSpacing = 1.sp
        )
        Text(
            text = "${selectedGameMode.displayName} · ${selectedBoardSize.size}×${selectedBoardSize.size} ˅",
            color = MaterialTheme.colorScheme.background,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.12f))
                .clickable(onClick = onOpenModePicker)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = "Change game mode" }
        )
    }
}

/** Compact bottom sheet listing [GameMode.entries] -- what replaced the two large Original/
 *  Extended cards previously shown directly on [StartScreen] (see [PlayCard]). Same
 *  [onSelect]/[GameViewModel.onSelectGameMode] wiring as before; this is only a presentation
 *  change. */
@Composable
private fun GameModePickerDialog(
    selectedGameMode: GameMode,
    onSelect: (GameMode) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalPaletteColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Game Mode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GameMode.entries.forEach { mode ->
                    val selected = mode == selectedGameMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) palette.accent.copy(alpha = 0.15f) else palette.surfaceChip)
                            .clickable { onSelect(mode) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (selected) "●" else "○",
                            color = palette.accent,
                            fontWeight = FontWeight.Bold
                        )
                        Column {
                            Text(
                                text = mode.displayName,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = modeDescription(mode),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    )
}

/** The five customize/utility entry points (theme, board size, stats, help, account) as a
 *  compact icon row -- deliberately small and borderless so they read as secondary to
 *  [PlayCard], not competing with it the way the old outlined icon-button row did. */
@Composable
private fun UtilityRow(
    selectedBoardSize: BoardSizeOption,
    onTheme: () -> Unit,
    onBoardSize: () -> Unit,
    onStats: () -> Unit,
    onHelp: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        UtilityIcon(label = "🎨", description = "Theme", onClick = onTheme)
        UtilityIcon(
            label = "${selectedBoardSize.size}×${selectedBoardSize.size}",
            description = "Board size",
            onClick = onBoardSize
        )
        UtilityIcon(label = "📊", description = "Stats", onClick = onStats)
        UtilityIcon(label = "❓", description = "How to Play", onClick = onHelp)
    }
}

@Composable
private fun UtilityIcon(label: String, description: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = description }
    ) {
        // Explicit color, not just the emoji-icon labels' default rendering: an emoji glyph
        // (🎨/📊/❓/🔒/☁️) carries its own color regardless of what's set here, but the one
        // text label (the board-size ratio, e.g. "6×6") doesn't -- left unset, it fell back to
        // black-on-dark-background and was unreadable in dark mode.
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = LocalPaletteColors.current.accent
        )
    }
}

/** Entry point to [DailyChallengeScreen] -- its own wide card rather than a 7th tiny icon
 *  crammed into the row above, since this is a headline feature worth real visual weight, not
 *  a settings shortcut. Doubles as a status display: shows today's score once played instead of
 *  just a static label, so there's a reason to glance at it even after finishing. */
@Composable
private fun DailyChallengeCard(
    completedToday: Boolean,
    lastScore: Int,
    bestScore: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPaletteColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceChip)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = if (completedToday) "✅ Daily Challenge" else "🗓️ Daily Challenge",
                fontWeight = FontWeight.Bold,
                color = palette.accent
            )
            Text(
                text = if (completedToday) {
                    "Today's score: $lastScore · Best: $bestScore"
                } else {
                    "Same board for everyone today — ${DailyChallengeTracker.MOVE_CAP} moves, one shot."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Text("›", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = palette.accent)
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


/** One-line description of a [GameMode], shown on [GameModePickerDialog]. */
private fun modeDescription(mode: GameMode): String = when (mode) {
    GameMode.ORIGINAL -> "Classic rules: swipe to move, no Undo, no Jokers."
    GameMode.EXTENDED -> "Adds Undo plus the Teleport, Swap, Rotate, Double, and Bomb Jokers."
}
