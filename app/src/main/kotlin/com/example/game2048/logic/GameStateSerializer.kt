package com.example.game2048.logic

/**
 * Pure, dependency-free (de)serialization of [GameState] to a single compact string, so the
 * whole board -- not just the best score -- can be persisted (e.g. in SharedPreferences) and
 * resumed across app restarts/process death. Kept separate from any Android storage API so it
 * can be unit tested the same way as [Game2048Engine].
 */
private const val FORMAT_VERSION = 2

object GameStateSerializer {

    fun encode(state: GameState): String {
        val tiles = state.tiles.joinToString(separator = ";") { tile ->
            "${tile.id},${tile.value},${tile.row},${tile.col}"
        }
        return listOf(
            FORMAT_VERSION,
            state.boardSize,
            state.nextTileId,
            state.score,
            state.best,
            if (state.isGameOver) 1 else 0,
            if (state.hasWon) 1 else 0,
            if (state.continuePastWin) 1 else 0,
            tiles
        ).joinToString(separator = "|")
    }

    /** Returns the decoded [GameState], or null if [encoded] is missing/corrupt/unrecognized.
     *  Understands both the current format (2, with an explicit [GameState.boardSize]) and the
     *  original one (1, always 4x4) -- saves written before Big Board existed must keep loading
     *  correctly rather than being silently discarded as "corrupt". */
    fun decode(encoded: String): GameState? {
        return try {
            when (encoded.substringBefore('|').toIntOrNull()) {
                1 -> decodeV1(encoded.split("|"))
                FORMAT_VERSION -> decodeV2(encoded.split("|"))
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeV1(parts: List<String>): GameState? {
        if (parts.size != 8) return null
        return GameState(
            tiles = parseTiles(parts[7]),
            nextTileId = parts[1].toInt(),
            score = parts[2].toInt(),
            best = parts[3].toInt(),
            isGameOver = parts[4] == "1",
            hasWon = parts[5] == "1",
            continuePastWin = parts[6] == "1"
        )
    }

    private fun decodeV2(parts: List<String>): GameState? {
        if (parts.size != 9) return null
        return GameState(
            tiles = parseTiles(parts[8]),
            boardSize = parts[1].toInt(),
            nextTileId = parts[2].toInt(),
            score = parts[3].toInt(),
            best = parts[4].toInt(),
            isGameOver = parts[5] == "1",
            hasWon = parts[6] == "1",
            continuePastWin = parts[7] == "1"
        )
    }

    private fun parseTiles(chunk: String): List<Tile> {
        if (chunk.isEmpty()) return emptyList()
        return chunk.split(";").map { tileChunk ->
            val f = tileChunk.split(",")
            require(f.size == 4)
            Tile(id = f[0].toInt(), value = f[1].toInt(), row = f[2].toInt(), col = f[3].toInt())
        }
    }
}
