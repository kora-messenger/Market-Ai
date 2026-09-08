package com.veltravia.marketscopeai.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.UserAvatar
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.UUID

private data class SignalUpdate(
    val id: String,
    val authorName: String,
    val authorPicture: String = "",
    val body: String,
    val createdAt: String,
    val replies: List<SignalUpdate> = emptyList()
)

private data class SignalComment(
    val id: String,
    val authorName: String,
    val authorPicture: String = "",
    val body: String,
    val createdAt: String,
    val hasImage: Boolean = false,
    val pendingReview: Boolean = false,
    val isMine: Boolean = false,
    val reactions: Map<String, Pair<Int, Boolean>> = emptyMap(),
    val pending: Boolean = false // optimistic, not yet on the server
)

/** Reaction set under each trader comment (server-validated). */
private val COMMENT_REACTIONS = listOf("\u2764\uFE0F", "\uD83D\uDE02", "\uD83D\uDE80", "\uD83D\uDC4D") // ❤️ 😂 🚀 👍

private fun timeAgo(iso: String): String {
    return try {
        val then = Instant.parse(iso)
        val mins = Duration.between(then, Instant.now()).toMinutes()
        when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 60 * 24 -> "${mins / 60}h ago"
            else -> "${mins / (60 * 24)}d ago"
        }
    } catch (_: Exception) { "" }
}

