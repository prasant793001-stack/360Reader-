package com.reader360.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.reader360.app.DecodedImage
import com.reader360.app.ctrlWheelZoom

/** Pinch / double-tap / Ctrl+wheel zoomable image. */
@Composable
fun ImageViewer(image: DecodedImage, modifier: Modifier = Modifier) {
    var scale by remember(image) { mutableFloatStateOf(1f) }
    var offset by remember(image) { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(LocalViewerColors.current.canvas)
            .clipToBounds(),
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val center = Offset(width / 2f, height / 2f)

        fun clamp(o: Offset, s: Float): Offset {
            val maxX = (width * s - width) / 2f
            val maxY = (height * s - height) / 2f
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        fun zoomAround(target: Float, focus: Offset) {
            val newScale = target.coerceIn(1f, 10f)
            val q = focus - center
            offset = clamp(q - (q - offset) * (newScale / scale), newScale)
            scale = newScale
        }

        Image(
            bitmap = image.bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(image, width, height) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        zoomAround(scale * zoom, centroid)
                        offset = clamp(offset + pan, scale)
                    }
                }
                .pointerInput(image, width, height) {
                    detectTapGestures(onDoubleTap = { p -> if (scale > 1.05f) zoomAround(1f, p) else zoomAround(3f, p) })
                }
                .ctrlWheelZoom { factor, position -> zoomAround(scale * factor, position) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )

        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xCC202330),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        ) {
            Text(
                "${image.width} × ${image.height}" + if (scale > 1.01f) "  ·  ${(scale * 100).toInt()}%" else "",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
