package com.example.game2048.ui.theme

import androidx.compose.ui.graphics.Color

// A warm, "Claude"-inspired neutral palette: cream paper background with a clay/terracotta
// accent, instead of the classic 2048 game's brighter yellow/orange tile set.

// Light theme
val LightBackground = Color(0xFFFAF9F5)
val LightBoardFrame = Color(0xFFE8E3D8)
val LightEmptyCell = Color(0xFFDEDAD0)
val LightTextPrimary = Color(0xFF3D3929)
val LightTextMuted = Color(0xFF8A867C)
val LightSurfaceChip = Color(0xFFEFEBE1)

// Dark theme
val DarkBackground = Color(0xFF211F1C)
val DarkBoardFrame = Color(0xFF2C2A25)
val DarkEmptyCell = Color(0xFF37342D)
val DarkTextPrimary = Color(0xFFF4F1E9)
val DarkTextMuted = Color(0xFFA39E92)
val DarkSurfaceChip = Color(0xFF35322B)

// Brand accent -- Claude's signature clay/terracotta -- used for CTAs, the wordmark, and
// woven through the top of the tile ramp.
val ClaudeAccent = Color(0xFFD97757)
val ClaudeAccentDeep = Color(0xFFB85A3E)

/**
 * Tile color ramp, keyed by tile value, warming from soft cream through clay to a deep
 * terracotta at 2048+. Kept in light/dark pairs so tiles stay readable on both backgrounds.
 */
private val lightTileRamp: Map<Int, Color> = mapOf(
    2 to Color(0xFFF1EDE3),
    4 to Color(0xFFE8DFC9),
    8 to Color(0xFFE3C9A0),
    16 to Color(0xFFE6B27B),
    32 to Color(0xFFE49D65),
    64 to Color(0xFFE28850),
    128 to Color(0xFFD97757), // Claude accent
    256 to Color(0xFFC8663F),
    512 to Color(0xFFB85A3E),
    1024 to Color(0xFFA34934),
    2048 to Color(0xFF8C3A2B)
)

private val darkTileRamp: Map<Int, Color> = mapOf(
    2 to Color(0xFF3C3930),
    4 to Color(0xFF4A4433),
    8 to Color(0xFF6B5334),
    16 to Color(0xFF8A5F35),
    32 to Color(0xFFA36A38),
    64 to Color(0xFFC0763F),
    128 to Color(0xFFD97757), // Claude accent stays consistent in dark mode
    256 to Color(0xFFC8663F),
    512 to Color(0xFFB85A3E),
    1024 to Color(0xFFA34934),
    2048 to Color(0xFF8C3A2B)
)

private val lightTileRampFallback = Color(0xFF5A2A1F)
private val darkTileRampFallback = Color(0xFF6E3323)

fun tileColor(value: Int, isDark: Boolean): Color {
    val ramp = if (isDark) darkTileRamp else lightTileRamp
    return ramp[value] ?: if (isDark) darkTileRampFallback else lightTileRampFallback
}

/** Tiles up to 4 read best with dark text; everything warmer than that wants light text. */
fun tileTextColor(value: Int, isDark: Boolean): Color = when {
    value <= 4 && !isDark -> LightTextPrimary
    value <= 4 && isDark -> DarkTextPrimary
    else -> Color(0xFFFBF7F0)
}
