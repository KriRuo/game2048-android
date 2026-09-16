package com.example.game2048.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelTrackerTest {

    @Test
    fun `level 1 requires zero score`() {
        assertEquals(0L, LevelTracker.scoreRequiredForLevel(1))
    }

    @Test
    fun `zero cumulative score is level 1`() {
        assertEquals(1, LevelTracker.levelForCumulativeScore(0))
    }

    @Test
    fun `score just below a level's threshold stays at the previous level`() {
        val threshold = LevelTracker.scoreRequiredForLevel(5)
        assertEquals(4, LevelTracker.levelForCumulativeScore(threshold - 1))
    }

    @Test
    fun `score exactly at a level's threshold reaches that level`() {
        val threshold = LevelTracker.scoreRequiredForLevel(5)
        assertEquals(5, LevelTracker.levelForCumulativeScore(threshold))
    }

    @Test
    fun `score just above a level's threshold is still that level`() {
        val threshold = LevelTracker.scoreRequiredForLevel(5)
        assertEquals(5, LevelTracker.levelForCumulativeScore(threshold + 1))
    }

    @Test
    fun `thresholds strictly increase level over level`() {
        var previous = LevelTracker.scoreRequiredForLevel(1)
        for (level in 2..50) {
            val current = LevelTracker.scoreRequiredForLevel(level)
            assertTrue("level $level threshold should exceed level ${level - 1}", current > previous)
            previous = current
        }
    }

    @Test
    fun `a large cumulative score still resolves correctly and quickly`() {
        val level = LevelTracker.levelForCumulativeScore(10_000_000L)
        assertTrue(level > 1)
        assertTrue(LevelTracker.scoreRequiredForLevel(level) <= 10_000_000L)
        assertTrue(LevelTracker.scoreRequiredForLevel(level + 1) > 10_000_000L)
    }

    @Test
    fun `progress is zero right at the start of a level`() {
        val threshold = LevelTracker.scoreRequiredForLevel(6)
        assertEquals(0f, LevelTracker.progressToNextLevel(threshold), 0.001f)
    }

    @Test
    fun `progress approaches one just before the next level`() {
        val nextThreshold = LevelTracker.scoreRequiredForLevel(7)
        val progress = LevelTracker.progressToNextLevel(nextThreshold - 1)
        assertTrue(progress > 0.9f)
        assertTrue(progress < 1f)
    }

    @Test
    fun `progress is clamped within zero and one`() {
        val progress = LevelTracker.progressToNextLevel(0)
        assertTrue(progress >= 0f)
        assertTrue(progress <= 1f)
    }

    @Test
    fun `xpProgress is zero earned with the full span right at the start of a level`() {
        val threshold = LevelTracker.scoreRequiredForLevel(6)
        val span = LevelTracker.scoreRequiredForLevel(7) - threshold
        val progress = LevelTracker.xpProgress(threshold)
        assertEquals(0L, progress.earnedInLevel)
        assertEquals(span, progress.spanForLevel)
    }

    @Test
    fun `xpProgress earned matches how far past the current level's threshold the score is`() {
        val threshold = LevelTracker.scoreRequiredForLevel(4)
        val progress = LevelTracker.xpProgress(threshold + 37)
        assertEquals(37L, progress.earnedInLevel)
    }
}
