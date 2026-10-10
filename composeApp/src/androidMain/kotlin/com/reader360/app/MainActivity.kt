package com.reader360.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    private val openRequests = MutableSharedFlow<DocumentRef>(replay = 1, extraBufferCapacity = 8)
    private lateinit var platform: AndroidPlatform

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        platform.onFilePicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        platform = AndroidPlatform(this) { picker.launch(arrayOf("*/*")) }
        if (savedInstanceState == null) handleViewIntent(intent)
        setContent { App(platform, openRequests) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        openRequests.tryEmit(platform.refFor(uri))
    }
}
