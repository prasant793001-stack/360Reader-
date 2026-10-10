package com.reader360.app.office

import com.reader360.app.EngineStatus
import com.reader360.app.LocalFile
import com.reader360.app.OfficeEngine
import com.reader360.app.model.FileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * Renders Office documents to PDF by running LibreOffice headless.
 *
 * Looks for LibreOffice in this order:
 *  1. READER360_SOFFICE environment variable (full path to soffice.exe)
 *  2. bundled with the app:  <install dir>/app/resources/libreoffice/program/soffice.exe
 *  3. a normal LibreOffice installation under Program Files
 *  4. soffice on the PATH
 *
 * A private LibreOffice profile is used, so it never clashes with a
 * LibreOffice window you have open.
 */
class LibreOfficeDesktopEngine(private val profileDir: File) : OfficeEngine {

    private val lock = Mutex()
    private val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    override fun status(): EngineStatus =
        if (locateSoffice() != null) {
            EngineStatus.Ready
        } else {
            EngineStatus.Unavailable(
                title = "Install the free office engine",
                detail = "PDFs, images, text and CSV files open now. To show Word, Excel and PowerPoint files with " +
                    "their exact layout, 360Reader uses LibreOffice (free). Install it once, then tap Check again.",
                actionLabel = "Download LibreOffice",
                actionUrl = "https://www.libreoffice.org/download/download-libreoffice/",
            )
        }

    override suspend fun convertToPdf(input: LocalFile, category: FileCategory, outputPath: String): Unit =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val soffice = locateSoffice() ?: throw IOException("LibreOffice isn't installed.")
                val output = File(outputPath).apply { parentFile?.mkdirs() }
                val workDir = Files.createTempDirectory("reader360-render").toFile()
                try {
                    val produced = if (category == FileCategory.EXCEL) {
                        // One page per sheet, sized to the sheet. Older LibreOffice
                        // versions don't know the option, so fall back gracefully.
                        runCatching { convertOnce(soffice, input, workDir, SPREADSHEET_TARGET) }
                            .getOrElse { convertOnce(soffice, input, workDir, "pdf") }
                    } else {
                        convertOnce(soffice, input, workDir, "pdf")
                    }
                    Files.move(produced.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    Unit
                } finally {
                    workDir.deleteRecursively()
                }
            }
        }

    private suspend fun convertOnce(soffice: File, input: LocalFile, workDir: File, target: String): File {
        workDir.listFiles()?.forEach { it.delete() }
        val log = File(workDir, "engine.log")
        val command = listOf(
            soffice.absolutePath,
            "--headless",
            "--invisible",
            "--norestore",
            "--nologo",
            "--nodefault",
            "--nolockcheck",
            "--nofirststartwizard",
            "-env:UserInstallation=" + profileDir.toPath().toUri().toString(),
            "--convert-to",
            commandLineSafe(target),
            "--outdir",
            workDir.absolutePath,
            input.path,
        )
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(log)
            .start()

        val finished = runInterruptible { process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        if (!finished) {
            process.destroyForcibly()
            throw IOException("The office engine took too long with this file.")
        }
        val pdf = workDir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
        if (pdf == null || pdf.length() == 0L) {
            val details = log.takeIf { it.isFile }?.readText()?.trim()?.lines()?.lastOrNull().orEmpty()
            throw IOException(
                "LibreOffice couldn't convert this file." + if (details.isNotBlank()) " ($details)" else ""
            )
        }
        return pdf
    }

    /** Windows re-parses the command line, so JSON quotes must be escaped. */
    private fun commandLineSafe(arg: String): String = if (isWindows) arg.replace("\"", "\\\"") else arg

    private fun locateSoffice(): File? {
        val exe = if (isWindows) "soffice.exe" else "soffice"
        val candidates = buildList {
            System.getenv("READER360_SOFFICE")?.let { add(File(it)) }
            System.getProperty("compose.application.resources.dir")?.let { add(File(it, "libreoffice/program/$exe")) }
            add(File(System.getProperty("user.dir"), "resources/windows/libreoffice/program/$exe"))
            if (isWindows) {
                listOf("ProgramFiles", "ProgramW6432", "ProgramFiles(x86)")
                    .mapNotNull { System.getenv(it) }
                    .distinct()
                    .forEach { add(File(it, "LibreOffice/program/soffice.exe")) }
                System.getenv("LOCALAPPDATA")?.let { add(File(it, "Programs/LibreOffice/program/soffice.exe")) }
            } else {
                add(File("/usr/bin/soffice"))
                add(File("/usr/lib/libreoffice/program/soffice"))
                add(File("/opt/libreoffice/program/soffice"))
                add(File("/Applications/LibreOffice.app/Contents/MacOS/soffice"))
            }
            System.getenv("PATH")?.split(File.pathSeparator)?.forEach { add(File(it, exe)) }
        }
        return candidates.firstOrNull { it.isFile }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 180L
        const val SPREADSHEET_TARGET =
            """pdf:calc_pdf_Export:{"SinglePageSheets":{"type":"boolean","value":"true"}}"""
    }
}
