import SwiftUI
import DatawatchShared

// ── Status styling (mirrors Android prdStatusColor / PWA mapping) ────────────

enum PrdStatusStyle {
    static func color(_ status: String) -> Color {
        switch status.lowercased() {
        case "running":                                        return Color(hex: 0x10B981)
        case "approved":                                       return Color(hex: 0x8B5CF6)
        case "needs_review", "revisions_asked", "awaiting_approval": return Color(hex: 0xF59E0B)
        case "blocked", "rejected", "failed":                  return Color(hex: 0xEF4444)
        case "decomposing", "planning":                        return Color(hex: 0xA855F7)
        case "complete", "completed":                          return Color(hex: 0x059669)
        default:                                               return Color(hex: 0x94A3B8)
        }
    }

    static func label(_ status: String) -> String {
        status.replacingOccurrences(of: "_", with: " ")
    }

    /// Task glyph + colour — matches Android PrdDetailDialog / PWA status mapping.
    static func taskGlyph(_ status: String) -> (String, Color) {
        switch status.lowercased() {
        case "running", "in_progress": return ("▶", Color(hex: 0x3B82F6))
        case "verifying":              return ("⟳", Color(hex: 0x8B5CF6))
        case "running_tests":          return ("🧪", Color(hex: 0x06B6D4))
        case "complete", "completed":  return ("✓", Color(hex: 0x10B981))
        case "failed":                 return ("✗", Color(hex: 0xEF4444))
        case "blocked":                return ("⛔", Color(hex: 0xF59E0B))
        case "waiting_capacity":       return ("⏳", Color(hex: 0xF59E0B))
        default:                       return ("○", DatawatchColors.onSurfaceMuted)
        }
    }

    /// PWA `.automata-filter-badge.status-*.active` colours.
    static func filterColor(_ status: String) -> Color {
        switch status {
        case "draft": return Color(hex: 0x6B7280)
        case "planning": return Color(hex: 0x3B82F6)
        case "needs_review": return Color(hex: 0xF59E0B)
        case "approved", "running": return Color(hex: 0x10B981)
        case "blocked": return Color(hex: 0xEF4444)
        default: return Color(hex: 0x6B7280)
        }
    }

    static func isDone(_ status: String) -> Bool {
        let s = status.lowercased()
        return s == "complete" || s == "completed"
    }
}

/// D23a: PWA `statusPill` — session state-badge tokens (11/600, 1 px currentColor
/// border, radius 10) with the `dw-running-pulse` (0.55↔1, 700 ms alternate) on
/// running / planning / decomposing; static under Reduce Motion.
struct PrdStatusChip: View {
    let status: String
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dim = false

    private var key: String { status.isEmpty ? "unknown" : status.lowercased() }
    private var active: Bool { ["running", "planning", "decomposing"].contains(key) }
    private var pulsing: Bool { active && !reduceMotion }

    private var fg: Color {
        if active { return DatawatchColors.success }
        if key == "failed" { return DatawatchColors.error }
        if key == "complete" || key == "completed" { return DatawatchColors.onSurfaceMuted }
        return DatawatchColors.onSurface
    }

    private var bg: Color {
        if active { return DatawatchColors.success.opacity(0.15) }
        if key == "failed" { return DatawatchColors.error.opacity(0.15) }
        if key == "complete" || key == "completed" { return DatawatchColors.onSurfaceMuted.opacity(0.10) }
        return Color.clear
    }

    var body: some View {
        Text(key)
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(fg)
            .padding(.horizontal, 7)
            .padding(.vertical, 1)
            .background(bg, in: RoundedRectangle(cornerRadius: DatawatchRadius.pill))
            .overlay(RoundedRectangle(cornerRadius: DatawatchRadius.pill).stroke(fg, lineWidth: 1))
            .opacity(pulsing && dim ? 0.55 : 1.0)
            .onAppear { restartPulse() }
            .onChange(of: pulsing) { _ in restartPulse() }
            .accessibilityLabel("Status: \(PrdStatusStyle.label(status))")
    }

    private func restartPulse() {
        if pulsing {
            dim = false
            withAnimation(.easeInOut(duration: 0.7).repeatForever(autoreverses: true)) { dim = true }
        } else {
            withAnimation(.none) { dim = false }
        }
    }
}

