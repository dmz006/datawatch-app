import LocalAuthentication
import SwiftUI

/// Wraps LAContext for Face ID / Touch ID authentication.
///
/// Usage:
/// ```swift
/// BiometricGate.authenticate(reason: "Unlock datawatch") { success in
///     if success { /* proceed */ }
/// }
/// ```
enum BiometricGate {

    static var isAvailable: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }

    /// True when the device can authenticate the owner at all (biometrics or
    /// passcode fallback). The lock is only applied when this holds — with no
    /// passcode set there is nothing to unlock with, and the user would be
    /// locked out of the app permanently.
    static var canAuthenticate: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    /// Settings key for the opt-in lock (Settings › Security).
    static let enabledKey = "biometricLockEnabled"

    /// Whether the app should be locked right now (enabled + usable).
    static var lockRequired: Bool {
        UserDefaults.standard.bool(forKey: enabledKey) && canAuthenticate
    }

    static var biometricType: LABiometryType {
        let ctx = LAContext()
        _ = ctx.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        return ctx.biometryType
    }

    /// Authenticate with Face ID / Touch ID (falls back to passcode).
    /// Result is delivered on the main thread.
    static func authenticate(
        reason: String,
        completion: @escaping (Bool) -> Void
    ) {
        let ctx = LAContext()
        var error: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            DispatchQueue.main.async { completion(false) }
            return
        }
        ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { success, _ in
            DispatchQueue.main.async { completion(success) }
        }
    }
}

/// App-wide lock screen shown over the content while `isLocked` (Settings ›
/// Security › Face ID / Touch ID lock, opt-in). The content stays mounted
/// underneath (hidden + non-interactive) so navigation state survives a lock.
///
/// Prompts automatically once per lock (when the scene is active); after a
/// cancel the user taps Unlock — no prompt loop when the system sheet dismisses
/// and the scene returns to `.active`.
struct BiometricLockModifier: ViewModifier {
    @Binding var isLocked: Bool
    @Environment(\.scenePhase) private var scenePhase
    @State private var autoPrompted = false
    @State private var inFlight = false

    func body(content: Content) -> some View {
        ZStack {
            content
                .opacity(isLocked ? 0 : 1)
                .allowsHitTesting(!isLocked)
                .accessibilityHidden(isLocked)
            if isLocked {
                lockScreen
                    .transition(.opacity)
                    .zIndex(10)
            }
        }
        .onAppear { autoUnlockIfNeeded() }
        .onChange(of: scenePhase) { _ in autoUnlockIfNeeded() }
        .onChange(of: isLocked) { locked in
            if locked {
                autoPrompted = false
                autoUnlockIfNeeded()
            }
        }
    }

    private var lockScreen: some View {
        VStack(spacing: 24) {
            Image(systemName: biometricIcon)
                .font(.system(.largeTitle))
                .imageScale(.large)
                .foregroundStyle(DatawatchColors.primary)
                .accessibilityHidden(true)
            Text("datawatch is locked")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Button("Unlock") { unlock() }
                .buttonStyle(.borderedProminent)
                .tint(DatawatchColors.primary)
                .disabled(inFlight)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background.ignoresSafeArea())
    }

    private var biometricIcon: String {
        switch BiometricGate.biometricType {
        case .faceID: return "faceid"
        case .touchID: return "touchid"
        default: return "lock.fill"
        }
    }

    private func autoUnlockIfNeeded() {
        guard isLocked, !autoPrompted, scenePhase == .active else { return }
        autoPrompted = true
        unlock()
    }

    private func unlock() {
        guard !inFlight else { return }
        inFlight = true
        BiometricGate.authenticate(reason: L("Unlock datawatch")) { success in
            inFlight = false
            if success { isLocked = false }
        }
    }
}

extension View {
    func biometricLocked(isLocked: Binding<Bool>) -> some View {
        modifier(BiometricLockModifier(isLocked: isLocked))
    }
}
