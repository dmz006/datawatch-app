import DatawatchShared
import Foundation
import SwiftUI
import WidgetKit

// Data for the home-screen widgets (BL403). All fetching goes through the shared
// Kotlin transport (`IosSurfaces`), which reads the widget configuration the app
// published to the shared Keychain and picks the server like Android's widgets.

/// `IosSurfaces.STATUS_*` values.
enum WidgetStatus {
    static let ok = "ok"
    static let noServers = "no_servers"
    static let offline = "offline"
    static let locked = "locked"
}

func WL(_ key: String) -> String {
    NSLocalizedString(key, comment: "")
}

/// Header line: server name, or "offline · name" like Android.
func widgetHeader(status: String, serverName: String) -> String {
    switch status {
    case WidgetStatus.noServers: return WL("No servers")
    case WidgetStatus.offline: return String(format: WL("offline · %@"), serverName)
    case WidgetStatus.locked: return String(format: WL("locked · %@"), serverName)
    default: return serverName
    }
}

// MARK: Sessions

struct SessionsEntry: TimelineEntry, Codable {
    var date: Date
    var status: String
    var serverName: String
    var running: Int
    var waiting: Int
    var total: Int
    /// True when the counts are the last ones fetched (device locked now).
    var stale: Bool = false

    static let placeholder = SessionsEntry(
        date: Date(), status: WidgetStatus.ok, serverName: "datawatch", running: 2, waiting: 1, total: 5
    )

    var hasCounts: Bool { status == WidgetStatus.ok || stale }
}

// MARK: Monitor

/// Swift copy of the shared `WidgetMonitorSnapshot` (Codable, for the locked-device cache).
struct MonitorData: Codable {
    var cpuPct: Int
    var cpuText: String
    var memPct: Int
    var memText: String
    var diskPct: Int
    var diskText: String
    var hasSwap: Bool
    var swapPct: Int
    var swapText: String
    var hasGpu: Bool
    var gpuPct: Int
    var gpuText: String
    var ebpfActive: Bool
    var netRxText: String
    var netTxText: String
    var daemonText: String
    var uptimeText: String
    var sessionsText: String

    init(_ s: WidgetMonitorSnapshot) {
        cpuPct = Int(s.cpuPct)
        cpuText = s.cpuText
        memPct = Int(s.memPct)
        memText = s.memText
        diskPct = Int(s.diskPct)
        diskText = s.diskText
        hasSwap = s.hasSwap
        swapPct = Int(s.swapPct)
        swapText = s.swapText
        hasGpu = s.hasGpu
        gpuPct = Int(s.gpuPct)
        gpuText = s.gpuText
        ebpfActive = s.ebpfActive
        netRxText = s.netRxText
        netTxText = s.netTxText
        daemonText = s.daemonText
        uptimeText = s.uptimeText
        sessionsText = s.sessionsText
    }

    static let placeholder = MonitorData(
        cpuPct: 25, cpuText: "2.00", memPct: 40, memText: "6.4 GB / 16.0 GB",
        diskPct: 55, diskText: "220 GB / 400 GB", hasSwap: false, swapPct: -1, swapText: "—",
        hasGpu: false, gpuPct: -1, gpuText: "", ebpfActive: false,
        netRxText: "↓ 1.2 GB", netTxText: "↑ 300 MB", daemonText: "52 MB RSS · 40g · 12fd",
        uptimeText: "3d4h", sessionsText: "5 · 2r · 1w"
    )

    init(
        cpuPct: Int, cpuText: String, memPct: Int, memText: String, diskPct: Int, diskText: String,
        hasSwap: Bool, swapPct: Int, swapText: String, hasGpu: Bool, gpuPct: Int, gpuText: String,
        ebpfActive: Bool, netRxText: String, netTxText: String, daemonText: String,
        uptimeText: String, sessionsText: String
    ) {
        self.cpuPct = cpuPct
        self.cpuText = cpuText
        self.memPct = memPct
        self.memText = memText
        self.diskPct = diskPct
        self.diskText = diskText
        self.hasSwap = hasSwap
        self.swapPct = swapPct
        self.swapText = swapText
        self.hasGpu = hasGpu
        self.gpuPct = gpuPct
        self.gpuText = gpuText
        self.ebpfActive = ebpfActive
        self.netRxText = netRxText
        self.netTxText = netTxText
        self.daemonText = daemonText
        self.uptimeText = uptimeText
        self.sessionsText = sessionsText
    }
}

