import SwiftUI
import DatawatchShared

// ── ViewModel ─────────────────────────────────────────────────────────────

/// WS-first like Android's StatsViewModel (v1.23.113/114): live `stats` frames
/// arrive over a global `/ws` stream at the server's cadence; REST `/api/stats`
/// runs once on start and every 30 s as a fallback, sequentially, only while the
/// screen is visible (the view calls `startPolling` / `stopPolling`).
@MainActor
final class ObserverViewModel: ObservableObject {
    @Published private(set) var stats: StatsDto? = nil
    @Published private(set) var isLoading: Bool = false
    @Published private(set) var error: String? = nil
    @Published private(set) var lastUpdated: Date? = nil

    private var profile: ServerProfile?
    private var pollTask: Task<Void, Never>? = nil
    private var wsSubscription: IosSubscription? = nil
    private var inFlight = false
    private static let restFallbackInterval: Duration = .seconds(30)

    func update(profiles: [ServerProfile]) {
        let newActive = profiles.first
        guard newActive?.id != profile?.id else { return }
        profile = newActive
        if newActive != nil {
            startPolling()
        } else {
            stopPolling()
            stats = nil
            error = nil
        }
    }

    func selectProfile(_ newProfile: ServerProfile) {
        guard newProfile.id != profile?.id else { return }
        profile = newProfile
        stats = nil
        error = nil
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

        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                await self.fetchOnce()
                try? await Task.sleep(for: Self.restFallbackInterval)
            }
        }
    }

    func stopPolling() {
        pollTask?.cancel()
        pollTask = nil
        wsSubscription?.cancel()
        wsSubscription = nil
    }

    private func fetchOnce() async {
        guard let profile, !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        if stats == nil { isLoading = true }
        do {
            apply(stats: try await ServiceLocatorAsync.getStats(profile: profile))
        } catch {
            self.error = error.localizedDescription
            isLoading = false
        }
    }

    private func apply(stats dto: StatsDto) {
        stats = dto
        error = nil
        isLoading = false
        lastUpdated = Date()
    }
}

// ── Main view ─────────────────────────────────────────────────────────────

