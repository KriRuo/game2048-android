package com.example.game2048.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyChallengeTrackerTest {

    @Test
    fun `a fresh state has not completed any day`() {
        assertFalse(DailyChallengeTracker.isCompletedToday(DailyChallengeState.NONE, epochDay = 100))
    }

    @Test
    fun `completing today's challenge marks that exact day completed`() {
        val state = DailyChallengeTracker.recordCompletion(DailyChallengeState.NONE, epochDay = 100, score = 500)
        assertTrue(DailyChallengeTracker.isCompletedToday(state, epochDay = 100))
    }

    @Test
    fun `completing one day does not mark a different day completed`() {
        val state = DailyChallengeTracker.recordCompletion(DailyChallengeState.NONE, epochDay = 100, score = 500)
        assertFalse(DailyChallengeTracker.isCompletedToday(state, epochDay = 101))
    }

    @Test
    fun `recordCompletion keeps the higher of the previous and new score as best`() {
        val first = DailyChallengeTracker.recordCompletion(DailyChallengeState.NONE, epochDay = 100, score = 300)
        val second = DailyChallengeTracker.recordCompletion(first, epochDay = 101, score = 700)
        assertEquals(700, second.bestScore)
        assertEquals(700, second.lastScore)

        val third = DailyChallengeTracker.recordCompletion(second, epochDay = 102, score = 200)
        assertEquals(700, third.bestScore)
        assertEquals(200, third.lastScore)
    }

    @Test
    fun `seedFor is deterministic and distinct per day`() {
        assertEquals(DailyChallengeTracker.seedFor(100), DailyChallengeTracker.seedFor(100))
        assertNotEquals(DailyChallengeTracker.seedFor(100), DailyChallengeTracker.seedFor(101))
    }
}
