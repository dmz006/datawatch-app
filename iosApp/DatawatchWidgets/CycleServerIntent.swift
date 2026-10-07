import AppIntents
import DatawatchShared
import SwiftUI
import WidgetKit

/// Tap the server name on a widget → next enabled server (Android
/// `WidgetActions.cycleActiveServer`). Interactive widget buttons need iOS 17;
/// on iOS 16 the server name is plain text and the whole widget opens the app.
/// The app adopts the new active server when it next comes to the foreground.
struct CycleServerIntent: AppIntent {
    static var title: LocalizedStringResource = "Next server"
    static var isDiscoverable: Bool = false

    func perform() async throws -> some IntentResult {
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            IosSurfaces.shared.cycleWidgetServer { cont.resume(returning: ()) }
        }
        // Android refreshes both widget kinds after a cycle.
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

/// Server-name header; a cycle button on iOS 17+.
struct ServerHeader: View {
    let text: String
    var font: Font = .caption

    var body: some View {
        if #available(iOS 17.0, *) {
            Button(intent: CycleServerIntent()) { label }
                .buttonStyle(.plain)
        } else {
            label
        }
    }

    private var label: some View {
        Text(text)
            .font(font)
            .foregroundColor(WidgetPalette.muted)
            .lineLimit(1)
            .truncationMode(.tail)
    }
}
