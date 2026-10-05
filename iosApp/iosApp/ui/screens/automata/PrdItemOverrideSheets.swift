import SwiftUI
import DatawatchShared

// Story / task LLM + execution-profile overrides and template instantiate
// (PWA renderStory ⚙ / 🤖, renderTask ✎ "Edit spec + LLM", openPRDInstantiateModal).

/// PWA `.prd-story-profile-pill` / `.prd-task-llm-badge` / `.prd-task-spawn-badge`.
struct PrdMiniPill: View {
    let text: String
    var color: Color = DatawatchColors.onSurfaceMuted

    var body: some View {
        Text(text)
            .font(DatawatchFonts.badge)
            .foregroundStyle(color)
            .lineLimit(1)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .background(color.opacity(0.10), in: Capsule())
            .overlay(Capsule().stroke(color.opacity(0.45), lineWidth: 1))
    }
}

/// What the override sheet edits.
enum PrdOverrideEdit: Identifiable {
    case storyLlm(PrdStoryDto)
    case storyProfile(PrdStoryDto)
    case taskSpecLlm(PrdTaskDto)
    case instantiate

    var id: String {
        switch self {
        case .storyLlm(let s): return "sl-\(s.id)"
        case .storyProfile(let s): return "sp-\(s.id)"
        case .taskSpecLlm(let t): return "tl-\(t.id)"
        case .instantiate: return "inst"
        }
    }
}

