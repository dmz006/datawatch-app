package com.dmzs.datawatchclient.ui.sessions

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.dmzs.datawatchclient.domain.SessionEvent
import org.json.JSONObject

/**
 * WebView that ignores its own attempts at vertical scrolling. The Android
 * WebView still synthesises scrollY changes from touch drags even when the
 * loaded document has `body { overflow: hidden; touch-action: none }` —
 * user-observed on Samsung S24 Ultra (Android 16): dragging across the
 * terminal area moved the whole xterm content as if scrolling a webpage.
 *
 * Disabling vertical movement at the view layer:
 *  - `overScrollBy` is called by the framework for every touch-driven
 *    scroll update; clamping deltaY = 0 / scrollRangeY = 0 stops the slide
 *  - `scrollTo` / `scrollBy` ignore the y component, so programmatic
 *    requests (e.g. accessibility "scroll forward") don't move us either
 *  - `computeVerticalScrollRange` returns 0 so the framework never thinks
 *    there's room to scroll vertically
 *
 * Horizontal scroll is left intact — xterm's `#term` container uses
 * `overflow-x: auto` so wide terminals (claude-code 120 cols on a 40-char
 * phone) can pan via touch.
 */
private class TerminalWebView(ctx: Context) : WebView(ctx) {
    override fun overScrollBy(
        deltaX: Int,
        deltaY: Int,
        scrollX: Int,
        scrollY: Int,
        scrollRangeX: Int,
        scrollRangeY: Int,
        maxOverScrollX: Int,
        maxOverScrollY: Int,
        isTouchEvent: Boolean,
    ): Boolean =
        super.overScrollBy(
            deltaX,
            0,
            scrollX,
            0,
            scrollRangeX,
            0,
            maxOverScrollX,
            0,
            isTouchEvent,
        )

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(x, 0)
    }

    override fun scrollBy(x: Int, y: Int) {
        super.scrollBy(x, 0)
    }

    override fun computeVerticalScrollRange(): Int = 0

    /**
     * Disable IME autocorrect and suppress Samsung keyboard's spurious Enter
     * injections. Samsung's "Smart Typing" feature commits a predicted word on
     * space and then fires performEditorAction / sendKeyEvent(ENTER) through
     * the InputConnection — which the WebView converts to a DOM Enter event
     * that xterm sends to the shell as \r (the "space sends the command" bug).
     *
     * Three-layer defence:
     *  1. TYPE_TEXT_VARIATION_VISIBLE_PASSWORD + NO_SUGGESTIONS: tells the IME
     *     to disable word prediction in the first place.
     *  2. IME_ACTION_NONE: no action button, so no IME_ACTION_DONE event.
     *  3. InputConnectionWrapper: intercepts the back-door Samsung paths that
     *     bypass the inputType flags (performEditorAction, sendKeyEvent(ENTER),
     *     and commitText with a trailing newline).
     *
     * Intentional Enter from the soft keyboard is distinguished from Samsung's
     * spurious autocomplete Enter by tracking whether a commitText call just
     * landed: Samsung always fires commitText("word") → sendKeyEvent(ENTER) in
     * sequence. A deliberate Enter tap has no preceding commitText.
     */
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_ACTION_NONE
        return object : InputConnectionWrapper(ic, true) {
            // Timestamp of the most recent commitText / finishComposingText call.
            // We use a time-based window instead of a boolean flag to avoid a
            // race where any non-Enter sendKeyEvent (e.g. KEYCODE_SPACE fired by
            // the keyboard between the word commit and the spurious Enter) clears
            // the guard prematurely, letting the spurious Enter through.
            private var lastCommitMs = 0L
            private fun recentlyCommitted() =
                lastCommitMs > 0 && System.currentTimeMillis() - lastCommitMs < SPURIOUS_ENTER_WINDOW_MS

            override fun performEditorAction(editorAction: Int): Boolean {
                if (!recentlyCommitted()) {
                    // Intentional soft-keyboard Enter (no autocomplete commit just fired).
                    // Inject \r directly so xterm processes it without a DOM round-trip.
                    this@TerminalWebView.evaluateJavascript(
                        "window.DwBridge && DwBridge.onInput('\r');",
                        null,
                    )
                } else {
                    Log.d("DwTerm", "performEditorAction SUPPRESSED (post-commit window)")
                }
                lastCommitMs = 0L
                return true
            }

            override fun sendKeyEvent(event: KeyEvent?): Boolean {
                val kc = event?.keyCode
                if (kc == KeyEvent.KEYCODE_DPAD_CENTER) {
                    lastCommitMs = 0L
                    return true // DPAD_CENTER never has a terminal role on touchscreen
                }
                if (kc == KeyEvent.KEYCODE_ENTER) {
                    if (recentlyCommitted()) {
                        // Spurious Enter from autocomplete commit — suppress.
                        Log.d("DwTerm", "IC.sendKeyEvent SUPPRESSED (post-commit window) kc=$kc")
                        lastCommitMs = 0L
                        return true
                    }
                    // Intentional soft-keyboard Enter — let it reach the WebView DOM so
                    // xterm.js fires onData("\r") → DwBridge.onInput → WsOutbound.sendInput.
                    lastCommitMs = 0L
                    return super.sendKeyEvent(event)
                }
                // Do NOT clear lastCommitMs for non-Enter keys — a KEYCODE_SPACE or
                // KEYCODE_BACKSPACE fired between commitText and the spurious Enter would
                // reset the window early and let the spurious Enter through as intentional.
                return super.sendKeyEvent(event)
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                // Strip any trailing \r / \n Samsung/Gboard appends when committing a word.
                val cleaned = text?.trimEnd('\r', '\n') ?: return super.commitText(text, newCursorPosition)
                if (cleaned.isNotEmpty()) lastCommitMs = System.currentTimeMillis()
                return super.commitText(cleaned, newCursorPosition)
            }

            override fun finishComposingText(): Boolean {
                // Some IMEs use setComposingText → finishComposingText instead of
                // commitText. Mark the same window so a spurious Enter on that path
                // is also suppressed.
                lastCommitMs = System.currentTimeMillis()
                return super.finishComposingText()
            }
        }
    }

    private companion object {
        // How long after a word commit to treat the next Enter as spurious.
        // Samsung and Gboard both fire the phantom Enter within ~50 ms; 300 ms
        // gives headroom without blocking intentional rapid Enter presses.
        const val SPURIOUS_ENTER_WINDOW_MS = 300L
    }

    /**
     * Intercept D-pad navigation keys before they reach the DOM.
     * Android delivers navigation keys both as native KeyEvents (which WebView
     * converts to DOM keyboard events that xterm.js handles) AND sometimes
     * as a second pass through the IME pipeline, causing each press to fire
     * xterm's onData handler twice. Consuming ACTION_DOWN here and sending the
     * ANSI sequence directly via DwBridge bypasses the DOM path entirely.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isDpad = event.keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        if (isDpad) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                val jsAnsi = when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP    -> "\\x1b[A"
                    KeyEvent.KEYCODE_DPAD_DOWN  -> "\\x1b[B"
                    KeyEvent.KEYCODE_DPAD_RIGHT -> "\\x1b[C"
                    KeyEvent.KEYCODE_DPAD_LEFT  -> "\\x1b[D"
                    else -> return super.dispatchKeyEvent(event)
                }
                evaluateJavascript(
                    "window.DwBridge && DwBridge.onInput('$jsAnsi');",
                    null,
                )
            }
            return true // consume both ACTION_DOWN and ACTION_UP
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            // Samsung S24 Ultra fires DPAD_CENTER after every keypress.
            // Always consume — a terminal on a touch-screen uses the Enter
            // key for intentional Enter; DPAD_CENTER has no terminal role.
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

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
    public fun setMinSize(
        cols: Int,
        rows: Int,
    ) {
        pendingMinCols = cols
        pendingMinRows = rows
        webView?.evaluateJavascript(
            "window.dwSetMinCols && window.dwSetMinCols($cols, $rows);",
            null,
        )
    }

    /**
     * Tell xterm whether the user has entered tmux scroll / copy-mode.
     * While true, pane_capture writes are paused so the live screen-wipe
     * (ESC[2J ESC[3J) doesn't reset the user's scroll position.
     * On exit (false) the terminal jumps back to the live tail.
     */
    public fun setScrollMode(on: Boolean) {
        webView?.evaluateJavascript(
            "window.dwSetScrollMode && window.dwSetScrollMode($on);",
            null,
        )
    }

    /**
     * Open a short window in which the next pane_capture is allowed to
     * write through even while scroll mode is active. Called by the
     * scroll-mode toolbar's PgUp/PgDn buttons right before they send
     * the tmux-page-up/down WS command, so the resulting (scrolled)
     * pane_capture renders exactly once. Mirrors the PWA's
     * `_scrollPendingRefresh` pattern (app.js ~line 665).
     */
    public fun scrollPendingRefresh(windowMs: Int = 700) {
        webView?.evaluateJavascript(
            "window.dwScrollPendingRefresh && window.dwScrollPendingRefresh($windowMs);",
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
    // Keyed to sessionId so navigating A → B resets ready to false, preventing
    // stale-true from causing dwPaneCapture to fire against a not-yet-loaded WebView.
    var ready by remember(sessionId) { mutableStateOf(false) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // Track actual render size so we can force xterm to re-fit when keyboard
    // opens/closes — the update block fires before WebView internal layout settles.
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

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

    // Compose is the sole owner of the WebView's pixel size. Whenever
    // onSizeChanged fires (keyboard open/close, rotation, foldable hinge,
    // tablet split), ship the exact dimensions to xterm so FitAddon
    // measures the right area. No cap — capping the height clipped the
    // terminal to the top portion of a tall WebView, leaving the rest
    // black (regression that produced "text is up off the top of the
    // screen" reports). visualViewport-based handlers inside host.html
    // do not fire reliably under Compose+imePadding, so this callback
    // is load-bearing.
    LaunchedEffect(viewSize, ready) {
        if (!ready || viewSize.width == 0 || viewSize.height == 0) return@LaunchedEffect
        val wv = webViewRef.value ?: return@LaunchedEffect
        wv.evaluateJavascript(
            "window.dwExplicitSize && window.dwExplicitSize(${viewSize.width}, ${viewSize.height});",
            null,
        )
    }

    // Watchdog removed in v0.33.13: it captured `events` at
    // LaunchedEffect composition (keyed only on sessionId), evaluated
    // the STALE captured list 5s later, and — if that snapshot had no
    // pane_capture — called `dwClear()` + reset the first-seen flag.
    // On a live session this reliably blanked the terminal 5s after
    // first open even when pane_captures had been streaming in the
    // whole time. The single-source pane_capture pipeline landed in
    // v0.33.8 already handles "no frame yet" by just not writing;
    // the explicit clear was a leftover from the legacy raw_output
    // fallback path and is no longer useful.

    AndroidView(
        modifier = modifier.onSizeChanged { size -> viewSize = size },
        factory = { ctx ->
            TerminalWebView(ctx).apply {
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
                // xterm.js owns all gesture handling for its content (its own
                // scrollback, copy/paste, search). The Android WebView's
                // default behaviour intercepts vertical drags as "scroll the
                // webpage" and translates them into scrollY changes even when
                // the document body has overflow:hidden — user observed this
                // as "having to scroll the WebView down to find the live tail"
                // after opening a session on the S24 (Android 16). Disabling
                // the WebView's own vertical scroll + overscroll + scrollbar
                // hands gesture ownership entirely to xterm.
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
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
                            // println instead of Log.d so JS console messages survive
                            // ProGuard's release-build log stripping — load-bearing
                            // for diagnosing xterm scroll/dispatch issues.
                            println(
                                "DwTerm-js: ${consoleMessage.messageLevel()} " +
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
                        fun onInput(data: String) {
                            // Log every byte sent to server — catches both the
                            // xterm onData path AND direct evaluateJavascript calls.
                            Log.d("DwTerm", "sendInput: ${data.map { it.code }}")
                            com.dmzs.datawatchclient.transport.ws.WsOutbound.sendInput(sessionId, data)
                        }

                        /**
                         * Called by host.html safeFit() whenever xterm's
                         * cols/rows actually change. We turn around and fire
                         * a `resize_term` WS frame so the server resizes the
                         * tmux pane to match; the next pane_capture arrives
                         * at the new dimensions. Matches PWA `syncTmuxSize`.
                         */
                        @JavascriptInterface
                        fun onResize(
                            cols: Int,
                            rows: Int,
                        ) {
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
            // onSizeChanged → dwExplicitSize is the primary resize path
            // (above LaunchedEffect). Keep dwResize() as a no-op nudge for
            // recompositions where dimensions did NOT change but state
            // settled (font-size toggle, controller swap) — safeFit() is
            // idempotent when measurements are unchanged.
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
        // println survives ProGuard's default rules where Log.d is stripped —
        // gives us visibility into dispatch cadence in release builds when
        // diagnosing live-tail lag.
        @Suppress("ForbiddenComment")
        println("DwTerm-dispatch: pc rows=${pc.lines.size} first=${pc.isFirst} ts=${pc.ts}")
        webView.evaluateJavascript(
            "window.dwPaneCapture($linesJson, ${pc.isFirst});",
            null,
        )
        Log.d("DwTerm", "pane_capture: ${pc.lines.size} lines (first=${pc.isFirst})")
    }

    DisposableEffect(Unit) {
        onDispose {
            controller?.webView = null
            webViewRef.value?.destroy()
            webViewRef.value = null
        }
    }
}

/** Quote a string as a JS literal — JSONObject handles all the escaping rules. */
internal fun jsonString(s: String): String = JSONObject.quote(s)
