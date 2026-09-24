package com.veltravia.marketscopeai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Assignment
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.R
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.BrokerConfig
import com.veltravia.marketscopeai.data.SessionManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.math.cos
import kotlin.math.sin

// --- Compass Home palette (from the approved MarketScope Compass concept) ---
private val DeskInk = Color(0xFFFFFFFF)        // clean white canvas (app palette)
private val InkCard = Color(0xFFF3F4F7)        // slot surface (soft gray)
private val InkCardBorder = Color(0xFFE2E5EC)
private val InkSubCard = Color(0xFFEAECF1)    // small-card surface
private val InkSubCardBorder = Color(0xFFE2E5EC)
private val DeskText = Color(0xFF0B0E14)
private val DeskBody = Color(0xFF4B5567)
private val DeskMuted = Color(0xFF8A93A6)
private val DeskLavender = Color(0xFF4B5567)
private val DeskAccent = Color(0xFF7C3AED)     // eyebrow (app violet)
private val DeskIndex = Color(0xFF7C3AED)
private val DeskHighlight = Color(0xFF7C3AED)
private val DeskArrowBox = Color(0xFFEAECF1)
private val DeskArrowTint = Color(0xFF0B0E14)
private val OrbitLine = Color(0xFFDDE2EC)
private val OrbitDash = Color(0xFFC8CEDC)
private val OrbitInner = Color(0xFFB9C0D4)
private val NodeCyan = Color(0xFF0891B2)
private val NodeViolet = Color(0xFF7C3AED)
private val CoreBorder = Color(0xFF0B0E14)
private val UpGreen = Color(0xFF16A34A)
private val DownRed = Color(0xFFDC2626)

private data class CompassWatchRow(
    val id: String,
    val display: String,
    val price: Double?,
    val changePct: Double?
)

private data class CompassToken(
    val symbol: String,
    val name: String,
    val price: Double,
    val change24h: Double?
)

