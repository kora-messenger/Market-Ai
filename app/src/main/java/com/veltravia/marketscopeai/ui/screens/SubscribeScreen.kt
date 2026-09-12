package com.veltravia.marketscopeai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.veltravia.marketscopeai.data.ApiClient
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

private data class SubPlan(
    val id: String,
    val name: String,
    val price: Double,
    val period: String?,
    val features: List<String>
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
    val scope = rememberCoroutineScope()

    val sessionToken = remember { SessionManager.sessionToken(context) }

    // Plan info from the backend (single source of truth for price/features).
    var plansLoading by remember { mutableStateOf(true) }
    var planError by remember { mutableStateOf<String?>(null) }
    var freePlan by remember { mutableStateOf<SubPlan?>(null) }
    var premiumPlan by remember { mutableStateOf<SubPlan?>(null) }
    var planCurrency by remember { mutableStateOf("USD") }

    // Which tab is showing: 0 = Free, 1 = Premium.
    var selectedTab by remember { mutableIntStateOf(1) }

    // Live account state.
    var isPremium by remember { mutableStateOf(SessionManager.isPremium(context)) }
    var trialDaysRemaining by remember { mutableStateOf(SessionManager.trialDaysRemaining(context)) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun refreshStatus() {
        val token = sessionToken ?: return
        scope.launch {
            try {
                val status = ApiClient.fetchSubscriptionStatus(token)
                val premium = status.optBoolean("isPremium", isPremium)
                val days = status.optInt("trialDaysRemaining", trialDaysRemaining)
                val active = status.optBoolean("trialActive", premium)
                isPremium = premium
                trialDaysRemaining = days
                SessionManager.updateTrialState(context, active, days, premium)
                if (premium) {
                    statusMessage = "Premium is now active on your account. Enjoy unlimited access!"
                    selectedTab = 1
                }
            } catch (_: Exception) {
                // Status refresh is best-effort; the screen keeps its current state.
            }
        }
    }

    // Load both plans once.
    LaunchedEffect(Unit) {
        try {
            val plans = ApiClient.fetchSubscriptionPlans()
            planCurrency = plans.optString("currency", planCurrency).uppercase()
            val arr = plans.optJSONArray("plans") ?: org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val feats = mutableListOf<String>()
                p.optJSONArray("features")?.let { fa ->
                    for (j in 0 until fa.length()) feats.add(fa.optString(j))
                }
                val parsed = SubPlan(
                    id = p.optString("id"),
                    name = p.optString("name"),
                    price = p.optDouble("price", 0.0),
                    period = p.optString("period").takeIf { it.isNotBlank() && it != "null" },
                    features = feats
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

    // After returning from the Paystack checkout page in the browser, check
    // whether the payment landed — this is the real confirmation path.
    // (The webhook already emails + pushes the outcome; this is just the
    // in-screen state refresh for whoever is still looking at the app.)
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
                "Take off with Premium",
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
                            if (plan.price > 0) {
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
                    isPremium && selectedTab == 1 -> {
                        InfoBox("Premium is already active on your account. Enjoy unlimited access!")
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
                        GradientPrimaryButton(
                            text = "Subscribe & pay",
                            enabled = !busy,
                            loading = busy,
                            height = 54.dp,
                            onClick = {
                                busy = true
                                statusMessage = null
                                scope.launch {
                                    try {
                                        val checkout = ApiClient.startSubscriptionCheckout(sessionToken)
                                        val url = checkout.optString("authorizationUrl", "")
                                        busy = false
                                        if (url.isNotBlank()) {
                                            // Open the real Paystack checkout page in the browser.
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            )
                                        } else {
                                            statusMessage = "Checkout could not start. Please try again."
                                        }
                                    } catch (e: Exception) {
                                        busy = false
                                        // The server's honest message (e.g. payments not live yet).
                                        statusMessage = e.message ?: "Checkout could not start. Please try again."
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.height(10.dp))

                        Text(
                            "By subscribing, you agree to our Purchaser Terms, and that subscriptions auto-renew until you cancel. Cancel anytime, at least 24 hours before renewal to avoid additional charges. You'll get an email and an in-app notification the moment your payment succeeds or fails.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, end = 4.dp)
                        )

                        Spacer(Modifier.height(12.dp))

                        PremiumSecondaryButton(
                            text = "Check subscription status",
                            onClick = { refreshStatus() },
                            height = 46.dp
                        )
                    }
                }
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
