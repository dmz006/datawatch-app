import SwiftUI
import DatawatchShared

/// Animated reachability dot — green (online), red (unreachable), amber/pulsing (probing).
/// Self-contained: manages its own 30s probe timer given a ServerProfile.
/// Tap opens a sheet with last-probe time and a retry button.
/// Matches Android's `ReachabilityDot` in HeaderComponents.kt.
/// Last probe result per server, shared by every dot. Toolbars re-create their
/// items often (spinners, badge counts); without a shared cache each new dot
/// started at "probing" (amber) and never settled on busy screens.
@MainActor
final class ReachabilityCache: ObservableObject {
    static let shared = ReachabilityCache()
    struct Entry { let reachable: Bool; let at: Date }
    @Published private(set) var entries: [String: Entry] = [:]
    private var inFlight: Set<String> = []
    private init() {}

    func entry(_ id: String?) -> Entry? { id.flatMap { entries[$0] } }

    /// Probe unless a result newer than `maxAge` exists (or `force`).
    func probe(_ profile: ServerProfile, maxAge: TimeInterval, force: Bool = false) {
        let id = profile.id
        if !force, let e = entries[id], Date().timeIntervalSince(e.at) < maxAge { return }
        guard !inFlight.contains(id) else { return }
        inFlight.insert(id)
        IosServiceLocator.shared.probeProfile(
            profile: profile,
            tokenValue: nil,
            onSuccess: { DispatchQueue.main.async { self.finish(id, true) } },
            onError: { _ in DispatchQueue.main.async { self.finish(id, false) } }
        )
    }

    private func finish(_ id: String, _ ok: Bool) {
        inFlight.remove(id)
        entries[id] = Entry(reachable: ok, at: Date())
    }
}

struct ReachabilityDotView: View {
    let profile: ServerProfile?

    @ObservedObject private var cache = ReachabilityCache.shared
    @State private var sheetOpen = false
    @State private var probeTimer: Timer? = nil

    private static let probeInterval: TimeInterval = 30

    private var reachable: Bool? { cache.entry(profile?.id)?.reachable }
    private var lastProbeDate: Date? { cache.entry(profile?.id)?.at }

    private var dotColor: Color {
        switch reachable {
        case .some(true):  DatawatchColors.success
        case .some(false): DatawatchColors.error
        case .none:   DatawatchColors.warning
        }
    }

    private var statusDescription: String {
        switch reachable {
        case .some(true):  "Server online"
        case .some(false): "Server unreachable"
        case .none:   "Probing…"
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
                    if let profile { cache.probe(profile, maxAge: 0, force: true) }
                },
                onDismiss: { sheetOpen = false }
            )
            .presentationDetents([.medium])
        }
        .onAppear {
            if let profile { cache.probe(profile, maxAge: Self.probeInterval) }
            startTimer()
        }
        .onDisappear {
            stopTimer()
        }
        .onChange(of: profile?.id) { _ in
            if let profile { cache.probe(profile, maxAge: Self.probeInterval) }
        }
    }

    private func startTimer() {
        stopTimer()
        probeTimer = Timer.scheduledTimer(withTimeInterval: Self.probeInterval, repeats: true) { _ in
            Task { @MainActor in
                if let profile { ReachabilityCache.shared.probe(profile, maxAge: Self.probeInterval - 1) }
            }
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
