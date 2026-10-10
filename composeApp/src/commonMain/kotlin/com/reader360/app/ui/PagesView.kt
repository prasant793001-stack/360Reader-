package com.reader360.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.reader360.app.PdfDoc
import com.reader360.app.ctrlWheelZoom
import com.reader360.app.model.FileCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Zoom and pan for the continuous page view. The list scrolls vertically on
 * its own; this adds pinch / double-tap / Ctrl+wheel zoom and horizontal pan.
 */
@Stable
class PageZoomState(val listState: LazyListState) {
    var scale by mutableFloatStateOf(1f)
        private set
    var offsetX by mutableFloatStateOf(0f)
        private set

    internal var viewportWidth = 0f
    internal var viewportHeight = 0f

    /** Quantised zoom used to pick bitmap resolution, so we only re-render at a few steps. */
    val renderScale: Int
        get() = when {
            scale <= 1.25f -> 1
            scale <= 2.25f -> 2
            scale <= 3.25f -> 3
            else -> 4
        }

    val percent: Int get() = (scale * 100).roundToInt()

    fun zoomAround(target: Float, focus: Offset) {
        val old = scale
        val new = target.coerceIn(MIN_SCALE, MAX_SCALE)
        if (abs(new - old) < 0.0001f) return
        offsetX = focus.x - (focus.x - offsetX) * (new / old)
        // Keep the content under the focus point steady vertically.
        listState.dispatchRawDelta(focus.y / old - focus.y / new)
        scale = new
        clamp()
    }

    fun zoomStep(zoomIn: Boolean) {
        val center = Offset(viewportWidth / 2f, viewportHeight / 2f)
        zoomAround(if (zoomIn) scale * 1.25f else scale / 1.25f, center)
    }

    fun reset() {
        scale = 1f
        offsetX = 0f
    }

    fun toggle(focus: Offset) {
        if (scale > 1.05f) {
            zoomAround(1f, focus)
            offsetX = 0f
        } else {
            zoomAround(2.5f, focus)
        }
    }

    fun panBy(dx: Float, dy: Float) {
        offsetX += dx
        clamp()
        if (dy != 0f) listState.dispatchRawDelta(-dy / scale)
    }

    private fun clamp() {
        val minX = viewportWidth - viewportWidth * scale
        offsetX = if (scale <= 1f) 0f else offsetX.coerceIn(minX, 0f)
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 6f
    }
}

@Composable
fun PagesView(
    doc: PdfDoc,
    category: FileCategory,
    zoom: PageZoomState,
    maxPagePixels: Int,
    modifier: Modifier = Modifier,
) {
    val viewerColors = LocalViewerColors.current
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(viewerColors.canvas)
            .clipToBounds(),
    ) {
        zoom.viewportWidth = constraints.maxWidth.toFloat()
        zoom.viewportHeight = constraints.maxHeight.toFloat()

        // When zoomed in, the scaled list shows only the top 1/scale of its own
        // viewport, so pad the end to let the last page scroll fully into view.
        val extraBottom = with(density) { (zoom.viewportHeight * (1f - 1f / zoom.scale)).toDp() }

        Box(
            Modifier
                .fillMaxSize()
                .pageZoomGestures(zoom)
                .ctrlWheelZoom { factor, position -> zoom.zoomAround(zoom.scale * factor, position) }
                .pointerInput(zoom) { detectTapGestures(onDoubleTap = { zoom.toggle(it) }) },
        ) {
            LazyColumn(
                state = zoom.listState,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offsetX
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 16.dp + extraBottom),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(count = doc.pageCount, key = { it }) { index ->
                    PageTile(
                        doc = doc,
                        index = index,
                        renderScale = zoom.renderScale,
                        maxPixels = maxPagePixels,
                        modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth(),
                    )
                }
            }
        }

        PageIndicator(
            listState = zoom.listState,
            pageCount = doc.pageCount,
            noun = category.pageNoun,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

@Composable
private fun PageTile(
    doc: PdfDoc,
    index: Int,
    renderScale: Int,
    maxPixels: Int,
    modifier: Modifier,
) {
    val aspect = remember(doc, index) { doc.pageAspect(index).takeIf { it.isFinite() && it > 0f } ?: 0.7071f }
    val viewerColors = LocalViewerColors.current

    BoxWithConstraints(
        modifier
            .aspectRatio(aspect)
            .shadow(3.dp, RoundedCornerShape(3.dp), ambientColor = viewerColors.pageShadow, spotColor = viewerColors.pageShadow)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val baseWidth = constraints.maxWidth.coerceAtLeast(1)
        val targetWidth = run {
            val wanted = baseWidth * renderScale
            val wantedHeight = wanted / aspect
            if (wanted * wantedHeight > maxPixels) sqrt(maxPixels * aspect).toInt() else wanted
        }.coerceAtLeast(1)

        var bitmap by remember(doc, index) { mutableStateOf<ImageBitmap?>(null) }
        var failed by remember(doc, index) { mutableStateOf(false) }

        LaunchedEffect(doc, index, targetWidth) {
            // Small debounce so fast flings don't queue renders for pages that scroll past.
            if (bitmap == null) delay(40)
            try {
                bitmap = doc.renderPage(index, targetWidth)
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failed = true
            }
        }

        val current = bitmap
        when {
            current != null -> Image(
                bitmap = current,
                contentDescription = "Page ${index + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            failed -> Text("Page ${index + 1} couldn't be drawn", color = Color(0xFF6B7280))
            else -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp,
                color = Color(0xFFB4B9C6),
            )
        }
    }
}

@Composable
private fun PageIndicator(listState: LazyListState, pageCount: Int, noun: String, modifier: Modifier) {
    val current by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - center) }?.index ?: 0
        }
    }
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(current, listState.isScrollInProgress) {
        visible = true
        if (!listState.isScrollInProgress) {
            delay(1800)
            visible = false
        }
    }
    AnimatedVisibility(visible = visible && pageCount > 1, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xE6202330),
            contentColor = Color.White,
        ) {
            Text(
                "${noun.replaceFirstChar { it.uppercase() }} ${current + 1} of $pageCount",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Two fingers: zoom + pan. One finger while zoomed in: pan freely.
 * One finger at 100%: left alone so the list scrolls and flings natively.
 * Runs in the Initial pass so it can claim the gesture before the list does.
 */
private fun Modifier.pageZoomGestures(zoom: PageZoomState): Modifier = pointerInput(zoom) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                val factor = event.calculateZoom()
                val pan = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = true)
                if (centroid != Offset.Unspecified && factor != 1f) zoom.zoomAround(zoom.scale * factor, centroid)
                if (pan != Offset.Zero) zoom.panBy(pan.x, pan.y)
                event.changes.forEach { it.consume() }
            } else if (pressed == 1 && zoom.scale > 1.01f) {
                val pan = event.calculatePan()
                if (pan != Offset.Zero) {
                    zoom.panBy(pan.x, pan.y)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
