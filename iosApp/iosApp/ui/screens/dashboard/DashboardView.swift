import SwiftUI
import DatawatchShared

// ── Model ─────────────────────────────────────────────────────────────────────

struct DashboardServerCard: Identifiable {
    var id: String { profile.id }
    let profile: ServerProfile
    var sessions: [Session] = []
    var stats: StatsDto? = nil
    var error: String? = nil
    var isLoading: Bool = true
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@MainActor
final class DashboardViewModel: ObservableObject {

    @Published private(set) var cards: [DashboardServerCard] = []

    // ── Public API ────────────────────────────────────────────────────────

    /// Rebuild card list from the current profiles and trigger parallel fetches.
    func refresh(profiles: [ServerProfile]) {
        // Preserve existing card data while adding / removing cards.
        var updated: [DashboardServerCard] = []
        for profile in profiles {
            if let existing = cards.first(where: { $0.id == profile.id }) {
                var card = existing
                card.isLoading = true
                card.error = nil
                updated.append(card)
            } else {
                updated.append(DashboardServerCard(profile: profile))
            }
        }
        cards = updated

        for profile in profiles {
            fetchCard(for: profile)
        }
    }

    // ── Private ───────────────────────────────────────────────────────────

    private func fetchCard(for profile: ServerProfile) {
        let group = DispatchGroup()

        var fetchedSessions: [Session] = []
        var fetchedStats: StatsDto? = nil
        var fetchError: String? = nil

        // Sessions fetch
        group.enter()
        IosServiceLocator.shared.listSessions(
            profile: profile,
            onSuccess: { list in
                fetchedSessions = list
                group.leave()
            },
            onError: { message in
                fetchError = message
                group.leave()
            }
        )

        // Stats fetch
        group.enter()
        IosServiceLocator.shared.getStats(
            profile: profile,
            onSuccess: { dto in
                fetchedStats = dto
                group.leave()
            },
            onError: { _ in
                // Stats failure is non-critical; sessions error takes precedence.
                group.leave()
            }
        )

        group.notify(queue: .main) { [weak self] in
            guard let self else { return }
            if let idx = self.cards.firstIndex(where: { $0.id == profile.id }) {
                self.cards[idx].sessions = fetchedSessions
                self.cards[idx].stats = fetchedStats
                self.cards[idx].error = fetchError
                self.cards[idx].isLoading = false
            }
        }
    }
}

// ── Root View ─────────────────────────────────────────────────────────────────

struct DashboardView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var viewModel = DashboardViewModel()

    var body: some View {
        Group {
            if store.profiles.isEmpty {
                DashboardEmptyState()
            } else {
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(viewModel.cards) { card in
                            ServerCard(card: card)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .background(DatawatchColors.background)
                .refreshable {
                    viewModel.refresh(profiles: store.profiles)
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(.principal) {
                HeaderView(title: "Dashboard")
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                DocsLinkButton(
                    profile: store.profiles.first,
                    anchor: "dashboard"
                )
            }
        }
        .onAppear {
            viewModel.refresh(profiles: store.profiles)
        }
        .onChange(of: store.profiles) { newProfiles in
            viewModel.refresh(profiles: newProfiles)
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

private struct DashboardEmptyState: View {
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "server.rack")
                .font(.system(size: 48))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)

            Text("No servers configured")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)

            Text("Add a server in Settings to get started.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            NavigationLink(destination: SettingsView()) {
                Text("Go to Settings")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.primary)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .overlay(Capsule().stroke(DatawatchColors.primary, lineWidth: 1))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
    }
}

// ── Server card ───────────────────────────────────────────────────────────────

private struct ServerCard: View {
    let card: DashboardServerCard
    @State private var isHighlighted = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // ── Header row ──────────────────────────────────────────────
            HStack(alignment: .center, spacing: 8) {
                StatusDot(card: card)

                VStack(alignment: .leading, spacing: 2) {
                    Text(card.profile.displayName)
                        .font(DatawatchFonts.titleMedium)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .lineLimit(1)

                    Text(card.profile.baseUrl)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }

                Spacer()
            }

            // ── Body ────────────────────────────────────────────────────
            if card.isLoading {
                HStack {
                    ProgressView()
                        .tint(DatawatchColors.primary)
                        .scaleEffect(0.8)
                    Text("Loading…")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            } else if card.error != nil {
                Text("Unreachable")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
            } else {
                SessionsRow(card: card)
                ResourceBars(stats: card.stats)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 10)
                .fill(isHighlighted ? DatawatchColors.chipBackground : DatawatchColors.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(DatawatchColors.border, lineWidth: 1)
                )
        )
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation(.easeInOut(duration: 0.12)) { isHighlighted = true }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                withAnimation(.easeInOut(duration: 0.12)) { isHighlighted = false }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityLabel)
    }

