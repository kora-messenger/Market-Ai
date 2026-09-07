package com.veltravia.marketscopeai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Subscribe screen — where a user whose 7-day free trial has ended (or anyone
 * who wants Premium early) subscribes to MarketScope AI Premium.
 *
 * Everything here is real: plan + price come from the backend, the Subscribe
 * button starts a genuine Paystack checkout in the browser, and premium state
 * is confirmed from the backend after payment. If payments are not live yet,
 * the server's honest status message is shown — no fake success states.
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

    // Plan info from the backend (single source of truth for price/currency).
    var plansLoading by remember { mutableStateOf(true) }
    var planError by remember { mutableStateOf<String?>(null) }
    var planName by remember { mutableStateOf("MarketScope AI Premium") }
    var planPrice by remember { mutableStateOf(9.99) }
    var planCurrency by remember { mutableStateOf("USD") }
    var planFeatures by remember { mutableStateOf(listOf<String>()) }

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
                }
            } catch (_: Exception) {
                // Status refresh is best-effort; the screen keeps its current state.
            }
        }
    }

    // Load plan info once.
    LaunchedEffect(Unit) {
        try {
            val plans = ApiClient.fetchSubscriptionPlans()
            val first = plans.optJSONArray("plans")?.optJSONObject(0) ?: JSONObject()
            planName = first.optString("name", planName)
            planPrice = first.optDouble("price", planPrice)
            planCurrency = first.optString("currency", planCurrency).uppercase()
            val feats = mutableListOf<String>()
            first.optJSONArray("features")?.let { arr ->
                for (i in 0 until arr.length()) feats.add(arr.optString(i))
            }
            planFeatures = feats
        } catch (e: Exception) {
            planError = e.message ?: "Could not load plans right now."
        } finally {
            plansLoading = false
        }
    }

    // After returning from the Paystack checkout page in the browser, check
    // whether the payment landed — this is the real confirmation path.
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
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                "Subscribe",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = GoldAmber,
                modifier = Modifier.size(52.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                planName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (isPremium) "Premium is active on your account"
                else if (trialDaysRemaining > 0) "Your free trial: $trialDaysRemaining day${if (trialDaysRemaining == 1) "" else "s"} remaining"
                else "Your 7-day free trial has ended",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            Spacer(Modifier.height(24.dp))

            if (sessionToken == null) {
                // Not signed in — subscribing requires an account.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceLight)
                        .padding(20.dp)
                ) {
                    Text(
                        "Please sign in to your MarketScope AI account to subscribe.",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (plansLoading) {
                Spacer(Modifier.height(30.dp))
                CircularProgressIndicator(color = AccentCyan)
            } else {
                planError?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLight)
                            .padding(20.dp)
                    ) {
                        Text(err, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // Plan card — price and features straight from the backend.
                if (planError == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLight)
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "$planCurrency %.2f".format(planPrice),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "/ month",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        planFeatures.forEach { feature ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 5.dp)
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(feature, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Text(
                            "Cancel anytime.",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                statusMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceLight)
                            .padding(16.dp)
                    ) {
                        Text(msg, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                }

                if (!isPremium) {
                    Button(
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
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = Color(0xFF06202A)
                        )
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = Color(0xFF06202A), modifier = Modifier.size(22.dp))
                        } else {
                            Text("Subscribe", fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { refreshStatus() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceLight,
                            contentColor = TextPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Check subscription status")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
