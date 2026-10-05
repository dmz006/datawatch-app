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

    /// PWA style.css `.session-card.state-complete` .7 / `.state-killed` .5;
    /// failed cards are not dimmed. Action zones stay at full opacity.
    static func doneDim(_ s: SessionState) -> Double {
        switch s {
        case .completed: return 0.7
        case .killed: return 0.5
        default: return 1.0
        }
    }
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
        // PWA `.state { text-transform: uppercase }` — wire label, uppercased.
        Text(SessionStateStyle.key(state).uppercased())
            .font(.system(size: 11, weight: .semibold))
            .lineLimit(1)
            .fixedSize()
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
    /// D43a: PWA `🤖 Summary` card action, only when `session.summarizer.enabled`.
    var summarizerEnabled: Bool = false
    var summarizing: Bool = false
    var onSummarize: () -> Void = {}

    /// PWA `state.summaryLongExpanded[fullId]` — waiting-row long-summary panel.
    @State private var summaryLongExpanded = false

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
    }

    /// Opacity for the non-actionable parts of a done card (PWA keeps the
    /// action buttons and 📄 Response at full opacity).
    private var dim: Double { SessionStateStyle.doneDim(session.state) }

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
                .opacity(dim)
            actions
            SessionStatePill(state: session.state)
                .opacity(dim)
            // PWA .drag-handle (style.css:2176): always-visible ⋮⋮ at opacity .4.
            // Reorder itself is the List's native drag (`.onMove`), which iOS
            // starts with a press-and-hold on the row — the handle included.
            Text(verbatim: "⋮⋮")
                .font(.system(size: 14))
                .kerning(-1)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .opacity(0.4)
                .frame(minWidth: 24)
                .accessibilityLabel(L("Drag to reorder"))
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
                if summarizerEnabled { summaryButton }
                // PWA sess-maximize-btn: open this session in Dashboard expand mode.
                cardButton("☷", tint: DatawatchColors.onSurface, action: onExpand)
                    .accessibilityLabel("Open in Dashboard")
            }
            Text("|").font(.system(size: 11)).foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.5))
        }
    }

    /// PWA manualSummarize button: `🤖 Summary`, `⏳ Summarizing…` (disabled) while running.
    private var summaryButton: some View {
        Button(action: onSummarize) {
            Text(summarizing ? "⏳ Summarizing…" : "🤖 Summary")
                .font(.system(size: 10))
                .lineLimit(1)
                .fixedSize()
                .foregroundStyle(DatawatchColors.onSurface)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(DatawatchColors.border, lineWidth: 1))
        }
        .buttonStyle(.borderless)
        .disabled(summarizing)
        .accessibilityHint("Re-summarize with AI")
    }

    private func cardButton(_ title: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(L(title))
                .font(.system(size: 11))
                .lineLimit(1)
                .fixedSize()
                .foregroundStyle(tint)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(tint == DatawatchColors.onSurface ? DatawatchColors.border : tint, lineWidth: 1))
        }
        .buttonStyle(.borderless)
    }

    private var metaLine: some View {
        // Wraps like the PWA's flex-wrap meta row instead of truncating badges.
        FlowLayout(spacing: 6) {
            Group { metaBadges }.opacity(dim)
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
            Group {
                if !isDone { ElapsedClock(since: session.createdAt.toEpochMilliseconds()) }
                Text(SessionCardView.ago(session.lastActivityAt.toEpochMilliseconds()))
                    .font(.system(size: 11))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .opacity(dim)
        }
        .font(.system(size: 11))
    }

    /// Identity badges of the meta row (id pill · LLM · worker · council · server · host · lineage · muted).
    @ViewBuilder
    private var metaBadges: some View {
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
        lineageBadges
        if muted || session.muted {
            Image(systemName: "speaker.slash.fill").font(.system(size: 10)).foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityLabel("Muted")
        }
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

    /// Parent (BL347) + zombie (`claude_alive == false`) badges.
    @ViewBuilder
    private var lineageBadges: some View {
        if let parent = session.parentId, !parent.isEmpty { parentBadge(parent) }
        if session.claudeAlive?.boolValue == false { zombieBadge }
    }

    /// PWA `↳ child of [host]` lineage badge (BL347), muted at 0.7 opacity.
    private func parentBadge(_ parentId: String) -> some View {
        let host: String = parentId.split(separator: "-").first.map(String.init) ?? parentId
        return Text("↳ " + L("child of") + " [" + host + "]")
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(DatawatchColors.onSurfaceMuted.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.onSurfaceMuted, lineWidth: 1))
            .lineLimit(1)
            .opacity(0.7)
    }

    /// PWA `⚠ zombie` (claude_alive === false): amber outline badge.
    private var zombieBadge: some View {
        let amber = Color(red: 0.961, green: 0.620, blue: 0.043)
        return Text("⚠ zombie")
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(amber)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(amber.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(amber, lineWidth: 1))
            .lineLimit(1)
            .accessibilityLabel("Claude process not running — session may be a zombie")
    }

    /// PWA: prompt_context last 4 non-empty lines (≤100 chars each), then the short
    /// summary (`last_response`, italic ≤180) with ▼/▲ for `last_summary_long`,
    /// `AI <age>` and the ✕-closable long-summary panel.
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
                waitingSummaryLine(lr)
                if summaryLongExpanded, let long = longSummary {
                    longSummaryPanel(long)
                }
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

    private var longSummary: String? {
        guard let l = session.lastSummaryLong, !l.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return l
    }

    private func waitingSummaryLine(_ lr: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(lr.count > 180 ? String(lr.prefix(180)) + "…" : lr)
                .font(.system(size: 10))
                .italic()
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if longSummary != nil {
                Button(summaryLongExpanded ? "▲" : "▼") { summaryLongExpanded.toggle() }
                    .font(.system(size: 11))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .buttonStyle(.borderless)
                    .accessibilityLabel(summaryLongExpanded ? "Collapse details" : "Show details")
            }
            if let at = session.summaryGeneratedAt {
                Text("AI " + SessionCardView.ago(at.toEpochMilliseconds()))
                    .font(.system(size: 9))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.7))
            }
        }
    }

    /// PWA long-summary panel: bg3, accent2 left edge, ✕ top-right.
    private func longSummaryPanel(_ long: String) -> some View {
        Text(long)
            .font(.system(size: 10))
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
            .padding(.leading, 8)
            .padding(.vertical, 6)
            .padding(.trailing, 22)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
            .overlay(alignment: .leading) { Rectangle().fill(DatawatchColors.secondary).frame(width: 2) }
            .overlay(alignment: .topTrailing) { longSummaryClose }
            .padding(.top, 2)
    }

    private var longSummaryClose: some View {
        Button { summaryLongExpanded = false } label: {
            Text("✕")
                .font(.system(size: 12))
                .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.7))
        }
        .buttonStyle(.borderless)
        .padding(.top, 3)
        .padding(.trailing, 4)
        .accessibilityLabel("Collapse")
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

