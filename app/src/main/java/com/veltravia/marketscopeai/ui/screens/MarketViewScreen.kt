package com.veltravia.marketscopeai.ui.screens

import android.graphics.Paint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.key
import java.net.URLEncoder
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veltravia.marketscopeai.data.ApiClient
import com.veltravia.marketscopeai.ui.theme.AccentCyan
import com.veltravia.marketscopeai.ui.theme.AccentViolet
import com.veltravia.marketscopeai.ui.theme.BearRed
import com.veltravia.marketscopeai.ui.theme.BorderSubtle
import com.veltravia.marketscopeai.ui.theme.BullGreen
import com.veltravia.marketscopeai.ui.theme.SurfaceLight
import com.veltravia.marketscopeai.ui.theme.TextMuted
import com.veltravia.marketscopeai.ui.theme.TextPrimary
import com.veltravia.marketscopeai.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs

private const val CANDLE_POLL_MS = 30_000L
private const val PRICE_POLL_MS = 6_000L
private const val FLASH_DECAY_MS = 700
private val INTERVALS = listOf("5m", "15m", "1h", "4h", "1d")

/** Maps a watchlist instrument id to its real TradingView symbol. */
private fun tradingViewSymbol(id: String): String = when (id) {
    "xauusd" -> "OANDA:XAUUSD"
    "xagusd" -> "OANDA:XAGUSD"
    "eurusd" -> "OANDA:EURUSD"
    "gbpusd" -> "OANDA:GBPUSD"
    "usdjpy" -> "OANDA:USDJPY"
    "audusd" -> "OANDA:AUDUSD"
    "btcusd" -> "BINANCE:BTCUSDT"
    "ethusd" -> "BINANCE:ETHUSDT"
    "solusd" -> "BINANCE:SOLUSDT"
    else -> {
        // Any other "<base>usd" id is a crypto ticker (Trending coins etc.)
        // — Binance has the widest symbol coverage on TradingView.
        val base = id.removeSuffix("usd")
        if (id.endsWith("usd") && base.isNotEmpty()) "BINANCE:" + base.uppercase() + "USDT"
        else "OANDA:" + id.uppercase()
    }
}

/** Maps our timeframe chips to TradingView interval parameter. */
private fun tradingViewInterval(tf: String): String = when (tf) {
    "5m" -> "5"
    "15m" -> "15"
    "1h" -> "60"
    "4h" -> "240"
    else -> "D"
}

private data class Candle(
    val t: Long,   // epoch seconds
    val o: Double,
    val h: Double,
    val l: Double,
    val c: Double,
    val v: Double
)

private fun candleDecimals(id: String, v: Double): Int = when {
    id.endsWith("jpy") -> 3
    id.length == 6 && id.all { it.isLetter() } -> 5 // forex pairs
    id == "xauusd" || id == "xagusd" -> 2
    v >= 1 -> 2
    v >= 0.01 -> 4
    v >= 0.0001 -> 6
    else -> 8
}

private fun formatPrice(id: String, v: Double): String {
    val d = candleDecimals(id, v)
    return "%,.${d}f".format(v)
}

/**
 * Market View — the live candlestick screen behind every Watchlist row.
 * Real OHLC candles from the backend (Coinbase for crypto, Yahoo for
 * forex/metals) rendered on a custom Canvas chart: grid + price scale,
 * volume bars when the feed provides them, a dashed live-price line with
 * a price chip, and time labels. The big price header ticks on real
 * spot-price polls (6s) and flashes green/red on genuine movement; the
 * candles refresh every 30s and the last candle also tracks the live
 * price between refreshes. No simulated data anywhere — if a feed is
 * down the screen says so honestly.
 */
