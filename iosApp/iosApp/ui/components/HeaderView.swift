import SwiftUI

/// Navigation bar header — title + optional subtitle + server name chip.
/// Matches Android's `HeaderComponents.kt` composable pattern and PWA header.
///
/// Usage: attach via `.navigationTitle` overrides or embed directly in a
/// `ToolbarItem(.principal)` for full customisation.
struct HeaderView: View {
    let title: String
    var subtitle: String? = nil
    var serverName: String? = nil

    var body: some View {
        VStack(spacing: 2) {
            Text(title)
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .accessibilityAddTraits(.isHeader)

            if let subtitle {
                Text(subtitle)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }

            if let serverName {
                Text(serverName)
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.primary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(DatawatchColors.chipBackground)
                    .clipShape(Capsule())
                    .accessibilityLabel("Server: \(serverName)")
            }
        }
    }
}

#if DEBUG
#Preview("Header — full") {
    NavigationStack {
        Color(DatawatchColors.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(.principal) {
                    HeaderView(
                        title: "Sessions",
                        subtitle: "3 active",
                        serverName: "dev-server"
                    )
                }
            }
    }
    .preferredColorScheme(.dark)
}

#Preview("Header — title only") {
    NavigationStack {
        Color(DatawatchColors.background)
            .toolbar {
                ToolbarItem(.principal) {
                    HeaderView(title: "Alerts")
                }
            }
    }
    .preferredColorScheme(.dark)
}
#endif
