import SwiftUI
import AVFoundation
import DatawatchShared

/// Detail screen for a single session — shows the live terminal plus metadata bar.
struct SessionDetailView: View {
    let session: DwSession
    let profile: ServerProfile

    @State private var isKilling = false
    @State private var killError: String? = nil
    @State private var showKillConfirm = false
    @State private var isRestarting = false
    @State private var showDeleteSheet = false
    @State private var showTimeline = false
    @State private var stateOverrideLabel: String? = nil
    @State private var overridingState = false
    @State private var isDeleting = false
    @State private var showRenameDialog = false
    @State private var renameText: String = ""
    @State private var showLastResponse = false
    @State private var replyText: String = ""
    @State private var termFontSize: Int = UserDefaults.standard.integer(forKey: "dw.terminal.font_size_px").nonZero ?? 9
    @State private var messagingBackend: String? = nil
    @State private var terminalInput: String? = nil
    @State private var whisperEnabled = false
    @State private var voiceRecorder: VoiceRecorder? = nil
    @State private var isTranscribing = false
    @State private var recordingPulse = false
    @Environment(\.dismiss) private var dismiss
    /// PWA output tab bar: "tmux" (terminal) or "status".
    @State private var detailTab = "tmux"
    /// Status tab sub-tabs (PWA switchStatusSubtab): "status" | "stats".
    @State private var statusSubtab = "status"
    @StateObject private var terminal = TerminalController()
    /// PWA scroll mode (tmux copy-mode): the scroll strip replaces the input bar.
    @State private var scrollMode = false

