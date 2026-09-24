package com.veltravia.marketscopeai.data

import android.content.Context
import com.veltravia.marketscopeai.monetization.PremiumAccessManager
import com.veltravia.marketscopeai.monetization.planDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background profile prefetch, launched once at app start while the user is
 * still on Home. Two jobs:
 *
 *  1. It wakes the Render server early — the free plan sleeps after ~15 min
 *     idle, so the first live call after reopening the app can wait up to a
 *     minute on a cold start. Doing it here means the user is never the one
 *     staring at a spinner.
 *  2. It refreshes the exact caches ProfileScreen reads on open (trial/plan
 *     entitlement, username, avatar, real counts), so opening Profile renders
 *     fresh values immediately instead of showing stale data until a live
 *     round-trip lands.
 *
 * ProfileScreen still refreshes on open (source of truth stays the server);
 * this only warms what it renders first. Every step fails silently — a cold
 * or offline prefetch must never block or crash startup.
 */
object ProfilePrefetch {

    suspend fun prefetch(context: Context) = withContext(Dispatchers.IO) {
        val token = SessionManager.sessionToken(context) ?: return@withContext
        val email = SessionManager.currentUser(context)?.email

        // 1. Plan entitlement — same mapping ProfileScreen performs on open.
        try {
            val trial = ApiClient.fetchTrialStatus(token)
            SessionManager.updateTrialState(
                context,
                trial.optBoolean("trialActive", false),
                trial.optInt("trialDaysRemaining", 0),
                trial.optBoolean("isPremium", false)
            )
            val display = planDisplay(trial)
            SessionManager.updatePlan(context, display.plan, display.trailingLabel)
            PremiumAccessManager.updateFromTrialStatus(trial)
        } catch (_: Exception) {
            // Cached entitlement stays visible; Profile retries on open.
        }

        // 2. Account status — public handle, avatar, real activity counts.
        if (email.isNullOrBlank()) return@withContext
        try {
            val status = ApiClient.fetchAccountStatus(token)
            val avatar = ApiClient.resolveAvatarUrl(
                if (status.isNull("avatar")) null else status.optString("avatar")
            )
            AccountSnapshotCache.save(
                context, email,
                AccountSnapshot(
                    username = if (status.isNull("username")) null else status.optString("username"),
                    avatarUrl = avatar,
                    analysesCount = if (status.has("analysesCount")) status.optInt("analysesCount") else null,
                    savedTradesCount = if (status.has("savedTradesCount")) status.optInt("savedTradesCount") else null,
                    savedPlanCount = if (status.has("savedTradePlansCount")) status.optInt("savedTradePlansCount") else null
                )
            )
            if (avatar == null) {
                AccountSnapshotCache.removeAvatar(context, email)
            } else {
                // Warm the private local avatar copy too, so it paints
                // instantly from disk when the Profile tab opens.
                runCatching { ApiClient.downloadOwnAvatar(token, avatar) }.getOrNull()?.let { bytes ->
                    AccountSnapshotCache.saveAvatar(context, email, bytes)
                }
            }
        } catch (_: Exception) {
            // Profile still refreshes live on open.
        }
    }
}
