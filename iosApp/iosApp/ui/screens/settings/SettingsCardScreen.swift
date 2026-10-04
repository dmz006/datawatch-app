import SwiftUI
import DatawatchShared

/// Detail screen for one Settings card (pushed from `SettingsView`). Carries
/// the per-card docs link (D26a) and dispatches on the card's content kind.
struct SettingsCardScreen: View {
    @EnvironmentObject private var store: ServerProfileStore
    let card: SettingsCard

    private var profile: ServerProfile? {
        store.activeProfile
    }

    var body: some View {
        content
            .background(DatawatchColors.background)
            .navigationTitle(L(card.title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    DocsLinkButton(profile: profile, anchor: card.docsAnchor)
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch card.content {
        case .custom(let custom):
            SettingsCustomCardView(card: custom, profile: profile)
        case .config(let fields, let extra):
            if let p = profile {
                SettingsConfigCardView(profile: p, fields: fields, extra: extra)
            } else {
                SettingsNoServerView()
            }
        case .list(let kind, let add, let cardActions):
            if let p = profile {
                SettingsListCardView(profile: p, kind: kind, addFields: add, cardActions: cardActions)
            } else {
                SettingsNoServerView()
            }
        }
    }
}

/// Shown when a server-backed card is opened with no server profile.
struct SettingsNoServerView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "server.rack")
                .font(.largeTitle)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No server connected")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("Add a server under Settings › Comms › Servers.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// MARK: - Restart-needed inline link (D57b, PWA restartHint / "Restart now")

struct RestartNeededRow: View {
    let profile: ServerProfile
    @State private var confirming = false
    @State private var status: String?

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(status.map { L($0) } ?? L("Restart required to apply changes."))
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.warning)
            Spacer(minLength: 4)
            Button("Restart now") { confirming = true }
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.primary)
                .buttonStyle(.borderless)
        }
        .listRowBackground(DatawatchColors.surface)
        .confirmationDialog("Restart the datawatch daemon?", isPresented: $confirming, titleVisibility: .visible) {
            Button("Restart", role: .destructive) { restart() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Active sessions briefly lose their connection while the daemon restarts.")
        }
    }

    private func restart() {
        status = "Restarting…"
        IosSettingsConfig.shared.restartDaemon(profile: profile) { err in
            DispatchQueue.main.async {
                status = err.map { "Restart failed: \($0)" } ?? "Restart requested."
            }
        }
    }
}

// MARK: - Schema-driven config card (PWA GENERAL/COMMS/LLM_CONFIG_FIELDS)

struct SettingsConfigCardView: View {
    let profile: ServerProfile
    let fields: [SettingsField]
    let extra: SettingsConfigExtra

    @State private var values: [String: String] = [:]
    @State private var isLoading = true
    @State private var error: String?
    @State private var interfaces: [String] = []
    @State private var llms: [String] = []
    @State private var saving = 0
    @State private var restartNeeded = false

