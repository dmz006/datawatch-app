import SwiftUI

/// Root view — 6-tab structure matching Android bottom nav and PWA sidebar.
///
/// Tab order: Sessions | Alerts | Automata | Observer | Dashboard | Settings
/// Matches composeApp's BottomNavItem ordering so deep links resolve to the
/// same conceptual surface regardless of platform.
struct RootView: View {
    @State private var selectedTab: AppTab = .sessions

    var body: some View {
        TabView(selection: $selectedTab) {
            ForEach(AppTab.allCases) { tab in
                NavigationStack {
                    tab.rootView
                }
                .tabItem {
                    Label(tab.title, systemImage: tab.iconName)
                }
                .tag(tab)
                .badge(tab.badge)
            }
        }
        .tint(DatawatchColors.primary)
        .preferredColorScheme(.dark)
        .onOpenURL { url in
            AppRouter.shared.handle(url: url, selectedTab: $selectedTab)
        }
    }
}

// ── Tabs ──────────────────────────────────────────────────────────────────

enum AppTab: String, CaseIterable, Identifiable {
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

    /// Badge value — wired to ViewModel state in Story 5 (Sessions) and Story 6 (Alerts).
    var badge: Int { 0 }

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
}
#endif
