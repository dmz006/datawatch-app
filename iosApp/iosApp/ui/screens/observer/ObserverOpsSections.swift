import SwiftUI
import UIKit
import DatawatchShared

// Observer cards below System Statistics (parity B24–B25): Memory Browser,
// Memory Maintenance (D89b: dry-run only on phones), Scheduled Events,
// Global Cooldown, Session Analytics, Audit Log, Knowledge Graph, Daemon Log.
// Each card loads when it is expanded (PWA loaders fire on view render).

private let obsFieldBackground = DatawatchColors.background

private struct ObsField: View {
    let placeholder: String
    @Binding var text: String
    var keyboard: UIKeyboardType = .default

    var body: some View {
        TextField(L(placeholder), text: $text)
            .font(DatawatchFonts.labelSmall)
            .keyboardType(keyboard)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .background(obsFieldBackground, in: RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.border, lineWidth: 1))
    }
}

/// UIActivityViewController wrapper for the memory export file.
private struct ObsShareSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: [url], applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

private struct ObsShareURL: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

// ── 7.2 Memory Browser ────────────────────────────────────────────────────

struct ObserverMemoryBrowser: View {
    let profile: ServerProfile
    @EnvironmentObject private var toaster: ObserverToastCenter
    @State private var query = ""
    @State private var role = ""
    @State private var sinceDays = 0
    @State private var rows: [IosMemoryRow]? = nil
    @State private var status: String? = nil
    @State private var stats: IosMemoryStats? = nil
    @State private var shareURL: ObsShareURL? = nil
    @State private var showAdd = false
    @State private var addText = ""
    @State private var addTags = ""

