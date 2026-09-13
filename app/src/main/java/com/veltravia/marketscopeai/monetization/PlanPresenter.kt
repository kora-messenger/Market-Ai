package com.veltravia.marketscopeai.monetization

import org.json.JSONObject

/**
 * What a screen should render for the user's plan, derived from a
 * /api/trial/status response. This is the SINGLE place that turns the
 * server's verdict (plan / premiumSource / adminGrant / trial) into display
 * text — so "Subscribed", "Monthly", "Lifetime" etc. read the same
 * everywhere instead of each screen guessing from the raw isPremium flag
 * (which only reflects a PAID subscription and misses admin grants).
 */
data class PlanDisplay(
    /** True Premium access — paid subscription OR an admin grant. */
    val effectivePremium: Boolean,
    /** free | trial | premium | lifetime, straight from the server. */
    val plan: String,
    /** Short label for what they're actually on: "Monthly", "Lifetime",
     *  "3 Months", "Trial: 2d left", or "Free". */
    val trailingLabel: String
)

/** Admin-grant label like "3 months Premium" -> "3 Months" (drop the
 *  " Premium" suffix, capitalize). Falls back to "Premium" if unparsable. */
private fun grantTrailingLabel(adminGrant: JSONObject): String {
    if (adminGrant.optString("kind", "") == "lifetime") return "Lifetime"
    val label = adminGrant.optString("label", "").removeSuffix(" Premium").trim()
    return if (label.isNotBlank()) label.replaceFirstChar { it.uppercase() } else "Premium"
}

/** Build the display verdict from a raw /api/trial/status JSON body. */
fun planDisplay(status: JSONObject): PlanDisplay {
    val plan = status.optString("plan", "free")
    val premiumSource = status.optString("premiumSource", "")
    val trialActive = status.optBoolean("trialActive", false)
    val trialDaysRemaining = status.optInt("trialDaysRemaining", 0)
    val adminGrant = status.optJSONObject("adminGrant")
    val effectivePremium = plan == "premium" || plan == "lifetime"

    val trailingLabel = when {
        plan == "lifetime" -> "Lifetime"
        plan == "premium" && premiumSource == "admin_grant" && adminGrant != null ->
            grantTrailingLabel(adminGrant)
        // Paid subscription — currently the only recurring product is
        // monthly (Paystack and Google Play premium-monthly alike).
        plan == "premium" -> "Monthly"
        trialActive -> "Trial: ${trialDaysRemaining}d left"
        else -> "Free"
    }

    return PlanDisplay(effectivePremium = effectivePremium, plan = plan, trailingLabel = trailingLabel)
}
