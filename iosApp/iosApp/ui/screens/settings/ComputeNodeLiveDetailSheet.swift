import SwiftUI
import DatawatchShared

/// PWA `computeShowDetail` (Settings › Compute › node row 📡): the node's
/// `/api/compute/nodes/{name}/detail` JSON, re-polled every 1 s while open.
/// On failure the title reads "📡 name — detail unavailable" with the server's
/// reason and the monitoring_endpoint fix hint; polling stops (PWA).
struct ComputeNodeLiveDetailSheet: View {
    let profile: ServerProfile
    let name: String

    @Environment(\.dismiss) private var dismiss
    @State private var json: String?
    @State private var error: String?
    @State private var sub: IosComputeLiveDetailSubscription?

    var body: some View {
        NavigationStack {
            ScrollView {
                content
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
            }
            .background(DatawatchColors.background)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .onAppear { start() }
        .onDisappear { stop() }
    }

    private var title: String {
        let suffix: String = error == nil ? L("live detail") : L("detail unavailable")
        return "📡 " + name + " — " + suffix
    }

    @ViewBuilder
    private var content: some View {
        if let error {
            ComputeNodeDetailErrorView(reason: error)
        } else if let json {
            ComputeNodeDetailJsonView(json: json)
        } else {
            ProgressView()
        }
    }

    private func start() {
        stop()
        sub = IosComputeLiveDetail.shared.watch(profile: profile, name: name, onJson: { text in
            DispatchQueue.main.async { json = text }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
    }

    private func stop() {
        sub?.cancel()
        sub = nil
    }
}

private struct ComputeNodeDetailJsonView: View {
    let json: String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(verbatim: json)
                .font(.system(size: 10, design: .monospaced))
                .foregroundStyle(DatawatchColors.onSurface)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(8)
                .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 4))
            Text("Live polling every 1s while modal is open. Close to stop.")
                .font(.system(size: 10))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }
}

private struct ComputeNodeDetailErrorView: View {
    let reason: String

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(verbatim: reason)
                .font(.system(size: 13))
                .foregroundStyle(DatawatchColors.onSurface)
            VStack(alignment: .leading, spacing: 2) {
                Text("Fix:")
                    .font(.system(size: 11, weight: .bold))
                Text("Edit this Compute Node and set monitoring_endpoint to the URL of a datawatch-stats --listen sidecar running on the node. Without it, the daemon has nothing to poll for live detail.")
                    .font(.system(size: 11))
            }
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }
}
