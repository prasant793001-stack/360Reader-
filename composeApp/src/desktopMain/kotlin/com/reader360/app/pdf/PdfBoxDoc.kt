package com.reader360.app.pdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.reader360.app.PdfDoc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.RenderingHints
import java.io.File
import java.io.IOException

/** PDF pages drawn with Apache PDFBox. */
class PdfBoxDoc(file: File) : PdfDoc {
    private val document: PDDocument = Loader.loadPDF(file)
    private val renderer = PDFRenderer(document).apply {
        isSubsamplingAllowed = false
        renderingHints = RenderingHints(
            mapOf(
                RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
                RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY,
                RenderingHints.KEY_INTERPOLATION to RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE,
            )
        )
    }

    // PDDocument isn't thread-safe; render one page at a time.
    private val lock = Mutex()
    @Volatile private var closed = false

    /** Page width in points as displayed (after /Rotate). */
    private val widths = FloatArray(document.numberOfPages)
    private val aspects = FloatArray(document.numberOfPages)

    init {
        for (i in 0 until document.numberOfPages) {
            val page = document.getPage(i)
            val box = page.cropBox
            val rotated = (page.rotation % 180) != 0
            val w = if (rotated) box.height else box.width
            val h = if (rotated) box.width else box.height
            widths[i] = w.coerceAtLeast(1f)
            aspects[i] = w.coerceAtLeast(1f) / h.coerceAtLeast(1f)
        }
    }

    override val pageCount: Int = document.numberOfPages

    override fun pageAspect(index: Int): Float = aspects[index]

    override suspend fun renderPage(index: Int, widthPx: Int): ImageBitmap = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed) throw IOException("Document closed")
            val scale = widthPx.coerceAtLeast(1) / widths[index]
            renderer.renderImage(index, scale, ImageType.RGB).toComposeImageBitmap()
        }
    }

    override fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            lock.withLock {
                if (closed) return@withLock
                closed = true
                runCatching { document.close() }
            }
        }
    }
}
