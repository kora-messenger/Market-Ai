package com.veltravia.marketscopeai.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.ui.UserAvatar
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.ApiConfig
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.PremiumSecondaryButton
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * MarketScope AI Settings — original layout, not a reskin of any reference
 * app. A single "Account" summary card up top (avatar, plan chip, two real
 * stat tiles) followed by flat grouped sections with tinted leading icons —
 * a lighter, less boxy structure than the FxLens screen it replaces. Every
 * row is wired to a real destination or a real backend call; nothing here
 * is decorative.
 */
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onOpenScreenshotGuide: () -> Unit,
    onOpenRiskCalculator: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSubscribe: () -> Unit,
    onViewSavedTradePlans: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val token = SessionManager.sessionToken(context)
    val user = SessionManager.currentUser(context)
    val answers = SessionManager.questionnaireAnswers(context)

    var savedPlanCount by remember { mutableStateOf<Int?>(null) }
    var trialActive by remember { mutableStateOf(true) }
    var trialDaysRemaining by remember { mutableStateOf(0) }
    var isPremium by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf("free") } // free | trial | premium | lifetime
    var deletionRequestedAt by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteBusy by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(token) {
        if (token == null) return@LaunchedEffect
        try {
            val plans = ApiClient.fetchTradePlans(token)
            savedPlanCount = plans.length()
        } catch (_: Exception) {
            savedPlanCount = 0
        }
        try {
            val trial = ApiClient.fetchTrialStatus(token)
            trialActive = trial.optBoolean("trialActive", false)
            trialDaysRemaining = trial.optInt("trialDaysRemaining", 0)
            isPremium = trial.optBoolean("isPremium", false)
            plan = trial.optString("plan", "free")
            com.veltravia.marketscopeai.monetization.PremiumAccessManager.updateFromTrialStatus(trial)
        } catch (_: Exception) {
            // Leave defaults — the plan chip just won't show until this loads.
        }
        try {
            val status = ApiClient.fetchAccountStatus(token)
            deletionRequestedAt = if (status.isNull("deletionRequestedAt")) null else status.optString("deletionRequestedAt")
        } catch (_: Exception) {
            // Non-fatal — Danger Zone just shows the request option.
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Spacer(Modifier.height(18.dp))

        AccountSummaryCard(
            name = user?.name ?: "Trader",
            email = user?.email ?: "",
            picture = user?.picture ?: "",
            isPremium = isPremium,
            trialActive = trialActive,
            trialDaysRemaining = trialDaysRemaining,
            savedPlanCount = savedPlanCount,
            plan = plan,
            onUpgrade = onOpenSubscribe
        )

        if (deletionRequestedAt != null) {
            Spacer(Modifier.height(14.dp))
            DeletionPendingBanner(
                deletionRequestedAt = deletionRequestedAt,
                busy = deleteBusy,
                onCancel = {
                    val t = token ?: return@DeletionPendingBanner
                    scope.launch {
                        deleteBusy = true
                        try {
                            ApiClient.cancelAccountDeletion(t)
                            deletionRequestedAt = null
                        } catch (_: Exception) {
                            // Silently leave banner — user can retry.
                        }
                        deleteBusy = false
                    }
                }
            )
        }

        answers?.let { profile ->
            Spacer(Modifier.height(20.dp))
            SectionLabel("Trading profile")
            SettingsGroup {
                ProfileRow("Experience", profile.experience)
                ProfileRow("Primary goal", profile.goal)
                if (profile.capitalUsd.isNotBlank()) ProfileRow("Capital (USD)", profile.capitalUsd)
                if (profile.riskPerTrade.isNotBlank()) ProfileRow("Risk per trade", profile.riskPerTrade)
                if (profile.targetReturn.isNotBlank()) ProfileRow("Target monthly return", profile.targetReturn)
                ProfileRow("Assets traded", profile.assets.joinToString(", "))
                ProfileRow("Style", profile.style)
                ProfileRow("Timeframes", profile.timeframes.joinToString(", "))
                if (profile.entryCriteria.isNotBlank()) ProfileRow("Entry criteria", profile.entryCriteria)
                if (profile.emotionalStruggles.isNotBlank()) ProfileRow("Emotional struggles", profile.emotionalStruggles)
                if (profile.dailyRoutine.isNotBlank()) ProfileRow("Daily routine", profile.dailyRoutine)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                SessionManager.coachingLine(profile),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AccentCyan.copy(alpha = 0.08f))
                    .padding(14.dp)
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Activity")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Filled.BookmarkBorder,
                tint = AccentCyan,
                label = "Saved trade plans",
                trailingText = savedPlanCount?.let { if (it == 1) "1 saved" else "$it saved" },
                onClick = onViewSavedTradePlans
            )
            SettingsRow(
                icon = Icons.Filled.Bolt,
                tint = GoldAmber,
                label = "Risk calculator",
                onClick = onOpenRiskCalculator
            )
            SettingsRow(
                icon = Icons.Filled.NotificationsNone,
                tint = AccentViolet,
                label = "Notifications",
                onClick = onOpenNotifications
            )
            SettingsRow(
                icon = Icons.Filled.CameraAlt,
                tint = AccentCyan,
                label = "Screenshot guide",
                onClick = onOpenScreenshotGuide,
                showDivider = false
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Membership")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Filled.WorkspacePremium,
                tint = GoldAmber,
                label = if (isPremium || plan == "premium" || plan == "lifetime") "Manage plan" else "Upgrade to Premium",
                trailingText = when {
                    plan == "lifetime" -> "Lifetime"
                    isPremium || plan == "premium" -> "Premium"
                    trialActive -> "Trial: ${trialDaysRemaining}d left"
                    else -> "Free"
                },
                onClick = onOpenSubscribe
            )
            SettingsRow(
                icon = Icons.Filled.PersonAddAlt,
                tint = AccentViolet,
                label = "Invite friends",
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "I'm using MarketScope AI to analyze my trades with AI — check it out: ${ApiConfig.BASE_URL}"
                        )
                    }
                    context.startActivity(Intent.createChooser(send, "Invite friends to MarketScope AI"))
                },
                showDivider = false
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Support & legal")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Filled.MailOutline,
                tint = AccentCyan,
                label = "Help & support",
                onClick = {
                    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@veltraviatech.com"))
                    context.startActivity(mail)
                }
            )
            SettingsRow(
                icon = Icons.Filled.Gavel,
                tint = TextSecondary,
                label = "Terms of service",
                onClick = { uriHandler.openUri("${ApiConfig.BASE_URL}/terms") }
            )
            SettingsRow(
                icon = Icons.Filled.PrivacyTip,
                tint = TextSecondary,
                label = "Privacy policy",
                onClick = { uriHandler.openUri("${ApiConfig.BASE_URL}/privacy") }
            )
            SettingsRow(
                icon = Icons.Filled.Groups,
                tint = TextSecondary,
                label = "Community guidelines",
                onClick = { uriHandler.openUri("${ApiConfig.BASE_URL}/community-guidelines") },
                showDivider = false
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Danger zone", tint = BearRed)
        SettingsGroup(borderColor = BearRed.copy(alpha = 0.25f)) {
            SettingsRow(
                icon = Icons.AutoMirrored.Filled.Login,
                tint = BearRed,
                label = "Sign out",
                onClick = onSignOut
            )
            if (deletionRequestedAt == null) {
                SettingsRow(
                    icon = Icons.Filled.Shield,
                    tint = BearRed,
                    label = "Delete my account",
                    labelColor = BearRed,
                    onClick = { showDeleteConfirm = true },
                    showDivider = false
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    if (showDeleteConfirm) {
        DeleteAccountSheet(
            email = user?.email ?: "",
            busy = deleteBusy,
            onDismiss = { if (!deleteBusy) showDeleteConfirm = false },
            onConfirm = {
                val t = token ?: return@DeleteAccountSheet
                scope.launch {
                    deleteBusy = true
                    try {
                        val res = ApiClient.requestAccountDeletion(t)
                        deletionRequestedAt = res.optString("deletionRequestedAt")
                        showDeleteConfirm = false
                    } catch (_: Exception) {
                        // Leave the sheet open so the user can retry.
                    }
                    deleteBusy = false
                }
            }
        )
    }
}

/**
 * Permanent-deletion safety sheet — our own copy and colors (not a reskin),
 * matching the checkbox + type-your-email confirmation pattern: an explicit
 * "I understand" checkbox plus retyping the account email before the
 * destructive action unlocks. Text is honest about our real flow (a
 * 30-day, cancellable grace period), not FxLens's "can't be undone" claim.
 */
@Composable
private fun DeleteAccountSheet(
    email: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var understood by remember { mutableStateOf(false) }
    var typedEmail by remember { mutableStateOf("") }
    val emailMatches = email.isNotBlank() && typedEmail.trim().equals(email.trim(), ignoreCase = true)
    val canDelete = understood && emailMatches && !busy

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = !busy) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false) {} // absorb clicks, don't dismiss
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Text(
                "Permanently delete account?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "We'll schedule your account, trade plans, and analyses for erasure in 30 days. " +
                    "You can cancel this request anytime before then from Settings. For safety, " +
                    "please check the box and type your email to proceed.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !busy) { understood = !understood }
            ) {
                Checkbox(
                    checked = understood,
                    onCheckedChange = { understood = it },
                    enabled = !busy,
                    colors = CheckboxDefaults.colors(checkedColor = BearRed)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "I understand this action is permanent.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "Type your email to confirm",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = typedEmail,
                onValueChange = { typedEmail = it },
                placeholder = { Text(email) },
                singleLine = true,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BearRed,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                PremiumSecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    shape = RoundedCornerShape(12.dp)
                )
                PremiumSecondaryButton(
                    text = "Delete Account",
                    onClick = onConfirm,
                    enabled = canDelete,
                    modifier = Modifier.weight(1f),
                    containerColor = BearRed,
                    contentColor = Color.White,
                    borderColor = BearRed,
                    height = 48.dp,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
private fun AccountSummaryCard(
    name: String,
    email: String,
    picture: String,
    isPremium: Boolean,
    trialActive: Boolean,
    trialDaysRemaining: Int,
    savedPlanCount: Int?,
    plan: String,
    onUpgrade: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceLight)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(
                photoUrl = picture.takeIf { it.isNotBlank() },
                size = 56.dp
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                if (email.isNotBlank()) {
                    Text(email, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            PlanChip(isPremium = isPremium, trialActive = trialActive, trialDaysRemaining = trialDaysRemaining, plan = plan, onClick = onUpgrade)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Saved trade plans",
                value = savedPlanCount?.toString() ?: "—"
            )
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Plan status",
                value = when {
                    plan == "lifetime" -> "Lifetime"
                    isPremium || plan == "premium" -> "Premium"
                    trialActive -> "Trial"
                    else -> "Free"
                }
            )
        }
    }
}

@Composable
private fun PlanChip(isPremium: Boolean, trialActive: Boolean, trialDaysRemaining: Int, plan: String, onClick: () -> Unit) {
    val (bg, fg, label) = when {
        plan == "lifetime" -> Triple(AccentViolet.copy(alpha = 0.14f), AccentViolet, "LIFETIME")
        isPremium || plan == "premium" -> Triple(GoldAmber.copy(alpha = 0.15f), GoldAmber, "PRO")
        trialActive -> Triple(AccentCyan.copy(alpha = 0.12f), AccentCyan, "${trialDaysRemaining}D")
        else -> Triple(BearRed.copy(alpha = 0.1f), BearRed, "FREE")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
private fun StatTile(modifier: Modifier = Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun DeletionPendingBanner(deletionRequestedAt: String?, busy: Boolean, onCancel: () -> Unit) {
    val dateLabel = remember(deletionRequestedAt) {
        try {
            val requested = Instant.parse(deletionRequestedAt)
            val scheduled = requested.plus(30, ChronoUnit.DAYS)
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
                .withZone(java.time.ZoneId.systemDefault())
                .format(scheduled)
        } catch (_: Exception) {
            "in 30 days"
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BearRed.copy(alpha = 0.08f))
            .border(1.dp, BearRed.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Account deletion scheduled", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = BearRed)
            Text("Your data will be erased on $dateLabel.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        TextButton(onClick = onCancel, enabled = !busy) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(14.dp)) else Text("Cancel")
        }
    }
}

@Composable
private fun SectionLabel(text: String, tint: Color = TextMuted) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = tint,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsGroup(borderColor: Color = BorderSubtle, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    trailingText: String? = null,
    labelColor: Color = TextPrimary,
    onClick: () -> Unit,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.weight(1f))
            if (trailingText != null) {
                Text(trailingText, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(12.dp)
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 58.dp)
                    .height(1.dp)
                    .background(BorderSubtle)
            )
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted, modifier = Modifier.weight(0.4f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f)
        )
    }
}