extension PrdDto {
    var displayTitle: String {
        if let t = title, !t.isEmpty { return t }
        return name.isEmpty ? id : name
    }
    var allTasks: [PrdTaskDto] { stories.flatMap { $0.tasks } }
    var doneTaskCount: Int { allTasks.filter { PrdStatusStyle.isDone($0.status) }.count }
}

// ── ViewModel ─────────────────────────────────────────────────────────────

/// Sequential, visibility-gated REST poll of the PRD list (15 s). Live
/// `prd_update` WS frames are the next step once PrdHub is bridged.
@MainActor
final class PrdListViewModel: ObservableObject {
    @Published private(set) var prds: [PrdDto] = []
    @Published private(set) var isLoading = false
    @Published private(set) var error: String? = nil
    @Published var filterOpen = false
    @Published var historyOn = false
    @Published var statusFilter: Set<String> = []
    @Published var typeFilter: Set<String> = []
    @Published var search = ""
    @Published private(set) var pinned: Set<String> =
        Set(UserDefaults.standard.stringArray(forKey: "dw.automata.pinned") ?? [])

    /// PWA _AUTOMATA_ACTIVE_STATUSES / _AUTOMATA_STATE_RANK.
    static let activeStatuses: Set<String> = ["draft", "planning", "decomposing", "needs_review", "revisions_asked",
                                              "approved", "running", "blocked", "completed"]
    static let filterStatuses = ["draft", "planning", "needs_review", "approved", "running", "blocked", "archived"]
    static let filterTypes = ["software", "research", "operational", "personal"]
    private static let rank: [String: Int] = [
        "waiting_input": 0, "needs_review": 0, "revisions_asked": 0, "blocked": 1, "running": 2, "decomposing": 2,
        "approved": 3, "planning": 3, "draft": 4, "completed": 5, "rejected": 5, "cancelled": 5, "archived": 6,
    ]

    /// PWA _automataFilteredList: non-templates, history gate, status/type/search filters,
    /// then pinned → state rank → most recently updated.
    var visible: [PrdDto] {
        var list = prds.filter { !$0.isTemplate }
        if !historyOn && statusFilter.isEmpty {
            list = list.filter { Self.activeStatuses.contains($0.status.isEmpty ? "draft" : $0.status) }
        }
        if !statusFilter.isEmpty { list = list.filter { statusFilter.contains($0.status.isEmpty ? "draft" : $0.status) } }
        if !typeFilter.isEmpty { list = list.filter { typeFilter.contains($0.type ?? "") } }
        let q = search.trimmingCharacters(in: .whitespaces).lowercased()
        if !q.isEmpty {
            list = list.filter { ($0.title ?? "").lowercased().contains(q) || $0.name.lowercased().contains(q) || $0.id.lowercased().contains(q) }
        }
        return list.sorted { a, b in
            let ap = pinned.contains(a.id) ? 0 : 1, bp = pinned.contains(b.id) ? 0 : 1
            if ap != bp { return ap < bp }
            let ar = Self.rank[a.status] ?? 9, br = Self.rank[b.status] ?? 9
            if ar != br { return ar < br }
            return (a.updatedAt ?? a.createdAt ?? "") > (b.updatedAt ?? b.createdAt ?? "")
        }
    }

    func togglePin(_ id: String) {
        if pinned.contains(id) { pinned.remove(id) } else { pinned.insert(id) }
        UserDefaults.standard.set(Array(pinned), forKey: "dw.automata.pinned")
    }

    // ── Batch mode (PWA _automataRenderBatchBar / batchAutomataAction) ──

    @Published var selectMode = false { didSet { if !selectMode { selected.removeAll() } } }
    @Published var selected: Set<String> = []
    @Published private(set) var batchRunning = false
    @Published var batchError: String? = nil

    static let historyStatuses: Set<String> = ["completed", "rejected", "cancelled", "archived"]
    static let batchActions = ["run", "approve", "cancel", "archive", "delete"]

