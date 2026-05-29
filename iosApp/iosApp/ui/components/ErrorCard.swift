import SwiftUI

/// Error state card with Retry action — matches Android's `ErrorCard` composable.
/// Fail-fast: shows the error immediately, no hidden retry loops.
struct ErrorCard: View {
    let message: String
    var onRetry: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(.largeTitle))
                .imageScale(.large)
                .foregroundStyle(DatawatchColors.error)
                .accessibilityHidden(true)

            Text(message)
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            if let onRetry {
                Button("Retry", action: onRetry)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.primary)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .overlay(
                        Capsule().stroke(DatawatchColors.primary, lineWidth: 1)
                    )
                    .accessibilityHint("Attempts to reconnect")
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Error: \(message)")
    }
}

#if DEBUG
#Preview("With retry") {
    ErrorCard(message: "Could not connect to server. Check your network connection.") {
        print("retry tapped")
    }
    .preferredColorScheme(.dark)
}

#Preview("No retry") {
    ErrorCard(message: "Session expired. Please sign in again.")
        .preferredColorScheme(.dark)
}
#endif
