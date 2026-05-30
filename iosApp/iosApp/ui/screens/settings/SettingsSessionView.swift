import SwiftUI
import DatawatchShared

/// Settings → Session: session response summarizer controls.
///
/// Surfaced in SettingsView below the Servers section.
/// Requires server v8.8.13+. The summarizer compresses `last_response` to
/// ~3 sentences before it reaches push notifications and alert bodies.
struct SettingsSessionView: View {
    @EnvironmentObject private var store: ServerProfileStore

    @State private var enabled = false
    @State private var llmRef = ""
    @State private var ollamaLlms: [String] = []
    @State private var isLoading = true
    @State private var error: String?
    @State private var isTesting = false
    @State private var testResult: String?

    private var activeProfile: ServerProfile? {
        store.profiles.first { $0.enabled == true }
    }

    var body: some View {
        List {
            if isLoading {
                Section {
                    HStack {
                        ProgressView().padding(.trailing, 8)
                        Text("Loading…")
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .listRowBackground(DatawatchColors.surface)
                }
            } else {
                Section {
                    Toggle(isOn: Binding(
                        get: { enabled },
                        set: { newValue in
                            enabled = newValue
                            saveEnabled(newValue)
                        }
                    )) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Summarize last response")
                                .foregroundStyle(DatawatchColors.onSurface)
                            Text("Compress session output to ~3 sentences for notifications")
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                    }
                    .tint(DatawatchColors.primary)
                    .listRowBackground(DatawatchColors.surface)
                } header: {
                    Text("Session Summarizer")
                }

                if enabled {
                    Section {
                        if ollamaLlms.isEmpty {
                            Text("No Ollama LLMs configured. Add one under Compute → LLMs.")
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                .listRowBackground(DatawatchColors.surface)
                        } else {
                            Picker("Summarizer LLM", selection: Binding(
                                get: { llmRef },
                                set: { newValue in
                                    llmRef = newValue
                                    saveLlmRef(newValue)
                                }
                            )) {
                                Text("— none —").tag("")
                                ForEach(ollamaLlms, id: \.self) { name in
                                    Text(name).tag(name)
                                }
                            }
                            .foregroundStyle(DatawatchColors.onSurface)
                            .listRowBackground(DatawatchColors.surface)
                        }
                    } header: {
                        Text("Summarizer LLM")
                    } footer: {
                        Text("Only Ollama LLMs are listed here.")
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }

                    Section {
                        HStack {
                            if let result = testResult {
                                Text(result)
                                    .font(DatawatchFonts.labelSmall)
                                    .foregroundStyle(result.hasPrefix("✓") ? DatawatchColors.primary : DatawatchColors.error)
                            }
                            Spacer()
                            if isTesting {
                                ProgressView().controlSize(.small)
                            } else {
                                Button("Test Summarizer") { runTest() }
                                    .font(DatawatchFonts.labelSmall)
                                    .foregroundStyle(DatawatchColors.primary)
                            }
                        }
                        .listRowBackground(DatawatchColors.surface)
                    }
                }
            }

            if let err = error {
                Section {
                    Text(err)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(Color.red)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .navigationTitle("Session")
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func load() async {
        guard let profile = activeProfile else {
            isLoading = false
            error = "No active server configured."
            return
        }
        isLoading = true
        error = nil

        await withCheckedContinuation { continuation in
            IosServiceLocator.shared.fetchSummarizerConfig(
                profile: profile,
                onSuccess: { isEnabled, ref in
                    enabled = isEnabled
                    llmRef = ref
                    continuation.resume()
                },
                onError: { msg in
                    error = msg
                    continuation.resume()
                }
            )
        }

        await withCheckedContinuation { continuation in
            IosServiceLocator.shared.listOllamaLlmNames(
                profile: profile,
                onSuccess: { names in
                    ollamaLlms = names as? [String] ?? []
                    continuation.resume()
                },
                onError: { _ in
                    continuation.resume()
                }
            )
        }

        isLoading = false
    }

    private func saveEnabled(_ value: Bool) {
        guard let profile = activeProfile else { return }
        IosServiceLocator.shared.writeConfigBool(
            profile: profile,
            key: "session.summarizer.enabled",
            value: value,
            onSuccess: {},
            onError: { msg in error = msg }
        )
    }

    private func saveLlmRef(_ value: String) {
        guard let profile = activeProfile else { return }
        IosServiceLocator.shared.writeConfigString(
            profile: profile,
            key: "session.summarizer.llm_ref",
            value: value,
            onSuccess: {},
            onError: { msg in error = msg }
        )
    }

    private func runTest() {
        guard let profile = activeProfile else { return }
        isTesting = true
        testResult = nil
        IosServiceLocator.shared.testSummarizer(
            profile: profile,
            onSuccess: { latencyMs in
                DispatchQueue.main.async {
                    self.testResult = "✓ ok · \(latencyMs)ms"
                    self.isTesting = false
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    self.testResult = "✗ \(msg)"
                    self.isTesting = false
                }
            }
        )
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        SettingsSessionView()
            .environmentObject(ServerProfileStore())
    }
    .preferredColorScheme(.dark)
}
#endif
