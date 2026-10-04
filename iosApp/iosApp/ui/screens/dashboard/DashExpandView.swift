import SwiftUI
import DatawatchShared

/// Cross-tab "Open in Dashboard" (PWA `window.openDashExpand(sid)`): any
/// screen can call `DashExpandNav.shared.open(sessionId)`; RootView switches
/// to the Dashboard tab and DashboardView presents the expand panel.
@MainActor
final class DashExpandNav: ObservableObject {
    static let shared = DashExpandNav()
    @Published private(set) var pendingSessionId: String? = nil
    private init() {}

    func open(_ sessionId: String) {
        pendingSessionId = sessionId
        NotificationCenter.default.post(name: .dwNavigateToDashboard, object: nil)
    }

    func consume() -> String? {
        defer { pendingSessionId = nil }
        return pendingSessionId
    }
}

extension Notification.Name {
    static let dwNavigateToDashboard = Notification.Name("dw.navigateToDashboard")
}

/// PWA `#dashExpand` overlay (BL303 expand mode): header (Back · title ·
/// Open), Task Tree sidebar, live Status board, Verdicts rail. Regular width
/// shows the three panes side by side like the PWA; compact width switches
/// between them with a segmented control.
struct DashExpandView: View {
    let profile: ServerProfile
    let sessionId: String
    let session: DwSession?
    let onOpenSession: (DwSession) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(\.horizontalSizeClass) private var sizeClass
    @State private var snapshot: IosSessionStatusSnapshot? = nil
    @State private var pane: Int = 0
    @State private var pollTask: Task<Void, Never>? = nil

    private var title: String {
        guard let s = session else { return String(sessionId.prefix(40)) }
        if let n = s.name, !n.isEmpty { return String(n.prefix(40)) }
        return String((s.taskSummary ?? s.id).prefix(40))
    }

    var body: some View {
        NavigationStack {
            content
                .background(DatawatchColors.background)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { toolbar }
        }
        .onAppear(perform: startPolling)
        .onDisappear { pollTask?.cancel() }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button { dismiss() } label: { Label("Back", systemImage: "chevron.left") }
        }
        ToolbarItem(placement: .principal) {
            Text(title)
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(1)
        }
        ToolbarItem(placement: .confirmationAction) {
            if let s = session {
                Button { onOpenSession(s) } label: { Label("Open", systemImage: "bubble.left") }
                    .tint(DatawatchColors.primary)
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        if session == nil {
            Text("No status data yet — hook events will populate this view.")
                .font(.footnote)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .padding()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        } else if sizeClass == .regular {
            HStack(spacing: 0) {
                DashTaskTreePane(telemetry: snapshot?.telemetry)
                    .frame(width: 260)
                Divider().overlay(DatawatchColors.border)
                mainPane
                Divider().overlay(DatawatchColors.border)
                DashVerdictsPane(telemetry: snapshot?.telemetry)
                    .frame(width: 240)
            }
        } else {
            VStack(spacing: 0) {
                Picker("Pane", selection: $pane) {
                    Text("Status").tag(0)
                    Text("Task Tree").tag(1)
                    Text("Verdicts").tag(2)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal)
                .padding(.vertical, 8)
                switch pane {
                case 1: DashTaskTreePane(telemetry: snapshot?.telemetry)
                case 2: DashVerdictsPane(telemetry: snapshot?.telemetry)
                default: mainPane
                }
            }
        }
    }

    @ViewBuilder
    private var mainPane: some View {
        if let s = session {
            SessionStatusView(profile: profile, session: s)
        }
    }

    /// Telemetry for the tree + verdicts rail (PWA re-renders on hook_update; 5 s poll here).
    private func startPolling() {
        guard let s = session else { return }
        pollTask?.cancel()
        pollTask = Task { @MainActor in
            while !Task.isCancelled {
                IosSessionStatus.shared.load(profile: profile, session: s) { snap in
                    Task { @MainActor in snapshot = snap }
                }
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
    }
}

/// PWA `renderLiveTaskTree` sidebar.
private struct DashTaskTreePane: View {
    let telemetry: SessionTelemetryDto?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                Text("Task Tree")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(DatawatchColors.onSurface)
                if let t = telemetry, !t.currentTask.isEmpty {
                    Text("▸ \(t.currentTask)")
                        .font(.caption2)
                        .foregroundStyle(DatawatchColors.primary)
                }
                if let tasks = telemetry?.tasks, !tasks.isEmpty {
                    ForEach(Array(tasks.enumerated()), id: \.offset) { _, task in
                        DashTaskRow(task: task)
                    }
                } else {
                    Text("No telemetry yet")
                        .font(.caption2)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(DatawatchColors.surface)
    }
}

private struct DashTaskRow: View {
    let task: TelemetryTaskDto

    private var symbol: (String, Color) {
        switch task.status {
        case "completed", "complete": return ("checkmark.circle.fill", DatawatchColors.success)
        case "in_progress", "running": return ("play.circle.fill", DatawatchColors.primary)
        case "failed": return ("xmark.circle.fill", DatawatchColors.error)
        default: return ("circle", DatawatchColors.onSurfaceMuted)
        }
    }

    var body: some View {
        let s = symbol
        HStack(alignment: .top, spacing: 6) {
            Image(systemName: s.0)
                .font(.caption2)
                .foregroundStyle(s.1)
            Text(task.title.isEmpty ? task.id : task.title)
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
            if task.durationMs > 0 {
                Text(String(format: "%.1fs", Double(task.durationMs) / 1000.0))
                    .font(.system(size: 9).monospaced())
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .accessibilityElement(children: .combine)
    }
}

/// PWA `renderSessionGuardrailVerdicts` rail.
private struct DashVerdictsPane: View {
    let telemetry: SessionTelemetryDto?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text("Verdicts")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(DatawatchColors.onSurface)
                if let verdicts = telemetry?.guardrailVerdicts, !verdicts.isEmpty {
                    ForEach(Array(verdicts.enumerated()), id: \.offset) { _, v in
                        DashVerdictRow(verdict: v)
                    }
                } else {
                    Text("No verdicts yet")
                        .font(.caption2)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(DatawatchColors.surface)
    }
}

private struct DashVerdictRow: View {
    let verdict: GuardrailVerdictDto

    private var color: Color {
        switch verdict.outcome {
        case "block": return DatawatchColors.error
        case "warn": return DatawatchColors.warning
        default: return DatawatchColors.success
        }
    }

    private var symbol: String {
        switch verdict.outcome {
        case "block": return "nosign"
        case "warn": return "exclamationmark.triangle"
        default: return "checkmark"
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Label(verdict.guardrail, systemImage: symbol)
                .font(.caption2.weight(.bold))
                .foregroundStyle(color)
            if !verdict.summary.isEmpty {
                Text(verdict.summary)
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .accessibilityElement(children: .combine)
    }
}
