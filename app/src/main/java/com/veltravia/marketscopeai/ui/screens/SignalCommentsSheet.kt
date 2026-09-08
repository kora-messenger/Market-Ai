package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.TextMuted
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.Duration
import java.time.Instant
import java.util.UUID

private data class SignalComment(
    val id: String,
    val authorName: String,
    val body: String,
    val createdAt: String,
    val pending: Boolean = false
)

/**
 * Flat comments sheet for a daily signal — same visual language as the
 * Community CommentsSheet, minus threading (signal discussions don't need
 * replies). Real backend-persisted comments, optimistic send with rollback.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SignalCommentsSheet(
    signalId: String,
    instrument: String,
    onDismiss: () -> Unit,
    onCountChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }

    var comments by remember { mutableStateOf<List<SignalComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var sending by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(signalId) {
        if (token == null) {
            loading = false
            loadError = "Not signed in"
            return@LaunchedEffect
        }
        try {
            val list: JSONArray = ApiClient.fetchSignalComments(token, signalId)
            comments = (0 until list.length()).mapNotNull { i ->
                val c = list.optJSONObject(i) ?: return@mapNotNull null
                SignalComment(
                    id = c.optString("id"),
                    authorName = c.optString("authorName").ifBlank { "Trader" },
                    body = c.optString("body"),
                    createdAt = c.optString("createdAt")
                )
            }
            onCountChange(comments.size)
        } catch (e: Exception) {
            loadError = e.message ?: "Could not load comments"
        } finally {
            loading = false
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun send() {
        val text = input.text.trim()
        if (text.isEmpty() || token == null || sending) return
        sending = true
        val optimistic = SignalComment(
            id = "tmp-${UUID.randomUUID()}",
            authorName = "You",
            body = text,
            createdAt = Instant.now().toString(),
            pending = true
        )
        comments = comments + optimistic
        input = TextFieldValue("")
        scope.launch {
            try {
                val resp = ApiClient.addSignalComment(token, signalId, text)
                val c = resp.optJSONObject("comment")
                comments = comments.map {
                    if (it.id == optimistic.id && c != null) SignalComment(
                        id = c.optString("id"),
                        authorName = c.optString("authorName").ifBlank { "You" },
                        body = c.optString("body"),
                        createdAt = c.optString("createdAt")
                    ) else it
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Comments", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    if (instrument.isNotBlank()) {
                        Text(instrument, fontSize = 11.sp, color = TextMuted)
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                loading -> CommentsSkeleton()
                comments.isEmpty() && loadError == null -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No comments yet", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                    Text("Be the first to discuss this call.", fontSize = 12.sp, color = TextMuted)
                }
                else -> {
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        comments.forEach { c -> SignalCommentRow(c) }
                    }
                }
            }

            loadError?.let { e ->
                Text("Couldn't load comments: $e", fontSize = 11.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Add a comment…", fontSize = 13.sp, color = TextMuted) },
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFF3F4F7),
                        unfocusedContainerColor = Color(0xFFF3F4F7),
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = AccentCyan,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = AccentCyan,
                    shape = RoundedCornerShape(14.dp),
                    onClick = { send() },
                    enabled = !sending && input.text.isNotBlank()
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                        if (sending) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SignalCommentRow(comment: SignalComment) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(AccentCyan.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                comment.authorName.take(1).uppercase(),
                color = AccentCyan,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.authorName, fontSize = 12.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (comment.pending) "sending…" else signalRelativeTime(comment.createdAt),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(comment.body, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

private fun signalRelativeTime(iso: String): String = try {
    val then = Instant.parse(iso)
    val mins = Duration.between(then, Instant.now()).toMinutes()
    when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        mins < 1440 -> "${mins / 60}h"
        else -> "${mins / 1440}d"
    }
} catch (e: Exception) {
    ""
}
