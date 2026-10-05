import Foundation
import DatawatchShared

/// Live WS `alert` frames → header badge + alert-dock entry (parity D51a; PWA
/// `handleAlert`, Android `AppRoot.LiveAlertFeed`).
///
/// One global `/ws` per enabled server while the app is in the foreground
/// (`IosAlertFeed.subscribe`). Several sockets can carry the same frame, so
/// pushes are de-duplicated by alert id. Muted sessions (D62a swipe-to-mute)
/// don't create dock entries; the badge respects the D61a watched-sessions
/// filter the same way AlertsView computes it.
@MainActor
final class LiveAlertFeed {
    static let shared = LiveAlertFeed()

    private var profiles: [ServerProfile] = []
    private var subs: [String: IosSubscription] = [:]
    private var running = false
    private var seen: [String] = []
    private var seenSet: Set<String> = []

    private init() {}

    func update(profiles newProfiles: [ServerProfile]) {
        profiles = newProfiles.filter { $0.enabled }
        if running { reconcile() }
    }

    func start() {
        running = true
        reconcile()
    }

    func stop() {
        running = false
        subs.values.forEach { $0.cancel() }
        subs = [:]
    }

    private func reconcile() {
        let wanted = Set(profiles.map { $0.id })
        for (id, sub) in subs where !wanted.contains(id) {
            sub.cancel()
            subs[id] = nil
        }
        for profile in profiles where subs[profile.id] == nil {
            let pid = profile.id
            subs[pid] = IosAlertFeed.shared.subscribe(profile: profile) { push in
                Task { @MainActor in LiveAlertFeed.shared.handle(push, profileId: pid) }
            }
        }
    }

    private func handle(_ push: AlertPush, profileId: String) {
        if let id = push.id {
            if seenSet.contains(id) { return }
            seen.append(id)
            seenSet.insert(id)
            if seen.count > 200 {
                let old = seen.removeFirst()
                seenSet.remove(old)
            }
        }
        let sid: String = push.sessionId ?? ""
        let shortId: String = sid.components(separatedBy: "-").last ?? sid

        // Badge (PWA state.alertUnread++), honouring the watched-sessions filter.
        let watched = LocalSessionPrefs.ids(.watchedSessions, profileId: profileId)
        let counts: Bool = watched.isEmpty || watched.contains(sid) || watched.contains(shortId)
        if counts {
            let key = "dw.alert.badge"
            UserDefaults.standard.set(UserDefaults.standard.integer(forKey: key) + 1, forKey: key)
        }

        // Dock entry (PWA showToast → pushToAlertDock), skipped for muted sessions.
        if !sid.isEmpty {
            let muted = LocalSessionPrefs.ids(.mutedSessions, profileId: profileId)
            if muted.contains(sid) || muted.contains(shortId) { return }
        }
        let title: String = push.title.count > 60 ? String(push.title.prefix(57)) + "…" : push.title
        let lvl = push.level.lowercased()
        let level: DockLevel = (lvl == "error" || lvl == "warn" || lvl == "warning") ? .error : .info
        AlertDock.shared.post(title, level: level, fromServerAlert: true)
    }
}
