import SwiftUI
import WebKit
import DatawatchShared

// MARK: - TerminalController

/// Lets the session screen drive the xterm page (fit, scroll mode) and hear back
/// when the page auto-sizes its font.
final class TerminalController: ObservableObject {
    weak var webView: WKWebView?
    var onAutoFontSize: ((Int) -> Void)?

    func eval(_ js: String) {
        webView?.evaluateJavaScript(js, completionHandler: nil)
    }

    /// PWA termFitToWidth: shrink the font until there is no horizontal overflow.
    func fitToWidth() { eval("window.dwAutoFitToWidth && window.dwAutoFitToWidth();") }
    func setScrollMode(_ on: Bool) { eval("window.dwSetScrollMode && window.dwSetScrollMode(\(on));") }
    /// Accept the next pane_capture while scrolled (PWA 700 ms window).
    func scrollPendingRefresh(ms: Int = 700) { eval("window.dwScrollPendingRefresh && window.dwScrollPendingRefresh(\(ms));") }

    /// Minimum columns from the session's server-resolved console size (PWA
    /// initXterm `configCols = sess.console_cols`, `minCols = configCols || 80`;
    /// Android setMinSize). claude-code defaults to 120 for its TUI layout. Rows
    /// are not enforced on mobile (the keyboard would clip the live tail).
    /// Re-applied when the page reports ready.
    private(set) var minCols = 0
    func setMinCols(_ cols: Int) {
        minCols = cols
        applyMinCols()
    }
    func applyMinCols() {
        guard minCols > 0 else { return }
        eval("window.dwSetMinCols && window.dwSetMinCols(\(minCols), 0);")
    }

    // ── D67a rate-limit notice ──────────────────────────────────────────

    /// Fired on each `rate_limited` session event with its retry-after (if known).
    var onRateLimited: ((Date?) -> Void)?

    // ── D69a search + copy (host.html dwSearch* / dwCopySelection bridge) ─

    private static func jsString(_ s: String) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: s, options: .fragmentsAllowed),
              let literal = String(data: data, encoding: .utf8) else { return "\"\"" }
        return literal
    }

    /// Next / previous match in the scrollback via the xterm search addon; the
    /// completion gets whether a match was found.
    func search(_ query: String, forward: Bool, completion: @escaping (Bool) -> Void) {
        let fn = forward ? "dwSearchNext" : "dwSearchPrev"
        let js = "window.\(fn) ? window.\(fn)(\(Self.jsString(query))) : false"
        webView?.evaluateJavaScript(js) { result, _ in
            completion((result as? Bool) ?? ((result as? NSNumber)?.boolValue ?? false))
        }
    }

    func clearSearch() { eval("window.dwSearchClear && window.dwSearchClear();") }

    /// The current selection, or — when nothing is selected — the visible rows
    /// (trailing whitespace trimmed).
    func copyText(completion: @escaping (String) -> Void) {
        let js = """
        (function(){
          var sel = window.dwCopySelection ? window.dwCopySelection() : '';
          if (sel) return sel;
          try {
            var b = term.buffer.active, out = [];
            for (var y = b.viewportY; y < b.viewportY + term.rows; y++) {
              var l = b.getLine(y); out.push(l ? l.translateToString(true) : '');
            }
            return out.join('\\n').replace(/\\s+$/, '');
          } catch (e) { return ''; }
        })()
        """
        webView?.evaluateJavaScript(js) { result, _ in
            completion((result as? String) ?? "")
        }
    }

    /// `session.console_cols` when the server reports it, else the server's
    /// per-backend default (claude-code 120, otherwise 80) — shared
    /// `Session.terminalMinCols`.
    static func minCols(for session: DwSession) -> Int {
        let cols: Int = Int(session.terminalMinCols)
        return cols
    }
}

// MARK: - TerminalView (public SwiftUI entry point)

/// xterm.js terminal in a `WKWebView`, driven by the session's `/ws` hub.
///
/// Rendering: `pane_capture` frames from the shared `WebSocketTransport` are
/// pushed into `host.html` via `window.dwPaneCapture`. Input: xterm's
/// `onData` reaches Swift through the `DwBridge` shim and is forwarded as a
/// `send_input` frame via `WsOutbound`. The page opens no sockets and never
/// sees the bearer token. `host.html` and the xterm libs are the same files
/// Android ships (bundled under `xterm/`), so terminal behaviour is identical.
struct TerminalView: View {
    let session: DwSession
    let profile: ServerProfile
    @Binding var fontSize: Int
    /// Set to a non-nil string to send input to the session. The view clears it
    /// back to nil after forwarding so callers can watch for completion.
    @Binding var terminalInput: String?
    var controller: TerminalController? = nil

