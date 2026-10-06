import SwiftUI
import DatawatchShared

/// Saved commands editor (parity B30; PWA loadSavedCommands / createSavedCmd /
/// saveCmdEdit). Rows: name + command; ✎ edits (rename via PUT old_name),
/// swipe deletes; Add form at the bottom.
struct SavedCommandsView: View {
    let profile: ServerProfile

    @State private var commands: [SavedCommand]? = nil
    @State private var loadError: String? = nil
    @State private var actionError: String? = nil
    @State private var newName = ""
    @State private var newCommand = ""
    @State private var editing: SavedCommand? = nil
    @State private var editName = ""
    @State private var editCommand = ""

    var body: some View {
        List {
            Section("Saved commands") {
                if let commands {
                    if commands.isEmpty {
                        Text("No saved commands.").font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    ForEach(commands, id: \.name) { cmd in
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(cmd.name).font(DatawatchFonts.bodyMedium.weight(.semibold)).foregroundStyle(DatawatchColors.onSurface)
                                Text(cmd.command).font(DatawatchFonts.terminalSmall).foregroundStyle(DatawatchColors.onSurfaceMuted).lineLimit(2)
                            }
                            Spacer(minLength: 4)
                            Button("✎") { editName = cmd.name; editCommand = cmd.command; editing = cmd }
                                .buttonStyle(.borderless)
                                .accessibilityLabel("Edit \(cmd.name)")
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                IosRulesEditors.shared.deleteCommand(profile: profile, name: cmd.name) { err in
                                    DispatchQueue.main.async { actionError = err; Task { await reload() } }
                                }
                            } label: { Label("Delete", systemImage: "trash") }
                        }
                    }
                } else if let loadError {
                    Text(loadError).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error)
                } else {
                    CardSkeleton()
                }
            }
            .listRowBackground(DatawatchColors.surface)

            Section("Add command") {
                TextField("Name (e.g. approve)", text: $newName)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                TextField("Command", text: $newCommand)
                    .font(DatawatchFonts.terminalSmall)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                Button("Save command") {
                    IosRulesEditors.shared.saveCommand(profile: profile, name: newName, command: newCommand) { err in
                        DispatchQueue.main.async {
                            actionError = err
                            if err == nil { newName = ""; newCommand = "" }
                            Task { await reload() }
                        }
                    }
                }
                .disabled(newName.trimmingCharacters(in: .whitespaces).isEmpty || newCommand.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .listRowBackground(DatawatchColors.surface)

            if let actionError {
                Section { Text(actionError).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error) }
            }
        }
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .navigationTitle("Saved Commands")
        .navigationBarTitleDisplayMode(.inline)
        .task { await reload() }
        .refreshable { await reload() }
        .alert("Edit command", isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) {
            TextField("Name", text: $editName)
            TextField("Command", text: $editCommand)
            Button("Save") {
                guard let old = editing else { return }
                IosRulesEditors.shared.updateCommand(profile: profile, oldName: old.name, name: editName, command: editCommand) { err in
                    DispatchQueue.main.async { actionError = err; Task { await reload() } }
                }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func reload() async {
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            IosRulesEditors.shared.commands(
                profile: profile,
                onSuccess: { list in DispatchQueue.main.async { commands = list; loadError = nil; cont.resume() } },
                onError: { msg in DispatchQueue.main.async { if commands == nil { loadError = msg }; cont.resume() } }
            )
        }
    }
}

/// Output / detection filters (parity B30 / B5; PWA loadFilters /
/// createFilter / saveFilterEdit / toggleFilter). Rows: on/off, pattern,
/// action → value; ⏸/▶ toggle, ✎ edit, swipe delete; Add form.
struct FiltersView: View {
    let profile: ServerProfile

    static let actions = ["alert", "send_input", "detect_prompt", "schedule"]

    @State private var filters: [IosFilterRow]? = nil
    @State private var loadError: String? = nil
    @State private var actionError: String? = nil
    @State private var pattern = ""
    @State private var action = "alert"
    @State private var value = ""
    @State private var editing: IosFilterRow? = nil

