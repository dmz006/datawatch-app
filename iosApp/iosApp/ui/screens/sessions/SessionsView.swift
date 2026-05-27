import SwiftUI

/// Sessions tab — placeholder for Story 5.
/// Full implementation: session list, active WebSocket stream, terminal push.
struct SessionsView: View {
    var body: some View {
        LoadingIndicator(message: "Sessions — coming in Story 5")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(.principal) {
                    HeaderView(title: "Sessions")
                }
            }
    }
}

#if DEBUG
#Preview {
    NavigationStack { SessionsView() }
        .preferredColorScheme(.dark)
}
#endif
