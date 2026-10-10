package com.reader360.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader360.app.DocumentRef
import com.reader360.app.EngineStatus
import com.reader360.app.data.RecentEntry
import com.reader360.app.model.FileCategory
import com.reader360.app.model.FileTypes

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    recents: List<RecentEntry>,
    engineStatus: EngineStatus,
    isDesktop: Boolean,
    now: Long,
    onOpenFile: () -> Unit,
    onOpenRecent: (DocumentRef) -> Unit,
    onRemoveRecent: (DocumentRef) -> Unit,
    onClearRecents: () -> Unit,
    onEngineAction: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        ) {
            item("header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogo(48.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("360Reader", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Every file, exactly as it was made.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(28.dp))
            }

            item("open") {
                OpenCard(isDesktop = isDesktop, onOpenFile = onOpenFile)
                Spacer(Modifier.height(16.dp))
            }

            if (engineStatus is EngineStatus.Unavailable) {
                item("engine") {
                    EngineNotice(engineStatus, onEngineAction)
                    Spacer(Modifier.height(16.dp))
                }
            }

            item("recent-header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (recents.isNotEmpty()) {
                        TextButton(onClick = onClearRecents) { Text("Clear") }
                    }
                }
            }

            if (recents.isEmpty()) {
                item("recent-empty") {
                    Text(
                        "Files you open will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(recents, key = { it.ref.id }) { entry ->
                    RecentRow(
                        entry = entry,
                        now = now,
                        onClick = { onOpenRecent(entry.ref) },
                        onRemove = { onRemoveRecent(entry.ref) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenCard(isDesktop: Boolean, onOpenFile: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(22.dp)) {
            Text("Open any document", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                if (isDesktop) {
                    "Word, Excel, PowerPoint, PDF, images and text, shown with their original layout. You can also drop a file anywhere in this window."
                } else {
                    "Word, Excel, PowerPoint, PDF, images and text, shown with their original layout."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onOpenFile,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Open a file", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(18.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FileTypes.highlighted.forEach { ext ->
                    val category = FileTypes.categoryForName("x.${ext.lowercase()}")
                    FormatChip(ext, Color(category.color))
                }
            }
        }
    }
}

@Composable
private fun FormatChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, style = BadgeTextStyle, color = color)
    }
}

@Composable
private fun EngineNotice(status: EngineStatus.Unavailable, onAction: (String) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(status.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    status.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val url = status.actionUrl
                if (status.actionLabel != null && url != null) {
                    TextButton(onClick = { onAction(url) }, contentPadding = PaddingValues(0.dp)) {
                        Text(status.actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentRow(
    entry: RecentEntry,
    now: Long,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = FileTypes.categoryForName(entry.ref.name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileBadge(entry.ref.name, category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.ref.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${if (category == FileCategory.UNKNOWN) "File" else category.displayName} · ${formatTimeAgo(entry.openedAt, now)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove from recent",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
