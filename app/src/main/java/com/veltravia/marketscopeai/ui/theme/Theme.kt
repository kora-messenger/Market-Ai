package com.veltravia.marketscopeai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * MarketScope AI's theme is ALWAYS light/white (per Ijezie's decision, matching the
 * FxLens reference look) — regardless of the device's dark-mode setting.
 *
 * All screens read colors either through MaterialTheme.colorScheme.* or
 * through the legacy named aliases in Color.kt (NavyBlack, SurfaceDark,
 * TextSecondary, ...), which are backed by the same [MarketAiPalette] provided
 * here, so both styles of color access stay in sync automatically.
 */
@Composable
fun MarketAiTheme(content: @Composable () -> Unit) {
    val palette = LightPalette

    CompositionLocalProvider(LocalMarketAiPalette provides palette) {
        MaterialTheme(
            colorScheme = lightColorScheme(
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
            ),
            typography = MarketAiTypography,
            content = content
        )
    }
}
