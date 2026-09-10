package com.veltravia.marketscopeai.monetization

import android.content.Context

/**
 * Mediation contract — the ONE interface the app knows about.
 *
 * The rest of the app never imports a specific ad network SDK surface; it
 * talks to AdManager, which routes to whichever adapter is enabled in the
 * server config. AdMob ships today; adding AppLovin, Meta Audience Network,
 * InMobi, Liftoff, Pangle, Unity/LevelPlay, Mintegral, Smaato or Moloco later
 * means writing a new [AdNetworkAdapter] and enabling it in the config —
 * no placement code changes anywhere.
 */
interface AdNetworkAdapter {
    /** Network key as it appears in the server config (e.g. "admob"). */
    val networkKey: String

    /** Warm the SDK (called once, after consent). */
    fun initialize(context: Context)

    /** True when a native ad is cached and ready to render. */
    fun hasNativeAd(): Boolean

    /** Pop the next cached native ad, or null. */
    fun takeNativeAd(): Any?

    /** Prefetch up to [count] native ads for feed placements. */
    fun prefetchNativeAds(context: Context, count: Int)

    /** True when an interstitial is loaded. */
    fun hasInterstitial(context: Context): Boolean

    /** Load an interstitial for the next eligible moment. */
    fun prefetchInterstitial(context: Context)

    /**
     * Show the loaded interstitial. [onClosed] fires when the ad is dismissed
     * (or immediately if nothing is loaded — never block the user flow).
     */
    fun showInterstitial(context: Context, onClosed: () -> Unit)

    /** True when a rewarded ad is loaded. */
    fun hasRewarded(context: Context): Boolean

    /** Load a rewarded ad. */
    fun prefetchRewarded(context: Context)

    /**
     * Show the rewarded ad. [onRewarded] fires ONLY when the SDK confirms
     * the user earned the reward; [onClosed] always fires after dismissal.
     */
    fun showRewarded(context: Context, onRewarded: () -> Unit, onClosed: (watched: Boolean) -> Unit)

    /** Tear down cached ads (e.g. when the user becomes Premium). */
    fun destroy(context: Context)
}
