import SwiftUI
import WebKit
import DatawatchShared

// MARK: - TerminalView (public SwiftUI entry point)

/// SwiftUI view that embeds an xterm.js terminal inside a `WKWebView`.
///
/// Layers a loading spinner until the WebSocket connects, and a reconnect
/// button when the socket drops. The underlying WebView (`TerminalWebView`)
/// is a `UIViewRepresentable` that holds all WKWebView / JS bridge logic.
struct TerminalView: View {
    let session: Session
    let profile: ServerProfile

    @State private var connected = false
    @State private var disconnected = false

    var body: some View {
        ZStack {
            // Underlying WKWebView.
            TerminalWebView(
                session: session,
                profile: profile,
                connected: $connected,
                disconnected: $disconnected
            )

            // Loading overlay until WS opens.
            if !connected && !disconnected {
                LoadingIndicator(message: "Connecting to terminal…")
                    .transition(.opacity)
            }

            // Reconnect overlay after WS drops.
            if disconnected {
                reconnectOverlay
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: connected)
        .animation(.easeInOut(duration: 0.25), value: disconnected)
        .background(DatawatchColors.background)
    }

    // ── Reconnect overlay ─────────────────────────────────────────────────

    private var reconnectOverlay: some View {
        VStack(spacing: 20) {
            Image(systemName: "wifi.slash")
                .font(.system(size: 40))
                .foregroundStyle(DatawatchColors.error)
                .accessibilityHidden(true)

            Text("Terminal disconnected")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurface)

            Button("Reconnect") {
                // Reset UI state; JS in the WebView will report back via
                // the "connected"/"disconnected" messages.
                disconnected = false
                connected = false
            }
            .font(DatawatchFonts.bodyMedium)
            .foregroundStyle(DatawatchColors.primary)
            .padding(.horizontal, 24)
            .padding(.vertical, 10)
            .overlay(Capsule().stroke(DatawatchColors.primary, lineWidth: 1))
            .accessibilityHint("Attempts to reconnect the WebSocket")
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background.opacity(0.92))
    }
}

// MARK: - TerminalWebView (UIViewRepresentable)

/// Internal `UIViewRepresentable` that owns the `WKWebView` and the
/// Swift ↔ JS message bridge. Not used directly — use `TerminalView` instead.
private struct TerminalWebView: UIViewRepresentable {

    let session: Session
    let profile: ServerProfile
    @Binding var connected: Bool
    @Binding var disconnected: Bool

    // MARK: UIViewRepresentable

    func makeCoordinator() -> Coordinator {
        Coordinator(connected: $connected, disconnected: $disconnected)
    }

    func makeUIView(context: Context) -> WKWebView {
        let contentController = WKUserContentController()
        contentController.add(context.coordinator, name: "terminalMsg")

        let config = WKWebViewConfiguration()
        config.userContentController = contentController
        config.allowsInlineMediaPlayback = true

        let webView = DwWKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = UIColor(red: 0x0f/255, green: 0x11/255, blue: 0x17/255, alpha: 1)
        webView.scrollView.backgroundColor = UIColor(red: 0x0f/255, green: 0x11/255, blue: 0x17/255, alpha: 1)
        webView.scrollView.isScrollEnabled = false
        webView.navigationDelegate = context.coordinator
        context.coordinator.webView = webView
        webView.onLayout = { [weak coordinator = context.coordinator] size in
            coordinator?.onFrameChanged(size: size)
        }

        let html = Self.buildHTML(session: session, profile: profile)
        webView.loadHTMLString(html, baseURL: nil)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        // Stateless after initial load — WebView owns its own lifecycle.
    }

    // MARK: HTML / WS URL builder

    private static func buildHTML(session: Session, profile: ServerProfile) -> String {
        let wsUrl = buildWSUrl(session: session, profile: profile)
        return xtermHTML.replacingOccurrences(of: "__WS_URL__", with: wsUrl)
    }

    /// Converts `http(s)://host` → `ws(s)://host/api/terminal/<sessionId>?token=<tok>`.
    private static func buildWSUrl(session: Session, profile: ServerProfile) -> String {
        var base = profile.baseUrl
        if base.hasSuffix("/") { base = String(base.dropLast()) }

        if base.hasPrefix("https://") {
            base = "wss://" + base.dropFirst("https://".count)
        } else if base.hasPrefix("http://") {
            base = "ws://" + base.dropFirst("http://".count)
        }

        var url = "\(base)/api/terminal/\(session.id)"

        let alias = profile.bearerTokenRef
        if !alias.isEmpty,
           let token = IosServiceLocator.shared.getToken(alias: alias),
           !token.isEmpty {
            let encoded = token.addingPercentEncoding(
                withAllowedCharacters: .urlQueryAllowed
            ) ?? token
            url += "?token=\(encoded)"
        }
        return url
    }

    // MARK: xterm.js HTML template

