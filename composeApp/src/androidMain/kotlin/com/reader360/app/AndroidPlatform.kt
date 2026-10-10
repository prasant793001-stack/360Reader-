package com.reader360.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.reader360.app.office.LibreOfficeKitEngine
import com.reader360.app.pdf.AndroidPdfDoc
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.security.MessageDigest

class AndroidPlatform(
    private val activity: ComponentActivity,
    private val launchPicker: () -> Unit,
) : Platform {

    override val isDesktop: Boolean = false
    override val cacheDir: String = activity.cacheDir.absolutePath
    override val office: OfficeEngine = LibreOfficeKitEngine(activity)
    override val maxPagePixels: Int = 10_000_000

    private var pendingPick: CompletableDeferred<Uri?>? = null

    override fun now(): Long = System.currentTimeMillis()

    override suspend fun pickFile(): DocumentRef? {
        val result = CompletableDeferred<Uri?>()
        pendingPick?.complete(null)
        pendingPick = result
        withContext(Dispatchers.Main) { launchPicker() }
        val uri = result.await() ?: return null
        // Keep access so the file can be reopened from Recent after a restart.
        runCatching {
            activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return refFor(uri)
    }

    fun onFilePicked(uri: Uri?) {
        pendingPick?.complete(uri)
        pendingPick = null
    }

    fun refFor(uri: Uri): DocumentRef = DocumentRef(id = uri.toString(), name = displayName(uri))

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            runCatching {
                activity.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val name = c.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Document"
    }

    override suspend fun materialize(ref: DocumentRef): LocalFile = withContext(Dispatchers.IO) {
        val uri = Uri.parse(ref.id)
        if (uri.scheme == "file" || uri.scheme == null) {
            val file = File(uri.path ?: ref.id)
            if (!file.isFile) throw FileNotFoundException("File not found")
            return@withContext LocalFile(file.absolutePath, ref.name, file.length(), sha1(file))
        }

        // Content URIs: copy into the cache so every engine gets a real file path.
        val dir = File(activity.cacheDir, "open").apply { mkdirs() }
        val safeName = ref.name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").ifBlank { "document" }
        val target = File(dir, "${Integer.toHexString(ref.id.hashCode())}-$safeName")
        val input = activity.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("File not found")
        input.use { src -> target.outputStream().use { dst -> src.copyTo(dst, 256 * 1024) } }

        // Tidy up copies from earlier sessions.
        val cutoff = System.currentTimeMillis() - 6 * 60 * 60 * 1000L
        dir.listFiles()?.forEach { if (it != target && it.lastModified() < cutoff) it.delete() }

        LocalFile(target.absolutePath, ref.name, target.length(), sha1(target))
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

    override suspend fun openPdf(path: String): PdfDoc = withContext(Dispatchers.IO) { AndroidPdfDoc(File(path)) }

    override suspend fun decodeImage(path: String, maxDimension: Int): DecodedImage = withContext(Dispatchers.IO) {
        val file = File(path)
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies EXIF rotation and decodes HEIC/AVIF/WebP natively.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                val h = info.size.height
                val largest = maxOf(w, h)
                if (largest > maxDimension) {
                    val ratio = maxDimension.toFloat() / largest
                    decoder.setTargetSize((w * ratio).toInt().coerceAtLeast(1), (h * ratio).toInt().coerceAtLeast(1))
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IllegalStateException("This image format isn't supported on this Android version.")
        }
        DecodedImage(bitmap.asImageBitmap(), bitmap.width, bitmap.height)
    }

    override suspend fun readAppFile(name: String): String? = withContext(Dispatchers.IO) {
        File(activity.filesDir, name).takeIf { it.isFile }?.readText()
    }

    override suspend fun writeAppFile(name: String, content: String) = withContext(Dispatchers.IO) {
        val target = File(activity.filesDir, name)
        val tmp = File(activity.filesDir, "$name.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }

    override fun setFullscreen(enabled: Boolean) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun openUrl(url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun sha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

actual fun Modifier.ctrlWheelZoom(onZoom: (factor: Float, position: Offset) -> Unit): Modifier = this
