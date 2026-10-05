import SwiftUI
import DatawatchShared

// Settings › Compute › LLMs add/edit form (PWA _renderLLMEditPanel +
// _llmKindChanged visibility rules; Android LlmRegistryCard edit dialog).

struct LlmFormSheet: View {
    let profile: ServerProfile
    let editName: String?
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var v: [String: String] = ["kind": "ollama"]
    @State private var models: [IosLlmModel] = []
    @State private var selectedNodes: [String] = []
    @State private var nodes: [IosNodeRef] = []
    @State private var apiKeyConfigured = false
    @State private var loading = false
    @State private var saving = false
    @State private var testing = false
    @State private var testModel = ""
    @State private var status: String?
    @State private var statusTone = 0
    @State private var showYaml = false

    private var isEdit: Bool { editName != nil }
    private var kind: String { v["kind"] ?? "ollama" }
    private var isSaas: Bool { IosSettingsForms.shared.llmSaasKinds.contains(kind) }
    private var isSessionBackend: Bool { IosSettingsForms.shared.llmSessionKinds.contains(kind) }

    var body: some View {
        NavigationStack {
            Form {
                if loading {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("Loading…").foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                } else {
                    identitySection
                    if !isSaas {
                        LlmComputeNodesSection(nodes: nodes, selected: $selectedNodes)
                    }
                    LlmModelsSection(profile: profile, kind: kind, isSaas: isSaas, nodes: nodes, models: $models)
                    LlmCoreSection(v: $v, isSaas: isSaas, apiKeyConfigured: apiKeyConfigured)
                    if isSessionBackend {
                        LlmSessionSection(v: $v)
                    }
                    if kind == "claude-code" {
                        LlmClaudeSection(v: $v)
                    }
                    testSection
                    yamlSection
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(isEdit ? L("Edit LLM") : L("Add LLM"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarContent }
            .task { load() }
            .sheet(isPresented: $showYaml) {
                LlmJsonSheet(profile: profile, name: editName ?? "") { load() }
            }
        }
        .dwThemed()
    }

    /// PWA LLM panel "</> YAML" escape hatch (_llmOpenYAMLForCurrent).
    private var yamlSection: some View {
        Section {
            Button {
                openYaml()
            } label: {
                Label("</> YAML", systemImage: "chevron.left.forwardslash.chevron.right")
                    .foregroundStyle(DatawatchColors.primary)
            }
        } footer: {
            Text("Edit raw YAML — form will reload with parsed values on save")
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func openYaml() {
        guard isEdit else {
            status = L("YAML editor available after first save")
            statusTone = 0
            return
        }
        showYaml = true
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { dismiss() }
        }
        ToolbarItem(placement: .confirmationAction) {
            if saving {
                ProgressView()
            } else {
                Button(isEdit ? L("Save") : L("Add")) { save() }
                    .disabled(loading)
            }
        }
    }

    private var identitySection: some View {
        Section {
            FormTextField(label: "Name", text: binding("name"), placeholder: "llama3-70b", disabled: isEdit)
            FormChoiceRow(label: "Kind", value: binding("kind"), options: IosSettingsForms.shared.llmKinds)
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private var testSection: some View {
        Section {
            if let status {
                FormStatusLine(text: status, tone: statusTone)
            }
            Picker(selection: $testModel) {
                Text("(first enabled)").tag("")
                ForEach(models.map { $0.model }, id: \.self) { m in
                    Text(verbatim: m).tag(m)
                }
            } label: {
                Text("Test model:").foregroundStyle(DatawatchColors.onSurface)
            }
            .pickerStyle(.menu)
            .tint(DatawatchColors.primary)
            Button {
                runTest()
            } label: {
                HStack {
                    Label("Test", systemImage: "checkmark.seal")
                        .foregroundStyle(DatawatchColors.primary)
                    Spacer()
                    if testing { ProgressView().controlSize(.small) }
                }
            }
            .disabled(testing)
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { v[key] ?? "" }, set: { v[key] = $0 })
    }

    // MARK: actions

    private func load() {
        IosSettingsForms.shared.computeNodes(profile: profile) { list in
            DispatchQueue.main.async { nodes = list }
        }
        guard let name = editName else { return }
        loading = true
        IosSettingsForms.shared.loadLlm(profile: profile, name: name, onSuccess: { form in
            DispatchQueue.main.async {
                v = form.values
                models = form.models
                selectedNodes = form.computeNodes
                apiKeyConfigured = form.apiKeyConfigured
                loading = false
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                loading = false
                status = msg
                statusTone = 2
            }
        })
    }

    private func runTest() {
        guard let name = editName else {
            status = L("Save first, then Test")
            statusTone = 0
            return
        }
        testing = true
        status = L("Testing…")
        statusTone = 0
        IosSettingsForms.shared.testLlm(profile: profile, name: name, model: testModel, onSuccess: { text in
            DispatchQueue.main.async {
                testing = false
                status = "✓ " + text
                statusTone = 1
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                testing = false
                status = "✕ " + msg
                statusTone = 2
            }
        })
    }

    private func save() {
        saving = true
        status = nil
        IosSettingsForms.shared.saveLlm(
            profile: profile,
            editName: editName ?? "",
            values: v,
            models: models,
            computeNodes: selectedNodes
        ) { err in
            DispatchQueue.main.async {
                saving = false
                if let err {
                    status = "✕ " + err
                    statusTone = 2
                } else {
                    onSaved()
                    dismiss()
                }
            }
        }
    }
}

/// PWA openFormEditPopup raw view for an LLM: the record as pretty-printed JSON
/// (the PWA's "YAML" view is JSON.stringify / JSON.parse). Test saves first,
/// like the PWA; Save PUTs and the form reloads with the parsed values.
struct LlmJsonSheet: View {
    let profile: ServerProfile
    let name: String
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var loaded = false
    @State private var busy = false
    @State private var status: String?
    @State private var statusTone = 0

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 8) {
                editor
                if let status {
                    FormStatusLine(text: status, tone: statusTone)
                }
                Button {
                    runTest()
                } label: {
                    Label("Test", systemImage: "checkmark.seal")
                }
                .disabled(!loaded || busy)
                .tint(DatawatchColors.primary)
            }
            .padding()
            .frame(maxHeight: .infinity, alignment: .top)
            .background(DatawatchColors.background)
            .navigationTitle(L("Edit") + " LLM: " + name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarContent }
            .task { load() }
        }
        .dwThemed()
    }

    @ViewBuilder
    private var editor: some View {
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
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { dismiss() }
        }
        ToolbarItem(placement: .confirmationAction) {
            if busy {
                ProgressView()
            } else {
                Button("Save") { save() }.disabled(!loaded)
            }
        }
    }

    private func load() {
        IosYamlRecall.shared.llmJson(profile: profile, name: name, onSuccess: { json in
            DispatchQueue.main.async {
                text = json
                loaded = true
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                status = msg
                statusTone = 2
            }
        })
    }

    private func runTest() {
        busy = true
        status = L("Testing unsaved values…")
        statusTone = 0
        IosYamlRecall.shared.testLlmJson(profile: profile, name: name, text: text, onSuccess: { reply in
            DispatchQueue.main.async {
                busy = false
                status = "✓ " + L("Test passed:") + " " + reply
                statusTone = 1
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                busy = false
                status = "✕ " + L("Test failed:") + " " + msg
                statusTone = 2
            }
        })
    }

    private func save() {
        busy = true
        status = L("Saving…")
        statusTone = 0
        IosYamlRecall.shared.saveLlmJson(profile: profile, name: name, text: text) { err in
            DispatchQueue.main.async {
                busy = false
                if let err {
                    status = "✕ " + err
                    statusTone = 2
                } else {
                    onSaved()
                    dismiss()
                }
            }
        }
    }
}

/// ComputeNodes multi-select; selection order = failover order.
private struct LlmComputeNodesSection: View {
    let nodes: [IosNodeRef]
    @Binding var selected: [String]

    var body: some View {
        Section {
            if nodes.isEmpty {
                Text("No ComputeNodes yet.")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            ForEach(nodes, id: \.name) { node in
                Button {
                    toggle(node.name)
                } label: {
                    row(node)
                }
            }
        } header: {
            Text("ComputeNodes (multi-select; ordered failover)")
        } footer: {
            Text("Order = failover order.")
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func row(_ node: IosNodeRef) -> some View {
        let index: Int? = selected.firstIndex(of: node.name)
        return HStack {
            Text(verbatim: "\(node.name) (\(node.kind))")
                .foregroundStyle(DatawatchColors.onSurface)
            Spacer()
            if let index {
                Text(verbatim: String(index + 1))
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.primary)
                Image(systemName: "checkmark")
                    .foregroundStyle(DatawatchColors.primary)
            }
        }
    }

    private func toggle(_ name: String) {
        if let i = selected.firstIndex(of: name) {
            selected.remove(at: i)
        } else {
            selected.append(name)
        }
    }
}

/// Enabled Models table + add row (node picker hidden for SaaS kinds).
private struct LlmModelsSection: View {
    let profile: ServerProfile
    let kind: String
    let isSaas: Bool
    let nodes: [IosNodeRef]
    @Binding var models: [IosLlmModel]

    @State private var newNode = ""
    @State private var newModel = ""
    @State private var suggestions: [String] = []

    var body: some View {
        Section {
            ForEach(Array(models.enumerated()), id: \.offset) { pair in
                modelRow(pair.element)
                    .swipeActions(edge: .trailing) {
                        Button(role: .destructive) { models.remove(at: pair.offset) } label: {
                            Label("Remove", systemImage: "trash")
                        }
                    }
            }
            if !isSaas {
                Picker(selection: $newNode) {
                    Text("—").tag("")
                    ForEach(nodes, id: \.name) { n in
                        Text(verbatim: n.name).tag(n.name)
                    }
                } label: {
                    Text("Node").foregroundStyle(DatawatchColors.onSurface)
                }
                .pickerStyle(.menu)
                .tint(DatawatchColors.primary)
            }
            addRow
        } header: {
            Text("Enabled Models")
        } footer: {
            Text("One model per row. For local kinds, pick the node then select or type the model.")
        }
        .listRowBackground(DatawatchColors.surface)
        .onChange(of: newNode) { _ in probe() }
    }

    private func modelRow(_ m: IosLlmModel) -> some View {
        HStack(spacing: 8) {
            if !isSaas {
                Text(verbatim: m.node.isEmpty ? "—" : m.node)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            Text(verbatim: m.model)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
            Spacer()
        }
    }

    private var addRow: some View {
        HStack(spacing: 8) {
            TextField("e.g. qwen3:8b", text: $newModel)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if !suggestions.isEmpty {
                Menu {
                    ForEach(suggestions, id: \.self) { s in
                        Button(s) { newModel = s }
                    }
                } label: {
                    Image(systemName: "list.bullet").foregroundStyle(DatawatchColors.primary)
                }
                .accessibilityLabel("Available models")
            }
            Button(L("+ Add")) { addModel() }
                .buttonStyle(.borderless)
                .foregroundStyle(DatawatchColors.primary)
                .disabled(newModel.trimmingCharacters(in: .whitespaces).isEmpty)
        }
    }

    private func addModel() {
        let m = newModel.trimmingCharacters(in: .whitespaces)
        guard !m.isEmpty else { return }
        let node: String = isSaas ? "" : newNode
        if models.contains(where: { $0.node == node && $0.model == m }) { return }
        models.append(IosLlmModel(node: node, model: m))
        newModel = ""
        suggestions.removeAll { $0 == m }
    }

    private func probe() {
        suggestions = []
        guard !isSaas, !newNode.isEmpty else { return }
        let node = newNode
        let already: Set<String> = Set(models.filter { $0.node == node }.map { $0.model })
        IosSettingsForms.shared.nodeModels(profile: profile, node: node, kind: kind) { list in
            let fresh: [String] = list.filter { !already.contains($0) }
            DispatchQueue.main.async { suggestions = fresh }
        }
    }
}

/// Auto-add, API key reference, timeout, max in-flight, tags.
private struct LlmCoreSection: View {
    @Binding var v: [String: String]
    let isSaas: Bool
    let apiKeyConfigured: Bool

    private var keyPlaceholder: String {
        apiKeyConfigured ? L("(configured — enter to change)") : "${secret:anthropic-key}"
    }

    var body: some View {
        Section {
            if !isSaas {
                Toggle("Auto-enable new models discovered on these Compute Nodes", isOn: flag("auto_add_models"))
                    .tint(DatawatchColors.primary)
            }
            FormTextField(
                label: "API key reference (literal or ${secret:name}; cloud kinds)",
                text: binding("api_key_ref"),
                placeholder: keyPlaceholder
            )
            FormTextField(label: "Timeout (seconds, 0 = adapter default)", text: binding("timeout_seconds"), placeholder: "0", numeric: true)
            FormTextField(label: "Max in-flight autonomous sessions (0 = unlimited)", text: binding("max_inflight"), placeholder: "0", numeric: true)
            FormTextField(label: "Tags", text: binding("tags"), placeholder: "fast, coding…")
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { v[key] ?? "" }, set: { v[key] = $0 })
    }

    private func flag(_ key: String) -> Binding<Bool> {
        Binding(get: { (v[key] ?? "") == "true" }, set: { v[key] = $0 ? "true" : "false" })
    }
}

/// Session-backend fields (PWA llmSessionSect).
private struct LlmSessionSection: View {
    @Binding var v: [String: String]

    var body: some View {
        Section {
            FormTextField(label: "Binary path", text: binding("binary"), placeholder: "e.g. claude / aider / goose")
            FormTextField(label: "Console width (cols)", text: binding("console_cols"), placeholder: "120", numeric: true)
            FormTextField(label: "Console height (rows)", text: binding("console_rows"), placeholder: "40", numeric: true)
            FormChoiceRow(label: "Output mode", value: binding("output_mode"), options: ["terminal", "log", "chat"], emptyLabel: "(default)")
            FormChoiceRow(label: "Input mode", value: binding("input_mode"), options: ["tmux", "none"], emptyLabel: "(default)")
            Toggle("Auto git init (create repo in project dir if missing)", isOn: flag("auto_git_init"))
                .tint(DatawatchColors.primary)
            Toggle("Auto git commit (commit before/after session)", isOn: flag("auto_git_commit"))
                .tint(DatawatchColors.primary)
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { v[key] ?? "" }, set: { v[key] = $0 })
    }

    private func flag(_ key: String) -> Binding<Bool> {
        Binding(get: { (v[key] ?? "") == "true" }, set: { v[key] = $0 ? "true" : "false" })
    }
}

/// claude-code-only fields (PWA llmClaudeSect).
private struct LlmClaudeSection: View {
    @Binding var v: [String: String]

    var body: some View {
        Section {
            Toggle("Skip permissions (--dangerously-skip-permissions)", isOn: flag("skip_permissions"))
                .tint(DatawatchColors.primary)
            Toggle("Channel mode (MCP channel bridge)", isOn: flag("channel_enabled"))
                .tint(DatawatchColors.primary)
            Toggle("Auto-accept startup disclaimers", isOn: flag("auto_accept_disclaimer"))
                .tint(DatawatchColors.primary)
            FormChoiceRow(
                label: "Permission mode",
                value: binding("permission_mode"),
                options: ["plan", "acceptEdits", "auto", "bypassPermissions", "dontAsk", "default"],
                emptyLabel: "(none)"
            )
            FormChoiceRow(label: "Default effort", value: binding("default_effort"), options: ["quick", "normal", "thorough"], emptyLabel: "(default)")
            FormTextField(label: "Fallback chain", text: binding("fallback_chain"), placeholder: "claude-personal, gemini-backup…", hint: "Comma-separated, in failover order.")
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { v[key] ?? "" }, set: { v[key] = $0 })
    }

    private func flag(_ key: String) -> Binding<Bool> {
        Binding(get: { (v[key] ?? "") == "true" }, set: { v[key] = $0 ? "true" : "false" })
    }
}
