import SwiftUI
import DatawatchShared

#if DEBUG
/// DEBUG-build-only launch hooks for simulator screenshot passes against the
/// sandbox test daemon (never compiled into Release/TestFlight builds).
///
///   -dwSeedURL https://<host>:18443   add a self-signed (trust-all) profile once
///   -dwSeedToken <token>              bearer token for that profile
///   -dwSeedName <name>                display name (default "sandbox")
///   -dwSeedTrust system               seed with normal certificate checking
///                                     (no trust-all) — for servers with a real
///                                     CA certificate, e.g. the review demo server
///   -dwTab sessions|alerts|automata|observer|dashboard|settings
///   -dwTheme dark|light|system
///   -dwOpenSession <id>               open that session (same path as a
///                                     datawatch://session/<id> link, no OS prompt)
///   -dwOpenAutomaton <id>             open that Automaton's detail (with -dwTab automata)
///
/// Values come from the `xcrun simctl launch` command line only; nothing is
/// stored in the repo.
enum DebugLaunchHooks {
    static func arg(_ name: String) -> String? {
        let args = ProcessInfo.processInfo.arguments
        guard let i = args.firstIndex(of: name), i + 1 < args.count else { return nil }
        return args[i + 1]
    }

    @MainActor
    static func seedServer(store: ServerProfileStore) {
        guard let url = arg("-dwSeedURL") else { return }
        let name = arg("-dwSeedName") ?? "sandbox"
        guard !store.profiles.contains(where: { $0.baseUrl == url }) else { return }
        let svc = IosServiceLocator.shared
        let profile = ServerProfile(
            id: svc.generateProfileId(),
            displayName: name,
            baseUrl: url,
            bearerTokenRef: "",
            trustAnchorSha256: arg("-dwSeedTrust") == "system" ? nil : svc.TRUST_ALL_SENTINEL,
            reachabilityProfileId: svc.generateProfileId(),
            enabled: true,
            createdTs: svc.nowMillis(),
            lastSeenTs: nil,
            signalLinked: false
        )
        store.save(profile: profile, token: arg("-dwSeedToken"), onSuccess: {
            print("DebugLaunchHooks: seeded \(name)")
        }, onError: { msg in
            print("DebugLaunchHooks: seed failed: \(msg)")
        })
    }

    static var initialTab: AppTab? { arg("-dwTab").flatMap { AppTab(rawValue: $0) } }

    static var openSessionURL: URL? {
        arg("-dwOpenSession").flatMap { URL(string: "datawatch://session/\($0)") }
    }

    /// `-dwOpenAutomaton <prd id>`: push that Automaton's detail once the
    /// Automata list has loaded it (one-shot per launch).
    static var openAutomatonId: String? { arg("-dwOpenAutomaton") }
    @MainActor static var automatonOpened = false

    static func applyTheme() {
        if let t = arg("-dwTheme") { UserDefaults.standard.set(t, forKey: "dw.theme") }
    }
}
#endif
