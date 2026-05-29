import SwiftUI
import DatawatchShared

/// Form to add a new server profile. Runs a health probe before persisting.
struct AddServerView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @Environment(\.dismiss) private var dismiss

    @State private var displayName = ""
    @State private var baseUrl = ""
    @State private var token = ""
    @State private var noToken = false
    @State private var selfSigned = false
    @State private var probing = false
    @State private var errorMessage: String?

    private var canSubmit: Bool {
        !displayName.trimmingCharacters(in: .whitespaces).isEmpty &&
        (baseUrl.hasPrefix("http://") || baseUrl.hasPrefix("https://"))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Server") {
                    TextField("Display name", text: $displayName)
                        .autocorrectionDisabled()
                    VStack(alignment: .leading, spacing: 4) {
                        TextField("Base URL (https://…)", text: $baseUrl)
                            .keyboardType(.URL)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        if !baseUrl.isEmpty && !baseUrl.hasPrefix("http://") && !baseUrl.hasPrefix("https://") {
                            Text("URL must start with https:// or http://")
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.error)
                        }
                    }
                }

                Section("Authentication") {
                    if !noToken {
                        SecureField("Bearer token", text: $token)
                    }
                    Toggle("No bearer token", isOn: $noToken)
                        .tint(DatawatchColors.error)
                    if noToken {
                        Text("Insecure — only use for local test servers.")
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.error)
                    }
                }

                Section("Security") {
                    Toggle("Trust all certificates", isOn: $selfSigned)
                        .tint(DatawatchColors.error)
                    if selfSigned {
                        Text("Allows self-signed TLS. Do not enable for production servers.")
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                }

                if let msg = errorMessage {
                    Section {
                        Text(msg)
                            .foregroundStyle(DatawatchColors.error)
                            .font(DatawatchFonts.bodyMedium)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle("Add Server")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if probing {
                        ProgressView()
                    } else {
                        Button("Add") { submit() }
                            .disabled(!canSubmit)
                            .fontWeight(.semibold)
                            .foregroundStyle(canSubmit ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func submit() {
        guard canSubmit, !probing else { return }
        probing = true
        errorMessage = nil

        let svc = IosServiceLocator.shared
        let profile = ServerProfile(
            id: svc.newProfileId(),
            displayName: displayName.trimmingCharacters(in: .whitespaces),
            baseUrl: baseUrl.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "/")),
            bearerTokenRef: "",
            trustAnchorSha256: selfSigned ? IosServiceLocator.shared.TRUST_ALL_SENTINEL : nil,
            reachabilityProfileId: svc.newProfileId(),
            enabled: true,
            createdTs: svc.nowMillis(),
            lastSeenTs: nil,
            signalLinked: false
        )
        let tokenValue = noToken ? nil : (token.isEmpty ? nil : token)

        store.save(profile: profile, token: tokenValue) {
            probing = false
            dismiss()
        } onError: { msg in
            probing = false
            errorMessage = msg
        }
    }
}

#if DEBUG
#Preview {
    AddServerView()
        .environmentObject(ServerProfileStore())
}
#endif
