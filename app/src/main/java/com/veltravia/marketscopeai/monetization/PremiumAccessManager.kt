package com.veltravia.marketscopeai.monetization

import android.content.Context
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The app-side view of the SERVER-AUTHORITATIVE Premium entitlement.
 *
 * The backend is the only authority on Premium state (paid subscription OR
 * trial OR admin grant) — this object just mirrors what /api/trial/status
 * reports so the ad system can decide eligibility. Premium = ad-free across
 * every device signed into the same account, because the state is fetched
 * from the server, not stored locally at purchase time.
 */
object PremiumAccessManager {

    /** Latest server-reported ad eligibility (null until first fetch). */
    @Volatile
    var adsEnabledByServer: Boolean? = null
        private set

    @Volatile
    var rewardedAvailableByServer: Boolean? = null
        private set

    /** Feed a raw /api/trial/status response into the entitlement state. */
    fun updateFromTrialStatus(status: JSONObject) {
        adsEnabledByServer = status.optBoolean("adsEnabled", adsEnabledByServer ?: true)
        rewardedAvailableByServer = status.optBoolean("rewardedAvailable", rewardedAvailableByServer ?: true)
        // Keep the legacy SessionManager fields in sync for existing UI.
    }

    /**
     * True when this user must not see ads. Trusts the server verdict when
     * we have one; falls back to the persisted session state offline.
     */
    fun isAdFree(context: Context): Boolean {
        adsEnabledByServer?.let { return !it }
        // Offline fallback: premium flag or an active trial means ad-free.
        return SessionManager.isPremium(context) || SessionManager.trialActive(context)
    }

    /** True when a rewarded ad may be offered (free users, config allowing). */
    fun rewardedEligible(context: Context): Boolean {
        rewardedAvailableByServer?.let { return it }
        return !isAdFree(context)
    }

    /**
     * Background refresh of entitlement + monetization config. Called at app
     * start and whenever Home re-fetches. When the user has just become
     * Premium, every cached ad is dropped on the spot.
     */
    fun refresh(context: Context, scope: CoroutineScope) {
        val token = SessionManager.sessionToken(context) ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val status = ApiClient.fetchTrialStatus(token)
                val wasAdFree = isAdFree(context)
                updateFromTrialStatus(status)
                SessionManager.updateTrialState(
                    context,
                    status.optBoolean("trialActive", false),
                    status.optInt("trialDaysRemaining", 0),
                    status.optBoolean("isPremium", false)
                )
                val nowAdFree = isAdFree(context)
                if (!wasAdFree && nowAdFree) {
                    AdManager.onBecamePremium(context)
                } else if (wasAdFree != nowAdFree || !nowAdFree) {
                    AdManager.prefetchRewardedIfAvailable(context)
                }
            } catch (_: Exception) {
                // Offline — keep the last known state.
            }
        }
    }
}
