package com.reader360.app.model

/** Turns raw bytes into text, handling BOMs, UTF-16 and legacy 8-bit files. */
object TextDecoding {

    fun looksLikeText(head: ByteArray): Boolean {
        if (head.isEmpty()) return true
        if (hasUtf16Bom(head) || hasUtf8Bom(head)) return true
        val sample = head.size.coerceAtMost(4096)
        var control = 0
        for (i in 0 until sample) {
            val b = head[i].toInt() and 0xFF
            if (b == 0) return false
            if (b < 0x09 || (b in 0x0E..0x1F)) control++
        }
        return control * 20 < sample
    }

    fun decode(bytes: ByteArray): String {
        if (hasUtf8Bom(bytes)) return bytes.decodeToString(3, bytes.size)
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) return decodeUtf16(bytes, 2, littleEndian = true)
            if (b0 == 0xFE && b1 == 0xFF) return decodeUtf16(bytes, 2, littleEndian = false)
        }
        guessUtf16WithoutBom(bytes)?.let { littleEndian -> return decodeUtf16(bytes, 0, littleEndian) }
        return if (isValidUtf8(bytes)) bytes.decodeToString() else decodeWindows1252(bytes)
    }

    private fun hasUtf8Bom(b: ByteArray) =
        b.size >= 3 && (b[0].toInt() and 0xFF) == 0xEF && (b[1].toInt() and 0xFF) == 0xBB && (b[2].toInt() and 0xFF) == 0xBF

    private fun hasUtf16Bom(b: ByteArray): Boolean {
        if (b.size < 2) return false
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        return (b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)
    }

    /** Plain ASCII saved as UTF-16 has a zero in every other byte. */
    private fun guessUtf16WithoutBom(b: ByteArray): Boolean? {
        val n = b.size.coerceAtMost(2048) and 0x7FFFFFFE
        if (n < 8) return null
        var evenZeros = 0
        var oddZeros = 0
        for (i in 0 until n) {
            if (b[i].toInt() == 0) {
                if (i % 2 == 0) evenZeros++ else oddZeros++
            }
        }
        val half = n / 2
        return when {
            oddZeros > half * 0.6 && evenZeros < half * 0.1 -> true
            evenZeros > half * 0.6 && oddZeros < half * 0.1 -> false
            else -> null
        }
    }

    private fun decodeUtf16(b: ByteArray, start: Int, littleEndian: Boolean): String {
        val sb = StringBuilder((b.size - start) / 2)
        var i = start
        while (i + 1 < b.size) {
            val lo = b[i].toInt() and 0xFF
            val hi = b[i + 1].toInt() and 0xFF
            sb.append((if (littleEndian) (hi shl 8) or lo else (lo shl 8) or hi).toChar())
            i += 2
        }
        return sb.toString()
    }

    private fun isValidUtf8(b: ByteArray): Boolean {
        var i = 0
        while (i < b.size) {
            val c = b[i].toInt() and 0xFF
            val extra = when {
                c < 0x80 -> 0
                c in 0xC2..0xDF -> 1
                c in 0xE0..0xEF -> 2
                c in 0xF0..0xF4 -> 3
                else -> return false
            }
            // A sequence cut off by the end of a truncated read is still UTF-8.
            if (i + extra >= b.size) return true
            for (k in 1..extra) {
                if ((b[i + k].toInt() and 0xC0) != 0x80) return false
            }
            i += extra + 1
        }
        return true
    }

    private val cp1252High = charArrayOf(
        '€', '�', '‚', 'ƒ', '„', '…', '†', '‡',
        'ˆ', '‰', 'Š', '‹', 'Œ', '�', 'Ž', '�',
        '�', '‘', '’', '“', '”', '•', '–', '—',
        '˜', '™', 'š', '›', 'œ', '�', 'ž', 'Ÿ',
    )

    private fun decodeWindows1252(b: ByteArray): String {
        val chars = CharArray(b.size)
        for (i in b.indices) {
            val c = b[i].toInt() and 0xFF
            chars[i] = if (c in 0x80..0x9F) cp1252High[c - 0x80] else c.toChar()
        }
        return chars.concatToString()
    }
}
