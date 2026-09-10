package com.veltravia.marketscopeai.monetization

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Privacy & consent for advertising (Google UMP — the official consent flow
 * required by AdMob in the EEA/UK and other regulated regions).
 *
 * No ad is requested until the user has either consented or the region
 * doesn't require a consent form. Personal information is never collected or
 * shared for ads without the appropriate consent — that's what this flow +
 * the UMP-managed SDK configuration enforce.
 */
object ConsentManager {

    private const val UMP_TEST_DEVICE_HASHED_ID = "" // real device IDs configured in AdMob UI

    /**
     * Gather consent, then initialize the ads SDK and run [onReady].
     * [onReady] is invoked exactly once, regardless of consent outcome —
     * ad requests simply no-op when consent is missing.
     */
    fun gatherThenInitialize(context: Context, onReady: () -> Unit = {}) {
        val activity = context as? Activity
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        // Never fabricate consent state for children; the app targets adults.
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        var ready = false
        fun completeOnce() {
            if (!ready) { ready = true; onReady() }
        }

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Consent info is valid. If a consent form is required by the
                // user's region, Google's official form is loaded and shown.
                if (activity != null) {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                        // Form shown/dismissed (or not required). Initialize ads
                        // only when ads may legally be requested.
                        if (consentInformation.canRequestAds()) {
                            MobileAds.initialize(context) { completeOnce() }
                        } else {
                            completeOnce()
                        }
                    }
                } else {
                    if (consentInformation.canRequestAds()) MobileAds.initialize(context) { completeOnce() }
                    else completeOnce()
                }
            },
            { _ -> completeOnce() } // Consent info unavailable (offline) — ad requests stay off until it succeeds.
        )

        // canRequestAds may already be true from a previous session — UMP keeps
        // consent valid ~7 days; initialize immediately in that case.
        if (consentInformation.canRequestAds() && !ready) {
            MobileAds.initialize(context) { completeOnce() }
        }
    }
}
