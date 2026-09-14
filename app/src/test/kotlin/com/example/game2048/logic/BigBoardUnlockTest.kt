package com.example.game2048.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BigBoardUnlockTest {

    @Test
    fun `unlock level matches the top tile-palette tier`() {
        assertEquals(TilePalette.BERRY.unlockLevel, BigBoardUnlock.UNLOCK_LEVEL)
    }

    @Test
    fun `isUnlocked is false just below the threshold and true at it`() {
        assertFalse(BigBoardUnlock.isUnlocked(BigBoardUnlock.UNLOCK_LEVEL - 1))
        assertTrue(BigBoardUnlock.isUnlocked(BigBoardUnlock.UNLOCK_LEVEL))
    }

    @Test
    fun `isUnlocked stays true above the threshold`() {
        assertTrue(BigBoardUnlock.isUnlocked(BigBoardUnlock.UNLOCK_LEVEL + 20))
    }
}
