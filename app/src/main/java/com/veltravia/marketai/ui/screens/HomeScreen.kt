package com.veltravia.marketai.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veltravia.marketai.R
import com.veltravia.marketai.data.ApiClient
import com.veltravia.marketai.data.BrokerConfig
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.AccentViolet
import com.veltravia.marketai.ui.theme.BullGreen
import com.veltravia.marketai.ui.theme.GoldAmber
import com.veltravia.marketai.ui.theme.NavyBlack
import com.veltravia.marketai.ui.theme.SurfaceDark
import com.veltravia.marketai.ui.theme.SurfaceLight
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextPrimary
import com.veltravia.marketai.ui.theme.TextSecondary

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Home screen — real header, an expandable quick-actions grid, a real
 * community card + a real trial-status card, and a single genuine
 * recommended-broker card. Every number shown here is real: the community
 * member count comes from the backend (COUNT of joined users), and the trial
 * days remaining come from the signed-in user's real trial state.
 *
 * Tab indices: 0 Home, 1 Signals, 2 Community, 3 Saved, 4 Profile — see
 * MarketAiApp's `tabs` list.
 */
@Composable
fun HomeScreen(
    onPickInstrument: () -> Unit,
    onSwitchTab: (Int) -> Unit,
    onOpenRiskCalculator: () -> Unit,
    onOpenNotifications: () -> Unit,
    onCreateTradePlan: () -> Unit
) {
    val context = LocalContext.current
    val user = remember { SessionManager.currentUser(context) }
    val firstName = remember(user) { user?.name?.trim()?.split(" ")?.firstOrNull() ?: "there" }

    var expanded by remember { mutableStateOf(false) }
    var memberCount by remember { mutableStateOf<Int?>(null) }
    var trialDaysRemaining by remember { mutableStateOf(SessionManager.trialDaysRemaining(context)) }
    var isPremium by remember { mutableStateOf(SessionManager.isPremium(context)) }
    val communityJoined = remember { SessionManager.communityJoined(context) }

    LaunchedEffect(Unit) {
        // Real member count — a literal COUNT() from the backend, refreshed
        // every time Home loads.
        runCatching { ApiClient.fetchCommunityStats() }.getOrNull()?.let {
            memberCount = it.optInt("totalMembers", memberCount ?: 0)
        }
        // Refresh trial state from the server so it never goes stale.
        SessionManager.sessionToken(context)?.let { token ->
            runCatching { ApiClient.fetchTrialStatus(token) }.getOrNull()?.let { status ->
                val active = status.optBoolean("trialActive", true)
                val days = status.optInt("trialDaysRemaining", trialDaysRemaining)
                val premium = status.optBoolean("isPremium", isPremium)
                SessionManager.updateTrialState(context, active, days, premium)
                trialDaysRemaining = days
                isPremium = premium
            }
        }
    }

    fun comingSoon(feature: String) {
        Toast.makeText(context, "$feature is coming soon", Toast.LENGTH_SHORT).show()
    }

    // Prioritize real, working features in the collapsed row; the three
    // not-yet-built tools only appear once the user explicitly expands.
    val primaryActions = listOf(
        QuickAction("News Outlook", Icons.AutoMirrored.Filled.Article) { comingSoon("News Outlook") },
        QuickAction("Risk calculator", Icons.Filled.Calculate, onOpenRiskCalculator),
        QuickAction("Community", Icons.Filled.Groups) { onSwitchTab(2) },
        QuickAction("Signals", Icons.AutoMirrored.Filled.ShowChart) { onSwitchTab(1) },
        QuickAction("Saved", Icons.Filled.Bookmark) { onSwitchTab(3) },
        QuickAction("Share", Icons.Filled.Share) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "I'm using MarketScope AI for AI-powered chart analysis — check it out.")
            }
            context.startActivity(Intent.createChooser(send, "Share MarketScope AI"))
        }
    )
    val moreActions = listOf(
        QuickAction("Learning hub", Icons.Filled.School) { comingSoon("Learning hub") },
        QuickAction("Trade Plan", Icons.Filled.Assignment, onCreateTradePlan),
        QuickAction("Journal", Icons.AutoMirrored.Filled.MenuBook) { comingSoon("Journal") }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        // --- Header ---
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "MarketScope AI",
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Hello $firstName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "Take more profitable trades now…",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    maxLines = 1
                )
            }
            IconButton(onClick = {
                val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@veltraviatech.com"))
                runCatching { context.startActivity(mail) }
            }) {
                Icon(Icons.Filled.Email, contentDescription = "Contact support", tint = TextSecondary)
            }
            IconButton(onClick = onOpenNotifications) {
                Icon(Icons.Filled.NotificationsNone, contentDescription = "Notifications", tint = TextSecondary)
            }
        }

        Spacer(Modifier.height(20.dp))

        // --- Quick-actions gradient card ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(AccentViolet, AccentCyan)))
                .padding(vertical = 22.dp, horizontal = 12.dp)
        ) {
            ActionRow(primaryActions.subList(0, 3))
            Spacer(Modifier.height(18.dp))
            ActionRow(primaryActions.subList(3, 6))

            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(18.dp))
                    ActionRow(moreActions)
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                        .clickable { expanded = !expanded },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Show less" else "Show more",
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Community + Trial cards ---
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceLight)
                    .clickable { onSwitchTab(2) }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "COMMUNITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(NavyBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.NorthEast, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AccentCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (communityJoined) "Visit the room" else "Join the room today",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    memberCount?.let { "$it member${if (it == 1) "" else "s"}" } ?: "Loading…",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceLight)
                    .clickable { onSwitchTab(4) }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isPremium) "PREMIUM" else "FREE TRIAL",
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    if (isPremium) "Active" else "$trialDaysRemaining day${if (trialDaysRemaining == 1) "" else "s"} left",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (isPremium) "Enjoy unlimited access" else "View your plan in Profile",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // --- Recommended tools ---
        Text(
            "MarketScope AI Recommended Tools",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(NavyBlack)
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BrokerConfig.REFERRAL_URL))
                    runCatching { context.startActivity(intent) }
                }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Trade with real market conditions",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Our recommended broker for testing MarketScope AI's analysis.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BullGreen)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(BrokerConfig.NAME, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun ActionRow(actions: List<QuickAction>) {
    Row(modifier = androidx.compose.ui.Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        actions.forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(84.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { action.onClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(action.icon, contentDescription = action.label, tint = AccentViolet, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

