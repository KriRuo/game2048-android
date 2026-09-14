package com.example.game2048.logic

/**
 * Pure, dependency-free unlock rule for the optional 5x5 "Big Board" mode (see [ThemeUnlocks],
 * [LevelTracker] for the same pattern). Unlike a tile palette this isn't purely cosmetic -- it
 * changes how the game plays -- so unlocking it only makes the toggle available; the player
 * still opts in explicitly (default off), and can switch back to the classic 4x4 at any time.
 * Gated on the same level as the top palette tier ([TilePalette.BERRY]), since it's the same
 * "you've mastered the classic game" milestone.
 */
object BigBoardUnlock {
    val UNLOCK_LEVEL: Int = TilePalette.BERRY.unlockLevel

    fun isUnlocked(level: Int): Boolean = level >= UNLOCK_LEVEL
}
