package com.veltravia.marketscopeai.monetization

import android.content.Context
import com.veltravia.marketscopeai.data.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Server-driven monetization configuration.
 *
 * Nothing here is hard-coded across the app: ad placements, frequency, the
 * free daily AI-analysis limit, rewarded-ad availability and which ad
 * networks are enabled all come from GET /api/monetization/config so they
 * can be changed from the admin endpoint WITHOUT an app release.
 *
 * These values are the OFFLINE DEFAULTS used until the first successful
 * fetch (or when the device is offline at startup).
 */
data class MonetizationConfig(
    val adsEnabled: Boolean = true,
    val freeDailyAnalysisLimit: Int = 3,
    val premiumAnalysisUnlimited: Boolean = true,
    val rewardedEnabled: Boolean = true,
    val rewardedBonusPerReward: Int = 1,
    val rewardedMaxUnlocksPerDay: Int = 3,
    val nativeNewsEnabled: Boolean = true,
    val nativeNewsEveryNthItem: Int = 6,
    val nativeNewsMaxPerFeed: Int = 3,
    val interstitialEnabled: Boolean = true,
    val interstitialMinIntervalMinutes: Int = 45,
    val bannerEnabled: Boolean = false,
    val admobEnabled: Boolean = true
) {
    companion object {
        val DEFAULTS = MonetizationConfig()
    }
}

/**
 * In-memory holder for the live config. Screens read from here; one refresh
 * happens on app start (and again whenever the Home screen re-fetches
 * entitlement state), so a config change propagates within one session.
 */
object MonetizationSettings {

    @Volatile
    var current: MonetizationConfig = MonetizationConfig.DEFAULTS
        private set

    @Volatile
    private var fetched = false

    fun parse(json: JSONObject): MonetizationConfig {
        val rewarded = json.optJSONObject("rewarded")
        val placements = json.optJSONObject("placements") ?: JSONObject()
        val native = placements.optJSONObject("nativeNews") ?: JSONObject()
        val inter = placements.optJSONObject("interstitial") ?: JSONObject()
        val banner = placements.optJSONObject("banner") ?: JSONObject()
        val networks = json.optJSONObject("networks") ?: JSONObject()
        val admob = networks.optJSONObject("admob") ?: JSONObject()
        return MonetizationConfig(
            adsEnabled = json.optBoolean("adsEnabled", true),
            freeDailyAnalysisLimit = json.optInt("freeDailyAnalysisLimit", 3),
            premiumAnalysisUnlimited = json.optBoolean("premiumAnalysisUnlimited", true),
            rewardedEnabled = rewarded?.optBoolean("enabled", true) ?: true,
            rewardedBonusPerReward = rewarded?.optInt("bonusPerReward", 1) ?: 1,
            rewardedMaxUnlocksPerDay = rewarded?.optInt("maxUnlocksPerDay", 3) ?: 3,
            nativeNewsEnabled = native.optBoolean("enabled", true),
            nativeNewsEveryNthItem = native.optInt("everyNthItem", 6).coerceAtLeast(3),
            nativeNewsMaxPerFeed = native.optInt("maxPerFeed", 3).coerceAtLeast(1),
            interstitialEnabled = inter.optBoolean("enabled", true),
            interstitialMinIntervalMinutes = inter.optInt("minIntervalMinutes", 45).coerceAtLeast(15),
            bannerEnabled = banner.optBoolean("enabled", false),
            admobEnabled = admob.optBoolean("enabled", true)
        )
    }

    /** Best-effort background refresh; falls back to defaults offline. */
    fun refresh(scope: CoroutineScope, onDone: (() -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            try {
                val response = ApiClient.fetchMonetizationConfig()
                val config = response.optJSONObject("config")
                if (config != null) {
                    current = parse(config)
                    fetched = true
                }
            } catch (_: Exception) {
                // Offline or backend unavailable — keep current/defaults.
            } finally {
                onDone?.invoke()
            }
        }
    }

    fun markFetched() { fetched = true }
}
