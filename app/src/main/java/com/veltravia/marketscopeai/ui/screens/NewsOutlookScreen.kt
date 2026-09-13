package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ===========================================================================
// News Outlook — a focused, single-purpose economic-calendar screen: a
// timezone-aware feed of scheduled macro events with impact/currency
// filters and a real AI "directional implication" note per event. Uses the
// same live economic-calendar backend as the Calendar tab, so this is a
// second, more specialised lens on the same real data — not a duplicate
// dataset.
// ===========================================================================

private data class OutlookEvent(
    val title: String,
    val country: String,
    val currency: String?,
    val impact: String,
    val forecast: String?,
    val previous: String?,
    val actual: String?,
    val timestampIso: String,
    val timestampMillis: Long
)

private data class TzOption(val label: String, val zoneId: String)

private val TZ_OPTIONS = listOf(
    TzOption("Lagos, Africa", "Africa/Lagos"),
    TzOption("London, UK", "Europe/London"),
    TzOption("New York, US", "America/New_York"),
    TzOption("Chicago, US", "America/Chicago"),
    TzOption("Los Angeles, US", "America/Los_Angeles"),
    TzOption("Johannesburg, Africa", "Africa/Johannesburg"),
    TzOption("Nairobi, Africa", "Africa/Nairobi"),
    TzOption("Cairo, Africa", "Africa/Cairo"),
    TzOption("Frankfurt, Europe", "Europe/Berlin"),
    TzOption("Paris, Europe", "Europe/Paris"),
    TzOption("Zurich, Europe", "Europe/Zurich"),
    TzOption("Moscow, Europe", "Europe/Moscow"),
    TzOption("Dubai, Middle East", "Asia/Dubai"),
    TzOption("Mumbai, Asia", "Asia/Kolkata"),
    TzOption("Singapore, Asia", "Asia/Singapore"),
    TzOption("Hong Kong, Asia", "Asia/Hong_Kong"),
    TzOption("Tokyo, Asia", "Asia/Tokyo"),
    TzOption("Shanghai, Asia", "Asia/Shanghai"),
    TzOption("Sydney, Australia", "Australia/Sydney"),
    TzOption("Auckland, NZ", "Pacific/Auckland"),
    TzOption("Toronto, Canada", "America/Toronto"),
    TzOption("São Paulo, Brazil", "America/Sao_Paulo")
)

private val CURRENCY_CHIPS = listOf("USD", "EUR", "GBP", "JPY", "CHF", "AUD", "CAD", "NZD", "CNY")

private fun defaultTz(): TzOption {
    val id = TimeZone.getDefault().id
    return TZ_OPTIONS.firstOrNull { it.zoneId == id }
        ?: TzOption(id.substringAfterLast('/').replace('_', ' '), id)
}

private fun offsetLabel(zoneId: String): String {
    val tz = TimeZone.getTimeZone(zoneId)
    val offsetMs = tz.getOffset(System.currentTimeMillis())
    val totalMin = offsetMs / 60000
    val sign = if (totalMin >= 0) "+" else "-"
    val h = kotlin.math.abs(totalMin) / 60
    val m = kotlin.math.abs(totalMin) % 60
    return if (m == 0) "UTC$sign$h" else "UTC$sign$h:${m.toString().padStart(2, '0')}"
}

