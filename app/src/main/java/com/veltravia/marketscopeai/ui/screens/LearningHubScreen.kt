package com.veltravia.marketscopeai.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

data class LearningCategory(val id: String, val label: String, val count: Int)

data class LearningAnalogy(val title: String, val body: String)

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
    val markers: List<LearningMarker> = emptyList()
)

data class LearningCheatSheet(
    val entry: String, val stopLoss: String, val target: String,
    val timeframes: String, val bias: String
)

data class LearningPattern(
    val slug: String,
    val title: String,
    val category: String,
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
        } ?: emptyList()
    )
}

private fun parsePattern(p: JSONObject): LearningPattern = LearningPattern(
    slug = p.optString("slug"),
    title = p.optString("title"),
    category = p.optString("category"),
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
                val cats = res.optJSONArray("categories") ?: JSONArray()
                val pats = res.optJSONArray("patterns") ?: JSONArray()
                LearningRepository.categories = (0 until cats.length()).map { i ->
                    val c = cats.getJSONObject(i)
                    LearningCategory(
                        id = c.optString("id"),
                        label = c.optString("label"),
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
    val visible = if (selectedCategory == null) all else all.filter { it.category == selectedCategory }
    val categoryLabel = { id: String -> LearningRepository.categories.firstOrNull { it.id == id }?.label ?: id }

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
                Text(
                    "Learning Hub",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "Read the setups the market repeats every week",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
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
                    TextButton(onClick = { load() }) { Text("Retry") }
                }
            }
            else -> {
                // Category filter chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip("All", all.size, selectedCategory == null) { selectedCategory = null }
                    LearningRepository.categories.forEach { c ->
                        FilterChip(c.label, c.count, selectedCategory == c.id) {
                            selectedCategory = if (selectedCategory == c.id) null else c.id
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
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
        }
        Spacer(Modifier.height(10.dp))
        Text(
            p.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Text(
            p.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.weight(1f))
        Text(
            biasLabel(p.bias),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = biasColor(p.bias)
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
            Text(
                "Learning Hub",
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
                Text(
                    "Illustrative sketch — not a real chart.",
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
        Text(
            "Educational content only — not financial advice.",
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
        }
    }
}
