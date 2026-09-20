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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.veltravia.marketscopeai.ui.theme.BorderSubtle

/**
 * Shimmer skeleton loading state for News Outlook — shown the instant the
 * screen opens (header, timezone picker and Filter button render for real
 * immediately; only the event list is still loading), so the screen never
 * shows a bare spinner. Mirrors OutlookEventCard's real shape: a header row
 * (title bar + trailing currency-pill bar), two body lines, an impact/data
 * chip row, then a row of short mini-stat bars.
 */

/** One event-card placeholder, matching OutlookEventCard's border + corners. */
@Composable
private fun OutlookEventCardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            SkeletonBar(width = 64.dp, height = 13.dp)
            SkeletonBar(width = 104.dp, height = 26.dp, corner = 14.dp)
        }
        Spacer(Modifier.height(12.dp))
        SkeletonBar(height = 13.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(width = 220.dp, height = 13.dp)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBar(width = 108.dp, height = 26.dp, corner = 14.dp)
            SkeletonBar(width = 108.dp, height = 26.dp, corner = 14.dp)
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SkeletonBar(width = 68.dp, height = 10.dp)
            SkeletonBar(width = 68.dp, height = 10.dp)
            SkeletonBar(width = 68.dp, height = 10.dp)
        }
    }
}

/** The event list while `events == null` — same spacing as the real top-3 list. */
@Composable
fun NewsOutlookSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlookEventCardSkeleton()
        Spacer(Modifier.height(12.dp))
        OutlookEventCardSkeleton()
        Spacer(Modifier.height(12.dp))
        OutlookEventCardSkeleton()
    }
}
