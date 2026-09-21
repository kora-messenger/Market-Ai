package com.veltravia.marketscopeai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import kotlinx.coroutines.delay

// ===========================================================================
// Link handling for the community feed — the "paste a link, get a preview
// card" behavior. URLs inside post and comment text render as tappable
// violet links; the first URL in a post also renders as an OpenGraph card
// (image, title, domain) scraped and cached by the server.
// ===========================================================================

/** The OpenGraph card the server stores/caches for a URL. */
data class LinkPreview(
    val url: String,
    val title: String?,
    val description: String?,
    val image: String?,
    val domain: String?
)

fun parseLinkPreview(json: org.json.JSONObject?): LinkPreview? {
    val lp = json?.optJSONObject("linkPreview") ?: return null
    val url = lp.optString("url").takeIf { it.isNotBlank() && it != "null" } ?: return null
    return LinkPreview(
        url = url,
        title = lp.optString("title").takeIf { it.isNotBlank() && it != "null" },
        description = lp.optString("description").takeIf { it.isNotBlank() && it != "null" },
        image = lp.optString("image").takeIf { it.isNotBlank() && it != "null" },
        domain = lp.optString("domain").takeIf { it.isNotBlank() && it != "null" }
    )
}

private val URL_REGEX = Regex(
    "(?:https?://|www\\.)[A-Za-z0-9\\-._~:/?#@!$&'()*+,;=%]+",
    setOf(RegexOption.IGNORE_CASE)
)

fun firstUrlIn(text: String): String? {
    val m = URL_REGEX.find(text) ?: return null
    val url = m.value.trimEnd('.', ',', ')', ']', '}')
    return if (url.startsWith("http", ignoreCase = true)) url else "https://$url"
}

fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
    }
}

/**
 * Body text with tappable links — URLs get the violet accent and open in
 * the browser on tap; any tap that is NOT on a link bubbles up so the
 * caller keeps its own tap behavior (e.g. tap-to-expand).
 */
@Composable
fun LinkText(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    color: Color,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onNonLinkTap: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val annotated = remember(text) { buildLinkAnnotated(text, color) }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layout.value = it },
        modifier = Modifier.pointerInput(annotated, onNonLinkTap) {
            detectTapGestures { pos ->
                val lr = layout.value ?: return@detectTapGestures
                val offset = lr.getOffsetForPosition(pos)
                val hit = annotated.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()?.item
                if (hit != null) openUrl(context, hit)
                else onNonLinkTap?.invoke()
            }
        }
    )
}

private fun buildLinkAnnotated(text: String, baseColor: Color): AnnotatedString =
    buildAnnotatedString {
        var idx = 0
        for (m in URL_REGEX.findAll(text)) {
            val start = m.range.first
            if (start > idx) append(text.substring(idx, start))
            var url = m.value.trimEnd('.', ',', ')', ']', '}')
            val display = url
            if (!url.startsWith("http", ignoreCase = true)) url = "https://$url"
            pushStringAnnotation("URL", url)
            withStyle(SpanStyle(color = AccentViolet, fontWeight = FontWeight.SemiBold)) {
                append(display)
            }
            pop()
            idx = m.range.last + 1
        }
        if (idx < text.length) append(text.substring(idx))
    }

/**
 * The preview card under a post body (or in the composer while writing):
 * og:image thumb, title, domain — taps open the site. Falls back to a
 * compact link row when the page has no image.
 */
@Composable
fun LinkPreviewCard(preview: LinkPreview, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val border = Color(0xFFE7ECF3)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8FAFC))
            .border(androidx.compose.foundation.BorderStroke(1.dp, border), RoundedCornerShape(14.dp))
            .clickable { openUrl(context, preview.url) }
            .padding(10.dp)
    ) {
        if (preview.image != null) {
            AsyncImage(
                model = preview.image,
                contentDescription = "Link preview image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE2E8F0))
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentViolet.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Language, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (preview.title != null) {
                Text(
                    preview.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    preview.url,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentViolet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Language, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    preview.domain ?: preview.url,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Per-URL in-memory cache: old posts without a stored preview fetch lazily
// once per app session, never per recomposition.
private val previewMemo = mutableMapOf<String, LinkPreview?>()
private val previewInFlight = mutableSetOf<String>()

/**
 * Resolves the preview for a post: uses the server-stored card when the
 * payload carries one, otherwise fetches it once via the cached endpoint
 * (older posts published before previews existed).
 */
@Composable
fun rememberResolvedLinkPreview(postId: String, bodyText: String, stored: LinkPreview?): LinkPreview? {
    val context = LocalContext.current
    var resolved by remember(postId) { mutableStateOf<LinkPreview?>(stored) }
    var attempted by remember(postId) { mutableStateOf(false) }
    val url = remember(postId, bodyText) { firstUrlIn(bodyText) }

    LaunchedEffect(postId, url) {
        if (stored != null || url == null || attempted) return@LaunchedEffect
        attempted = true
        val key = "$postId|$url"
        if (previewMemo.containsKey(key)) {
            resolved = previewMemo[key]
            return@LaunchedEffect
        }
        if (key in previewInFlight) return@LaunchedEffect
        previewInFlight.add(key)
        try {
            val token = SessionManager.sessionToken(context)
            if (token != null) {
                val resp = ApiClient.fetchLinkPreview(token, url)
                val lp = resp.optJSONObject("preview")
                val parsed = if (lp != null) LinkPreview(
                    url = lp.optString("url").takeIf { it.isNotBlank() && it != "null" } ?: url,
                    title = lp.optString("title").takeIf { it.isNotBlank() && it != "null" },
                    description = lp.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    image = lp.optString("image").takeIf { it.isNotBlank() && it != "null" },
                    domain = lp.optString("domain").takeIf { it.isNotBlank() && it != "null" }
                ) else null
                previewMemo[key] = parsed
                resolved = parsed
            }
        } catch (_: Exception) {
            previewMemo[key] = null
        } finally {
            previewInFlight.remove(key)
        }
    }
    return resolved
}

/** Debounced live preview for the composer: paste a link, the card appears. */
@Composable
fun rememberComposerLinkPreview(text: String): LinkPreview? {
    val context = LocalContext.current
    val url = remember(text) { firstUrlIn(text) }
    var preview by remember { mutableStateOf<LinkPreview?>(null) }
    var fetchedFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        if (url == null) {
            preview = null
            fetchedFor = null
            return@LaunchedEffect
        }
        val key = "composer|$url"
        if (previewMemo.containsKey(key)) {
            preview = previewMemo[key]
            fetchedFor = url
            return@LaunchedEffect
        }
        delay(600) // debounce while the author is still typing/pasting
        if (fetchedFor == url) return@LaunchedEffect
        fetchedFor = url
        try {
            val token = SessionManager.sessionToken(context)
            if (token != null) {
                val resp = ApiClient.fetchLinkPreview(token, url)
                val lp = resp.optJSONObject("preview")
                val parsed = if (lp != null) LinkPreview(
                    url = lp.optString("url").takeIf { it.isNotBlank() && it != "null" } ?: url,
                    title = lp.optString("title").takeIf { it.isNotBlank() && it != "null" },
                    description = lp.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    image = lp.optString("image").takeIf { it.isNotBlank() && it != "null" },
                    domain = lp.optString("domain").takeIf { it.isNotBlank() && it != "null" }
                ) else null
                previewMemo[key] = parsed
                preview = parsed
            }
        } catch (_: Exception) {
            previewMemo[key] = null
            preview = null
        }
    }
    return preview
}
