package com.example.game2048

import com.example.game2048.logic.BoardSizeOption
import com.example.game2048.logic.Direction
import com.example.game2048.logic.GameMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Runs [GameViewModel] (an `AndroidViewModel`, needing a real `Application`/`SharedPreferences`)
 * as a plain JVM test via Robolectric, rather than needing a device/emulator -- CI runs on
 * ordinary GitHub-hosted runners with neither. Firebase stays disabled here exactly like it is
 * in CI (no `google-services.json`), so every Firebase-touching path in [GameViewModel] takes
 * its fail-soft branch, same as a real signed-out install.
 *
 * These specifically cover the two real bugs found and fixed this session that the pure
 * `logic/` package's tests structurally can't catch, since both were about [GameViewModel]
 * wiring, not the engine itself: a mode/board-size preference change retroactively touching a
 * board already in progress.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameViewModelTest {

    private fun newViewModel(): GameViewModel = GameViewModel(RuntimeEnvironment.getApplication())

    @Test
    fun `starts with a fresh, playable board`() {
        val state = newViewModel().uiState.value
        assertTrue(state.game.tiles.isNotEmpty())
        assertFalse(state.game.isGameOver)
    }

    @Test
    fun `selecting a mode never changes the mode of a board already in progress`() {
        val viewModel = newViewModel()
        viewModel.onNewGame()
        val activeModeBefore = viewModel.uiState.value.gameMode

        viewModel.onSelectGameMode(GameMode.ORIGINAL)

        // Regression guard: selecting a mode used to retroactively change gameMode too,
        // silently granting/revoking Jokers on a board already in progress.
        assertEquals(activeModeBefore, viewModel.uiState.value.gameMode)
        assertEquals(GameMode.ORIGINAL, viewModel.uiState.value.selectedGameMode)
    }

    @Test
    fun `onNewGame locks the fresh board to whatever mode is currently selected`() {
        val viewModel = newViewModel()
        viewModel.onSelectGameMode(GameMode.ORIGINAL)

        viewModel.onNewGame()

        assertEquals(GameMode.ORIGINAL, viewModel.uiState.value.gameMode)
    }

    @Test
    fun `resolvedBoardSize reflects the selected preference once it is unlocked`() {
        val viewModel = newViewModel()
        viewModel.onSelectBoardSize(BoardSizeOption.CLASSIC)
        assertEquals(4, viewModel.resolvedBoardSize())
    }

    @Test
    fun `onSelectBoardSize never resizes a board already in progress`() {
        val viewModel = newViewModel()
        viewModel.onDebugJumpToLevel30() // unlocks every board-size tier
        viewModel.onNewGame() // starts a Classic (4x4) board, the default preference
        val activeSizeBefore = viewModel.uiState.value.game.boardSize

        viewModel.onSelectBoardSize(BoardSizeOption.GIANT)

        // Regression guard: this is the board-size analogue of the mode-switch bug above --
        // Play used to resume this exact board unresized until its own New Game button was
        // tapped separately. The preference is free to change; the active board must not.
        assertEquals(activeSizeBefore, viewModel.uiState.value.game.boardSize)
        assertEquals(8, viewModel.resolvedBoardSize())
    }

    @Test
    fun `onNewGame actually applies the selected board size`() {
        val viewModel = newViewModel()
        viewModel.onDebugJumpToLevel30()
        viewModel.onSelectBoardSize(BoardSizeOption.GIANT)

        viewModel.onNewGame()

        assertEquals(8, viewModel.uiState.value.game.boardSize)
    }

    @Test
    fun `a swipe in some legal direction advances the move token`() {
        val viewModel = newViewModel()
        val before = viewModel.uiState.value.moveToken
        // The starting board is randomly seeded, so try every direction -- at least one must
        // be legal on a freshly dealt two-tile board.
        Direction.entries.forEach { viewModel.onSwipe(it) }
        assertTrue(viewModel.uiState.value.moveToken > before)
    }

    @Test
    fun `best score persists across a simulated app restart`() {
        val app = RuntimeEnvironment.getApplication()
        val first = GameViewModel(app)
        repeat(6) { Direction.entries.forEach { first.onSwipe(it) } }
        val bestAfterPlaying = first.uiState.value.game.best

        val second = GameViewModel(app) // same Application -> same SharedPreferences
        assertEquals(bestAfterPlaying, second.uiState.value.game.best)
    }
}
