package com.veltravia.marketscopeai.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Trade Journal — the trader's REAL trade record, closing the loop the
 * research identified: analysis -> actual trade -> outcome -> lesson.
 * Everything on this screen comes from the signed-in user's own journal
 * via GET /api/journal; the stats are computed server-side from their own
 * numbers (a closed trade without R or P&L is never silently counted as
 * a win or a loss). No demo rows anywhere: an empty journal shows an
 * honest empty state.
 */
@Composable
fun JournalScreen(
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var entries by remember { mutableStateOf<List<JSONObject>?>(null) }
    var stats by remember { mutableStateOf<JSONObject?>(null) }

    fun load() {
        loading = entries == null
        error = null
        scope.launch {
            try {
                val json = ApiClient.fetchJournal(token)
                val arr = json.optJSONArray("entries") ?: org.json.JSONArray()
                entries = buildList {
                    for (i in 0 until arr.length()) add(arr.optJSONObject(i) ?: continue)
                }
                stats = json.optJSONObject("stats")
            } catch (e: Exception) {
                error = e.message ?: "Could not load the journal."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Trade Journal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(error ?: "", color = BearRed, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { load() }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
            entries.isNullOrEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Book, contentDescription = null, tint = SurfaceLight, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No trades logged yet",
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Log your real trades after you take them. Over time this shows what actually works for you — your win rate, average R, and the lessons worth keeping.",
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { onOpenEntry("") }, colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Log your first trade")
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp)
                ) {
                    item {
                        JournalStatsCard(stats)
                        Spacer(Modifier.height(14.dp))
                    }
                    items(entries.orEmpty()) { entry -> JournalEntryRow(entry) { onOpenEntry(entry.optString("id")) } }
                }
            }
        }
    }

    // Persistent "Log a trade" button (hidden in the empty state, which has
    // its own larger call to action).
    if (!loading && error == null && !entries.isNullOrEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.BottomCenter) {
            Button(
                onClick = { onOpenEntry("") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Log a trade")
            }
        }
    }
}

@Composable
private fun JournalStatsCard(stats: JSONObject?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLight)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your record", fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            val open = stats?.optInt("openTrades", 0) ?: 0
            if (open > 0) {
                Text(
                    "$open open",
                    color = GoldAmber,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            StatTile("Closed", stats?.opt("closedTrades")?.toString() ?: "0")
            StatTile(
                "Win rate",
                stats?.opt("winRate")?.let { if (it != JSONObject.NULL) "$it%" else "—" } ?: "—"
            )
            StatTile(
                "Avg R",
                stats?.opt("avgR")?.let { if (it != JSONObject.NULL) it.toString() else "—" } ?: "—"
            )
            StatTile(
                "Net P&L",
                stats?.opt("totalPnl")?.let { if (it != JSONObject.NULL) it.toString() else "—" } ?: "—"
            )
        }
        val decided = (stats?.optInt("wins", 0) ?: 0) + (stats?.optInt("losses", 0) ?: 0)
        if (decided == 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Close a trade by adding its exit price — wins, losses and R are only counted from your own numbers.",
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(2.dp))
        Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun JournalEntryRow(entry: JSONObject, onClick: () -> Unit) {
    val closed = entry.optString("status") == "closed"
    val direction = entry.optString("direction")
    val rVal = entry.opt("rMultiple")?.let { if (it != JSONObject.NULL) (it as? Double) ?: it.toString().toDoubleOrNull() else null }
    val pnl = entry.opt("pnl")?.let { if (it != JSONObject.NULL) (it as? Double) ?: it.toString().toDoubleOrNull() else null }
    val outcomeText = when {
        !closed -> "Open"
        rVal != null -> "R ${if (rVal >= 0) "+" else ""}${String.format(Locale.US, "%.2f", rVal)}"
        pnl != null -> (if (pnl >= 0) "+" else "") + String.format(Locale.US, "%.2f", pnl)
        else -> "Closed"
    }
    val outcomeColor = when {
        !closed -> TextMuted
        rVal != null -> if (rVal > 0) BullGreen else BearRed
        pnl != null -> if (pnl > 0) BullGreen else BearRed
        else -> TextMuted
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceLight)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.optString("instrument"),
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Text(
                direction,
                color = if (direction == "BUY") BullGreen else BearRed,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(outcomeText, color = outcomeColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        val openedAt = runCatching {
            Instant.parse(entry.optString("openedAt"))
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
        }.getOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(openedAt, entry.optString("setupTag").ifBlank { null }).joinToString("  ·  "),
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val lesson = entry.optString("lesson").ifBlank { null }
        if (lesson != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                lesson,
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}
