import SwiftUI
import DatawatchShared

/// Detail screen for a single session — shows the live terminal.
struct SessionDetailView: View {
    let session: Session
    let profile: ServerProfile

    @State private var isKilling = false
    @State private var killError: String? = nil
    @State private var showKillConfirm = false
    @State private var replyText: String = ""
    @State private var isSendingReply: Bool = false
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            DatawatchColors.background.ignoresSafeArea()

            VStack(spacing: 0) {
                TerminalView(session: session, profile: profile)
                    .ignoresSafeArea(edges: .bottom)
                if !isTerminalState {
                    composerBar
                }
            }
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
                HStack(spacing: 4) {
                    DocsLinkButton(profile: profile, anchor: "sessions")
                    killButton
                }
            }
        }
        .alert("Stop this session?", isPresented: $showKillConfirm) {
            Button("Stop", role: .destructive) { performKill() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Stop \"\(sessionTitle)\"?")
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

    // ── Terminal state ────────────────────────────────────────────────────

    private var isTerminalState: Bool {
        session.state == .completed || session.state == .killed || session.state == .error
    }

    // ── Composer bar ──────────────────────────────────────────────────────

    private var composerBar: some View {
        VStack(spacing: 0) {
            Divider().background(DatawatchColors.border)
            HStack(spacing: 8) {
                TextField("Reply or press Enter", text: $replyText)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(DatawatchColors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                Button {
                    sendReply()
                } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(replyText.isEmpty ? DatawatchColors.onSurfaceMuted : DatawatchColors.primary)
                }
                .disabled(replyText.isEmpty || isSendingReply)
                .accessibilityLabel("Send reply")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(DatawatchColors.background)
        }
    }

    private func sendReply() {
        let text = replyText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        replyText = ""
        isSendingReply = true
        IosServiceLocator.shared.replyToSession(
            profile: profile,
            sessionId: session.id,
            text: text,
            onSuccess: {
                DispatchQueue.main.async { self.isSendingReply = false }
            },
            onError: { _ in
                DispatchQueue.main.async { self.isSendingReply = false }
            }
        )
    }

    // ── Kill button ───────────────────────────────────────────────────────

    @ViewBuilder
    private var killButton: some View {
        if !isTerminalState {
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
                .accessibilityLabel("Stop session")
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
#Preview("SessionDetail — connecting") {
    let profile = ServerProfile(
        id: "preview-server",
        displayName: "Local dev",
        baseUrl: "http://localhost:8080",
        bearerTokenRef: "",
        trustAnchorSha256: nil,
        reachabilityProfileId: nil,
        enabled: true,
        createdTs: 0,
        lastSeenTs: 0,
        signalLinked: false
    )
    let now = Kotlinx_datetimeInstant.companion.fromEpochMilliseconds(epochMilliseconds: 0)
    let session = Session(
        id: "abc123",
        serverProfileId: "preview-server",
        hostnamePrefix: "ring",
        state: SessionState.running,
        taskSummary: "Implementing iOS previews",
        createdAt: now,
        lastActivityAt: now,
        muted: false,
        lastPrompt: nil,
        name: nil,
        promptContext: nil,
        lastResponse: nil,
        backend: "claude-code",
        outputMode: "terminal",
        inputMode: "tmux",
        agentId: nil,
        llmRef: nil,
        computeNodeRef: nil,
        chrome: false
    )
    NavigationStack {
        SessionDetailView(session: session, profile: profile)
    }
    .preferredColorScheme(.dark)
}
#endif
