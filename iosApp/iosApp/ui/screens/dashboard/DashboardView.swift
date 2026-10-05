import SwiftUI
import DatawatchShared

// ── ViewModel ─────────────────────────────────────────────────────────────────

/// Dashboard tab state — PWA `renderDashboardView` / `_dash` (parity B36, D34a:
/// the PWA/Android card grid replaces the old multi-server overview).
///
/// Live inputs follow the PWA: one global `/ws` (sessions list, session_state
/// diffs, `hook_update` boards → EKG / live events / guardrails / node
/// health). REST cadences mirror `_dashLoop`: automata 5 s, smoke 2.5 s while a
/// run is active else 30 s, cost 30 s, heatmap 60 s, sessions 30 s fallback.
/// All shaping lives in the shared `IosDashEngine`; `revision` bumps make the
/// cards re-read it.
@MainActor
final class DashboardViewModel: ObservableObject {
    @Published private(set) var revision: Int = 0
    @Published private(set) var layout: [IosDashCard] = []
    @Published private(set) var layoutLoaded: Bool = false
    @Published private(set) var editing: Bool = false
    @Published var saveError: String? = nil

    private(set) var engine = IosDashEngine()
    private(set) var constellation: IosDashConstellation
    private(set) var profile: ServerProfile?
    private var live: IosSubscription? = nil
    private var pollTask: Task<Void, Never>? = nil
    private var seeded = Set<String>()
    private static let tickNs: UInt64 = 2_500_000_000

    init() {
        let e = IosDashEngine()
        engine = e
        constellation = IosDashConstellation(engine: e)
    }

    func select(_ newProfile: ServerProfile) {
        guard newProfile.id != profile?.id || pollTask == nil else { return }
        if newProfile.id != profile?.id {
            let e = IosDashEngine()
            engine = e
            constellation = IosDashConstellation(engine: e)
            seeded = []
            layout = []
            layoutLoaded = false
            editing = false
        }
        profile = newProfile
        start()
    }

    func stop() {
        pollTask?.cancel()
        pollTask = nil
        live?.cancel()
        live = nil
    }

    func bump() { revision &+= 1 }

    // ── Live + polling ────────────────────────────────────────────────────

    private func start() {
        guard let p = profile else { return }
        stop()
        loadLayout()
        live = IosDashboard.shared.subscribeLive(
            profile: p,
            onSessions: { [weak self] list in
                Task { @MainActor [weak self] in self?.applySessions(list, for: p) }
            },
            onSession: { [weak self] one in
                Task { @MainActor [weak self] in self?.applySessionDiff(one, for: p) }
            },
            onBoard: { [weak self] board in
                Task { @MainActor [weak self] in
                    guard let self, self.profile?.id == p.id else { return }
                    self.engine.applyBoard(board: board, nowMs: DashboardViewModel.nowMs(), live: true)
                    self.bump()
                }
            }
        )
        let pid = p.id
        pollTask = Task { [weak self] in
            var tick = 0
            while !Task.isCancelled {
                guard let self, self.profile?.id == pid else { return }
                self.poll(tick: tick)
                tick += 1
                try? await Task.sleep(nanoseconds: Self.tickNs)
            }
        }
    }

    /// One 2.5 s tick of the PWA `_dashLoop` fetch schedule.
    private func poll(tick: Int) {
        guard let p = profile else { return }
        let api = IosDashboard.shared
        if tick % 12 == 0 { refreshSessions() }
        if tick % 2 == 0 {
            api.loadPrds(profile: p) { [weak self] list in
                self?.onMain(p) { $0.engine.setPrds(all: list) }
            }
        }
        if tick % 12 == 0 {
            api.loadCost(profile: p) { [weak self] total in
                self?.onMain(p) { $0.engine.setCost(totalUsd: total.doubleValue) }
            }
        }
        if tick % 24 == 0 {
            api.loadHeatmap(profile: p) { [weak self] buckets in
                self?.onMain(p) { $0.engine.setHeatmap(buckets: buckets) }
            }
            api.loadMemoryStats(profile: p) { [weak self] stats in
                self?.onMain(p) { $0.engine.setMemStats(stats: stats) }
            }
            api.loadWebSearch(profile: p) { [weak self] stats in
                self?.onMain(p) { $0.engine.setWebSearch(stats: stats) }
            }
        }
        if engine.hasActiveSmoke() || tick % 12 == 0 {
            loadSmoke()
        }
    }

