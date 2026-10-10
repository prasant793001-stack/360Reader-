package com.reader360.app.model

enum class FileCategory(
    val displayName: String,
    val color: Long,
    val pageNoun: String,
) {
    PDF("PDF document", 0xFFE5484D, "page"),
    WORD("Document", 0xFF2F6FED, "page"),
    EXCEL("Spreadsheet", 0xFF1F9D55, "sheet"),
    POWERPOINT("Presentation", 0xFFE8590C, "slide"),
    DRAWING("Drawing", 0xFF0EA5A4, "page"),
    IMAGE("Image", 0xFF8E4EC6, "image"),
    TEXT("Text", 0xFF64748B, "line"),
    CODE("Code", 0xFF475569, "line"),
    CSV("Table", 0xFF15803D, "row"),
    UNKNOWN("File", 0xFF6B7280, "page");

    val isOffice: Boolean
        get() = this == WORD || this == EXCEL || this == POWERPOINT || this == DRAWING

    val preparingMessage: String
        get() = when (this) {
            WORD -> "Laying out pages…"
            EXCEL -> "Rendering sheets…"
            POWERPOINT -> "Preparing slides…"
            DRAWING -> "Rendering drawing…"
            else -> "Opening…"
        }
}

object FileTypes {
    private val word = setOf(
        "doc", "docx", "docm", "dot", "dotx", "dotm", "rtf", "odt", "ott", "fodt",
        "wpd", "wps", "abw", "hwp", "lwp", "pages",
    )
    private val excel = setOf(
        "xls", "xlsx", "xlsm", "xlsb", "xlt", "xltx", "xltm", "ods", "ots", "fods", "numbers", "et",
    )
    private val powerpoint = setOf(
        "ppt", "pptx", "pptm", "pps", "ppsx", "ppsm", "pot", "potx", "potm", "odp", "otp", "fodp", "key", "dps",
    )
    private val drawing = setOf("odg", "otg", "vsd", "vsdx", "vsdm", "pub", "cdr")
    private val image = setOf("jpg", "jpeg", "jpe", "jfif", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "ico")
    private val text = setOf(
        "txt", "text", "md", "markdown", "log", "ini", "cfg", "conf", "env", "properties",
        "yaml", "yml", "toml", "srt", "vtt", "nfo",
    )
    private val code = setOf(
        "json", "xml", "html", "htm", "css", "js", "mjs", "ts", "jsx", "tsx", "kt", "kts", "java", "py",
        "c", "cc", "cpp", "h", "hpp", "cs", "go", "rs", "rb", "php", "sh", "bash", "bat", "cmd", "ps1",
        "sql", "gradle", "swift", "dart", "lua", "r", "pl", "vue", "scss", "less", "svg",
    )
    private val table = setOf("csv", "tsv")

    /** Extensions shown on the home screen as examples. */
    val highlighted = listOf("PDF", "DOCX", "XLSX", "PPTX", "JPG", "PNG", "TXT", "CSV", "ODT", "RTF", "HEIC", "MD")

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot < 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase()
    }

    fun badgeText(name: String): String {
        val ext = extensionOf(name).uppercase()
        return when {
            ext.isEmpty() -> "FILE"
            ext.length <= 4 -> ext
            else -> ext.take(4)
        }
    }

    fun categoryForName(name: String): FileCategory {
        val ext = extensionOf(name)
        return when (ext) {
            "pdf" -> FileCategory.PDF
            in word -> FileCategory.WORD
            in excel -> FileCategory.EXCEL
            in powerpoint -> FileCategory.POWERPOINT
            in drawing -> FileCategory.DRAWING
            in image -> FileCategory.IMAGE
            in table -> FileCategory.CSV
            in code -> FileCategory.CODE
            in text -> FileCategory.TEXT
            else -> FileCategory.UNKNOWN
        }
    }

    /** Uses the extension first, then the file's first bytes. */
    fun detect(name: String, head: ByteArray): FileCategory {
        val byName = categoryForName(name)
        if (byName != FileCategory.UNKNOWN) return byName
        return sniff(head)
    }

    private fun sniff(head: ByteArray): FileCategory {
        fun at(i: Int) = if (i < head.size) head[i].toInt() and 0xFF else -1
        fun ascii(from: Int, s: String) = s.indices.all { at(from + it) == s[it].code }

        return when {
            ascii(0, "%PDF") -> FileCategory.PDF
            at(0) == 0x89 && ascii(1, "PNG") -> FileCategory.IMAGE
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> FileCategory.IMAGE
            ascii(0, "GIF8") -> FileCategory.IMAGE
            ascii(0, "RIFF") && ascii(8, "WEBP") -> FileCategory.IMAGE
            ascii(0, "BM") && head.size > 26 -> FileCategory.IMAGE
            ascii(4, "ftypheic") || ascii(4, "ftypmif1") || ascii(4, "ftypavif") -> FileCategory.IMAGE
            // Legacy binary Office (doc/xls/ppt): LibreOffice works out which one.
            at(0) == 0xD0 && at(1) == 0xCF && at(2) == 0x11 && at(3) == 0xE0 -> FileCategory.WORD
            ascii(0, "{\\rtf") -> FileCategory.WORD
            TextDecoding.looksLikeText(head) -> FileCategory.TEXT
            else -> FileCategory.UNKNOWN
        }
    }
}
