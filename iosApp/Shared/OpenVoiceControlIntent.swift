import AppIntents

/// Action of the "datawatch voice" Control Center control (iOS 18+), the
/// equivalent of Android's voice Quick Settings tile. Compiled into BOTH the app
/// and the widget extension: Apple requires an app-opening control intent to be
/// a member of both targets so the system can run it in the app.
///
/// Android's tile launches `datawatch://voice/new`, a link no Android screen
/// handles yet (MainActivity only accepts session / alert links), so there is no
/// defined voice screen to mirror. This control opens datawatch where it was;
/// routing it to a voice screen is an open product question (docs/plans BL403).
@available(iOS 18.0, *)
struct OpenVoiceControlIntent: AppIntent {
    static var title: LocalizedStringResource = "Open datawatch voice"
    static var openAppWhenRun: Bool = true
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        .result()
    }
}
