package com.veltravia.marketai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketai.data.ApiClient
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.BearRed
import com.veltravia.marketai.ui.theme.BullGreen
import com.veltravia.marketai.ui.theme.SurfaceDark
import com.veltravia.marketai.ui.theme.TextMuted
import com.veltravia.marketai.json.JSONArray
import com.veltravia.marketai.json.JSONObject

/**
 * Saved signals — real analysis history from the backend.
 */
@Composable
fun SavedScreen(
    onOpenAnalysis: (String) -> Unit
) {
    var analyses by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        try {
            analyses = ApiClient.fetchAnalyses(50)
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Could not load history"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Saved Signals",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Every analysis you run is stored on your account history",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )

        Spacer(Modifier.height(16.dp))

        when {
            error != null -> {
                EmptyState(icon = { Icon(Icons.Filled.BookmarkBorder, null, tint = TextMuted, modifier = Modifier.size(48.dp)) },
                    title = "Couldn't reach history", subtitle = error ?: "")
            }
            analyses == null -> {
                Spacer(Modifier.height(60.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan)
                }
            }
            analyses!!.length() == 0 -> {
                EmptyState(icon = { Icon(Icons.Filled.BookmarkBorder, null, tint = TextMuted, modifier = Modifier.size(48.dp)) },
                    title = "No saved signals yet",
                    subtitle = "Run an analysis and it will be stored here automatically")
            }
            else -> {
                LazyColumn {
                    items((0 until analyses!!.length()).toList()) { i ->
                        val item = analyses!!.optJSONObject(i) ?: return@items
                        SavedRow(item, onOpenAnalysis)
                        if (i < analyses!!.length() - 1) Spacer(Modifier.height(10.dp))
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SavedRow(item: JSONObject, onOpen: (String) -> Unit) {
    val id = item.optString("id", "")
    val signal = item.optJSONObject("analysis")
    val inner = signal?.optJSONObject("analysis")
    val instrument = signal?.optString("instrument")
        ?: item.optString("instrumentId", "").uppercase()
    val mode = signal?.optString("mode") ?: item.optString("mode", "")
    val direction = inner?.optString("direction", "")?.uppercase() ?: ""
    val confidence = inner?.optInt("confidence", 0) ?: 0
    val isLong = direction == "LONG"
    val dirColor = when {
        isLong -> BullGreen
        direction == "SHORT" -> BearRed
        else -> TextMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .clickable(enabled = id.isNotEmpty()) { onOpen(id) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(dirColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                direction.take(1),
                color = dirColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(instrument, fontWeight = FontWeight.SemiBold)
            Text(
                "${mode.replaceFirstChar { it.uppercase() }} • ${confidence}% confidence",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Text(
            direction,
            color = dirColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptyState(icon: @Composable () -> Unit, title: String, subtitle: String) {
    Spacer(Modifier.height(60.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
