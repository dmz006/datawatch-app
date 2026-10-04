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
    @State private var pinnedSha: String? = nil
    @State private var pinning = false
    @State private var pinCandidate: CertProbe.Fingerprint? = nil
    @State private var pinError: String? = nil
    @State private var probing = false
    @State private var errorMessage: String?

    private var canSubmit: Bool {
        !displayName.trimmingCharacters(in: .whitespaces).isEmpty &&
        baseUrl.hasPrefix("https://")
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
                        if !baseUrl.isEmpty && !baseUrl.hasPrefix("https://") {
                            Text("URL must start with https:// — datawatch servers must expose TLS.")
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

                ServerTrustSection(
                    baseUrl: baseUrl,
                    selfSigned: $selfSigned,
                    pinnedSha: $pinnedSha,
                    pinning: $pinning,
                    pinCandidate: $pinCandidate,
                    pinError: $pinError,
                    disabled: probing
                )

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
        .dwThemed()
    }

    private func submit() {
        guard canSubmit, !probing else { return }
        probing = true
        errorMessage = nil

        let svc = IosServiceLocator.shared
        let profile = ServerProfile(
            id: svc.generateProfileId(),
            displayName: displayName.trimmingCharacters(in: .whitespaces),
            baseUrl: baseUrl.trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "/")),
            bearerTokenRef: "",
            trustAnchorSha256: selfSigned ? IosServiceLocator.shared.TRUST_ALL_SENTINEL : pinnedSha,
            reachabilityProfileId: svc.generateProfileId(),
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

// ── Shared "Security" section (Add + Edit) ─────────────────────────────────

/// Certificate trust controls: pin the server's leaf certificate (trust-on-first-use,
/// the supported path for self-signed servers) or, as an insecure fallback, trust all.
struct ServerTrustSection: View {
    let baseUrl: String
    @Binding var selfSigned: Bool
    @Binding var pinnedSha: String?
    @Binding var pinning: Bool
    @Binding var pinCandidate: CertProbe.Fingerprint?
    @Binding var pinError: String?
    var disabled: Bool = false

    var body: some View {
        Section {
            if let pin = pinnedSha {
                VStack(alignment: .leading, spacing: 4) {
                    Label("Pinned certificate", systemImage: "lock.shield")
                        .foregroundStyle(DatawatchColors.success)
                    Text(CertProbe.display(pin))
                        .font(DatawatchFonts.terminalSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .textSelection(.enabled)
                }
                Button("Remove pin", role: .destructive) { pinnedSha = nil }
                    .disabled(disabled)
            } else {
                Button {
                    probe()
                } label: {
                    HStack {
                        Label("Pin server certificate…", systemImage: "lock.shield")
                        if pinning {
                            Spacer()
                            ProgressView()
                        }
                    }
                }
                .disabled(disabled || pinning || !baseUrl.hasPrefix("https://"))
            }
            if let pinError {
                Text(pinError)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
            }

            Toggle("Trust all certificates (insecure)", isOn: $selfSigned)
                .tint(DatawatchColors.error)
                .disabled(disabled)
                .onChange(of: selfSigned) { on in
                    if on { pinnedSha = nil }
                }
            if selfSigned {
                Text("Disables certificate validation for this server. Prefer pinning the certificate above.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
            }
        } header: {
            Text("Security")
        } footer: {
            Text("Pinning trusts exactly this server's certificate — self-signed is fine. If the server's certificate is replaced, connections fail until you re-pin.")
        }
        .alert(
            "Pin this certificate?",
            isPresented: Binding(get: { pinCandidate != nil }, set: { if !$0 { pinCandidate = nil } })
        ) {
            Button("Trust & pin") {
                if let fp = pinCandidate {
                    pinnedSha = fp.sha256Hex
                    selfSigned = false
                }
                pinCandidate = nil
            }
            Button("Cancel", role: .cancel) { pinCandidate = nil }
        } message: {
            Text("\(pinCandidate?.subject ?? "")\n\nSHA-256\n\(pinCandidate?.display ?? "")\n\nVerify this matches the fingerprint shown on the server before trusting it.")
        }
    }

    private func probe() {
        pinning = true
        pinError = nil
        let url = baseUrl
        Task { @MainActor in
            do {
                pinCandidate = try await CertProbe.fetch(baseUrl: url)
            } catch {
                pinError = error.localizedDescription
            }
            pinning = false
        }
    }
}

#if DEBUG
#Preview {
    AddServerView()
        .environmentObject(ServerProfileStore())
}
#endif
