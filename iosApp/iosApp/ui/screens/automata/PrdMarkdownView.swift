import SwiftUI
import WebKit

/// D53b: automaton spec rendering on iOS. Markdown is rendered natively (block
/// parser below + `AttributedString(markdown:)` for inline styling, GFM tables via
/// `Grid`); ```mermaid fences render in a WKWebView that loads mermaid from the
/// CDN at runtime (same pinned version as the PWA). Offline / load failure falls
/// back to the diagram's source block.
struct PrdMarkdownView: View {
    let source: String

    var body: some View {
        let blocks: [MdBlock] = MdBlock.parse(source)
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(blocks.enumerated()), id: \.offset) { _, b in
                MdBlockView(block: b)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

enum MdBlock {
    case heading(level: Int, text: String)
    case paragraph(String)
    case listItem(marker: String, text: String, indent: Int)
    case quote(String)
    case code(lang: String, text: String)
    case table([[String]])
    case rule

    static func parse(_ src: String) -> [MdBlock] {
        let lines: [String] = src.replacingOccurrences(of: "\r\n", with: "\n").components(separatedBy: "\n")
        var out: [MdBlock] = []
        var para: [String] = []
        var i = 0

        func flush() {
            if !para.isEmpty {
                out.append(.paragraph(para.joined(separator: " ")))
                para.removeAll()
            }
        }

        while i < lines.count {
            let line = lines[i]
            let trimmed = line.trimmingCharacters(in: .whitespaces)
            if trimmed.hasPrefix("```") {
                flush()
                let lang = String(trimmed.dropFirst(3)).trimmingCharacters(in: .whitespaces).lowercased()
                var body: [String] = []
                i += 1
                while i < lines.count && !lines[i].trimmingCharacters(in: .whitespaces).hasPrefix("```") {
                    body.append(lines[i])
                    i += 1
                }
                out.append(.code(lang: lang, text: body.joined(separator: "\n")))
                i += 1
                continue
            }
            if trimmed.isEmpty {
                flush()
            } else if let h = headingLevel(trimmed) {
                flush()
                out.append(.heading(level: h, text: String(trimmed.dropFirst(h)).trimmingCharacters(in: .whitespaces)))
            } else if isRule(trimmed) {
                flush()
                out.append(.rule)
            } else if trimmed.hasPrefix("|") {
                flush()
                var rows: [[String]] = []
                while i < lines.count && lines[i].trimmingCharacters(in: .whitespaces).hasPrefix("|") {
                    let row = lines[i].trimmingCharacters(in: .whitespaces)
                    if !isTableSeparator(row) { rows.append(cells(row)) }
                    i += 1
                }
                out.append(.table(rows))
                continue
            } else if let item = listItem(line) {
                flush()
                out.append(item)
            } else if trimmed.hasPrefix(">") {
                flush()
                out.append(.quote(String(trimmed.dropFirst()).trimmingCharacters(in: .whitespaces)))
            } else {
                para.append(trimmed)
            }
            i += 1
        }
        flush()
        return out
    }

    private static func headingLevel(_ s: String) -> Int? {
        var n = 0
        for ch in s { if ch == "#" { n += 1 } else { break } }
        guard n >= 1, n <= 6, s.count > n, s[s.index(s.startIndex, offsetBy: n)] == " " else { return nil }
        return n
    }

    private static func isRule(_ s: String) -> Bool {
        let compact = s.replacingOccurrences(of: " ", with: "")
        guard compact.count >= 3, let first = compact.first, "-*_".contains(first) else { return false }
        return compact.allSatisfy { $0 == first }
    }

    private static func isTableSeparator(_ s: String) -> Bool {
        s.allSatisfy { "|-: ".contains($0) }
    }

    private static func cells(_ row: String) -> [String] {
        var r = row
        if r.hasPrefix("|") { r.removeFirst() }
        if r.hasSuffix("|") { r.removeLast() }
        return r.components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces) }
    }

