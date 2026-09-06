package com.veltravia.marketai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketai.R
import com.veltravia.marketai.data.ApiClient
import com.veltravia.marketai.data.SessionManager
import com.veltravia.marketai.ui.theme.AccentCyan
import com.veltravia.marketai.ui.theme.AccentViolet
import com.veltravia.marketai.ui.theme.TextMuted
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

// --- domain models ----------------------------------------------------------------

data class CommunityReaction(val emoji: String, val count: Int, val mine: Boolean)

data class CommunityPost(
    val id: String,
    val authorName: String,
    val authorEmail: String,
    val isTeam: Boolean,
    val body: String,
    val createdAt: String,
    val commentCount: Int,
    val reactions: List<CommunityReaction>
)

data class CommunityComment(
    val id: String,
    val parentId: String?,
    val authorName: String,
    val body: String,
    val createdAt: String,
    val pending: Boolean = false
)

private val REACTION_SET = listOf(
    "\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83D\uDE80",
    "\uD83D\uDCB0", "\uD83D\uDCC8", "\uD83D\uDCC9", "\uD83D\uDCAF",
    "\uD83D\uDC4F", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE4F"
)

// --- helpers ------------------------------------------------------------------------

private fun parseFeed(json: JSONObject): List<CommunityPost> {
    val posts = json.optJSONArray("posts") ?: JSONArray()
    return (0 until posts.length()).map { i ->
        val p = posts.getJSONObject(i)
        val reactions = p.optJSONArray("reactions") ?: JSONArray()
        CommunityPost(
            id = p.optString("id"),
            authorName = p.optString("authorName").ifBlank { "Trader" },
            authorEmail = p.optString("authorEmail"),
            isTeam = p.optBoolean("isTeam"),
            body = p.optString("body"),
            createdAt = p.optString("createdAt"),
            commentCount = p.optInt("commentCount", 0),
            reactions = (0 until reactions.length()).map { r ->
                val o = reactions.getJSONObject(r)
                CommunityReaction(
                    emoji = o.optString("emoji"),
                    count = o.optInt("count", 0),
                    mine = o.optBoolean("mine")
                )
            }
        )
    }
}

private fun parseComments(json: JSONArray): List<CommunityComment> =
    (0 until json.length()).map { i ->
        val c = json.getJSONObject(i)
        CommunityComment(
            id = c.optString("id"),
            parentId = if (c.isNull("parentId") || !c.has("parentId")) null else c.optString("parentId"),
            authorName = c.optString("author_name").ifBlank { "Trader" },
            body = c.optString("body"),
            createdAt = c.optString("created_at")
        )
    }

private fun relativeTime(iso: String): String = try {
    val t = Instant.parse(iso)
    val d = Duration.between(t, Instant.now())
    when {
        d.toMinutes() < 1 -> "just now"
        d.toHours() < 1 -> "${d.toMinutes()}m ago"
        d.toDays() < 1 -> "${d.toHours()}h ago"
        d.toDays() < 7 -> "${d.toDays()}d ago"
        else -> DateTimeFormatter.ofPattern("MMM d").format(t.atZone(java.time.ZoneOffset.UTC))
    }
} catch (e: Exception) { "" }

private fun avatarTint(name: String): Color =
    if (name.hashCode() % 2 == 0) AccentViolet.copy(alpha = 0.12f) else AccentCyan.copy(alpha = 0.12f)

// --- screen -------------------------------------------------------------------------

