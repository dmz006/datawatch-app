import SwiftUI
import SafariServices
import DatawatchShared

/// "?" button that opens the server-hosted docs page for a card or screen.
///
/// `key` is a card id (PWA section key, e.g. `work_queue`) or a `view_*` screen
/// key; the target page + anchor comes from the shared `DocsLinks` table (BL414),
/// the same table Android uses. URL format:
/// `<profile.baseUrl>/diagrams.html#docs/<file.md>#<anchor>`.
/// Matches Android's `DocsLinkAction` composable.
///
/// Hidden when `profile` is nil (no active server) or the key has no entry.
struct DocsLinkButton: View {
    let profile: ServerProfile?
    let key: String

    @State private var showSafari = false

    private var docsURL: URL? {
        guard let profile, let target = DocsLinks.shared.forKey(key: key) else { return nil }
        return URL(string: DocsLinks.shared.viewerUrl(baseUrl: profile.baseUrl, target: target))
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
        reachabilityProfileId: "preview",
        enabled: true,
        createdTs: 0,
        lastSeenTs: 0,
        signalLinked: false
    )
    NavigationStack {
        DatawatchColors.background
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    DocsLinkButton(profile: profile, key: "view_sessions")
                }
            }
    }
    .preferredColorScheme(.dark)
}
#endif
