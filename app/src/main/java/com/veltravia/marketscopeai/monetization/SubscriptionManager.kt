package com.veltravia.marketscopeai.monetization

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Central entry point for Premium checkout.
 *
 * The actual payment runs on Paystack in the browser (server-verified via
 * webhook); the backend flips the entitlement, and every device signed into
 * the account picks up ad-free Premium on its next entitlement refresh.
 */
object SubscriptionManager {

    /**
     * Start the real Paystack checkout. Opens the authorization URL in the
     * browser; the backend webhook + /api/trial/status confirm Premium.
     */
    fun startCheckout(
        context: Context,
        scope: CoroutineScope,
        onOpened: () -> Unit,
        onError: (message: String) -> Unit
    ) {
        val token = SessionManager.sessionToken(context)
        if (token == null) {
            onError("Please sign in to subscribe.")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val checkout = ApiClient.startSubscriptionCheckout(token)
                val url = checkout.optString("authorizationUrl", "")
                if (url.isBlank()) {
                    onError("Checkout is unavailable right now. Please try again shortly.")
                    return@launch
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                onOpened()
            } catch (e: Exception) {
                onError(e.message ?: "Checkout is unavailable right now.")
            }
        }
    }

    /** Re-check entitlement after returning from the checkout page. */
    fun refreshEntitlements(context: Context, scope: CoroutineScope) {
        PremiumAccessManager.refresh(context, scope)
    }
}
