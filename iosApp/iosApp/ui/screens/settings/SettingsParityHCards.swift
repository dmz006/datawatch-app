import SwiftUI
import AVFoundation
import UniformTypeIdentifiers
import DatawatchShared

// iOS-H parity cards: Whisper "Test transcription" (PWA testWhisperBackend),
// Docs Search trust queue (PWA loadDocsTrustPanel), File Service (PWA
// loadFileServicePanel), Federated Observer quicklink, Evals recent runs.

// MARK: - Whisper test (Voice Input card)

/// PWA `whisper.test_button` row — opens the record-and-transcribe sheet.
struct WhisperTestRow: View {
    let profile: ServerProfile
    @State private var showSheet = false

    var body: some View {
        Button(L("Test transcription endpoint")) { showSheet = true }
            .foregroundStyle(DatawatchColors.primary)
            .listRowBackground(DatawatchColors.surface)
            .sheet(isPresented: $showSheet) {
                WhisperTestSheet(profile: profile)
            }
    }
}

/// Mic → /api/voice/transcribe → transcript, with the PWA status line
/// (`idle` / `recording…` / `transcribing…` / `ok (Nms, N chars)`).
private struct WhisperTestSheet: View {
    let profile: ServerProfile
    @Environment(\.dismiss) private var dismiss
    @State private var recorder: VoiceRecorder?
    @State private var transcribing = false
    @State private var status: String = "idle"
    @State private var transcript: String = ""
    @State private var startedAt: Date = Date()

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Tap the microphone to start recording, tap again to stop. The transcribed text appears below. Verifies the configured Whisper backend end-to-end (mic → /api/voice/transcribe → text).")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                Section { micRow }
                Section {
                    Text(verbatim: transcript.isEmpty ? L("(transcript will appear here)") : transcript)
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(transcript.isEmpty ? DatawatchColors.onSurfaceMuted : DatawatchColors.onSurface)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, minHeight: 80, alignment: .topLeading)
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle(L("Test transcription"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { close() }
                }
            }
        }
    }

    private var micRow: some View {
        HStack(spacing: 12) {
            Button { toggle() } label: {
                Image(systemName: recorder == nil ? "mic.circle.fill" : "stop.circle.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(recorder == nil ? DatawatchColors.primary : DatawatchColors.error)
            }
            .buttonStyle(.borderless)
            .disabled(transcribing)
            .accessibilityLabel(recorder == nil ? L("Start recording") : L("Stop recording"))
            if transcribing { ProgressView().controlSize(.small) }
            Text(verbatim: status)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Spacer()
        }
    }

    private func toggle() {
        if let rec = recorder {
            recorder = nil
            guard let audio = rec.stop(), audio.count > 0 else {
                status = L("no audio captured")
                return
            }
            send(audio)
            return
        }
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            DispatchQueue.main.async {
                guard granted else {
                    status = L("mic permission denied")
                    return
                }
                let rec = VoiceRecorder()
                do {
                    try rec.start()
                    recorder = rec
                    status = L("recording — tap ■ to stop")
                } catch {
                    status = L("mic start failed")
                }
            }
        }
    }

    private func send(_ audio: Data) {
        transcribing = true
        status = L("transcribing…")
        startedAt = Date()
        IosServiceLocator.shared.transcribeAudioData(
            audioData: audio,
            audioMime: VoiceRecorder.mimeType,
            sessionId: nil,
            profile: profile,
            onSuccess: { text in
                DispatchQueue.main.async { finished(text) }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    transcribing = false
                    status = L("transcribe failed:") + " " + msg
                }
            }
        )
    }

    private func finished(_ text: String) {
        transcribing = false
        let ms: Int = Int(Date().timeIntervalSince(startedAt) * 1000.0)
        if text.isEmpty {
            transcript = L("(empty transcript — backend returned no text)")
            status = L("transcribed empty — backend may be misconfigured")
        } else {
            transcript = text
            status = "ok (\(ms)ms, \(text.count) chars)"
        }
    }

    private func close() {
        recorder?.cancel()
        recorder = nil
        dismiss()
    }
}

// MARK: - Docs Search trust queue

/// Pending sources awaiting trust (bulk select + Trust / Dismiss) and the
/// trusted-sources list (swipe to remove; `core` is not removable).
struct DocsTrustSections: View {
    let profile: ServerProfile
    @State private var pending: [DocsTrustEntry] = []
    @State private var trusted: [DocsTrustEntry] = []
    @State private var selected: Set<String> = []
    @State private var loaded = false
    @State private var error: String?
    @State private var exportYaml: String?