private val utcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Composable
fun NewsOutlookScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }

    var events by remember { mutableStateOf<List<OutlookEvent>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fetchedAt by remember { mutableStateOf<Long?>(null) }
    var selectedTz by remember { mutableStateOf(defaultTz()) }
    var tzPickerOpen by remember { mutableStateOf(false) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    var highImpactOnly by remember { mutableStateOf(false) }
    var selectedCurrencies by remember { mutableStateOf(setOf<String>()) }

    fun load() {
        error = null
        scope.launch {
            try {
                val arr = ApiClient.fetchEconomicCalendar()
                events = buildList {
                    for (i in 0 until arr.length()) {
                        val e = arr.optJSONObject(i) ?: continue
                        val iso = e.optString("timestamp")
                        val millis = try { utcParser.parse(iso)?.time ?: 0L } catch (_: Exception) { 0L }
                        add(
                            OutlookEvent(
                                title = e.optString("title"),
                                country = e.optString("country"),
                                currency = if (e.isNull("currency")) null else e.optString("currency").takeIf { it.isNotBlank() },
                                impact = e.optString("impact", "Low"),
                                forecast = if (e.isNull("forecast")) null else e.optString("forecast"),
                                previous = if (e.isNull("previous")) null else e.optString("previous"),
                                actual = if (e.isNull("actual")) null else e.optString("actual"),
                                timestampIso = iso,
                                timestampMillis = millis
                            )
                        )
                    }
                }
                fetchedAt = System.currentTimeMillis()
            } catch (e: Exception) {
                error = e.message ?: "Could not load today's news outlook"
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val filtered = events.orEmpty().filter { ev ->
        (!highImpactOnly || ev.impact.equals("High", ignoreCase = true)) &&
            (selectedCurrencies.isEmpty() || (ev.currency != null && ev.currency in selectedCurrencies))
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                "News Outlook",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { filterSheetOpen = true }, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.FilterList, contentDescription = "Filter", tint = AccentViolet)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                "All important news today",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Stay ahead of the scheduled events that tend to move the markets you trade.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            fetchedAt?.let { at ->
                Spacer(Modifier.height(10.dp))
                val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                Text(
                    "Showing the latest available calendar data, refreshed at ${fmt.format(Date(at))}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldAmber
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("SELECT TIMEZONE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextMuted)
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceLight)
                    .clickable { tzPickerOpen = true }
                    .padding(14.dp)
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(AccentCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AccessTime, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(selectedTz.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text("${selectedTz.zoneId} · ${offsetLabel(selectedTz.zoneId)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }

            Spacer(Modifier.height(18.dp))

            when {
                events == null && error == null -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentViolet, strokeWidth = 2.5.dp)
                    }
                }
                error != null -> {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "", color = TextSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { load() }) { Text("Retry") }
                    }
                }
                filtered.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text("No events match your filters right now.", color = TextSecondary, textAlign = TextAlign.Center)
                    }
                }
                else -> {
                    filtered.sortedBy { it.timestampMillis }.forEach { ev ->
                        OutlookEventCard(ev, selectedTz.zoneId, token)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (tzPickerOpen) {
        TimezonePickerSheet(
            current = selectedTz,
            onDismiss = { tzPickerOpen = false },
            onSelect = { selectedTz = it; tzPickerOpen = false }
        )
    }

    if (filterSheetOpen) {
        FilterSheet(
            highImpactOnly = highImpactOnly,
            onHighImpactChange = { highImpactOnly = it },
            selectedCurrencies = selectedCurrencies,
            onToggleCurrency = { c ->
                selectedCurrencies = if (c in selectedCurrencies) selectedCurrencies - c else selectedCurrencies + c
            },
            onDismiss = { filterSheetOpen = false }
        )
    }
}

@Composable
private fun OutlookEventCard(ev: OutlookEvent, zoneId: String, token: String?) {
    val scope = rememberCoroutineScope()
    var implication by remember(ev.title, ev.timestampIso) { mutableStateOf<String?>(null) }
    var implicationError by remember(ev.title, ev.timestampIso) { mutableStateOf<String?>(null) }
    var loadingImplication by remember(ev.title, ev.timestampIso) { mutableStateOf(false) }

    val isHigh = ev.impact.equals("High", ignoreCase = true)
    val impactColor = when (ev.impact.lowercase()) {
        "high" -> BearRed
        "medium" -> GoldAmber
        "holiday" -> AccentCyan
        else -> Color(0xFF94A3B8)
    }

    val cal = Calendar.getInstance(TimeZone.getTimeZone(zoneId))
    cal.timeInMillis = ev.timestampMillis
    val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(zoneId) }
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone(zoneId) }

    val dataParts = buildList {
        ev.forecast?.let { add("Forecast $it") }
        ev.previous?.let { add("Previous $it") }
        ev.actual?.let { add("Actual $it") }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.5.dp, if (isHigh) BearRed.copy(alpha = 0.55f) else BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                ev.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            ev.currency?.let { cur ->
                Text(
                    cur,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AccentCyan.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(dateFmt.format(Date(ev.timestampMillis)), style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.AccessTime, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(timeFmt.format(Date(ev.timestampMillis)), style = MaterialTheme.typography.labelSmall, color = TextMuted)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${ev.impact} impact",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = impactColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(impactColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (dataParts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                dataParts.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceLight)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Forecast not yet published",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            implication != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentViolet.copy(alpha = 0.08f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AI DIRECTIONAL IMPLICATION", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AccentViolet)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(implication ?: "", style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 18.sp)
                }
            }
            implicationError != null -> {
                Text(implicationError ?: "", style = MaterialTheme.typography.labelSmall, color = BearRed)
                Spacer(Modifier.height(6.dp))
                AiButton(loading = false, onClick = {
                    implicationError = null
                    loadingImplication = true
                    scope.launch {
                        runImplication(token, ev) { result, err -> loadingImplication = false; implication = result; implicationError = err }
                    }
                })
            }
            else -> {
                AiButton(loading = loadingImplication, onClick = {
                    if (token.isNullOrBlank()) {
                        implicationError = "Sign in required."
                        return@AiButton
                    }
                    loadingImplication = true
                    scope.launch {
                        runImplication(token, ev) { result, err -> loadingImplication = false; implication = result; implicationError = err }
                    }
                })
            }
        }
    }
}

private suspend fun runImplication(
    token: String?,
    ev: OutlookEvent,
    onDone: (String?, String?) -> Unit
) {
    if (token.isNullOrBlank()) {
        onDone(null, "Sign in required.")
        return
    }
    try {
        val text = ApiClient.fetchDirectionalImplication(
            sessionToken = token,
            title = ev.title,
            country = ev.country.takeIf { it.isNotBlank() },
            currency = ev.currency,
            impact = ev.impact,
            forecast = ev.forecast,
            previous = ev.previous,
            actual = ev.actual,
            timestampIso = ev.timestampIso
        )
        if (text.isBlank()) onDone(null, "Could not generate an implication — try again.")
        else onDone(text, null)
    } catch (e: Exception) {
        onDone(null, e.message ?: "Could not generate an implication — try again.")
    }
}

@Composable
private fun AiButton(loading: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(AccentViolet, AccentCyan))
            )
            .clickable(enabled = !loading) { onClick() }
            .padding(vertical = 12.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Thinking…", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        } else {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Get AI Directional Implication", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimezonePickerSheet(current: TzOption, onDismiss: () -> Unit, onSelect: (TzOption) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    val filtered = TZ_OPTIONS.filter { it.label.contains(query, ignoreCase = true) || it.zoneId.contains(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Select timezone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search city or region") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                items(filtered, key = { it.zoneId }) { opt ->
                    val selected = opt.zoneId == current.zoneId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(opt) }
                            .padding(vertical = 12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(opt.label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
                            Text("${opt.zoneId} · ${offsetLabel(opt.zoneId)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = AccentViolet)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    highImpactOnly: Boolean,
    onHighImpactChange: (Boolean) -> Unit,
    selectedCurrencies: Set<String>,
    onToggleCurrency: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Filter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceLight)
                    .padding(14.dp)
            ) {
                Text("Impact", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("High impact only", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Show only events the calendar marks high impact", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                    Switch(
                        checked = highImpactOnly,
                        onCheckedChange = onHighImpactChange,
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentViolet)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Currencies", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Narrow the feed to specific currencies", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Spacer(Modifier.height(10.dp))

            val rows = CURRENCY_CHIPS.chunked(5)
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    row.forEach { cur ->
                        val selected = cur in selectedCurrencies
                        Text(
                            cur,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) AccentViolet else Color.White)
                                .border(1.dp, if (selected) AccentViolet else BorderSubtle, RoundedCornerShape(50))
                                .clickable { onToggleCurrency(cur) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(AccentViolet, AccentCyan))
                    )
                    .clickable { onDismiss() }
                    .padding(vertical = 14.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
