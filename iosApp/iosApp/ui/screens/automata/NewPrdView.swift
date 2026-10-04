import SwiftUI
import DatawatchShared

/// Launch Automaton wizard (parity B14; PWA wizard / Android NewPrdDialog):
/// title → spec → workspace (project profile or directory) → execution backend /
/// model / effort → planning backend + decomposition model.
struct NewPrdView: View {
    let profile: ServerProfile
    var onCreated: (String) -> Void = { _ in }

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
    @State private var submitting = false
    @State private var errorMessage: String? = nil

    private var profileMode: Bool { !projectProfile.isEmpty }
    private var canSubmit: Bool {
        !submitting && !title.trimmingCharacters(in: .whitespaces).isEmpty
    }
    private func models(for b: String) -> [String] {
        guard let o = options else { return [] }
        return IosAutomata.shared.modelsFor(options: o, backend: b)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Title", text: $title, prompt: Text("What should this automaton build?"))
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Spec (optional)")
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        TextEditor(text: $spec)
                            .frame(minHeight: 110)
                            .font(DatawatchFonts.bodyMedium)
                            .scrollContentBackground(.hidden)
                            .accessibilityLabel("Spec")
                    }
                }

                Section("Workspace") {
                    if let o = options, !o.projectProfiles.isEmpty {
                        Picker("Profile", selection: $projectProfile) {
                            Text("— project directory —").tag("")
                            ForEach(o.projectProfiles, id: \.self) { Text($0).tag($0) }
                        }
                    }
                    if !profileMode {
                        TextField("Project directory", text: $projectDir, prompt: Text("/path/to/project"))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                }

                if !profileMode, let o = options {
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
                ToolbarItem(placement: .confirmationAction) {
                    if submitting {
                        ProgressView()
                    } else {
                        Button("Create") { submit() }
                            .fontWeight(.semibold)
                            .disabled(!canSubmit)
                    }
                }
            }
            .onAppear {
                guard options == nil else { return }
                IosAutomata.shared.loadWizardOptions(profile: profile) { o in
                    DispatchQueue.main.async { options = o }
                }
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
        .preferredColorScheme(.dark)
    }

    private func submit() {
        guard canSubmit else { return }
        submitting = true
        errorMessage = nil
        IosAutomata.shared.createPrd(
            profile: profile,
            title: title,
            spec: spec,
            projectDir: projectDir,
            projectProfile: projectProfile,
            backend: backend,
            model: model,
            effort: effort,
            planningBackend: planningBackend == backend ? "" : planningBackend,
            planningModel: planningModel,
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
    var onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var saving = false
    @State private var errorMessage: String? = nil

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
        }
        .preferredColorScheme(.dark)
    }

    private func save() {
        saving = true
        errorMessage = nil
        IosAutomata.shared.editPrd(
            profile: profile, prdId: prdId, title: title, spec: spec,
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
                        Text("Current: \(prd.backend ?? "daemon default")\(prd.model.map { "/\($0)" } ?? "")\(prd.effort.map { " · effort \($0)" } ?? "")")
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
        .preferredColorScheme(.dark)
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
        .preferredColorScheme(.dark)
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
