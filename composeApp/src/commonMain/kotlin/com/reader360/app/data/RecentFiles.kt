package com.reader360.app.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.reader360.app.DocumentRef
import com.reader360.app.Platform

data class RecentEntry(val ref: DocumentRef, val openedAt: Long)

/** Remembers recently opened files in a small tab-separated file. */
class RecentFiles(private val platform: Platform) {
    val items: SnapshotStateList<RecentEntry> = mutableStateListOf()

    suspend fun load() {
        val text = runCatching { platform.readAppFile(FILE) }.getOrNull() ?: return
        val parsed = text.lineSequence()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 3) return@mapNotNull null
                val time = parts[0].toLongOrNull() ?: return@mapNotNull null
                RecentEntry(DocumentRef(id = unescape(parts[1]), name = unescape(parts[2])), time)
            }
            .toList()
        items.clear()
        items.addAll(parsed)
    }

    suspend fun add(ref: DocumentRef) {
        items.removeAll { it.ref.id == ref.id }
        items.add(0, RecentEntry(ref, platform.now()))
        while (items.size > MAX) items.removeAt(items.lastIndex)
        save()
    }

    suspend fun remove(ref: DocumentRef) {
        items.removeAll { it.ref.id == ref.id }
        save()
    }

    suspend fun clear() {
        items.clear()
        save()
    }

    private suspend fun save() {
        val text = items.joinToString("\n") { "${it.openedAt}\t${escape(it.ref.id)}\t${escape(it.ref.name)}" }
        runCatching { platform.writeAppFile(FILE, text) }
    }

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private companion object {
        const val FILE = "recent-files.tsv"
        const val MAX = 50
    }
}
