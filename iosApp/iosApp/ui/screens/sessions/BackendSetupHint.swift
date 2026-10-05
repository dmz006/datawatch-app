import SwiftUI
import DatawatchShared

/// Installed / enabled backends per server (GET /api/backends `llm`) for the New
/// Session backend setup hint. Empty = unknown (no warning), as Android.
final class BackendHintStore: ObservableObject {
    static let shared = BackendHintStore()

    @Published private(set) var installed: [String: [String]] = [:]

    func load(_ profile: ServerProfile) {
        let id: String = profile.id
        IosBackendHint.shared.loadInstalled(profile: profile) { list in
            DispatchQueue.main.async { self.installed[id] = list }
        }
    }

    /// Android `backendNeedsSetup`: the kind is set, the server listed its backends,
    /// and the kind isn't one of them.
    func needsSetup(_ profile: ServerProfile, kind: String) -> Bool {
        IosBackendHint.shared.needsSetup(kind: kind, installed: installed[profile.id] ?? [])
    }
}

/// PWA `#backendWarn` / Android `BackendSetupHint` (parity 08 › Backend setup hint):
/// amber "⚠ Backend not installed or configured" box under the LLM picker.
struct BackendSetupHint: View {
    let kind: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("⚠ Backend not installed or configured")
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(DatawatchColors.warning)
            Text(detail)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(DatawatchColors.warning.opacity(0.08), in: RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.warning.opacity(0.3), lineWidth: 1))
    }

    private var detail: String {
        String(
            format: L("This server doesn't report “%@” as an installed, enabled backend. Install it or enable it in Settings › LLM before starting."),
            kind
        )
    }
}
