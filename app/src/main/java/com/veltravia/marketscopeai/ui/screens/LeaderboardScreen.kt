package com.veltravia.marketscopeai.ui.screens

import com.veltravia.marketscopeai.t

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.veltravia.marketscopeai.util.optStringOrNull
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.PremiumIndigo
import com.veltravia.marketscopeai.ui.theme.TextMuted
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class StandingsEntry(
    val rank: Int,
    val name: String,
    val username: String? = null,
    val picture: String? = null,
    val email: String,
    val score: Int,
    val posts: Int,
    val comments: Int,
    val reactionsReceived: Int,
    val reactionsGiven: Int,
    val pollVotes: Int,
    val isPremium: Boolean = false
)

private fun parseStandings(json: JSONObject): List<StandingsEntry> {
    val arr = json.optJSONArray("standings") ?: return emptyList()
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        StandingsEntry(
            rank = o.optInt("rank"),
            name = o.optString("name").ifBlank { "Trader" },
            username = o.optStringOrNull("username"),
            picture = o.optStringOrNull("picture"),
            email = o.optString("email"),
            score = o.optInt("score"),
            posts = o.optInt("posts"),
            comments = o.optInt("comments"),
            reactionsReceived = o.optInt("reactionsReceived"),
            reactionsGiven = o.optInt("reactionsGiven"),
            pollVotes = o.optInt("pollVotes"),
            isPremium = o.optBoolean("isPremium", false)
        )
    }
}

/** @handle with the full name as fallback — same convention as the Community feed. */
private fun handle(name: String, username: String?): String =
    username?.takeIf { it.isNotBlank() }?.let { "@$it" } ?: name

