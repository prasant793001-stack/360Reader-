package com.reader360.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
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

    private val saver = registerForActivityResult(SaveAsDocument()) { uri ->
        platform.onSaveTargetChosen(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        platform = AndroidPlatform(
            activity = this,
            launchPicker = { picker.launch(arrayOf("*/*")) },
            launchSaver = { name, mime -> saver.launch(name to mime) },
        )
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

/** System "Save to…" dialog with a suggested file name and the right file type. */
private class SaveAsDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == android.app.Activity.RESULT_OK) intent?.data else null
}
