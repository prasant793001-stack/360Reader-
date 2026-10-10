package com.reader360.app.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.reader360.app.PdfDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.roundToInt

/** PDF pages drawn by Android's built-in PdfRenderer (PDFium under the hood). */
class AndroidPdfDoc(file: File) : PdfDoc {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try {
        PdfRenderer(descriptor)
    } catch (e: SecurityException) {
        descriptor.close()
        throw IOException("This file is password protected.", e)
    } catch (e: IOException) {
        descriptor.close()
        throw e
    }

    // PdfRenderer allows one open page at a time, so every access is serialised.
    private val lock = Mutex()
    @Volatile private var closed = false

    private val aspects: FloatArray = FloatArray(renderer.pageCount) { index ->
        val page = renderer.openPage(index)
        try {
            page.width.toFloat() / page.height.toFloat().coerceAtLeast(1f)
        } finally {
            page.close()
        }
    }

    override val pageCount: Int = renderer.pageCount

    override fun pageAspect(index: Int): Float = aspects[index]

    override suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap = withContext(Dispatchers.Default) {
        lock.withLock {
            if (closed) throw IOException("Document closed")
            val page = renderer.openPage(index)
            try {
                val width = widthPx.coerceAtLeast(1)
                val height = (width / aspects[index]).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap.asImageBitmap()
            } finally {
                page.close()
            }
        }
    }

    override fun close() {
        CoroutineScope(Dispatchers.Default).launch {
            lock.withLock {
                if (closed) return@withLock
                closed = true
                runCatching { renderer.close() }
                runCatching { descriptor.close() }
            }
        }
    }
}