/**
 * MarketScope Compass Home — the concept Ijezie approved and saved:
 * a dark navy editorial trading desk with the app's purple-to-blue accents,
 * a static orbital compass ("Clarity before conviction.") whose core starts
 * a real analysis, and three indexed rails below it. Everything is wired to
 * real backend data and real destinations: the radar previews the live
 * multi-asset watchlist and trending tokens, the intelligence cards open the
 * real daily signals feed and economic calendar, and the people & tools area
 * keeps every previous Home destination reachable. The logo stays strictly
 * static. Tab indices: 0 Home, 1 Signals, 2 Community, 3 Saved, 4 Profile.
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
    onOpenMarket: (String) -> Unit
) {
    val context = LocalContext.current

    // Real, server-refreshed account state — same contract as the previous
    // Home so the trial/monetization system keeps working untouched.
    var memberCount by remember { mutableStateOf<Int?>(null) }
    var trialDaysRemaining by remember { mutableStateOf(SessionManager.trialDaysRemaining(context)) }
    var isPremium by remember { mutableStateOf(SessionManager.effectivePremium(context)) }
    var planLabel by remember { mutableStateOf(SessionManager.planLabel(context)) }
    var analysesLeftToday by remember { mutableStateOf<Int?>(null) }

    // Real radar data (live watchlist + trending), reloaded on pull-to-refresh.
    var watchRows by remember { mutableStateOf<List<CompassWatchRow>?>(null) }
    var watchError by remember { mutableStateOf<String?>(null) }
    var tokens by remember { mutableStateOf<List<CompassToken>?>(null) }
    var trendingError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    suspend fun refreshData() {
        runCatching { ApiClient.fetchCommunityStats() }.getOrNull()?.let {
            memberCount = it.optInt("totalMembers", memberCount ?: 0)
        }
        SessionManager.sessionToken(context)?.let { token ->
            runCatching { ApiClient.fetchTrialStatus(token) }.getOrNull()?.let { status ->
                val active = status.optBoolean("trialActive", true)
                val days = status.optInt("trialDaysRemaining", trialDaysRemaining)
                val premium = status.optBoolean("isPremium", isPremium)
                val granted = status.optString("plan", "") == "premium" || status.optString("plan", "") == "lifetime"
                SessionManager.updateTrialState(context, active, days, premium)
                val display = com.veltravia.marketscopeai.monetization.planDisplay(status)
                SessionManager.updatePlan(context, display.plan, display.trailingLabel)
                com.veltravia.marketscopeai.monetization.PremiumAccessManager.updateFromTrialStatus(status)
                trialDaysRemaining = days
                isPremium = premium || granted
                planLabel = display.trailingLabel
                val usage = status.optJSONObject("analysisUsage")
                if (usage != null && !usage.optBoolean("unlimited", true)) {
                    analysesLeftToday = usage.optInt("remaining", 3)
                } else {
                    analysesLeftToday = null
                }
            }
        }
        runCatching { ApiClient.fetchMarketsWatchlist() }
            .onSuccess { arr ->
                watchRows = parseCompassWatch(arr)
                watchError = null
            }
            .onFailure { if (watchRows == null) watchError = "Live market data is temporarily unavailable." }
        runCatching { ApiClient.fetchTrending() }
            .onSuccess { arr ->
                tokens = parseCompassTokens(arr)
                trendingError = null
            }
            .onFailure { if (tokens == null) trendingError = "Trending is temporarily unavailable." }
    }

    // Pull-to-refresh — identical behavior to the previous Home.
    var refreshing by remember { mutableStateOf(false) }
    var pullDistance by remember { mutableFloatStateOf(0f) }
    val pullThresholdPx = with(density) { 78.dp.toPx() }

    val pullConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (refreshing) return Offset.Zero
                val delta = available.y
                if (delta > 0f && scrollState.value == 0) {
                    // Finger pulled down while at the very top — grow the
                    // indicator instead of (impossibly) scrolling further up.
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
                scope.launch { animate(start, 0f) { v, _ -> pullDistance = v } }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(Unit) { refreshData() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeskInk)
            .nestedScroll(pullConnection)
            .verticalScroll(scrollState)
            .padding(horizontal = 22.dp)
    ) {
        val indicatorHeight = when {
            refreshing -> 56.dp
            pullDistance > 0f -> with(density) { pullDistance.toDp() }
            else -> 0.dp
        }
        if (indicatorHeight > 0.dp) {
            Box(Modifier.fillMaxWidth().height(indicatorHeight), contentAlignment = Alignment.Center) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = DeskAccent)
                } else {
                    val progress = (pullDistance / pullThresholdPx).coerceIn(0.2f, 1f)
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Pull to refresh",
                        tint = DeskAccent.copy(alpha = progress),
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = progress * 240f }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // --- Brand row (logo strictly static) ---
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "MarketScope AI",
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Row {
                    Text("MarketScope ", color = DeskText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("AI", color = DeskHighlight, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "THE TRADING DESK",
                    color = Color(0xFF8A93A6),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.45.sp
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenNotifications) {
                Icon(
                    Icons.Filled.NotificationsNone,
                    contentDescription = "Notifications",
                    tint = Color(0xFF0B0E14),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFFF3F4F7))
                        .border(1.dp, Color(0xFFE2E5EC), RoundedCornerShape(13.dp))
                        .padding(9.dp)
                )
            }
        }

        // --- Intro ---
        Spacer(Modifier.height(28.dp))
        Text(
            "YOUR SPACE TO THINK CLEARLY",
            color = DeskAccent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.4.sp
        )
        Spacer(Modifier.height(12.dp))
        Text("Clarity before", color = DeskText, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp)
        Text("conviction.", color = DeskHighlight, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "One focused place to assess a setup, track markets, and stay true to your plan.",
            color = DeskBody,
            style = MaterialTheme.typography.bodySmall
        )
        // Real account line — server-refreshed, never decorative.
        Spacer(Modifier.height(8.dp))
        Text(
            accountLine(isPremium, trialDaysRemaining, analysesLeftToday),
            color = DeskMuted,
            style = MaterialTheme.typography.labelSmall
        )

        // --- The compass ---
        Spacer(Modifier.height(18.dp))
        CompassFocus(onStartAnalysis = onPickInstrument)

        // --- 01 · The Market Radar ---
        Spacer(Modifier.height(14.dp))
        Rail(index = "01", title = "THE MARKET RADAR")
        Spacer(Modifier.height(12.dp))

        // Live watchlist slot with REAL prices from the backend.
        SlotCard(
            title = "Live watchlist",
            description = "Futures · Forex · Crypto — live from the desk",
            onArrowClick = { watchRows?.firstOrNull()?.let { onOpenMarket(it.id) } }
        ) {
            RadarPreview(
                loading = watchRows == null && watchError == null,
                error = watchError
            ) {
                watchRows.orEmpty().take(3).forEach { row ->
                    RadarLine(
                        primary = row.display,
                        secondary = row.price?.let { formatCompassPrice(row.id, it) } ?: "—",
                        delta = row.changePct,
                        onClick = { onOpenMarket(row.id) }
                    )
                }
            }
        }

        // Trending slot with REAL trending tokens.
        Spacer(Modifier.height(9.dp))
        SlotCard(
            title = "Trending markets",
            description = "What's drawing attention right now",
            onArrowClick = { tokens?.firstOrNull()?.let { onOpenMarket(it.symbol.lowercase() + "usd") } }
        ) {
            RadarPreview(
                loading = tokens == null && trendingError == null,
                error = trendingError
            ) {
                tokens.orEmpty().take(3).forEach { token ->
                    RadarLine(
                        primary = token.symbol.uppercase(),
                        secondary = if (token.price.isFinite()) "$${"%,.2f".format(token.price)}" else "—",
                        delta = token.change24h,
                        onClick = { onOpenMarket(token.symbol.lowercase() + "usd") }
                    )
                }
            }
        }

        // --- 02 · Intelligence & Context ---
        Spacer(Modifier.height(14.dp))
        Rail(index = "02", title = "INTELLIGENCE & CONTEXT")
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallCard(
                index = "SIGNALS / 01",
                title = "Daily signals",
                description = "Published trade ideas",
                modifier = Modifier.weight(1f),
                onClick = { onSwitchTab(1) }
            )
            SmallCard(
                index = "CALENDAR / 02",
                title = "Economic events",
                description = "High-impact releases",
                modifier = Modifier.weight(1f),
                onClick = { onOpenCalendar() }
            )
        }

        // --- 03 · Your People & Tools ---
        Spacer(Modifier.height(14.dp))
        Rail(index = "03", title = "YOUR PEOPLE & TOOLS")
        Spacer(Modifier.height(12.dp))
        SlotCard(
            title = "Community & toolkit",
            description = memberCount?.let { n ->
                if (n == 1) "1 trader · saved work, risk tools, trade plan"
                else "$n traders · saved work, risk tools, trade plan"
            } ?: "Your people, saved work, risk tools, trade plan",
            onArrowClick = { onSwitchTab(2) }
        )

        // Compact tool tiles so every previous Home destination stays
        // reachable from the new layout.
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolTile("Risk calculator", Icons.Filled.Calculate, Modifier.weight(1f), onOpenRiskCalculator)
                ToolTile("Trade plan", Icons.Filled.Assignment, Modifier.weight(1f), onCreateTradePlan)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolTile("Learning hub", Icons.Filled.School, Modifier.weight(1f), onOpenLearningHub)
                ToolTile("News outlook", Icons.AutoMirrored.Filled.Article, Modifier.weight(1f), onOpenNewsOutlook)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolTile("Saved work", Icons.Filled.Bookmark, Modifier.weight(1f)) { onSwitchTab(3) }
                ToolTile(
                    "Share MarketScope",
                    Icons.Filled.Share,
                    Modifier.weight(1f)
                ) {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "I'm using MarketScope AI for AI-powered chart analysis — check it out.")
                    }
                    runCatching { context.startActivity(Intent.createChooser(send, "Share MarketScope AI")) }
                }
            }
        }

        // Recommended broker — the unchanged Exness referral flow, restyled
        // for the desk. Kept on Home exactly like before.
        Spacer(Modifier.height(10.dp))
        SlotCard(
            title = "Recommended broker",
            description = "Trade with our recommended partner",
            onArrowClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BrokerConfig.REFERRAL_URL))
                runCatching { context.startActivity(intent) }
            }
        )

        Spacer(Modifier.height(28.dp))
    }
}

/** The real account status line under the intro. */
private fun accountLine(isPremium: Boolean, trialDays: Int, analysesLeft: Int?): String {
    if (isPremium) return "Premium active · unlimited analyses"
    val trial = when {
        trialDays > 1 -> "Trial: $trialDays days left"
        trialDays == 1 -> "Trial: 1 day left"
        trialDays == 0 -> "Trial ends today"
        else -> "Free plan"
    }
    val allowance = analysesLeft?.let { if (it == 1) "1 analysis left today" else "$it analyses left today" } ?: "unlimited analyses"
    return "$trial · $allowance"
}

