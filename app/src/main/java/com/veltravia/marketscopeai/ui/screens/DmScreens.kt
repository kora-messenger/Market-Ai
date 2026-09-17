package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.RoleBadge
import com.veltravia.marketscopeai.ui.UserAvatar
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

/** One inbox row — a private thread, newest activity first. */
data class DmThread(
    val id: String,
    val counterpartEmail: String,
    val counterpartName: String,
    val counterpartAvatar: String,
    val counterpartRole: String,
    val lastMessage: String?,
    val lastMessageAt: String,
    val lastSender: String?,
    val unread: Int
)

private fun parseThread(t: JSONObject): DmThread = DmThread(
    id = t.optString("id"),
    counterpartEmail = t.optString("counterpart_email"),
    counterpartName = t.optString("counterpart_name").ifBlank { t.optString("counterpart_email") },
    counterpartAvatar = ApiClient.resolveAvatarUrl(t.optString("counterpart_avatar")) ?: "",
    counterpartRole = t.optString("counterpart_role", "user"),
    lastMessage = if (t.isNull("last_message")) null else t.optString("last_message"),
    lastMessageAt = t.optString("last_message_at"),
    lastSender = if (t.isNull("last_sender")) null else t.optString("last_sender"),
    unread = t.optInt("unread", 0)
)

/** One chat message. */
data class DmMsg(
    val id: String,
    val senderEmail: String,
    val body: String,
    val createdAt: String
)

private fun relative(iso: String): String = try {
    val d = Duration.between(Instant.parse(iso), Instant.now())
    when {
        d.toMinutes() < 1 -> "now"
        d.toHours() < 1 -> "${d.toMinutes()}m"
        d.toDays() < 1 -> "${d.toHours()}h"
        d.toDays() < 7 -> "${d.toDays()}d"
        else -> "${d.toDays() / 7}w"
    }
} catch (e: Exception) { "" }

