import SwiftUI
import DatawatchShared

// iOS-D session-detail pieces: log-mode viewer, failed-task drill-down,
// channel `?` help, Yes / No / Stop quick-reply chips.

/// PWA log viewer for `output_mode == "log"` sessions (ACP / headless; app.js
/// renderOutput `log-viewer-mode`, Android `LogModeView`): output lines with ANSI
/// stripped, blank lines dropped, monospace, PWA `log-*` classes as colours.
/// Keeps its own session socket open so the composer can send input.
struct SessionLogView: View {
    let profile: ServerProfile
    let session: DwSession

    private struct Line: Identifiable {
        let id: Int
        let text: String
    }

    @State private var lines: [Line] = []
    @State private var nextId = 0
    @State private var subscription: IosSubscription? = nil
    @State private var connected = false
    private static let maxLines = 2000

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 2) {
                    if lines.isEmpty {
                        Text(L(connected ? "Waiting for output…" : "connecting…"))
                            .font(DatawatchFonts.bodyMedium)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                    }
                    ForEach(lines) { l in LogLineText(text: l.text).id(l.id) }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
            }
            .onChange(of: nextId) { _ in
                if let last = lines.last { proxy.scrollTo(last.id, anchor: .bottom) }
            }
        }
        .background(DatawatchColors.background)
        .onAppear(perform: start)
        .onDisappear { subscription?.cancel(); subscription = nil }
    }

    private func start() {
        guard subscription == nil else { return }
        subscription = IosServiceLocator.shared.subscribeSessionEvents(profile: profile, session: session) { event in
            DispatchQueue.main.async { handle(event) }
        }
    }

    private func handle(_ event: SessionEvent) {
        if event is SessionEventError { connected = false; return }
        connected = true
        guard let out = event as? SessionEventOutput else { return }
        var added: [Line] = []
        var id = nextId
        for raw in out.body.components(separatedBy: "\n") {
            let clean = LogLineText.stripAnsi(raw).trimmingCharacters(in: CharacterSet(charactersIn: "\r"))
            if clean.trimmingCharacters(in: .whitespaces).isEmpty { continue }
            added.append(Line(id: id, text: clean))
            id += 1
        }
        guard !added.isEmpty else { return }
        lines.append(contentsOf: added)
        if lines.count > Self.maxLines { lines.removeFirst(lines.count - Self.maxLines) }
        nextId = id
    }
}

/// One log line, coloured by the PWA classes (later CSS rules win:
/// error > ready > processing > acp-status).
private struct LogLineText: View {
    let text: String

    private static let ansi: NSRegularExpression? =
        try? NSRegularExpression(pattern: "\u{1B}\\[[0-9;?]*[ -/]*[@-~]")

    static func stripAnsi(_ s: String) -> String {
        guard let re = ansi else { return s }
        let range = NSRange(s.startIndex..<s.endIndex, in: s)
        return re.stringByReplacingMatches(in: s, options: [], range: range, withTemplate: "")
    }

    private var color: Color {
        if text.contains("error") || text.contains("failed") { return DatawatchColors.error }
        if text.contains("ready") || text.contains("awaiting input") { return DatawatchColors.success }
        if text.contains("thinking") || text.contains("processing") { return DatawatchColors.warning }
        if text.contains("[opencode-acp]") { return DatawatchColors.primary }
        return DatawatchColors.onSurfaceMuted
    }

    var body: some View {
        Text(text)
            .font(DatawatchFonts.terminalSmall)
            .fontWeight(text.contains("[opencode-acp]") ? .semibold : .regular)
            .foregroundStyle(color)
            .frame(maxWidth: .infinity, alignment: .leading)
            .textSelection(.enabled)
    }
}

/// PWA `renderFailedDrilldown`: red-edged inset under a failed task listing the
/// last buffered hook events ("HH:mm:ss event · tool").
struct FailedDrilldownView: View {
    let events: [TelemetryHookEventDto]

    var body: some View {
        HStack(spacing: 0) {
            Rectangle().fill(DatawatchColors.error).frame(width: 2)
            VStack(alignment: .leading, spacing: 2) {
                Text("Last 5 events before failure")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(DatawatchColors.error)
                ForEach(Array(events.enumerated()), id: \.offset) { _, e in
                    Text(Self.line(e))
                        .font(.caption2)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            Spacer(minLength: 0)
        }
        .background(DatawatchColors.background, in: RoundedRectangle(cornerRadius: 2))
        .padding(.leading, 22)
        .padding(.vertical, 2)
    }

    private static func line(_ e: TelemetryHookEventDto) -> String {
        var parts: [String] = []
        let time: String = clock(e.ts)
        if !time.isEmpty { parts.append(time) }
        if !e.event.isEmpty { parts.append(e.event) }
        var s: String = parts.joined(separator: " ")
        if !e.tool.isEmpty { s += " · " + e.tool }
        return s
    }

    private static func clock(_ iso: String) -> String {
        guard !iso.isEmpty else { return "" }
        let withFrac = ISO8601DateFormatter()
        withFrac.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let plain = ISO8601DateFormatter()
        guard let date = withFrac.date(from: iso) ?? plain.date(from: iso) else { return "" }
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f.string(from: date)
    }
}

/// PWA `showChannelHelp` ("Channel Commands") / Android `ChannelHelpDialog`,
/// opened from the `?` button while the Channel tab is active.
struct ChannelHelpSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("The Channel tab communicates via MCP tool calls, bypassing tmux. Messages appear directly in the LLM's context.")
                    section("You can send:", items: [
                        "Free-text instructions or follow-up questions",
                        "Code review feedback or corrections",
                        "Task reprioritization or scope changes",
                    ])
                    slashCommands
                    section("LLM can send back:", items: [
                        "Progress updates and status messages",
                        "Questions requiring your input",
                        "Completion notifications",
                    ])
                    Text("Channel replies appear as amber lines. Tmux tab shows raw terminal output. Use Channel for structured communication, Tmux for direct terminal access.")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
            }
            .background(DatawatchColors.background)
            .navigationTitle("Channel Commands")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .dwThemed()
    }

    private func section(_ title: String, items: [String]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(L(title)).bold()
            ForEach(items, id: \.self) { item in
                Text("• " + L(item))
            }
        }
    }

    private var slashCommands: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Claude slash commands (tmux tab):").bold()
            slash("/mcp", "restart MCP servers")
            slash("/effort", "toggle effort level")
            slash("/help", "claude help")
            slash("/compact", "compact conversation")
            slash("/clear", "clear screen")
        }
    }

    private func slash(_ cmd: String, _ desc: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(cmd).font(DatawatchFonts.terminalSmall)
            Text("— " + L(desc))
        }
    }
}

/// D68b quick-reply chips (Android `QuickReplyButtons`): Yes / No / Stop under a
/// waiting prompt. Stop sends the word "stop" (a graceful in-context halt), not a kill.
struct QuickReplyChips: View {
    let onReply: (String) -> Void

    var body: some View {
        HStack(spacing: 8) {
            chip("Yes", send: "yes\r")
            chip("No", send: "no\r")
            chip("Stop", send: "stop\r")
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.top, 6)
    }

    private func chip(_ title: String, send: String) -> some View {
        Button {
            onReply(send)
        } label: {
            Text(L(title))
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(DatawatchColors.onSurface)
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(DatawatchColors.chipBackground, in: Capsule())
                .overlay(Capsule().stroke(DatawatchColors.border, lineWidth: 1))
        }
        .buttonStyle(.borderless)
    }
}
