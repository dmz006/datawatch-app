import SwiftUI

/// Handles deep links and universal links for the datawatch app.
///
/// Supported schemes:
///   datawatch://session/<id>   → Sessions tab, push SessionDetailView
///   datawatch://alert/<id>     → Alerts tab, push AlertDetailView
///
/// Universal Links (domain pairing configured in Story 15):
///   https://datawatch.app/session/<id>
///   https://datawatch.app/alert/<id>
///
/// Mirror of the intent-filter deep links in composeApp's AndroidManifest.xml.
@MainActor
final class AppRouter: ObservableObject {
    static let shared = AppRouter()
    private init() {}

    func handle(url: URL, selectedTab: Binding<AppTab>) {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return }

        let path = components.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let parts = path.split(separator: "/")

        guard let firstPart = parts.first else { return }

        switch firstPart {
        case "session":
            selectedTab.wrappedValue = .sessions
            // Post notification so SessionsView can push detail (Story 5).
            if let id = parts.dropFirst().first {
                NotificationCenter.default.post(
                    name: .deepLinkSession,
                    object: nil,
                    userInfo: ["id": String(id)]
                )
            }
        case "alert":
            selectedTab.wrappedValue = .alerts
            if let id = parts.dropFirst().first {
                NotificationCenter.default.post(
                    name: .deepLinkAlert,
                    object: nil,
                    userInfo: ["id": String(id)]
                )
            }
        default:
            break
        }
    }
}

extension Notification.Name {
    static let deepLinkSession = Notification.Name("datawatch.deeplink.session")
    static let deepLinkAlert   = Notification.Name("datawatch.deeplink.alert")
}
