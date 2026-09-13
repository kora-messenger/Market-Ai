package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.R
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.RoleBadge
import com.veltravia.marketscopeai.ui.UserAvatar
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.components.PremiumGradientBrush
import com.veltravia.marketscopeai.ui.components.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawBehind
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.TextMuted
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

// --- domain models ----------------------------------------------------------------

data class CommunityReaction(val emoji: String, val count: Int, val mine: Boolean)

data class PollOptionData(val id: String, val label: String)

data class CommunityPoll(
    val options: List<PollOptionData>,
    val counts: Map<String, Int>,
    val totalVotes: Int,
    val myVote: String?
)

data class CommunityPost(
    val id: String,
    val authorName: String,
    val authorEmail: String,
    val authorPicture: String = "",
    val authorRole: String = "user",
    val authorIsPremium: Boolean = false,
    val isTeam: Boolean,
    val isTopContributor: Boolean,
    val isPinned: Boolean,
    val imageCount: Int,
    val body: String,
    val createdAt: String,
    val commentCount: Int,
    val allowComments: Boolean,
    val postType: String,
    val poll: CommunityPoll?,
    val reactions: List<CommunityReaction>,
    val outcomeTag: String? = null,
    val viewCount: Int = 0
)

/** A real curated pinned post (from GET /api/community/pinned). */
data class PinnedPost(
    val id: String,
    val title: String,
    val authorName: String,
    val createdAt: String
)

/** A real, engagement-ranked weekly proof post (from the leaderboard's topProofs). */
data class ProofPost(
    val postId: String,
    val authorName: String,
    val body: String,
    val imageCount: Int,
    val outcomeTag: String?,
    val weekReactions: Int
)

data class PickedPostImage(val dataUrl: String)

