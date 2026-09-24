package com.veltravia.marketscopeai.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.ui.UserAvatar

import coil.imageLoader
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.monetization.planDisplay
import com.veltravia.marketscopeai.data.ApiConfig
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.data.AccountSnapshot
import com.veltravia.marketscopeai.data.AccountSnapshotCache
import com.veltravia.marketscopeai.shake.ShakeBugReporter
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.components.PremiumSecondaryButton
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
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
    onOpenJournal: () -> Unit = {},
    onOpenNotifications: () -> Unit,
    onOpenSubscribe: () -> Unit,
    onOpenReferrals: () -> Unit,
    onOpenBugReport: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
    onViewSavedTradePlans: () -> Unit,
    onEditTradingProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val token = SessionManager.sessionToken(context)
    val user = SessionManager.currentUser(context)
    val cached = remember(user?.email) { user?.email?.let { AccountSnapshotCache.read(context, it) } }

    var trialActive by remember(user?.email) { mutableStateOf(SessionManager.trialActive(context)) }
    var trialDaysRemaining by remember(user?.email) { mutableStateOf(SessionManager.trialDaysRemaining(context)) }
    var isPremium by remember(user?.email) { mutableStateOf(SessionManager.isPremium(context)) }
    var plan by remember(user?.email) { mutableStateOf(SessionManager.plan(context)) } // free | trial | premium | lifetime
    // Server-derived plan display (handles admin grants correctly, unlike
    // the raw isPremium flag which only reflects a PAID subscription).
    var planEffectivePremium by remember(user?.email) { mutableStateOf(SessionManager.effectivePremium(context)) }
    var planTrailingLabel by remember(user?.email) {
        mutableStateOf(SessionManager.planLabel(context) ?: if (SessionManager.effectivePremium(context)) "Premium" else "Free")
    }
    var accountEmail by remember { mutableStateOf(user?.email ?: "") }
    var deletionRequestedAt by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var deleteBusy by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var shakeToReport by remember(user?.email) {
        mutableStateOf(SessionManager.shakeToReportBug(context) &&
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED))
    }
    val screenshotPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        SessionManager.setShakeToReportBug(context, granted)
        shakeToReport = SessionManager.shakeToReportBug(context)
        (context as? Activity)?.let { activity ->
            if (shakeToReport) ShakeBugReporter.start(activity) else ShakeBugReporter.stop(activity)
        }
    }

    // Profile card (FxLens-style): public handle + custom avatar + real stats.
    var username by remember(user?.email) { mutableStateOf(cached?.username ?: user?.username) }
    var myAvatarUrl by remember(user?.email) {
        mutableStateOf(user?.email?.let { AccountSnapshotCache.avatarUri(context, it) } ?: cached?.avatarUrl)
    }
    var analysesCount by remember(user?.email) { mutableStateOf(cached?.analysesCount) }
    var savedTradesCount by remember(user?.email) { mutableStateOf(cached?.savedTradesCount) }
    var savedPlanCount by remember(user?.email) { mutableStateOf(cached?.savedPlanCount) }
    var avatarUploading by remember { mutableStateOf(false) }
    var showHandleDialog by remember { mutableStateOf(false) }
    var handleInput by remember { mutableStateOf("") }
    var handleBusy by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }

    fun persistSnapshot(avatar: String? = user?.email?.let { AccountSnapshotCache.read(context, it)?.avatarUrl }) {
        user?.email?.let { email ->
            AccountSnapshotCache.save(context, email, AccountSnapshot(
                username, avatar, analysesCount, savedTradesCount, savedPlanCount
            ))
        }
    }

    androidx.compose.runtime.LaunchedEffect(token) {
        if (token == null) return@LaunchedEffect
        // Load plan entitlement and account stats concurrently. The account
        // status endpoint counts the same items actually shown in Saved.
        val trialDeferred = async {
            try { ApiClient.fetchTrialStatus(token) } catch (_: Exception) { null }
        }
        val statusDeferred = async {
            try { ApiClient.fetchAccountStatus(token) } catch (_: Exception) { null }
        }

        val trial = trialDeferred.await()
        if (trial != null) {
            trialActive = trial.optBoolean("trialActive", false)
            trialDaysRemaining = trial.optInt("trialDaysRemaining", 0)
            isPremium = trial.optBoolean("isPremium", false)
            plan = trial.optString("plan", "free")
            val display = planDisplay(trial)
            planEffectivePremium = display.effectivePremium
            planTrailingLabel = display.trailingLabel
            SessionManager.updateTrialState(context, trialActive, trialDaysRemaining, isPremium)
            SessionManager.updatePlan(context, display.plan, display.trailingLabel)
            com.veltravia.marketscopeai.monetization.PremiumAccessManager.updateFromTrialStatus(trial)
        }
        // Cached entitlement stays visible while offline or during a slow refresh.

        val status = statusDeferred.await()
        if (status != null) {
            accountEmail = status.optString("email", accountEmail).takeIf { it != "null" } ?: accountEmail
            deletionRequestedAt = if (status.isNull("deletionRequestedAt")) null else status.optString("deletionRequestedAt")
            username = if (status.isNull("username")) null else status.optString("username")
            val avatar = ApiClient.resolveAvatarUrl(if (status.isNull("avatar")) null else status.optString("avatar"))
            if (status.has("analysesCount")) analysesCount = status.optInt("analysesCount")
            if (status.has("savedTradesCount")) savedTradesCount = status.optInt("savedTradesCount")
            if (status.has("savedTradePlansCount")) savedPlanCount = status.optInt("savedTradePlansCount")
            persistSnapshot(avatar)
            if (avatar == null) {
                user?.email?.let { AccountSnapshotCache.removeAvatar(context, it) }
                myAvatarUrl = null
            } else {
                // Keep the private local photo on screen while refreshing it from R2.
                if (myAvatarUrl == null) myAvatarUrl = avatar
                val email = user?.email
                if (!email.isNullOrBlank()) {
                    launch {
                        runCatching { ApiClient.downloadOwnAvatar(token, avatar) }.getOrNull()?.let { bytes ->
                            AccountSnapshotCache.saveAvatar(context, email, bytes)?.let { myAvatarUrl = it }
                        }
                    }
                }
            }
        }
        // Non-fatal if status is null — Danger Zone just shows the request option.
    }

    val pickAvatar = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val t = token
        if (uri != null && t != null && !avatarUploading) {
            avatarUploading = true
            profileError = null
            scope.launch {
                try {
                    val dataUrl = ApiClient.prepareChartImage(context, uri)
                    val res = ApiClient.uploadProfileAvatar(t, dataUrl)
                    val fresh = ApiClient.resolveAvatarUrl(res.optString("avatar"))
                    if (fresh != null) {
                        // The avatar URL stays constant across uploads, so Coil's
                        // memory and disk caches would keep showing the old photo.
                        // Evict both and re-request with a one-time version suffix
                        // (the API ignores query strings) so the new photo shows
                        // immediately everywhere.
                        val loader = context.imageLoader
                        loader.memoryCache?.remove(coil.memory.MemoryCache.Key(fresh))
                        loader.diskCache?.remove(fresh)
                        myAvatarUrl = user?.email?.let { AccountSnapshotCache.saveUploadedAvatar(context, it, dataUrl) }
                            ?: fresh + "?v=" + System.currentTimeMillis()
                        persistSnapshot(fresh)
                    } else {
                        user?.email?.let { AccountSnapshotCache.removeAvatar(context, it) }
                        myAvatarUrl = null
                        persistSnapshot(null)
                    }
                } catch (e: Exception) {
                    profileError = e.message ?: "Could not update the photo"
                } finally {
                    avatarUploading = false
                }
            }
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
            // No custom avatar -> the app's own default avatar icon, never
            // the Google account photo.
            avatarUrl = myAvatarUrl,
            username = username,
            isPremium = isPremium,
            trialActive = trialActive,
            trialDaysRemaining = trialDaysRemaining,
            analysesCount = analysesCount,
            savedTradesCount = savedTradesCount,
            plan = plan,
            isVerified = planEffectivePremium,
            avatarUploading = avatarUploading,
            profileError = profileError,
            onPickAvatar = {
                pickAvatar.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onEditHandle = {
                handleInput = username ?: ""
                profileError = null
                showHandleDialog = true
            },
            onInvite = onOpenReferrals,
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
                        } catch (e: Exception) {
                            profileError = e.message ?: "Could not cancel account deletion. Please try again."
                        }
                        deleteBusy = false
                    }
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Activity")
        SettingsGroup {
            SettingsRow(
                icon = Icons.Filled.Tune,
                tint = BullGreen,
                label = "Trading profile",
                trailingText = SessionManager.questionnaireAnswers(context)?.style?.ifBlank { null } ?: "Not set",
                onClick = onEditTradingProfile
            )
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
                icon = Icons.Filled.Book,
                tint = BullGreen,
                label = "Trade journal",
                onClick = onOpenJournal
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
                label = if (planEffectivePremium) "Subscribed" else "Upgrade to Premium",
                trailingText = planTrailingLabel,
                onClick = onOpenSubscribe
            )
            SettingsRow(
                icon = Icons.Filled.PersonAddAlt,
                tint = AccentViolet,
                label = "Invite friends",
                onClick = onOpenReferrals,
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
                    val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@marketscopeai.com"))
                    context.startActivity(mail)
                }
            )
            SettingsRow(
                icon = Icons.Filled.Forum,
                tint = AccentViolet,
                label = "Your Opinion",
                onClick = onOpenFeedback
            )
            SettingsRow(
                icon = Icons.Filled.BugReport,
                tint = AccentViolet,
                label = "Report a bug",
                onClick = onOpenBugReport
            )
            SettingsSwitchRow(
                icon = Icons.Filled.Vibration,
                tint = GoldAmber,
                label = "Shake to report a bug",
                helper = "Off until you enable it. When on, a shake saves a screenshot to Photos, copies it, and opens bug reporting.",
                checked = shakeToReport,
                onChecked = { enabled ->
                    if (enabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        screenshotPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        SessionManager.setShakeToReportBug(context, enabled)
                        shakeToReport = SessionManager.shakeToReportBug(context)
                        (context as? Activity)?.let { activity ->
                            if (shakeToReport) ShakeBugReporter.start(activity) else ShakeBugReporter.stop(activity)
                        }
                    }
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
                onClick = { showSignOutConfirm = true }
            )
            if (deletionRequestedAt == null) {
                SettingsRow(
                    icon = Icons.Filled.Shield,
                    tint = BearRed,
                    label = "Delete my account",
                    labelColor = BearRed,
                    onClick = { deleteError = null; showDeleteConfirm = true },
                    showDivider = false
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }

    if (showHandleDialog) {
        HandleEditDialog(
            current = username,
            input = handleInput,
            busy = handleBusy,
            onInput = { raw ->
                handleInput = raw.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '_' }
            },
            onDismiss = { if (!handleBusy) showHandleDialog = false },
            onSave = {
                val t = token ?: return@HandleEditDialog
                if (handleBusy) return@HandleEditDialog
                handleBusy = true
                profileError = null
                scope.launch {
                    try {
                        val res = ApiClient.updateUsername(t, handleInput)
                        username = res.optString("username")
                        persistSnapshot()
                        showHandleDialog = false
                    } catch (e: Exception) {
                        profileError = e.message ?: "Could not save the handle"
                    } finally {
                        handleBusy = false
                    }
                }
            }
        )
    }

    if (showDeleteConfirm) {
        DeleteAccountSheet(
            email = accountEmail,
            busy = deleteBusy,
            error = deleteError,
            onDismiss = { if (!deleteBusy) showDeleteConfirm = false },
            onConfirm = {
                if (deleteBusy) return@DeleteAccountSheet
                val t = token
                if (t == null) {
                    deleteError = "Your sign-in has expired. Please sign in again to request deletion."
                    return@DeleteAccountSheet
                }
                deleteBusy = true
                deleteError = null
                scope.launch {
                    try {
                        val res = ApiClient.requestAccountDeletion(t)
                        val requestedAt = res.optString("deletionRequestedAt").takeIf { it.isNotBlank() && it != "null" }
                            ?: throw IllegalStateException("The server did not confirm your deletion request. Please retry.")
                        deletionRequestedAt = requestedAt
                        showDeleteConfirm = false
                    } catch (e: Exception) {
                        deleteError = e.message ?: "Could not request account deletion. Please try again."
                    } finally {
                        deleteBusy = false
                    }
                }
            }
        )
    }

    if (showSignOutConfirm) {
        SignOutConfirmDialog(
            onDismiss = { showSignOutConfirm = false },
            onConfirm = {
                showSignOutConfirm = false
                onSignOut()
            }
        )
    }
}

