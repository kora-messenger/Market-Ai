package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.components.PremiumSegmentedProgress
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * The 4th step of onboarding — right after the 3-page questionnaire, before
 * notifications/projection/broker setup and the first analysis. Reference layout:
 * step progress bar, centered icon badge, two-tone headline, member-count strip,
 * three benefit rows, single CTA. Every sentence here is our own wording (same
 * meaning as the reference, different words, per the no-verbatim-copy rule); the
 * member count is a REAL fetched total, never a hardcoded number — if the fetch
 * fails we show a plain caption with no fabricated figure. "Join" is a real,
 * backend-persisted action (POST /api/community/join), not a cosmetic transition.
 */
@Composable
fun CommunityIntroScreen(onJoined: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val alreadyJoined = remember { SessionManager.communityJoined(context) }
    var memberTotal by remember { mutableStateOf(-1) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { ApiClient.fetchCommunityStats() }.getOrNull()?.let {
            memberTotal = it.optInt("totalMembers", -1)
        }
    }

    fun proceed() {
        val token = SessionManager.sessionToken(context)
        if (token.isNullOrBlank()) {
            // No session token means the backend session/database isn't configured yet —
            // don't block onboarding on an optional feature; just continue.
            onJoined()
            return
        }
        joining = true
        error = null
        scope.launch {
            try {
                ApiClient.joinCommunity(token)
                SessionManager.setCommunityJoined(context, true)
                onJoined()
            } catch (e: Exception) {
                error = e.message ?: "Couldn't join right now. Please try again."
            } finally {
                joining = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PremiumSegmentedProgress(current = 3, total = 4, modifier = Modifier.weight(1f))
            Text(
                "First signal",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(40.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AccentViolet.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Groups,
                    contentDescription = null,
                    tint = AccentViolet,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "You've been figuring this out alone.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                "Not anymore.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = AccentCyan,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "Free community access unlocks the second you finish setup.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val dotColors = listOf(AccentViolet, AccentCyan, Color(0xFF16A34A), Color(0xFFD97706))
                val dotSize = 28.dp
                val overlap = 10.dp
                Box(
                    modifier = Modifier.width(dotSize + overlap * (dotColors.size - 1)).height(dotSize)
                ) {
                    dotColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .offset(x = overlap * index)
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.85f))
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (memberTotal > 0) {
                    Text(
                        formatMemberCount(memberTotal),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        "Growing every day",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CommunityBenefitRow(
                icon = Icons.Filled.RecordVoiceOver,
                title = "Mentor updates",
                description = "See what the desk is watching, live"
            )
            CommunityBenefitRow(
                icon = Icons.Filled.ShowChart,
                title = "Free signals",
                description = "Selected setups, before they go Pro-only"
            )
            CommunityBenefitRow(
                icon = Icons.Filled.CheckCircle,
                title = "Member wins",
                description = "Real results shared by real members"
            )
        }

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        GradientPrimaryButton(
            text = if (alreadyJoined) "Continue" else "Unlock Community Access",
            enabled = !joining,
            loading = joining,
            onClick = { proceed() },
            showArrow = !alreadyJoined,
            shape = RoundedCornerShape(50),
            height = 54.dp
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun CommunityBenefitRow(icon: ImageVector, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceLight),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentCyan.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

/** "1.2k" / "834" style compact count — always derived from a real fetched total. */
private fun formatMemberCount(total: Int): String {
    val label = if (total >= 1000) {
        val thousands = total / 1000.0
        "${"%.1f".format(thousands).removeSuffix(".0")}k"
    } else {
        total.toString()
    }
    return "$label traders already in"
}