    var body: some View {
        List {
            Section("Filters") {
                if let filters {
                    if filters.isEmpty {
                        Text("No filters. Run `datawatch seed` to populate defaults.")
                            .font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    ForEach(filters, id: \.id) { f in row(f) }
                } else if let loadError {
                    Text(loadError).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error)
                } else {
                    CardSkeleton()
                }
            }
            .listRowBackground(DatawatchColors.surface)

            Section("Add filter") {
                TextField("Pattern (regex)", text: $pattern)
                    .font(DatawatchFonts.terminalSmall)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                Picker("Action", selection: $action) {
                    ForEach(Self.actions, id: \.self) { Text($0).tag($0) }
                }
                TextField("Value (optional)", text: $value)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                Button("Add filter") {
                    IosRulesEditors.shared.createFilter(profile: profile, pattern: pattern, action: action, value: value) { err in
                        DispatchQueue.main.async {
                            actionError = err
                            if err == nil { pattern = ""; value = "" }
                            Task { await reload() }
                        }
                    }
                }
                .disabled(pattern.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .listRowBackground(DatawatchColors.surface)

            if let actionError {
                Section { Text(actionError).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error) }
            }
        }
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .navigationTitle("Filters")
        .navigationBarTitleDisplayMode(.inline)
        .task { await reload() }
        .refreshable { await reload() }
        .sheet(item: Binding(get: { editing.map { EditBox(row: $0) } }, set: { editing = $0?.row })) { box in
            FilterEditSheet(row: box.row) { p, a, v in
                IosRulesEditors.shared.updateFilter(profile: profile, id: box.row.id, pattern: p, action: a, value: v) { err in
                    DispatchQueue.main.async { actionError = err; Task { await reload() } }
                }
            }
        }
    }

    private struct EditBox: Identifiable { let row: IosFilterRow; var id: String { row.id } }

    private func row(_ f: IosFilterRow) -> some View {
        HStack(spacing: 8) {
            Text(f.enabled ? "on" : "off")
                .font(DatawatchFonts.badge)
                .foregroundStyle(f.enabled ? DatawatchColors.success : DatawatchColors.error)
                .padding(.horizontal, 6).padding(.vertical, 2)
                .background((f.enabled ? DatawatchColors.success : DatawatchColors.error).opacity(0.14), in: Capsule())
            VStack(alignment: .leading, spacing: 2) {
                Text(f.pattern).font(DatawatchFonts.terminalSmall).foregroundStyle(DatawatchColors.onSurface).lineLimit(2)
                Text(f.action + (f.value.isEmpty ? "" : " → \(f.value)"))
                    .font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            Spacer(minLength: 4)
            Button(f.enabled ? "⏸" : "▶") {
                IosRulesEditors.shared.setFilterEnabled(profile: profile, id: f.id, enabled: !f.enabled) { err in
                    DispatchQueue.main.async { actionError = err; Task { await reload() } }
                }
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(f.enabled ? "Disable filter" : "Enable filter")
            Button("✎") { editing = f }
                .buttonStyle(.borderless)
                .accessibilityLabel("Edit filter")
        }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) {
                IosRulesEditors.shared.deleteFilter(profile: profile, id: f.id) { err in
                    DispatchQueue.main.async { actionError = err; Task { await reload() } }
                }
            } label: { Label("Delete", systemImage: "trash") }
        }
    }

    private func reload() async {
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            IosRulesEditors.shared.filters(
                profile: profile,
                onSuccess: { list in DispatchQueue.main.async { filters = list; loadError = nil; cont.resume() } },
                onError: { msg in DispatchQueue.main.async { if filters == nil { loadError = msg }; cont.resume() } }
            )
        }
    }
}

private struct FilterEditSheet: View {
    let row: IosFilterRow
    let onSave: (String, String, String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var pattern = ""
    @State private var action = "alert"
    @State private var value = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("Pattern (regex)", text: $pattern)
                    .font(DatawatchFonts.terminalSmall)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                Picker("Action", selection: $action) {
                    ForEach(FiltersView.actions, id: \.self) { Text($0).tag($0) }
                }
                TextField("Value (optional)", text: $value)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle("Edit filter")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { onSave(pattern, action, value); dismiss() }
                        .disabled(pattern.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .onAppear { pattern = row.pattern; action = row.action.isEmpty ? "alert" : row.action; value = row.value }
        }
    }
}
