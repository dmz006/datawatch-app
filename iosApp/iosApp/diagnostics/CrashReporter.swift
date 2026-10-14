import Foundation
import Darwin
import MetricKit
import DatawatchShared

// On-device crash reporter (Android CrashReporter). No SDK, no network: reports
// are files in Application Support/crash-reports and only leave the device when
// the user picks Send in the next-launch prompt (docs/crash-reports.md).
//
// Sources, one file each, named <kind>-<launchId>.<ext>:
//   signal-*    SIGABRT/SIGSEGV/SIGBUS/SIGILL/SIGTRAP, async-signal-safe write
//   exception-* uncaught NSException
//   kotlin-*    uncaught Kotlin/Native exception (IosCrashReports hook)
//   metrickit-* MetricKit crash / hang diagnostic (JSON, delivered next launch)

// ── Signal handler state ─────────────────────────────────────────────────────
// Set once in installSignalHandlers() before any handler can run and never
// freed. The handler only reads these and calls write/fsync/signal/raise, all
// async-signal-safe; it never allocates or touches Swift objects.

private var dwSignalFD: Int32 = -1
private var dwSignalHeader: UnsafeMutablePointer<CChar>?
private var dwSignalHeaderLen: Int = 0
private let dwSignalSlots: Int = 32
private var dwSignalNames: UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>?
private var dwSignalNameLens: UnsafeMutablePointer<Int>?
private var dwPreviousExceptionHandler: (@convention(c) (NSException) -> Void)?

private func dwSignalHandler(_ sig: Int32) {
    if dwSignalFD >= 0 {
        if let header = dwSignalHeader {
            _ = write(dwSignalFD, header, dwSignalHeaderLen)
        }
        let slot = Int(sig)
        if slot >= 0, slot < dwSignalSlots, let names = dwSignalNames, let lens = dwSignalNameLens,
           let name = names[slot] {
            _ = write(dwSignalFD, name, lens[slot])
        }
        _ = fsync(dwSignalFD)
    }
    // Hand the signal back to the system so the process still terminates
    // (and the OS / MetricKit still record the crash).
    signal(sig, SIG_DFL)
    raise(sig)
}

/// A report waiting for the user's Send / View / Dismiss choice.
struct PendingCrashReport: Identifiable, Equatable {
    let url: URL
    var id: String { url.lastPathComponent }
}

final class CrashReporter: NSObject, ObservableObject, MXMetricManagerSubscriber {
    static let shared = CrashReporter()

    /// Pending reports, oldest first. Main-thread only.
    @Published private(set) var pending: [PendingCrashReport] = []

    private(set) var directory: URL?
    private(set) var launchId: String = ""
    private var installed = false

    private static let maxPending = 10
    private static let fatalSignals: [(Int32, String)] = [
        (SIGABRT, "SIGABRT"), (SIGSEGV, "SIGSEGV"), (SIGBUS, "SIGBUS"), (SIGILL, "SIGILL"), (SIGTRAP, "SIGTRAP"),
    ]

    /// Call once, as early as possible in launch.
    func install() {
        guard !installed else { return }
        installed = true
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("crash-reports", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        directory = dir
        launchId = String(Int(Date().timeIntervalSince1970 * 1000))

        // Clean up and list the previous launches' files before this launch
        // creates its own (empty) signal file.
        reloadPending()

        IosCrashReports.shared.install(directory: dir.path, launchId: launchId)
        dwPreviousExceptionHandler = NSGetUncaughtExceptionHandler()
        NSSetUncaughtExceptionHandler { exception in
            CrashReporter.writeException(exception)
            dwPreviousExceptionHandler?(exception)
        }
        installSignalHandlers(dir: dir)
        MXMetricManager.shared.add(self)
    }

    private func installSignalHandlers(dir: URL) {
        let header: String = IosCrashReports.shared.signalHeader()
        dwSignalHeader = strdup(header)
        dwSignalHeaderLen = dwSignalHeader.map { Int(strlen($0)) } ?? 0
        let names = UnsafeMutablePointer<UnsafeMutablePointer<CChar>?>.allocate(capacity: dwSignalSlots)
        names.initialize(repeating: nil, count: dwSignalSlots)
        let lens = UnsafeMutablePointer<Int>.allocate(capacity: dwSignalSlots)
        lens.initialize(repeating: 0, count: dwSignalSlots)
        for (sig, name) in Self.fatalSignals where Int(sig) < dwSignalSlots {
            let cName = strdup(name + "\n")
            names[Int(sig)] = cName
            lens[Int(sig)] = cName.map { Int(strlen($0)) } ?? 0
        }
        dwSignalNames = names
        dwSignalNameLens = lens
        // Pre-opened so the handler needs no path handling. Left empty unless a
        // signal arrives; empty files are deleted on the next launch.
        let path = dir.appendingPathComponent("signal-\(launchId).txt").path
        dwSignalFD = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0o600)
        for (sig, _) in Self.fatalSignals {
            signal(sig, dwSignalHandler)
        }
    }

