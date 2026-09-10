package com.veltravia.marketscopeai.ui.screens

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.Instrument
import com.veltravia.marketscopeai.data.InstrumentCatalog
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.components.PremiumSegmentedTabs
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * THE analyze experience - one 3-in-1 flow rendered identically everywhere:
 * Forex / Crypto / Stocks pages swipe inside a single pager, each with its own
 * instructions, its own instrument list and strict category handling (a forex
 * screenshot uploaded on the crypto page is rejected server-side with a clear
 * "upload the correct crypto chart" message).
 *
 * The only per-flow difference is the CTA label: onboarding's first analysis
 * says "Analyze Now!", the main flow says "Run AI Analysis".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnalyzeFlow(
    ctaLabel: String,
    onAnalysisComplete: (String) -> Unit,
    onTrialExpired: () -> Unit,
    onUpgradeRequired: () -> Unit = onTrialExpired,
    // Main flow arrives from Home's watchlist with a preselected instrument -
    // the flow opens on the matching tab with it preselected.
    initialInstrumentId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initial = initialInstrumentId?.let { InstrumentCatalog.byId(it) }
    val initialIsCrypto = initial?.kind == "crypto"
    val pagerState = rememberPagerState(initialPage = if (initialIsCrypto) 1 else 0) { 3 }

    // --- Per-market state: each tab keeps its own selections so switching
    // tabs never wipes what the user already uploaded. ---
    var fxInstrument by remember { mutableStateOf(if (initialIsCrypto) null else initial) }
    var crInstrument by remember { mutableStateOf(if (initialIsCrypto) initial else null) }
    var fxPickerOpen by rememberSaveable { mutableStateOf(false) }
    var crPickerOpen by rememberSaveable { mutableStateOf(false) }
    var fxImageH4 by remember { mutableStateOf<Uri?>(null) }
    var fxImageM15 by remember { mutableStateOf<Uri?>(null) }
    var crImageH4 by remember { mutableStateOf<Uri?>(null) }
    var crImageM15 by remember { mutableStateOf<Uri?>(null) }
    var mode by rememberSaveable { mutableStateOf("scalp") }
    var stockName by rememberSaveable { mutableStateOf("") }
    var stockImage by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var fxError by remember { mutableStateOf<String?>(null) }
    var crError by remember { mutableStateOf<String?>(null) }
    var stkError by remember { mutableStateOf<String?>(null) }

    val pickFxH4 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { fxImageH4 = it } }
    val pickFxM15 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { fxImageM15 = it } }
    val pickCrH4 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { crImageH4 = it } }
    val pickCrM15 = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { crImageM15 = it } }
    val pickStock = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { stockImage = it } }

    fun launchPick(launcher: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>) {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    /** Chart analysis (forex / crypto pages) - one shared path. */
    fun doChartAnalysis(inst: Instrument?, h4: Uri?, m15: Uri?, onError: (String?) -> Unit) {
        if (inst == null || h4 == null || m15 == null) return
        loading = true
        onError(null)
        scope.launch {
            try {
                val token = SessionManager.sessionToken(context)
                if (token == null) {
                    loading = false
                    onError("Not signed in")
                    return@launch
                }
                val dataH4 = ApiClient.prepareChartImage(context, h4)
                val dataM15 = ApiClient.prepareChartImage(context, m15)
                val result = ApiClient.analyze(token, inst.id, mode, dataH4, dataM15)
                val id = result.optString("id", "")
                loading = false
                if (id.isNotEmpty()) onAnalysisComplete(id)
                else onError("Analysis completed but was not saved")
            } catch (e: ApiClient.TrialExpiredException) {
                loading = false
                onTrialExpired()
            } catch (e: ApiClient.DailyLimitException) {
                loading = false
                onUpgradeRequired()
            } catch (e: Exception) {
                loading = false
                onError(e.message ?: "Analysis failed")
            }
        }
    }

    /** Stock analysis (stocks page) - typed name or screenshot, real data. */
    fun doStockAnalysis(name: String, image: Uri?, onError: (String?) -> Unit) {
        if (name.isBlank() && image == null) return
        loading = true
        onError(null)
        scope.launch {
            try {
                val token = SessionManager.sessionToken(context)
                if (token == null) {
                    loading = false
                    onError("Not signed in")
                    return@launch
                }
                val imageData = image?.let { ApiClient.prepareChartImage(context, it) }
                val result = ApiClient.analyzeStock(token, name.trim(), imageData)
                val id = result.optString("id", "")
                loading = false
                if (id.isNotEmpty()) onAnalysisComplete(id)
                else onError("Analysis completed but was not saved")
            } catch (e: ApiClient.TrialExpiredException) {
                loading = false
                onTrialExpired()
            } catch (e: ApiClient.DailyLimitException) {
                loading = false
                onUpgradeRequired()
            } catch (e: Exception) {
                loading = false
                onError(e.message ?: "Analysis failed")
            }
        }
    }

    Column(modifier = modifier) {
        // --- The 3-in-1 selector: tabs drive the pager and vice versa ---
        PremiumSegmentedTabs(
            tabs = listOf("Forex", "Crypto", "Stocks"),
            selectedIndex = pagerState.currentPage,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                // ----------------------------- FOREX -----------------------------
                0 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 20.dp)
                ) {
                    AnalyzeTabInfo(
                        icon = { Icon(Icons.Filled.CurrencyExchange, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp)) },
                        text = "You're on the Forex tab. Upload your 4H and 15M forex chart screenshots here for the best results - a crypto or stock screenshot won't be accepted on this tab."
                    )
                    Spacer(Modifier.height(20.dp))
                    AnalyzeSectionLabel("Choose instrument")
                    Spacer(Modifier.height(10.dp))
                    AnalyzeInstrumentRow(fxInstrument, onClick = { fxPickerOpen = true })
                    Spacer(Modifier.height(24.dp))
                    AnalyzeChartsSection(
                        imageH4 = fxImageH4,
                        imageM15 = fxImageM15,
                        onPickH4 = { launchPick(pickFxH4) },
                        onPickM15 = { launchPick(pickFxM15) },
                        onClearH4 = { fxImageH4 = null },
                        onClearM15 = { fxImageM15 = null }
                    )
                    Spacer(Modifier.height(24.dp))
                    AnalyzeModeSection(mode = mode, onModeChange = { mode = it })
                    Spacer(Modifier.height(24.dp))
                    fxError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                    }
                    GradientPrimaryButton(
                        text = ctaLabel,
                        enabled = !loading && fxInstrument != null && fxImageH4 != null && fxImageM15 != null,
                        loading = loading,
                        height = 54.dp,
                        onClick = { doChartAnalysis(fxInstrument, fxImageH4, fxImageM15) { fxError = it } }
                    )
                    AnalyzeDisclaimerFooter()
                }

                // ----------------------------- CRYPTO ----------------------------
                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 20.dp)
                ) {
                    AnalyzeTabInfo(
                        icon = { Icon(Icons.Filled.CurrencyBitcoin, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp)) },
                        text = "You're on the Crypto tab. Upload your 4H and 15M crypto chart screenshots here for the best results - a forex or stock screenshot won't be accepted on this tab."
                    )
                    Spacer(Modifier.height(20.dp))
                    AnalyzeSectionLabel("Choose instrument")
                    Spacer(Modifier.height(10.dp))
                    AnalyzeInstrumentRow(crInstrument, onClick = { crPickerOpen = true })
                    Spacer(Modifier.height(24.dp))
                    AnalyzeChartsSection(
                        imageH4 = crImageH4,
                        imageM15 = crImageM15,
                        onPickH4 = { launchPick(pickCrH4) },
                        onPickM15 = { launchPick(pickCrM15) },
                        onClearH4 = { crImageH4 = null },
                        onClearM15 = { crImageM15 = null }
                    )
                    Spacer(Modifier.height(24.dp))
                    AnalyzeModeSection(mode = mode, onModeChange = { mode = it })
                    Spacer(Modifier.height(24.dp))
                    crError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                    }
                    GradientPrimaryButton(
                        text = ctaLabel,
                        enabled = !loading && crInstrument != null && crImageH4 != null && crImageM15 != null,
                        loading = loading,
                        height = 54.dp,
                        onClick = { doChartAnalysis(crInstrument, crImageH4, crImageM15) { crError = it } }
                    )
                    AnalyzeDisclaimerFooter()
                }

                // ----------------------------- STOCKS ----------------------------
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 20.dp)
                ) {
                    AnalyzeTabInfo(
                        icon = { Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp)) },
                        text = "Take a screenshot or input the name of the Stock you want MarketScope AI to analyze for you. We research its real market performance and tell you whether to buy - with a confidence rate."
                    )
                    Spacer(Modifier.height(20.dp))
                    AnalyzeSectionLabel("Which stock?")
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = stockName,
                        onValueChange = { stockName = it },
                        placeholder = {
                            Text(
                                "e.g. Accesscorp, AAPL, Dangote Sugar",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                    AnalyzeSectionLabel("Optional - stock screenshot")
                    Spacer(Modifier.height(10.dp))
                    AnalyzeSingleChartTile(
                        imageUri = stockImage,
                        emptyLabel = "Add stock screenshot",
                        filledLabel = "Stock screenshot attached",
                        onPick = { launchPick(pickStock) },
                        onClear = { stockImage = null }
                    )
                    Spacer(Modifier.height(24.dp))
                    stkError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                    }
                    GradientPrimaryButton(
                        text = ctaLabel,
                        enabled = !loading && (stockName.isNotBlank() || stockImage != null),
                        loading = loading,
                        height = 54.dp,
                        onClick = { doStockAnalysis(stockName, stockImage) { stkError = it } }
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "MarketScope AI researches live exchange data - current price, performance from 1 week to 1 year, the 52-week range and more - before recommending a BUY, SELL or HOLD with a confidence percentage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // --- Category-scoped instrument pickers (one per chart tab) ---
    if (fxPickerOpen) {
        AnalyzeInstrumentPickerSheet(
            onDismiss = { fxPickerOpen = false },
            onSelect = { fxInstrument = it; fxPickerOpen = false },
            categories = listOf("Forex", "Metals", "Indices", "Synthetics")
        )
    }
    if (crPickerOpen) {
        AnalyzeInstrumentPickerSheet(
            onDismiss = { crPickerOpen = false },
            onSelect = { crInstrument = it; crPickerOpen = false },
            categories = listOf("Crypto")
        )
    }
}

/** Per-tab instruction banner - same visual language as the trial banner. */
@Composable
private fun AnalyzeTabInfo(
    icon: @Composable () -> Unit,
    text: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(30.dp),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}
