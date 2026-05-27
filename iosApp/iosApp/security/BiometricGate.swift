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

/// View modifier that gates display of its content behind biometric auth.
///
/// Usage:
/// ```swift
/// ContentView()
///     .biometricLocked(isLocked: $isLocked)
/// ```
struct BiometricLockModifier: ViewModifier {
    @Binding var isLocked: Bool

    func body(content: Content) -> some View {
        Group {
            if isLocked {
                VStack(spacing: 24) {
                    Image(systemName: biometricIcon)
                        .font(.system(size: 60))
                        .foregroundStyle(DatawatchColors.primary)
                    Text("datawatch is locked")
                        .font(DatawatchFonts.titleMedium)
                        .foregroundStyle(DatawatchColors.onSurface)
                    Button("Unlock") { unlock() }
                        .buttonStyle(.borderedProminent)
                        .tint(DatawatchColors.primary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(DatawatchColors.background)
                .onAppear { unlock() }
            } else {
                content
            }
        }
    }

    private var biometricIcon: String {
        switch BiometricGate.biometricType {
        case .faceID: return "faceid"
        case .touchID: return "touchid"
        default: return "lock.fill"
        }
    }

    private func unlock() {
        BiometricGate.authenticate(reason: "Unlock datawatch") { success in
            if success { isLocked = false }
        }
    }
}

extension View {
    func biometricLocked(isLocked: Binding<Bool>) -> some View {
        modifier(BiometricLockModifier(isLocked: isLocked))
    }
}
