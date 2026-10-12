package com.reader360.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import com.reader360.app.model.FileCategory

/**
 * A file the user asked to open. [id] is a content:// URI on Android and an
 * absolute path on Windows. [name] is the display name including extension.
 */
data class DocumentRef(val id: String, val name: String)

/**
 * A readable copy of a document on local disk.
 * [fingerprint] changes whenever the file's bytes change, so converted
 * output can be cached safely.
 */
data class LocalFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val fingerprint: String,
)

data class DecodedImage(val bitmap: ImageBitmap, val width: Int, val height: Int)

/** A paged document (a real PDF, or an Office file rendered to PDF). */
interface PdfDoc {
    val pageCount: Int

    /** Width divided by height of the page, already accounting for rotation. */
    fun pageAspect(index: Int): Float

    /** Renders one page to a bitmap [widthPx] wide. Safe to call from any coroutine. */
    suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap

    fun close()
}

sealed interface EngineStatus {
    data object Ready : EngineStatus

    data class Unavailable(
        val title: String,
        val detail: String,
        val actionLabel: String? = null,
        val actionUrl: String? = null,
    ) : EngineStatus
}

/** Renders Word, Excel, PowerPoint and drawing files to PDF with LibreOffice. */
interface OfficeEngine {
    fun status(): EngineStatus

    suspend fun convertToPdf(input: LocalFile, category: FileCategory, outputPath: String)
}

/** Everything the shared app needs from Android or Windows. */
interface Platform {
    val isDesktop: Boolean
    val cacheDir: String
    val office: OfficeEngine

    /** Largest bitmap (in pixels) the viewer may render for a single page. */
    val maxPagePixels: Int

    fun now(): Long

    suspend fun pickFile(): DocumentRef?

    suspend fun materialize(ref: DocumentRef): LocalFile

    /**
     * Keeps 360Reader's own copy of a file that was only lent to us (e.g. opened
     * from WhatsApp), so it can be reopened from Recent later. Returns the ref
     * to remember; files we can already reopen are returned unchanged.
     */
    suspend fun keepCopy(ref: DocumentRef): DocumentRef

    /** Deletes kept copies that are no longer in the Recent list. */
    suspend fun pruneKeptCopies(stillUsed: Set<String>)

    /** Lets the user save a copy of [file] somewhere. Returns a message to show, or null if cancelled. */
    suspend fun saveCopy(file: LocalFile): String?

    val canShare: Boolean

    /** Opens the system share sheet for [file]. */
    suspend fun share(file: LocalFile)

    suspend fun readBytes(path: String, limit: Long): ByteArray

    suspend fun fileExists(path: String): Boolean

    suspend fun openPdf(path: String): PdfDoc

    suspend fun decodeImage(path: String, maxDimension: Int): DecodedImage

    suspend fun readAppFile(name: String): String?

    suspend fun writeAppFile(name: String, content: String)

    fun setFullscreen(enabled: Boolean)

    fun openUrl(url: String)
}

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** Ctrl + mouse wheel zoom on Windows; does nothing on touch devices. */
expect fun Modifier.ctrlWheelZoom(onZoom: (factor: Float, position: Offset) -> Unit): Modifier
