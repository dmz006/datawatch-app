import SwiftUI
import WidgetKit

/// Monitor widget — Android `MonitorWidget`: CPU load, memory, disk, swap (when the
/// host has swap), GPU (when it has one), network, daemon footprint, session counts
/// and uptime from `/api/stats` of the active server. Tap opens the app. Large size,
/// like Android's 3×4 widget.
struct MonitorWidget: Widget {
    static let kind = "com.dmzs.datawatchclient.widgets.monitor"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: MonitorProvider()) { entry in
            MonitorWidgetView(entry: entry)
        }
        .configurationDisplayName("datawatch Monitor")
        .description("CPU load, memory and session counts from the active datawatch server.")
        .supportedFamilies([.systemLarge])
    }
}

struct MonitorProvider: TimelineProvider {
    func placeholder(in context: Context) -> MonitorEntry {
        .placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (MonitorEntry) -> Void) {
        if context.isPreview {
            completion(.placeholder)
            return
        }
        WidgetData.loadMonitor(completion)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MonitorEntry>) -> Void) {
        WidgetData.loadMonitor { entry in
            completion(Timeline(entries: [entry], policy: .after(WidgetData.nextRefresh())))
        }
    }
}

struct MonitorWidgetView: View {
    let entry: MonitorEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Monitor")
                    .font(.headline)
                    .foregroundColor(WidgetPalette.text)
                Spacer()
                ServerHeader(text: widgetHeader(status: entry.status, serverName: entry.serverName))
            }
            if let d = entry.data {
                bar("CPU", d.cpuText, d.cpuPct)
                bar("Memory", d.memText, d.memPct)
                bar("Disk", d.diskText, d.diskPct)
                if d.hasSwap { bar("Swap", d.swapText, d.swapPct) }
                if d.hasGpu { bar("GPU", d.gpuText, d.gpuPct) }
                row(LocalizedStringKey(d.ebpfActive ? "Net (eBPF)" : "Net"), "\(d.netRxText)  \(d.netTxText)")
                if !d.daemonText.isEmpty { row("Daemon", d.daemonText) }
                Spacer(minLength: 0)
                HStack {
                    Text(d.sessionsText)
                    Spacer()
                    Text(d.uptimeText)
                }
                .font(.caption)
                .foregroundColor(WidgetPalette.muted)
            } else {
                Spacer()
                Text("—")
                    .font(.largeTitle)
                    .foregroundColor(WidgetPalette.muted)
                    .frame(maxWidth: .infinity)
                Spacer()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .widgetContentPadding()
        .widgetBackground(WidgetPalette.background)
    }

    private func bar(_ label: LocalizedStringKey, _ value: String, _ pct: Int) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            row(label, value)
            // Android draws an empty bar when the value is unknown (-1).
            ProgressView(value: Double(max(pct, 0)), total: 100)
                .tint(tone(pct))
        }
    }

    private func row(_ label: LocalizedStringKey, _ value: String) -> some View {
        HStack {
            Text(label).foregroundColor(WidgetPalette.muted)
            Spacer()
            Text(value)
                .foregroundColor(WidgetPalette.text)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .font(.caption)
    }

    private func tone(_ pct: Int) -> Color {
        if pct >= 90 { return WidgetPalette.error }
        if pct >= 70 { return WidgetPalette.warning }
        return WidgetPalette.primary
    }
}

struct MonitorWidget_Previews: PreviewProvider {
    static var previews: some View {
        MonitorWidgetView(entry: .placeholder)
            .previewContext(WidgetPreviewContext(family: .systemLarge))
    }
}