    func eligible(_ action: String, _ p: PrdDto) -> Bool {
        let st = p.status.isEmpty ? "draft" : p.status
        switch action {
        case "run": return st == "approved"
        case "approve": return st == "needs_review"
        case "cancel": return !Self.historyStatuses.contains(st)
        case "archive", "delete": return Self.historyStatuses.contains(st)
        default: return false
        }
    }

    func eligibleIds(_ action: String) -> [String] {
        prds.filter { selected.contains($0.id) && eligible(action, $0) }.map { $0.id }
    }

    func toggleSelected(_ id: String) {
        if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
    }

    func selectAllVisible(_ on: Bool) {
        selected = on ? Set(visible.map { $0.id }) : []
    }

    /// Acts on the eligible subset only, one request at a time; non-eligible stay selected.
    func runBatch(_ action: String) async {
        let ids = eligibleIds(action)
        guard !ids.isEmpty else { return }
        batchRunning = true
        var failures = 0
        for id in ids {
            guard let profile = owner(ofPrd: id) else { failures += 1; continue }
            do {
                if action == "delete" {
                    try await ServiceLocatorAsync.cancelPrd(profile: profile, prdId: id, hard: true)
                } else {
                    try await ServiceLocatorAsync.prdAction(profile: profile, prdId: id, action: action)
                }
                selected.remove(id)
            } catch {
                failures += 1
            }
        }
        batchRunning = false
        if failures > 0 { batchError = "\(failures) of \(ids.count) \(action) request(s) failed." }
        await refreshAsync()
    }

    /// D72a inline card actions (Android PrdRow onApprove / onReject / onRevise / onPlan / onRun / onCancel).
    func act(prdId: String, action: String, body: [String: String]?) async {
        guard let profile = owner(ofPrd: prdId) else { return }
        do {
            if action == "cancel" {
                try await ServiceLocatorAsync.cancelPrd(profile: profile, prdId: prdId, hard: false)
            } else {
                try await ServiceLocatorAsync.prdAction(profile: profile, prdId: prdId, action: action, body: body)
            }
        } catch {
            batchError = error.localizedDescription
        }
        await refreshAsync()
    }

    var profileId: String? { profile?.id }

    // ── D2a all-servers scope (Android AutonomousViewModel.refreshAllServers) ──

    /// Every server listed — one normally, every enabled one under "All".
    private(set) var profiles: [ServerProfile] = []
    /// prd id → owning server profile id (aggregate mode only).
    @Published private(set) var prdServer: [String: String] = [:]
    var allMode: Bool { profiles.count > 1 }

    /// Server that owns an automaton: its aggregate owner, else the single profile.
    func owner(ofPrd id: String) -> ServerProfile? {
        if allMode, let pid = prdServer[id] { return profiles.first { $0.id == pid } }
        return profile
    }

    /// Server tag for a card — only when aggregating.
    func serverName(ofPrd id: String) -> String? {
        guard allMode else { return nil }
        return owner(ofPrd: id)?.displayName
    }

    func toggleStatus(_ v: String) {
        if statusFilter.contains(v) { statusFilter.remove(v) } else { statusFilter.insert(v) }
    }

    func toggleType(_ v: String) {
        if typeFilter.contains(v) { typeFilter.remove(v) } else { typeFilter.insert(v) }
    }

    private var profile: ServerProfile?
    private var pollTask: Task<Void, Never>? = nil
    private var prdSubscriptions: [IosSubscription] = []
    private var inFlight = false
    /// REST is a fallback now that prd_update frames patch the list live (PWA/Android #178).
    private static let interval: Duration = .seconds(30)