    /// Uncaught NSException: not a signal context, so building the full report is fine.
    private static func writeException(_ exception: NSException) {
        let reporter = CrashReporter.shared
        guard let dir = reporter.directory else { return }
        let text = IosCrashReports.shared.buildExceptionReport(
            name: exception.name.rawValue,
            reason: exception.reason ?? "",
            callStack: exception.callStackSymbols.joined(separator: "\n")
        )
        let url = dir.appendingPathComponent("exception-\(reporter.launchId).txt")
        try? text.write(to: url, atomically: true, encoding: .utf8)
    }

    // MARK: MetricKit

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        guard let dir = directory else { return }
        var saved = false
        for payload in payloads {
            // Only crashes and hangs: CPU / disk-write diagnostics are not "closed unexpectedly".
            let crashes = payload.crashDiagnostics?.count ?? 0
            let hangs = payload.hangDiagnostics?.count ?? 0
            guard crashes + hangs > 0 else { continue }
            let url = dir.appendingPathComponent("metrickit-\(UUID().uuidString).json")
            if (try? payload.jsonRepresentation().write(to: url, options: .atomic)) != nil { saved = true }
        }
        if saved {
            DispatchQueue.main.async { self.reloadPending() }
        }
    }

    // MARK: pending reports

    /// Re-list the directory: drop empty signal files from earlier launches, drop
    /// a bare signal file when a richer report exists for the same launch, keep
    /// the newest few. This launch's files are never touched.
    func reloadPending() {
        guard let dir = directory else { return }
        let fm = FileManager.default
        let urls = (try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        let names = Set(urls.map { $0.lastPathComponent })
        var keep: [URL] = []
        for url in urls {
            let name = url.lastPathComponent
            if name.hasSuffix("-\(launchId).txt") { continue }
            if name.hasPrefix("signal-") {
                let launch = String(name.dropFirst("signal-".count))
                let empty = ((try? Data(contentsOf: url))?.isEmpty) ?? true
                if empty || names.contains("kotlin-" + launch) || names.contains("exception-" + launch) {
                    try? fm.removeItem(at: url)
                    continue
                }
            }
            keep.append(url)
        }
        keep.sort { $0.lastPathComponent < $1.lastPathComponent }
        while keep.count > Self.maxPending {
            try? fm.removeItem(at: keep.removeFirst())
        }
        pending = keep.map { PendingCrashReport(url: $0) }
    }

    /// Redacted report text for View / Send.
    func text(for report: PendingCrashReport) -> String {
        guard let data = try? Data(contentsOf: report.url),
              let raw = String(data: data, encoding: .utf8) else { return "" }
        if report.url.pathExtension == "json" {
            return IosCrashReports.shared.buildMetricKitReport(json: raw)
        }
        return IosCrashReports.shared.redact(text: raw)
    }

    /// Remove a report after Send or Dismiss.
    func delete(_ report: PendingCrashReport) {
        try? FileManager.default.removeItem(at: report.url)
        reloadPending()
    }

    /// The report as a .txt file for the share sheet / mail attachment.
    static func temporaryFile(for text: String) -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("datawatch-report.txt")
        return (try? text.write(to: url, atomically: true, encoding: .utf8)) != nil ? url : nil
    }
}
