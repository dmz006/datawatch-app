import SwiftUI
import DatawatchShared

/// Sessions tab — live list of sessions for the active server (PWA renderSessionsView).
///
/// Parity decisions applied (2026-10-04): D12a collapsible `State (N)` chips with every
/// real state · D13a inline card actions (no swipe) · D14a inline current status ·
/// D15a select mode with fixed bottom bar · D16a PWA identity row · D42a manual order
/// then most-recent (no sort menu) · D44a "Stop" wording.
struct SessionsView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var viewModel = SessionsViewModel()
    @ObservedObject private var nav = SessionsNav.shared
    /// D61a watch / D62a mute id sets (per profile).
    @ObservedObject private var localPrefs = LocalSessionPrefs.shared

    @State private var filterText: String = ""
    @State private var showFilter: Bool = false
    /// PWA cs_session_state_chip: "all" | a real state key.
    @AppStorage("dw.sessions.state_chip") private var stateChip: String = "all"
    @State private var stateFilterOpen = false
    @State private var llmFilterOpen = false
    /// PWA cs_session_order: manual order of full ids.
    @AppStorage("dw.sessions.order") private var orderJSON: String = "[]"

    @State private var sessionToStop: DwSession? = nil
    @State private var sessionToRestart: DwSession? = nil
    @State private var sessionToDelete: DwSession? = nil
    @State private var actionInProgress: String? = nil
    @State private var actionError: String? = nil

    @State private var cardStatus: [String: CardStatus] = [:]
    @State private var responseSession: DwSession? = nil

    @State private var selectMode = false
    @State private var selected: Set<String> = []
    @State private var confirmBulkDelete = false

    @State private var showNewSession = false
    @State private var quickCmdSession: DwSession? = nil
    /// PWA `state.showHistory`: off = active sessions + those finished in the last few minutes.
    @State private var showHistory = false
    /// PWA `cs_session_tree_view` (BL348): parent/child lineage grouping.
    @AppStorage("dw.sessions.tree_view") private var treeView: Bool = false
    /// PWA `recent_session_minutes` default.
    private static let recentWindowMs: Int64 = 5 * 60 * 1000

    /// PWA realStateChips (key, label, colour).
    private static let stateChips: [(String, String, Color)] = [
        ("all", "All", DatawatchColors.onSurfaceMuted),
        ("running", "Running", DatawatchColors.success),
        ("waiting_input", "Waiting", DatawatchColors.warning),
        ("rate_limited", "Rate-limited", DatawatchColors.error),
        ("complete", "Complete", DatawatchColors.onSurfaceMuted),
        ("failed", "Failed", DatawatchColors.error),
        ("killed", "Killed", DatawatchColors.onSurfaceMuted),
    ]

    /// PWA setSessionStateChip `historicalStates`.
    private static let historicalChips: Set<String> = ["complete", "failed", "killed", "cancelled", "archived"]

    /// PWA setSessionStateChip: picking a historical state auto-enables History,
    /// otherwise those sessions stay hidden behind the recent-window pool.
    private func setStateChip(_ key: String) {
        stateChip = key
        selected.removeAll()
        if Self.historicalChips.contains(key) && !showHistory { showHistory = true }
    }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            DatawatchColors.background.ignoresSafeArea()
            content
            if selectMode {
                selectBar
            } else if viewModel.activeProfile != nil {
                newSessionFab
                    .padding(.trailing, 20)
                    .padding(.bottom, 20)
            }
        }
        .sheet(isPresented: Binding(
            get: { quickCmdSession != nil },
            set: { if !$0 { quickCmdSession = nil } }
        )) {
            if let s = quickCmdSession, let profile = profileFor(s) {
                QuickCommandsSheet(profile: profile, session: s)
            }
        }
        .sheet(isPresented: $showNewSession) {
            if let profile = viewModel.activeProfile {
                NewSessionView(profile: profile) { _ in viewModel.refresh() }
            }
        }
        .sheet(isPresented: Binding(
            get: { responseSession != nil },
            set: { if !$0 { responseSession = nil } }
        )) {
            if let s = responseSession {
                LastResponseSheet(session: s)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbarContent }
        .onChange(of: store.profiles) { _ in
            viewModel.update(profiles: activeList)
        }
        .onChange(of: store.activeProfileId) { _ in
            viewModel.update(profiles: activeList)
        }
        .onAppear {
            viewModel.update(profiles: activeList)
            viewModel.startPolling()
            applyPendingFilter()
            // A persisted historical chip would otherwise show nothing on launch.
            if Self.historicalChips.contains(stateChip) { showHistory = true }
        }
        .onChange(of: nav.pendingFilter) { _ in applyPendingFilter() }
        .onDisappear { viewModel.stopPolling() }
        .alert("Stop session?", isPresented: Binding(
            get: { sessionToStop != nil },
            set: { if !$0 { sessionToStop = nil } }
        )) {
            Button("Stop", role: .destructive) { performStop() }
            Button("Cancel", role: .cancel) { sessionToStop = nil }
        } message: {
            Text("This stops the session on the server and cannot be undone.")
        }
        .alert("Restart session?", isPresented: Binding(
            get: { sessionToRestart != nil },
            set: { if !$0 { sessionToRestart = nil } }
        )) {
            Button("Restart") { performRestart() }
            Button("Cancel", role: .cancel) { sessionToRestart = nil }
        }
        .alert("Delete session?", isPresented: Binding(
            get: { sessionToDelete != nil },
            set: { if !$0 { sessionToDelete = nil } }
        )) {
            Button("Delete", role: .destructive) { performDelete() }
            Button("Cancel", role: .cancel) { sessionToDelete = nil }
        } message: {
            if let s = sessionToDelete {
                Text("Permanently delete \"\(s.name ?? s.taskSummary ?? s.id)\"?")
            }
        }
        .alert("Delete \(selected.count) sessions?", isPresented: $confirmBulkDelete) {
            Button("Delete", role: .destructive) { performBulkDelete() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This permanently deletes the selected sessions.")
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            HeaderView(
                title: "datawatch",
                subtitle: sessionsSubtitle,
                serverName: store.isAllServers ? L("All servers") : viewModel.activeProfile?.displayName
            )
        }
        ToolbarItem(placement: .navigationBarTrailing) {
            HStack(spacing: 4) {
                if viewModel.isLoading && !viewModel.sessions.isEmpty {
                    ProgressView()
                        .tint(DatawatchColors.onSurfaceMuted)
                        .controlSize(.mini)
                        .accessibilityLabel("Refreshing")
                }
                DocsLinkButton(profile: viewModel.activeProfile, anchor: "sessions-list")
                Button {
                    withAnimation { showFilter.toggle() }
                } label: {
                    Image(systemName: showFilter ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                        .foregroundStyle(DatawatchColors.primary)
                }
                .accessibilityLabel(showFilter ? "Hide filter" : "Filter sessions")
                AlertsBellButton()
                ReachabilityDotView(profile: viewModel.activeProfile)
            }
        }
    }

    // ── Row actions ───────────────────────────────────────────────────────

    private func fetchCurrentStatus(for session: DwSession) {
        guard let profile = profileFor(session) else { return }
        let key = session.fullId
        var st = cardStatus[key] ?? CardStatus()
        st.loading = true
        cardStatus[key] = st
        IosServiceLocator.shared.fetchSessionCurrentStatus(
            sessionId: session.id,
            profile: profile,
            onSuccess: { short, long in
                DispatchQueue.main.async {
                    let text = short.isEmpty ? "(" + L("no change since last refresh") + ")" : short
                    cardStatus[key] = CardStatus(text: text, long: long, generatedAt: Date())
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    cardStatus[key] = CardStatus(text: "(\(msg))", generatedAt: Date())
                }
            }
        )
    }

    private func runOp(_ session: DwSession, _ op: (ServerProfile, String, @escaping () -> Void, @escaping (String) -> Void) -> Void) {
        guard let profile = profileFor(session) else { return }
        actionInProgress = session.id
        op(profile, session.id, {
            DispatchQueue.main.async { actionInProgress = nil; viewModel.refresh() }
        }, { msg in
            DispatchQueue.main.async { actionInProgress = nil; actionError = msg }
        })
    }

    private func performStop() {
        guard let s = sessionToStop else { return }
        sessionToStop = nil
        runOp(s) { p, id, ok, err in IosServiceLocator.shared.killSession(profile: p, sessionId: id, onSuccess: ok, onError: err) }
    }

    private func performRestart() {
        guard let s = sessionToRestart else { return }
        sessionToRestart = nil
        runOp(s) { p, id, ok, err in IosServiceLocator.shared.restartSession(profile: p, sessionId: id, onSuccess: ok, onError: err) }
    }

    private func performDelete() {
        guard let s = sessionToDelete else { return }
        sessionToDelete = nil
        runOp(s) { p, id, ok, err in IosServiceLocator.shared.deleteSession(profile: p, sessionId: id, onSuccess: ok, onError: err) }
    }

    /// PWA deleteSelectedSessions.
    private func performBulkDelete() {
        let targets = viewModel.sessions.filter { selected.contains($0.fullId) }
        let group = DispatchGroup()
        var failures = 0
        for s in targets {
            guard let profile = profileFor(s) else { continue }
            group.enter()
            IosServiceLocator.shared.deleteSession(
                profile: profile, sessionId: s.id,
                onSuccess: { DispatchQueue.main.async { group.leave() } },
                onError: { _ in DispatchQueue.main.async { failures += 1; group.leave() } }
            )
        }
        group.notify(queue: .main) {
            selected.removeAll()
            selectMode = false
            if failures > 0 { actionError = String(format: L("%lld of %lld deletes failed."), Int64(failures), Int64(targets.count)) }
            viewModel.refresh()
        }
    }

    // ── Body states ───────────────────────────────────────────────────────

    @ViewBuilder
    private var content: some View {
        VStack(spacing: 0) {
            ConnectionStatusBanner(state: connectionState)
            if viewModel.isLoading && viewModel.sessions.isEmpty {
                // D60a: Android SessionSkeletonList (5 shimmer rows, 900 ms).
                SkeletonListView(rows: 5)
            } else if let errorMsg = viewModel.error, viewModel.sessions.isEmpty {
                ErrorCard(message: errorMsg) { viewModel.refresh() }
            } else if viewModel.activeProfile == nil {
                emptyNoProfile
            } else {
                sessionList
            }
        }
    }

    /// D2a: the app-wide active server, or every enabled server in "All".
    private var activeList: [ServerProfile] {
        if store.isAllServers { return store.enabledProfiles }
        return store.activeProfile.map { [$0] } ?? []
    }

    /// The server a session belongs to (matters in "All servers").
    private func profileFor(_ s: DwSession) -> ServerProfile? {
        store.profiles.first { $0.id == s.serverProfileId } ?? viewModel.activeProfile
    }

    private var sessionList: some View {
        VStack(spacing: 0) {
            ServerPickerBar(showsAll: true)
            if showFilter { filterBar }
            if let actionError {
                Text(actionError)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .onTapGesture { self.actionError = nil }
            }
            List {
                listRows
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .refreshable { viewModel.refresh() }
            .safeAreaInset(edge: .bottom) { Color.clear.frame(height: selectMode ? 56 : 0) }
        }
    }

    @ViewBuilder
    private var listRows: some View {
        let visible = filteredSessions
        if visible.isEmpty && !viewModel.sessions.isEmpty && filterText.isEmpty && stateChip == "all" && !showHistory {
            Button {
                showHistory = true
            } label: {
                Text("No active sessions — show \(historyCount) finished")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.primary)
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
        } else if visible.isEmpty && (!filterText.isEmpty || stateChip != "all") {
            Text(filterText.isEmpty ? "No sessions in this state" : "No sessions match \"\(filterText)\"")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
        } else if viewModel.sessions.isEmpty {
            emptySessionsRow
        } else {
            if treeView {
                ForEach(SessionTree.flatten(visible), id: \.session.fullId) { row in
                    treeRow(row)
                }
            } else {
                ForEach(visible, id: \.id) { session in
                    sessionRow(session)
                }
                .onMove { from, to in move(visible, from: from, to: to) }
            }
        }
    }

    // ── Toolbar (PWA sessions-toolbar) ────────────────────────────────────

    private var filterBar: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(DatawatchColors.onSurfaceMuted)
                TextField("Filter sessions…", text: $filterText)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .onChange(of: filterText) { _ in selected.removeAll() }
                if !filterText.isEmpty {
                    Button { filterText = "" } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .accessibilityLabel("Clear filter")
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 8))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    if backendTypes.count > 1 || backendTypes.contains("council-virtual") {
                        toggleBadge(llmButtonLabel, active: llmFilterOpen || llmActive != nil) { llmFilterOpen.toggle() }
                    }
                    toggleBadge(stateButtonLabel, active: stateFilterOpen || stateChip != "all") { stateFilterOpen.toggle() }
                    toggleBadge(L("Tree"), active: treeView, chevron: false) { treeView.toggle() }
                        .accessibilityHint("Groups sessions by parent/child lineage")
                    if !viewModel.pendingSchedules.isEmpty { schedulesMenu }
                    toggleBadge("History (\(historyCount))", active: showHistory, chevron: false) {
                        showHistory.toggle()
                        if !showHistory { selectMode = false; selected.removeAll() }
                    }
                    if showHistory && historyCount > 0 {
                        Button {
                            selectMode.toggle()
                            if !selectMode { selected.removeAll() }
                        } label: {
                            Text("☑").font(.system(size: 14)).opacity(selectMode ? 1 : 0.5)
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Select sessions")
                    }
                }
            }
            if llmFilterOpen && (backendTypes.count > 1 || backendTypes.contains("council-virtual")) {
                // D64: council-virtual renders as the 🎭 Council chip (Android council_session_filter).
                chipRow(backendTypes.map { bt in
                    (bt, bt == "council-virtual" ? "🎭 Council" : bt, DatawatchColors.secondary, viewModel.sessions.filter { $0.backend == bt }.count, filterText.lowercased() == bt.lowercased())
                }) { key in filterText = filterText.lowercased() == key.lowercased() ? "" : key }
            }
            if stateFilterOpen {
                chipRow(visibleStateChips.map { c in
                    (c.0, c.1, c.2, stateCount(c.0), stateChip == c.0)
                }) { key in setStateChip(key) }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(DatawatchColors.background)
    }

    private func toggleBadge(_ label: String, active: Bool, chevron: Bool = true, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(chevron ? label + (active ? " ▾" : " ▸") : label)
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(active ? DatawatchColors.background : DatawatchColors.onSurfaceMuted)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(active ? DatawatchColors.primary : DatawatchColors.chipBackground, in: Capsule())
        }
        .buttonStyle(.borderless)
    }

    private func chipRow(_ chips: [(String, String, Color, Int, Bool)], onTap: @escaping (String) -> Void) -> some View {
        FlowLayout(spacing: 4) {
            ForEach(chips, id: \.0) { c in
                Button { onTap(c.0) } label: {
                    HStack(spacing: 4) {
                        Text("●").foregroundStyle(c.2)
                        Text(L(c.1)).foregroundStyle(c.4 ? DatawatchColors.background : DatawatchColors.onSurface)
                        Text("\(c.3)").foregroundStyle(c.4 ? DatawatchColors.background.opacity(0.8) : DatawatchColors.onSurfaceMuted)
                    }
                    .font(.system(size: 11))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(c.4 ? DatawatchColors.primary : DatawatchColors.chipBackground, in: Capsule())
                    .overlay(alignment: .leading) { Capsule().fill(c.2).frame(width: 3).padding(.vertical, 4) }
                }
                .buttonStyle(.borderless)
            }
        }
    }

    private var backendTypes: [String] {
        Array(Set(viewModel.sessions.compactMap { $0.backend }.filter { !$0.isEmpty })).sorted()
    }

    private var llmActive: String? {
        backendTypes.first { $0.lowercased() == filterText.lowercased() }
    }

    private var llmButtonLabel: String {
        if let a = llmActive { return "LLM: \(a)" }
        return "LLM (\(backendTypes.count))"
    }

    private func stateCount(_ key: String) -> Int {
        key == "all" ? viewModel.sessions.count : viewModel.sessions.filter { SessionStateStyle.key($0.state) == key }.count
    }

    /// PWA: hide 0-count chips except All and the active one.
    private var visibleStateChips: [(String, String, Color)] {
        Self.stateChips.filter { $0.0 == "all" || stateCount($0.0) > 0 || stateChip == $0.0 }
    }

    private var stateButtonLabel: String {
        if stateChip != "all" { return "State: \(stateChip)" }
        return "State (\(visibleStateChips.count - 1))"
    }

    // ── History + ordering (PWA pool + sortSessionsByOrder) ──────────────

    private var historyCount: Int { viewModel.sessions.filter { SessionStateStyle.isDone($0.state) }.count }

    private var visiblePool: [DwSession] {
        let cutoff = Int64(Date().timeIntervalSince1970 * 1000) - Self.recentWindowMs
        return viewModel.sessions.filter { s in
            !SessionStateStyle.isDone(s.state) || s.lastActivityAt.toEpochMilliseconds() >= cutoff
        }
    }

    private var manualOrder: [String] {
        (try? JSONDecoder().decode([String].self, from: Data(orderJSON.utf8))) ?? []
    }

    private func saveOrder(_ ids: [String]) {
        if let data = try? JSONEncoder().encode(ids), let s = String(data: data, encoding: .utf8) { orderJSON = s }
    }

    private func sortByOrder(_ sessions: [DwSession]) -> [DwSession] {
        let order = manualOrder
        var inOrder: [DwSession] = []
        var seen = Set<String>()
        for id in order {
            if let s = sessions.first(where: { $0.fullId == id }) { inOrder.append(s); seen.insert(id) }
        }
        let rest = sessions.filter { !seen.contains($0.fullId) }
            .sorted { $0.lastActivityAt.toEpochMilliseconds() > $1.lastActivityAt.toEpochMilliseconds() }
        return inOrder + rest
    }

    /// Drag-to-reorder / Move up-down (PWA sessionDrop / moveSession): persists the full order.
    private func move(_ visible: [DwSession], from: IndexSet, to: Int) {
        var ids = visible.map { $0.fullId }
        ids.move(fromOffsets: from, toOffset: to)
        let others = sortByOrder(viewModel.sessions).map { $0.fullId }.filter { !ids.contains($0) }
        saveOrder(ids + others)
    }

    private func moveOne(_ session: DwSession, by delta: Int) {
        var ids = sortByOrder(viewModel.sessions).map { $0.fullId }
        guard let i = ids.firstIndex(of: session.fullId) else { return }
        let j = i + delta
        guard j >= 0, j < ids.count else { return }
        ids.swapAt(i, j)
        saveOrder(ids)
    }

    /// Automata → View sessions / task session link (SessionsNav).
    private func applyPendingFilter() {
        guard let f = nav.consume() else { return }
        filterText = f
        showHistory = true
        withAnimation { showFilter = true }
    }

    private var filteredSessions: [DwSession] {
        var result = showHistory ? viewModel.sessions : visiblePool
        switch stateChip {
        case "all": break
        default: result = result.filter { SessionStateStyle.key($0.state) == stateChip }
        }
        if !filterText.isEmpty {
            let q = filterText.lowercased()
            result = result.filter { s in
                s.id.lowercased().contains(q) ||
                (s.name?.lowercased().contains(q) ?? false) ||
                (s.taskSummary?.lowercased().contains(q) ?? false) ||
                (s.backend?.lowercased().contains(q) ?? false) ||
                (s.llmRef?.lowercased().contains(q) ?? false) ||
                (s.computeNodeRef?.lowercased().contains(q) ?? false)
            }
        }
        return sortByOrder(result)
    }

    // ── Select bar (PWA select-bar-fixed) ─────────────────────────────────

    private var visibleDone: [DwSession] { filteredSessions.filter { SessionStateStyle.isDone($0.state) } }

    private var selectBar: some View {
        let done = visibleDone
        let allSelected = !done.isEmpty && done.allSatisfy { selected.contains($0.fullId) }
        return HStack(spacing: 10) {
            Button {
                if allSelected { selected.removeAll() } else { selected = Set(done.map { $0.fullId }) }
            } label: {
                Text("☑ " + L(allSelected ? "None" : "All") + " (\(done.count))")
            }
            Button(role: .destructive) {
                confirmBulkDelete = true
            } label: {
                Text("🗑 " + L("Delete") + " (\(selected.count))")
            }
            .disabled(selected.isEmpty)
            Spacer()
            Button("Cancel") { selectMode = false; selected.removeAll() }
        }
        .font(DatawatchFonts.bodyMedium)
        .buttonStyle(.borderless)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(DatawatchColors.surface)
        .overlay(alignment: .top) { Rectangle().fill(DatawatchColors.border).frame(height: 1) }
    }

    // ── New Session FAB (PWA `+` / Android FAB) ─────────────────────────────

    private var newSessionFab: some View {
        Button {
            showNewSession = true
        } label: {
            Image(systemName: "plus")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 56, height: 56)
                .background(DatawatchColors.primary, in: Circle())
                .shadow(color: .black.opacity(0.35), radius: 6, y: 3)
        }
        .accessibilityLabel("New session")
    }

    // ── Empty states ──────────────────────────────────────────────────────

    private var emptySessionsRow: some View {
        HStack {
            Spacer()
            VStack(spacing: 12) {
                Text("💬").font(.system(size: 40)).accessibilityHidden(true)
                Text("No active sessions")
                    .font(DatawatchFonts.titleMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                Text("Tap the + button to start a session,\nor send commands via Signal.")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, 60)
            Spacer()
        }
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private var emptyNoProfile: some View {
        VStack(spacing: 16) {
            Image(systemName: "server.rack")
                .font(.system(.largeTitle))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No server configured")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Text("Add a server in Settings to get started.")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // ── Session row ───────────────────────────────────────────────────────

    /// PWA `🕒 N` badge + dropdown with per-item cancel.
    private var schedulesMenu: some View {
        Menu {
            Section("Pending schedules") {
                ForEach(viewModel.pendingSchedules, id: \.id) { sc in
                    Button(role: .destructive) {
                        viewModel.cancelSchedule(sc.id) { msg in actionError = msg }
                    } label: {
                        Label(sc.label + " · " + sc.whenText, systemImage: "xmark")
                    }
                }
            }
        } label: {
            Text("🕒 \(viewModel.pendingSchedules.count)")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(DatawatchColors.onSurface)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(DatawatchColors.chipBackground, in: Capsule())
        }
        .accessibilityLabel(String(format: L("%lld pending schedules"), Int64(viewModel.pendingSchedules.count)))
    }

    /// BL348 tree row: 18 pt indent per level + `⚠ orphaned` note.
    @ViewBuilder
    private func treeRow(_ row: SessionTree.Row) -> some View {
        let indent: CGFloat = CGFloat(row.depth) * 18
        if row.orphaned {
            Text("⚠ " + L("orphaned — parent no longer exists"))
                .font(.system(size: 10))
                .foregroundStyle(DatawatchColors.warning)
                .listRowInsets(SessionRowBackground.insets(indent: indent))
                .listRowBackground(DatawatchColors.background)
                .listRowSeparator(.hidden)
        }
        sessionRow(row.session, indent: indent)
    }

    @ViewBuilder
    private func sessionRow(_ session: DwSession, indent: CGFloat = 0) -> some View {
        let card = SessionCardView(
            session: session,
            showHost: store.profiles.count > 1,
            serverName: store.isAllServers ? profileFor(session)?.displayName : nil,
            status: cardStatus[session.fullId],
            selecting: selectMode,
            selected: selected.contains(session.fullId),
            onStop: { sessionToStop = session },
            onQuick: { quickCmdSession = session },
            onRestart: { sessionToRestart = session },
            onDelete: { sessionToDelete = session },
            onFetchStatus: { fetchCurrentStatus(for: session) },
            onToggleLong: { cardStatus[session.fullId]?.longExpanded.toggle() },
            onToggleSelect: { toggleSelect(session) },
            onResponse: { responseSession = session },
            onExpand: { DashExpandNav.shared.open(session.fullId) },
            watched: isLocal(.watchedSessions, session),
            onWatchToggle: { toggleLocal(.watchedSessions, session) },
            muted: isLocal(.mutedSessions, session)
        )
        Group {
            if selectMode {
                card.onTapGesture { if SessionStateStyle.isDone(session.state) { toggleSelect(session) } }
            } else {
                NavigationLink {
                    if let profile = profileFor(session) {
                        SessionDetailView(session: session, profile: profile)
                    }
                } label: { card }
            }
        }
        .listRowInsets(SessionRowBackground.insets(indent: indent))
        .listRowBackground(SessionRowBackground(state: session.state, indent: indent))
        .listRowSeparator(.hidden)
        .accessibilityElement(children: .contain)
        .contextMenu {
            Button { moveOne(session, by: -1) } label: { Label("Move up", systemImage: "arrow.up") }
            Button { moveOne(session, by: 1) } label: { Label("Move down", systemImage: "arrow.down") }
        }
        // D62a swipe-to-mute (Android ≥64 dp horizontal swipe); full swipe toggles.
        .swipeActions(edge: .leading, allowsFullSwipe: true) {
            if !selectMode { muteSwipeButton(session) }
        }
    }

    private func muteSwipeButton(_ session: DwSession) -> some View {
        let isMuted: Bool = isLocal(.mutedSessions, session)
        return Button {
            toggleLocal(.mutedSessions, session)
        } label: {
            Label(isMuted ? "Unmute" : "Mute", systemImage: isMuted ? "speaker.wave.2" : "speaker.slash")
        }
        .tint(isMuted ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
    }

    private func isLocal(_ kind: LocalSessionPrefs.Kind, _ session: DwSession) -> Bool {
        _ = localPrefs.revision
        guard let pid = profileFor(session)?.id else { return false }
        return localPrefs.contains(kind, profileId: pid, id: session.id)
    }

    private func toggleLocal(_ kind: LocalSessionPrefs.Kind, _ session: DwSession) {
        guard let pid = profileFor(session)?.id else { return }
        localPrefs.toggle(kind, profileId: pid, id: session.id)
    }

    private func toggleSelect(_ s: DwSession) {
        if selected.contains(s.fullId) { selected.remove(s.fullId) } else { selected.insert(s.fullId) }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private var sessionsSubtitle: String? {
        guard !viewModel.sessions.isEmpty else { return nil }
        let running = viewModel.sessions.filter { $0.state == .running || $0.state == .rateLimited }.count
        let waiting = viewModel.sessions.filter { $0.state == .waiting }.count
        if running > 0 && waiting > 0 {
            return String(format: L("%lld running · %lld waiting"), Int64(running), Int64(waiting))
        } else if running > 0 {
            return String(format: L("%lld running"), Int64(running))
        } else if waiting > 0 {
            return String(format: L("%lld waiting"), Int64(waiting))
        }
        return nil
    }

    private var connectionState: ConnectionState {
        if viewModel.error != nil && !viewModel.sessions.isEmpty { return .reconnecting(attempt: 1) }
        if viewModel.isLoading && viewModel.sessions.isEmpty { return .connecting }
        return .connected
    }
}

/// Last-response viewer (PWA showResponseViewer; D43a).
private struct LastResponseSheet: View {
    let session: DwSession
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(session.lastResponse ?? "")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
            }
            .background(DatawatchColors.background)
            .navigationTitle("Last response")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } }
                ToolbarItem(placement: .primaryAction) {
                    ShareLink(item: session.lastResponse ?? "") { Image(systemName: "square.and.arrow.up") }
                }
            }
        }
    }
}

