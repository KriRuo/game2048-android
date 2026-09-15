package com.example.game2048.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardSizeUnlocksTest {

    @Test
    fun `only Classic is unlocked at level 1`() {
        assertEquals(listOf(BoardSizeOption.CLASSIC), BoardSizeUnlocks.unlockedOptions(1))
    }

    @Test
    fun `Big Board joins at level 10`() {
        assertEquals(
            listOf(BoardSizeOption.CLASSIC, BoardSizeOption.BIG),
            BoardSizeUnlocks.unlockedOptions(10)
        )
    }

    @Test
    fun `all three are unlocked at level 15`() {
        assertEquals(
            listOf(BoardSizeOption.CLASSIC, BoardSizeOption.BIG, BoardSizeOption.MEGA),
            BoardSizeUnlocks.unlockedOptions(15)
        )
    }

    @Test
    fun `isUnlocked is false just below the threshold and true at it`() {
        assertFalse(BoardSizeUnlocks.isUnlocked(BoardSizeOption.MEGA, 14))
        assertTrue(BoardSizeUnlocks.isUnlocked(BoardSizeOption.MEGA, 15))
    }

    @Test
    fun `newlyUnlocked reports a single threshold crossed`() {
        assertEquals(BoardSizeOption.BIG, BoardSizeUnlocks.newlyUnlocked(before = 9, after = 10))
    }

    @Test
    fun `newlyUnlocked reports the highest of several thresholds crossed at once`() {
        assertEquals(BoardSizeOption.MEGA, BoardSizeUnlocks.newlyUnlocked(before = 1, after = 15))
    }

    @Test
    fun `newlyUnlocked is null when no threshold was crossed`() {
        assertNull(BoardSizeUnlocks.newlyUnlocked(before = 11, after = 14))
    }

    @Test
    fun `fromId falls back to Classic for unknown or null ids`() {
        assertEquals(BoardSizeOption.CLASSIC, BoardSizeOption.fromId(null))
        assertEquals(BoardSizeOption.CLASSIC, BoardSizeOption.fromId("not-a-real-id"))
        assertEquals(BoardSizeOption.BIG, BoardSizeOption.fromId("big"))
        assertEquals(BoardSizeOption.MEGA, BoardSizeOption.fromId("mega"))
    }

    @Test
    fun `each option's size matches its board-size constant`() {
        assertEquals(BOARD_SIZE, BoardSizeOption.CLASSIC.size)
        assertEquals(BIG_BOARD_SIZE, BoardSizeOption.BIG.size)
        assertEquals(MEGA_BOARD_SIZE, BoardSizeOption.MEGA.size)
    }
}
