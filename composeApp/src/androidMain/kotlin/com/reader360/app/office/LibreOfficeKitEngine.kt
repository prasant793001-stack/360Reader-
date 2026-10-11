package com.reader360.app.office

import android.app.Activity
import com.reader360.app.BuildConfig
import com.reader360.app.EngineStatus
import com.reader360.app.LocalFile
import com.reader360.app.OfficeEngine
import com.reader360.app.model.FileCategory
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.libreoffice.kit.Document
import org.libreoffice.kit.LibreOfficeKit
import org.libreoffice.kit.Office
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Renders Office documents to PDF on the device with LibreOfficeKit, the same
 * engine as LibreOffice desktop. Layout, fonts and pagination therefore match
 * what Word, Excel and PowerPoint produce far more closely than a hand-written
 * parser could.
 *
 * LibreOfficeKit is single-threaded: every call runs on one dedicated thread.
 */
class LibreOfficeKitEngine(private val activity: Activity) : OfficeEngine {

    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LibreOfficeKit").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var office: Office? = null
    @Volatile private var startupError: String? = null

    override fun status(): EngineStatus {
        if (!BuildConfig.LO_ENGINE_BUNDLED) {
            return EngineStatus.Unavailable(
                title = "Office engine not in this build",
                detail = "PDFs, images, text and CSV files open normally. Word, Excel and PowerPoint need the " +
                    "LibreOffice engine added to the project once, then a rebuild. See “Android office engine” in README.md.",
            )
        }
        startupError?.let {
            return EngineStatus.Unavailable(
                title = "Office engine couldn't start",
                detail = it,
            )
        }
        return EngineStatus.Ready
    }

    override suspend fun convertToPdf(input: LocalFile, category: FileCategory, outputPath: String): Unit =
        withContext(dispatcher) {
            val office = ensureStarted()
            val output = File(outputPath)
            output.parentFile?.mkdirs()

            // LibreOffice takes URLs, so work on copies with plain ASCII names:
            // spaces, '#', '%' or non-Latin letters in a file name would break them.
            val work = File(activity.cacheDir, "lo-work").apply { deleteRecursively(); mkdirs() }
            val ext = input.name.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(6)
            val source = File(work, if (ext.isEmpty()) "document" else "document.$ext")
            File(input.path).copyTo(source, overwrite = true)
            val rendered = File(work, "rendered.pdf")

            val document = office.documentLoad(source.absolutePath)
                ?: throw IOException(
                    office.error?.takeIf { it.isNotBlank() }?.let { "LibreOffice couldn't read this file: $it" }
                        ?: "LibreOffice couldn't read this file. It may be damaged or password protected."
                )
            try {
                val isSpreadsheet = category == FileCategory.EXCEL ||
                    document.documentType == Document.DOCTYPE_SPREADSHEET
                // Each sheet becomes one page sized to its content, instead of
                // being chopped into printer-sized pieces.
                val options = if (isSpreadsheet) SINGLE_PAGE_SHEETS else ""
                document.saveAs("file://" + rendered.absolutePath, "pdf", options)
            } finally {
                document.destroy()
            }

            if (!rendered.isFile || rendered.length() == 0L) throw IOException("Rendering produced no pages.")
            output.delete()
            if (!rendered.renameTo(output)) rendered.copyTo(output, overwrite = true)
            work.deleteRecursively()
        }

    private fun ensureStarted(): Office {
        office?.let { return it }
        try {
            unpackRuntimeFiles()
            LibreOfficeKit.putenv("SAL_LOG=-WARN-INFO")
            LibreOfficeKit.putenv("SAL_LOK_OPTIONS=compact_fonts")
            LibreOfficeKit.putenv("TMPDIR=" + activity.cacheDir.absolutePath)
            LibreOfficeKit.init(activity)
            val handle = LibreOfficeKit.getLibreOfficeKitHandle()
                ?: throw IOException("LibreOfficeKit returned no handle")
            return Office(handle).also { office = it }
        } catch (e: Throwable) {
            val message = when (e) {
                is UnsatisfiedLinkError -> "The engine's native libraries are missing or built for a different CPU (${e.message})."
                else -> e.message ?: e.toString()
            }
            startupError = message
            throw IOException(message, e)
        }
    }

    /**
     * LibreOffice reads its configuration (sofficerc, fonts.conf, …) from real
     * files in the app's data folder, so the "unpack" part of the engine's
     * assets is copied out once per install, exactly like LibreOffice's own app.
     */
    private fun unpackRuntimeFiles() {
        val dataDir = File(activity.applicationInfo.dataDir)
        val stamp = runCatching {
            activity.packageManager.getPackageInfo(activity.packageName, 0).lastUpdateTime.toString()
        }.getOrDefault("0")
        val marker = File(dataDir, ".engine-unpacked")
        if (marker.isFile && marker.readText() == stamp) return
        val assets = activity.assets
        val top = assets.list("unpack").orEmpty()
        if (top.isEmpty()) throw IOException("The office engine's runtime files are missing from this build.")
        for (name in top) copyAsset(assets, "unpack/$name", File(dataDir, name))
        marker.writeText(stamp)
    }

    private fun copyAsset(assets: android.content.res.AssetManager, path: String, target: File) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            target.parentFile?.mkdirs()
            assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } }
        } else {
            target.mkdirs()
            for (child in children) copyAsset(assets, "$path/$child", File(target, child))
        }
    }

    private companion object {
        const val SINGLE_PAGE_SHEETS = """{"SinglePageSheets":{"type":"boolean","value":"true"}}"""
    }
}
