import Foundation
import Combine
import DatawatchShared

/// One visible Sessions-list row: a stored, stable `id` for `ForEach` (no
/// Kotlin getter per diff) plus the session it shows.
struct SessionRow: Identifiable {
    /// "<serverProfileId>|<fullId>" — unique across servers in "All servers".
    let id: String
    let fullId: String
    let session: DwSession
}

/// Every view-side input that shapes the visible list (PWA renderSessionsView
/// pool → state chip → watch filter → text filter → manual order).
struct SessionListFilter: Equatable {
    var showHistory: Bool = false
    var stateChip: String = "all"
    var watchFilter: Bool = false
    var filterText: String = ""
    var orderJSON: String = "[]"
    var treeView: Bool = false
}

/// Counts derived from the full session list (chips, badges, subtitle).
struct SessionListSummary: Equatable {
    var historyCount: Int = 0
    var watchedCount: Int = 0
    var running: Int = 0
    var waiting: Int = 0
    /// SessionStateStyle.key → count ("all" = every session).
    var stateCounts: [String: Int] = [:]
    var backendTypes: [String] = []
    var backendCounts: [String: Int] = [:]
}

/// ViewModel for the Sessions screen.
///
/// WS-first: while the screen is visible the shared global `/ws` delivers the
/// session list (`IosSessionsLive`: cached copy first, `session_state` diffs
/// merged, unchanged lists skipped); REST `listSessions` runs once on start and
/// every 30 s as a fallback, skipped in single-server mode while the WS is live.
/// Fetches are sequential (one in flight at a time) — overlapping timer-driven
/// fetches were the request pile-up that lagged Android (v1.23.112).
///
/// Performance (iOS perf pass 2026-10-14): the visible rows, tree rows and
/// counts are computed here once per data / filter change, off the main thread,
/// instead of several times per view body; unchanged sessions keep their object
/// identity so their cards aren't re-rendered.
@MainActor
final class SessionsViewModel: ObservableObject {

    // ── Published state ───────────────────────────────────────────────────

    @Published private(set) var sessions: [DwSession] = []
    @Published private(set) var isLoading = false
    /// Sessions are on screen and a user-visible refresh is in flight.
    @Published private(set) var isRefreshing = false
    @Published private(set) var error: String? = nil
    @Published private(set) var activeProfile: ServerProfile? = nil
    /// PWA `🕒 N` pending-schedules badge (single-server view only).
    @Published private(set) var pendingSchedules: [IosScheduleRow] = []
    /// D43a: profile ids whose `session.summarizer.enabled` is true (PWA
    /// `state._summarizerEnabled`, read from /api/config) — gates `🤖 Summary`.
    @Published private(set) var summarizerProfiles: Set<String> = []

    /// Filtered + ordered rows for the list.
    @Published private(set) var rows: [SessionRow] = []
    /// BL348 tree rows (only built while the tree view is on).
    @Published private(set) var treeRows: [SessionTree.Row] = []
    @Published private(set) var summary = SessionListSummary()

    // ── Private ───────────────────────────────────────────────────────────

    /// All profiles being shown (one normally; every enabled one in "All servers").
    @Published private(set) var profiles: [ServerProfile] = []
    private var byProfile: [String: [DwSession]] = [:]

    private var pollTask: Task<Void, Never>? = nil
    private var wsSubscriptions: [IosSubscription] = []
    private var inFlight = false
    private var polling = false
    private static let restFallbackInterval: Duration = .seconds(30)

    /// Current view filter (set by the view on change).
    private var filter = SessionListFilter()
    /// Decoded manual order (full id → position), re-decoded only when the JSON changes.
    private var orderIndex: [String: Int] = [:]
    private var orderSource: String = ""
    /// Bumped per recompute request; stale background results are dropped.
    private var generation = 0
    /// Cached D61a / D62a id sets per "<kind>|<profileId>", valid for one prefs revision.
    private var localSets: [String: Set<String>] = [:]
    private var localSetsRevision: Int = -1
    private var prefsSink: AnyCancellable? = nil

    init() {
        prefsSink = LocalSessionPrefs.shared.$revision.dropFirst().sink { [weak self] _ in
            Task { @MainActor [weak self] in self?.recompute() }
        }
    }

    // ── Profile wiring ────────────────────────────────────────────────────

