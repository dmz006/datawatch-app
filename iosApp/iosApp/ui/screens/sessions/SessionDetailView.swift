import SwiftUI
import DatawatchShared

/// Detail screen for a single session — shows the live terminal.
struct SessionDetailView: View {
    let session: Session
    let profile: ServerProfile

    @State private var isKilling = false
    @State private var killError: String? = nil
    @State private var showKillConfirm = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            DatawatchColors.background.ignoresSafeArea()

            TerminalView(session: session, profile: profile)
                .ignoresSafeArea(edges: .bottom)
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(.principal) {
                Text(sessionTitle)
                    .font(DatawatchFonts.titleMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(1)
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                killButton
            }
        }
        .alert("Kill session?", isPresented: $showKillConfirm) {
            Button("Kill", role: .destructive) { performKill() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This will terminate \"\(sessionTitle)\" immediately.")
        }
        .overlay(alignment: .top) {
            if let errorMsg = killError {
                Text(errorMsg)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(DatawatchColors.surface)
                    .cornerRadius(8)
                    .padding(.top, 8)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .onTapGesture { killError = nil }
            }
        }
        .animation(.easeInOut, value: killError)
    }

    // ── Kill button ───────────────────────────────────────────────────────

    @ViewBuilder
    private var killButton: some View {
        let isTerminal = session.state == .completed
            || session.state == .killed
            || session.state == .error

        if !isTerminal {
            if isKilling {
                ProgressView()
                    .tint(DatawatchColors.error)
                    .controlSize(.small)
            } else {
                Button {
                    showKillConfirm = true
                } label: {
                    Image(systemName: "stop.circle")
                        .foregroundStyle(DatawatchColors.error)
                }
                .accessibilityLabel("Kill session")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private var sessionTitle: String {
        if let name = session.name, !name.isEmpty { return name }
        if let task = session.taskSummary, !task.isEmpty { return task }
        return session.id
    }

    private func performKill() {
        isKilling = true
        killError = nil
        IosServiceLocator.shared.killSession(
            profile: profile,
            sessionId: session.id,
            onSuccess: {
                DispatchQueue.main.async {
                    isKilling = false
                    dismiss()
                }
            },
            onError: { message in
                DispatchQueue.main.async {
                    isKilling = false
                    killError = message
                }
            }
        )
    }
}

#if DEBUG
// Preview requires a real Kotlin Session — use a stub in a future preview helper.
#endif