    var body: some View {
        List {
            if isLoading {
                Section {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("Loading…").foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .listRowBackground(DatawatchColors.surface)
                }
            } else {
                Section {
                    ForEach(fields) { field in
                        SettingsFieldRow(
                            field: field,
                            value: values[field.key] ?? "",
                            options: options(for: field),
                            onCommit: { newValue in save(field, newValue) }
                        )
                        .listRowBackground(DatawatchColors.surface)
                    }
                } footer: {
                    if saving > 0 {
                        Text("Saving…").foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                }
                if restartNeeded {
                    Section { RestartNeededRow(profile: profile) }
                }
                extraSection
            }
            if let err = error {
                Section {
                    Text(err)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.interactively)
        .task { load() }
        .refreshable { load() }
    }

    @ViewBuilder
    private var extraSection: some View {
        switch extra {
        case .none:
            EmptyView()
        case .summarizerTest:
            Section { SummarizerTestRow(profile: profile) }
        case .scanDefaults:
            ScanDefaultsSection(profile: profile)
        }
    }

    private func options(for field: SettingsField) -> [String] {
        switch field.kind {
        case .interface: return interfaces
        case .llm: return llms
        default: return field.options
        }
    }

    private func load() {
        IosSettingsConfig.shared.load(profile: profile, onSuccess: { map in
            DispatchQueue.main.async {
                values = map
                isLoading = false
                error = nil
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                isLoading = false
                error = msg
            }
        })
        if fields.contains(where: { $0.kind == .interface }) {
            IosSettingsConfig.shared.interfaces(profile: profile) { list in
                DispatchQueue.main.async { interfaces = list }
            }
        }
        if fields.contains(where: { $0.kind == .llm }) {
            IosSettingsConfig.shared.llmNames(profile: profile) { list in
                DispatchQueue.main.async { llms = list }
            }
        }
    }

    private func save(_ field: SettingsField, _ newValue: String) {
        if field.kind == .password && newValue.trimmingCharacters(in: .whitespaces).isEmpty { return }
        if field.kind != .password && (values[field.key] ?? "") == newValue { return }
        let previous = values[field.key]
        if field.kind != .password { values[field.key] = newValue }
        saving += 1
        IosSettingsConfig.shared.write(profile: profile, key: field.key, kind: field.writeKind, value: newValue) { err in
            DispatchQueue.main.async {
                saving = max(0, saving - 1)
                if let err {
                    values[field.key] = previous
                    error = L("Save failed") + ": " + err
                } else {
                    error = nil
                    if field.kind == .password { values[field.key] = "***" }
                    if field.needsRestart { restartNeeded = true }
                }
            }
        }
    }
}

/// POST /api/summarizer/test (PWA Session AI Summarizer card).
private struct SummarizerTestRow: View {
    let profile: ServerProfile
    @State private var running = false
    @State private var result: String?
    @State private var ok = false

    var body: some View {
        HStack {
            if let result {
                Text(result)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(ok ? DatawatchColors.success : DatawatchColors.error)
            }
            Spacer()
            if running {
                ProgressView().controlSize(.small)
            } else {
                Button("Test Summarizer") { run() }
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.primary)
                    .buttonStyle(.borderless)
            }
        }
        .listRowBackground(DatawatchColors.surface)
    }

    private func run() {
        running = true
        result = nil
        IosServiceLocator.shared.testSummarizer(profile: profile, onSuccess: { latency in
            DispatchQueue.main.async {
                ok = true
                result = "✓ ok · \(latency)ms"
                running = false
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                ok = false
                result = "✗ \(msg)"
                running = false
            }
        })
    }
}

// MARK: - One config field

struct SettingsFieldRow: View {
    let field: SettingsField
    let value: String
    let options: [String]
    let onCommit: (String) -> Void

    var body: some View {
        switch field.kind {
        case .toggle:
            Toggle(isOn: Binding(
                get: { value.lowercased() == "true" },
                set: { onCommit($0 ? "true" : "false") }
            )) {
                SettingsFieldLabel(text: field.label)
            }
            .tint(DatawatchColors.primary)
        case .select, .interface, .llm:
            SettingsPickerRow(field: field, value: value, options: options, onCommit: onCommit)
        case .readonly:
            VStack(alignment: .leading, spacing: 4) {
                SettingsFieldLabel(text: field.label)
                Text(value.isEmpty ? "—" : value)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
            }
        default:
            SettingsTextRow(field: field, value: value, onCommit: onCommit)
        }
    }
}

struct SettingsFieldLabel: View {
    let text: String
    var body: some View {
        Text(L(text))
            .font(DatawatchFonts.bodyMedium)
            .foregroundStyle(DatawatchColors.onSurface)
            .fixedSize(horizontal: false, vertical: true)
    }
}

private struct SettingsPickerRow: View {
    let field: SettingsField
    let value: String
    let options: [String]
    let onCommit: (String) -> Void

    private var choices: [String] {
        var list: [String] = field.kind == .select ? [] : [""]
        list.append(contentsOf: options)
        if !value.isEmpty && !list.contains(value) { list.append(value) }
        return list
    }

    var body: some View {
        Picker(selection: Binding(get: { value }, set: { onCommit($0) })) {
            ForEach(choices, id: \.self) { opt in
                Text(opt.isEmpty ? L("— default —") : opt).tag(opt)
            }
        } label: {
            SettingsFieldLabel(text: field.label)
        }
        .pickerStyle(.menu)
        .tint(DatawatchColors.primary)
    }
}

/// Text / number / secret / csv / lines input. Commits on Return or when
/// focus leaves (PWA saves onchange). Secrets never show the stored value —
/// only a "configured" placeholder — and blank secret input is not sent.
private struct SettingsTextRow: View {
    let field: SettingsField
    let value: String
    let onCommit: (String) -> Void

    @State private var text: String = ""
    @State private var seeded = false
    @FocusState private var focused: Bool

    private var displayValue: String {
        switch field.kind {
        case .password: return ""
        case .csv: return value.replacingOccurrences(of: "\n", with: ", ")
        default: return value
        }
    }

    private var placeholder: String {
        if field.kind == .password && !value.isEmpty { return L("(configured — enter to change)") }
        return field.placeholder
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            SettingsFieldLabel(text: field.label)
            input
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .focused($focused)
                .onSubmit { commit() }
        }
        .onAppear {
            if !seeded {
                text = displayValue
                seeded = true
            }
        }
        .onChange(of: value) { _ in
            if !focused { text = displayValue }
        }
        .onChange(of: focused) { isFocused in
            if !isFocused { commit() }
        }
    }

    @ViewBuilder
    private var input: some View {
        switch field.kind {
        case .password:
            SecureField(placeholder, text: $text)
        case .number:
            TextField(placeholder, text: $text)
                .keyboardType(.numbersAndPunctuation)
        case .lines:
            TextField(placeholder, text: $text, axis: .vertical)
                .lineLimit(2...10)
        default:
            TextField(placeholder, text: $text)
        }
    }

    private func commit() {
        if field.kind == .password {
            let v = text.trimmingCharacters(in: .whitespaces)
            guard !v.isEmpty else { return }
            onCommit(v)
            text = ""
            return
        }
        if text == displayValue { return }
        onCommit(text)
    }
}
