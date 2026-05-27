import SwiftUI
import DatawatchShared
import UIKit

// ── AppDelegate for APNs ─────────────────────────────────────────────────────

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Task { @MainActor in
            NotificationService.shared.didRegister(tokenData: deviceToken)
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Non-fatal — app works without push notifications.
    }

    func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        Task { @MainActor in
            NotificationService.shared.handleNotification(userInfo)
        }
        completionHandler(.newData)
    }
}

// ── App entry point ──────────────────────────────────────────────────────────

@main
struct DatawatchClientApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    @StateObject private var profileStore = ServerProfileStore()
    @StateObject private var notificationService = NotificationService.shared

    init() {
        IosServiceLocator.shared.doInit()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(profileStore)
                .task {
                    await NotificationService.shared.requestAuthorization()
                }
        }
    }
}
