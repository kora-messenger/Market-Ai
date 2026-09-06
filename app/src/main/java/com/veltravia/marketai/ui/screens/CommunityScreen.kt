package com.veltravia.marketai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.BullGreen
import com.veltravia.marketai.ui.theme.TextMuted

@Composable
fun CommunityScreen() {
    val context = LocalContext.current
    val joined = SessionManager.communityJoined(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            if (joined) Icons.Filled.Groups else Icons.Filled.Forum,
            contentDescription = null,
            tint = if (joined) BullGreen else TextMuted,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (joined) "You're in the community" else "Beginning of the community",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (joined) "Be the first to post your win." else "Finish setup to unlock free community access.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}
