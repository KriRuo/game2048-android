package com.example.game2048.logic

/**
 * Pure, dependency-free player-level logic (see [StreakTracker], [GameStateSerializer] for
 * the same pattern). The level is derived from *cumulative* score across every game ever
 * played -- it never resets when a board does, so it tracks progress across the whole
 * "journey" rather than any single game.
 *
 * Uses a triangular-number XP curve: the score required to reach level L grows roughly
 * linearly faster each level, so early levels come quickly and later ones gradually slow
 * down, without needing a hand-maintained lookup table.
 */
object LevelTracker {
    private const val BASE_XP_PER_LEVEL = 200L

    /** Cumulative score required to reach [level] (level 1 requires 0). */
    fun scoreRequiredForLevel(level: Int): Long {
        val n = (level - 1).toLong()
        return BASE_XP_PER_LEVEL * n * (n + 1) / 2
    }

    /** The level reached at [cumulativeScore]. Always >= 1. */
    fun levelForCumulativeScore(cumulativeScore: Long): Int {
        var level = 1
        while (scoreRequiredForLevel(level + 1) <= cumulativeScore) level++
        return level
    }

    /** Progress toward the next level, in [0f, 1f). */
    fun progressToNextLevel(cumulativeScore: Long): Float {
        val level = levelForCumulativeScore(cumulativeScore)
        val currentThreshold = scoreRequiredForLevel(level)
        val nextThreshold = scoreRequiredForLevel(level + 1)
        val span = nextThreshold - currentThreshold
        if (span <= 0) return 1f
        return ((cumulativeScore - currentThreshold).toFloat() / span).coerceIn(0f, 1f)
    }
}
