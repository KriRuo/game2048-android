package com.example.game2048.logic

/**
 * Pure, dependency-free tile-palette unlock logic (see [StreakTracker], [LevelTracker] for the
 * same pattern). Palettes unlock permanently as [LevelTracker]'s level climbs -- never based on
 * anything that can go back down, so once unlocked, always unlocked.
 */
enum class TilePalette(val id: String, val displayName: String, val unlockLevel: Int) {
    CLAY("clay", "Clay", 1),
    MEADOW("meadow", "Meadow", 3),
    MIDNIGHT("midnight", "Midnight", 6),
    BERRY("berry", "Berry", 10);

    companion object {
        val DEFAULT = CLAY
        fun fromId(id: String?): TilePalette = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

object ThemeUnlocks {

    fun unlockedPalettes(level: Int): List<TilePalette> =
        TilePalette.entries.filter { level >= it.unlockLevel }

    fun isUnlocked(palette: TilePalette, level: Int): Boolean = level >= palette.unlockLevel

    /** The highest-tier palette newly unlocked going from [before] to [after], if any. */
    fun newlyUnlocked(before: Int, after: Int): TilePalette? =
        TilePalette.entries.filter { it.unlockLevel in (before + 1)..after }.maxByOrNull { it.unlockLevel }
}
