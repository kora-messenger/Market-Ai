package com.veltravia.marketai.ui.screens

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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketai.data.ApiClient
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.AccentViolet
import com.veltravia.marketai.ui.theme.BullGreen
import com.veltravia.marketai.ui.theme.NavyBlack
import com.veltravia.marketai.ui.theme.SurfaceLight
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Shown right after sign-in: onboards the trader into Market Ai's free community —
 * live signal drops and trader posts. "Join" is a real, backend-persisted action
 * (POST /api/community/join), not a cosmetic transition.
 */
@Composable
fun CommunityIntroScreen(onJoined: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val alreadyJoined = remember { SessionManager.communityJoined(context) }

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
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = "YOU'RE JOINING SOMETHING LIVE",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = TextSecondary
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Welcome to the trading desk",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Finish setup and you're automatically part of the free Market Ai community — real trader posts and select signal drops.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(28.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AccentCyan.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Groups,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Free community access",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Live market energy, structured for serious traders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Follow AI signal drops and trader posts in one feed — free, before you ever go Pro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    FeatureTile(
                        icon = Icons.Filled.Forum,
                        title = "Trader posts",
                        description = "See what the community is watching in real time.",
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    FeatureTile(
                        icon = Icons.Filled.ShowChart,
                        title = "Free signals",
                        description = "Selected setups and trade proof before going Pro.",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BullGreen.copy(alpha = 0.14f))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            "WHAT HAPPENS NEXT",
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            color = BullGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Finish setup and your free community access unlocks immediately.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
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

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (joining) AccentViolet.copy(alpha = 0.6f) else AccentViolet)
                .clickable(enabled = !joining) { proceed() }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (joining) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = NavyBlack
                )
            } else {
                Text(
                    text = if (alreadyJoined) "Continue" else "Start your trading journey",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = NavyBlack
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun FeatureTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLight)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
    }
}
