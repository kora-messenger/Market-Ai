package com.veltravia.marketscopeai.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import org.json.JSONArray
import kotlin.math.abs

private const val WATCHLIST_POLL_MS = 8000L
private const val FLASH_DECAY_MS = 700

private data class WatchRow(
    val section: String,
    val id: String,
    val display: String,
    val subtitle: String,
    val price: Double?,
    val changePct: Double?
)

private fun parseWatchlist(arr: JSONArray): List<WatchRow> {
    val out = mutableListOf<WatchRow>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(
            WatchRow(
                section = o.optString("section"),
                id = o.optString("id"),
                display = o.optString("display"),
                subtitle = o.optString("subtitle"),
                price = if (o.isNull("price")) null else o.optDouble("price"),
                changePct = if (o.isNull("changePct")) null else o.optDouble("changePct")
            )
        )
    }
    return out
}

private fun formatWatchPrice(id: String, v: Double): String {
    val decimals = when {
        id.startsWith("usdjpy") || id.endsWith("jpy") -> 3
        id.length == 6 && id.all { it.isLetter() } -> 5 // forex pairs
        v >= 1000 -> 2
        v >= 1 -> 4
        else -> 6
    }
    return "%,.${decimals}f".format(v)
}

/**
 * Always-live multi-asset Watchlist — Futures (Gold/Silver), Forex majors,
 * and Crypto — grouped exactly like a real trading watchlist. Polls the
 * backend's GET /api/markets/watchlist every ~8s; every price shown is a
 * genuine live quote from src/prices.js's real feeds (Yahoo/Frankfurter/
 * CoinGecko/Binance/Stooq). Each row flashes green/red on its own real tick,
 * so market movement is always visibly tracked — never simulated.
 */
@Composable
fun MarketsWatchlistSection(onOpenMarket: (String) -> Unit) {
    var rows by remember { mutableStateOf<List<WatchRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedSections by remember { mutableStateOf(setOf("Futures", "Forex", "Crypto")) }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching { ApiClient.fetchMarketsWatchlist() }
                .onSuccess { rows = parseWatchlist(it); error = null }
                .onFailure { if (rows == null) error = it.message ?: "Could not load live market data" }
            delay(WATCHLIST_POLL_MS)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Watchlist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (rows != null && error == null) {
                Spacer(Modifier.width(8.dp))
                LiveIndicator()
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            error != null -> Text(
                "Live market data is temporarily unavailable.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            rows == null -> Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = AccentCyan, strokeWidth = 2.dp)
            }
            else -> {
                val bySection = rows!!.groupBy { it.section }.toList().sortedBy {
                    when (it.first) { "Futures" -> 0; "Forex" -> 1; "Crypto" -> 2; else -> 3 }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceLight)
                ) {
                    bySection.forEachIndexed { sIdx, (section, items) ->
                        val expanded = section in expandedSections
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                section,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    expandedSections = if (expanded) expandedSections - section else expandedSections + section
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) "Collapse $section" else "Expand $section",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (expanded) {
                            items.forEachIndexed { i, row ->
                                WatchlistRow(row) { onOpenMarket(row.id) }
                                if (i != items.lastIndex) {
                                    HorizontalDivider(color = TextMuted.copy(alpha = 0.10f), thickness = 1.dp)
                                }
                            }
                        }
                        if (sIdx != bySection.lastIndex) {
                            HorizontalDivider(color = TextMuted.copy(alpha = 0.16f), thickness = 1.dp)
                        }
                    }
                }
            }
        }
    }
}

/** Small pulsing dot + "LIVE" label — an honest signal that quotes are polling in real time. */
@Composable
private fun LiveIndicator() {
    val transition = rememberInfiniteTransition(label = "watchlist-live-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "watchlist-live-pulse-alpha"
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
private fun WatchlistRow(row: WatchRow, onOpen: () -> Unit) {
    // Remembers the previous real price so each row can flash on its own
    // genuine tick — no shared/simulated animation, purely reactive to data.
    var lastPrice by remember(row.id) { mutableStateOf(row.price) }
    var flashUp by remember(row.id) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(row.price) {
        val prev = lastPrice
        val curr = row.price
        if (prev != null && curr != null && curr != prev) {
            flashUp = curr > prev
            lastPrice = curr
            delay(FLASH_DECAY_MS.toLong())
            flashUp = null
        } else if (curr != null) {
            lastPrice = curr
        }
    }

    val flashColor by animateColorAsState(
        targetValue = when (flashUp) {
            true -> BullGreen.copy(alpha = 0.12f)
            false -> BearRed.copy(alpha = 0.12f)
            null -> Color.Transparent
        },
        animationSpec = tween(FLASH_DECAY_MS),
        label = "row-flash"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(flashColor)
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.display, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(row.subtitle, style = MaterialTheme.typography.labelSmall, color = TextMuted, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                row.price?.let { formatWatchPrice(row.id, it) } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            row.changePct?.let {
                val up = it >= 0
                Text(
                    "${if (up) "+" else "-"}${"%.2f".format(abs(it))}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (up) BullGreen else BearRed
                )
            } ?: Text("0.00%", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
    }
}