    func start(profile: ServerProfile, all: [ServerProfile] = []) {
        let scope: [ServerProfile] = all.count > 1 ? all : [profile]
        if self.profile?.id != profile.id || scope.map({ $0.id }) != profiles.map({ $0.id }) {
            prds = []
            prdServer = [:]
            error = nil
        }
        self.profile = profile
        self.profiles = scope
        stop()
        for p in scope {
            let pid: String = p.id
            let sub: IosSubscription = IosServiceLocator.shared.subscribePrdUpdates(profile: p) { [weak self] updated in
                Task { @MainActor [weak self] in self?.patch(updated, serverId: pid) }
            }
            prdSubscriptions.append(sub)
        }
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                guard let self else { return }
                await self.refreshAsync()
                try? await Task.sleep(for: Self.interval)
            }
        }
    }

    func stop() {
        pollTask?.cancel()
        pollTask = nil
        for sub in prdSubscriptions { sub.cancel() }
        prdSubscriptions = []
    }

    /// Replace (or insert) one PRD from a `prd_update` frame — no refetch, no flicker.
    private func patch(_ updated: PrdDto, serverId: String) {
        if allMode { prdServer[updated.id] = serverId }
        if let i = prds.firstIndex(where: { $0.id == updated.id }) {
            prds[i] = updated
        } else {
            prds.insert(updated, at: 0)
        }
    }

    func refreshAsync() async {
        if allMode {
            await refreshAllAsync()
            return
        }
        guard let profile, !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        if prds.isEmpty { isLoading = true }
        do {
            prds = try await ServiceLocatorAsync.listPrds(profile: profile)
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        isLoading = false
    }

    /// PWA `/api/autonomous/prds/aggregated` equivalent: fetch each enabled
    /// server in turn and merge, tagging every automaton with its server.
    /// A server without autonomous enabled (404) is skipped silently.
    private func refreshAllAsync() async {
        guard !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        if prds.isEmpty { isLoading = true }
        let targets: [ServerProfile] = profiles
        var merged: [PrdDto] = []
        var owners: [String: String] = [:]
        var failures: [String] = []
        for p in targets {
            do {
                let list: [PrdDto] = try await ServiceLocatorAsync.listPrds(profile: p)
                for prd in list { owners[prd.id] = p.id }
                merged.append(contentsOf: list)
            } catch {
                let msg: String = error.localizedDescription
                let lower: String = msg.lowercased()
                if !lower.contains("404") && !lower.contains("not found") {
                    failures.append("\(p.displayName): \(msg)")
                }
            }
        }
        guard targets.map({ $0.id }) == profiles.map({ $0.id }) else { return }
        prds = merged
        prdServer = owners
        error = failures.isEmpty ? nil : L("Some servers unreachable: ") + failures.prefix(3).joined(separator: "; ")
        isLoading = false
    }
}

// ── List view ─────────────────────────────────────────────────────────────

