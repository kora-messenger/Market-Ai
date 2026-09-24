package com.veltravia.marketscopeai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.data.ApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.veltravia.marketscopeai.BuildConfig
import com.veltravia.marketscopeai.ui.screens.ForceUpdateScreen
import com.veltravia.marketscopeai.ui.components.PremiumTab
import com.veltravia.marketscopeai.ui.components.PremiumTabBar
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.screens.CommunityIntroScreen
import com.veltravia.marketscopeai.ui.screens.NotificationsIntroScreen
import com.veltravia.marketscopeai.ui.screens.ProjectionIntroScreen
import com.veltravia.marketscopeai.ui.screens.BrokerSetupIntroScreen
import com.veltravia.marketscopeai.ui.screens.CommunityScreen
import com.veltravia.marketscopeai.ui.screens.DmThreadsScreen
import com.veltravia.marketscopeai.ui.screens.DmChatScreen
import com.veltravia.marketscopeai.ui.screens.LeaderboardScreen
import com.veltravia.marketscopeai.ui.screens.LearningHubScreen
import com.veltravia.marketscopeai.ui.screens.NewsOutlookScreen
import com.veltravia.marketscopeai.ui.screens.LearningPatternScreen
import com.veltravia.marketscopeai.ui.screens.HomeScreen
import com.veltravia.marketscopeai.ui.screens.CalendarScreen
import com.veltravia.marketscopeai.ui.screens.WallOfWinsScreen
import com.veltravia.marketscopeai.ui.screens.RiskCalculatorScreen
import com.veltravia.marketscopeai.ui.screens.JournalScreen
import com.veltravia.marketscopeai.ui.screens.JournalEntryScreen
import com.veltravia.marketscopeai.ui.screens.NotificationsScreen
import com.veltravia.marketscopeai.ui.screens.AiTradePlanWizardScreen
import com.veltravia.marketscopeai.ui.screens.AiTradePlanDetailScreen
import com.veltravia.marketscopeai.ui.screens.ChartUploadScreen
import com.veltravia.marketscopeai.ui.screens.FirstAnalysisScreen
import com.veltravia.marketscopeai.ui.screens.SubscribeScreen
import com.veltravia.marketscopeai.ui.screens.SignalCardScreen
import com.veltravia.marketscopeai.ui.screens.StockBatchResultScreen
import com.veltravia.marketscopeai.ui.screens.ProfileScreen
import com.veltravia.marketscopeai.ui.screens.ReferralScreen
import com.veltravia.marketscopeai.ui.screens.ScreenshotGuideScreen
import com.veltravia.marketscopeai.ui.screens.QuestionnaireScreen
import com.veltravia.marketscopeai.ui.screens.MarketViewScreen
import com.veltravia.marketscopeai.ui.screens.SavedScreen
import com.veltravia.marketscopeai.ui.screens.DailySignalDetailScreen
import com.veltravia.marketscopeai.ui.screens.SignalsScreen
import com.veltravia.marketscopeai.ui.screens.AdminSignalsScreen
import com.veltravia.marketscopeai.ui.screens.AdminPremiumScreen
import com.veltravia.marketscopeai.ui.screens.WelcomeScreen

/**
 * Route hint delivered by a push notification tap (MainActivity sets it from
 * the notification intent; MainTabs consumes it and switches tabs).
 */
object PushRouter {
    @Volatile
    var pendingTab: Int? = null

    /**
     * True when a marketscopeai://subscribe deep link (the Subscribe button
     * in the trial-expired email) is waiting to be handled. Compose state so
     * the nav graph reacts the moment MainActivity sets it.
     */
    var pendingSubscribe by mutableStateOf(false)

    /**
     * A billing/subscription push (payment succeeded or failed) was tapped —
     * the nav graph should open the Notifications screen directly, scrolled
     * to the exact message (pendingHighlightNotificationId, if the payload
     * carried one) rather than just landing on a tab.
     */
    var pendingOpenNotifications by mutableStateOf(false)
    var pendingHighlightNotificationId by mutableStateOf<String?>(null)

    /**
     * A mentor-DM push was tapped — the nav graph should open the chat
     * thread directly (threadId from the payload's data.route === "dm").
     */
    var pendingDmThreadId by mutableStateOf<String?>(null)

    /**
     * A price-alert push was tapped — the nav graph should open the Market
     * View screen for the instrument the alert fired on.
     */
    var pendingMarketId by mutableStateOf<String?>(null)

