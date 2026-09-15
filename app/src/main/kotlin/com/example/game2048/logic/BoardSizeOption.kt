package com.example.game2048.logic

/**
 * The available board sizes, including the optional bigger ones (see [TilePalette],
 * [BoardSizeUnlocks] for the same pattern). Unlike a tile palette this isn't purely cosmetic --
 * a bigger board changes how the game plays -- so unlocking a size only makes it selectable;
 * the player still opts in explicitly (default [CLASSIC]), and can switch back at any time.
 */
enum class BoardSizeOption(val id: String, val displayName: String, val size: Int, val unlockLevel: Int) {
    CLASSIC("classic", "Classic (4×4)", BOARD_SIZE, unlockLevel = 1),
    BIG("big", "Big Board (5×5)", BIG_BOARD_SIZE, unlockLevel = 10),
    MEGA("mega", "Mega Board (6×6)", MEGA_BOARD_SIZE, unlockLevel = 15),
    GIANT("giant", "Giant Board (8×8)", GIANT_BOARD_SIZE, unlockLevel = 20);

    companion object {
        val DEFAULT = CLASSIC
        fun fromId(id: String?): BoardSizeOption = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

object BoardSizeUnlocks {

    fun unlockedOptions(level: Int): List<BoardSizeOption> =
        BoardSizeOption.entries.filter { level >= it.unlockLevel }

    fun isUnlocked(option: BoardSizeOption, level: Int): Boolean = level >= option.unlockLevel

    /** The highest-tier size newly unlocked going from [before] to [after], if any. */
    fun newlyUnlocked(before: Int, after: Int): BoardSizeOption? =
        BoardSizeOption.entries.filter { it.unlockLevel in (before + 1)..after }.maxByOrNull { it.unlockLevel }
}
