import SwiftUI
import DatawatchShared

/// New Session form (parity B1). Field order follows the PWA modal (spec §9):
/// name → task → project directory → profile / cluster → LLM + compute node →
/// LLM options (permission mode, model, effort) → Chrome / git toggles →
/// recently finished sessions (restart). Submits through `IosNewSession.submit`,
/// which uses `/api/sessions/start` for a directory and `/api/agents` for a profile.
struct NewSessionView: View {
    let profile: ServerProfile
    /// Called with the new session id after a successful start.
    var onStarted: (String) -> Void = { _ in }

    @Environment(\.dismiss) private var dismiss

    @State private var options: IosNewSessionOptions? = nil
    @State private var loading = true

    @State private var name = ""
    @State private var task = ""
    @State private var workingDir = ""
    @State private var projectProfile = ""
    @State private var clusterProfile = ""
    @State private var llmName = ""
    @State private var computeNode = ""
    @State private var permissionMode = ""
    @State private var model = ""
    @State private var effort = ""
    @State private var nonClaudeModels: [String] = []
    @State private var chrome = false
    @State private var autoGitInit = false
    @State private var autoGitCommit = false

    /// D82a: "" = start fresh, else the finished session id to warm-resume.
    @State private var resumeId = ""
    /// D81a: saved-command library for the task field.
    @State private var savedCommands: [IosSavedCommand] = []

    @State private var submitting = false
    @State private var errorMessage: String? = nil
    @State private var restartingId: String? = nil
    /// PWA openDirBrowser (08 › Directory browser).
    @State private var showDirBrowser = false
    /// PWA #backendWarn — installed backends per server.
    @ObservedObject private var backendHints = BackendHintStore.shared

