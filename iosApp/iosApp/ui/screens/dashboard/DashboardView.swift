import SwiftUI

/// Dashboard tab — placeholder for Story 10.
struct DashboardView: View {
    var body: some View {
        LoadingIndicator(message: "Dashboard — coming in Story 10")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(.principal) {
                    HeaderView(title: "Dashboard")
                }
            }
    }
}

#if DEBUG
#Preview {
    NavigationStack { DashboardView() }
        .preferredColorScheme(.dark)
}
#endif