    private static let roles: [(String, String)] = [
        ("", "All roles"), ("manual", "Manual"), ("session", "Session"),
        ("learning", "Learning"), ("output_chunk", "Chunks"),
    ]
    private static let sinces: [(Int, String)] = [
        (0, "All time"), (7, "Last 7 days"), (30, "Last 30 days"), (90, "Last 90 days"),
    ]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            statsTiles
            controls
            results
        }
        .task(id: profile.id) {
            loadStats()
            list()
        }
        .sheet(item: $shareURL) { item in
            ObsShareSheet(url: item.url)
        }
        .alert("Add memory", isPresented: $showAdd) {
            TextField("Memory text", text: $addText)
            TextField("Tags (comma-separated)", text: $addTags)
            Button("Save") { remember() }
            Button("Cancel", role: .cancel) {}
        }
    }

    @ViewBuilder
    private var statsTiles: some View {
        if let s = stats {
            if s.enabled {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 6)], spacing: 6) {
                    ForEach(Array(s.tiles.enumerated()), id: \.offset) { _, t in
                        VStack(spacing: 2) {
                            Text(t.value)
                                .font(DatawatchFonts.titleMedium.monospacedDigit())
                                .foregroundStyle(DatawatchColors.onSurface)
                            Text(L(t.key))
                                .font(.caption2)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                        .background(DatawatchColors.background.opacity(0.5), in: RoundedRectangle(cornerRadius: 8))
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.border, lineWidth: 1))
                    }
                }
            } else {
                ObsMuted(text: L("Memory not enabled. Enable in Settings → General → Episodic Memory."))
            }
        }
    }

    private var controls: some View {
        VStack(alignment: .leading, spacing: 6) {
            ObsField(placeholder: "Search memories…", text: $query)
                .onSubmit { search() }
            ObsFlowLayout(spacing: 6) {
                Picker("Role", selection: $role) {
                    ForEach(Self.roles, id: \.0) { r in Text(L(r.1)).tag(r.0) }
                }
                .pickerStyle(.menu)
                .font(DatawatchFonts.labelSmall)
                Picker("Since", selection: $sinceDays) {
                    ForEach(Self.sinces, id: \.0) { s in Text(L(s.1)).tag(s.0) }
                }
                .pickerStyle(.menu)
                .font(DatawatchFonts.labelSmall)
                ObsButton(title: "Search") { search() }
                ObsButton(title: "List") { list() }
                ObsButton(title: "Export") { export() }
                Button {
                    addText = ""
                    addTags = ""
                    showAdd = true
                } label: {
                    Image(systemName: "plus.circle")
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Add memory")
            }
        }
    }

    @ViewBuilder
    private var results: some View {
        if let status {
            ObsMuted(text: status)
        } else if let rows {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    ForEach(rows, id: \.id) { m in
                        memoryRow(m)
                    }
                }
            }
            .frame(maxHeight: 400)
        }
    }

    private func memoryRow(_ m: IosMemoryRow) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(m.header)
                        .font(.caption2)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Text(m.content)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .lineLimit(4)
                }
                Spacer(minLength: 4)
                Button {
                    delete(m.id)
                } label: {
                    Image(systemName: "trash").font(.caption)
                }
                .buttonStyle(.borderless)
                .foregroundStyle(DatawatchColors.error)
                .accessibilityLabel("Delete")
            }
            .padding(.vertical, 6)
            Divider().overlay(DatawatchColors.border)
        }
    }

    private func loadStats() {
        IosObserver.shared.memoryStats(
            profile: profile,
            onSuccess: { s in DispatchQueue.main.async { stats = s } },
            onError: { _ in DispatchQueue.main.async { stats = nil } }
        )
    }

    private func list() {
        status = L("Loading…")
        IosObserver.shared.memoryList(
            profile: profile,
            role: role,
            sinceDays: Int32(sinceDays),
            onSuccess: { list in
                DispatchQueue.main.async {
                    rows = list
                    status = list.isEmpty ? L("No memories stored.") : nil
                }
            },
            onError: { msg in DispatchQueue.main.async { status = L(msg) } }
        )
    }

    private func search() {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { list(); return }
        status = L("Searching…")
        IosObserver.shared.memorySearch(
            profile: profile,
            query: q,
            onSuccess: { list in
                DispatchQueue.main.async {
                    rows = list
                    status = list.isEmpty ? L("No matches found.") : nil
                }
            },
            onError: { msg in DispatchQueue.main.async { status = L(msg) } }
        )
    }

    private func delete(_ id: Int64) {
        IosObserver.shared.memoryDelete(profile: profile, id: id) { err in
            Task { @MainActor in
                toaster.show(err ?? "\(L("Deleted memory")) #\(id)")
                if err == nil {
                    list()
                    loadStats()
                }
            }
        }
    }

    private func export() {
        IosObserver.shared.memoryExport(
            profile: profile,
            onSuccess: { path in
                DispatchQueue.main.async { shareURL = ObsShareURL(url: URL(fileURLWithPath: path)) }
            },
            onError: { msg in Task { @MainActor in toaster.show(msg) } }
        )
    }

    private func remember() {
        let text = addText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        IosObserver.shared.memoryRemember(profile: profile, text: text, tags: addTags) { err in
            Task { @MainActor in
                toaster.show(err ?? L("Memory saved"))
                if err == nil {
                    list()
                    loadStats()
                }
            }
        }
    }
}

// ── 7.3 Memory Maintenance (D89b — dry-run only) ──────────────────────────

