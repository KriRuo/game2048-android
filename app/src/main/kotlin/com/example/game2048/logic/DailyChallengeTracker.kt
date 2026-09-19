package com.example.game2048.logic

/** State of the player's daily challenge participation -- same plain-data pattern as
 *  [StreakState]. [DailyChallengeState.NONE_DAY] is the "never completed one yet" sentinel,
 *  matching [StreakState.NONE]'s Long.MIN_VALUE convention. */
data class DailyChallengeState(
    val lastCompletedEpochDay: Long = NONE_DAY,
    val lastScore: Int = 0,
    val bestScore: Int = 0
) {
    companion object {
        const val NONE_DAY = Long.MIN_VALUE
        val NONE = DailyChallengeState()
    }
}

/**
 * A fixed-seed, move-capped daily puzzle: every player gets an identical board and an identical
 * tile-spawn sequence for a given local calendar day (same epoch-day convention as
 * [StreakTracker]), with exactly one attempt each. Deliberately simple -- a personal "beat your
 * own best on today's board" loop, not a competitive one: no leaderboard, and completing it
 * doesn't feed [StreakState] or create a streak of its own (see CLAUDE.md).
 */
object DailyChallengeTracker {
    /** Moves allowed before an attempt ends regardless of whether the board is still playable --
     *  keeps every attempt on a given day's seed directly comparable by score alone, and keeps
     *  the daily ritual short rather than an open-ended session. */
    const val MOVE_CAP = 30

    /** Flat bonus XP for completing today's challenge -- win, loss, or simply running out the
     *  move cap, there's no difference, one attempt is one attempt. Flat rather than scaling
     *  like [StreakTracker.dailyBonusXp]: this isn't a streak, so there's no "day N" to grow
     *  with. */
    const val COMPLETION_BONUS_XP = 50

    /** True once [epochDay]'s challenge has already been played. */
    fun isCompletedToday(state: DailyChallengeState, epochDay: Long): Boolean =
        state.lastCompletedEpochDay == epochDay

    /** Deterministic per-day seed for [Game2048Engine]'s injectable [kotlin.random.Random] --
     *  every player gets an identical board and identical spawn sequence for the same
     *  [epochDay]. Doesn't need to be unguessable: the seed is the same for everyone by design,
     *  so there's nothing to gain from knowing it early. */
    fun seedFor(epochDay: Long): Long = epochDay

    /** Records a finished attempt. [DailyChallengeState.bestScore] tracks the best
     *  *daily-challenge* score ever reached -- a separate notion from the ordinary game's best
     *  score, which lives on [GameState] instead. */
    fun recordCompletion(previous: DailyChallengeState, epochDay: Long, score: Int): DailyChallengeState =
        previous.copy(
            lastCompletedEpochDay = epochDay,
            lastScore = score,
            bestScore = maxOf(previous.bestScore, score)
        )
}
