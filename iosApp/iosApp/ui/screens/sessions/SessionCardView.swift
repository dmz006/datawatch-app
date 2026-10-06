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

/// Session card (parity D13a / D14a / D16a; PWA renderSessionCard app.js + `.session-card`).
/// Line 1: name/task (80 chars, 13/600, one line) · `|` · state pill · 👁 watch · 🔔 mute · ⋮⋮.
/// Line 2: right-aligned small actions (■ Stop / ▶ / ↻ Restart / 🗑 / 🤖 Summary / ☷) — the
/// PWA `.card-actions` group wraps under the header at phone width.
/// Line 3: id pill · badges … 📄 Response · elapsed · age.
/// Then the amber `.card-waiting-row` (last 4 prompt lines) or the running status row.
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
    /// D62a: locally muted (swipe-to-mute or the 🔔 toggle) or server-reported muted.
    var muted: Bool = false
    var onMuteToggle: (() -> Void)? = nil
    /// D43a: PWA `🤖 Summary` card action, only when `session.summarizer.enabled`.
    var summarizerEnabled: Bool = false
    var summarizing: Bool = false
    var onSummarize: () -> Void = {}

    /// PWA `state.summaryLongExpanded[fullId]` — waiting-row long-summary panel.
    @State private var summaryLongExpanded = false

    private var isDone: Bool { SessionStateStyle.isDone(session.state) }
    private var isWaiting: Bool { session.state == .waiting }
    private var isMuted: Bool { muted || session.muted }

    /// PWA `--warning` amber (#f59e0b): `.card-waiting-row`, council + zombie badges.
    static let amber = Color(red: 0.961, green: 0.620, blue: 0.043)

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
            if !selecting { actions }
            metaLine
            if isWaiting {
                waitingRow
            } else if !isDone {
                waitingBox { statusRow }
            }
        }
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
                .lineLimit(1)
                .truncationMode(.tail)
                .frame(maxWidth: .infinity, alignment: .leading)
                .opacity(dim)
            // PWA `|` divider between the (wrapped) action group and the state pill.
            Text(verbatim: "|")
                .font(.system(size: 13))
                .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.5))
                .accessibilityHidden(true)
            SessionStatePill(state: session.state)
                .opacity(dim)
            if !selecting {
                if let onWatchToggle { watchToggle(onWatchToggle) }
                if let onMuteToggle { muteToggle(onMuteToggle) }
            }
            // PWA .drag-handle (style.css:2176): always-visible ⋮⋮ at opacity .4.
            // Reorder itself is the List's native drag (`.onMove`), which iOS
            // starts with a press-and-hold on the row — the handle included.
            Text(verbatim: "⋮⋮")
                .font(.system(size: 14))
                .kerning(-1)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .opacity(0.4)
                .frame(minWidth: 20)
                .accessibilityLabel(L("Drag to reorder"))
        }
    }

    /// PWA 👁 watch toggle: accent2 when watching, .4 opacity when not.
    private func watchToggle(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(verbatim: "👁")
                .font(.system(size: 14))
                .foregroundStyle(watched ? DatawatchColors.secondary : DatawatchColors.onSurface)
                .opacity(watched ? 1 : 0.4)
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(L(watched ? "Watching" : "Not watching"))
        .accessibilityHint(L("Watch this session to include its alerts in your badge count"))
    }

    /// PWA 🔔/🔕 mute toggle: full opacity when muted, .4 when not.
    private func muteToggle(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(verbatim: isMuted ? "🔕" : "🔔")
                .font(.system(size: 14))
                .opacity(isMuted ? 1 : 0.4)
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(L(isMuted ? "Unmute notifications" : "Mute notifications for this session"))
    }

    /// PWA `.card-actions`: wraps under the header at phone width, right-aligned.
    private var actions: some View {
        HStack(spacing: 4) {
            Spacer(minLength: 0)
            if !isDone {
                cardButton("■ Stop", tint: DatawatchColors.error, action: onStop)
                if isWaiting {
                    cardButton("▶", tint: DatawatchColors.onSurface, action: onQuick)
                        .accessibilityLabel(L("Quick commands"))
                }
            } else {
                cardButton("↻ Restart", tint: DatawatchColors.onSurface, action: onRestart)
                cardButton("🗑", tint: DatawatchColors.error, action: onDelete)
                    .accessibilityLabel(L("Delete"))
            }
            if summarizerEnabled { summaryButton }
            // PWA sess-maximize-btn: open this session in Dashboard expand mode.
            cardButton("☷", tint: DatawatchColors.onSurface, action: onExpand)
                .accessibilityLabel(L("Open in Dashboard"))
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
                .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 4))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(DatawatchColors.border, lineWidth: 1))
        }
        .buttonStyle(.borderless)
        .disabled(summarizing)
        .accessibilityHint("Re-summarize with AI")
    }

    /// PWA card action button: 11 px, 1 px --border (state colour for Stop / 🗑), bg2, r4, 3×8.
    private func cardButton(_ title: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(L(title))
                .font(.system(size: 11))
                .lineLimit(1)
                .fixedSize()
                .foregroundStyle(tint)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 4))
                .overlay(RoundedRectangle(cornerRadius: 4).stroke(tint == DatawatchColors.onSurface ? DatawatchColors.border : tint, lineWidth: 1))
        }
        .buttonStyle(.borderless)
    }

    /// PWA meta row (flex-wrap): id · badges, then `margin-left:auto` group
    /// 📄 Response · elapsed · age pinned to the trailing edge.
    private var metaLine: some View {
        FlowLayout(spacing: 6, trailingLast: true) {
            Group { metaBadges }.opacity(dim)
            HStack(spacing: 8) {
                if session.lastResponse?.isEmpty == false {
                    Button(action: onResponse) {
                        Text(L("📄 Response"))
                            .font(.system(size: 10))
                            .lineLimit(1)
                            .foregroundStyle(DatawatchColors.onSurface)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 4))
                            .overlay(RoundedRectangle(cornerRadius: 4).stroke(DatawatchColors.border, lineWidth: 1))
                    }
                    .buttonStyle(.borderless)
                }
                Group {
                    if !isDone { ElapsedClock(since: session.createdAt.toEpochMilliseconds()) }
                    Text(SessionCardView.ago(session.lastActivityAt.toEpochMilliseconds()))
                        .font(.system(size: 10))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                .opacity(dim)
            }
            .fixedSize()
        }
        .font(.system(size: 11))
    }

    /// Identity badges of the meta row (id pill · council · LLM · server · worker · host · lineage).
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
        // PWA: council sessions get the amber `🎭 Council` badge instead of the LLM badge.
        if SessionCardView.isCouncil(session) {
            outlineBadge("🎭 " + L("Council"), SessionCardView.amber)
                .accessibilityLabel(L("Council session"))
        } else if let llm = session.llmRef ?? session.backend, !llm.isEmpty {
            outlineBadge(llm, DatawatchColors.secondary)
        }
        if let server = serverName, !server.isEmpty { outlineBadge(server, DatawatchColors.secondary) }
        if session.agentId != nil { outlineBadge("⬡ worker", DatawatchColors.secondary) }
        if showHost, let host = session.hostnamePrefix, !host.isEmpty { outlineBadge(host, DatawatchColors.secondary) }
        lineageBadges
    }

    /// PWA meta-row badge: 10/600, 1 px colour border, colour @12 % fill, r8, 2×7.
    private func outlineBadge(_ text: String, _ color: Color) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(color, lineWidth: 1))
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
        let amber = SessionCardView.amber
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

    /// PWA `.card-waiting-row`: amber-tinted box (warning @8 % fill, @15 % border).
    private func waitingBox<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 4) { content() }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(SessionCardView.amber.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(SessionCardView.amber.opacity(0.15), lineWidth: 1))
    }

    /// PWA: prompt_context last 4 non-empty lines (≤100 chars each, one line each,
    /// `.card-waiting-label` mono amber), then the short summary (`last_response`,
    /// italic ≤180) with ▼/▲ for `last_summary_long`, `AI <age>` and the ✕ panel.
    private var waitingRow: some View {
        let shown: [String] = SessionCardView.promptLines(session.promptContext ?? session.lastPrompt)
        return waitingBox {
            Group {
                if shown.isEmpty {
                    Text(L("Input needed"))
                } else {
                    ForEach(Array(shown.enumerated()), id: \.offset) { _, l in Text(verbatim: l) }
                }
            }
            .font(.system(size: 11, design: .monospaced))
            .foregroundStyle(SessionCardView.amber)
            .lineLimit(1)
            .truncationMode(.tail)
            if let lr = SessionCardView.shortSummary(session.lastResponse) {
                waitingSummaryLine(lr)
                if summaryLongExpanded, let long = longSummary {
                    longSummaryPanel(long)
                }
            }
        }
    }

    /// PWA ctxLines: trimmed non-empty lines, last 4, each ≤100 chars + "…".
    static func promptLines(_ raw: String?) -> [String] {
        let lines = (raw ?? "").split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        return lines.suffix(4).map { $0.count > 100 ? String($0.prefix(100)) + "…" : $0 }
    }

    /// PWA renders `last_response` in a <span>, so HTML collapses newlines and
    /// whitespace runs into single spaces; ≤180 chars + "…". Nil when blank.
    static func shortSummary(_ raw: String?) -> String? {
        let collapsed = (raw ?? "")
            .split(whereSeparator: { $0.isWhitespace })
            .joined(separator: " ")
        guard !collapsed.isEmpty else { return nil }
        return collapsed.count > 180 ? String(collapsed.prefix(180)) + "…" : collapsed
    }

    private var longSummary: String? {
        guard let l = session.lastSummaryLong, !l.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return l
    }

    private func waitingSummaryLine(_ lr: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 4) {
            Text(verbatim: lr)
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
                    .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 4))
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

    /// Row insets matching the background: PWA `.session-card { padding: 12px 14px }`
    /// plus the 4 pt edge and the 8 / 4 pt outer gap.
    static func insets(indent: CGFloat) -> EdgeInsets {
        EdgeInsets(top: 16, leading: 26 + indent, bottom: 16, trailing: 22)
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
