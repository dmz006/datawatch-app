import SwiftUI
import PhotosUI
import AVFoundation
import DatawatchShared

/// Detail screen for a single session — PWA `renderSessionDetail`:
/// info bar (badges · state pill · last activity · ■ Stop / ↻ Restart / 🗑 Delete
/// · 🕑 timeline · 📄 response), pending-schedules strip, channel connection
/// banner, inline process stats, output tabs (with the Aa▾ font menu), the
/// terminal / chat / channel / status panes, saved-commands row and composer.
///
/// `session` is the snapshot the caller navigated with; `cur` is refreshed
/// every 10 s (and after Stop / Restart) so state-dependent controls follow
/// the live session. The terminal keeps the original snapshot (stable socket).
struct SessionDetailView: View {
    let session: DwSession
    let profile: ServerProfile

    @State private var live: DwSession? = nil
    @State private var isStopping = false
    @State private var showStopConfirm = false
    @State private var isRestarting = false
    @State private var showDeleteSheet = false
    @State private var showTimeline = false
    @State private var stateOverrideLabel: String? = nil
    @State private var overridingState = false
    @State private var showRenameDialog = false
    @State private var renameText: String = ""
    @State private var renamedTo: String? = nil
    @State private var showResponse = false
    @State private var replyText: String = ""
    @State private var termFontSize: Int = UserDefaults.standard.integer(forKey: "dw.terminal.font_size_px").nonZero ?? 9
    @State private var terminalInput: String? = nil
    @State private var whisperEnabled = false
    @State private var voiceRecorder: VoiceRecorder? = nil
    @State private var isTranscribing = false
    /// Inline composer note (transcription result): text + isWarning.
    @State private var composerNote: (String, Bool)? = nil
    @State private var recordingPulse = false
    @Environment(\.dismiss) private var dismiss
    /// PWA output tab: "tmux" (terminal / chat), "channel" or "status".
    @State private var detailTab = "tmux"
    /// D67a: last-used output tab persists across sessions (Android chat_mode pref).
    @AppStorage("dw.session.detail.tab") private var savedDetailTab = "tmux"
    /// D61a watch toggle.
    @ObservedObject private var localPrefs = LocalSessionPrefs.shared
    /// D67a rate-limit notice.
    @State private var rateLimitShown = false
    @State private var rateRetryAt: Date? = nil
    /// D69a terminal search / copy strip.
    @State private var showSearch = false
    /// Status tab sub-tabs (PWA switchStatusSubtab): "status" | "stats".
    @State private var statusSubtab = "status"
    @StateObject private var terminal = TerminalController()
    /// PWA scroll mode (tmux copy-mode): the scroll strip replaces the input bar.
    @State private var scrollMode = false
    @State private var scrollBusy = false
    @State private var showSchedule = false
    @State private var scheduleReload = 0
    @State private var photoItem: PhotosPickerItem? = nil
    /// PWA 📷 "Attach image or take photo" (Android gallery / camera sheet).
    @State private var showPhotoLibrary = false
    @State private var showCamera = false
    /// nil = idle; "uploading" or "✓ <name>" for the composer banner (PWA _composerBanner).
    @State private var imageBanner: String? = nil
    /// PWA dismissConnBanner — "use tmux only".
    @State private var connBannerDismissed = false
    /// PWA state.channelReady[full_id]: WS `channel_ready` frame / output-marker scan.
    @State private var hubChannelReady = false
    @State private var channelReadySub: IosSubscription? = nil
    /// PWA tabStatusBadge: hook-health dot + board state, fetched on mount.
    @State private var boardHook: String = ""
    @State private var boardState: String = ""
    /// PWA showChannelHelp popup.
    @State private var showChannelHelp = false

    /// Live copy of the session (falls back to the navigation snapshot).
    private var cur: DwSession { live ?? session }