    private static func listItem(_ line: String) -> MdBlock? {
        let leading: Int = line.prefix(while: { $0 == " " }).count
        let body = line.dropFirst(leading)
        if let f = body.first, "-*+".contains(f), body.dropFirst().first == " " {
            return .listItem(marker: "•", text: String(body.dropFirst(2)), indent: leading / 2)
        }
        let digits = body.prefix(while: { $0.isNumber })
        if !digits.isEmpty {
            let rest = body.dropFirst(digits.count)
            if let p = rest.first, p == "." || p == ")", rest.dropFirst().first == " " {
                return .listItem(marker: String(digits) + ".", text: String(rest.dropFirst(2)), indent: leading / 2)
            }
        }
        return nil
    }
}

/// Inline markdown (bold, italic, code, links) → AttributedString; plain on failure.
func mdInline(_ s: String) -> AttributedString {
    let opts = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
    return (try? AttributedString(markdown: s, options: opts)) ?? AttributedString(s)
}

private struct MdBlockView: View {
    let block: MdBlock

    var body: some View {
        switch block {
        case .heading(let level, let text):
            Text(mdInline(text))
                .font(Self.headingFont(level))
                .foregroundStyle(DatawatchColors.onSurface)
                .padding(.top, 4)
        case .paragraph(let text):
            Text(mdInline(text))
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .textSelection(.enabled)
        case .listItem(let marker, let text, let indent):
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(marker).foregroundStyle(DatawatchColors.onSurfaceMuted)
                Text(mdInline(text)).foregroundStyle(DatawatchColors.onSurface)
            }
            .font(DatawatchFonts.bodyMedium)
            .padding(.leading, CGFloat(indent) * 14)
        case .quote(let text):
            Text(mdInline(text))
                .font(DatawatchFonts.bodyMedium)
                .italic()
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .padding(.leading, 10)
                .overlay(alignment: .leading) { Rectangle().fill(DatawatchColors.border).frame(width: 3) }
        case .code(let lang, let text):
            if lang == "mermaid" {
                MermaidBlock(source: text)
            } else {
                MdCodeBlock(text: text)
            }
        case .table(let rows):
            MdTable(rows: rows)
        case .rule:
            Divider().background(DatawatchColors.border)
        }
    }

    private static func headingFont(_ level: Int) -> Font {
        if level <= 1 { return Font.title3.weight(.bold) }
        if level == 2 { return Font.headline }
        return Font.subheadline.weight(.semibold)
    }
}

struct MdCodeBlock: View {
    let text: String
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            Text(text)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .textSelection(.enabled)
                .padding(8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
    }
}

/// GFM table: first row is the header; horizontal scroll for wide tables.
private struct MdTable: View {
    let rows: [[String]]

    var body: some View {
        let cols: Int = rows.map { $0.count }.max() ?? 0
        ScrollView(.horizontal, showsIndicators: false) {
            Grid(alignment: .leading, horizontalSpacing: 12, verticalSpacing: 6) {
                ForEach(Array(rows.enumerated()), id: \.offset) { ri, row in
                    GridRow {
                        ForEach(0..<cols, id: \.self) { ci in
                            cell(ci < row.count ? row[ci] : "", header: ri == 0)
                        }
                    }
                    if ri == 0 { Divider().gridCellUnsizedAxes(.horizontal) }
                }
            }
            .padding(8)
        }
        .background(DatawatchColors.surface2.opacity(0.5), in: RoundedRectangle(cornerRadius: 4))
    }

    private func cell(_ s: String, header: Bool) -> some View {
        Text(mdInline(s))
            .font(header ? DatawatchFonts.labelSmall.weight(.semibold) : DatawatchFonts.labelSmall)
            .foregroundStyle(DatawatchColors.onSurface)
            .frame(minWidth: 60, alignment: .leading)
    }
}

/// Mermaid fence: WKWebView + the bundled mermaid (ADR-0051); source block when it can't render.
struct MermaidBlock: View {
    let source: String
    @Environment(\.colorScheme) private var scheme
    @State private var height: CGFloat = 0
    @State private var failed = false

