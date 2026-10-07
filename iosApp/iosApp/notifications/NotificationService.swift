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
    /// Last APNs registration failed; retried on the next foreground.
    var registrationFailed: Bool = false

    /// APNs environment of this build — must match the token's environment so the
    /// server picks api.push.apple.com vs api.sandbox.push.apple.com. TestFlight /
    /// App Store builds use the production profile (aps-environment = production).
    static var apnsEnvironment: String {
        #if DEBUG
        return "development"
        #else
        return "production"
        #endif
    }

    private override init() {}

    /// Request notification permission. Call once on app launch (after onboarding).
    func requestAuthorization() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        authorizationStatus = settings.authorizationStatus

        guard settings.authorizationStatus == .notDetermined else { return }
        do {
            // Alert/badge/sound permission only; the device token itself is
            // requested on every launch from the AppDelegate.
            let granted = try await center.requestAuthorization(options: [.alert, .badge, .sound])
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
        registrationFailed = false
        IosServiceLocator.shared.registerApnsToken(token: token, environment: Self.apnsEnvironment)
    }

    /// Handle a tapped notification. Two payload shapes arrive here:
    /// - server APNs pushes (datawatch v8.63+): `{"aps":…, "sessionId": "<id>", "type": "<alert level>"}`;
    /// - the app's own interim local notifications (D87b): `session_id`, `profile_id`, `type: "input_needed"`.
    /// A session id opens that session; otherwise any datawatch notification opens the Alerts tab.
    func handleNotification(_ userInfo: [AnyHashable: Any]) {
        let sessionId: String? = (userInfo["session_id"] as? String) ?? (userInfo["sessionId"] as? String)
        if let sessionId, !sessionId.isEmpty {
            var info: [String: String] = ["id": sessionId]
            let pid = userInfo["profile_id"] as? String
            if let pid { info["profileId"] = pid }
            // A tap that cold-starts the app arrives before RootView listens:
            // the D40a restore slot reopens it once the root appears.
            ShellRestore.setOpenSession(profileId: pid ?? "", sessionId: sessionId)
            NotificationCenter.default.post(name: .deepLinkSession, object: nil, userInfo: info)
        } else if userInfo["type"] != nil || userInfo["aps"] != nil {
            NotificationCenter.default.post(name: .deepLinkAlert, object: nil, userInfo: nil)
        }
    }
}

/// UNUserNotificationCenter delegate (set at launch, before any notification
/// response can be delivered):
/// - shows the D87b interim local notifications as banners while the app is in
///   the foreground (iOS hides them otherwise);
/// - routes a tap to the session / Alerts tab via `NotificationService`.
final class NotificationRouter: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationRouter()

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        Task { @MainActor in
            NotificationService.shared.handleNotification(info)
            completionHandler()
        }
    }
}
