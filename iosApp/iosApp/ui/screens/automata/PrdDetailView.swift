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
    private var inFlight = false
    private static let interval: Duration = .seconds(10)

    init(profile: ServerProfile, initial: PrdDto) {
        self.profile = profile
        self.prd = initial
    }

    func start() {
        stop()
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
    @State private var rejectReason = ""
    @State private var showRevision = false
    @State private var revisionNote = ""
    @State private var showCancel = false
    @State private var showEdit = false
    @State private var showDelete = false
    @Environment(\.dismiss) private var dismissDetail

    init(profile: ServerProfile, initial: PrdDto) {
        _vm = StateObject(wrappedValue: PrdDetailViewModel(profile: profile, initial: initial))
    }

    private var prd: PrdDto { vm.prd }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                header
                actions
                if let spec = prd.spec, !spec.isEmpty {
                    specSection(spec)
                }
                storiesSection
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
                    Button { showEdit = true } label: { Label("Edit title / spec", systemImage: "pencil") }
                    if !["running", "planning", "archived"].contains(prd.status.lowercased()) {
                        Button {
                            Task { await vm.perform("reset_to_draft", body: ["actor": "operator"]) }
                        } label: { Label("Reset to Draft", systemImage: "arrow.uturn.backward") }
                    }
                    Divider()
                    Button(role: .destructive) { showDelete = true } label: { Label("Delete", systemImage: "trash") }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
                .accessibilityLabel("Automaton actions")
            }
        }
        .sheet(isPresented: $showEdit) {
            EditPrdView(profile: vm.profile, prdId: prd.id, title: prd.displayTitle, spec: prd.spec ?? "") {
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
        .refreshable { await vm.refresh() }
        .alert("Reject PRD", isPresented: $showReject) {
            TextField("Reason", text: $rejectReason)
            Button("Reject", role: .destructive) {
                let reason = rejectReason
                rejectReason = ""
                Task { await vm.perform("reject", body: ["reason": reason]) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The PRD moves to rejected. The reason is recorded with the PRD.")
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
        .alert("Cancel PRD?", isPresented: $showCancel) {
            Button("Cancel PRD", role: .destructive) { Task { await vm.cancel() } }
            Button("Keep running", role: .cancel) {}
        } message: {
            Text("Running tasks are stopped. The PRD and its history are kept.")
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
            metaRow
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
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 10))
    }

    private var metaRow: some View {
        var parts: [String] = []
        if let t = prd.type, !t.isEmpty { parts.append(t) }
        if let b = prd.backend, !b.isEmpty {
            parts.append((prd.model?.isEmpty == false) ? "\(b)/\(prd.model!)" : b)
        }
        if let e = prd.effort, !e.isEmpty { parts.append("effort \(e)") }
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
                        Task { await vm.perform("approve") }
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
            case "running", "decomposing", "planning", "blocked":
                actionButton("Cancel PRD", systemImage: "stop.circle", tint: DatawatchColors.error) { showCancel = true }
            default:
                EmptyView()
            }
        }
    }

    private func actionButton(_ title: String, systemImage: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: systemImage)
                .font(DatawatchFonts.bodyMedium)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
        }
        .foregroundStyle(tint)
        .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 8))
    }

    private func specSection(_ spec: String) -> some View {
        DisclosureGroup {
            Text(spec)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 6)
        } label: {
            Text("Spec")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
        }
        .tint(DatawatchColors.primary)
        .padding(14)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 10))
    }

    private var storiesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Stories & tasks (\(prd.stories.count))")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            if prd.stories.isEmpty {
                Text(prd.status.lowercased() == "decomposing" ? "Decomposing — stories appear when planning finishes." : "No stories yet.")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            ForEach(prd.stories, id: \.id) { story in
                storyGroup(story)
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
                if let d = story.description_, !d.isEmpty {
                    Text(d)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .padding(.bottom, 2)
                }
                ForEach(story.tasks, id: \.id) { task in
                    PrdTaskRow(task: task)
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
                Text("\(done)/\(total)")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .tint(DatawatchColors.primary)
        .padding(12)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 10))
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
    let task: PrdTaskDto

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
            if let err = task.error, !err.isEmpty {
                Text(err)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
                    .lineLimit(3)
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
                Text("session \(sid.prefix(12))")
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .padding(.leading, 24)
            }
        }
        .padding(.vertical, 3)
        .accessibilityElement(children: .combine)
    }
}
