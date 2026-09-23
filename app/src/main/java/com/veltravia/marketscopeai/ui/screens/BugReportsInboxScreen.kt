package com.veltravia.marketscopeai.ui.screens

import android.net.Uri
import android.util.Base64
import android.widget.MediaController
import android.widget.VideoView
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.ImageViewerDialog
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Owner-only inbox. Server authorization protects the records and signs R2
 * attachment links; screenshots and recordings can both be viewed here. */
@Composable
fun BugReportsInboxScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var reload by remember { mutableStateOf(0) }
    var reports by remember { mutableStateOf<JSONArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewerUrls by remember { mutableStateOf<List<String>?>(null) }
    var viewerIndex by remember { mutableStateOf(0) }
    var recording by remember { mutableStateOf<Pair<String?, String?>?>(null) }

    viewerUrls?.let { urls ->
        ImageViewerDialog(urls = urls, initialIndex = viewerIndex, onDismiss = { viewerUrls = null })
    }
    recording?.let { (url, dataUrl) ->
        ReportRecordingDialog(url = url, dataUrl = dataUrl, onDismiss = { recording = null })
    }
    LaunchedEffect(reload) {
        val token = SessionManager.sessionToken(context)
        if (token == null) {
            error = "Please sign in to view bug reports."
            reports = JSONArray()
        } else {
            error = null
            try {
                reports = ApiClient.fetchAdminBugReports(token).optJSONArray("reports") ?: JSONArray()
            } catch (e: Exception) {
                error = e.message ?: "Could not load bug reports"
                reports = JSONArray()
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AccentCyan)
            }
            Text("Bug reports inbox", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { reports = null; reload++ }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh bug reports", tint = AccentCyan)
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            Text("Reports from users, newest first. Attached media is private; refresh if an image or recording link expires.",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(18.dp))
            when {
                reports == null -> CircularProgressIndicator(color = AccentCyan)
                error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                reports?.length() == 0 -> Text("No bug reports yet.", color = TextMuted)
                else -> {
                    val items = reports ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val report = items.optJSONObject(i) ?: continue
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(SurfaceLight).padding(16.dp)) {
                            val date = runCatching {
                                DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
                                    .withZone(ZoneId.systemDefault())
                                    .format(Instant.parse(report.optString("createdAt")))
                            }.getOrDefault("")
                            Text(report.optString("email").ifBlank { "User account" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = AccentCyan)
                            if (date.isNotBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(date, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(report.optString("description"),
                                style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            val details = listOfNotNull(
                                report.optString("appVersion").takeIf { it.isNotBlank() && it != "null" }?.let { "App $it" },
                                report.optString("deviceModel").takeIf { it.isNotBlank() && it != "null" },
                                report.optString("androidVersion").takeIf { it.isNotBlank() && it != "null" }
                            )
                            if (details.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted)
                            }
                            val attachments = report.optJSONArray("attachments") ?: JSONArray()
                            val images = (0 until attachments.length()).mapNotNull { index ->
                                attachments.optJSONObject(index)?.takeIf { it.optString("kind") == "image" }
                                    ?.let { a -> a.optString("url").takeIf { it.isNotBlank() && it != "null" }
                                        ?: a.optString("dataUrl").takeIf { it.startsWith("data:image/") } }
                            }
                            if (images.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    images.forEachIndexed { index, image ->
                                        AsyncImage(
                                            model = image,
                                            contentDescription = "Report screenshot ${index + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.weight(1f).height(72.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .clickable { viewerUrls = images; viewerIndex = index }
                                        )
                                    }
                                    repeat(4 - images.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            for (index in 0 until attachments.length()) {
                                val a = attachments.optJSONObject(index) ?: continue
                                if (a.optString("kind") != "video") continue
                                val url = a.optString("url").takeIf { it.startsWith("https://") }
                                val dataUrl = a.optString("dataUrl").takeIf { it.startsWith("data:video/") }
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { recording = url to dataUrl },
                                    enabled = url != null || dataUrl != null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.OndemandVideo, contentDescription = null, tint = AccentCyan)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (url == null && dataUrl == null) "Recording unavailable; refresh"
                                        else "Play screen recording")
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

/** R2 links play directly. Legacy inline uploads are written temporarily to
 * the app's private cache and deleted when the playback dialog closes. */
@Composable
private fun ReportRecordingDialog(url: String?, dataUrl: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cachedFile = remember(url, dataUrl) {
        if (dataUrl == null) null else File(context.cacheDir, "report-video-${UUID.randomUUID()}.mp4")
    }
    var source by remember(url, dataUrl) { mutableStateOf<String?>(url) }
    var error by remember(url, dataUrl) { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<VideoView?>(null) }
    LaunchedEffect(url, dataUrl) {
        if (url == null && dataUrl != null && cachedFile != null) {
            try {
                withContext(Dispatchers.IO) {
                    cachedFile.writeBytes(Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT))
                }
                source = Uri.fromFile(cachedFile).toString()
            } catch (_: Exception) {
                error = "Could not open this recording."
            }
        }
    }
    DisposableEffect(cachedFile) {
        onDispose {
            player?.stopPlayback()
            cachedFile?.delete()
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Text("Screen recording", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            if (error != null) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            } else if (source == null) {
                CircularProgressIndicator(color = AccentCyan)
            } else {
                val videoSource = source ?: ""
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            player = this
                            setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                            setOnPreparedListener { start() }
                            setOnErrorListener { _, _, _ ->
                                error = "This recording could not be played. Refresh and try again."
                                true
                            }
                            setVideoURI(Uri.parse(videoSource))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(240.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close")
            }
        }
    }
}
