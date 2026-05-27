import SwiftUI
import DatawatchShared

/// Sessions tab — live list of Claude / agent sessions for the active server.
struct SessionsView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var viewModel = SessionsViewModel()

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
                Button {
                    // Story 9: Start session placeholder
                } label: {
                    Image(systemName: "plus")
                        .foregroundStyle(DatawatchColors.primary)
                }
                .accessibilityLabel("Start new session")
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
        List {
            if viewModel.sessions.isEmpty {
                emptySessionsRow
            } else {
                ForEach(viewModel.sessions, id: \.id) { session in
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
            HStack(spacing: 12) {
                // State indicator dot
                Circle()
                    .fill(dotColor(for: session.state))
                    .frame(width: 10, height: 10)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 4) {
                    // Session name/task
                    Text(sessionDisplayName(session))
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .lineLimit(1)

                    HStack(spacing: 6) {
                        // State label
                        Text(stateLabel(for: session.state))
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(dotColor(for: session.state))

                        // Backend chip
                        if let backend = session.backend, !backend.isEmpty {
                            Text(backend)
                                .font(DatawatchFonts.badge)
                                .foregroundStyle(DatawatchColors.primary)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(DatawatchColors.chipBackground)
                                .clipShape(Capsule())
                        }

                        // Needs-input badge
                        if session.needsInput {
                            Text("INPUT")
                                .font(DatawatchFonts.badge)
                                .foregroundStyle(DatawatchColors.secondary)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(DatawatchColors.secondary.opacity(0.15))
                                .clipShape(Capsule())
                        }
                    }
                }

                Spacer()
            }
            .padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .listRowBackground(DatawatchColors.surface)
        .listRowSeparatorTint(DatawatchColors.border)
        .accessibilityLabel(accessibilityLabel(for: session))
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private func sessionDisplayName(_ session: Session) -> String {
        if let name = session.name, !name.isEmpty { return name }
        if let task = session.taskSummary, !task.isEmpty { return task }
        return session.id
    }

    private func dotColor(for state: SessionState) -> Color {
        switch state {
        case .running:   return DatawatchColors.primary
        case .waiting:   return DatawatchColors.secondary
        default:         return DatawatchColors.onSurfaceMuted
        }
    }

    private func stateLabel(for state: SessionState) -> String {
        switch state {
        case .running:   return "Running"
        case .waiting:   return "Waiting"
        case .completed: return "Completed"
        case .killed:    return "Killed"
        case .error:     return "Error"
        default:         return "Unknown"
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
