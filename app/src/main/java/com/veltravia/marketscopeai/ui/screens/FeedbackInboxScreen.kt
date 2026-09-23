package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.ImageViewerDialog
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Owner-only feedback inbox. The backend also enforces owner access and
 * signs each private photo for 15 minutes, so Refresh renews old links. */
@Composable
fun FeedbackInboxScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var reload by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewerUrls by remember { mutableStateOf<List<String>?>(null) }
    var viewerIndex by remember { mutableStateOf(0) }
    viewerUrls?.let { urls ->
        ImageViewerDialog(urls = urls, initialIndex = viewerIndex, onDismiss = { viewerUrls = null })
    }
    LaunchedEffect(reload) {
        val token = SessionManager.sessionToken(context)
        if (token == null) {
            error = "Please sign in to view feedback."
            items = JSONArray()
        } else {
            error = null
            try {
                items = ApiClient.fetchAdminFeedback(token).optJSONArray("feedback") ?: JSONArray()
            } catch (e: Exception) {
                error = e.message ?: "Could not load feedback"
                items = JSONArray()
            }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AccentCyan)
            }
            Text("Your Opinion inbox", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { items = null; reload++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh feedback", tint = AccentCyan)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("Suggestions from MarketScope AI users, newest first. Photos are private and links expire after 15 minutes.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(18.dp))
            when {
                items == null -> CircularProgressIndicator(color = AccentCyan)
                error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                items?.length() == 0 -> Text("No feedback yet.", color = TextMuted)
                else -> {
                    val feedback = items ?: JSONArray()
                    for (i in 0 until feedback.length()) {
                        val item = feedback.optJSONObject(i) ?: continue
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(SurfaceLight)
                                .padding(16.dp)
                        ) {
                            val date = runCatching {
                                DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
                                    .withZone(ZoneId.systemDefault())
                                    .format(Instant.parse(item.optString("createdAt")))
                            }.getOrDefault("")
                            Text(item.optString("email"), style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = AccentCyan)
                            if (date.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(date, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(item.optString("message"), style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary)
                            val images = item.optJSONArray("images") ?: JSONArray()
                            val urls = (0 until images.length()).mapNotNull { index ->
                                images.optJSONObject(index)?.optString("url")?.takeIf { it.isNotBlank() && it != "null" }
                            }
                            if (urls.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    urls.forEachIndexed { index, url ->
                                        AsyncImage(
                                            model = url,
                                            contentDescription = "Feedback picture ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.weight(1f).height(70.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .clickable { viewerUrls = urls; viewerIndex = index }
                                        )
                                    }
                                    repeat(4 - urls.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(11.dp))
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
