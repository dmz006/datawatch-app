import SwiftUI
import DatawatchShared

// MARK: - Raw config editor (D79a)

/// Edits the masked config as JSON. Save diffs against the live config and
/// writes only changed dotted keys; masked "***" values are never sent back.
struct SettingsRawConfigCard: View {
    let profile: ServerProfile
    @State private var text = ""
    @State private var loaded = false
    @State private var error: String?
    @State private var status: String?
    @State private var confirming = false
    @State private var saving = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("View or edit the full server configuration as JSON. Most fields require a daemon restart to take effect.")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if let error {
                Text(error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
            }
            if let status {
                Text(verbatim: status).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.success)
            }
            if loaded {
                TextEditor(text: $text)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .scrollContentBackground(.hidden)
                    .background(DatawatchColors.surface)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            } else {
                ProgressView().frame(maxWidth: .infinity)
                Spacer()
            }
        }
        .padding()
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if saving {
                    ProgressView()
                } else {
                    Button("Save") { confirming = true }
                        .disabled(!loaded)
                }
            }
        }
        .confirmationDialog("Overwrite config?", isPresented: $confirming, titleVisibility: .visible) {
            Button("Overwrite", role: .destructive) { save() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This writes the changed values to the server config. A daemon restart may be required for most fields to take effect.")
        }
        .task {
            if !loaded { load() }
        }
    }

    private func load() {
        IosSettingsConfig.shared.rawJson(profile: profile, onSuccess: { s in
            DispatchQueue.main.async {
                text = s
                loaded = true
            }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
    }

    private func save() {
        saving = true
        error = nil
        status = nil
        IosSettingsConfig.shared.applyRaw(profile: profile, json: text, onSuccess: { count in
            DispatchQueue.main.async {
                saving = false
                let n: Int = count.intValue
                status = n == 0 ? L("No changes.") : "\(n) " + L("keys saved.")
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                saving = false
                error = msg
            }
        })
    }
}

// MARK: - Docs Search (PWA docs_search)

struct SettingsDocsSearchCard: View {
    let profile: ServerProfile
    @State private var query = ""
    @State private var results: [IosSettingsRow] = []
    @State private var searching = false
    @State private var error: String?
    @State private var searched = false

    var body: some View {
        List {
            Section {
                HStack {
                    TextField("Search the datawatch docs", text: $query)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onSubmit { search() }
                    if searching {
                        ProgressView().controlSize(.small)
                    } else {
                        Button { search() } label: {
                            Image(systemName: "magnifyingglass")
                        }
                        .buttonStyle(.borderless)
                        .accessibilityLabel("Search")
                    }
                }
            }
            .listRowBackground(DatawatchColors.surface)
            Section {
                if let error {
                    Text(error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
                } else if searched && results.isEmpty {
                    Text("No results.").foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                ForEach(results, id: \.id) { hit in
                    Button {
                        let anchor: String = hit.id.hasPrefix("docs/") ? hit.id : "docs/" + hit.id
                        openServerPath(profile, "/diagrams.html#" + anchor)
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(verbatim: hit.title)
                                .foregroundStyle(DatawatchColors.onSurface)
                            Text(verbatim: hit.subtitle)
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                .lineLimit(3)
                            Text(verbatim: hit.id)
                                .font(DatawatchFonts.badge)
                                .foregroundStyle(DatawatchColors.primary)
                        }
                    }
                }
            }
            .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    private func search() {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        searching = true
        error = nil
        IosSettingsLists.shared.docsSearch(profile: profile, query: q, onSuccess: { rows in
            DispatchQueue.main.async {
                results = rows
                searching = false
                searched = true
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                error = msg
                searching = false
            }
        })
    }
}

// MARK: - Identity (PWA Settings › Automata › Identity)

struct SettingsIdentityCard: View {
    let profile: ServerProfile
    @State private var role = ""
    @State private var goals = ""
    @State private var projects = ""
    @State private var values = ""
    @State private var focus = ""
    @State private var notes = ""
    @State private var updatedAt = ""
    @State private var loaded = false
    @State private var saving = false
    @State private var status: String?
    @State private var error: String?
    @State private var showWizard = false

    var body: some View {
        Form {
            Section {
                Button("🤖 Open Identity Wizard") { showWizard = true }
            }
            Section("Role") { TextField("Role", text: $role) }
            Section("North-star goals (one per line)") { multiline($goals) }
            Section("Current projects (one per line)") { multiline($projects) }
            Section("Values (one per line)") { multiline($values) }
            Section("Current focus") { TextField("Current focus", text: $focus, axis: .vertical).lineLimit(1...4) }
            Section("Context notes") { multiline($notes) }
            if !updatedAt.isEmpty || status != nil || error != nil {
                Section { footerRows }
            }
        }
        .scrollContentBackground(.hidden)
        .disabled(!loaded)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if saving {
                    ProgressView()
                } else {
                    Button("Save") { save() }.disabled(!loaded)
                }
            }
        }
        .task { load() }
        .sheet(isPresented: $showWizard) {
            IdentityWizardSheet(profile: profile, onSaved: { load() })
        }
    }

    @ViewBuilder
    private var footerRows: some View {
        if let error {
            Text(error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
        }
        if let status {
            Text(verbatim: status).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.success)
        }
        if !updatedAt.isEmpty {
            Text(verbatim: L("Updated") + " " + updatedAt)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }

    private func multiline(_ binding: Binding<String>) -> some View {
        TextField("", text: binding, axis: .vertical)
            .lineLimit(2...8)
    }

    private func load() {
        IosSettingsLists.shared.identity(profile: profile, onSuccess: { id in
            DispatchQueue.main.async {
                role = id.role
                goals = id.goals
                projects = id.projects
                values = id.values
                focus = id.focus
                notes = id.notes
                updatedAt = id.updatedAt
                loaded = true
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                error = msg
                loaded = true
            }
        })
    }

    private func save() {
        saving = true
        status = nil
        error = nil
        let identity = IosIdentity(role: role, goals: goals, projects: projects, values: values,
                                   focus: focus, notes: notes, updatedAt: updatedAt)
        IosSettingsLists.shared.saveIdentity(profile: profile, identity: identity) { err in
            DispatchQueue.main.async {
                saving = false
                if let err { error = err } else { status = L("Saved") }
            }
        }
    }
}
