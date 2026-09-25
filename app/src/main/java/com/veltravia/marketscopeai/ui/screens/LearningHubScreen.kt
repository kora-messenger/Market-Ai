package com.veltravia.marketscopeai.ui.screens

import com.veltravia.marketscopeai.t

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.data.SessionManager
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.GoldAmber
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.json.JSONArray
import org.json.JSONObject

// ===========================================================================
// Data model for the Learning Hub pattern library (fetched from the backend
// /api/learning/patterns). All lesson text is authored by MarketScope AI —
// structure and terminology are standard technical analysis; the words are
// our own original copy.
// ===========================================================================

data class LearningTrack(val id: String, val label: String, val count: Int)

data class LearningCategory(val id: String, val label: String, val track: String, val count: Int)

data class LearningAnalogy(val title: String, val body: String)

/** A single bar in a "bars" or "diverging-bars" diagram. */
data class LearningBar(val label: String, val value: Double, val display: String? = null)

/** A single ring slice in a "donut" diagram (values are fractions, ~summing to 1). */
data class LearningDonutSegment(val label: String, val value: Double, val color: String)

data class LearningCandle(
    val x: Double, val high: Double, val bodyTop: Double,
    val bodyBottom: Double, val low: Double, val bullish: Boolean
)

data class LearningZone(
    val x1: Double, val x2: Double,
    val yTop: Double, val yBottom: Double, val label: String?
)

data class LearningMarker(val index: Int, val label: String)

data class LearningDiagram(
    val kind: String,
    val points: List<List<Double>> = emptyList(),
    val candles: List<LearningCandle> = emptyList(),
    val neckline: List<List<Double>>? = null,
    val trend1: List<List<Double>>? = null,
    val trend2: List<List<Double>>? = null,
    val zone: LearningZone? = null,
    val markers: List<LearningMarker> = emptyList(),
    val bars: List<LearningBar> = emptyList(),
    val segments: List<LearningDonutSegment> = emptyList()
)

data class LearningCheatSheet(
    val entry: String, val stopLoss: String, val target: String,
    val timeframes: String, val bias: String
)

data class LearningPattern(
    val slug: String,
    val title: String,
    val category: String,
    val track: String,
    val accent: String,
    val bias: String,
    val tagline: String,
    val whatItIs: String,
    val howToSpot: List<String>,
    val psychology: String,
    val analogy: LearningAnalogy?,
    val diagram: LearningDiagram?,
    val howToTrade: List<String>,
    val cheatSheet: LearningCheatSheet?,
    val mistakes: List<String>
)

/** In-memory cache shared between the hub grid and the lesson screen. */
object LearningRepository {
    var tracks: List<LearningTrack> = emptyList()
    var categories: List<LearningCategory> = emptyList()
    var patterns: List<LearningPattern> = emptyList()
    fun find(slug: String): LearningPattern? = patterns.firstOrNull { it.slug == slug }
}

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).map { i -> optString(i) }.filter { it.isNotBlank() }

private fun JSONArray.toPointList(): List<List<Double>> =
    (0 until length()).mapNotNull { i ->
        val a = optJSONArray(i) ?: return@mapNotNull null
        listOf(a.optDouble(0), a.optDouble(1))
    }

private fun JSONObject.toDiagram(): LearningDiagram {
    val zoneObj = optJSONObject("zone")
    return LearningDiagram(
        kind = optString("kind", "line"),
        points = optJSONArray("points")?.toPointList() ?: emptyList(),
        candles = optJSONArray("candles")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val c = arr.optJSONObject(i) ?: return@mapNotNull null
                LearningCandle(
                    x = c.optDouble("x"), high = c.optDouble("high"),
                    bodyTop = c.optDouble("bodyTop"), bodyBottom = c.optDouble("bodyBottom"),
                    low = c.optDouble("low"), bullish = c.optBoolean("bullish", true)
                )
            }
        } ?: emptyList(),
        neckline = optJSONArray("neckline")?.toPointList(),
        trend1 = optJSONArray("trend1")?.toPointList(),
        trend2 = optJSONArray("trend2")?.toPointList(),
        zone = zoneObj?.let {
            LearningZone(
                x1 = it.optDouble("x1"), x2 = it.optDouble("x2"),
                yTop = it.optDouble("yTop"), yBottom = it.optDouble("yBottom"),
                label = it.optString("label").takeIf { l -> l.isNotBlank() }
            )
        },
        markers = optJSONArray("markers")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val m = arr.optJSONObject(i) ?: return@mapNotNull null
                LearningMarker(index = m.optInt("index"), label = m.optString("label"))
            }
        } ?: emptyList(),
        bars = optJSONArray("bars")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val b = arr.optJSONObject(i) ?: return@mapNotNull null
                LearningBar(
                    label = b.optString("label"),
                    value = b.optDouble("value"),
                    display = b.optString("display").takeIf { d -> d.isNotBlank() }
                )
            }
        } ?: emptyList(),
        segments = optJSONArray("segments")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val s = arr.optJSONObject(i) ?: return@mapNotNull null
                LearningDonutSegment(
                    label = s.optString("label"),
                    value = s.optDouble("value"),
                    color = s.optString("color", "violet")
                )
            }
        } ?: emptyList()
    )
}

