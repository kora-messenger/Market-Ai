package com.veltravia.marketscopeai.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.R
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextSecondary

/**
 * Force-update block screen — shown BEFORE anything else (even the welcome
 * screen) when the backend's server-driven app-version gate says this
 * install is below the required minimum. There is no dismiss/back action:
 * the only way past this screen is to actually update.
 *
 * The store button is platform-aware: the server decides from the client's
 * platform whether the update lives on the Play Store ("Open Play Store",
 * opened via the market:// app with a web fallback) or on the App Store
 * ("Open App Store", opened via the itms-apps:// scheme with a web
 * fallback). The icon floats gently in place, matching the same idle
 * motion as the welcome screen's logo.
 */
@Composable
fun ForceUpdateScreen(
    latestVersionName: String,
    updateMessage: String,
    storeUrl: String,
    storeLabel: String
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            val floatTransition = rememberInfiniteTransition(label = "updateLogoFloat")
            val floatOffset by floatTransition.animateFloat(
                initialValue = -8f,
                targetValue = 8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "updateLogoFloatOffset"
            )
            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = "MarketScope AI",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .graphicsLayer { translationY = floatOffset }
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = "Please update to the\nlatest version",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = latestVersionName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = updateMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(24.dp))

            GradientPrimaryButton(
                text = storeLabel,
                enabled = true,
                showArrow = false,
                height = 56.dp,
                shape = RoundedCornerShape(50),
                onClick = { openStorePage(context, storeUrl, storeLabel) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Opens the store listing the server picked for this platform. Play Store
 * links try the market:// app first (lands directly on the app's update
 * page), App Store links try itms-apps://, and both fall back to the
 * plain https listing when the native app/scheme is unavailable.
 */
private fun openStorePage(context: android.content.Context, storeUrl: String, storeLabel: String) {
    val isPlayStore = !storeLabel.equals("Open App Store", ignoreCase = true)
    val schemeUri = if (isPlayStore) {
        "market://details?id=com.veltravia.marketscopeai"
    } else {
        // itms-apps:// lands on the app's App Store page; https works everywhere.
        storeUrl.replace("https://", "itms-apps://")
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(schemeUri)))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl)))
    }
}
