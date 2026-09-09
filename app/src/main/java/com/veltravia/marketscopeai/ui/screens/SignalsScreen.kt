package com.veltravia.marketscopeai.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.R
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Daily Signals — mirrors the FxLens reference layout:
 *  - header: MarketScope AI logo, "Daily Signals", real today date line
 *  - "at a glance" stats card (public, real aggregates from the backend)
 *    with a Month/Week toggle
 *  - entitled users (premium/trial/admin): the real live feed of curated
 *    signals; non-entitled: the honest locked PRO card.
 *
 * Signals are real: the MarketScope AI team publishes curated calls (owner posts
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
    // Free-tier feed state: locked=true means "latest signal only, the rest
    // is premium" — the backend returns the lock state with the feed.
    var feedLocked by remember { mutableStateOf(false) }
    var premiumSignalCount by remember { mutableStateOf(0) }
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
            val feed = ApiClient.fetchDailySignalsFeed(token, 50)
            feedError = null
            feedLocked = feed.optBoolean("locked", false)
            premiumSignalCount = feed.optInt("premiumSignalCount", 0)
            signals = feed.optJSONArray("signals") ?: org.json.JSONArray()
        } catch (e: Exception) {
            feedError = e.message ?: "Could not load signals"
            signals = null
        }
    }

    val isAdmin = access?.optBoolean("isAdmin", false) == true
    val entitled = access?.optBoolean("entitled", false) == true
    // True only for the very first load: stats + access (and, if entitled,
    // the feed) haven't resolved yet. Once resolved this never flips back
    // to true, so switching the Month/Week range never re-shows the skeleton.
    // A feedError (e.g. not signed in) always breaks out of the skeleton
    // immediately instead of spinning forever waiting on access/signals
    // that will never arrive.
    val showSkeleton = feedError == null && (stats == null || access == null || signals == null)

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

        if (showSkeleton) {
            SignalsFeedSkeleton()
        } else {
            // --- "At a glance" stats card (public, real aggregates) ---
            GlanceCard(stats, range) { range = it }
            Spacer(Modifier.height(20.dp))

            // --- Feed or locked card ---
            when {
                entitled || feedLocked -> {
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
                            androidx.compose.runtime.key(item.optString("id", "$i")) {
                                DailySignalCard(item, isAdmin = isAdmin)
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                        Text(
                            "Outcomes are resolved automatically against live market prices every 15 minutes.",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        if (feedLocked) {
                            Spacer(Modifier.height(14.dp))
                            LockedSignalsCard(
                                historyCount = premiumSignalCount
                            )
                        }
                    }
                }
                feedError != null && access == null -> {
                    ErrorNote(feedError!!)
                }
                else -> {
                    LockedSignalsCard()
                }
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
private fun DailySignalCard(item: JSONObject, isAdmin: Boolean = false) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }

    val id = item.optString("id", "")
    val instrument = item.optString("instrument", "")
    val direction = item.optString("direction", "long")
    val isLong = direction.equals("long", ignoreCase = true)
    val dirColor = if (isLong) BullGreen else BearRed
    val mode = item.optString("mode", "").takeIf { it.isNotBlank() }
    val entry = item.optDouble("entry", Double.NaN)
    val sl = item.optDouble("stopLoss", Double.NaN)
    val tps = item.optJSONArray("takeProfits")
    val firstTp = if (tps != null && tps.length() > 0) tps.optDouble(0) else Double.NaN
    val rr = item.optDouble("riskReward", Double.NaN)
    val thesis = item.optString("thesis", "")
    val strength = item.optString("strength", "moderate")
    val status = item.optString("status", "live")
    val outcome = item.optString("outcome", "")
    val resolvedBy = item.optString("resolvedBy", "")
    val exitPrice = item.optDouble("exitPrice", Double.NaN)
    val lastPrice = item.optDouble("lastPrice", Double.NaN)
    val author = item.optString("author", "owner")
    val authorLabel = if (author == "ai") "AI-Generated" else "MarketScope AI Team"
    val publishedAt = item.optString("publishedAt", "")

    // Local, optimistic social state — seeded from the feed's rollup, updated
    // instantly on tap and reconciled with the server response.
    var reactions by remember(id) {
        val list = item.optJSONArray("reactions")
        val map = linkedMapOf<String, Pair<Int, Boolean>>()
        if (list != null) {
            for (i in 0 until list.length()) {
                val r = list.optJSONObject(i) ?: continue
                map[r.optString("emoji")] = r.optInt("count", 0) to r.optBoolean("mine", false)
            }
        }
        mutableStateOf<MutableMap<String, Pair<Int, Boolean>>>(map)
    }
    var saved by remember(id) { mutableStateOf(item.optBoolean("saved", false)) }
    var saving by remember(id) { mutableStateOf(false) }
    var commentCount by remember(id) { mutableStateOf(item.optInt("commentCount", 0)) }
    var showComments by remember(id) { mutableStateOf(false) }
    var detailsExpanded by remember(id) { mutableStateOf(false) }

    fun toggleReaction(emoji: String) {
        if (token == null || id.isEmpty()) return
        val (count, mine) = reactions[emoji] ?: (0 to false)
        reactions = reactions.toMutableMap().apply {
            put(emoji, if (mine) (count - 1).coerceAtLeast(0) to false else count + 1 to true)
        }
        scope.launch {
            try {
                ApiClient.reactToSignal(token, id, emoji)
            } catch (_: Exception) {
                // revert on failure
                reactions = reactions.toMutableMap().apply { put(emoji, count to mine) }
            }
        }
    }

    fun toggleSave() {
        if (token == null || id.isEmpty() || saving) return
        saving = true
        val prev = saved
        saved = !saved
        scope.launch {
            try {
                val resp = ApiClient.toggleSavedSignal(token, id)
                saved = resp.optBoolean("saved", saved)
            } catch (_: Exception) {
                saved = prev
                android.widget.Toast.makeText(context, "Could not update saved state", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                saving = false
            }
        }
    }

    // Outcome theming — a won/lost signal gets a colored wash + border + a
    // folded corner ribbon, exactly like a real settled trade result should
    // stand out from a still-live call.
    val outcomeTint: Color? = when {
        status != "closed" -> null
        outcome == "successful" -> BullGreen
        outcome == "invalidated_sl" -> BearRed
        outcome == "expired_partial" || outcome == "breakeven" -> GoldAmber
        else -> null
    }
    val cardBorderColor = outcomeTint?.copy(alpha = 0.45f) ?: AccentCyan.copy(alpha = 0.22f)
    val cardBg = outcomeTint?.copy(alpha = 0.07f)?.let { tint ->
        androidx.compose.ui.graphics.lerp(Color.White, outcomeTint, 0.07f)
    } ?: SurfaceLight

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg)
                .border(if (outcomeTint != null) 1.5.dp else 1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SignalStatusPill(status, outcome)
                if (status == "closed" && resolvedBy == "auto") {
                    Spacer(Modifier.width(8.dp))
                    val exitLabel = if (exitPrice.isFinite()) " at ${fmt(exitPrice)}" else ""
                    Text("Resolved automatically$exitLabel", fontSize = 10.5.sp, color = TextMuted)
                }
            }
            Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(dateTimeLine(publishedAt), fontSize = 12.sp, color = TextMuted)
            if (mode != null) {
                Text(mode.replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(6.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(dirColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(instrument, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPrimary)
            }
            ConvictionPill(strength)
        }
        Spacer(Modifier.height(18.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LevelCell("Entry", if (!entry.isNaN()) fmt(entry) else "—")
            LevelCell("SL", if (!sl.isNaN()) fmt(sl) else "—")
            LevelCell("Initial TP", if (!firstTp.isNaN()) fmt(firstTp) else "—")
            LevelCell("R:R", if (!rr.isNaN()) "1:${"%.2f".format(rr)}" else "—")
        }
        Spacer(Modifier.height(16.dp))

        // --- Reactions ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SIGNAL_REACTION_EMOJIS.forEach { emoji ->
                val (count, mine) = reactions[emoji] ?: (0 to false)
                ReactionPill(emoji, count, mine) { toggleReaction(emoji) }
            }
        }
        Spacer(Modifier.height(12.dp))

        // --- Save button ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, BorderSubtleColor(), RoundedCornerShape(12.dp))
                .clickable(enabled = !saving) { toggleSave() }
                .padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = null,
                tint = if (saved) AccentCyan else TextSecondary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (saving) "Saving…" else if (saved) "Saved" else "Save",
                color = if (saved) AccentCyan else TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(12.dp))

        // --- Comments + View Details ---
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .border(1.dp, BorderSubtleColor(), RoundedCornerShape(20.dp))
                    .clickable(enabled = id.isNotEmpty()) { showComments = true }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("$commentCount", color = AccentCyan, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            Text(
                if (detailsExpanded) "Hide Details" else "View Details",
                color = AccentCyan,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.clickable { detailsExpanded = !detailsExpanded }
            )
        }

        if (detailsExpanded) {
            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderSubtleColor()))
            Spacer(Modifier.height(12.dp))
            Row {
                Text(authorLabel, fontSize = 11.sp, color = AccentViolet, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text("·", fontSize = 11.sp, color = TextMuted)
                Spacer(Modifier.width(8.dp))
                Text(if (isLong) "LONG" else "SHORT", fontSize = 11.sp, color = dirColor, fontWeight = FontWeight.SemiBold)
            }
            if (thesis.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(thesis, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            if (!lastPrice.isNaN()) {
                Spacer(Modifier.height(8.dp))
                Text("Live price: ${fmt(lastPrice)}", fontSize = 11.sp, color = TextMuted)
            }
        }
        }

        // Folded-corner ribbon for a settled trade — "TAKE PROFIT HIT" (win),
        // "STOP LOSS HIT" (loss), or "CLOSED AT BREAKEVEN" (partial/breakeven).
        // Only ever shown once a signal is actually closed with that outcome.
        if (outcomeTint != null) {
            val ribbonLabel = when (outcome) {
                "successful" -> "TAKE PROFIT HIT"
                "invalidated_sl" -> "STOP LOSS HIT"
                else -> "CLOSED AT BREAKEVEN"
            }
            OutcomeRibbon(
                label = ribbonLabel,
                color = outcomeTint,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }

    if (showComments && id.isNotEmpty()) {
        SignalCommentsSheet(
            signalId = id,
            isAdmin = isAdmin,
            instrument = instrument,
            onDismiss = { showComments = false },
            onCountChange = { commentCount = it }
        )
    }
}

/** A single fixed-set reaction pill (👍 🔥 😮 👏 ❓), highlighted when the current user reacted. */
@Composable
private fun ReactionPill(emoji: String, count: Int, mine: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (mine) AccentCyan.copy(alpha = 0.14f) else Color.White)
            .border(1.dp, if (mine) AccentCyan.copy(alpha = 0.4f) else BorderSubtleColor(), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 14.sp)
        Spacer(Modifier.width(5.dp))
        Text("$count", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (mine) AccentCyan else TextSecondary)
    }
}

