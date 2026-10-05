import SwiftUI
import DatawatchShared

/// Session Stats sub-tab (parity B9) — PWA Host / Container / Compute Node / LLM
/// cards with 60-point sparklines. Polls every 5 s only while visible.
struct SessionStatsView: View {
    let profile: ServerProfile
    let session: DwSession

    @State private var snapshot: IosSessionStatsSnapshot? = nil
    @State private var cpu: [Double] = []
    @State private var rss: [Double] = []
    @State private var gpuUtil: [Double] = []
    @State private var gpuTemp: [Double] = []
    @State private var pollTask: Task<Void, Never>? = nil
    private static let maxPoints = 60

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                if snapshot == nil {
                    ProgressView().frame(maxWidth: .infinity).padding(.top, 40)
                } else {
                    hostCard
                    if let c = snapshot?.envelope?.container, !c.containerId.isEmpty {
                        card("Container") {
                            kv("ID", String(c.containerId.prefix(12)))
                            if !c.image.isEmpty { kv("Image", c.image) }
                            if !c.runtime.isEmpty { kv("Runtime", c.runtime) }
                        }
                    }
                    if let ref = snapshot?.computeNodeRef {
                        computeNodeCard(ref, snapshot?.computeNode)
                    }
                    if let llm = session.llmRef ?? session.backend, !llm.isEmpty {
                        card("LLM") {
                            kv("Ref", llm)
                            if let b = session.backend, !b.isEmpty, b != llm { kv("Backend", b) }
                            Text("Token rate / latency coming in a later server release.")
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            openLink("Open LLM →", cardId: "llms")
                        }
                    }
                }
            }
            .padding(12)
        }
        .background(DatawatchColors.background)
        .onAppear { startPolling() }
        .onDisappear { pollTask?.cancel(); pollTask = nil }
    }

    // MARK: Host

    @ViewBuilder
    private var hostCard: some View {
        if let env = snapshot?.envelope {
            card(hostTitle(env)) {
                HStack(alignment: .center, spacing: 14) {
                    Donut(value: env.cpuPct / 100, color: thresholdColor(env.cpuPct))
                        .frame(width: 60, height: 60)
                    VStack(alignment: .leading, spacing: 6) {
                        metricRow("CPU", String(format: "%.1f%%", env.cpuPct), cpu, DatawatchColors.primary)
                        metricRow("RSS", bytes(env.rssBytes), rss, DatawatchColors.secondary)
                    }
                }
                if env.threads > 0 { kv("Threads", "\(env.threads)") }
                if env.fds > 0 { kv("FDs", "\(env.fds)") }
                if env.rootPid > 0 {
                    kv("PID", env.pids.count > 1 ? "\(env.rootPid) (+\(env.pids.count - 1))" : "\(env.rootPid)")
                }
                if env.netRxBps > 0 || env.netTxBps > 0 {
                    kv("Net", "↓ \(bytes(env.netRxBps))/s  ↑ \(bytes(env.netTxBps))/s")
                }
                if env.gpuPct > 0 {
                    kv("GPU", String(format: "%.0f%%", env.gpuPct) + (env.gpuMemBytes > 0 ? " · \(bytes(env.gpuMemBytes))" : ""))
                }
            }
        } else {
            // PWA renderSessionStats: no envelope → "Process Stats" +
            // `session_stats_no_envelope_body`.
            card(L("Process Stats")) {
                Text("The observer hasn't attributed a process tree to this session. Most common causes: (1) SessionAttribution is off in the observer config, or (2) the LLM runs inside a container the observer can't enter. Falling back to backend-level stats when available.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    /// PWA titles: `session_stats_process_title` for the session's own envelope,
    /// `session_stats_backend_title — <label>` for the backend fallback.
    private func hostTitle(_ env: StatEnvelopeDto) -> String {
        if env.kind == "backend" {
            let label: String = env.label.isEmpty ? (session.backend ?? "") : env.label
            return L("Backend Stats") + " — " + label
        }
        return L("Process Stats")
    }

    // MARK: Compute node

    private func computeNodeCard(_ ref: String, _ d: ComputeNodeDetailDto?) -> some View {
        card("Compute Node") {
            kv("Name", ref)
            if let d {
                if let c = d.cpu { kv("Node CPU", String(format: "%.0f%% · %d cores", c.pct, c.cores)) }
                if let m = d.mem, m.totalBytes > 0 {
                    kv("Node Mem", "\(bytes(m.usedBytes)) / \(bytes(m.totalBytes))")
                }
                if let g = d.gpu.first {
                    metricRow("GPU", String(format: "%.0f%%", g.utilPct), gpuUtil, DatawatchColors.success)
                    metricRow("Temp", String(format: "%.0f°C", g.tempC), gpuTemp, DatawatchColors.warning)
                    if g.powerW > 0 { kv("Power", String(format: "%.0f W", g.powerW)) }
                    if g.memTotalBytes > 0 { kv("VRAM", "\(bytes(g.memUsedBytes)) / \(bytes(g.memTotalBytes))") }
                } else if let err = d.gpuError, !err.isEmpty {
                    kv("GPU", err)
                }
                if let o = d.ollamaStats, o.cpuPct > 0 || o.rssBytes > 0 {
                    kv("Ollama", String(format: "%.0f%% CPU", o.cpuPct) + " · \(bytes(o.rssBytes))")
                }
            } else {
                Text("Node detail unavailable.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            // PWA `stats_open_cn` → navigate('compute') (Android onNavigateToComputeTab).
            openLink("Open Compute Node →", cardId: "compute_nodes")
        }
    }

    /// PWA accent2 underlined link (`stats_open_cn` / `stats_open_llm`) into Settings.
    private func openLink(_ title: String, cardId: String) -> some View {
        Button {
            SettingsDeepLink.open(cardId: cardId)
        } label: {
            Text(L(title))
                .font(.system(size: 11))
                .underline()
                .foregroundStyle(DatawatchColors.secondary)
        }
        .buttonStyle(.borderless)
        .padding(.top, 2)
    }

    // MARK: Building blocks

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

    private func kv(_ k: String, _ v: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            Text(k)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .frame(width: 72, alignment: .leading)
            Text(v)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .textSelection(.enabled)
        }
    }

    private func metricRow(_ label: String, _ value: String, _ series: [Double], _ color: Color) -> some View {
        HStack(spacing: 8) {
            Text(L(label))
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .frame(width: 40, alignment: .leading)
            Text(value)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .frame(width: 74, alignment: .leading)
            Sparkline(values: series, color: color)
                .frame(width: 80, height: 18)
        }
    }

    /// PWA donut colours: green < 70 % ≤ amber < 90 % ≤ red.
    private func thresholdColor(_ pct: Double) -> Color {
        pct >= 90 ? DatawatchColors.error : (pct >= 70 ? DatawatchColors.warning : DatawatchColors.success)
    }

    private func bytes(_ b: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: b, countStyle: .memory)
    }

    // MARK: Polling

    private func startPolling() {
        pollTask?.cancel()
        pollTask = Task {
            while !Task.isCancelled {
                let s: IosSessionStatsSnapshot = await withCheckedContinuation { cont in
                    IosSessionStats.shared.load(profile: profile, session: session) { cont.resume(returning: $0) }
                }
                await MainActor.run { apply(s) }
                try? await Task.sleep(for: .seconds(5))
            }
        }
    }

    private func apply(_ s: IosSessionStatsSnapshot) {
        snapshot = s
        if let env = s.envelope {
            push(&cpu, env.cpuPct)
            push(&rss, Double(env.rssBytes))
        }
        if let g = s.computeNode?.gpu.first {
            push(&gpuUtil, g.utilPct)
            push(&gpuTemp, g.tempC)
        }
    }

    private func push(_ buf: inout [Double], _ v: Double) {
        buf.append(v)
        if buf.count > Self.maxPoints { buf.removeFirst(buf.count - Self.maxPoints) }
    }
}

// MARK: - Donut + sparkline

private struct Donut: View {
    let value: Double
    let color: Color

    var body: some View {
        ZStack {
            Circle().stroke(DatawatchColors.surface2, lineWidth: 7)
            Circle()
                .trim(from: 0, to: max(0, min(1, value)))
                .stroke(color, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                .rotationEffect(.degrees(-90))
            Text("\(Int((value * 100).rounded()))%")
                .font(DatawatchFonts.badge)
                .foregroundStyle(DatawatchColors.onSurface)
        }
        .accessibilityLabel("CPU \(Int((value * 100).rounded())) percent")
    }
}

private struct Sparkline: View {
    let values: [Double]
    let color: Color

    var body: some View {
        GeometryReader { geo in
            if values.count >= 2 {
                let lo = values.min() ?? 0
                let hi = values.max() ?? 1
                let span = max(hi - lo, 0.0001)
                Path { p in
                    for (i, v) in values.enumerated() {
                        let x = geo.size.width * CGFloat(i) / CGFloat(values.count - 1)
                        let y = geo.size.height * (1 - CGFloat((v - lo) / span))
                        if i == 0 { p.move(to: CGPoint(x: x, y: y)) } else { p.addLine(to: CGPoint(x: x, y: y)) }
                    }
                }
                .stroke(color, lineWidth: 1.5)
            }
        }
        .accessibilityHidden(true)
    }
}
