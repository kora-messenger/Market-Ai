package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.TextSecondary

/**
 * Onboarding "Let us carry out our first analysis for you" screen. Below the
 * greeting + trial banner it renders THE shared 3-in-1 analyze flow
 * (AnalyzeFlow) — identical to the main flow except the CTA reads
 * "Analyze Now!". Every new trader sees the same Forex / Crypto / Stocks
 * experience they'll use every day after onboarding.
 */
@Composable
fun FirstAnalysisScreen(
    onAnalysisComplete: (String) -> Unit,
    onTrialExpired: () -> Unit = {}
) {
    val context = LocalContext.current

    val user = remember { SessionManager.currentUser(context) }
    val firstName = remember(user) {
        (user?.name ?: "trader").trim().split(" ").first().ifBlank { "trader" }
    }
    val daysLeft = remember { SessionManager.trialDaysRemaining(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        Spacer(Modifier.height(20.dp))

        // --- THE shared 3-in-1 analyze experience (Forex / Crypto / Stocks).
        // Onboarding's only difference: the CTA says "Analyze Now!". ---
        AnalyzeFlow(
            ctaLabel = "Analyze Now!",
            onAnalysisComplete = onAnalysisComplete,
            onTrialExpired = onTrialExpired,
            modifier = Modifier.weight(1f)
        )
    }
}
