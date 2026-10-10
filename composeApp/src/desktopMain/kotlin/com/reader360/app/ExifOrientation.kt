package com.reader360.app

/**
 * Reads the EXIF orientation tag from a JPEG so phone photos display upright.
 * Returns 1 (normal) when the tag is missing or the file isn't a JPEG.
 */
internal object ExifOrientation {
    fun read(bytes: ByteArray): Int {
        fun u8(i: Int) = bytes[i].toInt() and 0xFF
        if (bytes.size < 4 || u8(0) != 0xFF || u8(1) != 0xD8) return 1

        var offset = 2
        while (offset + 4 < bytes.size) {
            if (u8(offset) != 0xFF) return 1
            val marker = u8(offset + 1)
            val length = (u8(offset + 2) shl 8) or u8(offset + 3)
            if (marker == 0xE1 && offset + 10 < bytes.size &&
                bytes[offset + 4] == 'E'.code.toByte() && bytes[offset + 5] == 'x'.code.toByte()
            ) {
                return parseTiff(bytes, offset + 10, offset + 2 + length)
            }
            if (marker == 0xDA || length < 2) return 1 // start of image data: no EXIF
            offset += 2 + length
        }
        return 1
    }

    private fun parseTiff(b: ByteArray, start: Int, end: Int): Int {
        if (start + 8 > b.size) return 1
        val little = b[start] == 'I'.code.toByte()
        fun u16(i: Int): Int {
            if (i + 1 >= b.size) return 0
            val a = b[i].toInt() and 0xFF
            val c = b[i + 1].toInt() and 0xFF
            return if (little) (c shl 8) or a else (a shl 8) or c
        }
        fun u32(i: Int): Int = if (little) (u16(i + 2) shl 16) or u16(i) else (u16(i) shl 16) or u16(i + 2)

        val ifd = start + u32(start + 4)
        if (ifd + 2 > minOf(end, b.size)) return 1
        val entries = u16(ifd)
        for (e in 0 until entries) {
            val entry = ifd + 2 + e * 12
            if (entry + 12 > b.size) return 1
            if (u16(entry) == 0x0112) {
                val value = u16(entry + 8)
                return if (value in 1..8) value else 1
            }
        }
        return 1
    }
}
