import SwiftUI
import DatawatchShared

/// Launch Automaton wizard (parity B14; PWA openLaunchAutomatonWizard / Android
/// NewPrdDialog): template strip → intent (auto-detected type, overridable) →
/// optional title → workspace (profile or directory + Browse) → execution backend /
/// model / effort → planning backend → Advanced (guided, scan, rules, per-story
/// approval, skills hint) → memory.
struct NewPrdView: View {
    let profile: ServerProfile
    var onCreated: (String) -> Void = { _ in }
    /// PWA "Or start from a template → Browse": switches Automata to Templates.
    var onBrowseTemplates: (() -> Void)? = nil

    @Environment(\.dismiss) private var dismiss
    @State private var options: IosPrdWizardOptions? = nil
    @State private var title = ""
    @State private var spec = ""
    @State private var projectProfile = ""
    @State private var projectDir = ""
    @State private var backend = ""
    @State private var model = ""
    @State private var effort = ""
    @State private var planningBackend = ""
    @State private var planningTouched = false
    @State private var planningModel = ""
    /// PWA `_wizardState.type` / `typeOverridden`.
    @State private var type = "software"
    @State private var typeOverridden = false
    /// PWA Advanced: guided is sent; scan / rules / story approval are UI-only (as in PWA + Android).
    @State private var guidedMode = false
    @State private var scanEnabled = true
    @State private var rulesEnabled = true
    @State private var storyApproval = false
    @State private var showDirBrowser = false
    private static let types = ["software", "research", "operational", "personal"]
    /// D73a (Android NewPrdDialog #175): memory seed / harvest + promote-to scope.
    @State private var memorySeed = false
    @State private var memoryHarvest = false
    @State private var promoteTo = "story-shared"
    private static let promoteScopes = ["session-local", "story-shared", "prd-shared", "project-shared"]
    /// Display labels for the server's memory scope ids (Terminology Rule: never "PRD").
    private static func scopeLabel(_ id: String) -> String {
        switch id {
        case "session-local": return "Session-local"
        case "story-shared": return "Story-shared"
        case "prd-shared": return "Automaton-shared"
        case "project-shared": return "Project-shared"
        default: return id
        }
    }
    @State private var submitting = false
    @State private var errorMessage: String? = nil

