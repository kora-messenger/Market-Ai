package com.veltravia.marketscopeai.ui.screens

import com.veltravia.marketscopeai.ui.roleStyle

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
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
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    val authorUsername: String? = null,
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
    val viewCount: Int = 0,
    val linkPreview: LinkPreview? = null,
    val isNew: Boolean = false
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
    val authorUsername: String? = null,
    val authorIsPremium: Boolean = false,
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
    val authorUsername: String? = null,
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
            authorUsername = p.optString("authorUsername").ifBlank { null },
            authorEmail = p.optString("authorEmail"),
            authorPicture = ApiClient.resolveAvatarUrl(p.optString("authorPicture")) ?: "",
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
            linkPreview = parseLinkPreview(p),
            isNew = p.optBoolean("isNew", false),
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
            authorUsername = c.optString("author_username").ifBlank { null },
            authorPicture = ApiClient.resolveAvatarUrl(c.optString("author_picture")) ?: "",
            authorRole = c.optString("author_role", "user"),
            authorIsPremium = c.optBoolean("authorIsPremium", c.optBoolean("author_is_premium", false)),
            body = c.optString("body"),
            createdAt = c.optString("created_at")
        )
    }

/** Author identity on the Community screen is the @handle when the user has
 *  set one; the full name is only a fallback for users without a handle. */
private fun displayHandle(name: String, username: String?): String =
    if (username.isNullOrBlank()) name else "@$username"

/** 1234 -> "1.2K", 2500000 -> "2.5M" — the compact view-count format. */
private fun compactCount(n: Int): String = when {
    n >= 1_000_000 -> if (n % 1_000_000 == 0) "${n / 1_000_000}M"
        else String.format(java.util.Locale.US, "%.1fM", n / 1_000_000f)
    n >= 1000 -> if (n % 1000 == 0) "${n / 1000}K"
        else String.format(java.util.Locale.US, "%.1fK", n / 1000f)
    else -> n.toString()
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

/**
 * "Today" / "Yesterday" / "September 10" day divider shown once above the
 * first post of each calendar day (local time), FxLens-style feed grouping.
 */
private fun dayGroupLabel(iso: String): String = try {
    val zone = java.time.ZoneId.systemDefault()
    val day = Instant.parse(iso).atZone(zone).toLocalDate()
    val today = java.time.LocalDate.now(zone)
    when {
        day.isEqual(today) -> "Today"
        day.isEqual(today.minusDays(1)) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("MMMM d").format(day)
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
            authorUsername = o.optString("authorUsername").ifBlank { null },
            authorIsPremium = o.optBoolean("authorIsPremium", false),
            body = o.optString("body"),
            imageCount = o.optInt("imageCount", 0),
            outcomeTag = o.optString("outcomeTag").takeIf { it.isNotBlank() && it != "null" },
            weekReactions = o.optInt("weekReactions", 0)
        )
    }
}

// --- screen -------------------------------------------------------------------------

