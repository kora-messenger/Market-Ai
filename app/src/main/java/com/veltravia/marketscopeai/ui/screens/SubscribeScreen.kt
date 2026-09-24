package com.veltravia.marketscopeai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.veltravia.marketscopeai.billing.PlayBillingHelper
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.monetization.planDisplay
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.components.PremiumSecondaryButton
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private data class BillingOption(
    val id: String, // "monthly" | "yearly"
    val label: String,
    val price: Double,
    val period: String,
    val productId: String,
    val discountPercent: Int? = null,
    val monthlyEquivalent: Double? = null,
    val savingsLabel: String? = null
)

private data class SubPlan(
    val id: String,
    val name: String,
    val price: Double,
    val period: String?,
    val features: List<String>,
    val billingOptions: List<BillingOption> = emptyList()
)

/**
 * Subscribe screen — where a user whose 7-day free trial has ended (or anyone
 * who wants Premium early) subscribes to MarketScope AI Premium.
 *
 * Layout follows the "Free plan / Premium" tab pattern (own copy, own
 * colors — cyan/violet brand, not a copy of any reference app's visual
 * style): a tab switch between what the account already has (Free) and what
 * a subscription unlocks (Premium), each with its own real feature list from
 * the backend. Price, features and payment readiness all come straight from
 * /api/subscription/plans — no placeholder numbers.
 *
 * Reached via the marketscopeai://subscribe deep link (the trial-expired
 * email button) and automatically when a chart analysis hits the 402
 * trial-expired response.
 */
