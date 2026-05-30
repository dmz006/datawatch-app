import SwiftUI
import DatawatchShared

/// Detail screen for a single session — shows the live terminal plus metadata bar.
struct SessionDetailView: View {
    let session: Session
    let profile: ServerProfile

    @State private var isKilling = false
    @State private var killError: String? = nil
    @State private var showKillConfirm = false
    @State private var isRestarting = false
    @State private var showDeleteConfirm = false
    @State private var isDeleting = false
    @State private var showRenameDialog = false
    @State private var renameText: String = ""
    @State private var showLastResponse = false
    @State private var replyText: String = ""
    @State private var isSendingReply: Bool = false
    @State private var termFontSize: Int = UserDefaults.standard.integer(forKey: "dw.terminal.font_size_px").nonZero ?? 9
    @State private var messagingBackend: String? = nil
    @State private var terminalInput: String? = nil
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            DatawatchColors.background.ignoresSafeArea()

            VStack(spacing: 0) {
                metadataBar
                terminalFontBar
                TerminalView(session: session, profile: profile, fontSize: $termFontSize, terminalInput: $terminalInput)
                    .ignoresSafeArea(edges: .bottom)
                if isTerminalState {
                    terminalActionBar
                } else {
                    composerBar
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(.principal) {
                Button {
                    renameText = sessionTitle
                    showRenameDialog = true
                } label: {
                    Text(sessionTitle)
                        .font(DatawatchFonts.titleMedium)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .lineLimit(1)
                }
                .accessibilityLabel("Rename session")
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 4) {
                    DocsLinkButton(profile: profile, anchor: "sessions")
                    if let resp = session.lastResponse, !resp.isEmpty {
                        Button { showLastResponse = true } label: {
                            Image(systemName: "doc.text")
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                        .accessibilityLabel("View last response")
                    }
                    killButton
                }
            }
        }
        .alert("Kill session?", isPresented: $showKillConfirm) {
            Button("Kill", role: .destructive) { performKill() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This stops the tmux session on the server. The session cannot be resumed (a new session would need to be started).")
        }
        .alert("Delete this session?", isPresented: $showDeleteConfirm) {
            Button("Delete", role: .destructive) { performDelete() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Permanently delete \"\(sessionTitle)\"?")
        }
        .alert("Rename session", isPresented: $showRenameDialog) {
            TextField("Display name", text: $renameText)
                .autocorrectionDisabled()
            Button("Save") { performRename() }
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $showLastResponse) {
            LastResponseSheet(session: session, onDismiss: { showLastResponse = false })
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
        .onAppear { fetchMessagingBackend() }
    }

    private func fetchMessagingBackend() {
        IosServiceLocator.shared.fetchServerInfo(
            profile: profile,
            onSuccess: { info in
                DispatchQueue.main.async {
                    let mb = info.messagingBackend ?? "tmux"
                    let normalized = mb.lowercased()
                    if !["tmux", "", "none"].contains(normalized) {
                        self.messagingBackend = normalized
                    }
                }
            },
            onError: { _ in }
        )
    }

    // ── Metadata bar ──────────────────────────────────────────────────────

    @ViewBuilder
    private var metadataBar: some View {
        let hasMetadata = session.backend != nil ||
            session.llmRef != nil ||
            session.computeNodeRef != nil ||
            session.agentId != nil ||
            session.chrome ||
            messagingBackend != nil
        if hasMetadata {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    if let backend = session.backend, !backend.isEmpty {
                        metaBadge(backend.lowercased(), color: DatawatchColors.primary)
                    }
                    if let llm = session.llmRef, !llm.isEmpty {
                        metaBadge("⚡ \(llm)", color: DatawatchColors.success)
                    }
                    if let node = session.computeNodeRef, !node.isEmpty {
                        metaBadge("⚙ \(node)", color: DatawatchColors.secondary)
                    }
                    if let mb = messagingBackend {
                        metaBadge(mb, color: DatawatchColors.secondary)
                    }
                    if let agentId = session.agentId {
                        metaBadge("⬡ \(agentId)", color: DatawatchColors.secondary)
                    }
                    if session.chrome {
                        metaBadge("Chrome", color: DatawatchColors.primary)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
            }
            .background(DatawatchColors.surface)
            Divider().background(DatawatchColors.border)
        }
    }

    private func metaBadge(_ text: String, color: Color) -> some View {
        Text(text)
            .font(DatawatchFonts.badge)
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.12))
            .clipShape(Capsule())
    }

    // ── Terminal state ────────────────────────────────────────────────────

    private var isTerminalState: Bool {
        session.state == .completed || session.state == .killed || session.state == .error
    }

    // ── Terminal font-size toolbar (PWA Aa▾ parity) ───────────────────────

