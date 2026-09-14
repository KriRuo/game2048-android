package com.example.game2048.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.game2048.logic.TilePalette

/**
 * Resolved colors for one [TilePalette] in either light or dark mode. Everything the UI reads
 * to render itself comes from here (via [LocalPaletteColors]) instead of hardcoded top-level
 * constants, so the active theme can change at runtime.
 */
data class PaletteColors(
    val background: Color,
    val boardFrame: Color,
    val emptyCell: Color,
    val surfaceChip: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val tileRamp: Map<Int, Color>,
    val tileRampFallback: Color
) {
    fun tileColor(value: Int): Color = tileRamp[value] ?: tileRampFallback

    /** Tiles up to 4 read best with dark text; everything warmer than that wants light text. */
    fun tileTextColor(value: Int): Color = if (value <= 4) textPrimary else Color(0xFFFBF7F0)
}

// Shared upper ramp per palette: 128 is always that palette's accent, and 256-2048 are identical
// between light and dark (already saturated/dark enough to read on either background) -- mirrors
// how the original Clay palette was structured.

private val clayUpper = mapOf(
    128 to ClaudeAccent,
    256 to Color(0xFFC8663F),
    512 to Color(0xFFB85A3E),
    1024 to Color(0xFFA34934),
    2048 to Color(0xFF8C3A2B)
)

private val meadowAccent = Color(0xFF6B8E4E)
private val meadowUpper = mapOf(
    128 to meadowAccent,
    256 to Color(0xFF5C7C41),
    512 to Color(0xFF4F7038),
    1024 to Color(0xFF3F5E2E),
    2048 to Color(0xFF334C26)
)

private val midnightAccent = Color(0xFF5B6EE8)
private val midnightUpper = mapOf(
    128 to midnightAccent,
    256 to Color(0xFF4C5AC9),
    512 to Color(0xFF3F49AA),
    1024 to Color(0xFF333B8C),
    2048 to Color(0xFF272D6E)
)

private val berryAccent = Color(0xFFB8507E)
private val berryUpper = mapOf(
    128 to berryAccent,
    256 to Color(0xFFA2456C),
    512 to Color(0xFF8E3B5D),
    1024 to Color(0xFF78314D),
    2048 to Color(0xFF63273E)
)

private fun paletteColors(
    isDark: Boolean,
    background: Color,
    boardFrame: Color,
    emptyCell: Color,
    surfaceChip: Color,
    textPrimary: Color,
    textMuted: Color,
    accent: Color,
    lowerRamp: Map<Int, Color>,
    upperRamp: Map<Int, Color>,
    rampFallback: Color
) = PaletteColors(
    background = background,
    boardFrame = boardFrame,
    emptyCell = emptyCell,
    surfaceChip = surfaceChip,
    textPrimary = textPrimary,
    textMuted = textMuted,
    accent = accent,
    tileRamp = lowerRamp + upperRamp,
    tileRampFallback = rampFallback
)

