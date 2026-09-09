package com.veltravia.marketscopeai.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

private const val LIVE_QUOTE_POLL_MS = 6500L
private const val FULL_REFRESH_MS = 3 * 60_000L
private const val LIVE_TAIL_MAX_POINTS = 40

private data class TrendingToken(
    val symbol: String,
    val name: String,
    val image: String,
    val price: Double,
    val marketCap: Double,
    val volume24h: Double,
    val change24h: Double?,
    val sparkline: List<Float>
)

private fun parseTokens(arr: JSONArray): List<TrendingToken> {
    val out = mutableListOf<TrendingToken>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val spark = o.optJSONArray("sparkline")
        val points = mutableListOf<Float>()
        if (spark != null) {
            for (j in 0 until spark.length()) points.add(spark.optDouble(j).toFloat())
        }
        out.add(
            TrendingToken(
                symbol = o.optString("symbol"),
                name = o.optString("name"),
                image = o.optString("image"),
                price = o.optDouble("price"),
                marketCap = o.optDouble("marketCap"),
                volume24h = o.optDouble("volume24h"),
                change24h = if (o.isNull("change24h")) null else o.optDouble("change24h"),
                sparkline = points
            )
        )
    }
    return out
}

private fun formatCompact(v: Double): String {
    if (!v.isFinite() || v <= 0) return "—"
    return when {
        v >= 1_000_000_000 -> "$${"%.2f".format(v / 1_000_000_000)}B"
        v >= 1_000_000 -> "$${"%.2f".format(v / 1_000_000)}M"
        v >= 1_000 -> "$${"%.2f".format(v / 1_000)}K"
        else -> "$${"%.2f".format(v)}"
    }
}

private fun formatPrice(v: Double): String {
    if (!v.isFinite()) return "—"
    return when {
        v >= 1 -> "$${"%,.2f".format(v)}"
        v >= 0.01 -> "$${"%.4f".format(v)}"
        else -> "$${"%.6f".format(v)}"
    }
}

/**
 * Real "Trending" section for the Home screen — top coins by market cap,
 * live from the backend's /api/trending (CoinGecko/Coinbase/Binance), with
 * a genuine 7-day sparkline. On top of that base snapshot, this section
 * polls real spot quotes every ~6.5s (GET /api/trending/quotes — Coinbase
 * first, Binance fallback) and appends each real tick to a live tail drawn
 * on the sparkline's right edge, so the chart and price genuinely move in
 * real time — no simulated/random movement, no placeholders. The full
 * snapshot (market cap, volume, 24h change, base sparkline) itself
 * refreshes every 3 minutes, matching the backend's cache window.
 */
@Composable
fun TrendingSection(onOpenMarket: (String) -> Unit) {
    var tokens by remember { mutableStateOf<List<TrendingToken>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val liveTails = remember { mutableStateMapOf<String, List<Float>>() }

    // Full snapshot: fetched immediately, then refreshed every 3 minutes.
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { ApiClient.fetchTrending() }
                .onSuccess {
                    tokens = parseTokens(it)
                    error = null
                }
                .onFailure { if (tokens == null) error = it.message ?: "Could not load trending tokens" }
            delay(FULL_REFRESH_MS)
        }
    }

    // Live tick: as soon as we have a token list, poll real spot quotes on a
    // short interval and append each real price to that token's live tail.
    val symbolsKey = tokens?.map { it.symbol } ?: emptyList()
    LaunchedEffect(symbolsKey) {
        if (symbolsKey.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(LIVE_QUOTE_POLL_MS)
            val quotes = runCatching { ApiClient.fetchTrendingQuotes(symbolsKey) }.getOrNull() ?: continue
            if (quotes.isEmpty()) continue
            tokens = tokens?.map { t ->
                val q = quotes[t.symbol]
                if (q != null && q > 0.0) {
                    liveTails[t.symbol] = ((liveTails[t.symbol] ?: emptyList()) + q.toFloat()).takeLast(LIVE_TAIL_MAX_POINTS)
                    t.copy(price = q)
                } else t
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = BullGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "Trending",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (tokens != null && error == null) {
                Spacer(Modifier.width(8.dp))
                LiveIndicator()
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            error != null -> Text(
                "Trending is temporarily unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            tokens == null -> Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = BullGreen, strokeWidth = 2.dp)
            }
            tokens!!.isEmpty() -> Text(
                "No trending data right now.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceLight)
            ) {
                tokens!!.forEachIndexed { index, token ->
                    TrendingRow(
                        token = token,
                        liveTail = liveTails[token.symbol] ?: emptyList(),
                        onClick = { onOpenMarket(token.symbol.lowercase() + "usd") }
                    )
                    if (index != tokens!!.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(color = TextMuted.copy(alpha = 0.12f), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

/** Small pulsing dot + "LIVE" label — an honest signal that quotes are polling in real time. */
@Composable
private fun LiveIndicator() {
    val transition = rememberInfiniteTransition(label = "live-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live-pulse-alpha"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(BullGreen.copy(alpha = alpha))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "LIVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = BullGreen.copy(alpha = alpha)
        )
    }
}

@Composable
private fun TrendingRow(token: TrendingToken, liveTail: List<Float>, onClick: () -> Unit) {
    val up = (token.change24h ?: 0.0) >= 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = token.image,
            contentDescription = token.name,
            modifier = Modifier.size(34.dp).clip(CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(token.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                "${formatCompact(token.marketCap)} MCap · ${formatCompact(token.volume24h)} Vol",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Sparkline(
            basePoints = token.sparkline,
            liveTail = liveTail,
            positive = up,
            modifier = Modifier.size(width = 56.dp, height = 28.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(formatPrice(token.price), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            token.change24h?.let {
                Text(
                    "${if (up) "+" else "-"}${"%.2f".format(abs(it))}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (up) BullGreen else BearRed
                )
            }
        }
    }
}

/**
 * A genuine mini line chart: the 7-day base series plus, appended at the
 * right edge, the real live-quote ticks polled since this row appeared —
 * the same idea as a live market chart's moving edge, built from real
 * prices only.
 */
@Composable
private fun Sparkline(basePoints: List<Float>, liveTail: List<Float>, positive: Boolean, modifier: Modifier = Modifier) {
    val color = if (positive) BullGreen else BearRed
    val points = if (liveTail.isEmpty()) basePoints else basePoints + liveTail
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val min = points.min()
        val max = points.max()
        val range = (max - min).let { if (it == 0f) 1f else it }
        val stepX = size.width / (points.size - 1)
        var prev: Offset? = null
        for (i in points.indices) {
            val x = i * stepX
            val y = size.height - ((points[i] - min) / range) * size.height
            val curr = Offset(x, y)
            prev?.let { p ->
                drawLine(color = color, start = p, end = curr, strokeWidth = 2.5f, cap = StrokeCap.Round)
            }
            prev = curr
        }
        if (liveTail.isNotEmpty()) {
            // A small live dot at the moving edge, like a real-time ticker.
            val lastIndex = points.lastIndex
            val x = lastIndex * stepX
            val y = size.height - ((points[lastIndex] - min) / range) * size.height
            drawCircle(color = color, radius = 3f, center = Offset(x, y))
        }
    }
}
