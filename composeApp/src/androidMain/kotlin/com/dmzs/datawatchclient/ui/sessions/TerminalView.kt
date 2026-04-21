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

    // Buffered state applied whenever a fresh WebView reaches
    // `onReady` — prevents re-entry races where Kotlin calls
    // `setMinSize(120, 40)` before host.html's script has defined
    // `window.dwSetMinCols`, causing the claude-code 120-col
    // enforcement to silently drop on the second session open (T2).
    internal var pendingMinCols: Int = 0
    internal var pendingMinRows: Int = 0
    internal var pendingFrozen: Boolean = false

    /** Replays every deferred directive once host.html fires onReady. */
    internal fun applyPending(wv: WebView) {
        if (pendingMinCols > 0 || pendingMinRows > 0) {
            wv.evaluateJavascript(
                "window.dwSetMinCols && window.dwSetMinCols($pendingMinCols, $pendingMinRows);",
                null,
            )
        }
        if (pendingFrozen) {
            wv.evaluateJavascript(
                "window.dwSetFrozen && window.dwSetFrozen(true);",
                null,
            )
        }
    }

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

    /**
     * User-driven fit-to-width. The container already auto-fits on
     * layout changes; this is the manual nudge users want after a
     * pinch-zoom or rotation that didn't trigger a resize callback.
     */
    public fun fit() {
        webView?.evaluateJavascript("window.dwFit && window.dwFit();", null)
    }

    /**
     * Snap viewport to the live tail. Maps to PWA's
     * `term.scrollToBottom()`.
     */
    public fun scrollToBottom() {
        webView?.evaluateJavascript(
            "window.dwScrollToBottom && window.dwScrollToBottom();",
            null,
        )
    }

    /**
     * Set the per-backend minimum cols / rows (e.g. claude-code
     * wants 120×40 per parent v0.14.1). xterm will be resized to
     * at least these dimensions; container scrolls horizontally
     * when narrower.
     */
    public fun setMinSize(cols: Int, rows: Int) {
        pendingMinCols = cols
        pendingMinRows = rows
        webView?.evaluateJavascript(
            "window.dwSetMinCols && window.dwSetMinCols($cols, $rows);",
            null,
        )
    }

    /**
     * Freeze further pane_capture writes. Called when the session
     * transitions to complete/failed/killed so the final screenshot
     * isn't overwritten by subsequent shell-prompt frames.
     */
    public fun setFrozen(frozen: Boolean) {
        pendingFrozen = frozen
        webView?.evaluateJavascript(
            "window.dwSetFrozen && window.dwSetFrozen($frozen);",
            null,
        )
    }

    /**
     * Auto-shrink font until xterm fits the container horizontally.
     * Mirrors PWA `termFitToWidth`. Useful on phone width when
     * `setMinSize(120, _)` forces a wide terminal.
     */
    public fun autoFitToWidth() {
        webView?.evaluateJavascript(
            "window.dwAutoFitToWidth && window.dwAutoFitToWidth();",
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

    // Watchdog — if no pane_capture arrives within 5 seconds of the
    // session opening, reset the first-capture flag so the next
    // frame (whenever it arrives) is treated as a fresh reset-and-
    // write. Mirrors PWA's `startTermConnectWatchdog` (app.js ~line
    // 1723). We don't force a WS reconnect — the underlying
    // WebSocketTransport already reconnects on error — but we do
    // un-freeze and clear the "initial seen" state.
    LaunchedEffect(sessionId) {
        kotlinx.coroutines.delay(5_000L)
        val sawAny = events.any { it is SessionEvent.PaneCapture }
        if (!sawAny) {
            Log.w("DwTerm", "watchdog: no pane_capture in 5s for $sessionId; resetting")
            com.dmzs.datawatchclient.transport.ws.resetPaneCaptureSeen(sessionId)
            webViewRef.value?.evaluateJavascript(
                "window.dwClear && window.dwClear();",
                null,
            )
        }
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
                            // Replay any deferred controller directives
                            // (setMinSize, setFrozen) — required for T2 so the
                            // re-opened session still gets claude-code's 120×40
                            // enforcement instead of falling back to the fit-to-
                            // container width.
                            view?.let { controller?.applyPending(it) }
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
                // Match PWA terminal bg (#0f1117) so WebView layout flashes
                // look identical to web users during reflow.
                setBackgroundColor(0xFF0F1117.toInt())
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

                        /**
                         * Called by host.html safeFit() whenever xterm's
                         * cols/rows actually change. We turn around and fire
                         * a `resize_term` WS frame so the server resizes the
                         * tmux pane to match; the next pane_capture arrives
                         * at the new dimensions. Matches PWA `syncTmuxSize`.
                         */
                        @JavascriptInterface
                        fun onResize(cols: Int, rows: Int) {
                            com.dmzs.datawatchclient.transport.ws.WsOutbound
                                .sendResizeTerm(sessionId, cols, rows)
                            Log.d("DwTerm", "resize_term → $cols×$rows")
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

    // Single display source (T3): pane_capture only — raw_output is
    // never written to xterm. Parent killed the "incremental append"
    // path in `dmz006/datawatch@0393e262` ("single display source")
    // because raw_output bytes contain fragmentary ANSI state that
    // garbles the TUI and fills scrollback with no way to clear
    // cleanly. Datawatch server v3.0+ always emits pane_capture for
    // terminal-mode sessions; older servers need the chat mode toggle.
    //
    // Keying on latestPaneCaptureTs (not size) lets replacement
    // captures still fire — our in-memory LivePaneCapture StateFlow
    // holds only the latest snapshot, so the emitted events list has
    // constant size while content changes. Timestamp grows per frame.
    val latestPaneCapture =
        events.lastOrNull { it is SessionEvent.PaneCapture } as? SessionEvent.PaneCapture
    LaunchedEffect(latestPaneCapture?.ts, ready) {
        if (!ready) return@LaunchedEffect
        val webView = webViewRef.value ?: return@LaunchedEffect
        val pc = latestPaneCapture ?: return@LaunchedEffect
        val arrayLiteral =
            pc.lines.joinToString(prefix = "[", postfix = "]") { JSONObject.quote(it) }
        val linesJson = JSONObject.quote(arrayLiteral)
        webView.evaluateJavascript(
            "window.dwPaneCapture($linesJson, ${pc.isFirst});",
            null,
        )
        Log.d("DwTerm", "pane_capture: ${pc.lines.size} lines (first=${pc.isFirst})")
        lastWrittenIndex = events.size
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