    private var profileMode: Bool { !projectProfile.isEmpty }
    /// PWA/Android: the intent is required; the title is auto-derived when blank.
    private var canSubmit: Bool {
        !submitting && !spec.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    private func models(for b: String) -> [String] {
        guard let o = options else { return [] }
        return IosAutomata.shared.modelsFor(options: o, backend: b)
    }

    var body: some View {
        NavigationStack {
            Form {
                if onBrowseTemplates != nil { templateStrip }
                intentSection
                workspaceSection
                if !profileMode, let o = options {
                    executionSection(o)
                    planningSection(o)
                }
                advancedSection
                memorySection

                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .font(DatawatchFonts.bodyMedium)
                            .foregroundStyle(DatawatchColors.error)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .overlay { if options == nil { ProgressView().tint(DatawatchColors.primary) } }
            .navigationTitle("Launch Automaton")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                // PWA wizard header "?" (automata_wizard_help_tip) → howto/automata-wizard.md.
                ToolbarItem(placement: .principal) {
                    HStack(spacing: 4) {
                        Text("Launch Automaton").font(.headline)
                        DocsLinkButton(profile: profile, anchor: "", docPath: "howto/automata-wizard.md")
                            .accessibilityHint("Open the Launch Automaton howto — covers every wizard field including Advanced switches")
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if submitting {
                        ProgressView()
                    } else {
                        Button("Launch") { submit() }
                            .fontWeight(.semibold)
                            .disabled(!canSubmit)
                    }
                }
            }
            .sheet(isPresented: $showDirBrowser) {
                DirectoryBrowserSheet(profile: profile, startPath: projectDir) { picked in projectDir = picked }
            }
            .onAppear {
                guard options == nil else { return }
                IosAutomata.shared.loadWizardOptions(profile: profile) { o in
                    DispatchQueue.main.async { options = o }
                }
            }
            .onChange(of: spec) { text in
                if !typeOverridden { type = IosPrdWizard.shared.inferType(intent: text) }
            }
            .onChange(of: backend) { b in
                if !models(for: b).contains(model) { model = "" }
                // Mirror execution into planning until the operator picks one (Android behaviour).
                if !planningTouched { planningBackend = b }
            }
            .onChange(of: planningBackend) { b in
                if !models(for: b).contains(planningModel) { planningModel = "" }
            }
        }
        .dwThemed()
    }

    private var templateStrip: some View {
        Section {
            HStack(spacing: 8) {
                Text("📦").accessibilityHidden(true)
                Text("Or start from a template →")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer(minLength: 4)
                Button("Browse") {
                    onBrowseTemplates?()
                    dismiss()
                }
                .buttonStyle(.bordered)
            }
        }
    }

    private var intentSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 4) {
                Text("What do you want to accomplish?")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                TextEditor(text: $spec)
                    .frame(minHeight: 110)
                    .font(DatawatchFonts.bodyMedium)
                    .scrollContentBackground(.hidden)
                    .accessibilityLabel("Intent")
            }
            detectedRow
            TextField("Title", text: $title, prompt: Text("Auto-derived from intent if blank"))
        }
    }

    /// PWA wizard-detected-row: accent2 "Detected: <type>" pill + override dropdown.
    private var detectedRow: some View {
        HStack(spacing: 8) {
            HStack(spacing: 4) {
                Text("Detected:").opacity(0.7)
                Text(type)
            }
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(DatawatchColors.secondary)
            .padding(.horizontal, 10)
            .padding(.vertical, 3)
            .background(DatawatchColors.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.secondary, lineWidth: 1))
            .accessibilityHint("Auto-inferred from your intent text. Override with the type menu.")
            Spacer(minLength: 4)
            Picker("Type", selection: Binding(
                get: { type },
                set: { type = $0; typeOverridden = true }
            )) {
                ForEach(Self.types, id: \.self) { Text($0).tag($0) }
            }
            .pickerStyle(.menu)
            .labelsHidden()
        }
    }

    private var workspaceSection: some View {
        Section("Workspace") {
            if let o = options, !o.projectProfiles.isEmpty {
                Picker("Profile", selection: $projectProfile) {
                    Text("— project directory —").tag("")
                    ForEach(o.projectProfiles, id: \.self) { Text($0).tag($0) }
                }
            }
            if !profileMode {
                HStack(spacing: 8) {
                    TextField("Project directory", text: $projectDir, prompt: Text("/path/to/project"))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("Browse…") { showDirBrowser = true }
                        .buttonStyle(.borderless)
                        .accessibilityHint("Browse folders on the server")
                }
            }
        }
    }

    private func executionSection(_ o: IosPrdWizardOptions) -> some View {
        Section("Execution") {
            Picker("Backend", selection: $backend) {
                Text("Daemon default").tag("")
                ForEach(o.backends, id: \.self) { Text($0).tag($0) }
            }
            if !models(for: backend).isEmpty {
                Picker("Model", selection: $model) {
                    Text("Default").tag("")
                    ForEach(models(for: backend), id: \.self) { Text($0).tag($0) }
                }
            }
            if !o.efforts.isEmpty {
                Picker("Effort", selection: $effort) {
                    Text("Default").tag("")
                    ForEach(o.efforts, id: \.self) { Text($0).tag($0) }
                }
            }
        }
    }

    private func planningSection(_ o: IosPrdWizardOptions) -> some View {
        Section {
            Picker("Planning backend", selection: Binding(
                get: { planningBackend },
                set: { planningBackend = $0; planningTouched = true }
            )) {
                Text("Same as execution").tag("")
                ForEach(o.backends, id: \.self) { Text($0).tag($0) }
            }
            if !models(for: planningBackend).isEmpty {
                Picker("Decomposition model", selection: $planningModel) {
                    Text("Default").tag("")
                    ForEach(models(for: planningBackend), id: \.self) { Text($0).tag($0) }
                }
            }
        } header: {
            Text("Planning")
        } footer: {
            Text("The backend that decomposes the spec into stories and tasks.")
        }
    }

    /// PWA wizard-advanced-details (collapsed by default).
    private var advancedSection: some View {
        Section {
            DisclosureGroup("Advanced") {
                Toggle("Guided Mode (pre-planning session)", isOn: $guidedMode)
                Toggle("Security scan", isOn: $scanEnabled)
                Toggle("Rules check (after scan)", isOn: $rulesEnabled)
                Toggle("Per-story approval", isOn: $storyApproval)
                // PWA automata_wizard_skills_hint_link / Android onOpenSettings.
                Button {
                    dismiss()
                    SettingsDeepLink.open(cardId: "gc_projectprofiles")
                } label: {
                    Text("💡 Configure skills in Settings → Agents → Project Profiles → Skills ↗")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.secondary)
                        .multilineTextAlignment(.leading)
                }
                .buttonStyle(.borderless)
            }
        }
    }

    private var memorySection: some View {
        Section("Memory") {
            Toggle(isOn: $memorySeed) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Seed from prior memory")
                    Text("Inject relevant memories at task start")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            Toggle(isOn: $memoryHarvest) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Harvest learnings on completion")
                    Text("Promote memories when the automaton finishes")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            if memoryHarvest {
                Picker("Promote to scope", selection: $promoteTo) {
                    ForEach(Self.promoteScopes, id: \.self) { Text(L(Self.scopeLabel($0))).tag($0) }
                }
            }
        }
    }

    private func submit() {
        guard canSubmit else { return }
        submitting = true
        errorMessage = nil
        IosPrdWizard.shared.createPrd(
            profile: profile,
            title: title,
            spec: spec,
            type: type,
            guidedMode: guidedMode,
            projectDir: projectDir,
            projectProfile: projectProfile,
            backend: backend,
            model: model,
            effort: effort,
            planningBackend: planningBackend == backend ? "" : planningBackend,
            planningModel: planningModel,
            memorySeed: memorySeed,
            memoryHarvest: memoryHarvest,
            promoteTo: promoteTo,
            onSuccess: { id in
                DispatchQueue.main.async {
                    submitting = false
                    onCreated(id)
                    dismiss()
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    submitting = false
                    errorMessage = msg
                }
            }
        )
    }
}

