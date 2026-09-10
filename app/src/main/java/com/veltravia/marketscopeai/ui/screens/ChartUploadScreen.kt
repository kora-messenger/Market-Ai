package com.veltravia.marketscopeai.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.InstrumentCatalog
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * Main analyze flow — renders the exact same shared form sections as the
 * onboarding first-analysis screen (AnalyzeShared.kt), so every chart-analysis
 * experience in the app is identical. The instrument arrives pre-selected from
 * the Home watchlist but can be changed via the shared picker.
 */
@Composable
fun ChartUploadScreen(
    instrumentId: String,
    onBack: () -> Unit,
    onAnalysisComplete: (String) -> Unit,
    // Free tier exhausted its daily analyses (429) — offer the real upgrade path.
    onUpgradeRequired: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var instrument by remember { mutableStateOf(InstrumentCatalog.byId(instrumentId)) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var imageH4 by remember { mutableStateOf<Uri?>(null) }
    var imageM15 by remember { mutableStateOf<Uri?>(null) }
    var mode by rememberSaveable { mutableStateOf("scalp") }
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
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text(
                    instrument?.display ?: instrumentId,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Upload two chart screenshots and let AI break them down",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // --- Choose instrument (same section as onboarding) ---
        AnalyzeSectionLabel("Choose instrument")
        Spacer(Modifier.height(10.dp))
        AnalyzeInstrumentRow(instrument, onClick = { pickerOpen = true })

        Spacer(Modifier.height(24.dp))

        // --- Upload your charts (same section as onboarding) ---
        AnalyzeChartsSection(
            imageH4 = imageH4,
            imageM15 = imageM15,
            onPickH4 = { pickH4.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPickM15 = { pickM15.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onClearH4 = { imageH4 = null },
            onClearM15 = { imageM15 = null }
        )

        Spacer(Modifier.height(24.dp))

        // --- Trade focus (same section as onboarding) ---
        AnalyzeModeSection(mode = mode, onModeChange = { mode = it })

        Spacer(Modifier.height(24.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
        }

        GradientPrimaryButton(
            text = "Analyze Now!",
            enabled = !loading && instrument != null && imageH4 != null && imageM15 != null,
            loading = loading,
            height = 54.dp,
            onClick = {
                val inst = instrument
                val h4 = imageH4
                val m15 = imageM15
                if (inst == null || h4 == null || m15 == null) return@GradientPrimaryButton
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
                        if (id.isNotEmpty()) onAnalysisComplete(id)
                        else error = "Analysis completed but was not saved"
                    } catch (e: ApiClient.TrialExpiredException) {
                        loading = false
                        error = e.message ?: "Your free trial has ended."
                    } catch (e: ApiClient.DailyLimitException) {
                        loading = false
                        error = e.message
                        onUpgradeRequired()
                    } catch (e: Exception) {
                        loading = false
                        error = e.message ?: "Analysis failed"
                    }
                }
            }
        )

        AnalyzeDisclaimerFooter()
    }

    // --- Instrument picker (shared with the onboarding flow) ---
    if (pickerOpen) {
        AnalyzeInstrumentPickerSheet(
            onDismiss = { pickerOpen = false },
            onSelect = { instrument = it; pickerOpen = false }
        )
    }
}
