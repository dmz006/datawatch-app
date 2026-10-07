import SwiftUI
import DatawatchShared

/// PWA `_renderStoryReadOnlyExtras` (Android StoryRow read-only extras):
/// shown when the story is not editable and has tasks — progress row +
/// bar ("Progress: d/t tasks · p% · ⟳ n active") and the worker session
/// link (first task with a session id).
struct PrdStoryReadOnlyExtras: View {
    let story: PrdStoryDto
    let profileId: String

    private var total: Int { story.tasks.count }
    private var done: Int { story.tasks.filter { PrdStatusStyle.isDone($0.status) }.count }
    private var active: Int {
        story.tasks.filter { $0.status == "verifying" || $0.status == "running_tests" }.count
    }
    private var pct: Int { total > 0 ? Int((100.0 * Double(done) / Double(total)).rounded()) : 0 }
    private var sessionId: String? {
        story.tasks.compactMap { $0.sessionId }.first { !$0.isEmpty }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if total > 0 {
                progressLabel
                ProgressView(value: Double(pct), total: 100.0)
                    .tint(pct == 100 ? DatawatchColors.success : DatawatchColors.primary)
            }
            if let sid = sessionId {
                sessionLink(sid)
            }
        }
    }

    private var progressLabel: some View {
        let base: String = L("Progress:") + " \(done)/\(total) " + L("tasks") + " · \(pct)%"
        return HStack(spacing: 0) {
            Text(verbatim: base)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if active > 0 {
                Text(verbatim: " · ⟳ \(active) " + L("active"))
                    .fontWeight(.semibold)
                    .foregroundStyle(DatawatchColors.primary)
            }
        }
        .font(DatawatchFonts.labelSmall)
    }

    /// PWA story session row: "→ Session <id>" + "▨ Status" (gotoSessionStatus).
    private func sessionLink(_ sid: String) -> some View {
        HStack(spacing: 10) {
            Button {
                open(sid, status: false)
            } label: {
                HStack(spacing: 4) {
                    Text(verbatim: "→ " + L("Session"))
                    Text(verbatim: sid).font(DatawatchFonts.terminalSmall)
                }
            }
            .accessibilityLabel("Open the worker session that ran this story")
            Button {
                open(sid, status: true)
            } label: {
                Text(verbatim: "▨ " + L("Status"))
            }
            .accessibilityLabel("Open session Status tab")
        }
        .font(DatawatchFonts.labelSmall)
        .foregroundStyle(DatawatchColors.primary)
        .buttonStyle(.borderless)
    }

    private func open(_ sid: String, status: Bool) {
        var info: [String: String] = ["id": sid, "profileId": profileId]
        if status { info["tab"] = "status" }
        NotificationCenter.default.post(name: .deepLinkSession, object: nil, userInfo: info)
    }
}