fun paletteColorsFor(palette: TilePalette, isDark: Boolean): PaletteColors = when (palette) {
    TilePalette.CLAY -> if (!isDark) {
        paletteColors(
            isDark = false,
            background = LightBackground, boardFrame = LightBoardFrame, emptyCell = LightEmptyCell,
            surfaceChip = LightSurfaceChip, textPrimary = LightTextPrimary, textMuted = LightTextMuted,
            accent = ClaudeAccent,
            lowerRamp = mapOf(
                2 to Color(0xFFF1EDE3), 4 to Color(0xFFE8DFC9), 8 to Color(0xFFE3C9A0),
                16 to Color(0xFFE6B27B), 32 to Color(0xFFE49D65), 64 to Color(0xFFE28850)
            ),
            upperRamp = clayUpper,
            rampFallback = Color(0xFF5A2A1F)
        )
    } else {
        paletteColors(
            isDark = true,
            background = DarkBackground, boardFrame = DarkBoardFrame, emptyCell = DarkEmptyCell,
            surfaceChip = DarkSurfaceChip, textPrimary = DarkTextPrimary, textMuted = DarkTextMuted,
            accent = ClaudeAccent,
            lowerRamp = mapOf(
                2 to Color(0xFF3C3930), 4 to Color(0xFF4A4433), 8 to Color(0xFF6B5334),
                16 to Color(0xFF8A5F35), 32 to Color(0xFFA36A38), 64 to Color(0xFFC0763F)
            ),
            upperRamp = clayUpper,
            rampFallback = Color(0xFF6E3323)
        )
    }

    TilePalette.MEADOW -> if (!isDark) {
        paletteColors(
            isDark = false,
            background = Color(0xFFF6F8F1), boardFrame = Color(0xFFE2E7D4), emptyCell = Color(0xFFD8DFC7),
            surfaceChip = Color(0xFFECF0E1), textPrimary = Color(0xFF313A26), textMuted = Color(0xFF838C74),
            accent = meadowAccent,
            lowerRamp = mapOf(
                2 to Color(0xFFF0F1E4), 4 to Color(0xFFE4E8C9), 8 to Color(0xFFD3DC9F),
                16 to Color(0xFFBECD79), 32 to Color(0xFFA8BD65), 64 to Color(0xFF92AF56)
            ),
            upperRamp = meadowUpper,
            rampFallback = Color(0xFF253A1C)
        )
    } else {
        paletteColors(
            isDark = true,
            background = Color(0xFF1A1E16), boardFrame = Color(0xFF242A1F), emptyCell = Color(0xFF2E3527),
            surfaceChip = Color(0xFF2A3122), textPrimary = Color(0xFFEBF0DF), textMuted = Color(0xFF9CA48C),
            accent = meadowAccent,
            lowerRamp = mapOf(
                2 to Color(0xFF383D2F), 4 to Color(0xFF434A37), 8 to Color(0xFF52633A),
                16 to Color(0xFF637A3D), 32 to Color(0xFF748F40), 64 to Color(0xFF82A344)
            ),
            upperRamp = meadowUpper,
            rampFallback = Color(0xFF2E4A24)
        )
    }

    TilePalette.MIDNIGHT -> if (!isDark) {
        paletteColors(
            isDark = false,
            background = Color(0xFFF5F6FA), boardFrame = Color(0xFFE1E3EF), emptyCell = Color(0xFFD6D9E8),
            surfaceChip = Color(0xFFEAEBF5), textPrimary = Color(0xFF2A2C3D), textMuted = Color(0xFF83879C),
            accent = midnightAccent,
            lowerRamp = mapOf(
                2 to Color(0xFFEDEEF7), 4 to Color(0xFFDEE1F2), 8 to Color(0xFFC5CBEC),
                16 to Color(0xFFA9B2E4), 32 to Color(0xFF8D98DC), 64 to Color(0xFF7280D5)
            ),
            upperRamp = midnightUpper,
            rampFallback = Color(0xFF1D2050)
        )
    } else {
        paletteColors(
            isDark = true,
            background = Color(0xFF181A24), boardFrame = Color(0xFF21232F), emptyCell = Color(0xFF2A2D3B),
            surfaceChip = Color(0xFF262838), textPrimary = Color(0xFFEDEEF5), textMuted = Color(0xFF9296AB),
            accent = midnightAccent,
            lowerRamp = mapOf(
                2 to Color(0xFF2A2D3C), 4 to Color(0xFF333749), 8 to Color(0xFF3E4460),
                16 to Color(0xFF4A5278), 32 to Color(0xFF57608F), 64 to Color(0xFF6570A5)
            ),
            upperRamp = midnightUpper,
            rampFallback = Color(0xFF262A66)
        )
    }

    TilePalette.BERRY -> if (!isDark) {
        paletteColors(
            isDark = false,
            background = Color(0xFFFAF5F7), boardFrame = Color(0xFFEEE0E6), emptyCell = Color(0xFFE5D3DB),
            surfaceChip = Color(0xFFF3E7EC), textPrimary = Color(0xFF3A2530), textMuted = Color(0xFF8C7681),
            accent = berryAccent,
            lowerRamp = mapOf(
                2 to Color(0xFFF6EBF0), 4 to Color(0xFFEEDCE4), 8 to Color(0xFFE2C2D2),
                16 to Color(0xFFD5A7C0), 32 to Color(0xFFC88DAE), 64 to Color(0xFFBC749C)
            ),
            upperRamp = berryUpper,
            rampFallback = Color(0xFF471D30)
        )
    } else {
        paletteColors(
            isDark = true,
            background = Color(0xFF211820), boardFrame = Color(0xFF2C2129), emptyCell = Color(0xFF382A33),
            surfaceChip = Color(0xFF332631), textPrimary = Color(0xFFF2E6EC), textMuted = Color(0xFFA6919D),
            accent = berryAccent,
            lowerRamp = mapOf(
                2 to Color(0xFF332730), 4 to Color(0xFF402E3B), 8 to Color(0xFF52374A),
                16 to Color(0xFF653F59), 32 to Color(0xFF784869), 64 to Color(0xFF8B4F78)
            ),
            upperRamp = berryUpper,
            rampFallback = Color(0xFF5A2540)
        )
    }
}