/**
 * The full discussion sheet for a daily signal — mirrors the reference layout:
 * a "Live updates" section (mentor desk commentary with nested follow-ups)
 * above a "Comments" section (trader comments with ❤️😂🚀👍 reactions and
 * optional trade screenshots pending mentor review). Everything is
 * backend-persisted; nothing here is decorative.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SignalCommentsSheet(
    signalId: String,
    instrument: String,
    isAdmin: Boolean = false,
    onDismiss: () -> Unit,
    onCountChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }

    var updates by remember { mutableStateOf<List<SignalUpdate>>(emptyList()) }
    var comments by remember { mutableStateOf<List<SignalComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var attachedImage by remember { mutableStateOf<String?>(null) } // data URL pending send
    var sending by remember { mutableStateOf(false) }
    var viewerImage by remember { mutableStateOf<String?>(null) } // full-screen comment screenshot

    // Admin-only inline composer for mentor-desk live updates.
    var showUpdateComposer by remember { mutableStateOf(false) }
    var updateInput by remember { mutableStateOf(TextFieldValue("")) }
    var replyTo by remember { mutableStateOf<String?>(null) } // parent update id

    LaunchedEffect(signalId) {
        if (token == null) {
            loading = false
            loadError = "Not signed in"
            return@LaunchedEffect
        }
        try {
            val updateList: JSONArray = ApiClient.fetchSignalUpdates(token, signalId)
            updates = (0 until updateList.length()).mapNotNull { i ->
                val u = updateList.optJSONObject(i) ?: return@mapNotNull null
                signalUpdateFromJson(u)
            }
        } catch (_: Exception) { /* updates are optional; a failure shouldn't block comments */ }
        try {
            val list: JSONArray = ApiClient.fetchSignalComments(token, signalId)
            comments = (0 until list.length()).mapNotNull { i ->
                val c = list.optJSONObject(i) ?: return@mapNotNull null
                signalCommentFromJson(c)
            }
            onCountChange(comments.size)
        } catch (e: Exception) {
            loadError = e.message ?: "Could not load comments"
        } finally {
            loading = false
        }
    }

    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val dataUrl = ApiClient.prepareChartImage(context, uri)
                    attachedImage = dataUrl
                } catch (_: Exception) { /* picker cancelled or unreadable */ }
            }
        }
    }

    fun sendComment() {
        val text = input.text.trim()
        if (text.isEmpty() || token == null || sending) return
        sending = true
        val image = attachedImage
        val me = SessionManager.currentUser(context)
        val optimistic = SignalComment(
            id = "tmp-${UUID.randomUUID()}",
            authorName = me?.name?.takeIf { it.isNotBlank() } ?: "You",
            authorPicture = me?.picture ?: "",
            body = text,
            createdAt = Instant.now().toString(),
            hasImage = image != null,
            pendingReview = image != null,
            isMine = true,
            pending = true
        )
        comments = comments + optimistic
        input = TextFieldValue("")
        attachedImage = null
        scope.launch {
            try {
                val resp = ApiClient.addSignalComment(token, signalId, text, image)
                val c = resp.optJSONObject("comment")
                if (c != null) {
                    val confirmed = signalCommentFromJson(c).copy(pending = false)
                    comments = comments.map { if (it.id == optimistic.id) confirmed else it }
                } else {
                    comments = comments.filterNot { it.id == optimistic.id }
                }
                onCountChange(comments.size)
            } catch (e: Exception) {
                comments = comments.filterNot { it.id == optimistic.id }
                loadError = e.message ?: "Could not post comment"
            } finally {
                sending = false
            }
        }
    }

    fun toggleCommentReaction(comment: SignalComment, emoji: String) {
        if (token == null || comment.pending) return
        val (count, mine) = comment.reactions[emoji] ?: (0 to false)
        val updated = comment.copy(
            reactions = comment.reactions.toMutableMap()
                .apply { put(emoji, if (mine) (count - 1).coerceAtLeast(0) to false else count + 1 to true) }
        )
        comments = comments.map { if (it.id == comment.id) updated else it }
        scope.launch {
            try {
                ApiClient.reactToSignalComment(token, comment.id, emoji)
            } catch (_: Exception) {
                comments = comments.map { if (it.id == comment.id) comment else it } // revert
            }
        }
    }

    fun moderate(commentId: String, approve: Boolean) {
        if (token == null) return
        scope.launch {
            try {
                if (approve) ApiClient.approveSignalComment(token, commentId)
                else ApiClient.rejectSignalComment(token, commentId)
                if (approve) {
                    comments = comments.map {
                        if (it.id == commentId) it.copy(pendingReview = false) else it
                    }
                } else {
                    comments = comments.filterNot { it.id == commentId }
                    onCountChange(comments.size)
                }
            } catch (e: Exception) {
                loadError = e.message ?: "Could not moderate comment"
            }
        }
    }

    fun postUpdate(parentId: String?) {
        val text = updateInput.text.trim()
        if (text.isEmpty() || token == null) return
        val optimistic = SignalUpdate(
            id = "tmp-${UUID.randomUUID()}",
            authorName = "Mentor Desk",
            body = text,
            createdAt = Instant.now().toString()
        )
        if (parentId == null) updates = updates + optimistic else updates = updates.map {
            if (it.id == parentId) it.copy(replies = it.replies + optimistic) else it
        }
        updateInput = TextFieldValue("")
        replyTo = null
        showUpdateComposer = false
        scope.launch {
            try {
                val resp = ApiClient.addSignalUpdate(token, signalId, text, parentId, "Mentor Desk")
                val u = resp.optJSONObject("update")
                val confirmed = if (u != null) signalUpdateFromJson(u) else null
                if (parentId == null) {
                    updates = updates.map { if (it.id == optimistic.id && confirmed != null) confirmed else it }
                } else {
                    updates = updates.map { up ->
                        if (up.id == parentId) up.copy(replies = up.replies.map { r ->
                            if (r.id == optimistic.id && confirmed != null) confirmed else r
                        }) else up
                    }
                }
            } catch (e: Exception) {
                // roll the optimistic update back out
                if (parentId == null) updates = updates.filterNot { it.id == optimistic.id }
                else updates = updates.map { up ->
                    if (up.id == parentId) up.copy(replies = up.replies.filterNot { it.id == optimistic.id }) else up
                }
                loadError = e.message ?: "Could not post update"
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            // --- Header ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Live updates & Comments", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (instrument.isNotBlank()) {
                        Text(instrument, fontSize = 11.sp, color = TextMuted)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(10.dp))

            when {
                loading -> SheetSkeleton()
                else -> {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // ============ LIVE UPDATES ============
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(7.dp).clip(CircleShape).background(BullGreen)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("Live updates", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(Modifier.width(8.dp))
                            if (updates.isEmpty() && !isAdmin) {
                                Text("None yet on this signal", fontSize = 11.sp, color = TextMuted)
                            }
                            if (isAdmin) {
                                Spacer(Modifier.weight(1f))
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(AccentViolet.copy(alpha = 0.08f))
                                        .clickable { showUpdateComposer = !showUpdateComposer; replyTo = null }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Post update", fontSize = 11.sp, color = AccentViolet, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))

                        if (isAdmin && showUpdateComposer) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceLight)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    if (replyTo == null) "Post a live update — every subscribed trader sees it instantly." else "Post a follow-up",
                                    fontSize = 10.sp, color = TextMuted
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = updateInput,
                                    onValueChange = { updateInput = it },
                                    placeholder = { Text(
                                        if (replyTo == null) "e.g. Apply good risk management" else "e.g. Close gold in profits",
                                        fontSize = 13.sp, color = TextMuted
                                    ) },
                                    shape = RoundedCornerShape(12.dp),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = AccentViolet,
                                        unfocusedBorderColor = BorderSubtle
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                Row {
                                    TextButton(onClick = { showUpdateComposer = false; replyTo = null; updateInput = TextFieldValue("") }) {
                                        Text("Cancel", fontSize = 12.sp, color = TextMuted)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Surface(
                                        color = AccentViolet,
                                        shape = RoundedCornerShape(10.dp),
                                        onClick = { postUpdate(replyTo) },
                                        enabled = updateInput.text.isNotBlank()
                                    ) {
                                        Text(
                                            "Post",
                                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                        }

                        updates.forEach { update -> MentorUpdateRow(update, isAdmin, onReply = {
                            replyTo = update.id
                            showUpdateComposer = true
                        }) }
                        if (updates.isEmpty() && !showUpdateComposer) {
                            Text("The mentor desk posts trade management notes here.", fontSize = 11.sp, color = TextMuted)
                        }
                        Spacer(Modifier.height(16.dp))

                        // ============ COMMENTS ============
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Comments", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("${comments.size}", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))

                        if (comments.isEmpty() && loadError == null) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("No comments yet", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text("Be the first to discuss this call.", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                        comments.forEach { c ->
                            TraderCommentRow(
                                c, isAdmin,
                                onReact = { emoji -> toggleCommentReaction(c, emoji) },
                                onOpenImage = { viewerImage = ApiClient.signalCommentImageUrl(c.id) },
                                onApprove = { moderate(c.id, true) },
                                onReject = { moderate(c.id, false) }
                            )
                        }

                        loadError?.let { e ->
                            Spacer(Modifier.height(6.dp))
                            Text("$e", fontSize = 11.sp, color = BearRed)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // ============ COMPOSER ============
            Spacer(Modifier.height(8.dp))
            if (attachedImage != null) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceLight)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = attachedImage,
                        contentDescription = "Attached screenshot",
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Screenshot attached", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("Visible to everyone after mentor review", fontSize = 10.sp, color = GoldAmber)
                    }
                    IconButton(onClick = { attachedImage = null }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove attachment", tint = TextMuted, modifier = Modifier.size(15.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Add a comment…", fontSize = 13.sp, color = TextMuted) },
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceLight,
                        unfocusedContainerColor = SurfaceLight,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = SurfaceLight,
                    shape = RoundedCornerShape(14.dp),
                    onClick = {
                        androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                        ).let { pickImage.launch(it) }
                    },
                    enabled = !sending
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.Image, contentDescription = "Attach screenshot", tint = TextSecondary, modifier = Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = AccentCyan,
                    shape = RoundedCornerShape(14.dp),
                    onClick = { sendComment() },
                    enabled = !sending && input.text.isNotBlank()
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                        if (sending) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }

    // Full-screen screenshot viewer.
    if (viewerImage != null) {
        Dialog(
            onDismissRequest = { viewerImage = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { viewerImage = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = viewerImage,
                    contentDescription = "Trade screenshot",
                    modifier = Modifier.fillMaxWidth()
                )
                IconButton(
                    onClick = { viewerImage = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

private fun signalUpdateFromJson(u: JSONObject): SignalUpdate {
    val replies = u.optJSONArray("replies") ?: JSONArray()
    return SignalUpdate(
        id = u.optString("id"),
        authorName = u.optString("authorName").ifBlank { "Mentor Desk" },
        authorPicture = u.optString("authorPicture"),
        body = u.optString("body"),
        createdAt = u.optString("createdAt"),
        replies = (0 until replies.length()).mapNotNull { i ->
            replies.optJSONObject(i)?.let { signalUpdateFromJson(it) }
        }
    )
}

private fun signalCommentFromJson(c: JSONObject): SignalComment {
    val list = c.optJSONArray("reactions")
    val reactions = linkedMapOf<String, Pair<Int, Boolean>>()
    if (list != null) {
        for (i in 0 until list.length()) {
            val r = list.optJSONObject(i) ?: continue
            reactions[r.optString("emoji")] = r.optInt("count", 0) to r.optBoolean("mine", false)
        }
    }
    return SignalComment(
        id = c.optString("id"),
        authorName = c.optString("authorName").ifBlank { "Trader" },
        authorPicture = c.optString("authorPicture"),
        body = c.optString("body"),
        createdAt = c.optString("createdAt"),
        hasImage = c.optBoolean("hasImage", false),
        pendingReview = c.optBoolean("pendingReview", false),
        isMine = c.optBoolean("isMine", false),
        reactions = reactions
    )
}

/** One mentor-desk live update, with nested follow-up replies below it. */
@Composable
private fun MentorUpdateRow(update: SignalUpdate, isAdmin: Boolean, onReply: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AccentViolet.copy(alpha = 0.05f))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(photoUrl = update.authorPicture.takeIf { it.isNotBlank() }, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(update.authorName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.width(6.dp))
                    Text(timeAgo(update.createdAt), fontSize = 10.sp, color = TextMuted)
                }
            }
            if (isAdmin) {
                Text(
                    "Follow-up",
                    fontSize = 10.sp, color = AccentViolet, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onReply() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(update.body, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)

        // Nested follow-up replies ("Close gold in profits" pattern).
        update.replies.forEach { reply ->
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(start = 8.dp)) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(reply.body.length.coerceAtMost(400).dp.coerceAtLeast(24.dp))
                        .background(AccentViolet.copy(alpha = 0.25f))
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(reply.authorName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentViolet)
                        Spacer(Modifier.width(6.dp))
                        Text(timeAgo(reply.createdAt), fontSize = 10.sp, color = TextMuted)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(reply.body, fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** One trader comment: name, body, optional screenshot, ❤️😂🚀👍 reaction pills, moderation controls for the admin. */
@Composable
private fun TraderCommentRow(
    comment: SignalComment,
    isAdmin: Boolean,
    onReact: (String) -> Unit,
    onOpenImage: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(photoUrl = comment.authorPicture.takeIf { it.isNotBlank() }, size = 26.dp)
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.authorName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(6.dp))
                Text(timeAgo(comment.createdAt), fontSize = 10.sp, color = TextMuted)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(comment.body, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)

        if (comment.hasImage && !comment.pending) {
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = ApiClient.signalCommentImageUrl(comment.id),
                contentDescription = "Trade screenshot",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenImage() }
            )
        }

        if (comment.pendingReview) {
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(GoldAmber.copy(alpha = 0.09f))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    if (comment.isMine) "Pending mentor review — only you can see this" else "Pending review",
                    fontSize = 10.sp, color = GoldAmber, fontWeight = FontWeight.Medium
                )
            }
            if (isAdmin) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onApprove,
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Text("Approve", fontSize = 11.sp, color = BullGreen, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        modifier = Modifier.height(32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                    ) {
                        Text("Reject", fontSize = 11.sp, color = BearRed, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        if (!comment.pending) {
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                COMMENT_REACTIONS.forEach { emoji ->
                    val (count, mine) = comment.reactions[emoji] ?: (0 to false)
                    val bg = if (mine) AccentCyan.copy(alpha = 0.12f) else SurfaceLight
                    val fg = if (mine) AccentCyan else TextMuted
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .clickable { onReact(emoji) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 11.sp)
                        if (count > 0) {
                            Spacer(Modifier.width(3.dp))
                            Text("$count", fontSize = 10.sp, color = fg, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

/** Shimmer-less placeholder rows for the first load of the sheet. */
@Composable
private fun SheetSkeleton() {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        repeat(3) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(SurfaceLight))
                Spacer(Modifier.width(8.dp))
                Column {
                    Box(Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(SurfaceLight))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.75f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(SurfaceLight))
                }
            }
        }
    }
}
