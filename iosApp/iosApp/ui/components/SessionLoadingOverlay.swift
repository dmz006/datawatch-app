import SwiftUI

/// datawatch connecting splash shown over the terminal until the first
/// `pane_capture` arrives. Mirrors Android's SessionLoadingOverlay stages:
/// "connecting…" (socket not yet up) → "waiting for terminal…" (subscribed,
/// no frame yet).
struct SessionLoadingOverlay: View {
    let status: String

    var body: some View {
        VStack(spacing: 14) {
            // D10a: the PWA/Android connect animation — eye + dot rain + lightning
            // bolt (SplashArt port of splash-art.js startSessionLoading); static
            // frame under Reduce Motion.
            SplashEyeView(bolt: true)
                .frame(width: 220, height: 220)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            Text("datawatch")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text(status)
                .font(DatawatchFonts.bodyMedium)
                .multilineTextAlignment(.center)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .animation(.none, value: status)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(SplashArt.bg)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("datawatch, \(status)")
    }
}

#if DEBUG
#Preview {
    SessionLoadingOverlay(status: "connecting…")
        .preferredColorScheme(.dark)
}
#endif
