package com.veltravia.marketscopeai.monetization

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * A feed-integrated native advertisement, styled with the app's own light
 * design language and ALWAYS clearly labeled "ADVERTISEMENT".
 *
 * Renders a real AdMob NativeAd via the official NativeAdView asset
 * registration (required by AdMob policy). No ad available → nothing is
 * rendered at all (zero height, never a fake placeholder).
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!AdManager.adsAllowed(context)) return

    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var adView by remember { mutableStateOf<NativeAdView?>(null) }

    DisposableEffect(Unit) {
        AdManager.loadOneNative(context) { ad ->
            nativeAd = ad as? NativeAd
        }
        onDispose {
            nativeAd?.destroy()
        }
    }

    val ad = nativeAd ?: return

    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            factory = { ctx ->
                buildNativeAdView(ctx).also { view ->
                    adView = view
                    bindNativeAd(view, ad)
                }
            },
            update = { view ->
                bindNativeAd(view, ad)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun dp(view: View, value: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, view.resources.displayMetrics).toInt()

/** Programmatic NativeAdView in the app's light theme (#F3F4F7 card, violet CTA). */
private fun buildNativeAdView(context: android.content.Context): NativeAdView {
    val adView = NativeAdView(context)
    val card = GradientDrawable().apply {
        setColor(0xFFF3F4F7.toInt())
        cornerRadius = dp(adView, 14f).toFloat()
    }
    adView.background = card
    val pad = dp(adView, 12f)
    adView.setPadding(pad, dp(adView, 10f), pad, pad)

    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    // --- Header row: label + AdChoices ---
    val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    val label = TextView(context).apply {
        text = "ADVERTISEMENT"
        setTextColor(0xFF6B7280.toInt())
        textSize = 10f
        letterSpacing = 0.12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val adOptions = com.google.android.gms.ads.nativead.AdOptionsView(context)
    header.addView(label)
    header.addView(adOptions)
    root.addView(header)

    // --- Media ---
    val media = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(adView, 130f)
        ).apply { topMargin = dp(adView, 8f) }
    }
    root.addView(media)

    // --- Icon + headline ---
    val titleRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(adView, 10f) }
    }
    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(adView, 34f), dp(adView, 34f))
    }
    val headline = TextView(context).apply {
        setTextColor(0xFF111318.toInt())
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 2
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            leftMargin = dp(adView, 8f)
        }
    }
    titleRow.addView(icon)
    titleRow.addView(headline)
    root.addView(titleRow)

    // --- Body ---
    val body = TextView(context).apply {
        setTextColor(0xFF5B616E.toInt())
        textSize = 12f
        maxLines = 3
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(adView, 4f) }
    }
    root.addView(body)

    // --- CTA pill (app violet) ---
    val cta = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 13f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(0xFF7C3AED.toInt())
            cornerRadius = dp(adView, 22f).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(adView, 42f)
        ).apply { topMargin = dp(adView, 10f) }
    }
    root.addView(cta)

    // --- Register asset views (AdMob policy requires this mapping) ---
    adView.adOptionsView = adOptions
    adView.mediaView = media
    adView.iconView = icon
    adView.headlineView = headline
    adView.bodyView = body
    adView.callToActionView = cta
    adView.addView(root)
    return adView
}

private fun bindNativeAd(adView: NativeAdView, ad: NativeAd) {
    (adView.headlineView as? TextView)?.text = ad.headline ?: ""
    (adView.bodyView as? TextView)?.text = ad.body ?: ""
    (adView.callToActionView as? TextView)?.text =
        if (ad.callToAction.isNullOrEmpty()) "Learn more" else ad.callToAction
    ad.icon?.drawable?.let { (adView.iconView as? ImageView)?.setImageDrawable(it) }
        ?: run { adView.iconView?.visibility = View.GONE }
    adView.mediaView?.visibility = if (ad.mediaContent == null) View.GONE else View.VISIBLE
    adView.setNativeAd(ad)
}

/**
 * Adaptive banner slot — rendered ONLY where the config allows and never for
 * Premium users. Off by default server-side (see monetization_config).
 */
@Composable
fun BannerAdSlot(surface: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cfg = MonetizationSettings.current
    if (!cfg.bannerEnabled || !cfg.adsEnabled || !AdManager.adsAllowed(context)) return

    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            factory = { ctx ->
                val widthDp = (ctx.resources.displayMetrics.widthPixels * 160 /
                    ctx.resources.displayMetrics.densityDpi).toInt()
                AdView(ctx).apply {
                    setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp - 24))
                    adUnitId = com.veltravia.marketscopeai.BuildConfig.ADMOB_UNIT_BANNER
                    loadAd(AdRequest.Builder().build())
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        )
    }
}
