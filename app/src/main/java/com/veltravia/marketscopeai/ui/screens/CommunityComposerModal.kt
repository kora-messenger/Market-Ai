package com.veltravia.marketscopeai.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.veltravia.marketscopeai.ui.components.ImageViewerDialog
import com.veltravia.marketscopeai.ui.components.PremiumGradientBrush
import com.veltravia.marketscopeai.ui.components.pressScale
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.TextMuted
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

/**
 * The community publish flow, rebuilt to match the reference app's composer:
 * a floating compose button (bottom-right) that expands into "Post" and
 * "Poll" pills, which open a centered "publish" dialog. The dialog collects
 * the post/poll, quick-insert instrument chips, up to 4 attached images
 * (rendered as a grid on the published post), an optional win/loss outcome
 * tag, and an allow-comments switch for polls, with a full-width publish
 * button. All copy is our own wording (no-verbatim rule).
 */

// --- floating compose button -----------------------------------------------------------

/**
 * Floating compose button anchored bottom-right. Collapsed it's a gradient
 * circle with a "+" that rotates 45° when expanded; expanded it reveals two
 * white shadow pills stacked above it: Poll (top) and Post (bottom).
 */
@Composable
fun ComposeFab(
    open: Boolean,
    onToggle: () -> Unit,
    onChoose: (mode: String) -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (open) 45f else 0f,
        animationSpec = spring(dampingRatio = 0.72f),
        label = "fabRotation"
    )
    val pillScale by animateFloatAsState(
        targetValue = if (open) 1f else 0.6f,
        animationSpec = spring(dampingRatio = 0.72f),
        label = "fabPillScale"
    )
    val pillAlpha by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f),
        label = "fabPillAlpha"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 20.dp, bottom = 96.dp)) {
            if (open) {
                ComposeFabPill(
                    label = "Poll",
                    icon = { Icon(Icons.Filled.HowToVote, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(17.dp)) },
                    scale = pillScale,
                    alpha = pillAlpha,
                    onClick = { onChoose("poll") }
                )
                Spacer(Modifier.height(10.dp))
                ComposeFabPill(
                    label = "Post",
                    icon = { Icon(Icons.Filled.Create, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(17.dp)) },
                    scale = pillScale,
                    alpha = pillAlpha,
                    onClick = { onChoose("text") }
                )
                Spacer(Modifier.height(14.dp))
            }
        }

        val interaction = remember { MutableInteractionSource() }
        val fabGradient = PremiumGradientBrush
        Box(
            modifier = Modifier
                .padding(end = 20.dp, bottom = 24.dp)
                .size(56.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .drawBehind { drawRect(brush = fabGradient) }
                .pressScale(interaction, downScale = 0.92f)
                .clickable(interactionSource = interaction, indication = null) { onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = if (open) "Close compose options" else "Compose",
                tint = Color.White,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

/** One white shadow pill ("Post" / "Poll") shown above the FAB when expanded. */
@Composable
private fun ComposeFabPill(
    label: String,
    icon: @Composable () -> Unit,
    scale: Float,
    alpha: Float,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(50),
        shadowElevation = 8.dp,
        onClick = onClick,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0FDFA)),
                contentAlignment = Alignment.Center
            ) { icon() }
            Spacer(Modifier.width(10.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
        }
    }
}

// --- publish dialog -------------------------------------------------------------------

/** Quick-insert instrument chips — tap to append the pair to the post text. */
private val QUICK_PAIRS = listOf("EURUSD", "GBPUSD", "XAUUSD", "NAS100", "BTCUSD")

