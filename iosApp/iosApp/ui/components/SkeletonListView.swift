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

/// Per-card loading placeholder — the PWA skeleton shimmer (`style.css`
/// `.skeleton-line` + `@keyframes skeleton-shimmer`, D60): bars at 40 % /
/// 70 % / 90 % width, 12 pt tall, radius 4, a border → surface2 → border
/// gradient sweeping over 1.4 s. Static under Reduce Motion; VoiceOver reads
/// "Loading…". Android `PwaLoadingText` is the same component.
/// Operator 2026-10-06: card loading states must animate, not show a bare
/// "Loading…" line.
struct CardSkeleton: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: CGFloat = -1

    var body: some View {
        GeometryReader { geo in
            let w: CGFloat = geo.size.width
            VStack(alignment: .leading, spacing: 8) {
                bar(width: w * 0.4)
                bar(width: w * 0.7)
                bar(width: w * 0.9)
            }
        }
        .frame(height: 52)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.linear(duration: 1.4).repeatForever(autoreverses: false)) { phase = 1 }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(L("Loading…"))
    }

    private func bar(width: CGFloat) -> some View {
        RoundedRectangle(cornerRadius: 4)
            .fill(
                LinearGradient(
                    stops: [
                        .init(color: DatawatchColors.border, location: 0.25),
                        .init(color: DatawatchColors.surface2, location: 0.37),
                        .init(color: DatawatchColors.border, location: 0.63),
                    ],
                    startPoint: UnitPoint(x: phase, y: 0.5),
                    endPoint: UnitPoint(x: phase + 1, y: 0.5)
                )
            )
            .frame(width: width, height: 12)
    }
}