struct ObserverMemoryMaintenance: View {
    let profile: ServerProfile
    @State private var days = "90"
    @State private var sweepResult = ""
    @State private var spellText = ""
    @State private var spellResult = ""
    @State private var extractText = ""
    @State private var extractResult = ""
    @State private var schemaResult = ""

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 260), spacing: 10, alignment: .top)], alignment: .leading, spacing: 12) {
            sweepTile
            spellTile
            extractTile
            schemaTile
        }
    }

    private func tileHeader(_ title: String, _ tool: String, _ blurb: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Text(L(title)).font(DatawatchFonts.labelSmall.weight(.semibold))
                Text(tool).font(.caption2).foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .foregroundStyle(DatawatchColors.onSurface)
            Text(L(blurb))
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }

    private func result(_ text: String) -> some View {
        Text(text)
            .font(DatawatchFonts.terminalSmall)
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
            .textSelection(.enabled)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var sweepTile: some View {
        VStack(alignment: .leading, spacing: 6) {
            tileHeader(
                "Similarity-stale eviction", "(sweeper.py)",
                "Drops rows that never surface in any search and are older than the cutoff. Manual + pinned rows exempt."
            )
            HStack(spacing: 6) {
                ObsField(placeholder: "days", text: $days, keyboard: .numberPad)
                    .frame(width: 80)
                ObsButton(title: "Dry-run") {
                    sweepResult = L("Running…")
                    let n = Int32(days.trimmingCharacters(in: .whitespaces)) ?? 90
                    IosObserver.shared.memorySweepDryRun(profile: profile, days: n) { r in
                        DispatchQueue.main.async { sweepResult = r }
                    }
                }
            }
            Text("Apply (destructive) is available in the web UI only.")
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if !sweepResult.isEmpty { result(sweepResult) }
        }
    }

    private var spellTile: some View {
        VStack(alignment: .leading, spacing: 6) {
            tileHeader(
                "Spellcheck", "(spellcheck.py)",
                "Conservative Levenshtein-based suggestions on text. Never rewrites — preview only."
            )
            TextField(L("Paste text to check…"), text: $spellText, axis: .vertical)
                .lineLimit(2...4)
                .font(DatawatchFonts.labelSmall)
                .padding(6)
                .background(obsFieldBackground, in: RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.border, lineWidth: 1))
            ObsButton(title: "Run spellcheck") {
                let text = spellText.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !text.isEmpty else { spellResult = L("Enter text first"); return }
                spellResult = L("Running…")
                IosObserver.shared.memorySpellcheck(profile: profile, text: text) { r in
                    DispatchQueue.main.async { spellResult = r }
                }
            }
            if !spellResult.isEmpty { result(spellResult) }
        }
    }

    private var extractTile: some View {
        VStack(alignment: .leading, spacing: 6) {
            tileHeader(
                "Extract facts", "(general_extractor.py)",
                "Heuristic schema-free SVO triple extraction. Useful for KG pre-population."
            )
            TextField(L("Paste text to extract triples from…"), text: $extractText, axis: .vertical)
                .lineLimit(2...4)
                .font(DatawatchFonts.labelSmall)
                .padding(6)
                .background(obsFieldBackground, in: RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.border, lineWidth: 1))
            ObsButton(title: "Extract triples") {
                let text = extractText.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !text.isEmpty else { extractResult = L("Enter text first"); return }
                extractResult = L("Running…")
                IosObserver.shared.memoryExtractFacts(profile: profile, text: text) { r in
                    DispatchQueue.main.async { extractResult = r }
                }
            }
            if !extractResult.isEmpty { result(extractResult) }
        }
    }

    private var schemaTile: some View {
        VStack(alignment: .leading, spacing: 6) {
            tileHeader(
                "Schema version", "(migrate.py)",
                "Highest schema_version row applied to the active memory backend."
            )
            ObsButton(title: "Check schema") {
                schemaResult = L("Loading…")
                IosObserver.shared.memorySchemaVersion(profile: profile) { r in
                    DispatchQueue.main.async { schemaResult = r }
                }
            }
            if !schemaResult.isEmpty { result(schemaResult) }
        }
    }
}

// ── 7.4 Scheduled Events ──────────────────────────────────────────────────

struct ObserverSchedulesCard: View {
    let profile: ServerProfile
    @EnvironmentObject private var toaster: ObserverToastCenter
    @State private var rows: [IosScheduleRow]? = nil
    @State private var error: String? = nil
    @State private var page = 0
    @State private var selected: Set<String> = []
    @State private var confirmBulk = false
    @State private var editing: IosScheduleRow? = nil
    @State private var editCommand = ""
    @State private var editRunAt = ""
    private static let perPage = 10

    private var totalPages: Int {
        let n = rows?.count ?? 0
        return max(1, (n + Self.perPage - 1) / Self.perPage)
    }

