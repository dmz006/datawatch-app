import SwiftUI
import SafariServices
import DatawatchShared

/// Toolbar button that opens the server-hosted definitions doc at a specific anchor.
///
/// URL format: `<profile.baseUrl>/diagrams.html#docs/datawatch-definitions.md#<anchor>`
/// Matches Android's `DocsLinkAction` composable.
///
/// If `profile` is nil the button is hidden — no active server, no docs.
struct DocsLinkButton: View {
    let profile: ServerProfile?
    let anchor: String

    @State private var showSafari = false

    private var docsURL: URL? {
        guard let profile else { return nil }
        var base = profile.baseUrl
        if base.hasSuffix("/") { base = String(base.dropLast()) }
        let urlString = "\(base)/diagrams.html#docs/datawatch-definitions.md#\(anchor)"
        return URL(string: urlString)
    }

    var body: some View {
        if docsURL != nil {
            Button {
                showSafari = true
            } label: {
                Image(systemName: "questionmark.circle")
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .accessibilityLabel("Open documentation")
            .sheet(isPresented: $showSafari) {
                if let url = docsURL {
                    SafariView(url: url)
                        .ignoresSafeArea()
                }
            }
        }
    }
}

// MARK: - SafariView

private struct SafariView: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        let vc = SFSafariViewController(url: url)
        vc.preferredBarTintColor = UIColor(
            red: 0x1a / 255, green: 0x1d / 255, blue: 0x27 / 255, alpha: 1
        )
        vc.preferredControlTintColor = UIColor(
            red: 0x7c / 255, green: 0x3a / 255, blue: 0xed / 255, alpha: 1
        )
        return vc
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

#if DEBUG
#Preview("With profile") {
    let profile = ServerProfile(
        id: "preview",
        displayName: "Dev server",
        baseUrl: "https://datawatch.example",
        bearerTokenRef: "",
        trustAnchorSha256: nil,
        reachabilityProfileId: nil,
        enabled: true,
        createdTs: 0,
        lastSeenTs: 0,
        signalLinked: false
    )
    NavigationStack {
        Color(DatawatchColors.background)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    DocsLinkButton(profile: profile, anchor: "sessions-list")
                }
            }
    }
    .preferredColorScheme(.dark)
}
#endif
