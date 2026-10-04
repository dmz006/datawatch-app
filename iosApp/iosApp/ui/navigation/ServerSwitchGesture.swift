import SwiftUI
import UIKit

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

/// Server picker dialog shown by the gesture (only when 2+ servers).
struct ServerPickerDialogModifier: ViewModifier {
    @EnvironmentObject private var store: ServerProfileStore
    @State private var show = false

    func body(content: Content) -> some View {
        content
            .onAppear { ServerSwitchGesture.shared.install() }
            .onReceive(NotificationCenter.default.publisher(for: .dwShowServerPicker)) { _ in
                if store.enabledProfiles.count > 1 { show = true }
            }
            .confirmationDialog("Switch server", isPresented: $show, titleVisibility: .visible) {
                ForEach(store.enabledProfiles, id: \.id) { p in
                    Button(p.id == store.activeProfile?.id ? "✓ \(p.displayName)" : p.displayName) {
                        store.selectActive(p.id)
                    }
                }
                Button("Cancel", role: .cancel) {}
            }
    }
}
