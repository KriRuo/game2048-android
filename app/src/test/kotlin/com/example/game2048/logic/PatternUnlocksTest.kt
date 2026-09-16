package com.example.game2048.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternUnlocksTest {

    @Test
    fun `only Solid is unlocked at level 1`() {
        assertEquals(listOf(TilePattern.NONE), PatternUnlocks.unlockedPatterns(1))
    }

    @Test
    fun `Stripes joins at level 40`() {
        assertEquals(
            listOf(TilePattern.NONE, TilePattern.STRIPES),
            PatternUnlocks.unlockedPatterns(40)
        )
    }

    @Test
    fun `isUnlocked is false just below the threshold and true at it`() {
        assertFalse(PatternUnlocks.isUnlocked(TilePattern.STRIPES, 39))
        assertTrue(PatternUnlocks.isUnlocked(TilePattern.STRIPES, 40))
    }

    @Test
    fun `Bubbles is the last to unlock, at level 70`() {
        assertFalse(PatternUnlocks.isUnlocked(TilePattern.BUBBLES, 69))
        assertTrue(PatternUnlocks.isUnlocked(TilePattern.BUBBLES, 70))
        assertEquals(
            listOf(TilePattern.NONE, TilePattern.STRIPES, TilePattern.CAMO, TilePattern.BUBBLES),
            PatternUnlocks.unlockedPatterns(70)
        )
    }

    @Test
    fun `newlyUnlocked reports a single threshold crossed`() {
        assertEquals(TilePattern.STRIPES, PatternUnlocks.newlyUnlocked(before = 39, after = 40))
    }

    @Test
    fun `newlyUnlocked reports the highest of several thresholds crossed at once`() {
        assertEquals(TilePattern.CAMO, PatternUnlocks.newlyUnlocked(before = 30, after = 55))
    }

    @Test
    fun `newlyUnlocked is null when no threshold was crossed`() {
        assertNull(PatternUnlocks.newlyUnlocked(before = 41, after = 42))
    }

    @Test
    fun `fromId falls back to Solid for unknown or null ids`() {
        assertEquals(TilePattern.NONE, TilePattern.fromId(null))
        assertEquals(TilePattern.NONE, TilePattern.fromId("not-a-real-id"))
        assertEquals(TilePattern.STRIPES, TilePattern.fromId("stripes"))
    }
}