    private var accessibilityLabel: String {
        var parts = [card.profile.displayName]
        if card.isLoading {
            parts.append("Loading")
        } else if let _ = card.error {
            parts.append("Unreachable")
        } else {
            let running = card.sessions.filter { $0.state == .running }.count
            let waiting = card.sessions.filter { $0.state == .waiting }.count
            parts.append("\(running) running, \(waiting) waiting")
        }
        return parts.joined(separator: ", ")
    }
}

// ── Status indicator dot ──────────────────────────────────────────────────────

private struct StatusDot: View {
    let card: DashboardServerCard

    var body: some View {
        Group {
            if card.isLoading {
                ProgressView()
                    .tint(DatawatchColors.primary)
                    .scaleEffect(0.65)
                    .frame(width: 12, height: 12)
            } else if card.error != nil {
                Circle()
                    .fill(DatawatchColors.error)
                    .frame(width: 10, height: 10)
            } else {
                Circle()
                    .fill(DatawatchColors.primary)
                    .frame(width: 10, height: 10)
            }
        }
        .accessibilityHidden(true)
    }
}

// ── Sessions row ──────────────────────────────────────────────────────────────

private struct SessionsRow: View {
    let card: DashboardServerCard

    private var running: Int {
        card.sessions.filter { $0.state == .running }.count
    }
    private var waiting: Int {
        card.sessions.filter { $0.state == .waiting }.count
    }

    var body: some View {
        HStack(spacing: 16) {
            Label("\(running) running", systemImage: "play.fill")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.success)

            Label("\(waiting) waiting", systemImage: "hourglass")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.waiting)
        }
    }
}

// ── CPU / Memory mini-bars ────────────────────────────────────────────────────

private struct ResourceBars: View {
    let stats: StatsDto?

    var body: some View {
        if let stats {
            VStack(spacing: 6) {
                if let cpu = stats.cpuPct {
                    MiniBar(label: "CPU", value: cpu / 100.0)
                }
                if let mem = stats.memPct {
                    MiniBar(label: "MEM", value: mem / 100.0)
                }
            }
        }
    }
}

private struct MiniBar: View {
    let label: String
    let value: Double   // 0.0–1.0

    private var barColor: Color {
        switch value {
        case ..<0.70: DatawatchColors.primary
        case ..<0.90: DatawatchColors.warning
        default:      DatawatchColors.error
        }
    }

    var body: some View {
        HStack(spacing: 8) {
            Text(label)
                .font(DatawatchFonts.badge)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .frame(width: 30, alignment: .leading)

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(DatawatchColors.border)
                        .frame(height: 5)

                    RoundedRectangle(cornerRadius: 2)
                        .fill(barColor)
                        .frame(width: geo.size.width * max(0, min(1, value)), height: 5)
                }
            }
            .frame(height: 5)

            Text(String(format: "%.0f%%", value * 100))
                .font(DatawatchFonts.badge)
                .foregroundStyle(barColor)
                .frame(width: 36, alignment: .trailing)
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

#if DEBUG
#Preview("Dashboard — loaded") {
    NavigationStack { DashboardView() }
        .environmentObject(ServerProfileStore())
        .preferredColorScheme(.dark)
}

#Preview("Dashboard — empty") {
    NavigationStack { DashboardEmptyState() }
        .preferredColorScheme(.dark)
}
#endif
