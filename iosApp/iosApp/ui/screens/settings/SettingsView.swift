import SwiftUI

/// Settings tab — placeholder for Story 11.
/// Full implementation: server profile management, biometric lock, notifications.
struct SettingsView: View {
    var body: some View {
        LoadingIndicator(message: "Settings — coming in Story 11")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(.principal) {
                    HeaderView(title: "Settings")
                }
            }
    }
}

#if DEBUG
#Preview {
    NavigationStack { SettingsView() }
        .preferredColorScheme(.dark)
}
#endif
