import SwiftUI

/// Root view — 6-tab structure matching Android bottom nav and PWA sidebar.
///
/// Tab order: Sessions | Alerts | Automata | Observer | Dashboard | Settings
/// Matches composeApp's BottomNavItem ordering so deep links resolve to the
/// same conceptual surface regardless of platform.
///
/// Story 13: On iPad (regular horizontal size class) uses NavigationSplitView
/// for a sidebar+detail layout. On iPhone uses TabView.
struct RootView: View {
    @EnvironmentObject private var profileStore: ServerProfileStore
    @State private var selectedTab: AppTab = .sessions
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @AppStorage("dw.alert.badge") private var alertBadgeCount: Int = 0

    var body: some View {
        if horizontalSizeClass == .regular {
            iPadLayout
        } else {
            iPhoneLayout
        }
    }

    // ── iPhone: TabView ───────────────────────────────────────────────────

    private var iPhoneLayout: some View {
        TabView(selection: $selectedTab) {
            ForEach(AppTab.allCases) { tab in
                NavigationStack {
                    tab.rootView
                }
                .tabItem {
                    Label(tab.title, systemImage: tab.iconName)
                }
                .tag(tab)
                .badge(tab == .alerts ? alertBadgeCount : 0)
            }
        }
        .tint(DatawatchColors.primary)
        .preferredColorScheme(.dark)
        .onOpenURL { url in
            AppRouter.shared.handle(url: url, selectedTab: $selectedTab)
        }
        .onReceive(NotificationCenter.default.publisher(for: .dwNavigateToAlerts)) { _ in
            selectedTab = .alerts
        }
    }

    // ── iPad: NavigationSplitView ─────────────────────────────────────────

    private var iPadLayout: some View {
        NavigationSplitView {
            List(AppTab.allCases, selection: $selectedTab) { tab in
                NavigationLink(value: tab) {
                    Label(tab.title, systemImage: tab.iconName)
                        .foregroundStyle(DatawatchColors.onSurface)
                }
            }
            .listStyle(.sidebar)
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.surface)
            .navigationTitle("datawatch")
        } detail: {
            NavigationStack {
                selectedTab.rootView
            }
        }
        .tint(DatawatchColors.primary)
        .preferredColorScheme(.dark)
        .onOpenURL { url in
            AppRouter.shared.handle(url: url, selectedTab: $selectedTab)
        }
        .onReceive(NotificationCenter.default.publisher(for: .dwNavigateToAlerts)) { _ in
            selectedTab = .alerts
        }
    }
}

// ── Tabs ──────────────────────────────────────────────────────────────────

enum AppTab: String, CaseIterable, Identifiable, Hashable {
    case sessions  = "sessions"
    case alerts    = "alerts"
    case automata  = "automata"
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