    /// Run a mutation on the main actor if the profile is still current, then redraw.
    private nonisolated func onMain(_ p: ServerProfile, _ apply: @escaping @MainActor (DashboardViewModel) -> Void) {
        Task { @MainActor [weak self] in
            guard let self, self.profile?.id == p.id else { return }
            apply(self)
            self.bump()
        }
    }

    func refreshSessions() {
        guard let p = profile else { return }
        Task { [weak self] in
            guard let list = try? await ServiceLocatorAsync.listSessions(profile: p) else { return }
            self?.applySessions(list, for: p)
        }
    }

    private func applySessions(_ list: [DwSession], for p: ServerProfile) {
        guard profile?.id == p.id else { return }
        engine.setSessions(list: list)
        seedBoards(profile: p)
        bump()
    }

    private func applySessionDiff(_ one: DwSession, for p: ServerProfile) {
        guard profile?.id == p.id else { return }
        var list = engine.allSessions()
        if let i = list.firstIndex(where: { $0.id == one.id }) {
            list[i] = one
        } else {
            list.append(one)
        }
        applySessions(list, for: p)
    }

    /// Seed hook boards for active sessions once (the PWA keeps them from earlier
    /// WS frames; iOS only listens while the tab is open).
    private func seedBoards(profile p: ServerProfile) {
        var budget = 8
        for sid in engine.activeSessionIds() where !seeded.contains(sid) && budget > 0 {
            seeded.insert(sid)
            budget -= 1
            IosDashboard.shared.loadBoard(profile: p, sessionId: sid) { [weak self] board in
                self?.onMain(p) { vm in
                    vm.engine.applyBoard(board: board, nowMs: DashboardViewModel.nowMs(), live: false)
                }
            }
        }
    }

    // ── Smoke ─────────────────────────────────────────────────────────────

    func loadSmoke() {
        guard let p = profile else { return }
        IosDashboard.shared.loadSmokeRuns(profile: p) { [weak self] runs in
            self?.onMain(p) { vm in
                let sel = vm.engine.setSmokeRuns(runs: runs)
                if !sel.isEmpty { vm.loadSmokeDetail(sel) }
            }
        }
    }

    private func loadSmokeDetail(_ id: String) {
        guard let p = profile else { return }
        IosDashboard.shared.loadSmokeDetail(profile: p, id: id) { [weak self] detail in
            self?.onMain(p) { $0.engine.setSmokeDetail(id: id, detail: detail) }
        }
    }

    func selectSmokeRun(_ id: String) {
        let fetch = engine.selectSmokeRun(id: id)
        bump()
        if !fetch.isEmpty { loadSmokeDetail(fetch) }
    }

    func setSmokeFilter(_ key: String) {
        engine.setSmokeFilter(key: key)
        bump()
    }

    /// Delete one run, or every run when [id] is empty (PWA "Clear all").
    func deleteSmoke(_ id: String) {
        guard let p = profile else { return }
        if id.isEmpty { engine.clearSmokeRuns() } else { engine.removeSmokeRun(id: id) }
        bump()
        IosDashboard.shared.deleteSmokeRun(profile: p, id: id) { }
    }

    func toggleTreeRow(_ prdId: String) {
        engine.toggleTreeRow(prdId: prdId)
        bump()
    }

    // ── Layout (BL303 S5 card grid) ───────────────────────────────────────

    private func loadLayout() {
        guard let p = profile else { return }
        IosDashboard.shared.loadLayout(profile: p) { [weak self] cards in
            Task { @MainActor [weak self] in
                guard let self, self.profile?.id == p.id else { return }
                if !self.editing { self.layout = cards }
                self.layoutLoaded = true
            }
        }
    }

    func startEdit() { editing = true }

    func stopEdit() {
        editing = false
        guard let p = profile else { return }
        let cards = layout
        IosDashboard.shared.saveLayout(profile: p, cards: cards) { [weak self] err in
            Task { @MainActor [weak self] in self?.saveError = err }
        }
    }

