import Foundation
import DatawatchShared

/// Per-server-profile local id sets (parity D61a / D62a; Android
/// `WatchedSessionsStore`, `WatchedAutomataStore`, `SessionRepository.setMuted`):
///
/// - watched sessions — once any session is watched, the alert badge counts only
///   those sessions' unread alerts (empty set = badge counts everything);
/// - watched automata — 🔔 toggle on the PRD card;
/// - muted sessions — swipe-to-mute on the session card (🔇 indicator). APNs
///   alerts are rendered by the system, so suppressing a muted session's pushes
///   needs a Notification Service Extension (not in the target yet).
///
/// Plain UserDefaults string arrays keyed `<prefix><profileId>` — only non-secret ids.
@MainActor
final class LocalSessionPrefs: ObservableObject {
    static let shared = LocalSessionPrefs()

    enum Kind: String {
        case watchedSessions = "dw.watched_sessions."
        case watchedAutomata = "dw.watched_automata."
        case mutedSessions = "dw.muted_sessions."
    }

    /// Bumped on every change so views / view models can re-read.
    @Published private(set) var revision: Int = 0

    private init() {}

    nonisolated static func ids(_ kind: Kind, profileId: String) -> Set<String> {
        Set(UserDefaults.standard.stringArray(forKey: kind.rawValue + profileId) ?? [])
    }

    func ids(_ kind: Kind, profileId: String) -> Set<String> {
        Self.ids(kind, profileId: profileId)
    }

    func contains(_ kind: Kind, profileId: String, id: String) -> Bool {
        Self.ids(kind, profileId: profileId).contains(id)
    }

    func toggle(_ kind: Kind, profileId: String, id: String) {
        var set = Self.ids(kind, profileId: profileId)
        if set.contains(id) { set.remove(id) } else { set.insert(id) }
        UserDefaults.standard.set(Array(set).sorted(), forKey: kind.rawValue + profileId)
        revision += 1
    }

    // ── Watched-badge filter (Android AlertsViewModel watchedAlertCount) ─────

    /// Badge value: the server unread count while nothing is watched; otherwise the
    /// unread alerts that belong to a watched session.
    static func badgeCount(
        serverUnread: Int,
        alerts: [DatawatchShared.Alert],
        sessions: [DwSession],
        profileId: String?
    ) -> Int {
        guard let profileId else { return serverUnread }
        let watched = ids(.watchedSessions, profileId: profileId)
        if watched.isEmpty { return serverUnread }
        var count = 0
        for a in alerts where !a.read {
            guard let sid = a.sessionId, !sid.isEmpty else { continue }
            if watched.contains(sid) { count += 1; continue }
            if let s = sessions.first(where: { $0.fullId == sid || $0.id == sid }), watched.contains(s.id) {
                count += 1
            }
        }
        return count
    }
}