    @State private var connected = false
    @State private var disconnected = false
    @State private var hasContent = false
    @State private var reconnectGeneration = 0
    /// PWA startTermConnectWatchdog: 5 s per attempt, 3 re-subscribes, then the
    /// "Unable to connect…" panel (Retry / Use without terminal).
    @State private var watchdogEpoch = 0
    @State private var watchdogAttempt = 0
    @State private var watchdogFailed = false
    @State private var withoutTerminal = false
    static let connectTimeoutSeconds: Double = 5
    static let connectMaxRetries = 3

    private var loadingStatus: String {
        if watchdogAttempt > 0 {
            return String(format: L("Reconnecting to session…\nattempt %lld of %lld"), Int64(watchdogAttempt), Int64(Self.connectMaxRetries))
        }
        return connected ? "waiting for terminal…" : "connecting…"
    }

    var body: some View {
        ZStack {
            TerminalWebView(
                session: session,
                profile: profile,
                fontSize: fontSize,
                reconnectGeneration: reconnectGeneration,
                connected: $connected,
                disconnected: $disconnected,
                hasContent: $hasContent,
                terminalInput: $terminalInput,
                controller: controller
            )

            // Splash stays up through socket connect → subscribe → first pane_capture,
            // so the user never sees a black terminal (Android SessionLoadingOverlay).
            if !hasContent && !withoutTerminal {
                if watchdogFailed {
                    TermConnectFailedPanel(
                        maxRetries: Self.connectMaxRetries,
                        onRetry: retryConnect,
                        onUseWithout: { withoutTerminal = true }
                    )
                } else {
                    SessionLoadingOverlay(status: loadingStatus)
                        .transition(.opacity)
                }
            }
            // D46b (PWA minimal): no blocking disconnect overlay. The shared
            // transport reconnects on its own with backoff; the last frame stays
            // visible and the header status dot shows reachability. Long-press
            // on the dot forces an immediate resubscribe.
        }
        .animation(.easeInOut(duration: 0.25), value: hasContent)
        .background(DatawatchColors.background)
        // The xterm terminal is always dark (PWA termOpts.theme), whatever the app theme.
        .environment(\.colorScheme, .dark)
        .onReceive(NotificationCenter.default.publisher(for: .dwReconnectRequested)) { _ in
            connected = false
            disconnected = false
            reconnectGeneration += 1
        }
        .task(id: watchdogEpoch) { await runConnectWatchdog() }
    }

    /// Every 5 s without a first frame, re-subscribe (bumps the coordinator's
    /// generation), up to 3 times; then show the failure panel.
    private func runConnectWatchdog() async {
        watchdogAttempt = 0
        watchdogFailed = false
        while !hasContent && !Task.isCancelled {
            try? await Task.sleep(nanoseconds: UInt64(Self.connectTimeoutSeconds * 1_000_000_000))
            if hasContent || Task.isCancelled { break }
            if watchdogAttempt >= Self.connectMaxRetries {
                watchdogFailed = true
                break
            }
            watchdogAttempt += 1
            reconnectGeneration += 1
        }
    }

    private func retryConnect() {
        reconnectGeneration += 1
        watchdogEpoch += 1
    }
}

/// PWA "Unable to connect to session terminal" (Android TermConnectFailedPanel).
private struct TermConnectFailedPanel: View {
    let maxRetries: Int
    let onRetry: () -> Void
    let onUseWithout: () -> Void

    var body: some View {
        VStack(spacing: 10) {
            Text("Unable to connect to session terminal")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .multilineTextAlignment(.center)
            Text(String(format: L("Connection failed after %lld retries."), Int64(maxRetries)))
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.error)
            HStack(spacing: 8) {
                Button("Retry", action: onRetry)
                Button("Use without terminal", action: onUseWithout)
            }
            .buttonStyle(.bordered)
            .font(DatawatchFonts.labelSmall)
            .padding(.top, 4)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
    }
}

// MARK: - TerminalWebView (UIViewRepresentable)

private struct TerminalWebView: UIViewRepresentable {
    let session: DwSession
    let profile: ServerProfile
    let fontSize: Int
    let reconnectGeneration: Int
    @Binding var connected: Bool
    @Binding var disconnected: Bool
    @Binding var hasContent: Bool
    @Binding var terminalInput: String?
    var controller: TerminalController?

