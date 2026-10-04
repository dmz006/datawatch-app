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

    /// LLMs + Compute Nodes use the full PWA add/edit forms instead of the generic add sheet.
    private var hasForm: Bool { kind == "llms" || kind == "compute_nodes" }

    var body: some View {
        List {
            if !cardActions.isEmpty {
                Section { cardActionButtons }
            }
            if let message {
                Section {
                    Text(message)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
            rowsSection
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
                if !addFields.isEmpty || hasForm {
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
        } else {
            ComputeNodeFormSheet(profile: profile, editName: name) { load() }
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
                    Text("Loading…").foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                .listRowBackground(DatawatchColors.surface)
            } else if rows.isEmpty {
                Text("Nothing here yet.")
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
                        if hasForm {
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

    private func runAction(_ row: IosSettingsRow, _ index: Int) {
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
        if hasForm {
            formEdit = SettingsFormEditItem(id: row.id)
            return
        }
        guard !row.detail.isEmpty else { return }
        detail = SettingsDetailItem(title: row.title, text: row.detail)
    }

    private func create(_ values: [String: String], done: @escaping (String?) -> Void) {
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

/// Add-entry form for list cards. `onSave(values, done)` — done(nil) dismisses.
struct SettingsAddEntrySheet: View {
    let fields: [SettingsAddField]
    let onSave: ([String: String], @escaping (String?) -> Void) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var values: [String: String] = [:]
    @State private var saving = false
    @State private var error: String?

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
            .navigationTitle("Add")
            .navigationBarTitleDisplayMode(.inline)
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
            if f.secure {
                SecureField(prompt, text: binding)
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
