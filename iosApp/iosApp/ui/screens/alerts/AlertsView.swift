import SwiftUI
import DatawatchShared

// ── ViewModel ─────────────────────────────────────────────────────────────

@MainActor
final class AlertsViewModel: ObservableObject {
    @Published var alerts: [Alert] = []
    @Published var unreadCount: Int = 0
    @Published var isLoading: Bool = false
    @Published var error: String? = nil

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

    /// Remove an alert locally (swipe-to-dismiss placeholder).
    func dismiss(alert: Alert) {
        alerts.removeAll { $0.id == alert.id }
        if !alert.read, unreadCount > 0 {
            unreadCount -= 1
        }
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

    // ── Alert list ────────────────────────────────────────────────────────

    private var alertListView: some View {
        List {
            ForEach(vm.alerts, id: \.id) { alert in
                AlertRow(alert: alert)
                    .listRowBackground(DatawatchColors.surface)
                    .listRowSeparatorTint(DatawatchColors.border)
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
        .refreshable {
            vm.refresh()
        }
    }
}

// ── Alert row ─────────────────────────────────────────────────────────────

private struct AlertRow: View {
    let alert: Alert

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            severityIcon
                .font(.system(size: 22))
                .frame(width: 28)
                .padding(.top, 2)

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(alert.title.isEmpty ? alert.type : alert.title)
                        .font(DatawatchFonts.titleMedium)
                        .foregroundStyle(alert.read ? DatawatchColors.onSurfaceMuted : DatawatchColors.onSurface)
                        .lineLimit(1)
                    Spacer()
                    Text(relativeTime(from: alert.createdAt))
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }

                Text(alert.message)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(1)
            }
        }
        .padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }

    private var severityIcon: some View {
        Group {
            switch alert.severity {
            case .warning:
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(DatawatchColors.secondary)
            case .error:
                Image(systemName: "xmark.octagon.fill")
                    .foregroundStyle(DatawatchColors.error)
            default:
                Image(systemName: "info.circle.fill")
                    .foregroundStyle(DatawatchColors.primary)
            }
        }
    }

    private func relativeTime(from instant: Kotlinx_datetimeInstant) -> String {
        let epochMs = instant.toEpochMilliseconds()
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let delta = Date().timeIntervalSince(date)
        switch delta {
        case ..<60:
            return "just now"
        case 60..<3600:
            let mins = Int(delta / 60)
            return "\(mins)m ago"
        case 3600..<86400:
            let hrs = Int(delta / 3600)
            return "\(hrs)h ago"
        default:
            let days = Int(delta / 86400)
            return "\(days)d ago"
        }
    }
}

#if DEBUG
#Preview {
    NavigationStack { AlertsView() }
        .preferredColorScheme(.dark)
}
#endif