// ------------------------------------------------------------
// Compass — strictly static orbital composition (no animation).
// ------------------------------------------------------------

@Composable
private fun CompassFocus(onStartAnalysis: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(316.dp)
    ) {
        // Soft radial glow behind the whole compass.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF7C3AED).copy(alpha = 0.06f), Color.Transparent),
                        radius = 460f
                    )
                )
        )

        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.49f
            val r1 = 133.5.dp.toPx()
            val r2 = 113.dp.toPx()
            val r3 = 85.dp.toPx()

            drawCircle(color = OrbitLine, radius = r1, center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
            drawCircle(
                color = OrbitDash.copy(alpha = 0.47f),
                radius = r2,
                center = Offset(cx, cy),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                )
            )
            drawCircle(
                color = OrbitInner.copy(alpha = 0.27f),
                radius = r3,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )

            // Gradient arcs on the outer orbit — the compass "needle ring".
            val arcSize = androidx.compose.ui.geometry.Size(r1 * 2f, r1 * 2f)
            val topLeft = Offset(cx - r1, cy - r1)
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to Color(0xFF0891B2),
                    1.0f to Color(0xFF7C3AED)
                ),
                startAngle = 213f,
                sweepAngle = 65f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFF4F46E5),
                startAngle = 232f,
                sweepAngle = 21f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )

            // The two static nodes sitting on the outer orbit.
            val nodeR = 4.5.dp.toPx()
            val glowR = 10.dp.toPx()
            val a1 = Math.toRadians(160.0)
            val a2 = Math.toRadians(20.0)
            val p1 = Offset(cx + (r1 * cos(a1)).toFloat(), cy + (r1 * sin(a1)).toFloat())
            val p2 = Offset(cx + (r1 * cos(a2)).toFloat(), cy + (r1 * sin(a2)).toFloat())
            drawCircle(color = NodeCyan.copy(alpha = 0.15f), radius = glowR, center = p1)
            drawCircle(color = NodeCyan, radius = nodeR, center = p1)
            drawCircle(color = NodeViolet.copy(alpha = 0.2f), radius = glowR, center = p2)
            drawCircle(color = NodeViolet, radius = nodeR, center = p2)
        }

        // Cardinal labels around the compass.
        Text(
            "ASSESS",
            color = Color(0xFF8A93A6),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.7.sp,
            modifier = Modifier.align(Alignment.TopCenter)
        )
        Row(Modifier.fillMaxWidth().align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MARKETS",
                color = Color(0xFF8A93A6),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
        Text(
            "SIGNALS",
            color = Color(0xFF8A93A6),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        Text(
            "REVIEW",
            color = Color(0xFF8A93A6),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Core — the single real action: start an analysis.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-8).dp)
                .size(151.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF20273F), Color(0xFF121830), Color(0xFF0B0E1C)),
                        center = Offset(x = 220f, y = 140f),
                        radius = 1600f
                    )
                )
                .border(1.dp, CoreBorder, CircleShape)
                .clickable { onStartAnalysis() }
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.CandlestickChart,
                    contentDescription = null,
                    tint = Color(0xFFDCE6FF),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Start an analysis",
                    color = DeskText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "CHART OR STOCK ↗",
                    color = Color(0xFFA5B4FC),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp
                )
            }
        }
    }
}

