package com.veltravia.marketai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.domain.ProjectionEngine
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.AccentViolet
import com.veltravia.marketai.ui.theme.BearRed
import com.veltravia.marketai.ui.theme.BullGreen
import com.veltravia.marketai.ui.theme.NavyBlack
import com.veltravia.marketai.ui.theme.SurfaceLight
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextSecondary
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Illustrative "next 12 trades" consistency pitch shown once, right after the
 * notifications soft-ask. Uses the trader's real capital/experience/style from
 * the questionnaire (already completed earlier in onboarding). All numbers are
 * computed live by ProjectionEngine — this is MarketScope AI's own math, not hardcoded copy.
 */
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
        Spacer(Modifier.height(28.dp))

        Text(
            text = "Now imagine your next 12 trades with MarketScope AI",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(listOf(AccentViolet, AccentCyan))
                )
                .clickable { onContinue() }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Analyze Now!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NavyBlack
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ProjectionCard(projection: ProjectionEngine.ProjectionResult, usd: NumberFormat) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(AccentViolet, AccentCyan)))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "12-Trade Projection (illustrative)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                "Target \u2265 ${(projection.targetPct * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ProjectionStat("Starting", usd.format(projection.startingEquity))
            ProjectionStat("Projected", usd.format(projection.finalEquity))
            ProjectionStat("Growth", "${(projection.growthPct * 100).roundToInt()}%")
        }

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.95f))
                .padding(12.dp)
        ) {
            Text(
                "Potential Gain",
                style = MaterialTheme.typography.labelSmall,
                color = NavyBlack.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(2.dp))
            val gain = projection.finalEquity - projection.startingEquity
            Text(
                text = "+${usd.format(gain)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = BullGreen
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Equity path across 12 disciplined trades",
                style = MaterialTheme.typography.labelSmall,
                color = NavyBlack.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(8.dp))
            EquityBars(projection)
            Spacer(Modifier.height(8.dp))

            val riskPctLabel = if (projection.trades.isNotEmpty()) {
                val pct = (projection.trades.first().risk / projection.startingEquity) * 100
                "Risk/Trade: %.1f%%".format(pct)
            } else "Risk/Trade: —"
            val winRateLabel = if (projection.trades.isNotEmpty()) {
                val wins = projection.trades.count { it.isWin }
                "Win-rate: ${(wins * 100 / projection.trades.size)}%"
            } else "Win-rate: —"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(riskPctLabel, style = MaterialTheme.typography.labelSmall, color = NavyBlack.copy(alpha = 0.55f))
                Text(winRateLabel, style = MaterialTheme.typography.labelSmall, color = NavyBlack.copy(alpha = 0.55f))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "This is a graphical illustration — not the exact order of outcomes. " +
                    "It shows how small, repeatable edges can compound.",
                style = MaterialTheme.typography.labelSmall,
                color = NavyBlack.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun EquityBars(projection: ProjectionEngine.ProjectionResult) {
    val equities = projection.trades.map { it.equity }
    val minE = minOf(projection.startingEquity, equities.minOrNull() ?: projection.startingEquity)
    val maxE = equities.maxOrNull() ?: projection.finalEquity
    val range = (maxE - minE).takeIf { it > 0.0 } ?: 1.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        projection.trades.forEach { trade ->
            val fraction = ((trade.equity - minE) / range).toFloat().coerceIn(0.12f, 1f)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height((70 * fraction).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (trade.isWin) BullGreen else BearRed.copy(alpha = 0.8f))
            )
        }
    }
}

@Composable
private fun ProjectionStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
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