    var body: some View {
        ZStack {
            DatawatchColors.background.ignoresSafeArea()
            VStack(spacing: 0) {
                infoBar
                PendingSchedulesStrip(profile: profile, session: session, reloadToken: scheduleReload)
                if showConnBanner {
                    ChannelConnectionBanner(mode: sessionMode, waiting: isWaiting) { connBannerDismissed = true }
                }
                if !isDone { InlineProcessStatsBar(profile: profile, session: session) }
                if !isChatMode { detailTabBar }
                if rateLimitShown && !isDone {
                    RateLimitNotice(retryAt: rateRetryAt) { rateLimitShown = false }
                }
                if showSearch && detailTab == "tmux" && !isChatMode && !isLogMode {
                    TerminalSearchBar(controller: terminal) { showSearch = false }
                }
                outputPanes
                bottomBar
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbarContent }
        // PWA view-full: no bottom nav inside a session.
        .toolbar(.hidden, for: .tabBar)
        .alert("Stop session?", isPresented: $showStopConfirm) {
            Button("Stop", role: .destructive) { performStop() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This stops the session on the server. It can be restarted later with the same task.")
        }
        .sheet(isPresented: $showDeleteSheet) {
            SessionDeleteSheet(profile: profile, session: cur) { dismiss() }
        }
        .sheet(isPresented: $showSchedule, onDismiss: { scheduleReload += 1 }) {
            ScheduleInputSheet(profile: profile, session: session, prefill: replyText)
        }
        .sheet(isPresented: $showChannelHelp) {
            ChannelHelpSheet()
        }
        .sheet(isPresented: $showTimeline) {
            SessionTimelineSheet(profile: profile, session: session)
        }
        .sheet(isPresented: $showResponse) {
            SessionResponseSheet(profile: profile, session: cur) { showResponse = false }
        }
        .alert("Rename session", isPresented: $showRenameDialog) {
            TextField("Display name", text: $renameText)
                .autocorrectionDisabled()
            Button("Save") { performRename() }
            Button("Cancel", role: .cancel) {}
        }
        .onChange(of: photoItem) { item in
            guard let item else { return }
            attachImage(item)
        }
        .photosPicker(isPresented: $showPhotoLibrary, selection: $photoItem, matching: .images)
        .fullScreenCover(isPresented: $showCamera) {
            CameraPicker { image in
                showCamera = false
                if let image { attachCapturedImage(image) }
            }
            .ignoresSafeArea()
        }
        .onChange(of: detailTab) { tab in savedDetailTab = tab }
        .onAppear(perform: onAppear)
        .onDisappear {
            // Leaving in scroll mode would leave tmux in copy-mode (pane looks frozen next time).
            if scrollMode {
                IosScrollMode.shared.command(profile: profile, session: session, enter: false) { _ in }
            }
            channelReadySub?.cancel()
            channelReadySub = nil
            LocalAlertWatcher.shared.foregroundSessionId = nil
            ShellRestore.setOpenSession(profileId: nil, sessionId: nil)
        }
        .task { await refreshLoop() }
        .overlay {
            if voiceRecorder != nil { recordingOverlay }
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
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
                watchButton
                DocsLinkButton(profile: profile, anchor: "sessions")
                AlertsBellButton()
                // D46b: the global status dot is the disconnect indicator (no overlay).
                ReachabilityDotView(profile: profile)
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    private func onAppear() {
        applyDetailExtras()
        terminal.onAutoFontSize = { px in termFontSize = px }
        terminal.setMinCols(TerminalController.minCols(for: session))
        IosServiceLocator.shared.fetchWhisperEnabled(profile: profile) { enabled in
            DispatchQueue.main.async { self.whisperEnabled = enabled.boolValue }
        }
        LocalAlertWatcher.shared.foregroundSessionId = session.id
        ShellRestore.setOpenSession(profileId: profile.id, sessionId: session.id)
        loadStatusBadge()
        watchChannelReady()
        replayPendingNeedsInput()
    }

    /// PWA maybeReplayPendingNeedsInputPopup (Android SessionStateWatcher): a
    /// needs-input prompt that fired while this session wasn't open is posted
    /// once to the alert dock ~200 ms after opening (≤ 1 h old).
    private func replayPendingNeedsInput() {
        let pid: String = profile.id
        let sid: String = session.id
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 200_000_000)
            guard let prompt = LocalAlertWatcher.shared.consumePendingNeedsInput(profileId: pid, sessionId: sid) else { return }
            let text: String = "[" + sid + "] " + L("needs input") + " — " + String(prompt.prefix(80))
            AlertDock.shared.post(text, level: .info)
        }
    }

    /// Live channel/ACP readiness from the shared hub (clears the conn banner).
    private func watchChannelReady() {
        guard channelReadySub == nil else { return }
        let ids: [String] = [session.fullId, session.id]
        channelReadySub = IosChannelReady.shared.watch(sessionIds: ids) { ready in
            let value: Bool = ready.boolValue
            DispatchQueue.main.async { hubChannelReady = value }
        }
    }

    private func loadStatusBadge() {
        IosSessionStatus.shared.load(profile: profile, session: session) { snap in
            let hook: String = snap.board?.hookHealth ?? ""
            let st: String = snap.board?.state ?? ""
            DispatchQueue.main.async {
                boardHook = hook
                boardState = st
            }
        }
    }

    /// Keeps `cur` fresh while the screen is up (cancelled on disappear).
    private func refreshLoop() async {
        while !Task.isCancelled {
            try? await Task.sleep(nanoseconds: 10_000_000_000)
            await refreshSession()
            loadStatusBadge()
        }
    }

    private func refreshSession() async {
        guard let list = try? await ServiceLocatorAsync.listSessions(profile: profile) else { return }
        if let s = list.first(where: { $0.id == session.id || $0.fullId == session.fullId }) {
            live = s
            stateOverrideLabel = nil
        }
    }

    // ── App-only extras (D61a / D67a) ─────────────────────────────────────

    private var isWatched: Bool {
        _ = localPrefs.revision
        return localPrefs.contains(.watchedSessions, profileId: profile.id, id: session.id)
    }

    /// D61a (Android SDS:404): watch toggle in the top bar.
    private var watchButton: some View {
        Button {
            localPrefs.toggle(.watchedSessions, profileId: profile.id, id: session.id)
        } label: {
            Image(systemName: isWatched ? "bell.fill" : "bell.slash")
                .foregroundStyle(isWatched ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted.opacity(0.5))
        }
        .accessibilityLabel(isWatched ? "Watching" : "Not watching")
    }

    private func applyDetailExtras() {
        // Restore the persisted output tab when this session offers it.
        if detailTabs.contains(where: { $0.0 == savedDetailTab }) { detailTab = savedDetailTab }
        // Rate-limit notice: current state now, then live `rate_limited` events.
        if session.state == .rateLimited { rateLimitShown = true }
        terminal.onRateLimited = { retry in
            DispatchQueue.main.async {
                rateRetryAt = retry
                rateLimitShown = true
            }
        }
        // One-time hooks-installed note for claude-code sessions (Android SDS:275) → dock (D41a).
        let key = "dw.session.hook_toast." + session.id
        if (session.backend ?? "").lowercased() == "claude-code" && !UserDefaults.standard.bool(forKey: key) {
            UserDefaults.standard.set(true, forKey: key)
            let path: String = session.taskSummary.map { String($0.prefix(30)) } ?? String(session.id.prefix(8))
            AlertDock.shared.post(String(format: L("Hooks installed in %@/.claude/"), path), level: .info)
        }
    }

    // ── Info bar (PWA session-info-bar) ───────────────────────────────────

    private var infoBar: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    infoBadges
                    stateMenu
                    if !isDone {
                        LastActivityIndicator(since: cur.lastActivityAt.toEpochMilliseconds())
                    }
                    SessionActionButtons(
                        isDone: isDone,
                        busy: isStopping || isRestarting,
                        onStop: { showStopConfirm = true },
                        onRestart: performRestart,
                        onDelete: { showDeleteSheet = true }
                    )
                    infoIcon("🕑", label: "Timeline") { showTimeline = true }
                    // D43a: always offered; the viewer fetches fresh.
                    infoIcon("📄", label: "View last response") { showResponse = true }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
            }
            .background(DatawatchColors.surface)
            Divider().background(DatawatchColors.border)
        }
    }

    @ViewBuilder
    private var infoBadges: some View {
        if let llm = cur.llmRef, !llm.isEmpty {
            metaBadge("⚡ \(llm)", color: DatawatchColors.success)
        } else if let backend = cur.backend, !backend.isEmpty {
            metaBadge(backend.lowercased(), color: DatawatchColors.onSurfaceMuted)
        }
        if let node = cur.computeNodeRef, !node.isEmpty {
            metaBadge("⚙ \(node)", color: DatawatchColors.primary) // PWA var(--accent), D6b
        }
        // D17a: the mode badge shows only for plain tmux sessions (the tab strip
        // already conveys channel / acp / chat).
        if sessionMode == "tmux" && !isChatMode {
            metaBadge("tmux", color: DatawatchColors.primary)
        }
        if let agentId = cur.agentId {
            metaBadge("⬡ \(agentId)", color: DatawatchColors.secondary)
        }
        if cur.chrome {
            metaBadge("Chrome", color: DatawatchColors.primary)
        }
        if let parent = cur.parentId, !parent.isEmpty {
            Button {
                NotificationCenter.default.post(
                    name: .deepLinkSession, object: nil,
                    userInfo: ["id": parent, "profileId": profile.id]
                )
            } label: {
                metaBadge("↑ " + L("parent"), color: DatawatchColors.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Open parent session")
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

    private func infoIcon(_ glyph: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(glyph)
                .font(DatawatchFonts.bodyMedium)
                .frame(minWidth: 32, minHeight: 28)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L(label))
    }

    // ── Session flags ─────────────────────────────────────────────────────

    private var isDone: Bool {
        cur.state == .completed || cur.state == .killed || cur.state == .error
    }

    private var isWaiting: Bool { cur.state == .waiting }

    /// PWA: chat-transcript sessions (OpenWebUI / Ollama) render bubbles, not a terminal.
    private var isChatMode: Bool { session.outputMode == "chat" }

    /// PWA log viewer (output_mode=log — ACP / headless sessions): colour-classed
    /// log lines instead of the terminal.
    private var isLogMode: Bool { session.outputMode == "log" }

    /// Chat and log panes mount no terminal, so input goes straight over the
    /// session socket they hold open.
    private var usesSocketInput: Bool { isChatMode || isLogMode }

    /// PWA getSessionMode: tmux | channel | acp.
    private var sessionMode: String { SessionMode.of(session) }

    private var isChannelMode: Bool { sessionMode == "channel" }

    /// PWA: the input bar only shows while active and input_mode != none.
    private var inputAllowed: Bool {
        !isDone && (cur.inputMode ?? "tmux").lowercased() != "none"
    }

    private var showConnBanner: Bool {
        !isDone && !connBannerDismissed && !cur.channelReady && !hubChannelReady
            && (sessionMode == "channel" || sessionMode == "acp")
    }

    /// PWA output tabs: Tmux · Channel (channel mode only) · Status. Chat-only
    /// sessions have no tab bar.
    private var detailTabs: [(String, String)] {
        var tabs = [("tmux", isChatMode ? "Chat" : "Tmux")]
        if isChannelMode { tabs.append(("channel", "Channel")) }
        tabs.append(("status", "Status"))
        return tabs
    }

    // ── Output tab bar (PWA output-tabs, fontCtrl on the right) ───────────

    private var detailTabBar: some View {
        HStack(spacing: 0) {
            ForEach(detailTabs, id: \.0) { tab in
                tabButton(tab.0, title: tab.1)
            }
            Spacer(minLength: 4)
            if detailTab == "tmux" && !isLogMode { terminalTools }
            if detailTab == "channel" { channelHelpButton }
        }
        .background(DatawatchColors.surface)
        .overlay(Divider().background(DatawatchColors.border), alignment: .bottom)
    }

    private func tabButton(_ id: String, title: String) -> some View {
        Button {
            detailTab = id
        } label: {
            VStack(spacing: 4) {
                HStack(spacing: 3) {
                    Text(L(title))
                        .font(DatawatchFonts.badge)
                        .foregroundStyle(detailTab == id ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
                    if id == "status" { statusTabBadge }
                }
                Rectangle()
                    .fill(detailTab == id ? DatawatchColors.primary : Color.clear)
                    .frame(height: 2)
            }
            .fixedSize()
            .padding(.horizontal, 12)
            .padding(.top, 6)
        }
        .accessibilityAddTraits(detailTab == id ? .isSelected : [])
    }

    /// PWA `updateSessionStatusBadge`: ● hook health (alive green / stale amber)
    /// + 🟢/🟠/⚪ board state.
    @ViewBuilder
    private var statusTabBadge: some View {
        let hookColor: Color? = boardHook == "alive" ? DatawatchColors.success : (boardHook == "stale" ? DatawatchColors.warning : nil)
        let sym: String = ["running": "🟢", "waiting": "🟠", "idle": "⚪"][boardState] ?? ""
        if let hookColor {
            Text("●").font(.system(size: 9)).foregroundStyle(hookColor)
                .accessibilityLabel("hooks " + boardHook)
        }
        if !sym.isEmpty {
            Text(sym).font(.system(size: 9))
        }
    }

    /// PWA `?` (showChannelHelp) beside the tabs while the Channel tab is active.
    private var channelHelpButton: some View {
        Button {
            showChannelHelp = true
        } label: {
            Image(systemName: "questionmark.circle")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .frame(minWidth: 40, minHeight: 40)
                .contentShape(Rectangle())
        }
        .accessibilityLabel("Channel Commands")
        .padding(.trailing, 4)
    }

    /// D20a Aa▾ font menu + D69a search + scroll mode.
    private var terminalTools: some View {
        HStack(spacing: 0) {
            TerminalFontMenu(size: $termFontSize) { terminal.fitToWidth() }
            Button {
                showSearch.toggle()
                if !showSearch { terminal.clearSearch() }
            } label: {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(showSearch ? DatawatchColors.primary : DatawatchColors.onSurface)
                    .frame(minWidth: 40, minHeight: 40)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(showSearch ? "Close search" : "Search terminal")
            if !isDone {
                Button(action: toggleScrollMode) {
                    Text(scrollMode ? "⏹" : "⤒")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundStyle(scrollMode ? DatawatchColors.warning : DatawatchColors.onSurface)
                        .frame(minWidth: 40, minHeight: 40)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(scrollMode ? "Exit scroll mode" : "Scroll mode")
            }
        }
        .padding(.trailing, 4)
    }

    private var statusSubtabStrip: some View {
        HStack(spacing: 18) {
            ForEach([("status", "Status"), ("stats", "Stats")], id: \.0) { tab in
                Button {
                    statusSubtab = tab.0
                } label: {
                    VStack(spacing: 3) {
                        Text(L(tab.1))
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

    // ── Output panes ──────────────────────────────────────────────────────

    private var outputPanes: some View {
        ZStack {
            if isChatMode {
                ChatTranscriptView(profile: profile, session: session)
                    .opacity(detailTab == "tmux" ? 1 : 0)
                    .allowsHitTesting(detailTab == "tmux")
            } else if isLogMode {
                SessionLogView(profile: profile, session: session)
                    .opacity(detailTab == "tmux" ? 1 : 0)
                    .allowsHitTesting(detailTab == "tmux")
            } else {
                // Kept mounted while other tabs show so the session socket stays open.
                TerminalView(session: session, profile: profile, fontSize: $termFontSize, terminalInput: $terminalInput, controller: terminal)
                    .ignoresSafeArea(edges: .bottom)
                    .opacity(detailTab == "tmux" ? 1 : 0)
                    .allowsHitTesting(detailTab == "tmux")
            }
            if detailTab == "channel" {
                ChannelTabView(profile: profile, session: session)
            }
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
    }

    @ViewBuilder
    private var bottomBar: some View {
        if scrollMode && detailTab == "tmux" && !isDone {
            scrollStrip
        } else if inputAllowed {
            composerBar
        }
    }

    // ── Image attach (PWA sessionImageInput → [image:<path>]) ─────────────

    /// Camera capture → same upload path as a library pick.
    private func attachCapturedImage(_ image: UIImage) {
        guard let jpeg = image.jpegData(compressionQuality: 0.85) else {
            AlertDock.shared.post(L("Couldn't read that image."), level: .error)
            return
        }
        imageBanner = "uploading"
        uploadJPEG(jpeg, name: "camera_\(Int(Date().timeIntervalSince1970)).jpg")
    }

    private func attachImage(_ item: PhotosPickerItem) {
        imageBanner = "uploading"
        Task {
            guard let raw = try? await item.loadTransferable(type: Data.self),
                  let jpeg = UIImage(data: raw)?.jpegData(compressionQuality: 0.85) else {
                await MainActor.run {
                    imageBanner = nil
                    photoItem = nil
                    AlertDock.shared.post(L("Couldn't read that image."), level: .error)
                }
                return
            }
            await MainActor.run {
                uploadJPEG(jpeg, name: "photo_\(Int(Date().timeIntervalSince1970)).jpg")
            }
        }
    }

    private func uploadJPEG(_ jpeg: Data, name: String) {
        IosServiceLocator.shared.uploadImageData(
            profile: profile, imageData: jpeg, fileName: name, mimeType: "image/jpeg",
            onSuccess: { path in
                DispatchQueue.main.async {
                    let trimmed = replyText.trimmingCharacters(in: .whitespacesAndNewlines)
                    replyText = (trimmed.isEmpty ? "" : trimmed + "\n") + "[image:\(path)]"
                    imageBanner = "✓ \(name)"
                    photoItem = nil
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    imageBanner = nil
                    photoItem = nil
                    AlertDock.shared.post(msg, level: .error)
                }
            }
        )
    }

    // ── Scroll mode (PWA toggleScrollMode / scrollPage / exitScrollMode) ─

    /// The UI flips only once the server confirms (REST /api/command, WS fallback),
    /// so a dropped command can't leave tmux in copy-mode behind a live composer.
    private func toggleScrollMode() {
        if scrollMode { exitScrollMode(); return }
        guard !scrollBusy else { return }
        scrollBusy = true
        IosScrollMode.shared.command(profile: profile, session: session, enter: true) { ok in
            DispatchQueue.main.async {
                scrollBusy = false
                guard ok.boolValue else { return }
                scrollMode = true
                terminal.setScrollMode(true)
            }
        }
    }

    private func scrollPage(up: Bool) {
        terminal.scrollPendingRefresh()
        _ = IosSessionOps.shared.tmuxCommand(session: session, command: up ? "tmux-page-up" : "tmux-page-down")
    }

    private func exitScrollMode() {
        guard !scrollBusy else { return }
        scrollBusy = true
        IosScrollMode.shared.command(profile: profile, session: session, enter: false) { ok in
            DispatchQueue.main.async {
                scrollBusy = false
                guard ok.boolValue else { return }  // strip stays up; tap ESC again
                terminal.setScrollMode(false)
                scrollMode = false
            }
        }
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
            Text(L(title))
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(tint)
                .frame(maxWidth: .infinity, minHeight: 36)
                .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 6))
        }
    }

    // ── State pill → state override (PWA showStateOverride) ──────────────

    private static let overrideStates: [(wire: String, label: String)] = [
        ("running", "Running"), ("waiting_input", "Waiting input"), ("complete", "Complete"),
        ("killed", "Killed"), ("failed", "Failed"),
    ]

    private var currentStateLabel: String {
        if let o = stateOverrideLabel { return o }
        switch cur.state {
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
                    Button(L(st.label)) { overrideState(st.wire, label: st.label) }
                }
            }
        } label: {
            HStack(spacing: 4) {
                if overridingState { ProgressView().controlSize(.mini) }
                Text(L(currentStateLabel).uppercased())
                    .font(DatawatchFonts.badge)
                Image(systemName: "chevron.down").font(.system(size: 8, weight: .bold))
            }
            .foregroundStyle(DatawatchColors.primary)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(DatawatchColors.primary.opacity(0.12), in: Capsule())
            .modifier(RunningPulse(active: cur.state == .running && stateOverrideLabel == nil))
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
                    AlertDock.shared.post(String(format: L("State set to %@"), wire), level: .success)
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    overridingState = false
                    AlertDock.shared.post(msg, level: .error)
                }
            }
        )
    }

    // ── Composer bar (active sessions, input_mode != none) ────────────────

    /// PWA input placeholder rule (app.js input_ph_*).
    private var composerPlaceholder: String {
        if isTranscribing { return "Transcribing…" }
        if awaitingConnection { return "Waiting for connection…" }
        if isWaiting { return "Type your response…" }
        if session.isChatMode || sessionMode == "channel" { return "Send message…" }
        return "Send command or input…"
    }

    /// PWA `connReady == false`: while the channel / ACP connection banner is up
    /// (and the session isn't waiting on a prompt) the input is disabled with the
    /// "Waiting for connection…" placeholder and no send button.
    private var awaitingConnection: Bool { showConnBanner && !isWaiting }

    /// PWA `▶ ch`: on the Channel tab (channel mode, not waiting) the composer
    /// sends via POST /api/channel/send instead of tmux.
    private var sendsViaChannel: Bool {
        isChannelMode && detailTab == "channel" && !isWaiting
    }

    private func flashComposerNote(_ text: String, warning: Bool, seconds: Double) {
        composerNote = (text, warning)
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds) {
            if composerNote?.0 == text { composerNote = nil }
        }
    }

    private var composerBar: some View {
        VStack(spacing: 0) {
            if isWaiting {
                Rectangle().fill(DatawatchColors.waiting).frame(height: 2)
            } else {
                Divider().background(DatawatchColors.border)
            }
            if isChatMode && detailTab == "tmux" {
                ChatMemoryCmdBar { prefix in replyText = prefix }
            }
            // D68b: Yes / No / Stop chips while the session waits on a prompt.
            if isWaiting && detailTab == "tmux" {
                QuickReplyChips { reply in sendQuickReply(reply) }
            }
            // D21b: Commands… dropdown + custom input, hold-to-repeat arrows.
            SavedCommandsRow(profile: profile, session: session) { _ in
                LocalAlertWatcher.shared.onReplied(sessionId: session.id)
            }
            composerNotes
            composerInputRow
        }
    }

    @ViewBuilder
    private var composerNotes: some View {
        if isTranscribing {
            // PWA _composerBanner('Transcribing voice message…').
            HStack(spacing: 6) {
                ProgressView().controlSize(.mini).tint(DatawatchColors.onSurfaceMuted)
                Text("Transcribing voice message…")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.top, 4)
        } else if let note = composerNote {
            Text(note.0)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(note.1 ? DatawatchColors.warning : DatawatchColors.success)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.top, 4)
        }
        if let banner = imageBanner {
            Text(banner == "uploading" ? "Uploading image…" : banner)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(banner == "uploading" ? DatawatchColors.warning : DatawatchColors.success)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.top, 4)
        }
    }

    /// PWA 📷 "Attach image or take photo": gallery or camera (Android
    /// image-source sheet: "Choose from gallery" / "Take a photo").
    private var imageAttachMenu: some View {
        Menu {
            Button { showPhotoLibrary = true } label: {
                Label("Choose from gallery", systemImage: "photo.on.rectangle")
            }
            if CameraPicker.isAvailable {
                Button { showCamera = true } label: {
                    Label("Take a photo", systemImage: "camera")
                }
            }
        } label: {
            Image(systemName: "camera").foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .disabled(imageBanner == "uploading")
        .accessibilityLabel("Attach image or take photo")
    }

    private var composerInputRow: some View {
        HStack(spacing: 8) {
            Button { showSchedule = true } label: {
                Image(systemName: "clock.badge").foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .accessibilityLabel("Schedule input")
            imageAttachMenu
            TextField(L(composerPlaceholder), text: $replyText)
                .disabled(isTranscribing || awaitingConnection)
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .submitLabel(.send)
                .onSubmit(sendReply)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(isWaiting ? DatawatchColors.waiting.opacity(0.08) : DatawatchColors.surface)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            if whisperEnabled { micButton }
            if !awaitingConnection { sendButton }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(DatawatchColors.background)
    }

    @ViewBuilder
    private var micButton: some View {
        if isTranscribing {
            ProgressView().controlSize(.small).tint(DatawatchColors.onSurfaceMuted)
        } else {
            Button(action: startRecording) {
                Image(systemName: "mic").foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .accessibilityLabel("Record voice message")
        }
    }

    private var sendButton: some View {
        Button(action: sendReply) {
            if sendsViaChannel {
                Text("▶ ch")
                    .font(DatawatchFonts.labelSmall.weight(.bold))
                    .foregroundStyle(DatawatchColors.secondary)
            } else {
                Image(systemName: "paperplane.fill")
                    .foregroundStyle(isWaiting ? DatawatchColors.waiting : DatawatchColors.primary)
            }
        }
        .accessibilityLabel(sendsViaChannel ? "Send via MCP channel" : "Send reply")
    }

    private func sendReply() {
        if imageBanner == "uploading" {
            AlertDock.shared.post(L("Wait for image upload to finish"), level: .warning)
            return
        }
        let text = replyText.trimmingCharacters(in: .whitespacesAndNewlines)
        imageBanner = nil
        LocalAlertWatcher.shared.onReplied(sessionId: session.id)
        if sendsViaChannel {
            guard !text.isEmpty else { return }
            replyText = ""
            IosSessionComposer.shared.sendChannel(profile: profile, sessionId: session.fullId, text: text) { err in
                if let err { AlertDock.notify(err, level: .error) }
            }
            return
        }
        replyText = ""
        if usesSocketInput {
            guard !text.isEmpty else { return }
            if !IosSessionOps.shared.sendText(session: session, text: text + "\r") {
                AlertDock.shared.post(L("Chat isn't connected yet — try again in a moment."), level: .error)
            }
            return
        }
        // PWA: an empty input sends Enter. TerminalView forwards this as a
        // `send_input` frame on the session's /ws hub (WsOutbound).
        terminalInput = text + "\r"
    }

    /// D68b chip reply ("yes\r" / "no\r" / "stop\r") — same path as the composer.
    private func sendQuickReply(_ reply: String) {
        LocalAlertWatcher.shared.onReplied(sessionId: session.id)
        if usesSocketInput {
            if !IosSessionOps.shared.sendText(session: session, text: reply) {
                AlertDock.shared.post(L("Chat isn't connected yet — try again in a moment."), level: .error)
            }
            return
        }
        terminalInput = reply
    }

    // ── Actions ───────────────────────────────────────────────────────────

    private var sessionTitle: String {
        if let r = renamedTo { return r }
        if let name = cur.name, !name.isEmpty { return name }
        if let task = cur.taskSummary, !task.isEmpty { return task }
        return session.id
    }

    private func performStop() {
        isStopping = true
        IosServiceLocator.shared.killSession(
            profile: profile,
            sessionId: session.id,
            onSuccess: {
                DispatchQueue.main.async {
                    isStopping = false
                    AlertDock.shared.post(L("Session stopped"), level: .success)
                    Task { await refreshSession() }
                }
            },
            onError: { message in
                DispatchQueue.main.async {
                    isStopping = false
                    AlertDock.shared.post(String(format: L("Stop failed: %@"), message), level: .error)
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
                DispatchQueue.main.async {
                    isRestarting = false
                    AlertDock.shared.post(L("Session restarted"), level: .success)
                    Task { await refreshSession() }
                }
            },
            onError: { message in
                DispatchQueue.main.async {
                    isRestarting = false
                    AlertDock.shared.post(String(format: L("Restart failed: %@"), message), level: .error)
                }
            }
        )
    }

    /// PWA rename toast → alert dock (D41a).
    private func performRename() {
        let name = renameText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        IosServiceLocator.shared.renameSession(
            profile: profile,
            sessionId: session.id,
            name: name,
            onSuccess: {
                DispatchQueue.main.async {
                    renamedTo = name
                    AlertDock.shared.post(L("Session renamed"), level: .success)
                }
            },
            onError: { message in
                DispatchQueue.main.async {
                    AlertDock.shared.post(String(format: L("Rename failed: %@"), message), level: .error)
                }
            }
        )
    }

    // ── Voice recording ───────────────────────────────────────────────────

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
                // PWA voice-waveform, driven by the live mic level.
                VoiceLevelBars(recorder: voiceRecorder)
                HStack(spacing: 16) {
                    Button("Cancel") {
                        voiceRecorder?.cancel()
                        voiceRecorder = nil
                    }
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Button("Send") { stopAndTranscribe() }
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
                    AlertDock.shared.post(L("Couldn't start recording."), level: .error)
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
                    self.isTranscribing = false
                    if transcript.isEmpty {
                        self.flashComposerNote("Backend returned empty transcript — check whisper config", warning: true, seconds: 4)
                    } else {
                        self.replyText = self.replyText.isEmpty ? transcript : self.replyText + " " + transcript
                        self.flashComposerNote("✓ Transcribed (\(transcript.count) chars)", warning: false, seconds: 2.5)
                    }
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    self.isTranscribing = false
                    self.flashComposerNote("Voice transcribe error: \(msg)", warning: true, seconds: 4)
                }
            }
        )
    }
}

// ── Int helper ───────────────────────────────────────────────────────────────

private extension Int {
    var nonZero: Int? { self == 0 ? nil : self }
}

/// PWA session-last-activity (D19a): dot green < 30 s, amber < 5 min, red beyond,
/// plus grey age text; ticks every second.
struct LastActivityIndicator: View {
    let since: Int64
    var body: some View {
        TimelineView(.periodic(from: Date(), by: 1)) { ctx in
            let nowMs: Int64 = Int64(ctx.date.timeIntervalSince1970 * 1000)
            let secs: Int64 = max(0, (nowMs - since) / 1000)
            let color: Color = secs < 30 ? DatawatchColors.success : (secs < 300 ? DatawatchColors.warning : DatawatchColors.error)
            HStack(spacing: 3) {
                Circle().fill(color).frame(width: 6, height: 6)
                Text(SessionCardView.ago(since))
                    .font(.system(size: 11))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .accessibilityLabel("Last activity")
    }
}