private fun parsePattern(p: JSONObject): LearningPattern = LearningPattern(
    slug = p.optString("slug"),
    title = p.optString("title"),
    category = p.optString("category"),
    track = p.optString("track", "trading"),
    accent = p.optString("accent", "violet"),
    bias = p.optString("bias", "neutral"),
    tagline = p.optString("tagline"),
    whatItIs = p.optString("whatItIs"),
    howToSpot = p.optJSONArray("howToSpot")?.toStringList() ?: emptyList(),
    psychology = p.optString("psychology"),
    analogy = p.optJSONObject("analogy")?.let {
        LearningAnalogy(title = it.optString("title"), body = it.optString("body"))
    },
    diagram = p.optJSONObject("diagram")?.toDiagram(),
    howToTrade = p.optJSONArray("howToTrade")?.toStringList() ?: emptyList(),
    cheatSheet = p.optJSONObject("cheatSheet")?.let {
        LearningCheatSheet(
            entry = it.optString("entry"), stopLoss = it.optString("stopLoss"),
            target = it.optString("target"), timeframes = it.optString("timeframes"),
            bias = it.optString("bias")
        )
    },
    mistakes = p.optJSONArray("mistakes")?.toStringList() ?: emptyList()
)

/** Pastel accent of a pattern card, matching the tag colours on the backend. */
fun accentColor(name: String): Color = when (name.lowercase()) {
    "violet" -> AccentViolet
    "rose" -> BearRed
    "emerald" -> BullGreen
    "cyan" -> AccentCyan
    "amber" -> GoldAmber
    "lime" -> Color(0xFF65A30D)
    "slate" -> Color(0xFF64748B)
    else -> AccentViolet
}

private fun biasLabel(bias: String): String = when (bias.lowercase()) {
    "bullish" -> "Bullish"
    "bearish" -> "Bearish"
    else -> "Neutral"
}

private fun biasColor(bias: String): Color = when (bias.lowercase()) {
    "bullish" -> BullGreen
    "bearish" -> BearRed
    else -> Color(0xFF64748B)
}

// ===========================================================================
// HUB GRID — category filter chips + colour-tagged pattern cards.
// ===========================================================================

