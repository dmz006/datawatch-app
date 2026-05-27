import Foundation
import UserNotifications
import UIKit

/// Handles APNs registration and push notification routing.
///
/// Story 12: Device token is registered with each configured server profile
/// via POST /api/devices/register (datawatch#107 — server-side APNs support).
/// Until the server endpoint ships, we store the token locally and register
/// when the server support becomes available.
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

    /// Called from AppDelegate/SwiftUI scene when APNs returns a device token.
    func didRegister(tokenData: Data) {
        let token = tokenData.map { String(format: "%02.2hhx", $0) }.joined()
        deviceToken = token
        // TODO: register token with each profile's server when datawatch#107 ships.
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
