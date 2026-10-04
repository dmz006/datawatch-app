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

    @Published private(set) var sessions: [Session] = []
    @Published private(set) var isLoading = false
    @Published private(set) var error: String? = nil
    @Published private(set) var activeProfile: ServerProfile? = nil

    // ── Private ───────────────────────────────────────────────────────────

    private var pollTask: Task<Void, Never>? = nil
    private var wsSubscription: IosSubscription? = nil
    private var inFlight = false
    private var polling = false
    private static let restFallbackInterval: Duration = .seconds(30)

    init() {}

    // ── Profile wiring ────────────────────────────────────────────────────

    /// Called by the view whenever the profiles array changes.
    /// Uses the first profile as the "active" server.
    func update(profiles: [ServerProfile]) {
        let newActive = profiles.first
        guard newActive?.id != activeProfile?.id else { return }
        activeProfile = newActive
        if newActive != nil {
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
        guard let profile = activeProfile else { return }

        wsSubscription = IosServiceLocator.shared.subscribeGlobalStream(
            profile: profile,
            onStats: { _ in },
            onSessions: { [weak self] list in
                Task { @MainActor [weak self] in
                    self?.sessions = list
                    self?.error = nil
                    self?.isLoading = false
                }
            }
        )

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
        wsSubscription?.cancel()
        wsSubscription = nil
    }

    private func refreshAsync() async {
        guard let profile = activeProfile, !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        if sessions.isEmpty { isLoading = true }
        do {
            sessions = try await ServiceLocatorAsync.listSessions(profile: profile)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }
}
