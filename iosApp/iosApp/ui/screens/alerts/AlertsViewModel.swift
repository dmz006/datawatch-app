import SwiftUI
import Combine
import DatawatchShared

// ── ViewModel ─────────────────────────────────────────────────────────────

/// Alerts tab state.
///
/// Performance (iOS perf pass 2026-10-14): `/api/alerts` polls every 15 s while
/// the tab is visible; the session list used to classify alerts comes from the
/// shared WS data (`IosSessionsLive`) instead of a multi-MB `/api/sessions` per
/// poll, with a REST fallback at most every 60 s while the WS isn't live.
/// `filteredAlerts`, `groups` and the tab / chip counts are computed once per
/// input change (not per view body), and alert → session lookups use an index.
@MainActor
final class AlertsViewModel: ObservableObject {
    @Published private(set) var alerts: [DatawatchShared.Alert] = []
    /// Live session list — classifies alerts into Active / Historical (PWA: by session liveness).
    @Published private(set) var sessions: [DwSession] = []
    @Published var unreadCount: Int = 0 {
        didSet { publishBadge() }
    }
    /// D61a watched-badge filter: once any session is watched, the tab / bell badge
    /// counts only watched sessions' unread alerts (Android watchedAlertCount).
    private var watchSink: AnyCancellable? = nil

    func publishBadge() {
        let badge: Int = LocalSessionPrefs.badgeCount(
            serverUnread: unreadCount, alerts: alerts, sessions: sessions, profileId: allMode ? nil : profile?.id
        )
        UserDefaults.standard.set(badge, forKey: "dw.alert.badge")
    }
    @Published var isLoading: Bool = false
    /// Alerts are on screen and a user-visible refresh (tab re-opened, server
    /// switch, ↻ / pull-to-refresh) is in flight — drives the "Refreshing…" pill.
    @Published private(set) var isRefreshing: Bool = false
    @Published var error: String? = nil
    @Published var filterText: String = "" { didSet { persistTabState(); recompute() } }
    @Published var severityFilter: AlertSeverityFilter = .all { didSet { persistTabState(); recompute() } }
    @Published var sortMode: SortMode = .session { didSet { persistTabState() } }
    @Published var selectedTab: AlertTab = .active {
        didSet {
            UserDefaults.standard.set(selectedTab.rawValue, forKey: "dw.alerts.activeTab")
            loadTabState()
            recompute()
        }
    }

    // ── Derived (recomputed when alerts / sessions / tab / filters change) ──

    /// Alerts for the selected tab after severity + text filtering, newest first.
    @Published private(set) var filteredAlerts: [DatawatchShared.Alert] = []
    /// By-session cards for the selected tab / filters.
    @Published private(set) var groups: [AlertGroup] = []
    /// Alerts for the currently selected tab (before severity/text filtering).
    private(set) var tabAlerts: [DatawatchShared.Alert] = []
    private var tabCounts: [AlertTab: Int] = [:]
    private var chipCounts: [AlertSeverityFilter: Int] = [:]
    /// Alert ids classified as prompts (PWA `catOf`).
    private var promptIds: Set<String> = []
    /// Session lookup index: short id and full id → sessions (in list order).
    private var sessionIndex: [String: [DwSession]] = [:]
    /// > 0 while several inputs change together — one recompute at the end.
    private var batchDepth = 0

    enum AlertTab: String, CaseIterable {
        case active    = "Active"
        case historical = "Historical"
        case system    = "System"
    }

    enum AlertSeverityFilter: String, CaseIterable {
        case all = "All"
        case prompt = "Prompt"
        case error = "Error"
        case warning = "Warn"
        case info = "Info"

        /// PWA `alert_chip_*` copy (rawValue stays the persisted key).
        var label: String {
            switch self {
            case .all: return L("all")
            case .prompt: return "🟡 " + L("prompts")
            case .error: return "🔴 " + L("errors")
            case .warning: return "🟠 " + L("warn")
            case .info: return "⚪ " + L("info")
            }
        }
    }

    /// PWA `setAlertsSort`: grouped by session (default) or flat chronological.
    enum SortMode: String { case session, chrono }

    /// One by-session card (PWA `renderSessionCard`). `session == nil` with `isSystem` = System card.
    struct AlertGroup: Identifiable {
        let id: String
        let session: DwSession?
        let isSystem: Bool
        let alerts: [DatawatchShared.Alert]
    }

