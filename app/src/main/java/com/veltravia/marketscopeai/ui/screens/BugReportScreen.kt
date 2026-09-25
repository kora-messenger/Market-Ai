package com.veltravia.marketscopeai.ui.screens

import com.veltravia.marketscopeai.t

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.veltravia.marketscopeai.BuildConfig
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.components.PremiumGradientBrush
import com.veltravia.marketscopeai.ui.components.pressScale
import com.veltravia.marketscopeai.ui.components.ImageViewerDialog
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * Report a bug — full screen from Settings (or from shaking the phone when
 * the shake toggle is on). The user writes what went wrong, attaches up to 4
 * screenshots and one screen recording, and sends it straight to the team.
 * Reports land in the backend (bug_reports) and ping the owner.
 */
@Composable
fun BugReportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }

    var description by remember { mutableStateOf(TextFieldValue("")) }
    val pickedImages = remember { mutableStateListOf<String>() }
    var videoDataUrl by remember { mutableStateOf<String?>(null) }
    var videoLabel by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    viewerIndex?.let { index ->
        ImageViewerDialog(
            urls = pickedImages.toList(),
            initialIndex = index,
            onDismiss = { viewerIndex = null }
        )
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        processing = true
        scope.launch {
            try {
                val dataUrl = ApiClient.prepareChartImage(context, uri)
                if (pickedImages.size < 4) pickedImages.add(dataUrl)
            } catch (e: Exception) {
                error = e.message ?: "Could not read that image"
            } finally {
                processing = false
            }
        }
    }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        processing = true
        scope.launch {
            try {
                val dataUrl = ApiClient.prepareVideoDataUrl(context, uri)
                videoDataUrl = dataUrl
                val bytes = dataUrl.substringAfter("base64,", "").length * 3 / 4
                videoLabel = formatMb(bytes)
            } catch (e: Exception) {
                error = e.message ?: "Could not read that video"
            } finally {
                processing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AccentCyan, modifier = Modifier.size(22.dp))
            }
            Text(t("Report a bug"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(10.dp))

            if (sent) {
                // Success state — the report is in, nothing else to do.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(t("Thanks — your report is with the team"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(t("We'll review it and follow up through your email if we need more detail."),
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    val doneInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val doneGradient = PremiumGradientBrush
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .drawBehind { drawRect(brush = doneGradient) }
                            .pressScale(doneInteraction, downScale = 0.97f)
                            .clickable { onBack() }
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t("Done"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            } else {
                // Intro card
                Surface(
                    color = AccentCyan.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.BugReport, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(t("Tell us what went wrong — the more detail, the faster we can fix it."),
                            fontSize = 12.5.sp,
                            color = TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Description
                Text(t("What happened?"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text(t("Describe the bug — what you did and what went wrong"), fontSize = 13.5.sp, color = Color(0xFF94A3B8)) },
                    minLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF8FAFC),
                        unfocusedContainerColor = Color(0xFFF8FAFC),
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // Screenshots (up to 4)
                Text(t("Screenshots"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Spacer(Modifier.height(6.dp))
                if (pickedImages.isEmpty() && !processing) {
                    AttachCard(
                        icon = { Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp)) },
                        title = t("Add screenshots"),
                        helper = "Up to 4 — error messages, the screen that glitched, anything helpful.",
                        onClick = {
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                } else {
                    val rows = (pickedImages.size + 2) / 3
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (0 until rows).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (0 until 3).forEach { col ->
                                    val idx = row * 3 + col
                                    when {
                                        idx < pickedImages.size -> {
                                            Box(modifier = Modifier.weight(1f)) {
                                                AsyncImage(
                                                    model = pickedImages[idx],
                                                    contentDescription = "Screenshot ${idx + 1}",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(92.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .background(Color(0xFFF1F5F9))
                                                        .clickable { viewerIndex = idx }
                                                )
                                                Surface(
                                                    color = Color(0x8C000000),
                                                    shape = CircleShape,
                                                    onClick = { pickedImages.removeAt(idx) },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(5.dp)
                                                        .size(22.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Filled.Close, contentDescription = "Remove screenshot", tint = Color.White, modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                            }
                                        }
                                        idx == pickedImages.size && !processing -> {
                                            Surface(
                                                color = Color(0xFFF8FAFC),
                                                shape = RoundedCornerShape(16.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                                onClick = {
                                                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                                },
                                                enabled = pickedImages.size < 4,
                                                modifier = Modifier.weight(1f).height(92.dp)
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(Icons.Filled.Add, contentDescription = null, tint = if (pickedImages.size < 4) AccentCyan else Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
                                                    Spacer(Modifier.height(3.dp))
                                                    Text(t("Add"), fontSize = 11.sp, color = TextMuted)
                                                }
                                            }
                                        }
                                        else -> Box(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // One screen recording
                Text(t("Screen recording"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Spacer(Modifier.height(6.dp))
                if (videoDataUrl == null) {
                    AttachCard(
                        icon = { Icon(Icons.Filled.OndemandVideo, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp)) },
                        title = t("Add a screen recording"),
                        helper = "Optional — one clip, up to 15MB. Perfect for showing the bug in motion.",
                        onClick = {
                            pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                        }
                    )
                } else {
                    Surface(
                        color = Color(0xFFF5F3FF),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDD6FE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Icon(Icons.Filled.OndemandVideo, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t("Recording attached"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                Text(videoLabel ?: "", fontSize = 11.5.sp, color = TextMuted)
                            }
                            Surface(
                                color = Color(0xFFFFF1F2),
                                shape = CircleShape,
                                onClick = {
                                    videoDataUrl = null
                                    videoLabel = null
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove recording", tint = Color(0xFFBE123C), modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }

                error?.let { msg ->
                    Spacer(Modifier.height(12.dp))
                    Text(msg, fontSize = 12.sp, color = Color(0xFFDC2626))
                }

                Spacer(Modifier.height(24.dp))

                // Send
                val canSend = !sending && description.text.trim().length >= 3 && token != null
                val sendInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val sendGradient = PremiumGradientBrush
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .drawBehind {
                            drawRect(brush = sendGradient, alpha = if (canSend) 1f else 0.45f)
                        }
                        .pressScale(sendInteraction, downScale = 0.98f)
                        .clickable(enabled = canSend) {
                            val t = token ?: return@clickable
                            sending = true
                            error = null
                            scope.launch {
                                try {
                                    ApiClient.submitBugReport(
                                        t,
                                        description.text.trim(),
                                        pickedImages.toList(),
                                        videoDataUrl,
                                        BuildConfig.VERSION_NAME,
                                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                                        "Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})"
                                    )
                                    sent = true
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not send the report"
                                } finally {
                                    sending = false
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (sending) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        Text(t("Send report"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(t("Sent with your account, app version and device model so we can reproduce it."),
                    fontSize = 11.sp,
                    color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** Dashed-look attach card used for both screenshots and the recording. */
@Composable
private fun AttachCard(
    icon: @Composable () -> Unit,
    title: String,
    helper: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0FDFA)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                Spacer(Modifier.height(2.dp))
                Text(helper, fontSize = 12.sp, color = Color(0xFF64748B), lineHeight = 16.sp)
            }
        }
    }
}

private fun formatMb(bytes: Int): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) String.format("%.1f MB", mb) else "${bytes / 1024} KB"
}
