package com.example.emailbot.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = IndigoDark,
    onPrimary = Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = OnIndigoContainer,
    secondary = Violet,
    onSecondary = Color.White,
    background = LightBackground,
    surface = Color.White,
    surfaceVariant = LightSurfaceVariant,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF1F1F8),
    error = StatusFailed,
)

private val DarkColors = darkColorScheme(
    primary = IndigoLight,
    onPrimary = OnIndigoLight,
    primaryContainer = IndigoContainerDark,
    onPrimaryContainer = IndigoContainer,
    secondary = Color(0xFFC4B5FD),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = Color(0xFF1D1D27),
    error = Color(0xFFF87171),
)

/**
 * Fixed brand colors instead of Material You dynamic colors, so the indigo header,
 * buttons and widget always match.
 */
@Composable
fun EmailBotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
