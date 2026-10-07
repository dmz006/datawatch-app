import AppIntents
import DatawatchShared
import Foundation

/// Siri / Shortcuts: send text to a running session (BL403). iOS equivalent of
/// Android's VOICE_SEND App Action (`VoiceCommandActivity`):
///  - target = the active server's most recently active running or waiting
///    session, or the first whose name contains / id starts with the optional
///    session hint (shared `VoiceSendTarget`, same rule as Android);
///  - never sends without confirmation (Android shows a confirm screen; Siri
///    asks "Send … to …?");
///  - sends through the shared transport (`IosServiceLocator.replyToSession`).
///
/// Runs in the app process without opening the app. Requires an unlocked device:
/// the profile database and the bearer token are only readable when unlocked.
struct SendToSessionIntent: AppIntent {
    // Same declaration style as Apple's App Intents samples.
    static var title: LocalizedStringResource = "Send to session"
    static var description = IntentDescription(
        "Sends text to your most recently active datawatch session after you confirm."
    )
    static var openAppWhenRun: Bool = false
    static var authenticationPolicy: IntentAuthenticationPolicy = .requiresAuthentication

    @Parameter(title: "Message", requestValueDialog: IntentDialog("What should I send?"))
    var message: String

    /// Optional, like Android's `sessionName` extra. Matches a session name or id prefix.
    @Parameter(title: "Session")
    var sessionName: String?

    static var parameterSummary: some ParameterSummary {
        Summary("Send \(\.$message) to \(\.$sessionName)")
    }

    func perform() async throws -> some IntentResult & ProvidesDialog {
        let text = message.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty {
            return .result(dialog: IntentDialog("Nothing to send."))
        }
        guard let target = try await Self.resolveTarget(hint: sessionName) else {
            return .result(dialog: IntentDialog("No running session to send to."))
        }
        let label = target.label
        // Throws (and Siri stops) when the user says no.
        try await requestConfirmation(
            result: .result(dialog: IntentDialog("Send “\(text)” to \(label)?")),
            confirmationActionName: .send
        )
        // Settings › Security › "Require Face ID for Siri" (default on): prove it's the
        // owner before anything reaches the server. If iOS can't show the prompt from
        // Siri, nothing is sent.
        if BiometricGate.siriAuthRequired {
            let ok = await BiometricGate.authenticate(reason: String(localized: "Confirm it's you to send to your session"))
            if !ok {
                return .result(dialog: IntentDialog("Not sent. Confirm with Face ID or your passcode, or open datawatch to send."))
            }
        }
        try await Self.send(text, to: target)
        return .result(dialog: IntentDialog("Sent to \(label)."))
    }

    // MARK: Shared-transport bridges (Kotlin callbacks fire exactly once)

    private static func resolveTarget(hint: String?) async throws -> IosSendTarget? {
        let active = UserDefaults.standard.string(forKey: "dw.active_profile_id")
        return try await withCheckedThrowingContinuation { cont in
            IosSurfaces.shared.resolveSendTarget(
                activeProfileId: active,
                sessionHint: hint,
                onResult: { cont.resume(returning: $0) },
                onError: { cont.resume(throwing: ServiceLocatorAsync.TransportError(message: $0)) }
            )
        }
    }

    private static func send(_ text: String, to target: IosSendTarget) async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            IosServiceLocator.shared.replyToSession(
                profile: target.profile,
                sessionId: target.session.id,
                text: text,
                onSuccess: { cont.resume(returning: ()) },
                onError: { cont.resume(throwing: ServiceLocatorAsync.TransportError(message: $0)) }
            )
        }
    }
}

/// Siri phrases that work without any setup ("Hey Siri, tell datawatch").
/// Free text can't be part of an App Shortcut phrase, so Siri then asks
/// "What should I send?". Localised phrases: Resources/<lang>.lproj/AppShortcuts.strings.
struct DatawatchShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: SendToSessionIntent(),
            phrases: [
                "Tell \(.applicationName)",
                "Send a message with \(.applicationName)",
                "Reply in \(.applicationName)",
            ]
        )
    }
}
