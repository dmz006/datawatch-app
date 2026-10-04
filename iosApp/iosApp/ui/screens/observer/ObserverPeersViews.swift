import SwiftUI
import DatawatchShared

// Observer peers (parity B22 + B25 bottom card). Peer row action per D55a:
// 📊 snapshot sheet + × remove (confirm; token rotates, peer re-registers).

/// Identifiable wrapper so a peer name can drive `.sheet(item:)`.
struct ObsPeerRef: Identifiable {
    let name: String
    var id: String { name }
}

/// Simple wrapping layout for chips / tags (iOS 16 `Layout`).
struct ObsFlowLayout: Layout {
    var spacing: CGFloat = 4

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth: CGFloat = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var widest: CGFloat = 0
        for sv in subviews {
            let size = sv.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > maxWidth {
                y += rowHeight + spacing
                x = 0
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
            widest = max(widest, x - spacing)
        }
        return CGSize(width: min(widest, maxWidth), height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x: CGFloat = bounds.minX
        var y: CGFloat = bounds.minY
        var rowHeight: CGFloat = 0
        for sv in subviews {
            let size = sv.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                y += rowHeight + spacing
                x = bounds.minX
                rowHeight = 0
            }
            sv.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

private struct ObsChipView: View {
    let chip: IosObsChip

    var body: some View {
        Text(chip.text)
            .font(.caption2.monospacedDigit())
            .foregroundStyle(chip.gpu ? DatawatchColors.secondary : DatawatchColors.onSurface)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .background(
                chip.gpu ? DatawatchColors.secondary.opacity(0.12) : DatawatchColors.surface2,
                in: RoundedRectangle(cornerRadius: 4)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 4)
                    .stroke(chip.gpu ? DatawatchColors.secondary.opacity(0.3) : Color.clear, lineWidth: 1)
            )
    }
}

// ── Peer Resources (live) ─────────────────────────────────────────────────

struct ObserverPeerResourcesBlock: View {
    let systems: IosSystemsSnapshot?

    var body: some View {
        ObsBlock {
            ObsSubHeader(title: "Peer Resources", live: true)
            if let s = systems {
                if !s.peersError.isEmpty {
                    ObsMuted(text: L("unavailable"))
                } else if s.peers.isEmpty {
                    ObsMuted(text: L("no peers registered"))
                } else {
                    ForEach(Array(s.peers.enumerated()), id: \.offset) { _, p in
                        resourceRow(p)
                    }
                }
            } else {
                ObsMuted(text: L("Loading…"))
            }
        }
    }

    private func resourceRow(_ p: IosPeerRow) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Divider().overlay(DatawatchColors.border)
            HStack(spacing: 5) {
                ObsDot(tone: p.dotTone)
                ObsTag(text: p.shapeTag)
                Text(p.name)
                    .font(DatawatchFonts.labelSmall.weight(.semibold))
                    .foregroundStyle(DatawatchColors.onSurface)
                Spacer(minLength: 0)
            }
            if p.chips.isEmpty {
                Text("no snapshot")
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            } else {
                ObsFlowLayout(spacing: 4) {
                    ForEach(Array(p.chips.enumerated()), id: \.offset) { _, c in
                        ObsChipView(chip: c)
                    }
                }
            }
        }
    }
}

// ── Federated peers block inside System Statistics (filters, group-by-node) ─

struct ObserverPeersBlock: View {
    @ObservedObject var vm: ObserverViewModel
    let profile: ServerProfile
    @EnvironmentObject private var toaster: ObserverToastCenter
    @AppStorage("cs_peer_filter") private var filter: String = "all"
    @AppStorage("cs_peer_group_by_node") private var groupByNode: Bool = false
    @State private var metaGroups: [IosMetaGroup]? = nil
    @State private var snapshotPeer: ObsPeerRef? = nil
    @State private var removeTarget: ObsPeerRef? = nil

    private var peers: [IosPeerRow] { vm.systems?.peers ?? [] }

    private var visible: [IosPeerRow] {
        filter == "all" ? peers : peers.filter { $0.shapeKey == filter }
    }

