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
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
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
 *
 * On top of the dials, occasional shooting stars streak diagonally across
 * the screen — a bright head with a long fading tail, fired on sparse
 * staggered schedules so a real one never feels on a timer. Same rule:
 * pure background texture, logos stay static.
 */
@Composable
fun DriftingDialsBackground(modifier: Modifier = Modifier) {
    // Sparse shooting stars — fixed seed like the dials.
    val stars = remember {
        val rnd = Random(7)
        List(3) { i ->
            ShootingStar(
                cycleMs = 9000 + rnd.nextInt(5000),
                fireAt = 0.15f + 0.25f * i + rnd.nextFloat() * 0.15f,
                streakMs = 750 + rnd.nextInt(300),
                startFraction = Offset(-0.05f, 0.10f + 0.18f * i + rnd.nextFloat() * 0.08f),
                endFraction = Offset(1.05f, 0.55f + 0.10f * i + rnd.nextFloat() * 0.06f)
            )
        }
    }

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
    val starProgresses = stars.mapIndexed { i, s ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = s.cycleMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "starCycle$i"
        ).value
    }
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
        // Shooting stars first so dials layer above them.
        stars.forEachIndexed { i, s ->
            drawShootingStar(starProgresses[i], s, w, h, density)
        }
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

private data class ShootingStar(
    val cycleMs: Int,
    val fireAt: Float,
    val streakMs: Int,
    val startFraction: Offset,
    val endFraction: Offset
)

/**
 * One star streak: [fireAt..fireAt + streakMs/cycleMs] of the cycle is the
 * visible flight; alpha eases in and out (sin) so it appears from nothing
 * and burns out, like a real one. Tail is a gradient line trailing the
 * head with a soft glow dot on the tip.
 */
private fun DrawScope.drawShootingStar(
    cycleProgress: Float,
    star: ShootingStar,
    w: Float,
    h: Float,
    density: Float
) {
    val streakFrac = star.streakMs.toFloat() / star.cycleMs
    val active = cycleProgress >= star.fireAt && cycleProgress < star.fireAt + streakFrac
    if (!active) return
    val t = (cycleProgress - star.fireAt) / streakFrac
    val start = Offset(star.startFraction.x * w, star.startFraction.y * h)
    val end = Offset(star.endFraction.x * w, star.endFraction.y * h)
    val head = lerp(start, end, t)
    // Fade in, fade out — never pops or cuts.
    val alpha = kotlin.math.sin(t * PI.toFloat()).coerceIn(0f, 1f)

    // Long tail trailing the head, fading to nothing.
    val dir = (end - start)
    val len = kotlin.math.hypot(dir.x, dir.y)
    if (len > 0f) {
        val ux = dir.x / len
        val uy = dir.y / len
        val tailLen = 0.22f * len
        val tailStart = Offset(head.x - ux * tailLen, head.y - uy * tailLen)
        val tailColor = AccentViolet
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(tailColor.copy(alpha = 0.5f * alpha), tailColor.copy(alpha = 0f)),
                start = head,
                end = tailStart
            ),
            start = head,
            end = tailStart,
            strokeWidth = 2.dp2px(density),
            cap = StrokeCap.Round
        )
    }

    // Bright head: soft glow halo + a solid core dot.
    drawCircle(color = AccentViolet, radius = 7.dp2px(density), center = head, alpha = 0.16f * alpha)
    drawCircle(color = AccentViolet, radius = 2.6f.dp2px(density), center = head, alpha = 0.85f * alpha)
}

private fun Float.dp2px(density: Float): Float = this * density

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
