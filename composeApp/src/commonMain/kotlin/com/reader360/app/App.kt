package com.reader360.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.reader360.app.data.RecentFiles
import com.reader360.app.ui.AppTheme
import com.reader360.app.ui.HomeScreen
import com.reader360.app.ui.ViewerScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Root of the app on both Android and Windows.
 * [openRequests] delivers files opened from outside the app
 * (Android "Open with", Windows command line or drag and drop).
 */
@Composable
fun App(platform: Platform, openRequests: Flow<DocumentRef>) {
    val scope = rememberCoroutineScope()
    val recents = remember(platform) { RecentFiles(platform) }
    var current by remember { mutableStateOf<DocumentRef?>(null) }
    var now by remember { mutableLongStateOf(platform.now()) }
    val engineStatus = remember(platform) { platform.office.status() }

    fun forgetUnusedCopies() {
        scope.launch {
            runCatching { platform.pruneKeptCopies(recents.items.map { it.ref.id }.toSet()) }
        }
    }

    fun open(ref: DocumentRef) {
        current = ref
        scope.launch {
            // Files lent by other apps (WhatsApp, Gmail, …) are copied, so Recent can reopen them later.
            val remembered = runCatching { platform.keepCopy(ref) }.getOrDefault(ref)
            recents.add(remembered)
            forgetUnusedCopies()
        }
    }

    LaunchedEffect(platform) { recents.load() }
    LaunchedEffect(openRequests) { openRequests.collect { open(it) } }

    AppTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it?.id },
                label = "screen",
            ) { ref ->
                if (ref == null) {
                    LaunchedEffect(Unit) { now = platform.now() }
                    HomeScreen(
                        recents = recents.items,
                        engineStatus = engineStatus,
                        isDesktop = platform.isDesktop,
                        now = now,
                        onOpenFile = { scope.launch { platform.pickFile()?.let { open(it) } } },
                        onOpenRecent = { open(it) },
                        onRemoveRecent = { scope.launch { recents.remove(it); forgetUnusedCopies() } },
                        onClearRecents = { scope.launch { recents.clear(); forgetUnusedCopies() } },
                        onEngineAction = { platform.openUrl(it) },
                    )
                } else {
                    ViewerScreen(ref = ref, platform = platform, onClose = { current = null })
                }
            }
        }
    }
}
