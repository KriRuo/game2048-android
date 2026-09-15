package com.example.game2048.logic

import kotlin.random.Random

/** Board side length. */
const val BOARD_SIZE = 4

/** Board side length for the optional "Big Board" mode (see [BoardSizeOption]). */
const val BIG_BOARD_SIZE = 5

/** Board side length for the optional "Mega Board" mode (see [BoardSizeOption]). */
const val MEGA_BOARD_SIZE = 6

/** Board side length for the optional "Giant Board" mode (see [BoardSizeOption]). */
const val GIANT_BOARD_SIZE = 8

enum class Direction { LEFT, RIGHT, UP, DOWN }

/** The tap-to-target "Joker" powerups available in [GameMode.EXTENDED], reached by tapping
 *  tiles/cells directly rather than swiping. [ROTATE] isn't here -- it has no target to pick,
 *  it fires immediately on tap (see [com.example.game2048.GameViewModel.onRotate]). See
 *  [Game2048Engine.teleportTile]/[swapTiles]/[bombTile]/[doubleTile]/[rotateBoard]. */
enum class Joker { TELEPORT, SWAP, BOMB, DOUBLE }

/** Which ruleset is active. ORIGINAL matches the classic 2048 -- swipe only, no Undo, no
 *  Jokers. EXTENDED is this app's enhanced version. Switching doesn't touch the board in
 *  progress; it only changes which actions the UI exposes (see
 *  [com.example.game2048.GameViewModel.onSelectGameMode]). */
enum class GameMode(val id: String, val displayName: String) {
    ORIGINAL("original", "Original"),
    EXTENDED("extended", "Extended");

