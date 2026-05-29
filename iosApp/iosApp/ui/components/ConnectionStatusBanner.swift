import SwiftUI

/// Disconnected / reconnecting banner shown below the navigation bar.
/// Matches Android's `ConnectionStatusBanner` composable.
///
/// State is driven by a `ConnectionState` enum — the ViewModel wires this
/// in Story 4 (Auth / ServiceLocator) once the KMP transport layer is connected.
struct ConnectionStatusBanner: View {
    let state: ConnectionState

    var body: some View {
        if state != .connected {
            HStack(spacing: 8) {
                ProgressView()
                    .tint(state.tintColor)
                    .controlSize(.mini)

                Text(state.label)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(state.tintColor)

                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(DatawatchColors.surface)
            .transition(.move(edge: .top).combined(with: .opacity))
            .accessibilityElement(children: .combine)
            .accessibilityLabel(state.accessibilityLabel)
        }
    }
}

enum ConnectionState: Equatable {
    case connected
    case connecting
    case reconnecting(attempt: Int)
    case disconnected

    var label: String {
        switch self {
        case .connected:              ""
        case .connecting:             "Connecting…"
        case .reconnecting(let n):    n > 1 ? "Reconnecting (attempt \(n))…" : "Reconnecting…"
        case .disconnected:           "Disconnected"
        }
    }

    var tintColor: Color {
        switch self {
        case .connected:    .clear
        case .connecting:   DatawatchColors.primary
        case .reconnecting: DatawatchColors.warning
        case .disconnected: DatawatchColors.error
        }
    }

    var accessibilityLabel: String {
        switch self {
        case .connected:              "Connected"
        case .connecting:             "Connecting to server"
        case .reconnecting(let n):    "Reconnecting to server, attempt \(n)"
        case .disconnected:           "Disconnected from server"
        }
    }
}

#if DEBUG
#Preview("Banners") {
    VStack(spacing: 0) {
        ConnectionStatusBanner(state: .connecting)
        ConnectionStatusBanner(state: .reconnecting(attempt: 3))
        ConnectionStatusBanner(state: .disconnected)
        ConnectionStatusBanner(state: .connected) // renders nothing
        Spacer()
    }
    .background(DatawatchColors.background)
    .preferredColorScheme(.dark)
}
#endif
