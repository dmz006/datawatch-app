import SwiftUI
import DatawatchShared
import UIKit
import UserNotifications

// ── AppDelegate for APNs ─────────────────────────────────────────────────────

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Before launch completes so a notification tap that cold-starts the app
        // is delivered (D87b local notifications → open the session).
        UNUserNotificationCenter.current().delegate = NotificationRouter.shared
        // Apple ("Registering your app with APNs"): register on EVERY launch —
        // the token can change (restore, reinstall, new device) and must not be
        // cached. Getting a token needs no user permission; showing alerts does.
        application.registerForRemoteNotifications()
        return true
    }

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
        // Apple: "set a flag and try to register again at a later time" —
        // retried on the next foreground (see willEnterForeground below).
        Task { @MainActor in NotificationService.shared.registrationFailed = true }
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
            // BL403: redraw the home-screen widgets with fresh data as the user leaves.
            if phase == .background { WidgetSync.reloadWidgets() }
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
                // Retry a failed APNs registration (Apple guidance), then forward the
                // current token to every profile (covers newly added servers).
                if NotificationService.shared.registrationFailed {
                    NotificationService.shared.registrationFailed = false
                    UIApplication.shared.registerForRemoteNotifications()
                }
                IosServiceLocator.shared.reregisterAllProfiles(onComplete: nil)
                // BL403: pick up a server switched from the widget while away.
                profileStore.adoptWidgetSelection()
            }
    }
}
