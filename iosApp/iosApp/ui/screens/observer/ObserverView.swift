import SwiftUI
import DatawatchShared

// ── ViewModel ─────────────────────────────────────────────────────────────

/// Observer tab state (parity B20–B23). Refresh model per D54b (PWA):
/// `/api/stats` is fetched once when the view opens and then follows the
/// WS `stats` frames (IosServiceLocator.subscribeGlobalStream → StatsHub).
/// The per-system grid, peer resources, observer peers and cluster blocks
/// refresh every 8 s while visible (PWA setInterval 8000); web-search usage
/// every third tick (~24 s; PWA 20 s).
@MainActor
final class ObserverViewModel: ObservableObject {
    @Published private(set) var stats: StatsDto? = nil
    @Published private(set) var panel: IosStatsPanel? = nil
    @Published private(set) var isLoading: Bool = false
    @Published private(set) var error: String? = nil
    @Published private(set) var systems: IosSystemsSnapshot? = nil
    @Published private(set) var serverInfo: IosObsCard? = nil
    @Published private(set) var ebpf: IosEbpfSnapshot? = nil
    @Published private(set) var plugins: [IosPluginRow]? = nil
    @Published private(set) var pluginsError: String? = nil
    @Published private(set) var cluster: [IosClusterRow] = []
    @Published private(set) var bridge: [IosObsLine]? = nil
    @Published private(set) var diagnostics: IosChannelDiagnostics? = nil
    @Published private(set) var comm: IosCommBackends? = nil
    @Published private(set) var matrix: IosMatrixStatus? = nil
    @Published private(set) var webSearch: IosWebSearchStats? = nil
    @Published private(set) var webSearchError: String? = nil

    private(set) var profile: ServerProfile?
    private var extras = IosStatsExtras(hostname: "", activeSessions: -1, rtkLatestVersion: "", certificates: [])
    private var maxSessions: Int32 = 0
    private var liveTask: Task<Void, Never>? = nil
    private var wsSubscription: IosSubscription? = nil
    private static let liveIntervalNs: UInt64 = 8_000_000_000

    func update(profiles: [ServerProfile]) {
        let newActive = profiles.first
        guard newActive?.id != profile?.id else { return }
        if let newActive {
            selectProfile(newActive)
        } else {
            stopPolling()
            profile = nil
            reset()
        }
    }

    func selectProfile(_ newProfile: ServerProfile) {
        guard newProfile.id != profile?.id || liveTask == nil else { return }
        if newProfile.id != profile?.id { reset() }
        profile = newProfile
        startPolling()
    }

    func startPolling() {
        guard let profile else { return }
        stopPolling()

        wsSubscription = IosServiceLocator.shared.subscribeGlobalStream(
            profile: profile,
            onStats: { [weak self] dto in
                Task { @MainActor [weak self] in self?.apply(stats: dto) }
            },
            onSessions: { _ in }
        )

        let pid = profile.id
        liveTask = Task { [weak self] in
            await self?.fetchStatsOnce()
            self?.loadOnce()
            var tick = 0
            while !Task.isCancelled {
                guard let self, self.profile?.id == pid else { return }
                self.loadLive(includeWebSearch: tick % 3 == 0)
                tick += 1
                try? await Task.sleep(nanoseconds: Self.liveIntervalNs)
            }
        }
    }

    func stopPolling() {
        liveTask?.cancel()
        liveTask = nil
        wsSubscription?.cancel()
        wsSubscription = nil
    }

    func refreshDiagnostics() {
        guard let p = profile else { return }
        IosObserver.shared.loadChannelDiagnostics(profile: p) { [weak self] d in
            Task { @MainActor [weak self] in
                guard let self, self.profile?.id == p.id else { return }
                self.diagnostics = d
            }
        }
    }

    // ── Loading ───────────────────────────────────────────────────────────

    private func reset() {
        stats = nil
        panel = nil
        error = nil
        systems = nil
        serverInfo = nil
        ebpf = nil
        plugins = nil
        pluginsError = nil
        cluster = []
        bridge = nil
        diagnostics = nil
        comm = nil
        matrix = nil
        webSearch = nil
        webSearchError = nil
        extras = IosStatsExtras(hostname: "", activeSessions: -1, rtkLatestVersion: "", certificates: [])
        maxSessions = 0
    }

