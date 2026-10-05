import SwiftUI
import Foundation

/// Severity of a dock entry — the PWA `showToast` types.
enum DockLevel: String {
    case info, success, warning, error
}

/// One alert-dock message (PWA `_alertDock.alerts` entry). `count` is the ×N
/// coalesce counter. `fromServerAlert` marks entries created from a live WS
/// `alert` frame (D51a) — the server unread badge already counts those, so the
/// header pill does not count them twice (same rule as Android).
struct DockEntry: Identifiable, Equatable {
    let id: Int
    var ts: Date
    let level: DockLevel
    var message: String
    let family: String
    var count: Int
    let fromServerAlert: Bool
}

/// App-wide alert-dock state (PWA alpha.29 #271; Android `AlertDockChannel`).
///
/// - D41a: transient in-app messages ("toasts") go through `post` into the dock
///   and bump the header pill instead of floating over the screen.
/// - D47a: 🔕 mutes the dock for this app run (`mute`); tapping the pill while
///   muted un-mutes and opens it (PWA `toggleAlertDock`).
/// - D51a: live WS `alert` frames are posted here too (`LiveAlertFeed`).
/// - Same-family messages within 60 s coalesce into one entry with ×N; max 100.
///
/// Deliberate difference from the PWA (same as Android): an app error that is
/// not a server alert is never dropped by mute and opens the dock, so a failed
/// action is never silent.
@MainActor
final class AlertDock: ObservableObject {
    static let shared = AlertDock()

    @Published private(set) var entries: [DockEntry] = []
    @Published private(set) var open = false
    @Published private(set) var muted = false
    /// Cards whose long message the user expanded (PWA `_expandedCards`).
    @Published var expandedCards: Set<Int> = []

    private static let maxEntries = 100
    private static let coalesceWindow: TimeInterval = 60
    private var nextId = 0

    private init() {}

    /// Pill tap (PWA `toggleAlertDock`).
    func toggle() {
        if muted {
            muted = false
            open = true
            return
        }
        open.toggle()
    }

    func close() { open = false }

    /// ✕ — clear entries and close (PWA `dismissAlertDock`).
    func dismiss() {
        entries = []
        expandedCards = []
        open = false
    }

    /// 🔕 — mute for this app run (PWA `muteAlertDock`).
    func mute() {
        muted = true
        dismiss()
    }

    /// Drop one card (PWA `dismissAlertCard`).
    func remove(_ id: Int) {
        entries.removeAll { $0.id == id }
        expandedCards.remove(id)
        if entries.isEmpty { open = false }
    }

    /// Replacement for a transient toast (main actor). From a background
    /// callback use `AlertDock.notify(...)`.
    func post(_ message: String, level: DockLevel = .info, fromServerAlert: Bool = false) {
        let text = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        let appError = level == .error && !fromServerAlert
        if muted && !appError { return }
        // PWA: a "Connected" success clears earlier disconnect entries and is not kept.
        if level == .success && text.range(of: #"\bconnect(ed|ion)\b"#, options: [.regularExpression, .caseInsensitive]) != nil {
            entries.removeAll {
                $0.message.range(of: #"\b(dis(connected|connect)|reconnect)"#, options: [.regularExpression, .caseInsensitive]) != nil
            }
            return
        }
        let family = Self.familyOf(text)
        let now = Date()
        if var head = entries.first,
           head.family == family, head.level == level, head.fromServerAlert == fromServerAlert,
           now.timeIntervalSince(head.ts) < Self.coalesceWindow {
            head.count += 1
            head.ts = now
            head.message = text
            entries[0] = head
        } else {
            let entry = DockEntry(
                id: nextId, ts: now, level: level, message: text,
                family: family, count: 1, fromServerAlert: fromServerAlert
            )
            nextId += 1
            entries.insert(entry, at: 0)
            if entries.count > Self.maxEntries { entries.removeLast(entries.count - Self.maxEntries) }
        }
        if appError { open = true }
    }

    /// Thread-safe convenience for callbacks that arrive off the main thread.
    nonisolated static func notify(_ message: String, level: DockLevel = .info) {
        Task { @MainActor in AlertDock.shared.post(message, level: level) }
    }

    /// Total ×N of all entries (PWA pill/header total).
    var total: Int { entries.reduce(0) { $0 + $1.count } }

    /// Pill contribution on top of the server unread badge: entries the server
    /// badge does not already count.
    var localCount: Int {
        entries.filter { !$0.fromServerAlert }.reduce(0) { $0 + $1.count }
    }

    /// Per-type counts for the dock header chips, in a stable order.
    var countsByLevel: [(DockLevel, Int)] {
        let order: [DockLevel] = [.error, .warning, .info, .success]
        return order.compactMap { lvl in
            let n = entries.filter { $0.level == lvl }.reduce(0) { $0 + $1.count }
            return n > 0 ? (lvl, n) : nil
        }
    }

    /// PWA family key: strip a leading `[prefix] `, keep text before the first — : or ,.
    static func familyOf(_ message: String) -> String {
        var s = message
        if let r = s.range(of: #"^\[[^\]]*\]\s*"#, options: .regularExpression) {
            s.removeSubrange(r)
        }
        let seps: Set<Character> = ["—", ":", ","]
        if let idx = s.firstIndex(where: { seps.contains($0) }) {
            let head = s[s.startIndex..<idx].trimmingCharacters(in: .whitespaces)
            if !head.isEmpty { return head }
        }
        return s
    }
}