    private var pickedLlm: IosLlmChoice? {
        options?.llms.first { $0.name == llmName }
    }
    private var profileMode: Bool { !projectProfile.isEmpty }
    private var showClaudeOptions: Bool {
        guard let o = options, !profileMode else { return false }
        let claudeBackend = pickedLlm?.isClaude ?? true
        return claudeBackend && !(o.permissionModes.isEmpty && o.claudeModels.isEmpty && o.claudeEfforts.isEmpty)
    }
    private var canSubmit: Bool {
        !submitting && !task.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Session name", text: $name, prompt: Text("e.g. Auth refactor"))
                        .autocorrectionDisabled()
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("Task").font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                            Spacer()
                            libraryMenu
                        }
                        TextEditor(text: $task)
                            .frame(minHeight: 110)
                            .font(DatawatchFonts.bodyMedium)
                            .scrollContentBackground(.hidden)
                            .accessibilityLabel("Task description")
                    }
                }

                Section("Where") {
                    HStack(spacing: 8) {
                        TextField("Project directory", text: $workingDir, prompt: Text("/path/to/project"))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Button("Browse…") { showDirBrowser = true }
                            .buttonStyle(.borderless)
                            .accessibilityHint("Browse folders on the server")
                    }
                    .disabled(profileMode)
                    if let o = options, !o.projectProfiles.isEmpty {
                        Picker("Profile", selection: $projectProfile) {
                            Text("— project directory (local checkout) —").tag("")
                            ForEach(o.projectProfiles, id: \.self) { Text($0).tag($0) }
                        }
                        if profileMode && !o.clusterProfiles.isEmpty {
                            Picker("Cluster", selection: $clusterProfile) {
                                Text("— Local service instance —").tag("")
                                ForEach(o.clusterProfiles, id: \.self) { Text($0).tag($0) }
                            }
                        }
                    }
                }

                if let o = options, !profileMode, !o.llms.isEmpty {
                    Section("LLM") {
                        Picker("LLM", selection: $llmName) {
                            Text("Server default").tag("")
                            ForEach(o.llms, id: \.name) { l in Text("\(l.name) · \(l.kind)").tag(l.name) }
                        }
                        .onAppear { backendHints.load(profile) }
                        // PWA #backendWarn (08 › Backend setup hint).
                        if let l = pickedLlm, backendHints.needsSetup(profile, kind: l.kind) {
                            BackendSetupHint(kind: l.kind)
                        }
                        if let l = pickedLlm, l.computeNodes.count >= 2 {
                            Picker("Compute node", selection: $computeNode) {
                                Text("Any").tag("")
                                ForEach(l.computeNodes, id: \.self) { Text($0).tag($0) }
                            }
                        }
                        if let l = pickedLlm, !l.isClaude, !nonClaudeModels.isEmpty {
                            Picker("Model", selection: $model) {
                                Text(l.defaultModel.isEmpty ? "Default" : "Default (\(l.defaultModel))").tag("")
                                ForEach(nonClaudeModels, id: \.self) { Text($0).tag($0) }
                            }
                        }
                    }
                }

                if showClaudeOptions, let o = options {
                    Section("LLM options") {
                        if !o.permissionModes.isEmpty {
                            Picker("Permission mode", selection: $permissionMode) {
                                Text("Default").tag("")
                                ForEach(o.permissionModes, id: \.self) { Text($0).tag($0) }
                            }
                        }
                        if !o.claudeModels.isEmpty {
                            Picker("Model", selection: $model) {
                                Text("Default").tag("")
                                ForEach(o.claudeModels, id: \.self) { Text($0).tag($0) }
                            }
                        }
                        if !o.claudeEfforts.isEmpty {
                            Picker("Effort", selection: $effort) {
                                Text("Default").tag("")
                                ForEach(o.claudeEfforts, id: \.self) { Text($0).tag($0) }
                            }
                        }
                    }
                }

                if !profileMode, let recent = options?.recentDone, !recent.isEmpty {
                    // D82a (Android ResumePickerDropdown / PWA populateResumeDropdown).
                    Section("Resume previous (optional)") {
                        Picker("Resume", selection: $resumeId) {
                            Text("Start fresh").tag("")
                            ForEach(recent, id: \.id) { s in
                                Text(resumeLabel(s)).tag(s.id)
                            }
                        }
                    }
                }

                if !profileMode {
                    Section {
                        Toggle("Chrome integration", isOn: $chrome)
                        Toggle("Git: init repo if missing", isOn: $autoGitInit)
                        Toggle("Git: auto-commit changes", isOn: $autoGitCommit)
                    }
                }

                if let msg = errorMessage {
                    Section {
                        Text(msg)
                            .font(DatawatchFonts.bodyMedium)
                            .foregroundStyle(DatawatchColors.error)
                    }
                }

                if let recent = options?.recentDone, !recent.isEmpty {
                    Section("Recently finished") {
                        ForEach(recent, id: \.id) { s in recentRow(s) }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .overlay {
                if loading { ProgressView().tint(DatawatchColors.primary) }
            }
            .navigationTitle("New Session")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if submitting {
                        ProgressView()
                    } else {
                        Button("Start") { submit() }
                            .fontWeight(.semibold)
                            .disabled(!canSubmit)
                    }
                }
            }
            .onAppear(perform: load)
            .sheet(isPresented: $showDirBrowser) {
                DirectoryBrowserSheet(profile: profile, startPath: workingDir) { picked in workingDir = picked }
            }
            .onChange(of: llmName) { _ in
                computeNode = ""
                model = ""
                effort = ""
                loadNonClaudeModels()
            }
            .onChange(of: projectProfile) { p in
                if p.isEmpty { clusterProfile = "" }
            }
        }
        .dwThemed()
    }

    /// D81a "From library ▾" (Android SavedCommandLibraryDropdown): hidden while the
    /// server has no saved commands; picking one replaces the task text.
    @ViewBuilder
    private var libraryMenu: some View {
        if !savedCommands.isEmpty {
            Menu {
                ForEach(savedCommands, id: \.name) { cmd in
                    Button { task = cmd.command } label: {
                        Text(cmd.name)
                        Text(cmd.command)
                    }
                }
            } label: {
                Text("From library ▾").font(DatawatchFonts.labelSmall)
            }
        }
    }

    private func resumeLabel(_ s: DwSession) -> String {
        let title: String = s.name ?? String((s.taskSummary ?? "(no task)").prefix(60))
        return title + " · " + s.id
    }

    private func recentRow(_ s: DwSession) -> some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text(s.name ?? s.taskSummary ?? s.id)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(1)
                Text(s.id)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            Spacer(minLength: 8)
            if restartingId == s.id {
                ProgressView()
            } else {
                Button("Restart") { restart(s) }
                    .font(DatawatchFonts.labelSmall)
                    .buttonStyle(.bordered)
                    .tint(DatawatchColors.primary)
                    .disabled(restartingId != nil || submitting)
            }
        }
    }

    // MARK: Actions

    private func load() {
        guard options == nil else { return }
        IosNewSession.shared.loadOptions(profile: profile) { o in
            DispatchQueue.main.async {
                options = o
                loading = false
            }
        }
        IosQuickCommands.shared.loadSaved(profile: profile) { list in
            DispatchQueue.main.async { savedCommands = list }
        }
    }

    private func loadNonClaudeModels() {
        nonClaudeModels = []
        guard let l = pickedLlm, !l.isClaude else { return }
        let kind = l.kind
        IosNewSession.shared.loadModels(profile: profile, kind: kind) { list in
            DispatchQueue.main.async {
                if pickedLlm?.kind == kind { nonClaudeModels = list }
            }
        }
    }

    private func submit() {
        guard canSubmit else { return }
        submitting = true
        errorMessage = nil
        IosExtras.shared.startSession(
            profile: profile,
            task: task,
            name: name,
            workingDir: workingDir,
            projectProfile: projectProfile,
            clusterProfile: clusterProfile,
            llm: llmName,
            computeNode: computeNode,
            permissionMode: permissionMode,
            model: model,
            effort: effort,
            chrome: chrome,
            autoGitInit: autoGitInit,
            autoGitCommit: autoGitCommit,
            resumeId: profileMode ? "" : resumeId,
            onSuccess: { id in
                DispatchQueue.main.async {
                    submitting = false
                    onStarted(id)
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

    private func restart(_ s: DwSession) {
        restartingId = s.id
        errorMessage = nil
        IosServiceLocator.shared.restartSession(
            profile: profile,
            sessionId: s.id,
            onSuccess: {
                DispatchQueue.main.async {
                    restartingId = nil
                    onStarted(s.id)
                    dismiss()
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    restartingId = nil
                    errorMessage = msg
                }
            }
        )
    }
}

/// Server folder picker (PWA openDirBrowser / Android FilePickerDialog FolderOnly):
/// current path, ⬆ parent, subfolders, "Use This Folder", "+ New folder".
/// Shared by New Session and the Launch Automaton wizard.
struct DirectoryBrowserSheet: View {
    let profile: ServerProfile
    var startPath: String = ""
    var onPick: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var listing: IosDirListing? = nil
    @State private var loading = true
    @State private var error: String? = nil
    @State private var showNewFolder = false
    @State private var newFolderName = ""

    var body: some View {
        NavigationStack {
            List {
                if let l = listing {
                    Section {
                        Text(l.path)
                            .font(DatawatchFonts.terminalSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .textSelection(.enabled)
                    }
                }
                Section {
                    folderRows
                }
                if let error {
                    Section {
                        Text(error).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .overlay { if loading { ProgressView().tint(DatawatchColors.primary) } }
            .navigationTitle("Choose folder")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { toolbarItems }
            .alert("New folder", isPresented: $showNewFolder) {
                TextField("Folder name", text: $newFolderName)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button("Create") { createFolder() }
                Button("Cancel", role: .cancel) { newFolderName = "" }
            } message: {
                Text(listing?.path ?? "")
            }
            .onAppear { if listing == nil { load(startPath.isEmpty ? "~" : startPath) } }
        }
        .dwThemed()
    }

    @ViewBuilder
    private var folderRows: some View {
        if let parent = listing?.parent {
            Button { load(parent) } label: {
                Label("..", systemImage: "arrow.up")
            }
        }
        ForEach(listing?.dirs ?? [], id: \.path) { d in
            Button { load(d.path) } label: {
                Label(d.name, systemImage: "folder")
                    .foregroundStyle(DatawatchColors.onSurface)
            }
        }
        if let l = listing, l.dirs.isEmpty {
            Text("No subdirectories")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }

    @ToolbarContentBuilder
    private var toolbarItems: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { dismiss() }
        }
        ToolbarItem(placement: .confirmationAction) {
            Button("Use This Folder") {
                if let p = listing?.path { onPick(p) }
                dismiss()
            }
            .fontWeight(.semibold)
            .disabled(listing == nil || loading)
        }
        ToolbarItem(placement: .bottomBar) {
            Button { showNewFolder = true } label: {
                Label("New folder", systemImage: "folder.badge.plus")
            }
            .disabled(listing == nil || loading)
        }
    }

    private func load(_ path: String) {
        loading = true
        error = nil
        IosDirBrowser.shared.list(
            profile: profile, path: path,
            onSuccess: { l in DispatchQueue.main.async { listing = l; loading = false } },
            onError: { msg in DispatchQueue.main.async { error = msg; loading = false } }
        )
    }

    private func createFolder() {
        guard let parent = listing?.path else { return }
        let name = newFolderName
        newFolderName = ""
        IosDirBrowser.shared.mkdir(
            profile: profile, parentPath: parent, name: name,
            onSuccess: { _ in DispatchQueue.main.async { load(parent) } },
            onError: { msg in DispatchQueue.main.async { error = msg } }
        )
    }
}
