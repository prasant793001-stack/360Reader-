package com.reader360.app.data

import com.reader360.app.DecodedImage
import com.reader360.app.DocumentRef
import com.reader360.app.EngineStatus
import com.reader360.app.LocalFile
import com.reader360.app.PdfDoc
import com.reader360.app.Platform
import com.reader360.app.model.Csv
import com.reader360.app.model.CsvTable
import com.reader360.app.model.FileCategory
import com.reader360.app.model.FileTypes
import com.reader360.app.model.TextDecoding
import kotlinx.coroutines.CancellationException

sealed interface ViewerContent {
    val category: FileCategory

    data class Pages(val doc: PdfDoc, override val category: FileCategory) : ViewerContent
    data class Picture(val image: DecodedImage, override val category: FileCategory) : ViewerContent
    data class PlainText(
        val text: String,
        val monospace: Boolean,
        val truncated: Boolean,
        override val category: FileCategory,
    ) : ViewerContent
    data class Table(val table: CsvTable, override val category: FileCategory) : ViewerContent
}

sealed interface LoadState {
    data class Loading(val message: String) : LoadState
    data class Ready(val content: ViewerContent, val file: LocalFile) : LoadState
    data class EngineMissing(val status: EngineStatus.Unavailable, val category: FileCategory) : LoadState
    data class Failed(val title: String, val detail: String) : LoadState
}

/** Decides how to show a file and prepares it. */
class DocumentLoader(private val platform: Platform) {

    suspend fun load(ref: DocumentRef, progress: (String) -> Unit): LoadState {
        return try {
            progress("Opening…")
            val file = platform.materialize(ref)
            val head = platform.readBytes(file.path, HEAD_BYTES.toLong())
            val category = FileTypes.detect(file.name, head)
            when {
                category == FileCategory.PDF ->
                    LoadState.Ready(ViewerContent.Pages(platform.openPdf(file.path), category), file)

                category.isOffice -> renderOffice(file, category, progress)

                category == FileCategory.IMAGE ->
                    LoadState.Ready(ViewerContent.Picture(platform.decodeImage(file.path, MAX_IMAGE_DIMENSION), category), file)

                category == FileCategory.CSV -> {
                    val (text, _) = readText(file)
                    val table = Csv.parse(text, forceTab = FileTypes.extensionOf(file.name) == "tsv")
                    LoadState.Ready(ViewerContent.Table(table, category), file)
                }

                category == FileCategory.TEXT || category == FileCategory.CODE -> {
                    val (text, truncated) = readText(file)
                    LoadState.Ready(
                        ViewerContent.PlainText(text, monospace = category == FileCategory.CODE, truncated, category),
                        file,
                    )
                }

                // Unknown extension: LibreOffice recognises hundreds of formats by content.
                platform.office.status() is EngineStatus.Ready -> renderOffice(file, FileCategory.UNKNOWN, progress)

                else -> LoadState.Failed(
                    "Can't show this file",
                    "“${file.name}” isn't a format 360Reader recognises.",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            LoadState.Failed("Couldn't open this file", friendlyMessage(e))
        }
    }

    private suspend fun renderOffice(file: LocalFile, category: FileCategory, progress: (String) -> Unit): LoadState {
        val status = platform.office.status()
        if (status is EngineStatus.Unavailable) return LoadState.EngineMissing(status, category)

        val output = "${platform.cacheDir}/rendered/${file.fingerprint}-$RENDER_VERSION.pdf"
        if (!platform.fileExists(output)) {
            progress(category.preparingMessage)
            platform.office.convertToPdf(file, category, output)
        }
        progress("Opening…")
        return LoadState.Ready(ViewerContent.Pages(platform.openPdf(output), category), file)
    }

    private suspend fun readText(file: LocalFile): Pair<String, Boolean> {
        val bytes = platform.readBytes(file.path, MAX_TEXT_BYTES)
        return TextDecoding.decode(bytes) to (file.sizeBytes > MAX_TEXT_BYTES)
    }

    private fun friendlyMessage(e: Throwable): String {
        val raw = (e.message ?: "").lowercase()
        return when {
            "password" in raw || e::class.simpleName?.contains("Password") == true ->
                "This file is password protected."
            "no such file" in raw || "not found" in raw || "enoent" in raw ->
                "The file was moved, deleted, or is no longer shared with 360Reader."
            "permission" in raw || "security" in raw ->
                "360Reader doesn't have permission to read this file. Open it again from your files."
            e.message.isNullOrBlank() -> "Something went wrong (${e::class.simpleName})."
            else -> e.message!!
        }
    }

    private companion object {
        const val HEAD_BYTES = 8192
        const val MAX_TEXT_BYTES = 8L * 1024 * 1024
        const val MAX_IMAGE_DIMENSION = 4096

        // Bump to invalidate every cached render after changing export settings.
        const val RENDER_VERSION = "r1"
    }
}
