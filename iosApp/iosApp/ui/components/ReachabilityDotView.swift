import SwiftUI
import Combine
import DatawatchShared

/// Last probe result per server, shared by every dot. Toolbars re-create their
/// items often (spinners, badge counts); without a shared cache each new dot
/// started at "probing" (amber) and never settled on busy screens.
///
/// Also mirrors the app-wide active server from `ServerProfileStore` (attached
/// once at launch), so a dot whose `profile` argument is nil or stale still
/// probes and shows the active server. Root cause of the amber-forever dot on
/// Sessions/Alerts: the old dot captured `profile` in a Timer closure on
/// appear (nil while the store / view model was still loading) and relied on
/// `.onChange(of: profile?.id)`, which toolbar items don't reliably re-run — so
/// it never probed. Probing is now a `.task(id:)` loop keyed on the effective
/// server id.
@MainActor
final class ReachabilityCache: ObservableObject {
    static let shared = ReachabilityCache()
    struct Entry { let reachable: Bool; let at: Date }
    @Published private(set) var entries: [String: Entry] = [:]
    /// The store's active server (falls back to the first enabled one).
    @Published private(set) var activeProfile: ServerProfile? = nil
    private var inFlight: Set<String> = []
    private var storeSink: AnyCancellable? = nil
    private init() {}

    /// Called once from the app root with the shared profile store.
    func attach(_ store: ServerProfileStore) {
        storeSink = store.$profiles
            .combineLatest(store.$activeProfileId)
            .sink { [weak self, weak store] _, _ in
                // @Published emits in willSet — read the store on the next turn.
                DispatchQueue.main.async {
                    guard let self, let store else { return }
                    let p = store.activeProfile
                    if p?.id != self.activeProfile?.id { self.activeProfile = p }
                }
            }
    }

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

/// Animated reachability dot — green (online), red (unreachable), amber/pulsing
/// (probing). Tap opens a sheet with last-probe time and a retry button
/// (Android `ReachabilityDot`). Long-press = PWA status-dot long-press (D38a):
/// re-probe now and reconnect live sockets (`.dwReconnectRequested`).
struct ReachabilityDotView: View {
    let profile: ServerProfile?

    @ObservedObject private var cache = ReachabilityCache.shared
    @State private var sheetOpen = false

    private static let probeInterval: TimeInterval = 30

    /// The explicit profile when given, else the app-wide active server.
    private var effective: ServerProfile? { profile ?? cache.activeProfile }

    private var reachable: Bool? { cache.entry(effective?.id)?.reachable }
    private var lastProbeDate: Date? { cache.entry(effective?.id)?.at }

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
        PulsingDot(color: dotColor, pulsing: reachable == nil)
            .frame(width: 24, height: 24)
            .contentShape(Rectangle())
            .onTapGesture { sheetOpen = true }
            .onLongPressGesture(minimumDuration: 0.6) { reconnect() }
            .accessibilityElement()
            .accessibilityLabel(L(statusDescription))
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { sheetOpen = true }
            .accessibilityAction(named: Text("Reconnect")) { reconnect() }
            .sheet(isPresented: $sheetOpen) { sheet }
            // Restarts whenever the effective server changes; cancelled when the
            // dot leaves the screen. Each pass probes unless a fresh result exists.
            .task(id: effective?.id) { await probeLoop() }
    }

    private var sheet: some View {
        ReachabilitySheet(
            description: statusDescription,
            lastProbeDate: lastProbeDate,
            onRetry: {
                sheetOpen = false
                reconnect()
            },
            onDismiss: { sheetOpen = false }
        )
        .presentationDetents([.medium])
    }

    private func probeLoop() async {
        let interval: UInt64 = UInt64(Self.probeInterval) * 1_000_000_000
        while !Task.isCancelled {
            if let p = effective { cache.probe(p, maxAge: Self.probeInterval - 1) }
            try? await Task.sleep(nanoseconds: interval)
        }
    }

    private func reconnect() {
        if let p = effective { cache.probe(p, maxAge: 0, force: true) }
        NotificationCenter.default.post(name: .dwReconnectRequested, object: nil)
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
            Text(L(description))
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

            Text("Tip: long-press the status dot to reconnect.")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)

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
