package com.veltravia.marketscopeai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.animation.core.animate
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.R
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.BrokerConfig
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceDark
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private data class HomeCalendarEvent(val title: String, val market: String, val timeMillis: Long)

private fun parseCalendarTime(raw: String): Long? {
    for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss'Z'")) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = false
            }.parse(raw)?.time
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/**
 * Decision-first Home preview. One primary analysis action leads to live
 * signals and high-impact calendar, then the existing live watchlist,
 * community, account status, tools and recommended broker. No fabricated
 * market numbers or static signal samples.
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
    onCreateTradePlan: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenLearningHub: () -> Unit,
    onOpenNewsOutlook: () -> Unit,
    onOpenMarket: (String) -> Unit,
    onOpenSignal: (String) -> Unit
) {
    val context = LocalContext.current
    val user = remember { SessionManager.currentUser(context) }
    val firstName = remember(user) { user?.name?.trim()?.split(" ")?.firstOrNull() ?: "there" }

    var expanded by remember { mutableStateOf(false) }
    var memberCount by remember { mutableStateOf<Int?>(null) }
    var onlineCount by remember { mutableStateOf<Int?>(null) }
    var latestSignal by remember { mutableStateOf<JSONObject?>(null) }
    var signalLoaded by remember { mutableStateOf(false) }
    var signalError by remember { mutableStateOf(false) }
    var nextHighImpact by remember { mutableStateOf<HomeCalendarEvent?>(null) }
    var calendarLoaded by remember { mutableStateOf(false) }
    var calendarError by remember { mutableStateOf(false) }
    var trialDaysRemaining by remember { mutableStateOf(SessionManager.trialDaysRemaining(context)) }
    var isPremium by remember { mutableStateOf(SessionManager.effectivePremium(context)) }
    // Free-tier allowance after the trial lapses: 3 chart analyses per day.
    var analysesLeftToday by remember { mutableStateOf<Int?>(null) }
    val communityJoined = remember { SessionManager.communityJoined(context) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    /** Refreshes Home briefing and account/community state on entry or pull.
     * Watchlist and trending sections retain their own live data loaders. */
    suspend fun refreshData() = coroutineScope {
        // Independent real feeds run together so a slow calendar does not
        // postpone the trial state or signals. Failure never invents data.
        launch {
            runCatching { ApiClient.fetchCommunityStats() }.getOrNull()?.let {
                memberCount = it.optInt("totalMembers", memberCount ?: 0)
                onlineCount = it.optInt("onlineCount", onlineCount ?: 0)
            }
        }
        SessionManager.sessionToken(context)?.let { token ->
            launch {
                runCatching { ApiClient.fetchTrialStatus(token) }.getOrNull()?.let { status ->
                    val active = status.optBoolean("trialActive", true)
                    val days = status.optInt("trialDaysRemaining", trialDaysRemaining)
                    val premium = status.optBoolean("isPremium", isPremium)
                    val granted = status.optString("plan", "") in listOf("premium", "lifetime")
                    SessionManager.updateTrialState(context, active, days, premium)
                    val display = com.veltravia.marketscopeai.monetization.planDisplay(status)
                    SessionManager.updatePlan(context, display.plan, display.trailingLabel)
                    com.veltravia.marketscopeai.monetization.PremiumAccessManager.updateFromTrialStatus(status)
                    trialDaysRemaining = days
                    isPremium = premium || granted
                    val usage = status.optJSONObject("analysisUsage")
                    analysesLeftToday = if (usage != null && !usage.optBoolean("unlimited", true))
                        usage.optInt("remaining", 3) else null
                }
            }
            launch {
                val feed = runCatching { ApiClient.fetchDailySignalsFeed(token, 1) }
                signalError = feed.isFailure
                latestSignal = feed.getOrNull()?.optJSONArray("signals")?.optJSONObject(0)
                signalLoaded = true
            }
        }
        launch {
            val feed = runCatching { ApiClient.fetchEconomicCalendar() }
            calendarError = feed.isFailure
            if (feed.isFailure) nextHighImpact = null
            if (feed.isSuccess) {
                val now = System.currentTimeMillis()
                val events = feed.getOrThrow()
                nextHighImpact = (0 until events.length()).mapNotNull { i ->
                    val e = events.optJSONObject(i) ?: return@mapNotNull null
                    if (!e.optString("impact").equals("High", ignoreCase = true)) return@mapNotNull null
                    val at = parseCalendarTime(e.optString("timestamp")) ?: return@mapNotNull null
                    if (at < now || at > now + 3 * 24 * 3600_000L) return@mapNotNull null
                    val title = e.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    HomeCalendarEvent(title, e.optString("currency").takeIf { it.isNotBlank() }
                        ?: e.optString("country"), at)
                }.minByOrNull { it.timeMillis }
            }
            calendarLoaded = true
        }
    }

    LaunchedEffect(Unit) { refreshData() }

    // --- Pull-to-refresh: drag the Home feed down to re-fetch everything ---
    val pullThresholdPx = with(density) { 110.dp.toPx() }
    val pullMaxPx = pullThresholdPx * 1.5f
    var pullDistance by remember { mutableFloatStateOf(0f) }
    var refreshing by remember { mutableStateOf(false) }

    val pullConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (refreshing) return Offset.Zero
                val delta = available.y
                if (delta > 0f && scrollState.value == 0) {
                    // Finger pulled down while at the very top — grow the
                    // indicator instead of (impossibly) scrolling further up.
                    val newPull = (pullDistance + delta).coerceAtMost(pullMaxPx)
                    val consumed = newPull - pullDistance
                    pullDistance = newPull
                    return Offset(0f, consumed)
                }
                if (delta < 0f && pullDistance > 0f) {
                    // Push back up — the indicator shrinks first.
                    val newPull = (pullDistance + delta).coerceAtLeast(0f)
                    val consumed = newPull - pullDistance
                    pullDistance = newPull
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (refreshing) return Velocity.Zero
                if (pullDistance >= pullThresholdPx) {
                    // Released past the threshold — run the real refresh.
                    refreshing = true
                    pullDistance = pullThresholdPx * 0.55f
                    scope.launch {
                        refreshData()
                        refreshing = false
                        val start = pullDistance
                        animate(start, 0f) { v, _ -> pullDistance = v }
                    }
                    return available
                }
                val start = pullDistance
                scope.launch {
                    animate(start, 0f) { v, _ -> pullDistance = v }
                }
                return Velocity.Zero
            }
        }
    }

    // Working destinations remain available in the expandable Explore grid.
    val primaryActions = listOf(
        QuickAction("News Outlook", Icons.AutoMirrored.Filled.Article, onOpenNewsOutlook),
        QuickAction("Risk calculator", Icons.Filled.Calculate, onOpenRiskCalculator),
        QuickAction("Saved", Icons.Filled.Bookmark) { onSwitchTab(3) },
        QuickAction("Signals", Icons.AutoMirrored.Filled.ShowChart) { onSwitchTab(1) },
        QuickAction("Community", Icons.Filled.Groups) { onSwitchTab(2) },
        QuickAction("Share", Icons.Filled.Share) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "I'm using MarketScope AI for AI-powered chart analysis — check it out.")
            }
            context.startActivity(Intent.createChooser(send, "Share MarketScope AI"))
        }
    )
    val moreActions = listOf(
        QuickAction("Learning hub", Icons.Filled.School, onOpenLearningHub),
        QuickAction("Trade Plan", Icons.Filled.Assignment, onCreateTradePlan),
        QuickAction("Calendar", Icons.Filled.CalendarMonth) { onOpenCalendar() }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullConnection)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        // --- Pull-to-refresh indicator ---
        // Grows with the drag (arrow rotates as it approaches the
        // threshold), becomes a spinner while the real re-fetch runs.
        val indicatorHeight = when {
            refreshing -> 56.dp
            pullDistance > 0f -> with(density) { pullDistance.toDp() }
            else -> 0.dp
        }
        if (indicatorHeight > 0.dp) {
            Box(
                modifier = Modifier.fillMaxWidth().height(indicatorHeight),
                contentAlignment = Alignment.Center
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = AccentViolet
                    )
                } else {
                    val progress = (pullDistance / pullThresholdPx).coerceIn(0.2f, 1f)
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Pull to refresh",
                        tint = AccentViolet.copy(alpha = progress),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = progress * 240f }
                    )
                }
            }
        }
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
                    "Welcome back, $firstName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "Your market desk, in one place.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentCyan,
                    maxLines = 1
                )
            }
            IconButton(onClick = {
                val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@marketscopeai.com"))
                runCatching { context.startActivity(mail) }
            }) {
                Icon(Icons.Filled.Email, contentDescription = "Contact support", tint = TextSecondary)
            }
            IconButton(onClick = onOpenNotifications) {
                Icon(Icons.Filled.NotificationsNone, contentDescription = "Notifications", tint = TextSecondary)
            }
        }

        Spacer(Modifier.height(20.dp))

        // --- Decision-first primary action. Static logo stays in the header. ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(SurfaceLight, SurfaceDark)))
                .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("YOUR NEXT STEP", style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (isPremium) "PREMIUM" else if (trialDaysRemaining > 0) "TRIAL" else "FREE",
                    color = if (isPremium) GoldAmber else TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                        .background(BorderSubtle).padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("Bring your next setup into focus", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text("Upload a chart or choose a stock. We'll work from real market data and your trading profile.",
                style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                    .background(Brush.horizontalGradient(listOf(AccentViolet, AccentCyan)))
                    .clickable(onClick = onPickInstrument)
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Analyze chart or stock", modifier = Modifier.weight(1f),
                    color = Color.White, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Filled.NorthEast, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Today's briefing", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("A quick view of the live feeds", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(14.dp))

        // The feed may hold a signal from a previous day: call it the latest,
        // never pretend it was published today or that a locked feed is open.
        val signal = latestSignal
        val signalId = signal?.optString("id")?.takeIf { it.isNotBlank() }
        val signalName = signal?.optString("instrument")?.takeIf { it.isNotBlank() }
        val signalMeta = signal?.let {
            listOfNotNull(
                it.optString("direction").takeIf { value -> value.isNotBlank() }?.uppercase(Locale.getDefault()),
                it.optString("status").takeIf { value -> value.isNotBlank() }?.replaceFirstChar { c -> c.uppercase() },
                parseCalendarTime(it.optString("publishedAt"))?.let { at ->
                    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(at))
                }
            ).joinToString(" · ")
        }
        HomeBriefRow(
            icon = Icons.AutoMirrored.Filled.ShowChart,
            label = "LATEST SIGNAL",
            title = when {
                signalName != null -> signalName
                !signalLoaded -> "Checking the signal feed…"
                signalError -> "Signals aren't available right now"
                else -> "No signals published yet"
            },
            detail = signalMeta?.takeIf { it.isNotBlank() } ?: "See the live signal feed",
            onClick = { if (signalId != null) onOpenSignal(signalId) else onSwitchTab(1) }
        )
        Spacer(Modifier.height(10.dp))
        val event = nextHighImpact
        HomeBriefRow(
            icon = Icons.Filled.CalendarMonth,
            label = "NEXT HIGH-IMPACT EVENT",
            title = when {
                event != null -> event.title
                !calendarLoaded -> "Checking the economic calendar…"
                calendarError -> "Calendar isn't available right now"
                else -> "No high-impact events in the next 3 days"
            },
            detail = if (event != null) {
                val localTime = SimpleDateFormat("EEE, h:mm a z", Locale.getDefault()).format(Date(event.timeMillis))
                listOf(event.market.takeIf { it.isNotBlank() }, localTime).filterNotNull().joinToString(" · ")
            } else "View the full calendar",
            onClick = onOpenCalendar
        )

        Spacer(Modifier.height(28.dp))

        // --- Multi-asset live watchlist (Futures/Forex/Crypto) ---
        MarketsWatchlistSection(onOpenMarket = onOpenMarket)

        Spacer(Modifier.height(28.dp))

        // --- Trending tokens (live) ---
        TrendingSection(onOpenMarket = onOpenMarket)

        Spacer(Modifier.height(28.dp))

        // Account and community are still here, but no longer compete with
        // the actual trading decision at the top of Home.
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
                            .background(AccentViolet),
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
                    memberCount?.let { "$it member${if (it == 1) "" else "s"}${onlineCount?.let { o -> if (o > 0) " · $o online" else "" } ?: ""}" } ?: "Loading…",
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
                    when {
                        isPremium -> "Active"
                        trialDaysRemaining > 0 -> "$trialDaysRemaining day${if (trialDaysRemaining == 1) "" else "s"} left"
                        else -> "Free plan"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        isPremium -> "Enjoy unlimited access"
                        trialDaysRemaining > 0 -> "View your plan in Profile"
                        analysesLeftToday != null ->
                            "3 chart analyses a day — $analysesLeftToday left today"
                        else -> "3 free chart analyses a day"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // --- Compact, expandable tools. Every previous action remains. ---
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Explore tools", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Your full toolkit is still one tap away", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Show fewer tools" else "Show all tools", tint = AccentCyan)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(SurfaceLight).border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                .padding(vertical = 16.dp, horizontal = 6.dp)
        ) {
            ActionRow(primaryActions.subList(0, 3))
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    ActionRow(primaryActions.subList(3, 6))
                    Spacer(Modifier.height(12.dp))
                    ActionRow(moreActions)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                    .padding(top = 14.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (expanded) "Show fewer" else "All tools", color = AccentCyan,
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(4.dp))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
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
                .background(SurfaceLight)
                .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
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
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Our recommended broker for testing MarketScope AI's analysis.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        actions.forEach { action ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(84.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(SurfaceDark)
                        .clickable { action.onClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(action.icon, contentDescription = action.label, tint = AccentViolet, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    action.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 2,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}


/** Small live-data row. The whole row opens its existing destination. */
@Composable
private fun HomeBriefRow(icon: ImageVector, label: String, title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp))
            .background(SurfaceLight).border(1.dp, BorderSubtle, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = AccentCyan, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(3.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(3.dp))
            Text(detail, style = MaterialTheme.typography.labelSmall,
                color = TextMuted, maxLines = 2)
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.Filled.ChevronRight, contentDescription = null,
            tint = TextMuted, modifier = Modifier.size(19.dp))
    }
}
