package com.reader360.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.reader360.app.office.LibreOfficeDesktopEngine
import com.reader360.app.pdf.PdfBoxDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.Desktop
import java.awt.FileDialog
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.net.URI
import java.security.MessageDigest

class DesktopPlatform(private val windowState: WindowState) : Platform {

    var window: ComposeWindow? = null

    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    private val dataDir: File = run {
        val base = if (isWindows) System.getenv("APPDATA")?.let(::File) else null
        File(base ?: File(System.getProperty("user.home"), ".config"), "360Reader").apply { mkdirs() }
    }

    private val cacheRoot: File = run {
        val base = if (isWindows) System.getenv("LOCALAPPDATA")?.let(::File) else null
        File(base ?: File(System.getProperty("user.home"), ".cache"), "360Reader").apply { mkdirs() }
    }

    override val isDesktop: Boolean = true
    override val cacheDir: String = File(cacheRoot, "Cache").apply { mkdirs() }.absolutePath
    override val office: OfficeEngine = LibreOfficeDesktopEngine(profileDir = File(cacheRoot, "engine-profile"))
    override val maxPagePixels: Int = 24_000_000

    private var placementBeforeFullscreen = WindowPlacement.Floating

    override fun now(): Long = System.currentTimeMillis()

    override suspend fun pickFile(): DocumentRef? = withContext(Dispatchers.Main) {
        val dialog = FileDialog(window, "Open a file", FileDialog.LOAD)
        dialog.isMultipleMode = false
        dialog.isVisible = true // blocks until the user chooses
        val dir = dialog.directory
        val name = dialog.file
        if (dir == null || name == null) null else refFor(File(dir, name))
    }

    override suspend fun materialize(ref: DocumentRef): LocalFile = withContext(Dispatchers.IO) {
        val file = File(ref.id)
        if (!file.isFile) throw FileNotFoundException("File not found: ${file.path}")
        // Originals stay where they are; path + size + modified time identify a version.
        val fingerprint = sha1("${file.absolutePath}|${file.length()}|${file.lastModified()}")
        LocalFile(file.absolutePath, file.name, file.length(), fingerprint)
    }

    override suspend fun readBytes(path: String, limit: Long): ByteArray = withContext(Dispatchers.IO) {
        RandomAccessFile(path, "r").use { raf ->
            val size = minOf(raf.length(), limit).toInt()
            ByteArray(size).also { raf.readFully(it) }
        }
    }

    override suspend fun fileExists(path: String): Boolean = withContext(Dispatchers.IO) {
        File(path).let { it.isFile && it.length() > 0 }
    }

    override suspend fun openPdf(path: String): PdfDoc = withContext(Dispatchers.IO) { PdfBoxDoc(File(path)) }

    override suspend fun decodeImage(path: String, maxDimension: Int): DecodedImage = withContext(Dispatchers.IO) {
        val bytes = File(path).readBytes()
        val decoded = runCatching { Image.makeFromEncoded(bytes) }.getOrNull()
            ?: throw IllegalStateException(
                if (path.endsWith(".heic", true) || path.endsWith(".heif", true)) {
                    "HEIC photos can't be decoded on Windows yet. Convert it to JPG, or open it on your phone."
                } else {
                    "This image format couldn't be decoded."
                }
            )
        val upright = applyExifOrientation(decoded, ExifOrientation.read(bytes))
        // Desktop shows full resolution for sharp zooming, unless the image is enormous.
        val limit = maxOf(maxDimension * 2, 8192)
        val finalImage = if (maxOf(upright.width, upright.height) > limit) downscale(upright, limit) else upright
        DecodedImage(finalImage.toComposeImageBitmap(), finalImage.width, finalImage.height)
    }

    private fun applyExifOrientation(image: Image, orientation: Int): Image {
        val degrees = when (orientation) {
            3 -> 180f
            6 -> 90f
            8 -> 270f
            else -> return image
        }
        val swap = degrees != 180f
        val w = if (swap) image.height else image.width
        val h = if (swap) image.width else image.height
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.apply {
            translate(w / 2f, h / 2f)
            rotate(degrees)
            translate(-image.width / 2f, -image.height / 2f)
            drawImage(image, 0f, 0f)
        }
        return surface.makeImageSnapshot()
    }

    private fun downscale(image: Image, limit: Int): Image {
        val ratio = limit.toFloat() / maxOf(image.width, image.height)
        val w = (image.width * ratio).toInt().coerceAtLeast(1)
        val h = (image.height * ratio).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(image, Rect.makeWH(w.toFloat(), h.toFloat()))
        return surface.makeImageSnapshot()
    }

    override suspend fun readAppFile(name: String): String? = withContext(Dispatchers.IO) {
        File(dataDir, name).takeIf { it.isFile }?.readText()
    }

    override suspend fun writeAppFile(name: String, content: String) = withContext(Dispatchers.IO) {
        File(dataDir, name).writeText(content)
    }

    override fun setFullscreen(enabled: Boolean) {
        if (enabled) {
            placementBeforeFullscreen = windowState.placement
            windowState.placement = WindowPlacement.Fullscreen
        } else {
            windowState.placement = placementBeforeFullscreen
        }
    }

    override fun openUrl(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    companion object {
        fun refFor(file: File): DocumentRef = DocumentRef(id = file.absolutePath, name = file.name)

        fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Windows has no system back gesture; Esc is handled where it matters.
}

actual fun Modifier.ctrlWheelZoom(onZoom: (factor: Float, position: Offset) -> Unit): Modifier =
    pointerInput(onZoom) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll || !event.keyboardModifiers.isCtrlPressed) continue
                val change = event.changes.firstOrNull() ?: continue
                val dy = change.scrollDelta.y
                if (dy == 0f) continue
                onZoom(if (dy < 0f) 1.12f else 1f / 1.12f, change.position)
                event.changes.forEach { it.consume() }
            }
        }
    }