    var body: some View {
        if failed {
            VStack(alignment: .leading, spacing: 4) {
                Text("Mermaid diagram (couldn't load the renderer — showing source)")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                MdCodeBlock(text: source)
            }
        } else {
            MermaidWebView(source: source, dark: scheme == .dark, height: $height, failed: $failed)
                .frame(height: max(height, CGFloat(80)))
                .frame(maxWidth: .infinity)
                .accessibilityLabel("Mermaid diagram")
        }
    }
}

private struct MermaidWebView: UIViewRepresentable {
    let source: String
    let dark: Bool
    @Binding var height: CGFloat
    @Binding var failed: Bool

    /// Bundled mermaid (folder reference `mermaid/` shared with Android,
    /// ADR-0051) — read once and injected as a user script, so no CDN fetch
    /// and diagrams render offline. Nil if the resource is missing.
    private static let bundledScript: String? = {
        guard let url = Bundle.main.url(forResource: "mermaid.min", withExtension: "js", subdirectory: "mermaid") else { return nil }
        return try? String(contentsOf: url, encoding: .utf8)
    }()

    func makeCoordinator() -> Coordinator { Coordinator(height: $height, failed: $failed) }

    func makeUIView(context: Context) -> WKWebView {
        let controller = WKUserContentController()
        controller.add(WeakScriptHandler(context.coordinator), name: "dw")
        if let js = Self.bundledScript {
            controller.addUserScript(WKUserScript(source: js, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        }
        let config = WKWebViewConfiguration()
        config.userContentController = controller
        let wv = WKWebView(frame: .zero, configuration: config)
        wv.isOpaque = false
        wv.backgroundColor = .clear
        wv.scrollView.backgroundColor = .clear
        wv.scrollView.isScrollEnabled = false
        wv.loadHTMLString(html(), baseURL: nil)
        context.coordinator.startTimeout()
        return wv
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "dw")
        coordinator.cancel()
    }

    private func html() -> String {
        let escaped = source
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
        let theme = dark ? "dark" : "default"
        return """
        <!DOCTYPE html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;background:transparent}svg{max-width:100%;height:auto}</style>
        <script>function dwPost(m){try{window.webkit.messageHandlers.dw.postMessage(m)}catch(e){}}</script>
        </head><body><pre class="mermaid">\(escaped)</pre>
        <script>
        (function(){
          if(!window.mermaid){dwPost('err');return;}
          mermaid.initialize({startOnLoad:false,theme:'\(theme)',securityLevel:'strict'});
          mermaid.run({querySelector:'.mermaid'})
            .then(function(){dwPost('ok:'+Math.ceil(document.body.scrollHeight));})
            .catch(function(){dwPost('err');});
        })();
        </script></body></html>
        """
    }

    final class Coordinator: NSObject, WKScriptMessageHandler {
        private let height: Binding<CGFloat>
        private let failed: Binding<Bool>
        private var settled = false
        private var timeout: DispatchWorkItem? = nil

        init(height: Binding<CGFloat>, failed: Binding<Bool>) {
            self.height = height
            self.failed = failed
        }

        func startTimeout() {
            let work = DispatchWorkItem { [weak self] in self?.finish(ok: false, h: 0) }
            timeout = work
            DispatchQueue.main.asyncAfter(deadline: .now() + 10, execute: work)
        }

        func cancel() {
            timeout?.cancel()
            timeout = nil
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let body = message.body as? String else { return }
            if body.hasPrefix("ok:") {
                let h: Double = Double(body.dropFirst(3)) ?? 0
                finish(ok: true, h: CGFloat(h))
            } else {
                finish(ok: false, h: 0)
            }
        }

        private func finish(ok: Bool, h: CGFloat) {
            guard !settled else { return }
            settled = true
            cancel()
            DispatchQueue.main.async {
                if ok { self.height.wrappedValue = h } else { self.failed.wrappedValue = true }
            }
        }
    }
}

/// Breaks the WKUserContentController → handler retain cycle.
private final class WeakScriptHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ target: WKScriptMessageHandler) { self.target = target }
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(userContentController, didReceive: message)
    }
}
