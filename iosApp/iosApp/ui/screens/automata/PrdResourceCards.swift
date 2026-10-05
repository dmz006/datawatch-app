import SwiftUI
import DatawatchShared

/// Automata detail Overview: per-story progress with worker CPU/RSS and the
/// compute-node card (CPU / RAM / per-GPU util·temp·power / VRAM) — Android
/// PrdDetailDialog progress card + PrdActiveComputeCard (operator 2026-10-05).
/// Polls IosPrdResources every 5 s while running / planning.
struct PrdResourceCards: View {
    let profile: ServerProfile
    let prd: PrdDto

    @State private var snapshot: IosPrdResourceSnapshot? = nil

    private var status: String { prd.status.lowercased() }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if ["running", "decomposing", "planning"].contains(status) {
                PrdStoryProgressCard(prd: prd, rows: snapshot?.stories ?? [])
            }
            if let node = snapshot?.node {
                PrdComputeNodeCard(status: status, nodeRef: snapshot?.nodeRef, node: node)
            }
        }
        .task(id: prd.id + prd.status) {
            guard ["running", "decomposing", "planning", "blocked"].contains(status) else {
                snapshot = nil
                return
            }
            while !Task.isCancelled {
                await load()
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
    }

    private func load() async {
        let result: IosPrdResourceSnapshot = await withCheckedContinuation { cont in
            IosPrdResources.shared.load(profile: profile, prd: prd) { cont.resume(returning: $0) }
        }
        await MainActor.run { snapshot = result }
    }
}

/// Android "PROGRESS" card: ✓ Decomposed · N stories · M tasks, then one bar per story.
struct PrdStoryProgressCard: View {
    let prd: PrdDto
    let rows: [PrdStoryResourceRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("PROGRESS")
                .font(DatawatchFonts.badge)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            summary
            if prd.stories.isEmpty {
                Text("No stories yet — decomposition in progress…")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            } else {
                ForEach(displayRows, id: \.storyId) { row in
                    PrdStoryProgressRow(row: row)
                }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: DatawatchRadius.card))
    }

    /// Before the first poll lands, derive rows from the automaton alone (no CPU/RSS).
    private var displayRows: [PrdStoryResourceRow] {
        rows.isEmpty ? PrdStoryResources.shared.rows(prd: prd, envelopes: []) : rows
    }

    private var summary: some View {
        let decomposed: Bool = !prd.stories.isEmpty
        let taskCount: Int = prd.stories.reduce(0) { $0 + $1.tasks.count }
        return HStack(spacing: 8) {
            Text(verbatim: (decomposed ? "✓ " : "… ") + L("Decomposed"))
                .foregroundStyle(decomposed ? DatawatchColors.success : DatawatchColors.onSurfaceMuted)
            Text("\(prd.stories.count) stories  ·  \(taskCount) tasks")
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .font(DatawatchFonts.labelSmall)
    }
}

private struct PrdStoryProgressRow: View {
    let row: PrdStoryResourceRow

    private var tint: Color {
        if row.complete { return DatawatchColors.success }
        if row.active { return DatawatchColors.primary }
        if row.failed { return DatawatchColors.error }
        return DatawatchColors.secondary
    }

    private var glyph: String {
        if row.complete { return "✓" }
        if row.active { return "▶" }
        return "·"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Text(glyph).foregroundStyle(tint)
                Text(row.title).foregroundStyle(DatawatchColors.onSurface).lineLimit(1)
                Spacer(minLength: 4)
                Text(verbatim: "\(row.done)/\(row.total)").foregroundStyle(DatawatchColors.onSurfaceMuted)
                Text(verbatim: "\(Int(row.fraction * 100))%")
                    .fontWeight(.bold)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .frame(minWidth: 34, alignment: .trailing)
            }
            .font(DatawatchFonts.labelSmall)
            ProgressView(value: min(1.0, max(0.0, row.fraction)))
                .tint(tint)
            if !row.resourceLabel.isEmpty {
                Text(row.resourceLabel)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.8))
            }
        }
    }
}