    var body: some View {
        Group {
            Section {
                pendingRows
            } header: {
                Text("Pending sources awaiting trust")
            }
            .listRowBackground(DatawatchColors.surface)
            Section {
                trustedRows
            } header: {
                Text("Trusted sources")
            } footer: {
                if let error {
                    Text(verbatim: error).foregroundStyle(DatawatchColors.error)
                }
            }
            .listRowBackground(DatawatchColors.surface)
            Section {
                Button("Export YAML") { export() }
                    .foregroundStyle(DatawatchColors.primary)
            }
            .listRowBackground(DatawatchColors.surface)
        }
        .task { load() }
        .sheet(isPresented: exportShown) {
            DocsTrustExportSheet(yaml: exportYaml ?? "")
        }
    }

    private var exportShown: Binding<Bool> {
        Binding(get: { exportYaml != nil }, set: { if !$0 { exportYaml = nil } })
    }

    /// PWA docsTrustExport: GET /api/docs/trust/export → yaml_snippet modal.
    private func export() {
        IosYamlRecall.shared.docsTrustExport(profile: profile, onSuccess: { yaml in
            DispatchQueue.main.async {
                error = nil
                exportYaml = yaml
            }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
    }

    @ViewBuilder
    private var pendingRows: some View {
        if !loaded {
            CardSkeleton()
        } else if pending.isEmpty {
            Text("none").italic().foregroundStyle(DatawatchColors.onSurfaceMuted)
        } else {
            bulkBar
            ForEach(pending, id: \.source) { e in
                DocsPendingRow(
                    entry: e,
                    isSelected: selected.contains(e.source),
                    onSelect: { toggle(e.source) },
                    onTrust: { decide([e.source], accept: true) },
                    onDismiss: { decide([e.source], accept: false) }
                )
            }
        }
    }

    private var bulkBar: some View {
        HStack(spacing: 10) {
            Button {
                let all: Bool = selected.count == pending.count
                selected = all ? [] : Set(pending.map { $0.source })
            } label: {
                Label(L("select all"), systemImage: selected.count == pending.count ? "checkmark.square" : "square")
                    .font(DatawatchFonts.labelSmall)
            }
            .buttonStyle(.borderless)
            Spacer()
            Button(L("Trust selected")) { decide(Array(selected), accept: true) }
                .buttonStyle(.borderless)
                .foregroundStyle(DatawatchColors.success)
                .disabled(selected.isEmpty)
            Button(L("Dismiss selected")) { decide(Array(selected), accept: false) }
                .buttonStyle(.borderless)
                .disabled(selected.isEmpty)
        }
        .font(DatawatchFonts.labelSmall)
    }

    @ViewBuilder
    private var trustedRows: some View {
        if loaded && trusted.isEmpty {
            Text("none").italic().foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        ForEach(trusted, id: \.source) { e in
            HStack {
                Text(verbatim: e.source)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                Spacer()
                Text(verbatim: e.detail)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                if e.source != "core" {
                    Button(role: .destructive) { remove(e.source) } label: {
                        Label("Remove", systemImage: "trash")
                    }
                }
            }
        }
    }

    private func toggle(_ source: String) {
        if selected.contains(source) { selected.remove(source) } else { selected.insert(source) }
    }

    private func load() {
        IosSettingsH.shared.docsTrust(profile: profile, onSuccess: { t in
            DispatchQueue.main.async {
                pending = t.pending
                trusted = t.trusted
                selected = selected.intersection(Set(t.pending.map { $0.source }))
                loaded = true
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                error = msg
                loaded = true
            }
        })
    }

    private func decide(_ sources: [String], accept: Bool) {
        guard !sources.isEmpty else { return }
        IosSettingsH.shared.docsTrustDecide(profile: profile, sources: sources, accept: accept) { err in
            DispatchQueue.main.async {
                error = err
                selected = []
                load()
            }
        }
    }

    private func remove(_ source: String) {
        IosSettingsH.shared.docsTrustRemove(profile: profile, source: source) { err in
            DispatchQueue.main.async {
                error = err
                load()
            }
        }
    }
}

/// PWA docsTrustExport modal: the YAML snippet plus the config.yaml paste hint.
private struct DocsTrustExportSheet: View {
    let yaml: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    Text(verbatim: yaml)
                        .font(DatawatchFonts.terminalSmall)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(10)
                        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 4))
                    Text("Paste this into your config.yaml's docs_search.trust block to make runtime trust survive a wipe.")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                .padding()
            }
            .background(DatawatchColors.background)
            .navigationTitle(L("Trust list — YAML for config.yaml"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .dwThemed()
    }
}

private struct DocsPendingRow: View {
    let entry: DocsTrustEntry
    let isSelected: Bool
    let onSelect: () -> Void
    let onTrust: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        HStack(spacing: 8) {
            Button(action: onSelect) {
                Image(systemName: isSelected ? "checkmark.square" : "square")
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(L("Select"))
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: entry.source)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                if !entry.detail.isEmpty {
                    Text(verbatim: entry.detail)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            Spacer(minLength: 4)
            Button(L("Trust"), action: onTrust)
                .buttonStyle(.borderless)
                .foregroundStyle(DatawatchColors.success)
            Button(L("Dismiss"), action: onDismiss)
                .buttonStyle(.borderless)
        }
        .font(DatawatchFonts.labelSmall)
    }
}

// MARK: - File Service

/// PWA File Service card: editable root, storage overview, upload, refresh.
struct SettingsFileServiceCard: View {
    let profile: ServerProfile
    @State private var info: IosFileService?
    @State private var rootInput = ""
    @State private var error: String?
    @State private var status: String?
    @State private var saving = false
    @State private var pickedURL: URL?
    @State private var uploadPath = ""
    @State private var uploading = false
    @State private var showImporter = false

