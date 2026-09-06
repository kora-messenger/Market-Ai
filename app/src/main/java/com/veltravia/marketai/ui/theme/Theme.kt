package com.veltravia.marketai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private fun colorSchemeFor(palette: MarketAiPalette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = AccentCyan,
        onPrimary = palette.background,
        secondary = AccentViolet,
        onSecondary = palette.background,
        tertiary = BullGreen,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        error = BearRed,
        outline = palette.border
    )
} else {
    lightColorScheme(
        primary = AccentCyan,
        onPrimary = palette.background,
        secondary = AccentViolet,
        onSecondary = palette.background,
        tertiary = BullGreen,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        error = BearRed,
        outline = palette.border
    )
}

/**
 * Market Ai's theme follows the device's system light/dark setting — no
 * in-app toggle needed. All screens read colors either through
 * MaterialTheme.colorScheme.* or through the legacy named aliases in
 * Color.kt (NavyBlack, SurfaceDark, TextSecondary, ...), which are now
 * backed by the same [MarketAiPalette] provided here, so both styles of
 * color access stay in sync automatically.
 */
@Composable
fun MarketAiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) DarkPalette else LightPalette

    CompositionLocalProvider(LocalMarketAiPalette provides palette) {
        MaterialTheme(
            colorScheme = colorSchemeFor(palette, dark),
            typography = MarketAiTypography,
            content = content
        )
    }
}