// ------------------------------------------------------------
// Radar / rails / slots
// ------------------------------------------------------------

@Composable
private fun Rail(index: String, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(index, color = DeskIndex, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(9.dp))
        Text(
            title,
            color = DeskLavender,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.width(9.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFFE2E5EC))
        )
    }
}

/** Dark slot with a title row + optional real content rows inside. */
@Composable
private fun SlotCard(
    title: String,
    description: String,
    onArrowClick: (() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(InkCard)
            .border(1.dp, InkCardBorder, RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = DeskText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(3.dp))
                Text(description, color = Color(0xFF4B5567), style = MaterialTheme.typography.labelSmall)
            }
            if (onArrowClick != null) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(DeskArrowBox)
                        .clickable { onArrowClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.NorthEast,
                        contentDescription = "Open",
                        tint = DeskArrowTint,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        if (content != null) {
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun RadarPreview(
    loading: Boolean,
    error: String?,
    rows: @Composable () -> Unit
) {
    when {
        loading -> Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NodeCyan)
        }
        error != null -> Text(error, color = DeskMuted, style = MaterialTheme.typography.labelSmall)
        else -> Column { rows() }
    }
}

@Composable
private fun RadarLine(primary: String, secondary: String, delta: Double?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 2.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(primary, color = DeskText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(secondary, color = Color(0xFF8A93A6), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(10.dp))
        val deltaText = delta?.let { (if (it >= 0) "+" else "") + String.format(java.util.Locale.US, "%.2f%%", it) } ?: "—"
        Text(
            deltaText,
            color = if (delta == null) DeskMuted else if (delta >= 0) UpGreen else DownRed,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SmallCard(
    index: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(InkSubCard)
            .border(1.dp, InkSubCardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
            .height(103.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            index,
            color = DeskIndex,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.3.sp
        )
        Column {
            Text(title, color = DeskText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(description, color = Color(0xFF8A93A6), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ToolTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(InkSubCard)
            .border(1.dp, InkSubCardBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = DeskHighlight, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = DeskText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

// ------------------------------------------------------------
// Radar data parsing — real backend payload shapes.
// ------------------------------------------------------------

private fun parseCompassWatch(arr: JSONArray): List<CompassWatchRow> {
    val out = mutableListOf<CompassWatchRow>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(
            CompassWatchRow(
                id = o.optString("id"),
                display = o.optString("display"),
                price = if (o.isNull("price")) null else o.optDouble("price"),
                changePct = if (o.isNull("changePct")) null else o.optDouble("changePct")
            )
        )
    }
    return out
}

private fun parseCompassTokens(arr: JSONArray): List<CompassToken> {
    val out = mutableListOf<CompassToken>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(
            CompassToken(
                symbol = o.optString("symbol"),
                name = o.optString("name"),
                price = o.optDouble("price"),
                change24h = if (o.isNull("change24h")) null else o.optDouble("change24h")
            )
        )
    }
    return out
}

private fun formatCompassPrice(id: String, v: Double): String {
    if (!v.isFinite()) return "—"
    val decimals = when {
        id.startsWith("usdjpy") || id.endsWith("jpy") -> 3
        id.length == 6 && id.all { it.isLetter() } -> 5
        v >= 1000 -> 2
        v >= 1 -> 4
        else -> 6
    }
    return "%,.${decimals}f".format(v)
}
