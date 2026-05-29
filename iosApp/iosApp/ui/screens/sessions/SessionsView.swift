import SwiftUI
import DatawatchShared

/// Sessions tab — live list of Claude / agent sessions for the active server.
struct SessionsView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var viewModel = SessionsViewModel()

    @State private var filterText: String = ""
    @State private var showFilter: Bool = false
    @State private var stateFilter: SessionStateFilter = .all
    @State private var sortOrder: SortOrder = .recentActivity

    // Confirmation state for per-row actions.
    @State private var sessionToKill: Session? = nil
    @State private var sessionToRestart: Session? = nil
    @State private var sessionToDelete: Session? = nil
    @State private var actionInProgress: String? = nil

    enum SessionStateFilter: String, CaseIterable {
        case all     = "All"
        case active  = "Active"
        case waiting = "Waiting"
        case done    = "Done"
    }

    enum SortOrder: String, CaseIterable {
        case recentActivity = "Recent activity"
        case startedAt      = "Started"
        case name           = "Name"
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
                    subtitle: sessionsSubtitle,
                    serverName: viewModel.activeProfile?.displayName
                )
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 4) {
                    if viewModel.isLoading && !viewModel.sessions.isEmpty {
                        ProgressView()
                            .tint(DatawatchColors.onSurfaceMuted)
                            .controlSize(.mini)
                            .accessibilityLabel("Refreshing")
                    }
                    DocsLinkButton(
                        profile: viewModel.activeProfile,
                        anchor: "sessions-list"
                    )
                    Button {
                        withAnimation { showFilter.toggle() }
                    } label: {
                        Image(systemName: showFilter ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                            .foregroundStyle(DatawatchColors.primary)
                    }
                    .accessibilityLabel(showFilter ? "Hide filter" : "Filter sessions")

                    AlertsBellButton()
                    ReachabilityDotView(profile: viewModel.activeProfile)
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
        .alert("Kill session?", isPresented: Binding(
            get: { sessionToKill != nil },
            set: { if !$0 { sessionToKill = nil } }
        )) {
            Button("Kill", role: .destructive) { performKill() }
            Button("Cancel", role: .cancel) { sessionToKill = nil }
        } message: {
            Text("This stops the session on the server and cannot be undone.")
        }
        .alert("Restart session?", isPresented: Binding(
            get: { sessionToRestart != nil },
            set: { if !$0 { sessionToRestart = nil } }
        )) {
            Button("Restart") { performRestart() }
            Button("Cancel", role: .cancel) { sessionToRestart = nil }
        }
        .alert("Delete session?", isPresented: Binding(
            get: { sessionToDelete != nil },
            set: { if !$0 { sessionToDelete = nil } }
        )) {
            Button("Delete", role: .destructive) { performDelete() }
            Button("Cancel", role: .cancel) { sessionToDelete = nil }
        } message: {
            if let s = sessionToDelete {
                Text("Permanently delete \"\(s.name ?? s.taskSummary ?? s.id)\"?")
            }
        }
    }

    // ── Row actions ───────────────────────────────────────────────────────

    private func performKill() {
        guard let session = sessionToKill, let profile = viewModel.activeProfile else { return }
        sessionToKill = nil
        actionInProgress = session.id
        IosServiceLocator.shared.killSession(
            profile: profile,
            sessionId: session.id,
            onSuccess: {
                DispatchQueue.main.async {
                    self.actionInProgress = nil
                    self.viewModel.refresh()
                }
            },
            onError: { _ in
                DispatchQueue.main.async { self.actionInProgress = nil }
            }
        )
    }

    private func performRestart() {
        guard let session = sessionToRestart, let profile = viewModel.activeProfile else { return }
        sessionToRestart = nil
        actionInProgress = session.id
        IosServiceLocator.shared.restartSession(
            profile: profile,
            sessionId: session.id,
            onSuccess: {
                DispatchQueue.main.async {
                    self.actionInProgress = nil
                    self.viewModel.refresh()
                }
            },
            onError: { _ in
                DispatchQueue.main.async { self.actionInProgress = nil }
            }
        )
    }

    private func performDelete() {
        guard let session = sessionToDelete, let profile = viewModel.activeProfile else { return }
        sessionToDelete = nil
        actionInProgress = session.id
        IosServiceLocator.shared.deleteSession(
            profile: profile,
            sessionId: session.id,
            onSuccess: {
                DispatchQueue.main.async {
                    self.actionInProgress = nil
                    self.viewModel.refresh()
                }
            },
            onError: { _ in
                DispatchQueue.main.async { self.actionInProgress = nil }
            }
        )
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
            // Row 1: state filter chips + sort menu
            HStack(spacing: 0) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(SessionStateFilter.allCases, id: \.self) { filter in
                            stateChip(filter)
                        }
                    }
                    .padding(.horizontal, 12)
                }

                Menu {
                    ForEach(SortOrder.allCases, id: \.self) { order in
                        Button {
                            sortOrder = order
                        } label: {
                            if sortOrder == order {
                                Label(order.rawValue, systemImage: "checkmark")
                            } else {
                                Text(order.rawValue)
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 3) {
                        Image(systemName: "arrow.up.arrow.down")
                        Text(sortOrder.rawValue)
                            .lineLimit(1)
                    }
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(DatawatchColors.chipBackground)
                    .clipShape(Capsule())
                }
                .padding(.trailing, 12)
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
            case .waiting: return DatawatchColors.waiting
            case .done:    return DatawatchColors.onSurfaceMuted
            }
        }()
        let count = chipCount(for: filter)
        Button { stateFilter = filter } label: {
            Text(count > 0 ? "\(filter.rawValue) (\(count))" : filter.rawValue)
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
        case .active:  result = result.filter { $0.state == .running || $0.state == .waiting || $0.state == .rateLimited }
        case .waiting: result = result.filter { $0.state == .waiting }
        case .done:    result = result.filter { $0.state == .completed || $0.state == .killed || $0.state == .error }
        }

        if !filterText.isEmpty {
            let q = filterText.lowercased()
            result = result.filter { s in
                s.id.lowercased().contains(q) ||
                (s.name?.lowercased().contains(q) ?? false) ||
                (s.taskSummary?.lowercased().contains(q) ?? false) ||
                (s.backend?.lowercased().contains(q) ?? false) ||
                (s.llmRef?.lowercased().contains(q) ?? false) ||
                (s.computeNodeRef?.lowercased().contains(q) ?? false) ||
                (s.hostnamePrefix?.lowercased().contains(q) ?? false)
            }
        }

        // State-bucket always wins (Waiting → Running → RateLimited → Done),
        // within each bucket apply user sort order.
        result.sort { lhs, rhs in
            let lb = stateBucket(lhs.state), rb = stateBucket(rhs.state)
            if lb != rb { return lb < rb }
            switch sortOrder {
            case .recentActivity:
                return lhs.lastActivityAt.toEpochMilliseconds() > rhs.lastActivityAt.toEpochMilliseconds()
            case .startedAt:
                return lhs.createdAt.toEpochMilliseconds() > rhs.createdAt.toEpochMilliseconds()
            case .name:
                let a = (lhs.name ?? lhs.taskSummary ?? lhs.id).lowercased()
                let b = (rhs.name ?? rhs.taskSummary ?? rhs.id).lowercased()
                return a < b
            }
        }

        return result
    }

    private func chipCount(for filter: SessionStateFilter) -> Int {
        let all = viewModel.sessions
        switch filter {
        case .all:     return all.count
        case .active:  return all.filter { $0.state == .running || $0.state == .waiting || $0.state == .rateLimited }.count
        case .waiting: return all.filter { $0.state == .waiting }.count
        case .done:    return all.filter { $0.state == .completed || $0.state == .killed || $0.state == .error }.count
        }
    }

    private func stateBucket(_ state: SessionState) -> Int {
        switch state {
        case .waiting:     return 0
        case .running:     return 1
        case .rateLimited: return 2
        default:           return 3
        }
    }

    // ── Empty states ──────────────────────────────────────────────────────

    private var emptySessionsRow: some View {
        HStack {
            Spacer()
            VStack(spacing: 12) {
                Image(systemName: "terminal")
                    .font(.system(.largeTitle))
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
                .font(.system(.largeTitle))
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
                    if let agentId = session.agentId {
                        Text("⬡ \(agentId)")
                            .font(DatawatchFonts.badge)
                            .foregroundStyle(DatawatchColors.secondary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(DatawatchColors.secondary.opacity(0.12))
                            .clipShape(Capsule())
                            .accessibilityLabel("Worker \(agentId)")
                    }
                    if session.backend == "council-virtual" {
                        Text("🎭")
                            .font(DatawatchFonts.badge)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(DatawatchColors.secondary.opacity(0.12))
                            .clipShape(Capsule())
                            .accessibilityLabel("Council session")
                    }
                    if let hostname = session.hostnamePrefix, !hostname.isEmpty {
                        Text(hostname)
                            .font(DatawatchFonts.badge)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(DatawatchColors.onSurfaceMuted.opacity(0.10))
                            .clipShape(Capsule())
                    }
                    if session.lastResponse != nil {
                        Image(systemName: "doc.text")
                            .font(DatawatchFonts.labelSmall)
                            .imageScale(.small)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .accessibilityLabel("Has response")
                    }
                    if session.muted {
                        Image(systemName: "bell.slash.fill")
                            .font(DatawatchFonts.labelSmall)
                            .imageScale(.small)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .accessibilityLabel("Muted")
                    }
                    Spacer()
                    Text(relativeTime(session.lastActivityAt))
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }

                // Row 4: prompt context for waiting sessions
                if session.state == .waiting, let ctx = session.promptContext ?? session.lastPrompt, !ctx.isEmpty {
                    Text(ctx)
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(2)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(DatawatchColors.waiting.opacity(0.08))
                        .overlay(Rectangle().fill(DatawatchColors.waiting).frame(width: 2), alignment: .leading)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
            .opacity(isDoneState(session.state) ? 0.6 : 1.0)
        }
        .listRowBackground(DatawatchColors.surface)
        .listRowSeparatorTint(DatawatchColors.border)
        .accessibilityLabel(accessibilityLabel(for: session))
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            if session.state == .running || session.state == .waiting || session.state == .rateLimited {
                Button(role: .destructive) {
                    sessionToKill = session
                } label: {
                    Label("Stop", systemImage: "stop.circle")
                }
            }
            if isDoneState(session.state) {
                Button(role: .destructive) {
                    sessionToDelete = session
                } label: {
                    Label("Delete", systemImage: "trash")
                }
            }
        }
        .swipeActions(edge: .leading, allowsFullSwipe: false) {
            if isDoneState(session.state) {
                Button {
                    sessionToRestart = session
                } label: {
                    Label("Restart", systemImage: "arrow.counterclockwise")
                }
                .tint(DatawatchColors.primary)
            }
        }
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
        case .running:     return DatawatchColors.success
        case .waiting:     return DatawatchColors.waiting
        case .rateLimited: return DatawatchColors.warning
        case .error:       return DatawatchColors.error
        default:           return DatawatchColors.onSurfaceMuted
        }
    }

    private func stateLabel(for state: SessionState) -> String {
        switch state {
        case .running:     return "RUNNING"
        case .waiting:     return "WAITING INPUT"
        case .rateLimited: return "RATE LIMITED"
        case .completed:   return "COMPLETED"
        case .killed:      return "KILLED"
        case .error:       return "ERROR"
        default:           return "UNKNOWN"
        }
    }

    private func isDoneState(_ state: SessionState) -> Bool {
        state == .completed || state == .killed || state == .error
    }

    private func relativeTime(_ instant: Kotlinx_datetimeInstant) -> String {
        let seconds = Int((Date().timeIntervalSince1970 * 1000 - Double(instant.toEpochMilliseconds())) / 1000)
        if seconds < 5   { return "just now" }
        if seconds < 60  { return "\(seconds)s ago" }
        if seconds < 3600 { return "\(seconds / 60)m ago" }
        if seconds < 86400 { return "\(seconds / 3600)h ago" }
        return "\(seconds / 86400)d ago"
    }

    private func accessibilityLabel(for session: Session) -> String {
        "\(sessionDisplayName(session)), \(stateLabel(for: session.state))"
    }

    private var sessionsSubtitle: String? {
        guard !viewModel.sessions.isEmpty else { return nil }
        let running = viewModel.sessions.filter { $0.state == .running || $0.state == .rateLimited }.count
        let waiting = viewModel.sessions.filter { $0.state == .waiting }.count
        if running > 0 && waiting > 0 {
            return "\(running) running · \(waiting) waiting"
        } else if running > 0 {
            return "\(running) running"
        } else if waiting > 0 {
            return "\(waiting) waiting"
        }
        return nil
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
