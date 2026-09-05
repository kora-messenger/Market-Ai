package com.veltravia.marketai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MarketAiColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = NavyBlack,
    secondary = AccentViolet,
    onSecondary = NavyBlack,
    tertiary = BullGreen,
    background = NavyBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = TextSecondary,
    error = BearRed,
    outline = BorderSubtle
)

@Composable
fun MarketAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MarketAiColorScheme,
        typography = MarketAiTypography,
        content = content
    )
}