    private func fetchStatsOnce() async {
        guard let profile else { return }
        if stats == nil { isLoading = true }
        do {
            apply(stats: try await ServiceLocatorAsync.getStats(profile: profile))
        } catch {
            if stats == nil { self.error = error.localizedDescription }
            isLoading = false
        }
    }

    /// One-shot blocks (PWA loaders fired on view render).
    private func loadOnce() {
        guard let p = profile else { return }
        let obs = IosObserver.shared
        obs.loadServerContext(profile: p) { [weak self] ctx in
            Task { @MainActor [weak self] in
                guard let self, self.profile?.id == p.id else { return }
                self.serverInfo = ctx.serverInfo
                self.maxSessions = ctx.maxSessions
                self.recomputePanel()
            }
        }
        obs.loadEbpf(profile: p) { [weak self] snap in
            Task { @MainActor [weak self] in
                guard let self, self.profile?.id == p.id else { return }
                self.ebpf = snap
            }
        }
        obs.loadPlugins(
            profile: p,
            onSuccess: { [weak self] rows in
                Task { @MainActor [weak self] in
                    guard let self, self.profile?.id == p.id else { return }
                    self.plugins = rows
                    self.pluginsError = nil
                }
            },
            onError: { [weak self] msg in
                Task { @MainActor [weak self] in
                    guard let self, self.profile?.id == p.id else { return }
                    self.pluginsError = msg
                }
            }
        )
        obs.loadChannelBridge(profile: p) { [weak self] lines in
            Task { @MainActor [weak self] in
                guard let self, self.profile?.id == p.id else { return }
                self.bridge = lines
            }
        }
        refreshDiagnostics()
        obs.loadCommBackends(profile: p) { [weak self] c in
            Task { @MainActor [weak self] in
                guard let self, self.profile?.id == p.id else { return }
                self.comm = c
                if c.matrixEnabled { self.loadMatrix() }
            }
        }
    }

    func loadMatrix() {
        guard let p = profile else { return }
        IosObserver.shared.loadMatrixStatus(profile: p) { [weak self] st in
            Task { @MainActor [weak self] in
                guard let self, self.profile?.id == p.id else { return }
                self.matrix = st
            }
        }
    }

    /// 8 s blocks: per-system grid + peer resources/peers share one fetch.
    private func loadLive(includeWebSearch: Bool) {
        guard let p = profile else { return }
        let obs = IosObserver.shared
        obs.loadSystems(profile: p) { [weak self] snap in
            Task { @MainActor [weak self] in
                guard let self, self.profile?.id == p.id else { return }
                self.systems = snap
                self.extras = snap.extras
                self.recomputePanel()
            }
        }
        obs.loadCluster(profile: p) { [weak self] rows in
            Task { @MainActor [weak self] in
                guard let self, self.profile?.id == p.id else { return }
                self.cluster = rows
            }
        }
        if includeWebSearch {
            obs.loadWebSearchStats(
                profile: p,
                onSuccess: { [weak self] st in
                    Task { @MainActor [weak self] in
                        guard let self, self.profile?.id == p.id else { return }
                        self.webSearch = st
                        self.webSearchError = nil
                    }
                },
                onError: { [weak self] msg in
                    Task { @MainActor [weak self] in
                        guard let self, self.profile?.id == p.id else { return }
                        self.webSearchError = msg
                    }
                }
            )
        }
    }

    private func apply(stats dto: StatsDto) {
        stats = dto
        error = nil
        isLoading = false
        recomputePanel()
    }

    private func recomputePanel() {
        guard let stats else { return }
        panel = IosObserver.shared.buildStatsPanel(s: stats, extras: extras, maxSessions: maxSessions)
    }
}

/// Toast host for Observer actions (PWA showToast).
@MainActor
final class ObserverToastCenter: ObservableObject {
    /// D41a: Observer notices go to the alert dock (PWA `showToast` →
    /// `pushToAlertDock`), not a floating toast.
    func show(_ text: String) {
        AlertDock.notify(text)
    }
}

// ── Main view ─────────────────────────────────────────────────────────────

