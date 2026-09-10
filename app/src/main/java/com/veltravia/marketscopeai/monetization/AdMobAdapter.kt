package com.veltravia.marketscopeai.monetization

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.veltravia.marketscopeai.BuildConfig
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AdMob adapter — the primary network.
 *
 * Ad unit IDs come from BuildConfig:
 *  - DEBUG builds (and any build without configured properties) use Google's
 *    OFFICIAL TEST ad units. Test ads are never clicked in testing revenue
 *    terms — they produce no impressions income and are the policy-correct
 *    way to develop.
 *  - RELEASE builds read the real units from gradle properties
 *    (-PADMOB_UNIT_NATIVE=... etc.), documented in the release checklist.
 */
class AdMobAdapter : AdNetworkAdapter {

    override val networkKey: String = "admob"

    private val nativeAdPool = CopyOnWriteArrayList<NativeAd>()
    private val nativeCallbacks = CopyOnWriteArrayList<(NativeAd) -> Unit>()

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null

    private fun adRequest(): AdRequest = AdRequest.Builder().build()

    override fun initialize(context: Context) {
        // MobileAds.initialize is handled by ConsentManager right before ads
        // may be requested — nothing to do here for AdMob.
    }

    // ---------- Native (feed) ads ----------

    override fun hasNativeAd(context: Context): Boolean = nativeAdPool.isNotEmpty()

    override fun takeNativeAd(context: Context): Any? =
        if (nativeAdPool.isEmpty()) null else nativeAdPool.removeAt(0)

    override fun prefetchNativeAds(context: Context, count: Int) {
        for (i in 0 until count) {
            val loader = AdLoader.Builder(context, BuildConfig.ADMOB_UNIT_NATIVE)
                .forNativeAd { ad ->
                    nativeAdPool.add(ad)
                    nativeCallbacks.forEach { cb -> cb(ad) }
                    nativeCallbacks.clear()
                }
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .build()
                )
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        // No ad available right now — feeds simply render one
                        // fewer card. Never fake or retry-spam.
                    }
                })
                .build()
            loader.loadAd(adRequest())
        }
    }

    /** Request one native ad and hand it back via [onLoaded] (or never). */
    fun loadOneNative(context: Context, onLoaded: (NativeAd) -> Unit) {
        if (nativeAdPool.isNotEmpty()) {
            onLoaded(nativeAdPool.removeAt(0))
            return
        }
        nativeCallbacks.add(onLoaded)
        prefetchNativeAds(context, 1)
    }

    // ---------- Interstitial ----------

    override fun hasInterstitial(context: Context): Boolean = interstitial != null

    override fun prefetchInterstitial(context: Context) {
        if (interstitial != null) return
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_UNIT_INTERSTITIAL,
            adRequest(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitial = null }
            }
        )
    }

    override fun showInterstitial(context: Context, onClosed: () -> Unit) {
        val ad = interstitial
        interstitial = null
        if (ad == null) {
            onClosed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { onClosed() }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) { onClosed() }
        }
        val activity = context as? Activity
        if (activity == null) onClosed() else ad.show(activity)
    }

    // ---------- Rewarded ----------

    override fun hasRewarded(context: Context): Boolean = rewarded != null

    override fun prefetchRewarded(context: Context) {
        if (rewarded != null) return
        RewardedAd.load(
            context,
            BuildConfig.ADMOB_UNIT_REWARDED,
            adRequest(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { rewarded = null }
            }
        )
    }

    override fun showRewarded(context: Context, onRewarded: () -> Unit, onClosed: (watched: Boolean) -> Unit) {
        val ad = rewarded
        rewarded = null
        if (ad == null) {
            onClosed(false)
            return
        }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { onClosed(earned) }
            override fun onAdFailedToShowFullScreenContent(adError: AdError) { onClosed(false) }
        }
        val activity = context as? Activity
        if (activity == null) {
            onClosed(false)
        } else {
            ad.show(activity) { _ ->
                // SDK-confirmed reward: only NOW does the backend unlock happen.
                earned = true
                onRewarded()
            }
        }
    }

    override fun destroy(context: Context) {
        nativeAdPool.forEach { it.destroy() }
        nativeAdPool.clear()
        interstitial = null
        rewarded = null
    }
}