data class CommunityComment(
    val id: String,
    val parentId: String?,
    val authorName: String,
    val authorPicture: String = "",
    val authorRole: String = "user",
    val authorIsPremium: Boolean = false,
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

private fun parsePoll(p: JSONObject): CommunityPoll? {
    if (p.optString("postType", "text") != "poll") return null
    val optionsArr = p.optJSONObject("poll")?.optJSONArray("options") ?: return null
    val options = (0 until optionsArr.length()).map { i ->
        val o = optionsArr.getJSONObject(i)
        PollOptionData(id = o.optString("id"), label = o.optString("label"))
    }
    if (options.size < 2) return null
    val pollObj = p.getJSONObject("poll")
    val countsObj = pollObj.optJSONObject("counts") ?: JSONObject()
    val counts = options.associate { it.id to countsObj.optInt(it.id, 0) }
    return CommunityPoll(
        options = options,
        counts = counts,
        totalVotes = pollObj.optInt("totalVotes", counts.values.sum()),
        myVote = pollObj.optString("myVote").takeIf { it.isNotBlank() && it != "null" }
    )
}

private fun parseFeed(json: JSONObject): List<CommunityPost> {
    val posts = json.optJSONArray("posts") ?: JSONArray()
    return (0 until posts.length()).map { i ->
        val p = posts.getJSONObject(i)
        val reactions = p.optJSONArray("reactions") ?: JSONArray()
        CommunityPost(
            id = p.optString("id"),
            authorName = p.optString("authorName").ifBlank { "Trader" },
            authorEmail = p.optString("authorEmail"),
            authorPicture = p.optString("authorPicture"),
            authorRole = p.optString("authorRole", "user"),
            authorIsPremium = p.optBoolean("authorIsPremium", false),
            isTeam = p.optBoolean("isTeam"),
            isTopContributor = p.optBoolean("isTopContributor"),
            isPinned = p.optBoolean("isPinned"),
            imageCount = p.optInt("imageCount", 0),
            body = p.optString("body"),
            createdAt = p.optString("createdAt"),
            commentCount = p.optInt("commentCount", 0),
            allowComments = p.optBoolean("allowComments", true),
            postType = p.optString("postType", "text"),
            poll = parsePoll(p),
            outcomeTag = p.optString("outcomeTag").takeIf { it.isNotBlank() && it != "null" },
            viewCount = p.optInt("viewCount", 0),
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
            authorPicture = c.optString("author_picture"),
            authorRole = c.optString("author_role", "user"),
            authorIsPremium = c.optBoolean("authorIsPremium", c.optBoolean("author_is_premium", false)),
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

private fun parsePinned(json: JSONObject): List<PinnedPost> {
    val arr = json.optJSONArray("pinned") ?: JSONArray()
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        PinnedPost(
            id = o.optString("id"),
            title = o.optString("title").ifBlank { "Pinned post" },
            authorName = o.optString("authorName").ifBlank { "Trader" },
            createdAt = o.optString("createdAt")
        )
    }
}

private fun parseTopProofs(leaderboard: JSONObject): List<ProofPost> {
    val arr = leaderboard.optJSONArray("topProofs") ?: JSONArray()
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        ProofPost(
            postId = o.optString("postId"),
            authorName = o.optString("authorName").ifBlank { "Trader" },
            body = o.optString("body"),
            imageCount = o.optInt("imageCount", 0),
            outcomeTag = o.optString("outcomeTag").takeIf { it.isNotBlank() && it != "null" },
            weekReactions = o.optInt("weekReactions", 0)
        )
    }
}

// --- screen -------------------------------------------------------------------------

@Composable
fun CommunityScreen(onOpenLeaderboard: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = SessionManager.sessionToken(context)
    val me = remember { SessionManager.currentUser(context) }

    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var totalPosts by remember { mutableStateOf(0) }
    var memberCount by remember { mutableStateOf(-1) }
    var onlineCount by remember { mutableStateOf(-1) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var composerText by remember { mutableStateOf(TextFieldValue("")) }
    var publishing by remember { mutableStateOf(false) }
    var composerMode by remember { mutableStateOf("text") } // "text" | "poll"
    val pollOptions = remember { mutableStateListOf(TextFieldValue(""), TextFieldValue("")) }
    var allowComments by remember { mutableStateOf(true) }
    val pickedImages = remember { mutableStateListOf<PickedPostImage>() }
    var imageProcessing by remember { mutableStateOf(false) }

    var openPost by remember { mutableStateOf<CommunityPost?>(null) }
    var viewerPost by remember { mutableStateOf<CommunityPost?>(null) }
    var viewerIndex by remember { mutableStateOf(0) }
    var isAdmin by remember { mutableStateOf(false) }
    var composerOutcomeTag by remember { mutableStateOf<String?>(null) }

    var pinnedPosts by remember { mutableStateOf<List<PinnedPost>>(emptyList()) }
    var pinnedIndex by remember { mutableStateOf(0) }
    var showPinnedList by remember { mutableStateOf(false) }
    var proofPosts by remember { mutableStateOf<List<ProofPost>>(emptyList()) }
    val viewedPostIds = remember { mutableStateOf(mutableSetOf<String>()) }

    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            imageProcessing = true
            scope.launch {
                try {
                    val dataUrl = ApiClient.prepareChartImage(context, uri)
                    if (pickedImages.size < 4) pickedImages.add(PickedPostImage(dataUrl))
                } catch (e: Exception) {
                    error = e.message ?: "Could not read that image"
                } finally {
                    imageProcessing = false
                }
            }
        }
    }

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

    fun togglePin(post: CommunityPost) {
        if (token == null) return
        posts = posts.map {
            if (it.id == post.id) it.copy(isPinned = !it.isPinned) else it
        }
        scope.launch {
            try {
                ApiClient.pinCommunityPost(token, post.id)
            } catch (e: Exception) {
                error = e.message ?: "Could not pin the post"
                load(reset = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        load(reset = true)
        scope.launch {
            try {
                val stats = ApiClient.fetchCommunityStats()
                memberCount = stats.optInt("totalMembers", -1)
                onlineCount = stats.optInt("onlineCount", -1)
            } catch (_: Exception) { }
            if (token != null) {
                try {
                    val access = ApiClient.fetchSignalAccess(token)
                    isAdmin = access.optBoolean("isAdmin", false)
                } catch (_: Exception) { }
                try {
                    pinnedPosts = parsePinned(ApiClient.fetchPinnedPosts(token))
                } catch (_: Exception) { }
                try {
                    proofPosts = parseTopProofs(ApiClient.fetchLeaderboard(token))
                } catch (_: Exception) { }
            }
        }
    }

    fun registerView(postId: String) {
        if (token == null || postId in viewedPostIds.value) return
        viewedPostIds.value = (viewedPostIds.value + postId).toMutableSet()
        scope.launch {
            try {
                val resp = ApiClient.registerPostView(token, postId)
                val newCount = resp.optInt("viewCount", -1)
                if (newCount >= 0) {
                    posts = posts.map { if (it.id == postId) it.copy(viewCount = newCount) else it }
                }
            } catch (_: Exception) { /* view tracking is best-effort, never blocks the UI */ }
        }
    }

    fun publish() {
        val text = composerText.text.trim()
        if (token == null || publishing) return
        if (composerMode == "text") {
            if (text.isEmpty()) return
        } else {
            val labels = pollOptions.map { it.text.trim() }.filter { it.isNotBlank() }
            if (text.isEmpty() || labels.size < 2) return
        }
        publishing = true
        scope.launch {
            try {
                val resp = if (composerMode == "text") {
                    ApiClient.createCommunityPost(
                        token, text, pickedImages.map { it.dataUrl },
                        outcomeTag = if (pickedImages.isNotEmpty()) composerOutcomeTag else null
                    )
                } else {
                    ApiClient.createCommunityPoll(
                        token, text,
                        pollOptions.map { it.text.trim() }.filter { it.isNotBlank() },
                        allowComments
                    )
                }
                val post = parseFeed(JSONObject().put("posts", JSONArray().put(resp.getJSONObject("post")))).first()
                posts = listOf(post) + posts
                totalPosts += 1
                composerText = TextFieldValue("")
                pickedImages.clear()
                composerOutcomeTag = null
                pollOptions.clear()
                pollOptions.addAll(listOf(TextFieldValue(""), TextFieldValue("")))
                composerMode = "text"
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
                error = e.message ?: "Could not update reaction"
                load(reset = true)
            }
        }
    }

    fun votePoll(post: CommunityPost, optionId: String) {
        if (token == null) return
        val wasMyVote = post.poll?.myVote == optionId
        if (wasMyVote) return // tapping your own choice does nothing
        // optimistic update
        posts = posts.map {
            if (it.id == post.id && it.poll != null) {
                val oldVote = it.poll.myVote
                val newCounts = it.poll.counts.toMutableMap()
                if (oldVote != null) newCounts[oldVote] = (newCounts[oldVote] ?: 0) - 1
                newCounts[optionId] = (newCounts[optionId] ?: 0) + 1
                val totalDelta = if (oldVote == null) 1 else 0
                it.copy(poll = it.poll.copy(
                    counts = newCounts,
                    totalVotes = it.poll.totalVotes + totalDelta,
                    myVote = optionId
                ))
            } else it
        }
        scope.launch {
            try {
                ApiClient.votePoll(token, post.id, optionId)
            } catch (e: Exception) {
                error = e.message ?: "Could not record the vote"
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
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "MarketScope AI Community",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.Verified,
                                contentDescription = "Official community",
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            if (memberCount >= 0) "$memberCount traders joined · $onlineCount online now · $totalPosts posts"
                            else "Live trading community",
                            fontSize = 11.sp, color = TextMuted
                        )
                    }
                    IconButton(onClick = { load(reset = true) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextMuted, modifier = Modifier.size(19.dp))
                    }
                }
            }

            when {
                loading && posts.isEmpty() -> CommunityFeedSkeleton()
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
                        if (pinnedPosts.isNotEmpty()) {
                            item {
                                PinnedPostsWidget(
                                    pinned = pinnedPosts,
                                    index = pinnedIndex,
                                    onIndexChange = { pinnedIndex = it },
                                    expanded = showPinnedList,
                                    onToggleExpanded = { showPinnedList = !showPinnedList },
                                    onOpenTop5 = onOpenLeaderboard,
                                    onOpenPinned = { pinnedId ->
                                        showPinnedList = false
                                        val idx = posts.indexOfFirst { it.id == pinnedId }
                                        if (idx >= 0) {
                                            // Header items before `items(posts)`: pinned widget, proof row,
                                            // weekly-competition card, composer — count only the ones present.
                                            val headerCount = (if (pinnedPosts.isNotEmpty()) 1 else 0) +
                                                (if (proofPosts.isNotEmpty()) 1 else 0) + 2
                                            scope.launch { listState.animateScrollToItem(idx + headerCount) }
                                        }
                                    }
                                )
                            }
                        }
                        if (proofPosts.isNotEmpty()) {
                            item {
                                FeaturedProofRow(proofPosts)
                            }
                        }
                        item {
                            WeeklyCompetitionCard(onOpen = onOpenLeaderboard)
                        }
                        item {
                            PostComposer(
                                mode = composerMode,
                                onModeChange = { composerMode = it },
                                text = composerText,
                                onTextChange = { composerText = it },
                                pollOptions = pollOptions,
                                allowComments = allowComments,
                                onAllowCommentsChange = { allowComments = it },
                                pickedImages = pickedImages,
                                imageProcessing = imageProcessing,
                                outcomeTag = composerOutcomeTag,
                                onOutcomeTagChange = { composerOutcomeTag = it },
                                onPickImage = {
                                    pickImage.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                onRemoveImage = { index ->
                                    if (index in pickedImages.indices) pickedImages.removeAt(index)
                                },
                                publishing = publishing,
                                onPublish = { publish() }
                            )
                        }
                        items(posts, key = { it.id }) { post ->
                            PostCard(
                                post = post,
                                isAdmin = isAdmin,
                                onReact = { emoji -> toggleReaction(post, emoji) },
                                onVote = { optionId -> votePoll(post, optionId) },
                                onOpenComments = { openPost = post },
                                onPin = { togglePin(post) },
                                onOpenImage = { idx ->
                                    viewerPost = post
                                    viewerIndex = idx
                                },
                                onRegisterView = { registerView(post.id) }
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
                if (posts.isEmpty()) {
                    // Full-screen centered failure state with a real retry action.
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("Couldn't load the feed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(6.dp))
                        Text(msg, fontSize = 13.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(18.dp))
                        GradientPrimaryButton(
                            text = "Retry",
                            enabled = true,
                            onClick = { load(reset = true) },
                            height = 44.dp,
                            showArrow = false
                        )
                    }
                } else {
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
    }

    viewerPost?.let { post ->
        if (post.imageCount > 0) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewerPost = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { viewerPost = null },
                    contentAlignment = Alignment.Center
                ) {
                    coil.compose.AsyncImage(
                        model = ApiClient.communityImageUrl(post.id, viewerIndex),
                        contentDescription = "Post image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        color = Color(0xB3000000),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            "${viewerIndex + 1} / ${post.imageCount} · tap anywhere to close",
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    openPost?.let { post ->
        CommentsSheet(
            post = post,
            myName = me?.name ?: "You",
            myPicture = me?.picture ?: "",
            onDismiss = { openPost = null },
            onCountChange = { newCount ->
                posts = posts.map { if (it.id == post.id) it.copy(commentCount = newCount) else it }
            }
        )
    }
}

// --- pinned posts widget + featured proofs ---------------------------------------------

@Composable
private fun PinnedPostsWidget(
    pinned: List<PinnedPost>,
    index: Int,
    onIndexChange: (Int) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenTop5: () -> Unit,
    onOpenPinned: (String) -> Unit
) {
    val safeIndex = index.coerceIn(0, pinned.size - 1)
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.PushPin, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "PINNED \u00B7 ${safeIndex + 1}/${pinned.size}",
                            fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = AccentViolet
                        )
                        Text(
                            pinned[safeIndex].title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { onOpenPinned(pinned[safeIndex].id) }
                        )
                    }
                    if (pinned.size > 1) {
                        IconButton(
                            onClick = { onIndexChange((safeIndex - 1 + pinned.size) % pinned.size) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous pinned post", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { onIndexChange((safeIndex + 1) % pinned.size) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next pinned post", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                    IconButton(onClick = onToggleExpanded, modifier = Modifier.size(24.dp)) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Hide pinned posts" else "Show all pinned posts",
                            tint = TextMuted, modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(14.dp),
                onClick = onOpenTop5
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Top 5", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309))
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "Curated pinned posts",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    pinned.forEachIndexed { i, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenPinned(item.id) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(AccentViolet.copy(alpha = 0.12f))
                            ) {
                                Text("${i + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentViolet)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(relativeTime(item.createdAt), fontSize = 10.5.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedProofRow(proofs: List<ProofPost>) {
    Column {
        Text(
            "Featured trader proof this week",
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(proofs, key = { it.postId }) { proof ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.width(150.dp)
                ) {
                    Column {
                        coil.compose.AsyncImage(
                            model = ApiClient.communityImageUrl(proof.postId, 0),
                            contentDescription = "Weekly proof from ${proof.authorName}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        )
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    proof.authorName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (proof.outcomeTag != null) {
                                Spacer(Modifier.height(4.dp))
                                val tint = if (proof.outcomeTag == "win") BullGreen else BearRed
                                Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        if (proof.outcomeTag == "win") "Profited" else "Lesson learned",
                                        fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = tint,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("Weekly proof \u00B7 ${proof.weekReactions} reactions", fontSize = 9.5.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyCompetitionCard(onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(AccentViolet.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Weekly Competition",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Climb the ranks \uD83C\uDFC6 · Standings reset every Monday · Top contributors win a badge",
                    fontSize = 11.5.sp,
                    color = TextMuted
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// --- composer ------------------------------------------------------------------------

@Composable
private fun PostComposer(
    mode: String,
    onModeChange: (String) -> Unit,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    pollOptions: androidx.compose.runtime.snapshots.SnapshotStateList<TextFieldValue>,
    allowComments: Boolean,
    onAllowCommentsChange: (Boolean) -> Unit,
    pickedImages: androidx.compose.runtime.snapshots.SnapshotStateList<PickedPostImage>,
    imageProcessing: Boolean,
    outcomeTag: String?,
    onOutcomeTagChange: (String?) -> Unit,
    onPickImage: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    publishing: Boolean,
    onPublish: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ComposerTab("Text", mode == "text") { onModeChange("text") }
                Spacer(Modifier.width(8.dp))
                ComposerTab("Poll", mode == "poll") { onModeChange("poll") }
            }
            Spacer(Modifier.height(10.dp))
            if (mode == "text") {
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
                if (pickedImages.isNotEmpty() || imageProcessing) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pickedImages.forEachIndexed { index, img ->
                            Box {
                                coil.compose.AsyncImage(
                                    model = img.dataUrl,
                                    contentDescription = "Attached image ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                                Surface(
                                    color = Color(0xCC1E293B),
                                    shape = CircleShape,
                                    onClick = { onRemoveImage(index) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(18.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove image", tint = Color.White, modifier = Modifier.size(11.dp))
                                }
                            }
                        }
                        if (imageProcessing) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF1F5F9))
                            ) {
                                CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    onClick = onPickImage,
                    enabled = !imageProcessing && pickedImages.size < 4
                ) {
                    Text(
                        if (pickedImages.isEmpty()) "＋ Add post images (share your win 🎉)" else "＋ Add more (${pickedImages.size}/4)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (pickedImages.size < 4) AccentCyan else TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
                if (pickedImages.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Tag the outcome (optional, your call)", fontSize = 11.5.sp, color = TextMuted)
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
            } else {
                Text("Ask the community", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("Your poll question…", fontSize = 13.5.sp, color = TextMuted) },
                    minLines = 1,
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
                pollOptions.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = option,
                            onValueChange = { pollOptions[index] = it },
                            placeholder = { Text("Option ${index + 1}", fontSize = 13.5.sp, color = TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFFF3F4F7),
                                unfocusedContainerColor = Color(0xFFF3F4F7),
                                focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                                focusedBorderColor = AccentCyan,
                                unfocusedBorderColor = Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 3.dp)
                        )
                        if (pollOptions.size > 2) {
                            IconButton(onClick = { pollOptions.removeAt(index) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove option", tint = TextMuted, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
                if (pollOptions.size < 6) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "+ Add option",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { pollOptions.add(TextFieldValue("")) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAllowCommentsChange(!allowComments) }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (allowComments) AccentCyan else Color(0xFFE2E8F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (allowComments) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Allow comments on this poll", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row {
                Spacer(Modifier.weight(1f))
                val composeEnabled = !publishing && (
                    (mode == "text" && text.text.isNotBlank()) ||
                    (mode == "poll" && text.text.isNotBlank() && pollOptions.count { it.text.isNotBlank() } >= 2)
                )
                val composeInteraction = remember { MutableInteractionSource() }
                val composeGradient = PremiumGradientBrush
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .pressScale(composeInteraction, downScale = 0.95f)
                        .clip(RoundedCornerShape(12.dp))
                        .drawBehind { drawRect(brush = composeGradient, alpha = if (composeEnabled) 1f else 0.4f) }
                        .clickable(
                            interactionSource = composeInteraction,
                            indication = rememberRipple(),
                            enabled = composeEnabled
                        ) { onPublish() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (publishing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (mode == "poll") Icons.Filled.HowToVote else Icons.Filled.Send,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (mode == "poll") "Publish poll" else "Publish",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) AccentCyan.copy(alpha = 0.12f) else Color(0xFFF3F4F7),
        shape = RoundedCornerShape(9.dp),
        onClick = onClick
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) AccentCyan else TextMuted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// --- post card ----------------------------------------------------------------------

@Composable
private fun PostCard(
    post: CommunityPost,
    isAdmin: Boolean,
    onReact: (String) -> Unit,
    onVote: (String) -> Unit,
    onOpenComments: () -> Unit,
    onPin: () -> Unit,
    onOpenImage: (Int) -> Unit,
    onRegisterView: () -> Unit
) {
    var showReactionRow by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Registers a real, deduped view once per composition (per session per post).
    LaunchedEffect(post.id) { onRegisterView() }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(photoUrl = post.authorPicture.takeIf { it.isNotBlank() }, size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            post.authorName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (post.authorIsPremium) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.Verified, contentDescription = "Premium member", tint = GoldAmber, modifier = Modifier.size(14.dp))
                        }
                        if (post.authorRole.equals("admin", true) || post.authorRole.equals("moderator", true) || post.authorRole.equals("mentor", true)) {
                            Spacer(Modifier.width(6.dp))
                            RoleBadge(post.authorRole)
                        }
                        if (post.isTeam) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = AccentViolet.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "MarketScope AI Team",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentViolet,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (post.isTopContributor) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "\uD83C\uDFC6 Top Contributor",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (post.isPinned) {
                            Spacer(Modifier.width(6.dp))
                            Surface(color = AccentViolet.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "\uD83D\uDCCC Pinned",
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
                if (isAdmin) {
                    IconButton(onClick = onPin, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = if (post.isPinned) "Unpin post" else "Pin post",
                            tint = if (post.isPinned) AccentViolet else TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (post.poll != null) {
                PollBody(post, onVote)
            } else {
                if (post.body.isNotBlank()) {
                    Text(
                        post.body,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = if (expanded) Int.MAX_VALUE else 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded }
                    )
                }
                if (post.outcomeTag != null) {
                    Spacer(Modifier.height(8.dp))
                    val tint = if (post.outcomeTag == "win") BullGreen else BearRed
                    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            if (post.outcomeTag == "win") "\uD83C\uDFC6 Profited \u2014 author\u2019s tag" else "\uD83D\uDCDA Lesson learned \u2014 author\u2019s tag",
                            fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = tint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                if (post.imageCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    if (post.imageCount == 1) {
                        coil.compose.AsyncImage(
                            model = ApiClient.communityImageUrl(post.id, 0),
                            contentDescription = "Post image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenImage(0) }
                        )
                    } else {
                        val rows = (post.imageCount + 1) / 2
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            (0 until rows).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    (0 until 2).forEach { col ->
                                        val idx = row * 2 + col
                                        if (idx < post.imageCount) {
                                            coil.compose.AsyncImage(
                                                model = ApiClient.communityImageUrl(post.id, idx),
                                                contentDescription = "Post image ${idx + 1}",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(110.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .clickable { onOpenImage(idx) }
                                            )
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                if (post.allowComments) {
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
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Visibility, contentDescription = "Views", tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("${post.viewCount}", fontSize = 12.sp, color = TextMuted)
                }
                Spacer(Modifier.width(12.dp))
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        val shared = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "${post.authorName} on MarketScope AI Community:\n\n${post.body}"
                            )
                        }
                        context.startActivity(android.content.Intent.createChooser(shared, "Share post"))
                    }
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Share post", tint = TextMuted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// --- poll body ------------------------------------------------------------------------

/** The poll reshare text — shared via the Android share sheet or copied to clipboard. */
private fun buildPollShareText(post: CommunityPost, poll: CommunityPoll): String {
    val sb = StringBuilder("\uD83D\uDCCA MarketScope AI Poll\n")
    sb.append(post.body).append("\n")
    poll.options.forEach { option ->
        val count = poll.counts[option.id] ?: 0
        sb.append("\u2022 ").append(option.label).append(" — ").append(count).append(" votes\n")
    }
    sb.append("\nCast your vote on MarketScope AI")
    return sb.toString()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PollBody(post: CommunityPost, onVote: (String) -> Unit) {
    val poll = post.poll ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    Column {
        Text(
            post.body,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(10.dp))
        poll.options.forEach { option ->
            val count = poll.counts[option.id] ?: 0
            val pct = if (poll.totalVotes > 0) (count * 100f / poll.totalVotes) else 0f
            val mine = poll.myVote == option.id
            val hasVoted = poll.myVote != null

            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (mine) AccentCyan else Color(0xFFE2E8F0)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable(enabled = !mine) { onVote(option.id) }
            ) {
                Box {
                    // result fill once you've voted
                    if (hasVoted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pct / 100f)
                                .height(38.dp)
                                .background(if (mine) AccentCyan.copy(alpha = 0.14f) else Color(0xFFEDF2F7))
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(38.dp)
                            .padding(horizontal = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (mine) AccentCyan else Color(0xFFE2E8F0))
                        ) {
                            if (mine) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .align(Alignment.Center)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            option.label,
                            fontSize = 13.sp,
                            fontWeight = if (mine) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasVoted) {
                            Text(
                                "${pct.toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (mine) AccentCyan else TextMuted
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                poll.totalVotes == 0 -> "No votes yet — be the first"
                poll.totalVotes == 1 -> "1 vote"
                else -> "$poll.totalVotes votes"
            } + if (poll.myVote != null) " · tap another option to switch" else "",
            fontSize = 11.sp,
            color = TextMuted
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = {
                        val shared = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, buildPollShareText(post, poll))
                        }
                        context.startActivity(android.content.Intent.createChooser(shared, "Reshare poll"))
                    },
                    onLongClick = {
                        clipboardManager.setText(AnnotatedString(buildPollShareText(post, poll)))
                        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Share, contentDescription = "Reshare poll", tint = TextMuted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reshare poll", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
        }
    }
}

// --- comments bottom sheet ------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(
    post: CommunityPost,
    myName: String,
    myPicture: String,
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
            authorPicture = myPicture,
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
                        authorPicture = c.optString("author_picture").ifBlank { myPicture },
                        authorRole = c.optString("author_role", "user"),
                        authorIsPremium = c.optBoolean("authorIsPremium", c.optBoolean("author_is_premium", false)),
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
                loading -> CommentsSkeleton()
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
            UserAvatar(
                photoUrl = comment.authorPicture.takeIf { it.isNotBlank() },
                size = if (isReply) 26.dp else 30.dp
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comment.authorName, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    if (comment.authorIsPremium) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Verified, contentDescription = "Premium member", tint = GoldAmber, modifier = Modifier.size(13.dp))
                    }
                    if (comment.authorRole.equals("admin", true) || comment.authorRole.equals("moderator", true) || comment.authorRole.equals("mentor", true)) {
                        Spacer(Modifier.width(6.dp))
                        RoleBadge(comment.authorRole)
                    }
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