    private var loadingTabState = false

    init() {
        if let raw = UserDefaults.standard.string(forKey: "dw.alerts.activeTab"),
           let tab = AlertTab(rawValue: raw) {
            selectedTab = tab
        }
        loadTabState()
        watchSink = LocalSessionPrefs.shared.$revision.dropFirst().sink { [weak self] _ in
            Task { @MainActor [weak self] in self?.publishBadge() }
        }
    }

    // ── Session lookup / classification ──────────────────────────────────

    func session(for alert: DatawatchShared.Alert) -> DwSession? {
        guard let sid = alert.sessionId, !sid.isEmpty, let candidates = sessionIndex[sid] else { return nil }
        let pid: String? = alertServer[alert.id]
        return candidates.first { pid == nil || $0.serverProfileId == pid }
    }

    private func rebuildSessionIndex() {
        var index: [String: [DwSession]] = [:]
        for s in sessions {
            let short: String = s.id
            let full: String = s.fullId
            index[short, default: []].append(s)
            if full != short { index[full, default: []].append(s) }
        }
        sessionIndex = index
    }

    // ── D2a all-servers aggregate (Android AlertsViewModel allServersMode) ──

    /// True when alerts from every enabled server are merged ("All" chip).
    var allMode: Bool { profiles.count > 1 }

    /// The server an alert came from (nil outside all-servers mode).
    func server(for alert: DatawatchShared.Alert) -> ServerProfile? {
        guard allMode, let pid = alertServer[alert.id] else { return nil }
        return profiles.first { $0.id == pid }
    }

    /// The profile that owns a session — used for links and quick replies.
    func owner(of session: DwSession) -> ServerProfile? {
        profiles.first { $0.id == session.serverProfileId } ?? profile
    }

    private func isDone(_ s: DwSession) -> Bool {
        s.state == .completed || s.state == .killed || s.state == .error
    }

    /// PWA: a session alert is Active only while its session is in the live list and not finished.
    private func isActive(_ alert: DatawatchShared.Alert) -> Bool {
        guard let s = session(for: alert) else { return false }
        return !isDone(s)
    }

    private func belongs(_ alert: DatawatchShared.Alert, to tab: AlertTab) -> Bool {
        let hasSession = !(alert.sessionId ?? "").isEmpty
        switch tab {
        case .system:     return !hasSession
        case .active:     return hasSession && isActive(alert)
        case .historical: return hasSession && !isActive(alert)
        }
    }

    /// PWA `catOf` (app.js renderAlertsView): an alert is a prompt when its
    /// session is `waiting_input` or its title mentions needs input / prompt /
    /// waiting; otherwise its level decides. Categories are exclusive.
    func isPrompt(_ a: DatawatchShared.Alert) -> Bool { promptIds.contains(a.id) }

