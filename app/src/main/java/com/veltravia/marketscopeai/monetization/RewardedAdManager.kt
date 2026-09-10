package com.veltravia.marketscopeai.monetization

import android.content.Context
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Rewarded ads — the ONLY reward path in the app.
 *
 * Flow: free user hits the daily AI-analysis limit → opts in to watch a
 * short video → the AdMob SDK confirms the reward → ONLY THEN does the app
 * call POST /api/monetization/rewarded-unlock, where the backend re-validates
 * eligibility and the daily cap before granting the bonus.
 *
 * Rewards are ALWAYS non-cash (extra AI analyses) — never money, crypto,
 * gift cards, or any monetary value.
 */
object RewardedAdManager {

    /**
     * Show the rewarded ad and, if the SDK confirms the reward, claim the
     * bonus from the backend.
     *
     * @param onSuccess called with the server-verified usage payload when the
     *        bonus was granted (the caller may immediately run the analysis).
     * @param onFail called with a user-presentable message when the reward
     *        could not be granted (no ad available / cap reached / offline).
     * @param onClosed always called after the ad is dismissed.
     */
    fun watchForExtraAnalysis(
        context: Context,
        scope: CoroutineScope,
        onSuccess: (usage: JSONObject) -> Unit,
        onFail: (message: String) -> Unit,
        onClosed: (watched: Boolean) -> Unit
    ) {
        if (!PremiumAccessManager.rewardedEligible(context)) {
            onFail("Rewarded ads are only available on the free plan.")
            onClosed(false)
            return
        }
        AdManager.showRewarded(
            context,
            onRewarded = {
                // SDK confirmed the reward — now the server validates + grants.
                scope.launch(Dispatchers.IO) {
                    val token = SessionManager.sessionToken(context)
                    if (token == null) {
                        onFail("Please sign in again to claim your bonus.")
                        return@launch
                    }
                    try {
                        val result = ApiClient.postRewardedUnlock(token, "admob")
                        if (result.optBoolean("granted", false)) {
                            result.optJSONObject("usage")?.let { usage -> onSuccess(usage) }
                                ?: onFail("Bonus applied — refresh and try again.")
                        } else {
                            onFail("The bonus could not be applied right now.")
                        }
                    } catch (e: Exception) {
                        // E.g. daily cap reached (429) or rewarded disabled (403).
                        onFail(e.message ?: "The bonus could not be applied right now.")
                    }
                }
            },
            onClosed = { watched -> onClosed(watched) }
        )
    }
}
