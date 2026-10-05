import Foundation

extension Notification.Name {
    /// Switch to the Settings tab (RootView) and open `SettingsDeepLink.pending`.
    static let dwNavigateToSettings = Notification.Name("dw.navigateToSettings")
}

/// In-app jump into a Settings card (PWA `navigate('settings', tab)` /
/// Android `SettingsNavChannel.request`). The card id is parked in `pending`
/// so a Settings tab that has not been built yet still opens it on appear.
@MainActor
enum SettingsDeepLink {
    static var pending: String? = nil

    static func open(cardId: String) {
        pending = cardId
        NotificationCenter.default.post(name: .dwNavigateToSettings, object: nil)
    }

    /// Returns and clears the parked card, if any.
    static func take() -> SettingsCard? {
        guard let id = pending else { return nil }
        pending = nil
        for group in SettingsCatalog.groups {
            if let card = group.cards.first(where: { $0.id == id }) { return card }
        }
        return nil
    }
}