    static let bridgeName = "dwBridge"

    // host.html calls DwBridge.* exactly as on Android (where it is a Java
    // object); here each call becomes a WKScriptMessage to the Coordinator.
    private static let bridgeShim = """
    window.DwBridge = {
      onReady: function () {
        window.webkit.messageHandlers.dwBridge.postMessage({ type: 'ready' });
      },
      onInput: function (d) {
        window.webkit.messageHandlers.dwBridge.postMessage({ type: 'input', data: String(d) });
      },
      onResize: function (c, r) {
        window.webkit.messageHandlers.dwBridge.postMessage({ type: 'resize', cols: Number(c), rows: Number(r) });
      },
      onAutoFontSize: function (px) {
        window.webkit.messageHandlers.dwBridge.postMessage({ type: 'autoFontSize', px: Number(px) });
      }
    };
    """

    func makeCoordinator() -> Coordinator {
        Coordinator(
            session: session,
            profile: profile,
            connected: $connected,
            disconnected: $disconnected,
            hasContent: $hasContent,
            terminalInput: $terminalInput
        )
    }

    func makeUIView(context: Context) -> WKWebView {
        let contentController = WKUserContentController()
        contentController.add(context.coordinator, name: Self.bridgeName)
        contentController.addUserScript(
            WKUserScript(source: Self.bridgeShim, injectionTime: .atDocumentStart, forMainFrameOnly: true)
        )

        let config = WKWebViewConfiguration()
        config.userContentController = contentController

        let webView = DwWKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 0x0f/255, green: 0x11/255, blue: 0x17/255, alpha: 1)
        webView.scrollView.backgroundColor = webView.backgroundColor
        webView.scrollView.isScrollEnabled = false
        webView.navigationDelegate = context.coordinator
        context.coordinator.webView = webView
        context.coordinator.controller = controller
        controller?.webView = webView
        context.coordinator.requestedFontSize = fontSize
        context.coordinator.generation = reconnectGeneration
        webView.onLayout = { [weak coordinator = context.coordinator] size in
            coordinator?.onFrameChanged(size: size)
        }

        if let xtermDir = Bundle.main.resourceURL?.appendingPathComponent("xterm", isDirectory: true) {
            let host = xtermDir.appendingPathComponent("host.html")
            webView.loadFileURL(host, allowingReadAccessTo: xtermDir)
        }

        context.coordinator.start()
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.setFontSize(fontSize)
        if terminalInput != nil {
            context.coordinator.flushPendingInput()
        }
        if context.coordinator.generation != reconnectGeneration {
            context.coordinator.generation = reconnectGeneration
            context.coordinator.start()
        }
    }

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        coordinator.stop()
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: bridgeName)
        uiView.configuration.userContentController.removeAllUserScripts()
    }
}

// MARK: - DwWKWebView

/// WKWebView subclass that calls `onLayout` whenever its bounds change, so the
/// terminal can re-fit rows/cols after keyboard show/hide, rotation or split view.
private final class DwWKWebView: WKWebView {
    var onLayout: ((CGSize) -> Void)?
    override func layoutSubviews() {
        super.layoutSubviews()
        onLayout?(bounds.size)
    }
}

// MARK: - Coordinator

extension TerminalWebView {
    final class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
        @Binding var connected: Bool
        @Binding var disconnected: Bool
        @Binding var hasContent: Bool
        @Binding var terminalInput: String?
        weak var webView: WKWebView?
        var controller: TerminalController?
        var generation = 0
        var requestedFontSize = 9

        private let session: DwSession
        private let profile: ServerProfile
        private var subscription: IosSubscription?
        private var ready = false
        private var pendingCapture: SessionEventPaneCapture?
        private var appliedFontSize: Int?

        /// Short id — the key WsOutbound filters on and host storage uses.
        private var storageId: String { session.id }

        init(
            session: DwSession,
            profile: ServerProfile,
            connected: Binding<Bool>,
            disconnected: Binding<Bool>,
            hasContent: Binding<Bool>,
            terminalInput: Binding<String?>
        ) {
            self.session = session
            self.profile = profile
            _connected = connected
            _disconnected = disconnected
            _hasContent = hasContent
            _terminalInput = terminalInput
        }

        // MARK: Stream lifecycle

