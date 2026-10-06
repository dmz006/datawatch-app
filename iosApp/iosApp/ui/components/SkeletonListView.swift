import SwiftUI

/// Skeleton loading list (parity D60a; Android `SessionSkeletonList`): N surface
/// cards, each with a 14 pt bar (65 % / 80 % width alternating) and a 10 pt bar
/// (45 %), shimmering between 0.3 and 0.7 opacity every 900 ms. Static at 0.5
/// under Reduce Motion.
struct SkeletonListView: View {
    var rows: Int = 5
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var bright = false

    var body: some View {
        VStack(spacing: 8) {
            ForEach(0..<rows, id: \.self) { i in
                SkeletonRow(wide: i % 2 == 1, alpha: shimmerAlpha)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { bright = true }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Loading")
    }

    private var shimmerAlpha: Double {
        if reduceMotion { return 0.5 }
        return bright ? 0.7 : 0.3
    }
}

private struct SkeletonRow: View {
    let wide: Bool
    let alpha: Double

    var body: some View {
        GeometryReader { geo in
            let w: Double = Double(geo.size.width)
            VStack(alignment: .leading, spacing: 8) {
                bar(width: w * (wide ? 0.80 : 0.65), height: 14)
                bar(width: w * 0.45, height: 10)
            }
        }
        .frame(height: 32)
        .padding(12)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 8))
    }

    private func bar(width: Double, height: Double) -> some View {
        RoundedRectangle(cornerRadius: 4)
            .fill(DatawatchColors.onSurface.opacity(alpha))
            .frame(width: CGFloat(width), height: CGFloat(height))
    }
}

/// Per-card loading placeholder: the animated datawatch eye (the splash eye,
/// `SplashEyeView`) with a pulsing "Loading…" label — compact, inside a card.
/// Operator 2026-10-06: card loading must show the animated icon, not a bare
/// "Loading…" line or shimmer bars. Android `PwaLoadingText` /
/// `DatawatchLoadingContent` is the same component.
struct CardSkeleton: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var bright = false

    var body: some View {
        VStack(spacing: 8) {
            SplashEyeView()
                .frame(width: 32, height: 32)
            Text(L("Loading…"))
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .tracking(1.5)
                .foregroundStyle(DatawatchColors.secondary.opacity(reduceMotion ? 0.7 : (bright ? 0.9 : 0.35)))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.85).repeatForever(autoreverses: true)) { bright = true }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Loading…"))
    }
}