/// PWA `renderSessionsAsTree` (Android `flattenTree`): a session whose `parent_id`
/// matches another visible session's full id nests under it (pre-order, siblings
/// keep list order); others are roots, flagged orphaned when the parent is missing.
enum SessionTree {
    struct Row {
        let session: DwSession
        let depth: Int
        let orphaned: Bool
    }

    static func flatten(_ sessions: [DwSession]) -> [Row] {
        var byFullId: Set<String> = []
        for s in sessions { byFullId.insert(s.fullId) }
        var children: [String: [DwSession]] = [:]
        var roots: [DwSession] = []
        for s in sessions {
            if let p = s.parentId, p != s.fullId, byFullId.contains(p) {
                children[p, default: []].append(s)
            } else {
                roots.append(s)
            }
        }
        var out: [Row] = []
        var seen: Set<String> = []
        func visit(_ s: DwSession, _ depth: Int) {
            guard !seen.contains(s.fullId) else { return }
            seen.insert(s.fullId)
            let orphaned: Bool = s.parentId != nil && !(s.parentId ?? "").isEmpty && !byFullId.contains(s.parentId ?? "")
            out.append(Row(session: s, depth: depth, orphaned: orphaned))
            for c in children[s.fullId] ?? [] { visit(c, depth + 1) }
        }
        for r in roots { visit(r, 0) }
        // Pure cycles have no root — append them flat so nothing disappears.
        for s in sessions where !seen.contains(s.fullId) {
            out.append(Row(session: s, depth: 0, orphaned: false))
        }
        return out
    }
}
