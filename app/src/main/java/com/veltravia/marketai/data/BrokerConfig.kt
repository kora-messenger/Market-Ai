package com.veltravia.marketai.data

/**
 * The broker MarketScope AI recommends (and links to) in onboarding and on the
 * Trade Analysis screen. Deliberately does NOT reuse any third-party app's
 * affiliate URL — this is Ijezie's own Exness referral link (provided
 * 2026-09-06). Swap values here to change it app-wide.
 */
object BrokerConfig {
    const val NAME = "Exness"

    /** Ijezie's own Exness referral link. */
    const val REFERRAL_URL = "https://one.exnessonelink.com/a/c1bre6uiv5"
}
