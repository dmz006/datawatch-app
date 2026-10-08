import Foundation
import UIKit
import DatawatchShared

/// Live-updating list of server profiles sourced from the shared SQLite DB.
///
/// Injected into the view hierarchy via `.environmentObject(profileStore)`.
/// All mutations (`save`, `delete`) run the connection probe before persisting.
@MainActor
final class ServerProfileStore: ObservableObject {
    @Published private(set) var profiles: [ServerProfile] = []
    @Published private(set) var isLoading = true
    /// D2a: one app-wide active server (persisted), chosen from the PWA-style
    /// "Server:" picker bar. Falls back to the first enabled profile.
    @Published private(set) var activeProfileId: String? = UserDefaults.standard.string(forKey: "dw.active_profile_id")

    /// #234: remote servers configured on each real server, reached through its
    /// `/api/proxy/<name>` (virtual profiles: parent token + TLS, never persisted).
    /// `profiles` / `enabledProfiles` stay real-only — push, widgets, live alert
    /// feeds and the "All" fan-out iterate those.
    @Published private(set) var proxied: [ServerProfile] = []

    /// PWA `server_picker_loading` (#236.1): true while a server's remote list
    /// is fetched for the first time — pickers show "Loading servers…".
    @Published private(set) var proxiedLoading = false

    /// #236.2 / #235: connection status of the active proxied remote (the
    /// shared probe: "Connecting to X…" → "Loading sessions from X…" → nil, or
    /// an error). Read it through `fedStatus(for:)`.
    @Published private(set) var fedConnState: IosFedConnState? = nil

    /// The status to show for the screen's server, or nil (real server, "All
    /// servers", or real data already arrived).
    func fedStatus(for profileId: String?) -> IosFedConnState? {
        guard !isAllServers, let st = fedConnState, let id = profileId, st.profileId == id else { return nil }
        return st
    }

    /// Feed the resolved active server to the shared connection-status probe.
    private func publishActiveToFedConn() {
        IosFedConn.shared.onActiveProfile(profile: isAllServers ? nil : activeProfile)
    }

    var enabledProfiles: [ServerProfile] { profiles.filter { $0.enabled } }

    /// Picker rows: each real enabled server followed by its remotes.
    var pickerProfiles: [ServerProfile] { IosProxiedServers.shared.pickerRows(real: enabledProfiles) }

    /// PWA picker "All" chip — aggregated sessions across every enabled server.
    static let allServersId = "__all__"
    var isAllServers: Bool { activeProfileId == Self.allServersId && enabledProfiles.count > 1 }

    var activeProfile: ServerProfile? {
        if let id = activeProfileId, IosProxiedServers.shared.isProxied(id: id),
           let p = IosProxiedServers.shared.resolveActive(real: profiles, activeId: id) {
            return p // the remote, or its parent when the remote vanished
        }
        if let id = activeProfileId, let p = profiles.first(where: { $0.id == id && $0.enabled }) { return p }
        return enabledProfiles.first ?? profiles.first
    }

    func selectActive(_ id: String) {
        activeProfileId = id
        UserDefaults.standard.set(id, forKey: "dw.active_profile_id")
        // Widgets stay on real servers: a proxied remote publishes its parent.
        WidgetSync.publish(activeProfileId: IosProxiedServers.shared.realIdOf(id: id))
        publishActiveToFedConn()
    }

    /// #234: re-discover remotes (also called when a picker opens). A selection
    /// whose remote vanished moves to its parent; one whose parent is gone clears.
    func refreshProxied() {
        // Before the first DB emission `profiles` is empty — a repair then would
        // wrongly clear a proxied selection.
        guard !profiles.isEmpty else { return }
        let real = profiles
        let active = activeProfileId
        if IosProxiedServers.shared.hasUnlistedParents(real: real) { proxiedLoading = true }
        IosProxiedServers.shared.refresh(real: real, activeId: active) { [weak self] result in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.proxiedLoading = false
                self.proxied = result.virtualProfiles
                self.scheduleProxiedRetry(ms: result.retryDelayMs)
                if result.repair, self.activeProfileId == active {
                    self.activeProfileId = result.newActiveId
                    if let id = result.newActiveId {
                        UserDefaults.standard.set(id, forKey: "dw.active_profile_id")
                    } else {
                        UserDefaults.standard.removeObject(forKey: "dw.active_profile_id")
                    }
                }
                // The remote list (and so the resolved active profile) may have changed.
                self.publishActiveToFedConn()
            }
        }
    }

    /// #236.1: a refresh where some server failed is retried with capped
    /// backoff (2 s … 60 s); a clean refresh cancels the pending retry. The
    /// last good remote list stays in place meanwhile.
    private func scheduleProxiedRetry(ms: Int64) {
        retryTask?.cancel()
        retryTask = nil
        guard ms > 0 else { return }
        retryTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(ms) * 1_000_000)
            guard !Task.isCancelled else { return }
            self?.refreshProxied()
        }
    }

    /// BL403: tapping the server name on a home-screen widget cycles the active
    /// server (Android "tap to cycle"). The widget writes the shared widget config;
    /// adopt its choice on launch and whenever the app returns to the foreground.
    func adoptWidgetSelection() {
        // The widget only knows real servers: keep a proxied remote whose parent it shows.
        guard let id = WidgetSync.widgetActiveProfileId(), id != activeProfileId,
              id != IosProxiedServers.shared.realIdOf(id: activeProfileId) else { return }
        activeProfileId = id
        UserDefaults.standard.set(id, forKey: "dw.active_profile_id")
        publishActiveToFedConn()
    }

    private var collectionTask: Task<Void, Never>?
    private var proxiedTask: Task<Void, Never>?
    private var retryTask: Task<Void, Never>?
    private var fedSubscription: IosSubscription?

    init() {
        adoptWidgetSelection()
        startCollecting()
        startProxiedRefresh()
        fedSubscription = IosFedConn.shared.watch { [weak self] st in
            Task { @MainActor [weak self] in self?.fedConnState = st }
        }
    }

    deinit {
        collectionTask?.cancel()
        proxiedTask?.cancel()
        retryTask?.cancel()
        fedSubscription?.cancel()
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
                    await MainActor.run {
                        self?.profiles = typed
                        // Widgets show the active (or first enabled) server.
                        WidgetSync.publish(activeProfileId: IosProxiedServers.shared.realIdOf(id: self?.activeProfileId))
                        // Launch-time discovery (PWA v8.73.2: independent of any view).
                        self?.refreshProxied()
                        self?.publishActiveToFedConn()
                    }
                }
            } catch {
                // Flow ended — not an error in normal operation.
            }
        }
    }

    /// Every 60 s while the app is in the foreground (PWA-like freshness without
    /// background traffic).
    private func startProxiedRefresh() {
        proxiedTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 60_000_000_000)
                guard let self else { return }
                if UIApplication.shared.applicationState == .active { self.refreshProxied() }
            }
        }
    }
}