@Composable
fun CommunityScreen(
    onOpenLeaderboard: () -> Unit = {},
    onOpenDms: () -> Unit = {},
    onOpenDmChat: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = SessionManager.sessionToken(context)
    val me = remember { SessionManager.currentUser(context) }

    var posts by remember { mutableStateOf<List<CommunityPost>>(emptyList()) }
    var totalPosts by remember { mutableStateOf(0) }
    var memberCount by remember { mutableStateOf(-1) }
    var dmUnread by remember { mutableStateOf(0) }
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
    var deleteTarget by remember { mutableStateOf<CommunityPost?>(null) }
    var viewerPost by remember { mutableStateOf<CommunityPost?>(null) }
    var viewerIndex by remember { mutableStateOf(0) }
    var isAdmin by remember { mutableStateOf(false) }
    var canCompose by remember { mutableStateOf(false) }
    var composerOutcomeTag by remember { mutableStateOf<String?>(null) }
    var composerOpen by remember { mutableStateOf(false) }
    var composeFabOpen by remember { mutableStateOf(false) }

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
        // Only the very first cold load (empty feed) gets the deliberate
        // skeleton treatment: on a slow connection the shimmer just stays up
        // naturally until real data arrives; on a fast connection the fetch
        // can finish in well under a second, which reads as an ugly flash —
        // so we hold the skeleton up to a smooth ~2s minimum before reveal.
        val isFirstLoad = reset && posts.isEmpty()
        if (reset) loading = true else loadingMore = true
        val startedAt = System.currentTimeMillis()
        scope.launch {
            try {
                val offset = if (reset) 0 else posts.size
                val feed = ApiClient.fetchCommunityFeed(token, offset = offset, limit = 20)
                val page = parseFeed(feed)
                hasMore = feed.optBoolean("hasMore", false)
                totalPosts = feed.optInt("total", 0)
                if (isFirstLoad) {
                    val elapsed = System.currentTimeMillis() - startedAt
                    val minDurationMs = 2000L
                    if (elapsed < minDurationMs) delay(minDurationMs - elapsed)
                }
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

    fun deletePost(post: CommunityPost) {
        val tk = token ?: return
        // Optimistic removal — the post disappears instantly; if the call
        // fails we reload the feed so it honestly comes back.
        posts = posts.filter { it.id != post.id }
        pinnedPosts = pinnedPosts.filter { it.id != post.id }
        totalPosts = (totalPosts - 1).coerceAtLeast(0)
        if (openPost?.id == post.id) openPost = null
        if (viewerPost?.id == post.id) viewerPost = null
        scope.launch {
            try {
                ApiClient.deleteCommunityPost(tk, post.id)
            } catch (e: Exception) {
                error = e.message ?: "Could not delete the post"
                load(reset = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        // DM unread badge for the Messages button (never blocks the feed load).
        val dmTk = token
        if (dmTk != null) {
            scope.launch {
                try {
                    val resp = ApiClient.fetchDmThreads(dmTk)
                    dmUnread = resp.optInt("unreadTotal", 0)
                } catch (_e: Exception) { dmUnread = 0 }
            }
        }

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
                    canCompose = access.optBoolean("canCompose", false)
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

    // Opens (or reuses) a private DM thread with a roled post author.
    fun openDm(post: CommunityPost) {
        if (post.authorEmail.isBlank()) return
        val tk = token ?: return
        scope.launch {
            try {
                val resp = ApiClient.createDmThread(tk, post.authorEmail)
                val threadId = resp.optJSONObject("thread")?.optString("id")
                if (!threadId.isNullOrBlank() && threadId != "null") onOpenDmChat(threadId)
                else android.util.Log.e("CommunityDM", resp.toString())
            } catch (e: Exception) {
                android.util.Log.e("CommunityDM", "openDm failed", e)
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
                composerOpen = false
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
                    // Messages inbox — private DM threads with team & mentors.
                    IconButton(onClick = onOpenDms, modifier = Modifier.size(30.dp)) {
                        Box {
                            Icon(Icons.Filled.Email, contentDescription = "Messages", tint = TextMuted, modifier = Modifier.size(20.dp))
                            if (dmUnread > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-3).dp)
                                        .background(AccentViolet, CircleShape)
                                        .border(1.5.dp, Color.White, CircleShape)
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        if (dmUnread > 99) "99+" else "$dmUnread",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { load(reset = true) }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextMuted, modifier = Modifier.size(19.dp))
                    }
                }
            }

            val postComposerBlock: @Composable () -> Unit = {
                // Team/mentor members compose via the floating compose button
                // (bottom-right); only non-composers get the quiet inline note.
                if (!canCompose) MemberComposerNote()
            }

            when {
                loading && posts.isEmpty() -> CommunityFeedSkeleton()
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentCyan)
                }
                posts.isEmpty() && error == null -> {
                    val joined = SessionManager.communityJoined(context)
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (joined) {
                            item {
                                WeeklyCompetitionCard(onOpen = onOpenLeaderboard)
                            }
                            item {
                                postComposerBlock()
                            }
                        }
                        item {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = if (joined) 24.dp else 80.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.Groups, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(14.dp))
                                Text("No posts yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (joined) "Be the first to share a win, a setup, or a thought."
                                    else "Posts from traders will appear here.",
                                    fontSize = 13.sp, color = TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
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
                            postComposerBlock()
                        }
                        itemsIndexed(posts, key = { _, post -> post.id }) { idx, post ->
                            val showDayHeader = idx == 0 ||
                                dayGroupLabel(post.createdAt) != dayGroupLabel(posts[idx - 1].createdAt)
                            Column {
                                if (showDayHeader) {
                                    DayGroupHeader(dayGroupLabel(post.createdAt))
                                }
                                PostCard(
                                    post = post,
                                    isAdmin = isAdmin,
                                    canDelete = isAdmin || post.authorEmail.equals(
                                        SessionManager.currentUser(context)?.email, ignoreCase = true
                                    ),
                                    onDelete = { deleteTarget = post },
                                    onReact = { emoji -> toggleReaction(post, emoji) },
                                    onVote = { optionId -> votePoll(post, optionId) },
                                    onOpenComments = { openPost = post },
                                    onPin = { togglePin(post) },
                                    onOpenImage = { idx2 ->
                                        viewerPost = post
                                        viewerIndex = idx2
                                    },
                                    onRegisterView = { registerView(post.id) },
                                    onMessage = { openDm(post) }
                                )
                            }
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
                            "We couldn't reach the community — tap refresh to try again.",
                            fontSize = 12.sp,
                            color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Floating compose button — the entry into the publish flow for
        // team/mentor members. Lives inside the root Box so it overlays the
        // feed (last child draws on top).
        if (canCompose) {
            ComposeFab(
                open = composeFabOpen,
                onToggle = { composeFabOpen = !composeFabOpen },
                onChoose = { chosen ->
                    composeFabOpen = false
                    composerMode = chosen
                    composerOpen = true
                }
            )
        }
    }

    // Live link card: paste a URL in the composer and the preview appears
    // before publishing — the same card readers will see.
    val composerLinkPreview = if (composerOpen) rememberComposerLinkPreview(composerText.text) else null

    if (composerOpen) {
        PublishComposerModal(
            mode = composerMode,
            linkPreview = composerLinkPreview,
            onDismiss = { composerOpen = false },
            text = composerText,
            onTextChange = { composerText = it },
            pollOptions = pollOptions,
            onChangePollOption = { index, value ->
                if (index in pollOptions.indices) pollOptions[index] = value
            },
            onAddPollOption = { if (pollOptions.size < 6) pollOptions.add(TextFieldValue("")) },
            onRemovePollOption = { index ->
                if (pollOptions.size > 2 && index in pollOptions.indices) pollOptions.removeAt(index)
            },
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

    deleteTarget?.let { post ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this post?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The post and its comments will be removed permanently. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    deletePost(post)
                }) {
                    Text("Delete", fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        )
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
            myUsername = me?.username,
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
                            contentDescription = "Weekly proof from ${displayHandle(proof.authorName, proof.authorUsername)}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        )
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    displayHandle(proof.authorName, proof.authorUsername), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (proof.authorIsPremium) {
                                    Spacer(Modifier.width(3.dp))
                                    Icon(Icons.Filled.Verified, contentDescription = "Premium member", tint = GoldAmber, modifier = Modifier.size(12.dp))
                                }
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

/** Shown instead of the composer to regular members: posting is a
 *  team/mentor privilege — reading, reacting and commenting stay open. */
@Composable
private fun MemberComposerNote() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLight)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Icon(
            Icons.Filled.Groups,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Posting is reserved for the MarketScope AI team and mentors. You can still react and join the conversation in the comments.",
            fontSize = 12.sp,
            color = TextMuted,
            lineHeight = 17.sp
        )
    }
}

// --- post card ----------------------------------------------------------------------

/** Centered "Today" / "Yesterday" / date pill shown above the first post of a new day. */
@Composable
private fun DayGroupHeader(label: String) {
    if (label.isBlank()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(20.dp)) {
            Text(
                label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun PostCard(
    post: CommunityPost,
    isAdmin: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onReact: (String) -> Unit,
    onVote: (String) -> Unit,
    onOpenComments: () -> Unit,
    onPin: () -> Unit,
    onOpenImage: (Int) -> Unit,
    onRegisterView: () -> Unit,
    onMessage: () -> Unit
) {
    var showReactionRow by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val roleMeta = roleStyle(post.authorRole, post.isTeam)
    val isMentorAuthor = post.authorRole.equals("mentor", true)
    // Registers a real, deduped view once per composition (per session per post).
    LaunchedEffect(post.id) { onRegisterView() }
    // Link card: server-stored preview when present; older posts resolve
    // lazily through the cached endpoint on first render.
    val linkPreview = rememberResolvedLinkPreview(post.id, post.body, post.linkPreview)

    // --- message row: avatar anchored left, everything else a chat column ---
    Row(Modifier.fillMaxWidth()) {
        Box(
            modifier = roleMeta?.let {
                Modifier
                    .border(2.dp, it.ring, CircleShape)
                    .padding(2.dp)
            } ?: Modifier
        ) {
            UserAvatar(photoUrl = ApiClient.resolveAvatarUrl(post.authorPicture), size = 36.dp)
        }
        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            // Sender line — like any messaging app: handle, badges, NEW marker.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayHandle(post.authorName, post.authorUsername),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                if (roleMeta != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, contentDescription = "Verified author", tint = roleMeta.text, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    RoleBadge(post.authorRole, post.isTeam)
                } else if (post.authorIsPremium) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Verified, contentDescription = "Premium member", tint = GoldAmber, modifier = Modifier.size(13.dp))
                }
                if (post.isPinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.PushPin, contentDescription = "Pinned", tint = Color(0xFF0F766E), modifier = Modifier.size(11.dp))
                }
                if (post.isTopContributor) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.EmojiEvents, contentDescription = "Top contributor", tint = Color(0xFFB45309), modifier = Modifier.size(13.dp))
                }
                if (post.isNew) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = AccentViolet.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            "NEW",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = AccentViolet,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // --- the bubble: asymmetric corners with a tight top-left
            // where it meets the avatar — the messaging-feed silhouette.
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 6.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                border = BorderStroke(1.dp, Color(0xFFE7ECF3)),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (post.poll != null) {
                        PollBody(post, onVote)
                    } else {
                        if (post.body.isNotBlank()) {
                            // Linkified body: URLs open the browser; tapping
                            // anything else still expands long posts.
                            LinkText(
                                post.body,
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = if (expanded) Int.MAX_VALUE else 8,
                                overflow = TextOverflow.Ellipsis,
                                onNonLinkTap = { expanded = !expanded }
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
                        if (linkPreview != null) {
                            Spacer(Modifier.height(8.dp))
                            LinkPreviewCard(linkPreview)
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

                    // Message time inside the bubble, bottom-right — the
                    // messaging-feed signature detail.
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (roleMeta != null) {
                            // Private-message pill for roled authors, in their
                            // own role colors, beside the timestamp.
                            Surface(
                                color = roleMeta.bg,
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, roleMeta.ring),
                                onClick = onMessage
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(Icons.Filled.Email, contentDescription = null, tint = roleMeta.text, modifier = Modifier.size(11.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text("Message", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = roleMeta.text)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(relativeTime(post.createdAt), fontSize = 10.sp, color = Color(0xFF94A3B8))
                    }
                }
            }

            // --- under-bubble engagement strip: reactions, comments, then the
            // quieter share / views / moderation tools anchored right.
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .horizontalScroll(rememberScrollState())
                ) {
                    post.reactions.take(4).forEach { reaction ->
                        Surface(
                            color = if (reaction.mine) AccentCyan.copy(alpha = 0.12f) else Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(50),
                            onClick = { onReact(reaction.emoji) }
                        ) {
                            Text(
                                "${reaction.emoji} ${reaction.count}",
                                fontSize = 11.5.sp,
                                color = if (reaction.mine) AccentCyan else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        color = if (showReactionRow) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, if (showReactionRow) Color(0xFF5EEAD4) else Color(0xFFEEF1F5)),
                        onClick = { showReactionRow = !showReactionRow },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = if (showReactionRow) "Hide reactions" else "Add reaction",
                                tint = if (showReactionRow) Color(0xFF0F766E) else Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    if (post.allowComments) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(50),
                            onClick = onOpenComments
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = Color(0xFF0F766E), modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Comments \u00B7 ${post.commentCount}", fontSize = 11.5.sp, color = Color(0xFF475569), maxLines = 1, softWrap = false)
                            }
                        }
                    } else if (isMentorAuthor || roleMeta != null) {
                        // Roled posts with comments off carry a quiet label —
                        // "Mentor post" for mentors, "Team post" for the desk.
                        Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(50)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isMentorAuthor) "Mentor post" else "Team post",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    onClick = {
                        val shared = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                "${displayHandle(post.authorName, post.authorUsername)} on MarketScope AI Community:\n\n${post.body}"
                            )
                        }
                        context.startActivity(android.content.Intent.createChooser(shared, "Share post"))
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Share, contentDescription = "Share post", tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.width(4.dp))
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = "Views", tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(compactCount(post.viewCount), fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
                if (canDelete) {
                    Spacer(Modifier.width(2.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete post",
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                if (isAdmin) {
                    Spacer(Modifier.width(2.dp))
                    IconButton(onClick = onPin, modifier = Modifier.size(26.dp)) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = if (post.isPinned) "Unpin post" else "Pin post",
                            tint = if (post.isPinned) AccentViolet else TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Reaction picker — a floating rounded panel with a soft border
            // that springs in; tapping an emoji reacts and closes it.
            if (showReactionRow) {
                Spacer(Modifier.height(8.dp))
                val pickerScale by animateFloatAsState(
                    targetValue = if (showReactionRow) 1f else 0.85f,
                    animationSpec = spring(dampingRatio = 0.72f),
                    label = "reactionPickerScale"
                )
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFFEEF1F5)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = pickerScale
                        scaleY = pickerScale
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        REACTION_SET.forEach { emoji ->
                            Text(
                                emoji,
                                fontSize = 20.sp,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        onReact(emoji)
                                        showReactionRow = false
                                    }
                                    .padding(6.dp)
                            )
                        }
                    }
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
    myUsername: String?,
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
            authorUsername = myUsername,
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
                        authorUsername = c.optString("author_username").ifBlank { myUsername },
                        authorPicture = ApiClient.resolveAvatarUrl(c.optString("author_picture")) ?: myPicture,
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
                // No imePadding(): activity uses adjustResize (non-edge-to-edge)
                // so the OS already resizes around the keyboard; stacking
                // Compose's imePadding() double-counts and pushes content off.
                .navigationBarsPadding()
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
                Text("We couldn't load the comments — tap refresh to try again.", fontSize = 11.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(vertical = 4.dp))
            }

            replyTo?.let { target ->
                Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Replying to ${displayHandle(target.authorName, target.authorUsername)}", fontSize = 11.sp, color = TextMuted, modifier = Modifier.weight(1f))
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
                    placeholder = {
                        Text(
                            if (replyTo != null) "Reply to ${displayHandle(replyTo?.authorName ?: "", replyTo?.authorUsername)}…"
                            else "Add a comment…",
                            fontSize = 13.sp, color = TextMuted
                        )
                    },
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
    // Swipe-right-to-reply: drag the comment right to reveal a reply arrow
    // (WhatsApp-style). Past the trigger distance the gesture commits and
    // the composer jumps into reply mode for this comment.
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxDragPx = with(density) { 72.dp.toPx() }
    val triggerPx = with(density) { 46.dp.toPx() }
    val offset = remember(comment.id) { Animatable(0f) }

    Box(Modifier.fillMaxWidth()) {
        // Reply arrow revealed behind the row as it slides right.
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .matchParentSize()
                .padding(start = 10.dp)
                .graphicsLayer {
                    alpha = (offset.value / triggerPx).coerceIn(0f, 1f)
                }
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Swipe to reply",
                tint = AccentCyan,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .then(
                    if (comment.pending) Modifier
                    else Modifier.pointerInput(comment.id) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                scope.launch {
                                    offset.snapTo(
                                        (offset.value + amount).coerceIn(0f, maxDragPx)
                                    )
                                }
                            },
                            onDragEnd = {
                                if (offset.value >= triggerPx) {
                                    onReply()
                                }
                                scope.launch {
                                    offset.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    offset.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        )
                    }
                )
                .padding(vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                UserAvatar(
                    photoUrl = ApiClient.resolveAvatarUrl(comment.authorPicture),
                    size = if (isReply) 26.dp else 30.dp
                )
                Spacer(Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(displayHandle(comment.authorName, comment.authorUsername), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
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
                LinkText(comment.body, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground)
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
}