/// Edit PRD title / spec (PWA prdEditMenu).
struct EditPrdView: View {
    let profile: ServerProfile
    let prdId: String
    @State var title: String
    @State var spec: String
    /// D75a (Android EditPrdDialog): claude-code permission mode; "" = inherit.
    var currentPermissionMode: String = ""
    var onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var saving = false
    @State private var errorMessage: String? = nil
    @State private var permissionModes: [String] = []
    @State private var permissionMode: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section("Title") {
                    TextField("Title", text: $title)
                }
                Section("Spec") {
                    TextEditor(text: $spec)
                        .frame(minHeight: 220)
                        .font(DatawatchFonts.terminalSmall)
                        .scrollContentBackground(.hidden)
                }
                if !permissionModes.isEmpty {
                    Section {
                        Picker("Permission mode", selection: Binding(
                            get: { permissionMode ?? currentPermissionMode },
                            set: { permissionMode = $0 }
                        )) {
                            Text("Inherit").tag("")
                            ForEach(permissionModes, id: \.self) { Text($0).tag($0) }
                        }
                    } footer: {
                        Text("Most specific wins: task › automaton › session default.")
                    }
                }
                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .font(DatawatchFonts.bodyMedium)
                            .foregroundStyle(DatawatchColors.error)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle("Edit Automaton")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if saving {
                        ProgressView()
                    } else {
                        Button("Save") { save() }.fontWeight(.semibold)
                    }
                }
            }
            .onAppear {
                guard permissionModes.isEmpty else { return }
                IosExtras.shared.permissionModes(profile: profile) { list in
                    DispatchQueue.main.async {
                        var modes: [String] = list
                        if !currentPermissionMode.isEmpty && !modes.contains(currentPermissionMode) {
                            modes.append(currentPermissionMode)
                        }
                        permissionModes = modes
                    }
                }
            }
        }
        .dwThemed()
    }

    private func save() {
        saving = true
        errorMessage = nil
        // Android sends permission_mode only when it changed (blank = unchanged).
        let changedMode: String = (permissionMode != nil && permissionMode != currentPermissionMode) ? (permissionMode ?? "") : ""
        IosExtras.shared.editPrd(
            profile: profile, prdId: prdId, title: title, spec: spec, permissionMode: changedMode,
            onSuccess: {
                DispatchQueue.main.async {
                    saving = false
                    onSaved()
                    dismiss()
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    saving = false
                    errorMessage = msg
                }
            }
        )
    }
}

/// Set LLM on an existing PRD (parity B16; PWA prdSetModel* / Android LlmOverrideDialog):
/// execution backend / model / effort + planning backend / decomposition model → set_llm.
struct SetPrdLlmView: View {
    let profile: ServerProfile
    let prd: PrdDto
    var onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var options: IosPrdWizardOptions? = nil
    @State private var backend = ""
    @State private var model = ""
    @State private var effort = ""
    @State private var planningBackend = ""
    @State private var planningModel = ""
    @State private var saving = false
    @State private var errorMessage: String? = nil

    private func models(for b: String) -> [String] {
        guard let o = options else { return [] }
        return IosAutomata.shared.modelsFor(options: o, backend: b)
    }

