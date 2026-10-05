import SwiftUI

/// Animated splash eye with label — shown while connecting or loading data.
/// Matches Android's full-screen loading composable.
struct LoadingIndicator: View {
    var message: String = "Connecting…"

    var body: some View {
        VStack(spacing: 16) {
            // D10a: the splash eye replaces the system spinner (Android
            // DatawatchLoadingContent `EyeOnlyAnimated`).
            SplashEyeView()
                .frame(width: 40, height: 40)

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
