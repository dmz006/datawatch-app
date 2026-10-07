import DatawatchShared
import Foundation
import WidgetKit

/// Keeps the home-screen widgets (DatawatchWidgets extension, BL403) in step with
/// the app. The app writes the enabled servers + active server id to the shared
/// Keychain (`IosSurfaces.publishWidgetConfig`, no tokens); the widget reads it and
/// fetches from the server itself every 30 minutes, like Android's widgets.
enum WidgetSync {
    /// Servers or the active server changed: republish, then redraw the widgets.
    static func publish(activeProfileId: String?) {
        IosSurfaces.shared.publishWidgetConfig(activeProfileId: activeProfileId) {
            DispatchQueue.main.async { WidgetCenter.shared.reloadAllTimelines() }
        }
    }

    /// Android refreshes its widgets after the app's own polls; iOS budgets widget
    /// reloads, so the app asks once when it leaves the foreground instead.
    static func reloadWidgets() {
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// Active server last chosen by the app or by tapping the widget's server name
    /// (Android "tap to cycle"). Nil when no widget configuration exists yet.
    static func widgetActiveProfileId() -> String? {
        IosSurfaces.shared.widgetActiveProfileId()
    }
}
