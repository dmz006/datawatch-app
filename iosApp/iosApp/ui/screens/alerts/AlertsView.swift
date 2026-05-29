import SwiftUI
import DatawatchShared

// ── ViewModel ─────────────────────────────────────────────────────────────

@MainActor
final class AlertsViewModel: ObservableObject {
    @Published var alerts: [Alert] = []
    @Published var unreadCount: Int = 0 {
        didSet { UserDefaults.standard.set(unreadCount, forKey: "dw.alert.badge") }
    }
    @Published var isLoading: Bool = false
    @Published var error: String? = nil
    @Published var filterText: String = ""
    @Published var severityFilter: AlertSeverityFilter = .all
    @Published var selectedTab: AlertTab = .active

    enum AlertTab: String, CaseIterable {
        case active    = "Active"
        case historical = "Historical"
        case system    = "System"
    }

    enum AlertSeverityFilter: String, CaseIterable {
        case all = "All"
        case prompt = "Prompt"
        case error = "Error"
        case warning = "Warn"
        case info = "Info"
    }

    /// Alerts for the currently selected tab (before severity/text filtering).
    var tabAlerts: [Alert] {
        switch selectedTab {
        case .active:     return alerts.filter { !$0.read && $0.sessionId != nil }
        case .historical: return alerts.filter { $0.read && $0.sessionId != nil }
        case .system:     return alerts.filter { $0.sessionId == nil }
        }
    }

    var filteredAlerts: [Alert] {
        var result = tabAlerts
        if !filterText.isEmpty {
            let q = filterText.lowercased()
            result = result.filter {
                $0.title.lowercased().contains(q) ||
                $0.message.lowercased().contains(q)
            }
        }
        switch severityFilter {
        case .all: break
        case .prompt:  result = result.filter { $0.type.contains("input") || $0.type.contains("prompt") }
        case .error:   result = result.filter { $0.severity == .error }
        case .warning: result = result.filter { $0.severity == .warning }
        case .info:    result = result.filter { $0.severity != .error && $0.severity != .warning && !$0.type.contains("input") }
        }
        return result
    }

    func tabCount(for tab: AlertTab) -> Int {
        switch tab {
        case .active:     return alerts.filter { !$0.read && $0.sessionId != nil }.count
        case .historical: return alerts.filter { $0.read && $0.sessionId != nil }.count
        case .system:     return alerts.filter { $0.sessionId == nil }.count
        }
    }

    func chipCount(for filter: AlertSeverityFilter) -> Int {
        let base = tabAlerts
        switch filter {
        case .all:     return base.count
        case .prompt:  return base.filter { $0.type.contains("input") || $0.type.contains("prompt") }.count
        case .error:   return base.filter { $0.severity == .error }.count
        case .warning: return base.filter { $0.severity == .warning }.count
        case .info:    return base.filter { $0.severity != .error && $0.severity != .warning && !$0.type.contains("input") }.count
        }
    }

    private var profile: ServerProfile?
    private var pollingTimer: Timer?
    private static let pollInterval: TimeInterval = 5

    func load(from profiles: [ServerProfile]) {
        let newActive = profiles.first
        guard newActive?.id != profile?.id else { return }
        profile = newActive
        if newActive != nil {
            refresh()
            startPolling()
        } else {
            stopPolling()
            alerts = []
            unreadCount = 0
            error = nil
        }
    }

    func startPolling() {
        stopPolling()
        pollingTimer = Timer.scheduledTimer(withTimeInterval: Self.pollInterval, repeats: true) { [weak self] _ in
            Task { @MainActor [weak self] in self?.refresh() }
        }
    }

    func stopPolling() {
        pollingTimer?.invalidate()
        pollingTimer = nil
    }

    func refresh() {
        guard let profile else { return }
        isLoading = true
        error = nil
        IosServiceLocator.shared.listAlerts(
            profile: profile,
            onSuccess: { [weak self] view in
                DispatchQueue.main.async {
                    self?.alerts = view.alerts
                    self?.unreadCount = Int(view.unreadCount)
                    self?.isLoading = false
                }
            },
            onError: { [weak self] msg in
                DispatchQueue.main.async {
                    self?.error = msg
                    self?.isLoading = false
                }
            }
        )
    }

    /// Mark an alert as read on server and remove it locally.
    func dismiss(alert: Alert) {
        alerts.removeAll { $0.id == alert.id }
        if !alert.read, unreadCount > 0 {
            unreadCount -= 1
        }
        guard let profile else { return }
        IosServiceLocator.shared.markAlertRead(
            profile: profile,
            alertId: alert.id,
            onSuccess: {},
            onError: { _ in }
        )
    }