@Composable
fun PublishComposerModal(
    mode: String, // "text" | "poll"
    linkPreview: LinkPreview?,
    onDismiss: () -> Unit,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    pollOptions: SnapshotStateList<TextFieldValue>,
    onChangePollOption: (Int, TextFieldValue) -> Unit,
    onAddPollOption: () -> Unit,
    onRemovePollOption: (Int) -> Unit,
    allowComments: Boolean,
    onAllowCommentsChange: (Boolean) -> Unit,
    pickedImages: SnapshotStateList<PickedPostImage>,
    imageProcessing: Boolean,
    outcomeTag: String?,
    onOutcomeTagChange: (String?) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    publishing: Boolean,
    onPublish: () -> Unit
) {
    val isPoll = mode == "poll"
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    viewerIndex?.let { index ->
        ImageViewerDialog(
            urls = pickedImages.map { it.dataUrl },
            initialIndex = index,
            onDismiss = { viewerIndex = null }
        )
    }

    Dialog(onDismissRequest = { if (!publishing) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 14.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Header — dialog title + close button, hairline divider below.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 14.dp)
                ) {
                    Text(
                        if (isPoll) "New community poll" else "New community post",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { if (!publishing) onDismiss() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1F5F9))
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFF64748B), modifier = Modifier.size(19.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFF1F5F9)))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // --- main text --------------------------------------------------
                    Text(
                        if (isPoll) "Poll question" else "Your post",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    ComposerField(
                        value = text,
                        onValueChange = onTextChange,
                        placeholder = if (isPoll) "What should traders vote on?" else "What are you watching in the markets?",
                        minLines = if (isPoll) 1 else 3
                    )

                    if (!isPoll) {
                        // --- quick-insert pair chips -----------------------------------
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Quick pairs", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF94A3B8))
                            Spacer(Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                QUICK_PAIRS.forEach { pair ->
                                    Surface(
                                        color = Color(0xFFF0FDFA),
                                        shape = RoundedCornerShape(50),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCCFBF1)),
                                        onClick = {
                                            val base = text.text.trimEnd()
                                            val sep = if (base.isEmpty()) "" else " "
                                            onTextChange(TextFieldValue("$base$sep$pair "))
                                        }
                                    ) {
                                        Text(
                                            pair,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF0F766E),
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isPoll) {
                        // --- poll options -----------------------------------------------
                        Spacer(Modifier.height(16.dp))
                        Text("Options", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(Modifier.height(6.dp))
                        pollOptions.forEachIndexed { index, option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ComposerField(
                                    value = option,
                                    onValueChange = { onChangePollOption(index, it) },
                                    placeholder = "Option ${index + 1}",
                                    minLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFFFFF1F2),
                                    shape = CircleShape,
                                    onClick = { onRemovePollOption(index) },
                                    enabled = pollOptions.size > 2,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Remove,
                                            contentDescription = "Remove option ${index + 1}",
                                            tint = if (pollOptions.size > 2) Color(0xFFBE123C) else Color(0xFFFECDD3),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (pollOptions.size < 6) {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(20.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                onClick = onAddPollOption,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "+ Add another option",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F766E),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 13.dp)
                                )
                            }
                        }

                        // --- allow comments switch --------------------------------------
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            color = Color(0xFFF0FDFA),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCCFBF1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Allow comments", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Members can reply under this poll. You can remove any comment later.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF475569),
                                        lineHeight = 16.sp
                                    )
                                }
                                Switch(
                                    checked = allowComments,
                                    onCheckedChange = onAllowCommentsChange,
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Color(0xFF14B8A6),
                                        uncheckedTrackColor = Color(0xFFDBEAFE)
                                    )
                                )
                            }
                        }
                    }

                    if (!isPoll) {
                        // --- images -------------------------------------------------------
                        Spacer(Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Images", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.weight(1f))
                            Text(
                                "${pickedImages.size}/4",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        Spacer(Modifier.height(6.dp))

                        if (pickedImages.isEmpty() && !imageProcessing) {
                            // The dashed "add images" card
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(24.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                onClick = onPickImage,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF0FDFA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text("Add images to your post", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            "Up to 4 — they show as a grid on your post.",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B),
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            // Thumbnail grid with per-image remove + an extra add tile
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
                                                            model = pickedImages[idx].dataUrl,
                                                            contentDescription = "Attached image ${idx + 1}",
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
                                                            onClick = { onRemoveImage(idx) },
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(5.dp)
                                                                .size(22.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(Icons.Filled.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(12.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                                idx == pickedImages.size && !imageProcessing -> {
                                                    Surface(
                                                        color = Color(0xFFF8FAFC),
                                                        shape = RoundedCornerShape(16.dp),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                                        onClick = onPickImage,
                                                        enabled = pickedImages.size < 4,
                                                        modifier = Modifier.weight(1f).height(92.dp)
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Icon(Icons.Filled.Add, contentDescription = null, tint = if (pickedImages.size < 4) Color(0xFF0F766E) else Color(0xFFCBD5E1), modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.height(3.dp))
                                                            Text("Add", fontSize = 11.sp, color = Color(0xFF64748B))
                                                        }
                                                    }
                                                }
                                                imageProcessing && idx == pickedImages.size -> {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(92.dp)
                                                            .clip(RoundedCornerShape(16.dp))
                                                            .background(Color(0xFFF1F5F9))
                                                    ) {
                                                        CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                    }
                                                }
                                                else -> Box(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // --- outcome tag (our own feature) ----------------------------------
                        if (pickedImages.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Tag the outcome (optional)", fontSize = 11.5.sp, color = TextMuted)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("win" to "Profited", "loss" to "Lesson learned").forEach { (tag, label) ->
                                    val selected = outcomeTag == tag
                                    val tint = if (tag == "win") BullGreen else BearRed
                                    Surface(
                                        color = if (selected) tint.copy(alpha = 0.14f) else Color(0xFFF8FAFC),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) tint else Color(0xFFE2E8F0)),
                                        onClick = { onOutcomeTagChange(if (selected) null else tag) }
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (selected) tint else TextMuted,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- live link preview card --------------------------------------------
                    // Pasting a URL shows the scraped card immediately: the
                    // author sees exactly what readers will see under the post.
                    if (!isPoll && text.text.isNotBlank() && firstUrlIn(text.text) != null) {
                        Spacer(Modifier.height(16.dp))
                        Text("Link preview", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(Modifier.height(6.dp))
                        if (linkPreview != null) {
                            LinkPreviewCard(linkPreview)
                        } else {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                                ) {
                                    CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Reading the link you pasted\u2026",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    // --- publish button ---------------------------------------------------
                    Spacer(Modifier.height(20.dp))
                    val composeEnabled = !publishing && (
                        (!isPoll && text.text.isNotBlank()) ||
                            (isPoll && text.text.isNotBlank() && pollOptions.count { it.text.isNotBlank() } >= 2)
                        )
                    val interaction = remember { MutableInteractionSource() }
                    val publishGradient = PremiumGradientBrush
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .drawBehind {
                                drawRect(brush = publishGradient, alpha = if (composeEnabled) 1f else 0.45f)
                            }
                            .pressScale(interaction, downScale = 0.98f)
                            .clickable(interactionSource = interaction, indication = null, enabled = composeEnabled) { onPublish() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (publishing) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        } else {
                            Text(
                                if (isPoll) "Publish poll" else "Publish post",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/** Shared rounded input used across the publish dialog. */
@Composable
private fun ComposerField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    minLines: Int,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, fontSize = 13.5.sp, color = Color(0xFF94A3B8)) },
        minLines = minLines,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF0F172A)),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF8FAFC),
            unfocusedContainerColor = Color(0xFFF8FAFC),
            focusedBorderColor = AccentCyan,
            unfocusedBorderColor = Color(0xFFCBD5E1)
        ),
        modifier = modifier.fillMaxWidth()
    )
}
