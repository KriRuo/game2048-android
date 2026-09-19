package com.example.game2048

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.game2048.logic.BoardSizeOption
import com.example.game2048.logic.BoardSizeUnlocks
import com.example.game2048.logic.Direction
import com.example.game2048.logic.GameMode
import com.example.game2048.logic.GameState
import com.example.game2048.logic.Game2048Engine
import com.example.game2048.logic.GameStateSerializer
import com.example.game2048.logic.Joker
import com.example.game2048.logic.LevelTracker
import com.example.game2048.logic.StreakState
import com.example.game2048.logic.StreakTracker
import com.example.game2048.logic.ThemeUnlocks
import com.example.game2048.logic.Tile
import com.example.game2048.logic.TilePalette
import com.example.game2048.logic.TileMovement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.TimeZone

private const val PREFS_NAME = "game2048_prefs"
private const val KEY_BEST_SCORE = "best_score"
private const val KEY_GAME_STATE = "game_state"
private const val KEY_STREAK_CURRENT = "streak_current"
private const val KEY_STREAK_LONGEST = "streak_longest"
private const val KEY_STREAK_LAST_DAY = "streak_last_day"
private const val KEY_CUMULATIVE_SCORE = "cumulative_score"
private const val KEY_SELECTED_PALETTE = "selected_palette"
private const val KEY_SELECTED_BOARD_SIZE = "selected_board_size"
private const val KEY_SELECTED_GAME_MODE = "selected_game_mode"
/** The ruleset the *currently saved board* was actually started under -- separate from
 *  [KEY_SELECTED_GAME_MODE] (the Start Screen preference for the *next* board). Written once
 *  per New Game, never touched by [GameViewModel.onSelectGameMode] or a cloud-progress sync. */
private const val KEY_ACTIVE_GAME_MODE = "active_game_mode"
private const val KEY_GAMES_PLAYED = "games_played"
private const val KEY_HIGHEST_TILE_EVER = "highest_tile_ever"
private const val KEY_TOTAL_MERGES = "total_merges"
private const val KEY_HAS_SEEN_WELCOME = "has_seen_welcome"
/** The uid last signed into on this device -- lets [GameViewModel.applyCloudProgressIfSignedIn]
 *  tell "this device's own progress, being claimed by its first account" (a legitimate push)
 *  apart from "leftover progress from whoever was signed in here before" (which a brand-new
 *  second account must NOT silently inherit). */
private const val KEY_LAST_SYNCED_UID = "last_synced_uid"

/** Single-move undos allowed per game (see [GameViewModel.onUndo]). Intentionally *not*
 *  persisted across a process restart, along with the one-move [GameUiState.undoState] snapshot
 *  it spends -- resuming a killed app resets the allowance. Acceptable for a casual
 *  single-player game with no stakes riding on it (same reasoning as the debug level-30
 *  shortcut existing at all). Not private: [GameScreen] reads it to size the dash/pip
 *  indicator under the Undo button. */
const val MAX_UNDOS = 3

/** Uses allowed per game for each Joker (see [GameUiState.teleportsRemaining] and friends),
 *  reset to this on New Game -- same not-persisted-across-restart reasoning as [MAX_UNDOS].
 *  Not private, for the same dash-indicator reason. */
