package com.veltravia.marketscopeai.ui.screens

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
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.Instrument
import com.veltravia.marketscopeai.data.InstrumentCatalog
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.SurfaceDark
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Onboarding "first analysis" screen — mirrors the reference app's combined
 * instrument + chart-upload + trade-focus screen shown right after the broker
 * setup / screenshot guide. Key difference from the reference (which offers a
 * single free analysis, "This is on us!"): MarketScope AI gives every new account a
 * real 7-day full-access free trial, enforced server-side.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstAnalysisScreen(
    onAnalysisComplete: (String) -> Unit,
    onTrialExpired: () -> Unit = {}
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
            "Okay ${firstName.uppercase()},",
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
        AnalyzeSectionLabel("Choose instrument")
        Spacer(Modifier.height(10.dp))
        AnalyzeInstrumentRow(instrument, onClick = { pickerOpen = true })

        Spacer(Modifier.height(24.dp))

        // --- Upload your charts (shared with the main analyze flow) ---
        AnalyzeChartsSection(
            imageH4 = imageH4,
            imageM15 = imageM15,
            onPickH4 = { pickH4.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPickM15 = { pickM15.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onClearH4 = { imageH4 = null },
            onClearM15 = { imageM15 = null }
        )

        Spacer(Modifier.height(24.dp))

        // --- Trade focus (shared with the main analyze flow) ---
        AnalyzeModeSection(mode = mode, onModeChange = { mode = it })

        Spacer(Modifier.height(28.dp))

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
                        if (id.isNotEmpty()) {
                            onAnalysisComplete(id)
                        } else {
                            error = "Analysis completed but was not saved"
                        }
                    } catch (e: ApiClient.TrialExpiredException) {
                        loading = false
                        error = e.message ?: "Your free trial has ended."
                        onTrialExpired()
                    } catch (e: ApiClient.DailyLimitException) {
                        loading = false
                        error = e.message
                        onTrialExpired()
                    } catch (e: Exception) {
                        loading = false
                        error = e.message ?: "Analysis failed"
                    }
                }
            }
        )

        AnalyzeDisclaimerFooter()
    }

    // --- Instrument picker (shared with the main analyze flow) ---
    if (pickerOpen) {
        AnalyzeInstrumentPickerSheet(
            onDismiss = { pickerOpen = false },
            onSelect = { instrument = it; pickerOpen = false }
        )
    }
}
