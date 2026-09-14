package com.example.game2048.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameStateSerializerTest {

    @Test
    fun `round-trips a mid-game state exactly`() {
        val state = GameState(
            tiles = listOf(
                Tile(1, 4, 0, 0),
                Tile(5, 2048, 2, 3),
                Tile(9, 16, 3, 1)
            ),
            nextTileId = 10,
            score = 132,
            best = 500,
            isGameOver = false,
            hasWon = true,
            continuePastWin = true
        )

        val decoded = GameStateSerializer.decode(GameStateSerializer.encode(state))

        assertEquals(state, decoded)
    }

    @Test
    fun `round-trips an empty board`() {
        val state = GameState(tiles = emptyList(), nextTileId = 1, score = 0, best = 0)

        val decoded = GameStateSerializer.decode(GameStateSerializer.encode(state))

        assertEquals(state, decoded)
    }

    @Test
    fun `decode returns null for garbage input`() {
        assertNull(GameStateSerializer.decode("not a valid encoding"))
        assertNull(GameStateSerializer.decode(""))
        assertNull(GameStateSerializer.decode("1|2|3"))
    }

    @Test
    fun `decode returns null for an unrecognized format version`() {
        val state = GameState(tiles = listOf(Tile(1, 2, 0, 0)), nextTileId = 2)
        val encoded = GameStateSerializer.encode(state)
        val bumpedVersion = encoded.replaceFirst(Regex("^\\d+"), "99")

        assertNull(GameStateSerializer.decode(bumpedVersion))
    }

    @Test
    fun `decode returns null when a tile chunk is malformed`() {
        assertNull(GameStateSerializer.decode("1|2|0|0|0|0|0|1,2,0"))
    }

    @Test
    fun `round-trips a Big Board state including its board size`() {
        val state = GameState(
            tiles = listOf(Tile(1, 4, 0, 0), Tile(2, 8, 4, 4)),
            nextTileId = 3,
            score = 40,
            boardSize = BIG_BOARD_SIZE
        )

        val decoded = GameStateSerializer.decode(GameStateSerializer.encode(state))

        assertEquals(state, decoded)
        assertEquals(BIG_BOARD_SIZE, decoded?.boardSize)
    }

    @Test
    fun `decodes a pre-Big-Board (version 1) save as a classic 4x4 board`() {
        // Format written before boardSize existed: version|nextTileId|score|best|gameOver|won|
        // continuePastWin|tiles -- one field shorter than the current version, with no size
        // field at all. Must still load correctly (as 4x4), not be treated as corrupt.
        val legacyEncoded = "1|3|12|12|0|0|0|1,4,0,0;2,8,1,1"

        val decoded = GameStateSerializer.decode(legacyEncoded)

        assertEquals(BOARD_SIZE, decoded?.boardSize)
        assertEquals(2, decoded?.tiles?.size)
        assertEquals(12, decoded?.score)
    }
}
