import Foundation
import UserNotifications
import UIKit
import DatawatchShared

/// Handles APNs registration and push notification routing.
///
/// On token receipt, calls IosServiceLocator.registerApnsToken() which
/// POSTs /api/devices/register (kind=apns) for every enabled profile.
/// On profile delete, IosServiceLocator.deleteProfile() handles unregistration.
@MainActor
final class NotificationService: NSObject, ObservableObject {
    static let shared = NotificationService()

    @Published private(set) var deviceToken: String?
    @Published private(set) var authorizationStatus: UNAuthorizationStatus = .notDetermined

    private override init() {}

    /// Request notification permission. Call once on app launch (after onboarding).
    func requestAuthorization() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        authorizationStatus = settings.authorizationStatus

        guard settings.authorizationStatus == .notDetermined else { return }
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .badge, .sound])
            if granted {
                await MainActor.run {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
            authorizationStatus = granted ? .authorized : .denied
        } catch {
            // User denied or system error — not fatal.
        }
    }

    /// Called from AppDelegate when APNs returns a device token.
    /// Stores the token and registers it with all enabled server profiles.
    func didRegister(tokenData: Data) {
        let token = tokenData.map { String(format: "%02.2hhx", $0) }.joined()
        deviceToken = token
        IosServiceLocator.shared.registerApnsToken(token: token)
    }

    /// Handle incoming push notification payload.
    func handleNotification(_ userInfo: [AnyHashable: Any]) {
        // Route to the appropriate tab based on payload type.
        guard let type = userInfo["type"] as? String else { return }
        switch type {
        case "session_waiting", "input_needed":
            if let sessionId = userInfo["session_id"] as? String {
                NotificationCenter.default.post(
                    name: .deepLinkSession,
                    object: nil,
                    userInfo: ["sessionId": sessionId]
                )
            }
        case "alert":
            if let alertId = userInfo["alert_id"] as? String {
                NotificationCenter.default.post(
                    name: .deepLinkAlert,
                    object: nil,
                    userInfo: ["alertId": alertId]
                )
            }
        default:
            break
        }
    }
}
