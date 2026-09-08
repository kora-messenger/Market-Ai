package com.veltravia.marketscopeai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.R

/**
 * A user's avatar: their Google account photo when we have one, otherwise the
 * shared MarketScope AI branded default illustration (the same one for every
 * account, like the reference app). Never renders initials or a gray
 * silhouette — there is always a real image.
 */
@Composable
fun UserAvatar(photoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    if (!photoUrl.isNullOrBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape)
        )
    } else {
        Image(
            painter = painterResource(R.drawable.ic_default_avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape)
        )
    }
}
