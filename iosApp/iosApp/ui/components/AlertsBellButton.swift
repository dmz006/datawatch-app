import SwiftUI

extension Notification.Name {
    static let dwNavigateToAlerts = Notification.Name("dw.navigateToAlerts")
}

/// Toolbar bell icon with unread-alert badge — matches Android's `AlertsBellAction`.
/// Tapping navigates to the Alerts tab via `Notification.Name.dwNavigateToAlerts`.
/// Reads the live unread count from @AppStorage so it stays in sync with AlertsViewModel.
struct AlertsBellButton: View {
    @AppStorage("dw.alert.badge") private var unreadCount: Int = 0

    var body: some View {
        Button {
            NotificationCenter.default.post(name: .dwNavigateToAlerts, object: nil)
        } label: {
            ZStack(alignment: .topTrailing) {
                Image(systemName: "bell")
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)

                if unreadCount > 0 {
                    Text(unreadCount < 100 ? "\(unreadCount)" : "99+")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(DatawatchColors.background)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 2)
                        .background(DatawatchColors.error)
                        .clipShape(Capsule())
                        .offset(x: 10, y: -8)
                }
            }
        }
        .accessibilityLabel(unreadCount > 0 ? "\(unreadCount) unread alerts" : "Alerts")
    }
}

#if DEBUG
#Preview("With badge") {
    NavigationStack {
        Color(DatawatchColors.background)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    AlertsBellButton()
                }
            }
    }
    .preferredColorScheme(.dark)
}
#endif