    var body: some View {
        List {
            rootSection
            overviewSection
            uploadSection
            if error != nil || status != nil {
                Section { messages }.listRowBackground(DatawatchColors.surface)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .task { load() }
        .refreshable { load() }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { load() } label: { Image(systemName: "arrow.clockwise") }
                    .accessibilityLabel("Refresh")
            }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [UTType.item]) { result in
            if case .success(let url) = result {
                pickedURL = url
                if uploadPath.isEmpty { uploadPath = url.lastPathComponent }
            }
        }
    }

    private var rootSection: some View {
        Section {
            HStack {
                TextField("/data/files", text: $rootInput)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                if saving {
                    ProgressView().controlSize(.small)
                } else {
                    Button("Save") { saveRoot() }
                        .buttonStyle(.borderless)
                        .disabled(info == nil || rootInput.trimmingCharacters(in: .whitespaces) == (info?.root ?? ""))
                }
            }
        } header: {
            Text("File service root")
        } footer: {
            Text("Leave blank to use session.root_path or home directory.")
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private var overviewSection: some View {
        Section {
            if let info {
                countRow(L("Discussions"), info.discussions.count)
                countRow(L("Peers"), info.peers.count)
            } else {
                CardSkeleton()
            }
        } header: {
            Text("Storage overview")
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func countRow(_ label: String, _ n: Int) -> some View {
        HStack {
            Text(verbatim: label).foregroundStyle(DatawatchColors.onSurface)
            Spacer()
            Text(verbatim: "\(n)").foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }

    private var uploadSection: some View {
        Section {
            Button {
                showImporter = true
            } label: {
                Label(pickedURL?.lastPathComponent ?? L("Choose File"), systemImage: "doc")
            }
            TextField("/path/in/service", text: $uploadPath)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            HStack {
                Button(uploading ? L("Uploading…") : L("Upload file")) { upload() }
                    .disabled(uploading || pickedURL == nil)
                if uploading { ProgressView().controlSize(.small) }
            }
        } header: {
            Text("Upload file")
        }
        .listRowBackground(DatawatchColors.surface)
    }

    @ViewBuilder
    private var messages: some View {
        if let error {
            Text(verbatim: error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
        }
        if let status {
            Text(verbatim: status).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.success)
        }
    }

    private func load() {
        IosSettingsH.shared.fileService(profile: profile, onSuccess: { m in
            DispatchQueue.main.async {
                info = m
                rootInput = m.root
                error = nil
            }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
    }

    private func saveRoot() {
        saving = true
        IosSettingsH.shared.setFileServiceRoot(profile: profile, path: rootInput) { err in
            DispatchQueue.main.async {
                saving = false
                error = err
                if err == nil {
                    status = L("Saved")
                    load()
                }
            }
        }
    }

    private func upload() {
        guard let url = pickedURL else { return }
        let scoped: Bool = url.startAccessingSecurityScopedResource()
        let data: Data? = try? Data(contentsOf: url)
        if scoped { url.stopAccessingSecurityScopedResource() }
        guard let data else {
            error = L("Could not read the selected file.")
            return
        }
        uploading = true
        status = nil
        let dest: String = uploadPath
        IosSettingsH.shared.uploadFile(profile: profile, data: data, fileName: url.lastPathComponent, destPath: dest) { err in
            DispatchQueue.main.async {
                uploading = false
                error = err
                if err == nil {
                    status = L("Uploaded to") + " " + (dest.isEmpty ? url.lastPathComponent : dest)
                    pickedURL = nil
                    uploadPath = ""
                    load()
                }
            }
        }
    }
}

// MARK: - Federated Observer quicklink

/// PWA `observer_quicklink`: one-click jump to the Observer tab.
struct SettingsObserverQuicklinkCard: View {
    var body: some View {
        List {
            Section {
                Button {
                    if let url = URL(string: "datawatch://observer") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Label(L("Open Observer view →"), systemImage: "binoculars")
                }
                .foregroundStyle(DatawatchColors.primary)
            } header: {
                Text("Mode + peers")
            } footer: {
                Text("The Observer view holds: shape A/B/C deployment toggle · push targets · peer registry CRUD (mint/revoke bearer tokens) · per-peer stats with last-push age.")
            }
            .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }
}

// MARK: - Evals recent runs

/// PWA `_renderEvalsPanel` "Recent Runs" (GET /api/evals run history).
/// Loaded by SettingsListCardView (with the suites, and again after a Run).
struct EvalRunsSection: View {
    let runs: [IosEvalRun]

    var body: some View {
        if !runs.isEmpty {
            Section {
                ForEach(runs, id: \.id) { r in EvalRunRow(run: r) }
            } header: {
                Text("Recent Runs")
            }
            .listRowBackground(DatawatchColors.surface)
        }
    }
}

private struct EvalRunRow: View {
    let run: IosEvalRun

    var body: some View {
        HStack(spacing: 8) {
            Text(verbatim: run.passed ? "PASS" : "FAIL")
                .font(DatawatchFonts.badge)
                .foregroundStyle(run.passed ? DatawatchColors.success : DatawatchColors.error)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: run.name)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                if !run.createdAt.isEmpty {
                    Text(verbatim: run.createdAt)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            Spacer()
            Text(verbatim: "\(run.scorePercent)%")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }
}

// MARK: - LLM "In use…"

/// PWA `llmLoadInUse` / Android LlmDetailDialog "In use" tab: sessions routed
/// through one LLM, paged 10 at a time.
struct LlmInUseSheet: View {
    let profile: ServerProfile
    let name: String
    @Environment(\.dismiss) private var dismiss
    @State private var sessions: [IosLlmSession] = []
    @State private var total: Int = 0
    @State private var page: Int = 1
    @State private var loading = true
    @State private var error: String?

    private let pageSize: Int = 10

    private var totalPages: Int { max(1, (total + pageSize - 1) / pageSize) }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    rows
                } footer: {
                    if total > pageSize { pager }
                }
                .listRowBackground(DatawatchColors.surface)
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle(L("In use…"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task(id: page) { load() }
        }
    }

    @ViewBuilder
    private var rows: some View {
        if let error {
            Text(verbatim: error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
        } else if loading && sessions.isEmpty {
            CardSkeleton()
        } else if sessions.isEmpty {
            Text("No sessions are using this LLM.").foregroundStyle(DatawatchColors.onSurfaceMuted)
        } else {
            ForEach(sessions, id: \.id) { s in LlmInUseRow(session: s) }
        }
    }

    private var pager: some View {
        HStack(spacing: 10) {
            Spacer()
            Button("◀ Prev") { page -= 1 }.disabled(page <= 1)
            Text(verbatim: "\(page)/\(totalPages) · \(total)")
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Button("Next ▶") { page += 1 }.disabled(page >= totalPages)
            Spacer()
        }
        .font(DatawatchFonts.labelSmall)
        .buttonStyle(.borderless)
    }

    private func load() {
        loading = true
        IosSettingsH.shared.llmInUse(profile: profile, name: name, page: Int32(page), size: Int32(pageSize), onSuccess: { r in
            DispatchQueue.main.async {
                sessions = r.sessions
                total = Int(r.total)
                loading = false
                error = nil
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                loading = false
                error = msg
            }
        })
    }
}

private struct LlmInUseRow: View {
    let session: IosLlmSession

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(verbatim: session.id)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                Spacer()
                Text(verbatim: session.state)
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            if !session.task.isEmpty {
                Text(verbatim: session.task)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(2)
            }
        }
    }
}
