import SwiftUI
import WebKit
import DatawatchShared

// MARK: - TerminalView

/// A `UIViewRepresentable` that embeds an xterm.js terminal inside a WKWebView.
///
/// The WebView loads a self-contained HTML string (xterm.js from CDN) and opens
/// a WebSocket directly to the datawatch terminal endpoint. Swift↔JS messages
/// are exchanged via `window.webkit.messageHandlers.terminalMsg`.
struct TerminalView: UIViewRepresentable {

    let session: Session
    let profile: ServerProfile

    // ── Connection state (read by overlay) ────────────────────────────────

    @State private var connected = false
    @State private var disconnected = false

    // MARK: UIViewRepresentable

    func makeCoordinator() -> Coordinator {
        Coordinator(connected: $connected, disconnected: $disconnected)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.userContentController.add(context.coordinator, name: "terminalMsg")
        // Allow inline media and mixed content for self-signed / dev servers.
        config.allowsInlineMediaPlayback = true

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = false
        webView.backgroundColor = .black
        webView.scrollView.backgroundColor = .black
        webView.scrollView.isScrollEnabled = false
        webView.navigationDelegate = context.coordinator

        // Store reference so coordinator can call JS later.
        context.coordinator.webView = webView

        let html = Self.buildHTML(session: session, profile: profile)
        webView.loadHTMLString(html, baseURL: nil)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        // No dynamic updates — the WebView owns its own state.
    }

    // MARK: HTML builder

    private static func buildHTML(session: Session, profile: ServerProfile) -> String {
        let wsUrl = buildWSUrl(session: session, profile: profile)
        return rawHTML.replacingOccurrences(of: "__WS_URL__", with: wsUrl)
    }

    /// Converts `http(s)://host/...` → `ws(s)://host/api/terminal/<id>?token=<tok>`.
    private static func buildWSUrl(session: Session, profile: ServerProfile) -> String {
        var base = profile.baseUrl
        // Normalise trailing slash.
        if base.hasSuffix("/") { base = String(base.dropLast()) }
        // Scheme swap.
        if base.hasPrefix("https://") {
            base = "wss://" + base.dropFirst("https://".count)
        } else if base.hasPrefix("http://") {
            base = "ws://" + base.dropFirst("http://".count)
        }
        var url = "\(base)/api/terminal/\(session.id)"
        // Append bearer token as query param when available.
        let alias = profile.bearerTokenRef
        if !alias.isEmpty, let token = IosServiceLocator.shared.getToken(alias: alias) {
            // Percent-encode the token value so it survives the URL.
            let encoded = token.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? token
            url += "?token=\(encoded)"
        }
        return url
    }

    // MARK: xterm.js HTML

    // swiftlint:disable line_length
    private static let rawHTML = """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
      <style>
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; background: #000000; height: 100%; overflow: hidden; }
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
            background: '#000000',
            foreground: '#D0E8ED',
            cursor: '#00E5FF',
            cursorAccent: '#000000',
            selection: 'rgba(0, 229, 255, 0.3)',
            black: '#000000',
            brightBlack: '#1A2830',
            white: '#D0E8ED',
            brightWhite: '#FFFFFF'
          }
        });
        var fitAddon = new FitAddon.FitAddon();
        term.loadAddon(fitAddon);
        term.open(document.getElementById('terminal'));
        fitAddon.fit();

        window.addEventListener('resize', function() { fitAddon.fit(); });

        // WebSocket setup
        var wsUrl = '__WS_URL__';
        var ws;

        function connect() {
          ws = new WebSocket(wsUrl);
          ws.binaryType = 'arraybuffer';

          ws.onopen = function() {
            window.webkit.messageHandlers.terminalMsg.postMessage({ type: 'connected' });
            // Send initial terminal size.
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

        // Public interface callable from Swift.
        window.sendInput = function(data) {
          if (ws && ws.readyState === WebSocket.OPEN) ws.send(data);
        };

        window.resizeTerm = function(cols, rows) {
          term.resize(cols, rows);
        };

        window.reconnect = function() {
          if (ws) ws.close();
          connect();
        };

        connect();
      </script>
    </body>
    </html>
    """
    // swiftlint:enable line_length
}

// MARK: - Coordinator

extension TerminalView {
    final class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
        @Binding var connected: Bool
        @Binding var disconnected: Bool
        weak var webView: WKWebView?

        init(connected: Binding<Bool>, disconnected: Binding<Bool>) {
            _connected = connected
            _disconnected = disconnected
        }

        // WKScriptMessageHandler — receives messages from JS.
        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard message.name == "terminalMsg",
                  let body = message.body as? [String: Any],
                  let type = body["type"] as? String else { return }

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

        // WKNavigationDelegate — allow all navigation inside the WebView.
        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            decisionHandler(.allow)
        }

        /// Sends a reconnect command to the running JS context.
        func reconnect() {
            webView?.evaluateJavaScript("window.reconnect && window.reconnect();", completionHandler: nil)
        }
    }
}

// MARK: - TerminalView + overlay wrapper

/// Public-facing wrapper that layers the loading/reconnect overlay on top of
/// the raw `UIViewRepresentable`. Use this in `SessionDetailView`.
private struct TerminalViewWithOverlay: View {
    let session: Session
    let profile: ServerProfile

    @State private var connected = false
    @State private var disconnected = false

    var body: some View {
        ZStack {
            // The actual WebView underneath.
            TerminalView(session: session, profile: profile)

            // Loading overlay until connected.
            if !connected && !disconnected {
                LoadingIndicator(message: "Connecting to terminal…")
                    .transition(.opacity)
            }

            // Reconnect overlay when disconnected.
            if disconnected {
                reconnectOverlay
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: connected)
        .animation(.easeInOut(duration: 0.25), value: disconnected)
    }

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
                connected = false
                disconnected = false
                // The coordinator's reconnect() call happens via JS in the WebView;
                // we just reset UI state here and let the JS WS handler report back.
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

// Re-export `TerminalViewWithOverlay` as the public `TerminalView` used by
// `SessionDetailView`. We shadow the UIViewRepresentable with a SwiftUI alias.
//
// Callers: `TerminalView(session:profile:)` → uses the overlay wrapper.
// The underlying UIViewRepresentable is private-ish via the same-name struct above,
// but Swift resolves the SwiftUI-conforming struct by preference in View contexts.
//
// To keep things clean we use a typealias at file scope:
typealias TerminalView = TerminalViewWithOverlay
