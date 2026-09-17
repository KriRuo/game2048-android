package com.example.game2048.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.game2048.logic.TilePalette
import com.example.game2048.logic.TilePattern

/** Whether the game's own warm palette should use its dark variant right now. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/** The resolved colors for the currently-selected [TilePalette]; see [paletteColorsFor]. */
val LocalPaletteColors = staticCompositionLocalOf { paletteColorsFor(TilePalette.DEFAULT, isDark = false) }

/** The currently-selected [TilePattern], drawn as an overlay on top of [LocalPaletteColors]'s
 *  tile colors rather than replacing them -- see [com.example.game2048.tilePattern]. */
val LocalTilePattern = staticCompositionLocalOf { TilePattern.DEFAULT }

@Composable
fun Game2048Theme(
    palette: TilePalette = TilePalette.DEFAULT,
    pattern: TilePattern = TilePattern.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val paletteColors = paletteColorsFor(palette, darkTheme)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = paletteColors.accent,
            onPrimary = Color.White,
            background = paletteColors.background,
            surface = paletteColors.background,
            onBackground = paletteColors.textPrimary,
            onSurface = paletteColors.textPrimary
        )
    } else {
        lightColorScheme(
            primary = paletteColors.accent,
            onPrimary = Color.White,
            background = paletteColors.background,
            surface = paletteColors.background,
            onBackground = paletteColors.textPrimary,
            onSurface = paletteColors.textPrimary
        )
    }
    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme,
        LocalPaletteColors provides paletteColors,
        LocalTilePattern provides pattern
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