    private func computeIsPrompt(_ a: DatawatchShared.Alert) -> Bool {
        if session(for: a)?.state == .waiting { return true }
        return a.title.range(of: #"\b(needs input|prompt|waiting)\b"#, options: [.regularExpression, .caseInsensitive]) != nil
    }

    func category(_ a: DatawatchShared.Alert) -> AlertSeverityFilter {
        if isPrompt(a) { return .prompt }
        switch a.severity {
        case .error: return .error
        case .warning: return .warning
        default: return .info
        }
    }

    // ── Derived-state recompute ──────────────────────────────────────────

    /// Apply several input changes with one recompute at the end.
    private func batch(_ apply: () -> Void) {
        batchDepth += 1
        apply()
        batchDepth -= 1
        recompute()
    }

    /// Rebuilds the per-tab lists, counts, filtered list and groups from the
    /// current inputs. Called on input changes only — views read the results.
    private func recompute() {
        guard batchDepth == 0 else { return }
        var prompts = Set<String>()
        var perTab: [AlertTab: [DatawatchShared.Alert]] = [:]
        for a in alerts {
            if computeIsPrompt(a) { prompts.insert(a.id) }
            let hasSession = !(a.sessionId ?? "").isEmpty
            let tab: AlertTab = !hasSession ? .system : (isActive(a) ? .active : .historical)
            perTab[tab, default: []].append(a)
        }
        promptIds = prompts
        var counts: [AlertTab: Int] = [:]
        for tab in AlertTab.allCases { counts[tab] = perTab[tab]?.count ?? 0 }
        tabCounts = counts

        let base: [DatawatchShared.Alert] = perTab[selectedTab] ?? []
        tabAlerts = base
        var chips: [AlertSeverityFilter: Int] = [.all: base.count]
        for a in base { chips[category(a), default: 0] += 1 }
        chipCounts = chips

        var result = base
        if !filterText.isEmpty {
            let q = filterText.lowercased()
            result = result.filter {
                $0.title.lowercased().contains(q) ||
                $0.message.lowercased().contains(q)
            }
        }
        if severityFilter != .all {
            let wanted = severityFilter
            result = result.filter { category($0) == wanted }
        }
        let sorted: [DatawatchShared.Alert] = result
            .map { (a: $0, ms: $0.createdAt.toEpochMilliseconds()) }
            .sorted { $0.ms > $1.ms }
            .map { $0.a }
        filteredAlerts = sorted
        groups = buildGroups(sorted)
    }

    /// By-session cards: waiting → running → others (PWA stateRank), System card last.
    private func buildGroups(_ filtered: [DatawatchShared.Alert]) -> [AlertGroup] {
        var bySession: [String: [DatawatchShared.Alert]] = [:]
        var system: [DatawatchShared.Alert] = []
        for a in filtered {
            if let sid = a.sessionId, !sid.isEmpty {
                // All servers: two servers can share a session id — key by server too.
                let key: String = allMode ? "\(alertServer[a.id] ?? "")|\(sid)" : sid
                bySession[key, default: []].append(a)
            } else {
                system.append(a)
            }
        }
        func rank(_ g: AlertGroup) -> Int {
            switch g.session?.state {
            case .some(.waiting): return 0
            case .some(.running): return 1
            default: return 2
            }
        }
        var out: [AlertGroup] = bySession.map { key, list in
            let s: DwSession? = list.first.flatMap { session(for: $0) }
            return AlertGroup(id: key, session: s, isSystem: false, alerts: list)
        }
        out.sort { l, r in
            let lr = rank(l), rr = rank(r)
            if lr != rr { return lr < rr }
            return (l.alerts.first?.createdAt.toEpochMilliseconds() ?? 0) > (r.alerts.first?.createdAt.toEpochMilliseconds() ?? 0)
        }
        if !system.isEmpty { out.append(AlertGroup(id: "__system__", session: nil, isSystem: true, alerts: system)) }
        return out
    }

    /// Alert deep link: select the sub-tab holding `alertId` and clear chip +
    /// search. False while the alert hasn't loaded yet.
    func focus(alertId: String) -> Bool {
        guard let a = alerts.first(where: { $0.id == alertId }) else { return false }
        let tab: AlertTab = AlertTab.allCases.first(where: { belongs(a, to: $0) }) ?? .active
        batch {
            if selectedTab != tab { selectedTab = tab }
            filterText = ""
            severityFilter = .all
        }
        return true
    }

    /// Id of the by-session card that contains `alertId` (current tab/filters).
    func groupId(containing alertId: String) -> String? {
        groups.first(where: { g in g.alerts.contains(where: { $0.id == alertId }) })?.id
    }

    func tabCount(for tab: AlertTab) -> Int { tabCounts[tab] ?? 0 }

    func chipCount(for filter: AlertSeverityFilter) -> Int { chipCounts[filter] ?? 0 }

    // ── Per-tab persisted filter state (PWA cs_alerts_tab_state_<tab>) ──

    private func tabKey(_ tab: AlertTab) -> String { "dw.alerts.tab.\(tab.rawValue)" }

    private func loadTabState() {
        loadingTabState = true
        defer { loadingTabState = false }
        let d = UserDefaults.standard.dictionary(forKey: tabKey(selectedTab)) ?? [:]
        batch {
            filterText = d["search"] as? String ?? ""
            severityFilter = AlertSeverityFilter(rawValue: d["chip"] as? String ?? "") ?? .all
            sortMode = SortMode(rawValue: d["sort"] as? String ?? "") ?? .session
        }
    }

    private func persistTabState() {
        guard !loadingTabState else { return }
        UserDefaults.standard.set(
            ["search": filterText, "chip": severityFilter.rawValue, "sort": sortMode.rawValue],
            forKey: tabKey(selectedTab)
        )
    }

    // ── Input setters (skip unchanged content) ───────────────────────────

    private func setAlerts(_ list: [DatawatchShared.Alert], owners: [String: String]) {
        let alertsChanged: Bool = list != alerts
        let ownersChanged: Bool = owners != alertServer
        guard alertsChanged || ownersChanged else { return }
        batch {
            if alertsChanged { alerts = list }
            if ownersChanged { alertServer = owners }
        }
    }

    /// Per-profile session lists (WS cache or REST fallback), merged in profile order.
    private var sessionsByProfile: [String: [DwSession]] = [:]

    private func setSessions(_ list: [DwSession], for profileId: String) {
        if sessionsByProfile[profileId] == list { return }
        sessionsByProfile[profileId] = list
        let merged: [DwSession] = profiles.flatMap { sessionsByProfile[$0.id] ?? [] }
        guard merged != sessions else { return }
        sessions = merged
        rebuildSessionIndex()
        recompute()
        publishBadge()
    }

    // ── Loading ──────────────────────────────────────────────────────────

    /// Primary profile (the single active server, or the first in "All").
    private(set) var profile: ServerProfile?
    /// Every server being shown — one normally, every enabled one in "All" (D2a).
    private(set) var profiles: [ServerProfile] = []
    /// alert id → server profile id (alert ids are server-random hex, unique across servers).
    @Published private(set) var alertServer: [String: String] = [:]
    private var pollTask: Task<Void, Never>? = nil
    private var sessionSubs: [IosSubscription] = []
    private var inFlight = false
    private static let pollInterval: Duration = .seconds(15)
    /// REST session fallback cadence while the shared WS isn't delivering.
    private static let sessionsRestInterval: TimeInterval = 60
    private var lastRestSessions: [String: Date] = [:]

    func load(from newProfiles: [ServerProfile]) {
        guard newProfiles.map({ $0.id }) != profiles.map({ $0.id }) else { return }
        let polling: Bool = pollTask != nil
        stopPolling()
        profiles = newProfiles
        profile = newProfiles.first
        sessionsByProfile = sessionsByProfile.filter { entry in newProfiles.contains(where: { $0.id == entry.key }) }
        batch {
            alerts = []
            alertServer = [:]
        }
        if profile != nil {
            refresh()
            if polling { startPolling() }
        } else {
            batch {
                alerts = []
                sessions = []
            }
            rebuildSessionIndex()
            unreadCount = 0
            error = nil
        }
    }

    /// onAppear: load when the server changed, else refetch now and resume the
    /// poll `onDisappear` stopped (`load(from:)` alone returned early for the
    /// same server, so a revisited tab never polled again).
    func appear(with newProfiles: [ServerProfile]) {
        if newProfiles.map({ $0.id }) != profiles.map({ $0.id }) {
            load(from: newProfiles)
            startPolling()
            return
        }
        guard profile != nil else { return }
        refresh()
        startPolling()
    }

    /// Sequential loop: the next fetch is scheduled only after the previous one
    /// completes, so a slow server can't stack requests (the Android v1.23.112
    /// pile-up). Visibility-gated by the view's onAppear / onDisappear. While
    /// running, each shown server's session list streams in from the shared WS.
    func startPolling() {
        stopPolling()
        guard profile != nil else { return }
        for p in profiles {
            let pid: String = p.id
            sessionSubs.append(IosSessionsLive.shared.subscribe(profile: p) { [weak self] list in
                Task { @MainActor [weak self] in self?.setSessions(list, for: pid) }
            })
        }
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: Self.pollInterval)
                guard let self, !Task.isCancelled else { return }
                await self.refreshAsync()
            }
        }
    }

    func stopPolling() {
        pollTask?.cancel()
        pollTask = nil
        sessionSubs.forEach { $0.cancel() }
        sessionSubs = []
    }

    /// User-visible refresh (appear, server switch, ↻): shows the loader on first
    /// load, the "Refreshing…" pill over cached alerts otherwise.
    func refresh() {
        Task { await userRefreshAsync() }
    }

    /// Pull-to-refresh / ↻ / after a quick reply.
    func userRefreshAsync() async {
        if alerts.isEmpty { isLoading = true } else { isRefreshing = true }
        await refreshAsync()
        isRefreshing = false
    }

    /// Sessions over REST only when the shared WS has nothing live for [p], at
    /// most every 60 s (the list is multi-MB on busy servers).
    private func needsRestSessions(_ p: ServerProfile) -> Bool {
        if IosSessionsLive.shared.isLive(profileId: p.id) { return false }
        if let last = lastRestSessions[p.id], Date().timeIntervalSince(last) < Self.sessionsRestInterval { return false }
        return true
    }

    func refreshAsync() async {
        if allMode {
            await refreshAllAsync()
            return
        }
        guard let profile, !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        async let alertsResult = ServiceLocatorAsync.listAlerts(profile: profile)
        var live: [DwSession]? = nil
        if needsRestSessions(profile) {
            lastRestSessions[profile.id] = Date()
            live = try? await ServiceLocatorAsync.listSessions(profile: profile)
        }
        let fetched: Result<(alerts: [DatawatchShared.Alert], unreadCount: Int), Error>
        do {
            fetched = .success(try await alertsResult)
        } catch {
            fetched = .failure(error)
        }
        // #236.6/7: the server changed while this ran — drop the stale result and
        // fetch the new server now (its own refresh() hit the in-flight guard).
        if self.profile?.id != profile.id || allMode {
            Task { [weak self] in await self?.refreshAsync() }
            return
        }
        if let live { setSessions(live, for: profile.id) }
        do {
            let result = try fetched.get()
            setAlerts(result.alerts, owners: [:])
            if unreadCount != result.unreadCount { unreadCount = result.unreadCount }
            if error != nil { error = nil }
            // D49a (PWA renderAlertsView): opening the page acknowledges everything.
            if unreadCount > 0 && !alerts.isEmpty { acknowledgeAll(profile) }
        } catch {
            self.error = error.localizedDescription
        }
        publishBadge()
        if isLoading { isLoading = false }
    }

    /// D2a: fetch every enabled server in turn (sequential, like Sessions) and
    /// merge newest-first; an unreachable server is named in the error line.
    private func refreshAllAsync() async {
        guard !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        let targets: [ServerProfile] = profiles
        var merged: [DatawatchShared.Alert] = []
        var owners: [String: String] = [:]
        var restSessions: [String: [DwSession]] = [:]
        var failures: [String] = []
        for p in targets {
            do {
                let result = try await ServiceLocatorAsync.listAlerts(profile: p)
                for a in result.alerts { owners[a.id] = p.id }
                merged.append(contentsOf: result.alerts)
                if result.unreadCount > 0 && !result.alerts.isEmpty { acknowledgeAll(p) }
            } catch {
                failures.append("\(p.displayName): \(error.localizedDescription)")
            }
            if needsRestSessions(p) {
                lastRestSessions[p.id] = Date()
                if let live = try? await ServiceLocatorAsync.listSessions(profile: p) { restSessions[p.id] = live }
            }
        }
        guard targets.map({ $0.id }) == profiles.map({ $0.id }) else { return }
        let sorted: [DatawatchShared.Alert] = merged
            .map { (a: $0, ms: $0.createdAt.toEpochMilliseconds()) }
            .sorted { $0.ms > $1.ms }
            .map { $0.a }
        for (pid, list) in restSessions { setSessions(list, for: pid) }
        setAlerts(sorted, owners: owners)
        // Opening the page acknowledged each server's alerts (D49a).
        if unreadCount != 0 { unreadCount = 0 }
        let newError: String? = failures.isEmpty ? nil : L("Some servers unreachable: ") + failures.prefix(2).joined(separator: "; ")
        if newError != error { error = newError }
        publishBadge()
        if isLoading { isLoading = false }
    }

    private func acknowledgeAll(_ profile: ServerProfile) {
        unreadCount = 0
        IosServiceLocator.shared.markAllAlertsRead(profile: profile, onSuccess: {}, onError: { _ in })
    }

    /// Dismiss all alerts — D48a: deletes on the server like the PWA ✕.
    func dismissAll() {
        for p in profiles {
            let pid: String = p.id
            IosServiceLocator.shared.deleteAllAlerts(
                profile: p,
                onSuccess: { [weak self] in
                    DispatchQueue.main.async {
                        guard let self else { return }
                        self.unreadCount = 0
                        let kept = self.alerts.filter { self.alertServer[$0.id] != nil && self.alertServer[$0.id] != pid }
                        self.setAlerts(kept, owners: self.alertServer)
                    }
                },
                onError: { _ in }
            )
        }
    }
}
