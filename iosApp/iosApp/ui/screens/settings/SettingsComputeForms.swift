import SwiftUI
import DatawatchShared

// Settings › Compute add/edit forms (PWA openComputeAddPanel +
// _renderLLMEditPanel; Android ComputeNodesCard / LlmRegistryCard dialogs).
// PWA content and field order, native Form controls.

/// Row identity for an edit sheet (`name` of the node / LLM).
struct SettingsFormEditItem: Identifiable {
    let id: String
}

// MARK: - Shared form rows

/// Caption-labelled text input used by the compute / LLM forms.
struct FormTextField: View {
    let label: String
    @Binding var text: String
    var placeholder: String = ""
    var hint: String = ""
    var numeric: Bool = false
    var disabled: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(L(label))
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            TextField(placeholder, text: $text)
                .keyboardType(numeric ? .numberPad : .default)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .disabled(disabled)
                .foregroundStyle(disabled ? DatawatchColors.onSurfaceMuted : DatawatchColors.onSurface)
            if !hint.isEmpty {
                Text(L(hint))
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

/// Picker over string options with an optional "(default)" empty entry.
struct FormChoiceRow: View {
    let label: String
    @Binding var value: String
    let options: [String]
    var emptyLabel: String = ""

    private var choices: [String] {
        var list: [String] = emptyLabel.isEmpty ? [] : [""]
        list.append(contentsOf: options)
        if !value.isEmpty && !list.contains(value) { list.append(value) }
        return list
    }

    var body: some View {
        Picker(selection: $value) {
            ForEach(choices, id: \.self) { opt in
                Text(opt.isEmpty ? L(emptyLabel) : opt).tag(opt)
            }
        } label: {
            Text(L(label)).foregroundStyle(DatawatchColors.onSurface)
        }
        .pickerStyle(.menu)
        .tint(DatawatchColors.primary)
    }
}

/// Inline status line (✓ success / ✕ error / muted progress).
struct FormStatusLine: View {
    let text: String
    let tone: Int  // 0 muted, 1 success, 2 error

    var body: some View {
        Text(verbatim: text)
            .font(DatawatchFonts.labelSmall)
            .foregroundStyle(color)
            .fixedSize(horizontal: false, vertical: true)
    }

    private var color: Color {
        switch tone {
        case 1: return DatawatchColors.success
        case 2: return DatawatchColors.error
        default: return DatawatchColors.onSurfaceMuted
        }
    }
}

// MARK: - ComputeNode form

/// Add / edit a ComputeNode (PWA openComputeAddPanel; `editName == nil` = add).
struct ComputeNodeFormSheet: View {
    let profile: ServerProfile
    let editName: String?
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var v: [String: String] = ["kind": "ollama", "routing": "direct"]
    @State private var loading = false
    @State private var saving = false
    @State private var testing = false
    @State private var status: String?
    @State private var statusTone = 0
    @State private var observers: [String] = []
    @State private var proxyPeers: [String] = []

    private var isEdit: Bool { editName != nil }

    var body: some View {
        NavigationStack {
            Form {
                if loading {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("Loading…").foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                } else {
                    ComputeIdentitySection(v: $v, isEdit: isEdit)
                    ComputeRoutingSection(v: $v, proxyPeers: proxyPeers)
                    observerSection
                    ComputeHardwareSection(v: $v)
                    if let name = editName, (v["kind"] ?? "") == "ollama" {
                        ComputeModelsSection(profile: profile, nodeName: name)
                    }
                    statusSection
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle(isEdit ? L("Edit ComputeNode") : L("Add ComputeNode"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarContent }
            .task { load() }
        }
        .dwThemed()
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

    private var observerSection: some View {
        Section {
            Picker(selection: binding("observer_peer")) {
                Text(L("(none)")).tag("")
                ForEach(observers, id: \.self) { name in
                    Text(verbatim: name).tag(name)
                }
            } label: {
                Text("Observer peer (datawatch-stats)").foregroundStyle(DatawatchColors.onSurface)
            }
            .pickerStyle(.menu)
            .tint(DatawatchColors.primary)
        } footer: {
            Text("Picks a registered datawatch-stats observer. Monitoring endpoint + hardware are inferred from the observer's heartbeat.")
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private var statusSection: some View {
        Section {
            if let status {
                FormStatusLine(text: status, tone: statusTone)
            }
            Button {
                testConnection()
            } label: {
                HStack {
                    Label("Test Connection", systemImage: "antenna.radiowaves.left.and.right")
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
        let current = v["observer_peer"] ?? ""
        IosSettingsForms.shared.observerChoices(profile: profile, current: current) { list in
            DispatchQueue.main.async { observers = list }
        }
        IosSettingsLists.shared.load(profile: profile, kind: "remote_servers", onSuccess: { rows in
            let names: [String] = rows.map { $0.title }
            DispatchQueue.main.async { proxyPeers = names }
        }, onError: { _ in })
        guard let name = editName else { return }
        loading = true
        IosSettingsForms.shared.loadComputeNode(profile: profile, name: name, onSuccess: { map in
            DispatchQueue.main.async {
                v = map
                loading = false
                let peer = map["observer_peer"] ?? ""
                if !peer.isEmpty && !observers.contains(peer) { observers.insert(peer, at: 0) }
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                loading = false
                status = msg
                statusTone = 2
            }
        })
    }

    private func testConnection() {
        testing = true
        status = L("Testing…")
        statusTone = 0
        IosSettingsForms.shared.testComputeDraft(
            profile: profile,
            kind: v["kind"] ?? "ollama",
            address: v["address"] ?? "",
            onSuccess: { summary in
                DispatchQueue.main.async {
                    testing = false
                    status = "✓ " + L("Reachable") + ": " + summary
                    statusTone = 1
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    testing = false
                    status = "✕ " + msg
                    statusTone = 2
                }
            }
        )
    }

    private func save() {
        saving = true
        status = nil
        IosSettingsForms.shared.saveComputeNode(profile: profile, editName: editName ?? "", values: v) { err in
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

/// Name / Kind / Address (PWA field order).
private struct ComputeIdentitySection: View {
    @Binding var v: [String: String]
    let isEdit: Bool

    var body: some View {
        Section {
            FormTextField(
                label: "Name",
                text: binding("name"),
                placeholder: "gpu-1",
                hint: "Short identifier, lowercase letters/numbers/dashes (e.g. gpu-1, dev-laptop, prod-h100)",
                disabled: isEdit
            )
            VStack(alignment: .leading, spacing: 4) {
                FormChoiceRow(
                    label: "Kind (LLM-API protocol)",
                    value: binding("kind"),
                    options: IosSettingsForms.shared.computeKinds
                )
                Text("ollama = native Ollama API · openai-compat = OpenAI /v1 (covers OpenWebUI, vLLM, LMStudio, OpenAI itself) · gemini-api = Google Generative Language · opencode-api = opencode /v1")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            FormTextField(
                label: "Address (host:port or URL)",
                text: binding("address"),
                placeholder: "https://gpu-1:11434",
                hint: "http:// or https:// auto-added if missing. Not used for docker-network or datawatch-proxy routing."
            )
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { v[key] ?? "" }, set: { v[key] = $0 })
    }
}

/// Routing mode + docker-network / datawatch-proxy sub-sections (BL322).
private struct ComputeRoutingSection: View {
    @Binding var v: [String: String]
    let proxyPeers: [String]

    private var routing: String { v["routing"] ?? "direct" }

    var body: some View {
        Section {
            Picker(selection: binding("routing")) {
                Text("direct — use Address field directly").tag("direct")
                Text("docker-network — manage container via Docker CLI").tag("docker-network")
                Text("datawatch-proxy — forward through a federated peer").tag("datawatch-proxy")
            } label: {
                Text("Routing mode").foregroundStyle(DatawatchColors.onSurface)
            }
            .pickerStyle(.menu)
            .tint(DatawatchColors.primary)
            if routing == "docker-network" {
                dockerFields
            } else if routing == "datawatch-proxy" {
                proxyFields
            }
        } footer: {
            Text("direct = stable host; docker-network = daemon manages container lifecycle; datawatch-proxy = peer routes the request.")
        }
        .listRowBackground(DatawatchColors.surface)
    }

    @ViewBuilder
    private var dockerFields: some View {
        FormTextField(label: "Image (required)", text: binding("dn_image"), placeholder: "ollama/ollama:latest")
        FormTextField(label: "Network name", text: binding("dn_network"), placeholder: "datawatch-llm")
        FormTextField(label: "Port", text: binding("dn_port"), placeholder: "11434", numeric: true)
        FormTextField(label: "Container name (optional)", text: binding("dn_container"), placeholder: "datawatch-ollama")
        FormTextField(label: "Docker endpoint (optional)", text: binding("dn_endpoint"), placeholder: "unix:///var/run/docker.sock")
        VStack(alignment: .leading, spacing: 4) {
            Text("Env vars (one KEY=VALUE per line, optional)")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            TextField("OLLAMA_NUM_GPU=1", text: binding("dn_env"), axis: .vertical)
                .font(DatawatchFonts.terminalSmall)
                .lineLimit(2...6)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
        Toggle("Auto-start container on probe", isOn: flag("dn_auto_start"))
            .tint(DatawatchColors.primary)
        Toggle("Auto-pull image if missing", isOn: flag("dn_auto_pull"))
            .tint(DatawatchColors.primary)
    }

    @ViewBuilder
    private var proxyFields: some View {
        FormChoiceRow(
            label: "Peer (registered server)",
            value: binding("dp_peer"),
            options: proxyPeers,
            emptyLabel: "(select peer)"
        )
        FormTextField(label: "Remote LLM name (on peer)", text: binding("dp_remote_llm"), placeholder: "llama3")
        FormTextField(label: "Timeout (seconds)", text: binding("dp_timeout"), placeholder: "30", numeric: true)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { v[key] ?? "" }, set: { v[key] = $0 })
    }

    private func flag(_ key: String) -> Binding<Bool> {
        Binding(get: { (v[key] ?? "") == "true" }, set: { v[key] = $0 ? "true" : "false" })
    }
}

/// Hardware + declared capacity (hidden for SaaS openai-compat endpoints, PWA Q3).
private struct ComputeHardwareSection: View {
    @Binding var v: [String: String]

    private static let saasHosts: [String] = [
        "api.openai.com", ".azure.com", "generativelanguage.googleapis.com", "api.anthropic.com",
        "api.together.xyz", "api.groq.com", "api.mistral.ai",
    ]

    private var hideHardware: Bool {
        guard (v["kind"] ?? "") == "openai-compat" else { return false }
        let addr = (v["address"] ?? "").lowercased()
        return Self.saasHosts.contains { addr.contains($0) }
    }

    /// PWA computeRecomputeMax: floor(VRAM per GPU × GPU count ÷ 8), min 1.
    private var computedMax: Int {
        let vram = Int(v["hw_vram"] ?? "") ?? 0
        let count = Int(v["hw_gpu_count"] ?? "") ?? 0
        if vram <= 0 || count <= 0 { return 0 }
        return max(1, (vram * count) / 8)
    }

    var body: some View {
        if !hideHardware {
            Section {
                FormTextField(label: "OS (linux/macos/windows)", text: binding("hw_os"))
                FormTextField(label: "Arch (x86_64/arm64)", text: binding("hw_arch"))
                FormTextField(label: "GPU vendor (nvidia/amd/apple/none)", text: binding("hw_gpu_vendor"))
                FormTextField(label: "GPU model (h100/rtx-4090/m3-max)", text: binding("hw_gpu_model"))
                FormTextField(label: "GPU count", text: binding("hw_gpu_count"), numeric: true)
                FormTextField(label: "VRAM GB (per GPU)", text: binding("hw_vram"), numeric: true)
                FormTextField(label: "RAM GB", text: binding("hw_memory_gb"), numeric: true)
                FormTextField(label: "CPU cores", text: binding("hw_cpu_cores"), numeric: true)
            } header: {
                Text("Hardware (auto-detect via observer or set manually)")
            }
            .listRowBackground(DatawatchColors.surface)
        }
        Section {
            FormTextField(label: "Max concurrent models (declared)", text: binding("max_models"), placeholder: "2", numeric: true)
            if computedMax > 0 {
                HStack {
                    Text(L("computed: ~") + String(computedMax))
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Spacer()
                    if (v["max_models"] ?? "") != String(computedMax) {
                        Button(L("[Use →]")) { v["max_models"] = String(computedMax) }
                            .font(DatawatchFonts.labelSmall)
                            .buttonStyle(.borderless)
                            .foregroundStyle(DatawatchColors.primary)
                    }
                }
            }
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { v[key] ?? "" }, set: { v[key] = $0 })
    }
}
