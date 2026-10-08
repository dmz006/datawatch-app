import SwiftUI
import UIKit
import DatawatchShared

/// D65a: Android's three-finger swipe-up opens the server picker. Installed on
/// the key window as a UISwipeGestureRecognizer that never cancels or delays
/// normal touches, so it can't interfere with scrolling or the terminal.
final class ServerSwitchGesture: NSObject {
    static let shared = ServerSwitchGesture()
    private weak var installedOn: UIWindow?

    func install() {
        guard let window = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .flatMap({ $0.windows })
            .first(where: { $0.isKeyWindow }),
            window !== installedOn else { return }
        let swipe = UISwipeGestureRecognizer(target: self, action: #selector(fired))
        swipe.direction = .up
        swipe.numberOfTouchesRequired = 3
        swipe.cancelsTouchesInView = false
        swipe.delaysTouchesBegan = false
        swipe.delaysTouchesEnded = false
        window.addGestureRecognizer(swipe)
        installedOn = window
    }

    @objc private func fired() {
        NotificationCenter.default.post(name: .dwShowServerPicker, object: nil)
    }
}

extension Notification.Name {
    static let dwShowServerPicker = Notification.Name("dw.showServerPicker")
}

/// Server picker dialog shown by the gesture (only when 2+ choices, remotes
/// reached through a server's /api/proxy included).
struct ServerPickerDialogModifier: ViewModifier {
    @EnvironmentObject private var store: ServerProfileStore
    @State private var show = false

    func body(content: Content) -> some View {
        content
            .onAppear { ServerSwitchGesture.shared.install() }
            .onReceive(NotificationCenter.default.publisher(for: .dwShowServerPicker)) { _ in
                store.refreshProxied()
                // #236 (PWA v8.73.2): the list is loaded eagerly at launch; open
                // even while a server's remotes are still loading, and say so.
                if store.pickerProfiles.count > 1 || store.proxiedLoading { show = true }
            }
            .confirmationDialog("Switch server", isPresented: $show, titleVisibility: .visible) {
                // #234: each server followed by its remotes ("workstation › demo").
                ForEach(store.pickerProfiles, id: \.id) { p in
                    Button(p.id == store.activeProfile?.id ? "✓ \(label(p))" : label(p)) {
                        store.selectActive(p.id)
                    }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                if store.proxiedLoading {
                    Text(L("Loading servers…"))
                }
            }
    }

    /// Dialog buttons can't indent or carry a subtitle, so a remote reads
    /// "↳ workstation › demo (via workstation)".
    private func label(_ p: ServerProfile) -> String {
        guard let parent = IosProxiedServers.shared.parentName(profile: p, real: store.profiles) else {
            return p.displayName
        }
        return "↳ \(p.displayName) (" + String(format: L("via %@"), parent) + ")"
    }
}
