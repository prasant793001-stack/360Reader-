package com.reader360.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader360.app.DocumentRef
import com.reader360.app.PdfDoc
import com.reader360.app.Platform
import com.reader360.app.PlatformBackHandler
import com.reader360.app.data.DocumentLoader
import com.reader360.app.data.LoadState
import com.reader360.app.data.ViewerContent
import com.reader360.app.model.FileCategory
import com.reader360.app.model.FileTypes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(ref: DocumentRef, platform: Platform, onClose: () -> Unit) {
    val loader = remember(platform) { DocumentLoader(platform) }
    var state by remember(ref) { mutableStateOf<LoadState>(LoadState.Loading("Opening…")) }
    var reloadKey by remember(ref) { mutableStateOf(0) }
    val openedDocs = remember(ref) { mutableListOf<PdfDoc>() }

    LaunchedEffect(ref, reloadKey) {
        state = LoadState.Loading("Opening…")
        val result = loader.load(ref) { message -> state = LoadState.Loading(message) }
        ((result as? LoadState.Ready)?.content as? ViewerContent.Pages)?.let { openedDocs += it.doc }
        state = result
    }
    DisposableEffect(ref) {
        onDispose { openedDocs.forEach { runCatching { it.close() } } }
    }

    val zoom = remember(ref) { PageZoomState(LazyListState()) }
    val search = remember(ref) { TextSearchState() }
    var wrap by remember(ref) { mutableStateOf<Boolean?>(null) }
    var presentingFrom by remember(ref) { mutableStateOf<Int?>(null) }

    PlatformBackHandler(enabled = presentingFrom == null) {
        if (search.active) search.active = false else onClose()
    }

    val ready = state as? LoadState.Ready
    val content = ready?.content
    val category = content?.category ?: FileTypes.categoryForName(ref.name)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = { if (search.active) search.active = false else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (search.active) {
                        SearchField(search)
                    } else {
                        Column {
                            Text(ref.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                subtitleFor(content, ready?.file?.sizeBytes, category),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    when (content) {
                        is ViewerContent.Pages -> {
                            TextButton(onClick = { zoom.zoomStep(zoomIn = false) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                            TextButton(onClick = { zoom.reset() }) { Text("${zoom.percent}%") }
                            TextButton(onClick = { zoom.zoomStep(zoomIn = true) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                            if (content.category == FileCategory.POWERPOINT) {
                                IconButton(onClick = { presentingFrom = currentPage(zoom.listState) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Present")
                                }
                            }
                        }
                        is ViewerContent.PlainText -> {
                            if (search.active) {
                                val total = search.matchLines.size
                                Text(
                                    if (search.query.isBlank()) "" else if (total == 0) "0/0" else "${search.current + 1}/$total",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(onClick = { search.previous() }) { Icon(Icons.Filled.KeyboardArrowUp, "Previous match") }
                                IconButton(onClick = { search.next() }) { Icon(Icons.Filled.KeyboardArrowDown, "Next match") }
                                IconButton(onClick = { search.query = ""; search.active = false }) { Icon(Icons.Filled.Close, "Close search") }
                            } else {
                                val wrapping = wrap ?: !content.monospace
                                TextButton(onClick = { wrap = !wrapping }) { Text(if (wrapping) "No wrap" else "Wrap") }
                                IconButton(onClick = { search.active = true }) { Icon(Icons.Filled.Search, "Find") }
                            }
                        }
                        else -> {}
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is LoadState.Loading -> LoadingView(ref.name, category, s.message)
                is LoadState.Failed -> StatusMessage(
                    title = s.title,
                    detail = s.detail,
                    badgeName = ref.name,
                    badgeCategory = category,
                    primaryLabel = "Try again",
                    onPrimary = { reloadKey++ },
                    secondaryLabel = "Back",
                    onSecondary = onClose,
                )
                is LoadState.EngineMissing -> StatusMessage(
                    title = s.status.title,
                    detail = s.status.detail,
                    badgeName = ref.name,
                    badgeCategory = s.category,
                    primaryLabel = s.status.actionLabel,
                    onPrimary = s.status.actionUrl?.let { url -> { platform.openUrl(url) } },
                    secondaryLabel = "Check again",
                    onSecondary = { reloadKey++ },
                )
                is LoadState.Ready -> when (val c = s.content) {
                    is ViewerContent.Pages -> PagesView(c.doc, c.category, zoom, platform.maxPagePixels)
                    is ViewerContent.Picture -> ImageViewer(c.image)
                    is ViewerContent.PlainText -> TextViewer(
                        text = c.text,
                        monospace = c.monospace,
                        wrap = wrap ?: !c.monospace,
                        truncated = c.truncated,
                        search = search,
                    )
                    is ViewerContent.Table -> TableViewer(c.table)
                }
            }
        }
    }

    val pages = content as? ViewerContent.Pages
    val start = presentingFrom
    if (pages != null && start != null) {
        PresentationMode(pages.doc, start, platform) { last ->
            presentingFrom = null
            zoom.listState.requestScrollToItem(last)
        }
    }
}

@Composable
private fun SearchField(search: TextSearchState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = search.query,
        onValueChange = { search.query = it },
        placeholder = { Text("Find in file") },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
}

@Composable
private fun LoadingView(name: String, category: FileCategory, message: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FileBadge(name, category, size = 64.dp)
        Spacer(Modifier.height(24.dp))
        CircularProgressIndicator(strokeWidth = 3.dp)
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (category.isOffice) {
            Spacer(Modifier.height(6.dp))
            Text(
                "First open takes a moment. After that it's instant.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun currentPage(state: LazyListState): Int {
    val info = state.layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { kotlin.math.abs((it.offset + it.size / 2) - center) }?.index
        ?: state.firstVisibleItemIndex
}

private fun subtitleFor(content: ViewerContent?, size: Long?, fallback: FileCategory): String {
    val sizeText = size?.let { " · ${formatSize(it)}" } ?: ""
    return when (content) {
        null -> fallback.displayName
        is ViewerContent.Pages -> {
            val label = if (content.category == FileCategory.UNKNOWN) "Document" else content.category.displayName
            "$label · ${plural(content.doc.pageCount, content.category.pageNoun)}$sizeText"
        }
        is ViewerContent.Picture -> "${content.category.displayName}$sizeText"
        is ViewerContent.PlainText -> "${content.category.displayName}$sizeText"
        is ViewerContent.Table -> "${plural((content.table.rows.size - 1).coerceAtLeast(0), "row")} · ${plural(content.table.columnCount, "column")}$sizeText"
    }
}
