import Foundation
import UserNotifications
import DatawatchShared

/// Interim "needs input" local notifications sourced from polling while the
/// app runs (parity D87b — APNs is not enabled on the App ID yet). Swift port
/// of Android `SessionStateWatcher`:
///
/// 1. Settle window — a session must stay `waiting_input` for 45 s (server
///    `detection.alert_settle` default) before it notifies, so running ↔ waiting
///    flaps don't alert.
/// 2. Prompt dedup — one notification per distinct prompt within a waiting
///    episode; a new prompt re-fires.
/// 3. Cold-start safety — the first poll per server only seeds state.
/// 4. Blip safety — a waiting session that briefly vanishes keeps its episode.
///
/// Only runs in the foreground (iOS suspends the app otherwise); the session
/// currently open in detail is never notified. Muted sessions (D62a) are
/// skipped. Leaving waiting removes the delivered notification.
@MainActor
final class LocalAlertWatcher {
    static let shared = LocalAlertWatcher()

    static let settle: TimeInterval = 45
    private static let pollInterval: Duration = .seconds(15)

    private struct Episode {
        var firstSeen: Date
        var notifiedPrompt: String?
    }

    /// Per profile: known states (by short id) and waiting episodes.
    private var known: [String: [String: SessionState]] = [:]
    private var episodes: [String: [String: Episode]] = [:]
    private var profiles: [ServerProfile] = []
    private var task: Task<Void, Never>? = nil

    /// Short id of the session open in detail (set by SessionDetailView).
    var foregroundSessionId: String? = nil

    private init() {}

    func update(profiles newProfiles: [ServerProfile]) {
        let enabled = newProfiles.filter { $0.enabled }
        let ids = Set(enabled.map { $0.id })
        known = known.filter { ids.contains($0.key) }
        episodes = episodes.filter { ids.contains($0.key) }
        profiles = enabled
    }

    func start() {
        guard task == nil else { return }
        task = Task { [weak self] in
            while !Task.isCancelled {
                await self?.pollOnce()
                try? await Task.sleep(for: Self.pollInterval)
            }
        }
    }

    func stop() {
        task?.cancel()
        task = nil
    }

    /// The user replied — the next distinct prompt may notify again.
    func onReplied(sessionId: String) {
        for pid in episodes.keys { episodes[pid]?[sessionId] = nil }
    }

    private func pollOnce() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        let allowed = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
        for profile in profiles {
            guard let list = try? await ServiceLocatorAsync.listSessions(profile: profile) else { continue }
            apply(list, profile: profile, post: allowed)
        }
    }

    private func apply(_ sessions: [DwSession], profile: ServerProfile, post: Bool) {
        let pid = profile.id
        let now = Date()
        guard var states = known[pid] else {
            // Cold start: seed; already-waiting sessions count as notified.
            var seedStates: [String: SessionState] = [:]
            var seedEpisodes: [String: Episode] = [:]
            for s in sessions {
                seedStates[s.id] = s.state
                if s.state == .waiting {
                    seedEpisodes[s.id] = Episode(firstSeen: now.addingTimeInterval(-Self.settle), notifiedPrompt: Self.prompt(for: s))
                }
            }
            known[pid] = seedStates
            episodes[pid] = seedEpisodes
            return
        }
        var eps = episodes[pid] ?? [:]
        let muted = LocalSessionPrefs.ids(.mutedSessions, profileId: pid)
        var seen = Set<String>()
        for s in sessions {
            seen.insert(s.id)
            let prev = states[s.id]
            states[s.id] = s.state
            if s.state == .waiting {
                var ep = eps[s.id] ?? Episode(firstSeen: now, notifiedPrompt: nil)
                let prompt = Self.prompt(for: s)
                let settled: Bool = now.timeIntervalSince(ep.firstSeen) >= Self.settle
                if settled && ep.notifiedPrompt != prompt {
                    ep.notifiedPrompt = prompt
                    let quiet: Bool = foregroundSessionId == s.id || muted.contains(s.id)
                    if post && !quiet { notify(s, prompt: prompt, profile: profile) }
                }
                eps[s.id] = ep
            } else if prev == .waiting {
                eps[s.id] = nil
                UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: [Self.identifier(pid, s.id)])
            }
        }
        for id in states.keys where !seen.contains(id) {
            if states[id] != .waiting { eps[id] = nil }
            states[id] = nil
        }
        known[pid] = states
        episodes[pid] = eps
    }

    private func notify(_ s: DwSession, prompt: String, profile: ServerProfile) {
        let content = UNMutableNotificationContent()
        let name: String = (s.name?.isEmpty == false ? s.name : nil) ?? (s.taskSummary?.isEmpty == false ? s.taskSummary : nil) ?? s.id
        content.title = name
        content.subtitle = L("Waiting for your input")
        content.body = prompt
        content.sound = .default
        content.threadIdentifier = "dw.input." + profile.id
        content.userInfo = ["type": "input_needed", "session_id": s.id, "profile_id": profile.id]
        let req = UNNotificationRequest(identifier: Self.identifier(profile.id, s.id), content: content, trigger: nil)
        UNUserNotificationCenter.current().add(req) { _ in }
    }

    private static func identifier(_ profileId: String, _ sessionId: String) -> String {
        "dw.input.\(profileId).\(sessionId)"
    }

    /// Android `promptFor`: first non-blank prompt line, else last prompt /
    /// summary / response, capped at 200 chars.
    static func prompt(for s: DwSession) -> String {
        if let ctx = s.promptContext,
           let line = ctx.split(separator: "\n").first(where: { !$0.trimmingCharacters(in: .whitespaces).isEmpty }) {
            return String(line.prefix(200))
        }
        let fallbacks: [String?] = [s.lastPrompt, s.lastSummaryLong, s.lastResponse]
        for f in fallbacks {
            if let f, !f.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return String(f.prefix(200)) }
        }
        return L("Waiting for your input")
    }
}