    companion object {
        val DEFAULT = EXTENDED
        fun fromId(id: String?): GameMode = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** A single tile on the board. [id] is stable across moves so the UI can animate it. */
data class Tile(val id: Int, val value: Int, val row: Int, val col: Int)

/**
 * Immutable snapshot of the game state. [tiles] holds only occupied cells (no zero-padding).
 * [nextTileId] is threaded through so every new tile (initial seed or post-move spawn) gets
 * a fresh, globally unique id without the engine needing mutable internal state.
 */
data class GameState(
    val tiles: List<Tile>,
    val nextTileId: Int = 1,
    val score: Int = 0,
    val best: Int = 0,
    val isGameOver: Boolean = false,
    val hasWon: Boolean = false,
    /** True once the player has dismissed the "You Win" banner and kept playing. */
    val continuePastWin: Boolean = false,
    /** Side length of the square board this game is being played on -- [BOARD_SIZE] (4) unless
     *  the player opted into [BigBoardUnlock]'s 5x5 mode when this game started. Carried on the
     *  state itself (rather than threaded separately) since every row/col bound in the engine
     *  needs it and it never changes for the lifetime of one game. */
    val boardSize: Int = BOARD_SIZE
) {
    companion object {
        fun empty(best: Int = 0, boardSize: Int = BOARD_SIZE): GameState =
            GameState(tiles = emptyList(), best = best, boardSize = boardSize)
    }
}

/**
 * Describes how one tile moved during a single [Game2048Engine.move] call, for animation.
 * [isConsumedByMerge] is true for the "losing" partner of a merge: it slides to ([toRow],
 * [toCol]) same as its surviving partner and should be animated out (fade/scale to 0) once
 * it arrives, rather than being drawn as a standalone tile afterwards.
 */
data class TileMovement(
    val tileId: Int,
    val fromRow: Int,
    val fromCol: Int,
    val toRow: Int,
    val toCol: Int,
    val isConsumedByMerge: Boolean
)

/** Result of a Joker action ([Game2048Engine.teleportTile]/[Game2048Engine.swapTiles]): tiles
 *  reposition with no merge and no new tile spawned. [applied] is false (and [state] unchanged)
 *  if the requested tile(s)/cell were invalid -- e.g. the target cell wasn't actually empty. */
data class JokerResult(
    val state: GameState,
    val applied: Boolean,
    val movements: List<TileMovement> = emptyList()
)

/** Result of attempting a move, with enough detail for the UI to animate it. */
data class MoveResult(
    val state: GameState,
    val moved: Boolean,
    val movements: List<TileMovement> = emptyList(),
    /** Ids of tiles whose value doubled this move (drive a "pop" scale animation). */
    val mergedTileIds: Set<Int> = emptySet(),
    /** Id of the tile spawned after the move, if any (drive a fade/scale-in animation). */
    val spawnedTileId: Int? = null
)

/**
 * Pure game engine for 2048. Holds no Android dependencies so it can be unit tested
 * (and cross-checked) independently of the UI layer. Tiles carry stable ids through
 * slides and merges so the Compose UI can animate individual tiles rather than snapping
 * a raw value grid into place.
 */
class Game2048Engine(private val random: Random = Random.Default) {

    /** Starts a fresh game: empty board with two random tiles seeded in. */
    fun newGame(best: Int = 0, boardSize: Int = BOARD_SIZE): GameState {
        var state = GameState.empty(best = best, boardSize = boardSize)
        state = spawnTile(state)
        state = spawnTile(state)
        return state
    }

    /**
     * Applies [direction] to [state]. Returns the resulting state plus per-tile movement
     * detail the UI can use to animate slides, merges, and the post-move spawn.
     */
    fun move(state: GameState, direction: Direction): MoveResult {
        if (state.isGameOver) return MoveResult(state, moved = false)

        val (newTiles, movements, mergedIds, gained) = applyMove(state.tiles, direction, state.boardSize)
        val changed = gridOf(newTiles) != gridOf(state.tiles)
        if (!changed) {
            return MoveResult(state, moved = false)
        }

        var newState = state.copy(
            tiles = newTiles,
            score = state.score + gained,
            best = maxOf(state.best, state.score + gained)
        )

        val spawnedState = spawnTile(newState)
        val spawnedId = spawnedState.tiles.firstOrNull { spawned ->
            newState.tiles.none { it.id == spawned.id }
        }?.id
        newState = spawnedState

        val won = !state.hasWon && newState.tiles.any { it.value >= 2048 }
        val gameOver = !canAnyMoveBeMade(newState.tiles, newState.boardSize)

        newState = newState.copy(hasWon = state.hasWon || won, isGameOver = gameOver)
        return MoveResult(
            state = newState,
            moved = true,
            movements = movements,
            mergedTileIds = mergedIds,
            spawnedTileId = spawnedId
        )
    }

    /** Moves the tile with [tileId] to ([toRow], [toCol]) if that cell is in bounds and empty.
     *  No merge, no spawn -- a pure reposition, for the Teleport Joker (see [Joker]). Re-checks
     *  game-over the same way [move] does: unlike a normal move this can't add an empty cell,
     *  but a full board can only be teleported *from* in the first place if [toRow]/[toCol] is
     *  itself the one empty cell, so this only ever matches [move]'s notion of game-over. */
    fun teleportTile(state: GameState, tileId: Int, toRow: Int, toCol: Int): JokerResult {
        val tile = state.tiles.firstOrNull { it.id == tileId } ?: return JokerResult(state, applied = false)
        if (toRow !in 0 until state.boardSize || toCol !in 0 until state.boardSize) {
            return JokerResult(state, applied = false)
        }
        if (tile.row == toRow && tile.col == toCol) return JokerResult(state, applied = false)
        if (state.tiles.any { it.row == toRow && it.col == toCol }) return JokerResult(state, applied = false)

        val newTiles = state.tiles.map { if (it.id == tileId) it.copy(row = toRow, col = toCol) else it }
        val movement = TileMovement(tileId, tile.row, tile.col, toRow, toCol, isConsumedByMerge = false)
        val newState = state.copy(tiles = newTiles, isGameOver = !canAnyMoveBeMade(newTiles, state.boardSize))
        return JokerResult(newState, applied = true, movements = listOf(movement))
    }

    /** Exchanges the positions of the two given tiles. No merge, no spawn -- for the Swap Joker
     *  (see [Joker]). Allowed even if [state.isGameOver][GameState.isGameOver]: unlike Teleport
     *  (which needs an empty cell that can't exist on a full dead board), swapping two tiles on
     *  a full board can unlock a merge that wasn't there before, which is the whole point of
     *  offering it as a way out of a stuck game. */
    fun swapTiles(state: GameState, tileId1: Int, tileId2: Int): JokerResult {
        if (tileId1 == tileId2) return JokerResult(state, applied = false)
        val t1 = state.tiles.firstOrNull { it.id == tileId1 } ?: return JokerResult(state, applied = false)
        val t2 = state.tiles.firstOrNull { it.id == tileId2 } ?: return JokerResult(state, applied = false)

        val newTiles = state.tiles.map {
            when (it.id) {
                tileId1 -> it.copy(row = t2.row, col = t2.col)
                tileId2 -> it.copy(row = t1.row, col = t1.col)
                else -> it
            }
        }
        val movements = listOf(
            TileMovement(tileId1, t1.row, t1.col, t2.row, t2.col, isConsumedByMerge = false),
            TileMovement(tileId2, t2.row, t2.col, t1.row, t1.col, isConsumedByMerge = false)
        )
        val newState = state.copy(tiles = newTiles, isGameOver = !canAnyMoveBeMade(newTiles, state.boardSize))
        return JokerResult(newState, applied = true, movements = movements)
    }

    /** Removes the given tile outright -- the Bomb Joker. Frees its cell with no score change
     *  and no spawn; only ever reduces the board's tile count, so re-checking game-over exists
     *  purely for symmetry with the other Jokers (it can only go from over to not-over here). */
    fun bombTile(state: GameState, tileId: Int): JokerResult {
        if (state.tiles.none { it.id == tileId }) return JokerResult(state, applied = false)
        val newTiles = state.tiles.filterNot { it.id == tileId }
        val newState = state.copy(tiles = newTiles, isGameOver = !canAnyMoveBeMade(newTiles, state.boardSize))
        return JokerResult(newState, applied = true)
    }

    /** Doubles the given tile's value in place -- the Double Joker. Scored exactly like a
     *  merge (the gained value is the tile's *new* value), since conceptually it's a merge
     *  with no partner. No spawn. Re-checks game-over the same way [swapTiles] does: changing
     *  one tile's value, like swapping two, can unlock a merge on an otherwise-full board. */
    fun doubleTile(state: GameState, tileId: Int): JokerResult {
        val tile = state.tiles.firstOrNull { it.id == tileId } ?: return JokerResult(state, applied = false)
        val newValue = tile.value * 2
        val newTiles = state.tiles.map { if (it.id == tileId) it.copy(value = newValue) else it }
        val newState = state.copy(
            tiles = newTiles,
            score = state.score + newValue,
            best = maxOf(state.best, state.score + newValue),
            isGameOver = !canAnyMoveBeMade(newTiles, state.boardSize)
        )
        return JokerResult(newState, applied = true)
    }

    /** Rotates every tile's position 90 degrees clockwise -- the Rotate Joker. Pure relabeling
     *  of the grid's axes (no merge, no spawn, no score change), and unlike the other Jokers it
     *  needs no target: activating it applies immediately (see [com.example.game2048.
     *  GameViewModel.onRotate]). Game-over is invariant under rotation -- [canAnyMoveBeMade]
     *  already checks all four directions regardless of orientation -- so it's not re-checked. */
    fun rotateBoard(state: GameState): JokerResult {
        val size = state.boardSize
        val newTiles = state.tiles.map { it.copy(row = it.col, col = size - 1 - it.row) }
        val movements = state.tiles.map {
            TileMovement(it.id, it.row, it.col, it.col, size - 1 - it.row, isConsumedByMerge = false)
        }
        return JokerResult(state.copy(tiles = newTiles), applied = true, movements = movements)
    }

    /** Adds one random tile (90% a 2, 10% a 4) into a random empty cell, if any exist. */
    fun spawnTile(state: GameState): GameState {
        val occupied = state.tiles.map { it.row to it.col }.toSet()
        val emptyCells = buildList {
            for (r in 0 until state.boardSize) {
                for (c in 0 until state.boardSize) {
                    if ((r to c) !in occupied) add(r to c)
                }
            }
        }
        if (emptyCells.isEmpty()) return state

        val (r, c) = emptyCells[random.nextInt(emptyCells.size)]
        val value = if (random.nextInt(10) == 0) 4 else 2
        val newTile = Tile(id = state.nextTileId, value = value, row = r, col = c)
        return state.copy(tiles = state.tiles + newTile, nextTileId = state.nextTileId + 1)
    }

    /** True if there is any empty cell, or any move in any direction would change the board. */
    fun canAnyMoveBeMade(tiles: List<Tile>, boardSize: Int = BOARD_SIZE): Boolean {
        val occupied = tiles.map { it.row to it.col }.toSet()
        if (occupied.size < boardSize * boardSize) return true

        for (direction in Direction.values()) {
            val (newTiles, _, _, _) = applyMove(tiles, direction, boardSize)
            if (gridOf(newTiles) != gridOf(tiles)) return true
        }
        return false
    }

    private fun gridOf(tiles: List<Tile>): Map<Pair<Int, Int>, Int> =
        tiles.associate { (it.row to it.col) to it.value }

    private data class LineResult(
        val newTiles: List<Tile>,
        val movements: List<TileMovement>,
        val mergedIds: Set<Int>,
        val gained: Int
    )

    /**
     * Slides and merges [tiles] toward [direction]. Tiles are grouped into "lines" (rows for
     * LEFT/RIGHT, columns for UP/DOWN), each line is compacted independently, and every tile's
     * movement is recorded for animation. A tile consumed by a merge (the right-of-pair partner
     * when sliding left, etc.) keeps its own [TileMovement] with [TileMovement.isConsumedByMerge]
     * set, targeting the same cell as the tile it merged into, rather than being silently dropped.
     */
    private fun applyMove(tiles: List<Tile>, direction: Direction, boardSize: Int): LineResult {
        val lines = tiles.groupBy { lineNumber(direction, it.row, it.col) }

        val newTiles = mutableListOf<Tile>()
        val movements = mutableListOf<TileMovement>()
        val mergedIds = mutableSetOf<Int>()
        var gained = 0

        for ((lineNo, lineTiles) in lines) {
            val ordered = lineTiles.sortedBy { indexInLine(direction, it.row, it.col, boardSize) }
            var i = 0
            var target = 0
            while (i < ordered.size) {
                val current = ordered[i]
                val next = ordered.getOrNull(i + 1)
                if (next != null && next.value == current.value) {
                    val newValue = current.value * 2
                    val (toRow, toCol) = coordFromLine(direction, lineNo, target, boardSize)
                    newTiles.add(Tile(current.id, newValue, toRow, toCol))
                    mergedIds.add(current.id)
                    gained += newValue
                    movements.add(TileMovement(current.id, current.row, current.col, toRow, toCol, isConsumedByMerge = false))
                    movements.add(TileMovement(next.id, next.row, next.col, toRow, toCol, isConsumedByMerge = true))
                    i += 2
                } else {
                    val (toRow, toCol) = coordFromLine(direction, lineNo, target, boardSize)
                    newTiles.add(Tile(current.id, current.value, toRow, toCol))
                    movements.add(TileMovement(current.id, current.row, current.col, toRow, toCol, isConsumedByMerge = false))
                    i += 1
                }
                target += 1
            }
        }

        return LineResult(newTiles, movements, mergedIds, gained)
    }

    private fun lineNumber(direction: Direction, row: Int, col: Int): Int = when (direction) {
        Direction.LEFT, Direction.RIGHT -> row
        Direction.UP, Direction.DOWN -> col
    }

    private fun indexInLine(direction: Direction, row: Int, col: Int, boardSize: Int): Int = when (direction) {
        Direction.LEFT -> col
        Direction.RIGHT -> boardSize - 1 - col
        Direction.UP -> row
        Direction.DOWN -> boardSize - 1 - row
    }

    private fun coordFromLine(direction: Direction, lineNo: Int, index: Int, boardSize: Int): Pair<Int, Int> = when (direction) {
        Direction.LEFT -> lineNo to index
        Direction.RIGHT -> lineNo to (boardSize - 1 - index)
        Direction.UP -> index to lineNo
        Direction.DOWN -> (boardSize - 1 - index) to lineNo
    }
}
