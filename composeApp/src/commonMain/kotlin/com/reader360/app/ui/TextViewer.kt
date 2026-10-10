package com.reader360.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Search state shared between the top bar and the text view. */
@Stable
class TextSearchState {
    var active by mutableStateOf(false)
    var query by mutableStateOf("")
    var matchLines by mutableStateOf<List<Int>>(emptyList())
        internal set
    var current by mutableIntStateOf(0)

    fun next() {
        if (matchLines.isNotEmpty()) current = (current + 1) % matchLines.size
    }

    fun previous() {
        if (matchLines.isNotEmpty()) current = (current - 1 + matchLines.size) % matchLines.size
    }
}

@Composable
fun TextViewer(
    text: String,
    monospace: Boolean,
    wrap: Boolean,
    truncated: Boolean,
    search: TextSearchState,
    modifier: Modifier = Modifier,
) {
    val lines = remember(text) { text.split('\n').map { it.removeSuffix("\r").replace("\t", "    ") } }
    val listState = rememberLazyListState()
    val baseStyle = TextStyle(
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        fontSize = if (monospace) 13.sp else 15.sp,
        lineHeight = if (monospace) 20.sp else 23.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val gutterStyle = baseStyle.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        textAlign = TextAlign.End,
    )
    val highlight = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
    val currentHighlight = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)

    // Recompute matches when the query changes.
    LaunchedEffect(search.query, lines) {
        val q = search.query
        search.matchLines = if (q.isBlank()) emptyList() else lines.indices.filter { lines[it].contains(q, ignoreCase = true) }
        search.current = 0
    }
    LaunchedEffect(search.current, search.matchLines) {
        val line = search.matchLines.getOrNull(search.current) ?: return@LaunchedEffect
        listState.animateScrollToItem((line - 3).coerceAtLeast(0))
    }

    val digits = lines.size.toString().length.coerceAtLeast(2)
    val gutterWidth = (digits * 8 + 20).dp
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val contentWidth = remember(lines, wrap, monospace) {
        if (wrap) null else {
            val longest = lines.maxByOrNull { it.length } ?: ""
            val px = measurer.measure(AnnotatedString(longest.take(4000)), baseStyle).size.width
            with(density) { (px.toDp() + gutterWidth + 48.dp).coerceAtMost(40_000.dp) }
        }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (truncated) {
            Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Large file: showing the first 8 MB.",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        val scrollModifier = if (wrap) Modifier.fillMaxSize() else Modifier.fillMaxSize().horizontalScroll(rememberScrollState())
        Box(scrollModifier) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = if (contentWidth != null) Modifier.width(contentWidth).fillMaxHeight() else Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    itemsIndexed(lines) { index, line ->
                        val matchIndex = search.matchLines.indexOf(index)
                        Row(Modifier.fillMaxWidth().padding(end = 16.dp)) {
                            Text(
                                (index + 1).toString(),
                                style = gutterStyle,
                                modifier = Modifier.width(gutterWidth).padding(end = 12.dp),
                                maxLines = 1,
                            )
                            Text(
                                text = if (matchIndex >= 0) {
                                    highlighted(line, search.query, if (matchIndex == search.current) currentHighlight else highlight)
                                } else {
                                    AnnotatedString(line)
                                },
                                style = baseStyle,
                                softWrap = wrap,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun highlighted(line: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(line)
    if (query.isEmpty()) return@buildAnnotatedString
    var start = line.indexOf(query, ignoreCase = true)
    while (start >= 0) {
        addStyle(SpanStyle(background = color), start, start + query.length)
        start = line.indexOf(query, start + query.length, ignoreCase = true)
    }
}