struct ObserverView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var vm = ObserverViewModel()
    @State private var selectedProfileId: String? = nil

    private var selectedProfile: ServerProfile? {
        if let id = selectedProfileId {
            return store.profiles.first(where: { $0.id == id })
        }
        return store.profiles.first
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
            if store.profiles.count > 1 {
                profilePicker
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                    .background(DatawatchColors.surface)
            }
            if vm.isLoading && vm.stats == nil {
                LoadingIndicator(message: "Loading stats…")
            } else if let err = vm.error, vm.stats == nil {
                ErrorCard(message: err) { vm.startPolling() }
            } else {
                observerContent
            }
        }
        .task(id: selectedProfile?.id ?? "") {
            if let profile = selectedProfile {
                vm.selectProfile(profile)
            }
        }
    }

    private var profilePicker: some View {
        Picker("Server", selection: Binding(
            get: { selectedProfileId ?? store.profiles.first?.id ?? "" },
            set: { selectedProfileId = $0 }
        )) {
            ForEach(store.profiles, id: \.id) { profile in
                Text(profile.displayName).tag(profile.id)
            }
        }
        .pickerStyle(.segmented)
        .accessibilityLabel("Select server profile")
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

    // ── Observer content ──────────────────────────────────────────────────

    private var observerContent: some View {
        ScrollView {
            VStack(spacing: 16) {
                metricsGrid
                    .padding(.horizontal)

                if let stats = vm.stats {
                    uptimeRow(seconds: stats.uptimeSeconds)
                        .padding(.horizontal)
                }

                if let updated = vm.lastUpdated {
                    Text("Updated \(lastUpdatedString(updated))")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .padding(.vertical, 16)
        }
    }

    private var metricsGrid: some View {
        let columns = [GridItem(.flexible()), GridItem(.flexible())]
        let stats = vm.stats
        return LazyVGrid(columns: columns, spacing: 12) {
            MetricCard(
                label: "CPU",
                icon: "cpu",
                value: stats?.cpuPct?.doubleValue,
                formatAsPercent: true
            )
            MetricCard(
                label: "Memory",
                icon: "memorychip",
                value: stats?.memPct?.doubleValue,
                formatAsPercent: true
            )
            MetricCard(
                label: "Disk",
                icon: "internaldrive",
                value: stats?.diskPct?.doubleValue,
                formatAsPercent: true
            )
            MetricCard(
                label: "VRAM",
                icon: "memorychip.fill",
                value: stats?.gpuPct?.doubleValue,
                formatAsPercent: true
            )
            SessionMetricCard(
                label: "Running",
                icon: "play.circle",
                count: Int(stats?.sessionsRunning ?? 0),
                color: DatawatchColors.success
            )
            SessionMetricCard(
                label: "Waiting",
                icon: "clock.circle",
                count: Int(stats?.sessionsWaiting ?? 0),
                color: DatawatchColors.waiting
            )
        }
    }

    private func uptimeRow(seconds: Int64) -> some View {
        HStack {
            Image(systemName: "timer")
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Text("Uptime: \(formatUptime(seconds))")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Spacer()
        }
        .padding()
        .background(DatawatchColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(DatawatchColors.border, lineWidth: 1)
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private func formatUptime(_ seconds: Int64) -> String {
        let s = Int(seconds)
        let days = s / 86400
        let hours = (s % 86400) / 3600
        let mins = (s % 3600) / 60
        if days > 0 {
            return "\(days)d \(hours)h \(mins)m"
        } else if hours > 0 {
            return "\(hours)h \(mins)m"
        } else {
            return "\(mins)m"
        }
    }

    private func lastUpdatedString(_ date: Date) -> String {
        let delta = Date().timeIntervalSince(date)
        if delta < 5 { return "just now" }
        if delta < 60 { return "\(Int(delta))s ago" }
        let mins = Int(delta / 60)
        return "\(mins)m ago"
    }
}

// ── Metric card (percent) ─────────────────────────────────────────────────

private struct MetricCard: View {
    let label: String
    let icon: String
    let value: Double?
    var formatAsPercent: Bool = true

    private var displayText: String {
        guard let v = value else { return "N/A" }
        return String(format: "%.1f%%", v)
    }

    private var accentColor: Color {
        guard let v = value else { return DatawatchColors.onSurfaceMuted }
        switch v {
        case 90...: return DatawatchColors.error
        case 70..<90: return DatawatchColors.warning
        default: return DatawatchColors.primary
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .foregroundStyle(accentColor)
                    .font(DatawatchFonts.bodyMedium)
                Text(label)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer()
            }

            Text(displayText)
                .font(DatawatchFonts.titleLarge)
                .foregroundStyle(accentColor)
                .minimumScaleFactor(0.7)
                .lineLimit(1)

            if let v = value {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(DatawatchColors.border)
                            .frame(height: 4)
                        RoundedRectangle(cornerRadius: 2)
                            .fill(accentColor)
                            .frame(width: geo.size.width * CGFloat(min(v, 100) / 100), height: 4)
                    }
                }
                .frame(height: 4)
            }
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(DatawatchColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(DatawatchColors.border, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(label): \(displayText)")
    }
}

// ── Session count card ────────────────────────────────────────────────────

private struct SessionMetricCard: View {
    let label: String
    let icon: String
    let count: Int
    var color: Color = DatawatchColors.primary

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .foregroundStyle(color)
                    .font(DatawatchFonts.bodyMedium)
                Text(label)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer()
            }

            Text("\(count)")
                .font(DatawatchFonts.titleLarge)
                .foregroundStyle(color)
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(DatawatchColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(DatawatchColors.border, lineWidth: 1)
        )
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Sessions \(label): \(count)")
    }
}

#if DEBUG
#Preview {
    NavigationStack { ObserverView() }
        .preferredColorScheme(.dark)
}
#endif