struct PrdListView: View {
    let profile: ServerProfile
    /// D2a: every enabled server when the picker is on "All" (empty otherwise).
    var allProfiles: [ServerProfile] = []
    /// Wizard "Browse" template link → Automata › Templates.
    var onBrowseTemplates: (() -> Void)? = nil
    @StateObject private var vm = PrdListViewModel()
    @State private var showWizard = false
    @State private var confirmBatchDelete = false
    /// D72a / D74a review dialogs raised from a card's lifecycle strip.
    @State private var review: PrdReviewRequest? = nil
    /// D71a parent ↗ link target.
    @State private var openParent: PrdDto? = nil
    /// D61a watched automata.
    @ObservedObject private var localPrefs = LocalSessionPrefs.shared

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            listContent
            if vm.selectMode {
                batchBar
            } else {
            Button {
                showWizard = true
            } label: {
                Text("⚡")
                    .font(.system(size: 24))
                    .frame(width: 56, height: 56)
                    .background(DatawatchColors.secondary, in: Circle()) // PWA .fab: accent2 fill
                    .shadow(color: .black.opacity(0.35), radius: 6, y: 3)
            }
            .padding(.trailing, 20)
            .padding(.bottom, 20)
            .accessibilityLabel("Launch automaton")
            }
        }
        .alert("Delete \(vm.eligibleIds("delete").count) automaton(s)?", isPresented: $confirmBatchDelete) {
            Button("Delete", role: .destructive) { Task { await vm.runBatch("delete") } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This cannot be undone.")
        }
        .alert("Batch action", isPresented: Binding(get: { vm.batchError != nil }, set: { if !$0 { vm.batchError = nil } })) {
            Button("OK", role: .cancel) { vm.batchError = nil }
        } message: {
            Text(vm.batchError ?? "")
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            if vm.allMode, let err = vm.error, !vm.prds.isEmpty {
                Text(err)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.warning)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
            }
        }
        .sheet(isPresented: $showWizard) {
            NewPrdView(profile: profile, onCreated: { _ in Task { await vm.refreshAsync() } }, onBrowseTemplates: onBrowseTemplates)
        }
        .prdReviewDialogs($review) { prdId, action, body in
            Task { await vm.act(prdId: prdId, action: action, body: body) }
        }
        .navigationDestination(isPresented: Binding(
            get: { openParent != nil },
            set: { if !$0 { openParent = nil } }
        )) {
            if let parent = openParent {
                PrdDetailView(profile: vm.owner(ofPrd: parent.id) ?? profile, initial: parent)
            }
        }
    }

    /// Lifecycle strip taps on a card: Plan / Run go straight through; Approve,
    /// Reject, Revise and Cancel open their dialog first.
    private func cardAction(_ prd: PrdDto, _ action: String) {
        switch action {
        case "approve", "reject", "request_revision", "cancel":
            review = PrdReviewRequest(prdId: prd.id, title: prd.displayTitle, action: action)
        default:
            Task { await vm.act(prdId: prd.id, action: action, body: nil) }
        }
    }

    private func isWatched(_ prd: PrdDto) -> Bool {
        _ = localPrefs.revision
        guard let pid = vm.owner(ofPrd: prd.id)?.id else { return false }
        return localPrefs.contains(.watchedAutomata, profileId: pid, id: prd.id)
    }

    private func toggleWatch(_ prd: PrdDto) {
        guard let pid = vm.owner(ofPrd: prd.id)?.id else { return }
        localPrefs.toggle(.watchedAutomata, profileId: pid, id: prd.id)
    }

    private func parentTap(_ prd: PrdDto) -> (() -> Void)? {
        guard let pid = prd.parentPrdId, !pid.isEmpty else { return nil }
        return {
            if let parent = vm.prds.first(where: { $0.id == pid }) { openParent = parent } else { vm.search = pid }
        }
    }

    private func row(_ prd: PrdDto) -> PrdRow {
        var watchToggle: (() -> Void)? = nil
        var parent: (() -> Void)? = nil
        var action: ((String) -> Void)? = nil
        if !vm.selectMode {
            watchToggle = { toggleWatch(prd) }
            parent = parentTap(prd)
            action = { (a: String) in cardAction(prd, a) }
        }
        return PrdRow(
            prd: prd,
            pinned: vm.pinned.contains(prd.id),
            watched: isWatched(prd),
            onWatchToggle: watchToggle,
            onParent: parent,
            onAction: action,
            serverName: vm.serverName(ofPrd: prd.id)
        )
    }

    private var listContent: some View {
        Group {
            if vm.isLoading && vm.prds.isEmpty {
                LoadingIndicator(message: "Loading automata…")
            } else if let err = vm.error, vm.prds.isEmpty {
                ErrorCard(message: err) { vm.start(profile: profile, all: allProfiles) }
            } else if vm.prds.isEmpty {
                emptyView
            } else {
                VStack(spacing: 0) {
                    listToolbar
                    list
                }
            }
        }
        .onAppear { vm.start(profile: profile, all: allProfiles) }
        .onDisappear { vm.stop() }
        .onChange(of: scopeKey) { _ in vm.start(profile: profile, all: allProfiles) }
    }

    /// Restart when the server or the All-servers scope changes.
    private var scopeKey: String {
        ([profile.id] + allProfiles.map { $0.id }).joined(separator: ",")
    }

    private var batchBar: some View {
        let allSelected = !vm.visible.isEmpty && vm.visible.allSatisfy { vm.selected.contains($0.id) }
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                batchButton(allSelected ? "None" : "All", count: nil) { vm.selectAllVisible(!allSelected) }
                batchButton("Run", count: vm.eligibleIds("run").count) { Task { await vm.runBatch("run") } }
                batchButton("Approve", count: vm.eligibleIds("approve").count) { Task { await vm.runBatch("approve") } }
                batchButton("Cancel run", count: vm.eligibleIds("cancel").count) { Task { await vm.runBatch("cancel") } }
                batchButton("Archive", count: vm.eligibleIds("archive").count) { Task { await vm.runBatch("archive") } }
                batchButton("🗑 Delete", count: vm.eligibleIds("delete").count, tint: DatawatchColors.error) { confirmBatchDelete = true }
                batchButton("Done", count: nil) { vm.selectMode = false }
                if vm.batchRunning { ProgressView().controlSize(.small) }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
        }
        .background(DatawatchColors.surface)
        .overlay(Divider().background(DatawatchColors.border), alignment: .top)
        .frame(maxWidth: .infinity)
    }

    private func batchButton(_ title: String, count: Int?, tint: Color = DatawatchColors.onSurface, action: @escaping () -> Void) -> some View {
        let disabled = (count == 0) || vm.batchRunning
        return Button(action: action) {
            HStack(spacing: 3) {
                Text(L(title))
                if let count { Text("(\(count))").opacity(0.6) }
            }
            .font(DatawatchFonts.labelSmall.weight(.semibold))
            .foregroundStyle(disabled ? DatawatchColors.onSurfaceMuted : tint)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(DatawatchColors.surface2, in: Capsule())
        }
        .disabled(disabled)
    }

    private var listToolbar: some View {
        VStack(spacing: 6) {
            HStack(spacing: 8) {
                chip("⊞ Filter", on: vm.filterOpen) { vm.filterOpen.toggle() }
                chip("History", on: vm.historyOn) { vm.historyOn.toggle() }
                chip("☑ Select", on: vm.selectMode) { vm.selectMode.toggle() }
                HStack(spacing: 4) {
                    Image(systemName: "magnifyingglass").foregroundStyle(DatawatchColors.onSurfaceMuted)
                    TextField("Search automata…", text: $vm.search)
                        .font(DatawatchFonts.bodyMedium)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 5)
                .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 6))
            }
            if vm.filterOpen {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(PrdListViewModel.filterStatuses, id: \.self) { st in
                            chip(PrdStatusStyle.label(st), on: vm.statusFilter.contains(st), tint: PrdStatusStyle.filterColor(st)) {
                                vm.toggleStatus(st)
                            }
                        }
                        Divider().frame(height: 18)
                        ForEach(PrdListViewModel.filterTypes, id: \.self) { ty in
                            chip(ty, on: vm.typeFilter.contains(ty)) { vm.toggleType(ty) }
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }

    private func chip(_ title: String, on: Bool, tint: Color = DatawatchColors.primary, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(L(title))
                .font(DatawatchFonts.badge)
                .foregroundStyle(on ? Color.white : DatawatchColors.onSurfaceMuted)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(on ? tint : DatawatchColors.surface2, in: Capsule())
        }
        .buttonStyle(.borderless)
    }

    private var list: some View {
        List {
            if vm.visible.isEmpty {
                Text(L(vm.historyOn ? "No cancelled, rejected or archived automata." : "No automata. Launch one with ⚡."))
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .listRowBackground(Color.clear)
            }
            ForEach(vm.visible, id: \.id) { prd in
                Group {
                    if vm.selectMode {
                        Button {
                            vm.toggleSelected(prd.id)
                        } label: {
                            HStack(spacing: 10) {
                                Image(systemName: vm.selected.contains(prd.id) ? "checkmark.circle.fill" : "circle")
                                    .foregroundStyle(vm.selected.contains(prd.id) ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
                                row(prd)
                            }
                        }
                        .buttonStyle(.plain)
                    } else {
                        NavigationLink {
                            PrdDetailView(profile: vm.owner(ofPrd: prd.id) ?? profile, initial: prd)
                        } label: {
                            row(prd)
                        }
                    }
                }
                .contextMenu {
                    Button(vm.pinned.contains(prd.id) ? "Unpin" : "Pin") { vm.togglePin(prd.id) }
                    // D52b: Pause / Resume (POST /prds/{id}/pause|resume).
                    if prd.status.lowercased() == "running" {
                        Button { Task { await vm.act(prdId: prd.id, action: "pause", body: nil) } } label: {
                            Label("Pause", systemImage: "pause.fill")
                        }
                    } else if prd.status.lowercased() == "paused" {
                        Button { Task { await vm.act(prdId: prd.id, action: "resume", body: nil) } } label: {
                            Label("Resume", systemImage: "play.fill")
                        }
                    }
                }
                .listRowBackground(PrdRowBackground(status: prd.status))
                .listRowSeparatorTint(DatawatchColors.border)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .refreshable { await vm.refreshAsync() }
    }

    private var emptyView: some View {
        VStack(spacing: 20) {
            Image(systemName: "doc.text.magnifyingglass")
                .font(.system(.largeTitle))
                .imageScale(.large)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No automata")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("No automata. Launch one with ⚡.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// ── Row ───────────────────────────────────────────────────────────────────

struct PrdRow: View {
    let prd: PrdDto
    var pinned: Bool = false
    /// D61a 🔔 watch toggle (nil hides it).
    var watched: Bool = false
    var onWatchToggle: (() -> Void)? = nil
    /// D71a parent ↗ chip tap (nil = not tappable).
    var onParent: (() -> Void)? = nil
    /// D72a: lifecycle strip steps act inline when set (Android PrdRow).
    var onAction: ((String) -> Void)? = nil
    /// D2a: owning server, shown when the list aggregates every server.
    var serverName: String? = nil

    var body: some View {
        let total = prd.allTasks.count
        let done = prd.doneTaskCount
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                if pinned { Text("📌").font(DatawatchFonts.labelSmall).accessibilityLabel("Pinned") }
                if let t = prd.type, !t.isEmpty { PrdTypeBadge(type: t) }
                if prd.isTemplate { PrdTemplateBadge() }
                Text(prd.displayTitle)
                    .font(DatawatchFonts.titleMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(2)
                Spacer(minLength: 8)
                if let onWatchToggle { watchButton(onWatchToggle) }
                PrdStatusChip(status: prd.status)
            }
            HStack(spacing: 6) {
                if let serverName { serverChip(serverName) }
                if let pid = prd.parentPrdId, !pid.isEmpty { parentChip(pid) }
                Text(metaLine)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(1)
            }
            if let pos = PrdCardStyle.positionLine(prd) {
                Text(pos)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.success)
                    .lineLimit(2)
            }
            if total > 0 {
                ProgressView(value: Double(done), total: Double(total))
                    .tint(PrdStatusStyle.color(prd.status))
                    .accessibilityLabel("\(done) of \(total) tasks complete")
            }
            PrdLifecycleStrip(prd: prd, onAction: onAction)
            PrdStoriesTree(prd: prd)
        }
        .padding(.vertical, 6)
    }

    private func watchButton(_ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: watched ? "bell.fill" : "bell.slash")
                .font(.system(size: 12))
                .foregroundStyle(watched ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted.opacity(0.5))
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(watched ? "Watching" : "Not watching")
    }

    private func serverChip(_ name: String) -> some View {
        Text(name)
            .font(DatawatchFonts.labelSmall)
            .foregroundStyle(DatawatchColors.secondary)
            .lineLimit(1)
            .padding(.horizontal, 5)
            .padding(.vertical, 1)
            .background(DatawatchColors.secondary.opacity(0.16), in: RoundedRectangle(cornerRadius: 6))
            .accessibilityLabel("Server \(name)")
    }

    /// Android `↗ <parent id prefix>` chip (accent2 @16 %), tappable here.
    @ViewBuilder
    private func parentChip(_ pid: String) -> some View {
        let chip = Text("↗ " + String(pid.prefix(8)))
            .font(DatawatchFonts.labelSmall)
            .foregroundStyle(DatawatchColors.secondary)
            .padding(.horizontal, 5)
            .padding(.vertical, 1)
            .background(DatawatchColors.secondary.opacity(0.16), in: RoundedRectangle(cornerRadius: 6))
        if let onParent {
            Button(action: onParent) { chip }
                .buttonStyle(.borderless)
                .accessibilityLabel("Open parent automaton \(pid)")
        } else {
            chip.accessibilityLabel("Parent automaton \(pid)")
        }
    }

    private var metaLine: String {
        var parts: [String] = []
        if let b = prd.backend, !b.isEmpty {
            parts.append((prd.model?.isEmpty == false) ? "\(b)/\(prd.model!)" : b)
        }
        parts.append("\(prd.stories.count) stories · \(prd.doneTaskCount)/\(prd.allTasks.count) tasks")
        return parts.joined(separator: "  ·  ")
    }
}
