package com.reader360.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reader360.app.model.FileCategory
import com.reader360.app.model.FileTypes
import kotlin.math.roundToInt

/** Coloured rounded tile showing the file extension, e.g. "PPTX". */
@Composable
fun FileBadge(name: String, category: FileCategory, size: Dp = 44.dp) {
    val color = Color(category.color)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.78f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = FileTypes.badgeText(name),
            color = Color.White,
            style = BadgeTextStyle,
            maxLines = 1,
        )
    }
}

/** The app mark: a page with a folded corner on an indigo-to-violet tile. */
class AppLogoPainter : Painter() {
    override val intrinsicSize: Size = Size(256f, 256f)

    override fun DrawScope.onDraw() {
        val s = size.minDimension
        val r = s * 0.24f
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFEC4899)),
                start = Offset.Zero,
                end = Offset(s, s),
            ),
            cornerRadius = CornerRadius(r, r),
            size = Size(s, s),
        )
        val left = s * 0.29f
        val top = s * 0.2f
        val right = s * 0.71f
        val bottom = s * 0.8f
        val fold = s * 0.13f
        val page = Path().apply {
            moveTo(left, top)
            lineTo(right - fold, top)
            lineTo(right, top + fold)
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
        }
        drawPath(page, Color.White)
        val corner = Path().apply {
            moveTo(right - fold, top)
            lineTo(right - fold, top + fold)
            lineTo(right, top + fold)
            close()
        }
        drawPath(corner, Color(0xFFC7D2FE))
        val lineStroke = s * 0.045f
        val lineColor = Color(0xFF6366F1)
        val lines = listOf(0.42f to 0.62f, 0.52f to 0.62f, 0.62f to 0.52f)
        for ((y, end) in lines) {
            drawLine(
                color = lineColor,
                start = Offset(s * 0.37f, s * y),
                end = Offset(s * end, s * y),
                strokeWidth = lineStroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
fun AppLogo(size: Dp) {
    val painter = androidx.compose.runtime.remember { AppLogoPainter() }
    Image(painter = painter, contentDescription = null, modifier = Modifier.size(size))
}

/** Full-screen message used for loading errors and missing engines. */
@Composable
fun StatusMessage(
    title: String,
    detail: String,
    badgeName: String? = null,
    badgeCategory: FileCategory? = null,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (badgeName != null && badgeCategory != null) {
                FileBadge(badgeName, badgeCategory, size = 64.dp)
                Spacer(Modifier.height(20.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (primaryLabel != null && onPrimary != null) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onPrimary) { Text(primaryLabel) }
            }
            if (secondaryLabel != null && onSecondary != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

fun formatTimeAgo(then: Long, now: Long): String {
    val minutes = ((now - then) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        minutes < 60 * 24 * 60 -> "${minutes / (60 * 24 * 7)}w ago"
        else -> "${minutes / (60 * 24 * 30)}mo ago"
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
    bytes < 1024L * 1024 * 1024 -> "${((bytes / (1024.0 * 1024)) * 10).roundToInt() / 10.0} MB"
    else -> "${((bytes / (1024.0 * 1024 * 1024)) * 10).roundToInt() / 10.0} GB"
}

fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"
