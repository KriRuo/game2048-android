package com.example.game2048.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeUnlocksTest {

    @Test
    fun `only Clay is unlocked at level 1`() {
        assertEquals(listOf(TilePalette.CLAY), ThemeUnlocks.unlockedPalettes(1))
    }

    @Test
    fun `Meadow joins at level 3`() {
        assertEquals(
            listOf(TilePalette.CLAY, TilePalette.MEADOW),
            ThemeUnlocks.unlockedPalettes(3)
        )
    }

    @Test
    fun `all four are unlocked at level 10`() {
        assertEquals(
            listOf(TilePalette.CLAY, TilePalette.MEADOW, TilePalette.MIDNIGHT, TilePalette.BERRY),
            ThemeUnlocks.unlockedPalettes(10)
        )
    }

    @Test
    fun `isUnlocked is false just below the threshold and true at it`() {
        assertFalse(ThemeUnlocks.isUnlocked(TilePalette.MIDNIGHT, 5))
        assertTrue(ThemeUnlocks.isUnlocked(TilePalette.MIDNIGHT, 6))
    }

    @Test
    fun `newlyUnlocked reports a single threshold crossed`() {
        assertEquals(TilePalette.MEADOW, ThemeUnlocks.newlyUnlocked(before = 2, after = 3))
    }

    @Test
    fun `newlyUnlocked reports the highest of several thresholds crossed at once`() {
        assertEquals(TilePalette.BERRY, ThemeUnlocks.newlyUnlocked(before = 1, after = 10))
    }

    @Test
    fun `newlyUnlocked is null when no threshold was crossed`() {
        assertNull(ThemeUnlocks.newlyUnlocked(before = 4, after = 5))
    }

    @Test
    fun `fromId falls back to Clay for unknown or null ids`() {
        assertEquals(TilePalette.CLAY, TilePalette.fromId(null))
        assertEquals(TilePalette.CLAY, TilePalette.fromId("not-a-real-id"))
        assertEquals(TilePalette.MEADOW, TilePalette.fromId("meadow"))
    }
}
