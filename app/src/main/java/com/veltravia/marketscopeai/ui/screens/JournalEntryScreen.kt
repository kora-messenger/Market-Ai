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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.json.JSONObject

/**
 * Journal entry editor — logs a REAL trade the trader actually took, or
 * edits/closes/reopens an existing one. A trade stays open until an exit
 * price is recorded; the backend then computes the honest outcome (R from
 * the trader's own stop distance, or recorded P&L). Nothing is pre-filled
 * from an AI analysis: this is the trader's own record, and the lesson
 * field is where the "outcome -> lesson" half of the loop happens.
 */
@Composable
fun JournalEntryScreen(
    entryId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }
    val isNew = entryId.isBlank()

    var loading by remember { mutableStateOf(!isNew) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var instrument by remember { mutableStateOf("") }
    var assetClass by remember { mutableStateOf("fx") }
    var direction by remember { mutableStateOf("BUY") }
    var entryPrice by remember { mutableStateOf("") }
    var exitPrice by remember { mutableStateOf("") }
    var stopLoss by remember { mutableStateOf("") }
    var takeProfit by remember { mutableStateOf("") }
    var positionSize by remember { mutableStateOf("") }
    var riskAmount by remember { mutableStateOf("") }
    var pnl by remember { mutableStateOf("") }
    var setupTag by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var lesson by remember { mutableStateOf("") }

    // Load the existing entry so the form edits real values, never blanks
    // that would silently wipe the trader's record.
    LaunchedEffect(entryId) {
        if (isNew) return@LaunchedEffect
        scope.launch {
            try {
                val json = ApiClient.fetchJournal(token)
                val arr = json.optJSONArray("entries") ?: org.json.JSONArray()
                var found: JSONObject? = null
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    if (e.optString("id") == entryId) { found = e; break }
                }
                val e = found ?: throw ApiClient.MarketAiException("Journal entry not found.")
                instrument = e.optString("instrument")
                assetClass = e.optString("assetClass").ifBlank { "other" }
                direction = e.optString("direction").ifBlank { "BUY" }
                entryPrice = e.opt("entryPrice")?.let { if (it != JSONObject.NULL) it.toString() else "" } ?: ""
                exitPrice = e.opt("exitPrice")?.let { if (it != JSONObject.NULL) it.toString() else "" } ?: ""
                stopLoss = e.opt("stopLoss")?.let { if (it != JSONObject.NULL) it.toString() else "" } ?: ""
                takeProfit = e.opt("takeProfit")?.let { if (it != JSONObject.NULL) it.toString() else "" } ?: ""
                positionSize = e.opt("positionSize")?.let { if (it != JSONObject.NULL) it.toString() else "" } ?: ""
                riskAmount = e.opt("riskAmount")?.let { if (it != JSONObject.NULL) it.toString() else "" } ?: ""
                pnl = e.opt("pnl")?.let { if (it != JSONObject.NULL) it.toString() else "" } ?: ""
                setupTag = e.optString("setupTag")
                notes = e.optString("notes")
                lesson = e.optString("lesson")
            } catch (err: Exception) {
                loadError = err.message ?: "Could not load the trade."
            } finally {
                loading = false
            }
        }
    }

    fun save() {
        if (saving) return
        if (instrument.isBlank()) { saveError = "Instrument is required."; return }
        val entry = entryPrice.trim().toDoubleOrNull()
        if (entry == null || entry <= 0) { saveError = "A valid entry price is required."; return }
        val exit = exitPrice.trim().toDoubleOrNull()
        if (exitPrice.isNotBlank() && (exit == null || exit <= 0)) { saveError = "Exit price must be a positive number."; return }
        saving = true
        saveError = null
        scope.launch {
            try {
                val fields = JSONObject().apply {
                    put("instrument", instrument.trim())
                    put("direction", direction)
                    put("entryPrice", entry)
                    put("assetClass", assetClass)
                    // An explicit null reopens a closed trade; omitted stays open on create.
                    if (isNew && exitPrice.isNotBlank()) put("exitPrice", exit)
                    if (!isNew) {
                        if (exitPrice.isNotBlank()) put("exitPrice", exit) else put("exitPrice", JSONObject.NULL)
                    }
                    stopLoss.trim().toDoubleOrNull()?.let { put("stopLoss", it) }
                    takeProfit.trim().toDoubleOrNull()?.let { put("takeProfit", it) }
                    positionSize.trim().toDoubleOrNull()?.let { put("positionSize", it) }
                    riskAmount.trim().toDoubleOrNull()?.let { put("riskAmount", it) }
                    pnl.trim().toDoubleOrNull()?.let { put("pnl", it) }
                    if (setupTag.isNotBlank()) put("setupTag", setupTag.trim())
                    if (notes.isNotBlank()) put("notes", notes.trim())
                    if (lesson.isNotBlank()) put("lesson", lesson.trim())
                }
                if (isNew) ApiClient.createJournalEntry(token, fields)
                else ApiClient.updateJournalEntry(token, entryId, fields)
                onBack()
            } catch (err: Exception) {
                saveError = err.message ?: "Could not save the trade."
                saving = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                if (isNew) "Log a trade" else "Edit trade",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        when {
            loading -> Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
            loadError != null -> Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadError ?: "", color = BearRed)
                Spacer(Modifier.height(10.dp))
                Button(onClick = onBack) { Text("Back") }
            }
            else -> {
                Spacer(Modifier.height(12.dp))
                FieldLabel("Instrument")
                OutlinedTextField(
                    value = instrument,
                    onValueChange = { instrument = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g. XAU/USD or AAPL", color = TextMuted) },
                    colors = JournalFieldColors()
                )
                Spacer(Modifier.height(12.dp))

                FieldLabel("What did you trade?")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("fx" to "FX", "crypto" to "Crypto", "stock" to "Stock", "other" to "Other").forEach { (key, label) ->
                        FilterChip(
                            selected = assetClass == key,
                            onClick = { assetClass = key },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCyan,
                                selectedLabelColor = Color.White,
                                containerColor = SurfaceLight
                            )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                FieldLabel("Direction")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DirectionChip("BUY", direction == "BUY", BullGreen) { direction = "BUY" }
                    DirectionChip("SELL", direction == "SELL", BearRed) { direction = "SELL" }
                }
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Entry price *")
                        OutlinedTextField(
                            value = entryPrice,
                            onValueChange = { entryPrice = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = JournalFieldColors()
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Exit price")
                        OutlinedTextField(
                            value = exitPrice,
                            onValueChange = { exitPrice = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            placeholder = { Text("blank = still open", color = TextMuted) },
                            colors = JournalFieldColors()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Stop loss")
                        OutlinedTextField(
                            value = stopLoss,
                            onValueChange = { stopLoss = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = JournalFieldColors()
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Take profit")
                        OutlinedTextField(
                            value = takeProfit,
                            onValueChange = { takeProfit = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = JournalFieldColors()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Position size")
                        OutlinedTextField(
                            value = positionSize,
                            onValueChange = { positionSize = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            placeholder = { Text("lots / shares", color = TextMuted) },
                            colors = JournalFieldColors()
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Risk amount")
                        OutlinedTextField(
                            value = riskAmount,
                            onValueChange = { riskAmount = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = JournalFieldColors()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                FieldLabel("P&L (if you know it)")
                OutlinedTextField(
                    value = pnl,
                    onValueChange = { pnl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("your account currency", color = TextMuted) },
                    colors = JournalFieldColors()
                )
                Spacer(Modifier.height(12.dp))

                FieldLabel("Setup")
                OutlinedTextField(
                    value = setupTag,
                    onValueChange = { setupTag = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("e.g. London breakout, 4H pullback", color = TextMuted) },
                    colors = JournalFieldColors()
                )
                Spacer(Modifier.height(12.dp))

                FieldLabel("Notes")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                    placeholder = { Text("What did you see when you took it?", color = TextMuted) },
                    colors = JournalFieldColors()
                )
                Spacer(Modifier.height(12.dp))

                FieldLabel("Lesson")
                OutlinedTextField(
                    value = lesson,
                    onValueChange = { lesson = it },
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                    placeholder = { Text("What did the outcome teach you?", color = TextMuted) },
                    colors = JournalFieldColors()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "R and win rate are computed from your own stop distance and exit. A closed trade with neither R nor P&L stays undecided — it is never counted as a win or a loss.",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )

                if (saveError != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(saveError ?: "", color = BearRed, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { save() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.height(18.dp).width(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isNew) "Save trade" else "Update trade")
                    }
                }

                if (!isNew) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.height(16.dp).width(16.dp), tint = BearRed)
                        Spacer(Modifier.width(6.dp))
                        Text("Remove from journal", color = BearRed)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove this trade?") },
            text = { Text("This permanently deletes the entry and its lesson from your journal.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        try {
                            ApiClient.deleteJournalEntry(token, entryId)
                            onBack()
                        } catch (err: Exception) {
                            saveError = err.message ?: "Could not delete the trade."
                        }
                    }
                }) { Text("Remove", color = BearRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Keep", color = TextMuted) } }
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun DirectionChip(label: String, selected: Boolean, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tint else SurfaceLight)
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else TextMuted,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun JournalFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = AccentCyan,
    cursorColor = AccentCyan
)