/**
 * Sign-out confirmation — a small centered card (title, message, two text
 * actions) so a stray tap on "Sign out" can't drop someone out of their
 * session by accident.
 */
@Composable
private fun SignOutConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Text(
                "Log out?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "You'll need to sign back in to keep using MarketScope AI.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onConfirm) {
                    Text("Log out", fontWeight = FontWeight.SemiBold, color = BearRed)
                }
            }
        }
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
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var understood by remember { mutableStateOf(false) }
    var typedEmail by remember { mutableStateOf("") }
    val confirmationMatches = if (email.isNotBlank()) {
        typedEmail.trim().equals(email.trim(), ignoreCase = true)
    } else {
        typedEmail.trim() == "DELETE"
    }
    val canDelete = understood && confirmationMatches && !busy

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                    .imePadding()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {} // consume taps inside the sheet, don't dismiss
                    .verticalScroll(rememberScrollState())
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
                if (email.isNotBlank()) "Type your email to confirm" else "Type DELETE to confirm",
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = typedEmail,
                onValueChange = { typedEmail = it },
                placeholder = { Text(if (email.isNotBlank()) email else "DELETE") },
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

            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = BearRed)
            }
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
}

@Composable
private fun AccountSummaryCard(
    name: String,
    email: String,
    avatarUrl: String?,
    username: String?,
    isPremium: Boolean,
    trialActive: Boolean,
    trialDaysRemaining: Int,
    analysesCount: Int?,
    savedTradesCount: Int?,
    plan: String,
    isVerified: Boolean,
    avatarUploading: Boolean,
    profileError: String?,
    onPickAvatar: () -> Unit,
    onEditHandle: () -> Unit,
    onInvite: () -> Unit,
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
            // Avatar with a camera badge — tap to upload a custom photo.
            Box {
                UserAvatar(
                    photoUrl = avatarUrl,
                    size = 64.dp,
                    modifier = Modifier.clickable(enabled = !avatarUploading) { onPickAvatar() }
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(AccentViolet, AccentCyan))
                        )
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUploading) {
                        CircularProgressIndicator(
                            strokeWidth = 1.5.dp,
                            color = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Change photo",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isVerified) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Filled.Verified,
                            contentDescription = "Premium verified",
                            tint = GoldAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    PlanChip(
                        isPremium = isPremium,
                        trialActive = trialActive,
                        trialDaysRemaining = trialDaysRemaining,
                        plan = plan,
                        onClick = onUpgrade
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEditHandle() }
                        .padding(vertical = 1.dp)
                ) {
                    Text(
                        if (username.isNullOrBlank()) "Add a handle" else "@$username",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (username.isNullOrBlank()) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (username.isNullOrBlank()) AccentCyan else TextMuted
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit handle",
                        tint = TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
                if (email.isNotBlank()) {
                    Text(
                        email,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (profileError != null) {
            Spacer(Modifier.height(10.dp))
            Text(profileError, style = MaterialTheme.typography.bodySmall, color = BearRed)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Analyses run",
                value = analysesCount?.toString() ?: "—"
            )
            StatTile(
                modifier = Modifier.weight(1f),
                label = "Saved trades",
                value = savedTradesCount?.toString() ?: "—"
            )
        }

        Spacer(Modifier.height(14.dp))

        GradientPrimaryButton(
            text = "Invite friends",
            enabled = true,
            onClick = onInvite,
            modifier = Modifier.fillMaxWidth(),
            height = 46.dp,
            leadingIcon = Icons.Filled.PersonAddAlt
        )
    }
}

/**
 * Small centered editor for the public @handle. Client-side validation
 * mirrors the backend rule (3-20 chars, lowercase letters/numbers/./_),
 * and the backend re-validates + enforces uniqueness on save.
 */
@Composable
private fun HandleEditDialog(
    current: String?,
    input: String,
    busy: Boolean,
    onInput: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Text(
                "Your handle",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "3-20 characters — lowercase letters, numbers, dots or underscores. Other traders will see it as @handle.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInput,
                singleLine = true,
                placeholder = { Text("e.g. euro.trader") },
                modifier = Modifier.fillMaxWidth()
            )
            if (!current.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("Current: @$current", style = MaterialTheme.typography.labelSmall, color = TextMuted)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onSave,
                    enabled = !busy && input.matches(Regex("^[a-z0-9._]{3,20}$")),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentViolet)
                ) {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 1.5.dp, color = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        Text("Save")
                    }
                }
            }
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
private fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    helper: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChecked(!checked) }
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
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(helper, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = TextMuted)
            }
            Spacer(Modifier.width(6.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChecked,
                modifier = Modifier.height(28.dp)
            )
        }
        Box(
            Modifier
                .padding(start = 58.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderSubtle)
        )
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
