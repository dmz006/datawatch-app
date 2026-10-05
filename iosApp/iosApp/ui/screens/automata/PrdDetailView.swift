import SwiftUI
import DatawatchShared

// ── ViewModel ─────────────────────────────────────────────────────────────

@MainActor
final class PrdDetailViewModel: ObservableObject {
    @Published private(set) var prd: PrdDto
    @Published private(set) var error: String? = nil
    @Published private(set) var busy = false
    @Published var actionError: String? = nil

    let profile: ServerProfile
    private var pollTask: Task<Void, Never>? = nil
    private var prdSubscription: IosSubscription? = nil
    private var inFlight = false
    /// REST fallback; live changes arrive as prd_update frames.
    private static let interval: Duration = .seconds(30)

    init(profile: ServerProfile, initial: PrdDto) {
        self.profile = profile
        self.prd = initial
    }

    func start() {
        stop()
        let id = prd.id
        prdSubscription = IosServiceLocator.shared.subscribePrdUpdates(profile: profile) { [weak self] updated in
            guard updated.id == id else { return }
            Task { @MainActor [weak self] in self?.prd = updated }
        }
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                await self.refresh()
                try? await Task.sleep(for: Self.interval)
            }
        }
    }

    func stop() {
        pollTask?.cancel()
        pollTask = nil
        prdSubscription?.cancel()
        prdSubscription = nil
    }

    func refresh() async {
        guard !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        do {
            prd = try await ServiceLocatorAsync.getPrd(profile: profile, prdId: prd.id)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
    }

    func perform(_ action: String, body: [String: String]? = nil) async {
        busy = true
        defer { busy = false }
        do {
            try await ServiceLocatorAsync.prdAction(profile: profile, prdId: prd.id, action: action, body: body)
            await refresh()
        } catch {
            actionError = error.localizedDescription
        }
    }

    func cancel() async {
        busy = true
        defer { busy = false }
        do {
            try await ServiceLocatorAsync.cancelPrd(profile: profile, prdId: prd.id, hard: false)
            await refresh()
        } catch {
            actionError = error.localizedDescription
        }
    }
}

// ── Detail view ───────────────────────────────────────────────────────────

struct PrdDetailView: View {
    @StateObject private var vm: PrdDetailViewModel
    @State private var expandedStories: Set<String> = []
    @State private var showReject = false
    @State private var itemConfirm: ItemConfirm? = nil
    @State private var rejectStoryId: String? = nil
    @State private var rejectStoryReason = ""
    @State private var itemBusy: String? = nil

    /// A confirm-before-run story/task action (PWA uses confirm() for these).
    struct ItemConfirm: Identifiable {
        let id = UUID()
        let title: String
        let message: String
        let destructiveLabel: String
        let run: () -> Void
    }
    @State private var rejectReason = ""
    @State private var showRevision = false
    @State private var revisionNote = ""
    @State private var showCancel = false
    @State private var showEdit = false
    @State private var showDelete = false
    @State private var templateSaved = false
    @State private var showSetLlm = false
    @State private var showSettings = false
    @State private var itemEdit: PrdItemEdit? = nil
    /// PWA ⚙ / 🤖 / ✎ spec + LLM overrides and template Instantiate.
    @State private var overrideEdit: PrdOverrideEdit? = nil
    /// Live planning stream (PWA _startDecomposeStream).
    @StateObject private var live = PrdDecomposeLiveModel()
    @State private var openFile: PrdOpenFile? = nil
    @State private var capacity: CapacityResponseDto? = nil
    /// D74a approve-with-note dialog.
    @State private var review: PrdReviewRequest? = nil
    /// D24a: PWA detail sub-tab (`_automataDetailTab`).
    @State private var tab: PrdDetailTab = .overview
    @Environment(\.dismiss) private var dismissDetail

    init(profile: ServerProfile, initial: PrdDto) {
        _vm = StateObject(wrappedValue: PrdDetailViewModel(profile: profile, initial: initial))
    }

    private var prd: PrdDto { vm.prd }

    var body: some View { withAlerts }