struct MonitorEntry: TimelineEntry, Codable {
    var date: Date
    var status: String
    var serverName: String
    var data: MonitorData?
    var stale: Bool = false

    static let placeholder = MonitorEntry(
        date: Date(), status: WidgetStatus.ok, serverName: "datawatch", data: .placeholder
    )
}

// MARK: Loading + locked-device cache

enum WidgetData {
    /// Android `updatePeriodMillis = 1800000` (30 min). iOS may stretch it to fit its budget.
    static let refreshInterval: TimeInterval = 30 * 60

    static func nextRefresh() -> Date {
        Date().addingTimeInterval(refreshInterval)
    }

    static func loadSessions(_ done: @escaping (SessionsEntry) -> Void) {
        IosSurfaces.shared.loadWidgetSessions { r in
            var entry = SessionsEntry(
                date: Date(), status: r.status, serverName: r.serverName,
                running: Int(r.running), waiting: Int(r.waiting), total: Int(r.total)
            )
            if r.status == WidgetStatus.ok {
                Cache.save(entry, key: Cache.sessionsKey)
            } else if r.status == WidgetStatus.locked,
                      var cached: SessionsEntry = Cache.load(Cache.sessionsKey),
                      cached.serverName == r.serverName {
                // Locked phone: keep showing the last counts (lock-screen widget).
                cached.date = Date()
                cached.status = r.status
                cached.stale = true
                entry = cached
            }
            done(entry)
        }
    }

    static func loadMonitor(_ done: @escaping (MonitorEntry) -> Void) {
        IosSurfaces.shared.loadWidgetMonitor { r in
            var entry = MonitorEntry(
                date: Date(), status: r.status, serverName: r.serverName,
                data: r.snapshot.map { MonitorData($0) }
            )
            if r.status == WidgetStatus.ok {
                Cache.save(entry, key: Cache.monitorKey)
            } else if r.status == WidgetStatus.locked,
                      var cached: MonitorEntry = Cache.load(Cache.monitorKey),
                      cached.serverName == r.serverName {
                cached.date = Date()
                cached.status = r.status
                cached.stale = true
                entry = cached
            }
            done(entry)
        }
    }

    /// Last good numbers, in the widget extension's own container (no tokens,
    /// nothing the widget doesn't already show on screen).
    enum Cache {
        static let sessionsKey = "dw.widget.sessions.last"
        static let monitorKey = "dw.widget.monitor.last"

        static func save<T: Encodable>(_ value: T, key: String) {
            if let data = try? JSONEncoder().encode(value) {
                UserDefaults.standard.set(data, forKey: key)
            }
        }

        static func load<T: Decodable>(_ key: String) -> T? {
            guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
            return try? JSONDecoder().decode(T.self, from: data)
        }
    }
}

// MARK: Palette (PWA dark tokens, same values as the app's DatawatchColors)

enum WidgetPalette {
    static let background = Color(hex: 0x0F1117)
    static let surface = Color(hex: 0x1A1D27)
    static let primary = Color(hex: 0x7C3AED)
    static let success = Color(hex: 0x10B981)
    static let warning = Color(hex: 0xF59E0B)
    static let error = Color(hex: 0xEF4444)
    static let waiting = Color(hex: 0x3B82F6)
    static let text = Color(hex: 0xE2E8F0)
    static let muted = Color(hex: 0x94A3B8)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0
        )
    }
}

extension View {
    /// iOS 17+ requires `containerBackground`; iOS 16 uses a plain background.
    @ViewBuilder
    func widgetBackground(_ color: Color) -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(color, for: .widget)
        } else {
            background(color)
        }
    }

    /// iOS 17+ adds the system content margins; iOS 16 needs its own padding.
    @ViewBuilder
    func widgetContentPadding() -> some View {
        if #available(iOS 17.0, *) {
            self
        } else {
            padding()
        }
    }
}
