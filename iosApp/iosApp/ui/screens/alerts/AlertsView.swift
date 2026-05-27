import SwiftUI

/// Alerts tab — placeholder for Story 8.
struct AlertsView: View {
    var body: some View {
        LoadingIndicator(message: "Alerts — coming in Story 8")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(.principal) {
                    HeaderView(title: "Alerts")
                }
            }
    }
}

#if DEBUG
#Preview {
    NavigationStack { AlertsView() }
        .preferredColorScheme(.dark)
}
#endif