    /// Called by the view with the profile(s) to show: the active server, or
    /// every enabled server in "All servers" mode (D2a / PWA picker "All").
    func update(profiles newProfiles: [ServerProfile]) {
        let newIds = newProfiles.map { $0.id }
        guard newIds != profiles.map({ $0.id }) else { return }
        profiles = newProfiles
        byProfile = byProfile.filter { newIds.contains($0.key) }
        activeProfile = newProfiles.first
        summarizerProfiles = summarizerProfiles.filter { newIds.contains($0) }
        loadSummarizerConfig()
        if !newProfiles.isEmpty {
            publishMerged()
            if polling {
                startPolling(restart: true)
            } else {
                refresh()
            }
        } else {
            stopPolling()
            sessions = []
            error = nil
            recompute()
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /// One-shot refresh (pull-to-refresh, profile change, after an action).
    func refresh() {
        Task { await userRefreshAsync() }
    }

    /// User-visible refresh: first-load loader when empty, "Refreshing…" pill otherwise.
    func userRefreshAsync() async {
        if !sessions.isEmpty { isRefreshing = true }
        await refreshAsync(force: true)
        isRefreshing = false
    }

    /// Visibility-gated: the view calls this on appear and `stopPolling` on disappear.
    func startPolling(restart: Bool = false) {
        if polling && !restart { return }
        tearDown()
        polling = true
        guard !profiles.isEmpty else { return }

        for profile in profiles {
            let pid = profile.id
            wsSubscriptions.append(IosServiceLocator.shared.subscribeGlobalStream(
                profile: profile,
                onStats: { _ in },
                onSessions: { [weak self] list in
                    Task { @MainActor [weak self] in
                        guard let self else { return }
                        self.setSessions(list, for: pid)
                        if self.error != nil { self.error = nil }
                        if self.isLoading { self.isLoading = false }
                    }
                }
            ))
        }

        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                // Not forced: a single server whose shared WS is live already
                // has its list (replayed from the cache) — skip the multi-MB GET.
                await self.refreshAsync(force: false)
                // Re-filter so the PWA "recently finished" window keeps moving.
                self.recompute()
                try? await Task.sleep(for: Self.restFallbackInterval)
            }
        }
    }

    func stopPolling() {
        tearDown()
        polling = false
    }

    // ── Filter + derived rows ─────────────────────────────────────────────

    /// The view's filter inputs changed.
    func setFilter(_ newFilter: SessionListFilter) {
        guard newFilter != filter else { return }
        filter = newFilter
        if filter.orderJSON != orderSource {
            orderSource = filter.orderJSON
            orderIndex = SessionListLogic.decodeOrder(orderSource)
        }
        recompute()
    }

    /// D61a / D62a id set for one profile (UserDefaults read once per prefs revision).
    func localSet(_ kind: LocalSessionPrefs.Kind, profileId: String) -> Set<String> {
        let revision: Int = LocalSessionPrefs.shared.revision
        if revision != localSetsRevision {
            localSets = [:]
            localSetsRevision = revision
        }
        let key: String = kind.rawValue + "|" + profileId
        if let cached = localSets[key] { return cached }
        let set = LocalSessionPrefs.ids(kind, profileId: profileId)
        localSets[key] = set
        return set
    }

    /// Every session in manual order, then most recent (drag / move up-down).
    func sortedByOrder(_ list: [DwSession]) -> [DwSession] {
        SessionListLogic.sortByOrder(list, order: orderIndex)
    }

    /// Recompute rows / tree rows / counts off the main thread, then publish
    /// (only what changed). Coalesced: a newer request drops an older result.
    private func recompute() {
        generation &+= 1
        let gen: Int = generation
        let input: [DwSession] = sessions
        let f: SessionListFilter = filter
        let order: [String: Int] = orderIndex
        var watched: [String: Set<String>] = [:]
        for p in profiles { watched[p.id] = localSet(.watchedSessions, profileId: p.id) }
        let fallbackPid: String? = activeProfile?.id
        let shownIds: Set<String> = Set(profiles.map { $0.id })
        let nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
        Task.detached(priority: .userInitiated) { [weak self] in
            let derived: SessionListDerived = SessionListLogic.derive(
                input, filter: f, order: order, watched: watched,
                shownIds: shownIds, fallbackPid: fallbackPid, nowMs: nowMs
            )
            await self?.publish(derived, generation: gen)
        }
    }

