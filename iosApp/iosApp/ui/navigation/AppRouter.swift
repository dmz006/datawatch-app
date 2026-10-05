import SwiftUI
import DatawatchShared

/// A session to open in the Sessions tab's navigation stack (deep link,
/// notification tap, or D40a cold-start restore). `profileId` nil = search the
/// active server first, then every other enabled server.
struct SessionRoute: Hashable {
    let sessionId: String
    let profileId: String?
}

/// Handles `datawatch://` deep links (D84b — same scheme as Android).
///
///   datawatch://session/<id>   → Sessions tab, open that session
///   datawatch://alert/<id>     → Alerts tab
///   datawatch://<tab>          → sessions | automata | alerts | observer | dashboard | settings
///
/// The scheme is registered in project.yml (`CFBundleURLTypes`). For a custom
/// scheme the first segment is the URL *host* (`datawatch://session/abc` has
/// host "session", path "/abc"), so parsing joins host + path — reading only
/// the path was the old bug that dropped every link.
@MainActor
final class AppRouter: ObservableObject {
    static let shared = AppRouter()
    private init() {}

    static let scheme = "datawatch"

    /// Parse a link into (target, id). Exposed for tests / debug hooks.
    nonisolated static func parse(_ url: URL) -> (String, String?)? {
        guard let comps = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return nil }
        var parts: [String] = []
        if comps.scheme?.lowercased() == scheme, let host = comps.host, !host.isEmpty {
            parts.append(host)
        }
        parts += comps.path.split(separator: "/").map(String.init)
        guard let first = parts.first?.lowercased() else { return nil }
        let id: String? = parts.count > 1 ? parts[1].removingPercentEncoding ?? parts[1] : nil
        return (first, id)
    }

    func handle(url: URL, selectedTab: Binding<AppTab>) {
        guard let (target, id) = Self.parse(url) else { return }
        switch target {
        case "session", "sessions":
            selectedTab.wrappedValue = .sessions
            if let id, !id.isEmpty {
                NotificationCenter.default.post(
                    name: .deepLinkSession, object: nil,
                    userInfo: ["id": id]
                )
            }
        case "alert", "alerts":
            selectedTab.wrappedValue = .alerts
        default:
            if let tab = AppTab(rawValue: target) { selectedTab.wrappedValue = tab }
        }
    }
}

extension Notification.Name {
    /// userInfo: "id" (session short or full id), optional "profileId".
    static let deepLinkSession = Notification.Name("datawatch.deeplink.session")
    static let deepLinkAlert   = Notification.Name("datawatch.deeplink.alert")
    /// Status-dot long-press (D38a): reconnect live sockets now.
    static let dwReconnectRequested = Notification.Name("dw.reconnectRequested")
}

/// Resolves a `SessionRoute` to a live session and shows its detail view.
struct DeepLinkSessionView: View {
    let route: SessionRoute
    @EnvironmentObject private var store: ServerProfileStore
    @State private var resolved: (DwSession, ServerProfile)? = nil
    @State private var failed = false

    var body: some View {
        Group {
            if let r = resolved {
                SessionDetailView(session: r.0, profile: r.1)
            } else if failed {
                notFound
            } else {
                LoadingIndicator(message: L("Opening session…"))
            }
        }
        .task(id: route) { await resolve() }
    }

    private var notFound: some View {
        VStack(spacing: 12) {
            Image(systemName: "questionmark.circle")
                .font(.system(.largeTitle))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("Session not found")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text(route.sessionId)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
    }

    private func candidates() -> [ServerProfile] {
        var list: [ServerProfile] = []
        if let pid = route.profileId, let p = store.profiles.first(where: { $0.id == pid }) { list.append(p) }
        if let a = store.activeProfile, !list.contains(where: { $0.id == a.id }) { list.append(a) }
        for p in store.enabledProfiles where !list.contains(where: { $0.id == p.id }) { list.append(p) }
        return list
    }

    private func resolve() async {
        // The profile store loads asynchronously on cold start.
        for _ in 0..<50 where store.isLoading { try? await Task.sleep(nanoseconds: 100_000_000) }
        let id = route.sessionId
        for profile in candidates() {
            guard let list = try? await ServiceLocatorAsync.listSessions(profile: profile) else { continue }
            if let s = list.first(where: { $0.id == id || $0.fullId == id }) {
                resolved = (s, profile)
                return
            }
        }
        failed = true
    }
}