const val MAX_TELEPORTS = 1
const val MAX_SWAPS = 1
const val MAX_BOMBS = 1
const val MAX_DOUBLES = 1
const val MAX_ROTATES = 1

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
    val invalidMoveToken: Long = 0L,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    /** Highest streak milestone just reached this app open, if any -- shown once, then
     *  cleared via [GameViewModel.onMilestoneBannerShown]. */
    val justReachedMilestone: Int? = null,
    /** Player level, derived from cumulative score across every game ever played -- never
     *  resets when a board does (see [LevelTracker]). */
    val level: Int = 1,
    /** Progress toward the next level, in [0f, 1f), for a progress bar. */
    val levelProgress: Float = 0f,
    /** The raw cumulative score [level]/[levelProgress] are derived from, exposed so the UI can
     *  show actual numbers ("1,234 / 2,000 XP") via [LevelTracker.xpProgress] instead of just a
     *  bar -- see [StartScreen] and [Header]/[Sidebar]. */
    val cumulativeScore: Long = 0L,
    /** The level at the moment the current board started, so the game-over screen can tell
     *  whether this run leveled the player up. */
    val levelAtGameStart: Int = 1,
    /** The currently-active tile color palette (see [TilePalette]). */
    val selectedPalette: TilePalette = TilePalette.DEFAULT,
    /** The player's stored board-size preference (see [BoardSizeOption]). Takes effect on the
     *  *next* New Game -- [game]'s actual size is [GameState.boardSize], which doesn't change
     *  mid-game even if this is changed while playing. */
    val selectedBoardSize: BoardSizeOption = BoardSizeOption.DEFAULT,
    /** Snapshot of [game] from just before the last move, or null if there's nothing to undo
     *  (fresh game, or the allowance below is spent) -- see [GameViewModel.onUndo]. */
    val undoState: GameState? = null,
    /** Single-move undos left this game; resets to [MAX_UNDOS] on New Game. */
    val undosRemaining: Int = MAX_UNDOS,
    /** Which ruleset [game] itself was started under -- see [GameMode]. ORIGINAL hides Undo and
     *  all Jokers from the UI. Locked in at New Game time and never changed on an in-progress
     *  board -- otherwise a board started under ORIGINAL could gain Jokers/Undo mid-game just
     *  from picking EXTENDED on the Start Screen, which is exactly the bug [selectedGameMode]
     *  exists to prevent. */
    val gameMode: GameMode = GameMode.DEFAULT,
    /** The Start Screen's ruleset picker selection -- takes effect on the *next* New Game only,
     *  same pattern as [selectedBoardSize]/[selectedPalette]. [Game2048App]'s onPlay compares
     *  this against [gameMode] and starts a fresh board whenever they differ, so switching modes
     *  can never retroactively grant (or take away) Jokers/Undo on a board already in progress. */
    val selectedGameMode: GameMode = GameMode.DEFAULT,
    /** The Joker currently being aimed (player tapped its button, hasn't tapped a target yet
     *  or is midway through Swap's two-tile pick), or null when none is active. Rotate never
     *  appears here -- it has no target, see [GameViewModel.onRotate]. */
    val activeJoker: Joker? = null,
    /** For [Joker.TELEPORT]: the tile picked to move. For [Joker.SWAP]: the first of the two
     *  tiles picked. Unused (stays null) for [Joker.BOMB]/[Joker.DOUBLE], which complete on the
     *  first tile tap. Null until the player has tapped a tile after activating a Joker. */
    val jokerFirstTileId: Int? = null,
    /** Teleport uses left this game; resets to [MAX_TELEPORTS] on New Game. */
    val teleportsRemaining: Int = MAX_TELEPORTS,
    /** Swap uses left this game; resets to [MAX_SWAPS] on New Game. */
    val swapsRemaining: Int = MAX_SWAPS,
    /** Bomb uses left this game; resets to [MAX_BOMBS] on New Game. */
    val bombsRemaining: Int = MAX_BOMBS,
    /** Double uses left this game; resets to [MAX_DOUBLES] on New Game. */
    val doublesRemaining: Int = MAX_DOUBLES,
    /** Rotate uses left this game; resets to [MAX_ROTATES] on New Game. */
    val rotatesRemaining: Int = MAX_ROTATES,
    /** Lifetime stats, never reset by New Game (see the "Your Stats" section of Customize). */
    val gamesPlayed: Int = 0,
    val highestTileEver: Int = 0,
    val totalMerges: Long = 0L,
    /** Whether the first-run "How to Play" walkthrough ([WelcomeDialog]) has already been
     *  dismissed on this install. Defaults to `true` here so any ad-hoc [GameUiState] (e.g.
     *  [freshGame]'s pre-[buildInitialState] value) never accidentally implies "show it" --
     *  the real value is only ever read from [android.content.SharedPreferences] in
     *  [buildInitialState]. [StartScreen] reads this once, on first composition, to decide
     *  whether to auto-open the walkthrough. */
    val hasSeenWelcome: Boolean = true,
    /** Bonus XP waiting to be claimed via [GameViewModel.onClaimDailyReward] for showing up
     *  today, or null if there's nothing pending -- see [DailyRewardDialog]. Set once, in
     *  [GameViewModel.buildInitialState], the moment [currentStreak] advances to a new day;
     *  never recomputed afterward, so claiming it (or simply not claiming it before the app is
     *  next closed) is final for that day -- same not-a-big-deal-either-way reasoning as
     *  [MAX_UNDOS] not surviving a process restart. */
    val pendingDailyReward: Int? = null,
    /** Signed-in Firebase uid, or null when signed out -- see [AccountDialog]. Purely optional
     *  cross-device cloud sync; the game is always fully playable while this is null. */
    val signedInUserId: String? = null,
    /** True while a sign-up/sign-in request is in flight, so [AccountDialog] can disable its
     *  buttons and show a spinner instead of allowing a duplicate submit. */
    val authBusy: Boolean = false,
    /** Message from the last failed sign-up/sign-in/reset attempt, or null -- cleared by
     *  [GameViewModel.onDismissAuthError] or the next attempt. */
    val authError: String? = null,
    /** True right after [GameViewModel.onResetPassword] succeeds, so [AccountDialog] can show
     *  a "check your inbox" confirmation -- cleared by [GameViewModel.onDismissAuthError] or
     *  the next sign-in/sign-up/reset attempt. */
    val passwordResetSent: Boolean = false
)

