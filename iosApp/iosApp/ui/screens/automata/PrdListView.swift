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

    var body: some View {
        Group {
            if vm.isLoading && vm.prds.isEmpty {
                LoadingIndicator(message: "Loading PRDs…")
            } else if let err = vm.error, vm.prds.isEmpty {
                ErrorCard(message: err) { vm.start(profile: profile) }
            } else if vm.prds.isEmpty {
                emptyView
            } else {
                list
            }
        }
        .onAppear { vm.start(profile: profile) }
        .onDisappear { vm.stop() }
        .onChange(of: profile.id) { _ in vm.start(profile: profile) }
    }

    private var list: some View {
        List {
            ForEach(vm.prds, id: \.id) { prd in
                NavigationLink {
                    PrdDetailView(profile: profile, initial: prd)
                } label: {
                    PrdRow(prd: prd)
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

    var body: some View {
        let total = prd.allTasks.count
        let done = prd.doneTaskCount
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
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
