package com.dmzs.datawatchclient.ui.sessions

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.dmzs.datawatchclient.domain.SessionEvent
import org.json.JSONObject

/**
 * Embeds an xterm.js terminal in a WebView and pipes [events] into it. The
 * host page (`assets/xterm/host.html`) exposes `dwWrite(text)`, `dwClear()`,
 * and `dwResize()` which we drive via `evaluateJavascript`.
 *
 * Only output / state-change events that contribute terminal text are written;
 * everything else (rate-limit banners, prompt detection) shows in the chat
 * spine, not the terminal.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
public fun TerminalView(
    events: List<SessionEvent>,
    modifier: Modifier = Modifier,
) {
    var ready by remember { mutableStateOf(false) }
    var lastWrittenIndex by remember { mutableStateOf(0) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                setBackgroundColor(0xFF0C0C14.toInt())
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onReady() {
                        post { ready = true }
                    }

                    @JavascriptInterface
                    fun onInput(@Suppress("UNUSED_PARAMETER") data: String) {
                        // Sprint 3: bridge typed input back to the WS reply lane.
                    }
                }, "DwBridge")
                loadUrl("file:///android_asset/xterm/host.html")
                webViewRef.value = this
            }
        },
        update = { /* updates handled via LaunchedEffect below */ },
    )

    // When new events arrive (or terminal becomes ready), flush any unwritten
    // suffix into xterm. We never re-render the full backlog after first write
    // — xterm preserves its own scrollback (5000 lines, see host.html).
    LaunchedEffect(events.size, ready) {
        if (!ready) return@LaunchedEffect
        val webView = webViewRef.value ?: return@LaunchedEffect
        if (lastWrittenIndex == 0 && events.isNotEmpty()) {
            // First flush — render entire backlog.
            val joined = events.mapNotNull { it.terminalText() }.joinToString("")
            if (joined.isNotEmpty()) {
                webView.evaluateJavascript("window.dwWrite(${jsonString(joined)});", null)
            }
            lastWrittenIndex = events.size
            return@LaunchedEffect
        }
        if (events.size > lastWrittenIndex) {
            val newOnes = events.subList(lastWrittenIndex, events.size)
            val joined = newOnes.mapNotNull { it.terminalText() }.joinToString("")
            if (joined.isNotEmpty()) {
                webView.evaluateJavascript("window.dwWrite(${jsonString(joined)});", null)
            }
            lastWrittenIndex = events.size
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.value?.destroy()
            webViewRef.value = null
        }
    }
}

/**
 * Map a [SessionEvent] to the text that should be written to the terminal.
 * Output events contribute their body verbatim (server already includes line
 * endings); state changes get a system marker line; everything else is `null`
 * so the chat surface (not the terminal) renders it.
 */
internal fun SessionEvent.terminalText(): String? = when (this) {
    is SessionEvent.Output -> body
    is SessionEvent.StateChange -> "\u001b[2m[state] $from → $to\u001b[0m\n"
    is SessionEvent.Completed -> "\u001b[2m[completed] exit ${exitCode ?: ""}\u001b[0m\n"
    is SessionEvent.Error -> "\u001b[31m[error] $message\u001b[0m\n"
    else -> null
}

/** Quote a string as a JS literal — JSONObject handles all the escaping rules. */
private fun jsonString(s: String): String = JSONObject.quote(s)