@Composable
fun CommunityScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = SessionManager.sessionToken(context)
    val me = remember { SessionManager.currentUser(context) }

    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var totalPosts by remember { mutableStateOf(0) }
    var memberCount by remember { mutableStateOf(-1) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var composerText by remember { mutableStateOf(TextFieldValue("")) }
    var publishing by remember { mutableStateOf(false) }

    var openPost by remember { mutableStateOf<CommunityPost?>(null) }

    fun load(reset: Boolean) {
        if (token == null) return
        if (reset) loading = true else loadingMore = true
        scope.launch {
            try {
                val offset = if (reset) 0 else posts.size
                val feed = ApiClient.fetchCommunityFeed(token, offset = offset, limit = 20)
                val page = parseFeed(feed)
                hasMore = feed.optBoolean("hasMore", false)
                totalPosts = feed.optInt("total", 0)
                if (reset) {
                    posts = page
                    error = null
                } else {
                    posts = posts + page
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not load the feed"
            } finally {
                loading = false
                loadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        load(reset = true)
        scope.launch {
            try {
                val stats = ApiClient.fetchCommunityStats()
                memberCount = stats.optInt("totalMembers", -1)
            } catch (_: Exception) { }
        }
    }

    fun publish() {
        val text = composerText.text.trim()
        if (text.isEmpty() || token == null || publishing) return
        publishing = true
        scope.launch {
            try {
                val resp = ApiClient.createCommunityPost(token, text)
                val post = parseFeed(JSONObject().put("posts", JSONArray().put(resp.getJSONObject("post")))).first()
                posts = listOf(post) + posts
                totalPosts += 1
                composerText = TextFieldValue("")
            } catch (e: Exception) {
                error = e.message ?: "Could not publish"
            } finally {
                publishing = false
            }
        }
    }

    fun toggleReaction(post: CommunityPost, emoji: String) {
        if (token == null) return
        // optimistic update
        posts = posts.map {
            if (it.id == post.id) {
                val existing = it.reactions.find { r -> r.emoji == emoji }
                val updated = if (existing == null) {
                    it.reactions + CommunityReaction(emoji, 1, true)
                } else it.reactions.map { r ->
                    if (r.emoji == emoji) r.copy(
                        count = if (r.mine) r.count - 1 else r.count + 1,
                        mine = !r.mine
                    ) else r
                }.filter { r -> r.count > 0 }
                it.copy(reactions = updated)
            } else it
        }
        scope.launch {
            try {
                ApiClient.toggleCommunityReaction(token, post.id, emoji)
            } catch (e: Exception) {
                // revert on failure
                error = e.message ?: "Could not update reaction"
                load(reset = true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Original programmatic wallpaper behind the whole feed
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.community_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // translucent floating header
            Surface(
                color = Color(0xECFFFFFF),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            "Market Ai Community",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            if (memberCount >= 0) "$memberCount traders joined · $totalPosts posts"
                            else "Live trading community",
                            fontSize = 11.sp, color = TextMuted
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { load(reset = true) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextMuted, modifier = Modifier.size(19.dp))
                    }
                }
            }

            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan)
                }
                posts.isEmpty() && error == null -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("No posts yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (SessionManager.communityJoined(context))
                            "Be the first to share a win, a setup, or a thought."
                        else "Posts from traders will appear here.",
                        fontSize = 13.sp, color = TextMuted
                    )
                }
                else -> {
                    val listState = rememberLazyListState()
                    val endReached by remember {
                        derivedStateOf {
                            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                            last != null && last.index >= listState.layoutInfo.totalItemsCount - 3
                        }
                    }
                    LaunchedEffect(endReached) {
                        if (endReached && hasMore && !loadingMore && !loading && error == null) {
                            load(reset = false)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { PostComposer(composerText, publishing, { composerText = it }, { publish() }) }
                        items(posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                onReact = { emoji -> toggleReaction(post, emoji) },
                                onOpenComments = { openPost = post }
                            )
                        }
                        if (loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }

            error?.let { msg ->
                Surface(
                    color = Color(0xFFFFF7ED),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        "Couldn't reach the community: $msg — tap refresh to retry.",
                        fontSize = 12.sp,
                        color = Color(0xFFB45309),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    openPost?.let { post ->
        CommentsSheet(
            post = post,
            myName = me?.name ?: "You",
            onDismiss = { openPost = null },
            onCountChange = { newCount ->
                posts = posts.map { if (it.id == post.id) it.copy(commentCount = newCount) else it }
            }
        )
    }
}

// --- composer ------------------------------------------------------------------------

@Composable
private fun PostComposer(
    text: TextFieldValue,
    publishing: Boolean,
    onTextChange: (TextFieldValue) -> Unit,
    onPublish: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Share with the community", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("A win, a setup, a lesson learned…", fontSize = 13.5.sp, color = TextMuted) },
                minLines = 2,
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F4F7),
                    unfocusedContainerColor = Color(0xFFF3F4F7),
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = Color(0xFFE2E8F0)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Spacer(Modifier.weight(1f))
                Surface(
                    color = AccentCyan,
                    shape = RoundedCornerShape(12.dp),
                    onClick = onPublish,
                    enabled = !publishing && text.text.isNotBlank()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (publishing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Publish", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// --- post card ----------------------------------------------------------------------

@Composable
private fun PostCard(
    post: CommunityPost,
    onReact: (String) -> Unit,
    onOpenComments: () -> Unit
) {
    var showReactionRow by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(avatarTint(post.authorName))
                ) {
                    Text(
                        post.authorName.trim().take(1).uppercase(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (post.authorName.hashCode() % 2 == 0) AccentViolet else AccentCyan
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            post.authorName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (post.isTeam) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = AccentViolet.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "Market Ai Team",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentViolet,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(relativeTime(post.createdAt), fontSize = 11.sp, color = TextMuted)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                post.body,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            // reactions row
            if (post.reactions.isNotEmpty() || showReactionRow) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.reactions.take(6).forEach { reaction ->
                        Surface(
                            color = if (reaction.mine) AccentCyan.copy(alpha = 0.12f) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(10.dp),
                            onClick = { onReact(reaction.emoji) }
                        ) {
                            Text(
                                "${reaction.emoji} ${reaction.count}",
                                fontSize = 12.sp,
                                color = if (reaction.mine) AccentCyan else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (showReactionRow) {
                Spacer(Modifier.height(8.dp))
                Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        REACTION_SET.forEach { emoji ->
                            Text(
                                emoji,
                                fontSize = 19.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    onClick = { showReactionRow = !showReactionRow }
                ) {
                    Text(
                        if (showReactionRow) "Hide reactions" else "React",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AccentCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    onClick = onOpenComments
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AddComment, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (post.commentCount == 1) "1 comment" else "${post.commentCount} comments",
                            fontSize = 12.sp, color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

// --- comments bottom sheet ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(
    post: CommunityPost,
    myName: String,
    onDismiss: () -> Unit,
    onCountChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = SessionManager.sessionToken(context)

    var comments by remember { mutableStateOf<List<CommunityComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var replyTo by remember { mutableStateOf<CommunityComment?>(null) }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(post.id) {
        if (token == null) return@LaunchedEffect
        try {
            val list = ApiClient.fetchPostComments(token, post.id)
            comments = parseComments(list)
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
        val optimistic = CommunityComment(
            id = "tmp-${UUID.randomUUID()}",
            parentId = replyTo?.id,
            authorName = myName,
            body = text,
            createdAt = Instant.now().toString(),
            pending = true
        )
        comments = comments + optimistic
        val parent = replyTo
        input = TextFieldValue("")
        replyTo = null
        scope.launch {
            try {
                val resp = ApiClient.addPostComment(token, post.id, text, parent?.id)
                val c = resp.getJSONObject("comment")
                comments = comments.map {
                    if (it.id == optimistic.id) CommunityComment(
                        id = c.optString("id"),
                        parentId = if (c.isNull("parentId") || !c.has("parentId")) null else c.optString("parentId"),
                        authorName = c.optString("author_name").ifBlank { myName },
                        body = c.optString("body"),
                        createdAt = c.optString("created_at")
                    ) else it
                }
                onCountChange(post.commentCount + 1)
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
                    Text("Comments", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Text(relativeTime(post.createdAt), fontSize = 11.sp, color = TextMuted)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(6.dp))
            Surface(color = Color(0xFFF8FAFC), shape = RoundedCornerShape(12.dp)) {
                Text(
                    post.body,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 3,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(Modifier.height(10.dp))
            when {
                loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                comments.isEmpty() && loadError == null -> Column(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Be the first to reply", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                    Text("Start the conversation.", fontSize = 12.sp, color = TextMuted)
                }
                else -> {
                    val roots = comments.filter { it.parentId == null }
                    val repliesByParent = comments.filter { it.parentId != null }.groupBy { it.parentId }
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        roots.forEach { root ->
                            CommentRow(root) { replyTo = root }
                            repliesByParent[root.id]?.forEach { reply ->
                                Row(Modifier.padding(start = 30.dp)) {
                                    CommentRow(reply, isReply = true) { replyTo = reply }
                                }
                            }
                        }
                    }
                }
            }

            loadError?.let { e ->
                Text("Couldn't load comments: $e", fontSize = 11.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(vertical = 4.dp))
            }

            replyTo?.let { target ->
                Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Replying to ${target.authorName}", fontSize = 11.sp, color = TextMuted, modifier = Modifier.weight(1f))
                        IconButton(onClick = { replyTo = null }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel reply", tint = TextMuted, modifier = Modifier.size(13.dp))
                        }
                    }
                }
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
                        unfocusedBorderColor = Color(0xFFE2E8F0)
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
private fun CommentRow(
    comment: CommunityComment,
    isReply: Boolean = false,
    onReply: () -> Unit
) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (isReply) 26.dp else 30.dp)
                    .clip(CircleShape)
                    .background(avatarTint(comment.authorName))
            ) {
                Text(
                    comment.authorName.trim().take(1).uppercase(),
                    fontSize = if (isReply) 10.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (comment.authorName.hashCode() % 2 == 0) AccentViolet else AccentCyan
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.authorName, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        (if (comment.pending) "sending…" else relativeTime(comment.createdAt)),
                        fontSize = 10.sp, color = TextMuted
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(comment.body, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground)
                if (!comment.pending) {
                    Spacer(Modifier.height(2.dp))
                    Surface(color = Color.Transparent, shape = RoundedCornerShape(6.dp), onClick = onReply) {
                        Text("Reply", fontSize = 10.5.sp, fontWeight = FontWeight.Medium, color = AccentCyan, modifier = Modifier.padding(top = 1.dp))
                    }
                }
            }
        }
    }
}
