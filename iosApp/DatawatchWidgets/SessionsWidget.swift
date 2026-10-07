import SwiftUI
import WidgetKit

/// Sessions widget — Android `SessionsWidget`: running / waiting / total on the
/// active server (else the first enabled one), server name on top, tap opens the
/// app. Home Screen (small, medium) and Lock Screen (rectangular, inline), as
/// Android's widget is allowed on the home screen and the keyguard.
struct SessionsWidget: Widget {
    static let kind = "com.dmzs.datawatchclient.widgets.sessions"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: SessionsProvider()) { entry in
            SessionsWidgetView(entry: entry)
        }
        .configurationDisplayName("datawatch Sessions")
        .description("Running / waiting / total session counts at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular, .accessoryInline])
    }
}

struct SessionsProvider: TimelineProvider {
    func placeholder(in context: Context) -> SessionsEntry {
        .placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (SessionsEntry) -> Void) {
        if context.isPreview {
            completion(.placeholder)
            return
        }
        WidgetData.loadSessions(completion)
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SessionsEntry>) -> Void) {
        WidgetData.loadSessions { entry in
            completion(Timeline(entries: [entry], policy: .after(WidgetData.nextRefresh())))
        }
    }
}

struct SessionsWidgetView: View {
    let entry: SessionsEntry
    @Environment(\.widgetFamily) private var family

    private var header: String {
        widgetHeader(status: entry.status, serverName: entry.serverName)
    }

    private func value(_ n: Int) -> String {
        entry.hasCounts ? "\(n)" : "—"
    }

    var body: some View {
        switch family {
        case .accessoryInline:
            Text(String(format: WL("%@ running · %@ waiting"), value(entry.running), value(entry.waiting)))
                .widgetBackground(.clear)
        case .accessoryRectangular:
            VStack(alignment: .leading, spacing: 2) {
                Text(header).font(.caption2).lineLimit(1)
                HStack(spacing: 8) {
                    compact(value(entry.running), "running")
                    compact(value(entry.waiting), "waiting")
                    compact(value(entry.total), "total")
                }
            }
            .widgetBackground(.clear)
        default:
            VStack(alignment: .leading, spacing: 8) {
                Text("datawatch")
                    .font(.headline)
                    .foregroundColor(WidgetPalette.text)
                ServerHeader(text: header)
                Spacer(minLength: 0)
                HStack(alignment: .bottom, spacing: 12) {
                    counter(value(entry.running), "running", WidgetPalette.success)
                    counter(value(entry.waiting), "waiting", WidgetPalette.waiting)
                    counter(value(entry.total), "total", WidgetPalette.text)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .widgetContentPadding()
            .widgetBackground(WidgetPalette.background)
        }
    }

    private func counter(_ value: String, _ label: LocalizedStringKey, _ color: Color) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(value)
                .font(.system(.title, design: .rounded).weight(.semibold))
                .foregroundColor(color)
                .minimumScaleFactor(0.6)
                .lineLimit(1)
            Text(label)
                .font(.caption2)
                .foregroundColor(WidgetPalette.muted)
                .lineLimit(1)
        }
    }

    private func compact(_ value: String, _ label: LocalizedStringKey) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(value).font(.headline)
            Text(label).font(.caption2).lineLimit(1)
        }
    }
}

struct SessionsWidget_Previews: PreviewProvider {
    static var previews: some View {
        SessionsWidgetView(entry: .placeholder)
            .previewContext(WidgetPreviewContext(family: .systemSmall))
    }
}
