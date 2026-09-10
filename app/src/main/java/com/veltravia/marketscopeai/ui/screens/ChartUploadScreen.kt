package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.ui.theme.TextMuted

/**
 * Main analyze flow — same 3-in-1 AnalyzeFlow as onboarding (one analyze
 * experience across the whole app). The instrument from Home's watchlist is
 * preselected on the matching tab, and the CTA reads "Run AI Analysis"
 * (onboarding's first analysis says "Analyze Now!").
 */
@Composable
fun ChartUploadScreen(
    instrumentId: String,
    onBack: () -> Unit,
    onAnalysisComplete: (String) -> Unit,
    // Free tier exhausted its daily analyses (429) — offer the real upgrade path.
    onUpgradeRequired: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    "Analyze the market",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Upload two chart screenshots or research a stock",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // --- THE shared 3-in-1 analyze experience, with the watchlist's
        // instrument preselected on its matching tab. ---
        AnalyzeFlow(
            ctaLabel = "Run AI Analysis",
            onAnalysisComplete = onAnalysisComplete,
            onTrialExpired = onUpgradeRequired,
            onUpgradeRequired = onUpgradeRequired,
            initialInstrumentId = instrumentId,
            modifier = Modifier.weight(1f)
        )
    }
}
