import Foundation
import DatawatchShared

/// ViewModel for the Sessions screen.
///
/// Polls `IosServiceLocator.listSessions` on appear and every 10 seconds.
/// Observes `ServerProfileStore` via initialiser injection so it can react
/// to profile additions / deletions without holding an EnvironmentObject.
@MainActor
final class SessionsViewModel: ObservableObject {

    // ── Published state ───────────────────────────────────────────────────

    @Published private(set) var sessions: [Session] = []
    @Published private(set) var isLoading = false
    @Published private(set) var error: String? = nil
    @Published private(set) var activeProfile: ServerProfile? = nil

    // ── Private ───────────────────────────────────────────────────────────

    private var pollingTimer: Timer? = nil
    private static let pollInterval: TimeInterval = 5

    // ── Init ──────────────────────────────────────────────────────────────

    init() {}

    // ── Profile wiring ────────────────────────────────────────────────────

    /// Called by the view whenever the profiles array changes (via EnvironmentObject).
    /// Uses the first profile as the "active" server (profile picker comes later).
    func update(profiles: [ServerProfile]) {
        let newActive = profiles.first
        // Only refresh if the active profile identity changed.
        guard newActive?.id != activeProfile?.id else { return }
        activeProfile = newActive
        if newActive != nil {
            refresh()
        } else {
            sessions = []
            error = nil
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    func refresh() {
        guard let profile = activeProfile else { return }
        isLoading = true
        error = nil
        IosServiceLocator.shared.listSessions(
            profile: profile,
            onSuccess: { [weak self] list in
                DispatchQueue.main.async {
                    self?.sessions = list
                    self?.isLoading = false
                }
            },
            onError: { [weak self] message in
                DispatchQueue.main.async {
                    self?.error = message
                    self?.isLoading = false
                }
            }
        )
    }

    func startPolling() {
        guard pollingTimer == nil else { return }
        pollingTimer = Timer.scheduledTimer(
            withTimeInterval: Self.pollInterval,
            repeats: true
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.refresh()
            }
        }
    }

    func stopPolling() {
        pollingTimer?.invalidate()
        pollingTimer = nil
    }
}