    private var pageRows: [IosScheduleRow] {
        guard let rows else { return [] }
        let start = min(page * Self.perPage, rows.count)
        let end = min(start + Self.perPage, rows.count)
        return Array(rows[start..<end])
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if let error {
                Text(L(error)).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
            } else if let rows {
                if rows.isEmpty {
                    ObsMuted(text: L("No scheduled events."))
                } else {
                    if pageRows.count > 1 { bulkBar }
                    ForEach(pageRows, id: \.id) { sc in scheduleRow(sc) }
                    if totalPages > 1 { pager }
                }
            } else {
                CardSkeleton()
            }
        }
        .task(id: profile.id) { load() }
        .alert("Delete \(selected.count) scheduled event(s)?", isPresented: $confirmBulk) {
            Button("Delete", role: .destructive) { delete(Array(selected)) }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Edit schedule", isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) {
            TextField("Command", text: $editCommand)
            TextField("New time (ISO, or empty to keep)", text: $editRunAt)
            Button("Save") { saveEdit() }
            Button("Cancel", role: .cancel) { editing = nil }
        }
    }

    private var bulkBar: some View {
        let allOnPage: Bool = pageRows.allSatisfy { selected.contains($0.id) }
        return HStack {
            Button {
                if allOnPage {
                    pageRows.forEach { selected.remove($0.id) }
                } else {
                    pageRows.forEach { selected.insert($0.id) }
                }
            } label: {
                Label("Select all", systemImage: allOnPage ? "checkmark.square" : "square")
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .buttonStyle(.borderless)
            Spacer()
            ObsButton(title: "Delete selected") {
                if selected.isEmpty {
                    toaster.show(L("No items selected"))
                } else {
                    confirmBulk = true
                }
            }
        }
    }

    private func scheduleRow(_ sc: IosScheduleRow) -> some View {
        HStack(spacing: 6) {
            if pageRows.count > 1 {
                Button {
                    if selected.contains(sc.id) { selected.remove(sc.id) } else { selected.insert(sc.id) }
                } label: {
                    Image(systemName: selected.contains(sc.id) ? "checkmark.square" : "square")
                        .font(.caption)
                }
                .buttonStyle(.borderless)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 4) {
                    Text(sc.label)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .lineLimit(1)
                    if !sc.cron.isEmpty {
                        Text("cron")
                            .font(.caption2)
                            .padding(.horizontal, 4)
                            .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 2))
                            .accessibilityLabel(Text("Cron: \(sc.cron)"))
                    }
                }
                HStack(spacing: 6) {
                    Text(sc.whenText)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Text(sc.state.uppercased())
                        .fontWeight(.semibold)
                        .foregroundStyle(ObsTone.color(sc.stateTone))
                }
                .font(.caption2)
            }
            Spacer(minLength: 4)
            if sc.pending {
                Button {
                    editCommand = sc.command
                    editRunAt = sc.runAtIso
                    editing = sc
                } label: {
                    Image(systemName: "pencil").font(.caption)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Edit")
            }
            Button {
                delete([sc.id])
            } label: {
                Image(systemName: "trash").font(.caption)
            }
            .buttonStyle(.borderless)
            .foregroundStyle(DatawatchColors.error)
            .accessibilityLabel("Delete")
        }
        .padding(.vertical, 3)
    }

    private var pager: some View {
        HStack(spacing: 10) {
            Spacer()
            if page > 0 {
                Button("◀ Prev") { page -= 1 }.font(.caption)
            }
            Text("Page \(page + 1)/\(totalPages)")
                .font(.caption)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if page < totalPages - 1 {
                Button("Next ▶") { page += 1 }.font(.caption)
            }
            Spacer()
        }
        .buttonStyle(.borderless)
    }

    private func load() {
        IosObserver.shared.listSchedules(
            profile: profile,
            onSuccess: { list in
                DispatchQueue.main.async {
                    rows = list
                    error = nil
                    let pages = max(1, (list.count + Self.perPage - 1) / Self.perPage)
                    if page >= pages { page = pages - 1 }
                    selected = selected.filter { id in list.contains(where: { $0.id == id }) }
                }
            },
            onError: { msg in DispatchQueue.main.async { error = msg } }
        )
    }

    private func delete(_ ids: [String]) {
        IosObserver.shared.deleteSchedules(profile: profile, ids: ids) { err in
            Task { @MainActor in
                if let err {
                    toaster.show(err)
                } else {
                    toaster.show(ids.count > 1 ? "\(L("Deleted")) \(ids.count) \(L("events"))" : L("Deleted"))
                    ids.forEach { selected.remove($0) }
                }
                load()
            }
        }
    }

    private func saveEdit() {
        guard let sc = editing else { return }
        let command = editCommand.isEmpty ? sc.command : editCommand
        IosObserver.shared.updateSchedule(profile: profile, id: sc.id, command: command, runAt: editRunAt) { err in
            Task { @MainActor in
                toaster.show(err ?? L("Schedule updated"))
                load()
            }
        }
        editing = nil
    }
}