    /**
     * A signal push (TP hit) was tapped — the nav graph should open that
     * signal's detail screen.
     */
    var pendingSignalId by mutableStateOf<String?>(null)

    /**
     * The phone was shaken with "shake to report a bug" enabled — open the
     * bug report screen. Set by ShakeBugReporter's sensor callback.
     */
    var pendingBugReport by mutableStateOf(false)

    fun tabForRoute(route: String?): Int? = when (route) {
        "signals" -> 1
        "community" -> 2
        else -> null
    }
}

private data class Tab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val tabs = listOf(
    Tab("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Tab("Signals", Icons.Filled.Insights, Icons.Outlined.Insights),
    Tab("Community", Icons.Filled.Forum, Icons.Outlined.Forum),
    Tab("Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
    Tab("Profile", Icons.Filled.Person, Icons.Outlined.Person)
)

@Composable
fun MarketAiApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // Force-update gate — server-driven, checked once at launch, BEFORE the
    // welcome screen or any session state is even considered. null = still
    // checking (render nothing yet, avoids a home-screen flash); true = the
    // installed build is below the admin's configured minimum, block with
    // ForceUpdateScreen; false = proceed into the app as normal.
    var updateRequired by remember { mutableStateOf<Boolean?>(null) }
    var updateInfo by remember { mutableStateOf<org.json.JSONObject?>(null) }
    LaunchedEffect(Unit) {
        val result = runCatching { ApiClient.checkAppVersion(BuildConfig.VERSION_CODE) }.getOrNull()
        updateInfo = result
        updateRequired = result?.optBoolean("updateRequired", false) ?: false
    }

    if (updateRequired == true) {
        val info = updateInfo
        ForceUpdateScreen(
            latestVersionName = info?.optString("latestVersionName", "").takeUnless { it.isNullOrBlank() } ?: BuildConfig.VERSION_NAME,
            updateMessage = info?.optString("updateMessage", "").takeUnless { it.isNullOrBlank() }
                ?: "New version available. We strongly recommend installing the update before using the app.",
            // Platform-aware: the server picks Play Store vs App Store from the
            // platform the client reported. This build reports "android".
            storeUrl = info?.optString("storeUrl", "").takeUnless { it.isNullOrBlank() }
                ?: "https://play.google.com/store/apps/details?id=com.veltravia.marketscopeai",
            storeLabel = info?.optString("storeLabel", "").takeUnless { it.isNullOrBlank() }
                ?: "Open Play Store"
        )
        return
    }
    if (updateRequired == null) {
        // Brief version-check window — same white canvas as the rest of the
        // app so there is no flash, just a beat before content appears.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
        return
    }

    val startDestination = remember {
        val hasSession = SessionManager.currentUser(context) != null
        val questionnaireDone = SessionManager.questionnaireDone(context)
        // Real FxLens order: the questionnaire is the FIRST thing a new user sees right
        // after sign-in — before community/notifications/projection/broker/screenshot-guide.
        val needsCommunityIntro = hasSession &&
            questionnaireDone &&
            SessionManager.sessionToken(context) != null &&
            !SessionManager.communityJoined(context)
        val needsNotificationsIntro = hasSession &&
            questionnaireDone &&
            !needsCommunityIntro &&
            !SessionManager.notificationsPromptShown(context)
        val needsProjectionIntro = hasSession &&
            questionnaireDone &&
            !needsCommunityIntro &&
            !needsNotificationsIntro &&
            !SessionManager.projectionIntroShown(context)
        val needsBrokerSetupIntro = hasSession &&
            questionnaireDone &&
            !needsCommunityIntro &&
            !needsNotificationsIntro &&
            !needsProjectionIntro &&
            !SessionManager.brokerSetupShown(context)
        val needsScreenshotGuideIntro = hasSession &&
            questionnaireDone &&
            !needsCommunityIntro &&
            !needsNotificationsIntro &&
            !needsProjectionIntro &&
            !needsBrokerSetupIntro &&
            !SessionManager.screenshotGuideShown(context)
        when {
            !hasSession -> "welcome"
            !questionnaireDone -> "questionnaire"
            needsCommunityIntro -> "community_intro"
            needsNotificationsIntro -> "notifications_intro"
            needsProjectionIntro -> "projection_intro"
            needsBrokerSetupIntro -> "broker_setup_intro"
            needsScreenshotGuideIntro -> "screenshot_guide_intro"
            else -> "main"
        }
    }

    // Deep link marketscopeai://subscribe (trial-expired email button).
    LaunchedEffect(PushRouter.pendingSubscribe) {
        if (PushRouter.pendingSubscribe) {
            PushRouter.pendingSubscribe = false
            navController.navigate("subscribe")
        }
    }

    // Billing push tapped (payment succeeded/failed) — open Notifications
    // directly, scrolled straight to that message.
    LaunchedEffect(PushRouter.pendingOpenNotifications) {
        if (PushRouter.pendingOpenNotifications) {
            PushRouter.pendingOpenNotifications = false
            val highlightId = PushRouter.pendingHighlightNotificationId
            PushRouter.pendingHighlightNotificationId = null
            val encoded = highlightId?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
            navController.navigate("notifications?highlightId=$encoded")
        }
    }

    // Mentor-DM push tapped — open the private chat directly.
    LaunchedEffect(PushRouter.pendingDmThreadId) {
        val threadId = PushRouter.pendingDmThreadId
        if (threadId != null) {
            PushRouter.pendingDmThreadId = null
            navController.navigate("dm_chat/$threadId")
        }
    }

    // Phone shaken with the shake-to-report toggle on — open the bug report
    // screen (guarded so a double-fire never stacks two copies).
    LaunchedEffect(PushRouter.pendingBugReport) {
        if (PushRouter.pendingBugReport) {
            PushRouter.pendingBugReport = false
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != "report_bug") navController.navigate("report_bug")
        }
    }

    // Price-alert push tapped — open the Market View for that instrument.
    LaunchedEffect(PushRouter.pendingMarketId) {
        val marketId = PushRouter.pendingMarketId
        if (marketId != null) {
            PushRouter.pendingMarketId = null
            navController.navigate("market/$marketId")
        }
    }

    // Signal push (TP hit) tapped — open that signal's detail screen.
    LaunchedEffect(PushRouter.pendingSignalId) {
        val signalId = PushRouter.pendingSignalId
        if (signalId != null) {
            PushRouter.pendingSignalId = null
            navController.navigate("daily_signal/$signalId")
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("welcome") {
            WelcomeScreen(
                onSignedIn = { alreadyOnboarded ->
                    // Server-authoritative routing: a returning user (the backend
                    // confirms this account already completed the questionnaire)
                    // lands straight on Home — sign out / sign in never repeats
                    // onboarding. Only genuinely-new accounts go through the
                    // questionnaire flow.
                    navController.navigate(if (alreadyOnboarded) "main" else "questionnaire") {
                        popUpTo("welcome") { inclusive = true }
                    }
                }
            )
        }
        composable("community_intro") {
            CommunityIntroScreen(
                onJoined = {
                    navController.navigate("notifications_intro") {
                        popUpTo("community_intro") { inclusive = true }
                    }
                }
            )
        }
        composable("notifications_intro") {
            NotificationsIntroScreen(
                onDone = {
                    navController.navigate("projection_intro") {
                        popUpTo("notifications_intro") { inclusive = true }
                    }
                }
            )
        }
        composable("projection_intro") {
            ProjectionIntroScreen(
                onContinue = {
                    SessionManager.setProjectionIntroShown(context, true)
                    navController.navigate("broker_setup_intro") {
                        popUpTo("projection_intro") { inclusive = true }
                    }
                }
            )
        }
        composable("broker_setup_intro") {
            BrokerSetupIntroScreen(
                onDone = {
                    navController.navigate("screenshot_guide_intro") {
                        popUpTo("broker_setup_intro") { inclusive = true }
                    }
                }
            )
        }
        composable("screenshot_guide_intro") {
            ScreenshotGuideScreen(
                ctaLabel = "Analyze Now!",
                onCta = {
                    SessionManager.setScreenshotGuideShown(context, true)
                    navController.navigate("first_analysis") {
                        popUpTo("screenshot_guide_intro") { inclusive = true }
                    }
                }
            )
        }
        composable("first_analysis") {
            FirstAnalysisScreen(
                onAnalysisComplete = { analysisId ->
                    navController.navigate("first_signal/$analysisId") {
                        popUpTo("first_analysis")
                    }
                },
                onStockAnalysesComplete = { ids ->
                    navController.navigate("first_stock_results?ids=${ids.joinToString(",")}") {
                        popUpTo("first_analysis")
                    }
                },
                onTrialExpired = {
                    // 402 from the backend: the 7-day trial is over — take the
                    // user straight to the real Subscribe screen.
                    navController.navigate("subscribe")
                },
                onSkip = {
                    // Onboarding is already marked complete by the previous
                    // screen (screenshot guide) — skipping the first analysis
                    // just drops the user straight into the main app, same
                    // as finishing an analysis and tapping Continue.
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(
            "first_stock_results?ids={ids}",
            arguments = listOf(navArgument("ids") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            StockBatchResultScreen(
                analysisIds = entry.arguments?.getString("ids").orEmpty().split(",").filter(String::isNotBlank),
                onBack = {
                    navController.navigate("main") { popUpTo(0) { inclusive = true } }
                },
                onOpenBrokerInfo = { navController.navigate("broker_info") },
                continueCta = "Continue to MarketScope AI" to {
                    navController.navigate("main") { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable("first_signal/{analysisId}") { entry ->
            SignalCardScreen(
                analysisId = entry.arguments?.getString("analysisId") ?: "",
                // Back from the very first analysis result goes straight Home —
                // there's nothing useful behind it (the upload screen is a dead
                // end at this point in onboarding), matching FxLens's behavior.
                onBack = {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenBrokerInfo = { navController.navigate("broker_info") },
                continueCta = "Continue to MarketScope AI" to {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable("screenshot_guide") {
            ScreenshotGuideScreen(
                ctaLabel = "Got it",
                onCta = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable("feedback") {
            com.veltravia.marketscopeai.ui.screens.FeedbackScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("report_bug") {
            com.veltravia.marketscopeai.ui.screens.BugReportScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("broker_info") {
            // Reachable from the Trade Analysis screen's broker card — shows
            // the same broker recommendation as onboarding, but with a back
            // arrow and returns instead of advancing the onboarding flow.
            BrokerSetupIntroScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable("market/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: ""
            MarketViewScreen(
                instrumentId = id,
                onBack = { navController.popBackStack() }
            )
        }
        composable("news_outlook") {
            NewsOutlookScreen(onBack = { navController.popBackStack() })
        }
        composable("learning_hub") {
            LearningHubScreen(
                onBack = { navController.popBackStack() },
                onOpenPattern = { slug -> navController.navigate("learning_pattern/$slug") }
            )
        }
        composable("learning_pattern/{slug}") { entry ->
            LearningPatternScreen(
                slug = entry.arguments?.getString("slug") ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable("risk_calculator") {
            RiskCalculatorScreen(onBack = { navController.popBackStack() })
        }
        composable("journal") {
            JournalScreen(
                onBack = { navController.popBackStack() },
                onOpenEntry = { id ->
                    navController.navigate(
                        if (id.isBlank()) "journal_edit" else "journal_edit/$id"
                    )
                }
            )
        }
        composable(
            "journal_edit?entryId={entryId}",
            arguments = listOf(
                androidx.navigation.navArgument("entryId") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            JournalEntryScreen(
                entryId = entry.arguments?.getString("entryId")?.takeIf { it.isNotBlank() } ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable("calendar") {
            CalendarScreen(onBack = { navController.popBackStack() })
        }
        composable("wall_of_wins") {
            WallOfWinsScreen(onBack = { navController.popBackStack() })
        }
        composable("subscribe") {
            SubscribeScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("referrals") {
            ReferralScreen(onBack = { navController.popBackStack() })
        }
        composable(
            "notifications?highlightId={highlightId}",
            arguments = listOf(
                androidx.navigation.navArgument("highlightId") {
                    type = androidx.navigation.NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            NotificationsScreen(
                onBack = { navController.popBackStack() },
                highlightId = entry.arguments?.getString("highlightId")?.takeIf { it.isNotBlank() }
            )
        }
        composable("dm_threads") {
            DmThreadsScreen(
                onBack = { navController.popBackStack() },
                onOpenThread = { id -> navController.navigate("dm_chat/$id") }
            )
        }
        composable("dm_chat/{threadId}") { entry ->
            DmChatScreen(
                threadId = entry.arguments?.getString("threadId") ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable("leaderboard") {
            LeaderboardScreen(onBack = { navController.popBackStack() })
        }
        composable("signals_admin") {
            AdminSignalsScreen(onBack = { navController.popBackStack() })
        }
        composable("premium_admin") {
            AdminPremiumScreen(onBack = { navController.popBackStack() })
        }
        composable("create_trade_plan") {
            AiTradePlanWizardScreen(
                onBack = { navController.popBackStack() },
                onCreated = { planId ->
                    navController.navigate("ai_trade_plan/$planId") {
                        popUpTo("create_trade_plan") { inclusive = true }
                    }
                }
            )
        }
        composable("ai_trade_plan/{id}") { entry ->
            AiTradePlanDetailScreen(
                planId = entry.arguments?.getString("id") ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable("questionnaire") {
            QuestionnaireScreen(
                onDone = {
                    navController.navigate("community_intro") {
                        popUpTo("questionnaire") { inclusive = true }
                    }
                }
            )
        }
        // Editing the trading profile from Settings: same questionnaire flow,
        // but completing it returns to wherever the user came from instead of
        // running the rest of onboarding again.
        composable("questionnaire_edit") {
            QuestionnaireScreen(
                onDone = { navController.popBackStack() }
            )
        }
        composable("main") {
            MainTabs(navController)
        }
        composable(
            "upload?instrumentId={instrumentId}",
            arguments = listOf(navArgument("instrumentId") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            ChartUploadScreen(
                instrumentId = entry.arguments?.getString("instrumentId") ?: "",
                onBack = { navController.popBackStack() },
                onAnalysisComplete = { analysisId ->
                    navController.navigate("signal/${analysisId}") {
                        popUpTo("main")
                    }
                },
                onStockAnalysesComplete = { ids ->
                    navController.navigate("stock_results?ids=${ids.joinToString(",")}") {
                        popUpTo("main")
                    }
                },
                onUpgradeRequired = { navController.navigate("subscribe") }
            )
        }
        composable("daily_signal/{id}") { entry ->
            DailySignalDetailScreen(
                signalId = entry.arguments?.getString("id") ?: "",
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            "stock_results?ids={ids}",
            arguments = listOf(navArgument("ids") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            val adContext = androidx.compose.ui.platform.LocalContext.current
            StockBatchResultScreen(
                analysisIds = entry.arguments?.getString("ids").orEmpty().split(",").filter(String::isNotBlank),
                onBack = {
                    com.veltravia.marketscopeai.monetization.AdManager
                        .maybeShowInterstitialAfterAnalysis(adContext) { navController.popBackStack() }
                },
                onOpenBrokerInfo = { navController.navigate("broker_info") }
            )
        }
        composable("signal/{analysisId}") { entry ->
            val adContext = androidx.compose.ui.platform.LocalContext.current
            SignalCardScreen(
                analysisId = entry.arguments?.getString("analysisId") ?: "",
                onBack = {
                    // Post-result interstitial (free users only, server-config
                    // frequency-capped; never onboarding). Navigation ALWAYS
                    // continues, with or without an ad.
                    com.veltravia.marketscopeai.monetization.AdManager
                        .maybeShowInterstitialAfterAnalysis(adContext) {
                            navController.popBackStack()
                        }
                },
                onOpenBrokerInfo = { navController.navigate("broker_info") }
            )
        }
    }
}

@Composable
private fun MainTabs(navController: NavHostController) {
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val badgeScope = rememberCoroutineScope()

    // New-content badges for the Signals (tab 1) and Community (tab 2) tabs:
    // how many signals / posts were published since the user's last visit to
    // each tab. Fetched at launch, refreshed while the app is open, and
    // cleared the moment the user lands on the tab (server stamps "seen").
    var signalBadge by rememberSaveable { mutableIntStateOf(0) }
    var communityBadge by rememberSaveable { mutableIntStateOf(0) }

    fun clearBadge(tab: Int) {
        val name = when (tab) {
            1 -> "signals"; 2 -> "community"; else -> return
        }
        if (tab == 1) signalBadge = 0 else communityBadge = 0
        SessionManager.sessionToken(context)?.let { tk ->
            badgeScope.launch {
                try { ApiClient.markTabSeen(tk, name) } catch (_: Exception) { /* retry on next poll */ }
            }
        }
    }

    fun refreshBadges() {
        val tk = SessionManager.sessionToken(context) ?: return
        badgeScope.launch {
            try {
                val resp = ApiClient.fetchTabActivity(tk)
                signalBadge = resp.optInt("signals", 0)
                communityBadge = resp.optInt("community", 0)
            } catch (_: Exception) { /* badges are best-effort */ }
        }
    }

    LaunchedEffect(Unit) { refreshBadges() }
    // Keep the badges fresh while the app is open (new posts / signals can
    // arrive from other team members or the cron pipeline at any time).
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            refreshBadges()
        }
    }

    // Consume a push-notification tap: jump straight to the relevant tab.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        PushRouter.pendingTab?.let {
            currentTab = it
            PushRouter.pendingTab = null
        }
    }
    // Opening a badged tab clears its badge — the content has been seen.
    androidx.compose.runtime.LaunchedEffect(currentTab) {
        clearBadge(currentTab)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PremiumTabBar(
                tabs = tabs.mapIndexed { index, tab ->
                    PremiumTab(
                        label = tab.label,
                        selectedIcon = tab.selectedIcon,
                        unselectedIcon = tab.unselectedIcon,
                        badge = when (index) {
                            1 -> signalBadge
                            2 -> communityBadge
                            else -> 0
                        }
                    )
                },
                selected = currentTab,
                onSelect = { currentTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> HomeScreen(
                    onPickInstrument = {
                        // Skip the full-screen "Choose Instrument" picker —
                        // AnalyzeFlow already has its own inline instrument
                        // picker, so landing there directly saves a screen.
                        navController.navigate("upload") { popUpTo("main") }
                    },
                    onSwitchTab = { index -> currentTab = index },
                    onOpenRiskCalculator = { navController.navigate("risk_calculator") },
                    onOpenJournal = { navController.navigate("journal") },
                    onOpenNotifications = { navController.navigate("notifications") },
                    onCreateTradePlan = { navController.navigate("create_trade_plan") },
                    onOpenCalendar = { navController.navigate("calendar") },
                    onOpenLearningHub = { navController.navigate("learning_hub") },
                    onOpenNewsOutlook = { navController.navigate("news_outlook") },
                    onOpenMarket = { id -> navController.navigate("market/$id") }
                )
                1 -> SignalsScreen(
                    onOpenAdmin = { navController.navigate("signals_admin") },
                    onOpenWall = { navController.navigate("wall_of_wins") },
                    onOpenSignal = { id -> navController.navigate("daily_signal/$id") }
                )
                2 -> CommunityScreen(
                    onOpenLeaderboard = { navController.navigate("leaderboard") },
                    onOpenDms = { navController.navigate("dm_threads") },
                    onOpenDmChat = { id -> navController.navigate("dm_chat/$id") }
                )
                3 -> SavedScreen(
                    onOpenAnalysis = { id ->
                        navController.navigate("signal/${id}")
                    },
                    onCreateTradePlan = { navController.navigate("create_trade_plan") },
                    onOpenSignal = { id -> navController.navigate("daily_signal/$id") },
                    onOpenTradePlan = { id -> navController.navigate("ai_trade_plan/$id") }
                )
                else -> ProfileScreen(
                    onSignOut = {
                        SessionManager.signOut(navController.context)
                        navController.navigate("welcome") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onOpenScreenshotGuide = { navController.navigate("screenshot_guide") },
                    onOpenRiskCalculator = { navController.navigate("risk_calculator") },
                    onOpenJournal = { navController.navigate("journal") },
                    onOpenNotifications = { navController.navigate("notifications") },
                    onOpenSubscribe = { navController.navigate("subscribe") },
                    onOpenReferrals = { navController.navigate("referrals") },
                    onOpenBugReport = { navController.navigate("report_bug") },
                    onOpenFeedback = { navController.navigate("feedback") },
                    onViewSavedTradePlans = { currentTab = 3 },
                    onEditTradingProfile = { navController.navigate("questionnaire_edit") }
                )
            }

            // Floating "Start Analysis" pill — only on Home, matching the
            // FxLens reference layout. Same real destination as the Home
            // screen's own CTA: the instrument picker → chart upload flow.
            // An auto-sized pill (icon + label in a Row) instead of forcing
            // both into a fixed-size circle — the old 92dp circle was too
            // small for "Start Analysis" at readable size, so the label
            // overflowed the circle and floated loose above the nav bar.
            // Offset is negative (upward) so the whole pill sits fully
            // visible, half-overlapping the top edge of the nav bar.
            if (currentTab == 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = (-20).dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.horizontalGradient(listOf(AccentCyan, AccentViolet)))
                        .clickable {
                            // Skip the full-screen "Choose Instrument" picker
                            // — go straight to Analyze the market, which has
                            // its own inline instrument picker.
                            navController.navigate("upload") { popUpTo("main") }
                        }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Start Analysis",
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
