package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.firebase.configureAppCheck
import com.example.ui.navigation.AiClipperApp
import com.example.ui.theme.AiClipperTheme

class MainActivity : ComponentActivity() {

    private var pendingSharedLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        extractSharedText(intent)?.let { pendingSharedLink = it }

        setContent {
            AiClipperTheme {
                Surface {
                    AiClipperApp(
                        sharedLinkText = pendingSharedLink,
                        onSharedLinkConsumed = { pendingSharedLink = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractSharedText(intent)?.let { pendingSharedLink = it }
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent == null) return null
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            return intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        }
        if (intent.action == Intent.ACTION_VIEW) {
            val dataUri = intent.data ?: return null
            val embeddedUrl = dataUri.getQueryParameter("url")
            if (!embeddedUrl.isNullOrBlank()) return embeddedUrl
            val scheme = dataUri.scheme?.lowercase()
            if (scheme == "http" || scheme == "https") {
                val urlStr = dataUri.toString()
                if (!urlStr.contains("asia-northeast1.run.app")) {
                    return urlStr
                }
            }
        }
        return null
    }
}
