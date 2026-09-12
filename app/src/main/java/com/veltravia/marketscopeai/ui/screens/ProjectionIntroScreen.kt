package com.veltravia.marketscopeai.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.Dp
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
 * hardcoded copy. The card is a deep-navy gradient surface with a zigzag equity
 * chart (solid "with an edge" line vs dashed "drifting without one" baseline,
 * plus a hairline at the starting level and a glowing end dot), an animated
 * count-up on the projected balance, a W/L chip strip and a stats caption —
 * drawn with our own cyan/violet accents.
 */

// Deep-navy card palette for this screen only (scoped here so the shared
// DarkInk constant used elsewhere stays untouched).
private val ProjectionCardTop = Color(0xFF0D1526)
private val ProjectionCardBottom = Color(0xFF12233A)
private val ProjectionCardBorder = Color(0xFF1C3050)
private val CardLabel = Color(0xFF7E93B1)
private val CardValue = Color(0xFFC7D5EA)
private val CardStats = Color(0xFF8EA3C0)
private val CardDot = Color(0xFF33496B)
private val CardDisclaimer = Color(0xFF63779A)
private val DriftGray = Color(0xFF64748B)
private val BaselineNavy = Color(0xFF26405A)
private val ChipLossSoft = Color(0xFFFB7185)

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

    // Count-up on the projected balance: eased 600ms roll from zero to the
    // projected figure, mirroring the "number lands" feel of the reference.
    val projectedValue = remember { Animatable(0f) }
    LaunchedEffect(projection.finalEquity) {
        projectedValue.animateTo(
            targetValue = projection.finalEquity.toFloat(),
            animationSpec = tween(durationMillis = 600, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(ProjectionCardTop, ProjectionCardBottom)))
            .border(1.dp, ProjectionCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Starting", style = MaterialTheme.typography.labelSmall, color = CardLabel)
                Spacer(Modifier.height(2.dp))
                Text(
                    usd.format(projection.startingEquity),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = CardValue
                )
            }
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(16.dp)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text("Projected", style = MaterialTheme.typography.labelSmall, color = CardLabel)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        usd.format(projectedValue.value.roundToInt()),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AccentCyan.copy(alpha = 0.14f))
                            .border(1.dp, AccentCyan.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            growthLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth().height(158.dp)) {
            EquityLineChart(projection = projection, modifier = Modifier.fillMaxSize())
            // Legend overlaid inside the chart's top-left corner, not a row of
            // its own — keeps the whole card visual inside one frame.
            Column(modifier = Modifier.padding(start = 2.dp, top = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChartLegendSwatch(color = AccentCyan, dashed = false, width = 14.dp, height = 3.dp)
                    Spacer(Modifier.width(5.dp))
                    Text("With an edge", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChartLegendSwatch(color = DriftGray, dashed = true, width = 14.dp, height = 2.dp)
                    Spacer(Modifier.width(5.dp))
                    Text("Drifting without one", fontSize = 9.sp, color = CardStats)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            trades.forEach { trade ->
                val chipColor = if (trade.isWin) BullGreen else ChipLossSoft
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(chipColor.copy(alpha = if (trade.isWin) 0.16f else 0.10f))
                        .border(
                            1.dp,
                            chipColor.copy(alpha = if (trade.isWin) 0.40f else 0.35f),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (trade.isWin) "W" else "L",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = chipColor
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Risk %.1f%%".format(riskPct),
                style = MaterialTheme.typography.labelSmall,
                color = CardStats
            )
            Box(Modifier.size(3.dp).background(CardDot, CircleShape))
            Text(
                "Target %.1fR".format(rMultiple),
                style = MaterialTheme.typography.labelSmall,
                color = CardStats
            )
            Box(Modifier.size(3.dp).background(CardDot, CircleShape))
            Text(
                "Win rate %d%%".format(winRatePct),
                style = MaterialTheme.typography.labelSmall,
                color = CardStats
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "A pattern, not a prediction — real outcomes never line up this neatly. " +
                "What it shows is how a small, repeatable edge compounds over time.",
            style = MaterialTheme.typography.labelSmall,
            color = CardDisclaimer,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChartLegendSwatch(color: Color, dashed: Boolean, width: Dp = 16.dp, height: Dp = 3.dp) {
    Canvas(modifier = Modifier.width(width).height(height)) {
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
 * Zigzag equity chart: the trader's illustrative "with an edge" path
 * (gradient-filled, solid line, glowing end dot) against a declining
 * "drifting without one" dashed line, with a hairline marking the starting
 * balance level.
 */
@Composable
private fun EquityLineChart(projection: ProjectionEngine.ProjectionResult, modifier: Modifier = Modifier) {
    val edgeColor = AccentCyan
    val fillTop = AccentCyan.copy(alpha = 0.45f)
    val fillBottom = AccentCyan.copy(alpha = 0.0f)
    val driftColor = DriftGray.copy(alpha = 0.55f)

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

        // Leave breathing room above and below the plotted area so the glow
        // dot and the dashed baseline are never clipped.
        val topPad = h * 0.10f
        val usable = h - 2f * topPad

        fun pointsFor(series: List<Double>): List<Offset> = series.mapIndexed { i, v ->
            val x = w * i / denom
            val y = topPad + usable * (1f - ((v - minV) / range).toFloat())
            Offset(x, y)
        }

        // Straight-segment (zigzag) path — angular peaks/valleys, no smoothing.
        fun zigzagPath(points: List<Offset>): Path {
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
        val edgePath = zigzagPath(edgePts)
        val driftPath = zigzagPath(driftPts)

        // Dashed hairline at the starting-balance level.
        drawLine(
            color = BaselineNavy,
            start = Offset(0f, edgePts.first().y),
            end = Offset(w, edgePts.first().y),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
        )
        // "Drifting without one" comparison line + its fading end dot.
        drawPath(
            driftPath,
            color = driftColor,
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))
            )
        )
        drawCircle(
            color = DriftGray.copy(alpha = 0.6f),
            radius = 2.5.dp.toPx(),
            center = driftPts.last()
        )
        // Gradient area under the edge line.
        val fillPath = Path().apply {
            addPath(edgePath)
            lineTo(edgePts.last().x, h)
            lineTo(edgePts.first().x, h)
            close()
        }
        drawPath(fillPath, brush = Brush.verticalGradient(listOf(fillTop, fillBottom)))
        // The edge line itself.
        drawPath(
            edgePath,
            color = edgeColor,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Glowing end dot: soft halo + solid core at the final equity point.
        drawCircle(
            color = edgeColor.copy(alpha = 0.2f),
            radius = 7.dp.toPx(),
            center = edgePts.last()
        )
        drawCircle(
            color = edgeColor,
            radius = 3.dp.toPx(),
            center = edgePts.last()
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
    val stack = timeframes.joinToString(" · ")
    val items = listOf(
        FocusItem(
            Icons.Filled.Psychology,
            "Balanced psychology",
            "Risk the same fixed size every time. Two losses in a row? Step away — never trade to win it back."
        ),
        FocusItem(
            Icons.Filled.ShowChart,
            "Sharper chart reading",
            "Mark your levels on the $stack stack before the session. No trigger means no trade."
        ),
        FocusItem(
            Icons.Filled.QueryStats,
            "Real metrics tracking",
            "Screenshot each idea with its R-multiple and a note, then give the week 20 minutes of review."
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
            "No single setup defines a trader — a dozen disciplined ones start to.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}
