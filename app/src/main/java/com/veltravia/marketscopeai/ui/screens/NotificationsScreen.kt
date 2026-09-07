package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class NotificationRow(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val unread: Boolean
)

/**
 * Real notification feed — every row comes from the backend's notifications
 * table (new signals, comments on your community posts), same events that
 * fire the FCM pushes. Empty state stays honest until events exist.
 */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    var rows by remember { mutableStateOf<List<NotificationRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var unread by remember { mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Android 13+ requires POST_NOTIFICATIONS; per the app's design the OS
    // dialog is only ever launched from an explicit button — here, so that
    // returning users (who skip the onboarding intro) still have a real way
    // to enable pushes.
    var notificationsGranted by remember {
        mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
        SessionManager.setNotificationsEnabled(context, granted)
    }

    fun load() {
        val token = SessionManager.sessionToken(context)
        if (token == null) {
            error = "Sign in to see your notifications."
            rows = emptyList()
            return
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                val res = ApiClient.fetchNotifications(token)
                val arr = res.optJSONArray("notifications") ?: org.json.JSONArray()
                val list = buildList {
                    for (i in 0 until arr.length()) {
                        val n = arr.optJSONObject(i) ?: continue
                        val created = try {
                            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                                .parse(n.optString("created_at").substringBefore("."))?.time ?: 0L
                        } catch (_: Exception) { 0L }
                        add(
                            NotificationRow(
                                id = n.optString("id"),
                                title = n.optString("title"),
                                body = n.optString("body"),
                                createdAt = created,
                                unread = n.isNull("read_at")
                            )
                        )
                    }
                }
                rows = list
                unread = res.optInt("unread", 0)
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Could not load notifications"
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Notifications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (!notificationsGranted) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentCyan.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Turn on notifications so signals and community replies reach you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                TextButton(onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Below Android 13 notifications are granted at install —
                        // reflect the real state instead of showing a dead dialog.
                        notificationsGranted = true
                        SessionManager.setNotificationsEnabled(context, true)
                    }
                }) {
                    Text("Turn on", fontWeight = FontWeight.Bold, color = AccentCyan)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            rows == null && error == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Loading…", color = TextSecondary)
            }
            error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(error ?: "", color = TextSecondary)
            }
            rows != null && rows.orEmpty().isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.NotificationsNone, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No notifications yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "New Daily Signals and activity on your community posts will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            }
            else -> {
                if (unread > 0) {
                    TextButton(onClick = {
                        val token = SessionManager.sessionToken(context) ?: return@TextButton
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                            try {
                                ApiClient.markNotificationsRead(token)
                                load()
                            } catch (_: Exception) { }
                        }
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Mark all as read")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows.orEmpty(), key = { it.id }) { n ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    n.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (n.unread) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (n.unread) Box(
                                    Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(n.body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            if (n.createdAt > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    formatNotificationTime(n.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun formatNotificationTime(epochMs: Long): String {
    val diff = System.currentTimeMillis() - epochMs
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(epochMs))
    }
}
