import SwiftUI

/// Shared load feedback for the main tabs (Sessions, Alerts, Automata, Observer,
/// Dashboard) — operator UX requirement 2026-10-14:
///
/// - **First load** (nothing cached yet): the datawatch splash eye (the same
///   `SplashEyeView` art as the launch splash and the session-connect overlay)
///   with a spinner and a label, instead of an empty list.
/// - **Refresh over cached data**: the content stays on screen with a thin
///   indeterminate bar and a small "Refreshing…" pill pinned under the header.
///
/// Usage:
/// ```swift
/// ScreenLoadState(isFirstLoad: vm.isLoading && vm.items.isEmpty,
///                 isRefreshing: vm.isRefreshing,
///                 message: L("Loading sessions…")) {
///     list
/// }
/// ```
struct ScreenLoadState<Content: View>: View {
    let isFirstLoad: Bool
    let isRefreshing: Bool
    var message: String = L("Loading…")
    @ViewBuilder var content: () -> Content

    var body: some View {
        if isFirstLoad {
            ScreenFirstLoadView(message: message)
        } else {
            content()
                .overlay(alignment: .top) { RefreshingIndicator(active: isRefreshing) }
        }
    }
}

/// Full-area first-load state: splash eye + spinner + label.
struct ScreenFirstLoadView: View {
    var message: String = L("Loading…")

    var body: some View {
        VStack(spacing: 14) {
            SplashEyeView()
                .frame(width: 96, height: 96)
            ProgressView()
                .tint(DatawatchColors.primary)
            Text(message)
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(message)
    }
}

/// Thin indeterminate bar + "Refreshing…" pill, shown while cached data is
/// being refreshed. Renders nothing (and takes no space) when inactive.
struct RefreshingIndicator: View {
    let active: Bool

    var body: some View {
        if active {
            VStack(spacing: 4) {
                IndeterminateBar()
                    .frame(height: 2)
                HStack(spacing: 5) {
                    ProgressView()
                        .controlSize(.mini)
                        .tint(DatawatchColors.primary)
                    Text(L("Refreshing…"))
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(DatawatchColors.onSurface)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 3)
                .background(DatawatchColors.surface2, in: Capsule())
                .overlay(Capsule().stroke(DatawatchColors.border, lineWidth: 1))
            }
            .allowsHitTesting(false)
            .transition(.opacity)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(L("Refreshing…"))
        }
    }
}

/// A short primary-colour segment sliding across a faint track (static
/// full-width track under Reduce Motion).
private struct IndeterminateBar: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: CGFloat = 0

    var body: some View {
        GeometryReader { geo in
            let w: CGFloat = geo.size.width
            let seg: CGFloat = max(40, w * 0.3)
            ZStack(alignment: .leading) {
                Rectangle().fill(DatawatchColors.primary.opacity(0.15))
                if !reduceMotion {
                    Rectangle()
                        .fill(DatawatchColors.primary)
                        .frame(width: seg)
                        .offset(x: -seg + phase * (w + seg))
                }
            }
            .clipped()
        }
        .onAppear {
            guard !reduceMotion else { return }
            phase = 0
            withAnimation(.linear(duration: 1.1).repeatForever(autoreverses: false)) { phase = 1 }
        }
    }
}

#if DEBUG
#Preview {
    VStack(spacing: 0) {
        ScreenLoadState(isFirstLoad: false, isRefreshing: true) {
            List(0..<5, id: \.self) { i in Text("Row \(i)") }
        }
        ScreenLoadState(isFirstLoad: true, isRefreshing: false, message: "Loading sessions…") {
            EmptyView()
        }
    }
    .preferredColorScheme(.dark)
}
#endif
