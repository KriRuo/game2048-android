package com.example.game2048.logic

/**
 * Pure, dependency-free (de)serialization of [GameState] to a single compact string, so the
 * whole board -- not just the best score -- can be persisted (e.g. in SharedPreferences) and
 * resumed across app restarts/process death. Kept separate from any Android storage API so it
 * can be unit tested the same way as [Game2048Engine].
 */
private const val FORMAT_VERSION = 1

object GameStateSerializer {

    fun encode(state: GameState): String {
        val tiles = state.tiles.joinToString(separator = ";") { tile ->
            "${tile.id},${tile.value},${tile.row},${tile.col}"
        }
        return listOf(
            FORMAT_VERSION,
            state.nextTileId,
            state.score,
            state.best,
            if (state.isGameOver) 1 else 0,
            if (state.hasWon) 1 else 0,
            if (state.continuePastWin) 1 else 0,
            tiles
        ).joinToString(separator = "|")
    }

    /** Returns the decoded [GameState], or null if [encoded] is missing/corrupt/unrecognized. */
    fun decode(encoded: String): GameState? {
        return try {
            val parts = encoded.split("|", limit = 8)
            if (parts.size != 8 || parts[0].toInt() != FORMAT_VERSION) return null

            val tiles = if (parts[7].isEmpty()) {
                emptyList()
            } else {
                parts[7].split(";").map { chunk ->
                    val f = chunk.split(",")
                    require(f.size == 4)
                    Tile(id = f[0].toInt(), value = f[1].toInt(), row = f[2].toInt(), col = f[3].toInt())
                }
            }

            GameState(
                tiles = tiles,
                nextTileId = parts[1].toInt(),
                score = parts[2].toInt(),
                best = parts[3].toInt(),
                isGameOver = parts[4] == "1",
                hasWon = parts[5] == "1",
                continuePastWin = parts[6] == "1"
            )
        } catch (e: Exception) {
            null
        }
    }
}