// ── 7.5 Global Cooldown ───────────────────────────────────────────────────

struct ObserverCooldownCard: View {
    let profile: ServerProfile
    @EnvironmentObject private var toaster: ObserverToastCenter
    @State private var status: IosCooldown? = nil
    @State private var error: String? = nil
    @State private var reason = ""
    private static let presets: [Int] = [15, 30, 60, 240, 480, 1440]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let s = status {
                Text(s.text)
                    .font(DatawatchFonts.labelSmall.weight(.semibold))
                    .foregroundStyle(ObsTone.color(s.tone))
            } else if let error {
                Text(L(error)).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
            } else {
                CardSkeleton()
            }
            ObsFlowLayout(spacing: 6) {
                Text("Set for:")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                ForEach(Self.presets, id: \.self) { m in
                    ObsButton(title: label(m)) { set(m) }
                }
            }
            HStack(spacing: 6) {
                ObsField(placeholder: "Reason (optional)", text: $reason)
                if status?.active == true {
                    ObsButton(title: "Clear", destructive: true) { clear() }
                }
            }
        }
        .task(id: profile.id) { load() }
    }

    private func label(_ m: Int) -> String { m >= 60 ? "\(m / 60)h" : "\(m)m" }

    private func load() {
        IosObserver.shared.cooldownStatus(
            profile: profile,
            onSuccess: { s in DispatchQueue.main.async { status = s; error = nil } },
            onError: { msg in DispatchQueue.main.async { error = msg } }
        )
    }

    private func set(_ minutes: Int) {
        IosObserver.shared.setCooldown(profile: profile, minutes: Int32(minutes), reason: reason) { err in
            Task { @MainActor in
                toaster.show(err.map { L($0) } ?? "\(L("Cooldown set for")) \(label(minutes))")
                load()
            }
        }
    }

    private func clear() {
        IosObserver.shared.clearCooldown(profile: profile) { err in
            Task { @MainActor in
                toaster.show(err.map { L($0) } ?? L("Cooldown cleared"))
                load()
            }
        }
    }
}

// ── 7.6 Session Analytics ─────────────────────────────────────────────────

struct ObserverAnalyticsCard: View {
    let profile: ServerProfile
    @State private var range = 7
    @State private var data: IosAnalytics? = nil
    @State private var error: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Text("Range:").font(DatawatchFonts.labelSmall)
                Picker("Range", selection: $range) {
                    ForEach([7, 14, 30, 90], id: \.self) { d in Text("\(d)d").tag(d) }
                }
                .pickerStyle(.menu)
                if let d = data, !d.successRate.isEmpty {
                    Text(d.successRate).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                Spacer(minLength: 0)
            }
            .foregroundStyle(DatawatchColors.onSurface)
            if let error {
                Text(L(error)).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
            } else if let d = data {
                if d.rows.isEmpty {
                    ObsMuted(text: L("No sessions in range."))
                } else {
                    table(d.rows)
                }
            } else {
                CardSkeleton()
            }
        }
        .task(id: "\(profile.id)-\(range)") { load() }
    }

    private func table(_ rows: [IosAnalyticsRow]) -> some View {
        VStack(spacing: 0) {
            HStack {
                Text("Date").frame(maxWidth: .infinity, alignment: .leading)
                Text("Total").frame(width: 40, alignment: .trailing)
                Text("OK").frame(width: 36, alignment: .trailing)
                Text("Err").frame(width: 36, alignment: .trailing)
                Text("Bar").frame(width: 90, alignment: .leading).padding(.leading, 8)
            }
            .font(.caption2)
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
            .padding(.bottom, 2)
            ForEach(Array(rows.enumerated()), id: \.offset) { _, r in
                VStack(spacing: 0) {
                    Divider().overlay(DatawatchColors.border)
                    analyticsRow(r)
                }
            }
        }
    }

    private func analyticsRow(_ r: IosAnalyticsRow) -> some View {
        let barWidth: CGFloat = CGFloat(90.0 * min(1.0, max(0.0, r.fraction)))
        return HStack {
            Text(r.date).frame(maxWidth: .infinity, alignment: .leading)
            Text("\(r.total)").frame(width: 40, alignment: .trailing)
            Text("\(r.ok)").foregroundStyle(DatawatchColors.success).frame(width: 36, alignment: .trailing)
            Text("\(r.err)").foregroundStyle(DatawatchColors.error).frame(width: 36, alignment: .trailing)
            ZStack(alignment: .leading) {
                Color.clear.frame(width: 90, height: 8)
                RoundedRectangle(cornerRadius: 2)
                    .fill(ObsTone.color(r.tone))
                    .frame(width: barWidth, height: 8)
            }
            .padding(.leading, 8)
        }
        .font(DatawatchFonts.labelSmall.monospacedDigit())
        .foregroundStyle(DatawatchColors.onSurface)
        .padding(.vertical, 3)
    }

    private func load() {
        let r = range
        IosObserver.shared.analytics(
            profile: profile,
            rangeDays: Int32(r),
            onSuccess: { d in DispatchQueue.main.async { data = d; error = nil } },
            onError: { msg in DispatchQueue.main.async { error = msg } }
        )
    }
}

