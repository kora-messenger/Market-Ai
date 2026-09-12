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
 * Layout follows the reference the owner sent (large app icon, bold two-line
 * headline, version caption, description, full-width CTA pinned near the
 * bottom) reworded/rebuilt in MarketScope AI's own violet-to-cyan brand
 * instead of the reference app's green. The icon floats gently in place,
 * matching the same idle motion added to the welcome screen's logo.
 */
@Composable
fun ForceUpdateScreen(
    latestVersionName: String,
    updateMessage: String,
    playStoreUrl: String
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
                text = "Open Play Store",
                enabled = true,
                showArrow = false,
                height = 56.dp,
                shape = RoundedCornerShape(50),
                onClick = {
                    val marketIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.veltravia.marketscopeai")
                    )
                    try {
                        context.startActivity(marketIntent)
                    } catch (e: ActivityNotFoundException) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(playStoreUrl))
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