    var body: some View {
        ObsBlock {
            ObsSubHeader(title: "Federated peers", note: "(datawatch-stats)")
            content
        }
        .task(id: groupByNode) { loadMeta() }
        .sheet(item: $snapshotPeer) { ref in
            PeerSnapshotSheet(profile: profile, name: ref.name)
        }
        .peerRemoveAlert(target: $removeTarget, profile: profile, toaster: toaster)
    }

    @ViewBuilder
    private var content: some View {
        if let s = vm.systems {
            if !s.peersError.isEmpty {
                Text("peer registry \(L(s.peersError)) · enable with observer.peers.allow_register: true")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            } else if peers.isEmpty {
                Text("no peers registered · deploy datawatch-stats --datawatch <url> --name <peer> on a remote host, or spawn an autonomous worker (it auto-peers).")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            } else {
                pills
                if groupByNode {
                    metaContent
                } else if visible.isEmpty {
                    Text("no peers match the \"\(filter)\" filter")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                } else {
                    ForEach(Array(visible.enumerated()), id: \.offset) { _, p in
                        peerRow(p)
                    }
                }
            }
        } else {
            ObsMuted(text: L("Loading…"))
        }
    }

    private var pills: some View {
        ObsFlowLayout(spacing: 4) {
            pill("all", "All")
            pill("A", "Agents")
            pill("B", "Standalone")
            pill("C", "Cluster")
            Button {
                groupByNode.toggle()
            } label: {
                Text("⊞ \(L("Group by ComputeNode"))")
                    .font(.caption2)
                    .foregroundStyle(groupByNode ? DatawatchColors.background : DatawatchColors.onSurface)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(groupByNode ? DatawatchColors.secondary : DatawatchColors.surface2, in: Capsule())
            }
            .buttonStyle(.borderless)
        }
    }

    private func pill(_ value: String, _ label: String) -> some View {
        let count: Int = value == "all" ? peers.count : peers.filter { $0.shapeKey == value }.count
        let active: Bool = filter == value
        return Button {
            filter = value
        } label: {
            Text("\(L(label)) (\(count))")
                .font(.caption2)
                .foregroundStyle(active ? DatawatchColors.background : DatawatchColors.onSurface)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(active ? DatawatchColors.secondary : DatawatchColors.surface2, in: Capsule())
        }
        .buttonStyle(.borderless)
    }

