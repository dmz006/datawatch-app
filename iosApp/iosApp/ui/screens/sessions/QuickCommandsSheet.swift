import SwiftUI
import DatawatchShared

/// Quick commands for a waiting session, opened from the ▶ button on its card
/// (PWA `cardCmds` dropdown: System group, Saved group, Custom…).
struct QuickCommandsSheet: View {
    let profile: ServerProfile
    let session: DwSession

    @Environment(\.dismiss) private var dismiss
    @State private var saved: [IosSavedCommand] = []
    @State private var custom = ""
    @State private var sending: String? = nil
    @State private var errorMessage: String? = nil

    /// PWA system commands: value → label.
    private static let system: [(value: String, label: String)] = [
        ("yes", "approve"), ("no", "reject"), ("continue", "continue"), ("skip", "skip"),
        ("__esc__", "ESC"), ("__ctrlb__", "tmux prefix (Ctrl-b)"), ("/exit", "quit"),
    ]

    var body: some View {
        NavigationStack {
            List {
                Section("System") {
                    ForEach(Self.system, id: \.value) { cmd in
                        commandRow(label: cmd.label, value: cmd.value)
                    }
                }
                if !saved.isEmpty {
                    Section("Saved") {
                        ForEach(saved, id: \.name) { cmd in
                            commandRow(label: cmd.name, value: cmd.command)
                        }
                    }
                }
                Section("Custom") {
                    HStack {
                        TextField("Type…", text: $custom)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .onSubmit { sendCustom() }
                        Button {
                            sendCustom()
                        } label: {
                            Image(systemName: "paperplane.fill")
                        }
                        .disabled(custom.trimmingCharacters(in: .whitespaces).isEmpty || sending != nil)
                        .accessibilityLabel("Send custom command")
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
            .navigationTitle("Commands")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .onAppear {
                IosQuickCommands.shared.loadSaved(profile: profile) { list in
                    DispatchQueue.main.async { saved = list }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .preferredColorScheme(.dark)
    }

    private func commandRow(label: String, value: String) -> some View {
        Button {
            send(value)
        } label: {
            HStack {
                Text(label).foregroundStyle(DatawatchColors.onSurface)
                Spacer()
                if sending == value {
                    ProgressView().controlSize(.small)
                } else if !value.hasPrefix("__") && value != label {
                    Text(value)
                        .font(DatawatchFonts.terminalSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(1)
                }
            }
        }
        .disabled(sending != nil)
    }

    private func sendCustom() {
        let text = custom.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        send(text)
    }

    private func send(_ value: String) {
        sending = value
        errorMessage = nil
        IosQuickCommands.shared.send(
            profile: profile,
            session: session,
            value: value,
            onSuccess: {
                DispatchQueue.main.async {
                    sending = nil
                    dismiss()
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    sending = nil
                    errorMessage = msg
                }
            }
        )
    }
}
