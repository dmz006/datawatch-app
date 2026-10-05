import SwiftUI
import DatawatchShared

/// PWA `showCrossHostView`: envelopes from the local observer + every peer,
/// grouped by peer, with 🔗 cross-host caller attribution.
struct CrossHostSheet: View {
    let profile: ServerProfile
    @Environment(\.dismiss) private var dismiss
    @State private var peers: [IosCrossHostPeer]?
    @State private var error: String?

    var body: some View {
        NavigationStack {
            content
                .background(DatawatchColors.background)
                .navigationTitle("Cross-host envelope view")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }
                    }
                }
                .task { load() }
        }
    }

    @ViewBuilder
    private var content: some View {
        if let error {
            Text(L("load failed") + ": " + error)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.error)
                .padding()
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        } else if let peers {
            if peers.isEmpty {
                Text("No envelopes anywhere — local observer empty and no peers pushed yet.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .padding()
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            } else {
                peerList(peers)
            }
        } else {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func peerList(_ list: [IosCrossHostPeer]) -> some View {
        List {
            ForEach(list, id: \.peer) { p in
                Section {
                    if p.envelopes.isEmpty {
                        Text("no envelopes")
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .listRowBackground(DatawatchColors.surface)
                    }
                    ForEach(Array(p.envelopes.enumerated()), id: \.offset) { pair in
                        CrossHostEnvelopeRow(env: pair.element)
                            .listRowBackground(DatawatchColors.surface)
                    }
                } header: {
                    Text(verbatim: p.peer + "  (\(p.envelopes.count) " + L("envelopes") + ")")
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    private func load() {
        IosCrossHost.shared.load(profile: profile, onSuccess: { list in
            DispatchQueue.main.async { peers = list }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
    }
}

private struct CrossHostEnvelopeRow: View {
    let env: IosCrossHostEnvelope

    private var title: String {
        [env.id, env.kind, env.label].filter { !$0.isEmpty }.joined(separator: "  ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: title)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
            if !env.listen.isEmpty {
                Text(verbatim: "listen: " + env.listen)
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            if !env.outbound.isEmpty {
                Text(verbatim: "outbound: " + env.outbound)
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            ForEach(Array(env.callers.enumerated()), id: \.offset) { pair in
                callerLine(pair.element)
            }
        }
    }

    private func callerLine(_ c: IosCrossHostCaller) -> some View {
        HStack(spacing: 4) {
            if c.cross {
                Text("🔗 cross")
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.primary)
                    .padding(.horizontal, 4)
                    .background(DatawatchColors.waiting.opacity(0.25), in: RoundedRectangle(cornerRadius: 4))
            }
            Text(verbatim: c.caller)
                .font(.caption2.monospaced())
                .foregroundStyle(c.cross ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
            Text(verbatim: c.detail)
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .padding(.leading, 12)
    }
}
