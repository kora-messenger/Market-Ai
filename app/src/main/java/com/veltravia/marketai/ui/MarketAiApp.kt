package com.veltravia.marketai.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.AccentViolet
import com.veltravia.marketai.ui.screens.CommunityIntroScreen
import com.veltravia.marketai.ui.screens.NotificationsIntroScreen
import com.veltravia.marketai.ui.screens.ProjectionIntroScreen
import com.veltravia.marketai.ui.screens.BrokerSetupIntroScreen
import com.veltravia.marketai.ui.screens.CommunityScreen
import com.veltravia.marketai.ui.screens.HomeScreen
import com.veltravia.marketai.ui.screens.RiskCalculatorScreen
import com.veltravia.marketai.ui.screens.NotificationsScreen
import com.veltravia.marketai.ui.screens.CreateTradePlanScreen
import com.veltravia.marketai.ui.screens.ChartUploadScreen
import com.veltravia.marketai.ui.screens.FirstAnalysisScreen
import com.veltravia.marketai.ui.screens.InstrumentPickerScreen
import com.veltravia.marketai.ui.screens.SignalCardScreen
import com.veltravia.marketai.ui.screens.ProfileScreen
import com.veltravia.marketai.ui.screens.ScreenshotGuideScreen
import com.veltravia.marketai.ui.screens.QuestionnaireScreen
import com.veltravia.marketai.ui.screens.SavedScreen
import com.veltravia.marketai.ui.screens.SignalsScreen
import com.veltravia.marketai.ui.screens.AdminSignalsScreen
import com.veltravia.marketai.ui.screens.WelcomeScreen

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

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("welcome") {
            WelcomeScreen(
                onSignedIn = {
                    navController.navigate("questionnaire") {
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
                continueCta = "Continue to Market Ai" to {
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
        composable("broker_info") {
            // Reachable from the Trade Analysis screen's broker card — shows
            // the same broker recommendation as onboarding, but with a back
            // arrow and returns instead of advancing the onboarding flow.
            BrokerSetupIntroScreen(
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        composable("risk_calculator") {
            RiskCalculatorScreen(onBack = { navController.popBackStack() })
        }
        composable("notifications") {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }
        composable("signals_admin") {
            AdminSignalsScreen(onBack = { navController.popBackStack() })
        }
        composable("create_trade_plan") {
            CreateTradePlanScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
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
        composable("main") {
            MainTabs(navController)
        }
        composable("picker") {
            InstrumentPickerScreen(
                onSelected = { instrument ->
                    navController.navigate("upload/${instrument.id}") {
                        popUpTo("main")
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }
        composable("upload/{instrumentId}") { entry ->
            ChartUploadScreen(
                instrumentId = entry.arguments?.getString("instrumentId") ?: "",
                onBack = { navController.popBackStack() },
                onAnalysisComplete = { analysisId ->
                    navController.navigate("signal/${analysisId}") {
                        popUpTo("main")
                    }
                }
            )
        }
        composable("signal/{analysisId}") { entry ->
            SignalCardScreen(
                analysisId = entry.arguments?.getString("analysisId") ?: "",
                onBack = { navController.popBackStack() },
                onOpenBrokerInfo = { navController.navigate("broker_info") }
            )
        }
    }
}

@Composable
private fun MainTabs(navController: NavHostController) {
    var currentTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == index) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (currentTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> HomeScreen(
                    onPickInstrument = { navController.navigate("picker") },
                    onSwitchTab = { index -> currentTab = index },
                    onOpenRiskCalculator = { navController.navigate("risk_calculator") },
                    onOpenNotifications = { navController.navigate("notifications") },
                    onCreateTradePlan = { navController.navigate("create_trade_plan") }
                )
                1 -> SignalsScreen(
                    onOpenAdmin = { navController.navigate("signals_admin") }
                )
                2 -> CommunityScreen()
                3 -> SavedScreen(
                    onOpenAnalysis = { id ->
                        navController.navigate("signal/${id}")
                    },
                    onCreateTradePlan = { navController.navigate("create_trade_plan") }
                )
                else -> ProfileScreen(
                    onSignOut = {
                        SessionManager.signOut(navController.context)
                        navController.navigate("welcome") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onOpenScreenshotGuide = { navController.navigate("screenshot_guide") }
                )
            }

            // Floating "Start Analysis" button — only on Home, matching the
            // FxLens reference layout. Same real destination as the Home
            // screen's own CTA: the instrument picker → chart upload flow.
            if (currentTab == 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 34.dp)
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AccentCyan, AccentViolet)))
                        .clickable { navController.navigate("picker") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            "Start Analysis",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
