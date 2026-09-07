package com.veltravia.marketscopeai.ui.screens

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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

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
 * live from the backend's /api/trending (CoinGecko), with a genuine 7-day
 * sparkline drawn from actual price history. No mock rows, no fake charts.
 */
@Composable
fun TrendingSection() {
    var tokens by remember { mutableStateOf<List<TrendingToken>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { ApiClient.fetchTrending() }
            .onSuccess { tokens = parseTokens(it) }
            .onFailure { error = it.message ?: "Could not load trending tokens" }
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
                    TrendingRow(token)
                    if (index != tokens!!.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(color = TextMuted.copy(alpha = 0.12f), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendingRow(token: TrendingToken) {
    val up = (token.change24h ?: 0.0) >= 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* real-time detail view is a future upgrade; row is informational for now */ }
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
            points = token.sparkline,
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

/** A genuine mini line chart from a real price series — no decoration, no fake curve. */
@Composable
private fun Sparkline(points: List<Float>, positive: Boolean, modifier: Modifier = Modifier) {
    val color = if (positive) BullGreen else BearRed
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
    }
}