        /// (Re)subscribe to the session's event stream. Safe to call repeatedly.
        func start() {
            stop()
            EventMapperKt.resetPaneCaptureSeen(sessionId: storageId)
            subscription = IosServiceLocator.shared.subscribeSessionEvents(
                profile: profile,
                session: session
            ) { [weak self] event in
                DispatchQueue.main.async { self?.handle(event: event) }
            }
        }

        func stop() {
            subscription?.cancel()
            subscription = nil
        }

        // MARK: Inbound (Kotlin → xterm)

        private func handle(event: SessionEvent) {
            if event is SessionEventError {
                connected = false
                disconnected = true
                return
            }
            if !connected || disconnected {
                connected = true
                disconnected = false
            }
            if let limited = event as? SessionEventRateLimited {
                let retry = limited.retryAfter.map { Date(timeIntervalSince1970: Double($0.toEpochMilliseconds()) / 1000.0) }
                controller?.onRateLimited?(retry)
                return
            }
            if let capture = event as? SessionEventPaneCapture {
                if ready {
                    write(capture: capture)
                } else {
                    pendingCapture = capture
                }
            }
        }

        private func write(capture: SessionEventPaneCapture) {
            // host.html expects a JS *string* containing the JSON array (it JSON.parses it),
            // matching Android's JSONObject.quote(arrayLiteral) — hence the double encode.
            guard let arrayData = try? JSONSerialization.data(withJSONObject: capture.lines),
                  let arrayString = String(data: arrayData, encoding: .utf8),
                  let quotedData = try? JSONSerialization.data(withJSONObject: arrayString, options: .fragmentsAllowed),
                  let literal = String(data: quotedData, encoding: .utf8)
            else { return }
            evaluate("window.dwPaneCapture && window.dwPaneCapture(\(literal), \(capture.isFirst ? "true" : "false"));")
            if !hasContent { hasContent = true }
        }

        // MARK: Outbound (Swift → Kotlin → /ws)

        /// Forwards `terminalInput` as a `send_input` frame and clears the binding.
        func flushPendingInput() {
            guard let text = terminalInput else { return }
            terminalInput = nil
            _ = WsOutbound.shared.sendInput(sessionId: storageId, text: text)
        }

        // MARK: Sizing

        func setFontSize(_ px: Int) {
            requestedFontSize = px
            guard ready, appliedFontSize != px else { return }
            appliedFontSize = px
            evaluate("window.dwSetFontSize && window.dwSetFontSize(\(px));")
        }

        /// Called by `DwWKWebView.onLayout` with the new pixel size so xterm can
        /// recompute cols/rows exactly.
        func onFrameChanged(size: CGSize) {
            let w = Int(size.width)
            let h = Int(size.height)
            guard w > 0, h > 0 else { return }
            evaluate("window.dwExplicitSize && window.dwExplicitSize(\(w), \(h));")
        }

        private func evaluate(_ script: String) {
            webView?.evaluateJavaScript(script, completionHandler: nil)
        }

        // MARK: WKScriptMessageHandler (DwBridge shim → Swift)

        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard message.name == TerminalWebView.bridgeName,
                  let body = message.body as? [String: Any],
                  let type = body["type"] as? String
            else { return }

            switch type {
            case "ready":
                ready = true
                appliedFontSize = requestedFontSize
                evaluate("window.dwSetFontSize && window.dwSetFontSize(\(requestedFontSize));")
                controller?.applyMinCols()
                if let capture = pendingCapture {
                    pendingCapture = nil
                    write(capture: capture)
                }
            case "input":
                if let data = body["data"] as? String {
                    _ = WsOutbound.shared.sendInput(sessionId: storageId, text: data)
                }
            case "resize":
                if let cols = body["cols"] as? Int, let rows = body["rows"] as? Int {
                    _ = WsOutbound.shared.sendResizeTerm(sessionId: storageId, cols: Int32(cols), rows: Int32(rows))
                }
            case "autoFontSize":
                if let px = body["px"] as? Int, px > 0 {
                    UserDefaults.standard.set(px, forKey: "dw.terminal.font_size_px")
                    // The page already applied it — record so updateUIView doesn't revert it.
                    requestedFontSize = px
                    appliedFontSize = px
                    controller?.onAutoFontSize?(px)
                }
            default:
                break
            }
        }

        // MARK: WKNavigationDelegate

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            // Only the bundled host page may load; nothing else navigates this view.
            decisionHandler(navigationAction.request.url?.isFileURL == true ? .allow : .cancel)
        }
    }
}

