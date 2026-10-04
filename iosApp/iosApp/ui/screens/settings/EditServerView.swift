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
    @State private var pinnedSha: String?
    @State private var pinning = false
    @State private var pinCandidate: CertProbe.Fingerprint? = nil
    @State private var pinError: String? = nil
    @State private var probing = false
    @State private var deleting = false
    @State private var confirmDelete = false
    @State private var errorMessage: String?
    @State private var fetchingCert = false
    @State private var certFileURL: URL?
    @State private var certError: String?

    init(profile: ServerProfile) {
        self.profile = profile
        _displayName = State(initialValue: profile.displayName)
        _baseUrl = State(initialValue: profile.baseUrl)
        _noToken = State(initialValue: profile.bearerTokenRef.isEmpty)
        let sentinel = IosServiceLocator.shared.TRUST_ALL_SENTINEL
        let anchor = profile.trustAnchorSha256
        _selfSigned = State(initialValue: anchor == sentinel)
        _pinnedSha = State(initialValue: (anchor != nil && anchor != sentinel && !(anchor ?? "").isEmpty) ? anchor : nil)
    }

    private var canSubmit: Bool {
        !displayName.trimmingCharacters(in: .whitespaces).isEmpty &&
        baseUrl.hasPrefix("https://")
    }

    var body: some View {
        Form {
            Section("Server") {
                TextField("Display name", text: $displayName)
                    .autocorrectionDisabled()
                VStack(alignment: .leading, spacing: 4) {
                    TextField("Base URL (https://…)", text: $baseUrl)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    if !baseUrl.isEmpty && !baseUrl.hasPrefix("https://") {
                        Text("URL must start with https:// — datawatch servers must expose TLS.")
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

            ServerTrustSection(
                baseUrl: baseUrl,
                selfSigned: $selfSigned,
                pinnedSha: $pinnedSha,
                pinning: $pinning,
                pinCandidate: $pinCandidate,
                pinError: $pinError,
                disabled: probing || deleting
            )

            Section {
                Button {
                    downloadCert()
                } label: {
                    HStack {
                        Label("Download server CA certificate", systemImage: "lock.doc")
                        if fetchingCert {
                            Spacer()
                            ProgressView()
                        }
                    }
                }
                .disabled(fetchingCert || probing || deleting)

                if let url = certFileURL {
                    ShareLink(item: url) {
                        Label("Share certificate…", systemImage: "square.and.arrow.up")
                    }
                }
                if let certError {
                    Text(certError)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                }
            } header: {
                Text("Server CA certificate")
            } footer: {
                Text("Alternative to pinning: install the server's CA on this device. Download, open the file (Save to Files → tap it) to install the profile, then enable it under Settings → General → About → Certificate Trust Settings.")
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
                        .foregroundStyle(canSubmit && !deleting ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
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

    private func downloadCert() {
        fetchingCert = true
        certError = nil
        certFileURL = nil
        IosServiceLocator.shared.fetchServerCertPem(
            profile: profile,
            onSuccess: { pem in
                DispatchQueue.main.async {
                    fetchingCert = false
                    let safeName = profile.displayName
                        .components(separatedBy: CharacterSet.alphanumerics.inverted)
                        .filter { !$0.isEmpty }
                        .joined(separator: "-")
                        .lowercased()
                    let url = FileManager.default.temporaryDirectory
                        .appendingPathComponent("datawatch-\(safeName.isEmpty ? "server" : safeName)-ca.pem")
                    do {
                        try Data(pem.utf8).write(to: url, options: .atomic)
                        certFileURL = url
                    } catch {
                        certError = "Could not write certificate file."
                    }
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    fetchingCert = false
                    certError = msg
                }
            }
        )
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
            trustAnchorSha256: selfSigned ? IosServiceLocator.shared.TRUST_ALL_SENTINEL : pinnedSha,
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
        baseUrl: "https://localhost:8443",
        bearerTokenRef: "tok-preview",
        trustAnchorSha256: nil,
        reachabilityProfileId: "preview",
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