/** Dark pill matching the reference's "strong"/"moderate"/"weak" conviction badge. */
@Composable
private fun ConvictionPill(strength: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(com.veltravia.marketscopeai.ui.theme.DarkInk)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(strength.lowercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** One cell of the Entry / SL / Initial TP / R:R grid. */
@Composable
private fun LevelCell(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = TextMuted)
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

/** Top-left status pill — a light tint + dash/arrow icon, mirroring the reference's amber "— Breakeven" chip. */
@Composable
private fun SignalStatusPill(status: String, outcome: String) {
    data class PillSpec(val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)
    val spec = when {
        status == "closed" && outcome == "successful" -> PillSpec(BullGreen, Icons.Filled.Check, "Successful")
        status == "closed" && outcome == "invalidated_sl" -> PillSpec(BearRed, Icons.Filled.Close, "Unsuccessful")
        status == "closed" && outcome == "expired_partial" -> PillSpec(GoldAmber, Icons.Filled.PauseCircle, "Partial")
        status == "closed" && outcome == "expired" -> PillSpec(TextMuted, Icons.Filled.HorizontalRule, "Expired")
        status == "closed" && outcome == "breakeven" -> PillSpec(GoldAmber, Icons.Filled.HorizontalRule, "Breakeven")
        status == "closed" -> PillSpec(TextMuted, Icons.Filled.HorizontalRule, "Closed")
        outcome == "triggered_active" -> PillSpec(AccentCyan, Icons.Filled.Check, "In Progress")
        else -> PillSpec(AccentViolet, Icons.Filled.Check, "Awaiting Entry")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(spec.color.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(spec.icon, contentDescription = null, tint = spec.color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(spec.label, fontSize = 13.sp, color = spec.color, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A folded-corner ribbon banner across the top-right of a settled signal's
 *  card — mirrors a real settled-trade result badge, not a decoration. */
@Composable
private fun OutcomeRibbon(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .offset(x = 26.dp, y = 14.dp)
            .graphicsLayer(rotationZ = 45f)
            .width(150.dp)
            .background(color)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
    }
}

/** A light gray border color that adapts with the theme's border token. */
@Composable
private fun BorderSubtleColor(): Color = com.veltravia.marketscopeai.ui.theme.BorderSubtle

/** "Sep 7 · 10:16 AM" in the device's local timezone, from the backend's ISO-8601 publishedAt. */
private fun dateTimeLine(iso: String): String {
    if (iso.isBlank()) return ""
    return try {
        val instant = java.time.Instant.parse(iso)
        val zoned = instant.atZone(ZoneId.systemDefault())
        zoned.format(DateTimeFormatter.ofPattern("MMM d '\u00B7' h:mm a"))
    } catch (e: Exception) {
        ""
    }
}

private val SIGNAL_REACTION_EMOJIS = listOf("\uD83D\uDC4D", "\uD83D\uDD25", "\uD83D\uDE2E", "\uD83D\uDC4F", "\u2753")
@Composable
private fun LockedSignalsCard(historyCount: Int = 0) {
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("MarketScope AI Premium", fontWeight = FontWeight.Bold) },
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
            "Every day, the MarketScope AI team curates trade setups and shares live updates on each one until it closes — right here, for premium members. One AI-generated call is published daily too, and every outcome is resolved against live market prices.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        if (historyCount > 1) {
            Spacer(Modifier.height(6.dp))
            Text(
                "You're seeing the latest call above — $historyCount published signals with full outcome history are waiting inside.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
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
