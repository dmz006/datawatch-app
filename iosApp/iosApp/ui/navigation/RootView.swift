import SwiftUI
import DatawatchShared

/// Root view — PWA nav order: Sessions · Automata · Alerts · Observer ·
/// Dashboard · Settings. Automata and Dashboard are hidden until the active
/// server reports `autonomous.enabled` (PWA `navBtnAutonomous` /
/// `navBtnDashboard` gating; Android `probeAutonomous`); unknown = shown.
///
/// iPhone: TabView, one NavigationStack per tab. iPad (regular width):
/// NavigationSplitView sidebar + detail.
///
/// Shell services owned here: the alert dock overlay (D3a), live WS alert feed
/// (D51a), interim local notifications (D87b), `datawatch://` deep links
/// (D84b) and cold-start restore of the last tab + open session (D40a).
struct RootView: View {
    @EnvironmentObject private var profileStore: ServerProfileStore
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.scenePhase) private var scenePhase
    @AppStorage("dw.alert.badge") private var alertBadgeCount: Int = 0
    /// D40a: last tab, restored on cold start.
    @AppStorage(ShellRestore.tabKey) private var lastTab: String = AppTab.sessions.rawValue
    @State private var selectedTab: AppTab = ShellRestore.initialTab()
    /// Sessions tab stack — deep links, notification taps and D40a restore push here.
    @State private var sessionsPath = NavigationPath()
    /// iPhone "More" tab stack (Dashboard / Settings when six tabs are visible).
    @State private var morePath: [AppTab] = []
    @State private var showingMore = false
    /// nil = unknown (tabs shown); false hides Automata + Dashboard.
    @State private var autonomousEnabled: Bool? = nil
    @State private var restored = false
    /// PWA `#peerStaleBadge`: federated peers never pushed or silent > 60 s.
    @State private var stalePeerCount: Int = 0

    private var visibleTabs: [AppTab] {
        AppTab.allCases.filter { tab in
            guard autonomousEnabled == false else { return true }
            return tab != .automata && tab != .dashboard
        }
    }

    /// PWA `updateAlertBadge`: hidden at 0, `99+` cap.
    private var alertBadgeText: Text? {
        let n: Int = alertBadgeCount
        if n <= 0 { return nil }
        return Text(verbatim: n > 99 ? "99+" : String(n))
    }

    /// PWA `updatePeerStaleBadge`: red count on the Settings tab, `99+` cap.
    private var stalePeerBadgeText: Text? {
        let n: Int = stalePeerCount
        if n <= 0 { return nil }
        return Text(verbatim: n > 99 ? "99+" : String(n))
    }

    private func badgeText(_ tab: AppTab) -> Text? {
        switch tab {
        case .alerts: return alertBadgeText
        case .settings: return stalePeerBadgeText
        default: return nil
        }
    }

    var body: some View {
        Group {
            if horizontalSizeClass == .regular {
                iPadLayout
            } else {
                iPhoneLayout
            }
        }
        .modifier(ServerPickerDialogModifier())
        .alertDockOverlay()
        .onOpenURL { url in
            AppRouter.shared.handle(url: url, selectedTab: $selectedTab)
        }
        .onReceive(NotificationCenter.default.publisher(for: .dwNavigateToAlerts)) { _ in
            selectedTab = .alerts
        }
        .onReceive(NotificationCenter.default.publisher(for: .dwNavigateToSessions)) { _ in
            selectedTab = .sessions
        }
        .onReceive(NotificationCenter.default.publisher(for: .dwNavigateToDashboard)) { _ in
            selectedTab = .dashboard
        }
        .onReceive(NotificationCenter.default.publisher(for: .dwNavigateToSettings)) { _ in
            selectedTab = .settings
        }
        .onReceive(NotificationCenter.default.publisher(for: .deepLinkSession)) { note in
            openSession(note.userInfo)
        }
        .onReceive(NotificationCenter.default.publisher(for: .deepLinkAlert)) { _ in
            selectedTab = .alerts
        }
        .onReceive(NotificationCenter.default.publisher(for: .dwReconnectRequested)) { _ in
            LiveAlertFeed.shared.stop()
            if scenePhase == .active { LiveAlertFeed.shared.start() }
        }
        .onChange(of: selectedTab) { tab in
            lastTab = tab.rawValue
            AlertDock.shared.close()
        }
        .onChange(of: profileStore.profiles) { _ in profilesChanged() }
        .onChange(of: profileStore.activeProfileId) { _ in probeAutonomous() }
        .onChange(of: scenePhase) { phase in shellServices(active: phase == .active) }
        .onAppear {
            ReachabilityCache.shared.attach(profileStore)
            profilesChanged()
            shellServices(active: true)
        }
        .task { await restoreOnce() }
        .task(id: profileStore.activeProfileId) { await pollStalePeers() }
        #if DEBUG
        .onAppear {
            DebugLaunchHooks.applyTheme()
            if let tab = DebugLaunchHooks.initialTab { selectedTab = tab }
        }
        .task {
            // Wait for the profile store's first load, then seed (sandbox only).
            for _ in 0..<50 where profileStore.isLoading { try? await Task.sleep(nanoseconds: 100_000_000) }
            DebugLaunchHooks.seedServer(store: profileStore)
            if let url = DebugLaunchHooks.openSessionURL {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                AppRouter.shared.handle(url: url, selectedTab: $selectedTab)
            }
            if let card = DebugLaunchHooks.openSettingsCard {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                SettingsDeepLink.open(cardId: card)
            }
        }
        #endif
    }

    // ── iPhone: TabView ───────────────────────────────────────────────────

    /// iPhone shows at most five tab items. With six visible tabs the last two
    /// (Dashboard, Settings) go behind our own "More" list instead of UIKit's
    /// More controller, which wrapped each tab's NavigationStack in a second
    /// navigation bar (two back buttons).
    private var barTabs: [AppTab] {
        visibleTabs.count > 5 ? Array(visibleTabs.prefix(4)) : visibleTabs
    }

    private var overflowTabs: [AppTab] {
        visibleTabs.count > 5 ? Array(visibleTabs.dropFirst(4)) : []
    }

    private static let moreTag = "more"

    private var tabTag: Binding<String> {
        Binding(
            get: {
                (showingMore || overflowTabs.contains(selectedTab)) && !overflowTabs.isEmpty
                    ? Self.moreTag : selectedTab.rawValue
            },
            set: { tag in
                if tag == Self.moreTag {
                    showingMore = true
                } else if let tab = AppTab(rawValue: tag) {
                    showingMore = false
                    selectedTab = tab
                }
            }
        )
    }

    private var iPhoneLayout: some View {
        TabView(selection: tabTag) {
            ForEach(barTabs) { tab in
                tabStack(tab)
                    .tabItem {
                        Label(L(tab.title), systemImage: tab.iconName)
                    }
                    .tag(tab.rawValue)
                    .badge(badgeText(tab))
            }
            if !overflowTabs.isEmpty {
                moreStack
                    .tabItem {
                        Label(L("More"), systemImage: "ellipsis")
                    }
                    .tag(Self.moreTag)
                    .badge(overflowTabs.contains(.settings) ? badgeText(.settings) : nil)
            }
        }
        .tint(DatawatchColors.secondary)
        .dwThemed()
        .onChange(of: selectedTab) { tab in
            // Programmatic switches (deep links, restore) to an overflow tab open it in More.
            if overflowTabs.contains(tab) {
                showingMore = true
                if morePath.last != tab { morePath = [tab] }
            }
        }
        .onChange(of: morePath) { path in
            if let last = path.last, last != selectedTab { selectedTab = last }
        }
        .onAppear {
            if overflowTabs.contains(selectedTab) {
                showingMore = true
                morePath = [selectedTab]
            }
        }
    }

    private var moreStack: some View {
        NavigationStack(path: $morePath) {
            List(overflowTabs) { tab in
                NavigationLink(value: tab) {
                    Label(L(tab.title), systemImage: tab.iconName)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .badge(badgeText(tab))
                }
                .listRowBackground(DatawatchColors.surface)
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle(L("More"))
            .navigationDestination(for: AppTab.self) { tab in
                tab.rootView
            }
        }
    }

    @ViewBuilder
    private func tabStack(_ tab: AppTab) -> some View {
        if tab == .sessions {
            NavigationStack(path: $sessionsPath) {
                tab.rootView
                    .navigationDestination(for: SessionRoute.self) { route in
                        DeepLinkSessionView(route: route)
                    }
            }
        } else {
            NavigationStack {
                tab.rootView
            }
        }
    }

    // ── iPad: NavigationSplitView ─────────────────────────────────────────

    private var iPadLayout: some View {
        NavigationSplitView {
            List(visibleTabs, selection: Binding<AppTab?>(get: { selectedTab }, set: { if let t = $0 { selectedTab = t } })) { tab in
                NavigationLink(value: tab) {
                    Label(L(tab.title), systemImage: tab.iconName)
                        .foregroundStyle(DatawatchColors.onSurface)
                }
            }
            .listStyle(.sidebar)
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.surface)
            .navigationTitle("datawatch")
        } detail: {
            tabStack(selectedTab)
        }
        .tint(DatawatchColors.secondary)
        .dwThemed()
    }

    // ── Shell services ────────────────────────────────────────────────────

    private func profilesChanged() {
        let profiles = profileStore.profiles
        LiveAlertFeed.shared.update(profiles: profiles)
        LocalAlertWatcher.shared.update(profiles: profiles)
        probeAutonomous()
    }

    /// Foreground-only: live alert sockets + the D87b polling watcher.
    private func shellServices(active: Bool) {
        if active {
            LiveAlertFeed.shared.start()
            LocalAlertWatcher.shared.start()
        } else {
            LiveAlertFeed.shared.stop()
            LocalAlertWatcher.shared.stop()
        }
    }

    private func probeAutonomous() {
        // All-servers mode keeps the tabs (any server may have autonomous on).
        guard !profileStore.isAllServers, let profile = profileStore.activeProfile else {
            autonomousEnabled = nil
            return
        }
        let pid = profile.id
        IosShellProbe.shared.autonomousEnabled(profile: profile) { code in
            let value: Int = Int(code.int32Value)
            DispatchQueue.main.async {
                guard profileStore.activeProfile?.id == pid else { return }
                if value < 0 { return }
                autonomousEnabled = value == 1
                if value == 0 && (selectedTab == .automata || selectedTab == .dashboard) {
                    selectedTab = .sessions
                }
            }
        }
    }

    /// PWA polls the peer registry for the stale badge; 30 s like Android.
    private func pollStalePeers() async {
        stalePeerCount = 0
        while !Task.isCancelled {
            if !profileStore.isAllServers, let profile = profileStore.activeProfile {
                IosStalePeers.shared.count(profile: profile) { value in
                    let n: Int = Int(value.int32Value)
                    DispatchQueue.main.async {
                        if n >= 0 { stalePeerCount = n }
                    }
                }
            }
            try? await Task.sleep(nanoseconds: 30_000_000_000)
        }
    }

    private func openSession(_ info: [AnyHashable: Any]?) {
        guard let id = info?["id"] as? String, !id.isEmpty else { return }
        let pid = info?["profileId"] as? String
        selectedTab = .sessions
        AlertDock.shared.close()
        sessionsPath = NavigationPath()
        let status = (info?["tab"] as? String) == "status"
        sessionsPath.append(SessionRoute(sessionId: id, profileId: pid, statusTab: status))
    }

    /// D40a: reopen the session that was open when the app was last killed.
    private func restoreOnce() async {
        guard !restored else { return }
        restored = true
        guard let route = ShellRestore.lastSession() else { return }
        // Let the first frame settle so the push animates onto a built stack.
        try? await Task.sleep(nanoseconds: 300_000_000)
        // Launched from an alert deep link: stay on Alerts (Android parity).
        if AlertDeepLinkFocus.shared.skipRestore { return }
        if sessionsPath.isEmpty {
            selectedTab = .sessions
            sessionsPath.append(route)
        }
    }
}

