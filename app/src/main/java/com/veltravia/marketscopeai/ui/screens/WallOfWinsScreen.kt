package com.veltravia.marketscopeai.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.UserAvatar
import com.veltravia.marketscopeai.ui.components.GradientPrimaryButton
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wall of Wins — the full wall of approved member win proofs, fed by the same
 * mentor-reviewed "Share Your Win" pipeline that powers the Signals strip.
 * Everything here is real: every proof is attached to a settled, profitable
 * signal and passed admin review before it can appear.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallOfWinsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var stats by remember { mutableStateOf<JSONObject?>(null) }
    val wins = remember { mutableStateListOf<JSONObject>() }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    suspend fun loadPage(offset: Int) {
        val token = SessionManager.sessionToken(context)
        if (token.isNullOrBlank()) { loadError = "Please sign in again."; loading = false; loadingMore = false; return }
        runCatching { ApiClient.fetchWinsWall(token, offset) }
            .onSuccess { json ->
                stats = json.optJSONObject("stats")
                val page = json.optJSONArray("wins") ?: return@onSuccess
                for (i in 0 until page.length()) page.optJSONObject(i)?.let { wins.add(it) }
                hasMore = json.optBoolean("hasMore", false)
                loadError = null
            }
            .onFailure { if (offset == 0) loadError = "Could not load the wall. Check your connection and try again." }
        loading = false
        loadingMore = false
    }

    LaunchedEffect(retryKey) { loadPage(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Wall of Wins", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Real wins shared by MarketScope AI traders — every proof is tied to a settled signal and passed review.",
            fontSize = 12.sp, color = TextMuted
        )
        Spacer(Modifier.height(14.dp))

        // Wall stats — real aggregates from the same query as the wall itself.
        val st = stats
        if (st != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WallStatTile(st.optInt("total", 0).toString(), "Wins shared", Modifier.weight(1f))
                WallStatTile(st.optInt("thisWeek", 0).toString(), "This week", Modifier.weight(1f))
                WallStatTile(st.optInt("traders", 0).toString(), "Traders sharing", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
        }

        when {
            loading -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan)
            }
            loadError != null -> Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadError!!, fontSize = 13.sp, color = BearRed)
                Spacer(Modifier.height(10.dp))
                GradientPrimaryButton(
                    text = "Retry",
                    enabled = true,
                    onClick = { loading = true; loadError = null; wins.clear(); retryKey++ },
                    height = 44.dp,
                    showArrow = false
                )
            }
            wins.isEmpty() -> Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BullGreen, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(10.dp))
                Text("No wins shared yet", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "When a signal closes in profit, take it and share your win — the best proofs land here.",
                    fontSize = 12.sp, color = TextMuted
                )
            }
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(wins) { w -> WallWinCard(w) { detail = w } }
                    if (hasMore) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                            if (loadingMore) CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                // Infinite scroll — load the next page only when the spacer
                // item at the end actually scrolls into view.
                val endVisible by androidx.compose.runtime.remember {
                    androidx.compose.runtime.derivedStateOf {
                        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        hasMore && last >= wins.size
                    }
                }
                LaunchedEffect(endVisible) {
                    if (endVisible && !loadingMore && wins.isNotEmpty()) {
                        loadingMore = true
                        loadPage(wins.size)
                    }
                }
            }
        }
    }

    // Full-proof detail sheet.
    val d = detail
    if (d != null) {
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            WinDetailSheet(
                win = d,
                onReshare = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, buildWinShareText(d))
                    }
                    context.startActivity(android.content.Intent.createChooser(send, "Reshare win"))
                },
                onCopy = {
                    clipboardManager.setText(AnnotatedString(buildWinShareText(d)))
                    android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

/** One wall card: proof screenshot (or instrument tile), instrument + outcome, author. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WallWinCard(w: JSONObject, onClick: () -> Unit) {
    val isLong = w.optString("direction", "long").equals("long", ignoreCase = true)
    val hasImage = w.optBoolean("hasImage", false)
    val exitPrice = w.optDouble("exitPrice", Double.NaN)
    val instrument = w.optString("instrument", "")
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceLight)
            .border(1.dp, BullGreen.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick)
            .padding(10.dp)
    ) {
        if (hasImage) {
            AsyncImage(
                model = ApiClient.signalTestimonialImageUrl(w.optString("id", "")),
                contentDescription = "Win proof screenshot",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
            )
            Spacer(Modifier.height(8.dp))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(BullGreen.copy(alpha = 0.12f), AccentCyan.copy(alpha = 0.10f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(instrument, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = BullGreen, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(5.dp))
            Text(instrument, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(4.dp))
            Text(if (isLong) "LONG" else "SHORT", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = if (isLong) BullGreen else BearRed)
        }
        if (!exitPrice.isNaN()) {
            Spacer(Modifier.height(3.dp))
            Text("Closed at $exitPrice", fontSize = 10.sp, color = TextSecondary)
        }
        val comment = w.optString("comment", "")
        if (comment.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(comment, fontSize = 11.sp, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(photoUrl = ApiClient.resolveAvatarUrl(w.optString("avatarUrl", "")), size = 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(w.optString("authorName", "Trader"), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (w.optBoolean("authorIsPremium", false)) {
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Filled.Verified, contentDescription = "Premium member", tint = GoldAmber, modifier = Modifier.size(11.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(winDate(w.optString("createdAt", "")), fontSize = 9.sp, color = TextMuted)
        }
    }
}

/** The full proof: image at size, full comment, author, reshare (tap = sheet, long-press = copy). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WinDetailSheet(win: JSONObject, onReshare: () -> Unit, onCopy: () -> Unit) {
    val isLong = win.optString("direction", "long").equals("long", ignoreCase = true)
    val hasImage = win.optBoolean("hasImage", false)
    val exitPrice = win.optDouble("exitPrice", Double.NaN)
    val comment = win.optString("comment", "")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(photoUrl = ApiClient.resolveAvatarUrl(win.optString("avatarUrl", "")), size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(win.optString("authorName", "Trader"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    if (win.optBoolean("authorIsPremium", false)) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Verified, contentDescription = "Premium member", tint = GoldAmber, modifier = Modifier.size(14.dp))
                    }
                }
                Text(win.optString("instrument", "") + " · " + (if (isLong) "LONG" else "SHORT"), fontSize = 11.sp, color = if (isLong) BullGreen else BearRed, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (hasImage) {
            AsyncImage(
                model = ApiClient.signalTestimonialImageUrl(win.optString("id", "")),
                contentDescription = "Win proof screenshot",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceLight)
            )
            Spacer(Modifier.height(12.dp))
        }
        if (!exitPrice.isNaN()) {
            Text(
                "Signal closed at $exitPrice",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
        }
        if (comment.isNotBlank()) {
            Text(comment, fontSize = 13.5.sp, color = TextPrimary, lineHeight = 19.sp)
            Spacer(Modifier.height(14.dp))
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(AccentCyan, AccentViolet)))
                .combinedClickable(onClick = onReshare, onLongClick = onCopy)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reshare this win", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Spacer(Modifier.height(6.dp))
        Text("Long-press to copy instead", fontSize = 10.sp, color = TextMuted)
    }
}

/** "Sep 8" from the backend's ISO timestamp, in the device's local time. */
private fun winDate(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        val parsed = java.time.Instant.parse(iso)
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date.from(parsed))
    }.getOrDefault("")
}

/** The share text for a wall win — same text for the share sheet and the clipboard. */
private fun buildWinShareText(w: JSONObject): String {
    val isLong = w.optString("direction", "long").equals("long", ignoreCase = true)
    val sb = StringBuilder("✅ MarketScope AI — Trader win\n")
    sb.append(w.optString("instrument", "")).append(" (").append(if (isLong) "LONG" else "SHORT").append(")\n")
    val exitPrice = w.optDouble("exitPrice", Double.NaN)
    if (!exitPrice.isNaN()) sb.append("Closed at ").append(exitPrice).append("\n")
    val comment = w.optString("comment", "").trim()
    if (comment.isNotBlank()) sb.append("\n\"").append(comment).append("\"\n")
    sb.append("\nvia MarketScope AI")
    return sb.toString()
}

/** Small stat tile for the wall header. */
@Composable
private fun WallStatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLight)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}