    var body: some View {
        NavigationStack {
            Form {
                if let o = options {
                    Section("Execution") {
                        Picker("Backend", selection: $backend) {
                            Text("Unchanged").tag("")
                            ForEach(o.backends, id: \.self) { Text($0).tag($0) }
                        }
                        if !models(for: backend.isEmpty ? (prd.backend ?? "") : backend).isEmpty {
                            Picker("Model", selection: $model) {
                                Text("Unchanged").tag("")
                                ForEach(models(for: backend.isEmpty ? (prd.backend ?? "") : backend), id: \.self) { Text($0).tag($0) }
                            }
                        }
                        if !o.efforts.isEmpty {
                            Picker("Effort", selection: $effort) {
                                Text("Unchanged").tag("")
                                ForEach(o.efforts, id: \.self) { Text($0).tag($0) }
                            }
                        }
                    }
                    Section("Planning") {
                        Picker("Planning backend", selection: $planningBackend) {
                            Text("Unchanged").tag("")
                            ForEach(o.backends, id: \.self) { Text($0).tag($0) }
                        }
                        if !models(for: planningBackend).isEmpty {
                            Picker("Decomposition model", selection: $planningModel) {
                                Text("Unchanged").tag("")
                                ForEach(models(for: planningBackend), id: \.self) { Text($0).tag($0) }
                            }
                        }
                    }
                    Section {
                        Text("Current: \(prd.backend ?? "daemon default")\(prd.model.map { "/\($0)" } ?? "")\(prd.effort.map { " · " + L("effort") + " \($0)" } ?? "")")
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                }
                if let errorMessage {
                    Section { Text(errorMessage).foregroundStyle(DatawatchColors.error).font(DatawatchFonts.bodyMedium) }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .overlay { if options == nil { ProgressView().tint(DatawatchColors.primary) } }
            .navigationTitle("Set LLM")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() } else {
                        Button("Save") { save() }
                            .fontWeight(.semibold)
                            .disabled([backend, model, effort, planningBackend, planningModel].allSatisfy { $0.isEmpty })
                    }
                }
            }
            .onAppear {
                guard options == nil else { return }
                IosAutomata.shared.loadWizardOptions(profile: profile) { o in DispatchQueue.main.async { options = o } }
            }
            .onChange(of: backend) { _ in model = "" }
            .onChange(of: planningBackend) { _ in planningModel = "" }
        }
        .dwThemed()
    }

    private func save() {
        var body: [String: String] = ["actor": "operator"]
        if !backend.isEmpty { body["backend"] = backend }
        if !effort.isEmpty { body["effort"] = effort }
        if !model.isEmpty { body["model"] = model }
        if !planningBackend.isEmpty { body["decomposition_profile"] = planningBackend }
        if !planningModel.isEmpty { body["decomposition_model"] = planningModel }
        saving = true
        errorMessage = nil
        Task {
            do {
                try await ServiceLocatorAsync.prdAction(profile: profile, prdId: prd.id, action: "set_llm", body: body)
                await MainActor.run { saving = false; onSaved(); dismiss() }
            } catch {
                await MainActor.run { saving = false; errorMessage = error.localizedDescription }
            }
        }
    }
}

/// PRD settings panel (parity B16; PWA prdSettings*).
struct PrdSettingsView: View {
    let profile: ServerProfile
    let prd: PrdDto
    var onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var type = ""
    @State private var guided = false
    @State private var continueOnFailure = "inherit"
    @State private var priority = 3
    @State private var readDirs = ""
    @State private var writeDirs = ""
    @State private var saving = false
    @State private var errorMessage: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Type", selection: $type) {
                        ForEach(["software", "research", "operational", "personal"], id: \.self) { Text($0).tag($0) }
                    }
                    Toggle("Guided mode", isOn: $guided)
                    Picker("On story failure", selection: $continueOnFailure) {
                        Text("Server default").tag("inherit")
                        Text("Continue").tag("on")
                        Text("Stop (blocked)").tag("off")
                    }
                    Stepper("Priority \(priority)", value: $priority, in: 1...9)
                } footer: {
                    Text("Guided mode pauses each story for approval. Higher priority runs first when capacity is limited.")
                }
                Section {
                    TextField("Read dirs (comma-separated)", text: $readDirs, axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(DatawatchFonts.terminalSmall)
                    TextField("Write dirs (comma-separated)", text: $writeDirs, axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(DatawatchFonts.terminalSmall)
                } header: {
                    Text("Scope")
                }
                if let errorMessage {
                    Section { Text(errorMessage).foregroundStyle(DatawatchColors.error).font(DatawatchFonts.bodyMedium) }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle("Automaton settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() } else { Button("Save") { save() }.fontWeight(.semibold) }
                }
            }
            .onAppear {
                type = prd.type?.isEmpty == false ? prd.type! : "software"
                guided = prd.guidedMode
                continueOnFailure = prd.continueOnStoryFailure.map { $0.boolValue ? "on" : "off" } ?? "inherit"
                priority = Int(prd.priority)
                readDirs = prd.readDirs.joined(separator: ", ")
                writeDirs = prd.writeDirs.joined(separator: ", ")
            }
        }
        .dwThemed()
    }

    private func save() {
        saving = true
        errorMessage = nil
        IosPrdSettings.shared.apply(
            profile: profile, prd: prd, type: type, guidedMode: guided, continueOnFailure: continueOnFailure,
            priority: Int32(priority), readDirs: readDirs, writeDirs: writeDirs
        ) { err in
            DispatchQueue.main.async {
                saving = false
                if let err { errorMessage = err } else { onSaved(); dismiss() }
            }
        }
    }
}