// ── 7.7 Audit Log ─────────────────────────────────────────────────────────

struct ObserverAuditCard: View {
    let profile: ServerProfile
    @State private var actor = ""
    @State private var action = ""
    @State private var limit = 5
    @State private var rows: [IosAuditRow]? = nil
    @State private var error: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ObsFlowLayout(spacing: 6) {
                ObsField(placeholder: "Actor filter", text: $actor).frame(width: 130)
                ObsField(placeholder: "Action filter", text: $action).frame(width: 130)
                Picker("Limit", selection: $limit) {
                    ForEach([5, 20, 50, 100], id: \.self) { n in Text("\(n)").tag(n) }
                }
                .pickerStyle(.menu)
                ObsButton(title: "Load") { load() }
            }
            if let error {
                Text(L(error)).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
            } else if let rows {
                if rows.isEmpty {
                    ObsMuted(text: L("No audit entries in range."))
                }
                ForEach(Array(rows.enumerated()), id: \.offset) { _, e in
                    auditRow(e)
                }
            } else {
                CardSkeleton()
            }
        }
        .task(id: profile.id) { load() }
    }

    private func auditRow(_ e: IosAuditRow) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Divider().overlay(DatawatchColors.border)
            ObsFlowLayout(spacing: 6) {
                Text(e.ts).foregroundStyle(DatawatchColors.onSurfaceMuted)
                Text(e.action).fontWeight(.semibold).foregroundStyle(DatawatchColors.onSurface)
                Text(e.actor).foregroundStyle(DatawatchColors.onSurfaceMuted)
                if !e.session.isEmpty {
                    Text(e.session)
                        .font(.system(.caption2, design: .monospaced))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .font(.caption2)
            if !e.details.isEmpty {
                Text(e.details)
                    .font(.system(.caption2, design: .monospaced))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .textSelection(.enabled)
            }
        }
        .padding(.vertical, 2)
    }

    private func load() {
        rows = nil
        error = nil
        IosObserver.shared.audit(
            profile: profile,
            actor: actor,
            action: action,
            limit: Int32(limit),
            onSuccess: { list in DispatchQueue.main.async { rows = list } },
            onError: { msg in DispatchQueue.main.async { error = msg } }
        )
    }
}

// ── 7.8 Knowledge Graph ───────────────────────────────────────────────────

