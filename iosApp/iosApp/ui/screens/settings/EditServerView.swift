import SwiftUI
import DatawatchShared

/// Edit an existing server profile. Probe runs before persisting changes.
struct EditServerView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @Environment(\.dismiss) private var dismiss

    let profile: ServerProfile

    @State private var displayName: String
    @State private var baseUrl: String
    @State private var newToken = ""
    @State private var noToken: Bool
    @State private var selfSigned: Bool
    @State private var probing = false
    @State private var deleting = false
    @State private var confirmDelete = false
    @State private var errorMessage: String?

    init(profile: ServerProfile) {
        self.profile = profile
        _displayName = State(initialValue: profile.displayName)
        _baseUrl = State(initialValue: profile.baseUrl)
        _noToken = State(initialValue: profile.bearerTokenRef.isEmpty)
        _selfSigned = State(initialValue: profile.trustAnchorSha256 == IosServiceLocator.shared.TRUST_ALL_SENTINEL)
    }

    private var canSubmit: Bool {
        !displayName.trimmingCharacters(in: .whitespaces).isEmpty &&
        (baseUrl.hasPrefix("http://") || baseUrl.hasPrefix("https://"))
    }

    var body: some View {
        Form {
            Section("Server") {
                TextField("Display name", text: $displayName)
                    .autocorrectionDisabled()
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Base URL", text: $baseUrl)
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
                    SecureField(
                        profile.bearerTokenRef.isEmpty ? "Bearer token" : "New token (leave blank to keep)",
                        text: $newToken
                    )
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
                    .tint(DatawatchColors.secondary)
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

            Section {
                Button(role: .destructive) {
                    confirmDelete = true
                } label: {
                    Label("Delete server", systemImage: "trash")
                }
                .disabled(probing || deleting)
            }
        }
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .navigationTitle("Edit Server")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                if probing {
                    ProgressView()
                } else {
                    Button("Save") { save() }
                        .disabled(!canSubmit || deleting)
                        .fontWeight(.semibold)
                }
            }
        }
        .alert("Delete \"\(profile.displayName)\"?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) { deleteProfile() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes the profile and its bearer token from this device. The server daemon is not affected.")
        }
    }

    private func save() {
        guard canSubmit, !probing else { return }
        probing = true
        errorMessage = nil

        let tokenValue: String? = {
            if noToken { return nil }
            return newToken.isEmpty ? (profile.bearerTokenRef.isEmpty ? nil : "") : newToken
        }()

        let updated = ServerProfile(
            id: profile.id,
            displayName: displayName.trimmingCharacters(in: .whitespaces),
            baseUrl: baseUrl.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "/")),
            bearerTokenRef: noToken ? "" : profile.bearerTokenRef,
            trustAnchorSha256: selfSigned ? IosServiceLocator.shared.TRUST_ALL_SENTINEL : nil,
            reachabilityProfileId: profile.reachabilityProfileId,
            enabled: profile.enabled,
            createdTs: profile.createdTs,
            lastSeenTs: profile.lastSeenTs,
            signalLinked: profile.signalLinked
        )

        store.save(profile: updated, token: tokenValue.flatMap { $0.isEmpty ? nil : $0 }) {
            probing = false
            dismiss()
        } onError: { msg in
            probing = false
            errorMessage = msg
        }
    }

    private func deleteProfile() {
        deleting = true
        store.delete(profile: profile) {
            deleting = false
            dismiss()
        } onError: { msg in
            deleting = false
            errorMessage = msg
        }
    }
}

#Preview {
    let store = ServerProfileStore()
    let profile = ServerProfile(
        id: "preview-1",
        displayName: "Local dev",
        baseUrl: "http://localhost:8080",
        bearerTokenRef: "tok-preview",
        trustAnchorSha256: nil,
        reachabilityProfileId: nil,
        enabled: true,
        createdTs: 0,
        lastSeenTs: 0,
        signalLinked: false
    )
    NavigationStack {
        EditServerView(profile: profile)
            .environmentObject(store)
    }
    .preferredColorScheme(.dark)
}