struct PrdOverrideSheet: View {
    let profile: ServerProfile
    let prdId: String
    let edit: PrdOverrideEdit
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var options: IosPrdWizardOptions? = nil
    @State private var spec = ""
    @State private var backend = ""
    @State private var effort = ""
    @State private var model = ""
    @State private var profileName = ""
    @State private var vars = ""
    /// Backend the model field belongs to — a change clears the model (not the prefill).
    @State private var modelBackend = ""
    @State private var saving = false
    @State private var errorMessage: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                formContent
                if let errorMessage {
                    Section { Text(errorMessage).foregroundStyle(DatawatchColors.error).font(DatawatchFonts.bodyMedium) }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle(L(navTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() } else {
                        Button { save() } label: {
                            if isInstantiate { Text("Instantiate") } else { Text("Save") }
                        }
                        .fontWeight(.semibold)
                    }
                }
            }
            .onAppear(perform: prefill)
        }
        .dwThemed()
    }

    private var isInstantiate: Bool {
        if case .instantiate = edit { return true }
        return false
    }

    private var navTitle: String {
        switch edit {
        case .storyLlm: return "Story LLM override"
        case .storyProfile: return "Override execution profile"
        case .taskSpecLlm: return "Edit task"
        case .instantiate: return "Instantiate"
        }
    }

    @ViewBuilder
    private var formContent: some View {
        switch edit {
        case .storyLlm:
            llmSection(hint: "Per-story LLM override — empty inherits from the Automaton then the global config. All tasks in this story inherit this unless they have their own override.")
        case .taskSpecLlm:
            Section("Spec") {
                TextEditor(text: $spec)
                    .font(DatawatchFonts.bodyMedium)
                    .frame(minHeight: 140)
            }
            .listRowBackground(DatawatchColors.surface)
            llmSection(hint: "Per-task LLM override — empty inherits automaton then global.")
        case .storyProfile(let s):
            Section {
                Picker("Profile", selection: $profileName) {
                    Text("(inherit automaton default)").tag("")
                    ForEach(profileChoices(current: s.executionProfile ?? ""), id: \.self) { Text($0).tag($0) }
                }
            } header: {
                Text(verbatim: "Story " + s.id)
            } footer: {
                Text("Empty = inherit the Automaton's default execution profile. A name overrides for this story only.")
            }
            .listRowBackground(DatawatchColors.surface)
        case .instantiate:
            Section {
                TextField("Template vars (k=v,k=v)", text: $vars)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            .listRowBackground(DatawatchColors.surface)
        }
    }

    private func profileChoices(current: String) -> [String] {
        var list: [String] = options?.projectProfiles ?? []
        if !current.isEmpty && !list.contains(current) { list.append(current) }
        return list
    }

    private func modelChoices() -> [String] {
        guard let o = options else { return [] }
        var list: [String] = IosAutomata.shared.modelsFor(options: o, backend: backend)
        if !model.isEmpty && !list.contains(model) { list.append(model) }
        return list
    }

    private func effortChoices() -> [String] {
        var list: [String] = options?.efforts ?? []
        if list.isEmpty { list = ["low", "medium", "high", "max"] }
        if !effort.isEmpty && !list.contains(effort) { list.append(effort) }
        return list
    }

    private func backendChoices() -> [String] {
        var list: [String] = options?.backends ?? []
        if !backend.isEmpty && !list.contains(backend) { list.append(backend) }
        return list
    }

    @ViewBuilder
    private func llmSection(hint: String) -> some View {
        Section {
            Picker("Backend", selection: $backend) {
                Text("(inherit)").tag("")
                ForEach(backendChoices(), id: \.self) { Text($0).tag($0) }
            }
            .onChange(of: backend) { newValue in
                if newValue != modelBackend { model = ""; modelBackend = newValue }
            }
            Picker("Effort", selection: $effort) {
                Text("(inherit)").tag("")
                ForEach(effortChoices(), id: \.self) { Text($0).tag($0) }
            }
            if !backend.isEmpty {
                if modelChoices().isEmpty {
                    TextField("Model (optional)", text: $model)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } else {
                    Picker("Model (optional)", selection: $model) {
                        Text("(inherit)").tag("")
                        ForEach(modelChoices(), id: \.self) { Text($0).tag($0) }
                    }
                }
            }
        } footer: {
            Text(L(hint))
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func prefill() {
        switch edit {
        case .storyLlm(let s):
            backend = s.backend ?? ""; effort = s.effort ?? ""; model = s.model ?? ""
            modelBackend = backend
        case .taskSpecLlm(let t):
            spec = t.spec.isEmpty ? t.task : t.spec
            backend = t.backend ?? ""; effort = t.effort ?? ""; model = t.model ?? ""
            modelBackend = backend
        case .storyProfile(let s):
            profileName = s.executionProfile ?? ""
        case .instantiate:
            break
        }
        if options == nil {
            IosAutomata.shared.loadWizardOptions(profile: profile) { o in
                DispatchQueue.main.async { options = o }
            }
        }
    }

    private func save() {
        saving = true
        errorMessage = nil
        Task {
            do {
                try await performSave()
                await MainActor.run { saving = false; onSaved(); dismiss() }
            } catch {
                await MainActor.run { saving = false; errorMessage = error.localizedDescription }
            }
        }
    }

    private func performSave() async throws {
        switch edit {
        case .storyLlm(let s):
            let body: [String: String] = ["story_id": s.id, "backend": backend, "effort": effort, "model": model, "actor": "operator"]
            try await ServiceLocatorAsync.prdAction(profile: profile, prdId: prdId, action: "set_story_llm", body: body)
        case .storyProfile(let s):
            let body: [String: String] = ["story_id": s.id, "profile": profileName, "actor": "operator"]
            try await ServiceLocatorAsync.prdAction(profile: profile, prdId: prdId, action: "set_story_profile", body: body)
        case .taskSpecLlm(let t):
            let original: String = t.spec.isEmpty ? t.task : t.spec
            let trimmed: String = spec.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty && spec != original {
                try await editTaskSpec(taskId: t.id, spec: spec)
            }
            let llmChanged: Bool = backend != (t.backend ?? "") || effort != (t.effort ?? "") || model != (t.model ?? "")
            if llmChanged {
                let body: [String: String] = ["task_id": t.id, "backend": backend, "effort": effort, "model": model, "actor": "operator"]
                try await ServiceLocatorAsync.prdAction(profile: profile, prdId: prdId, action: "set_task_llm", body: body)
            }
        case .instantiate:
            try await instantiate()
        }
    }

    private func editTaskSpec(taskId: String, spec: String) async throws {
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            IosPrdItemEdit.shared.editTaskSpec(profile: profile, prdId: prdId, taskId: taskId, spec: spec) { err in
                if let err { cont.resume(throwing: ServiceLocatorAsync.TransportError(message: err)) } else { cont.resume(returning: ()) }
            }
        }
    }

    /// PWA openPRDInstantiateModal: `k=v,k=v` → `{vars, actor}`.
    private func instantiate() async throws {
        var parsed: [String: String] = [:]
        for kv in vars.split(separator: ",") {
            let part = String(kv)
            guard let eq = part.firstIndex(of: "=") else { continue }
            let key = part[..<eq].trimmingCharacters(in: .whitespaces)
            if key.isEmpty { continue }
            parsed[key] = part[part.index(after: eq)...].trimmingCharacters(in: .whitespaces)
        }
        let payload: [String: Any] = ["vars": parsed, "actor": "operator"]
        let data = try JSONSerialization.data(withJSONObject: payload)
        let json: String = String(data: data, encoding: .utf8) ?? "{}"
        try await withCheckedThrowingContinuation { (cont: CheckedContinuation<Void, Error>) in
            IosServiceLocator.shared.prdAction(
                profile: profile,
                prdId: prdId,
                action: "instantiate",
                bodyJson: json,
                onSuccess: { cont.resume(returning: ()) },
                onError: { cont.resume(throwing: ServiceLocatorAsync.TransportError(message: $0)) }
            )
        }
    }
}

// ── Live planning stream (PWA _startDecomposeStream) ───────────────────────

@MainActor
final class PrdDecomposeLiveModel: ObservableObject {
    @Published private(set) var state: DecomposeLiveState? = nil
    private var subscription: IosSubscription? = nil
    private var prdId: String? = nil

    /// Opens the stream once per planning run; [onEnd] refreshes the automaton.
    func start(profile: ServerProfile, prdId: String, onEnd: @escaping () -> Void) {
        if subscription != nil && self.prdId == prdId { return }
        stop()
        self.prdId = prdId
        state = DecomposeLiveState(stories: [], done: 0, total: 0, finished: false, storyCount: 0, error: nil)
        subscription = IosDecomposeStream.shared.watch(
            profile: profile,
            prdId: prdId,
            onState: { [weak self] s in
                Task { @MainActor [weak self] in self?.state = s }
            },
            onEnd: { [weak self] in
                Task { @MainActor [weak self] in
                    onEnd()
                    self?.finish()
                }
            }
        )
    }

    private func finish() {
        subscription = nil
        guard let s = state else { return }
        // Keep ✓ / ✗ visible briefly like the PWA (3 s / 8 s), then hide.
        let nanos: UInt64 = s.error != nil ? 8_000_000_000 : (s.finished ? 3_000_000_000 : 0)
        Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: nanos)
            guard let self, self.subscription == nil else { return }
            self.state = nil
        }
    }

    func stop() {
        subscription?.cancel()
        subscription = nil
        state = nil
    }
}