@Composable
fun LearningHubScreen(
    onBack: () -> Unit,
    onOpenPattern: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }

    var loading by remember { mutableStateOf(LearningRepository.patterns.isEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTrack by remember { mutableStateOf("trading") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }

    fun load() {
        if (token.isNullOrBlank()) {
            loading = false
            error = "Sign in required."
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val res = ApiClient.fetchLearningPatterns(token)
                val trks = res.optJSONArray("tracks") ?: JSONArray()
                val cats = res.optJSONArray("categories") ?: JSONArray()
                val pats = res.optJSONArray("patterns") ?: JSONArray()
                LearningRepository.tracks = (0 until trks.length()).map { i ->
                    val t = trks.getJSONObject(i)
                    LearningTrack(id = t.optString("id"), label = t.optString("label"), count = t.optInt("count"))
                }
                LearningRepository.categories = (0 until cats.length()).map { i ->
                    val c = cats.getJSONObject(i)
                    LearningCategory(
                        id = c.optString("id"),
                        label = c.optString("label"),
                        track = c.optString("track", "trading"),
                        count = c.optInt("count")
                    )
                }
                LearningRepository.patterns = (0 until pats.length()).map { i ->
                    parsePattern(pats.getJSONObject(i))
                }
                if (LearningRepository.patterns.isEmpty()) {
                    error = "No lessons available yet."
                }
            } catch (e: Exception) {
                error = e.message ?: "Could not load lessons"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { if (LearningRepository.patterns.isEmpty()) load() }

    val all = LearningRepository.patterns
    val inTrack = all.filter { it.track == selectedTrack }
    val visible = if (selectedCategory == null) inTrack else inTrack.filter { it.category == selectedCategory }
    val trackCategories = LearningRepository.categories.filter { it.track == selectedTrack }
    val categoryLabel = { id: String -> LearningRepository.categories.firstOrNull { it.id == id }?.label ?: id }
    // Short, friendly subtitle per track — same header, different framing.
    val trackSubtitle = when (selectedTrack) {
        "crypto" -> "How crypto markets actually work, in plain English"
        "stocks" -> "The fundamentals behind every stock move"
        else -> "Read the setups the market repeats every week"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column {
                Text(t("Learning Hub"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    trackSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // Three-in-one: Trading / Crypto / Stocks. Switching tracks resets
        // the category filter since each track has its own category set.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LearningRepository.tracks.ifEmpty {
                listOf(LearningTrack("trading", "Trading", 0), LearningTrack("crypto", "Crypto", 0), LearningTrack("stocks", "Stocks", 0))
            }.forEach { t ->
                TrackTab(
                    label = t.label,
                    selected = selectedTrack == t.id,
                    modifier = Modifier.weight(1f)
                ) {
                    if (selectedTrack != t.id) {
                        selectedTrack = t.id
                        selectedCategory = null
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentViolet, strokeWidth = 2.5.dp)
                }
            }
            error != null -> {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(error ?: "", color = TextSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { load() }) { Text(t("Retry")) }
                }
            }
            else -> {
                // Category filter chips — scoped to the selected track only.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip("All", inTrack.size, selectedCategory == null) { selectedCategory = null }
                    trackCategories.forEach { c ->
                        FilterChip(c.label, c.count, selectedCategory == c.id) {
                            selectedCategory = if (selectedCategory == c.id) null else c.id
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                if (visible.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(t("No lessons in this category yet."), color = TextMuted)
                    }
                } else {
                    // Compact, single-column list — a fixed-square grid card leaves
                    // a lot of dead space for short lesson taglines, which is the
                    // "awkward" gap this replaces.
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 20.dp)
                    ) {
                        items(visible, key = { it.slug }) { p ->
                            PatternCard(p, categoryLabel(p.category)) { onOpenPattern(p.slug) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AccentViolet else SurfaceLight)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else TextSecondary
        )
    }
}

@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) AccentViolet else SurfaceLight
    val fg = if (selected) Color.White else TextSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = fg)
        Spacer(Modifier.width(6.dp))
        Text("$count", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.75f))
    }
}

@Composable
private fun PatternCard(p: LearningPattern, categoryLabel: String, onClick: () -> Unit) {
    val accent = accentColor(p.accent)
    // Compact, pastel-tinted row card — height follows content instead of a
    // fixed square, so a short one-line tagline never leaves a dead gap.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    biasLabel(p.bias),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = biasColor(p.bias),
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                p.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                p.tagline,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ===========================================================================
// LESSON SCREEN — the full pattern breakdown with a live-drawn diagram.
// ===========================================================================

@Composable
fun LearningPatternScreen(slug: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val token = remember { SessionManager.sessionToken(context) }

    var loading by remember { mutableStateOf(LearningRepository.find(slug) == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pattern by remember { mutableStateOf(LearningRepository.find(slug)) }

    LaunchedEffect(slug) {
        if (pattern == null && !token.isNullOrBlank()) {
            loading = true
            scope.launch {
                try {
                    val res = ApiClient.fetchLearningPatterns(token)
                    val pats = res.optJSONArray("patterns") ?: JSONArray()
                    LearningRepository.patterns = (0 until pats.length()).map { i ->
                        parsePattern(pats.getJSONObject(i))
                    }
                    pattern = LearningRepository.find(slug)
                    if (pattern == null) error = "Lesson not found."
                } catch (e: Exception) {
                    error = e.message ?: "Could not load the lesson"
                } finally {
                    loading = false
                }
            }
        } else if (pattern == null && token.isNullOrBlank()) {
            loading = false
            error = "Sign in required."
        } else {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(t("Learning Hub"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentViolet, strokeWidth = 2.5.dp)
                }
            }
            error != null || pattern == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "Lesson not found.", color = TextSecondary)
                }
            }
            else -> LessonBody(pattern!!)
        }
    }
}