    var body: some View {
        ZStack {
            DatawatchColors.background.ignoresSafeArea()

            VStack(spacing: 0) {
                metadataBar
                detailTabBar
                if detailTab == "tmux" { terminalFontBar }
                ZStack {
                    // Kept mounted while Status is shown so the session socket stays open.
                    TerminalView(session: session, profile: profile, fontSize: $termFontSize, terminalInput: $terminalInput, controller: terminal)
                        .ignoresSafeArea(edges: .bottom)
                        .opacity(detailTab == "tmux" ? 1 : 0)
                        .allowsHitTesting(detailTab == "tmux")
                    if detailTab == "status" {
                        VStack(spacing: 0) {
                            statusSubtabStrip
                            if statusSubtab == "stats" {
                                SessionStatsView(profile: profile, session: session)
                            } else {
                                SessionStatusView(profile: profile, session: session)
                            }
                        }
                        .background(DatawatchColors.background)
                    }
                }
                if isTerminalState {
                    terminalActionBar
                } else if scrollMode && detailTab == "tmux" {
                    scrollStrip
                } else {
                    composerBar
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
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
                    Button { showTimeline = true } label: {
                        Image(systemName: "clock")
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .accessibilityLabel("Timeline")
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
        .sheet(isPresented: $showDeleteSheet) {
            SessionDeleteSheet(profile: profile, session: session) { dismiss() }
        }
        .sheet(isPresented: $showTimeline) {
            SessionTimelineSheet(profile: profile, session: session)
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
        .onAppear {
            terminal.onAutoFontSize = { px in termFontSize = px }
            fetchMessagingBackend()
            IosServiceLocator.shared.fetchWhisperEnabled(profile: profile) { enabled in
                DispatchQueue.main.async { self.whisperEnabled = enabled.boolValue }
            }
        }
        // Recording overlay — shown while mic is active.
        .overlay {
            if voiceRecorder != nil {
                recordingOverlay
            }
        }
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

    // ── Output tab bar (PWA: Tmux · Status) ──────────────────────────────

    private var detailTabBar: some View {
        HStack(spacing: 0) {
            ForEach([("tmux", "Tmux"), ("status", "Status")], id: \.0) { tab in
                Button {
                    detailTab = tab.0
                } label: {
                    VStack(spacing: 4) {
                        Text(tab.1)
                            .font(DatawatchFonts.badge)
                            .foregroundStyle(detailTab == tab.0 ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
                        Rectangle()
                            .fill(detailTab == tab.0 ? DatawatchColors.primary : Color.clear)
                            .frame(height: 2)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 6)
                }
                .accessibilityAddTraits(detailTab == tab.0 ? .isSelected : [])
            }
        }
        .background(DatawatchColors.surface)
        .overlay(Divider().background(DatawatchColors.border), alignment: .bottom)
    }

    private var statusSubtabStrip: some View {
        HStack(spacing: 18) {
            ForEach([("status", "Status"), ("stats", "Stats")], id: \.0) { tab in
                Button {
                    statusSubtab = tab.0
                } label: {
                    VStack(spacing: 3) {
                        Text(tab.1)
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(statusSubtab == tab.0 ? DatawatchColors.waiting : DatawatchColors.onSurfaceMuted)
                        Rectangle()
                            .fill(statusSubtab == tab.0 ? DatawatchColors.waiting : Color.clear)
                            .frame(height: 2)
                    }
                    .fixedSize()
                }
            }
            Spacer()
        }
        .padding(.horizontal, 12)
        .padding(.top, 6)
    }

    // ── Keys strip (PWA: ␛ · ↑ ↓ ← → · ⏎, right-aligned) ────────────────

    private var keysStrip: some View {
        HStack(spacing: 6) {
            Spacer()
            keyButton("␛", key: "Escape", label: "Escape")
            Text("·").foregroundStyle(DatawatchColors.onSurfaceMuted)
            keyButton("↑", key: "Up", label: "Arrow up")
            keyButton("↓", key: "Down", label: "Arrow down")
            keyButton("←", key: "Left", label: "Arrow left")
            keyButton("→", key: "Right", label: "Arrow right")
            Text("·").foregroundStyle(DatawatchColors.onSurfaceMuted)
            keyButton("⏎", key: "Enter", label: "Enter")
        }
        .padding(.horizontal, 12)
        .padding(.top, 6)
    }

    private func keyButton(_ glyph: String, key: String, label: String) -> some View {
        Button {
            _ = IosSessionOps.shared.sendKey(session: session, key: key)
        } label: {
            Text(glyph)
                .font(.system(size: 15, weight: .semibold, design: .monospaced))
                .foregroundStyle(DatawatchColors.onSurface)
                .frame(minWidth: 34, minHeight: 30)
                .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 6))
        }
        .accessibilityLabel(label)
    }

    // ── Scroll mode (PWA toggleScrollMode / scrollPage / exitScrollMode) ─

    private func toggleScrollMode() {
        if scrollMode { exitScrollMode(); return }
        scrollMode = true
        terminal.setScrollMode(true)
        _ = IosSessionOps.shared.tmuxCommand(session: session, command: "tmux-copy-mode")
    }

    private func scrollPage(up: Bool) {
        terminal.scrollPendingRefresh()
        _ = IosSessionOps.shared.tmuxCommand(session: session, command: up ? "tmux-page-up" : "tmux-page-down")
    }

    private func exitScrollMode() {
        _ = IosSessionOps.shared.sendKey(session: session, key: "Escape")
        terminal.setScrollMode(false)
        scrollMode = false
    }

    private var scrollStrip: some View {
        VStack(spacing: 0) {
            Rectangle().fill(DatawatchColors.warning).frame(height: 2)
            HStack(spacing: 8) {
                scrollButton("▲ Page Up") { scrollPage(up: true) }
                scrollButton("▼ Page Down") { scrollPage(up: false) }
                scrollButton("ESC — Exit Scroll", tint: DatawatchColors.warning) { exitScrollMode() }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(DatawatchColors.background)
        }
    }

    private func scrollButton(_ title: String, tint: Color = DatawatchColors.onSurface, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(tint)
                .frame(maxWidth: .infinity, minHeight: 36)
                .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 6))
        }
    }

    // ── State badge → state override (PWA showStateOverride) ─────────────

    private static let overrideStates: [(wire: String, label: String)] = [
        ("running", "Running"), ("waiting_input", "Waiting input"), ("complete", "Complete"),
        ("killed", "Killed"), ("failed", "Failed"),
    ]

    private var currentStateLabel: String {
        if let o = stateOverrideLabel { return o }
        switch session.state {
        case .running: return "Running"
        case .waiting: return "Waiting input"
        case .rateLimited: return "Rate limited"
        case .completed: return "Complete"
        case .killed: return "Killed"
        case .error: return "Failed"
        default: return "New"
        }
    }

    private var stateMenu: some View {
        Menu {
            Section("Set state") {
                ForEach(Self.overrideStates, id: \.wire) { st in
                    Button(st.label) { overrideState(st.wire, label: st.label) }
                }
            }
        } label: {
            HStack(spacing: 4) {
                if overridingState { ProgressView().controlSize(.mini) }
                Text(currentStateLabel.uppercased())
                    .font(DatawatchFonts.badge)
                Image(systemName: "chevron.down").font(.system(size: 8, weight: .bold))
            }
            .foregroundStyle(DatawatchColors.primary)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(DatawatchColors.primary.opacity(0.12), in: Capsule())
        }
        .disabled(overridingState)
        .accessibilityLabel("Session state \(currentStateLabel). Change state")
    }

    private func overrideState(_ wire: String, label: String) {
        overridingState = true
        IosSessionOps.shared.overrideState(
            profile: profile,
            session: session,
            wireState: wire,
            onSuccess: {
                DispatchQueue.main.async {
                    overridingState = false
                    stateOverrideLabel = label
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    overridingState = false
                    killError = msg
                }
            }
        )
    }

    // ── Terminal font-size toolbar (PWA Aa▾ parity) ───────────────────────

    private var terminalFontBar: some View {
        HStack(spacing: 0) {
            stateMenu
                .padding(.leading, 12)
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

            Button {
                terminal.fitToWidth()
            } label: {
                Text("Fit")
                    .font(.system(.caption, design: .monospaced).weight(.medium))
                    .foregroundStyle(DatawatchColors.onSurface)
                    .frame(minWidth: 44, minHeight: 44)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Fit terminal to width")

            if !isTerminalState {
                Button {
                    toggleScrollMode()
                } label: {
                    Text(scrollMode ? "⏹" : "⤒")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(scrollMode ? DatawatchColors.warning : DatawatchColors.onSurface)
                        .frame(minWidth: 44, minHeight: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(scrollMode ? "Exit scroll mode" : "Scroll mode")
            }
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
            keysStrip
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
                if whisperEnabled {
                    if isTranscribing {
                        ProgressView()
                            .controlSize(.small)
                            .tint(DatawatchColors.onSurfaceMuted)
                    } else {
                        Button {
                            startRecording()
                        } label: {
                            Image(systemName: "mic")
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                        .accessibilityLabel("Record voice message")
                    }
                }
                Button {
                    sendReply()
                } label: {
                    Image(systemName: "paperplane.fill")
                        .foregroundStyle(replyText.isEmpty ? DatawatchColors.onSurfaceMuted : (isWaiting ? DatawatchColors.waiting : DatawatchColors.primary))
                }
                .disabled(replyText.isEmpty)
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
                    showDeleteSheet = true
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
        // TerminalView forwards this as a `send_input` frame on the session's
        // /ws hub (WsOutbound) — the only reply path the server exposes.
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

    // ── Voice recording ───────────────────────────────────────────────────

    @ViewBuilder
    private var recordingOverlay: some View {
        ZStack {
            Color.black.opacity(0.5).ignoresSafeArea()
            VStack(spacing: 20) {
                Image(systemName: "mic.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(DatawatchColors.error)
                    .opacity(recordingPulse ? 1.0 : 0.45)
                    .onAppear {
                        withAnimation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true)) {
                            recordingPulse = true
                        }
                    }
                    .onDisappear { recordingPulse = false }
                Text("Recording…")
                    .font(DatawatchFonts.titleMedium)
                    .foregroundStyle(.white)
                HStack(spacing: 16) {
                    Button("Cancel") {
                        voiceRecorder?.cancel()
                        voiceRecorder = nil
                    }
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Button("Send") {
                        stopAndTranscribe()
                    }
                    .font(DatawatchFonts.bodyMedium)
                    .fontWeight(.semibold)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .background(DatawatchColors.error)
                    .clipShape(Capsule())
                }
            }
            .padding(32)
            .background(DatawatchColors.surface)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .padding(24)
        }
    }

    private func startRecording() {
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            DispatchQueue.main.async {
                guard granted else { return }
                let rec = VoiceRecorder()
                do {
                    try rec.start()
                    self.voiceRecorder = rec
                } catch {
                    // hardware error after permission granted — silently drop
                }
            }
        }
    }

    private func stopAndTranscribe() {
        guard let rec = voiceRecorder else { return }
        voiceRecorder = nil
        isTranscribing = true
        guard let audioData = rec.stop() else {
            isTranscribing = false
            return
        }
        IosServiceLocator.shared.transcribeAudioData(
            audioData: audioData,
            audioMime: VoiceRecorder.mimeType,
            sessionId: session.id,
            profile: profile,
            onSuccess: { transcript in
                DispatchQueue.main.async {
                    self.replyText = transcript
                    self.isTranscribing = false
                }
            },
            onError: { _ in
                DispatchQueue.main.async { self.isTranscribing = false }
            }
        )
    }
}

// ── Int helper ───────────────────────────────────────────────────────────────

private extension Int {
    var nonZero: Int? { self == 0 ? nil : self }
}

// ── Last response sheet ───────────────────────────────────────────────────────

private struct LastResponseSheet: View {
    let session: DwSession
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