    func cycleSpan(_ id: String) { if editing { layout = IosDashCatalog.shared.cycledSpan(cards: layout, id: id) } }
    func cycleRows(_ id: String) { if editing { layout = IosDashCatalog.shared.cycledRows(cards: layout, id: id) } }
    func remove(_ id: String) { if editing { layout = IosDashCatalog.shared.removed(cards: layout, id: id) } }
    func add(_ id: String) { if editing { layout = IosDashCatalog.shared.added(cards: layout, id: id) } }
    func move(_ id: String, by delta: Int) {
        if editing { layout = IosDashCatalog.shared.moved(cards: layout, id: id, delta: Int32(delta)) }
    }

    nonisolated static func nowMs() -> Double { Date().timeIntervalSince1970 * 1000.0 }
}

/// Per-card collapse state persisted across launches (D27a).
@MainActor
final class DashCollapseStore: ObservableObject {
    private static let defaultsKey = "dw.dashboard.collapsed"
    @Published private var collapsed: Set<String>

    init() {
        collapsed = Set(UserDefaults.standard.stringArray(forKey: Self.defaultsKey) ?? [])
    }

    func isCollapsed(_ id: String) -> Bool { collapsed.contains(id) }

    func toggle(_ id: String) {
        if collapsed.contains(id) { collapsed.remove(id) } else { collapsed.insert(id) }
        UserDefaults.standard.set(Array(collapsed), forKey: Self.defaultsKey)
    }
}

/// Where a card tap wants to go (session detail, automaton detail, expand panel).
enum DashTarget {
    case session(String)
    case prd(String)
    case expand(String)
}

// ── Root View ─────────────────────────────────────────────────────────────────

