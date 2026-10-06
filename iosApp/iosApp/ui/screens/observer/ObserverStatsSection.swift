import SwiftUI
import UIKit
import DatawatchShared

// System Statistics card body (parity B20–B23). Sub-block order follows
// PWA renderObserverView: per-system grid → statistics panel → eBPF →
// network traffic → installed plugins → peer resources → federated peers →
// cluster nodes → MCP channel bridge → channel diagnostics → comm backends.
// The D78a server-info card leads the block.

struct ObserverStatsBlock: View {
    @ObservedObject var vm: ObserverViewModel
    let profile: ServerProfile

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let info = vm.serverInfo {
                ObsStatCard(card: info)
            }
            ObserverSystemGrid(cards: vm.systems?.grid ?? [])
            ObserverStatsPanelView(vm: vm)
            ObserverEbpfBlocks(snapshot: vm.ebpf)
            ObserverPluginsBlock(rows: vm.plugins, error: vm.pluginsError)
            ObserverPeerResourcesBlock(systems: vm.systems)
            ObserverPeersBlock(vm: vm, profile: profile)
            if !vm.cluster.isEmpty {
                ObserverClusterBlock(rows: vm.cluster)
            }
            ObserverChannelBlocks(vm: vm)
            ObserverCommBlock(vm: vm, profile: profile)
        }
    }
}

// ── Per-system grid (BL379) ───────────────────────────────────────────────

struct ObserverSystemGrid: View {
    let cards: [IosSystemCard]

    var body: some View {
        if !cards.isEmpty {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 200), spacing: 8)], spacing: 8) {
                ForEach(Array(cards.enumerated()), id: \.offset) { _, card in
                    SystemCardView(card: card)
                }
            }
        }
    }
}

private struct SystemCardView: View {
    let card: IosSystemCard

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                ObsDot(tone: card.dotTone, size: 7)
                Text(card.name)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(1)
                    .truncationMode(.tail)
                if card.isLocal {
                    Text("local")
                        .font(.caption2)
                        .foregroundStyle(Color.white)
                        .padding(.horizontal, 5)
                        .padding(.vertical, 1)
                        .background(DatawatchColors.primary, in: RoundedRectangle(cornerRadius: 3))
                }
                Spacer(minLength: 0)
            }
            ForEach(Array(card.bars.enumerated()), id: \.offset) { _, bar in
                ObsBarView(bar: bar)
            }
            ForEach(Array(card.lines.enumerated()), id: \.offset) { _, line in
                ObsKvRow(kv: line)
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background(DatawatchColors.background.opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.border, lineWidth: 1))
    }
}

// ── Statistics panel (renderStatsData) ───────────────────────────────────

struct ObserverStatsPanelView: View {
    @ObservedObject var vm: ObserverViewModel
    @EnvironmentObject private var toaster: ObserverToastCenter

    var body: some View {
        ObsBlock {
            if let panel = vm.panel {
                panelContent(panel)
            } else if let err = vm.error {
                Text("Stats unavailable.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                Text(err)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
            } else {
                CardSkeleton()
            }
        }
    }

    @ViewBuilder
    private func panelContent(_ panel: IosStatsPanel) -> some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 180), spacing: 8)], spacing: 8) {
            ForEach(Array(panel.bars.enumerated()), id: \.offset) { _, bar in
                ObsBarView(bar: bar, barHeight: 6)
                    .padding(8)
                    .background(DatawatchColors.background.opacity(0.5))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.border, lineWidth: 1))
            }
        }
        if !panel.gpuError.isEmpty {
            GpuProbeFailedCard(reason: panel.gpuError)
        }
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 180), spacing: 8)], spacing: 8) {
            ForEach(Array(panel.cards.enumerated()), id: \.offset) { _, card in
                ObsStatCard(card: card) { cmd in
                    UIPasteboard.general.string = cmd
                    toaster.show(L("Copied to clipboard"))
                }
            }
        }
        WebSearchUsageCard(vm: vm)
        SessionStatisticsRow(panel: panel)
        if !panel.ebpfBanner.isEmpty {
            EbpfBanner(text: panel.ebpfBanner, degraded: panel.ebpfDegraded)
        }
    }
}

private struct GpuProbeFailedCard: View {
    let reason: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("GPU probe failed")
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(DatawatchColors.error)
            Text(reason)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .textSelection(.enabled)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.background.opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.error, lineWidth: 1))
    }
}

