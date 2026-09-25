package com.veltravia.marketscopeai.ui.screens

import com.veltravia.marketscopeai.t

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.R
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private enum class SignalFilter(val label: String) { ALL("All signals"), SCALP("Scalp"), SWING("Swing") }

/**
 * Saved screen: a real Trade Plans section (user-authored plans, persisted
 * via the backend) plus the real Saved Signals history (AI analyses).
 *
 * Note: FxLens's reference filters are "All signals / Generated / Daily
 * signals" — MarketScope AI has no distinct "daily push" signal feature (no
 * separate signal source exists), so filtering by that would be dishonest.
 * Instead the filters are grounded in a real field we do have: `mode`
 * (Scalp / Swing), which every saved analysis genuinely carries.
 */
@Composable
fun SavedScreen(
    onOpenAnalysis: (String) -> Unit,
    onCreateTradePlan: () -> Unit,
    onOpenSignal: (String) -> Unit = {},
    onOpenTradePlan: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }

    var analyses by remember { mutableStateOf<JSONArray?>(null) }
    var analysesError by remember { mutableStateOf<String?>(null) }
    var savedDaily by remember { mutableStateOf<JSONArray?>(null) }
    var savedDailyError by remember { mutableStateOf<String?>(null) }
    var plans by remember { mutableStateOf<JSONArray?>(null) }
    var plansError by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(SignalFilter.ALL) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        if (token == null) {
            analysesError = "Not signed in"
            plansError = "Not signed in"
            return@LaunchedEffect
        }
        try {
            analyses = ApiClient.fetchAnalyses(token, 50)
            analysesError = null
        } catch (e: Exception) {
            analysesError = e.message ?: "Could not load history"
        }
        try {
            savedDaily = ApiClient.fetchSavedDailySignals(token).optJSONArray("signals") ?: JSONArray()
            savedDailyError = null
        } catch (e: Exception) {
            savedDaily = JSONArray()
            savedDailyError = e.message ?: "Could not load saved signals"
        }
        try {
            plans = ApiClient.fetchAiTradePlans(token).optJSONArray("plans") ?: JSONArray()
            plansError = null
        } catch (e: Exception) {
            plansError = e.message ?: "Could not load trade plans"
        }
    }

    val filteredAnalyses = remember(analyses, filter) {
        val list = analyses ?: return@remember null
        (0 until list.length()).mapNotNull { i -> list.optJSONObject(i) }.filter { item ->
            when (filter) {
                SignalFilter.ALL -> true
                SignalFilter.SCALP -> item.optString("mode", "").equals("scalp", ignoreCase = true)
                SignalFilter.SWING -> item.optString("mode", "").equals("swing", ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(t("MarketScope AI"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(com.veltravia.marketscopeai.ui.theme.BorderSubtle)
            )
            Spacer(Modifier.height(24.dp))
        }

        // --- Saved daily signals (Signals tab bookmark) ---
        if (savedDaily == null) {
            item {
                Text(t("Saved daily signals"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
                SavedDailySignalsSkeleton()
                Spacer(Modifier.height(20.dp))
            }
        } else if (savedDaily!!.length() > 0) {
            item {
                Text(t("Saved daily signals"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(12.dp))
            }
            items(
                (0 until savedDaily!!.length()).mapNotNull { savedDaily!!.optJSONObject(it) },
                key = { it.optString("id") }
            ) { signal ->
                SavedDailySignalRow(signal, onOpen = { onOpenSignal(it) }) { id ->
                    // Unsave straight from this list — same toggle endpoint
                    // the Signals tab uses. Optimistic remove + refresh on error.
                    val removed = savedDaily
                    savedDaily = JSONArray().apply {
                        for (i in 0 until (removed?.length() ?: 0)) {
                            val o = removed!!.optJSONObject(i) ?: continue
                            if (o.optString("id") != id) put(o)
                        }
                    }
                    val t = token
                    if (t != null) {
                        scope.launch {
                            try {
                                ApiClient.toggleSavedSignal(t, id)
                            } catch (_: Exception) {
                                reloadKey++ // restore on failure
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            item { Spacer(Modifier.height(20.dp)) }
        }

        // --- Trade Plans ---
        item {
            when {
                plansError != null -> Unit // stay silent — signals section below still works
                plans == null -> {
                    TradePlansSkeleton()
                }
                plans!!.length() == 0 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(AccentCyan.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(t("No trade plans created yet."), color = TextSecondary, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(t("Answer five quick steps and our AI writes your personal trading playbook."),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Brush.horizontalGradient(listOf(AccentViolet, AccentCyan)))
                                .clickable { onCreateTradePlan() }
                                .padding(horizontal = 28.dp, vertical = 14.dp)
                        ) {
                            Text(t("Create Trade Plan"), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t("Trade Plans"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        IconButton(onClick = onCreateTradePlan) {
                            Icon(Icons.Filled.Add, contentDescription = "New trade plan", tint = AccentViolet)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    val planList = plans!!
                    for (i in 0 until minOf(planList.length(), 3)) {
                        val p = planList.optJSONObject(i) ?: continue
                        AiPlanRow(p) { id -> onOpenTradePlan(id) }
                        Spacer(Modifier.height(10.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }

        // --- Saved signals header + filters ---
        item {
            Text(t("Saved signals"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SignalFilter.entries.forEach { f ->
                    val selected = filter == f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) AccentCyan.copy(alpha = 0.14f) else SurfaceLight)
                            .clickable { filter = f }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            f.label,
                            color = if (selected) AccentCyan else TextSecondary,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        when {
            analysesError != null -> item {
                EmptyState(
                    icon = { Icon(Icons.Filled.BookmarkBorder, null, tint = TextMuted, modifier = Modifier.size(48.dp)) },
                    title = t("Couldn't reach history"),
                    subtitle = analysesError ?: ""
                )
            }
            filteredAnalyses == null -> item {
                SavedSignalsSkeleton()
            }
            filteredAnalyses.isEmpty() -> item {
                EmptyState(
                    icon = { Icon(Icons.Filled.BookmarkBorder, null, tint = TextMuted, modifier = Modifier.size(48.dp)) },
                    title = if (filter == SignalFilter.ALL) "No saved signals yet" else "No ${filter.label.lowercase()} signals yet",
                    subtitle = t("Run an analysis and it will be stored here automatically")
                )
            }
            else -> {
                items(filteredAnalyses) { item ->
                    SavedSignalCard(item, onOpenAnalysis)
                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        // Adaptive banner slot (OFF by default server-side; only renders for
        // free users when placements.banner.enabled is switched on in config).
        item {
            com.veltravia.marketscopeai.monetization.BannerAdSlot(surface = "saved")
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AiPlanRow(plan: JSONObject, onOpen: (String) -> Unit) {
    val id = plan.optString("id", "")
    val name = plan.optString("name", "Trade plan")
    val goal = plan.optString("goal", "")
    val created = plan.optString("createdAt", "")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLight)
            .clickable { onOpen(id) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AccentViolet.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = AccentViolet,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            if (goal.isNotBlank()) {
                Text(goal, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted
        )
    }
}

@Composable
private fun SavedSignalCard(item: JSONObject, onOpen: (String) -> Unit) {
    val id = item.optString("id", "")
    val signal = item.optJSONObject("analysis")
    val inner = signal?.optJSONObject("analysis")
    val instrument = signal?.optString("instrument")
        ?: item.optString("instrumentId", "").uppercase()
    val mode = (signal?.optString("mode") ?: item.optString("mode", "")).replaceFirstChar { it.uppercase() }
    val direction = inner?.optString("direction", "")?.uppercase() ?: ""
    val entryZone = inner?.optJSONObject("entryZone")
    val entryValue = entryZone?.let {
        val low = it.optDouble("low", Double.NaN)
        val high = it.optDouble("high", Double.NaN)
        if (!low.isNaN() && !high.isNaN()) (low + high) / 2.0 else Double.NaN
    } ?: Double.NaN
    val stopLoss = inner?.optDouble("stopLoss", Double.NaN) ?: Double.NaN
    val takeProfits = inner?.optJSONArray("takeProfits")
    val firstTp = if (takeProfits != null && takeProfits.length() > 0) takeProfits.optDouble(0) else Double.NaN
    val rr = inner?.optDouble("riskReward", Double.NaN) ?: Double.NaN
    val thesis = inner?.optString("thesis", "") ?: ""
    val analyzedAt = item.optString("analyzedAt", "")
    val (dateLabel, timeLabel) = formatAnalyzedAt(analyzedAt)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLight)
            .border(1.dp, AccentCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(dateLabel, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text(timeLabel, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "$instrument · $mode",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LevelColumn("Entry", if (!entryValue.isNaN()) formatLevel(entryValue) else "—")
                LevelColumn("SL", if (!stopLoss.isNaN()) formatLevel(stopLoss) else "—")
                LevelColumn("TP", if (!firstTp.isNaN()) formatLevel(firstTp) else "—")
                LevelColumn("RR", if (!rr.isNaN()) "1:${"%.1f".format(rr).trimEnd('0').trimEnd('.')}" else "—")
            }
            if (thesis.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    thesis,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = id.isNotEmpty()) { onOpen(id) },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(t("View Details"), color = AccentCyan, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(4.dp))
                Text("›", color = AccentCyan, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LevelColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

@Composable
private fun EmptyState(icon: @Composable () -> Unit, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

private fun formatLevel(value: Double): String {
    if (value.isNaN()) return "—"
    return if (value >= 100) "%.2f".format(value) else "%.4f".format(value).trimEnd('0').trimEnd('.')
}

/** Parses the backend's ISO-8601 timestamp and formats it like "Sun, Sep 6, 2026" + "7:15 PM" in the device's local timezone. */
/** Placeholder shimmer row while the bookmarked daily signals load. */
@Composable
private fun SavedDailySignalsSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceLight)
            )
        }
    }
}

/** A bookmarked daily signal (from the Signals tab), listed on the Saved
 *  screen with its real levels and a one-tap unsave. */
@Composable
private fun SavedDailySignalRow(signal: JSONObject, onOpen: (String) -> Unit, onRemove: (String) -> Unit) {
    val id = signal.optString("id", "")
    val isLong = signal.optString("direction", "long").equals("long", ignoreCase = true)
    val tps = signal.optJSONArray("takeProfits")
    val tpLabel = if (tps != null && tps.length() > 0) {
        (0 until tps.length()).joinToString(" / ") { i ->
            trimNum(tps.optDouble(i, Double.NaN))
        }
    } else "—"
    val (dateLabel, timeLabel) = formatAnalyzedAt(signal.optString("publishedAt", ""))
    val status = signal.optString("status", "").lowercase()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLight)
            .clickable(enabled = id.isNotEmpty()) { onOpen(id) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        signal.optString("instrument", signal.optString("instrumentId", "").uppercase()),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isLong) "BUY" else "SELL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLong) BullGreen else BearRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isLong) BullGreen.copy(alpha = 0.12f) else BearRed.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    if (status.isNotBlank() && status != "active") {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            status.replaceFirstChar { it.uppercase() },
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Entry ${trimNum(signal.optDouble("entry", Double.NaN))}  ·  SL ${trimNum(signal.optDouble("stopLoss", Double.NaN))}  ·  TP $tpLabel",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                if (dateLabel.isNotBlank()) {
                    Text("$dateLabel · $timeLabel", fontSize = 10.sp, color = TextMuted)
                }
            }
            IconButton(onClick = { onRemove(id) }, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Bookmark,
                    contentDescription = "Remove from saved",
                    tint = AccentCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Formats a number without trailing ".0"-style noise; NaN -> "—". */
private fun trimNum(v: Double): String =
    if (v.isNaN()) "—" else if (v == v.toLong().toDouble()) v.toLong().toString() else trimDecimals(v)

private fun trimDecimals(v: Double): String {
    val s = String.format(java.util.Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')
    return if (s.isBlank()) "—" else s
}

private fun formatAnalyzedAt(iso: String): Pair<String, String> {
    if (iso.isBlank()) return "" to ""
    return try {
        val instant = Instant.parse(iso)
        val zoned = instant.atZone(ZoneId.systemDefault())
        val date = zoned.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
        val time = zoned.format(DateTimeFormatter.ofPattern("h:mm a"))
        date to time
    } catch (e: DateTimeParseException) {
        "" to ""
    }
}
