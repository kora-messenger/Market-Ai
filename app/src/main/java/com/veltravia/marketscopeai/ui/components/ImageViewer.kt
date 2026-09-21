package com.veltravia.marketscopeai.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Full-screen image viewer used everywhere the app shows an image that can
 * be expanded: community post images, weekly proofs, wall-of-wins proofs and
 * signal comment screenshots.
 *
 * Behavior: horizontal swipe paging between images, pinch to zoom (1x-4x)
 * with pan while zoomed, double-tap to zoom in/out, drag down to dismiss with
 * a spring, close button + "n / m" counter + dot indicators, per-page loading
 * bubble. Landscape of a single image still opens fine (no pager chrome).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerDialog(
    urls: List<String>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (urls.isEmpty()) { onDismiss(); return }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // FxLens-style dismiss physics, in pixels.
    val dismissDistancePx = with(density) { 150.dp.toPx() }
    val dragFadePx = with(density) { 260.dp.toPx() }
    val dragScalePx = with(density) { 400.dp.toPx() }
    val flingVelocityPx = with(density) { 1000.dp.toPx() }
    val exitPx = with(density) { (LocalConfiguration.current.screenHeightDp.dp.toPx() / 3f) }

    val dragY = remember { Animatable(0f) }     // vertical dismiss drag
    val fade = remember { Animatable(1f) }      // close-animation fade
    var zoomedPage by remember { mutableStateOf(false) } // any page currently zoomed
    var zoomResetKey by remember { mutableIntStateOf(0) } // bump to reset page zoom states

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, urls.size - 1)
    ) { urls.size }

    // Leaving a page resets its zoom so the pager is always swipeable again.
    LaunchedEffect(pagerState) {
        var last = pagerState.currentPage
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != last) { zoomResetKey++; zoomedPage = false; last = page }
        }
    }

    fun animateClose() {
        scope.launch {
            val target = if (dragY.value >= 0f) exitPx else -exitPx
            listOf(
                launch { dragY.animateTo(target, tween(180)) },
                launch { fade.animateTo(0f, tween(180)) }
            ).joinAll()
            onDismiss()
        }
    }

    // Progress of the drag: 0 untouched, 1 dragged far enough to fully fade.
    val dragFadeT = (kotlin.math.abs(dragY.value) / dragFadePx).coerceIn(0f, 1f)
    val scrimAlpha = 0.92f * fade.value * (1f - 0.78f * dragFadeT)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha))) {
            // The whole pager translates and scales down while dragging.
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = urls.size > 1 && !zoomedPage,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = dragY.value
                        val s = 1f - 0.14f * ((kotlin.math.abs(dragY.value) / dragScalePx).coerceIn(0f, 1f))
                        scaleX = s
                        scaleY = s
                        alpha = fade.value
                    }
            ) { page ->
                ViewerPage(
                    url = urls[page],
                    zoomResetKey = zoomResetKey,
                    flingVelocityPx = flingVelocityPx,
                    onZoomedChange = { zoomedPage = it },
                    onDrag = { dy -> scope.launch { dragY.snapTo(dragY.value + dy) } },
                    onDragEnd = { velocity ->
                        if (kotlin.math.abs(dragY.value) > dismissDistancePx ||
                            kotlin.math.abs(velocity) > flingVelocityPx
                        ) animateClose()
                        else scope.launch {
                            dragY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 380f))
                        }
                    },
                    onTap = { onDismiss() }
                )
            }

            val controlsAlpha = (1f - dragFadeT).coerceIn(0f, 1f) * fade.value

            // Close button, top-left, FxLens dimensions (40dp circle, 18dp X).
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 10.dp)
                    .size(40.dp)
                    .graphicsLayer { alpha = controlsAlpha }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close image", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Counter pill + dots, only when there is more than one image.
            if (urls.size > 1) {
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 14.dp)
                        .graphicsLayer { alpha = controlsAlpha }
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${urls.size}",
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 28.dp)
                        .graphicsLayer { alpha = controlsAlpha }
                ) {
                    repeat(urls.size) { i ->
                        val active = i == pagerState.currentPage
                        Box(
                            Modifier
                                .size(if (active) 7.dp else 6.dp)
                                .graphicsLayer {
                                    alpha = if (active) 1f else 0.35f
                                }
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One full-screen page: zoomable image + drag-to-dismiss when not zoomed.
 * Own gesture layer is split so the pager and the dismiss drag never fight:
 *  - not zoomed: vertical draggable = dismiss, tap = close, double-tap = zoom in
 *  - zoomed: pan/pinch transform, tap = reset zoom, pager is disabled
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ViewerPage(
    url: String,
    zoomResetKey: Int,
    flingVelocityPx: Float,
    onZoomedChange: (Boolean) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    onTap: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var scale by remember(zoomResetKey) { mutableFloatStateOf(1f) }
    var offX by remember(zoomResetKey) { mutableFloatStateOf(0f) }
    var offY by remember(zoomResetKey) { mutableFloatStateOf(0f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    suspend fun animateZoomTo(target: Float, tX: Float, tY: Float) {
        val startS = scale
        val sOX = offX
        val sOY = offY
        val a = Animatable(startS)
        a.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 380f)) {
            val f = if (target == startS) 1f else ((value - startS) / (target - startS))
            scale = value
            offX = sOX + (tX - sOX) * f
            offY = sOY + (tY - sOY) * f
        }
        onZoomedChange(scale > 1.01f)
    }

    suspend fun resetZoom() = animateZoomTo(1f, 0f, 0f)

    fun maxOffsetX() = (scale - 1f) * boxSize.width / 2f
    fun maxOffsetY() = (scale - 1f) * boxSize.height / 2f

    // Dismiss drag only while the page is un-zoomed; while zoomed the pan
    // transform handler owns every gesture and the pager is disabled.
    val dismissDrag = if (scale <= 1.01f) Modifier.draggable(
        state = rememberDraggableState { dy -> onDrag(dy) },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity -> onDragEnd(velocity) }
    ) else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .then(dismissDrag)
            .then(
                if (scale > 1.01f) Modifier.pointerInput(url) {
                    // Pan + pinch while zoomed; consume so the pager never scrolls.
                    val needsSettle = awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            if (zoom != 1f || pan != Offset.Zero) {
                                event.changes.forEach { it.consume() }
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                val cx = boxSize.width / 2f
                                val cy = boxSize.height / 2f
                                // Zoom around the gesture centroid, then pan.
                                var nx = cx - (cx - offX) * (newScale / scale)
                                var ny = cy - (cy - offY) * (newScale / scale)
                                nx += pan.x
                                ny += pan.y
                                scale = newScale
                                offX = nx
                                offY = ny
                                onZoomedChange(newScale > 1.01f)
                            }
                        } while (event.changes.any { it.pressed })
                        // Finger lifted: report whether zoom settled below 1x
                        // (the caller suspends outside the restricted scope).
                        scale < 1.02f
                    }
                    if (needsSettle) resetZoom()
                } else Modifier
            )
            .pointerInput(url) {
                detectTapGestures(
                    onTap = {
                        if (scale > 1.01f) scope.launch { resetZoom() } else onTap()
                    },
                    onDoubleTap = { pos ->
                        val cx = boxSize.width / 2f
                        val cy = boxSize.height / 2f
                        if (scale > 1.01f) {
                            scope.launch { resetZoom() }
                        } else {
                            val target = 2.5f
                            val tX = ((pos.x - cx) * (target - 1f)).coerceIn(-1e6f, 1e6f)
                            val tY = ((pos.y - cy) * (target - 1f)).coerceIn(-1e6f, 1e6f)
                            scope.launch { animateZoomTo(target, tX, tY) }
                        }
                    }
                )
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offX.coerceIn(-maxOffsetX(), maxOffsetX())
                translationY = offY.coerceIn(-maxOffsetY(), maxOffsetY())
            }
    ) {
        var loadState by remember(zoomResetKey) { mutableStateOf<AsyncImagePainter.State?>(null) }
        AsyncImage(
            model = url,
            contentDescription = "Image preview",
            contentScale = ContentScale.Fit,
            onState = { loadState = it },
            modifier = Modifier.fillMaxSize()
        )
        // Loading bubble (and honest error note) per page.
        when (loadState) {
            is AsyncImagePainter.State.Loading, null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                }
            }
            is AsyncImagePainter.State.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Could not load this image",
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            else -> {}
        }
    }
}
