package com.dmzs.datawatchclient.ui.sessions

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
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
 * WebView console messages are forwarded to Android logcat under the tag
 * `DwTerm`, so `adb logcat DwTerm:V *:S` shows xterm's own diagnostic
 * output (fit dimensions, buffered writes, etc.) — invaluable for debugging
 * the "terminal opens as a tiny black square" class of WebView layout races.
 */

/**
 * Controller handle surfaced to callers of [TerminalView] so sibling UI
 * (toolbar, FAB, etc.) can drive xterm search + clipboard without owning
 * the WebView reference.
 *
 * Stable across recompositions — the underlying [WebView] reference is
 * swapped in/out by TerminalView's AndroidView factory/dispose cycle.
 */
@Stable
public class TerminalController internal constructor() {
    internal var webView: WebView? = null

    /** Search forward for [query]. Highlights matches and cycles on repeat. */
    public fun searchNext(query: String) {
        webView?.evaluateJavascript(
            "window.dwSearchNext && window.dwSearchNext(${jsonString(query)});",
            null,
        )
    }

    /** Search backward for [query]. */
    public fun searchPrev(query: String) {
        webView?.evaluateJavascript(
            "window.dwSearchPrev && window.dwSearchPrev(${jsonString(query)});",
            null,
        )
    }

    /** Drop all search-match decorations. */
    public fun searchClear() {
        webView?.evaluateJavascript("window.dwSearchClear && window.dwSearchClear();", null)
    }

    /**
     * Pull the currently-selected text out of xterm and deliver it to [onResult]
     * on the main thread. Empty string when there is no selection.
     */
    public fun copySelection(onResult: (String) -> Unit) {
        val wv = webView ?: return onResult("")
        wv.evaluateJavascript(
            "(window.dwCopySelection && window.dwCopySelection()) || '';",
        ) { raw ->
            // evaluateJavascript returns the JS value as a JSON string —
            // unwrap the outer quotes before handing to the caller.
            val unwrapped =
                runCatching { JSONObject("{\"v\":$raw}").optString("v", "") }
                    .getOrDefault("")
            onResult(unwrapped)
        }
    }

    /**
     * Fetch the server-side session output backlog (N lines of PTY text)
     * and prepend it into xterm. Idempotent per session via an internal
     * already-loaded-for flag; toolbar button should disable itself after
     * first click to mirror this at the UI level.
     *
     * The fetch and the write live off-thread inside the provided
     * [scope.launch] at the call site — this method just writes to JS.
     * Caller passes the already-fetched text to [prepend].
     */
    public fun prepend(text: String) {
        webView?.evaluateJavascript(
            "window.dwPrependBacklog && window.dwPrependBacklog(${jsonString(text)});",
            null,
        )
    }

    /**
     * Set xterm font size in CSS px. Mirrors PWA's
     * `changeTermFontSize(delta)` — caller is responsible for
     * persistence + clamping (same contract the PWA has with
     * localStorage).
     */
    public fun setFontSize(px: Int) {
        webView?.evaluateJavascript(
            "window.dwSetFontSize && window.dwSetFontSize($px);",
            null,
        )
    }
}

@Composable
public fun rememberTerminalController(): TerminalController = remember { TerminalController() }

