package com.veltravia.marketscopeai.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.SurfaceDark
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextSecondary

/**
 * The app's single premium interaction identity: one violet→cyan gradient
 * shared by every interactive control (choice pills, primary CTAs, progress),
 * plus spring physics on every touch so buttons feel physical, not flat.
 *
 * Motion language:
 *  - press     → springy squash (scale ~0.96) with a soft bounce back
 *  - select    → gradient fades in, pill pops (0.9→1 overshoot), check springs in
 *  - CTA       → a slow light band sweeps the gradient every ~3s
 *  - disabled  → gradient breathes out to a neutral surface, never a hard cut
 */

/** The signature gradient — the ONE color every control shares. */
val PremiumGradientBrush: Brush
    @Composable get() = Brush.linearGradient(
        colors = listOf(AccentViolet, AccentCyan),
        start = Offset.Zero,
        end = Offset.Infinite
    )

/** Springy press-down squash. Attach the same interactionSource to the click. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, downScale: Float = 0.96f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) downScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressScale"
    )
    return this.scale(scale)
}

/**
 * Premium choice pill: white card at rest, signature gradient when selected.
 * Animated selection (color fade + 0.9→1 pop + spring-in check mark),
 * press squash, and a light tactile tick on tap.
 */
@Composable
fun PremiumChoicePill(
    option: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    // Draw lambdas aren't composable — capture the gradient as a plain value.
    val gradient = PremiumGradientBrush

    // Colors animate — never a hard swap.
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) AccentViolet else BorderSubtle,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "pillBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else TextSecondary,
        animationSpec = tween(180),
        label = "pillText"
    )

    // Gradient fades in over the white base.
    val gradientAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(240),
        label = "pillGradient"
    )

    // Selection pop: squash slightly then overshoot back to full size.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            pop.snapTo(0.88f)
            pop.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .pressScale(interaction)
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .drawBehind { drawRect(brush = gradient, alpha = gradientAlpha) }
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .clickable(
                interactionSource = interaction,
                indication = rememberRipple(color = Color.White)
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onSelect()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            option,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1
        )
        AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            Row {
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * Premium gradient CTA: the signature gradient, a slow light sweep while
 * enabled, spring squash on press, and a breathing fade between enabled
 * and disabled states. Text color animates with the same timing.
 */
@Composable
fun GradientPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    showArrow: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val gradient = PremiumGradientBrush

    val enabledAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(280),
        label = "ctaEnabled"
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) Color.White else TextMuted,
        animationSpec = tween(280),
        label = "ctaContent"
    )

    // Slow light band sweeping across the gradient — premium "alive" feel.
    val sheenTransition = rememberInfiniteTransition(label = "ctaSheen")
    val sheenProgress by sheenTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ctaSheenX"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pressScale(interaction, downScale = 0.98f)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .drawBehind { drawRect(brush = gradient, alpha = enabledAlpha) }
            .drawWithContent {
                drawContent()
                if (enabledAlpha > 0.01f) {
                    val bandWidth = size.width * 0.55f
                    val x = sheenProgress * (size.width + bandWidth) - bandWidth
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            start = Offset(x, 0f),
                            end = Offset(x + bandWidth, size.height)
                        ),
                        alpha = enabledAlpha
                    )
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = rememberRipple(color = Color.White)
            ) {
                if (enabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            if (showArrow) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Segmented progress bar with a spring-animated gradient fill — used for the
 * questionnaire steps. `current` is 0-based; segments up to and including it fill.
 */
@Composable
fun PremiumSegmentedProgress(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val gradient = PremiumGradientBrush
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        repeat(total) { index ->
            val active = index <= current
            val fill by animateFloatAsState(
                targetValue = if (active) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "segment$index"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceDark)
                    .drawBehind { drawRect(brush = gradient, alpha = fill) }
            )
        }
    }
}

/**
 * Staggered entrance: content fades and rises in with a per-block delay,
 * replayed whenever [key] changes (page navigation).
 */
@Composable
fun StaggeredBlock(key: Any, index: Int, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 420,
                delayMillis = index * 70,
                easing = FastOutSlowInEasing
            )
        )
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 26f
        }
    ) {
        content()
    }
}
