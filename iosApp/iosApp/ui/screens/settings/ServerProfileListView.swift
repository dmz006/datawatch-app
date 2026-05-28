import SwiftUI
import DatawatchShared

/// List of configured server profiles. Tapping a row opens EditServerView.
/// The "+" button opens AddServerView.
struct ServerProfileListView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @State private var showAdd = false
    @State private var selectedProfile: ServerProfile?

    var body: some View {
        Group {
            if store.isLoading {
                LoadingIndicator(message: "Loading servers…")
            } else if store.profiles.isEmpty {
                emptyState
            } else {
                profileList
            }
        }
        .sheet(isPresented: $showAdd) {
            AddServerView()
                .environmentObject(store)
        }
        .sheet(item: $selectedProfile) { profile in
            NavigationStack {
                EditServerView(profile: profile)
                    .environmentObject(store)
            }
            .preferredColorScheme(.dark)
        }
    }

    private var profileList: some View {
        List(store.profiles, id: \.id) { profile in
            Button {
                selectedProfile = profile
            } label: {
                ProfileRow(profile: profile)
            }
            .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAdd = true
                } label: {
                    Image(systemName: "plus")
                        .foregroundStyle(DatawatchColors.primary)
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 20) {
            Image(systemName: "server.rack")
                .font(.system(size: 56))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Text("No servers configured")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("Add your first datawatch server to get started.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
            Button {
                showAdd = true
            } label: {
                Label("Add Server", systemImage: "plus.circle.fill")
                    .font(DatawatchFonts.bodyLarge)
            }
            .buttonStyle(.borderedProminent)
            .tint(DatawatchColors.primary)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showAdd = true
                } label: {
                    Image(systemName: "plus")
                        .foregroundStyle(DatawatchColors.primary)
                }
            }
        }
    }
}

// ── Profile row ───────────────────────────────────────────────────────────────

private struct ProfileRow: View {
    let profile: ServerProfile

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(profile.enabled ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
                .frame(width: 8, height: 8)
            VStack(alignment: .leading, spacing: 2) {
                Text(profile.displayName)
                    .font(DatawatchFonts.bodyLarge)
                    .foregroundStyle(DatawatchColors.onSurface)
                Text(profile.baseUrl)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(1)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .padding(.vertical, 4)
    }
}

// ServerProfile must be Identifiable for sheet(item:)
extension ServerProfile: Identifiable {}

#Preview {
    let store = ServerProfileStore()
    NavigationStack {
        ServerProfileListView()
            .environmentObject(store)
            .navigationTitle("Servers")
            .navigationBarTitleDisplayMode(.large)
    }
    .preferredColorScheme(.dark)
}