    private func peerRow(_ p: IosPeerRow) -> some View {
        HStack(alignment: .center, spacing: 6) {
            ObsDot(tone: p.dotTone)
            VStack(alignment: .leading, spacing: 2) {
                ObsFlowLayout(spacing: 4) {
                    Text(p.name).font(DatawatchFonts.labelSmall.weight(.semibold))
                    ObsTag(text: L(p.shapeLabel))
                    if p.attachedNodes.isEmpty {
                        ObsTag(text: L("free"), dashed: true)
                    } else {
                        ObsTag(text: "⇄ " + p.attachedNodes.joined(separator: ", "), tone: "accent2")
                            .accessibilityLabel(Text("attached to ComputeNode \(p.attachedNodes.joined(separator: ", "))"))
                    }
                    if !p.version.isEmpty {
                        Text("v\(p.version)").font(.caption2).foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                }
                Text(L(p.ageLabel))
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .foregroundStyle(DatawatchColors.onSurface)
            Spacer(minLength: 4)
            PeerActionButtons(
                onSnapshot: { snapshotPeer = ObsPeerRef(name: p.name) },
                onRemove: { removeTarget = ObsPeerRef(name: p.name) }
            )
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private var metaContent: some View {
        if let groups = metaGroups {
            ForEach(Array(groups.enumerated()), id: \.offset) { _, g in
                HStack(alignment: .top, spacing: 8) {
                    Rectangle()
                        .fill(g.unbound ? DatawatchColors.onSurfaceMuted : DatawatchColors.secondary)
                        .frame(width: 2)
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 6) {
                            Text(L(g.title))
                                .font(DatawatchFonts.labelSmall.weight(.semibold))
                                .foregroundStyle(g.unbound ? DatawatchColors.onSurfaceMuted : DatawatchColors.onSurface)
                            Text(g.subtitle).font(.caption2).foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                        ForEach(Array(g.lines.enumerated()), id: \.offset) { _, line in
                            Text(line).font(.caption2).foregroundStyle(DatawatchColors.onSurface)
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        } else {
            ObsMuted(text: L("Loading…"))
        }
    }

    private func loadMeta() {
        guard groupByNode else { return }
        IosObserver.shared.loadMetaPeers(
            profile: profile,
            onSuccess: { groups in DispatchQueue.main.async { metaGroups = groups } },
            onError: { _ in DispatchQueue.main.async { metaGroups = []; groupByNode = false } }
        )
    }
}

private struct PeerActionButtons: View {
    let onSnapshot: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 2) {
            Button(action: onSnapshot) {
                Image(systemName: "chart.bar.xaxis")
                    .font(.caption)
                    .padding(4)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Last snapshot")
            Button(action: onRemove) {
                Image(systemName: "xmark")
                    .font(.caption)
                    .padding(4)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Remove peer")
        }
        .foregroundStyle(DatawatchColors.onSurfaceMuted)
    }
}

// ── Remove confirm (PWA confirm()) ────────────────────────────────────────

private struct PeerRemoveAlert: ViewModifier {
    @Binding var target: ObsPeerRef?
    let profile: ServerProfile
    let toaster: ObserverToastCenter
    var onRemoved: (() -> Void)? = nil

    func body(content: Content) -> some View {
        content.alert(
            Text("Remove peer \"\(target?.name ?? "")\"?"),
            isPresented: Binding(get: { target != nil }, set: { if !$0 { target = nil } }),
            presenting: target
        ) { ref in
            Button("Remove", role: .destructive) { remove(ref.name) }
            Button("Cancel", role: .cancel) {}
        } message: { _ in
            Text("It will auto-re-register on next push (token rotates).")
        }
    }

    private func remove(_ name: String) {
        IosObserver.shared.removePeer(profile: profile, name: name) { err in
            Task { @MainActor in
                toaster.show(err ?? "\(L("Removed peer")) \(name)")
                onRemoved?()
            }
        }
    }
}

extension View {
    func peerRemoveAlert(
        target: Binding<ObsPeerRef?>,
        profile: ServerProfile,
        toaster: ObserverToastCenter,
        onRemoved: (() -> Void)? = nil
    ) -> some View {
        modifier(PeerRemoveAlert(target: target, profile: profile, toaster: toaster, onRemoved: onRemoved))
    }
}

// ── Peer snapshot sheet (PWA showObserverPeerSnapshot) ────────────────────

struct PeerSnapshotSheet: View {
    let profile: ServerProfile
    let name: String
    @Environment(\.dismiss) private var dismiss
    @State private var snapshot: IosPeerSnapshot? = nil
    @State private var error: String? = nil

    var body: some View {
        NavigationStack {
            List {
                if let s = snapshot {
                    Section {
                        Text(s.headLine)
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .listRowBackground(DatawatchColors.surface)
                    Section {
                        if s.envelopes.isEmpty {
                            Text("No envelopes in this snapshot.")
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                        ForEach(Array(s.envelopes.enumerated()), id: \.offset) { _, e in
                            envelopeRow(e)
                        }
                    } header: {
                        Text("\(s.envelopes.count) envelopes")
                    }
                    .listRowBackground(DatawatchColors.surface)
                } else if let error {
                    Text("Snapshot unavailable: \(error)")
                        .foregroundStyle(DatawatchColors.error)
                        .listRowBackground(DatawatchColors.surface)
                } else {
                    ProgressView()
                        .listRowBackground(DatawatchColors.surface)
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle("Peer snapshot")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .task {
            IosObserver.shared.peerSnapshot(
                profile: profile,
                name: name,
                onSuccess: { s in DispatchQueue.main.async { snapshot = s } },
                onError: { msg in DispatchQueue.main.async { error = msg } }
            )
        }
    }

    private func envelopeRow(_ e: IosSnapshotEnvelope) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                Text(e.kind)
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.secondary)
                Text(e.id)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
            Text(e.stats)
                .font(DatawatchFonts.labelSmall.monospacedDigit())
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }
}

// ── Bottom "Federated Peers" card (renderObserverPeersCard, 8 s live) ────

struct ObserverFederatedPeersCard: View {
    let profile: ServerProfile
    @EnvironmentObject private var toaster: ObserverToastCenter
    @State private var card: IosPeersCard? = nil
    @State private var snapshotPeer: ObsPeerRef? = nil
    @State private var removeTarget: ObsPeerRef? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let c = card {
                if !c.statPills.isEmpty { statPills(c.statPills) }
                if !c.config.isEmpty { configTable(c.config) }
                HStack(spacing: 6) {
                    Text("\(L("Peers").uppercased()) (\(c.peers.count))")
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    ObsLiveDot()
                    Text("live").font(.caption2).foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                if c.peers.isEmpty {
                    ObsMuted(text: L("no peers registered"))
                }
                ForEach(Array(c.peers.enumerated()), id: \.offset) { _, p in
                    peerRow(p)
                }
            } else {
                ObsMuted(text: L("Loading…"))
            }
        }
        .task(id: profile.id) {
            while !Task.isCancelled {
                load()
                try? await Task.sleep(nanoseconds: 8_000_000_000)
            }
        }
        .sheet(item: $snapshotPeer) { ref in
            PeerSnapshotSheet(profile: profile, name: ref.name)
        }
        .peerRemoveAlert(target: $removeTarget, profile: profile, toaster: toaster, onRemoved: { load() })
    }

    private func load() {
        // PWA skips the refresh while a snapshot drill-down is open.
        guard snapshotPeer == nil else { return }
        IosObserver.shared.loadPeersCard(profile: profile) { c in
            DispatchQueue.main.async { card = c }
        }
    }

    private func statPills(_ pills: [IosObsKv]) -> some View {
        ObsFlowLayout(spacing: 8) {
            ForEach(Array(pills.enumerated()), id: \.offset) { _, kv in
                VStack(alignment: .leading, spacing: 1) {
                    Text(kv.key.uppercased())
                        .font(.caption2)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Text(kv.value)
                        .font(DatawatchFonts.labelSmall.weight(.semibold))
                        .foregroundStyle(DatawatchColors.onSurface)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .frame(minWidth: 80, alignment: .leading)
                .background(DatawatchColors.background.opacity(0.5), in: RoundedRectangle(cornerRadius: 8))
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.border, lineWidth: 1))
            }
        }
    }

    private func configTable(_ rows: [IosObsKv]) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("CONFIG")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            ForEach(Array(rows.enumerated()), id: \.offset) { _, kv in
                HStack {
                    Text(kv.key).foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Spacer(minLength: 6)
                    Text(kv.value)
                        .foregroundStyle(ObsTone.color(kv.tone))
                        .fontWeight(kv.tone == "muted" ? .regular : .semibold)
                        .multilineTextAlignment(.trailing)
                }
                .font(DatawatchFonts.labelSmall)
            }
        }
        .padding(10)
        .background(DatawatchColors.background.opacity(0.5), in: RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.border, lineWidth: 1))
    }

    private func peerRow(_ p: IosPeerRow) -> some View {
        VStack(spacing: 0) {
            Divider().overlay(DatawatchColors.border)
            HStack(spacing: 6) {
                ObsDot(tone: p.dotTone)
                Text(p.name)
                    .font(DatawatchFonts.labelSmall.weight(.semibold))
                    .foregroundStyle(DatawatchColors.onSurface)
                ObsTag(text: L(p.shapeLabel))
                Text(L(p.ageShort))
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer(minLength: 4)
                PeerActionButtons(
                    onSnapshot: { snapshotPeer = ObsPeerRef(name: p.name) },
                    onRemove: { removeTarget = ObsPeerRef(name: p.name) }
                )
            }
            .padding(.vertical, 4)
        }
    }
}
