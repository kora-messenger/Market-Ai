package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.domain.ProjectionEngine
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.components.PremiumSegmentedProgress
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Illustrative "next 12 trades" consistency pitch shown once, right after the
 * notifications soft-ask. Uses the trader's real capital/experience/style from
 * the questionnaire (already completed earlier in onboarding). All numbers are
 * computed live by ProjectionEngine — this is MarketScope AI's own math, not
 * hardcoded copy. The equity visual is a smooth line/area chart (not the old
 * per-trade bar chart) with a "drifting without one" comparison baseline,
 * matching the requested look — drawn with our own violet/cyan accents.
 */
/** FxLens's confirmed dark-card shade (Tailwind slate-900, #0F172A) — found live
 *  in their decompiled bundle, used app-wide for dark surfaces. Used only for this
 *  card's background; doesn't touch the shared DarkInk constant used elsewhere. */
private val ProjectionCardNavy = Color(0xFF0F172A)

@Composable
fun ProjectionIntroScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val user = SessionManager.currentUser(context)
    val answers = SessionManager.questionnaireAnswers(context)

    // Use the trader's own capital from the questionnaire (screen 1: "How much
    // capital do you currently have?"). Only fall back to a sane default if
    // that's somehow missing/zero (e.g. this screen was reached out of order).
    val userCapital = answers?.capitalUsd?.toDoubleOrNull()?.takeIf { it > 0 } ?: 1_000.0
    val projection = remember(userCapital) {
        ProjectionEngine.computeProjection(
            capital = userCapital,
            experience = answers?.experience ?: "",
            style = answers?.style ?: "",
            seedKey = user?.email ?: "market-ai-guest"
        )
    }
    val subtitle = remember {
        ProjectionEngine.consistencyLine(answers?.experience ?: "", answers?.style ?: "")
    }

    // Recall what the trader actually filled in: their timeframes and style.
    val timeframes = answers?.timeframes?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
        ?: listOf("15M", "4H")
    val timeframeStack = timeframes.joinToString(" · ")
    val styleLine = answers?.style?.takeIf { it.isNotBlank() } ?: "your style"

    val usd = remember { NumberFormat.getCurrencyInstance(java.util.Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PremiumSegmentedProgress(current = 3, total = 4, modifier = Modifier.weight(1f))
            Text(
                "First signal",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "WHAT COMES NEXT · ILLUSTRATIVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = AccentCyan
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Now imagine your next 12 trades",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "with MarketScope AI.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = AccentCyan
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = "Trading $timeframeStack with $styleLine, starting from ${usd.format(userCapital)}:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )

        Spacer(Modifier.height(20.dp))

        ProjectionCard(projection = projection, usd = usd)

        Spacer(Modifier.height(14.dp))

        TradeChips(projection = projection, usd = usd, timeframes = timeframes)

        Spacer(Modifier.height(20.dp))

        FocusCard(timeframes = timeframes)

        Spacer(Modifier.height(28.dp))

        GradientPrimaryButton(
            text = "Analyze Now!",
            enabled = true,
            onClick = { onContinue() },
            showArrow = true,
            shape = RoundedCornerShape(50),
            height = 54.dp
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ProjectionCard(projection: ProjectionEngine.ProjectionResult, usd: NumberFormat) {
    val trades = projection.trades
    val growthLabel = "+${(projection.growthPct * 100).roundToInt()}%"
    val riskPct = if (trades.isNotEmpty()) (trades.first().risk / projection.startingEquity) * 100 else 0.0
    val rMultiple = trades.firstOrNull { it.isWin }?.let { it.pnl / it.risk } ?: 0.0
    val winRatePct = if (trades.isNotEmpty()) trades.count { it.isWin } * 100 / trades.size else 0
    val statsLine = "Risk %.1f%% \u00b7 Target %.1fR \u00b7 Win rate %d%%".format(riskPct, rMultiple, winRatePct)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(ProjectionCardNavy)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Starting", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(2.dp))
                Text(
                    usd.format(projection.startingEquity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text("Projected", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        usd.format(projection.finalEquity),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(BullGreen.copy(alpha = 0.22f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            growthLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BullGreen
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ChartLegendSwatch(color = AccentCyan, dashed = false)
            Spacer(Modifier.width(6.dp))
            Text("With an edge", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = AccentCyan)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChartLegendSwatch(color = Color.White.copy(alpha = 0.4f), dashed = true)
            Spacer(Modifier.width(6.dp))
            Text("Drifting without one", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
        }

        Spacer(Modifier.height(12.dp))

        EquityLineChart(projection = projection, modifier = Modifier.fillMaxWidth().height(130.dp))

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            trades.forEach { trade ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background((if (trade.isWin) BullGreen else BearRed).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (trade.isWin) "W" else "L",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (trade.isWin) BullGreen else BearRed
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            statsLine,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "A pattern, not a prediction \u2014 real outcomes never line up this neatly. " +
                "What it shows is how a small, repeatable edge compounds over time.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChartLegendSwatch(color: Color, dashed: Boolean) {
    Canvas(modifier = Modifier.width(16.dp).height(3.dp)) {
        val y = size.height / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = size.height,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 3f)) else null
        )
    }
}

/**
 * Smooth mountain-style equity chart: the trader's illustrative "with an edge"
 * path (gradient-filled, solid line) against a flat/declining "drifting
 * without one" baseline (dashed) \u2014 replaces the old per-trade bar chart.
 */
@Composable
private fun EquityLineChart(projection: ProjectionEngine.ProjectionResult, modifier: Modifier = Modifier) {
    val edgeColor = AccentCyan
    val fillTop = AccentCyan.copy(alpha = 0.35f)
    val fillBottom = AccentViolet.copy(alpha = 0.02f)
    val driftColor = Color.White.copy(alpha = 0.32f)

    val edgeSeries = remember(projection) {
        listOf(projection.startingEquity) + projection.trades.map { it.equity }
    }
    val driftSeries = remember(projection) {
        val start = projection.startingEquity
        val n = edgeSeries.size
        val denom = (n - 1).coerceAtLeast(1)
        List(n) { i -> start * (1.0 - 0.09 * i / denom) }
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = edgeSeries.size
        val allValues = edgeSeries + driftSeries
        val minV = allValues.min()
        val maxV = allValues.max()
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val denom = (n - 1).coerceAtLeast(1)

        fun pointsFor(series: List<Double>): List<Offset> = series.mapIndexed { i, v ->
            val x = w * i / denom
            val y = h - (h * ((v - minV) / range)).toFloat()
            Offset(x, y)
        }

        // Straight-segment (zigzag) path — matches the reference's angular
        // peaks/valleys rather than a smoothed curve.
        fun smoothPath(points: List<Offset>): Path {
            val path = Path()
            if (points.isEmpty()) return path
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
            return path
        }

        val edgePts = pointsFor(edgeSeries)
        val driftPts = pointsFor(driftSeries)
        val edgePath = smoothPath(edgePts)
        val driftPath = smoothPath(driftPts)

        val fillPath = Path().apply {
            addPath(edgePath)
            lineTo(edgePts.last().x, h)
            lineTo(edgePts.first().x, h)
            close()
        }
        drawPath(fillPath, brush = Brush.verticalGradient(listOf(fillTop, fillBottom)))
        drawPath(
            driftPath,
            color = driftColor,
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))
            )
        )
        drawPath(
            edgePath,
            color = edgeColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun TradeChips(
    projection: ProjectionEngine.ProjectionResult,
    usd: NumberFormat,
    timeframes: List<String>
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(projection.trades) { trade ->
            // Cycle the trader's own chosen timeframes across the 12 days.
            val tf = if (timeframes.isNotEmpty()) timeframes[(trade.index - 1) % timeframes.size] else ""
            val dayLabel = if (tf.isBlank()) "Day ${trade.index}" else "Day ${trade.index} · $tf"
            val bg = if (trade.isWin) BullGreen.copy(alpha = 0.14f) else BearRed.copy(alpha = 0.14f)
            val fg = if (trade.isWin) BullGreen else BearRed
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .padding(12.dp)
            ) {
                Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = (if (trade.pnl >= 0) "+" else "") + usd.format(trade.pnl),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
                Spacer(Modifier.height(6.dp))
                Text("Risk: ${usd.format(trade.risk)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Text("Equity: ${usd.format(trade.equity)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
        }
    }
}

private data class FocusItem(val icon: ImageVector, val title: String, val desc: String)

@Composable
private fun FocusCard(timeframes: List<String>) {
    val stack = timeframes.joinToString(" \u00b7 ")
    val items = listOf(
        FocusItem(
            Icons.Filled.Psychology,
            "Balanced psychology",
            "One fixed risk per trade, stop after 2 straight losses, no revenge trades."
        ),
        FocusItem(
            Icons.Filled.ShowChart,
            "Sharper chart reading",
            "Work your $stack stack. Mark levels pre-session \u2014 no trigger, no trade."
        ),
        FocusItem(
            Icons.Filled.QueryStats,
            "Real metrics tracking",
            "Log every idea with screenshots, R-multiple, and notes; a 20-minute weekly review."
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceLight)
            .padding(16.dp)
    ) {
        Text(
            "Your 1-Month Focus",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { i, item ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(AccentCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(item.desc, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            if (i != items.lastIndex) Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "One signal won\u2019t change your results \u2014 twelve disciplined trades will start to. " +
                "Keep the momentum.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}
