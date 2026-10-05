import SwiftUI

extension Notification.Name {
    static let dwNavigateToAlerts = Notification.Name("dw.navigateToAlerts")
}

/// PWA `headerAlertPill` (D3a): always-visible "🔔 N" / "🔕 muted" pill in the
/// header. Tap toggles the alert dock (PWA `toggleAlertDock`); while muted the
/// tap un-mutes and opens it.
///
/// N = server unread badge (`dw.alert.badge`, kept by AlertsView polling and
/// bumped by live WS `alert` frames) + client-side dock entries (former toasts,
/// D41a) — the Android `AlertsBellAction` rule. Styling per PWA
/// `renderAlertPill`: dimmed at 0 or muted, blue tint + accent2 border at ≥1.
///
/// The type keeps its old name so every screen's toolbar picks up the pill
/// without edits.
struct AlertsBellButton: View {
    @AppStorage("dw.alert.badge") private var unreadCount: Int = 0
    @ObservedObject private var dock = AlertDock.shared

    /// PWA `--accent2`.
    static let accent2 = Color(hex: 0x60A5FA)

    private var count: Int { unreadCount + dock.localCount }

    var body: some View {
        Button {
            dock.toggle()
        } label: {
            Text(label)
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .monospacedDigit()
                .foregroundStyle(DatawatchColors.onSurface)
                .padding(.horizontal, 7)
                .padding(.vertical, 2)
                .background(background, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(border, lineWidth: 1))
                .opacity(active ? 1 : 0.55)
                .fixedSize()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(accessibilityText)
        .accessibilityHint(dock.muted ? L("Opens the alert dock and un-mutes") : L("Toggles the alert dock"))
    }

    private var active: Bool { !dock.muted && count > 0 }

    private var label: String {
        if dock.muted { return "🔕 " + L("muted") }
        return "🔔 " + (count > 99 ? "99+" : "\(count)")
    }

    private var background: Color {
        if dock.muted { return DatawatchColors.onSurfaceMuted.opacity(0.15) }
        return count > 0 ? Self.accent2.opacity(0.18) : DatawatchColors.onSurfaceMuted.opacity(0.10)
    }

    private var border: Color { active ? Self.accent2 : DatawatchColors.onSurfaceMuted }

    private var accessibilityText: String {
        if dock.muted { return L("Alerts muted") }
        return count == 1 ? L("1 alert") : String(format: L("%d alerts"), count)
    }
}

#if DEBUG
#Preview("Pill") {
    NavigationStack {
        DatawatchColors.background
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    AlertsBellButton()
                }
            }
    }
    .preferredColorScheme(.dark)
}
#endif
