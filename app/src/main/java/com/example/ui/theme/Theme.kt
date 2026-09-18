package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SultanClockColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = Color(0xFF1C1300),
    primaryContainer = Color(0xFF4A3700),
    onPrimaryContainer = Color(0xFFFFDF9E),
    secondary = CyanAccent,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFF97F0FF),
    tertiary = SuccessGreen,
    onTertiary = Color(0xFF003915),
    tertiaryContainer = Color(0xFF005322),
    onTertiaryContainer = Color(0xFF67FE8D),
    background = DeepBackground,
    onBackground = TextPrimary,
    surface = CardBackground,
    onSurface = TextPrimary,
    surfaceVariant = CardBackgroundElevated,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder,
    outlineVariant = Color(0xFF1E2538),
    error = ErrorRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Sultan Clock is designed with dark IoT theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SultanClockColorScheme,
        typography = Typography,
        content = content
    )
}