    private var mainScroll: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                PrdLifecycleStrip(prd: prd, compact: false) { action in
                    switch action {
                    case "reject": showReject = true
                    case "request_revision": showRevision = true
                    case "cancel": showCancel = true
                    case "approve": askApprove()
                    case "instantiate": overrideEdit = .instantiate
                    default: Task { await vm.perform(action) }
                    }
                }
                if let liveState = live.state {
                    PrdDecomposeLiveCard(state: liveState)
                }
                actions
                if ["completed", "archived"].contains(prd.status.lowercased()) {
                    // PWA prd_terminal_state_hint (not shown for cancelled, which can reset to draft).
                    Text("This automaton is in a terminal state. Edit/Settings/Run actions are no longer available; only Clone-to-Template + Delete remain.")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                PrdDetailTabStrip(selection: $tab)
                tabBody
                if let err = vm.error {
                    Text(err)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                }
            }
            .padding(16)
        }
        .background(DatawatchColors.background)
        .navigationTitle(prd.displayTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Menu {
                    Button { showEdit = true } label: { Label("Edit", systemImage: "pencil") }
                    Button { showSetLlm = true } label: { Label("Set LLM", systemImage: "cpu") }
                    Button { showSettings = true } label: { Label("Settings", systemImage: "slider.horizontal.3") }
                    Button {
                        IosTemplates.shared.clonePrd(
                            profile: vm.profile, prdId: prd.id, description: "",
                            onSuccess: { DispatchQueue.main.async { templateSaved = true } },
                            onError: { msg in DispatchQueue.main.async { vm.actionError = msg } }
                        )
                    } label: { Label("Save as template", systemImage: "doc.on.doc") }
                    if !["running", "planning", "archived"].contains(prd.status.lowercased()) {
                        Button {
                            Task { await vm.perform("reset_to_draft", body: ["actor": "operator"]) }
                        } label: { Label("Reset to Draft", systemImage: "arrow.uturn.backward") }
                    }
                    // D76a (Android #202): re-resolve stuck depends_on refs; no-op when resolved.
                    Button {
                        Task { await vm.perform("repair_depends_on") }
                    } label: { Label("Repair Dependencies", systemImage: "wrench.and.screwdriver") }
                    Divider()
                    Button(role: .destructive) { showDelete = true } label: { Label("Delete", systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel("Automaton actions")
            }
        }
    }

    /// Override sheet + live planning stream (split out so the type checker copes).
    private var withLive: some View {
        mainScroll
        .sheet(item: $overrideEdit) { edit in
            PrdOverrideSheet(profile: vm.profile, prdId: prd.id, edit: edit) { Task { await vm.refresh() } }
        }
        .task(id: "live-" + prd.status) {
            guard ["planning", "decomposing"].contains(prd.status.lowercased()) else { return }
            live.start(profile: vm.profile, prdId: prd.id) { Task { await vm.refresh() } }
        }
        .onDisappear { live.stop() }
    }

    /// Sheets + lifecycle (split out so the type checker copes).
    private var withSheets: some View {
        withLive
        .alert("Saved as template", isPresented: $templateSaved) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("Find it in Automata → Templates.")
        }
        .sheet(item: $itemEdit) { edit in
            PrdItemEditSheet(profile: vm.profile, prdId: prd.id, edit: edit) { Task { await vm.refresh() } }
        }
        .sheet(item: $openFile) { f in
            PrdFileViewerSheet(profile: vm.profile, path: f.path, projectDir: prd.projectDir)
        }
        .sheet(isPresented: $showSettings) {
            PrdSettingsView(profile: vm.profile, prd: prd) { Task { await vm.refresh() } }
        }
        .sheet(isPresented: $showSetLlm) {
            SetPrdLlmView(profile: vm.profile, prd: prd) { Task { await vm.refresh() } }
        }
        .sheet(isPresented: $showEdit) {
            EditPrdView(profile: vm.profile, prdId: prd.id, title: prd.displayTitle, spec: prd.spec ?? "",
                        currentPermissionMode: prd.permissionMode ?? "") {
                Task { await vm.refresh() }
            }
        }
        .sheet(isPresented: $showDelete) {
            MemoryStrategyDeleteSheet(
                title: "Delete this automaton?",
                question: "What should happen to this Automaton's memories?",
                perform: { strategy, roles, scope, done in
                    IosAutomata.shared.deletePrd(
                        profile: vm.profile, prdId: prd.id, strategy: strategy,
                        roleFilter: roles, archiveScope: scope,
                        onSuccess: { done(nil) }, onError: { done($0) }
                    )
                },
                onDeleted: { dismissDetail() }
            )
        }
        .onAppear {
            expandedStories = Set(prd.stories.filter { $0.status == "in_progress" }.map { $0.id })
            vm.start()
        }
        .onDisappear { vm.stop() }
        .task(id: prd.status) {
            guard ["running", "decomposing", "planning", "approved"].contains(prd.status.lowercased()) else { capacity = nil; return }
            // PWA `_prdCapacityInterval`: re-render the capacity card every 5 s.
            while !Task.isCancelled {
                IosPrdCapacity.shared.load(profile: vm.profile, prdId: prd.id) { c in
                    DispatchQueue.main.async { capacity = c }
                }
                try? await Task.sleep(nanoseconds: 5_000_000_000)
            }
        }
        .refreshable { await vm.refresh() }
    }

    private var withAlerts: some View {
        withSheets
        .prdReviewDialogs($review) { _, action, body in
            Task { await vm.perform(action, body: body) }
        }
        .alert("Reject automaton", isPresented: $showReject) {
            TextField("Reason", text: $rejectReason)
            Button("Reject", role: .destructive) {
                let reason = rejectReason
                rejectReason = ""
                Task { await vm.perform("reject", body: ["reason": reason]) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The automaton moves to rejected. The reason is recorded with it.")
        }
        .alert("Request revision", isPresented: $showRevision) {
            TextField("What should change?", text: $revisionNote)
            Button("Send") {
                let note = revisionNote
                revisionNote = ""
                Task { await vm.perform("request_revision", body: ["note": note]) }
            }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Cancel automaton?", isPresented: $showCancel) {
            Button("Cancel automaton", role: .destructive) { Task { await vm.cancel() } }
            Button("Keep running", role: .cancel) {}
        } message: {
            Text(PrdReviewDialogs.cancelMessage(status: prd.status))
        }
        .alert(
            itemConfirm?.title ?? "",
            isPresented: Binding(get: { itemConfirm != nil }, set: { if !$0 { itemConfirm = nil } })
        ) {
            Button(itemConfirm?.destructiveLabel ?? "OK", role: .destructive) {
                itemConfirm?.run()
                itemConfirm = nil
            }
            Button("Keep", role: .cancel) { itemConfirm = nil }
        } message: {
            Text(itemConfirm?.message ?? "")
        }
        .alert(
            "Reject story",
            isPresented: Binding(get: { rejectStoryId != nil }, set: { if !$0 { rejectStoryId = nil } })
        ) {
            TextField("Reason", text: $rejectStoryReason)
            Button("Reject", role: .destructive) {
                if let sid = rejectStoryId {
                    storyOp(sid, "reject", reason: rejectStoryReason)
                }
                rejectStoryReason = ""
                rejectStoryId = nil
            }
            Button("Cancel", role: .cancel) { rejectStoryId = nil }
        }
        .alert(
            "Action failed",
            isPresented: Binding(get: { vm.actionError != nil }, set: { if !$0 { vm.actionError = nil } })
        ) {
            Button("OK") { vm.actionError = nil }
        } message: {
            Text(vm.actionError ?? "")
        }
    }

    // MARK: Tabs (D24a — Overview · Stories · Decisions · Rules · Scan)

    @ViewBuilder
    private var tabBody: some View {
        switch tab {
        case .overview: overviewTab
        case .stories: storiesSection
        case .decisions: PrdDecisionsSection(decisions: prd.decisions ?? [])
        case .rules: PrdRulesCard(profile: vm.profile, prdId: prd.id)
        case .scan: scanTab
        }
    }

    /// PWA _renderDetailOverview: scope warnings, progress + graph cards, capacity,
    /// active session, spec, memory, then the sessions link.
    private var overviewTab: some View {
        VStack(alignment: .leading, spacing: 16) {
            if !prd.scopeWarnings.isEmpty { scopeWarningsBanner }
            PrdActiveSessionCard(profile: vm.profile, prd: prd) { showCancel = true }
            statusGraphs
            // Android Graph card — Automaton DAG (#184 / PWA #182).
            PrdDagCard(profile: vm.profile, prdId: prd.id)
            // Android progress (per-story CPU/RSS) + compute-node GPU card.
            PrdResourceCards(profile: vm.profile, prd: prd)
            capacityCard
            PrdOverviewMeta(prd: prd)
            PrdMemorySection(profile: vm.profile, prd: prd)
            Button { SessionsNav.shared.jumpTo(prd.name) } label: {
                Text("→ View sessions").font(DatawatchFonts.bodyMedium)
            }
            .buttonStyle(.borderless)
        }
    }

    private var scanTab: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Static analysis (SAST · secrets · dependencies · LLM grader) over the Automaton spec and any associated files. Runs the configured scanners and reports a verdict + findings.")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            PrdScanCard(profile: vm.profile, prdId: prd.id) { _ in Task { await vm.refresh() } }
        }
    }

    // MARK: Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .firstTextBaseline) {
                Text(prd.displayTitle)
                    .font(DatawatchFonts.titleLarge)
                    .foregroundStyle(DatawatchColors.onSurface)
                Spacer(minLength: 8)
                PrdStatusChip(status: prd.status)
            }
            // PWA prd-detail-row2: id + last activity.
            PrdIdMetaRow(prd: prd)
            metaRow
            // PWA prd-detail-spec-row (280 chars, show full / collapse).
            if let spec = prd.spec, !spec.isEmpty {
                PrdSpecSnippet(spec: spec)
            }
            if let dir = prd.projectDir, !dir.isEmpty {
                Text(dir)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(1)
                    .truncationMode(.head)
            }
            let total = prd.allTasks.count
            if total > 0 {
                ProgressView(value: Double(prd.doneTaskCount), total: Double(total))
                    .tint(PrdStatusStyle.color(prd.status))
                Text("\(prd.doneTaskCount) of \(total) tasks complete")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .padding(14)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: DatawatchRadius.card))
    }

    private var metaRow: some View {
        var parts: [String] = []
        if let t = prd.type, !t.isEmpty { parts.append(t) }
        if let b = prd.backend, !b.isEmpty {
            parts.append((prd.model?.isEmpty == false) ? "\(b)/\(prd.model!)" : b)
        }
        if let e = prd.effort, !e.isEmpty { parts.append(L("effort") + " \(e)") }
        if prd.guidedMode { parts.append("guided") }
        return Text(parts.joined(separator: "  ·  "))
            .font(DatawatchFonts.labelSmall)
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
    }

    @ViewBuilder
    private var actions: some View {
        let status = prd.status.lowercased()
        if vm.busy {
            HStack { ProgressView(); Text("Working…").font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.onSurfaceMuted) }
        } else {
            switch status {
            case "needs_review", "revisions_asked":
                HStack(spacing: 10) {
                    actionButton("Approve", systemImage: "checkmark.circle", tint: DatawatchColors.success) {
                        askApprove()
                    }
                    actionButton("Revise", systemImage: "pencil.and.outline", tint: DatawatchColors.warning) { showRevision = true }
                    actionButton("Reject", systemImage: "xmark.circle", tint: DatawatchColors.error) { showReject = true }
                }
            case "approved":
                actionButton("Run", systemImage: "play.fill", tint: DatawatchColors.primary) {
                    Task { await vm.perform("run") }
                }
            case "draft":
                actionButton("Decompose", systemImage: "wand.and.stars", tint: DatawatchColors.primary) {
                    Task { await vm.perform("decompose") }
                }
            case "running":
                // D52b: Pause / Resume wired to POST /prds/{id}/pause|resume.
                HStack(spacing: 10) {
                    actionButton("Pause", systemImage: "pause.fill", tint: DatawatchColors.warning) {
                        Task { await vm.perform("pause") }
                    }
                    actionButton("Cancel automaton", systemImage: "stop.circle", tint: DatawatchColors.error) { showCancel = true }
                }
            case "paused":
                HStack(spacing: 10) {
                    actionButton("Resume", systemImage: "play.fill", tint: DatawatchColors.primary) {
                        Task { await vm.perform("resume") }
                    }
                    actionButton("Cancel automaton", systemImage: "stop.circle", tint: DatawatchColors.error) { showCancel = true }
                }
            case "decomposing", "planning", "blocked":
                actionButton("Cancel automaton", systemImage: "stop.circle", tint: DatawatchColors.error) { showCancel = true }
            case "completed", "rejected", "cancelled":
                // PWA lifecycle 📦 Archive (terminal, not yet archived).
                actionButton("Archive", systemImage: "archivebox", tint: DatawatchColors.onSurface) {
                    Task { await vm.perform("archive") }
                }
            default:
                EmptyView()
            }
        }
    }

    /// D74a: Approve opens an optional-note dialog (Android approve dialog).
    private func askApprove() {
        review = PrdReviewRequest(prdId: prd.id, title: prd.displayTitle, action: "approve")
    }

    private func actionButton(_ title: String, systemImage: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(L(title), systemImage: systemImage)
                .font(DatawatchFonts.bodyMedium)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
        }
        .foregroundStyle(tint)
        .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 8))
    }

    private var storiesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Stories & tasks (\(prd.stories.count))")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            if prd.stories.isEmpty {
                if let liveState = live.state, liveState.stories.count > 0 {
                    // Stories streamed so far (PWA decompose stream "+ title" lines).
                    PrdDecomposeLiveCard(state: liveState)
                } else {
                    Text(["decomposing", "planning"].contains(prd.status.lowercased()) ? "Decomposing — stories appear when planning finishes." : "No stories yet.")
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            ForEach(prd.stories, id: \.id) { story in
                storyGroup(story)
            }
            if editable {
                Button { itemEdit = .addStory } label: {
                    Text("+ Add story").font(DatawatchFonts.bodyMedium)
                }
                .buttonStyle(.borderless)
            }
        }
    }

    /// Planned files claimed by more than one open story (PWA ⚠ conflict).
    private var fileConflicts: Set<String> {
        var seen: [String: Int] = [:]
        for st in prd.stories where !["complete", "completed", "rejected"].contains(st.status.lowercased()) {
            for f in Set(st.files) { seen[f, default: 0] += 1 }
        }
        return Set(seen.filter { $0.value > 1 }.keys)
    }

    @ViewBuilder
    private func storyEditRow(_ story: PrdStoryDto) -> some View {
        if editable {
            FlowLayout(spacing: 8) {
                smallAction("✎ Edit", DatawatchColors.primary) { itemEdit = .editStory(story) }
                smallAction("📁 Files", DatawatchColors.primary) { itemEdit = .storyFiles(story) }
                smallAction("⚙ Profile", DatawatchColors.primary) { overrideEdit = .storyProfile(story) }
                smallAction("🤖 LLM", DatawatchColors.primary) { overrideEdit = .storyLlm(story) }
                smallAction("+ Add task", DatawatchColors.primary) { itemEdit = .addTask(story) }
                // PWA prdRemoveStory 🗑 (confirm, then remove_story).
                smallAction("🗑", DatawatchColors.error) {
                    itemConfirm = ItemConfirm(
                        title: "Remove story?",
                        message: "Remove story \"\(story.title.isEmpty ? story.id : story.title)\" and all its tasks? This cannot be undone.",
                        destructiveLabel: "Remove"
                    ) { storyOp(story.id, "remove") }
                }
            }
        }
    }

    /// PWA story header pills: `prof:` (read-only states) + `LLM:` override.
    @ViewBuilder
    private func storyPills(_ story: PrdStoryDto) -> some View {
        let prof: String? = PrdLlmBadge.shared.storyProfileLabel(story: story, editable: editable)
        let llm: String? = PrdLlmBadge.shared.storyLabel(story: story)
        if prof != nil || llm != nil {
            FlowLayout(spacing: 4) {
                if let prof { PrdMiniPill(text: prof) }
                if let llm { PrdMiniPill(text: llm) }
            }
        }
    }

    private func storyGroup(_ story: PrdStoryDto) -> some View {
        let total = story.tasks.count
        let done = story.tasks.filter { PrdStatusStyle.isDone($0.status) }.count
        let (glyph, color): (String, Color) = {
            if total > 0 && done == total { return ("✓", DatawatchColors.success) }
            if story.status == "in_progress" { return ("▶", DatawatchColors.waiting) }
            if story.status == "failed" { return ("✗", DatawatchColors.error) }
            return ("·", DatawatchColors.onSurfaceMuted)
        }()
        return DisclosureGroup(isExpanded: binding(for: story.id)) {
            VStack(alignment: .leading, spacing: 6) {
                // PWA 5(a): story verdicts on a dedicated row, first in the body.
                PrdStoryVerdictsRow(verdicts: story.verdicts)
                storyPills(story)
                storyActions(story)
                storyEditRow(story)
                if let d = story.description_, !d.isEmpty {
                    Text(d)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .padding(.bottom, 2)
                }
                PrdFileChips(label: "PLANNED FILES", files: story.files, conflicts: fileConflicts) { openFile = PrdOpenFile(path: $0) }
                PrdFileChips(label: "FILES TOUCHED", files: story.filesTouched) { openFile = PrdOpenFile(path: $0) }
                if !editable && !story.tasks.isEmpty {
                    PrdStoryReadOnlyExtras(story: story, profileId: vm.profile.id)
                }
                ForEach(story.tasks, id: \.id) { task in
                    PrdTaskRow(
                        task: task, actions: taskActions(task), busy: itemBusy == task.id,
                        onAction: { action in runTaskAction(action, story: story, task: task) },
                        onOpenFile: { openFile = PrdOpenFile(path: $0) }
                    )
                }
                if story.tasks.isEmpty {
                    Text("No tasks").font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .padding(.top, 6)
        } label: {
            HStack(spacing: 8) {
                Text(glyph).font(DatawatchFonts.bodyMedium).foregroundStyle(color)
                Text(story.title.isEmpty ? story.id : story.title)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(2)
                Spacer(minLength: 6)
                if !story.status.isEmpty { PrdStoryStatusPill(status: story.status) }
                Text("\(done)/\(total)")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .tint(DatawatchColors.primary)
        .padding(12)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: DatawatchRadius.card))
    }

    // ── Status graphs + capacity (parity B17; PWA _renderStatusGraphs) ─────

    @ViewBuilder
    private var statusGraphs: some View {
        let status = prd.status.lowercased()
        if ["decomposing", "planning", "running"].contains(status) {
            let stories = prd.stories
            let storiesDone = stories.filter { s in !s.tasks.isEmpty && s.tasks.allSatisfy { PrdStatusStyle.isDone($0.status) } }.count
            let tasks = prd.allTasks
            let tasksDone = prd.doneTaskCount
            VStack(alignment: .leading, spacing: 8) {
                graphRow("Decomposed", value: stories.isEmpty ? "…" : "\(stories.count) stories",
                         fraction: stories.isEmpty ? 0 : 1)
                graphRow("Stories", value: "\(storiesDone)/\(stories.count)",
                         fraction: stories.isEmpty ? 0 : Double(storiesDone) / Double(stories.count))
                graphRow("Tasks", value: "\(tasksDone)/\(tasks.count)",
                         fraction: tasks.isEmpty ? 0 : Double(tasksDone) / Double(tasks.count))
            }
            .padding(14)
            .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: DatawatchRadius.card))
        }
    }

    private func graphRow(_ label: String, value: String, fraction: Double) -> some View {
        HStack(spacing: 10) {
            Text(L(label))
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .frame(width: 84, alignment: .leading)
            ProgressView(value: max(0, min(1, fraction)))
                .tint(DatawatchColors.primary)
            Text(value)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .frame(minWidth: 64, alignment: .trailing)
        }
    }

    /// #191 scope_warnings (PWA prd_scope_warnings_*).
    private var scopeWarningsBanner: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("Scope warnings")
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(DatawatchColors.error)
            Text("The plan references paths outside this Automaton's allowed directories; tasks that do will not start until fixed.")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurface)
            ForEach(prd.scopeWarnings, id: \.self) { w in
                Text(verbatim: "• " + w)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(DatawatchColors.error.opacity(0.14), in: RoundedRectangle(cornerRadius: 6))
    }

    @ViewBuilder
    private var capacityCard: some View {
        if let c = capacity, !c.pools.isEmpty || !c.waiting.isEmpty {
            VStack(alignment: .leading, spacing: 6) {
                Text("CAPACITY")
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                ForEach(c.pools, id: \.name) { pool in
                    HStack {
                        Text(pool.name).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurface)
                        Spacer()
                        Text("\(pool.held)/\(pool.limit)" + (pool.external > 0 ? " (+\(pool.external) external)" : ""))
                            .font(DatawatchFonts.terminalSmall)
                            .foregroundStyle(pool.limit > 0 && pool.held >= pool.limit ? DatawatchColors.warning : DatawatchColors.onSurface)
                    }
                }
                if !c.waiting.isEmpty {
                    Text("\(c.waiting.count) waiting for capacity")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.warning)
                }
            }
            .padding(14)
            .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: DatawatchRadius.card))
        }
    }

    // ── Story / task operations (parity B18; PWA visibility rules) ────────

    private var prdStatus: String { prd.status.lowercased() }
    /// PWA `editable`: structure can change only before/after a run.
    private var editable: Bool { ["needs_review", "revisions_asked", "cancelled"].contains(prdStatus) }

    @ViewBuilder
    private func storyActions(_ story: PrdStoryDto) -> some View {
        let runnable = ["approved", "active", "running"].contains(prdStatus)
        let showApproveReject = runnable && story.status == "awaiting_approval"
        let terminal = ["completed", "cancelled", "failed"].contains(story.status)
        let canCancel = prdStatus == "running" && !terminal
        if showApproveReject || canCancel {
            HStack(spacing: 8) {
                if showApproveReject {
                    smallAction("✓ Approve", DatawatchColors.success) { storyOp(story.id, "approve") }
                    smallAction("✗ Reject", DatawatchColors.error) { rejectStoryId = story.id }
                }
                if canCancel {
                    smallAction("⏹ Cancel story", DatawatchColors.error) {
                        itemConfirm = ItemConfirm(
                            title: "Cancel story?",
                            message: "Cancel story \"\(story.title.isEmpty ? story.id : story.title)\" and all remaining tasks?",
                            destructiveLabel: "Cancel story"
                        ) { storyOp(story.id, "cancel") }
                    }
                }
                if itemBusy == story.id { ProgressView().controlSize(.small) }
            }
        }
    }

    private func smallAction(_ title: String, _ tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(L(title))
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(tint)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(tint.opacity(0.14), in: Capsule())
        }
        .buttonStyle(.borderless)
        .disabled(itemBusy != nil)
    }

    private func taskActions(_ task: PrdTaskDto) -> [PrdTaskRow.Action] {
        let st = task.status
        var out: [PrdTaskRow.Action] = []
        if (st == "failed" || st == "blocked") && ["running", "blocked", "cancelled"].contains(prdStatus) {
            out.append(.retry)
        }
        if ["pending", "in_progress", "running", "verifying", "running_tests", "waiting_capacity"].contains(st)
            && prdStatus == "running" {
            out.append(.cancel)
        }
        if (st == "completed" || st == "cancelled") && ["running", "cancelled"].contains(prdStatus) && editable {
            out.append(.requeue)
        }
        if editable { out.append(contentsOf: [.edit, .files, .remove]) }
        return out
    }

    private func runTaskAction(_ action: PrdTaskRow.Action, story: PrdStoryDto, task: PrdTaskDto) {
        let name = task.task.isEmpty ? task.id : task.task
        switch action {
        case .cancel:
            itemConfirm = ItemConfirm(title: "Cancel task?", message: "Cancel \"\(name)\"?", destructiveLabel: "Cancel task") {
                taskOp(story.id, task.id, "cancel")
            }
        case .remove:
            itemConfirm = ItemConfirm(title: "Remove task?", message: "Remove \"\(name)\" from this story? This doesn't re-run decompose.", destructiveLabel: "Remove") {
                taskOp(story.id, task.id, "remove")
            }
        case .retry: taskOp(story.id, task.id, "retry")
        case .requeue: taskOp(story.id, task.id, "requeue")
        case .edit: overrideEdit = .taskSpecLlm(task)
        case .files: itemEdit = .taskFiles(task)
        }
    }

    private func storyOp(_ storyId: String, _ action: String, reason: String = "") {
        itemBusy = storyId
        IosPrdItemOps.shared.storyAction(
            profile: vm.profile, prdId: prd.id, storyId: storyId, action: action, reason: reason,
            onSuccess: { DispatchQueue.main.async { itemBusy = nil; Task { await vm.refresh() } } },
            onError: { msg in DispatchQueue.main.async { itemBusy = nil; vm.actionError = msg } }
        )
    }

    private func taskOp(_ storyId: String, _ taskId: String, _ action: String) {
        itemBusy = taskId
        IosPrdItemOps.shared.taskAction(
            profile: vm.profile, prdId: prd.id, storyId: storyId, taskId: taskId, action: action, reason: "",
            onSuccess: { DispatchQueue.main.async { itemBusy = nil; Task { await vm.refresh() } } },
            onError: { msg in DispatchQueue.main.async { itemBusy = nil; vm.actionError = msg } }
        )
    }

    private func binding(for storyId: String) -> Binding<Bool> {
        Binding(
            get: { expandedStories.contains(storyId) },
            set: { open in
                if open { expandedStories.insert(storyId) } else { expandedStories.remove(storyId) }
            }
        )
    }
}

