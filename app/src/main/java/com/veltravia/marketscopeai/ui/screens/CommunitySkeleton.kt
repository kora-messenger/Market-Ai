package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shimmer skeleton loading states for the Community screen, mirroring the
 * real layouts so nothing jumps when the feed/comments land:
 *  - CommunityFeedSkeleton: repeated post-card placeholders shaped like
 *    PostCard (avatar + author/time, body lines, optional image block,
 *    react/comment footer)
 *  - CommentsSkeleton: comment-row placeholders shaped like CommentRow
 *    (avatar circle + author bar + two body bars)
 * Only shown for the very first load — refreshing with posts on screen
 * keeps the real content and shows a small spinner instead.
 */

/** One Community post-card placeholder. [withImage] adds the 220dp image block. */
@Composable
private fun CommunityPostSkeleton(withImage: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            // Author row: avatar + name/time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(rememberShimmerBrush())
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBar(width = 130.dp, height = 13.dp)
                    Spacer(Modifier.height(5.dp))
                    SkeletonBar(width = 60.dp, height = 9.dp)
                }
            }
            Spacer(Modifier.height(12.dp))
            // Body: full line + partial line(s)
            SkeletonBar(height = 12.dp)
            Spacer(Modifier.height(7.dp))
            SkeletonBar(width = 240.dp, height = 12.dp)
            if (withImage) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(rememberShimmerBrush())
                )
            } else {
                Spacer(Modifier.height(7.dp))
                SkeletonBar(width = 170.dp, height = 12.dp)
            }
            // Footer: react + comment pills
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBar(width = 48.dp, height = 12.dp, corner = 8.dp)
                Spacer(Modifier.width(14.dp))
                SkeletonBar(width = 90.dp, height = 12.dp, corner = 8.dp)
            }
        }
    }
}

/** Full community feed skeleton — same padding/spacing as the real LazyColumn. */
@Composable
fun CommunityFeedSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CommunityPostSkeleton(withImage = false)
        CommunityPostSkeleton(withImage = true)
        CommunityPostSkeleton(withImage = false)
        CommunityPostSkeleton(withImage = false)
    }
}

/** One comment-row placeholder (30dp avatar + author + two body bars). */
@Composable
private fun CommentRowSkeleton() {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 5.dp)) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(rememberShimmerBrush())
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBar(width = 110.dp, height = 11.dp)
            Spacer(Modifier.height(6.dp))
            SkeletonBar(height = 11.dp)
            Spacer(Modifier.height(5.dp))
            SkeletonBar(width = 190.dp, height = 11.dp)
        }
    }
}

/** Comments bottom-sheet skeleton while the first comment page loads. */
@Composable
fun CommentsSkeleton() {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        repeat(4) { CommentRowSkeleton() }
    }
}