struct DashboardView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var vm = DashboardViewModel()
    @StateObject private var collapse = DashCollapseStore()
    @ObservedObject private var expandNav = DashExpandNav.shared
    @State private var selectedProfileId: String? = UserDefaults.standard.string(forKey: "dw.active_profile_id")
    @State private var openSession: DwSession? = nil
    @State private var openPrd: PrdDto? = nil
    @State private var expandSession: DashExpandItem? = nil
    @State private var showAddPanel = false

    private var selectedProfile: ServerProfile? {
        if let id = selectedProfileId, let p = store.profiles.first(where: { $0.id == id }) {
            return p
        }
        return store.activeProfile
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(DatawatchColors.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarContent }
            .onAppear(perform: activate)
            .onDisappear { vm.stop() }
            .onChange(of: store.activeProfileId) { id in
                if id != selectedProfileId { selectedProfileId = id; activate() }
            }
            .onChange(of: store.profiles) { _ in activate() }
            .onChange(of: expandNav.pendingSessionId) { _ in consumeExpandNav() }
            .navigationDestination(isPresented: sessionBinding) { sessionDestination }
            .navigationDestination(isPresented: prdBinding) { prdDestination }
            .sheet(item: $expandSession) { item in expandSheet(item) }
            .sheet(isPresented: $showAddPanel) { addPanel }
            .alert("Layout not saved", isPresented: saveErrorBinding) {
                Button("OK", role: .cancel) { vm.saveError = nil }
            } message: {
                Text(vm.saveError ?? "")
            }
    }

    @ViewBuilder
    private var content: some View {
        if store.profiles.isEmpty {
            DashboardEmptyState()
        } else {
            VStack(spacing: 0) {
                // D2a: shared PWA "Server:" chip bar; selection flows back
                // through store.activeProfileId → onChange → activate().
                ServerPickerBar()
                grid
            }
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            HeaderView(title: L("Dashboard"), serverName: selectedProfile?.displayName)
        }
        ToolbarItem(placement: .navigationBarTrailing) {
            HStack(spacing: 4) {
                DocsLinkButton(profile: selectedProfile, anchor: "dashboard")
                AlertsBellButton()
                ReachabilityDotView(profile: selectedProfile)
            }
        }
    }

    private var grid: some View {
        GeometryReader { geo in
            let width = Double(geo.size.width)
            ScrollView {
                VStack(spacing: 0) {
                    DashStatBar(vm: vm, revision: vm.revision, onAdd: { showAddPanel = true })
                    if vm.layoutLoaded, let profile = selectedProfile {
                        DashCardGrid(
                            vm: vm,
                            collapse: collapse,
                            profile: profile,
                            viewportWidth: width,
                            onTarget: { handle($0) }
                        )
                    } else {
                        ProgressView()
                            .tint(DatawatchColors.primary)
                            .padding(.top, 40)
                    }
                }
            }
            .refreshable {
                vm.refreshSessions()
                vm.loadSmoke()
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────

    private func activate() {
        if let id = selectedProfileId, !store.profiles.contains(where: { $0.id == id }) {
            selectedProfileId = nil
        }
        if let p = selectedProfile { vm.select(p) }
        consumeExpandNav()
    }

    private func consumeExpandNav() {
        guard expandNav.pendingSessionId != nil, let id = expandNav.consume() else { return }
        handle(.expand(id))
    }

    private func handle(_ target: DashTarget) {
        switch target {
        case .session(let id):
            openSession = vm.engine.session(id: id)
        case .prd(let id):
            openPrd = vm.engine.prd(id: id)
        case .expand(let id):
            expandSession = DashExpandItem(sessionId: id)
        }
    }

    private var sessionBinding: Binding<Bool> {
        Binding(get: { openSession != nil }, set: { if !$0 { openSession = nil } })
    }

    private var prdBinding: Binding<Bool> {
        Binding(get: { openPrd != nil }, set: { if !$0 { openPrd = nil } })
    }

    private var saveErrorBinding: Binding<Bool> {
        Binding(get: { vm.saveError != nil }, set: { if !$0 { vm.saveError = nil } })
    }

    @ViewBuilder
    private var sessionDestination: some View {
        if let s = openSession, let p = selectedProfile {
            SessionDetailView(session: s, profile: p)
        }
    }

    @ViewBuilder
    private var prdDestination: some View {
        if let prd = openPrd, let p = selectedProfile {
            PrdDetailView(profile: p, initial: prd)
        }
    }

    @ViewBuilder
    private func expandSheet(_ item: DashExpandItem) -> some View {
        if let p = selectedProfile {
            DashExpandView(
                profile: p,
                sessionId: item.sessionId,
                session: vm.engine.session(id: item.sessionId),
                onOpenSession: { s in
                    expandSession = nil
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { openSession = s }
                }
            )
        }
    }

    private var addPanel: some View {
        DashAddCardPanel(
            used: Set(vm.layout.map { $0.id }),
            onAdd: { id in
                vm.add(id)
                showAddPanel = false
            }
        )
        .presentationDetents([.medium, .large])
    }
}

/// Identifiable wrapper for the expand sheet.
struct DashExpandItem: Identifiable {
    let sessionId: String
    var id: String { sessionId }
}

// ── Stat bar (PWA .dboard-stat-bar) ───────────────────────────────────────────

private struct DashStatBar: View {
    @ObservedObject var vm: DashboardViewModel
    let revision: Int
    let onAdd: () -> Void

    var body: some View {
        let s = vm.engine.statBar()
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Label("DASHBOARD", systemImage: "hexagon")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(DatawatchColors.primary)
                Spacer(minLength: 4)
                Text("live · ws")
                    .font(.caption2.monospaced())
                    .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.6))
                if vm.editing {
                    Button(action: onAdd) { Label("Card", systemImage: "plus") }
                        .buttonStyle(.bordered)
                        .controlSize(.mini)
                        .tint(DatawatchColors.primary)
                }
                editButton
            }
            DashStatLine(stats: s)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(DatawatchColors.surface)
        .overlay(alignment: .bottom) {
            Rectangle().fill(DatawatchColors.border).frame(height: 1)
        }
    }

    @ViewBuilder
    private var editButton: some View {
        if vm.editing {
            Button { vm.stopEdit() } label: { Label("Done", systemImage: "checkmark") }
                .buttonStyle(.borderedProminent)
                .controlSize(.mini)
                .tint(DatawatchColors.primary)
        } else {
            Button { vm.startEdit() } label: { Label("Edit", systemImage: "pencil") }
                .buttonStyle(.bordered)
                .controlSize(.mini)
                .tint(DatawatchColors.onSurfaceMuted)
        }
    }
}