/** Messages inbox — every private thread with team members and mentors. */
@Composable
fun DmThreadsScreen(onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val token = SessionManager.sessionToken(context)

    var threads by remember { mutableStateOf<List<DmThread>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val tk = token ?: run {
            error = "Please sign in again."
            loading = false
            return@LaunchedEffect
        }
        scope.launch {
            try {
                val resp = ApiClient.fetchDmThreads(tk)
                val arr = resp.optJSONArray("threads")
                threads = (0 until (arr?.length() ?: 0)).map { parseThread(arr!!.getJSONObject(it)) }
            } catch (e: Exception) {
                error = "Could not load your messages."
            }
            loading = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(4.dp))
            Column {
                Text("Messages", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("Private chats with team & mentors", fontSize = 11.sp, color = TextMuted)
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentCyan) }
            threads.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    error ?: "No messages yet.\nTap Message on a team or mentor post to start a private chat.",
                    fontSize = 13.sp, color = TextMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 16.dp)
            ) {
                items(threads) { t ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenThread(t.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        UserAvatar(photoUrl = t.counterpartAvatar.takeIf { it.isNotBlank() }, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    t.counterpartName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.width(6.dp))
                                RoleBadge(t.counterpartRole)
                            }
                            Text(
                                t.lastMessage ?: "Say hello",
                                fontSize = 12.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(relative(t.lastMessageAt), fontSize = 10.sp, color = TextMuted)
                            if (t.unread > 0) {
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .background(AccentViolet, CircleShape)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (t.unread > 99) "99+" else "${t.unread}",
                                        fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A live private chat with one roled author. Polls for new messages while
 * open, marks the thread read, and sends over the same authenticated API
 * as the rest of the app.
 */
@Composable
fun DmChatScreen(threadId: String, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val tk = SessionManager.sessionToken(context) ?: ""
    val myEmail = remember { SessionManager.currentUser(context)?.email ?: "" }.lowercase()

    var counterpartName by remember { mutableStateOf("") }
    var counterpartAvatar by remember { mutableStateOf("") }
    var counterpartRole by remember { mutableStateOf("user") }
    val messages = remember { mutableStateListOf<DmMsg>() }
    var loading by remember { mutableStateOf(true) }
    var sendFailed by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()

    suspend fun refresh(silent: Boolean = true) {
        if (tk.isBlank()) return
        try {
            val resp = ApiClient.fetchDmMessages(tk, threadId)
            if (!silent) loading = true
            val threadJson = resp.optJSONObject("thread")
            val cp = threadJson?.optJSONObject("counterpart")
            if (cp != null) {
                counterpartName = cp.optString("name")
                counterpartAvatar = ApiClient.resolveAvatarUrl(cp.optString("avatar")) ?: ""
                counterpartRole = cp.optString("role", "user")
            }
            val arr = resp.optJSONArray("messages")
            val fresh = (0 until (arr?.length() ?: 0)).map { i ->
                val m = arr!!.getJSONObject(i)
                DmMsg(
                    id = m.optString("id"),
                    senderEmail = m.optString("sender_email"),
                    body = m.optString("body"),
                    createdAt = m.optString("created_at")
                )
            }
            val hadCount = messages.size
            messages.clear()
            messages.addAll(fresh)
            // Anything I can see is read.
            ApiClient.markDmRead(tk, threadId)
            // Only glide to the bottom when messages actually arrived —
            // never yank the user away from older messages they're reading.
            if (messages.isNotEmpty() && fresh.size != hadCount) {
                listState.animateScrollToItem(messages.size - 1)
            }
        } catch (e: Exception) {
            // Poll hiccups are fine — the next tick retries.
        }
        loading = false
    }

    LaunchedEffect(threadId) {
        refresh(silent = false)
    }

    // Live-ish chat: poll every 5s while the screen is open.
    LaunchedEffect(threadId) {
        while (true) {
            delay(5000)
            refresh()
        }
    }

    // No .imePadding() here: the activity declares
    // windowSoftInputMode="adjustResize" (non-edge-to-edge window), so the
    // OS already resizes the whole window around the keyboard. Stacking
    // Compose's own imePadding() on top double-counted the keyboard height
    // and shoved this header clean off the top of the screen until the
    // keyboard closed.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(4.dp))
            UserAvatar(photoUrl = ApiClient.resolveAvatarUrl(counterpartAvatar), size = 34.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        counterpartName.ifBlank { "Loading..." },
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.width(6.dp))
                    RoleBadge(counterpartRole)
                }
                Text("Private conversation", fontSize = 10.sp, color = TextMuted)
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                loading && messages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan)
                }
                messages.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Say hello — this chat is private between you two.",
                        fontSize = 13.sp, color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { m ->
                        val mine = m.senderEmail.lowercase() == myEmail
                        Row(
                            horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                color = if (mine) Color(0xFFEEF2FF) else Color.White,
                                shape = RoundedCornerShape(
                                    topStart = 14.dp, topEnd = 14.dp,
                                    bottomStart = if (mine) 14.dp else 3.dp,
                                    bottomEnd = if (mine) 3.dp else 14.dp
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (mine) Color(0xFFE0E7FF) else Color(0xFFEEF1F5)),
                                modifier = Modifier.width(260.dp)
                            ) {
                                Text(
                                    m.body,
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Composer.
        Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 8.dp) {
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        sendFailed = false
                    },
                    placeholder = { Text("Message...", fontSize = 14.sp) },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = AccentViolet,
                    shape = CircleShape,
                    onClick = {
                        val body = draft.text.trim()
                        if (body.isBlank()) return@Surface
                        draft = TextFieldValue("")
                        scope.launch {
                            try {
                                ApiClient.sendDmMessage(tk, threadId, body)
                                refresh()
                            } catch (e: Exception) {
                                sendFailed = true
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
        if (sendFailed) {
            Text(
                "Message didn't send — check your connection and try again.",
                fontSize = 11.sp, color = Color(0xFFE11D48),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFF1F2F).copy(alpha = 0.06f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}
