package com.dmzs.datawatchclient

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.dmzs.datawatchclient.ui.AppRoot
import com.dmzs.datawatchclient.ui.DeepLinks

/**
 * Launch Activity. Hands off to the Compose navigation root — see
 * [com.dmzs.datawatchclient.ui.AppRoot]. Also extracts session-deep-link
 * targets from the intent (`dwclient://session/<id>`) and surfaces them via
 * [DeepLinks] for AppRoot to consume.
 */
public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeDeepLink(intent)
        setContent { AppRoot() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme != "dwclient") return
        if (uri.host == "session") {
            // dwclient://session/<id>  → path "/<id>"
            val id = uri.pathSegments.firstOrNull() ?: return
            DeepLinks.pendingSessionTarget.tryEmit(id)
        }
    }
}
