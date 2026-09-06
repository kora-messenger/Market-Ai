package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.TextMuted
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class StandingsEntry(
    val rank: Int,
    val name: String,
    val email: String,
    val score: Int,
    val posts: Int,
    val comments: Int,
    val reactionsReceived: Int,
    val reactionsGiven: Int,
    val pollVotes: Int
)

private fun parseStandings(json: JSONObject): List<StandingsEntry> {
    val arr = json.optJSONArray("standings") ?: return emptyList()
    return (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        StandingsEntry(
            rank = o.optInt("rank"),
            name = o.optString("name").ifBlank { "Trader" },
            email = o.optString("email"),
            score = o.optInt("score"),
            posts = o.optInt("posts"),
            comments = o.optInt("comments"),
            reactionsReceived = o.optInt("reactionsReceived"),
            reactionsGiven = o.optInt("reactionsGiven"),
            pollVotes = o.optInt("pollVotes")
        )
    }
}

private fun formatDate(iso: String): String = try {
    val t = Instant.parse(iso)
    DateTimeFormatter.ofPattern("EEE, MMM d").format(t.atZone(ZoneOffset.UTC))
} catch (e: Exception) { "" }

@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val token = SessionManager.sessionToken(context)
    val me = remember { SessionManager.currentUser(context) }

    var entries by remember { mutableStateOf<List<StandingsEntry>>(emptyList()) }
    var myRank by remember { mutableStateOf<Int?>(null) }
    var myScore by remember { mutableStateOf(0) }
    var lastWeekWinners by remember { mutableStateOf<List<StandingsEntry>>(emptyList()) }
    var weekStart by remember { mutableStateOf("") }
    var nextReset by remember { mutableStateOf("") }
    var proof by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Top Contributors",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    if (nextReset.isNotBlank()) "Resets ${formatDate(nextReset)}" else "Weekly competition",
                    fontSize = 11.sp, color = TextMuted
                )
            }
            IconButton(onClick = { load() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = TextMuted)
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
            else -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                error?.let { e ->
                    Surface(
                        color = Color(0xFFFFF7ED),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Couldn't load standings: $e — tap refresh to retry.",
                            fontSize = 12.sp, color = Color(0xFFB45309),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // How it works
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F7)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("How it works", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Standings reset every Monday and everyone starts at zero. Earn points from real activity:",
                            fontSize = 12.sp, lineHeight = 17.sp, color = TextMuted
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "• Post — 5 points\n• Comment — 2 points\n• Reaction given or received — 1 point\n• Poll vote — 1 point",
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

                // Featured proof of the week (most-reacted image post this week)
                proof?.let { pr ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Weekly Proof \uD83D\uDCC8 — featured trader proof from the week",
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
                    Spacer(Modifier.height(12.dp))
                }

                // Last week's winners
                if (lastWeekWinners.isNotEmpty()) {
                    Text("Last week's top 5", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(8.dp))
                    lastWeekWinners.forEach { w ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFEF3C7))
                            ) {
                                Text(
                                    "${w.rank}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                w.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${w.score} pts", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // Current standings
                Text(
                    if (weekStart.isNotBlank()) "This week — started ${formatDate(weekStart)}" else "This week",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))

                if (entries.isEmpty() && error == null) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "A fresh week just started",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "No standings yet. Post, comment and react to climb.",
                            fontSize = 12.sp, color = TextMuted
                        )
                    }
                } else {
                    entries.forEachIndexed { index, e ->
                        val isMe = me?.email != null && e.email.equals(me.email, ignoreCase = true)
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMe) AccentCyan.copy(alpha = 0.08f) else Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                            when (e.rank) {
                                1 -> Color(0xFFFEF3C7)
                                2 -> Color(0xFFE8E8EC)
                                3 -> Color(0xFFF3E0D3)
                                else -> Color(0xFFF1F5F9)
                            }
                                        )
                                ) {
                                    Text(
                                        "${e.rank}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (e.rank) {
                                            1 -> Color(0xFFB45309)
                                            2, 3 -> MaterialTheme.colorScheme.onBackground
                                            else -> TextMuted
                                        }
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            e.name + if (isMe) " (you)" else "",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    Text(
                                        "${e.posts} posts · ${e.comments} comments · ${e.reactionsReceived} reactions received",
                                        fontSize = 10.5.sp, color = TextMuted
                                    )
                                }
                                Text(
                                    "${e.score} pts",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (e.rank <= 3) AccentViolet else MaterialTheme.colorScheme.onBackground
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
                            "You're #$myRank this week with $myScore points — keep going!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentCyan,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                } else if (entries.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Post, comment and react to earn points and appear here.",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
