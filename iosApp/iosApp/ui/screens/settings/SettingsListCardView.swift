import SwiftUI
import DatawatchShared

/// Generic registry/list card (LLMs, compute nodes, secrets, plugins, skill
/// registries, …) driven by `IosSettingsLists`. Rows support the PWA's
/// enable switch, delete (with confirmation), per-row actions and a JSON
/// detail sheet (credential keys redacted server-side of the bridge).
struct SettingsListCardView: View {
    let profile: ServerProfile
    let kind: String
    let addFields: [SettingsAddField]
    let cardActions: [String]

    @State private var rows: [IosSettingsRow] = []
    @State private var isLoading = true
    @State private var error: String?
    @State private var message: String?
    @State private var pendingDelete: IosSettingsRow?
    @State private var detail: SettingsDetailItem?
    @State private var showAdd = false
    @State private var busy = false
    @State private var formEdit: SettingsFormEditItem?
    /// Session template "Use" → New Session prefilled from the template.
    @State private var templateUse: TemplateUseItem?
    /// Skill registry "Browse" → available skills with sync / unsync.
    @State private var browseRegistry: SettingsFormEditItem?
    /// LLM "In use…" → paged sessions routed through that LLM.
    @State private var llmInUse: SettingsFormEditItem?
    /// Compute node 📡 → live monitoring detail (PWA computeShowDetail).
    @State private var computeDetail: SettingsFormEditItem?
    /// Evals card "Recent Runs" (PWA _renderEvalsPanel).
    @State private var evalRuns: [IosEvalRun] = []

    /// LLMs + Compute Nodes use the full PWA add/edit forms instead of the generic add sheet.
    private var hasForm: Bool { kind == "llms" || kind == "compute_nodes" }
    /// Field-form edit via IosSettingsCrud (templates, fed peers, providers, …).
    private var crudEdit: Bool { IosSettingsCrud.shared.canEdit(kind: kind) }
    /// Project / cluster profiles: JSON editor (PWA form ↔ YAML escape hatch).
    private var jsonEdit: Bool { IosSettingsCrud.shared.isJsonEdited(kind: kind) }
    private var canEditRows: Bool { hasForm || crudEdit || jsonEdit }