/// PWA `.session-card` 4 px left state border + `pulse-border` (waiting_input 2 s,
/// rate_limited 3 s); static under Reduce Motion (Android `pwaStateEdge`).
struct SessionStateEdge: View {
    let state: SessionState
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var lit = false

    private var pulsing: Bool { (state == .waiting || state == .rateLimited) && !reduceMotion }
    private var peak: Color {
        // #FCD34D (amber-300) for rate_limited, #93C5FD for waiting_input.
        state == .rateLimited
            ? Color(red: 0.988, green: 0.827, blue: 0.302)
            : Color(red: 0.576, green: 0.773, blue: 0.992)
    }
    private var halfPeriod: Double { state == .rateLimited ? 1.5 : 1.0 }

    var body: some View {
        ZStack {
            Rectangle().fill(SessionStateStyle.color(state))
            Rectangle().fill(peak).opacity(pulsing && lit ? 1.0 : 0.0)
        }
        .frame(width: 4)
        .onAppear { restart() }
        .onChange(of: pulsing) { _ in restart() }
        .accessibilityHidden(true)
    }

    private func restart() {
        if pulsing {
            lit = false
            withAnimation(.easeInOut(duration: halfPeriod).repeatForever(autoreverses: true)) { lit = true }
        } else {
            withAnimation(.none) { lit = false }
        }
    }
}

/// List-row background for a session card: PWA bg2 card (radius 12, 8 pt gap) with
/// the state edge; `indent` reproduces the tree view's 18 pt/level + 2 pt guide line.
struct SessionRowBackground: View {
    let state: SessionState
    var indent: CGFloat = 0

    var body: some View {
        HStack(spacing: 0) {
            if indent > 0 {
                Spacer().frame(width: indent - 8)
                Rectangle().fill(DatawatchColors.border).frame(width: 2)
                Spacer().frame(width: 6)
            }
            HStack(spacing: 0) {
                SessionStateEdge(state: state)
                DatawatchColors.surface
            }
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(DatawatchColors.background)
    }

    /// Row insets matching the background (card padding 10 + edge 4 + outer 8).
    static func insets(indent: CGFloat) -> EdgeInsets {
        EdgeInsets(top: 4, leading: 22 + indent, bottom: 4, trailing: 18)
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