// ── Task row ──────────────────────────────────────────────────────────────

struct PrdTaskRow: View {
    enum Action { case retry, cancel, requeue, edit, files, remove }

    let task: PrdTaskDto
    var actions: [Action] = []
    var busy: Bool = false
    var onAction: (Action) -> Void = { _ in }
    var onOpenFile: (String) -> Void = { _ in }

    var body: some View {
        let (glyph, color) = PrdStatusStyle.taskGlyph(task.status)
        VStack(alignment: .leading, spacing: 2) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(glyph)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(color)
                    .frame(width: 16)
                Text(task.task.isEmpty ? task.id : task.task)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(3)
                Spacer(minLength: 4)
                Text(PrdStatusStyle.label(task.status))
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(color)
            }
            badges
            if let err = task.error, !err.isEmpty {
                // PWA prd-task-error: "⚠ Error: <msg>".
                Text(verbatim: "⚠ " + L("Error") + ": " + err)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
                    .lineLimit(3)
                    .padding(.leading, 24)
            }
            if let v = task.verification {
                PrdTaskVerificationRow(verification: v)
                    .padding(.leading, 24)
            }
            if let wait = task.waitReason, !wait.isEmpty {
                Text(wait)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.warning)
                    .lineLimit(2)
                    .padding(.leading, 24)
            }
            if let sid = task.sessionId, !sid.isEmpty {
                Button { SessionsNav.shared.jumpTo(sid) } label: {
                    Text("→ session \(sid.prefix(12))")
                        .font(DatawatchFonts.terminalSmall)
                        .foregroundStyle(DatawatchColors.primary)
                }
                .buttonStyle(.plain)
                .padding(.leading, 24)
                .accessibilityLabel("Open worker session \(sid)")
            }
            if !task.files.isEmpty || !task.filesTouched.isEmpty {
                VStack(alignment: .leading, spacing: 3) {
                    PrdFileChips(label: "PLANNED", files: task.files, onOpen: onOpenFile)
                    PrdFileChips(label: "OUTPUT", files: task.filesTouched, onOpen: onOpenFile)
                }
                .padding(.leading, 24)
            }
            if !actions.isEmpty || busy {
                HStack(spacing: 6) {
                    ForEach(actions, id: \.self) { a in
                        Button { onAction(a) } label: {
                            Text(label(a))
                                .font(DatawatchFonts.badge)
                                .foregroundStyle(a == .remove || a == .cancel ? DatawatchColors.error : DatawatchColors.primary)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(DatawatchColors.surface2, in: Capsule())
                        }
                        .buttonStyle(.borderless)
                        .disabled(busy)
                    }
                    if busy { ProgressView().controlSize(.mini) }
                }
                .padding(.leading, 24)
                .padding(.top, 2)
            }
        }
        .padding(.vertical, 3)
    }

    /// PWA inline task badges: LLM override, ↳ spawn, → child <id>.
    @ViewBuilder
    private var badges: some View {
        let llm: String? = PrdLlmBadge.shared.taskLabel(task: task)
        let child: String? = (task.childPrdId?.isEmpty == false) ? task.childPrdId : nil
        if llm != nil || task.spawnPrd || child != nil {
            FlowLayout(spacing: 4) {
                if let llm { PrdMiniPill(text: llm) }
                if task.spawnPrd {
                    PrdMiniPill(text: "↳ spawn", color: DatawatchColors.secondary)
                        .accessibilityLabel("Spawns a child automaton")
                }
                if let child {
                    Text(verbatim: "→ " + L("child") + " " + String(child.prefix(8)))
                        .font(DatawatchFonts.terminalSmall)
                        .foregroundStyle(DatawatchColors.primary)
                }
            }
            .padding(.leading, 24)
        }
    }

    private func label(_ a: Action) -> String {
        switch a {
        case .retry: return "↻ Retry"
        case .cancel: return "⏹ Cancel"
        case .requeue: return "↻ Re-run"
        case .edit: return "✎ Edit"
        case .files: return "📁 Files"
        case .remove: return "🗑 Remove"
        }
    }
}