    var body: some View {
        List {
            if !cardActions.isEmpty {
                Section { cardActionButtons }
            }
            SettingsListExtras(profile: profile, kind: kind, onChanged: { load() })
            if let message {
                Section {
                    Text(message)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
            rowsSection
            if kind == "evals" {
                EvalRunsSection(runs: evalRuns)
            }
            if let err = error {
                Section {
                    Text(err)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .task { load() }
        .refreshable { load() }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                if !addFields.isEmpty || hasForm || jsonEdit {
                    Button { showAdd = true } label: {
                        Image(systemName: "plus").foregroundStyle(DatawatchColors.primary)
                    }
                    .accessibilityLabel("Add")
                }
            }
        }
        .sheet(isPresented: $showAdd) {
            addSheet
        }
        .sheet(item: $formEdit) { item in
            editSheet(item.id)
        }
        .sheet(item: $detail) { item in
            SettingsTextSheet(title: item.title, text: item.text)
        }
        .sheet(item: $templateUse) { item in
            NewSessionView(profile: profile, template: item.values)
        }
        .sheet(item: $browseRegistry) { item in
            SkillBrowseSheet(profile: profile, registry: item.id)
        }
        .sheet(item: $llmInUse) { item in
            LlmInUseSheet(profile: profile, name: item.id)
        }
        .sheet(item: $computeDetail) { item in
            ComputeNodeLiveDetailSheet(profile: profile, name: item.id)
        }
        .confirmationDialog(
            deleteTitle,
            isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let row = pendingDelete { delete(row) }
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        }
    }

    @ViewBuilder
    private var addSheet: some View {
        if kind == "llms" {
            LlmFormSheet(profile: profile, editName: nil) { load() }
        } else if kind == "compute_nodes" {
            ComputeNodeFormSheet(profile: profile, editName: nil) { load() }
        } else if jsonEdit {
            ProfileEditorSheet(profile: profile, kind: kind, name: nil) { load() }
        } else {
            SettingsAddEntrySheet(fields: addFields) { values, done in
                create(values, done: done)
            }
        }
    }

    @ViewBuilder
    private func editSheet(_ name: String) -> some View {
        if kind == "llms" {
            LlmFormSheet(profile: profile, editName: name) { load() }
        } else if kind == "compute_nodes" {
            ComputeNodeFormSheet(profile: profile, editName: name) { load() }
        } else if jsonEdit {
            ProfileEditorSheet(profile: profile, kind: kind, name: name) { load() }
        } else {
            SettingsCrudEditSheet(profile: profile, kind: kind, id: name, fields: addFields) { load() }
        }
    }

    /// D35a: PWA per-card empty-state copy (app.js), generic fallback otherwise.
    private var emptyCopy: String {
        switch kind {
        case "session_templates": return "No templates — add one with + or via YAML session.templates."
        case "device_aliases": return "No aliases — add one with + or via YAML device_aliases."
        case "remote_servers": return "No remote servers configured."
        case "fed_peers": return "No federation peers registered."
        case "web_search_providers": return "No search providers configured."
        case "secrets": return "No secrets stored."
        case "channel_routing": return "No channel routing rules configured"
        case "guardrail_library": return "No guardrails registered"
        case "guardrail_profiles", "cluster_profiles", "project_profiles": return "No profiles yet"
        default: return "Nothing here yet."
        }
    }

    private var deleteTitle: String {
        L("Delete") + " \(pendingDelete?.title ?? "")?"
    }

    @ViewBuilder
    private var rowsSection: some View {
        Section {
            if isLoading && rows.isEmpty {
                HStack(spacing: 8) {
                    ProgressView()
                    CardSkeleton()
                }
                .listRowBackground(DatawatchColors.surface)
            } else if rows.isEmpty {
                Text(L(emptyCopy))
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .listRowBackground(DatawatchColors.surface)
            } else {
                ForEach(rows, id: \.id) { row in
                    SettingsListRowView(
                        row: row,
                        onToggle: { on in setEnabled(row, on) },
                        onAction: { idx in runAction(row, idx) },
                        onOpen: { open(row) }
                    )
                    .listRowBackground(DatawatchColors.surface)
                    .contextMenu {
                        if canEditRows {
                            Button { formEdit = SettingsFormEditItem(id: row.id) } label: {
                                Label("Edit", systemImage: "pencil")
                            }
                            if !row.detail.isEmpty {
                                Button { detail = SettingsDetailItem(title: row.title, text: row.detail) } label: {
                                    Label("Details", systemImage: "doc.text")
                                }
                            }
                        }
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        if row.canDelete {
                            Button(role: .destructive) { pendingDelete = row } label: {
                                Label("Delete", systemImage: "trash")
                            }
                            .tint(DatawatchColors.error)
                        }
                    }
                }
            }
        }
    }

    private var cardActionButtons: some View {
        ForEach(Array(cardActions.enumerated()), id: \.offset) { pair in
            Button {
                runCardAction(pair.offset)
            } label: {
                HStack {
                    Text(L(pair.element)).foregroundStyle(DatawatchColors.primary)
                    Spacer()
                    if busy { ProgressView().controlSize(.small) }
                }
            }
            .disabled(busy)
            .listRowBackground(DatawatchColors.surface)
        }
    }

    // MARK: Actions

    private func load() {
        if kind == "evals" {
            IosSettingsH.shared.evalRuns(profile: profile, onSuccess: { list in
                DispatchQueue.main.async { evalRuns = list }
            }, onError: { _ in })
        }
        IosSettingsLists.shared.load(profile: profile, kind: kind, onSuccess: { list in
            DispatchQueue.main.async {
                rows = list
                isLoading = false
                error = nil
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                isLoading = false
                error = msg
            }
        })
    }

    private func setEnabled(_ row: IosSettingsRow, _ on: Bool) {
        IosSettingsLists.shared.setEnabled(profile: profile, kind: kind, id: row.id, enabled: on) { err in
            DispatchQueue.main.async {
                error = err
                load()
            }
        }
    }

    private func delete(_ row: IosSettingsRow) {
        pendingDelete = nil
        IosSettingsLists.shared.delete(profile: profile, kind: kind, id: row.id) { err in
            DispatchQueue.main.async {
                error = err
                load()
            }
        }
    }

    private func useTemplate(_ row: IosSettingsRow) {
        IosSettingsCrud.shared.formValues(profile: profile, kind: kind, id: row.id, onSuccess: { values in
            DispatchQueue.main.async { templateUse = TemplateUseItem(id: row.id, values: values) }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
    }

    private func runAction(_ row: IosSettingsRow, _ index: Int) {
        if kind == "session_templates" && index < row.actions.count && row.actions[index] == "Use" {
            useTemplate(row)
            return
        }
        if kind == "skill_registries" && index < row.actions.count && row.actions[index] == "Browse" {
            browseRegistry = SettingsFormEditItem(id: row.id)
            return
        }
        if kind == "llms" && index < row.actions.count && row.actions[index] == "In use…" {
            llmInUse = SettingsFormEditItem(id: row.id)
            return
        }
        if kind == "compute_nodes" && index < row.actions.count && row.actions[index].hasPrefix("📡") {
            computeDetail = SettingsFormEditItem(id: row.id)
            return
        }
        if kind == "discussions" && index < row.actions.count && row.actions[index] == "Recall" {
            recallDiscussion(row.id)
            return
        }
        message = L("Working…")
        IosSettingsLists.shared.action(profile: profile, kind: kind, id: row.id, index: Int32(index), onSuccess: { msg in
            DispatchQueue.main.async {
                message = "\(row.title): \(msg)"
                load()
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                message = nil
                error = msg
            }
        })
    }

    /// PWA discussionViewEntries: GET /api/memory/discussion/{id} → "id — N entries" modal.
    private func recallDiscussion(_ id: String) {
        IosYamlRecall.shared.discussionRecall(profile: profile, id: id, onSuccess: { entries in
            let title: String = id + " — " + String(entries.count) + " " + L("entries")
            let body: String = entries.isEmpty ? L("(no entries)") : entries.joined(separator: "\n\n")
            DispatchQueue.main.async { detail = SettingsDetailItem(title: title, text: body) }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
    }

    private func runCardAction(_ index: Int) {
        busy = true
        IosSettingsLists.shared.cardAction(profile: profile, kind: kind, index: Int32(index), onSuccess: { msg in
            DispatchQueue.main.async {
                busy = false
                message = msg
                load()
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                busy = false
                error = msg
            }
        })
    }

    private func open(_ row: IosSettingsRow) {
        if canEditRows {
            formEdit = SettingsFormEditItem(id: row.id)
            return
        }
        guard !row.detail.isEmpty else { return }
        detail = SettingsDetailItem(title: row.title, text: row.detail)
    }

    private func create(_ values: [String: String], done: @escaping (String?) -> Void) {
        if IosSettingsCrud.shared.handlesCreate(kind: kind) {
            IosSettingsCrud.shared.save(profile: profile, kind: kind, originalId: nil, values: values) { err in
                DispatchQueue.main.async {
                    done(err)
                    if err == nil { load() }
                }
            }
            return
        }
        IosSettingsLists.shared.create(profile: profile, kind: kind, values: values) { err in
            DispatchQueue.main.async {
                done(err)
                if err == nil { load() }
            }
        }
    }
}

struct SettingsDetailItem: Identifiable {
    let id = UUID()
    let title: String
    let text: String
}

/// One generic row: title, subtitle, badges, optional switch / action menu.
private struct SettingsListRowView: View {
    let row: IosSettingsRow
    let onToggle: (Bool) -> Void
    let onAction: (Int) -> Void
    let onOpen: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            Button(action: onOpen) { labels }
                .buttonStyle(.plain)
            Spacer(minLength: 4)
            if !row.actions.isEmpty {
                Menu {
                    ForEach(Array(row.actions.enumerated()), id: \.offset) { pair in
                        Button(L(pair.element)) { onAction(pair.offset) }
                    }
                } label: {
                    Image(systemName: "ellipsis.circle").foregroundStyle(DatawatchColors.primary)
                }
                .accessibilityLabel("Actions")
            }
            if row.hasToggle {
                Toggle("", isOn: Binding(get: { row.enabled }, set: { onToggle($0) }))
                    .labelsHidden()
                    .tint(DatawatchColors.primary)
            }
        }
        .opacity(row.hasToggle && !row.enabled ? 0.6 : 1.0)
    }

    private var labels: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(verbatim: row.title)
                .font(DatawatchFonts.bodyLarge)
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(2)
            if !row.subtitle.isEmpty {
                Text(verbatim: row.subtitle)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(3)
            }
            if !row.badges.isEmpty {
                SettingsBadgeLine(badges: row.badges)
            }
        }
    }
}

struct SettingsBadgeLine: View {
    let badges: [String]
    var body: some View {
        HStack(spacing: 4) {
            ForEach(badges.prefix(5), id: \.self) { b in
                Text(verbatim: b)
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(DatawatchColors.surface2, in: Capsule())
            }
        }
    }
}

/// Read-only monospaced text sheet (row JSON detail, MCP channel info, …).
struct SettingsTextSheet: View {
    let title: String
    let text: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(verbatim: text)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
            }
            .background(DatawatchColors.background)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

/// Add/edit-entry form for list cards. `onSave(values, done)` — done(nil) dismisses.
/// Edit mode: `initial` pre-fills, `lockedKeys` are read-only (the entry's id),
/// secure fields stay blank ("leave blank to keep current").
struct SettingsAddEntrySheet: View {
    let fields: [SettingsAddField]
    var initial: [String: String] = [:]
    var lockedKeys: Set<String> = []
    var title: String = "Add"
    let onSave: ([String: String], @escaping (String?) -> Void) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var values: [String: String] = [:]
    @State private var saving = false
    @State private var error: String?
    @State private var seeded = false

    private var isEdit: Bool { !lockedKeys.isEmpty }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(fields) { f in
                        fieldInput(f)
                    }
                }
                if let error {
                    Section {
                        Text(error)
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.error)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle(L(title))
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                if !seeded {
                    seeded = true
                    values = initial
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if saving {
                        ProgressView()
                    } else {
                        Button("Save") { save() }
                            .disabled((values[fields.first?.key ?? ""] ?? "").trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func fieldInput(_ f: SettingsAddField) -> some View {
        let binding = Binding<String>(get: { values[f.key] ?? "" }, set: { values[f.key] = $0 })
        let prompt: String = f.placeholder.isEmpty ? L(f.label) : f.placeholder
        VStack(alignment: .leading, spacing: 4) {
            Text(L(f.label))
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if lockedKeys.contains(f.key) {
                Text(verbatim: values[f.key] ?? "")
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            } else if f.secure {
                SecureField(isEdit ? L("Leave blank to keep current") : prompt, text: binding)
            } else if f.multiline {
                TextEditor(text: binding)
                    .frame(minHeight: 120)
                    .font(DatawatchFonts.bodyMedium)
            } else {
                TextField(prompt, text: binding)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
        }
    }

    private func save() {
        saving = true
        error = nil
        onSave(values) { err in
            saving = false
            if let err {
                error = err
            } else {
                dismiss()
            }
        }
    }
}

/// Session template picked with "Use": its field values prefill New Session.
struct TemplateUseItem: Identifiable {
    let id: String
    let values: [String: String]
}
