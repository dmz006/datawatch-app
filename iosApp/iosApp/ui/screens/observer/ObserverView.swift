import SwiftUI

/// Observer tab — placeholder for Story 10 (multi-server dashboard).
struct ObserverView: View {
    var body: some View {
        LoadingIndicator(message: "Observer — coming in Story 10")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(.principal) {
                    HeaderView(title: "Observer")
                }
            }
    }
}

#if DEBUG
#Preview {
    NavigationStack { ObserverView() }
        .preferredColorScheme(.dark)
}
#endif