@Composable
fun SubscribeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val sessionToken = remember { SessionManager.sessionToken(context) }

    // Plan info from the backend (single source of truth for price/features).
    var plansLoading by remember { mutableStateOf(true) }
    var planError by remember { mutableStateOf<String?>(null) }
    var freePlan by remember { mutableStateOf<SubPlan?>(null) }
    var premiumPlan by remember { mutableStateOf<SubPlan?>(null) }
    var planCurrency by remember { mutableStateOf("USD") }

    // Which billing option is selected within the Premium tab: "monthly"
    // (default) or "yearly" (10% cheaper, billed once a year).
    var selectedBilling by remember { mutableStateOf("monthly") }
    // Google Play product ids the backend actually has wired up right now —
    // only offer the Play button for a billing choice if its product id is
    // in this set (e.g. "premium-yearly" may not exist in Play Console yet).
    var allowedGooglePlayIds by remember { mutableStateOf(setOf<String>()) }

    // Which tab is showing: 0 = Free, 1 = Premium.
    var selectedTab by remember { mutableIntStateOf(1) }

    // Live account state.
    var isPremium by remember { mutableStateOf(SessionManager.isPremium(context)) }
    var trialDaysRemaining by remember { mutableStateOf(SessionManager.trialDaysRemaining(context)) }
    // True Premium access — paid subscription OR an admin grant (the raw
    // isPremium flag above only reflects a PAID subscription, so an
    // admin-granted Lifetime/Premium user would otherwise still see the
    // buy button as if they had nothing).
    var effectivePremium by remember { mutableStateOf(SessionManager.effectivePremium(context)) }
    var planTrailingLabel by remember { mutableStateOf(SessionManager.planLabel(context) ?: "Premium") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Google Play Billing is the only payment method. It requires a Play
    // installation and a configured subscription product.
    var googlePlayEnabled by remember { mutableStateOf(false) }
    var googleBusy by remember { mutableStateOf(false) }

    // Google Play Billing wrapper — one instance for this screen's lifetime.
    // The token Google hands back is verified by OUR backend before Premium
    // activates; the client is never the authority.

    fun refreshStatus() {
        val token = sessionToken ?: return
        scope.launch {
            try {
                val status = ApiClient.fetchTrialStatus(token)
                val premium = status.optBoolean("isPremium", isPremium)
                val days = status.optInt("trialDaysRemaining", trialDaysRemaining)
                val active = status.optBoolean("trialActive", premium)
                isPremium = premium
                trialDaysRemaining = days
                val display = planDisplay(status)
                effectivePremium = display.effectivePremium
                planTrailingLabel = display.trailingLabel
                SessionManager.updateTrialState(context, active, days, premium)
                SessionManager.updatePlan(context, display.plan, display.trailingLabel)
                if (display.effectivePremium) {
                    statusMessage = "Premium is now active on your account. Enjoy unlimited access!"
                    selectedTab = 1
                }
            } catch (_: Exception) {
                // Status refresh is best-effort; the screen keeps its current state.
            }
        }
    }
    // Kotlin won't let the helper's own callbacks capture the val while it's
    // being initialized — so acknowledge() goes through a nullable ref that
    // is assigned the moment the helper exists (the callback only fires
    // long after composition).
    var billingHelperRef: PlayBillingHelper? = null
    val billingHelper = remember {
        PlayBillingHelper(
            context = context,
            onPurchase = { productId, purchaseToken ->
                scope.launch {
                    // Billing buttons only render when signed in — guarded
                    // anyway so the type is a plain String below.
                    val token = sessionToken ?: return@launch
                    try {
                        val result = ApiClient.verifyGooglePlayPurchase(token, productId, purchaseToken)
                        if (result.optBoolean("active", false)) {
                            // Verified server-side — now acknowledge so Google
                            // doesn't auto-refund the purchase.
                            billingHelperRef?.acknowledge(purchaseToken)
                            isPremium = true
                            effectivePremium = true
                            statusMessage = "Premium is now active on your account. Enjoy unlimited access!"
                            refreshStatus()
                        } else {
                            statusMessage = "Google hasn't activated this subscription yet. If you were just charged, it will activate automatically once Google confirms the payment."
                        }
                    } catch (e: Exception) {
                        statusMessage = e.message ?: "Could not confirm this purchase with our server. Please try again shortly."
                    } finally {
                        googleBusy = false
                    }
                }
            },
            onError = { message ->
                statusMessage = message
                googleBusy = false
            }
        ).also { billingHelperRef = it }
    }
    DisposableEffect(Unit) {
        onDispose { billingHelper.close() }
    }

    // --- Google Play checkout: connect → find the product → Play sheet ---
    fun startGooglePlayCheckout() {
        val productId = premiumPlan?.billingOptions
            ?.firstOrNull { it.id == selectedBilling }
            ?.productId
            ?: return
        if (!googlePlayEnabled || productId !in allowedGooglePlayIds) return
        val activity = context as? android.app.Activity ?: return
        googleBusy = true
        statusMessage = null
        billingHelper.connect { ready ->
            if (!ready) {
                googleBusy = false
                statusMessage = "Google Play billing isn't available on this device or installation. Install the app from Google Play and try again."
                return@connect
            }
            scope.launch {
                val details = billingHelper.querySubscription(productId)
                if (details == null) {
                    googleBusy = false
                    statusMessage = "This Premium plan isn't available in Google Play yet. Check that the app was installed from Google Play, or try again later."
                } else {
                    // googleBusy stays true until the sheet closes and the
                    // purchase is verified (or the user cancels).
                    billingHelper.launchPurchase(activity, details)
                }
            }
        }
    }

    // Load both plans once.
    LaunchedEffect(Unit) {
        try {
            val plans = ApiClient.fetchSubscriptionPlans()
            planCurrency = plans.optString("currency", planCurrency).uppercase()
            val methods = plans.optJSONObject("paymentMethods")
            var playIds = setOf<String>()
            methods?.optJSONObject("googlePlay")?.let { gp ->
                googlePlayEnabled = gp.optBoolean("enabled", false)
                val ids = gp.optJSONArray("productIds")
                val allIds = ids?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() && it != "null" }
                } ?: emptyList()
                playIds = allIds.toSet()
            }
            allowedGooglePlayIds = playIds
            val arr = plans.optJSONArray("plans") ?: org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val feats = mutableListOf<String>()
                p.optJSONArray("features")?.let { fa ->
                    for (j in 0 until fa.length()) feats.add(fa.optString(j))
                }
                val billing = mutableListOf<BillingOption>()
                p.optJSONArray("billingOptions")?.let { ba ->
                    for (j in 0 until ba.length()) {
                        val b = ba.optJSONObject(j) ?: continue
                        billing.add(
                            BillingOption(
                                id = b.optString("id"),
                                label = b.optString("label"),
                                price = b.optDouble("price", 0.0),
                                period = b.optString("period", "month"),
                                productId = b.optString("productId"),
                                discountPercent = b.optInt("discountPercent", -1).takeIf { it >= 0 },
                                monthlyEquivalent = b.optDouble("monthlyEquivalent", -1.0).takeIf { it >= 0 },
                                savingsLabel = b.optString("savingsLabel").takeIf { it.isNotBlank() && it != "null" }
                            )
                        )
                    }
                }
                val parsed = SubPlan(
                    id = p.optString("id"),
                    name = p.optString("name"),
                    price = p.optDouble("price", 0.0),
                    period = p.optString("period").takeIf { it.isNotBlank() && it != "null" },
                    features = feats,
                    billingOptions = billing
                )
                when (parsed.id) {
                    "free" -> freePlan = parsed
                    "premium" -> premiumPlan = parsed
                }
            }
        } catch (e: Exception) {
            planError = e.message ?: "Could not load plans right now."
        } finally {
            plansLoading = false
        }
    }

    // Refresh entitlement when the app resumes after Google Play checkout.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && sessionToken != null) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top bar — close button, no title (matches the reference layout).
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimary)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))

            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = AccentViolet,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Go further with Premium",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(Modifier.height(18.dp))

            // Free / Premium tab switch.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                PlanTab(
                    label = "Free plan",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                PlanTab(
                    label = "Premium",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            if (sessionToken == null) {
                // Not signed in — subscribing requires an account.
                InfoBox("Please sign in to your MarketScope AI account to subscribe.")
            } else if (plansLoading) {
                Spacer(Modifier.height(30.dp))
                androidx.compose.material3.CircularProgressIndicator(color = AccentCyan)
            } else {
                planError?.let { err -> InfoBox(err) }

                if (planError == null) {
                    val activePlan = if (selectedTab == 0) freePlan else premiumPlan
                    activePlan?.let { plan ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(SurfaceLight)
                                .padding(20.dp)
                        ) {
                            // Monthly / Yearly billing switch — only Premium
                            // has two real, separately priced options.
                            val activeBilling = plan.billingOptions.firstOrNull { it.id == selectedBilling }
                                ?: plan.billingOptions.firstOrNull()
                            if (plan.id == "premium" && plan.billingOptions.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    plan.billingOptions.forEach { option ->
                                        BillingPill(
                                            option = option,
                                            selected = option.id == selectedBilling,
                                            onClick = { selectedBilling = option.id },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                            if (activeBilling != null) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "$planCurrency %.2f".format(activeBilling.price),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "/ ${activeBilling.period}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextMuted,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                if (activeBilling.id == "yearly" && activeBilling.monthlyEquivalent != null) {
                                    Text(
                                        "Billed once a year — works out to $planCurrency %.2f/mo".format(activeBilling.monthlyEquivalent),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextMuted,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            } else if (plan.price > 0) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "$planCurrency %.2f".format(plan.price),
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "/ ${plan.period ?: "month"}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextMuted,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                            } else {
                                Text(
                                    "Included with every account",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            plan.features.forEach { feature ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (plan.id == "premium") AccentViolet.copy(alpha = 0.12f)
                                                else AccentCyan.copy(alpha = 0.12f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = if (plan.id == "premium") AccentViolet else AccentCyan,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(feature, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (plan.id == "premium") {
                                Text(
                                    "Cancel anytime.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextMuted,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                        }
                    }
                }

                // Honest positioning: MarketScope AI is market research and
                // education — never financial advice, never guarantees.
                Text(
                    "MarketScope AI is a market-research and education tool. AI analysis is for informational purposes only and is not financial advice.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp)
                )

                Spacer(Modifier.height(24.dp))

                statusMessage?.let { msg ->
                    InfoBox(msg)
                    Spacer(Modifier.height(16.dp))
                }

                when {
                    effectivePremium && selectedTab == 1 -> {
                        InfoBox("You're on the $planTrailingLabel plan. Enjoy unlimited access!")
                        Spacer(Modifier.height(16.dp))
                        GradientPrimaryButton(
                            text = "Subscribed",
                            enabled = false,
                            loading = false,
                            showArrow = false,
                            height = 54.dp,
                            onClick = {}
                        )
                    }
                    selectedTab == 0 -> {
                        // Free tab: nothing to buy — a nudge toward Premium instead.
                        PremiumSecondaryButton(
                            text = "See what Premium unlocks",
                            onClick = { selectedTab = 1 },
                            height = 48.dp
                        )
                    }
                    else -> {
                        // Which billing option is currently selected, and
                        // whether Google Play actually has a matching
                        // product live for it (e.g. "premium-yearly" may not
                        // exist in Play Console yet even after monthly does).
                        val selectedProductId = premiumPlan?.billingOptions
                            ?.firstOrNull { it.id == selectedBilling }
                            ?.productId
                        val playAvailableForSelection = googlePlayEnabled &&
                            selectedProductId != null &&
                            selectedProductId in allowedGooglePlayIds

                        if (playAvailableForSelection) {
                            GradientPrimaryButton(
                                text = "Subscribe with Google Play",
                                enabled = !googleBusy,
                                loading = googleBusy,
                                height = 54.dp,
                                onClick = { startGooglePlayCheckout() }
                            )
                        } else {
                            InfoBox("Google Play billing is not available for this plan yet. No payment will be taken.")
                            GradientPrimaryButton(
                                text = "Subscribe with Google Play",
                                enabled = false,
                                loading = false,
                                height = 54.dp,
                                onClick = {}
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        val purchasePrefix = "By subscribing, you agree to our "
                        val purchaseLabel = "Purchase Terms"
                        val billingNote = buildAnnotatedString {
                            append(purchasePrefix)
                            withStyle(SpanStyle(color = AccentViolet, textDecoration = TextDecoration.Underline)) {
                                append(purchaseLabel)
                            }
                            append(". Google Play is our only payment method. Subscriptions renew automatically unless you cancel in Google Play before the next renewal. Google shows the final price before you confirm.")
                        }
                        ClickableText(
                            text = billingNote,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, end = 4.dp),
                            onClick = { offset ->
                                if (offset in purchasePrefix.length until purchasePrefix.length + purchaseLabel.length) {
                                    uriHandler.openUri("${com.veltravia.marketscopeai.data.ApiConfig.BASE_URL}/purchase-terms")
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        PremiumSecondaryButton(
                            text = "Check subscription status",
                            onClick = { refreshStatus() },
                            height = 46.dp
                        )
                    }
                }

                // Payment trouble — always one tap from support at the
                // bottom of the subscription screen, whatever plan state.
                Spacer(Modifier.height(24.dp))
                PremiumSecondaryButton(
                    text = "Facing issue with payment \u00b7 Contact support",
                    height = 42.dp,
                    onClick = {
                        val mail = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@marketscopeai.com")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "Payment issue - MarketScope AI")
                        }
                        context.startActivity(mail)
                    }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlanTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) TextPrimary else TextMuted
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) AccentViolet else BorderSubtle)
        )
    }
}

@Composable
private fun BillingPill(
    option: BillingOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentViolet.copy(alpha = 0.12f) else Color.White)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AccentViolet else BorderSubtle,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            option.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AccentViolet else TextPrimary
        )
        option.savingsLabel?.let { label ->
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (selected) AccentViolet else AccentCyan
            )
        }
    }
}

@Composable
private fun InfoBox(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLight)
            .padding(16.dp)
    ) {
        Text(message, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}
