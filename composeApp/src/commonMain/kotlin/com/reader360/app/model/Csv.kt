package com.reader360.app.model

data class CsvTable(val rows: List<List<String>>, val columnCount: Int, val truncated: Boolean)

/** RFC 4180-style CSV/TSV parser with delimiter detection. */
object Csv {
    const val MAX_ROWS = 100_000
    const val MAX_COLUMNS = 256

    fun parse(text: String, forceTab: Boolean = false): CsvTable {
        val delimiter = if (forceTab) '\t' else detectDelimiter(text)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var truncated = false
        var i = 0
        val n = text.length

        fun endCell() {
            if (row.size < MAX_COLUMNS) row.add(cell.toString())
            cell.setLength(0)
        }

        fun endRow(): Boolean {
            endCell()
            rows.add(row)
            row = ArrayList()
            if (rows.size >= MAX_ROWS) {
                truncated = true
                return false
            }
            return true
        }

        while (i < n) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text[i + 1] == '"') {
                        cell.append('"'); i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cell.append(c)
                }
            } else {
                when (c) {
                    '"' -> if (cell.isEmpty()) {
                        inQuotes = true
                    } else {
                        cell.append(c)
                    }
                    delimiter -> endCell()
                    '\r' -> {}
                    '\n' -> if (!endRow()) break
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (!truncated && (cell.isNotEmpty() || row.isNotEmpty())) endRow()

        val columns = rows.maxOfOrNull { it.size } ?: 0
        return CsvTable(rows, columns, truncated)
    }

    private fun detectDelimiter(text: String): Char {
        val sample = text.take(8192).lineSequence().take(10).toList()
        val candidates = listOf(',', ';', '\t', '|')
        return candidates.maxByOrNull { d ->
            val counts = sample.map { line -> line.count { it == d } }
            val nonZero = counts.filter { it > 0 }
            if (nonZero.isEmpty()) 0 else nonZero.size * 1000 + nonZero.min()
        } ?: ','
    }
}
