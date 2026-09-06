package com.veltravia.marketai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Market Ai design language — "Terminal Luxury".
// Brand/semantic colors stay constant across light & dark (accent, bull/bear,
// gold) — only surfaces, backgrounds, text and borders adapt to the device's
// theme, same convention most real apps use (green stays green either way).
val AccentCyan = Color(0xFF22D3EE)
val AccentViolet = Color(0xFF8B5CF6)
val BullGreen = Color(0xFF22C55E)
val BearRed = Color(0xFFEF4444)
val GoldAmber = Color(0xFFF59E0B)

/** Theme-dependent roles: background, surfaces, text, borders. */
data class MarketAiPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color
)

val DarkPalette = MarketAiPalette(
    background = Color(0xFF0B0E14),
    surface = Color(0xFF131826),
    surfaceElevated = Color(0xFF1B2233),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    border = Color(0xFF232B3E)
)

val LightPalette = MarketAiPalette(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF2F4F8),
    textPrimary = Color(0xFF0B0E14),
    textSecondary = Color(0xFF4B5567),
    textMuted = Color(0xFF8A93A6),
    border = Color(0xFFE2E5EC)
)

val LocalMarketAiPalette = staticCompositionLocalOf { DarkPalette }

/**
 * Backward-compatible aliases: every existing screen already writes
 * `.background(NavyBlack)`, `color = TextSecondary`, etc. Turning these into
 * @Composable-getter properties backed by [LocalMarketAiPalette] means every
 * one of those call sites (all inside @Composable functions already) keeps
 * compiling unchanged, but now resolves to the correct light/dark value
 * automatically — no per-screen edits required.
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

/** Convenience: is the app currently rendering in dark mode (follows the device). */
val isMarketAiDark: Boolean
    @Composable get() = isSystemInDarkTheme()
