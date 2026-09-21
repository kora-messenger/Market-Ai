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
        if (route == "dm") {
            // Mentor-DM push — open the private chat thread directly.
            val threadId = intent.getStringExtra("threadId")
            if (!threadId.isNullOrBlank()) {
                com.veltravia.marketscopeai.ui.PushRouter.pendingDmThreadId = threadId
            }
            return
        }
        if (route == "notifications") {
            // Billing push (payment succeeded/failed) — open the
            // Notifications screen directly, scrolled to that exact message.
            com.veltravia.marketscopeai.ui.PushRouter.pendingHighlightNotificationId =
                intent.getStringExtra("notificationId")
            com.veltravia.marketscopeai.ui.PushRouter.pendingOpenNotifications = true
            return
        }
        if (route.startsWith("market/")) {
            // Price-alert push tapped — open that instrument's Market View.
            com.veltravia.marketscopeai.ui.PushRouter.pendingMarketId = route.removePrefix("market/")
            return
        }
        if (route.startsWith("daily_signal/")) {
            // TP-hit / signal push tapped — open that signal's detail screen.
            com.veltravia.marketscopeai.ui.PushRouter.pendingSignalId = route.removePrefix("daily_signal/")
            return
        }
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

    // ---------- presence heartbeat (online status) ----------
    // Pings the backend every 2 minutes while the app is in the FOREGROUND so
    // the community can show an honest "online now" count. Stopped on onStop —
    // a user who backgrounds the app is honestly no longer "online".
    private val presenceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val presenceExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val presenceTick = object : Runnable {
        override fun run() {
            val token = com.veltravia.marketscopeai.data.SessionManager.sessionToken(applicationContext)
            if (token != null) {
                presenceExecutor.execute {
                    kotlinx.coroutines.runBlocking {
                        runCatching { com.veltravia.marketscopeai.data.ApiClient.presencePing(token) }
                    }
                }
            }
            presenceHandler.postDelayed(this, 120_000L)
        }
    }

    override fun onStart() {
        super.onStart()
        presenceTick.run()
        ensurePushTokenRegistered()
        // Shake-to-report-bug: registered fresh every foreground while the
        // pref is on, so toggling it takes effect on the next app open even
        // if the in-session start/stop was missed.
        com.veltravia.marketscopeai.shake.ShakeBugReporter.start(applicationContext)
    }

    /**
     * Fetch (or refresh) the FCM token and register it with the backend.
     * Called on every foreground (onStart), not just cold start — Play
     * Services can be mid-initialization right after a fresh install/update,
     * which silently drops a cold-start-only registration with no retry.
     * FirebaseMessaging.getInstance().token is cheap/cached internally, so
     * repeating this on every resume is safe and is Firebase's own
     * recommended pattern.
     */
    private fun ensurePushTokenRegistered() {
        if (com.veltravia.marketscopeai.data.SessionManager.sessionToken(applicationContext) == null) return
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                com.veltravia.marketscopeai.push.MarketScopeFcmService.registerToken(
                    applicationContext, token
                )
            }
            .addOnFailureListener { err ->
                android.util.Log.w("MarketScopeAI", "FCM token fetch failed: ${err.message}")
            }
    }

    override fun onStop() {
        super.onStop()
        presenceHandler.removeCallbacks(presenceTick)
        com.veltravia.marketscopeai.shake.ShakeBugReporter.stop(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Notification channels (signals / community / general).
        com.veltravia.marketscopeai.push.MarketScopeFcmService.createChannels(applicationContext)
        handlePushIntent(intent)
        handleDeepLink(intent)

        // --- Monetization boot (Free + Premium + Advertising) ---
        // 1) Privacy-first: gather any consent the region requires (UMP),
        //    then initialize the Mobile Ads SDK — ads are only requested once
        //    they may legally be shown. Premium users never reach ad code.
        com.veltravia.marketscopeai.monetization.AdManager.initialize(this)
        // 2) Pull the live server-driven monetization config (placements,
        //    limits, rewarded availability) so nothing is hard-coded.
        com.veltravia.marketscopeai.monetization.MonetizationSettings.refresh(
            kotlinx.coroutines.MainScope()
        )
        // 3) Mirror the server's entitlement state (ad eligibility) for the
        //    signed-in user — the backend stays the single authority.
        com.veltravia.marketscopeai.monetization.PremiumAccessManager.refresh(
            applicationContext, kotlinx.coroutines.MainScope()
        )
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
        // Safe content install: on a minority of devices (seen on Samsung
        // One UI cold starts) the window's content container is not yet
        // attached when the activity launches, and Compose's setContent
        // throws "Window couldn't find content container view". Retrying on
        // the next main-loop passes lets the window finish attaching — the
        // app starts normally instead of crash-looping.
        installAppContent()
    }

    private var contentInstallAttempts = 0

    private fun installAppContent() {
        try {
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
        } catch (t: Throwable) {
            contentInstallAttempts++
            if (contentInstallAttempts <= 3) {
                android.os.Handler(mainLooper).postDelayed({ installAppContent() }, 120L)
            } else {
                // Out of retries — surface through the debug crash reporter
                // (or rethrow on release so the system handles it).
                throw t
            }
        }
    }
}