    /// Dismiss all alerts (clear locally and on server).
    func dismissAll() {
        guard let profile else { return }
        IosServiceLocator.shared.markAllAlertsRead(
            profile: profile,
            onSuccess: { [weak self] in
                DispatchQueue.main.async {
                    self?.unreadCount = 0
                    self?.alerts = []
                }
            },
            onError: { _ in }
        )
    }
}

// ── Main view ─────────────────────────────────────────────────────────────

struct AlertsView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var vm = AlertsViewModel()

    var body: some View {
        Group {
            if store.profiles.isEmpty {
                noProfilesView
            } else if vm.isLoading && vm.alerts.isEmpty {
                LoadingIndicator(message: "Loading alerts…")
            } else if let err = vm.error, vm.alerts.isEmpty {
                ErrorCard(message: err) { vm.refresh() }
            } else {
                alertListView
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(.principal) {
                headerTitle
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 4) {
                    DocsLinkButton(
                        profile: store.profiles.first,
                        anchor: "alerts"
                    )
                    ReachabilityDotView(profile: store.profiles.first)
                }
            }
        }
        .onAppear {
            vm.load(from: store.profiles)
        }
        .onDisappear {
            vm.stopPolling()
        }
        .onChange(of: store.profiles) { newProfiles in
            vm.load(from: newProfiles)
        }
    }

    // ── Header ────────────────────────────────────────────────────────────

    private var headerTitle: some View {
        HStack(spacing: 6) {
            HeaderView(title: "Alerts")
            if vm.unreadCount > 0 {
                Text("\(vm.unreadCount)")
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.background)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(DatawatchColors.error)
                    .clipShape(Capsule())
                    .accessibilityLabel("\(vm.unreadCount) unread alerts")
            }
        }
    }

    // ── States ────────────────────────────────────────────────────────────

    private var noProfilesView: some View {
        VStack(spacing: 20) {
            Image(systemName: "bell.slash")
                .font(.system(.largeTitle))
                .imageScale(.large)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No server connected")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("Add a server in Settings to view alerts.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
    }

    // ── Tab row ───────────────────────────────────────────────────────────

    private var tabRow: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(AlertsViewModel.AlertTab.allCases, id: \.self) { tab in
                    let count = vm.tabCount(for: tab)
                    let isSelected = vm.selectedTab == tab
                    Button { vm.selectedTab = tab } label: {
                        VStack(spacing: 4) {
                            Text(count > 0 ? "\(tab.rawValue) (\(count))" : tab.rawValue)
                                .font(DatawatchFonts.badge)
                                .foregroundStyle(isSelected ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
                                .lineLimit(1)
                            Rectangle()
                                .fill(isSelected ? DatawatchColors.primary : Color.clear)
                                .frame(height: 2)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                    }
                }
            }
            .background(DatawatchColors.surface)
            Divider().background(DatawatchColors.border)
        }
    }

    // ── Filter bar ────────────────────────────────────────────────────────

    private var filterBar: some View {
        VStack(spacing: 0) {
            // Row 1: 🔔 count + ✕ dismiss-all + 🔕 mute + ↻ refresh
            HStack(spacing: 8) {
                Text("🔔 \(vm.tabAlerts.count) \(vm.tabAlerts.count == 1 ? "alert" : "alerts")")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                Spacer()
                controlBtn("✕") { vm.dismissAll() }
                    .accessibilityLabel("Dismiss all")
                controlBtn("🔕") { vm.dismissAll() }
                    .accessibilityLabel("Mute all")
                controlBtn("↻") { vm.refresh() }
                    .accessibilityLabel("Refresh")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)

            Divider().background(DatawatchColors.border.opacity(0.5))

            // Row 2: severity chips with emoji + ×N counts
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(AlertsViewModel.AlertSeverityFilter.allCases, id: \.self) { filter in
                        severityChip(filter)
                    }
                }
                .padding(.horizontal, 12)
            }
            .padding(.vertical, 6)

            // Row 3: search
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                TextField("Search alerts…", text: $vm.filterText)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                if !vm.filterText.isEmpty {
                    Button { vm.filterText = "" } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .accessibilityLabel("Clear search")
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(DatawatchColors.surface)

            Divider().background(DatawatchColors.border)
        }
        .background(DatawatchColors.background)
    }

    private func controlBtn(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(DatawatchFonts.badge)
                .foregroundStyle(DatawatchColors.onSurface)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(DatawatchColors.surface2)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.border, lineWidth: 1))
        }
    }

    @ViewBuilder
    private func severityChip(_ filter: AlertsViewModel.AlertSeverityFilter) -> some View {
        let selected = vm.severityFilter == filter
        let count = vm.chipCount(for: filter)
        let (emoji, color): (String, Color) = {
            switch filter {
            case .all:     return ("", DatawatchColors.onSurfaceMuted)
            case .prompt:  return ("🟡 ", DatawatchColors.warning)
            case .error:   return ("🔴 ", DatawatchColors.error)
            case .warning: return ("🟠 ", DatawatchColors.warning)
            case .info:    return ("⚪ ", DatawatchColors.onSurfaceMuted)
            }
        }()
        let label = "\(emoji)\(filter.rawValue) ×\(count)"
        Button { vm.severityFilter = filter } label: {
            Text(label)
                .font(DatawatchFonts.badge)
                .foregroundStyle(selected ? DatawatchColors.background : color)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(selected ? color : color.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(color.opacity(0.4), lineWidth: 1))
        }
    }

    // ── Alert list ────────────────────────────────────────────────────────

    private var alertListView: some View {
        VStack(spacing: 0) {
            tabRow
            filterBar
            List {
                if vm.filteredAlerts.isEmpty {
                    HStack {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "bell.slash")
                                .font(.system(.title))
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                .accessibilityHidden(true)
                            Text("No \(vm.selectedTab.rawValue.lowercased()) alerts")
                                .font(DatawatchFonts.bodyMedium)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                        .padding(.top, 48)
                        Spacer()
                    }
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                } else {
                    ForEach(vm.filteredAlerts, id: \.id) { alert in
                        AlertRow(alert: alert)
                            .listRowBackground(DatawatchColors.surface)
                            .listRowSeparatorTint(DatawatchColors.border)
                            .listRowInsets(EdgeInsets())
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) {
                                    vm.dismiss(alert: alert)
                                } label: {
                                    Label("Dismiss", systemImage: "xmark.circle")
                                }
                                .tint(DatawatchColors.error)
                            }
                    }
                }
            }
            .listStyle(.plain)
            .background(DatawatchColors.background)
            .scrollContentBackground(.hidden)
            .refreshable { vm.refresh() }
        }
    }
}