/// `#dashStatSessions/Tasks/Guardrails/BurnRate` as one wrapping line.
private struct DashStatLine: View {
    let stats: IosDashStatBar

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                sessionsText
                if stats.tasksTotal > 0 { tasksText }
                guardrailsText
                if hasBurn { burnText }
            }
            .font(.caption)
        }
    }

    private var hasBurn: Bool { stats.costUsd > 0 || stats.active > 0 || stats.automata > 0 }

    private var sessionsText: some View {
        HStack(spacing: 4) {
            Text("\(Int(stats.sessions)) sess").foregroundStyle(DatawatchColors.onSurface)
            Text("·").foregroundStyle(DatawatchColors.onSurfaceMuted)
            Text("\(Int(stats.active)) active").foregroundStyle(DatawatchColors.primary)
            if stats.costUsd > 0 {
                Text(String(format: "$%.2f", stats.costUsd)).foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
    }

    private var tasksText: some View {
        HStack(spacing: 0) {
            Text("\(Int(stats.tasksDone))").foregroundStyle(DatawatchColors.success)
            Text("/\(Int(stats.tasksTotal)) tasks").foregroundStyle(DatawatchColors.onSurface)
        }
    }

    @ViewBuilder
    private var guardrailsText: some View {
        if stats.block > 0 {
            Label("\(Int(stats.block)) blk", systemImage: "exclamationmark.triangle.fill")
                .foregroundStyle(DatawatchColors.error)
                .fontWeight(.bold)
        }
        if stats.warn > 0 {
            Text("\(Int(stats.warn)) warn").foregroundStyle(DatawatchColors.warning)
        }
    }

    private var burnText: some View {
        HStack(spacing: 4) {
            if stats.costUsd > 0 {
                Text(String(format: "$%.2f today", stats.costUsd))
                    .foregroundStyle(DatawatchColors.success)
                    .fontWeight(.bold)
            }
            Text("\(Int(stats.active)) running").foregroundStyle(DatawatchColors.primary)
            if stats.automata > 0 {
                Text("\(Int(stats.automata)) automata").foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .font(.caption2.monospaced())
        .padding(.leading, 8)
        .overlay(alignment: .leading) {
            Rectangle().fill(DatawatchColors.border).frame(width: 1)
        }
    }
}

// ── Add Card panel (PWA _dashShowAddPanel) ────────────────────────────────────

private struct DashAddCardPanel: View {
    let used: Set<String>
    let onAdd: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 8) {
                    ForEach(IosDashCatalog.shared.defs, id: \.id) { def in
                        item(def)
                    }
                }
                .padding(16)
            }
            .background(DatawatchColors.surface)
            .navigationTitle(Text("Add Card"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }

    private func item(_ def: IosDashCardDef) -> some View {
        let isUsed = used.contains(def.id)
        return Button {
            onAdd(def.id)
        } label: {
            HStack(spacing: 6) {
                Image(systemName: def.symbol)
                Text(L(def.label)).lineLimit(1)
                Spacer(minLength: 0)
            }
            .font(.footnote)
            .foregroundStyle(DatawatchColors.onSurface)
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
            .background(DatawatchColors.background)
            .overlay(RoundedRectangle(cornerRadius: 5).stroke(DatawatchColors.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(isUsed)
        .opacity(isUsed ? 0.4 : 1.0)
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

private struct DashboardEmptyState: View {
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: "server.rack")
                .font(.system(.largeTitle))
                .imageScale(.large)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)

            Text("No servers configured")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)

            Text("Add a server in Settings to get started.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            NavigationLink(destination: SettingsView()) {
                Text("Go to Settings")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.primary)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 10)
                    .overlay(Capsule().stroke(DatawatchColors.primary, lineWidth: 1))
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

#if DEBUG
#Preview("Dashboard") {
    NavigationStack { DashboardView() }
        .environmentObject(ServerProfileStore())
        .preferredColorScheme(.dark)
}
#endif