    private func publish(_ d: SessionListDerived, generation gen: Int) {
        guard gen == generation else { return }
        let sameRows: Bool = d.rows.count == rows.count
            && zip(d.rows, rows).allSatisfy { $0.id == $1.id && $0.session === $1.session }
        if !sameRows { rows = d.rows }
        let sameTree: Bool = d.treeRows.count == treeRows.count
            && zip(d.treeRows, treeRows).allSatisfy {
                $0.id == $1.id && $0.depth == $1.depth && $0.orphaned == $1.orphaned && $0.session === $1.session
            }
        if !sameTree { treeRows = d.treeRows }
        if d.summary != summary { summary = d.summary }
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private func tearDown() {
        pollTask?.cancel()
        pollTask = nil
        wsSubscriptions.forEach { $0.cancel() }
        wsSubscriptions = []
    }

    /// Store one server's list. Sessions whose content didn't change keep the
    /// previous object, so `@Published sessions` (and each card) only updates
    /// when something actually changed.
    private func setSessions(_ list: [DwSession], for profileId: String) {
        let previous: [DwSession] = byProfile[profileId] ?? []
        var oldByKey: [String: DwSession] = [:]
        for s in previous { oldByKey[s.fullId] = s }
        let canonical: [DwSession] = list.map { s in
            if let old = oldByKey[s.fullId], old == s { return old }
            return s
        }
        byProfile[profileId] = canonical
        // Real data arrived — a proxied remote's connection status is stale (PWA).
        IosFedConn.shared.reportData(profileId: profileId)
        publishMerged()
    }

    private func publishMerged() {
        let merged: [DwSession] = profiles.flatMap { byProfile[$0.id] ?? [] }
        let unchanged: Bool = merged.count == sessions.count && zip(merged, sessions).allSatisfy { $0 === $1 }
        guard !unchanged else { return }
        sessions = merged
        recompute()
    }

    /// REST fetch. `force` = first load / user refresh; the 30 s fallback poll
    /// skips a single server whose shared WS is live (it already pushes the list).
    private func refreshAsync(force: Bool) async {
        guard !profiles.isEmpty, !inFlight else { return }
        let targets: [ServerProfile] = profiles
        if !force && targets.count == 1 && IosSessionsLive.shared.isLive(profileId: targets[0].id) {
            loadPendingSchedules()
            return
        }
        inFlight = true
        defer { inFlight = false }
        if sessions.isEmpty { isLoading = true }
        var firstError: String? = nil
        for profile in targets {
            do {
                // "All servers" (several targets) also shows each server's remotes,
                // like Android's /api/federation/sessions fan-out (#234).
                let list = targets.count > 1
                    ? try await ServiceLocatorAsync.listFederationSessions(profile: profile)
                    : try await ServiceLocatorAsync.listSessions(profile: profile)
                setSessions(list, for: profile.id)
            } catch {
                if firstError == nil { firstError = error.localizedDescription }
            }
        }
        // #236.7: the server changed while this fetch ran (its refresh() was
        // skipped by the in-flight guard) — fetch the new one right away
        // instead of waiting for the next 30 s poll.
        guard targets.map({ $0.id }) == profiles.map({ $0.id }) else {
            Task { [weak self] in await self?.refreshAsync(force: true) }
            return
        }
        if self.error != firstError { self.error = firstError }
        isLoading = false
        loadPendingSchedules()
    }

    // ── Summarizer gate (PWA app.js:352 `cfg.session.summarizer.enabled`) ──

    func loadSummarizerConfig() {
        for profile in profiles {
            let pid = profile.id
            IosSettingsConfig.shared.load(
                profile: profile,
                onSuccess: { [weak self] map in
                    let on: Bool = map["session.summarizer.enabled"] == "true"
                    Task { @MainActor [weak self] in
                        guard let self else { return }
                        if on { self.summarizerProfiles.insert(pid) } else { self.summarizerProfiles.remove(pid) }
                    }
                },
                onError: { _ in }
            )
        }
    }

    // ── Pending schedules (PWA schedBadge / Android loadPendingSchedules) ──

    func loadPendingSchedules() {
        guard profiles.count == 1, let profile = profiles.first else {
            pendingSchedules = []
            return
        }
        let pid = profile.id
        IosObserver.shared.listSchedules(
            profile: profile,
            onSuccess: { [weak self] list in
                let pending: [IosScheduleRow] = list.filter { $0.pending }
                Task { @MainActor [weak self] in
                    guard let self, self.profiles.first?.id == pid else { return }
                    self.pendingSchedules = pending
                }
            },
            onError: { [weak self] _ in
                Task { @MainActor [weak self] in self?.pendingSchedules = [] }
            }
        )
    }

    /// Per-item cancel from the 🕒 dropdown; reports a failure message via `onError`.
    func cancelSchedule(_ id: String, onError: @escaping (String) -> Void) {
        guard let profile = profiles.first else { return }
        IosObserver.shared.deleteSchedules(profile: profile, ids: [id]) { [weak self] err in
            Task { @MainActor [weak self] in
                if let err { onError(err) }
                self?.loadPendingSchedules()
            }
        }
    }
}

/// Result of one Sessions-list derivation.
struct SessionListDerived {
    let rows: [SessionRow]
    let treeRows: [SessionTree.Row]
    let summary: SessionListSummary
}

/// Pure Sessions-list shaping (no actor isolation — runs off the main thread).
enum SessionListLogic {
    /// Pure: PWA pool → state chip → watch → text filter → manual order, plus counts.
    static func derive(
        _ all: [DwSession],
        filter f: SessionListFilter,
        order: [String: Int],
        watched: [String: Set<String>],
        shownIds: Set<String>,
        fallbackPid: String?,
        nowMs: Int64
    ) -> SessionListDerived {
        func isWatched(_ s: DwSession) -> Bool {
            let owner: String = s.serverProfileId
            let pid: String? = shownIds.contains(owner) ? owner : fallbackPid
            guard let pid, let set = watched[pid] else { return false }
            return set.contains(s.id)
        }

        var summary = SessionListSummary()
        var stateCounts: [String: Int] = ["all": all.count]
        var backendCounts: [String: Int] = [:]
        for s in all {
            let key: String = SessionStateStyle.key(s.state)
            stateCounts[key, default: 0] += 1
            if SessionStateStyle.isDone(s.state) { summary.historyCount += 1 }
            if s.state == .running || s.state == .rateLimited { summary.running += 1 }
            if s.state == .waiting { summary.waiting += 1 }
            if let b = s.backend, !b.isEmpty { backendCounts[b, default: 0] += 1 }
            if isWatched(s) { summary.watchedCount += 1 }
        }
        summary.stateCounts = stateCounts
        summary.backendCounts = backendCounts
        summary.backendTypes = backendCounts.keys.sorted()

        let cutoff: Int64 = nowMs - recentWindowMs
        var result: [DwSession] = f.showHistory ? all : all.filter { s in
            !SessionStateStyle.isDone(s.state) || s.lastActivityAt.toEpochMilliseconds() >= cutoff
        }
        if f.stateChip != "all" {
            let chip: String = f.stateChip
            result = result.filter { SessionStateStyle.key($0.state) == chip }
        }
        if f.watchFilter { result = result.filter { isWatched($0) } }
        if !f.filterText.isEmpty {
            let q = f.filterText.lowercased()
            result = result.filter { s in
                s.id.lowercased().contains(q) ||
                (s.name?.lowercased().contains(q) ?? false) ||
                (s.taskSummary?.lowercased().contains(q) ?? false) ||
                (s.backend?.lowercased().contains(q) ?? false) ||
                (s.llmRef?.lowercased().contains(q) ?? false) ||
                (s.computeNodeRef?.lowercased().contains(q) ?? false)
            }
        }
        let ordered: [DwSession] = sortByOrder(result, order: order)
        let rows: [SessionRow] = ordered.map { s in
            let full: String = s.fullId
            return SessionRow(id: s.serverProfileId + "|" + full, fullId: full, session: s)
        }
        let tree: [SessionTree.Row] = f.treeView ? SessionTree.flatten(ordered) : []
        return SessionListDerived(rows: rows, treeRows: tree, summary: summary)
    }

