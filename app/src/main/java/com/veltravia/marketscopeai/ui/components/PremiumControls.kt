package com.veltravia.marketscopeai.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp

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
 * Premium segmented tab row (3-in-1 analyze screen): one shared motion
 * identity with the tab bar — gradient pill fades in behind the selected
 * tab, selection pops with spring physics, every tap gets a light tick.
 */
@Composable
fun PremiumSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val gradient = PremiumGradientBrush
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(5.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val view = LocalView.current

            val gradientAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(240),
                label = "segGradient"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) Color.White else TextSecondary,
                animationSpec = tween(180),
                label = "segText"
            )
            val pop = remember { Animatable(1f) }
            LaunchedEffect(selected) {
                if (selected) {
                    pop.snapTo(0.9f)
                    pop.animateTo(
                        1f,
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction, downScale = 0.96f)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .drawBehind {
                        if (gradientAlpha > 0f) {
                            drawRect(brush = gradient, alpha = gradientAlpha)
                        }
                    }
                    .clickable(
                        interactionSource = interaction,
                        indication = rememberRipple(),
                        enabled = !selected
                    ) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onSelect(index)
                    }
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
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
    showArrow: Boolean = true,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    loading: Boolean = false,
    leadingIcon: ImageVector? = null
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
            .clip(shape)
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
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
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
    // Column, not Box: every call site passes multiple children (a heading +
    // subtitle, or a question label + its pill row) meant to stack vertically.
    // Box stacks children on top of each other instead — that was rendering
    // the questionnaire's labels and pills overlapping one another.
    Column(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 26f
        }
    ) {
        content()
    }
}

/** One tab of the premium bottom bar: filled icon when active, outlined at rest. */
data class PremiumTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/**
 * Premium bottom tab bar: white surface, thin top border, gradient pill that
 * fades in behind the active icon with a spring pop, animated label colors,
 * and a light haptic tick on every switch. Replaces the stock M3
 * NavigationBar on the main screen.
 */
@Composable
fun PremiumTabBar(
    tabs: List<PremiumTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val gradient = PremiumGradientBrush
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(BorderSubtle))
        Row(Modifier.fillMaxWidth().height(64.dp)) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = selected == index
                val gradientAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = tween(220),
                    label = "tabPill$index"
                )
                val iconTint by animateColorAsState(
                    targetValue = if (isSelected) Color.White else TextSecondary,
                    animationSpec = tween(200),
                    label = "tabIcon$index"
                )
                val labelTint by animateColorAsState(
                    targetValue = if (isSelected) AccentViolet else TextMuted,
                    animationSpec = tween(200),
                    label = "tabLabel$index"
                )
                // Pop the icon whenever this tab becomes active.
                val pop = remember { Animatable(1f) }
                LaunchedEffect(isSelected) {
                    if (isSelected) {
                        pop.snapTo(0.8f)
                        pop.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelect(index)
                        },
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = pop.value
                                scaleY = pop.value
                            }
                            .clip(RoundedCornerShape(50))
                            .drawBehind { drawRect(brush = gradient, alpha = gradientAlpha) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = labelTint
                    )
                }
            }
        }
    }
}

/**
 * Premium secondary button: solid surface fill with a hairline border,
 * spring squash on press, haptic tick, animated content color. Pass a red
 * container/content pair for destructive actions — same motion, honest
 * color semantics.
 */
@Composable
fun PremiumSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.White,
    contentColor: Color = TextPrimary,
    borderColor: Color = BorderSubtle,
    height: Dp = 48.dp,
    showArrow: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    loading: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val tint by animateColorAsState(
        targetValue = if (enabled) contentColor else TextMuted,
        animationSpec = tween(200),
        label = "secondaryContent"
    )
    val borderTint by animateColorAsState(
        targetValue = if (enabled) borderColor else BorderSubtle,
        animationSpec = tween(200),
        label = "secondaryBorder"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pressScale(interaction, downScale = 0.97f)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderTint, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = rememberRipple()
            ) {
                if (enabled) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onClick()
                }
            }
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = tint
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tint
                )
                if (showArrow) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}


/**
 * Small uppercase "chapter" label above a questionnaire page's headline —
 * e.g. "CHAPTER 01 · THE TRADER — IJEZIE". Purely cosmetic wayfinding,
 * gives each page a sense of place beyond the segmented progress bar.
 */
@Composable
fun PremiumChapterLabel(chapterNumber: Int, chapterTitle: String, name: String) {
    Text(
        "CHAPTER ${chapterNumber.toString().padStart(2, '0')} · $chapterTitle — ${name.uppercase()}",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = AccentViolet
    )
}

/**
 * Two-line headline: a neutral first line and an accent-colored second line
 * — the same violet-to-cyan brand pop used everywhere else, applied to
 * page headlines instead of just buttons/pills.
 */
@Composable
fun PremiumTwoToneHeadline(line1: String, line2: String) {
    Text(
        line1,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
    )
    Text(
        line2,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = AccentCyan
    )
}

/**
 * Full-width selectable option card: icon avatar, title + subtitle, and a
 * radio indicator that fills with the signature gradient when selected.
 * Used for single-choice questions where each option deserves a short
 * description (e.g. experience level) — richer than a plain choice pill.
 */
@Composable
fun PremiumOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val gradient = PremiumGradientBrush

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) AccentViolet else BorderSubtle,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "cardBorder"
    )
    val washAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(240),
        label = "cardWash"
    )

    val pop = remember { Animatable(1f) }
    LaunchedEffect(isSelected) {
        if (isSelected) {
            pop.snapTo(0.97f)
            pop.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
            }
            .pressScale(interaction, downScale = 0.985f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .drawBehind {
                drawRect(brush = gradient, alpha = washAlpha * 0.07f)
            }
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = rememberRipple(color = AccentViolet)
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onSelect()
            }
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentViolet.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        Spacer(Modifier.width(8.dp))
        PremiumRadioDot(isSelected = isSelected, borderColor = borderColor)
    }
}

/**
 * The radio indicator drawn inside [PremiumOptionCard]. Extracted into its
 * own plain @Composable on purpose: called directly inside the card's Row
 * scope, AnimatedVisibility resolves to the RowScope extension and the
 * compiler rejects the implicit receiver — this wrapper has no such scope.
 */
@Composable
private fun PremiumRadioDot(isSelected: Boolean, borderColor: Color) {
    val gradient = PremiumGradientBrush
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = isSelected,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        ) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .drawBehind { drawRect(brush = gradient) }
            )
        }
    }
}
