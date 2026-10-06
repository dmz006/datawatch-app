import SwiftUI
import DatawatchShared

// Settings › Automata: Algorithm Mode card (PWA BL258 loadAlgorithmPanel) and
// the Autonomous Config scan defaults (PWA loadAutomataSettingsPanel).

// MARK: - Algorithm Mode

struct SettingsAlgorithmModeCard: View {
    let profile: ServerProfile

    @State private var rows: [IosAlgorithmRow] = []
    @State private var isLoading = true
    @State private var error: String?

    var body: some View {
        List {
            Section {
                Text("7-phase Observe→Improve harness. Operator-driven advance.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .listRowBackground(DatawatchColors.surface)
            sessionsSection
            if let error {
                Section {
                    Text(error)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                }
                .listRowBackground(DatawatchColors.surface)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.interactively)
        .task { load() }
        .refreshable { load() }
    }

    @ViewBuilder
    private var sessionsSection: some View {
        Section {
            if isLoading && rows.isEmpty {
                HStack(spacing: 8) {
                    ProgressView()
                    CardSkeleton()
                }
            } else if rows.isEmpty {
                Text("No sessions in Algorithm Mode. Use the API to start one.")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            } else {
                ForEach(rows, id: \.sessionId) { row in
                    AlgorithmSessionRow(profile: profile, row: row) { err in
                        error = err
                        load()
                    }
                }
            }
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func load() {
        IosSettingsForms.shared.algorithmSessions(profile: profile, onSuccess: { list in
            DispatchQueue.main.async {
                rows = list
                isLoading = false
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                isLoading = false
                error = msg
            }
        })
    }
}

/// One session: id, aborted flag, phase-output count, 7-step strip, output field + actions.
private struct AlgorithmSessionRow: View {
    let profile: ServerProfile
    let row: IosAlgorithmRow
    let onDone: (String?) -> Void

    @State private var output = ""
    @State private var busy = false
    @State private var confirmAbort = false
    @State private var confirmReset = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
            AlgorithmPhaseStrip(index: Int(row.phaseIndex))
            TextField("phase output (optional)", text: $output)
                .font(DatawatchFonts.bodyMedium)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            actions
        }
        .padding(.vertical, 4)
        .disabled(busy)
        .confirmationDialog(L("Abort algorithm for ") + row.sessionId + "?", isPresented: $confirmAbort, titleVisibility: .visible) {
            Button("Abort", role: .destructive) { run("abort") }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog(L("Reset algorithm state for ") + row.sessionId + "?", isPresented: $confirmReset, titleVisibility: .visible) {
            Button("Reset", role: .destructive) { run("reset") }
            Button("Cancel", role: .cancel) {}
        }
    }

    private var header: some View {
        HStack(spacing: 6) {
            Text(verbatim: row.sessionId)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(1)
            if row.aborted {
                Text("aborted")
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.error)
            }
            Spacer()
            Text(verbatim: "\(row.historyCount) " + L("phase outputs"))
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }

    private var actions: some View {
        HStack(spacing: 14) {
            Button("Advance") { run("advance") }
                .foregroundStyle(DatawatchColors.primary)
            Button("Edit") { run("edit") }
                .foregroundStyle(DatawatchColors.onSurface)
            Button("Abort") { confirmAbort = true }
                .foregroundStyle(DatawatchColors.warning)
            Button("Reset") { confirmReset = true }
                .foregroundStyle(DatawatchColors.error)
            Spacer()
            if busy { ProgressView().controlSize(.small) }
        }
        .font(DatawatchFonts.labelSmall)
        .buttonStyle(.borderless)
    }

    private func run(_ action: String) {
        if action == "edit" && output.trimmingCharacters(in: .whitespaces).isEmpty {
            onDone(L("Edit requires output text in the field above"))
            return
        }
        busy = true
        IosSettingsForms.shared.algorithmAction(profile: profile, sessionId: row.sessionId, action: action, output: output) { err in
            DispatchQueue.main.async {
                busy = false
                if err == nil { output = "" }
                onDone(err)
            }
        }
    }
}

/// PWA phase strip: 3-letter chips, done = success, active = accent, pending = muted.
private struct AlgorithmPhaseStrip: View {
    let index: Int

    var body: some View {
        HStack(spacing: 3) {
            ForEach(Array(IosSettingsForms.shared.phases.enumerated()), id: \.offset) { pair in
                chip(pair.element, pair.offset)
            }
        }
    }

    private func chip(_ phase: String, _ i: Int) -> some View {
        let color: Color = i == index ? DatawatchColors.primary : (i < index ? DatawatchColors.success : DatawatchColors.onSurfaceMuted)
        let fill: Double = i == index ? 0.15 : (i < index ? 0.10 : 0.0)
        let short: String = String(phase.prefix(1)).uppercased() + String(phase.dropFirst().prefix(2))
        return Text(verbatim: short)
            .font(DatawatchFonts.badge)
            .foregroundStyle(color)
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(color.opacity(fill), in: RoundedRectangle(cornerRadius: 4))
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(color, lineWidth: 1))
            .accessibilityLabel(phase)
    }
}

// MARK: - Scan defaults (Autonomous Config card extra)

/// GET/PUT /api/autonomous/scan/config — each control saves on change (PWA saveAutomataScanField).
struct ScanDefaultsSection: View {
    let profile: ServerProfile

    @State private var values: [String: String] = [:]
    @State private var loaded = false
    @State private var error: String?

    private struct ScanToggle: Identifiable {
        let key: String
        let label: String
        var id: String { key }
    }

    private static let toggles: [ScanToggle] = [
        ScanToggle(key: "enabled", label: "Scan enabled"),
        ScanToggle(key: "sast_enabled", label: "SAST scanner"),
        ScanToggle(key: "secrets_enabled", label: "Secrets scanner"),
        ScanToggle(key: "deps_enabled", label: "Dependency scanner"),
        ScanToggle(key: "rules_grader_enabled", label: "LLM rules grader"),
        ScanToggle(key: "fix_loop_enabled", label: "Auto-fix loop"),
    ]

    var body: some View {
        Section {
            if !loaded {
                HStack(spacing: 8) {
                    ProgressView()
                    CardSkeleton()
                }
            } else {
                ForEach(Self.toggles) { item in
                    Toggle(L(item.label), isOn: flag(item.key))
                        .tint(DatawatchColors.primary)
                }
                FormChoiceRow(label: "Fail on severity", value: choice("fail_on_severity"), options: ["info", "warning", "error", "critical"])
                ScanNumberRow(label: "Max findings (0=unlimited)", value: values["max_findings"] ?? "0") { save("max_findings", "number", $0) }
                ScanNumberRow(label: "Fix loop max retries", value: values["fix_loop_max_retries"] ?? "0") { save("fix_loop_max_retries", "number", $0) }
            }
            if let error {
                Text(error)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
            }
        } header: {
            Text("Scan defaults")
        }
        .listRowBackground(DatawatchColors.surface)
        .task { load() }
    }

    private func flag(_ key: String) -> Binding<Bool> {
        Binding(
            get: { (values[key] ?? "") == "true" },
            set: { on in save(key, "toggle", on ? "true" : "false") }
        )
    }

    private func choice(_ key: String) -> Binding<String> {
        Binding(
            get: { values[key] ?? "error" },
            set: { val in save(key, "text", val) }
        )
    }

    private func load() {
        IosSettingsForms.shared.loadScanConfig(profile: profile, onSuccess: { map in
            DispatchQueue.main.async {
                values = map
                loaded = true
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                loaded = true
                error = L("not available") + " — " + msg
            }
        })
    }

    private func save(_ key: String, _ kind: String, _ value: String) {
        let previous = values[key]
        values[key] = value
        IosSettingsForms.shared.saveScanField(profile: profile, key: key, kind: kind, value: value) { err in
            DispatchQueue.main.async {
                if let err {
                    values[key] = previous
                    error = L("Save failed") + ": " + err
                } else {
                    error = nil
                }
            }
        }
    }
}

/// Numeric scan field that commits on Return / focus loss.
private struct ScanNumberRow: View {
    let label: String
    let value: String
    let onCommit: (String) -> Void

    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        HStack {
            Text(L(label)).foregroundStyle(DatawatchColors.onSurface)
            Spacer()
            TextField("0", text: $text)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 80)
                .focused($focused)
                .onSubmit { commit() }
        }
        .onAppear { text = value }
        .onChange(of: focused) { isFocused in
            if !isFocused { commit() }
        }
    }

    private func commit() {
        let t = text.trimmingCharacters(in: .whitespaces)
        if t == value || Int(t) == nil { return }
        onCommit(t)
    }
}
