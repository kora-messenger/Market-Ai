package com.veltravia.marketscopeai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Faint decorative "dial" glyphs (concentric ring + three radiating ticks)
 * drifting slowly across the screen in endless diagonal loops, the ambient
 * background texture style used on the welcome/update screens. This is
 * purely ambient chrome BEHIND the real content — and the logo itself is
 * NEVER animated (standing rule: app logos stay completely static; only
 * this background texture may move).
 *
 * Each glyph runs its own independent drift so the motion never looks
 * synchronized. Very low alpha keeps it ambient, not distracting, on the
 * white canvas.
 */
@Composable
fun DriftingDialsBackground(modifier: Modifier = Modifier) {
    // Fixed seed -> same glyph layout every launch (deliberate, not noise).
    val glyphs = remember {
        val rnd = Random(42)
        List(6) {
            DialGlyph(
                startFraction = Offset(rnd.nextFloat(), rnd.nextFloat()),
                radiusDp = 14f + rnd.nextFloat() * 16f,
                periodMs = 16000 + rnd.nextInt(9000),
                driftDp = 90f + rnd.nextFloat() * 60f,
                angleDeg = rnd.nextFloat() * 360f,
                color = if (it % 2 == 0) AccentCyan else AccentViolet,
                alpha = 0.05f + rnd.nextFloat() * 0.04f
            )
        }
    }

    // Animated progress values must be read in composable scope (Canvas's
    // draw lambda is not one), one independent loop per glyph.
    val transition = rememberInfiniteTransition(label = "dialDrift")
    val progresses = glyphs.mapIndexed { i, g ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = g.periodMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "dialDrift$i"
        ).value
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        glyphs.forEachIndexed { i, g ->
            val progress = progresses[i]
            // Diagonal drift down-and-right, wrapping smoothly once it
            // exits — an endless slow loop, never a visible jump (alpha is
            // low enough that the wrap read as a gentle fade of texture).
            val margin = 160f * density
            val spanW = w + margin
            val spanH = h + margin
            val dx = (g.driftDp * density) * progress
            val dy = (g.driftDp * 0.6f * density) * progress
            val cx = ((g.startFraction.x * spanW + dx) % spanW + spanW) % spanW - margin / 2f
            val cy = ((g.startFraction.y * spanH + dy) % spanH + spanH) % spanH - margin / 2f
            drawDial(
                cx = cx, cy = cy,
                radiusPx = g.radiusDp * density,
                angleDeg = g.angleDeg + progress * 40f,
                color = g.color,
                alpha = g.alpha
            )
        }
    }
}

private data class DialGlyph(
    val startFraction: Offset,
    val radiusDp: Float,
    val periodMs: Int,
    val driftDp: Float,
    val angleDeg: Float,
    val color: Color,
    val alpha: Float
)

private fun DrawScope.drawDial(
    cx: Float,
    cy: Float,
    radiusPx: Float,
    angleDeg: Float,
    color: Color,
    alpha: Float
) {
    rotate(degrees = angleDeg, pivot = Offset(cx, cy)) {
        // Concentric ring, like a compass/target face.
        drawCircle(
            color = color,
            radius = radiusPx,
            center = Offset(cx, cy),
            alpha = alpha,
            style = Stroke(width = radiusPx * 0.12f)
        )
        // Three short radiating ticks, evenly spaced — the reference
        // icon's "dial with marks" look.
        for (i in 0 until 3) {
            val a = (i * 120f) * PI.toFloat() / 180f
            val innerR = radiusPx * 0.55f
            val outerR = radiusPx * 1.35f
            val start = Offset(cx + innerR * cos(a), cy + innerR * sin(a))
            val end = Offset(cx + outerR * cos(a), cy + outerR * sin(a))
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = radiusPx * 0.14f,
                alpha = alpha,
                cap = StrokeCap.Round
            )
        }
    }
}
