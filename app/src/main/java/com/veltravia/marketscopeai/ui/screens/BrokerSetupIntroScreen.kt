package com.veltravia.marketscopeai.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.BrokerConfig
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.NavyBlack
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextSecondary

/**
 * Optional broker-recommendation screen shown right after the projection intro,
 * before the questionnaire. "Continue with Exness" opens a REAL external link via
 * an actual Android view Intent (not a dead button). The referral URL is Ijezie's own
 * Exness link, centralized in the shared data/BrokerConfig.kt. Deliberately
 * does NOT reuse any third-party app's affiliate URL.
 */
@Composable
fun BrokerSetupIntroScreen(
    onDone: () -> Unit,
    // When provided (e.g. opened from the Trade Analysis screen's broker card
    // instead of onboarding), a back arrow appears at the top and onBack is
    // called instead of finishing onboarding.
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(BullGreen.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                "Optional, but highly recommended",
                style = MaterialTheme.typography.labelMedium,
                color = BullGreen,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Our recommended broker setup",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Use a broker that feels close to the environment MarketScope AI is tested against when reading your charts.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceLight)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GoldAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Text("EX", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = NavyBlack)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        BrokerConfig.NAME,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Recommended broker for MarketScope AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        "Non-US regions",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                buildAnnotatedTested(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(14.dp))

            BrokerBulletRow(
                icon = Icons.Filled.ShowChart,
                iconTint = BullGreen,
                text = "Great fit for MarketScope AI-style 4H & 15M chart analysis."
            )
            Spacer(Modifier.height(10.dp))
            BrokerBulletRow(
                icon = Icons.Filled.Bolt,
                iconTint = AccentCyan,
                text = "Easier to act on our analysis \u2014 entry, SL and TP levels tend to line up more closely with your chart."
            )
            Spacer(Modifier.height(10.dp))
            BrokerBulletRow(
                icon = Icons.Filled.FiberManualRecord,
                iconTint = GoldAmber,
                text = "Less mismatch between what MarketScope AI expects and your broker's pricing."
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "Don\u2019t worry \u2014 you can still use any broker you prefer. This is just our recommended match.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Public, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "MarketScope AI works with any major broker (including US brokers). This recommendation only applies where our partner is available.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(AccentViolet, AccentCyan)))
                .clickable {
                    SessionManager.setBrokerChoice(context, BrokerConfig.NAME)
                    SessionManager.setBrokerSetupShown(context, true)
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BrokerConfig.REFERRAL_URL))
                    context.startActivity(intent)
                    onDone()
                }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Continue with ${BrokerConfig.NAME}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NavyBlack
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = NavyBlack, modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.height(14.dp))

        Text(
            "I\u2019ll use my own broker for now",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    SessionManager.setBrokerChoice(context, "own_broker")
                    SessionManager.setBrokerSetupShown(context, true)
                    onDone()
                }
                .padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "MarketScope AI is not a broker and does not provide financial advice. Trading involves risk. " +
                "This broker recommendation is optional and provided for closer alignment with how MarketScope AI is tested and tuned.",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted.copy(alpha = 0.7f)
        )

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun BrokerBulletRow(icon: ImageVector, iconTint: Color, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun buildAnnotatedTested(): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        append("MarketScope AI has been ")
        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
            append("tested heavily with spreads & execution")
        }
        append(" similar to what you get on ${BrokerConfig.NAME}.")
    }
}