/// Android PrdActiveComputeCard: status line + node name, CPU / RAM, per-GPU util and VRAM.
struct PrdComputeNodeCard: View {
    let status: String
    let nodeRef: String?
    let node: ComputeNodeDetailDto

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            header
            cpuBar
            ramBar
            gpuBars
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.surface)
        .overlay(alignment: .leading) { Rectangle().fill(DatawatchColors.secondary).frame(width: 3) }
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }

    private var header: some View {
        HStack(spacing: 8) {
            ProgressView().controlSize(.mini)
            Text(L(statusLabel))
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Spacer(minLength: 4)
            if let ref = nodeRef {
                Text(ref)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.6))
                    .lineLimit(1)
            }
        }
    }

    private var statusLabel: String {
        switch status {
        case "planning", "decomposing": return "Decomposing Automaton…"
        case "running": return "Running…"
        default: return status
        }
    }

    @ViewBuilder
    private var cpuBar: some View {
        let cpu: Double = node.cpu?.pct ?? node.cpuPct?.doubleValue ?? 0
        if cpu > 0 {
            PrdResourceBar(
                label: "CPU", fraction: cpu / 100, value: "\(Int(cpu))%",
                color: cpu >= 90 ? DatawatchColors.error : (cpu >= 70 ? DatawatchColors.warning : DatawatchColors.success)
            )
        }
    }

    @ViewBuilder
    private var ramBar: some View {
        if let mem = node.mem, mem.totalBytes > 0 {
            let frac: Double = Double(mem.usedBytes) / Double(mem.totalBytes)
            PrdResourceBar(
                label: "RAM", fraction: frac,
                value: Self.fmtBytes(mem.usedBytes) + " / " + Self.fmtBytes(mem.totalBytes),
                color: frac >= 0.85 ? DatawatchColors.error : DatawatchColors.primary
            )
        }
    }

    @ViewBuilder
    private var gpuBars: some View {
        let multi: Bool = node.gpu.count > 1
        ForEach(Array(node.gpu.enumerated()), id: \.offset) { i, g in
            PrdGpuBars(gpu: g, label: multi ? "GPU \(i)" : (g.name.isEmpty ? "GPU" : g.name))
        }
    }

    static func fmtBytes(_ b: Int64) -> String {
        if b >= 1_073_741_824 { return String(format: "%.1f GB", Double(b) / 1_073_741_824) }
        if b >= 1_048_576 { return "\(b / 1_048_576) MB" }
        if b >= 1024 { return "\(b / 1024) KB" }
        return "\(b) B"
    }
}

private struct PrdGpuBars: View {
    let gpu: ComputeNodeGpuStatDto
    let label: String

    var body: some View {
        VStack(spacing: 4) {
            if gpu.utilPct > 0 {
                PrdResourceBar(
                    label: label + " util", fraction: gpu.utilPct / 100, value: utilText,
                    color: gpu.utilPct >= 80 ? DatawatchColors.error : DatawatchColors.secondary
                )
            }
            if gpu.memTotalBytes > 0 {
                let frac: Double = Double(gpu.memUsedBytes) / Double(gpu.memTotalBytes)
                PrdResourceBar(
                    label: label + " VRAM", fraction: frac,
                    value: PrdComputeNodeCard.fmtBytes(gpu.memUsedBytes) + " / " + PrdComputeNodeCard.fmtBytes(gpu.memTotalBytes),
                    color: frac >= 0.85 ? DatawatchColors.error : DatawatchColors.secondary
                )
            }
        }
    }

    private var utilText: String {
        var s: String = "\(Int(gpu.utilPct))%"
        if gpu.tempC > 0 { s += "  \(Int(gpu.tempC))°C" }
        if gpu.powerW > 0 { s += "  \(Int(gpu.powerW))W" }
        return s
    }
}

private struct PrdResourceBar: View {
    let label: String
    let fraction: Double
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 1) {
            HStack {
                Text(L(label)).foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer()
                Text(value).monospacedDigit().foregroundStyle(DatawatchColors.onSurface)
            }
            .font(.system(size: 10))
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(DatawatchColors.background)
                    Capsule().fill(color).frame(width: geo.size.width * CGFloat(min(1.0, max(0.0, fraction))))
                }
            }
            .frame(height: 4)
        }
    }
}