    /// PWA `recent_session_minutes` default.
    static let recentWindowMs: Int64 = 5 * 60 * 1000

    /// PWA sortSessionsByOrder: sessions in the saved order first, the rest by
    /// most recent activity. O(n log n) via the decoded order index.
    static func sortByOrder(_ list: [DwSession], order: [String: Int]) -> [DwSession] {
        var inOrder: [(pos: Int, s: DwSession)] = []
        var rest: [(ms: Int64, s: DwSession)] = []
        var placed = Set<String>()
        for s in list {
            let full: String = s.fullId
            if let pos = order[full], !placed.contains(full) {
                placed.insert(full)
                inOrder.append((pos, s))
            } else {
                rest.append((s.lastActivityAt.toEpochMilliseconds(), s))
            }
        }
        inOrder.sort { $0.pos < $1.pos }
        rest.sort { $0.ms > $1.ms }
        return inOrder.map { $0.s } + rest.map { $0.s }
    }

    /// PWA cs_session_order JSON → full id → first position.
    static func decodeOrder(_ json: String) -> [String: Int] {
        let ids: [String] = (try? JSONDecoder().decode([String].self, from: Data(json.utf8))) ?? []
        var index: [String: Int] = [:]
        for (i, id) in ids.enumerated() where index[id] == nil { index[id] = i }
        return index
    }
}