@Composable
private fun LessonBody(p: LearningPattern) {
    val accent = accentColor(p.accent)
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(6.dp))

        // Colour-tinted hero block: category chip + bias chip + title + tagline
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(accent.copy(alpha = 0.10f))
                .padding(18.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagChip(p.category, accent)
                TagChip(biasLabel(p.bias), biasColor(p.bias))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                p.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                p.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(22.dp))

        SectionLabel("WHAT IT IS")
        BodyText(p.whatItIs)

        if (p.howToSpot.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("HOW TO SPOT IT")
            NumberedList(p.howToSpot, accent)
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("THE PSYCHOLOGY BEHIND IT")
        BodyText(p.psychology)

        if (p.analogy != null) {
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.07f))
                    .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.FormatQuote,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        p.analogy.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    p.analogy.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 21.sp
                )
            }
        }

        if (p.diagram != null) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("THE PATTERN, SKETCHED")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                PatternDiagram(p.diagram, accent)
                Spacer(Modifier.height(8.dp))
                Text(t("Illustrative sketch — not a real chart."),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (p.howToTrade.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("HOW TO TRADE IT")
            NumberedList(p.howToTrade, accent)
        }

        if (p.cheatSheet != null) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("QUICK CHEAT SHEET")
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceLight)
                    .padding(16.dp)
            ) {
                CheatRow("Entry", p.cheatSheet.entry)
                CheatRow("Stop loss", p.cheatSheet.stopLoss)
                CheatRow("Target", p.cheatSheet.target)
                CheatRow("Best timeframes", p.cheatSheet.timeframes)
                CheatRow("Bias", p.cheatSheet.bias)
            }
        }

        if (p.mistakes.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionLabel("COMMON MISTAKES")
            Spacer(Modifier.height(8.dp))
            p.mistakes.forEach { m ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = BearRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        m,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(t("Educational content only — not financial advice."),
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun TagChip(text: String, color: Color) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = AccentViolet,
        letterSpacing = 1.sp
    )
}

@Composable
private fun BodyText(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        lineHeight = 21.sp
    )
}

