import SwiftUI
import DatawatchShared

// Settings depth (iOS item 16): edit forms + per-card extras for the generic
// list cards, backed by IosSettingsCrud.

// MARK: - Field-form edit

/// Loads the entry's current values, then shows the shared add/edit form.
struct SettingsCrudEditSheet: View {
    let profile: ServerProfile
    let kind: String
    let id: String
    let fields: [SettingsAddField]
    let onSaved: () -> Void

    @State private var initial: [String: String]?
    @State private var error: String?

    /// Channel-routing rows are addressed by index, so every field stays editable.
    private var locked: Set<String> {
        if kind == "channel_routing" { return ["_index"] }
        if let first = fields.first { return [first.key] }
        return []
    }

    var body: some View {
        if let initial {
            SettingsAddEntrySheet(fields: fields, initial: initial, lockedKeys: locked, title: "Edit") { values, done in
                IosSettingsCrud.shared.save(profile: profile, kind: kind, originalId: id, values: values) { err in
                    DispatchQueue.main.async {
                        done(err)
                        if err == nil { onSaved() }
                    }
                }
            }
        } else {
            loadingView
        }
    }

    private var loadingView: some View {
        VStack(spacing: 12) {
            if let error {
                Text(error)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
            } else {
                ProgressView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
        .task { load() }
    }

    private func load() {
        IosSettingsCrud.shared.formValues(profile: profile, kind: kind, id: id, onSuccess: { map in
            DispatchQueue.main.async { initial = map }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
    }
}

// MARK: - Project / cluster profile JSON editor

/// PWA profile editor "form ↔ YAML" escape hatch as a JSON document. Credential
/// values arrive masked (•••); leaving them masked keeps the stored value.
struct SettingsProfileJsonSheet: View {
    let profile: ServerProfile
    let kind: String
    let name: String?
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var loaded = false
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 8) {
                Text("Edit the profile as JSON. Masked values (•••) are kept unchanged.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                if loaded {
                    TextEditor(text: $text)
                        .font(DatawatchFonts.terminalSmall)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .scrollContentBackground(.hidden)
                        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 8))
                } else {
                    ProgressView().frame(maxWidth: .infinity)
                }
                if let error {
                    Text(error)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                }
            }
            .padding()
            .frame(maxHeight: .infinity, alignment: .top)
            .background(DatawatchColors.background)
            .navigationTitle(name ?? L("New profile"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if saving {
                        ProgressView()
                    } else {
                        Button("Save") { save() }.disabled(!loaded)
                    }
                }
            }
            .task { load() }
        }
    }

    private func load() {
        IosSettingsCrud.shared.profileJson(profile: profile, kind: kind, name: name ?? "", onSuccess: { json in
            DispatchQueue.main.async {
                text = json
                loaded = true
            }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
    }

    private func save() {
        saving = true
        error = nil
        IosSettingsCrud.shared.saveProfileJson(profile: profile, kind: kind, originalName: name, json: text) { err in
            DispatchQueue.main.async {
                saving = false
                if let err {
                    error = err
                } else {
                    onSaved()
                    dismiss()
                }
            }
        }
    }
}

// MARK: - Per-card extras

/// Extra sections rendered above a list card's rows for specific kinds.
struct SettingsListExtras: View {
    let profile: ServerProfile
    let kind: String
    let onChanged: () -> Void

    var body: some View {
        switch kind {
        case "tailscale":
            Section { TailscaleAuthKeyRow(profile: profile) }
        case "discussions":
            Section { DiscussionWriteRow(profile: profile, onSent: onChanged) }
        case "council_personas":
            Section {
                NavigationLink {
                    SettingsCouncilRunsView(profile: profile)
                } label: {
                    Label("Council runs", systemImage: "person.3.sequence")
                }
                .listRowBackground(DatawatchColors.surface)
            }
        case "compute_nodes":
            KindMigrationSection(profile: profile, onChanged: onChanged)
            Section { marketplaceLink }
        case "llms":
            Section { marketplaceLink }
        default:
            EmptyView()
        }
    }

    private var marketplaceLink: some View {
        NavigationLink {
            OllamaMarketplaceView(profile: profile)
        } label: {
            Label("Ollama marketplace", systemImage: "square.and.arrow.down.on.square")
        }
        .listRowBackground(DatawatchColors.surface)
    }
}

/// Tailscale "Generate Auth Key": the key goes straight to the clipboard and
/// is never rendered on screen.
private struct TailscaleAuthKeyRow: View {
    let profile: ServerProfile
    @State private var busy = false
    @State private var message: String?
    @State private var isError = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button(action: generate) {
                HStack {
                    Text("Generate Auth Key").foregroundStyle(DatawatchColors.primary)
                    Spacer()
                    if busy { ProgressView().controlSize(.small) }
                }
            }
            .disabled(busy)
            if let message {
                Text(message)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(isError ? DatawatchColors.error : DatawatchColors.onSurfaceMuted)
            }
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func generate() {
        busy = true
        message = nil
        IosSettingsCrud.shared.tailscaleAuthKey(profile: profile, onSuccess: { key, expires in
            DispatchQueue.main.async {
                UIPasteboard.general.string = key
                busy = false
                isError = false
                let suffix: String = expires.isEmpty ? "" : " · " + L("expires") + " " + expires
                message = L("Auth key copied to clipboard") + suffix
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                busy = false
                isError = true
                message = msg
            }
        })
    }
}

/// Discussion Scopes "write message" (PWA discussion_scopes card).
private struct DiscussionWriteRow: View {
    let profile: ServerProfile
    let onSent: () -> Void
    @State private var scope = ""
    @State private var content = ""
    @State private var busy = false
    @State private var message: String?

    private var canSend: Bool {
        !scope.trimmingCharacters(in: .whitespaces).isEmpty && !content.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Write message")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            TextField(L("Scope ID"), text: $scope)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            TextField(L("Message"), text: $content, axis: .vertical)
                .lineLimit(1...4)
            HStack {
                if let message {
                    Text(message)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                Spacer()
                Button("Send", action: send)
                    .buttonStyle(.borderless)
                    .disabled(!canSend || busy)
            }
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func send() {
        busy = true
        IosSettingsCrud.shared.writeDiscussion(profile: profile, id: scope, content: content) { err in
            DispatchQueue.main.async {
                busy = false
                if let err {
                    message = err
                } else {
                    content = ""
                    message = L("Sent")
                    onSent()
                }
            }
        }
    }
}

// MARK: - Compute kind migration

/// PWA Compute Nodes "Kind-migration banner": nodes still on a deprecated kind
/// get a per-node migrate menu.
/// Published values are only mutated on the main queue.
private final class KindMigrationModel: ObservableObject {
    @Published var state: IosKindMigration?
    @Published var error: String?
    let profile: ServerProfile

    /// Loads on creation: an empty Section never runs `.task`, so the fetch
    /// can't hang off a view modifier.
    init(profile: ServerProfile) {
        self.profile = profile
        load()
    }

    func load() {
        IosSettingsCrud.shared.kindMigration(profile: profile, onSuccess: { s in
            DispatchQueue.main.async { self.state = s }
        }, onError: { _ in })
    }
}

private struct KindMigrationSection: View {
    let profile: ServerProfile
    let onChanged: () -> Void
    @StateObject private var model: KindMigrationModel

    init(profile: ServerProfile, onChanged: @escaping () -> Void) {
        self.profile = profile
        self.onChanged = onChanged
        _model = StateObject(wrappedValue: KindMigrationModel(profile: profile))
    }

    private var visible: IosKindMigration? {
        guard let s = model.state else { return nil }
        return (s.nodes.isEmpty && !s.show) ? nil : s
    }

    var body: some View {
        Section {
            if let s = visible {
                if !s.notice.isEmpty {
                    Text(verbatim: s.notice)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.warning)
                        .listRowBackground(DatawatchColors.surface)
                }
                ForEach(Array(s.nodes.enumerated()), id: \.offset) { pair in
                    migrateRow(node: pair.element, current: kindAt(s, pair.offset), kinds: s.supportedKinds)
                }
                if let err = model.error {
                    Text(err)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                        .listRowBackground(DatawatchColors.surface)
                }
                if s.show {
                    Button("Dismiss", action: dismissNotice)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
        } header: {
            if visible != nil {
                Label("Kind migration", systemImage: "exclamationmark.triangle")
            }
        }
    }

    private func kindAt(_ s: IosKindMigration, _ i: Int) -> String {
        i < s.nodeKinds.count ? s.nodeKinds[i] : ""
    }

    private func migrateRow(node: String, current: String, kinds: [String]) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: node).foregroundStyle(DatawatchColors.onSurface)
                Text(verbatim: "⚠ " + current)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.warning)
            }
            Spacer()
            Menu("Migrate") {
                ForEach(kinds, id: \.self) { k in
                    Button(k) { migrate(node, k) }
                }
            }
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func migrate(_ node: String, _ kind: String) {
        IosSettingsCrud.shared.migrateKind(profile: profile, node: node, kind: kind) { err in
            DispatchQueue.main.async {
                model.error = err
                model.load()
                onChanged()
            }
        }
    }

    private func dismissNotice() {
        IosSettingsCrud.shared.dismissMigration(profile: profile) { _ in
            DispatchQueue.main.async { model.load() }
        }
    }
}

// MARK: - Ollama marketplace

/// PWA/Android Ollama marketplace: browse the catalog, pull a tag onto an
/// Ollama compute node, live progress per pull.
struct OllamaMarketplaceView: View {
    let profile: ServerProfile

    @State private var models: [IosOllamaModel] = []
    @State private var nodes: [String] = []
    @State private var node = ""
    @State private var loaded = false
    @State private var error: String?
    @State private var query = ""
    @State private var pulls: [String: IosPullTask] = [:]

    private var filtered: [IosOllamaModel] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        if q.isEmpty { return models }
        return models.filter { $0.name.lowercased().contains(q) || $0.description_.lowercased().contains(q) }
    }

    var body: some View {
        List {
            Section { nodePicker }
            if !pulls.isEmpty {
                Section("Pulls") { pullRows }
            }
            Section { modelRows }
            if let error {
                Section {
                    Text(error)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .searchable(text: $query)
        .navigationTitle("Ollama marketplace")
        .navigationBarTitleDisplayMode(.inline)
        .task { load() }
    }

    @ViewBuilder
    private var nodePicker: some View {
        if nodes.isEmpty {
            Text(loaded ? L("No enabled Ollama compute nodes.") : L("Loading…"))
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .listRowBackground(DatawatchColors.surface)
        } else {
            Picker("Pull to node", selection: $node) {
                ForEach(nodes, id: \.self) { n in Text(verbatim: n).tag(n) }
            }
            .listRowBackground(DatawatchColors.surface)
        }
    }

    private var pullRows: some View {
        ForEach(Array(pulls.values).sorted { $0.model < $1.model }, id: \.id) { task in
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: task.model).foregroundStyle(DatawatchColors.onSurface)
                ProgressView(value: Double(task.progress), total: 100.0)
                    .tint(DatawatchColors.primary)
                Text(verbatim: task.status + " · \(task.progress)%")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .listRowBackground(DatawatchColors.surface)
        }
    }

    @ViewBuilder
    private var modelRows: some View {
        if !loaded {
            ProgressView().listRowBackground(DatawatchColors.surface)
        } else if filtered.isEmpty {
            Text("Nothing here yet.")
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .listRowBackground(DatawatchColors.surface)
        } else {
            ForEach(filtered, id: \.name) { m in
                OllamaModelRow(model: m, canPull: !node.isEmpty) { tag in pull(m.name, tag) }
                    .listRowBackground(DatawatchColors.surface)
            }
        }
    }

    private func load() {
        IosSettingsCrud.shared.ollamaNodes(profile: profile, onSuccess: { list in
            DispatchQueue.main.async {
                nodes = list
                if node.isEmpty { node = list.first ?? "" }
            }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
        IosSettingsCrud.shared.ollamaCatalog(profile: profile, onSuccess: { list in
            DispatchQueue.main.async {
                models = list
                loaded = true
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                loaded = true
                error = msg
            }
        })
    }

    private func pull(_ name: String, _ tag: String) {
        let model = tag.isEmpty ? name : name + ":" + tag
        let target = node
        IosSettingsCrud.shared.pullOllamaModel(profile: profile, node: target, model: model, onProgress: { task in
            DispatchQueue.main.async { pulls[model] = task }
        }, onError: { msg in
            DispatchQueue.main.async { error = model + ": " + msg }
        })
    }
}

private struct OllamaModelRow: View {
    let model: IosOllamaModel
    let canPull: Bool
    let onPull: (String) -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: model.name)
                    .font(DatawatchFonts.bodyLarge)
                    .foregroundStyle(DatawatchColors.onSurface)
                if !model.description_.isEmpty {
                    Text(verbatim: model.description_)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(3)
                }
            }
            Spacer(minLength: 4)
            pullMenu
        }
    }

    private var pullMenu: some View {
        Menu {
            if model.tagNames.isEmpty {
                Button("latest") { onPull("") }
            }
            ForEach(Array(model.tags.enumerated()), id: \.offset) { pair in
                Button(pair.element) { onPull(tagName(pair.offset)) }
            }
        } label: {
            Image(systemName: "arrow.down.circle")
                .foregroundStyle(canPull ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
        }
        .disabled(!canPull)
        .accessibilityLabel("Pull")
    }

    private func tagName(_ i: Int) -> String {
        i < model.tagNames.count ? model.tagNames[i] : ""
    }
}

// MARK: - Council runs

/// PWA Council panel: proposal + mode + personas → run; recent runs list.
struct SettingsCouncilRunsView: View {
    let profile: ServerProfile

    @State private var runs: [IosCouncilRun] = []
    @State private var personas: [IosSettingsRow] = []
    @State private var selected: Set<String> = []
    @State private var proposal = ""
    @State private var mode = "quick"
    @State private var busy = false
    @State private var loaded = false
    @State private var error: String?

    var body: some View {
        List {
            Section("New run") { newRunForm }
            Section("Recent runs") { runRows }
            if let error {
                Section {
                    Text(error)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .navigationTitle("Council runs")
        .navigationBarTitleDisplayMode(.inline)
        .task { load() }
        .refreshable { load() }
    }

    @ViewBuilder
    private var newRunForm: some View {
        TextField(L("Proposal"), text: $proposal, axis: .vertical)
            .lineLimit(2...6)
            .listRowBackground(DatawatchColors.surface)
        Picker("Mode", selection: $mode) {
            Text("Quick").tag("quick")
            Text("Debate").tag("debate")
        }
        .pickerStyle(.segmented)
        .listRowBackground(DatawatchColors.surface)
        ForEach(personas, id: \.id) { p in
            Toggle(isOn: personaBinding(p.id)) {
                Text(verbatim: p.title)
            }
            .tint(DatawatchColors.primary)
            .listRowBackground(DatawatchColors.surface)
        }
        Button(action: start) {
            HStack {
                Text("Run council").foregroundStyle(DatawatchColors.primary)
                Spacer()
                if busy { ProgressView().controlSize(.small) }
            }
        }
        .disabled(busy || proposal.trimmingCharacters(in: .whitespaces).isEmpty)
        .listRowBackground(DatawatchColors.surface)
    }

    @ViewBuilder
    private var runRows: some View {
        if !loaded {
            ProgressView().listRowBackground(DatawatchColors.surface)
        } else if runs.isEmpty {
            Text("No council runs yet.")
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .listRowBackground(DatawatchColors.surface)
        } else {
            ForEach(runs.prefix(5), id: \.id) { r in
                CouncilRunRow(run: r) { stop(r) }
                    .listRowBackground(DatawatchColors.surface)
            }
        }
    }

    private func personaBinding(_ id: String) -> Binding<Bool> {
        Binding(
            get: { selected.contains(id) },
            set: { on in
                if on { selected.insert(id) } else { selected.remove(id) }
            }
        )
    }

    private func load() {
        IosSettingsLists.shared.load(profile: profile, kind: "council_personas", onSuccess: { list in
            DispatchQueue.main.async {
                personas = list
                if selected.isEmpty { selected = Set(list.filter { $0.enabled }.map { $0.id }) }
            }
        }, onError: { _ in })
        IosSettingsCrud.shared.councilRuns(profile: profile, onSuccess: { list in
            DispatchQueue.main.async {
                runs = list
                loaded = true
                error = nil
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                loaded = true
                error = msg
            }
        })
    }

    private func start() {
        busy = true
        error = nil
        let chosen: [String] = personas.map { $0.id }.filter { selected.contains($0) }
        IosSettingsCrud.shared.startCouncilRun(profile: profile, proposal: proposal, mode: mode, personas: chosen) { err in
            DispatchQueue.main.async {
                busy = false
                if let err {
                    error = err
                } else {
                    proposal = ""
                    load()
                }
            }
        }
    }

    private func stop(_ r: IosCouncilRun) {
        IosSettingsCrud.shared.stopCouncilRun(profile: profile, id: r.id) { err in
            DispatchQueue.main.async {
                error = err
                load()
            }
        }
    }
}

private struct CouncilRunRow: View {
    let run: IosCouncilRun
    let onStop: () -> Void

    private var running: Bool { run.status == "running" || run.status == "pending" }

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(verbatim: run.proposal)
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(2)
            Text(verbatim: [run.mode, run.status, run.startedAt].filter { !$0.isEmpty }.joined(separator: " · "))
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if !run.consensus.isEmpty {
                Text(verbatim: run.consensus)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(4)
            }
            if running {
                Button("Stop", role: .destructive, action: onStop)
                    .buttonStyle(.borderless)
                    .font(DatawatchFonts.labelSmall)
            }
        }
    }
}
