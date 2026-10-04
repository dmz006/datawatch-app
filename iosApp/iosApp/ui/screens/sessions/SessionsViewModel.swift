import Foundation
import DatawatchShared

/// ViewModel for the Sessions screen.
///
/// WS-first: while the screen is visible a global `/ws` stream delivers full
/// session-list pushes; REST `listSessions` runs once on start and every 30 s as
/// a fallback. Fetches are sequential (one in flight at a time) — overlapping
/// timer-driven fetches were the request pile-up that lagged Android (v1.23.112).
@MainActor
final class SessionsViewModel: ObservableObject {

    // ── Published state ───────────────────────────────────────────────────

    @Published private(set) var sessions: [DwSession] = []
    @Published private(set) var isLoading = false
    @Published private(set) var error: String? = nil
    @Published private(set) var activeProfile: ServerProfile? = nil

    // ── Private ───────────────────────────────────────────────────────────

    /// All profiles being shown (one normally; every enabled one in "All servers").
    @Published private(set) var profiles: [ServerProfile] = []
    private var byProfile: [String: [DwSession]] = [:]

    private var pollTask: Task<Void, Never>? = nil
    private var wsSubscriptions: [IosSubscription] = []
    private var diffSubscriptions: [IosSubscription] = []
    private var inFlight = false
    private var polling = false
    private static let restFallbackInterval: Duration = .seconds(30)

    init() {}

    // ── Profile wiring ────────────────────────────────────────────────────

    /// Called by the view with the profile(s) to show: the active server, or
    /// every enabled server in "All servers" mode (D2a / PWA picker "All").
    func update(profiles newProfiles: [ServerProfile]) {
        let newIds = newProfiles.map { $0.id }
        guard newIds != profiles.map({ $0.id }) else { return }
        profiles = newProfiles
        byProfile = byProfile.filter { newIds.contains($0.key) }
        activeProfile = newProfiles.first
        if !newProfiles.isEmpty {
            if polling {
                startPolling(restart: true)
            } else {
                refresh()
            }
        } else {
            stopPolling()
            sessions = []
            error = nil
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /// One-shot refresh (pull-to-refresh, profile change).
    func refresh() {
        Task { await refreshAsync() }
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
                        self?.setSessions(list, for: pid)
                        self?.error = nil
                        self?.isLoading = false
                    }
                }
            ))
            diffSubscriptions.append(IosServiceLocator.shared.subscribeSessionDiffs(profile: profile) { [weak self] updated in
                Task { @MainActor [weak self] in self?.upsert(updated, for: pid) }
            })
        }

        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                await self.refreshAsync()
                try? await Task.sleep(for: Self.restFallbackInterval)
            }
        }
    }

    func stopPolling() {
        tearDown()
        polling = false
    }

    // ── Internals ─────────────────────────────────────────────────────────

    private func tearDown() {
        pollTask?.cancel()
        pollTask = nil
        wsSubscriptions.forEach { $0.cancel() }
        wsSubscriptions = []
        diffSubscriptions.forEach { $0.cancel() }
        diffSubscriptions = []
    }

    private func setSessions(_ list: [DwSession], for profileId: String) {
        byProfile[profileId] = list
        sessions = profiles.flatMap { byProfile[$0.id] ?? [] }
    }

    /// Apply a single-row `session_state` diff without refetching the list.
    private func upsert(_ updated: DwSession, for profileId: String) {
        var list = byProfile[profileId] ?? []
        if let i = list.firstIndex(where: { $0.id == updated.id }) {
            list[i] = updated
        } else {
            list.append(updated)
        }
        setSessions(list, for: profileId)
    }

    private func refreshAsync() async {
        guard !profiles.isEmpty, !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        if sessions.isEmpty { isLoading = true }
        var firstError: String? = nil
        for profile in profiles {
            do {
                let list = try await ServiceLocatorAsync.listSessions(profile: profile)
                setSessions(list, for: profile.id)
            } catch {
                if firstError == nil { firstError = error.localizedDescription }
            }
        }
        self.error = firstError
        isLoading = false
    }
}
