import SwiftUI
import DatawatchShared

/// #236.2 / #235 — PWA federated connection status (app.js renderSessionsView,
/// v8.73.2–v8.73.3), shared with Android's `FedConnStatusPane`: the eye +
/// "Connecting to X…" / "Loading sessions from X…" while the real probe runs,
/// or "Could not reach this server" with "X: reason" (the specific auth text on
/// 401/403) and a button back to the server the remote is reached through.
struct FedConnStatusView: View {
    @EnvironmentObject private var store: ServerProfileStore
    let status: IosFedConnState

    var body: some View {
        switch status.phase {
        case "error":
            errorBody
        case "loading":
            LoadingIndicator(message: String(format: L("Loading sessions from %@…"), status.serverName))
        default:
            LoadingIndicator(message: String(format: L("Connecting to %@…"), status.serverName))
        }
    }

    private var reason: String {
        status.authFailed ? L("Authentication failed — this server has no valid token configured") : status.reason
    }

    private var errorBody: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(.largeTitle))
                .imageScale(.large)
                .foregroundStyle(DatawatchColors.error)
                .accessibilityHidden(true)
            Text(L("Could not reach this server"))
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .multilineTextAlignment(.center)
            Text(String(format: L("%1$@: %2$@"), status.serverName, reason))
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            Button(String(format: L("Back to %@"), status.parentName)) {
                store.selectActive(status.parentId)
            }
            .font(DatawatchFonts.bodyMedium)
            .foregroundStyle(DatawatchColors.primary)
            .padding(.horizontal, 24)
            .padding(.vertical, 10)
            .overlay(Capsule().stroke(DatawatchColors.primary, lineWidth: 1))
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
    }
}

#if DEBUG
struct FedConnStatusView_Previews: PreviewProvider {
    static var previews: some View {
        FedConnStatusView(status: IosFedConnState(
            profileId: "p::proxy::demo", serverName: "demo", parentId: "p", parentName: "workstation",
            phase: "error", reason: "", authFailed: true
        ))
        .environmentObject(ServerProfileStore())
    }
}
#endif