// ── Alert row ─────────────────────────────────────────────────────────────

private struct AlertRow: View {
    let alert: Alert

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            // Left colored border (3px)
            Rectangle()
                .fill(borderColor)
                .frame(width: 3)

            VStack(alignment: .leading, spacing: 6) {
                // Top: badge + timestamp
                HStack(spacing: 8) {
                    badgeView
                    Text(alertTime(from: alert.createdAt))
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.7))
                    Spacer()
                    if !alert.read {
                        Circle()
                            .fill(DatawatchColors.primary)
                            .frame(width: 7, height: 7)
                            .accessibilityLabel("Unread")
                    }
                }

                // Title
                Text(alert.title.isEmpty ? alert.type : alert.title)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(alert.read ? DatawatchColors.onSurfaceMuted : DatawatchColors.onSurface)
                    .lineLimit(2)

                // Message
                if !alert.message.isEmpty {
                    Text(String(alert.message.prefix(500)))
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(3)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(alertBackground)
        }
        .accessibilityElement(children: .combine)
    }

    private var isPrompt: Bool {
        alert.type.contains("input") || alert.type.contains("prompt") || alert.type.contains("waiting")
    }

    private var borderColor: Color {
        if isPrompt { return DatawatchColors.warning }
        switch alert.severity {
        case .error: return DatawatchColors.error
        case .warning: return DatawatchColors.warning
        default: return DatawatchColors.border
        }
    }

    private var alertBackground: Color {
        if isPrompt { return DatawatchColors.warning.opacity(0.06) }
        switch alert.severity {
        case .error:   return DatawatchColors.error.opacity(0.05)
        case .warning: return DatawatchColors.warning.opacity(0.04)
        default:       return Color.clear
        }
    }

    @ViewBuilder
    private var badgeView: some View {
        if isPrompt {
            badgeLabel("🟡 PROMPT", fg: Color(hex: 0x0F1117), bg: DatawatchColors.warning)
        } else {
            switch alert.severity {
            case .error:
                badgeLabel("🔴 ERROR", fg: .white, bg: DatawatchColors.error)
            case .warning:
                badgeLabel("🟠 WARNING", fg: Color(hex: 0x0F1117), bg: DatawatchColors.warning)
            default:
                badgeLabel("⚪ info", fg: DatawatchColors.onSurfaceMuted, bg: DatawatchColors.surface2)
            }
        }
    }

    private func badgeLabel(_ text: String, fg: Color, bg: Color) -> some View {
        Text(text)
            .font(DatawatchFonts.badge)
            .foregroundStyle(fg)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(bg)
            .clipShape(RoundedRectangle(cornerRadius: 3))
    }

    private func alertTime(from instant: Kotlinx_datetimeInstant) -> String {
        let epochMs = instant.toEpochMilliseconds()
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let cal = Calendar.current
        let h = cal.component(.hour, from: date)
        let m = cal.component(.minute, from: date)
        let s = cal.component(.second, from: date)
        return String(format: "%02d:%02d:%02d", h, m, s)
    }
}

#if DEBUG
#Preview {
    NavigationStack { AlertsView() }
        .preferredColorScheme(.dark)
}
#endif
