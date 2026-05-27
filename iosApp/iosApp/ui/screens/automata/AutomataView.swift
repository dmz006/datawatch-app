import SwiftUI
import DatawatchShared

/// Automata tab — Story 7 scope.
/// Shows a static placeholder card per configured profile.
/// Full automata rule management is deferred to a future story.
struct AutomataView: View {
    @EnvironmentObject private var store: ServerProfileStore

    var body: some View {
        Group {
            if store.profiles.isEmpty {
                emptyStateView
            } else {
                profileListView
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(.principal) {
                HeaderView(title: "Automata")
            }
        }
    }

    // ── Empty state ───────────────────────────────────────────────────────

    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Image(systemName: "arrow.trianglehead.2.counterclockwise")
                .font(.system(size: 56))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)

            Text("No server connected")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)

            Text("Connect a server to view automation rules.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // ── Profile list ──────────────────────────────────────────────────────

    private var profileListView: some View {
        ScrollView {
            VStack(spacing: 0) {
                headerBanner
                    .padding(.horizontal)
                    .padding(.top, 16)
                    .padding(.bottom, 8)

                ForEach(store.profiles, id: \.id) { profile in
                    AutomataProfileCard(profile: profile)
                        .padding(.horizontal)
                        .padding(.vertical, 6)
                }
            }
            .padding(.bottom, 16)
        }
    }

    private var headerBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "arrow.trianglehead.2.counterclockwise")
                .foregroundStyle(DatawatchColors.primary)
            Text("Automata — powered by your datawatch server")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Spacer()
        }
    }
}

// ── Per-profile placeholder card ──────────────────────────────────────────

private struct AutomataProfileCard: View {
    let profile: ServerProfile

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "circle.hexagonpath")
                    .foregroundStyle(DatawatchColors.primary)
                Text(profile.displayName)
                    .font(DatawatchFonts.titleMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                Spacer()
            }

            Text("Automation rules loading...")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)

            Divider()
                .background(DatawatchColors.border)

            Link(destination: URL(string: profile.baseUrl)!) {
                HStack(spacing: 6) {
                    Image(systemName: "safari")
                        .font(DatawatchFonts.labelSmall)
                    Text("View in web UI")
                        .font(DatawatchFonts.bodyMedium)
                }
                .foregroundStyle(DatawatchColors.primary)
            }
            .accessibilityLabel("Open \(profile.displayName) in web browser")
        }
        .padding()
        .background(DatawatchColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(DatawatchColors.border, lineWidth: 1)
        )
    }
}

#if DEBUG
#Preview("With profiles") {
    NavigationStack { AutomataView() }
        .preferredColorScheme(.dark)
}

#Preview("Empty") {
    NavigationStack { AutomataView() }
        .preferredColorScheme(.dark)
}
#endif
