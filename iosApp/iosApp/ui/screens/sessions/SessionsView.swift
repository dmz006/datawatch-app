import SwiftUI
import DatawatchShared

/// Sessions tab — live list of Claude / agent sessions for the active server.
struct SessionsView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var viewModel = SessionsViewModel()

    @State private var filterText: String = ""
    @State private var showFilter: Bool = false
    @State private var stateFilter: SessionStateFilter = .all

    enum SessionStateFilter: String, CaseIterable {
        case all       = "All"
        case active    = "Active"
        case running   = "Running"
        case waiting   = "Waiting"
        case done      = "Done"
    }

    var body: some View {
        ZStack {
            DatawatchColors.background.ignoresSafeArea()

            content
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(.principal) {
                HeaderView(
                    title: "Sessions",
                    serverName: viewModel.activeProfile?.displayName
                )
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 4) {
                    DocsLinkButton(
                        profile: viewModel.activeProfile,
                        anchor: "sessions-list"
                    )
                    Button {
                        withAnimation { showFilter.toggle() }
                        if !showFilter { filterText = ""; stateFilter = .all }
                    } label: {
                        Image(systemName: showFilter ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                            .foregroundStyle(DatawatchColors.primary)
                    }
                    .accessibilityLabel(showFilter ? "Hide filter" : "Filter sessions")

                    Button {
                        // Story 9: Start session placeholder
                    } label: {
                        Image(systemName: "plus")
                            .foregroundStyle(DatawatchColors.primary)
                    }
                    .accessibilityLabel("Start new session")
                }
            }
        }
        // React to profile changes.
        .onChange(of: store.profiles) { profiles in
            viewModel.update(profiles: profiles)
        }
        .onAppear {
            viewModel.update(profiles: store.profiles)
            viewModel.startPolling()
        }
        .onDisappear {
            viewModel.stopPolling()
        }
    }

    // ── Body states ───────────────────────────────────────────────────────

    @ViewBuilder
    private var content: some View {
        VStack(spacing: 0) {
            ConnectionStatusBanner(state: connectionState)

            if viewModel.isLoading && viewModel.sessions.isEmpty {
                LoadingIndicator(message: "Loading sessions…")
            } else if let errorMsg = viewModel.error, viewModel.sessions.isEmpty {
                ErrorCard(message: errorMsg) {
                    viewModel.refresh()
                }
            } else if viewModel.activeProfile == nil {
                emptyNoProfile
            } else {
                sessionList
            }
        }
    }

    private var sessionList: some View {
        VStack(spacing: 0) {
            if showFilter {
                filterBar
            }
            List {
                if filteredSessions.isEmpty && (!filterText.isEmpty || stateFilter != .all) {
                    Text(filterText.isEmpty ? "No \(stateFilter.rawValue.lowercased()) sessions" : "No sessions match \"\(filterText)\"")
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                } else if viewModel.sessions.isEmpty {
                    emptySessionsRow
                } else {
                    ForEach(filteredSessions, id: \.id) { session in
                        sessionRow(session)
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .refreshable {
                viewModel.refresh()
            }
        }
    }

    private var filterBar: some View {
        VStack(spacing: 0) {
            // Row 1: state filter chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(SessionStateFilter.allCases, id: \.self) { filter in
                        stateChip(filter)
                    }
                }
                .padding(.horizontal, 12)
            }
            .padding(.vertical, 6)
            .background(DatawatchColors.background)

            // Row 2: text search
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                TextField("Filter by name / task / id / backend", text: $filterText)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                if !filterText.isEmpty {
                    Button { filterText = "" } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(DatawatchColors.surface)
            Divider().background(DatawatchColors.border)
        }
    }

    @ViewBuilder
    private func stateChip(_ filter: SessionStateFilter) -> some View {
        let selected = stateFilter == filter
        let color: Color = {
            switch filter {
            case .all:     return DatawatchColors.onSurfaceMuted
            case .active:  return DatawatchColors.primary
            case .running: return DatawatchColors.success
            case .waiting: return DatawatchColors.waiting
            case .done:    return DatawatchColors.onSurfaceMuted
            }
        }()
        Button { stateFilter = filter } label: {
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

    private var filteredSessions: [Session] {
        var result = viewModel.sessions

        switch stateFilter {
        case .all:     break
        case .active:  result = result.filter { $0.state == .running || $0.state == .waiting }
        case .running: result = result.filter { $0.state == .running }
        case .waiting: result = result.filter { $0.state == .waiting }
        case .done:    result = result.filter { $0.state == .completed || $0.state == .killed || $0.state == .error }
        }

        if !filterText.isEmpty {
            let q = filterText.lowercased()
            result = result.filter { s in
                s.id.lowercased().contains(q) ||
                (s.name?.lowercased().contains(q) ?? false) ||
                (s.taskSummary?.lowercased().contains(q) ?? false) ||
                (s.backend?.lowercased().contains(q) ?? false)
            }
        }
        return result
    }

    // ── Empty states ──────────────────────────────────────────────────────

    private var emptySessionsRow: some View {
        HStack {
            Spacer()
            VStack(spacing: 12) {
                Image(systemName: "terminal")
                    .font(.system(size: 40))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .accessibilityHidden(true)
                Text("No sessions — start one in the web UI")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, 60)
            Spacer()
        }
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private var emptyNoProfile: some View {
        VStack(spacing: 16) {
            Image(systemName: "server.rack")
                .font(.system(size: 40))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No server configured")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Text("Add a server in Settings to get started.")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // ── Session row ───────────────────────────────────────────────────────

    @ViewBuilder
    private func sessionRow(_ session: Session) -> some View {
        NavigationLink {
            if let profile = viewModel.activeProfile {
                SessionDetailView(session: session, profile: profile)
            }
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                // Row 1: name + state pill
                HStack(spacing: 8) {
                    Text(sessionDisplayName(session))
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .lineLimit(1)
                    Spacer()
                    statePill(for: session.state)
                }

                // Row 2: task text (if different from name)
                if let task = session.taskSummary, !task.isEmpty,
                   task != session.name {
                    Text(task)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(2)
                }

                // Row 3: badges
                HStack(spacing: 6) {
                    if let backend = session.backend, !backend.isEmpty {
                        Text(backend.uppercased())
                            .font(DatawatchFonts.badge)
                            .foregroundStyle(DatawatchColors.secondary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(DatawatchColors.secondary.opacity(0.12))
                            .clipShape(Capsule())
                    }
                    if session.state == .waiting {
                        Text("WAITING INPUT")
                            .font(DatawatchFonts.badge)
                            .foregroundStyle(DatawatchColors.waiting)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(DatawatchColors.waiting.opacity(0.15))
                            .clipShape(Capsule())
                    }
                    if session.muted {
                        Image(systemName: "bell.slash.fill")
                            .font(.system(size: 10))
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .accessibilityLabel("Muted")
                    }
                    Spacer()
                }
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .listRowBackground(DatawatchColors.surface)
        .listRowSeparatorTint(DatawatchColors.border)
        .accessibilityLabel(accessibilityLabel(for: session))
    }

    @ViewBuilder
    private func statePill(for state: SessionState) -> some View {
        let color = dotColor(for: state)
        Text(stateLabel(for: state))
            .font(DatawatchFonts.badge)
            .foregroundStyle(color)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(color.opacity(0.15))
            .clipShape(Capsule())
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private func sessionDisplayName(_ session: Session) -> String {
        if let name = session.name, !name.isEmpty { return name }
        if let task = session.taskSummary, !task.isEmpty { return task }
        return session.id
    }

    private func dotColor(for state: SessionState) -> Color {
        switch state {
        case .running:   return DatawatchColors.success
        case .waiting:   return DatawatchColors.waiting
        case .error:     return DatawatchColors.error
        default:         return DatawatchColors.onSurfaceMuted
        }
    }

    private func stateLabel(for state: SessionState) -> String {
        switch state {
        case .running:   return "RUNNING"
        case .waiting:   return "WAITING INPUT"
        case .completed: return "COMPLETED"
        case .killed:    return "KILLED"
        case .error:     return "ERROR"
        default:         return "UNKNOWN"
        }
    }

    private func accessibilityLabel(for session: Session) -> String {
        "\(sessionDisplayName(session)), \(stateLabel(for: session.state))"
    }

    private var connectionState: ConnectionState {
        if viewModel.error != nil && !viewModel.sessions.isEmpty {
            return .reconnecting(attempt: 1)
        }
        if viewModel.isLoading && viewModel.sessions.isEmpty {
            return .connecting
        }
        return .connected
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        SessionsView()
            .environmentObject(ServerProfileStore())
    }
    .preferredColorScheme(.dark)
}
#endif
