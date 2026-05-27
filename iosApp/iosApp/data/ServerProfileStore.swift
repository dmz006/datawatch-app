import Foundation
import DatawatchShared

/// Live-updating list of server profiles sourced from the shared SQLite DB.
///
/// Injected into the view hierarchy via `.environmentObject(profileStore)`.
/// All mutations (`save`, `delete`) run the connection probe before persisting.
@MainActor
final class ServerProfileStore: ObservableObject {
    @Published private(set) var profiles: [ServerProfile] = []
    @Published private(set) var isLoading = true

    private var collectionTask: Task<Void, Never>?

    init() {
        startCollecting()
    }

    deinit {
        collectionTask?.cancel()
    }

    // ── Mutations ─────────────────────────────────────────────────────────

    func save(
        profile: ServerProfile,
        token: String?,
        onSuccess: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        IosServiceLocator.shared.saveProfile(
            profile: profile,
            tokenValue: token,
            onSuccess: { DispatchQueue.main.async { onSuccess() } },
            onError: { msg in DispatchQueue.main.async { onError(msg) } }
        )
    }

    func delete(
        profile: ServerProfile,
        onSuccess: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        IosServiceLocator.shared.deleteProfile(
            profile: profile,
            onSuccess: { DispatchQueue.main.async { onSuccess() } },
            onError: { msg in DispatchQueue.main.async { onError(msg) } }
        )
    }

    func probe(
        profile: ServerProfile,
        token: String?,
        onSuccess: @escaping () -> Void,
        onError: @escaping (String) -> Void
    ) {
        IosServiceLocator.shared.probeProfile(
            profile: profile,
            tokenValue: token,
            onSuccess: { DispatchQueue.main.async { onSuccess() } },
            onError: { msg in DispatchQueue.main.async { onError(msg) } }
        )
    }

    // ── Private ───────────────────────────────────────────────────────────

    private func startCollecting() {
        collectionTask = Task { [weak self] in
            let flow = IosServiceLocator.shared.profilesFlow()
            let stream: AsyncThrowingStream<NSArray, Error> = FlowAdapter.stream(from: flow)
            self?.isLoading = false
            do {
                for try await array in stream {
                    let typed = array.compactMap { $0 as? ServerProfile }
                    await MainActor.run { self?.profiles = typed }
                }
            } catch {
                // Flow ended — not an error in normal operation.
            }
        }
    }
}
