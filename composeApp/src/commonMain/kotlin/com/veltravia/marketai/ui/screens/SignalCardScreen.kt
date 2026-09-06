package com.veltravia.marketai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketai.data.ApiClient
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.BearRed
import com.veltravia.marketai.ui.theme.BullGreen
import com.veltravia.marketai.ui.theme.BorderSubtle
import com.veltravia.marketai.ui.theme.SurfaceDark
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextSecondary
import com.veltravia.marketai.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Displays one stored AI signal — fetched by id from the backend,
 * so it works both after a fresh analysis and from the Saved history.
 */
@Composable
fun SignalCardScreen(
    analysisId: String,
    onBack: () -> Unit
) {
    var record by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(analysisId) {
        try {
            record = ApiClient.fetchAnalysis(analysisId)
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
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "AI Signal",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
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
                    Text("Loading signal…", color = TextMuted)
                }
            }
            else -> {
                val rec = record!!
                val analysis = rec.optJSONObject("analysis") ?: JSONObject()
                SignalCard(
                    instrument = analysis.optString("instrument", rec.optString("instrumentId", "").uppercase()),
                    mode = rec.optString("mode", ""),
                    analyzedAt = rec.optString("analyzedAt", ""),
                    analysis = analysis.optJSONObject("analysis") ?: analysis
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SignalCard(
    instrument: String,
    mode: String,
    analyzedAt: String,
    analysis: JSONObject
) {
    val direction = analysis.optString("direction", "").uppercase()
    val isLong = direction == "LONG"
    val dirColor = if (isLong) BullGreen else BearRed
    val confidence = analysis.optInt("confidence", 0)
    val entryZone = analysis.optJSONObject("entryZone")
    val takeProfits = analysis.optJSONArray("takeProfits")
    val keyLevels = analysis.optJSONArray("keyLevels")
    val rr = analysis.optDouble("riskReward", 0.0)

    Spacer(Modifier.height(16.dp))

    // Direction header
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        instrument,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        mode.replaceFirstChar { it.uppercase() } + " • " + formatTime(analyzedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(dirColor.copy(alpha = 0.16f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        direction,
                        color = dirColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Metric("Confidence", "$confidence%")
                Metric("Risk / Reward", if (rr > 0) "1 : $rr" else "—")
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    // Levels
    Panel("TRADE LEVELS") {
        LevelRow(
            "Entry Zone",
            if (entryZone != null)
                "${formatPrice(entryZone.optDouble("low"))} – ${formatPrice(entryZone.optDouble("high"))}"
            else "—"
        )
        LevelRow("Stop Loss", formatPrice(analysis.optDouble("stopLoss", Double.NaN)), valueColor = BearRed)
        if (takeProfits != null) {
            (0 until takeProfits.length()).forEach { i ->
                LevelRow("Take Profit ${i + 1}", formatPrice(takeProfits.optDouble(i)), valueColor = BullGreen)
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    // Thesis
    val thesis = analysis.optString("thesis", "")
    if (thesis.isNotEmpty()) {
        Panel("AI THESIS") {
            Text(thesis, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(14.dp))
    }

    val invalidation = analysis.optString("invalidation", "")
    if (invalidation.isNotEmpty()) {
        Panel("INVALIDATION") {
            Text(invalidation, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(14.dp))
    }

    if (keyLevels != null && keyLevels.length() > 0) {
        Panel("KEY LEVELS") {
            Text(
                (0 until keyLevels.length()).joinToString("  •  ") { formatPrice(keyLevels.optDouble(it)) },
                style = MaterialTheme.typography.bodyMedium,
                color = AccentCyan
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun Panel(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .padding(20.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = AccentCyan, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
private fun LevelRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = TextSecondary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

private fun formatPrice(v: Double): String {
    if (v.isNaN()) return "—"
    return if (v >= 1000) String.format("%,.0f", v) else String.format("%.4f", v)
}

private fun formatTime(iso: String): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    parser.timeZone = TimeZone.getTimeZone("UTC")
    val date = parser.parse(iso) ?: return iso
    val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
    fmt.timeZone = TimeZone.getTimeZone("Africa/Lagos")
    fmt.format(date)
} catch (e: Exception) {
    try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso) ?: return iso
        val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
        fmt.timeZone = TimeZone.getTimeZone("Africa/Lagos")
        fmt.format(date)
    } catch (e2: Exception) { iso }
}
