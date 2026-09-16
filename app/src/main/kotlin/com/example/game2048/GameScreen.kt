package com.example.game2048

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

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
            onSelectPalette = viewModel::onSelectPalette,
            onSelectPattern = viewModel::onSelectPattern,
            onSelectBoardSize = viewModel::onSelectBoardSize,
            onWelcomeDismissed = viewModel::onWelcomeDismissed,
            onDebugResetWelcome = viewModel::onDebugResetWelcome,
            onClaimDailyReward = viewModel::onClaimDailyReward,
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

@Composable
fun GameScreen(viewModel: GameViewModel = viewModel(), onNavigateHome: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    // Guards the persistent New Game button (Header/Sidebar) against a stray tap wiping a
    // board in progress -- see ConfirmNewGameDialog. Skipped once the game has already ended
    // (isGameOver below): there's nothing left to lose at that point, so confirming would just
    // be friction on the one moment New Game is actually meant to be reached for quickly.
    var showConfirmNewGame by remember { mutableStateOf(false) }
    val onNewGameRequested = {
        if (uiState.game.isGameOver) viewModel.onNewGame() else showConfirmNewGame = true
    }

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
                    onNewGame = onNewGameRequested,
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
                    onNewGame = onNewGameRequested,
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

    if (showConfirmNewGame) {
        ConfirmNewGameDialog(
            onConfirm = {
                showConfirmNewGame = false
                viewModel.onNewGame()
            },
            onDismiss = { showConfirmNewGame = false }
        )
    }
}
