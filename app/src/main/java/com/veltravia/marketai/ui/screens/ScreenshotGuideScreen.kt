package com.veltravia.marketai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.veltravia.marketai.domain.IllustrativeChart
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.AccentViolet
import com.veltravia.marketai.ui.theme.BearRed
import com.veltravia.marketai.ui.theme.BullGreen
import com.veltravia.marketai.ui.theme.GoldAmber
import com.veltravia.marketai.ui.theme.NavyBlack
import com.veltravia.marketai.ui.theme.SurfaceLight
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextSecondary
import kotlin.math.max
import kotlin.math.min

private enum class ChartQuality { GOOD, BAD }

/**
 * Chart-screenshot quality guide. Shown once during onboarding (right after the
 * broker step, before the questionnaire) via [ctaLabel]="Analyze Now!", and
 * revisitable any time from Profile ("Screenshot guide") with a back arrow and
 * a "Got it" button that just pops back.
 *
 * The good/bad example charts are rendered live with Canvas from
 * [IllustrativeChart] — not static images — since illustrating "crisp vs.
 * blurry" needs an actual chart shape, and this avoids shipping a broken or
 * placeholder image asset.
 */
@Composable
fun ScreenshotGuideScreen(
    ctaLabel: String,
    onCta: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf<ChartQuality?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBack() }
                )
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }

        Text(
            "Chart Analysis Guide",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "A clear screenshot helps Market AI read your charts precisely and return stronger entries, SL, and TP.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        ExampleCard(
            quality = ChartQuality.GOOD,
            badgeText = "\u2713 Good",
            badgeColor = BullGreen,
            title = "Ideal Screenshot",
            description = "Candles are crisp, wicks visible, and enough history is shown to understand recent market structure.",
            onExpand = { expanded = ChartQuality.GOOD }
        )

        Spacer(Modifier.height(16.dp))

        ExampleCard(
            quality = ChartQuality.BAD,
            badgeText = "\u2715 Bad",
            badgeColor = BearRed,
            title = "Avoid This",
            description = "Blurry or over-compressed. Wicks and bodies get hidden, or too little recent history is shown \u2014 structure becomes unreadable.",
            onExpand = { expanded = ChartQuality.BAD }
        )

        Spacer(Modifier.height(20.dp))

        ChecklistCard()

        Spacer(Modifier.height(16.dp))

        Text(
            "You can always revisit this guide from Profile \u2192 Screenshot guide.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(AccentViolet, AccentCyan)))
                .clickable { onCta() }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                ctaLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NavyBlack
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    val expandedQuality = expanded
    if (expandedQuality != null) {
        Dialog(onDismissRequest = { expanded = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { expanded = null },
                contentAlignment = Alignment.Center
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    val (badgeText, badgeColor) = if (expandedQuality == ChartQuality.GOOD) {
                        "\u2713 Good" to BullGreen
                    } else {
                        "\u2715 Bad" to BearRed
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(badgeText, color = badgeColor, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                    CandlestickPreview(
                        quality = expandedQuality,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Tap anywhere to close",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ExampleCard(
    quality: ChartQuality,
    badgeText: String,
    badgeColor: Color,
    title: String,
    description: String,
    onExpand: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLight)
            .border(1.5.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            CandlestickPreview(quality = quality, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(badgeText, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onExpand) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Tap to expand")
            }
        }
    }
}

@Composable
private fun CandlestickPreview(quality: ChartQuality, modifier: Modifier = Modifier) {
    val candles = remember(quality) {
        val seed = if (quality == ChartQuality.GOOD) 4101L else 7733L
        IllustrativeChart.generate(seed)
    }
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.background(Color(0xFF060B10))) {
        val w = size.width
        val h = size.height
        val labelGutter = if (quality == ChartQuality.GOOD) 46.dp.toPx() else 0f
        val chartWidth = w - labelGutter
        val maxPrice = candles.maxOf { it.high }
        val minPrice = candles.minOf { it.low }
        val range = (maxPrice - minPrice).takeIf { it > 0 } ?: 1.0

        fun yFor(price: Double): Float {
            val t = ((price - minPrice) / range).toFloat()
            return h - (t * h * 0.92f) - h * 0.04f
        }

        // gridlines
        val gridAlpha = if (quality == ChartQuality.GOOD) 0.14f else 0.06f
        val gridLines = 5
        for (i in 0..gridLines) {
            val y = h * i / gridLines
            drawLine(
                color = Color.White.copy(alpha = gridAlpha),
                start = Offset(0f, y),
                end = Offset(chartWidth, y),
                strokeWidth = 1f
            )
            if (quality == ChartQuality.GOOD) {
                val price = minPrice + range * (1.0 - i.toDouble() / gridLines)
                textMeasurer.measure(
                    text = "%.2f".format(price),
                    style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp)
                ).let { result ->
                    drawText(result, topLeft = Offset(chartWidth + 4f, y - result.size.height / 2f))
                }
            }
        }

        val slotWidth = chartWidth / candles.size
        val bodyWidth = slotWidth * (if (quality == ChartQuality.GOOD) 0.55f else 0.5f)

        candles.forEachIndexed { index, candle ->
            val isUp = candle.close >= candle.open
            val baseColor = if (isUp) BullGreen else BearRed
            val cx = slotWidth * index + slotWidth / 2f
            val yOpen = yFor(candle.open)
            val yClose = yFor(candle.close)
            val yHigh = yFor(candle.high)
            val yLow = yFor(candle.low)
            val bodyTop = min(yOpen, yClose)
            val bodyBottom = max(yOpen, yClose)

            if (quality == ChartQuality.GOOD) {
                drawLine(baseColor, Offset(cx, yHigh), Offset(cx, yLow), strokeWidth = 2f)
                drawRect(
                    color = baseColor,
                    topLeft = Offset(cx - bodyWidth / 2f, bodyTop),
                    size = androidx.compose.ui.geometry.Size(bodyWidth, max(bodyBottom - bodyTop, 2f))
                )
            } else {
                // Desaturated + double-drawn with jitter to simulate motion blur, plus low alpha.
                val desaturated = Color(
                    red = (baseColor.red * 0.7f + 0.3f),
                    green = (baseColor.green * 0.7f + 0.3f),
                    blue = (baseColor.blue * 0.7f + 0.3f),
                    alpha = 0.5f
                )
                val jitter = 3.5f
                for (offset in listOf(-jitter, 0f, jitter)) {
                    drawLine(desaturated, Offset(cx + offset, yHigh), Offset(cx + offset, yLow), strokeWidth = 2.5f)
                    drawRect(
                        color = desaturated,
                        topLeft = Offset(cx - bodyWidth / 2f + offset, bodyTop),
                        size = androidx.compose.ui.geometry.Size(bodyWidth, max(bodyBottom - bodyTop, 2f))
                    )
                }
            }
        }

        if (quality == ChartQuality.BAD) {
            // Overexposed glare blob + heavy vignette to sell "unusable screenshot".
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                    center = Offset(w * 0.68f, h * 0.62f),
                    radius = w * 0.32f
                ),
                radius = w * 0.32f,
                center = Offset(w * 0.68f, h * 0.62f)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.18f),
                size = size
            )
        }
    }
}

private data class ChecklistItem(val icon: ImageVector, val title: String, val desc: String)

@Composable
private fun ChecklistCard() {
    val items = listOf(
        ChecklistItem(
            Icons.Filled.CameraAlt,
            "Show recent price action",
            "Include a slice of history so Market AI can read swing highs/lows, structure shifts, and nearby levels."
        ),
        ChecklistItem(
            Icons.Filled.Contrast,
            "Keep candles & wicks legible",
            "Avoid blur/glare and heavy overlays. Body and wick detail must be visible."
        ),
        ChecklistItem(
            Icons.Filled.Straighten,
            "Balanced zoom",
            "Don\u2019t over-zoom or zoom out too far. Natural scale shows momentum and structure."
        ),
        ChecklistItem(
            Icons.Filled.History,
            "Capture both timeframes cleanly",
            "If analyzing 4H + 15M, capture each clearly with matching pair symbols/timestamps."
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GoldAmber.copy(alpha = 0.08f))
            .border(1.dp, GoldAmber.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Quick checklist for better results",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = GoldAmber
            )
        }
        Spacer(Modifier.height(14.dp))
        items.forEachIndexed { i, item ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(GoldAmber.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                    Text(item.desc, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            if (i != items.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}
