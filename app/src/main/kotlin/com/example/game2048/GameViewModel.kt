package com.example.game2048

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game2048.logic.Direction
import com.example.game2048.logic.GameState
import com.example.game2048.logic.Game2048Engine
import com.example.game2048.logic.GameStateSerializer
import com.example.game2048.logic.Tile
import com.example.game2048.logic.TileMovement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val PREFS_NAME = "game2048_prefs"
private const val KEY_BEST_SCORE = "best_score"
private const val KEY_GAME_STATE = "game_state"

/**
 * Everything the UI needs to render one frame of the game, including enough detail about
 * the *last* move to drive per-tile slide/merge/spawn animations.
 *
 * [moveToken] changes on every successful move (and on new game) and has no meaning beyond
 * being a fresh value each time -- the UI keys animation-trigger side effects off it so a
 * move that happens to produce an identical-looking board still re-triggers animations.
 */
data class GameUiState(
    val game: GameState,
    val lastMovements: List<TileMovement> = emptyList(),
    val lastMergedTileIds: Set<Int> = emptySet(),
    val lastSpawnedTileIds: Set<Int> = emptySet(),
    /** Tile values from just before the last move, keyed by id -- lets the UI look up what
     *  value a "ghost" (merge-consumed) tile carried, since it's no longer in [game.tiles]. */
    val previousTilesById: Map<Int, Tile> = emptyMap(),
    /** Score gained by the last move, for a transient "+N" popup. */
    val lastScoreGained: Int = 0,
    val moveToken: Long = 0L,
    /** Bumped on a swipe that didn't change the board, so the UI can play a "denied" cue. */
    val invalidMoveToken: Long = 0L
)

/**
 * Holds the current [GameUiState], forwards swipes to the pure [Game2048Engine], and
 * persists the best score across app restarts via [android.content.SharedPreferences].
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = Game2048Engine()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(loadSavedGame() ?: freshGame())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    fun onSwipe(direction: Direction) {
        val current = _uiState.value
        val result = engine.move(current.game, direction)
        if (result.moved) {
            saveBestIfNeeded(result.state.best)
            saveGameState(result.state)
            _uiState.value = current.copy(
                game = result.state,
                lastMovements = result.movements,
                lastMergedTileIds = result.mergedTileIds,
                lastSpawnedTileIds = setOfNotNull(result.spawnedTileId),
                previousTilesById = current.game.tiles.associateBy { it.id },
                lastScoreGained = result.state.score - current.game.score,
                moveToken = current.moveToken + 1
            )
        } else {
            _uiState.value = current.copy(invalidMoveToken = current.invalidMoveToken + 1)
        }
    }

    fun onNewGame() {
        val fresh = freshGame(best = _uiState.value.game.best)
        saveGameState(fresh.game)
        _uiState.value = fresh
    }

    /** Called when the player dismisses the "You Win" banner and wants to keep playing. */
    fun onContinuePastWin() {
        _uiState.value = _uiState.value.let { it.copy(game = it.game.copy(continuePastWin = true)) }
        saveGameState(_uiState.value.game)
    }

    private fun freshGame(best: Int = loadBest()): GameUiState {
        val game = engine.newGame(best = best)
        return GameUiState(
            game = game,
            lastSpawnedTileIds = game.tiles.map { it.id }.toSet(),
            moveToken = 0L
        )
    }

    /** Restores the last saved board (if any) as a static snapshot -- no replayed animations. */
    private fun loadSavedGame(): GameUiState? {
        val encoded = prefs.getString(KEY_GAME_STATE, null) ?: return null
        val game = GameStateSerializer.decode(encoded) ?: return null
        if (game.tiles.isEmpty()) return null
        return GameUiState(game = game, moveToken = 0L)
    }

    private fun saveGameState(state: GameState) {
        viewModelScope.launch {
            prefs.edit().putString(KEY_GAME_STATE, GameStateSerializer.encode(state)).apply()
        }
    }

    private fun loadBest(): Int = prefs.getInt(KEY_BEST_SCORE, 0)

    private fun saveBestIfNeeded(best: Int) {
        viewModelScope.launch {
            if (best > loadBest()) {
                prefs.edit().putInt(KEY_BEST_SCORE, best).apply()
            }
        }
    }
}
