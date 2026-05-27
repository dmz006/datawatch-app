import SwiftUI

/// Count chip showing Wait / Run / Total — matches Android's `SessionCountChip.kt`.
/// Teal on chip-background, arranged horizontally.
struct SessionCountChip: View {
    let waiting: Int
    let running: Int
    let total: Int

    var body: some View {
        HStack(spacing: 6) {
            chip(count: waiting, label: "Wait")
            chip(count: running, label: "Run")
            chip(count: total,   label: "Total")
        }
    }

    private func chip(count: Int, label: String) -> some View {
        HStack(spacing: 3) {
            Text("\(count)")
                .font(DatawatchFonts.badge)
                .foregroundStyle(DatawatchColors.primary)
            Text(label)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(DatawatchColors.chipBackground)
        .clipShape(Capsule())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(count) \(label.lowercased())")
    }
}

#if DEBUG
#Preview {
    VStack(spacing: 16) {
        SessionCountChip(waiting: 3, running: 1, total: 4)
        SessionCountChip(waiting: 0, running: 0, total: 0)
        SessionCountChip(waiting: 12, running: 7, total: 19)
    }
    .padding()
    .background(DatawatchColors.background)
    .preferredColorScheme(.dark)
}
#endif