/// PWA "Session Statistics" mini donut (active of max) + counts (D78a ring).
private struct SessionStatisticsRow: View {
    let panel: IosStatsPanel

    private var ringFraction: CGFloat {
        let f: Double = panel.sessionFraction
        return CGFloat(f)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Divider().overlay(DatawatchColors.border)
            Text("Session Statistics")
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            HStack(spacing: 12) {
                ZStack {
                    Circle().stroke(DatawatchColors.border, lineWidth: 8)
                    Circle()
                        .trim(from: 0, to: ringFraction)
                        .stroke(DatawatchColors.success, style: StrokeStyle(lineWidth: 8, lineCap: .butt))
                        .rotationEffect(.degrees(-90))
                    Text("\(panel.sessionActive)")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(DatawatchColors.onSurface)
                }
                .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(panel.sessionActive) of \(panel.sessionMax) max")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Text(panel.sessionCounts)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                Spacer(minLength: 0)
            }
            .accessibilityElement(children: .combine)
        }
    }
}

private struct EbpfBanner: View {
    let text: String
    let degraded: Bool

    var body: some View {
        if degraded {
            VStack(alignment: .leading, spacing: 2) {
                Text("eBPF Degraded")
                    .font(DatawatchFonts.labelSmall.weight(.semibold))
                    .foregroundStyle(DatawatchColors.warning)
                Text(text)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .padding(10)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(DatawatchColors.warning.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.warning.opacity(0.3), lineWidth: 1))
        } else {
            Text(text)
                .font(.caption2)
                .foregroundStyle(DatawatchColors.success)
        }
    }
}

// ── Web search usage (BL391) + history sheet ──────────────────────────────

private struct WebSearchUsageCard: View {
    @ObservedObject var vm: ObserverViewModel
    @State private var showHistory = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Search Usage")
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if let ws = vm.webSearch {
                if ws.hasProviders {
                    ForEach(Array(ws.rows.enumerated()), id: \.offset) { _, row in ObsKvRow(kv: row) }
                    ForEach(Array(ws.providers.enumerated()), id: \.offset) { _, row in ObsKvRow(kv: row) }
                    SeriesBars(counts: ws.series.map { $0.intValue })
                        .accessibilityLabel("Daily queries, last 14 days")
                    ObsButton(title: "History") { showHistory = true }
                } else {
                    ObsMuted(text: L("No search providers enabled. Configure one in Settings → Web Search Providers."))
                }
            } else if let err = vm.webSearchError {
                ObsMuted(text: L(err))
            } else {
                CardSkeleton()
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.background.opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.border, lineWidth: 1))
        .sheet(isPresented: $showHistory) {
            if let p = vm.profile {
                WebSearchHistorySheet(profile: p)
            }
        }
    }
}

private struct SeriesBars: View {
    let counts: [Int]

    private var maxCount: Int { max(1, counts.max() ?? 1) }

    var body: some View {
        HStack(alignment: .bottom, spacing: 1) {
            ForEach(Array(counts.enumerated()), id: \.offset) { _, c in
                RoundedRectangle(cornerRadius: 1)
                    .fill(DatawatchColors.primary)
                    .frame(width: 5, height: barHeight(c))
            }
        }
        .frame(height: 16, alignment: .bottom)
    }

    private func barHeight(_ c: Int) -> CGFloat {
        let h: Double = (16.0 * Double(c) / Double(maxCount)).rounded()
        return CGFloat(max(1.0, h))
    }
}

private struct WebSearchHistorySheet: View {
    let profile: ServerProfile
    @Environment(\.dismiss) private var dismiss
    @State private var rows: [IosWebSearchHistoryRow]? = nil
    @State private var error: String? = nil

    var body: some View {
        NavigationStack {
            List {
                if let rows {
                    if rows.isEmpty {
                        Text("No search history yet.")
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    ForEach(Array(rows.enumerated()), id: \.offset) { _, r in
                        historyRow(r)
                    }
                } else if let error {
                    Text(error).foregroundStyle(DatawatchColors.error)
                } else {
                    ProgressView()
                }
            }
            .listRowBackground(DatawatchColors.surface)
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle("Search history")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .task {
            IosObserver.shared.loadWebSearchHistory(
                profile: profile,
                onSuccess: { list in DispatchQueue.main.async { rows = list } },
                onError: { msg in DispatchQueue.main.async { error = msg } }
            )
        }
    }

    private func historyRow(_ r: IosWebSearchHistoryRow) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(r.time)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer()
                Text(L(r.status))
                    .foregroundStyle(ObsTone.color(r.tone))
            }
            .font(DatawatchFonts.labelSmall)
            Text(r.provider)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.secondary)
            Text(r.query)
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(2)
        }
    }
}

