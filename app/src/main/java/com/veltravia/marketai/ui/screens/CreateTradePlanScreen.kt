package com.veltravia.marketai.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.veltravia.marketai.data.ApiClient
import com.veltravia.marketai.data.Instrument
import com.veltravia.marketai.data.InstrumentCatalog
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.AccentViolet
import com.veltravia.marketai.ui.theme.BearRed
import com.veltravia.marketai.ui.theme.BullGreen
import com.veltravia.marketai.ui.theme.SurfaceLight
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextPrimary
import com.veltravia.marketai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * A real trade plan: instrument, direction, planned entry/SL/TP levels and
 * free-form notes. Saved for real via POST /api/trade-plans — this is a
 * user-authored plan, distinct from the AI-generated saved signals list.
 */
@Composable
fun CreateTradePlanScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var pickerOpen by remember { mutableStateOf(false) }
    var instrument by remember { mutableStateOf<Instrument?>(null) }
    var direction by remember { mutableStateOf("LONG") }
    var entry by remember { mutableStateOf("") }
    var stopLoss by remember { mutableStateOf("") }
    var takeProfit by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSave = instrument != null && !saving

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
                "Create Trade Plan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Lay out your instrument, direction, and planned levels before you enter — real, saved to your account.",
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
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentCyan,
                    cursorColor = AccentCyan
                )
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
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Direction", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DirectionChip("Long", direction == "LONG", BullGreen) { direction = "LONG" }
            DirectionChip("Short", direction == "SHORT", BearRed) { direction = "SHORT" }
        }

        Spacer(Modifier.height(20.dp))
        Text("Planned levels (optional)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlanField(entry, { entry = it }, "Entry", Modifier.weight(1f))
            PlanField(stopLoss, { stopLoss = it }, "Stop loss", Modifier.weight(1f))
            PlanField(takeProfit, { takeProfit = it }, "Take profit", Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))
        Text("Notes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
            placeholder = { Text("Why this trade — thesis, invalidation, anything you want to remember.", color = TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentCyan,
                cursorColor = AccentCyan
            )
        )

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = BearRed, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                val inst = instrument ?: return@Button
                saving = true
                error = null
                scope.launch {
                    try {
                        val token = SessionManager.sessionToken(context)
                            ?: throw IllegalStateException("Not signed in")
                        ApiClient.createTradePlan(
                            sessionToken = token,
                            instrumentId = inst.id,
                            instrument = inst.display,
                            direction = direction,
                            entry = entry.toDoubleOrNull(),
                            stopLoss = stopLoss.toDoubleOrNull(),
                            takeProfit = takeProfit.toDoubleOrNull(),
                            notes = notes.ifBlank { null }
                        )
                        Toast.makeText(context, "Trade plan saved", Toast.LENGTH_SHORT).show()
                        onSaved()
                    } catch (e: Exception) {
                        error = e.message ?: "Could not save trade plan"
                    } finally {
                        saving = false
                    }
                }
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp)
            } else {
                Text("Save Trade Plan", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DirectionChip(label: String, selected: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color else SurfaceLight)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            color = if (selected) androidx.compose.ui.graphics.Color.White else TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PlanField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(12)) },
        modifier = modifier,
        placeholder = { Text(placeholder, color = TextMuted, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = AccentCyan,
            cursorColor = AccentCyan
        )
    )
}
