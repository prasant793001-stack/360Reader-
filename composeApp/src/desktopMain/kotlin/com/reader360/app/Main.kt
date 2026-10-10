package com.reader360.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.reader360.app.ui.AppLogoPainter
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.Dimension
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File

fun main(args: Array<String>) {
    val openRequests = MutableSharedFlow<DocumentRef>(replay = 1, extraBufferCapacity = 8)

    // "Open with 360Reader" in Explorer passes the file path as the first argument.
    args.firstOrNull()
        ?.let { File(it) }
        ?.takeIf { it.isFile }
        ?.let { openRequests.tryEmit(DesktopPlatform.refFor(it)) }

    application {
        val windowState = rememberWindowState(
            width = 1280.dp,
            height = 860.dp,
            position = WindowPosition(Alignment.Center),
        )
        val platform = remember { DesktopPlatform(windowState) }
        val icon = remember { AppLogoPainter() }

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "360Reader",
            icon = icon,
        ) {
            LaunchedEffect(Unit) {
                platform.window = window
                window.minimumSize = Dimension(720, 520)
                window.dropTarget = DropTarget().apply {
                    addDropTargetListener(object : DropTargetAdapter() {
                        override fun drop(event: DropTargetDropEvent) {
                            event.acceptDrop(DnDConstants.ACTION_COPY)
                            val files = runCatching {
                                event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                            }.getOrNull().orEmpty()
                            files.filterIsInstance<File>().firstOrNull { it.isFile }?.let {
                                openRequests.tryEmit(DesktopPlatform.refFor(it))
                            }
                            event.dropComplete(true)
                        }
                    })
                }
            }
            App(platform, openRequests)
        }
    }
}