/// PWA `#decompose-progress-<id>` box: spinner + "(done/total)" + "+ title" per streamed story.
struct PrdDecomposeLiveCard: View {
    let state: DecomposeLiveState

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            headline
            if state.isActive {
                ForEach(state.stories, id: \.index) { s in
                    Text(verbatim: "+ " + (s.title.isEmpty ? "Story \(s.index + 1)" : s.title))
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(2)
                        .padding(.leading, 16)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.secondary.opacity(0.08))
        .overlay(alignment: .leading) { Rectangle().fill(edgeColor).frame(width: 3) }
        .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    private var edgeColor: Color {
        if state.error != nil { return DatawatchColors.error }
        if state.finished { return DatawatchColors.success }
        return DatawatchColors.secondary
    }

    @ViewBuilder
    private var headline: some View {
        if let err = state.error {
            Text(verbatim: "✗ " + L("Decompose failed") + ": " + err)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.error)
        } else if state.finished {
            Text("✓ \(Int(state.storyCount)) stories generated")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.success)
        } else {
            HStack(spacing: 6) {
                ProgressView().controlSize(.mini)
                Text(verbatim: L("Decomposing Automaton…") + (state.total > 0 ? " (\(state.done)/\(state.total))" : ""))
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
            }
        }
    }
}
