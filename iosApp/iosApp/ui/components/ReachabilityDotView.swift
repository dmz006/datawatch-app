import SwiftUI
import Combine
import DatawatchShared

/// Live connection state per server, shared by every dot — the PWA
/// `state.connected` (set on `ws.onopen`, cleared on `ws.onclose`). The source
/// is the shared `WsConnectionHub` (via `IosWsReachability`): a server is
/// reachable while any global `/ws` for it is open. Those sockets are the
/// foreground alert feed's (`LiveAlertFeed`, one per enabled server), which is
/// restarted on scene-active and on long-press reconnect — the Android
/// ON_RESUME re-ping equivalent. No REST probe loop.
///
/// Also mirrors the app-wide active server from `ServerProfileStore` (attached
/// once at launch), so a dot whose `profile` argument is nil or stale still
/// shows the active server.
@MainActor
final class ReachabilityCache: ObservableObject {
    static let shared = ReachabilityCache()
    /// `at` = when the state last changed.
    struct Entry { let reachable: Bool; let at: Date }
    @Published private(set) var entries: [String: Entry] = [:]
    /// The store's active server (falls back to the first enabled one).
    @Published private(set) var activeProfile: ServerProfile? = nil
    private var watchers: [String: IosSubscription] = [:]
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

    /// Start following `profile`'s live socket state (idempotent; a watcher
    /// only observes the hub and opens no socket, so it is kept for the app's
    /// lifetime).
    func watch(_ profile: ServerProfile) {
        let id: String = profile.id
        guard watchers[id] == nil else { return }
        watchers[id] = IosWsReachability.shared.watch(profileId: id) { connected in
            let ok: Bool = connected.boolValue
            DispatchQueue.main.async { ReachabilityCache.shared.update(id, ok) }
        }
    }

    private func update(_ id: String, _ ok: Bool) {
        if let e = entries[id], e.reachable == ok { return }
        entries[id] = Entry(reachable: ok, at: Date())
    }
}

/// Reachability dot — PWA `.status-dot` (style.css:193-204): green while the
/// server's live WebSocket is connected, red otherwise (no separate amber
/// state, operator 2026-10-05). Tap opens a sheet with the time of the last
/// change and a reconnect button (Android `ReachabilityDot`). Long-press = PWA
/// status-dot long-press (D38a): reconnect live sockets (`.dwReconnectRequested`).
struct ReachabilityDotView: View {
    let profile: ServerProfile?

    @ObservedObject private var cache = ReachabilityCache.shared
    @State private var sheetOpen = false

    /// The explicit profile when given, else the app-wide active server.
    private var effective: ServerProfile? { profile ?? cache.activeProfile }

    private var reachable: Bool { cache.entry(effective?.id)?.reachable ?? false }
    private var lastChangeDate: Date? { cache.entry(effective?.id)?.at }

    private var dotColor: Color { reachable ? DatawatchColors.success : DatawatchColors.error }

    private var statusDescription: String { reachable ? "Server online" : "Server unreachable" }

    var body: some View {
        StatusDot(color: dotColor)
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
            // Follows whichever server is effective; the cache keeps one hub
            // watcher per server.
            .task(id: effective?.id) {
                if let p = effective { cache.watch(p) }
            }
    }

    private var sheet: some View {
        ReachabilitySheet(
            description: statusDescription,
            lastChangeDate: lastChangeDate,
            onRetry: {
                sheetOpen = false
                reconnect()
            },
            onDismiss: { sheetOpen = false }
        )
        .presentationDetents([.medium])
    }

    private func reconnect() {
        NotificationCenter.default.post(name: .dwReconnectRequested, object: nil)
    }
}

// ── Status dot ────────────────────────────────────────────────────────────────

/// 10 pt circle; colour changes ease over 0.3 s like the PWA's
/// `transition: background 0.3s ease`. No pulse (the PWA dot never pulses).
private struct StatusDot: View {
    let color: Color

    var body: some View {
        Circle()
            .fill(color)
            .frame(width: 10, height: 10)
            .animation(.easeInOut(duration: 0.3), value: color)
    }
}

// ── Sheet ─────────────────────────────────────────────────────────────────────

private struct ReachabilitySheet: View {
    let description: String
    let lastChangeDate: Date?
    let onRetry: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L(description))
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)

            Text("Last change: \(changeLabel)")
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

    private var changeLabel: String {
        guard let date = lastChangeDate else { return "Never" }
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
