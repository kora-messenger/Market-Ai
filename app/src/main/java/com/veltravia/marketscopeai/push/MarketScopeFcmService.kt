package com.veltravia.marketscopeai.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.veltravia.marketscopeai.MainActivity
import com.veltravia.marketscopeai.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager

/**
 * Real FCM service for MarketScope AI:
 * - onNewToken registers the device token with the backend (tied to the
 *   signed-in user, so pushes reach the right account across reinstalls)
 * - onMessageReceived builds the actual Android notification on the channel
 *   matching the payload type (signals / community) — users can mute one
 *   and keep the other in system settings.
 * - The app only needs INTERNET + POST_NOTIFICATIONS; no extra permissions.
 */
class MarketScopeFcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        registerToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: message.notification?.category ?: "general"
        val title = message.notification?.title ?: data["title"] ?: "MarketScope AI"
        val body = message.notification?.body ?: data["body"] ?: return
        val channel = when (type) {
            "signal" -> CHANNEL_SIGNALS
            "community" -> CHANNEL_COMMUNITY
            else -> CHANNEL_GENERAL
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data["route"]?.let { putExtra("route", it) }
        }
        val pending = PendingIntent.getActivity(
            this, type.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_market)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(type.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_SIGNALS = "signals"
        const val CHANNEL_COMMUNITY = "community"
        const val CHANNEL_GENERAL = "general"

        /** Create the channels once at app start (no-op below API 26). */
        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_SIGNALS, "Daily Signals", NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "New AI and Team trading signals" },
                NotificationChannel(
                    CHANNEL_COMMUNITY, "Community", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Activity on your community posts" },
                NotificationChannel(
                    CHANNEL_GENERAL, "General", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Other MarketScope AI updates" }
            )
            channels.forEach { nm.createNotificationChannel(it) }
        }

        /**
         * Register (or refresh) this device's FCM token against the signed-in
         * user. Safe to call repeatedly — the backend upserts.
         */
        fun registerToken(context: Context, token: String) {
            val sessionToken = SessionManager.sessionToken(context) ?: return
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ApiClient.registerPushToken(sessionToken, token)
                } catch (_: Exception) {
                    // Non-fatal: retried on next cold start / token refresh.
                }
            }
        }
    }
}
