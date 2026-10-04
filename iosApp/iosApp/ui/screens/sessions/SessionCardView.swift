import SwiftUI
import DatawatchShared

/// Inline "What's it doing?" state for one card (PWA state.currentStatus[fullId]).
struct CardStatus {
    var text: String = ""
    var long: String = ""
    var generatedAt: Date = Date()
    var loading: Bool = false
    var longExpanded: Bool = false
}

/// PWA session state vocabulary + badge colours (style.css .state-badge-*).
enum SessionStateStyle {
    static func key(_ s: SessionState) -> String {
        switch s {
        case .running: return "running"
        case .waiting: return "waiting_input"
        case .rateLimited: return "rate_limited"
        case .completed: return "complete"
        case .killed: return "killed"
        case .error: return "failed"
        default: return "unknown"
        }
    }

    static func color(_ s: SessionState) -> Color {
        switch s {
        case .running: return DatawatchColors.success
        case .waiting: return DatawatchColors.waiting
        case .rateLimited: return DatawatchColors.warning
        case .error: return DatawatchColors.error
        default: return DatawatchColors.onSurfaceMuted
        }
    }

    static func isDone(_ s: SessionState) -> Bool { s == .completed || s == .killed || s == .error }
}

/// State pill: border currentColor, 11/600, pulse on running (PWA dw-running-pulse
/// 700 ms alternate; off under Reduce Motion) — D18a.
struct SessionStatePill: View {
    let state: SessionState
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dim = false

    var body: some View {
        let color = SessionStateStyle.color(state)
        let running = state == .running && !reduceMotion
        Text(SessionStateStyle.key(state))
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 7)
            .padding(.vertical, 1)
            .background(color.opacity(0.15), in: Capsule())
            .overlay(Capsule().stroke(color, lineWidth: 1))
            .opacity(running && dim ? 0.55 : 1)
            .onAppear {
                guard running else { return }
                withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) { dim = true }
            }
            .onChange(of: running) { on in
                if on {
                    withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) { dim = true }
                } else {
                    withAnimation(.none) { dim = false }
                }
            }
    }
}

/// Session card (parity D13a / D14a / D16a; PWA sessionCard app.js).
/// Header: name/task (80 chars, 13/600) · inline actions (■ Stop / ▶ / ↻ Restart / 🗑) · | · state pill.
/// Line 2: id pill · LLM badge · worker · host (multi-server) · 📄 Response · elapsed · age.
/// Then the waiting prompt (last 4 lines) or the inline current-status row.
struct SessionCardView: View {
    let session: DwSession
    var showHost: Bool = false
    /// PWA server-badge: set in "All servers" mode.
    var serverName: String? = nil
    var status: CardStatus? = nil
    var selecting: Bool = false
    var selected: Bool = false
    var onStop: () -> Void = {}
    var onQuick: () -> Void = {}
    var onRestart: () -> Void = {}
    var onDelete: () -> Void = {}
    var onFetchStatus: () -> Void = {}
    var onToggleLong: () -> Void = {}
    var onToggleSelect: () -> Void = {}
    var onResponse: () -> Void = {}
    var onExpand: () -> Void = {}
    /// D61a: watched sessions' alerts drive the badge once any session is watched.
    var watched: Bool = false
    var onWatchToggle: (() -> Void)? = nil
    /// D62a: locally muted (swipe-to-mute) or server-reported muted.
    var muted: Bool = false

