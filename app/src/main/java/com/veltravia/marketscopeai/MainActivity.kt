package com.veltravia.marketscopeai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.view.WindowCompat
import com.veltravia.marketscopeai.ui.MarketAiApp
import com.veltravia.marketscopeai.ui.theme.MarketAiTheme

class MainActivity : ComponentActivity() {

    /**
     * Ask for the Android 13+ notification permission at app start.
     *
     * The onboarding flow asks once for new users (NotificationsIntroScreen),
     * but anyone who installed an app update OVER an existing install had
     * already finished onboarding — that screen never ran for them, so the
     * OS silently swallows every push. Requesting here covers that case:
     * every cold start, if the permission is missing, we ask (the OS itself
     * stops showing the dialog after the user denies it twice, so this can
     * never nag). Permission can also be re-granted from system settings.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handlePushIntent(intent: Intent?) {
        val route = intent?.getStringExtra("route") ?: return
        com.veltravia.marketscopeai.ui.PushRouter.pendingTab =
            com.veltravia.marketscopeai.ui.PushRouter.tabForRoute(route)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePushIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Notification channels (signals / community / general).
        com.veltravia.marketscopeai.push.MarketScopeFcmService.createChannels(applicationContext)
        handlePushIntent(intent)
        // Covers users who installed an update over an existing install and
        // never ran the onboarding notification prompt (see kdoc above).
        ensureNotificationPermission()
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
        enableEdgeToEdge()
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
