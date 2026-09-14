package com.example.game2048.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Whether the game's own warm palette should use its dark variant right now. */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val LightColors = lightColorScheme(
    primary = ClaudeAccent,
    onPrimary = Color.White,
    background = LightBackground,
    surface = LightBackground,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary
)

private val DarkColors = darkColorScheme(
    primary = ClaudeAccent,
    onPrimary = Color.White,
    background = DarkBackground,
    surface = DarkBackground,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary
)

@Composable
fun Game2048Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
