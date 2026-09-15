package com.example.game2048.logic

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These cases (and the underlying slide/merge algorithm, including the tile-identity
 * tracking used for animation) were cross-checked against an independent Python
 * re-implementation -- known board/merge cases plus 500 randomized-game invariant
 * simulations -- before being committed here, since this project was assembled in a
 * sandbox without an Android SDK to compile and run the Kotlin directly.
 */
class Game2048EngineTest {

    private val engine = Game2048Engine()

    private fun gridOf(state: GameState): Map<Pair<Int, Int>, Int> =
        state.tiles.associate { (it.row to it.col) to it.value }

    @Test
    fun `moving left compacts and merges once per pair, left tile of each pair survives`() {
        val state = GameState(
            tiles = listOf(
                Tile(1, 2, 0, 0), Tile(2, 2, 0, 1),
                Tile(3, 4, 1, 0), Tile(4, 4, 1, 1), Tile(5, 4, 1, 2), Tile(6, 4, 1, 3),
            ),
            nextTileId = 7
        )
        val result = engine.move(state, Direction.LEFT)

        assertTrue(result.moved)
        // These three cells are occupied by the merge results themselves, so the random
        // post-move spawn (which only ever lands on a cell that's still empty) can't land
        // on any of them -- safe to assert on directly.
        val grid = gridOf(result.state)
        assertEquals(4, grid[0 to 0])
        assertEquals(8, grid[1 to 0])
        assertEquals(8, grid[1 to 1])
        assertEquals(4 + 8 + 8, result.state.score - state.score)
        assertEquals(setOf(1, 3, 5), result.mergedTileIds)

        val ghostIds = result.movements.filter { it.isConsumedByMerge }.map { it.tileId }.toSet()
        assertEquals(setOf(2, 4, 6), ghostIds)
    }

    @Test
    fun `three equal tiles in a row merge only the first pair`() {
        val state = GameState(
            tiles = listOf(Tile(1, 2, 0, 0), Tile(2, 2, 0, 1), Tile(3, 2, 0, 2)),
            nextTileId = 4
        )
        val result = engine.move(state, Direction.LEFT)

        assertTrue(result.moved)
        assertEquals(setOf(1), result.mergedTileIds)
        assertEquals(4, result.state.score)

        val survivingNonSpawn = result.state.tiles.filter { it.id in setOf(1, 3) }
        assertEquals(setOf(0 to 0, 0 to 1), survivingNonSpawn.map { it.row to it.col }.toSet())
        assertEquals(4, result.state.tiles.first { it.id == 1 }.value)
        assertEquals(2, result.state.tiles.first { it.id == 3 }.value)
    }

    @Test
    fun `a lone tile keeps its id while sliding`() {
        val state = GameState(tiles = listOf(Tile(42, 2, 0, 3)), nextTileId = 43)
        val result = engine.move(state, Direction.LEFT)

        assertTrue(result.moved)
        val survivor = result.state.tiles.first { it.id == 42 }
        assertEquals(0 to 0, survivor.row to survivor.col)
        assertEquals(2, survivor.value)
        assertEquals(0, result.state.score - state.score)
        assertTrue(result.mergedTileIds.isEmpty())
    }

    @Test
    fun `move that changes nothing is reported as not moved and state is untouched`() {
        val state = GameState(
            tiles = listOf(Tile(1, 2, 0, 0), Tile(2, 4, 0, 1), Tile(3, 8, 0, 2), Tile(4, 16, 0, 3)),
            nextTileId = 5
        )
        val result = engine.move(state, Direction.LEFT)

        assertFalse(result.moved)
        assertEquals(state, result.state)
        assertTrue(result.movements.isEmpty())
        assertNull(result.spawnedTileId)
    }

    @Test
    fun `moving up merges vertically`() {
        val state = GameState(
            tiles = listOf(Tile(1, 2, 0, 0), Tile(2, 2, 1, 0), Tile(3, 4, 2, 0)),
            nextTileId = 4
        )
        val result = engine.move(state, Direction.UP)

        assertTrue(result.moved)
        val nonSpawn = result.state.tiles.filter { it.id in setOf(1, 3) }
        assertEquals(setOf(0 to 0, 1 to 0), nonSpawn.map { it.row to it.col }.toSet())
        assertEquals(4, result.state.score)
    }

    @Test
    fun `game over is detected when no move changes the board`() {
        val fullyBlocked = listOf(
            Tile(1, 2, 0, 0), Tile(2, 4, 0, 1), Tile(3, 2, 0, 2), Tile(4, 4, 0, 3),
            Tile(5, 4, 1, 0), Tile(6, 2, 1, 1), Tile(7, 4, 1, 2), Tile(8, 2, 1, 3),
            Tile(9, 2, 2, 0), Tile(10, 4, 2, 1), Tile(11, 2, 2, 2), Tile(12, 4, 2, 3),
            Tile(13, 4, 3, 0), Tile(14, 2, 3, 1), Tile(15, 4, 3, 2), Tile(16, 2, 3, 3),
        )
        assertFalse(engine.canAnyMoveBeMade(fullyBlocked))
    }