@Composable
private fun NumberedList(items: List<String>, accent: Color) {
    Spacer(Modifier.height(8.dp))
    items.forEachIndexed { i, item ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                item,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CheatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            modifier = Modifier.width(130.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ===========================================================================
// DIAGRAM RENDERER — draws the backend's diagram spec (line path or OHLC
// candles) on a Canvas. All coordinates in the spec are 0-1 normalized with
// y growing downward (0 = top of the chart = higher price).
// ===========================================================================

@Composable
private fun PatternDiagram(diagram: LearningDiagram, accent: Color) {
    val labelPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#475569")
            textAlign = android.graphics.Paint.Align.CENTER
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        val w = size.width
        val h = size.height
        val padX = 4.dp.toPx()
        val padY = 14.dp.toPx()
        val cw = w - padX * 2
        val ch = h - padY * 2

        fun px(v: Double) = padX + (v.toFloat() * cw)
        fun py(v: Double) = padY + (v.toFloat() * ch)

        labelPaint.textSize = 9.sp.toPx()

        // Shaded zone (order block / fair value gap)
        diagram.zone?.let { z ->
            drawRect(
                color = accent.copy(alpha = 0.13f),
                topLeft = Offset(px(z.x1), py(z.yTop)),
                size = androidx.compose.ui.geometry.Size(
                    px(z.x2) - px(z.x1),
                    py(z.yBottom) - py(z.yTop)
                )
            )
            z.label?.let { lbl ->
                drawContext.canvas.nativeCanvas.drawText(
                    lbl,
                    px((z.x1 + z.x2) / 2),
                    py(z.yBottom) + 14.dp.toPx(),
                    labelPaint
                )
            }
        }

        val dashed = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        val lineColor = Color(0xFF94A3B8)

        // Dashed reference lines: neckline / trend1 / trend2
        listOf(diagram.neckline, diagram.trend1, diagram.trend2).forEach { seg ->
            if (seg != null && seg.size == 2) {
                drawLine(
                    color = lineColor,
                    start = Offset(px(seg[0][0]), py(seg[0][1])),
                    end = Offset(px(seg[1][0]), py(seg[1][1])),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = dashed
                )
            }
        }

        if (diagram.kind == "candles" && diagram.candles.isNotEmpty()) {
            val bodyHalf = 9.dp.toPx()
            diagram.candles.forEach { c ->
                val x = px(c.x)
                val col = if (c.bullish) BullGreen else BearRed
                // wick
                drawLine(
                    color = col,
                    start = Offset(x, py(c.high)),
                    end = Offset(x, py(c.low)),
                    strokeWidth = 1.5.dp.toPx()
                )
                // body
                drawRect(
                    color = col,
                    topLeft = Offset(x - bodyHalf, py(c.bodyTop)),
                    size = androidx.compose.ui.geometry.Size(
                        bodyHalf * 2,
                        (py(c.bodyBottom) - py(c.bodyTop)).coerceAtLeast(2.dp.toPx())
                    )
                )
            }
        } else if (diagram.points.isNotEmpty()) {
            // Price path
            val path = Path()
            diagram.points.forEachIndexed { i, pt ->
                val off = Offset(px(pt[0]), py(pt[1]))
                if (i == 0) path.moveTo(off.x, off.y) else path.lineTo(off.x, off.y)
            }
            drawPath(
                path = path,
                color = AccentCyan,
                style = Stroke(width = 2.5.dp.toPx())
            )
            // labelled markers
            diagram.markers.forEach { m ->
                val pt = diagram.points.getOrNull(m.index) ?: return@forEach
                val off = Offset(px(pt[0]), py(pt[1]))
                drawCircle(
                    color = accent,
                    radius = 4.dp.toPx(),
                    center = off
                )
                val above = pt[1] > 0.25
                drawContext.canvas.nativeCanvas.drawText(
                    m.label,
                    off.x,
                    if (above) off.y - 9.dp.toPx() else off.y + 17.dp.toPx(),
                    labelPaint
                )
            }
        } else if (diagram.kind == "donut" && diagram.segments.isNotEmpty()) {
            // Proportion donut: slices sized by value, labelled with %.
            val ringCol = Color(0xFFE2E8F0)
            val ringW = 22.dp.toPx()
            val strokeW = 16.dp.toPx()
            val radius = (minOf(w, h * 1.4f) / 2f) * 0.62f - ringW
            val center = Offset(w * 0.32f, h / 2f)
            // soft backing ring
            drawCircle(color = ringCol, radius = radius + strokeW / 2 + 2, center = center, style = Stroke(strokeW + 4))
            var start = -90f
            val total = diagram.segments.map { it.value }.sum().takeIf { it > 0 } ?: 1.0
            diagram.segments.forEach { seg ->
                val sweep = (seg.value / total * 360.0).toFloat()
                drawArc(
                    color = accentColor(seg.color),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(strokeW)
                )
                start += sweep
            }
            // centre caption + legend on the right
            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            drawContext.canvas.nativeCanvas.drawText(
                "100%",
                center.x,
                center.y + 4.dp.toPx(),
                labelPaint
            )
            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
            val legendX = center.x + radius + 18.dp.toPx()
            var legendY = center.y - (diagram.segments.size - 1) * 11.dp.toPx()
            diagram.segments.forEachIndexed { i, seg ->
                val segColor = accentColor(seg.color)
                drawCircle(color = segColor, radius = 3.dp.toPx(), center = Offset(legendX, legendY - 3.dp.toPx()))
                val legendPaint = android.graphics.Paint(labelPaint).apply {
                    textAlign = android.graphics.Paint.Align.LEFT
                    textSize = 8.5.sp.toPx()
                }
                val pct = Math.round(seg.value / total * 100.0).toInt()
                drawContext.canvas.nativeCanvas.drawText(
                    "$pct%  " + seg.label,
                    legendX + 8.dp.toPx(),
                    legendY,
                    legendPaint
                )
                legendY += 22.dp.toPx()
            }
            labelPaint.textAlign = android.graphics.Paint.Align.CENTER
        } else if (diagram.kind == "bars" && diagram.bars.isNotEmpty()) {
            // Horizontal comparison bars, each labelled with its display value.
            val barH = ((ch / diagram.bars.size) * 0.52f).coerceIn(14.dp.toPx(), 26.dp.toPx())
            val gapY = ch / diagram.bars.size
            val maxAbs = diagram.bars.maxOfOrNull { kotlin.math.abs(it.value) }?.takeIf { it > 0 } ?: 1.0
            val barAreaW = cw * 0.62f
            diagram.bars.forEachIndexed { i, bar ->
                val cy = padY + gapY * i + gapY / 2f
                val namePaint = android.graphics.Paint(labelPaint).apply {
                    textAlign = android.graphics.Paint.Align.LEFT
                    textSize = 9.sp.toPx()
                }
                drawContext.canvas.nativeCanvas.drawText(bar.label, padX, cy + 3.dp.toPx(), namePaint)
                val bw = (bar.value / maxAbs).toFloat() * barAreaW
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(padX + cw * 0.30f, cy - barH / 2),
                    size = androidx.compose.ui.geometry.Size(bw.coerceAtLeast(3f), barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
                val valPaint = android.graphics.Paint(labelPaint).apply {
                    textAlign = android.graphics.Paint.Align.LEFT
                    textSize = 10.sp.toPx()
                    isFakeBoldText = true
                }
                drawContext.canvas.nativeCanvas.drawText(
                    bar.display ?: "",
                    padX + cw * 0.30f + bw + 8.dp.toPx(),
                    cy + 3.5.dp.toPx(),
                    valPaint
                )
            }
        } else if (diagram.kind == "diverging-bars" && diagram.bars.isNotEmpty()) {
            // Bars diverging left (negative) / right (positive) of a zero line.
            val barH = ((ch / diagram.bars.size) * 0.5f).coerceIn(13.dp.toPx(), 24.dp.toPx())
            val gapY = ch / diagram.bars.size
            val maxAbs = diagram.bars.maxOfOrNull { kotlin.math.abs(it.value) }?.takeIf { it > 0 } ?: 1.0
            val zeroX = padX + cw * 0.55f
            val halfW = cw * 0.38f
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(zeroX, padY * 0.6f),
                end = Offset(zeroX, h - padY * 0.6f),
                strokeWidth = 1.25.dp.toPx()
            )
            diagram.bars.forEachIndexed { i, bar ->
                val cy = padY + gapY * i + gapY / 2f
                val namePaint = android.graphics.Paint(labelPaint).apply {
                    textAlign = android.graphics.Paint.Align.LEFT
                    textSize = 9.sp.toPx()
                }
                drawContext.canvas.nativeCanvas.drawText(bar.label, padX, cy + 3.dp.toPx(), namePaint)
                val ratio = (kotlin.math.abs(bar.value) / maxAbs).toFloat()
                val bw = ratio * halfW
                val col = if (bar.value >= 0) BullGreen else BearRed
                if (bar.value >= 0) {
                    drawRoundRect(
                        color = col,
                        topLeft = Offset(zeroX, cy - barH / 2),
                        size = androidx.compose.ui.geometry.Size(bw.coerceAtLeast(3f), barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                } else {
                    drawRoundRect(
                        color = col,
                        topLeft = Offset(zeroX - bw, cy - barH / 2),
                        size = androidx.compose.ui.geometry.Size(bw.coerceAtLeast(3f), barH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                    )
                }
                val valPaint = android.graphics.Paint(labelPaint).apply {
                    textAlign = if (bar.value >= 0) android.graphics.Paint.Align.LEFT else android.graphics.Paint.Align.RIGHT
                    textSize = 10.sp.toPx()
                    isFakeBoldText = true
                }
                val vx = if (bar.value >= 0) zeroX + bw + 8.dp.toPx() else zeroX - bw - 8.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(bar.display ?: "", vx, cy + 3.5.dp.toPx(), valPaint)
            }
        }
    }
}
