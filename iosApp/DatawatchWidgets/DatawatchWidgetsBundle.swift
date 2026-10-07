import SwiftUI
import WidgetKit

/// WidgetKit extension (BL403): the iOS equivalents of Android's home-screen
/// widgets (Sessions, Monitor) and its voice Quick Settings tile (Control Center
/// control, iOS 18+).
@main
struct DatawatchWidgetsBundle: WidgetBundle {
    var body: some Widget {
        SessionsWidget()
        MonitorWidget()
        if #available(iOS 18.0, *) {
            VoiceControl()
        }
    }
}

/// "datawatch voice" Control Center / Lock Screen control — Android's voice
/// Quick Settings tile. Opens the app (see OpenVoiceControlIntent).
@available(iOS 18.0, *)
struct VoiceControl: ControlWidget {
    static let kind = "com.dmzs.datawatchclient.widgets.voice"

    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: Self.kind) {
            ControlWidgetButton(action: OpenVoiceControlIntent()) {
                Label("datawatch voice", systemImage: "mic.fill")
            }
        }
        .displayName("datawatch voice")
        .description("Opens datawatch.")
    }
}
