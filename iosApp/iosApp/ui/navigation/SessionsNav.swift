import SwiftUI

/// Cross-tab "jump to Sessions with this filter" (mirror of Android
/// SessionsNavChannel). Used by Automata → View sessions and task worker
/// session links. The Sessions tab consumes the pending filter when it
/// appears or while visible, so it works even before the tab is first built.
@MainActor
final class SessionsNav: ObservableObject {
    static let shared = SessionsNav()
    @Published private(set) var pendingFilter: String? = nil
    private init() {}

    func jumpTo(_ filter: String) {
        pendingFilter = filter
        NotificationCenter.default.post(name: .dwNavigateToSessions, object: nil)
    }

    func consume() -> String? {
        defer { pendingFilter = nil }
        return pendingFilter
    }
}

extension Notification.Name {
    static let dwNavigateToSessions = Notification.Name("dw.navigateToSessions")
}
