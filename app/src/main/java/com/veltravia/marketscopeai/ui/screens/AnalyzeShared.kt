package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.data.Instrument
import com.veltravia.marketscopeai.data.InstrumentCatalog
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.SurfaceDark
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextSecondary

/**
 * Shared analyze-form sections. Every screen that uploads charts for AI analysis
 * (onboarding first analysis + the main analyze flow) renders these exact same
 * sections, so the two flows look and feel identical.
 */

@Composable
internal fun AnalyzeSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

/** "Choose instrument" row — the one shared way to pick or show the instrument. */
@Composable
internal fun AnalyzeInstrumentRow(
    instrument: Instrument?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                instrument?.display ?: "Select Instrument",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (instrument != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (instrument != null) AccentCyan else TextSecondary
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Filled.ExpandMore, contentDescription = "Select instrument", tint = TextSecondary)
        }
    }
}

/** Instrument picker sheet with search + category filter (real catalog). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnalyzeInstrumentPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (Instrument) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark
    ) {
        var query by rememberSaveable { mutableStateOf("") }
        var categoryFilter by rememberSaveable { mutableStateOf("All") }
        var categoryMenuOpen by remember { mutableStateOf(false) }

        val filtered = remember(query, categoryFilter) {
            InstrumentCatalog.all.filter {
                (categoryFilter == "All" || it.category == categoryFilter) &&
                    it.display.contains(query.trim(), ignoreCase = true)
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "Select Instrument",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search instruments…") },
                    singleLine = true
                )
                Spacer(Modifier.width(10.dp))
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                            .clickable { categoryMenuOpen = true }
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Text(categoryFilter, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = "Filter category",
                            modifier = Modifier.size(18.dp),
                            tint = TextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = categoryMenuOpen,
                        onDismissRequest = { categoryMenuOpen = false }
                    ) {
                        (listOf("All") + InstrumentCatalog.categories).forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    categoryFilter = option
                                    categoryMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            LazyColumn {
                items(filtered) { inst ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                            .clickable { onSelect(inst) }
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Text(
                            inst.display,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            InstrumentCatalog.fullNameFor(inst),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}

/** "Upload Your Charts" — two side-by-side 1:1 tiles (4H + 15M). */
@Composable
internal fun AnalyzeChartsSection(
    imageH4: android.net.Uri?,
    imageM15: android.net.Uri?,
    onPickH4: () -> Unit,
    onPickM15: () -> Unit,
    onClearH4: () -> Unit,
    onClearM15: () -> Unit
) {
    AnalyzeSectionLabel("Upload Your Charts")
    Spacer(Modifier.height(6.dp))
    Text(
        "Use clear images with clear price number digits for the best results.",
        style = MaterialTheme.typography.bodySmall,
        color = TextMuted
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnalyzeChartTile(
            imageUri = imageH4,
            emptyLabel = "Upload 4H Chart",
            filledLabel = "4H Chart",
            modifier = Modifier.weight(1f),
            onPick = onPickH4,
            onClear = onClearH4
        )
        AnalyzeChartTile(
            imageUri = imageM15,
            emptyLabel = "Upload 15M Chart",
            filledLabel = "15M Chart",
            modifier = Modifier.weight(1f),
            onPick = onPickM15,
            onClear = onClearM15
        )
    }
}

@Composable
private fun AnalyzeChartTile(
    imageUri: android.net.Uri?,
    emptyLabel: String,
    filledLabel: String,
    modifier: Modifier = Modifier,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable(enabled = imageUri == null) { onPick() },
            contentAlignment = Alignment.Center
        ) {
            if (imageUri == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.AddAPhoto,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        emptyLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                AsyncImage(
                    model = imageUri,
                    contentDescription = filledLabel,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove " + filledLabel,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** "What's Your Trade Focus?" — Scalp / Swing. */
@Composable
internal fun AnalyzeModeSection(
    mode: String,
    onModeChange: (String) -> Unit
) {
    AnalyzeSectionLabel("What's Your Trade Focus?")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnalyzeFocusOption(
            title = "Scalp",
            subtitle = "Quick moves, 15M",
            selected = mode == "scalp",
            onClick = { onModeChange("scalp") },
            modifier = Modifier.weight(1f)
        )
        AnalyzeFocusOption(
            title = "Swing",
            subtitle = "Wider targets, 4H",
            selected = mode == "swing",
            onClick = { onModeChange("swing") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AnalyzeFocusOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentCyan.copy(alpha = 0.14f) else SurfaceDark)
            .border(
                1.dp,
                if (selected) AccentCyan else BorderSubtle,
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) AccentCyan else Color.Unspecified
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}

/** Honest disclaimer footer shared by every analyze screen. */
@Composable
internal fun AnalyzeDisclaimerFooter() {
    Spacer(Modifier.height(18.dp))
    Text(
        "This is not financial advice and should not be considered as such. Always do your own research and consult with a financial advisor before making any trading decisions.",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
        color = TextMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(30.dp))
}
