import Foundation
import UserNotifications
import DatawatchShared

/// Interim "needs input" local notifications sourced from the live session list
/// while the app runs (parity D87b — APNs is not enabled on the App ID yet).
/// Swift port of Android `SessionStateWatcher`.
///
/// Data source: the shared WS session data (`IosSessionsLive` — the same socket
/// as the alert feed, no extra connection), applied as each list arrives and
/// re-evaluated every 15 s for the settle window. REST `/api/sessions` (multi-MB
/// on busy servers) is only a slow fallback — at most every 120 s per server and
/// only while no WS data has arrived recently.
///
/// 1. Settle window — a session must stay `waiting_input` for 45 s (server
///    `detection.alert_settle` default) before it notifies, so running ↔ waiting
///    flaps don't alert.
/// 2. Prompt dedup — one notification per distinct prompt within a waiting
///    episode; a new prompt re-fires.
/// 3. Cold-start safety — the first list per server only seeds state.
/// 4. Blip safety — a waiting session that briefly vanishes keeps its episode.
///
/// Only runs in the foreground (iOS suspends the app otherwise); the session
/// currently open in detail is never notified. Muted sessions (D62a) are
/// skipped. Leaving waiting removes the delivered notification.
@MainActor
final class LocalAlertWatcher {
    static let shared = LocalAlertWatcher()

    static let settle: TimeInterval = 45
    /// Re-evaluation cadence for the settle window (no network when WS is live).
    private static let evalInterval: Duration = .seconds(15)
    /// REST fallback cadence / WS staleness threshold.
    private static let restFallback: TimeInterval = 120

    private struct Episode {
        var firstSeen: Date
        var notifiedPrompt: String?
    }

    /// Per profile: known states (by short id) and waiting episodes.
    private var known: [String: [String: SessionState]] = [:]
    private var episodes: [String: [String: Episode]] = [:]
    private var profiles: [ServerProfile] = []
    private var task: Task<Void, Never>? = nil
    private var running = false
    /// Per profile: shared-WS session subscriptions.
    private var subs: [String: IosSubscription] = [:]
    /// Per profile: last session list (WS or REST) and when it arrived.
    private var lastLists: [String: [DwSession]] = [:]
    private var lastListAt: [String: Date] = [:]
    private var lastRest: [String: Date] = [:]
    /// Notification permission, refreshed on every evaluation tick.
    private var postAllowed = false

    /// Short id of the session open in detail (set by SessionDetailView).
    var foregroundSessionId: String? = nil

    /// PWA `pendingNeedsInputPopup` (Android `SessionStateWatcher.pendingNeedsInput`):
    /// a session that entered waiting_input while not open in detail, keyed
    /// "<profileId>/<shortId>", replayed once into the alert dock on next open.
    private var pendingNeedsInput: [String: (prompt: String, at: Date)] = [:]
    static let pendingTTL: TimeInterval = 60 * 60

    /// One-shot: returns (and clears) the stashed prompt if it is ≤ 1 h old.
    func consumePendingNeedsInput(profileId: String, sessionId: String) -> String? {
        let key = profileId + "/" + sessionId
        guard let p = pendingNeedsInput.removeValue(forKey: key) else { return nil }
        return Date().timeIntervalSince(p.at) <= Self.pendingTTL ? p.prompt : nil
    }

    private init() {}

    func update(profiles newProfiles: [ServerProfile]) {
        let enabled = newProfiles.filter { $0.enabled }
        let ids = Set(enabled.map { $0.id })
        known = known.filter { ids.contains($0.key) }
        episodes = episodes.filter { ids.contains($0.key) }
        pendingNeedsInput = pendingNeedsInput.filter { entry in
            ids.contains(where: { entry.key.hasPrefix($0 + "/") })
        }
        lastLists = lastLists.filter { ids.contains($0.key) }
        lastListAt = lastListAt.filter { ids.contains($0.key) }
        lastRest = lastRest.filter { ids.contains($0.key) }
        profiles = enabled
        if running { reconcileSubscriptions() }
    }

    func start() {
        guard task == nil else { return }
        running = true
        reconcileSubscriptions()
        task = Task { [weak self] in
            while !Task.isCancelled {
                await self?.evaluate()
                try? await Task.sleep(for: Self.evalInterval)
            }
        }
    }

    func stop() {
        running = false
        task?.cancel()
        task = nil
        subs.values.forEach { $0.cancel() }
        subs = [:]
    }

    /// One shared-WS session subscription per enabled server.
    private func reconcileSubscriptions() {
        let wanted = Set(profiles.map { $0.id })
        for (id, sub) in subs where !wanted.contains(id) {
            sub.cancel()
            subs[id] = nil
        }
        for profile in profiles where subs[profile.id] == nil {
            let pid: String = profile.id
            subs[pid] = IosSessionsLive.shared.subscribe(profile: profile) { list in
                Task { @MainActor in LocalAlertWatcher.shared.receive(list, profileId: pid) }
            }
        }
    }

    /// A pushed list: remember it and apply it right away.
    private func receive(_ list: [DwSession], profileId: String) {
        guard running, let profile = profiles.first(where: { $0.id == profileId }) else { return }
        lastLists[profileId] = list
        // A subscription replays the cached list, which may predate a background
        // spell — date it by when the WS delivered it, not when it reached us.
        let ageMs: Int64 = IosSessionsLive.shared.ageMillis(profileId: profileId)
        lastListAt[profileId] = Date().addingTimeInterval(-Double(max(0, ageMs)) / 1000)
        apply(list, profile: profile, post: postAllowed)
    }

    /// The user replied — the next distinct prompt may notify again.
    func onReplied(sessionId: String) {
        for pid in episodes.keys { episodes[pid]?[sessionId] = nil }
    }

    /// Every 15 s: re-apply the current list (settle window) when it is live or
    /// recent; otherwise fall back to REST at most every 120 s.
    private func evaluate() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        postAllowed = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional
        let now = Date()
        for profile in profiles {
            let pid: String = profile.id
            let wsLive: Bool = IosSessionsLive.shared.isLive(profileId: pid)
            let recent: Bool = lastListAt[pid].map { now.timeIntervalSince($0) < Self.restFallback } ?? false
            if let cached = lastLists[pid], wsLive || recent {
                apply(cached, profile: profile, post: postAllowed)
                continue
            }
            if let last = lastRest[pid], now.timeIntervalSince(last) < Self.restFallback { continue }
            lastRest[pid] = now
            guard let list = try? await ServiceLocatorAsync.listSessions(profile: profile) else { continue }
            guard running, profiles.contains(where: { $0.id == pid }) else { return }
            lastLists[pid] = list
            lastListAt[pid] = Date()
            apply(list, profile: profile, post: postAllowed)
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
                if prev != .waiting && foregroundSessionId != s.id {
                    pendingNeedsInput[pid + "/" + s.id] = (prompt: prompt, at: now)
                }
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