// ── eBPF status + per-process network (B21) ───────────────────────────────

struct ObserverEbpfBlocks: View {
    let snapshot: IosEbpfSnapshot?

    var body: some View {
        ObsBlock {
            ObsSubHeader(title: "eBPF (per-process net)")
            if let s = snapshot {
                HStack(spacing: 6) {
                    ObsDot(tone: s.tone)
                    Text(L(s.headline))
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                if !s.message.isEmpty {
                    Text(s.message)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.8))
                }
            } else {
                CardSkeleton()
            }
        }
        ObsBlock {
            ObsSubHeader(title: "Network Traffic")
            if let s = snapshot {
                if s.procs.isEmpty {
                    ObsMuted(text: L("No eBPF data available"))
                } else {
                    NetTrafficTable(procs: s.procs)
                }
            } else {
                CardSkeleton()
            }
        }
    }
}

private struct NetTrafficTable: View {
    let procs: [IosNetProc]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Process").frame(maxWidth: .infinity, alignment: .leading)
                Text("In").frame(width: 84, alignment: .trailing)
                Text("Out").frame(width: 84, alignment: .trailing)
            }
            .font(.caption2.weight(.medium))
            .textCase(.uppercase)
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
            .padding(.bottom, 4)
            ForEach(Array(procs.enumerated()), id: \.offset) { _, p in
                VStack(spacing: 0) {
                    Divider().overlay(DatawatchColors.border)
                    HStack {
                        Text(p.name)
                            .lineLimit(1)
                            .truncationMode(.tail)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(p.rx)
                            .foregroundStyle(DatawatchColors.success)
                            .frame(width: 84, alignment: .trailing)
                        Text(p.tx)
                            .foregroundStyle(DatawatchColors.primary)
                            .frame(width: 84, alignment: .trailing)
                    }
                    .font(DatawatchFonts.labelSmall.monospacedDigit())
                    .padding(.vertical, 3)
                }
            }
        }
    }
}

// ── Installed plugins (B21) ───────────────────────────────────────────────

struct ObserverPluginsBlock: View {
    let rows: [IosPluginRow]?
    let error: String?

    var body: some View {
        ObsBlock {
            ObsSubHeader(title: "Installed plugins")
            if let rows {
                if rows.isEmpty {
                    ObsMuted(text: L("none installed"))
                }
                ForEach(Array(rows.enumerated()), id: \.offset) { _, p in
                    pluginRow(p)
                }
            } else if let error {
                ObsMuted(text: L(error))
            } else {
                CardSkeleton()
            }
        }
    }

    private func pluginRow(_ p: IosPluginRow) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            ObsDot(tone: p.enabled ? "success" : "muted")
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Text(p.name).font(DatawatchFonts.labelSmall.weight(.semibold))
                    if p.native { ObsTag(text: L("native")) }
                    if !p.version.isEmpty {
                        Text("v\(p.version)").font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                }
                if !p.detail.isEmpty {
                    Text(p.detail)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .foregroundStyle(DatawatchColors.onSurface)
            Spacer(minLength: 0)
        }
    }
}

// ── Cluster nodes (B22; hidden while empty) ───────────────────────────────

struct ObserverClusterBlock: View {
    let rows: [IosClusterRow]

    var body: some View {
        ObsBlock {
            ObsSubHeader(title: "Cluster nodes")
            ForEach(Array(rows.enumerated()), id: \.offset) { _, n in
                clusterRow(n)
            }
        }
    }

    private func clusterRow(_ n: IosClusterRow) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                ObsDot(tone: n.ready ? "success" : "error")
                Text(n.name).font(DatawatchFonts.labelSmall.weight(.semibold))
                if !n.pressure.isEmpty {
                    Text(n.pressure).font(.caption2).foregroundStyle(DatawatchColors.warning)
                }
                Text("· \(n.pods)").font(.caption2).foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer(minLength: 0)
            }
            .foregroundStyle(DatawatchColors.onSurface)
            HStack(spacing: 10) {
                ObsMiniBar(label: "cpu", pct: Int(n.cpuPct), color: DatawatchColors.primary)
                ObsMiniBar(label: "mem", pct: Int(n.memPct), color: DatawatchColors.secondary)
            }
        }
    }
}

