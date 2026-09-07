package com.veltravia.marketscopeai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.view.WindowCompat
import com.veltravia.marketscopeai.ui.MarketAiApp
import com.veltravia.marketscopeai.ui.theme.MarketAiTheme

class MainActivity : ComponentActivity() {

    // NOTE: the Android 13+ POST_NOTIFICATIONS permission is requested ONLY when
    // the user taps "Turn on notifications" (NotificationsIntroScreen in
    // onboarding). It must never pop up automatically at app launch.

    private fun handlePushIntent(intent: Intent?) {
        val route = intent?.getStringExtra("route") ?: return
        com.veltravia.marketscopeai.ui.PushRouter.pendingTab =
            com.veltravia.marketscopeai.ui.PushRouter.tabForRoute(route)
    }

    /**
     * marketscopeai://subscribe deep link — the Subscribe button in the
     * trial-expired email opens this. The landing page in the browser fires
     * the link; MainActivity hands it to the nav graph, which opens the
     * real Subscribe screen.
     */
    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "marketscopeai" && data.host == "subscribe") {
            com.veltravia.marketscopeai.ui.PushRouter.pendingSubscribe = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePushIntent(intent)
        handleDeepLink(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Notification channels (signals / community / general).
        com.veltravia.marketscopeai.push.MarketScopeFcmService.createChannels(applicationContext)
        handlePushIntent(intent)
        handleDeepLink(intent)
        // Register this device for FCM pushes whenever the user is signed in.
        if (com.veltravia.marketscopeai.data.SessionManager.sessionToken(applicationContext) != null) {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    com.veltravia.marketscopeai.push.MarketScopeFcmService.registerToken(
                        applicationContext, token
                    )
                }
        }
        if (BuildConfig.DEBUG) {
            CrashReporter.install(applicationContext)
            val lastCrash = CrashReporter.consumeLastCrash(applicationContext)
            if (lastCrash != null) {
                // Show the report as the very first thing — guaranteed to render
                // since this is a fresh cold start, not a rescue mid-crash.
                setContentView(CrashReporter.buildReportView(this, lastCrash))
                return
            }
        }
        // NOTE: deliberately NOT calling enableEdgeToEdge(). Edge-to-edge drew
        // every screen underneath the Android status bar (time/battery/signal),
        // overlapping the app's top UI. With the normal window fit here, the
        // system reserves the status bar and the app content always starts
        // below it. The white status-bar color comes from themes.xml
        // (app_window_background) and the dark status icons from the
        // insets-controller SideEffect below.
        setContent {
            MarketAiTheme {
                // The app is always white/light, so status & nav bar icons are
                // always dark-on-light regardless of the device's dark mode.
                val view = androidx.compose.ui.platform.LocalView.current
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = true
                    controller.isAppearanceLightNavigationBars = true
                }
                // Defensive full-bleed white surface: guarantees every screen —
                // even ones that don't paint every pixel themselves — always
                // shows the app's real white background, never the old dark
                // window background (android:windowBackground) underneath.
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    MarketAiApp()
                }
            }
        }
    }
}