@Composable
fun MarketViewScreen(instrumentId: String, onBack: () -> Unit) {
    val id = instrumentId.lowercase()

    var interval by remember { mutableStateOf("15m") }
    var display by remember { mutableStateOf(id.uppercase()) }
    var subtitle by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<String?>(null) }
    var candles by remember { mutableStateOf<List<Candle>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    var livePrice by remember { mutableStateOf<Double?>(null) }
    var priceError by remember { mutableStateOf(false) }
    var flashUp by remember { mutableStateOf<Boolean?>(null) }
    var tvFailed by remember { mutableStateOf(false) }
    var tvRetryKey by remember { mutableStateOf(0) }

    // Real candle polling — full refresh every 30s per timeframe.
    LaunchedEffect(id, interval, retryKey) {
        candles = null
        loadError = null
        while (true) {
            runCatching { ApiClient.fetchCandles(id, interval) }
                .onSuccess { json ->
                    display = json.optString("display", id.uppercase())
                    subtitle = json.optString("subtitle", "")
                    source = json.optString("source").takeIf { it.isNotBlank() }
                    val arr = json.optJSONArray("candles")
                    val list = mutableListOf<Candle>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            list.add(
                                Candle(
                                    t = o.optLong("t"),
                                    o = o.optDouble("o"),
                                    h = o.optDouble("h"),
                                    l = o.optDouble("l"),
                                    c = o.optDouble("c"),
                                    v = o.optDouble("v")
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        candles = list
                        loadError = null
                    } else {
                        loadError = "Live candles are temporarily unavailable for this market."
                    }
                }
                .onFailure { if (candles == null) loadError = it.message ?: "Could not load live candles" }
            delay(CANDLE_POLL_MS)
        }
    }

    // Real spot-price polling — the big ticking number.
    LaunchedEffect(id) {
        var last: Double? = null
        while (true) {
            runCatching { ApiClient.fetchMarketPrice(id) }
                .onSuccess { json ->
                    val p = if (json.isNull("price")) null else json.optDouble("price")
                    if (p != null && p > 0) {
                        livePrice = p
                        priceError = false
                        if (last != null && p != last) {
                            flashUp = p > last
                        }
                        last = p
                    } else {
                        priceError = true
                    }
                }
                .onFailure { priceError = true }
            delay(PRICE_POLL_MS)
        }
    }

    // Flash decay for the big price.
    LaunchedEffect(flashUp) {
        if (flashUp != null) {
            delay(FLASH_DECAY_MS.toLong())
            flashUp = null
        }
    }

    val flashColor by animateColorAsState(
        targetValue = when (flashUp) {
            true -> BullGreen
            false -> BearRed
            null -> TextPrimary
        },
        animationSpec = tween(FLASH_DECAY_MS),
        label = "price-flash"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(display, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            LivePill()
        }

        Spacer(Modifier.height(18.dp))

        // Big live price + period change
        val bigPrice = livePrice ?: candles?.lastOrNull()?.c
        Row(verticalAlignment = Alignment.Bottom) {
            if (bigPrice != null) {
                Text(
                    formatPrice(id, bigPrice),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = flashColor
                )
            } else {
                Text("—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = TextMuted)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val firstOpen = candles?.firstOrNull()?.o
            if (bigPrice != null && firstOpen != null && firstOpen > 0) {
                val changePct = ((bigPrice - firstOpen) / firstOpen) * 100
                val up = changePct >= 0
                Text(
                    "${if (up) "▲" else "▼"} ${"%.2f".format(abs(changePct))}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (up) BullGreen else BearRed
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "over the visible range",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
            if (priceError && bigPrice == null) {
                Spacer(Modifier.width(8.dp))
                Text("spot price unavailable", style = MaterialTheme.typography.bodySmall, color = BearRed)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Timeframe chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INTERVALS.forEach { tf ->
                val selected = tf == interval
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (selected) Modifier.background(Brush.horizontalGradient(listOf(AccentCyan, AccentViolet)))
                            else Modifier
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) Color.Transparent else BorderSubtle,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { interval = tf }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        tf.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Chart — the real TradingView chart (same engine the reference
        // platform uses), with our built-in canvas as an honest offline fallback.
        val shown: List<Candle>? = remember(candles, livePrice) {
            val list = candles ?: return@remember null
            val p = livePrice ?: return@remember list
            val last = list.last()
            if (p == last.c) return@remember list
            list.dropLast(1) + last.copy(c = p, h = max(last.h, p), l = min(last.l, p))
        }
        if (tvFailed) {
            when {
                loadError != null && candles == null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SurfaceLight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("TradingView could not be reached and live candles are temporarily unavailable.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { retryKey++ },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = Color.White)
                    ) { Text("Retry") }
                }
                shown == null -> Column(modifier = Modifier.fillMaxWidth()) {
                    repeat(3) { i ->
                        Box(
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .fillMaxWidth()
                                .height(if (i == 1) 60.dp else 24.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(rememberShimmerBrush())
                        )
                        if (i != 2) Spacer(Modifier.height(10.dp))
                    }
                }
                else -> {
                    // Built-in chart fallback — still real data, just ours.
                    Text(
                        "TradingView unreachable — showing the built-in chart.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Spacer(Modifier.height(6.dp))
                    CandleChart(
                        id = id,
                        candles = shown,
                        livePrice = livePrice,
                        interval = interval
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { tvRetryKey++; tvFailed = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentViolet, contentColor = Color.White)
                    ) { Text("Try TradingView again") }
                }
            }
        } else {
            key(interval, tvRetryKey) {
                TradingViewChart(
                    symbol = tradingViewSymbol(id),
                    tvInterval = tradingViewInterval(interval),
                    onFailed = { tvFailed = true }
                )
            }
        }

        // Range stats — real values from our live series, shown under either chart.
        if (shown != null) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val high = shown.maxOf { it.h }
                val low = shown.minOf { it.l }
                RangeStat("Range high", formatPrice(id, high), Modifier.weight(1f))
                RangeStat("Range low", formatPrice(id, low), Modifier.weight(1f))
                RangeStat("Data feed", (source ?: "live").replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(110.dp))
    }
}

/** Pulsing green dot + LIVE — honestly reflects the real polling behind the screen. */
@Composable
private fun LivePill() {
    val transition = rememberInfiniteTransition(label = "market-live-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "market-live-pulse-alpha"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BullGreen.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(BullGreen.copy(alpha = alpha))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "LIVE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = BullGreen.copy(alpha = alpha)
        )
    }
}

@Composable
private fun RangeStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

/**
 * The candlestick chart itself — a single custom Canvas, drawn entirely
 * from the real candle data: dashed grid + right price scale, green/red
 * bodies with wicks, volume bars when the feed reports volume, a dashed
 * live-price line with a price chip, and time labels along the bottom.
 */
@Composable
private fun CandleChart(
    id: String,
    candles: List<Candle>,
    livePrice: Double?,
    interval: String
) {
    val textMuted = TextMuted
    val gridColor = BorderSubtle
    val upColor = BullGreen
    val downColor = BearRed
    val accentCyan = AccentCyan
    val decimals = remember(id, candles) { candleDecimals(id, candles.last().c) }
    val timeFmt = remember(interval) {
        if (interval == "1d") SimpleDateFormat("MMM d", Locale.getDefault())
        else SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(top = 14.dp, bottom = 26.dp, start = 10.dp, end = 62.dp)) {
            if (candles.isEmpty()) return@Canvas

            val hasVolume = candles.any { it.v > 0.0 }
            val volumeHeight = if (hasVolume) size.height * 0.18f else 0f
            val chartHeight = size.height - volumeHeight
            val chartWidth = size.width
            val n = candles.size

            var minL = Double.MAX_VALUE
            var maxH = -Double.MAX_VALUE
            candles.forEach { c ->
                minL = min(minL, c.l)
                maxH = max(maxH, c.h)
            }
            livePrice?.let { p ->
                minL = min(minL, p)
                maxH = max(maxH, p)
            }
            if (maxH <= minL) maxH = minL + 1.0
            val pad = (maxH - minL) * 0.06
            val lo = minL - pad
            val hi = maxH + pad
            val y: (Double) -> Float = { price -> (chartHeight * ((hi - price) / (hi - lo))).toFloat() }

            // Grid + price labels (4 lines)
            val labelPaint = android.graphics.Paint().apply {
                color = textMuted.toArgb()
                textSize = 9.sp.toPx()
                textAlign = android.graphics.Paint.Align.RIGHT
                isAntiAlias = true
            }
            val steps = 4
            for (i in 0..steps) {
                val price = lo + (hi - lo) * i / steps
                val yy = y(price)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, yy),
                    end = Offset(chartWidth, yy),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "%.${decimals}f".format(price),
                    chartWidth + 54f,
                    yy + labelPaint.textSize / 3f,
                    labelPaint
                )
            }

            // Time labels along the bottom
            val timePaint = android.graphics.Paint().apply {
                color = textMuted.toArgb()
                textSize = 9.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val labelEvery = max(1, ceil(n / 6.0).toInt())
            candles.forEachIndexed { i, c ->
                if (i % labelEvery == 0 || i == n - 1) {
                    drawContext.canvas.nativeCanvas.drawText(
                        timeFmt.format(Date(c.t * 1000)),
                        (chartWidth * (i + 0.5f) / n),
                        size.height + 18f,
                        timePaint
                    )
                }
            }

            // Volume bars (real volume — hidden when the feed has none, e.g. forex)
            if (hasVolume) {
                val maxV = candles.maxOf { it.v }
                candles.forEachIndexed { i, c ->
                    if (c.v <= 0.0) return@forEachIndexed
                    val barH = (volumeHeight * (c.v / maxV)).toFloat()
                    val left = chartWidth * i / n
                    val w = (chartWidth / n) * 0.66f
                    drawRect(
                        color = (if (c.c >= c.o) upColor else downColor).copy(alpha = 0.25f),
                        topLeft = Offset(left, size.height - barH),
                        size = androidx.compose.ui.geometry.Size(w, barH)
                    )
                }
            }

            // Candles
            val slot = chartWidth / n
            val bodyW = slot * 0.66f
            candles.forEachIndexed { i, c ->
                val cx = slot * (i + 0.5f)
                val isUp = c.c >= c.o
                val color = if (isUp) upColor else downColor
                // Wick
                drawLine(
                    color = color,
                    start = Offset(cx, y(c.h)),
                    end = Offset(cx, y(c.l)),
                    strokeWidth = 1.5f
                )
                // Body (min height so a doji is still visible)
                val top = y(max(c.o, c.c))
                val bottom = y(min(c.o, c.c))
                val bodyTop = top
                val bodyH = max(2f, bottom - top)
                drawRect(
                    color = color,
                    topLeft = Offset(cx - bodyW / 2f, bodyTop),
                    size = androidx.compose.ui.geometry.Size(bodyW, bodyH)
                )
            }

            // Live price dashed line + chip
            livePrice?.let { p ->
                if (p in lo..hi) {
                    val yy = y(p)
                    drawLine(
                        color = accentCyan,
                        start = Offset(0f, yy),
                        end = Offset(chartWidth, yy),
                        strokeWidth = 1.5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                    val chipPaint = android.graphics.Paint().apply {
                        color = accentCyan.toArgb()
                        textSize = 9.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        "%.${decimals}f".format(p),
                        chartWidth + 54f,
                        yy + 3f,
                        chipPaint
                    )
                }
            }
        }
    }
}

/**
 * The real TradingView chart — embedded via TradingView's public chart widget,
 * the same engine the reference platform serves on its web workstation.
 * Full candlesticks, crosshair, zoom, indicators and drawing tools, themed
 * light to match MarketScope. Timeframe comes from the app's own chips.
 */
@Composable
private fun TradingViewChart(
    symbol: String,
    tvInterval: String,
    onFailed: () -> Unit
) {
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val url = remember(symbol, tvInterval) {
        "https://www.tradingview.com/widgetembed/?symbol=" +
            URLEncoder.encode(symbol, "UTF-8") +
            "&interval=" + tvInterval +
            "&theme=light&style=1&timezone=Africa%2FLagos&locale=en" +
            "&withdateranges=1&allow_symbol_change=0&save_image=0&hide_volume=0&hide_legend=0"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(460.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceLight)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            loaded = false
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!failed) loaded = true
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true && !failed) {
                                failed = true
                                onFailed()
                            }
                        }
                    }
                    loadUrl(url)
                }
            },
            update = { }
        )
        if (!loaded && !failed) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SurfaceLight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(rememberShimmerBrush())
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(rememberShimmerBrush())
                )
            }
        }
    }
}
