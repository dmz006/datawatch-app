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
    /// D37a / D59a launch splash: first launch, app version change, or > 24 h.
    @State private var showSplash: Bool = SplashGate.consume()
    /// Settings › Security opt-in lock: locked on cold start and whenever the
    /// app returns from the background while the toggle is on.
    @State private var isLocked: Bool = BiometricGate.lockRequired
    @Environment(\.scenePhase) private var scenePhase

    init() {
        IosServiceLocator.shared.doInit()
    }

    var body: some Scene {
        WindowGroup {
            ZStack {
                rootContent
                if showSplash {
                    LaunchSplashView {
                        // PWA .fade-out: opacity → 0 over 0.6 s ease.
                        withAnimation(.easeOut(duration: 0.6)) { showSplash = false }
                    }
                    .transition(.opacity)
                    .zIndex(1)
                }
            }
            .biometricLocked(isLocked: $isLocked)
        }
        .onChange(of: scenePhase) { phase in
            // Lock on background only — `.inactive` also fires for Control
            // Center / the Face ID sheet itself, which must not re-lock.
            if phase == .background && BiometricGate.lockRequired { isLocked = true }
        }
    }

    private var rootContent: some View {
        RootView()
            .environmentObject(profileStore)
            .task {
                #if DEBUG
                // Simulator screenshot passes: skip the system permission prompt.
                if ProcessInfo.processInfo.arguments.contains("-dwSkipNotifPrompt") { return }
                #endif
                await NotificationService.shared.requestAuthorization()
            }
            .onReceive(
                NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)
            ) { _ in
                // Re-register all profiles on foreground in case a new profile was
                // added on another device or the APNs token rotated.
                IosServiceLocator.shared.reregisterAllProfiles(onComplete: nil)
            }
    }
}
