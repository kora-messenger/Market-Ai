package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.SurfaceLight

/**
 * Shimmer skeleton loading states for the Saved screen — mirrors the real
 * layouts (TradePlanRow, SavedSignalCard) the same way CommunitySkeleton
 * mirrors PostCard, so nothing jumps once trade plans / saved signals land.
 * Shown only on the true first load (see SavedScreen: plans/analyses null,
 * no error yet); a refresh with content already on screen never shows this.
 */

/** One Trade Plan row placeholder — mirrors TradePlanRow's icon + text layout. */
@Composable
private fun TradePlanRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLight)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(rememberShimmerBrush())
        ) {}
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBar(width = 96.dp, height = 14.dp)
            Spacer(Modifier.height(7.dp))
            SkeletonBar(width = 180.dp, height = 11.dp)
        }
    }
}

/** Trade Plans section while `plans == null` — real header bar + 2 rows. */
@Composable
fun TradePlansSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkeletonBar(width = 110.dp, height = 18.dp)
            Column(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(rememberShimmerBrush())
            ) {}
        }
        Spacer(Modifier.height(12.dp))
        TradePlanRowSkeleton()
        Spacer(Modifier.height(10.dp))
        TradePlanRowSkeleton()
        Spacer(Modifier.height(20.dp))
    }
}

/** One saved-signal card placeholder — mirrors SavedSignalCard's full layout. */
@Composable
private fun SavedSignalCardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLight)
            .border(1.dp, AccentCyan.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SkeletonBar(width = 78.dp, height = 10.dp)
            SkeletonBar(width = 54.dp, height = 10.dp)
        }
        Spacer(Modifier.height(10.dp))
        SkeletonBar(width = 150.dp, height = 17.dp)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            repeat(4) {
                Column {
                    SkeletonBar(width = 28.dp, height = 9.dp)
                    Spacer(Modifier.height(4.dp))
                    SkeletonBar(width = 42.dp, height = 14.dp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        SkeletonBar(height = 11.dp)
        Spacer(Modifier.height(6.dp))
        SkeletonBar(width = 210.dp, height = 11.dp)
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SkeletonBar(width = 88.dp, height = 11.dp)
        }
    }
}

/** Saved-signals list while `filteredAnalyses == null` — same spacing as the real list. */
@Composable
fun SavedSignalsSkeleton() {
    Column {
        SavedSignalCardSkeleton()
        Spacer(Modifier.height(14.dp))
        SavedSignalCardSkeleton()
        Spacer(Modifier.height(14.dp))
        SavedSignalCardSkeleton()
    }
}