    private var isDone: Bool { SessionStateStyle.isDone(session.state) }
    private var isWaiting: Bool { session.state == .waiting }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
            metaLine
            if isWaiting {
                waitingRow
            } else if !isDone {
                statusRow
            }
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .opacity(isDone ? 0.6 : 1.0)
    }

    private var displayText: String {
        let t = (session.name?.isEmpty == false ? session.name : session.taskSummary) ?? ""
        if t.isEmpty { return L("(no task)") }
        return t.count > 80 ? String(t.prefix(80)) + "…" : t
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 8) {
            if selecting && isDone {
                Button(action: onToggleSelect) {
                    Image(systemName: selected ? "checkmark.square.fill" : "square")
                        .foregroundStyle(selected ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(selected ? "Deselect" : "Select")
            }
            Text(displayText)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            actions
            SessionStatePill(state: session.state)
        }
    }

    @ViewBuilder
    private var actions: some View {
        if !selecting {
            HStack(spacing: 4) {
                if !isDone {
                    cardButton("■ Stop", tint: DatawatchColors.error, action: onStop)
                    if isWaiting { cardButton("▶", tint: DatawatchColors.onSurface, action: onQuick) }
                } else {
                    cardButton("↻ Restart", tint: DatawatchColors.onSurface, action: onRestart)
                    cardButton("🗑", tint: DatawatchColors.error, action: onDelete)
                }
                // PWA sess-maximize-btn: open this session in Dashboard expand mode.
                cardButton("☷", tint: DatawatchColors.onSurface, action: onExpand)
                    .accessibilityLabel("Open in Dashboard")
            }
            Text("|").font(.system(size: 11)).foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.5))
        }
    }

    private func cardButton(_ title: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(L(title))
                .font(.system(size: 11))
                .foregroundStyle(tint)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(tint == DatawatchColors.onSurface ? DatawatchColors.border : tint, lineWidth: 1))
        }
        .buttonStyle(.borderless)
    }

    private var metaLine: some View {
        HStack(spacing: 6) {
            Text(session.id)
                .font(.system(size: 11, weight: .semibold, design: .monospaced))
                .foregroundStyle(DatawatchColors.onSurface)
                .padding(.horizontal, 7)
                .padding(.vertical, 2)
                .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(DatawatchColors.border, lineWidth: 1))
                .accessibilityLabel("Session ID \(session.id)")
            if let llm = session.llmRef ?? session.backend, !llm.isEmpty {
                accentBadge(llm)
            }
            if session.agentId != nil { accentBadge("⬡ worker") }
            if SessionCardView.isCouncil(session) { accentBadge("🎭").accessibilityLabel("Council session") }
            if let server = serverName, !server.isEmpty { accentBadge(server) }
            if showHost, let host = session.hostnamePrefix, !host.isEmpty { accentBadge(host) }
            if muted || session.muted {
                Image(systemName: "speaker.slash.fill").font(.system(size: 10)).foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .accessibilityLabel("Muted")
            }
            if let onWatchToggle {
                Button(action: onWatchToggle) {
                    Image(systemName: watched ? "bell.fill" : "bell.slash")
                        .font(.system(size: 11))
                        .foregroundStyle(watched ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted.opacity(0.5))
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(watched ? "Watching" : "Not watching")
                .accessibilityHint("Watch this session to include its alerts in your badge count")
            }
            Spacer(minLength: 4)
            if session.lastResponse != nil {
                Button(action: onResponse) {
                    Text("📄 Response")
                        .font(.system(size: 10))
                        .foregroundStyle(DatawatchColors.onSurface)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(DatawatchColors.border, lineWidth: 1))
                }
                .buttonStyle(.borderless)
            }
            if !isDone { ElapsedClock(since: session.createdAt.toEpochMilliseconds()) }
            Text(SessionCardView.ago(session.lastActivityAt.toEpochMilliseconds()))
                .font(.system(size: 11))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .font(.system(size: 11))
    }

    private func accentBadge(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(DatawatchColors.secondary)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(DatawatchColors.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.secondary, lineWidth: 1))
            .lineLimit(1)
    }

    /// PWA: prompt_context last 4 non-empty lines (≤100 chars each), then short summary.
    private var waitingRow: some View {
        let raw = session.promptContext ?? session.lastPrompt ?? ""
        let lines = raw.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        let shown: [String] = lines.suffix(4).map { $0.count > 100 ? String($0.prefix(100)) + "…" : $0 }
        return VStack(alignment: .leading, spacing: 4) {
            if shown.isEmpty {
                Text("Input needed")
            } else {
                ForEach(Array(shown.enumerated()), id: \.offset) { _, l in Text(l) }
            }
            if let lr = session.lastResponse, !lr.isEmpty {
                Text(lr.count > 180 ? String(lr.prefix(180)) + "…" : lr)
                    .italic()
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .font(.system(size: 11, design: .monospaced))
        .foregroundStyle(DatawatchColors.onSurface)
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.waiting.opacity(0.08))
        .overlay(alignment: .leading) { Rectangle().fill(DatawatchColors.waiting).frame(width: 2) }
        .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    /// PWA running row: "▶ What's it doing?" → inline summary, ▼ details, ↻ age (D14a).
    @ViewBuilder
    private var statusRow: some View {
        if let s = status, s.loading {
            Text("Summarizing…").font(.system(size: 10)).foregroundStyle(DatawatchColors.onSurfaceMuted)
        } else if let s = status, !s.text.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text(s.text).font(.system(size: 10)).foregroundStyle(DatawatchColors.onSurfaceMuted)
                    if !s.long.isEmpty {
                        Button(s.longExpanded ? "▲" : "▼", action: onToggleLong)
                            .font(.system(size: 11)).buttonStyle(.borderless)
                            .accessibilityLabel(s.longExpanded ? "Collapse details" : "Show details")
                    }
                    Button(action: onFetchStatus) {
                        Text("↻ " + SessionCardView.ago(Int64(s.generatedAt.timeIntervalSince1970 * 1000)))
                            .font(.system(size: 10)).foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Refresh status")
                }
                if s.longExpanded && !s.long.isEmpty {
                    Text(s.long)
                        .font(.system(size: 10))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .padding(8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
                        .overlay(alignment: .leading) { Rectangle().fill(DatawatchColors.secondary).frame(width: 2) }
                }
            }
        } else {
            Button(action: onFetchStatus) {
                Text("▶ What's it doing?")
                    .font(.system(size: 10))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(DatawatchColors.border, lineWidth: 1))
            }
            .buttonStyle(.borderless)
        }
    }

    /// D64: Council virtual session (Android v0.74.0 S5-7 rule).
    static func isCouncil(_ s: DwSession) -> Bool {
        s.backend == "council-virtual" || s.fullId.hasPrefix("council-")
    }

    static func ago(_ epochMs: Int64) -> String {
        let nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
        let seconds: Int64 = max(0, (nowMs - epochMs) / 1000)
        if seconds < 5 { return L("just now") }
        if seconds < 60 { return String(format: L("%llds ago"), seconds) }
        if seconds < 3600 { return String(format: L("%lldm ago"), seconds / 60) }
        if seconds < 86400 { return String(format: L("%lldh ago"), seconds / 3600) }
        return String(format: L("%lldd ago"), seconds / 86400)
    }
}

/// Live elapsed time since session start (PWA .session-elapsed, accent2 tabular).
struct ElapsedClock: View {
    let since: Int64
    var body: some View {
        TimelineView(.periodic(from: Date(), by: 1)) { ctx in
            Text(ElapsedClock.format(Int64(ctx.date.timeIntervalSince1970 * 1000) - since))
                .font(.system(size: 10))
                .monospacedDigit()
                .foregroundStyle(DatawatchColors.secondary.opacity(0.85))
        }
        .accessibilityLabel("Elapsed time")
    }

    static func format(_ ms: Int64) -> String {
        let s: Int64 = max(0, ms / 1000)
        let h: Int64 = s / 3600
        let m: Int64 = (s % 3600) / 60
        let sec: Int64 = s % 60
        if h > 0 { return String(format: "%lldh %02lldm", h, m) }
        if m > 0 { return String(format: "%lldm %02llds", m, sec) }
        return String(format: "%llds", sec)
    }
}
