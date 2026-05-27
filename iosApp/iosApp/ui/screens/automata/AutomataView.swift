import SwiftUI

/// Automata tab — placeholder for Story 9.
struct AutomataView: View {
    var body: some View {
        LoadingIndicator(message: "Automata — coming in Story 9")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(.principal) {
                    HeaderView(title: "Automata")
                }
            }
    }
}

#if DEBUG
#Preview {
    NavigationStack { AutomataView() }
        .preferredColorScheme(.dark)
}
#endif
