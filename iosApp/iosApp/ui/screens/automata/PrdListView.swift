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

struct PrdStatusChip: View {
    let status: String
    var body: some View {
        let color = PrdStatusStyle.color(status)
        Text(PrdStatusStyle.label(status).uppercased())
            .font(DatawatchFonts.badge)
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.18), in: Capsule())
            .accessibilityLabel("Status: \(PrdStatusStyle.label(status))")
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

    func toggleStatus(_ v: String) {
        if statusFilter.contains(v) { statusFilter.remove(v) } else { statusFilter.insert(v) }
    }

    func toggleType(_ v: String) {
        if typeFilter.contains(v) { typeFilter.remove(v) } else { typeFilter.insert(v) }
    }

    private var profile: ServerProfile?
    private var pollTask: Task<Void, Never>? = nil
    private var inFlight = false
    private static let interval: Duration = .seconds(15)

    func start(profile: ServerProfile) {
        if self.profile?.id != profile.id {
            prds = []
            error = nil
        }
        self.profile = profile
        stop()
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
    }

    func refreshAsync() async {
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
}

// ── List view ─────────────────────────────────────────────────────────────

struct PrdListView: View {
    let profile: ServerProfile
    @StateObject private var vm = PrdListViewModel()
    @State private var showWizard = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            listContent
            Button {
                showWizard = true
            } label: {
                Text("⚡")
                    .font(.system(size: 24))
                    .frame(width: 56, height: 56)
                    .background(DatawatchColors.primary, in: Circle())
                    .shadow(color: .black.opacity(0.35), radius: 6, y: 3)
            }
            .padding(.trailing, 20)
            .padding(.bottom, 20)
            .accessibilityLabel("Launch automaton")
        }
        .sheet(isPresented: $showWizard) {
            NewPrdView(profile: profile) { _ in Task { await vm.refreshAsync() } }
        }
    }

    private var listContent: some View {
        Group {
            if vm.isLoading && vm.prds.isEmpty {
                LoadingIndicator(message: "Loading PRDs…")
            } else if let err = vm.error, vm.prds.isEmpty {
                ErrorCard(message: err) { vm.start(profile: profile) }
            } else if vm.prds.isEmpty {
                emptyView
            } else {
                VStack(spacing: 0) {
                    listToolbar
                    list
                }
            }
        }
        .onAppear { vm.start(profile: profile) }
        .onDisappear { vm.stop() }
        .onChange(of: profile.id) { _ in vm.start(profile: profile) }
    }

    private var listToolbar: some View {
        VStack(spacing: 6) {
            HStack(spacing: 8) {
                chip("⊞ Filter", on: vm.filterOpen) { vm.filterOpen.toggle() }
                chip("History", on: vm.historyOn) { vm.historyOn.toggle() }
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
            Text(title)
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
                Text(vm.historyOn || !vm.statusFilter.isEmpty ? "No automata match these filters." : "No active automata — turn on History to see finished ones.")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .listRowBackground(Color.clear)
            }
            ForEach(vm.visible, id: \.id) { prd in
                NavigationLink {
                    PrdDetailView(profile: profile, initial: prd)
                } label: {
                    PrdRow(prd: prd, pinned: vm.pinned.contains(prd.id))
                }
                .contextMenu {
                    Button(vm.pinned.contains(prd.id) ? "Unpin" : "Pin") { vm.togglePin(prd.id) }
                }
                .listRowBackground(DatawatchColors.surface)
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
            Text("No PRDs")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("PRDs created on the server or in the PWA appear here.")
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

    var body: some View {
        let total = prd.allTasks.count
        let done = prd.doneTaskCount
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                if pinned { Text("📌").font(DatawatchFonts.labelSmall).accessibilityLabel("Pinned") }
                Text(prd.displayTitle)
                    .font(DatawatchFonts.titleMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(2)
                Spacer(minLength: 8)
                PrdStatusChip(status: prd.status)
            }
            Text(metaLine)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .lineLimit(1)
            if total > 0 {
                ProgressView(value: Double(done), total: Double(total))
                    .tint(PrdStatusStyle.color(prd.status))
                    .accessibilityLabel("\(done) of \(total) tasks complete")
            }
        }
        .padding(.vertical, 6)
    }

    private var metaLine: String {
        var parts: [String] = []
        if let t = prd.type, !t.isEmpty { parts.append(t) }
        if let b = prd.backend, !b.isEmpty {
            parts.append((prd.model?.isEmpty == false) ? "\(b)/\(prd.model!)" : b)
        }
        parts.append("\(prd.stories.count) stories · \(prd.doneTaskCount)/\(prd.allTasks.count) tasks")
        return parts.joined(separator: "  ·  ")
    }
}
