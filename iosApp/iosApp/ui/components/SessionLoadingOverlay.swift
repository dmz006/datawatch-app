import SwiftUI

/// datawatch connecting splash shown over the terminal until the first
/// `pane_capture` arrives. Mirrors Android's SessionLoadingOverlay stages:
/// "connecting…" (socket not yet up) → "waiting for terminal…" (subscribed,
/// no frame yet).
struct SessionLoadingOverlay: View {
    let status: String
    @State private var pulse = false

    var body: some View {
        VStack(spacing: 18) {
            ZStack {
                Circle()
                    .stroke(DatawatchColors.primary.opacity(pulse ? 0.15 : 0.6), lineWidth: 2)
                    .frame(width: 84, height: 84)
                    .scaleEffect(pulse ? 1.18 : 0.92)
                Image(systemName: "eye")
                    .font(.system(size: 34, weight: .light))
                    .foregroundStyle(DatawatchColors.secondary)
                    .scaleEffect(pulse ? 1.04 : 0.96)
            }
            .animation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true), value: pulse)

            Text("datawatch")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text(status)
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .animation(.none, value: status)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
        .onAppear { pulse = true }
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
