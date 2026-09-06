package com.veltravia.marketai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketai.data.Instrument
import com.veltravia.marketai.data.InstrumentCatalog
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.BorderSubtle
import com.veltravia.marketai.ui.theme.SurfaceDark
import com.veltravia.marketai.ui.theme.SurfaceLight
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextPrimary
import com.veltravia.marketai.ui.theme.TextSecondary
import java.util.Locale

/**
 * Standalone position-size (lot-size) calculator — reachable from the Home
 * screen quick actions without needing an existing AI analysis. Uses the
 * exact same real formula as the calculator on the Trade Analysis screen:
 *
 *   distance (points)      = |entry - stop| / instrument.pointSize
 *   pipValuePerLotInQuote   = contractSize * pointSize
 *   pipValueUsd             = pipValuePerLotInQuote / rate   (if quote != USD)
 *   riskAmount               = capital * riskPct / 100
 *   lots                     = riskAmount / (distance * pipValueUsd)
 *
 * Worked check (USD/JPY): capital $10,000, risk 1%, distance 40 points,
 * rate 156.20 → riskAmount $100, pipValueUsd ≈ $6.40 → lots ≈ 0.39.
 */
@Composable
fun RiskCalculatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var instrument by remember { mutableStateOf<Instrument?>(null) }

    var capital by remember {
        mutableStateOf(
            SessionManager.questionnaireAnswers(context)?.capitalUsd?.takeIf { it.isNotBlank() } ?: ""
        )
    }
    var entry by remember { mutableStateOf("") }
    var stop by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var riskPct by remember { mutableStateOf("1") }
    var rate by remember { mutableStateOf("1") }
    var result by remember { mutableStateOf<LotResult?>(null) }

    val quoteCurrency = instrument?.quoteCurrency ?: "USD"
    val needsRate = quoteCurrency != "USD"
    val parse: (String) -> Double = { it.trim().toDoubleOrNull() ?: Double.NaN }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Risk Calculator",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Work out your real position size before you trade — using your instrument's actual contract specs.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(20.dp))

        Text("Instrument", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceLight)
                .clickable { pickerOpen = !pickerOpen }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                instrument?.display ?: "Select an instrument",
                color = if (instrument != null) TextPrimary else TextMuted,
                fontWeight = FontWeight.Medium
            )
            Text(if (pickerOpen) "▲" else "▼", color = TextMuted)
        }

        if (pickerOpen) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search instruments", color = TextMuted) },
                singleLine = true,
                colors = fieldColors()
            )
            Spacer(Modifier.height(8.dp))
            val filtered = remember(query) {
                InstrumentCatalog.all.filter {
                    query.isBlank() || it.display.contains(query, ignoreCase = true)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceLight)
            ) {
                LazyColumn {
                    items(filtered) { item ->
                        Text(
                            item.display,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    instrument = item
                                    pickerOpen = false
                                    query = ""
                                    result = null
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Your capital (USD)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        NumberField(capital, { capital = it; result = null }, "e.g. 10000")

        Spacer(Modifier.height(20.dp))
        Text("Entry & Stop (optional)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(entry, { entry = it }, "Entry", Modifier.weight(1f))
            NumberField(stop, { stop = it }, "Stop", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val e = parse(entry); val s = parse(stop)
                val ps = instrument?.pointSize ?: Double.NaN
                if (!e.isNaN() && !s.isNaN() && !ps.isNaN() && ps > 0) {
                    distance = String.format(Locale.US, "%.1f", kotlin.math.abs(e - s) / ps)
                    result = null
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = instrument != null
        ) {
            Text("Generate stoploss distance")
        }

        Spacer(Modifier.height(16.dp))
        Text("Stop loss distance (points)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        NumberField(distance, { distance = it; result = null }, "e.g. 40")

        Spacer(Modifier.height(16.dp))
        Text("Risk % of capital", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        NumberField(riskPct, { riskPct = it; result = null }, "e.g. 1")

        if (needsRate) {
            Spacer(Modifier.height(16.dp))
            Text("Exchange rate ($quoteCurrency → USD)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            NumberField(rate, { rate = it; result = null }, "e.g. 156.20")
            Spacer(Modifier.height(4.dp))
            Text(
                "No live FX feed for $quoteCurrency — enter your broker's current rate for exact sizing.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                val cap = parse(capital)
                val d = parse(distance)
                val rp = parse(riskPct)
                val ex = parse(rate).takeUnless { it.isNaN() } ?: 1.0
                val cs = instrument?.contractSize ?: Double.NaN
                val ps = instrument?.pointSize ?: Double.NaN
                if (cap.isNaN() || d.isNaN() || rp.isNaN() || cs.isNaN() || ps.isNaN() || cap <= 0 || d <= 0 || rp <= 0 || ex <= 0) {
                    result = null
                    return@Button
                }
                val riskAmount = cap * rp / 100.0
                val pipValueInQuote = cs * ps
                val pipValueUsd = if (needsRate) pipValueInQuote / ex else pipValueInQuote
                val lots = riskAmount / (d * pipValueUsd)
                result = LotResult(lots = lots, units = lots * cs, riskAmount = riskAmount)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
            enabled = instrument != null
        ) {
            Text("Calculate position size", fontWeight = FontWeight.SemiBold)
        }

        result?.let { r ->
            Spacer(Modifier.height(16.dp))
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
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Estimates only — verify against your broker's exact contract specs before trading.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
        Spacer(Modifier.height(32.dp))
    }
}

private data class LotResult(val lots: Double, val units: Double, val riskAmount: Double)

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(12)) },
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = fieldColors()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AccentCyan,
    unfocusedBorderColor = BorderSubtle,
    cursorColor = AccentCyan,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    disabledTextColor = TextMuted
)
