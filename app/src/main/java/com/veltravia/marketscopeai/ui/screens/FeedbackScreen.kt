package com.veltravia.marketscopeai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.components.ImageViewerDialog
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Private product feedback. An acknowledged submission is stored on the backend;
 * photos are compressed client-side, then uploaded into private R2 storage. */
@Composable
fun FeedbackScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = SessionManager.sessionToken(context)
    var message by remember { mutableStateOf("") }
    val photos = remember { mutableStateListOf<String>() }
    var preparing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    viewerIndex?.let { index ->
        ImageViewerDialog(urls = photos.toList(), initialIndex = index, onDismiss = { viewerIndex = null })
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        preparing = true
        error = null
        scope.launch {
            try {
                val photo = ApiClient.prepareChartImage(context, uri)
                if (photo.substringAfter("base64,", "").length > 4_000_000) {
                    error = "This image is too large. Choose a smaller picture."
                } else if (photos.size < 4) {
                    photos.add(photo)
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not add that picture"
            } finally {
                preparing = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AccentCyan)
            }
            Text("Your Opinion", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            if (sent) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 58.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null,
                        tint = AccentCyan, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(18.dp))
                    Text("Thanks for helping shape MarketScope AI",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = TextPrimary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Your message has reached our team. We read every submission, though we may not reply individually.",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(26.dp))
                    GradientPrimaryButton(text = "Done", enabled = true, onClick = onBack)
                }
            } else {
                Surface(
                    color = SurfaceLight,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Forum, contentDescription = null, tint = AccentViolet,
                            modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("We'd love to hear from you",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(Modifier.height(7.dp))
                            Text("MarketScope AI grows with the people who use it. Tell us what's working, what could be better, or which features you'd like to see. Your perspective helps guide what we build next.",
                                style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                Text("What's on your mind?", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= 4000) message = it },
                    placeholder = { Text("Share an idea, request a feature, or tell us about your experience…") },
                    minLines = 6,
                    maxLines = 12,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentViolet,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("${message.length}/4000", style = MaterialTheme.typography.labelSmall,
                    color = TextMuted, modifier = Modifier.align(Alignment.End).padding(top = 5.dp))
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Add pictures", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Optional · ${photos.size}/4", style = MaterialTheme.typography.labelMedium,
                        color = TextMuted)
                }
                Spacer(Modifier.height(8.dp))
                if (photos.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEachIndexed { index, image ->
                            Box(modifier = Modifier.weight(1f)) {
                                AsyncImage(
                                    model = image,
                                    contentDescription = "Attached picture ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(72.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewerIndex = index }
                                )
                                Surface(
                                    color = SurfaceLight,
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = { photos.removeAt(index) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(25.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove picture ${index + 1}",
                                            tint = TextPrimary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        repeat(4 - photos.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (photos.size < 4) {
                    Surface(
                        color = SurfaceLight,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                        onClick = {
                            if (!preparing && !sending) pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = AccentCyan)
                            Spacer(Modifier.width(12.dp))
                            Text(if (preparing) "Preparing picture…" else "Choose a picture",
                                color = TextPrimary, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Your feedback is sent with your account so our team can review it. Pictures are optional and visible only to the team.",
                    style = MaterialTheme.typography.bodySmall, color = TextMuted)
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = BearRed, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(24.dp))
                GradientPrimaryButton(
                    text = "Send your opinion",
                    enabled = !sending && !preparing && token != null && message.trim().length >= 3,
                    loading = sending,
                    onClick = {
                        val t = token ?: return@GradientPrimaryButton
                        sending = true
                        error = null
                        scope.launch {
                            try {
                                ApiClient.submitFeedback(t, message.trim(), photos.toList())
                                sent = true
                            } catch (e: Exception) {
                                error = e.message ?: "Could not send feedback. Try again."
                            } finally {
                                sending = false
                            }
                        }
                    }
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
