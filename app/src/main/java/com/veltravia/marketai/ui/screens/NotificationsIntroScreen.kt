package com.veltravia.marketai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.AccentViolet
import com.veltravia.marketai.ui.theme.NavyBlack
import com.veltravia.marketai.ui.theme.SurfaceLight
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.ui.theme.TextSecondary

/**
 * Soft-ask screen shown right after community onboarding, before the questionnaire.
 * "Enable notifications" triggers the REAL Android runtime permission dialog
 * (POST_NOTIFICATIONS, required on API 33+) — not a cosmetic button. The result is
 * persisted so we don't re-prompt on every app start.
 */
@Composable
fun NotificationsIntroScreen(onDone: () -> Unit) {
    val context = LocalContext.current

    fun alreadyGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    var requesting by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        SessionManager.setNotificationsPromptShown(context, true)
        SessionManager.setNotificationsEnabled(context, granted)
        requesting = false
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(AccentViolet.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = AccentViolet,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Never miss a signal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Turn on notifications so you know the moment something worth acting on happens.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(28.dp))

        Text(
            text = "STAY PLUGGED IN",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.5.sp,
            color = AccentCyan,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(14.dp))

        NotificationBenefitRow(
            icon = Icons.Filled.TrendingUp,
            title = "Free community signals",
            description = "Know the instant a free setup or signal drop lands in the Community feed."
        )
        Spacer(Modifier.height(14.dp))
        NotificationBenefitRow(
            icon = Icons.Filled.SupportAgent,
            title = "Mentor & trade updates",
            description = "Follow-through commentary on active ideas, right when it happens."
        )
        Spacer(Modifier.height(14.dp))
        NotificationBenefitRow(
            icon = Icons.Filled.Shield,
            title = "Account alerts",
            description = "Security notices and analysis updates that actually matter."
        )

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (requesting) AccentViolet.copy(alpha = 0.6f) else AccentViolet)
                .clickable(enabled = !requesting) {
                    if (alreadyGranted()) {
                        SessionManager.setNotificationsPromptShown(context, true)
                        SessionManager.setNotificationsEnabled(context, true)
                        onDone()
                    } else {
                        requesting = true
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable notifications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = NavyBlack
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Maybe later",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    SessionManager.setNotificationsPromptShown(context, true)
                    SessionManager.setNotificationsEnabled(context, false)
                    onDone()
                }
                .padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun NotificationBenefitRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLight)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(3.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
    }
}