private struct ObsMiniBar: View {
    let label: String
    let pct: Int
    let color: Color

    private var fill: CGFloat {
        let f: Double = Double(min(100, max(0, pct))) / 100.0
        return CGFloat(60.0 * f)
    }

    var body: some View {
        HStack(spacing: 4) {
            Text(L(label)).font(.caption2).foregroundStyle(DatawatchColors.onSurfaceMuted)
            ZStack(alignment: .leading) {
                Capsule().fill(DatawatchColors.background).frame(width: 60, height: 5)
                Capsule().fill(color).frame(width: fill, height: 5)
            }
            Text("\(pct)%").font(.caption2.monospacedDigit()).foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }
}

// ── MCP channel bridge + diagnostics (B23) ────────────────────────────────

struct ObserverChannelBlocks: View {
    @ObservedObject var vm: ObserverViewModel
    @State private var bridgeOpen = false
    @State private var diagOpen = false

    var body: some View {
        ObsBlock {
            DisclosureGroup(isExpanded: $bridgeOpen) {
                VStack(alignment: .leading, spacing: 2) {
                    if let lines = vm.bridge {
                        ForEach(Array(lines.enumerated()), id: \.offset) { _, l in ObsLineView(line: l) }
                    } else {
                        CardSkeleton()
                    }
                }
                .padding(.top, 4)
            } label: {
                ObsSubHeader(title: "MCP channel bridge")
            }
            .tint(DatawatchColors.onSurfaceMuted)
        }
        ObsBlock {
            DisclosureGroup(isExpanded: $diagOpen) {
                diagnosticsBody
                    .padding(.top, 4)
            } label: {
                HStack(spacing: 8) {
                    ObsSubHeader(title: "Channel bridge diagnostics")
                    Button {
                        vm.refreshDiagnostics()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.caption2)
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Refresh diagnostics")
                }
            }
            .tint(DatawatchColors.onSurfaceMuted)
        }
    }

    @ViewBuilder
    private var diagnosticsBody: some View {
        if let d = vm.diagnostics {
            VStack(alignment: .leading, spacing: 2) {
                ForEach(Array(d.lines.enumerated()), id: \.offset) { _, l in ObsLineView(line: l) }
                if !d.hints.isEmpty {
                    DisclosureGroup {
                        ForEach(Array(d.hints.enumerated()), id: \.offset) { _, h in
                            Text("• \(h)")
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    } label: {
                        Text("⚠ \(d.hints.count) hints")
                            .font(DatawatchFonts.labelSmall.weight(.semibold))
                            .foregroundStyle(DatawatchColors.warning)
                    }
                    .tint(DatawatchColors.warning)
                }
            }
        } else {
            CardSkeleton()
        }
    }
}

// ── Communication backends + Matrix test (B23) ────────────────────────────

struct ObserverCommBlock: View {
    @ObservedObject var vm: ObserverViewModel
    let profile: ServerProfile
    @EnvironmentObject private var toaster: ObserverToastCenter

    var body: some View {
        ObsBlock {
            ObsSubHeader(title: "Communication backends")
            if let c = vm.comm {
                if !c.message.isEmpty {
                    ObsMuted(text: L(c.message))
                }
                ForEach(c.enabled, id: \.self) { name in
                    backendRow(name)
                }
            } else {
                CardSkeleton()
            }
        }
    }

    @ViewBuilder
    private func backendRow(_ name: String) -> some View {
        HStack(spacing: 6) {
            ObsDot(tone: "success", size: 7)
            Text(name.capitalized)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurface)
            if name == "matrix" {
                Text(vm.matrix.map { L($0.text) } ?? L("checking…"))
                    .font(.caption2)
                    .foregroundStyle(ObsTone.color(vm.matrix?.tone ?? "muted"))
                Spacer(minLength: 4)
                ObsButton(title: "Test") { sendMatrixTest() }
            } else {
                Spacer(minLength: 0)
            }
        }
    }

    private func sendMatrixTest() {
        IosObserver.shared.matrixTest(profile: profile) { err in
            Task { @MainActor in
                toaster.show(err ?? L("Matrix test message sent"))
            }
        }
    }
}
