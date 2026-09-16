package com.example.game2048.logic

/**
 * Pure, dependency-free tile-pattern unlock logic (see [StreakTracker], [LevelTracker],
 * [ThemeUnlocks] for the same pattern). A pattern is drawn as an overlay on top of whichever
 * [TilePalette] is active rather than replacing its colors, so it's a second, independent
 * cosmetic axis rather than a rival to palette -- both unlock permanently as level climbs.
 *
 * Existing palette/board-size unlocks all land by level 30; these start where that leaves off,
 * so leveling still pays out something past the point everything else runs dry.
 */
enum class TilePattern(val id: String, val displayName: String, val unlockLevel: Int) {
    NONE("none", "Solid", 1),
    STRIPES("stripes", "Stripes", 40),
    CAMO("camo", "Camo", 55),
    BUBBLES("bubbles", "Bubbles", 70);

    companion object {
        val DEFAULT = NONE
        fun fromId(id: String?): TilePattern = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

object PatternUnlocks {

    fun unlockedPatterns(level: Int): List<TilePattern> =
        TilePattern.entries.filter { level >= it.unlockLevel }

    fun isUnlocked(pattern: TilePattern, level: Int): Boolean = level >= pattern.unlockLevel

    /** The highest-tier pattern newly unlocked going from [before] to [after], if any. */
    fun newlyUnlocked(before: Int, after: Int): TilePattern? =
        TilePattern.entries.filter { it.unlockLevel in (before + 1)..after }.maxByOrNull { it.unlockLevel }
}
