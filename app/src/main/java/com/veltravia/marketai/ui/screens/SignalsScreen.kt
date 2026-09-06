package com.veltravia.marketai.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketai.R
import com.veltravia.marketai.data.ApiClient
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.AccentViolet
import com.veltravia.marketai.ui.theme.BearRed
import com.veltravia.marketai.ui.theme.BullGreen
import com.veltravia.marketai.ui.theme.GoldAmber
import com.veltravia.marketai.ui.theme.SurfaceLight
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextPrimary
import com.veltravia.marketai.ui.theme.TextSecondary
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Daily Signals — mirrors the FxLens reference layout:
 *  - header: Market Ai logo, "Daily Signals", real today date line
 *  - "at a glance" stats card (public, real aggregates from the backend)
 *    with a Month/Week toggle
 *  - entitled users (premium/trial/admin): the real live feed of curated
 *    signals; non-entitled: the honest locked PRO card.
 *
 * Signals are real: the Market Ai team publishes curated calls (owner posts
 * manually via the admin screen) plus one AI-generated call per day, and a
 * GitHub Actions cron resolves outcomes automatically against live prices.
 */
@Composable
fun SignalsScreen(
    onOpenAdmin: () -> Unit
) {
    val context = LocalContext.current
    var range by remember { mutableStateOf("month") }
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    var access by remember { mutableStateOf<JSONObject?>(null) }
    var signals by remember { mutableStateOf<JSONArray?>(null) }
    var feedError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(range, reloadKey) {
        try {
            stats = ApiClient.fetchSignalStats(range)
        } catch (_: Exception) {
            stats = JSONObject().put("error", true)
        }
    }

    LaunchedEffect(reloadKey) {
        val token = SessionManager.sessionToken(context)
        if (token == null) {
            feedError = "Not signed in"
            return@LaunchedEffect
        }
        try {
            access = ApiClient.fetchSignalAccess(token)
            val entitled = access?.optBoolean("entitled", false) == true
            feedError = null
            signals = if (entitled) ApiClient.fetchDailySignals(token, 50) else null
        } catch (e: Exception) {
            feedError = e.message ?: "Could not load signals"
            signals = null
        }
    }

    val isAdmin = access?.optBoolean("isAdmin", false) == true
    val entitled = access?.optBoolean("entitled", false) == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        // --- Header (mirror of the reference: date line + title) ---
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(26.dp).clip(RoundedCornerShape(7.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Daily Signals",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    todayLine(),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (isAdmin) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(AccentViolet)
                        .clickable { onOpenAdmin() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Post", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // --- "At a glance" stats card (public, real aggregates) ---
        GlanceCard(stats, range) { range = it }
        Spacer(Modifier.height(20.dp))

        // --- Feed or locked card ---
        when {
            entitled -> {
                Text("Live trades", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                val feed = signals
                if (feedError != null) {
                    ErrorNote(feedError!!)
                    Spacer(Modifier.height(20.dp))
                } else if (feed == null) {
                    androidx.compose.material3.CircularProgressIndicator(color = AccentCyan)
                    Spacer(Modifier.height(20.dp))
                } else if (feed.length() == 0) {
                    EmptyFeedNote()
                    Spacer(Modifier.height(20.dp))
                } else {
                    for (i in 0 until feed.length()) {
                        val item = feed.optJSONObject(i) ?: continue
                        DailySignalCard(item)
                        Spacer(Modifier.height(14.dp))
                    }
                    Text(
                        "Outcomes are resolved automatically against live market prices every 15 minutes.",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
            feedError != null && access == null -> {
                ErrorNote(feedError!!)
            }
            else -> {
                LockedSignalsCard()
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** "Sunday, Sep 6" — real device-local date, like the reference's toLocaleDateString line. */
private fun todayLine(): String {
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    return now.format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
}

@Composable
private fun GlanceCard(stats: JSONObject?, range: String, onRangeChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFFE6FFFB), Color(0xFFEEF2FF))))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (range == "month") "This month at a glance" else "This week at a glance",
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFFDDE4F2)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RangePill("Month", range == "month", onRangeChange)
                RangePill("Week", range == "week", onRangeChange)
            }
        }
        Spacer(Modifier.height(14.dp))

        if (stats == null) {
            androidx.compose.material3.CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
        } else if (stats.optBoolean("error", false)) {
            Text("Stats unavailable right now.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TextSecondary)
        } else {
            val wins = stats.optInt("wins", 0)
            val losses = stats.optInt("losses", 0)
            val decided = wins + losses
            val successPct = stats.opt("successPct")
            val avgRR = stats.opt("avgRR")
            val strongCount = stats.optInt("strongCount", 0)
            val total = stats.optInt("total", 0)
            val live = stats.optInt("live", 0)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Win Rate", if (successPct != null && successPct != JSONObject.NULL) "$successPct%" else "—", Modifier.weight(1f))
                StatChip("Avg R:R", if (avgRR != null && avgRR != JSONObject.NULL) "1:$avgRR" else "—", Modifier.weight(1f))
                StatChip("High-Conv.", if (strongCount > 0) "$strongCount" else "0", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Published", "$total", Modifier.weight(1f))
                StatChip("Live now", "$live", Modifier.weight(1f))
                StatChip("Decided", "$decided", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Range: this ${if (range == "month") "month" else "week"}. Win rate = wins / (wins + losses). High-Conviction counts signals marked strong.",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun RangePill(label: String, selected: Boolean, onSelect: (String) -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color(0xFF0F172A) else Color.Transparent)
            .clickable { onSelect(label.lowercase()) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Text(value, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DailySignalCard(item: JSONObject) {
    val instrument = item.optString("instrument", "")
    val direction = item.optString("direction", "long")
    val isLong = direction.equals("long", ignoreCase = true)
    val dirColor = if (isLong) BullGreen else BearRed
    val dirLabel = if (isLong) "LONG" else "SHORT"
    val author = item.optString("author", "owner")
    val authorLabel = if (author == "ai") "AI-Generated" else "Market Ai Team"
    val entry = item.optDouble("entry", Double.NaN)
    val sl = item.optDouble("stopLoss", Double.NaN)
    val tps = item.optJSONArray("takeProfits")
    val firstTp = if (tps != null && tps.length() > 0) tps.optDouble(0) else Double.NaN
    val finalTp = if (tps != null && tps.length() > 0) tps.optDouble(tps.length() - 1) else Double.NaN
    val rr = item.optDouble("riskReward", Double.NaN)
    val thesis = item.optString("thesis", "")
    val strength = item.optString("strength", "moderate")
    val status = item.optString("status", "live")
    val outcome = item.optString("outcome", "")
    val lastPrice = item.optDouble("lastPrice", Double.NaN)
    val strengthColor = when (strength.lowercase()) {
        "strong" -> BullGreen
        "weak" -> BearRed
        else -> GoldAmber
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLight)
            .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(instrument, fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(
                dirLabel,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(dirColor)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text(authorLabel, fontSize = 11.sp, color = AccentViolet, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("·", fontSize = 11.sp, color = TextMuted)
            Spacer(Modifier.width(8.dp))
            Text("$strength conviction", fontSize = 11.sp, color = strengthColor, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniStat("Entry", if (!entry.isNaN()) fmt(entry) else "—")
            MiniStat("SL", if (!sl.isNaN()) fmt(sl) else "—")
            MiniStat("TP1", if (!firstTp.isNaN()) fmt(firstTp) else "—")
            MiniStat("Final TP", if (!finalTp.isNaN()) fmt(finalTp) else "—")
            MiniStat("R:R", if (!rr.isNaN()) "1:$rr" else "—")
        }
        if (!lastPrice.isNaN()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Live price: ${fmt(lastPrice)}",
                fontSize = 11.sp,
                color = TextMuted
            )
        }
        if (thesis.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(thesis, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Spacer(Modifier.height(12.dp))
        StatusBadge(status, outcome)
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = TextMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    }
}

@Composable
private fun StatusBadge(status: String, outcome: String) {
    val (bg, fg, label) = when {
        status == "closed" && outcome == "successful" -> Triple(BullGreen, Color.White, "CLOSED · WIN")
        status == "closed" && outcome == "invalidated_sl" -> Triple(BearRed, Color.White, "CLOSED · LOSS")
        status == "closed" && outcome == "expired_partial" -> Triple(GoldAmber, Color.White, "CLOSED · PARTIAL")
        status == "closed" && outcome == "expired" -> Triple(TextMuted, Color.White, "CLOSED · EXPIRED")
        status == "closed" && outcome == "breakeven" -> Triple(TextMuted, Color.White, "CLOSED · BREAKEVEN")
        status == "closed" -> Triple(TextMuted, Color.White, "CLOSED")
        outcome == "triggered_active" -> Triple(AccentCyan, Color.White, "LIVE · IN PROGRESS")
        else -> Triple(AccentViolet, Color.White, "LIVE · AWAITING ENTRY")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun LockedSignalsCard() {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Market Ai Premium", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Daily Signals is a premium feature. Your 7-day free trial has ended. " +
                    "Premium billing is being finalized and will be available in the app soon — " +
                    "the win-rate stats above stay free in the meantime.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDialog = false }) {
                    Text("Okay", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLight)
            .border(1.dp, GoldAmber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Filled.Lock, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "DAILY SIGNALS · PREMIUM",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = GoldAmber,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Unlock the exact daily trade setups",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Every day, the Market Ai team curates trade setups and shares live updates on each one until it closes — right here, for premium members. One AI-generated call is published daily too, and every outcome is resolved against live market prices.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(AccentViolet, AccentCyan)))
                .clickable { showDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Text("See what's live today", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Your free trial has ended. Premium billing is coming soon — the stats above stay free.",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun EmptyFeedNote() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "No live signals right now.",
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "The team publishes curated setups and the AI posts one daily call — check back soon.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorNote(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        Text(
            "Couldn't load signals",
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(message, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
    }
}

private fun fmt(v: Double): String {
    return if (v >= 100) String.format(java.util.Locale.US, "%.2f", v)
    else String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
}
