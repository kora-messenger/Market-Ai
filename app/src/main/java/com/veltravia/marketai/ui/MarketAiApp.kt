package com.veltravia.marketai.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.screens.CommunityIntroScreen
import com.veltravia.marketai.ui.screens.NotificationsIntroScreen
import com.veltravia.marketai.ui.screens.ProjectionIntroScreen
import com.veltravia.marketai.ui.screens.BrokerSetupIntroScreen
import com.veltravia.marketai.ui.screens.CommunityScreen
import com.veltravia.marketai.ui.screens.HomeScreen
import com.veltravia.marketai.ui.screens.ChartUploadScreen
import com.veltravia.marketai.ui.screens.InstrumentPickerScreen
import com.veltravia.marketai.ui.screens.SignalCardScreen
import com.veltravia.marketai.ui.screens.ProfileScreen
import com.veltravia.marketai.ui.screens.ScreenshotGuideScreen
import com.veltravia.marketai.ui.screens.QuestionnaireScreen
import com.veltravia.marketai.ui.screens.SavedScreen
import com.veltravia.marketai.ui.screens.SignalsScreen
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
        // Only gate on community_intro if the backend session exists (i.e. joining is
        // actually possible) and the user hasn't already joined or finished onboarding.
        val needsCommunityIntro = hasSession &&
            SessionManager.sessionToken(context) != null &&
            !SessionManager.communityJoined(context) &&
            !questionnaireDone
        val needsNotificationsIntro = hasSession &&
            !needsCommunityIntro &&
            !SessionManager.notificationsPromptShown(context) &&
            !questionnaireDone
        val needsProjectionIntro = hasSession &&
            !needsCommunityIntro &&
            !needsNotificationsIntro &&
            !SessionManager.projectionIntroShown(context) &&
            !questionnaireDone
        val needsBrokerSetupIntro = hasSession &&
            !needsCommunityIntro &&
            !needsNotificationsIntro &&
            !needsProjectionIntro &&
            !SessionManager.brokerSetupShown(context) &&
            !questionnaireDone
        val needsScreenshotGuideIntro = hasSession &&
            !needsCommunityIntro &&
            !needsNotificationsIntro &&
            !needsProjectionIntro &&
            !needsBrokerSetupIntro &&
            !SessionManager.screenshotGuideShown(context) &&
            !questionnaireDone
        when {
            !hasSession -> "welcome"
            needsCommunityIntro -> "community_intro"
            needsNotificationsIntro -> "notifications_intro"
            needsProjectionIntro -> "projection_intro"
            needsBrokerSetupIntro -> "broker_setup_intro"
            needsScreenshotGuideIntro -> "screenshot_guide_intro"
            !questionnaireDone -> "questionnaire"
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
                    navController.navigate("community_intro") {
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
                    navController.navigate("questionnaire") {
                        popUpTo("screenshot_guide_intro") { inclusive = true }
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
        composable("questionnaire") {
            QuestionnaireScreen(
                onDone = {
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = true }
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
                onBack = { navController.popBackStack() }
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
                    onPickInstrument = { navController.navigate("picker") }
                )
                1 -> SignalsScreen()
                2 -> CommunityScreen()
                3 -> SavedScreen(
                    onOpenAnalysis = { id ->
                        navController.navigate("signal/${id}")
                    }
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
        }
    }
}
