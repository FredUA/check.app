package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CheckAppColorScheme = darkColorScheme(
    primary = NeonPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4A1835),
    onPrimaryContainer = Color(0xFFFFD8E4),
    secondary = NeonBlue,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF1E3A6C),
    onSecondaryContainer = Color(0xFFB8EAFF),
    tertiary = NeonPurple,
    onTertiary = Color.White,
    background = DeepMidnight,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCardLight,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceCardBorder,
    outlineVariant = Color(0xFF384E96)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CheckAppColorScheme,
        typography = Typography,
        content = content
    )
}