    @Test
    fun `game is not over when an empty cell remains`() {
        val almostFull = listOf(
            Tile(1, 2, 0, 0), Tile(2, 4, 0, 1), Tile(3, 2, 0, 2), Tile(4, 4, 0, 3),
            Tile(5, 4, 1, 0), Tile(6, 2, 1, 1), Tile(7, 4, 1, 2), Tile(8, 2, 1, 3),
            Tile(9, 2, 2, 0), Tile(10, 4, 2, 1), Tile(11, 2, 2, 2), Tile(12, 4, 2, 3),
            Tile(13, 4, 3, 0), Tile(14, 2, 3, 1), Tile(15, 4, 3, 2),
        )
        assertTrue(engine.canAnyMoveBeMade(almostFull))
    }

    @Test
    fun `new game seeds exactly two tiles with fresh unique ids`() {
        val state = engine.newGame()
        assertEquals(2, state.tiles.size)
        assertEquals(state.tiles.map { it.id }.toSet().size, state.tiles.size)
        assertEquals(0, state.score)
    }

    @Test
    fun `spawnTile does nothing when board is full`() {
        val fullBoard = (0 until BOARD_SIZE).flatMap { r ->
            (0 until BOARD_SIZE).map { c -> Tile(r * BOARD_SIZE + c + 1, 2, r, c) }
        }
        val state = GameState(tiles = fullBoard, nextTileId = fullBoard.size + 1)
        val result = engine.spawnTile(state)
        assertEquals(fullBoard.toSet(), result.tiles.toSet())
    }

    @Test
    fun `best score only increases`() {
        val engineWithFixedSeed = Game2048Engine(Random(1))
        val state = GameState(
            tiles = listOf(Tile(1, 2, 0, 0), Tile(2, 2, 0, 1)),
            nextTileId = 3,
            score = 0,
            best = 100
        )
        val result = engineWithFixedSeed.move(state, Direction.LEFT)
        assertEquals(100, result.state.best)
    }

    @Test
    fun `newGame on Big Board seeds a 5x5 state with two tiles`() {
        val state = engine.newGame(boardSize = BIG_BOARD_SIZE)
        assertEquals(BIG_BOARD_SIZE, state.boardSize)
        assertEquals(2, state.tiles.size)
        assertTrue(state.tiles.all { it.row in 0 until BIG_BOARD_SIZE && it.col in 0 until BIG_BOARD_SIZE })
    }

    @Test
    fun `moving left on a 5x5 board compacts all the way to column 0`() {
        val state = GameState(
            tiles = listOf(Tile(1, 2, 2, 4)),
            nextTileId = 2,
            boardSize = BIG_BOARD_SIZE
        )
        val result = engine.move(state, Direction.LEFT)

        assertTrue(result.moved)
        val survivor = result.state.tiles.first { it.id == 1 }
        assertEquals(2 to 0, survivor.row to survivor.col)
    }

    @Test
    fun `game over on a 5x5 board is not reported early using the 4x4 cell count`() {
        // 20 tiles: fewer than BOARD_SIZE*BOARD_SIZE (16) would already look "full" if the
        // engine still hard-coded 4x4, but this is far short of a full 5x5 (25) board, and no
        // two adjacent equal values exist, so no move should be reported as game-over-inducing.
        val tiles = (0 until 20).map { i -> Tile(i + 1, if (i % 2 == 0) 2 else 4, i / BIG_BOARD_SIZE, i % BIG_BOARD_SIZE) }
        assertTrue(engine.canAnyMoveBeMade(tiles, BIG_BOARD_SIZE))
    }

    @Test
    fun `spawnTile on a 5x5 board only lands within its own bounds`() {
        val fullFourByFour = (0 until BOARD_SIZE * BOARD_SIZE).map { i ->
            Tile(i + 1, 2, i / BOARD_SIZE, i % BOARD_SIZE)
        }
        // A 4x4-full set of tiles leaves the whole last row/column of a 5x5 board empty.
        val state = GameState(tiles = fullFourByFour, nextTileId = fullFourByFour.size + 1, boardSize = BIG_BOARD_SIZE)
        val result = engine.spawnTile(state)

        assertEquals(fullFourByFour.size + 1, result.tiles.size)
        val spawned = result.tiles.first { it.id == fullFourByFour.size + 1 }
        assertTrue(spawned.row == BIG_BOARD_SIZE - 1 || spawned.col == BIG_BOARD_SIZE - 1)
    }

    @Test
    fun `Mega Board (6x6) plays with the same generic boardSize logic as 5x5`() {
        val state = engine.newGame(boardSize = MEGA_BOARD_SIZE)
        assertEquals(MEGA_BOARD_SIZE, state.boardSize)
        assertTrue(state.tiles.all { it.row in 0 until MEGA_BOARD_SIZE && it.col in 0 until MEGA_BOARD_SIZE })

        val slideState = GameState(tiles = listOf(Tile(1, 2, 3, 5)), nextTileId = 2, boardSize = MEGA_BOARD_SIZE)
        val result = engine.move(slideState, Direction.LEFT)
        assertTrue(result.moved)
        val survivor = result.state.tiles.first { it.id == 1 }
        assertEquals(3 to 0, survivor.row to survivor.col)
    }
}