/// Observer tab — PWA `renderObserverView()` card order (D28a): System
/// Statistics (grid, statistics panel, eBPF, network, plugins, peer
/// resources, federated peers, cluster, MCP bridge, diagnostics, comm
/// backends) → Memory Browser → Memory Maintenance → Scheduled Events →
/// Global Cooldown → Session Analytics → Audit Log → Knowledge Graph →
/// Daemon Log → Federated Peers. Cards collapse with persisted state (D27a)
/// and carry a per-card docs link (D26a).
struct ObserverView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var vm = ObserverViewModel()
    @StateObject private var collapse = ObserverCollapseStore()
    @StateObject private var toaster = ObserverToastCenter()
    @State private var selectedProfileId: String? = UserDefaults.standard.string(forKey: "dw.active_profile_id")

    private var selectedProfile: ServerProfile? {
        // D2a: follow the app-wide active server; "All" (Sessions-only) falls
        // back to the store's first enabled profile.
        if let id = selectedProfileId, let p = store.profiles.first(where: { $0.id == id }) {
            return p
        }
        return store.activeProfile
    }

    var body: some View {
        Group {
            if store.profiles.isEmpty {
                emptyStateView
            } else {
                profileContent
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
        .environmentObject(toaster)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                HeaderView(
                    title: "Observer",
                    serverName: selectedProfile?.displayName
                )
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 4) {
                    DocsLinkButton(profile: selectedProfile, anchor: "observer")
                    AlertsBellButton()
                    ReachabilityDotView(profile: selectedProfile)
                }
            }
        }
        .onAppear {
            if let profile = selectedProfile {
                vm.selectProfile(profile)
            } else {
                vm.update(profiles: store.profiles)
            }
        }
        .onDisappear {
            vm.stopPolling()
        }
        .onChange(of: store.activeProfileId) { id in
            if id != selectedProfileId { selectedProfileId = id; }
        }
        .onChange(of: store.profiles) { newProfiles in
            if let id = selectedProfileId, !newProfiles.contains(where: { $0.id == id }) {
                selectedProfileId = nil
            }
            if let profile = selectedProfile {
                vm.selectProfile(profile)
            }
        }
    }

    // ── Profile content ───────────────────────────────────────────────────

    @ViewBuilder
    private var profileContent: some View {
        VStack(spacing: 0) {
            ServerPickerBar()
            if let profile = selectedProfile {
                observerContent(profile: profile)
            }
        }
        .task(id: selectedProfile?.id ?? "") {
            if let profile = selectedProfile {
                vm.selectProfile(profile)
            }
        }
    }


    // ── Empty state ───────────────────────────────────────────────────────

    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Image(systemName: "eye.slash")
                .font(.system(.largeTitle))
                .imageScale(.large)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No server connected")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("Add a server in Settings to monitor metrics.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
    }

    // ── Cards (PWA order) ─────────────────────────────────────────────────

    private func observerContent(profile: ServerProfile) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                ObsSection(key: "stats", title: "System Statistics", profile: profile, store: collapse) {
                    ObserverStatsBlock(vm: vm, profile: profile)
                }
                ObsSection(key: "membrowser", title: "Memory Browser", profile: profile, store: collapse) {
                    ObserverMemoryBrowser(profile: profile)
                }
                ObsSection(key: "memmaint", title: "Memory Maintenance", profile: profile, store: collapse) {
                    ObserverMemoryMaintenance(profile: profile)
                }
                ObsSection(key: "schedules", title: "Scheduled Events", profile: profile, store: collapse) {
                    ObserverSchedulesCard(profile: profile)
                }
                ObsSection(key: "cooldown", title: "Global Cooldown", profile: profile, store: collapse) {
                    ObserverCooldownCard(profile: profile)
                }
                ObsSection(key: "analytics", title: "Session Analytics", profile: profile, store: collapse) {
                    ObserverAnalyticsCard(profile: profile)
                }
                ObsSection(key: "audit", title: "Audit Log", profile: profile, store: collapse) {
                    ObserverAuditCard(profile: profile)
                }
                ObsSection(key: "kg", title: "Knowledge Graph", profile: profile, store: collapse) {
                    ObserverKnowledgeGraphCard(profile: profile)
                }
                ObsSection(key: "daemonlog", title: "Daemon Log", profile: profile, store: collapse) {
                    ObserverDaemonLogCard(profile: profile)
                }
                ObsSection(key: "observer_peers", title: "Federated Peers", profile: profile, store: collapse) {
                    ObserverFederatedPeersCard(profile: profile)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
        }
    }
}

#if DEBUG
#Preview {
    NavigationStack { ObserverView() }
        .preferredColorScheme(.dark)
}
#endif
