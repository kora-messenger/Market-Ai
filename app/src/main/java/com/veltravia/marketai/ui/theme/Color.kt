package com.veltravia.marketai.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// MarketScope AI design language — always-light "clean terminal" look.
// Brand/semantic colors: accents are tuned slightly darker than the old
// dark-theme values so they stay legible on white/light-gray surfaces.
val AccentCyan = Color(0xFF0891B2)     // primary accent (dark cyan — readable on white)
val AccentViolet = Color(0xFF7C3AED)  // secondary accent (deep violet)
val BullGreen = Color(0xFF16A34A)
val BearRed = Color(0xFFDC2626)
val GoldAmber = Color(0xFFD97706)

/** The one and only palette: MarketScope AI is always light/white (like the FxLens reference). */
data class MarketAiPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color
)

val LightPalette = MarketAiPalette(
    background = Color(0xFFFFFFFF),      // app canvas: pure white
    surface = Color(0xFFF3F4F7),        // cards: soft light gray (visible on white)
    surfaceElevated = Color(0xFFEAECF1),// elevated/secondary surfaces
    textPrimary = Color(0xFF0B0E14),    // near-black text
    textSecondary = Color(0xFF4B5567),
    textMuted = Color(0xFF8A93A6),
    border = Color(0xFFE2E5EC)
)

val LocalMarketAiPalette = staticCompositionLocalOf { LightPalette }

/**
 * Backward-compatible aliases: every existing screen already writes
 * `.background(NavyBlack)`, `color = TextSecondary`, etc. These resolve to
 * the light palette above, so all call sites keep compiling unchanged while
 * the whole app renders white. (The old names are kept deliberately — they
 * are referenced across ~20 screen files.)
 */
val NavyBlack: Color
    @Composable get() = LocalMarketAiPalette.current.background

val SurfaceDark: Color
    @Composable get() = LocalMarketAiPalette.current.surface

val SurfaceLight: Color
    @Composable get() = LocalMarketAiPalette.current.surfaceElevated

val TextPrimary: Color
    @Composable get() = LocalMarketAiPalette.current.textPrimary

val TextSecondary: Color
    @Composable get() = LocalMarketAiPalette.current.textSecondary

val TextMuted: Color
    @Composable get() = LocalMarketAiPalette.current.textMuted

val BorderSubtle: Color
    @Composable get() = LocalMarketAiPalette.current.border
