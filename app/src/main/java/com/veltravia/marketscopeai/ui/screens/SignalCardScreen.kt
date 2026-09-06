package com.veltravia.marketscopeai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.R
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.InstrumentCatalog
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.NavyBlack
import com.veltravia.marketscopeai.ui.theme.SurfaceDark
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Trade Analysis — shows one stored AI signal (fetched by id from the backend,
 * so it works after a fresh analysis and from Saved history).
 *
 * Every value rendered here comes from the real AI analysis stored in Postgres:
 * direction, confidence, entry zone, stop loss, take profits, risk/reward,
 * thesis, invalidation, key levels, and the AI's own estimatedDuration.
 * Trend / trade idea / strength labels are derived from those real fields —
 * nothing is fabricated.
 */
@Composable
fun SignalCardScreen(
    analysisId: String,
    onBack: () -> Unit,
    continueCta: Pair<String, () -> Unit>? = null,
    onOpenBrokerInfo: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var record by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(analysisId) {
        try {
            val token = SessionManager.sessionToken(context)
            if (token == null) {
                error = "Not signed in"
                return@LaunchedEffect
            }
            record = ApiClient.fetchAnalysis(token, analysisId)
        } catch (e: Exception) {
            error = e.message ?: "Could not load analysis"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // --- Top bar: back arrow + centered title ---
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Trade Analysis",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        when {
            error != null -> {
                Spacer(Modifier.height(40.dp))
                Text(
                    error ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
            record == null -> {
                Spacer(Modifier.height(80.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AccentCyan)
                    Spacer(Modifier.height(16.dp))
                    Text("Loading analysis…", color = TextMuted)
                }
            }
            else -> {
                val rec = record!!
                // Stored shape: { id, instrumentId, mode, analysis: { instrument, instrumentId, mode, model, analysis: {AI fields}, analyzedAt }, analyzedAt }
                val stored = rec.optJSONObject("analysis") ?: JSONObject()
                val ai = stored.optJSONObject("analysis") ?: stored
                val instrumentId = stored.optString("instrumentId", rec.optString("instrumentId", ""))
                TradeAnalysisBody(
                    instrumentDisplay = stored.optString("instrument", instrumentId.uppercase()),
                    instrumentId = instrumentId,
                    mode = stored.optString("mode", rec.optString("mode", "")),
                    analyzedAt = stored.optString("analyzedAt", rec.optString("analyzedAt", "")),
                    analysis = ai,
                    onOpenBrokerInfo = onOpenBrokerInfo
                )
            }
        }

        if (continueCta != null) {
            Spacer(Modifier.height(20.dp))
            val (label, action) = continueCta
            Button(
                onClick = action,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "MarketScope AI provides AI-generated analysis for educational purposes only and is not financial advice. Trade at your own risk.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.padding(vertical = 16.dp)
        )
    }
}

@Composable
private fun TradeAnalysisBody(
    instrumentDisplay: String,
    instrumentId: String,
    mode: String,
    analyzedAt: String,
    analysis: JSONObject,
    onOpenBrokerInfo: (() -> Unit)?
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var showBrokerCard by remember { mutableStateOf(true) }
    var showLotSheet by remember { mutableStateOf(false) }

    val direction = analysis.optString("direction", "").uppercase()
    val noTrade = direction == "NO_TRADE"
    val isLong = direction == "LONG"
    val dirColor = when {
        isLong -> BullGreen
        direction == "SHORT" -> BearRed
        else -> TextSecondary
    }
    val confidence = analysis.optInt("confidence", 0)
    val entryZone = analysis.optJSONObject("entryZone")
    val takeProfits = analysis.optJSONArray("takeProfits")
    val stopLoss = analysis.optDouble("stopLoss", Double.NaN)
    val rr = analysis.optDouble("riskReward", 0.0)
    val estimatedDuration = analysis.optString("estimatedDuration", "")
    val thesis = analysis.optString("thesis", "")
    val invalidation = analysis.optString("invalidation", "")
    val keyLevels = analysis.optJSONArray("keyLevels")

    // --- Logo row ---
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = "MarketScope AI",
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text("MarketScope AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(14.dp))

    // --- "Saved" badge (true statement: every analysis is persisted to the
    // database at creation time) ---
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(SurfaceLight)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Save, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Saved", style = MaterialTheme.typography.labelMedium, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
    }

    Spacer(Modifier.height(10.dp))

    // --- Recommended broker card (dismissible) ---
    if (showBrokerCard && onOpenBrokerInfo != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Recommended broker for MarketScope AI",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Executions similar to what we test MarketScope AI's analysis against.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Learn why",
                style = MaterialTheme.typography.labelMedium,
                color = AccentCyan,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onOpenBrokerInfo() }
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { showBrokerCard = false }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
    }

    // --- Lot-size calculator entry ---
    if (!noTrade) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { showLotSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50)),
                colors = ButtonDefaults.buttonColors(containerColor = NavyBlack),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp)
            ) {
                Icon(Icons.Filled.Calculate, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get lotsize for this trade", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Sizing uses the AI's entry & stop — adjust your risk to your own comfort.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    // --- Chip row: PAIR / BIAS / TIME / DATE ---
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        InfoChip("PAIR", instrumentDisplay, Modifier.weight(1f))
        InfoChip("BIAS", mode.replaceFirstChar { it.uppercase() }.ifEmpty { "—" }, Modifier.weight(1f))
        InfoChip("TIME", formatTimeOfDay(analyzedAt), Modifier.weight(1f))
        InfoChip("DATE", formatDate(analyzedAt), Modifier.weight(1.4f))
    }

    Spacer(Modifier.height(16.dp))

    // --- Risk-management tip banner ---
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GoldAmber.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Tip: when price reaches your first take profit, consider moving your stop to break-even so the trade can no longer turn into a loss.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }

    Spacer(Modifier.height(18.dp))

    val copy: (String) -> Unit = { value ->
        clipboard.setText(AnnotatedString(value))
        Toast.makeText(context, "Copied \"$value\"", Toast.LENGTH_SHORT).show()
    }

    // --- SNAPSHOT grid ---
    SectionHeader("SNAPSHOT")
    Spacer(Modifier.height(10.dp))

    val trendLabel = when (direction) {
        "LONG" -> "Bullish"
        "SHORT" -> "Bearish"
        else -> "Neutral"
    }
    val ideaLabel = when (direction) {
        "LONG" -> "Buy"
        "SHORT" -> "Sell"
        else -> "No Trade"
    }
    val strengthLabel = when {
        confidence >= 75 -> "Strong"
        confidence >= 50 -> "Moderate"
        else -> "Weak"
    }

    // Row 1: TREND + TRADE IDEA
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        SnapshotCard(
            title = "TREND", value = trendLabel, color = dirColor, weight = 1f, onCopy = { copy(trendLabel) },
            icon = if (isLong) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown
        )
        SnapshotCard(title = "TRADE IDEA", value = ideaLabel, color = dirColor, weight = 1f, onCopy = { copy(ideaLabel) })
    }
    Spacer(Modifier.height(10.dp))

    if (!noTrade) {
        val entryText = if (entryZone != null)
            "${formatPrice(entryZone.optDouble("low"))} – ${formatPrice(entryZone.optDouble("high"))}"
        else "—"
        val initialTp = if (takeProfits != null && takeProfits.length() > 0) formatPrice(takeProfits.optDouble(0)) else "—"
        val finalTp = if (takeProfits != null && takeProfits.length() > 0) formatPrice(takeProfits.optDouble(takeProfits.length() - 1)) else "—"

        // Row 2: ENTRY + SL
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SnapshotCard(title = "ENTRY", value = entryText, weight = 1f, onCopy = { copy(entryText) })
            SnapshotCard(title = "SL", value = formatPrice(stopLoss), color = BearRed, weight = 1f, onCopy = { copy(formatPrice(stopLoss)) })
        }
        Spacer(Modifier.height(10.dp))
        // Row 3: INITIAL TP + FINAL TP
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SnapshotCard(title = "INITIAL TP", value = initialTp, color = BullGreen, weight = 1f, onCopy = { copy(initialTp) })
            SnapshotCard(title = "FINAL TP", value = finalTp, color = BullGreen, weight = 1f, onCopy = { copy(finalTp) })
        }
        Spacer(Modifier.height(10.dp))
        // Row 4: RR RATIO + STRENGTH
        val rrText = if (rr > 0) "1 : ${trimNum(rr)}" else "—"
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SnapshotCard(title = "RR RATIO", value = rrText, weight = 1f, onCopy = { copy(rrText) })
            SnapshotCard(title = "STRENGTH", value = strengthLabel, color = dirColor, weight = 1f, onCopy = { copy(strengthLabel) })
        }
        Spacer(Modifier.height(10.dp))
        // Row 5: ESTIMATE (real AI-generated duration)
        if (estimatedDuration.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SnapshotCard(title = "ESTIMATE", value = estimatedDuration, color = AccentCyan, weight = 1f, onCopy = { copy(estimatedDuration) })
            }
            Spacer(Modifier.height(6.dp))
        }
    } else {
        // NO_TRADE — show the derived labels but not fabricated trade levels.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SnapshotCard(title = "STRENGTH", value = strengthLabel, color = dirColor, weight = 1f, onCopy = { copy(strengthLabel) })
        }
        Spacer(Modifier.height(6.dp))
    }

    Spacer(Modifier.height(16.dp))

    // --- EXPLANATION ---
    if (thesis.isNotBlank()) {
        SectionHeader("EXPLANATION")
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .padding(16.dp)
        ) {
            Text(thesis, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, lineHeight = 20.sp)
            if (invalidation.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text("Invalidation", style = MaterialTheme.typography.labelMedium, color = BearRed, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(invalidation, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, lineHeight = 20.sp)
            }
            if (keyLevels != null && keyLevels.length() > 0) {
                Spacer(Modifier.height(12.dp))
                Text("Key levels", style = MaterialTheme.typography.labelMedium, color = AccentCyan, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    (0 until keyLevels.length()).joinToString("  •  ") { formatPrice(keyLevels.optDouble(it)) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showLotSheet && !noTrade) {
        LotSizeSheet(
            instrumentId = instrumentId,
            entryMid = if (entryZone != null) (entryZone.optDouble("low") + entryZone.optDouble("high")) / 2.0 else Double.NaN,
            stopLoss = stopLoss,
            onDismiss = { showLotSheet = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Position-size calculator bottom sheet
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LotSizeSheet(
    instrumentId: String,
    entryMid: Double,
    stopLoss: Double,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val instrument = remember(instrumentId) { InstrumentCatalog.byId(instrumentId.lowercase()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // User's real questionnaire capital (honest fallback if missing/zero).
    val capital = remember {
        val raw = SessionManager.questionnaireAnswers(context)?.capitalUsd?.toDoubleOrNull() ?: 0.0
        if (raw > 0.0) raw else 1_000.0
    }

    var entry by remember { mutableStateOf(if (entryMid.isNaN()) "" else trimNum(entryMid)) }
    var stop by remember { mutableStateOf(if (stopLoss.isNaN()) "" else trimNum(stopLoss)) }
    var distance by remember { mutableStateOf("") }
    var riskPct by remember { mutableStateOf("1") }
    var rate by remember { mutableStateOf("1") }
    var result by remember { mutableStateOf<LotResult?>(null) }

    val quoteCurrency = instrument?.quoteCurrency ?: "USD"
    val needsRate = quoteCurrency != "USD"

    val parse: (String) -> Double = { it.trim().toDoubleOrNull() ?: Double.NaN }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text("Trade details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                if (instrument != null) "${instrument.display} • point size ${trimNum(instrument.pointSize)}"
                else instrumentId.uppercase(),
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            Spacer(Modifier.height(16.dp))

            Text("Entry & Stop (optional)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SheetField(entry, { entry = it }, "Entry", Modifier.weight(1f))
                SheetField(stop, { stop = it }, "Stop", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    // Real computation from the instrument's actual point size:
                    // distance (points) = |entry - stop| / pointSize
                    // e.g. USD/JPY pointSize 0.01, entry 156.20, stop 156.60 → 40.0
                    val e = parse(entry); val s = parse(stop)
                    val ps = instrument?.pointSize ?: Double.NaN
                    if (!e.isNaN() && !s.isNaN() && !ps.isNaN() && ps > 0) {
                        distance = String.format(Locale.US, "%.1f", kotlin.math.abs(e - s) / ps)
                        result = null
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate stoploss distance")
            }
            Spacer(Modifier.height(10.dp))

            SheetField(distance, { distance = it; result = null }, "Stop loss distance (points)", Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))

            SheetField(riskPct, { riskPct = it; result = null }, "Risk % of capital", Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))

            if (needsRate) {
                SheetField(rate, { rate = it; result = null }, "Exchange rate ($quoteCurrency → USD)", Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "MarketScope AI has no live FX feed for $quoteCurrency — enter your broker's current $quoteCurrency/USD rate for exact sizing.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Spacer(Modifier.height(10.dp))
            }

            Button(
                onClick = {
                    val d = parse(distance)
                    val rp = parse(riskPct)
                    val ex = parse(rate).takeUnless { it.isNaN() } ?: 1.0
                    val cs = instrument?.contractSize ?: Double.NaN
                    val ps = instrument?.pointSize ?: Double.NaN
                    if (d.isNaN() || rp.isNaN() || cs.isNaN() || ps.isNaN() || d <= 0 || rp <= 0 || ex <= 0) {
                        result = null
                        return@Button
                    }
                    // Worked formula (USD/JPY example in comments):
                    //  riskAmount = capital * riskPct/100          → $10,000 * 1% = $100
                    //  pipValuePerLotInQuote = contractSize*point  → 100,000 * 0.01 = 1,000 JPY/point/lot
                    //  pipValueUsd = quote-value / rate (non-USD quote) → 1,000 / 156.20 ≈ $6.401/point/lot
                    //  lots = riskAmount / (distance * pipValueUsd) → 100 / (40 * 6.401) ≈ 0.39
                    val riskAmount = capital * rp / 100.0
                    val pipValueInQuote = cs * ps
                    val pipValueUsd = if (needsRate) pipValueInQuote / ex else pipValueInQuote
                    val lots = riskAmount / (d * pipValueUsd)
                    result = LotResult(
                        lots = lots,
                        units = lots * cs,
                        riskAmount = riskAmount
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Text("Calculate position size", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))

            result?.let { r ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceDark)
                        .padding(16.dp)
                ) {
                    Text("Position Size: ${"%.2f".format(r.lots)} lots", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AccentCyan)
                    Spacer(Modifier.height(4.dp))
                    Text("Units: ${"%,.0f".format(r.units)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.height(2.dp))
                    Text("Risk Amount: $${"%,.2f".format(r.riskAmount)}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Spacer(Modifier.height(14.dp))
            }

            Text(
                "Estimates only — verify against your broker's exact contract specs before trading.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class LotResult(val lots: Double, val units: Double, val riskAmount: Double)

@Composable
private fun SheetField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }.take(12)) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccentCyan,
            unfocusedBorderColor = BorderSubtle,
            cursorColor = AccentCyan,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            disabledTextColor = TextMuted
        )
    )
}

// ---------------------------------------------------------------------------
// Small building blocks
// ---------------------------------------------------------------------------

@Composable
private fun InfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            color = TextPrimary
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = TextSecondary
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SnapshotCard(
    title: String,
    value: String,
    color: Color = TextPrimary,
    weight: Float = 1f,
    onCopy: (() -> Unit)? = null,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .weight(weight)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                if (icon != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1
            )
        }
        if (onCopy != null) {
            IconButton(onClick = onCopy, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = TextMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Formatting helpers
// ---------------------------------------------------------------------------

private fun formatPrice(v: Double): String {
    if (v.isNaN()) return "—"
    return if (v >= 1000) String.format("%,.0f", v) else String.format("%.4f", v)
}

private fun trimNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else String.format(Locale.US, "%.4f", v).trimEnd('0').trimEnd('.')

private fun parseIso(iso: String): Date? {
    val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")
    for (p in patterns) {
        try {
            val parser = SimpleDateFormat(p, Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            parser.parse(iso)?.let { return it }
        } catch (_: Exception) { }
    }
    return null
}

/** "7:15 PM" — local device time. */
private fun formatTimeOfDay(iso: String): String {
    val date = parseIso(iso) ?: return "—"
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}

/** "Sun, Sep 6, 2026" — local device date. */
private fun formatDate(iso: String): String {
    val date = parseIso(iso) ?: return "—"
    return SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(date)
}
