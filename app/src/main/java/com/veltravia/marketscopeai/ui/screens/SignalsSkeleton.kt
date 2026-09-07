package com.veltravia.marketscopeai.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shimmer skeleton loading state for the Daily Signals screen — shown while
 * the "at a glance" stats and the live feed are both still loading, so the
 * user sees the real layout shape immediately instead of a bare spinner.
 * Mirrors the reference: one big glance-card placeholder, then repeated
 * signal-card placeholders matching DailySignalCard's real layout
 * (title + direction badge, 3 mini-stat columns, two body lines, a status
 * pill + a trailing line) so nothing visually "jumps" once real data lands.
 */

/** Soft, slow left-to-right shimmer sweep — light-theme friendly (no dark flash). */
@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "signals_shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "signals_shimmer_translate"
    )
    val base = Color(0xFFEDF1F7)
    val sheen = Color(0xFFF8FAFC)
    return Brush.linearGradient(
        colors = listOf(base, sheen, base),
        start = Offset(translate - 300f, 0f),
        end = Offset(translate, 300f)
    )
}

@Composable
private fun SkeletonBar(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 12.dp,
    corner: Dp = 6.dp
) {
    val brush = rememberShimmerBrush()
    val sized = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Column(
        modifier = sized
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(brush)
    ) {}
}

/** Placeholder for one DailySignalCard: header row, 3 mini stats, 2 body lines, footer row. */
@Composable
private fun SignalCardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, Color(0xFFE7ECF3), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SkeletonBar(width = 108.dp, height = 16.dp)
            SkeletonBar(width = 60.dp, height = 22.dp, corner = 8.dp)
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(3) { index ->
                Column(modifier = Modifier.padding(end = if (index < 2) 14.dp else 0.dp)) {
                    SkeletonBar(width = 34.dp, height = 8.dp)
                    Spacer(Modifier.height(6.dp))
                    SkeletonBar(width = 50.dp, height = 12.dp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SkeletonBar(height = 12.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(width = 190.dp, height = 12.dp)
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SkeletonBar(width = 94.dp, height = 22.dp, corner = 20.dp)
            SkeletonBar(width = 128.dp, height = 10.dp)
        }
    }
}

/** Placeholder for the "at a glance" gradient stats card. */
@Composable
private fun GlanceCardSkeleton() {
    val brush = rememberShimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
    ) {}
}

/** Full skeleton body shown below the header while the first load is in flight. */
@Composable
fun SignalsFeedSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        SkeletonBar(width = 96.dp, height = 10.dp)
        Spacer(Modifier.height(12.dp))
        GlanceCardSkeleton()
        Spacer(Modifier.height(20.dp))
        SkeletonBar(width = 104.dp, height = 14.dp)
        Spacer(Modifier.height(12.dp))
        repeat(3) {
            SignalCardSkeleton()
            Spacer(Modifier.height(14.dp))
        }
    }
}
