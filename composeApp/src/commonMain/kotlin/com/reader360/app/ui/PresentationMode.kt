package com.reader360.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.reader360.app.PdfDoc
import com.reader360.app.Platform
import com.reader360.app.PlatformBackHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Full-screen slideshow. Swipe, tap the left/right half, or use the arrow
 * keys, Space, Page Up/Down. Esc or Back exits.
 */
@Composable
fun PresentationMode(
    doc: PdfDoc,
    startIndex: Int,
    platform: Platform,
    onExit: (lastIndex: Int) -> Unit,
) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))) { doc.pageCount }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    var chromeVisible by remember { mutableStateOf(true) }

    fun go(delta: Int) {
        val target = (pager.currentPage + delta).coerceIn(0, doc.pageCount - 1)
        scope.launch { pager.animateScrollToPage(target) }
    }

    DisposableEffect(Unit) {
        platform.setFullscreen(true)
        onDispose { platform.setFullscreen(false) }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(pager.currentPage, chromeVisible) {
        if (chromeVisible) {
            delay(2500)
            chromeVisible = false
        }
    }
    PlatformBackHandler { onExit(pager.currentPage) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.DirectionDown, Key.Spacebar, Key.PageDown, Key.Enter -> { go(1); true }
                    Key.DirectionLeft, Key.DirectionUp, Key.PageUp, Key.Backspace -> { go(-1); true }
                    Key.MoveHome -> { scope.launch { pager.scrollToPage(0) }; true }
                    Key.MoveEnd -> { scope.launch { pager.scrollToPage(doc.pageCount - 1) }; true }
                    Key.Escape -> { onExit(pager.currentPage); true }
                    else -> false
                }
            },
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        chromeVisible = true
                        when {
                            position.x > size.width * 0.66f -> go(1)
                            position.x < size.width * 0.33f -> go(-1)
                        }
                    }
                },
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            Slide(doc, page)
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
        ) {
            IconButton(
                onClick = { onExit(pager.currentPage) },
                modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(50)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Exit presentation", tint = Color.White)
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        ) {
            Surface(shape = RoundedCornerShape(50), color = Color(0x99000000), contentColor = Color.White) {
                Text(
                    "${pager.currentPage + 1} / ${doc.pageCount}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Slide(doc: PdfDoc, index: Int) {
    val aspect = remember(doc, index) { doc.pageAspect(index).takeIf { it.isFinite() && it > 0f } ?: (16f / 9f) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val w = min(boxW, boxH * aspect)
        val h = w / aspect
        val pixels = (w * h).coerceAtLeast(1f)
        val maxPixels = 16_000_000f
        val renderWidth = (if (pixels > maxPixels) w * sqrt(maxPixels / pixels) else w).toInt().coerceAtLeast(1)

        var bitmap by remember(doc, index) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(doc, index, renderWidth) {
            try {
                bitmap = doc.renderPage(index, renderWidth)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
        val current = bitmap
        val sizeModifier = with(density) { Modifier.size(w.toDp(), h.toDp()) }
        if (current != null) {
            Image(current, contentDescription = "Slide ${index + 1}", modifier = sizeModifier, contentScale = ContentScale.FillBounds)
        } else {
            Box(sizeModifier.background(Color(0xFF111111)))
        }
    }
}