/**
 * Holds the current [GameUiState], forwards swipes to the pure [Game2048Engine], and
 * persists the best score across app restarts via [android.content.SharedPreferences].
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = Game2048Engine()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Application.MODE_PRIVATE)

    init {
        // Both no-op entirely unless google-services.json was present at build time -- see
        // AppAnalytics.kt/AuthRepository.kt.
        AppAnalytics.init(application)
        AuthRepository.init()
    }

    // Cached in memory so a move doesn't re-read them from disk every time. Declared before
    // _uiState because buildInitialState() reads them.
    private var cumulativeScore: Long = prefs.getLong(KEY_CUMULATIVE_SCORE, 0L)
    private var bestScore: Int = prefs.getInt(KEY_BEST_SCORE, 0)
    private var selectedPalette: TilePalette = TilePalette.fromId(prefs.getString(KEY_SELECTED_PALETTE, null))
    private var selectedBoardSize: BoardSizeOption = BoardSizeOption.fromId(prefs.getString(KEY_SELECTED_BOARD_SIZE, null))
    private var selectedGameMode: GameMode = GameMode.fromId(prefs.getString(KEY_SELECTED_GAME_MODE, null))
    private var gamesPlayed: Int = prefs.getInt(KEY_GAMES_PLAYED, 0)
    private var highestTileEver: Int = prefs.getInt(KEY_HIGHEST_TILE_EVER, 0)
    private var totalMerges: Long = prefs.getLong(KEY_TOTAL_MERGES, 0L)

    private val _uiState = MutableStateFlow(buildInitialState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        // Reacts to sign-in/out from anywhere (e.g. AuthRepository restoring a previous
        // session on app open, not just an in-app AccountDialog action). See
        // applyCloudProgressIfSignedIn() for what a sign-in actually does.
        viewModelScope.launch {
            AuthRepository.currentUserId.collect { uid ->
                _uiState.value = _uiState.value.copy(signedInUserId = uid)
                AppAnalytics.setUserId(uid)
                if (uid != null) applyCloudProgressIfSignedIn(uid)
            }
        }
    }

    fun onSwipe(direction: Direction) {
        val current = _uiState.value
        val result = engine.move(current.game, direction)
        if (result.moved) {
            val gained = result.state.score - current.game.score
            cumulativeScore += gained
            val bestChanged = result.state.best > bestScore
            if (bestChanged) bestScore = result.state.best
            totalMerges += result.mergedTileIds.size
            val highestTile = result.state.tiles.maxOfOrNull { it.value } ?: 0
            if (highestTile > highestTileEver) highestTileEver = highestTile
            val level = LevelTracker.levelForCumulativeScore(cumulativeScore)
            if (level > current.level) {
                AppAnalytics.logLevelUp(level)
                ThemeUnlocks.newlyUnlocked(current.levelAtGameStart, level)?.let { AppAnalytics.logThemeUnlocked(it.id) }
                BoardSizeUnlocks.newlyUnlocked(current.levelAtGameStart, level)?.let { AppAnalytics.logBoardSizeUnlocked(it.id) }
            }
            if (!current.game.isGameOver && result.state.isGameOver) {
                AppAnalytics.logGameOver(result.state.score, level)
            }
            persist(result.state, includeBest = bestChanged)
            _uiState.value = current.copy(
                game = result.state,
                lastMovements = result.movements,
                lastMergedTileIds = result.mergedTileIds,
                lastSpawnedTileIds = setOfNotNull(result.spawnedTileId),
                previousTilesById = current.game.tiles.associateBy { it.id },
                lastScoreGained = gained,
                moveToken = current.moveToken + 1,
                level = level,
                levelProgress = LevelTracker.progressToNextLevel(cumulativeScore),
                cumulativeScore = cumulativeScore,
                undoState = current.game,
                highestTileEver = highestTileEver,
                totalMerges = totalMerges
            )
        } else {
            _uiState.value = current.copy(invalidMoveToken = current.invalidMoveToken + 1)
        }
    }

    /** Reverts the single most recent move. Costs one of [GameUiState.undosRemaining] (resets
     *  to [MAX_UNDOS] on New Game) and no-ops if there's nothing to undo or the allowance is
     *  spent. Also reverses that move's contribution to cumulativeScore, so undo can't be used
     *  to farm Level XP by repeatedly making then undoing the same merge -- unlike
     *  [highestTileEver]/[totalMerges] (framed as lifetime "ever" stats that don't need this),
     *  cumulativeScore gates real unlocks (themes, board sizes) so it can't be left exploitable. */
    fun onUndo() {
        val current = _uiState.value
        val previous = current.undoState ?: return
        if (current.undosRemaining <= 0) return

        val gained = current.game.score - previous.score
        cumulativeScore = (cumulativeScore - gained).coerceAtLeast(0L)
        // best never regresses, even on undo -- the player genuinely reached it, if only briefly.
        val restored = previous.copy(best = bestScore)

        persist(restored, includeBest = false)
        _uiState.value = current.copy(
            game = restored,
            lastMovements = emptyList(),
            lastMergedTileIds = emptySet(),
            lastSpawnedTileIds = emptySet(),
            lastScoreGained = 0,
            moveToken = current.moveToken + 1,
            level = LevelTracker.levelForCumulativeScore(cumulativeScore),
            levelProgress = LevelTracker.progressToNextLevel(cumulativeScore),
            cumulativeScore = cumulativeScore,
            undoState = null,
            undosRemaining = current.undosRemaining - 1
        )
    }

    fun onNewGame() {
        val current = _uiState.value
        val level = current.level
        val boardSize = if (BoardSizeUnlocks.isUnlocked(selectedBoardSize, level)) selectedBoardSize.size else BoardSizeOption.DEFAULT.size
        AppAnalytics.logGameStarted()
        gamesPlayed += 1
        // Locks this fresh board to whatever mode is currently selected -- see KEY_ACTIVE_GAME_MODE.
        prefs.edit().putString(KEY_ACTIVE_GAME_MODE, selectedGameMode.id).commit()
        val fresh = freshGame(best = bestScore, boardSize = boardSize).copy(
            level = level,
            levelProgress = current.levelProgress,
            cumulativeScore = cumulativeScore,
            levelAtGameStart = level,
            selectedPalette = selectedPalette,
            selectedBoardSize = selectedBoardSize,
            gameMode = selectedGameMode,
            selectedGameMode = selectedGameMode,
            gamesPlayed = gamesPlayed,
            highestTileEver = highestTileEver,
            totalMerges = totalMerges,
            // freshGame() defaults these to 0 -- New Game only resets the *board*, not the
            // streak badge (that's real persisted state, only touched by loadStreak/saveStreak
            // on app open; pre-existing bug found while adding undo -- the badge was vanishing
            // for the rest of the session every time New Game was tapped).
            currentStreak = current.currentStreak,
            longestStreak = current.longestStreak
        )
        persist(fresh.game, includeBest = false)
        _uiState.value = fresh
    }

    /** No-ops if [palette] isn't unlocked yet at the player's current level. */
    fun onSelectPalette(palette: TilePalette) {
        val current = _uiState.value
        if (!ThemeUnlocks.isUnlocked(palette, current.level)) return
        selectedPalette = palette
        prefs.edit().putString(KEY_SELECTED_PALETTE, palette.id).commit()
        syncToCloudIfSignedIn()
        _uiState.value = current.copy(selectedPalette = palette)
    }

    /** No-ops if [option] isn't unlocked yet at the player's current level. Only updates the
     *  stored preference for the *next* New Game -- selecting this mid-game doesn't resize (or
     *  reset) the board currently in play; see [GameUiState.selectedBoardSize]. */
    fun onSelectBoardSize(option: BoardSizeOption) {
        val current = _uiState.value
        if (!BoardSizeUnlocks.isUnlocked(option, current.level)) return
        selectedBoardSize = option
        prefs.edit().putString(KEY_SELECTED_BOARD_SIZE, option.id).commit()
        syncToCloudIfSignedIn()
        _uiState.value = current.copy(selectedBoardSize = option)
    }

    /** Only updates the stored preference for the *next* New Game -- same pattern as
     *  [onSelectBoardSize]. Never touches the board currently in play: [GameUiState.gameMode]
     *  (what actually gates Undo/Jokers) only changes at New Game time, in [onNewGame]. See
     *  [GameUiState.selectedGameMode]. */
    fun onSelectGameMode(mode: GameMode) {
        if (mode == selectedGameMode) return
        selectedGameMode = mode
        prefs.edit().putString(KEY_SELECTED_GAME_MODE, mode.id).commit()
        syncToCloudIfSignedIn()
        _uiState.value = _uiState.value.copy(selectedGameMode = mode)
    }

    /** Activates the Teleport Joker: the next tile tap picks (or re-picks) the tile to move,
     *  and the next empty-cell tap ([onJokerCellTapped]) moves it there. No-ops in
     *  [GameMode.ORIGINAL] or once the allowance is spent. */
    fun onStartTeleport() {
        val current = _uiState.value
        if (current.gameMode != GameMode.EXTENDED || current.teleportsRemaining <= 0) return
        _uiState.value = current.copy(activeJoker = Joker.TELEPORT, jokerFirstTileId = null)
    }

    /** Activates the Swap Joker: the next two tile taps ([onJokerTileTapped]) exchange
     *  positions. No-ops in [GameMode.ORIGINAL] or once the allowance is spent. */
    fun onStartSwap() {
        val current = _uiState.value
        if (current.gameMode != GameMode.EXTENDED || current.swapsRemaining <= 0) return
        _uiState.value = current.copy(activeJoker = Joker.SWAP, jokerFirstTileId = null)
    }

    /** Activates the Bomb Joker: the next tile tap ([onJokerTileTapped]) removes it. No-ops in
     *  [GameMode.ORIGINAL] or once the allowance is spent. */
    fun onStartBomb() {
        val current = _uiState.value
        if (current.gameMode != GameMode.EXTENDED || current.bombsRemaining <= 0) return
        _uiState.value = current.copy(activeJoker = Joker.BOMB, jokerFirstTileId = null)
    }

    /** Activates the Double Joker: the next tile tap ([onJokerTileTapped]) doubles its value.
     *  No-ops in [GameMode.ORIGINAL] or once the allowance is spent. */
    fun onStartDouble() {
        val current = _uiState.value
        if (current.gameMode != GameMode.EXTENDED || current.doublesRemaining <= 0) return
        _uiState.value = current.copy(activeJoker = Joker.DOUBLE, jokerFirstTileId = null)
    }

    /** Rotates the board 90 degrees clockwise. Unlike the other Jokers this has no target to
     *  pick, so it applies immediately rather than going through [onStartTeleport]'s activate-
     *  then-tap flow. No-ops in [GameMode.ORIGINAL] or once the allowance is spent. */
    fun onRotate() {
        val current = _uiState.value
        if (current.gameMode != GameMode.EXTENDED || current.rotatesRemaining <= 0) return
        val result = engine.rotateBoard(current.game)
        AppAnalytics.logJokerUsed("rotate")
        persist(result.state, includeBest = false)
        _uiState.value = current.copy(
            game = result.state,
            lastMovements = result.movements,
            lastMergedTileIds = emptySet(),
            lastSpawnedTileIds = emptySet(),
            previousTilesById = current.game.tiles.associateBy { it.id },
            lastScoreGained = 0,
            moveToken = current.moveToken + 1,
            undoState = current.game,
            rotatesRemaining = current.rotatesRemaining - 1
        )
    }

    /** Backs out of whichever Joker is active without spending its allowance. */
    fun onCancelJoker() {
        _uiState.value = _uiState.value.copy(activeJoker = null, jokerFirstTileId = null)
    }

    /** Tap on a tile while a Joker is active. Teleport: (re-)picks the tile to move -- tapping
     *  a different tile before an empty cell just changes which one will move. Swap: picks the
     *  first tile, then completes as soon as a *different* tile is tapped as the second pick
     *  (tapping the same tile again just re-picks it, so a mis-tap isn't a dead end). Bomb and
     *  Double have only one thing to pick, so they complete on this same tap. */
    fun onJokerTileTapped(tileId: Int) {
        val current = _uiState.value
        when (current.activeJoker) {
            Joker.TELEPORT -> _uiState.value = current.copy(jokerFirstTileId = tileId)
            Joker.SWAP -> {
                val first = current.jokerFirstTileId
                if (first == null || first == tileId) {
                    _uiState.value = current.copy(jokerFirstTileId = tileId)
                } else {
                    applySwap(first, tileId)
                }
            }
            Joker.BOMB -> applyBomb(tileId)
            Joker.DOUBLE -> applyDouble(tileId)
            null -> Unit
        }
    }

    /** Tap on an empty cell while Teleport is active and a tile has been picked: completes the
     *  move. No-op otherwise (including for Swap, which only ever targets tiles). */
    fun onJokerCellTapped(row: Int, col: Int) {
        val current = _uiState.value
        if (current.activeJoker != Joker.TELEPORT) return
        val tileId = current.jokerFirstTileId ?: return
        applyTeleport(tileId, row, col)
    }

    private fun applyTeleport(tileId: Int, row: Int, col: Int) {
        val current = _uiState.value
        val result = engine.teleportTile(current.game, tileId, row, col)
        if (!result.applied) return
        AppAnalytics.logJokerUsed("teleport")
        if (!current.game.isGameOver && result.state.isGameOver) {
            AppAnalytics.logGameOver(result.state.score, current.level)
        }
        persist(result.state, includeBest = false)
        _uiState.value = current.copy(
            game = result.state,
            lastMovements = result.movements,
            lastMergedTileIds = emptySet(),
            lastSpawnedTileIds = emptySet(),
            previousTilesById = current.game.tiles.associateBy { it.id },
            lastScoreGained = 0,
            moveToken = current.moveToken + 1,
            undoState = current.game,
            activeJoker = null,
            jokerFirstTileId = null,
            teleportsRemaining = current.teleportsRemaining - 1
        )
    }

    private fun applySwap(tileId1: Int, tileId2: Int) {
        val current = _uiState.value
        val result = engine.swapTiles(current.game, tileId1, tileId2)
        if (!result.applied) return
        AppAnalytics.logJokerUsed("swap")
        if (!current.game.isGameOver && result.state.isGameOver) {
            AppAnalytics.logGameOver(result.state.score, current.level)
        }
        persist(result.state, includeBest = false)
        _uiState.value = current.copy(
            game = result.state,
            lastMovements = result.movements,
            lastMergedTileIds = emptySet(),
            lastSpawnedTileIds = emptySet(),
            previousTilesById = current.game.tiles.associateBy { it.id },
            lastScoreGained = 0,
            moveToken = current.moveToken + 1,
            undoState = current.game,
            activeJoker = null,
            jokerFirstTileId = null,
            swapsRemaining = current.swapsRemaining - 1
        )
    }

    private fun applyBomb(tileId: Int) {
        val current = _uiState.value
        val result = engine.bombTile(current.game, tileId)
        if (!result.applied) return
        AppAnalytics.logJokerUsed("bomb")
        if (!current.game.isGameOver && result.state.isGameOver) {
            AppAnalytics.logGameOver(result.state.score, current.level)
        }
        persist(result.state, includeBest = false)
        _uiState.value = current.copy(
            game = result.state,
            lastMovements = emptyList(),
            lastMergedTileIds = emptySet(),
            lastSpawnedTileIds = emptySet(),
            previousTilesById = current.game.tiles.associateBy { it.id },
            lastScoreGained = 0,
            moveToken = current.moveToken + 1,
            undoState = current.game,
            activeJoker = null,
            jokerFirstTileId = null,
            bombsRemaining = current.bombsRemaining - 1
        )
    }

    /** Scores exactly like [onSwipe] does for a merge -- tracks cumulativeScore/best/
     *  highestTileEver off the gained value -- since doubling a tile is conceptually a merge
     *  with no partner. */
    private fun applyDouble(tileId: Int) {
        val current = _uiState.value
        val result = engine.doubleTile(current.game, tileId)
        if (!result.applied) return
        AppAnalytics.logJokerUsed("double")
        val gained = result.state.score - current.game.score
        cumulativeScore += gained
        val bestChanged = result.state.best > bestScore
        if (bestChanged) bestScore = result.state.best
        val highestTile = result.state.tiles.maxOfOrNull { it.value } ?: 0
        if (highestTile > highestTileEver) highestTileEver = highestTile
        val level = LevelTracker.levelForCumulativeScore(cumulativeScore)
        if (level > current.level) {
            AppAnalytics.logLevelUp(level)
            ThemeUnlocks.newlyUnlocked(current.levelAtGameStart, level)?.let { AppAnalytics.logThemeUnlocked(it.id) }
            BoardSizeUnlocks.newlyUnlocked(current.levelAtGameStart, level)?.let { AppAnalytics.logBoardSizeUnlocked(it.id) }
        }
        if (!current.game.isGameOver && result.state.isGameOver) {
            AppAnalytics.logGameOver(result.state.score, level)
        }
        persist(result.state, includeBest = bestChanged)
        _uiState.value = current.copy(
            game = result.state,
            lastMovements = emptyList(),
            lastMergedTileIds = emptySet(),
            lastSpawnedTileIds = emptySet(),
            previousTilesById = current.game.tiles.associateBy { it.id },
            lastScoreGained = gained,
            moveToken = current.moveToken + 1,
            level = level,
            levelProgress = LevelTracker.progressToNextLevel(cumulativeScore),
            cumulativeScore = cumulativeScore,
            undoState = current.game,
            activeJoker = null,
            jokerFirstTileId = null,
            doublesRemaining = current.doublesRemaining - 1,
            highestTileEver = highestTileEver
        )
    }

    /** Debug backdoor (tap the SCORE chip 5x quickly): jumps straight to Level 30, mainly so
     *  Cyber (the level-30 theme) and Mega Board (level 15) don't require actually grinding
     *  there. Sets cumulativeScore to whatever XP Level 30 requires rather than faking a
     *  separate "level" field, so it stays consistent with everything else [LevelTracker]
     *  derives from real cumulative score -- and never regresses a player already past it. */
    fun onDebugJumpToLevel30() {
        val target = LevelTracker.scoreRequiredForLevel(30)
        if (cumulativeScore >= target) return
        cumulativeScore = target
        prefs.edit().putLong(KEY_CUMULATIVE_SCORE, cumulativeScore).commit()
        val level = LevelTracker.levelForCumulativeScore(cumulativeScore)
        _uiState.value = _uiState.value.copy(
            level = level,
            levelProgress = LevelTracker.progressToNextLevel(cumulativeScore),
            cumulativeScore = cumulativeScore,
            levelAtGameStart = level
        )
    }

    /** Called once the player dismisses [WelcomeDialog] (Skip or the final page's "Let's
     *  play!"), so it never auto-shows again on this install. */
    fun onWelcomeDismissed() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_WELCOME, true).commit()
        _uiState.value = _uiState.value.copy(hasSeenWelcome = true)
    }

    /** Debug backdoor (tap the Start Screen's BEST chip 5x quickly): clears the "seen" flag
     *  for [WelcomeDialog], same tap gesture as [onDebugJumpToLevel30] but on a different
     *  chip/screen. Doesn't show the dialog itself -- force-stop and relaunch the app afterward
     *  to see it auto-open exactly as it would for a real first-time install, without losing
     *  any other progress the way clearing all app data would. */
    fun onDebugResetWelcome() {
        prefs.edit().putBoolean(KEY_HAS_SEEN_WELCOME, false).commit()
        _uiState.value = _uiState.value.copy(hasSeenWelcome = false)
    }

    /** Grants [GameUiState.pendingDailyReward] (see [DailyRewardDialog]) and clears it. No-op
     *  if there's nothing pending -- safe to call from a dialog's dismiss path as well as its
     *  confirm button. Also bumps [GameUiState.levelAtGameStart] so a reward that happens to
     *  cross a level boundary isn't later misattributed to the board in progress on the
     *  game-over screen. */
    fun onClaimDailyReward() {
        val reward = _uiState.value.pendingDailyReward ?: return
        cumulativeScore += reward
        prefs.edit().putLong(KEY_CUMULATIVE_SCORE, cumulativeScore).commit()
        val level = LevelTracker.levelForCumulativeScore(cumulativeScore)
        _uiState.value = _uiState.value.copy(
            pendingDailyReward = null,
            level = level,
            levelProgress = LevelTracker.progressToNextLevel(cumulativeScore),
            cumulativeScore = cumulativeScore,
            levelAtGameStart = level
        )
    }

    /** Called when the player dismisses the "You Win" banner and wants to keep playing. */
    fun onContinuePastWin() {
        _uiState.value = _uiState.value.let { it.copy(game = it.game.copy(continuePastWin = true)) }
        persist(_uiState.value.game, includeBest = false)
    }

    /** Called once the milestone celebration banner has finished showing, so it doesn't linger
     *  or reappear on recomposition. */
    fun onMilestoneBannerShown() {
        _uiState.value = _uiState.value.copy(justReachedMilestone = null)
    }

    private fun buildInitialState(): GameUiState {
        val savedGame = loadSavedGame()
        // No saved board (fresh install, or a previous game ended with an empty board) means
        // freshGame() below is starting game #1 -- count it the same as onNewGame() would.
        if (savedGame == null) {
            gamesPlayed += 1
            prefs.edit()
                .putInt(KEY_GAMES_PLAYED, gamesPlayed)
                .putString(KEY_ACTIVE_GAME_MODE, selectedGameMode.id)
                .commit()
            AppAnalytics.logGameStarted()
        }
        val base = savedGame ?: freshGame()
        // A saved board from before KEY_ACTIVE_GAME_MODE existed has no recorded mode of its
        // own -- falling back to the current preference matches this app's behavior before this
        // field existed, rather than guessing. Every board from here on always has one, written
        // above (fresh board) or by onNewGame (an existing board that already had one).
        val activeGameMode = prefs.getString(KEY_ACTIVE_GAME_MODE, null)?.let { GameMode.fromId(it) }
            ?: selectedGameMode
        val previousStreak = loadStreak()
        val updatedStreak = StreakTracker.onAppOpened(previousStreak, todayEpochDay())
        val milestone = StreakTracker.newlyReachedMilestone(previousStreak, updatedStreak)
        if (milestone != null) AppAnalytics.logStreakMilestone(milestone)
        saveStreak(updatedStreak)
        // A new day was just recorded (as opposed to this being a same-day reopen, where
        // onAppOpened leaves lastPlayedEpochDay unchanged) -- offer today's claimable reward.
        // Computed once here and never recomputed afterward: if the app is closed before it's
        // claimed, it's simply gone rather than reappearing later that day or piling up --
        // same not-a-big-deal reasoning as [MAX_UNDOS] not surviving a restart.
        val justAdvancedStreak = previousStreak == StreakState.NONE ||
            updatedStreak.lastPlayedEpochDay != previousStreak.lastPlayedEpochDay
        val pendingReward = if (justAdvancedStreak) StreakTracker.dailyBonusXp(updatedStreak.current) else null
        val level = LevelTracker.levelForCumulativeScore(cumulativeScore)
        return base.copy(
            currentStreak = updatedStreak.current,
            longestStreak = updatedStreak.longest,
            justReachedMilestone = milestone,
            level = level,
            levelProgress = LevelTracker.progressToNextLevel(cumulativeScore),
            cumulativeScore = cumulativeScore,
            levelAtGameStart = level,
            selectedPalette = selectedPalette,
            selectedBoardSize = selectedBoardSize,
            gameMode = activeGameMode,
            selectedGameMode = selectedGameMode,
            gamesPlayed = gamesPlayed,
            highestTileEver = highestTileEver,
            totalMerges = totalMerges,
            hasSeenWelcome = prefs.getBoolean(KEY_HAS_SEEN_WELCOME, false),
            pendingDailyReward = pendingReward
        )
    }

    /** Local calendar day (device time zone), so a streak isn't broken by UTC day boundaries. */
    private fun todayEpochDay(): Long {
        val nowMillis = System.currentTimeMillis()
        val offsetMillis = TimeZone.getDefault().getOffset(nowMillis)
        return Math.floorDiv(nowMillis + offsetMillis, 86_400_000L)
    }

    private fun loadStreak(): StreakState {
        val lastDay = prefs.getLong(KEY_STREAK_LAST_DAY, Long.MIN_VALUE)
        if (lastDay == Long.MIN_VALUE) return StreakState.NONE
        return StreakState(
            current = prefs.getInt(KEY_STREAK_CURRENT, 0),
            longest = prefs.getInt(KEY_STREAK_LONGEST, 0),
            lastPlayedEpochDay = lastDay
        )
    }

    /** Uses commit() (synchronous), not apply(): this runs once at app open, and the streak
     *  must survive the process being killed moments later (e.g. by the OS, or the user
     *  swiping the app away) -- apply()'s async write can otherwise be lost in that window. */
    private fun saveStreak(state: StreakState) {
        prefs.edit()
            .putInt(KEY_STREAK_CURRENT, state.current)
            .putInt(KEY_STREAK_LONGEST, state.longest)
            .putLong(KEY_STREAK_LAST_DAY, state.lastPlayedEpochDay)
            .commit()
    }

    private fun freshGame(best: Int = bestScore, boardSize: Int = BoardSizeOption.DEFAULT.size): GameUiState {
        val game = engine.newGame(best = best, boardSize = boardSize)
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

    /**
     * Writes everything a move changed in ONE synchronous commit. commit(), not apply() --
     * see [saveStreak]: this must survive an abrupt process kill right after a move, which is
     * exactly what these saves exist to protect against. Batched into a single edit because
     * three separate commits per swipe was enough main-thread disk I/O to drop frames.
     */
    private fun persist(state: GameState, includeBest: Boolean) {
        val editor = prefs.edit()
            .putString(KEY_GAME_STATE, GameStateSerializer.encode(state))
            .putLong(KEY_CUMULATIVE_SCORE, cumulativeScore)
            .putInt(KEY_GAMES_PLAYED, gamesPlayed)
            .putInt(KEY_HIGHEST_TILE_EVER, highestTileEver)
            .putLong(KEY_TOTAL_MERGES, totalMerges)
        if (includeBest) editor.putInt(KEY_BEST_SCORE, bestScore)
        editor.commit()
        syncToCloudIfSignedIn()
    }

    /** Best-effort push of the current lifetime stats/preferences to Firestore -- no-ops when
     *  signed out or Firebase isn't configured. See [CloudSyncRepository]. */
    private fun syncToCloudIfSignedIn() {
        val uid = AuthRepository.currentUserId.value ?: return
        val streak = loadStreak()
        val progress = CloudProgress(
            cumulativeScore = cumulativeScore,
            bestScore = bestScore,
            highestTileEver = highestTileEver,
            totalMerges = totalMerges,
            gamesPlayed = gamesPlayed,
            currentStreak = streak.current,
            longestStreak = streak.longest,
            streakLastPlayedEpochDay = streak.lastPlayedEpochDay,
            selectedPaletteId = selectedPalette.id,
            selectedBoardSizeId = selectedBoardSize.id,
            selectedGameModeId = selectedGameMode.id,
            updatedAtEpochMillis = System.currentTimeMillis(),
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE
        )
        viewModelScope.launch { CloudSyncRepository.push(uid, progress) }
    }

    /** Called on every sign-in (including one [AuthRepository] silently restores on app open).
     *  Cloud progress wins over local only when it represents *more* lifetime progress
     *  ([CloudProgress.cumulativeScore] higher than this device's) -- otherwise this device's
     *  local progress is the more advanced one, so it's pushed up to the cloud instead. Either
     *  way this never loses progress on either side, only ever adopts the larger of the two.
     *
     *  Exception: a brand-new account (no cloud doc yet) signing in on a device that was last
     *  signed into a *different* account never adopts-and-pushes local progress -- that local
     *  progress belongs to whoever used this device before, not to this new account. See
     *  [KEY_LAST_SYNCED_UID] and [resetLocalProgressForNewIdentity]. */
    private fun applyCloudProgressIfSignedIn(uid: String) {
        viewModelScope.launch {
            val cloud = CloudSyncRepository.pull(uid)
            val lastSyncedUid = prefs.getString(KEY_LAST_SYNCED_UID, null)
            prefs.edit().putString(KEY_LAST_SYNCED_UID, uid).apply()

            if (cloud == null && lastSyncedUid != null && lastSyncedUid != uid) {
                resetLocalProgressForNewIdentity()
                syncToCloudIfSignedIn()
                return@launch
            }

            if (cloud == null || cloud.cumulativeScore <= cumulativeScore) {
                syncToCloudIfSignedIn()
                return@launch
            }

            applyProgressSnapshot(
                cumulativeScore = cloud.cumulativeScore,
                bestScore = maxOf(bestScore, cloud.bestScore),
                highestTileEver = maxOf(highestTileEver, cloud.highestTileEver),
                totalMerges = maxOf(totalMerges, cloud.totalMerges),
                gamesPlayed = maxOf(gamesPlayed, cloud.gamesPlayed),
                selectedPalette = TilePalette.fromId(cloud.selectedPaletteId),
                selectedBoardSize = BoardSizeOption.fromId(cloud.selectedBoardSizeId),
                selectedGameMode = GameMode.fromId(cloud.selectedGameModeId),
                streak = StreakState(
                    current = cloud.currentStreak,
                    longest = cloud.longestStreak,
                    lastPlayedEpochDay = cloud.streakLastPlayedEpochDay
                )
            )
        }
    }

    /** A fresh identity on this device starts with a clean slate -- same defaults as a brand
     *  new install -- rather than silently inheriting whatever the previously-signed-in account
     *  left in local storage. See [applyCloudProgressIfSignedIn]. */
    private fun resetLocalProgressForNewIdentity() {
        applyProgressSnapshot(
            cumulativeScore = 0L,
            bestScore = 0,
            highestTileEver = 0,
            totalMerges = 0L,
            gamesPlayed = 0,
            selectedPalette = TilePalette.DEFAULT,
            selectedBoardSize = BoardSizeOption.DEFAULT,
            selectedGameMode = GameMode.DEFAULT,
            streak = StreakState(current = 0, longest = 0, lastPlayedEpochDay = 0L)
        )
    }

    /** Writes a full lifetime-progress snapshot to memory, [prefs], and [_uiState] in one place
     *  -- shared by the cloud-adopt and new-identity-reset paths in [applyCloudProgressIfSignedIn]. */
    private fun applyProgressSnapshot(
        cumulativeScore: Long,
        bestScore: Int,
        highestTileEver: Int,
        totalMerges: Long,
        gamesPlayed: Int,
        selectedPalette: TilePalette,
        selectedBoardSize: BoardSizeOption,
        selectedGameMode: GameMode,
        streak: StreakState
    ) {
        this.cumulativeScore = cumulativeScore
        this.bestScore = bestScore
        this.highestTileEver = highestTileEver
        this.totalMerges = totalMerges
        this.gamesPlayed = gamesPlayed
        this.selectedPalette = selectedPalette
        this.selectedBoardSize = selectedBoardSize
        this.selectedGameMode = selectedGameMode
        saveStreak(streak)
        prefs.edit()
            .putLong(KEY_CUMULATIVE_SCORE, cumulativeScore)
            .putInt(KEY_BEST_SCORE, bestScore)
            .putInt(KEY_HIGHEST_TILE_EVER, highestTileEver)
            .putLong(KEY_TOTAL_MERGES, totalMerges)
            .putInt(KEY_GAMES_PLAYED, gamesPlayed)
            .putString(KEY_SELECTED_PALETTE, selectedPalette.id)
            .putString(KEY_SELECTED_BOARD_SIZE, selectedBoardSize.id)
            .putString(KEY_SELECTED_GAME_MODE, selectedGameMode.id)
            .commit()

        val level = LevelTracker.levelForCumulativeScore(cumulativeScore)
        val current = _uiState.value
        _uiState.value = current.copy(
            level = level,
            levelProgress = LevelTracker.progressToNextLevel(cumulativeScore),
            cumulativeScore = cumulativeScore,
            highestTileEver = highestTileEver,
            totalMerges = totalMerges,
            gamesPlayed = gamesPlayed,
            selectedPalette = selectedPalette,
            selectedBoardSize = selectedBoardSize,
            // Only the *preference* -- never gameMode itself, which is locked to whatever board
            // is currently in progress. A sign-in adopting a different device's mode preference
            // must not retroactively grant/hide Jokers on this device's in-progress board any
            // more than picking a mode on the Start Screen does; see onSelectGameMode.
            selectedGameMode = selectedGameMode,
            currentStreak = streak.current,
            longestStreak = streak.longest,
            game = current.game.copy(best = bestScore)
        )
    }

    /** No-ops if Firebase isn't configured for this build. On success, [onSwipe]/etc. will pick
     *  up the new uid via the [AuthRepository.currentUserId] collector in init{} and merge/push
     *  cloud progress automatically -- this function only handles the request/error UI state. */
    fun onSignUp(email: String, password: String) {
        val current = _uiState.value
        if (current.authBusy) return
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            _uiState.value = current.copy(authError = "Enter your email and password.", passwordResetSent = false)
            return
        }
        // Matches Firebase's own minimum -- catching it here skips a network round trip for an
        // outcome that's already certain, but AuthRepository.signUp still maps the same error
        // if this check is ever out of sync with Firebase's actual requirement.
        if (password.length < 6) {
            _uiState.value = current.copy(authError = "Password must be at least 6 characters.", passwordResetSent = false)
            return
        }
        _uiState.value = current.copy(authBusy = true, authError = null, passwordResetSent = false)
        viewModelScope.launch {
            when (val outcome = AuthRepository.signUp(trimmedEmail, password)) {
                is AuthRepository.Outcome.Success -> {
                    AppAnalytics.logSignUp()
                    _uiState.value = _uiState.value.copy(authBusy = false, authError = null)
                }
                is AuthRepository.Outcome.Failure ->
                    _uiState.value = _uiState.value.copy(authBusy = false, authError = outcome.message)
            }
        }
    }

    fun onSignIn(email: String, password: String) {
        val current = _uiState.value
        if (current.authBusy) return
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            _uiState.value = current.copy(authError = "Enter your email and password.", passwordResetSent = false)
            return
        }
        _uiState.value = current.copy(authBusy = true, authError = null, passwordResetSent = false)
        viewModelScope.launch {
            when (val outcome = AuthRepository.signIn(trimmedEmail, password)) {
                is AuthRepository.Outcome.Success -> {
                    AppAnalytics.logSignIn()
                    _uiState.value = _uiState.value.copy(authBusy = false, authError = null)
                }
                is AuthRepository.Outcome.Failure ->
                    _uiState.value = _uiState.value.copy(authBusy = false, authError = outcome.message)
            }
        }
    }

    /** Signs out locally only -- never touches any already-synced cloud or local progress. */
    fun onSignOut() {
        AuthRepository.signOut()
    }

    /** Sends a Firebase password-reset email. No-ops with a friendly error if [email] is blank
     *  rather than letting Firebase's own validation error surface instead. */
    fun onResetPassword(email: String) {
        val current = _uiState.value
        if (current.authBusy) return
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            _uiState.value = current.copy(authError = "Enter your email above first.", passwordResetSent = false)
            return
        }
        _uiState.value = current.copy(authBusy = true, authError = null, passwordResetSent = false)
        viewModelScope.launch {
            when (val outcome = AuthRepository.sendPasswordResetEmail(trimmedEmail)) {
                is AuthRepository.ResetOutcome.Sent ->
                    _uiState.value = _uiState.value.copy(authBusy = false, passwordResetSent = true)
                is AuthRepository.ResetOutcome.Failure ->
                    _uiState.value = _uiState.value.copy(authBusy = false, authError = outcome.message)
            }
        }
    }

    fun onDismissAuthError() {
        _uiState.value = _uiState.value.copy(authError = null, passwordResetSent = false)
    }
}
