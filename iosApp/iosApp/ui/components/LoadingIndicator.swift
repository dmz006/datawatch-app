import SwiftUI

/// Circular spinner with label — shown while connecting or loading data.
/// Matches Android's full-screen loading composable.
struct LoadingIndicator: View {
    var message: String = "Connecting…"

    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .tint(DatawatchColors.primary)
                .controlSize(.large)
                .scaleEffect(1.5)

            Text(message)
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(message)
    }
}

#if DEBUG
#Preview {
    LoadingIndicator()
    LoadingIndicator(message: "Loading sessions…")
}
#endif
