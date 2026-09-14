package com.example.game2048.logic

/**
 * Pure, dependency-free daily-streak logic, kept separate from Android storage/clock APIs so
 * it can be unit tested directly (see [GameStateSerializer] for the same pattern). Days are
 * represented as a local calendar-day number (days since epoch in the *device's* time zone,
 * not UTC) so a streak isn't broken by playing late one night and early the next morning.
 */
data class StreakState(
    val current: Int,
    val longest: Int,
    val lastPlayedEpochDay: Long
) {
    companion object {
        val NONE = StreakState(current = 0, longest = 0, lastPlayedEpochDay = Long.MIN_VALUE)
    }
}

/** Milestones that get their own celebration the first time a streak reaches them. */
val STREAK_MILESTONES = listOf(3, 7, 14, 30, 50, 100, 200, 365)

object StreakTracker {

    /**
     * Call once per app open. Returns the updated [StreakState] for [todayEpochDay]:
     * - Same day as last played: unchanged (already counted today).
     * - Exactly one day later: streak continues (+1), [StreakState.longest] updated if beaten.
     * - A gap of more than one day: streak resets to 1 (longest is preserved).
     * - A day *before* the last recorded one (clock set backwards): left unchanged, defensively,
     *   rather than rewarding or punishing an impossible transition.
     */
    fun onAppOpened(previous: StreakState, todayEpochDay: Long): StreakState {
        if (previous == StreakState.NONE) {
            return StreakState(current = 1, longest = 1, lastPlayedEpochDay = todayEpochDay)
        }
        return when (val gap = todayEpochDay - previous.lastPlayedEpochDay) {
            0L -> previous
            1L -> {
                val newCurrent = previous.current + 1
                previous.copy(
                    current = newCurrent,
                    longest = maxOf(previous.longest, newCurrent),
                    lastPlayedEpochDay = todayEpochDay
                )
            }
            else -> if (gap > 1L) {
                StreakState(current = 1, longest = previous.longest, lastPlayedEpochDay = todayEpochDay)
            } else {
                previous
            }
        }
    }

    /** The highest milestone newly crossed going from [before] to [after], if any. */
    fun newlyReachedMilestone(before: StreakState, after: StreakState): Int? =
        STREAK_MILESTONES.lastOrNull { it in (before.current + 1)..after.current }
}