private fun formatDate(iso: String): String = try {
    val t = Instant.parse(iso)
    DateTimeFormatter.ofPattern("EEE, MMM d").format(t.atZone(ZoneOffset.UTC))
} catch (e: Exception) { "" }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardSheet(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val token = SessionManager.sessionToken(context)
    val me = remember { SessionManager.currentUser(context) }
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var entries by remember { mutableStateOf<List<StandingsEntry>>(emptyList()) }
    var myRank by remember { mutableStateOf<Int?>(null) }
    var myScore by remember { mutableStateOf(0) }
    var lastWeekWinners by remember { mutableStateOf<List<StandingsEntry>>(emptyList()) }
    var weekStart by remember { mutableStateOf("") }
    var nextReset by remember { mutableStateOf("") }
    var proof by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showHowItWorks by remember { mutableStateOf(false) }

    fun load() {
        if (token == null) return
        loading = true
        scope.launch {
            try {
                val lb = ApiClient.fetchLeaderboard(token)
                entries = parseStandings(lb)
                myRank = if (lb.has("myRank") && !lb.isNull("myRank")) lb.optInt("myRank") else null
                myScore = lb.optInt("myScore", 0)
                weekStart = lb.optString("weekStart")
                nextReset = lb.optString("nextReset")
                proof = if (lb.has("proof") && !lb.isNull("proof")) lb.getJSONObject("proof") else null
                val winners = lb.optJSONArray("lastWeekWinners") ?: org.json.JSONArray()
                lastWeekWinners = (0 until winners.length()).map { i ->
                    val o = winners.getJSONObject(i)
                    StandingsEntry(
                        rank = o.optInt("rank"),
                        name = o.optString("name").ifBlank { "Trader" },
                        email = o.optString("email"),
                        score = o.optInt("score"),
                        username = o.optStringOrNull("username"),
                        picture = o.optStringOrNull("picture"),
                        isPremium = o.optBoolean("isPremium", false),
                        posts = 0, comments = 0, reactionsReceived = 0, reactionsGiven = 0, pollVotes = 0
                    )
                }
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Could not load the standings"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    // Reference-style rank badge: gold / silver / bronze for the top 3,
    // a plain dark circle for everyone else — same coloring FxLens uses.
    fun rankBadgeColor(rank: Int): Color = when (rank) {
        1 -> Color(0xFFF59E0B)
        2 -> Color(0xFF94A3B8)
        3 -> Color(0xFFEA580C)
        else -> Color(0xFF1E293B)
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(max = 640.dp)
        ) {
            // Header: title + "This week · live standings" subtitle on the
            // left, a "How it works" info toggle on the right — the same
            // split FxLens uses in its Top Contributors sheet.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t("Top Contributors"),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        if (weekStart.isNotBlank()) "${t("This week")} \u00B7 ${t("live standings")}" else t("Weekly competition"),
                        fontSize = 12.sp, color = TextMuted
                    )
                }
                TextButton(onClick = { showHowItWorks = !showHowItWorks }) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(t("How it works"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
                }
                IconButton(onClick = { load() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextMuted, modifier = Modifier.size(17.dp))
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                if (showHowItWorks) {
                    Spacer(Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F7)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(t("Standings reset every Monday and everyone starts at zero. Earn points from real activity:"),
                                fontSize = 12.sp, lineHeight = 17.sp, color = TextMuted
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "\u2022 Post \u2014 5 points\n\u2022 Comment \u2014 2 points\n\u2022 Reaction given or received \u2014 1 point\n\u2022 Poll vote \u2014 1 point",
                                fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Rewards: last week's top 5 carry the \uD83C\uDFC6 Top Contributor badge by their name for the whole week.",
                                fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                error?.let { e ->
                    Surface(
                        color = Color(0xFFFFF7ED),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(t("We couldn't load the standings — tap refresh to try again."),
                            fontSize = 12.sp, color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (loading) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentCyan)
                    }
                } else {
                    // Current standings — the main list, styled like FxLens's
                    // Top Contributors rows: medal-colored rank badge, real
                    // avatar, handle + a small activity line, a pts pill.
                    if (entries.isEmpty() && error == null) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(40.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(t("A fresh week just started"),
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(t("No standings yet. Post, comment and react to climb."),
                                fontSize = 12.sp, color = TextMuted
                            )
                        }
                    } else {
                        entries.forEach { e ->
                            val isMe = me?.email != null && e.email.equals(me.email, ignoreCase = true)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isMe) AccentCyan.copy(alpha = 0.06f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(vertical = 9.dp, horizontal = 4.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(rankBadgeColor(e.rank))
                                ) {
                                    Text("${e.rank}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Spacer(Modifier.width(10.dp))
                                com.veltravia.marketscopeai.ui.UserAvatar(
                                    photoUrl = ApiClient.resolveAvatarUrl(e.picture),
                                    size = 36.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            handle(e.name, e.username) + if (isMe) " (you)" else "",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (e.isPremium) {
                                            Spacer(Modifier.width(4.dp))
                                            Icon(Icons.Filled.Verified, contentDescription = "Premium member", tint = PremiumIndigo, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    Text(
                                        "${e.posts} posts \u00B7 ${e.comments} comments \u00B7 ${e.reactionsReceived} reactions",
                                        fontSize = 10.5.sp, color = TextMuted,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = BullGreen.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        "${e.score} pts",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BullGreen,
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // my standing chip
                    if (myRank != null) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = AccentCyan.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "You're ranked #$myRank this week with $myScore points — the climb continues.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = AccentCyan,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    } else if (entries.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(t("Post, comment and react to earn points and appear here."),
                            fontSize = 11.5.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // Featured proof of the week (most-reacted image post this week)
                    proof?.let { pr ->
                        Spacer(Modifier.height(14.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "Weekly Proof \uD83D\uDCC8 \u2014 featured trader proof from the week",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(Modifier.height(8.dp))
                                coil.compose.AsyncImage(
                                    model = ApiClient.communityImageUrl(pr.optString("postId"), 0),
                                    contentDescription = "Featured proof",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        pr.optString("authorName").ifBlank { "Trader" },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        if (pr.optInt("weekReactions", 0) == 1) "1 reaction this week"
                                        else "${pr.optInt("weekReactions", 0)} reactions this week",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                if (pr.optString("body").isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        pr.optString("body"),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        color = TextMuted,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    // Last week's winners
                    if (lastWeekWinners.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Text(t("Last week's top 5"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(8.dp))
                        lastWeekWinners.forEach { w ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                com.veltravia.marketscopeai.ui.UserAvatar(
                                    photoUrl = ApiClient.resolveAvatarUrl(w.picture),
                                    size = 26.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    handle(w.name, w.username),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                if (w.isPremium) {
                                    Icon(Icons.Filled.Verified, contentDescription = "Premium member", tint = PremiumIndigo, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                }
                                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("${w.score} pts", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                // The exact footer line FxLens ends its sheet with.
                Text(
                    t("Standings reset each week and are reviewed before rewards are given."),
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}
