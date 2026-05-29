import SwiftUI
import DatawatchShared

/// Animated reachability dot — green (online), red (unreachable), amber/pulsing (probing).
/// Self-contained: manages its own 30s probe timer given a ServerProfile.
/// Tap opens a sheet with last-probe time and a retry button.
/// Matches Android's `ReachabilityDot` in HeaderComponents.kt.
struct ReachabilityDotView: View {
    let profile: ServerProfile?

    @State private var reachable: Bool? = nil
    @State private var lastProbeDate: Date? = nil
    @State private var sheetOpen = false
    @State private var probeTimer: Timer? = nil

    private static let probeInterval: TimeInterval = 30

    private var dotColor: Color {
        switch reachable {
        case true:  DatawatchColors.success
        case false: DatawatchColors.error
        case nil:   DatawatchColors.warning
        }
    }

    private var statusDescription: String {
        switch reachable {
        case true:  "Server online"
        case false: "Server unreachable"
        case nil:   "Probing…"
        }
    }

    var body: some View {
        Button {
            sheetOpen = true
        } label: {
            PulsingDot(color: dotColor, pulsing: reachable == nil)
                .frame(width: 24, height: 24)
        }
        .accessibilityLabel(statusDescription)
        .sheet(isPresented: $sheetOpen) {
            ReachabilitySheet(
                description: statusDescription,
                lastProbeDate: lastProbeDate,
                onRetry: {
                    sheetOpen = false
                    probe()
                },
                onDismiss: { sheetOpen = false }
            )
            .presentationDetents([.medium])
        }
        .onAppear {
            probe()
            startTimer()
        }
        .onDisappear {
            stopTimer()
        }
        .onChange(of: profile?.id) { _ in
            reachable = nil
            lastProbeDate = nil
            probe()
        }
    }

    private func probe() {
        guard let profile else { return }
        reachable = nil
        IosServiceLocator.shared.probeProfile(
            profile: profile,
            tokenValue: nil,
            onSuccess: {
                DispatchQueue.main.async {
                    reachable = true
                    lastProbeDate = Date()
                }
            },
            onError: { _ in
                DispatchQueue.main.async {
                    reachable = false
                }
            }
        )
    }

    private func startTimer() {
        stopTimer()
        probeTimer = Timer.scheduledTimer(withTimeInterval: Self.probeInterval, repeats: true) { _ in
            Task { @MainActor in probe() }
        }
    }

    private func stopTimer() {
        probeTimer?.invalidate()
        probeTimer = nil
    }
}

// ── Pulsing dot ───────────────────────────────────────────────────────────────

private struct PulsingDot: View {
    let color: Color
    let pulsing: Bool

    @State private var scale: CGFloat = 1.0

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 10, height: 10)
            .scaleEffect(scale)
            .onAppear { updateScale() }
            .onChange(of: pulsing) { _ in updateScale() }
    }

    private func updateScale() {
        if pulsing {
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                scale = 1.4
            }
        } else {
            withAnimation(.easeInOut(duration: 0.2)) {
                scale = 1.0
            }
        }
    }
}

// ── Sheet ─────────────────────────────────────────────────────────────────────

private struct ReachabilitySheet: View {
    let description: String
    let lastProbeDate: Date?
    let onRetry: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(description)
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)

            Text("Last probe: \(probeLabel)")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)

            Button("Retry now") {
                onRetry()
            }
            .font(DatawatchFonts.bodyMedium)
            .foregroundStyle(DatawatchColors.primary)
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
            .overlay(Capsule().stroke(DatawatchColors.primary, lineWidth: 1))

            Spacer()
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.surface)
    }

    private var probeLabel: String {
        guard let date = lastProbeDate else { return "Never" }
        let delta = Date().timeIntervalSince(date)
        if delta < 5  { return "just now" }
        if delta < 60 { return "\(Int(delta))s ago" }
        let mins = Int(delta / 60)
        if mins < 60  { return "\(mins)m ago" }
        return "\(mins / 60)h ago"
    }
}

#if DEBUG
#Preview("ReachabilityDot states") {
    HStack(spacing: 24) {
        ReachabilityDotView(profile: nil)
    }
    .padding()
    .background(DatawatchColors.background)
    .preferredColorScheme(.dark)
}
#endif
