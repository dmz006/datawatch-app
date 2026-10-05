import SwiftUI
import DatawatchShared

/// PRD active-session card (parity B17; PWA _loadPRDActiveSessionCard,
/// Android PrdActiveSessionsCard). Polls every 5 s while visible:
/// - live sessions → state pill, id, name, hook dot, tests, story · task,
///   last event, CPU/RAM/GPU bars; tap opens the session;
/// - planning with no session → "Decomposing automaton…" spinner;
/// - running/decomposing with no session → stuck warning + Unstick + Cancel.
struct PrdActiveSessionCard: View {
    let profile: ServerProfile
    let prd: PrdDto
    let onCancel: () -> Void

    @State private var rows: [IosPrdActiveSessionRow]? = nil
    @State private var stuckExpanded = true

    private var status: String { prd.status.lowercased() }

    var body: some View {
        content
            .task(id: prd.id + prd.status) {
                while !Task.isCancelled {
                    await load()
                    try? await Task.sleep(nanoseconds: 5_000_000_000)
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        if let rows, !rows.isEmpty {
            VStack(spacing: 6) {
                ForEach(rows, id: \.session.id) { row in
                    NavigationLink {
                        SessionDetailView(session: row.session, profile: profile)
                    } label: {
                        sessionRow(row)
                    }
                    .buttonStyle(.plain)
                }
            }
        } else if rows != nil, status == "planning" {
            HStack(spacing: 8) {
                ProgressView().controlSize(.small)
                Text("Decomposing automaton…").font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer()
            }
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(DatawatchColors.surface)
            .overlay(alignment: .leading) { accentEdge(DatawatchColors.secondary) }
            .clipShape(RoundedRectangle(cornerRadius: 6))
        } else if rows != nil, ["decomposing", "running"].contains(status) {
            stuckWarning
        }
    }

    private var stuckWarning: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button { withAnimation { stuckExpanded.toggle() } } label: {
                HStack(spacing: 6) {
                    Text("⚠ No active session record").fontWeight(.semibold)
                    Spacer()
                    Image(systemName: stuckExpanded ? "chevron.up" : "chevron.down").font(.caption2).opacity(0.6)
                }
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.warning)
            }
            .buttonStyle(.plain)
            if stuckExpanded {
                Text("Status looks active but no spawned session was found. The LLM call may have failed silently.")
                    .font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                (Text("Unstick: ").fontWeight(.semibold)
                    + Text("Cancel below, then re-trigger Plan after verifying the LLM's compute node is reachable in Compute Nodes."))
                    .font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                Button("✕ Cancel", action: onCancel)
                    .font(DatawatchFonts.labelSmall)
                    .buttonStyle(.bordered)
                    .accessibilityHint("Cancel this automaton")
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.warning.opacity(0.08), in: RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.warning.opacity(0.3), lineWidth: 1))
    }

    private func sessionRow(_ row: IosPrdActiveSessionRow) -> some View {
        let s = row.session
        return VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 8) {
                Text(stateText(s.state))
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(stateColor(s.state))
                    .padding(.horizontal, 7).padding(.vertical, 1)
                    .overlay(Capsule().stroke(stateColor(s.state), lineWidth: 1))
                Text(s.id)
                    .font(DatawatchFonts.terminalSmall)
                    .padding(.horizontal, 6).padding(.vertical, 1)
                    .background(DatawatchColors.background, in: RoundedRectangle(cornerRadius: 4))
                if let n = s.name, !n.isEmpty {
                    Text(n).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurface).lineLimit(1)
                }
                if let board = row.board {
                    Text("●").font(.system(size: 11)).foregroundStyle(hookColor(board.hookHealth))
                        .accessibilityLabel("Hooks \(board.hookHealth)")
                    if let tests = board.tests {
                        (Text("\(tests.passing)✓").foregroundColor(DatawatchColors.success)
                            + Text(" / ") + Text("\(tests.failing)✗").foregroundColor(DatawatchColors.error))
                            .font(DatawatchFonts.labelSmall)
                    }
                }
                Spacer(minLength: 4)
                Text("→").font(DatawatchFonts.labelSmall).opacity(0.6)
            }
            if row.storyTitle != nil || row.taskTitle != nil {
                HStack(spacing: 4) {
                    if let st = row.storyTitle { Text(st).opacity(0.6) }
                    if row.storyTitle != nil, row.taskTitle != nil { Text("·").opacity(0.4) }
                    if let tt = row.taskTitle { Text(tt) }
                }
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .lineLimit(1)
            }
            if let ev = row.board?.lastEvent?.event, !ev.isEmpty {
                Text(ev + ((row.board?.lastEvent?.tool).map { " · \($0)" } ?? ""))
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            if let node = row.node {
                resourceBars(node).padding(.top, 4)
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.surface)
        .overlay(alignment: .leading) { accentEdge(DatawatchColors.secondary) }
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private func resourceBars(_ d: ComputeNodeDetailDto) -> some View {
        VStack(spacing: 3) {
            let cpu = d.cpu?.pct ?? d.cpuPct?.doubleValue
            if let cpu {
                bar("CPU", cpu / 100, "\(Int(cpu.rounded()))%",
                    cpu >= 90 ? DatawatchColors.error : cpu >= 70 ? DatawatchColors.warning : DatawatchColors.success)
            }
            if let mem = d.mem, mem.totalBytes > 0 {
                let frac = Double(mem.usedBytes) / Double(mem.totalBytes)
                bar("RAM", frac, "\(Self.fmtBytes(mem.usedBytes)) / \(Self.fmtBytes(mem.totalBytes))",
                    frac >= 0.85 ? DatawatchColors.error : DatawatchColors.primary)
            }
            let multi = d.gpu.count > 1
            ForEach(Array(d.gpu.enumerated()), id: \.offset) { i, g in
                let prefix = multi ? "GPU \(i + 1)" : "GPU"
                let extra = "\(Int(g.utilPct.rounded()))%" + (g.tempC > 0 ? " \(Int(g.tempC.rounded()))°C" : "")
                    + (g.powerW > 0 ? " \(Int(g.powerW.rounded()))W" : "")
                bar("\(prefix) util", g.utilPct / 100, extra, g.utilPct >= 80 ? DatawatchColors.error : DatawatchColors.secondary)
                if g.memTotalBytes > 0 {
                    bar("\(prefix) VRAM", Double(g.memUsedBytes) / Double(g.memTotalBytes),
                        "\(Self.fmtBytes(g.memUsedBytes)) / \(Self.fmtBytes(g.memTotalBytes))", DatawatchColors.secondary)
                }
            }
            if d.gpu.isEmpty, let err = d.gpuError, !err.isEmpty {
                Text("GPU probe failed: \(String(err.prefix(80)))")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private func bar(_ label: String, _ fraction: Double, _ value: String, _ color: Color) -> some View {
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
                    Capsule().fill(color).frame(width: geo.size.width * min(1, max(0, fraction)))
                }
            }
            .frame(height: 4)
        }
    }

    /// 3 pt left accent (PWA border-left); the parent clips it to the corner radius.
    private func accentEdge(_ color: Color) -> some View {
        Rectangle().fill(color).frame(width: 3)
    }

    private func hookColor(_ health: String) -> Color {
        switch health {
        case "alive": return DatawatchColors.success
        case "stale": return DatawatchColors.warning
        default: return DatawatchColors.onSurfaceMuted
        }
    }

    private func stateColor(_ state: SessionState) -> Color {
        switch state {
        case .running: return DatawatchColors.success
        case .waiting: return DatawatchColors.waiting
        case .rateLimited: return DatawatchColors.warning
        case .error: return DatawatchColors.error
        default: return DatawatchColors.onSurfaceMuted
        }
    }

    private func stateText(_ state: SessionState) -> String {
        switch state {
        case .running: return "running"
        case .waiting: return "waiting_input"
        case .rateLimited: return "rate_limited"
        case .error: return "error"
        default: return state.name.lowercased()
        }
    }

    private static func fmtBytes(_ b: Int64) -> String {
        if b <= 0 { return "0 B" }
        if b >= 1_073_741_824 { return String(format: "%.1f GB", Double(b) / 1_073_741_824) }
        if b >= 1_048_576 { return "\(b / 1_048_576) MB" }
        return "\(b / 1024) KB"
    }

    private func load() async {
        let result: [IosPrdActiveSessionRow]? = await withCheckedContinuation { cont in
            IosPrdActiveSessions.shared.load(profile: profile, prd: prd) { cont.resume(returning: $0) }
        }
        // Keep the last good snapshot on a transient fetch failure.
        if let result { await MainActor.run { rows = result } }
    }
}
