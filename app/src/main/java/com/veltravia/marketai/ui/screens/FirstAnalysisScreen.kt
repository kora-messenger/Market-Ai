package com.veltravia.marketai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import coil.compose.AsyncImage
import com.veltravia.marketai.data.ApiClient
import com.veltravia.marketai.data.Instrument
import com.veltravia.marketai.data.InstrumentCatalog
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.BorderSubtle
import com.veltravia.marketai.ui.theme.SurfaceDark
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Onboarding "first analysis" screen — mirrors the reference app's combined
 * instrument + chart-upload + trade-focus screen shown right after the broker
 * setup / screenshot guide. Key difference from the reference (which offers a
 * single free analysis, "This is on us!"): Market Ai gives every new account a
 * real 7-day full-access free trial, enforced server-side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstAnalysisScreen(
    onAnalysisComplete: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val user = remember { SessionManager.currentUser(context) }
    val firstName = remember(user) {
        (user?.name ?: "trader").trim().split(" ").first().ifBlank { "trader" }
    }
    val daysLeft = remember { SessionManager.trialDaysRemaining(context) }

    var instrument by remember { mutableStateOf<Instrument?>(null) }
    var imageH4 by remember { mutableStateOf<android.net.Uri?>(null) }
    var imageM15 by remember { mutableStateOf<android.net.Uri?>(null) }
    var mode by rememberSaveable { mutableStateOf("scalp") }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickH4 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { imageH4 = it }
    }
    val pickM15 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { imageM15 = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        // Personalized greeting, same energy as the reference recording.
        Text(
            "Okay ${'$'}{firstName.uppercase()},",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Let us carry out our first analysis for you.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))

        // 7-day free trial banner (real, enforced server-side — not the
        // reference app's one-time freebie).
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AccentCyan.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        if (SessionManager.isPremium(context)) "Premium is active — enjoy full access."
                        else if (daysLeft > 1) "Your first $daysLeft days are on us!"
                        else "Your last free day — every analysis included!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (SessionManager.isPremium(context)) "No limits on instruments, modes or charts."
                        else "7 days of full access. No card required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        // --- Choose instrument ---
        SectionLabel("Choose instrument")
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                .clickable { pickerOpen = true }
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

        Spacer(Modifier.height(24.dp))

        // --- Upload your charts ---
        SectionLabel("Upload Your Charts")
        Spacer(Modifier.height(6.dp))
        Text(
            "Use clear images with clear price number digits for the best results.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChartTile(
                imageUri = imageH4,
                emptyLabel = "Upload 4H Chart",
                filledLabel = "4H Chart",
                modifier = Modifier.weight(1f),
                onPick = {
                    pickH4.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onClear = { imageH4 = null }
            )
            ChartTile(
                imageUri = imageM15,
                emptyLabel = "Upload 15M Chart",
                filledLabel = "15M Chart",
                modifier = Modifier.weight(1f),
                onPick = {
                    pickM15.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onClear = { imageM15 = null }
            )
        }

        Spacer(Modifier.height(24.dp))

        // --- Trade focus ---
        SectionLabel("What's Your Trade Focus?")
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusOption(
                title = "Scalp",
                subtitle = "Quick moves, 15M",
                selected = mode == "scalp",
                onClick = { mode = "scalp" },
                modifier = Modifier.weight(1f)
            )
            FocusOption(
                title = "Swing",
                subtitle = "Wider targets, 4H",
                selected = mode == "swing",
                onClick = { mode = "swing" },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(28.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
        }

        Button(
            onClick = {
                val inst = instrument
                val h4 = imageH4
                val m15 = imageM15
                if (inst == null || h4 == null || m15 == null) return@Button
                loading = true
                error = null
                scope.launch {
                    try {
                        val token = SessionManager.sessionToken(context)
                        if (token == null) {
                            loading = false
                            error = "Not signed in"
                            return@launch
                        }
                        val dataH4 = ApiClient.prepareChartImage(context, h4)
                        val dataM15 = ApiClient.prepareChartImage(context, m15)
                        val result = ApiClient.analyze(token, inst.id, mode, dataH4, dataM15)
                        val id = result.optString("id", "")
                        loading = false
                        if (id.isNotEmpty()) {
                            onAnalysisComplete(id)
                        } else {
                            error = "Analysis completed but was not saved"
                        }
                    } catch (e: ApiClient.TrialExpiredException) {
                        loading = false
                        error = e.message ?: "Your free trial has ended."
                    } catch (e: Exception) {
                        loading = false
                        error = e.message ?: "Analysis failed"
                    }
                }
            },
            enabled = !loading && instrument != null && imageH4 != null && imageM15 != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCyan,
                contentColor = Color(0xFF06202A)
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color(0xFF06202A),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text("Analyzing your charts…", fontWeight = FontWeight.SemiBold)
            } else {
                Text("Analyze", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(18.dp))

        // Honest disclaimer footer (same wording style as the broker screen).
        Text(
            "This is not financial advice and should not be considered as such. Always do your own research and consult with a financial advisor before making any trading decisions.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(30.dp))
    }

    // --- Instrument picker bottom sheet (same real catalog as the picker screen) ---
    if (pickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { pickerOpen = false },
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
                        androidx.compose.material3.DropdownMenu(
                            expanded = categoryMenuOpen,
                            onDismissRequest = { categoryMenuOpen = false }
                        ) {
                            (listOf("All") + InstrumentCatalog.categories).forEach { option ->
                                androidx.compose.material3.DropdownMenuItem(
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
                                .clickable {
                                    instrument = inst
                                    pickerOpen = false
                                }
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
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ChartTile(
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
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove image",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Caption strip under the image, same idea as the FxLens reference
        // ("4H Chart" / "15M Chart") so the user can see which slot is which
        // at a glance once a screenshot is in place.
        if (imageUri != null) {
            Text(
                filledLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun FocusOption(
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
