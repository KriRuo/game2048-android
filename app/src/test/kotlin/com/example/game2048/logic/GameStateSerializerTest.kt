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
}
