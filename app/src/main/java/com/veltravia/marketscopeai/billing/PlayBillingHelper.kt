package com.veltravia.marketscopeai.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.queryProductDetails

/**
 * Thin wrapper around Google Play Billing for the Premium subscription.
 *
 * The flow: connect → query the subscription product (e.g. "premium-monthly")
 * → launch the Play billing sheet → on purchase, hand the purchase token to
 * OUR backend (/api/subscription/google-play/verify), which verifies it
 * against Google's Play Developer API and activates Premium server-side —
 * then acknowledge the purchase with Google so it isn't refunded.
 *
 * The client is NEVER the authority on Premium: the server verifies every
 * token with Google, exactly like the Paystack webhook verifies every charge.
 */
class PlayBillingHelper(
    context: Context,
    private val onPurchase: (productId: String, purchaseToken: String) -> Unit,
    private val onError: (userMessage: String) -> Unit
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        // Required so purchases stuck in PENDING (e.g. cash at a store)
        // don't kill the connection.
        .enablePendingPurchases()
        .build()

    /** True once the billing connection is ready to query/launch. */
    var connected = false
        private set

    /** Connect (idempotent). Calls back with true when ready to use. */
    fun connect(onReady: (ready: Boolean) -> Unit) {
        if (connected) {
            onReady(true)
            return
        }
        client.startConnection(object : com.android.billingclient.api.BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (!connected) {
                    // Sideloaded APKs / devices without Play services land
                    // here — the honest message is handled by the screen.
                    onError("Google Play billing is not available on this device or installation.")
                }
                onReady(connected)
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    /**
     * Query the subscription product by id. Returns null if the product
     * isn't set up (or isn't visible to this device — e.g. the app isn't
     * installed from the Play Store yet).
     */
    suspend fun querySubscription(productId: String): ProductDetails? {
        if (!connected) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        val result: ProductDetailsResult = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            return null
        }
        return result.productDetailsList?.firstOrNull { it.productId == productId }
    }

    /** Open the Play billing sheet for this product. */
    fun launchPurchase(activity: Activity, productDetails: ProductDetails) {
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK &&
            result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED
        ) {
            onError("Google Play checkout could not open right now. Please try again.")
        }
    }

    /** Acknowledge after OUR backend verified the token — required within
     * 3 days or Google auto-refunds the purchase. */
    fun acknowledge(purchaseToken: String) {
        if (!client.isReady) return
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build()
        ) { /* result ignored: a failed ack is retried on the next purchase query */ }
    }

    fun close() {
        if (client.isReady) client.endConnection()
    }

    // --- PurchasesUpdatedListener: the Play sheet finished ---
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val purchase = purchases?.firstOrNull() ?: return
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    val productId = purchase.products.firstOrNull() ?: return
                    onPurchase(productId, purchase.purchaseToken)
                } else {
                    // PENDING: Google is still confirming the payment method.
                    onError("Your payment is still processing with Google. Premium will activate automatically once it completes.")
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // Silent — the user closed the sheet themselves.
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                onError("You already have an active Premium subscription on Google Play. Try restoring from the Subscribe screen.")
            }
            else -> {
                onError("Google Play checkout did not complete. Please try again.")
            }
        }
    }

}