// ── D40a restore store ────────────────────────────────────────────────────

/// Persists the last tab and the session open in detail (PWA `cs_active_view`
/// / `cs_active_session`). Only ids — nothing secret.
enum ShellRestore {
    static let tabKey = "dw.shell.last_tab"
    static let sessionKey = "dw.shell.last_session"

    static func initialTab() -> AppTab {
        let raw = UserDefaults.standard.string(forKey: tabKey) ?? ""
        return AppTab(rawValue: raw) ?? .sessions
    }

    /// SessionDetailView calls this on appear (open) and disappear (nil).
    static func setOpenSession(profileId: String?, sessionId: String?) {
        if let sid = sessionId, let pid = profileId {
            UserDefaults.standard.set(pid + "|" + sid, forKey: sessionKey)
        } else {
            UserDefaults.standard.removeObject(forKey: sessionKey)
        }
    }

    static func lastSession() -> SessionRoute? {
        guard let raw = UserDefaults.standard.string(forKey: sessionKey) else { return nil }
        let parts = raw.split(separator: "|", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 2, !parts[1].isEmpty else { return nil }
        return SessionRoute(sessionId: parts[1], profileId: parts[0].isEmpty ? nil : parts[0])
    }
}

// ── Tabs ──────────────────────────────────────────────────────────────────

/// Declaration order = PWA nav order (index.html nav-btn sequence).
enum AppTab: String, CaseIterable, Identifiable, Hashable {
    case sessions  = "sessions"
    case automata  = "automata"
    case alerts    = "alerts"
    case observer  = "observer"
    case dashboard = "dashboard"
    case settings  = "settings"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .sessions:  "Sessions"
        case .alerts:    "Alerts"
        case .automata:  "Automata"
        case .observer:  "Observer"
        case .dashboard: "Dashboard"
        case .settings:  "Settings"
        }
    }

    var iconName: String {
        switch self {
        case .sessions:  "terminal"
        case .alerts:    "bell"
        case .automata:  "circle.hexagonpath"
        case .observer:  "eye"
        case .dashboard: "chart.bar"
        case .settings:  "gearshape"
        }
    }

    @ViewBuilder
    var rootView: some View {
        switch self {
        case .sessions:  SessionsView()
        case .alerts:    AlertsView()
        case .automata:  AutomataView()
        case .observer:  ObserverView()
        case .dashboard: DashboardView()
        case .settings:  SettingsView()
        }
    }
}

#if DEBUG
#Preview {
    RootView()
        .environmentObject(ServerProfileStore())
}
#endif
