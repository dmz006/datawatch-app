import SwiftUI
import DatawatchShared

/// Council subsystem config (PWA council panel / Android CouncilCard):
/// comm firehose, real sessions, LLM reference, max parallel, draft retention.
struct CouncilConfigView: View {
    let profile: ServerProfile

    @State private var firehose = false
    @State private var realSessions = false
    @State private var llmRef = ""
    @State private var maxParallel = ""
    @State private var retention = ""
    @State private var loaded = false
    @State private var saving = false
    @State private var status: String? = nil
    @State private var error: String? = nil

    var body: some View {
        Form {
            Section {
                Toggle("Comm firehose", isOn: $firehose)
                Toggle("Spawn real sessions", isOn: $realSessions)
            }
            Section("LLM reference") {
                TextField("Server default", text: $llmRef)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            Section {
                numberRow("Max parallel", text: $maxParallel)
                numberRow("Draft retention (days)", text: $retention)
            }
            if status != nil || error != nil {
                Section { footer }
            }
        }
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .disabled(!loaded || saving)
        .navigationTitle("Council settings")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if saving {
                    ProgressView()
                } else {
                    Button("Save") { save() }.disabled(!loaded)
                }
            }
        }
        .task { load() }
    }

    private func numberRow(_ label: String, text: Binding<String>) -> some View {
        HStack {
            Text(L(label))
            Spacer()
            TextField("—", text: text)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: 80)
        }
    }

    @ViewBuilder
    private var footer: some View {
        if let error {
            Text(error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
        }
        if let status {
            Text(verbatim: status).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.success)
        }
    }

    private func apply(_ c: IosCouncilConfig) {
        firehose = c.commFirehose
        realSessions = c.spawnRealSessions
        llmRef = c.llmRef
        maxParallel = c.maxParallel
        retention = c.draftRetentionDays
    }

    private func load() {
        IosCouncilSettings.shared.load(profile: profile, onSuccess: { c in
            DispatchQueue.main.async {
                apply(c)
                loaded = true
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                error = msg
                loaded = true
            }
        })
    }

    private func save() {
        saving = true
        status = nil
        error = nil
        let digits: (String) -> String = { s in s.filter { $0.isNumber } }
        let config = IosCouncilConfig(
            commFirehose: firehose,
            spawnRealSessions: realSessions,
            llmRef: llmRef,
            maxParallel: digits(maxParallel),
            draftRetentionDays: digits(retention)
        )
        IosCouncilSettings.shared.save(profile: profile, config: config, onSuccess: { c in
            DispatchQueue.main.async {
                saving = false
                apply(c)
                status = L("Saved")
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                saving = false
                error = msg
            }
        })
    }
}