    private var terminalFontBar: some View {
        HStack(spacing: 0) {
            Spacer()
            Button {
                if termFontSize > 5 {
                    termFontSize -= 1
                    UserDefaults.standard.set(termFontSize, forKey: "dw.terminal.font_size_px")
                }
            } label: {
                Text("A−")
                    .font(.system(.caption, design: .monospaced).weight(.medium))
                    .foregroundStyle(termFontSize > 5 ? DatawatchColors.onSurface : DatawatchColors.onSurfaceMuted)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .disabled(termFontSize <= 5)
            .accessibilityLabel("Decrease font size")

            Text("\(termFontSize)px")
                .font(.system(.caption2, design: .monospaced))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .padding(.horizontal, 4)

            Button {
                if termFontSize < 20 {
                    termFontSize += 1
                    UserDefaults.standard.set(termFontSize, forKey: "dw.terminal.font_size_px")
                }
            } label: {
                Text("A+")
                    .font(.system(.caption, design: .monospaced).weight(.medium))
                    .foregroundStyle(termFontSize < 20 ? DatawatchColors.onSurface : DatawatchColors.onSurfaceMuted)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .disabled(termFontSize >= 20)
            .accessibilityLabel("Increase font size")
        }
        .background(DatawatchColors.surface)
        .overlay(Divider().background(DatawatchColors.border), alignment: .bottom)
    }

    // ── Composer bar (active sessions) ───────────────────────────────────

    private var isWaiting: Bool { session.state == .waiting }

    private var composerBar: some View {
        VStack(spacing: 0) {
            if isWaiting {
                Rectangle()
                    .fill(DatawatchColors.waiting)
                    .frame(height: 2)
            } else {
                Divider().background(DatawatchColors.border)
            }
            HStack(spacing: 8) {
                TextField(isWaiting ? "Type a reply…" : "Reply or press Enter", text: $replyText)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(isWaiting ? DatawatchColors.waiting.opacity(0.08) : DatawatchColors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                Button {
                    sendReply()
                } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(replyText.isEmpty ? DatawatchColors.onSurfaceMuted : (isWaiting ? DatawatchColors.waiting : DatawatchColors.primary))
                }
                .disabled(replyText.isEmpty || isSendingReply)
                .accessibilityLabel("Send reply")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(DatawatchColors.background)
        }
    }

    // ── Terminal action bar (completed / killed / error sessions) ─────────

    private var terminalActionBar: some View {
        VStack(spacing: 0) {
            Divider().background(DatawatchColors.border)
            HStack(spacing: 12) {
                Button {
                    performRestart()
                } label: {
                    HStack(spacing: 4) {
                        if isRestarting {
                            ProgressView().controlSize(.small).tint(DatawatchColors.primary)
                        } else {
                            Image(systemName: "arrow.counterclockwise")
                        }
                        Text("Restart")
                    }
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.primary)
                }
                .disabled(isRestarting || isDeleting)

                Spacer()

                Button {
                    showDeleteConfirm = true
                } label: {
                    HStack(spacing: 4) {
                        if isDeleting {
                            ProgressView().controlSize(.small).tint(DatawatchColors.error)
                        } else {
                            Image(systemName: "trash")
                        }
                        Text("Delete")
                    }
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.error)
                }
                .disabled(isRestarting || isDeleting)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(DatawatchColors.background)
        }
    }

    private func sendReply() {
        let text = replyText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        replyText = ""
        // Route through the terminal WebSocket (window.sendInput) so input lands
        // in the PTY exactly as if typed — REST /api/sessions/reply returns 404.
        // Append \r so the shell executes the command.
        terminalInput = text + "\r"
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
                    self.isKilling = false
                    dismiss()
                }
            },
            onError: { message in
                DispatchQueue.main.async {
                    self.isKilling = false
                    self.killError = message
                }
            }
        )
    }

    private func performRestart() {
        isRestarting = true
        IosServiceLocator.shared.restartSession(
            profile: profile,
            sessionId: session.id,
            onSuccess: {
                DispatchQueue.main.async { self.isRestarting = false }
            },
            onError: { _ in
                DispatchQueue.main.async { self.isRestarting = false }
            }
        )
    }

    private func performDelete() {
        isDeleting = true
        IosServiceLocator.shared.deleteSession(
            profile: profile,
            sessionId: session.id,
            onSuccess: {
                DispatchQueue.main.async {
                    self.isDeleting = false
                    dismiss()
                }
            },
            onError: { _ in
                DispatchQueue.main.async { self.isDeleting = false }
            }
        )
    }

    private func performRename() {
        let name = renameText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        IosServiceLocator.shared.renameSession(
            profile: profile,
            sessionId: session.id,
            name: name,
            onSuccess: {},
            onError: { _ in }
        )
    }
}

// ── Int helper ───────────────────────────────────────────────────────────────

private extension Int {
    var nonZero: Int? { self == 0 ? nil : self }
}

// ── Last response sheet ───────────────────────────────────────────────────────

private struct LastResponseSheet: View {
    let session: Session
    let onDismiss: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(session.lastResponse ?? "")
                    .font(.system(.body, design: .monospaced))
                    .foregroundStyle(DatawatchColors.onSurface)
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .background(DatawatchColors.background)
            .navigationTitle("Last Response")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { onDismiss() }
                }
            }
        }
        .preferredColorScheme(.dark)
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
        lastResponse: "Here is the response from the model.",
        backend: "claude-code",
        outputMode: "terminal",
        inputMode: "tmux",
        agentId: nil,
        llmRef: "claude-3-7-sonnet",
        computeNodeRef: nil,
        chrome: false
    )
    NavigationStack {
        SessionDetailView(session: session, profile: profile)
    }
    .preferredColorScheme(.dark)
}
#endif
