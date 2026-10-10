package com.reader360.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reader360.app.model.CsvTable

private val CellWidth = 160.dp
private val RowNumberWidth = 56.dp
private val RowHeight = 36.dp

/** Spreadsheet-style grid for CSV and TSV files with a sticky header row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TableViewer(table: CsvTable, modifier: Modifier = Modifier) {
    val columns = table.columnCount.coerceAtLeast(1)
    val totalWidth = RowNumberWidth + CellWidth * columns
    val header = table.rows.firstOrNull().orEmpty()
    val body = if (table.rows.size > 1) table.rows.subList(1, table.rows.size) else emptyList()
    val stripe = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val divider = MaterialTheme.colorScheme.outlineVariant

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (table.truncated) {
            Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Large table: showing the first ${table.rows.size} rows.",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            SelectionContainer {
                LazyColumn(Modifier.width(totalWidth).fillMaxHeight()) {
                    stickyHeader(key = "header") {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column {
                                TableRow(rowLabel = "", cells = header, columns = columns, bold = true)
                                HorizontalDivider(color = divider)
                            }
                        }
                    }
                    items(count = body.size) { i ->
                        Column(Modifier.background(if (i % 2 == 1) stripe else MaterialTheme.colorScheme.surface)) {
                            TableRow(rowLabel = (i + 2).toString(), cells = body[i], columns = columns, bold = false)
                            HorizontalDivider(color = divider.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(rowLabel: String, cells: List<String>, columns: Int, bold: Boolean) {
    Row(Modifier.height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
        Text(
            rowLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(RowNumberWidth).padding(end = 10.dp),
        )
        for (c in 0 until columns) {
            Text(
                cells.getOrElse(c) { "" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(CellWidth).padding(horizontal = 10.dp),
            )
        }
    }
}