@SuppressLint("SetJavaScriptEnabled")
@Composable
public fun TerminalView(
    sessionId: String,
    events: List<SessionEvent>,
    modifier: Modifier = Modifier,
    controller: TerminalController? = null,
) {
    var ready by remember { mutableStateOf(false) }
    // Keyed to sessionId so navigating A → B resets the write cursor. Without
    // this, the LaunchedEffect's initial-flush branch (lastWrittenIndex == 0)
    // fails to fire for session B and nothing is ever written into xterm —
    // the WebView keeps session A's DOM and looks frozen. See B1.
    var lastWrittenIndex by remember(sessionId) { mutableStateOf(0) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // When the session changes, wipe the xterm DOM so session A's scrollback
    // doesn't bleed into session B's view. Also reset the pane_capture
    // "seen" flag for the new session so its next capture paints fresh.
    LaunchedEffect(sessionId) {
        webViewRef.value?.evaluateJavascript(
            "window.dwClear && window.dwClear();",
            null,
        )
        com.dmzs.datawatchclient.transport.ws.resetPaneCaptureSeen(sessionId)
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = true
                // Let user pinch-zoom into an 80-col TUI rendered on a
                // 39-col mobile viewport. Server-side PTY resize to our
                // actual cols is the proper fix (v1.1) and requires a
                // protocol addition; zoom is the interim escape hatch.
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient =
                    object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView?,
                            url: String?,
                        ) {
                            // Force a fit right after the HTML finishes loading —
                            // WebView reports `term.open()` success before the
                            // container has a stable layout in AndroidView.
                            view?.evaluateJavascript("window.dwResize && window.dwResize();", null)
                            // Belt-and-braces: flip `ready` from Kotlin so the
                            // initial flush still happens if DwBridge.onReady() was
                            // swallowed by a JS error before line 168 of host.html.
                            view?.post { ready = true }
                        }
                    }
                webChromeClient =
                    object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            Log.d(
                                "DwTerm",
                                "${consoleMessage.messageLevel()} " +
                                    "${consoleMessage.message()} " +
                                    "(line ${consoleMessage.lineNumber()})",
                            )
                            return true
                        }
                    }
                setBackgroundColor(0xFF0C0C14.toInt())
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onReady() {
                            post { ready = true }
                            Log.d("DwTerm", "onReady")
                        }

                        @JavascriptInterface
                        fun onInput(
                            @Suppress("UNUSED_PARAMETER") data: String,
                        ) {
                            // Sprint 3: bridge typed input back to the WS reply lane.
                        }
                    },
                    "DwBridge",
                )
                loadUrl("file:///android_asset/xterm/host.html")
                webViewRef.value = this
                controller?.webView = this
            }
        },
        update = { webView ->
            // Compose may resize us across configuration changes; nudge xterm.
            webView.evaluateJavascript("window.dwResize && window.dwResize();", null)
            controller?.webView = webView
        },
    )

    // When new events arrive (or terminal becomes ready), flush any unwritten
    // suffix into xterm.
    //
    // Two paths, chosen per-session at first event:
    //   1. pane_capture present → authoritative PWA-style rendering.
    //      raw_output is suppressed; each PaneCapture wipes + writes the
    //      full pane. No lastWrittenIndex arithmetic needed.
    //   2. pane_capture absent (older server) → fall back to the legacy
    //      raw_output incremental-append path.
    LaunchedEffect(events.size, ready) {
        if (!ready) return@LaunchedEffect
        val webView = webViewRef.value ?: return@LaunchedEffect
        val hasPaneCapture = events.any { it is SessionEvent.PaneCapture }
        if (hasPaneCapture) {
            // Pane-capture mode: only replay pane captures we haven't
            // written yet; skip Output entirely (log-mode).
            if (events.size > lastWrittenIndex) {
                val newOnes = events.subList(lastWrittenIndex, events.size)
                newOnes.filterIsInstance<SessionEvent.PaneCapture>().forEach { pc ->
                    // Build a JSON array string by JSON-quoting each line
                    // (JSONObject.quote handles all string escape rules) and
                    // joining with commas. Then JSON-quote the whole thing
                    // again because dwPaneCapture takes a JSON string that it
                    // JSON.parse()'s — nested quoting is intentional.
                    val arrayLiteral =
                        pc.lines.joinToString(prefix = "[", postfix = "]") { JSONObject.quote(it) }
                    val linesJson = JSONObject.quote(arrayLiteral)
                    webView.evaluateJavascript(
                        "window.dwPaneCapture($linesJson, ${pc.isFirst});",
                        null,
                    )
                    Log.d(
                        "DwTerm",
                        "pane_capture: ${pc.lines.size} lines (first=${pc.isFirst})",
                    )
                }
                lastWrittenIndex = events.size
            }
            return@LaunchedEffect
        }
        // Legacy raw_output path (server doesn't send pane_capture yet).
        if (lastWrittenIndex == 0 && events.isNotEmpty()) {
            val joined = events.mapNotNull { it.terminalText() }.joinToString("")
            if (joined.isNotEmpty()) {
                webView.evaluateJavascript("window.dwWrite(${jsonString(joined)});", null)
                Log.d("DwTerm", "initial flush: ${joined.length} chars")
            }
            lastWrittenIndex = events.size
            return@LaunchedEffect
        }
        if (events.size > lastWrittenIndex) {
            val newOnes = events.subList(lastWrittenIndex, events.size)
            val joined = newOnes.mapNotNull { it.terminalText() }.joinToString("")
            if (joined.isNotEmpty()) {
                webView.evaluateJavascript("window.dwWrite(${jsonString(joined)});", null)
                Log.d("DwTerm", "incremental: ${joined.length} chars (${newOnes.size} events)")
            }
            lastWrittenIndex = events.size
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            controller?.webView = null
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
internal fun SessionEvent.terminalText(): String? =
    when (this) {
        is SessionEvent.Output -> body
        is SessionEvent.StateChange -> "\u001b[2m[state] $from → $to\u001b[0m\n"
        is SessionEvent.Completed -> "\u001b[2m[completed] exit ${exitCode ?: ""}\u001b[0m\n"
        is SessionEvent.Error -> "\u001b[31m[error] $message\u001b[0m\n"
        else -> null
    }

/** Quote a string as a JS literal — JSONObject handles all the escaping rules. */
internal fun jsonString(s: String): String = JSONObject.quote(s)