    // swiftlint:disable:next line_length
    private static let xtermHTML: String = """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
      <style>
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; background: #0f1117; height: 100%; overflow: hidden; }
        #terminal { height: 100vh; width: 100vw; }
        .xterm-viewport { overflow: hidden !important; }
      </style>
      <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/xterm@5.3.0/css/xterm.css"/>
    </head>
    <body>
      <div id="terminal"></div>
      <script src="https://cdn.jsdelivr.net/npm/xterm@5.3.0/lib/xterm.js"></script>
      <script src="https://cdn.jsdelivr.net/npm/xterm-addon-fit@0.8.0/lib/xterm-addon-fit.js"></script>
      <script>
        var term = new Terminal({
          cursorBlink: true,
          allowTransparency: false,
          scrollback: 5000,
          theme: {
            background: '#0f1117',
            foreground: '#e2e8f0',
            cursor: '#a855f7',
            cursorAccent: '#0f1117',
            selection: 'rgba(168, 85, 247, 0.3)'
          }
        });
        var fitAddon = new FitAddon.FitAddon();
        term.loadAddon(fitAddon);
        term.open(document.getElementById('terminal'));
        fitAddon.fit();
        window.addEventListener('resize', function() { fitAddon.fit(); });

        // Called from native when the WKWebView frame changes size (keyboard, rotation).
        // Takes pixel dimensions of the WKWebView frame, computes char cell size from
        // the terminal's current font metrics, and resizes xterm to fit exactly.
        window.dwExplicitSize = function(w, h) {
          var core = term._core;
          var cw = core._renderService.dimensions.css.cell.width;
          var ch = core._renderService.dimensions.css.cell.height;
          if (!cw || !ch) { fitAddon.fit(); return; }
          var cols = Math.max(2, Math.floor(w / cw));
          var rows = Math.max(1, Math.floor(h / ch));
          if (cols !== term.cols || rows !== term.rows) {
            term.resize(cols, rows);
          }
          fitAddon.fit();
        };

        var wsUrl = '__WS_URL__';
        var ws;

        function connect() {
          ws = new WebSocket(wsUrl);
          ws.binaryType = 'arraybuffer';

          ws.onopen = function() {
            window.webkit.messageHandlers.terminalMsg.postMessage({ type: 'connected' });
            var dims = fitAddon.proposeDimensions();
            if (dims) {
              ws.send(JSON.stringify({ type: 'resize', cols: dims.cols, rows: dims.rows }));
            }
          };

          ws.onclose = function() {
            window.webkit.messageHandlers.terminalMsg.postMessage({ type: 'disconnected' });
            term.write('\\r\\n\\x1b[31m[Disconnected]\\x1b[0m\\r\\n');
          };

          ws.onerror = function() {
            window.webkit.messageHandlers.terminalMsg.postMessage({ type: 'error' });
          };

          ws.onmessage = function(e) {
            if (typeof e.data === 'string') {
              term.write(e.data);
            } else {
              term.write(new Uint8Array(e.data));
            }
          };
        }

        term.onData(function(data) {
          if (ws && ws.readyState === WebSocket.OPEN) ws.send(data);
        });

        term.onResize(function(size) {
          if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: 'resize', cols: size.cols, rows: size.rows }));
          }
        });

        window.sendInput = function(data) {
          if (ws && ws.readyState === WebSocket.OPEN) ws.send(data);
        };

        window.resizeTerm = function(cols, rows) {
          term.resize(cols, rows);
        };

        window.reconnect = function() {
          if (ws) { try { ws.close(); } catch(e) {} }
          connect();
        };

        connect();
      </script>
    </body>
    </html>
    """
}

// MARK: - DwWKWebView

/// WKWebView subclass that calls `onLayout` whenever its bounds change.
/// This fires after SwiftUI's keyboard-avoidance shrinks the view, giving
/// the terminal a chance to re-fit its rows/cols to the new visible area.
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
        weak var webView: WKWebView?

        init(connected: Binding<Bool>, disconnected: Binding<Bool>) {
            _connected = connected
            _disconnected = disconnected
        }

        // WKScriptMessageHandler — JS → Swift bridge.
        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard message.name == "terminalMsg",
                  let body = message.body as? [String: Any],
                  let type = body["type"] as? String
            else { return }

            DispatchQueue.main.async { [weak self] in
                switch type {
                case "connected":
                    self?.connected = true
                    self?.disconnected = false
                case "disconnected", "error":
                    self?.connected = false
                    self?.disconnected = true
                default:
                    break
                }
            }
        }

        // WKNavigationDelegate — allow all navigations inside the WebView.
        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            decisionHandler(.allow)
        }

        /// Called by `DwWKWebView.onLayout` whenever the WebView frame changes
        /// (keyboard show/hide, rotation, split-view resize). Passes the new
        /// pixel dimensions into JS so xterm can recalculate cols/rows exactly.
        func onFrameChanged(size: CGSize) {
            let w = Int(size.width)
            let h = Int(size.height)
            guard w > 0, h > 0 else { return }
            webView?.evaluateJavaScript(
                "window.dwExplicitSize && window.dwExplicitSize(\(w), \(h));",
                completionHandler: nil
            )
        }

        /// Triggers a JS-level WebSocket reconnect without reloading the page.
        func reconnect() {
            webView?.evaluateJavaScript(
                "window.reconnect && window.reconnect();",
                completionHandler: nil
            )
        }
    }
}

#if DEBUG
#Preview("Terminal — connecting") {
    let profile = ServerProfile(
        id: "preview-server",
        displayName: "Local dev",
        baseUrl: "http://localhost:8080",
        bearerTokenRef: "",
        trustAnchorSha256: nil,
        reachabilityProfileId: nil,
        enabled: true,
        createdTs: 0,
        lastSeenTs: 0,
        signalLinked: false
    )
    let now = Kotlinx_datetimeInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0)
    let session = Session(
        id: "abc123",
        serverProfileId: "preview-server",
        hostnamePrefix: "ring",
        state: SessionState.running,
        taskSummary: "Preview session",
        createdAt: now,
        lastActivityAt: now,
        muted: false,
        lastPrompt: nil,
        name: nil,
        promptContext: nil,
        lastResponse: nil,
        backend: "claude-code",
        outputMode: "terminal",
        inputMode: "tmux",
        agentId: nil,
        llmRef: nil,
        computeNodeRef: nil,
        chrome: false
    )
    TerminalView(session: session, profile: profile)
        .preferredColorScheme(.dark)
}
#endif
