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

    enum AlertSeverityFilter: String, CaseIterable {
        case all = "All"
        case prompt = "Prompt"
        case error = "Error"
        case warning = "Warn"
        case info = "Info"
    }

    var filteredAlerts: [Alert] {
        var result = alerts
        if !filterText.isEmpty {
            let q = filterText.lowercased()
            result = result.filter {
                $0.title.lowercased().contains(q) ||
                $0.message.lowercased().contains(q)
            }
        }
        switch severityFilter {
        case .all: break
        case .prompt: result = result.filter { $0.type.contains("input") || $0.type.contains("prompt") }
        case .error: result = result.filter { $0.severity == .error }
        case .warning: result = result.filter { $0.severity == .warning }
        case .info: result = result.filter { $0.severity != .error && $0.severity != .warning && !$0.type.contains("input") }
        }
        return result
    }

    private var profile: ServerProfile?

    func load(from profiles: [ServerProfile]) {
        let newActive = profiles.first
        guard newActive?.id != profile?.id else { return }
        profile = newActive
        if newActive != nil {
            refresh()
        } else {
            alerts = []
            unreadCount = 0
            error = nil
        }
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
            } else if vm.alerts.isEmpty {
                emptyStateView
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
                DocsLinkButton(
                    profile: store.profiles.first,
                    anchor: "alerts"
                )
            }
        }
        .onAppear {
            vm.load(from: store.profiles)
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
                    .background(DatawatchColors.secondary)
                    .clipShape(Capsule())
                    .accessibilityLabel("\(vm.unreadCount) unread alerts")
            }
        }
    }

    // ── States ────────────────────────────────────────────────────────────

    private var noProfilesView: some View {
        VStack(spacing: 20) {
            Image(systemName: "bell.slash")
                .font(.system(size: 56))
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

    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Image(systemName: "bell.slash")
                .font(.system(size: 56))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No alerts")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("You're all caught up.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }

    // ── Filter bar ────────────────────────────────────────────────────────

    private var filterBar: some View {
        VStack(spacing: 0) {
            // Row 1: count + dismiss-all + refresh
            HStack(spacing: 12) {
                Text(vm.unreadCount > 0 ? "\(vm.unreadCount) unread" : "\(vm.alerts.count) alerts")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                Spacer()
                Button { vm.dismissAll() } label: {
                    Image(systemName: "xmark.circle")
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                .accessibilityLabel("Dismiss all")
                Button { vm.refresh() } label: {
                    Image(systemName: "arrow.clockwise")
                        .foregroundStyle(DatawatchColors.primary)
                }
                .accessibilityLabel("Refresh")
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            // Row 2: severity chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(AlertsViewModel.AlertSeverityFilter.allCases, id: \.self) { filter in
                        severityChip(filter)
                    }
                }
                .padding(.horizontal, 16)
            }
            .padding(.bottom, 6)

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
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(DatawatchColors.surface)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            Divider().background(DatawatchColors.border)
        }
        .background(DatawatchColors.background)
    }

    @ViewBuilder
    private func severityChip(_ filter: AlertsViewModel.AlertSeverityFilter) -> some View {
        let selected = vm.severityFilter == filter
        let color: Color = {
            switch filter {
            case .all: return DatawatchColors.onSurfaceMuted
            case .prompt, .warning: return DatawatchColors.warning
            case .error: return DatawatchColors.error
            case .info: return DatawatchColors.onSurfaceMuted
            }
        }()
        Button { vm.severityFilter = filter } label: {
            Text(filter.rawValue)
                .font(DatawatchFonts.badge)
                .foregroundStyle(selected ? DatawatchColors.background : color)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(selected ? color : color.opacity(0.15))
                .clipShape(Capsule())
                .overlay(Capsule().stroke(color.opacity(0.4), lineWidth: 1))
        }
    }

    // ── Alert list ────────────────────────────────────────────────────────

    private var alertListView: some View {
        VStack(spacing: 0) {
            filterBar
            List {
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
                    Text(relativeTime(from: alert.createdAt))
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
        case .error: return DatawatchColors.error.opacity(0.05)
        default: return Color.clear
        }
    }

    @ViewBuilder
    private var badgeView: some View {
        if isPrompt {
            badgeLabel("PROMPT", fg: Color(hex: 0x0F1117), bg: DatawatchColors.warning)
        } else {
            switch alert.severity {
            case .error:
                badgeLabel("ERROR", fg: .white, bg: DatawatchColors.error)
            case .warning:
                badgeLabel("WARN", fg: Color(hex: 0x0F1117), bg: DatawatchColors.warning)
            default:
                badgeLabel("INFO", fg: DatawatchColors.onSurfaceMuted, bg: DatawatchColors.surface2)
            }
        }
    }

    private func badgeLabel(_ text: String, fg: Color, bg: Color) -> some View {
        Text(text)
            .font(DatawatchFonts.badge)
            .foregroundStyle(fg)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(bg)
            .clipShape(Capsule())
    }

    private func relativeTime(from instant: Kotlinx_datetimeInstant) -> String {
        let epochMs = instant.toEpochMilliseconds()
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let delta = Date().timeIntervalSince(date)
        switch delta {
        case ..<60: return "just now"
        case 60..<3600: return "\(Int(delta / 60))m ago"
        case 3600..<86400: return "\(Int(delta / 3600))h ago"
        default: return "\(Int(delta / 86400))d ago"
        }
    }
}

#if DEBUG
#Preview {
    NavigationStack { AlertsView() }
        .preferredColorScheme(.dark)
}
#endif
