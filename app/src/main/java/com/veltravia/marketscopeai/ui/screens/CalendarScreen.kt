package com.veltravia.marketscopeai.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val category: String,
    val imageUrl: String?,
    val publishedAt: Long?
)

private data class CalendarEvent(
    val title: String,
    val country: String,
    val impact: String,
    val forecast: String?,
    val previous: String?,
    val actual: String?,
    val timestamp: Long
)

/** UTC ISO-8601 (…:ss.SSSZ) → epoch millis. */
private val utcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

private fun timeAgo(publishedAt: Long?): String? {
    if (publishedAt == null || publishedAt <= 0L) return null
    val mins = (System.currentTimeMillis() - publishedAt) / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        else -> "${mins / (60 * 24)}d ago"
    }
}

/**
 * Calendar — the app's real market-intelligence hub, replacing the old
 * placeholder "Journal" tile. Two tabs:
 *
 *   News      : live Forex / Crypto / Stocks headlines aggregated server-side
 *               from Investing.com, Cointelegraph and Yahoo Finance — every
 *               card opens the publisher's article in the browser.
 *   Events    : the real economic calendar (NFP, CPI, rate decisions …)
 *               from TradingView's public feed with impact level, forecast,
 *               previous and actual values, grouped by day, shown in the
 *               device's local time.
 *
 * No fabricated content anywhere: if a feed is down the server says so and
 * this screen shows an honest error + retry.
 */
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = News, 1 = Economic calendar
    var news by remember { mutableStateOf<List<NewsItem>?>(null) }
    var newsError by remember { mutableStateOf<String?>(null) }
    var newsCategory by remember { mutableStateOf("all") }
    var events by remember { mutableStateOf<List<CalendarEvent>?>(null) }
    var eventsError by remember { mutableStateOf<String?>(null) }
    var impactFilter by remember { mutableStateOf("all") }

    fun loadNews(category: String) {
        newsError = null
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val arr = ApiClient.fetchMarketNews(category, 40)
                news = buildList {
                    for (i in 0 until arr.length()) {
                        val n = arr.optJSONObject(i) ?: continue
                        add(
                            NewsItem(
                                title = n.optString("title"),
                                link = n.optString("link"),
                                source = n.optString("source"),
                                category = n.optString("category"),
                                imageUrl = if (n.isNull("imageUrl")) null else n.optString("imageUrl"),
                                publishedAt = try { utcParser.parse(n.optString("publishedAt"))?.time } catch (_: Exception) { null }
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                newsError = e.message ?: "Could not load market news"
            }
        }
    }

    fun loadEvents() {
        eventsError = null
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val arr = ApiClient.fetchEconomicCalendar()
                events = buildList {
                    for (i in 0 until arr.length()) {
                        val e = arr.optJSONObject(i) ?: continue
                        add(
                            CalendarEvent(
                                title = e.optString("title"),
                                country = e.optString("country"),
                                impact = e.optString("impact", "Low"),
                                forecast = if (e.isNull("forecast")) null else e.optString("forecast"),
                                previous = if (e.isNull("previous")) null else e.optString("previous"),
                                actual = if (e.isNull("actual")) null else e.optString("actual"),
                                timestamp = try { utcParser.parse(e.optString("timestamp"))?.time ?: 0L } catch (_: Exception) { 0L }
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                eventsError = e.message ?: "Could not load the economic calendar"
            }
        }
    }

    LaunchedEffect(Unit) {
        loadNews("all")
        loadEvents()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Calendar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(Modifier.height(8.dp))

        // --- Segmented control: News | Economic calendar ---
        SegmentedControl(
            options = listOf("News", "Economic calendar"),
            selected = selectedTab,
            onSelect = { selectedTab = it }
        )
        Spacer(Modifier.height(14.dp))

        if (selectedTab == 0) {
            // ---------- NEWS ----------
            CategoryChips(
                options = listOf("all" to "All", "forex" to "Forex", "crypto" to "Crypto", "stocks" to "Stocks"),
                selected = newsCategory,
                onSelect = { cat ->
                    if (cat != newsCategory) {
                        newsCategory = cat
                        news = null // honest skeleton: a new feed is loading
                        loadNews(cat)
                    }
                }
            )
            Spacer(Modifier.height(6.dp))

            when {
                news == null && newsError == null -> NewsSkeleton()
                newsError != null -> ErrorState(newsError ?: "", onRetry = { loadNews(newsCategory) })
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(news.orEmpty()) { item ->
                        NewsCard(item = item, onOpen = {
                            // Open the publisher's article in the real browser.
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(item.link)))
                        })
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        } else {
            // ---------- ECONOMIC CALENDAR ----------
            CategoryChips(
                options = listOf("all" to "All", "high" to "High impact", "medium" to "Medium", "low" to "Low"),
                selected = impactFilter,
                onSelect = { impactFilter = it } // client-side filter — data already loaded
            )
            Spacer(Modifier.height(6.dp))

            when {
                events == null && eventsError == null -> EventsSkeleton()
                eventsError != null -> ErrorState(eventsError ?: "", onRetry = { loadEvents() })
                else -> {
                    val filtered = events.orEmpty().filter { ev ->
                        impactFilter == "all" || ev.impact.lowercase() == impactFilter
                    }
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text("No ${impactFilter.replaceFirstChar { it.uppercase() }}-impact events scheduled this week.", color = TextSecondary)
                        }
                    } else {
                        EventsList(filtered)
                    }
                }
            }
        }
    }
}

/** Grouped, day-headed list of economic events (device-local time). */
@Composable
private fun EventsList(events: List<CalendarEvent>) {
    val dayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val dayLabel = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val todayKey = dayKey.format(Date())
    val tomorrowKey = dayKey.format(Date(System.currentTimeMillis() + 24 * 3600_000L))

    val groups = events.groupBy { dayKey.format(Date(it.timestamp)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { (key, dayEvents) ->
            item(key = "header-$key") {
                val label = when (key) {
                    todayKey -> "Today"
                    tomorrowKey -> "Tomorrow"
                    else -> dayLabel.format(Date(dayEvents.first().timestamp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }
            items(dayEvents, key = { "${it.timestamp}-${it.title}" }) { ev ->
                EventRow(ev = ev, timeFmt = timeFmt)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventRow(ev: CalendarEvent, timeFmt: SimpleDateFormat) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isPast = ev.timestamp < System.currentTimeMillis()
    val (dotColor, dotLabel) = when (ev.impact.lowercase()) {
        "high" -> BearRed to "High"
        "medium" -> GoldAmber to "Med"
        "holiday" -> AccentCyan to "Holiday"
        else -> Color(0xFFAAB3C5) to "Low"
    }
    val values = buildList {
        ev.actual?.let { add("A $it") }
        ev.forecast?.let { add("F $it") }
        ev.previous?.let { add("P $it") }
    }.joinToString("  ·  ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF3F4F7))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            timeFmt.format(Date(ev.timestamp)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (isPast) TextMuted else TextPrimary
        )
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E5EC), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                ev.country,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                ev.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isPast) TextMuted else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (values.isNotEmpty()) {
                Text(
                    values,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                dotLabel,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .combinedClickable(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, buildEventShareText(ev, timeFmt))
                        }
                        context.startActivity(Intent.createChooser(send, "Reshare event"))
                    },
                    onLongClick = {
                        clipboardManager.setText(AnnotatedString(buildEventShareText(ev, timeFmt)))
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Share, contentDescription = "Reshare event", tint = TextMuted, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
/** News reshare text — Android share sheet on tap, clipboard on long-press. */
private fun buildNewsShareText(item: NewsItem): String =
    "\uD83D\uDCF0 ${item.title}\n\n${item.source} — read it here:\n${item.link}\n\nvia MarketScope AI"

/** Economic-event reshare text — share sheet on tap, clipboard on long-press. */
private fun buildEventShareText(ev: CalendarEvent, timeFmt: SimpleDateFormat): String {
    val impactWord = ev.impact.replaceFirstChar { it.uppercase() }
    val sb = StringBuilder("\uD83D\uDCC5 MarketScope AI Economic Calendar\n")
    sb.append(ev.title).append(" (").append(ev.country).append(")\n")
    sb.append(timeFmt.format(Date(ev.timestamp))).append(" · ").append(impactWord).append(" impact")
    if (ev.forecast != null || ev.previous != null) {
        sb.append("\n")
        ev.forecast?.let { sb.append("Forecast: ").append(it).append("  ") }
        ev.previous?.let { sb.append("Previous: ").append(it) }
    }
    sb.append("\n\nvia MarketScope AI")
    return sb.toString()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewsCard(item: NewsItem, onOpen: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val categoryColor = when (item.category) {
        "forex" -> AccentViolet
        "crypto" -> GoldAmber
        else -> AccentCyan
    }
    val categoryLabel = when (item.category) {
        "forex" -> "FOREX"
        "crypto" -> "CRYPTO"
        else -> "STOCKS"
    }
    val ago = timeAgo(item.publishedAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF3F4F7))
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(categoryColor.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = categoryColor
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    item.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (ago != null) {
                    Text("  ·  $ago", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.imageUrl != null) {
            Spacer(Modifier.width(12.dp))
            coil.compose.AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .combinedClickable(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, buildNewsShareText(item))
                        }
                        context.startActivity(Intent.createChooser(send, "Reshare news"))
                    },
                    onLongClick = {
                        clipboardManager.setText(AnnotatedString(buildNewsShareText(item)))
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Share, contentDescription = "Reshare news", tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEAECF1))
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) Color.White else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CategoryChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (id, label) ->
            val isSelected = id == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) AccentCyan.copy(alpha = 0.10f) else Color(0xFFF3F4F7))
                    .border(
                        1.dp,
                        if (isSelected) AccentCyan.copy(alpha = 0.4f) else Color(0xFFE2E5EC),
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) AccentCyan else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry", fontWeight = FontWeight.Bold, color = AccentCyan)
        }
    }
}

/** Skeleton matching the real news-card layout so nothing jumps on arrival. */
@Composable
private fun NewsSkeleton() {
    val brush = rememberShimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF3F4F7))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(Modifier.width(110.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(240.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.width(150.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(76.dp).clip(RoundedCornerShape(10.dp)).background(brush))
            }
        }
    }
}

/** Skeleton matching the day-grouped event rows. */
@Composable
private fun EventsSkeleton() {
    val brush = rememberShimmerBrush()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(90.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(brush))
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF3F4F7))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(44.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(30.dp).clip(CircleShape).background(brush))
                Spacer(Modifier.width(12.dp))
                Column {
                    Box(Modifier.width(200.dp).height(15.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.width(120.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                }
            }
        }
    }
}