struct ObserverKnowledgeGraphCard: View {
    let profile: ServerProfile
    @EnvironmentObject private var toaster: ObserverToastCenter
    @State private var entity = ""
    @State private var subject = ""
    @State private var predicate = ""
    @State private var object = ""
    @State private var triples: [IosKgTriple]? = nil
    @State private var status: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                ObsField(placeholder: "Entity to query…", text: $entity)
                    .onSubmit { query() }
                Button("Query") { query() }
                    .font(DatawatchFonts.labelSmall)
                    .buttonStyle(.borderedProminent)
                    .tint(DatawatchColors.primary)
            }
            ObsFlowLayout(spacing: 6) {
                ObsField(placeholder: "Subject", text: $subject).frame(width: 100)
                ObsField(placeholder: "Predicate", text: $predicate).frame(width: 100)
                ObsField(placeholder: "Object", text: $object).frame(width: 100)
                ObsButton(title: "Add triple") { add() }
            }
            results
        }
    }

    @ViewBuilder
    private var results: some View {
        if let status {
            Text(status).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
        } else if let triples {
            Text("\(triples.count) triples")
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            ForEach(Array(triples.enumerated()), id: \.offset) { _, t in
                VStack(alignment: .leading, spacing: 0) {
                    Divider().overlay(DatawatchColors.border)
                    HStack(spacing: 4) {
                        Text(t.subject).foregroundStyle(DatawatchColors.primary)
                        Text(t.predicate).foregroundStyle(DatawatchColors.onSurfaceMuted)
                        Text(t.obj).foregroundStyle(DatawatchColors.onSurface)
                        if !t.validFrom.isEmpty {
                            Text(t.validFrom).font(.caption2).foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.7))
                        }
                    }
                    .font(DatawatchFonts.terminalSmall)
                    .padding(.vertical, 3)
                }
            }
        }
    }

    private func query() {
        let e = entity.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !e.isEmpty else { return }
        status = L("Querying…")
        IosObserver.shared.kgQuery(
            profile: profile,
            entity: e,
            onSuccess: { list in
                DispatchQueue.main.async {
                    triples = list
                    status = list.isEmpty ? L("No triples found for this entity.") : nil
                }
            },
            onError: { msg in DispatchQueue.main.async { status = msg } }
        )
    }

    private func add() {
        let s = subject.trimmingCharacters(in: .whitespaces)
        let p = predicate.trimmingCharacters(in: .whitespaces)
        let o = object.trimmingCharacters(in: .whitespaces)
        guard !s.isEmpty, !p.isEmpty, !o.isEmpty else {
            toaster.show(L("Subject, predicate, object all required"))
            return
        }
        IosObserver.shared.kgAdd(profile: profile, subject: s, predicate: p, obj: o) { err in
            Task { @MainActor in
                toaster.show(err ?? L("Triple added"))
                if err == nil { query() }
            }
        }
    }
}

// ── 7.9 Daemon Log ────────────────────────────────────────────────────────

struct ObserverDaemonLogCard: View {
    let profile: ServerProfile
    @State private var offset: Int = 0
    @State private var page: IosLogPage? = nil
    @State private var error: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 1) {
                    if let page {
                        ForEach(Array(page.lines.enumerated()), id: \.offset) { _, line in
                            Text(line.text)
                                .foregroundStyle(ObsTone.color(line.tone))
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    } else if let error {
                        Text(L(error))
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    } else {
                        CardSkeleton()
                    }
                }
                .font(.system(.caption2, design: .monospaced))
                .textSelection(.enabled)
                .padding(6)
            }
            .frame(maxHeight: 300)
            .background(DatawatchColors.background, in: RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.border, lineWidth: 1))
            HStack(spacing: 8) {
                ObsButton(title: "Newest") { offset = 0 }
                ObsButton(title: "Older") { offset += 50 }
                if let page {
                    Text(page.info)
                        .font(.caption2)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                Spacer(minLength: 0)
            }
        }
        // Auto-refresh every 10 s at the current offset (PWA setInterval).
        .task(id: "\(profile.id)-\(offset)") {
            while !Task.isCancelled {
                load()
                try? await Task.sleep(nanoseconds: 10_000_000_000)
            }
        }
    }

    private func load() {
        let off = offset
        IosObserver.shared.daemonLog(
            profile: profile,
            offset: Int32(off),
            onSuccess: { p in DispatchQueue.main.async { page = p; error = nil } },
            onError: { msg in DispatchQueue.main.async { error = msg } }
        )
    }
}
