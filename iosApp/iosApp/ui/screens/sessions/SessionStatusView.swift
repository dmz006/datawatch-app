import SwiftUI
import DatawatchShared

/// Session Status sub-tab (parity B8) — PWA `renderSessionStatusBoard`: hook-health
/// dot, Current focus, Live Task Tree / Sprint (+ last-5-events drill-down under a
/// failed task), Tests, Git, guardrail verdicts with approve / run.
/// Polls every 5 s only while visible.
struct SessionStatusView: View {
    let profile: ServerProfile
    let session: DwSession

    @State private var snapshot: IosSessionStatusSnapshot? = nil
    @State private var pollTask: Task<Void, Never>? = nil

    private var board: SessionStatusBoardDto? { snapshot?.board }
    private var telemetry: SessionTelemetryDto? { snapshot?.telemetry }
    private var hooksDocURL: URL? { URL(string: profile.baseUrl + "/diagrams.html#docs/howto/claude-hooks.md") }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if snapshot == nil {
                    CardSkeleton().padding(.top, 40)
                } else if board == nil {
                    Text(snapshot?.error ?? "Status isn't available for this session.")
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 40)
                } else if let board {
                    HStack { Spacer(); hookHealth(board.hookHealth) }
                    card("Current focus") { focusBody(board) }
                    card(taskTree.isEmpty ? "Sprint / Automata" : "Live Task Tree") { sprintBody(board) }
                    card("Tests") { testsBody(board) }
                    card("Git") { gitBody(board) }
                    // Always shown (as Android) so "Run guardrail" is reachable with no verdicts.
                    card("Guardrail verdicts") {
                        GuardrailVerdictsBody(
                            profile: profile,
                            session: session,
                            verdicts: telemetry?.guardrailVerdicts ?? []
                        ) { Task { await loadOnce() } }
                    }
                    Text("Council / Skills / Tracker / closed-task summaries appear once hook payloads include those fields.")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(8)
                }
            }
            .padding(12)
        }
        .background(DatawatchColors.background)
        .refreshable { await loadOnce() }
        .onAppear { startPolling() }
        .onDisappear { pollTask?.cancel(); pollTask = nil }
    }

    // MARK: Cards

    private func card<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title.uppercased())
                .font(DatawatchFonts.badge)
                .tracking(0.5)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.border, lineWidth: 1))
    }

    private func muted(_ text: String) -> some View {
        Text(L(text))
            .font(DatawatchFonts.labelSmall.italic())
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
    }

    @ViewBuilder
    private func hookHealth(_ health: String) -> some View {
        let (color, label): (Color, String) = {
            switch health {
            case "alive": return (DatawatchColors.success, "hooks alive")
            case "stale": return (DatawatchColors.warning, "hooks stale")
            default: return (DatawatchColors.onSurfaceMuted, "no hooks installed")
            }
        }()
        HStack(spacing: 4) {
            Button {
                Task { await loadOnce() }
            } label: {
                Text("●").foregroundStyle(color)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Re-poll status")
            Text(label).foregroundStyle(DatawatchColors.onSurfaceMuted)
            if health != "alive", let url = hooksDocURL {
                Link("Set up", destination: url)
                    .foregroundStyle(DatawatchColors.secondary)
            }
        }
        .font(DatawatchFonts.labelSmall)
    }

    @ViewBuilder
    private func focusBody(_ board: SessionStatusBoardDto) -> some View {
        if let last = board.lastEvent {
            let focus = (board.currentFocus?.isEmpty == false ? board.currentFocus : nil)
                ?? (telemetry?.currentTask.isEmpty == false ? telemetry?.currentTask : nil)
                ?? "—"
            VStack(alignment: .leading, spacing: 4) {
                if focus.contains("\n") {
                    Text(focus)
                        .font(DatawatchFonts.terminalSmall)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .padding(8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(DatawatchColors.background, in: RoundedRectangle(cornerRadius: 4))
                        .textSelection(.enabled)
                } else {
                    (Text("Current focus: ").bold() + Text(focus))
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.onSurface)
                }
                Text(lastEventLine(last))
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                if let idle = board.idleSince?.int64Value {
                    Text("idle since \(clock(idle))")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.warning)
                }
            }
        } else {
            HStack(spacing: 4) {
                muted("No hook events received yet.")
                if let url = hooksDocURL {
                    Link("Set up", destination: url).font(DatawatchFonts.labelSmall)
                }
            }
        }
    }

    private var taskTree: [TelemetryTaskDto] { telemetry?.tasks ?? [] }
    private var failedBuf: [TelemetryHookEventDto] { telemetry?.failedTaskBuf ?? [] }

    @ViewBuilder
    private func sprintBody(_ board: SessionStatusBoardDto) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            if let crumb = breadcrumb(board), !crumb.isEmpty {
                Text(crumb)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.secondary)
            }
            if let progress = telemetry?.progress, progress > 0 {
                ProgressView(value: Double(progress), total: progress > 1 ? 100 : 1)
                    .tint(DatawatchColors.primary)
            }
            if !taskTree.isEmpty {
                ForEach(taskTree, id: \.id) { task in
                    let (glyph, color) = PrdStatusStyle.taskGlyph(task.status)
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text(glyph).foregroundStyle(color).frame(width: 16)
                        Text(task.title.isEmpty ? task.id : task.title)
                            .foregroundStyle(DatawatchColors.onSurface)
                            .lineLimit(2)
                        Spacer(minLength: 4)
                        if task.durationMs > 0 {
                            Text(duration(task.durationMs))
                                .font(DatawatchFonts.terminalSmall)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                    }
                    .font(DatawatchFonts.labelSmall)
                    // PWA renderFailedDrilldown (T10): last 5 hook events before failure.
                    if task.status == "failed" && !failedBuf.isEmpty {
                        FailedDrilldownView(events: Array(failedBuf.suffix(5)))
                    }
                }
            } else if let sprint = board.sprint {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(sprintFields(sprint), id: \.0) { pair in
                        Text("\(pair.0): \(pair.1)")
                            .font(DatawatchFonts.terminalSmall)
                            .foregroundStyle(DatawatchColors.onSurface)
                    }
                }
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(DatawatchColors.background, in: RoundedRectangle(cornerRadius: 4))
            } else {
                muted("no sprint data — hook payload sprint=… expected")
            }
        }
    }

    @ViewBuilder
    private func testsBody(_ board: SessionStatusBoardDto) -> some View {
        let t = telemetry?.tests
        let pass = (t?.total ?? 0) > 0 ? Int(t!.pass) : Int(board.tests?.passing ?? 0)
        let fail = (t?.total ?? 0) > 0 ? Int(t!.fail) : Int(board.tests?.failing ?? 0)
        let skip = (t?.total ?? 0) > 0 ? Int(t!.skip) : 0
        if (t?.total ?? 0) > 0 || board.tests != nil {
            HStack(spacing: 4) {
                Text("\(pass) pass").bold().foregroundStyle(fail > 0 ? DatawatchColors.error : DatawatchColors.success)
                Text("·").foregroundStyle(DatawatchColors.onSurfaceMuted)
                Text("\(fail) fail").foregroundStyle(DatawatchColors.error)
                if skip > 0 {
                    Text("· \(skip) skip").foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .font(DatawatchFonts.bodyMedium)
        } else {
            muted("no test signal yet — hook payload tests=… expected")
        }
    }

    @ViewBuilder
    private func gitBody(_ board: SessionStatusBoardDto) -> some View {
        if let g = board.git {
            HStack(spacing: 4) {
                Text("branch:").foregroundStyle(DatawatchColors.onSurface)
                Text(g.branch.isEmpty ? "—" : g.branch)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                if g.uncommitted > 0 {
                    Text("· dirty").foregroundStyle(DatawatchColors.warning)
                }
                if g.ahead > 0 {
                    Text("· ↑\(g.ahead)").foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .font(DatawatchFonts.bodyMedium)
        } else {
            muted("no git state — hook payload git=… expected")
        }
    }

    // MARK: Helpers

    private func breadcrumb(_ board: SessionStatusBoardDto) -> String? {
        if let s = telemetry?.sprint {
            return [s.automata, s.name, s.task].filter { !$0.isEmpty }.joined(separator: " › ")
        }
        if let s = board.sprint {
            return [s.automata, s.name.isEmpty ? s.title : s.name].filter { !$0.isEmpty }.joined(separator: " › ")
        }
        return nil
    }

    private func sprintFields(_ s: SprintStatusDto) -> [(String, String)] {
        [("name", s.name), ("title", s.title), ("automata", s.automata), ("sprint_id", s.sprintId),
         ("task_id", s.taskId), ("status", s.status), ("progress", s.progress)].filter { !$0.1.isEmpty }
    }

    private func lastEventLine(_ e: LastEventDto) -> String {
        var parts = ["last \(e.event ?? "event")"]
        if let tool = e.tool, !tool.isEmpty { parts.append(tool) }
        if let ts = e.ts?.int64Value { parts.append(clock(ts)) }
        return parts.joined(separator: " · ")
    }

    private func clock(_ epochMs: Int64) -> String {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f.string(from: Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000))
    }

    private func duration(_ ms: Int64) -> String {
        let s = ms / 1000
        return s >= 60 ? "\(s / 60)m \(s % 60)s" : "\(s)s"
    }

    // MARK: Polling (5 s while visible)

    private func startPolling() {
        pollTask?.cancel()
        pollTask = Task {
            while !Task.isCancelled {
                await loadOnce()
                try? await Task.sleep(for: .seconds(5))
            }
        }
    }

    private func loadOnce() async {
        let result: IosSessionStatusSnapshot = await withCheckedContinuation { cont in
            IosSessionStatus.shared.load(profile: profile, session: session) { cont.resume(returning: $0) }
        }
        await MainActor.run { snapshot = result }
    }
}
