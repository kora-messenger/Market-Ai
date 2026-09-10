package com.veltravia.marketscopeai.monetization

import android.content.Context

/**
 * The single façade the app uses for advertising.
 *
 * Placement policy lives here (driven by the server config):
 *  - ADS ARE OFF for Premium users — no ad is even requested for them
 *    (ads-free across every device signed into the same account).
 *  - Native ads appear in feed contexts only, clearly labeled.
 *  - One interstitial after the user CLOSES an analysis result — never on
 *    app open, never during analysis/chart interaction, frequency-capped.
 *  - Rewarded ads only unlock non-cash features (extra AI analyses) and only
 *    for free users.
 */
object AdManager {

    private val adapter = AdMobAdapter()

    private const val PREFS = "monetization_prefs"
    private const val KEY_LAST_INTERSTITIAL = "last_interstitial_at"

    /** One-time startup: consent → SDK init → prefetch. */
    fun initialize(context: Context) {
        ConsentManager.gatherThenInitialize(context) {
            if (!PremiumAccessManager.isAdFree(context)) {
                prefetchAll(context)
            }
        }
    }

    /** Prefetch according to what the current config allows. */
    fun prefetchAll(context: Context) {
        val cfg = MonetizationSettings.current
        if (!adsAllowed(context)) return
        if (cfg.nativeNewsEnabled) adapter.prefetchNativeAds(context, cfg.nativeNewsMaxPerFeed)
        if (cfg.interstitialEnabled) adapter.prefetchInterstitial(context)
        prefetchRewardedIfAvailable(context)
    }

    fun prefetchRewardedIfAvailable(context: Context) {
        val cfg = MonetizationSettings.current
        if (adsAllowed(context) && cfg.rewardedEnabled && !adapter.hasRewarded(context)) {
            adapter.prefetchRewarded(context)
        }
    }

    /** Master gate: global switch + network enabled + user not Premium. */
    fun adsAllowed(context: Context): Boolean {
        val cfg = MonetizationSettings.current
        if (!cfg.adsEnabled || !cfg.admobEnabled) return false
        return !PremiumAccessManager.isAdFree(context)
    }

    // ---------- Native feed ads ----------

    /**
     * True if the news feed should render an ad slot after item [itemIndex].
     * Pure function of the index so recomposition never double-counts:
     * one slot after every Nth item, capped at maxPerFeed per feed.
     */
    fun shouldShowNativeAt(itemIndex: Int): Boolean {
        val cfg = MonetizationSettings.current
        if (!cfg.nativeNewsEnabled) return false
        val adsBefore = (itemIndex / cfg.nativeNewsEveryNthItem).coerceAtMost(cfg.nativeNewsMaxPerFeed)
        return adsBefore < cfg.nativeNewsMaxPerFeed &&
            (itemIndex + 1) % cfg.nativeNewsEveryNthItem == 0
    }

    fun hasNativeAd(): Boolean = adapter.hasNativeAd()
    fun takeNativeAd(): Any? = adapter.takeNativeAd()

    /** Request one native ad (cached pool first). */
    fun loadOneNative(context: Context, onLoaded: (Any) -> Unit) {
        if (!adsAllowed(context)) return
        (adapter as AdMobAdapter).loadOneNative(context) { ad -> onLoaded(ad) }
    }

    // ---------- Interstitial (post-analysis only) ----------

    /**
     * Frequency-capped interstitial after the user closes an analysis
     * result. [then] ALWAYS runs (with or without an ad) so navigation is
     * never blocked. Never called from onboarding, charts, or mid-analysis.
     */
    fun maybeShowInterstitialAfterAnalysis(context: Context, then: () -> Unit) {
        val cfg = MonetizationSettings.current
        if (!cfg.interstitialEnabled || !adsAllowed(context)) {
            then()
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_INTERSTITIAL, 0L)
        val minMs = cfg.interstitialMinIntervalMinutes * 60_000L
        if (System.currentTimeMillis() - last < minMs) {
            then()
            return
        }
        if (!adapter.hasInterstitial(context)) {
            then()
            return
        }
        adapter.showInterstitial(context) {
            prefs.edit().putLong(KEY_LAST_INTERSTITIAL, System.currentTimeMillis()).apply()
            // Preload the next one in the background.
            adapter.prefetchInterstitial(context)
            then()
        }
    }

    // ---------- Rewarded ----------

    /** Show a rewarded ad. [onRewarded] = SDK confirmed; [onClosed] always. */
    fun showRewarded(context: Context, onRewarded: () -> Unit, onClosed: (watched: Boolean) -> Unit) {
        if (!adapter.hasRewarded(context)) {
            prefetchRewardedIfAvailable(context)
            onClosed(false)
            return
        }
        adapter.showRewarded(context, onRewarded, onClosed)
    }

    /** Call when entitlement turns Premium: drop every cached ad immediately. */
    fun onBecamePremium(context: Context) {
        adapter.destroy(context)
    }

    /** True when a rewarded ad is loaded and allowed for this user. */
    fun rewardedReady(context: Context): Boolean =
        adsAllowed(context) &&
        MonetizationSettings.current.rewardedEnabled &&
        adapter.hasRewarded(context)
}
