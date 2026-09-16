package com.example.game2048.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreakTrackerTest {

    @Test
    fun `first ever open starts a streak of one`() {
        val state = StreakTracker.onAppOpened(StreakState.NONE, todayEpochDay = 100)
        assertEquals(StreakState(current = 1, longest = 1, lastPlayedEpochDay = 100), state)
    }

    @Test
    fun `opening again the same day does not change the streak`() {
        val previous = StreakState(current = 3, longest = 5, lastPlayedEpochDay = 100)
        val state = StreakTracker.onAppOpened(previous, todayEpochDay = 100)
        assertEquals(previous, state)
    }

    @Test
    fun `opening the very next day extends the streak`() {
        val previous = StreakState(current = 3, longest = 5, lastPlayedEpochDay = 100)
        val state = StreakTracker.onAppOpened(previous, todayEpochDay = 101)
        assertEquals(StreakState(current = 4, longest = 5, lastPlayedEpochDay = 101), state)
    }

    @Test
    fun `extending the streak past the longest ever updates longest too`() {
        val previous = StreakState(current = 5, longest = 5, lastPlayedEpochDay = 100)
        val state = StreakTracker.onAppOpened(previous, todayEpochDay = 101)
        assertEquals(StreakState(current = 6, longest = 6, lastPlayedEpochDay = 101), state)
    }

    @Test
    fun `skipping a day resets current streak but keeps the longest record`() {
        val previous = StreakState(current = 5, longest = 9, lastPlayedEpochDay = 100)
        val state = StreakTracker.onAppOpened(previous, todayEpochDay = 103)
        assertEquals(StreakState(current = 1, longest = 9, lastPlayedEpochDay = 103), state)
    }

    @Test
    fun `a clock that moved backwards is ignored rather than rewarded or punished`() {
        val previous = StreakState(current = 5, longest = 9, lastPlayedEpochDay = 100)
        val state = StreakTracker.onAppOpened(previous, todayEpochDay = 98)
        assertEquals(previous, state)
    }

    @Test
    fun `newlyReachedMilestone returns the highest milestone just crossed`() {
        val before = StreakState(current = 2, longest = 2, lastPlayedEpochDay = 100)
        val after = StreakState(current = 3, longest = 3, lastPlayedEpochDay = 101)
        assertEquals(3, StreakTracker.newlyReachedMilestone(before, after))
    }

    @Test
    fun `newlyReachedMilestone returns null when no milestone was crossed`() {
        val before = StreakState(current = 4, longest = 4, lastPlayedEpochDay = 100)
        val after = StreakState(current = 5, longest = 5, lastPlayedEpochDay = 101)
        assertNull(StreakTracker.newlyReachedMilestone(before, after))
    }

    @Test
    fun `newlyReachedMilestone returns the highest of several milestones skipped at once`() {
        // e.g. streak was reset, then restored from a backup jumping current from 1 to 10.
        val before = StreakState(current = 1, longest = 30, lastPlayedEpochDay = 100)
        val after = StreakState(current = 10, longest = 30, lastPlayedEpochDay = 101)
        assertEquals(7, StreakTracker.newlyReachedMilestone(before, after))
    }

    @Test
    fun `newlyReachedMilestone from a fresh streak of exactly three fires the first milestone`() {
        val after = StreakTracker.onAppOpened(StreakState.NONE, todayEpochDay = 1)
        val day2 = StreakTracker.onAppOpened(after, todayEpochDay = 2)
        val day3 = StreakTracker.onAppOpened(day2, todayEpochDay = 3)
        assertEquals(3, StreakTracker.newlyReachedMilestone(day2, day3))
    }

    @Test
    fun `dailyBonusXp grows with streak day`() {
        assertEquals(15, StreakTracker.dailyBonusXp(1))
        assertEquals(30, StreakTracker.dailyBonusXp(2))
        assertEquals(75, StreakTracker.dailyBonusXp(5))
    }

    @Test
    fun `dailyBonusXp caps at ten streak days`() {
        val atCap = StreakTracker.dailyBonusXp(10)
        assertEquals(atCap, StreakTracker.dailyBonusXp(11))
        assertEquals(atCap, StreakTracker.dailyBonusXp(365))
    }

    @Test
    fun `dailyBonusXp treats a non-positive streak day the same as day one`() {
        assertEquals(StreakTracker.dailyBonusXp(1), StreakTracker.dailyBonusXp(0))
    }
}
