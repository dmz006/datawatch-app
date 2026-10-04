import SwiftUI
import DatawatchShared

// ── ViewModel ──────────────────────────────────────────────────────────────

@MainActor
final class AutomataViewModel: ObservableObject {
    @Published var types: [AutomataTypeDto] = []
    @Published var isLoading: Bool = false
    @Published var error: String? = nil

    func load(profile: ServerProfile) {
        isLoading = true
        error = nil
        IosServiceLocator.shared.listAutomataTypes(
            profile: profile,
            onSuccess: { [weak self] list in
                DispatchQueue.main.async {
                    self?.types = list
                    self?.isLoading = false
                }
            },
            onError: { [weak self] msg in
                DispatchQueue.main.async {
                    self?.error = msg
                    self?.isLoading = false
                }
            }
        )
    }

    func delete(id: String, profile: ServerProfile, onDone: @escaping () -> Void) {
        IosServiceLocator.shared.deleteAutomataType(
            profile: profile,
            id: id,
            onSuccess: { [weak self] in
                DispatchQueue.main.async {
                    self?.load(profile: profile)
                    onDone()
                }
            },
            onError: { [weak self] msg in
                DispatchQueue.main.async {
                    self?.error = msg
                }
            }
        )
    }
}

// ── Main view ──────────────────────────────────────────────────────────────

struct AutomataView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @State private var selectedProfileId: String? = UserDefaults.standard.string(forKey: "dw.active_profile_id")
    @State private var section: AutomataSection = .prds

    // D22a/D25a: PRDs | Templates. The type registry moved to
    // Settings › Automata › Type Registry (SettingsAutomataTypesView).
    private enum AutomataSection: String, CaseIterable {
        case prds = "PRDs"
        case templates = "Templates"
    }

    private var selectedProfile: ServerProfile? {
        if let id = selectedProfileId {
            return store.profiles.first(where: { $0.id == id })
        }
        return store.profiles.first
    }

    var body: some View {
        Group {
            if store.profiles.isEmpty {
                noProfilesView
            } else {
                profileContent
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                HeaderView(
                    title: "Automata",
                    serverName: selectedProfile?.displayName
                )
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 4) {
                    DocsLinkButton(profile: selectedProfile, anchor: "automata")
                    AlertsBellButton()
                    ReachabilityDotView(profile: selectedProfile)
                }
            }
        }
        .onChange(of: store.activeProfileId) { id in
            if id != selectedProfileId { selectedProfileId = id; }
        }
        .onChange(of: store.profiles) { profiles in
            // If the selected profile was removed, reset selection
            if let id = selectedProfileId, !profiles.contains(where: { $0.id == id }) {
                selectedProfileId = nil
            }
        }
    }

    // ── Profile content ───────────────────────────────────────────────────

    @ViewBuilder
    private var profileContent: some View {
        VStack(spacing: 0) {
            if store.profiles.count > 1 {
                profilePicker
                    .padding(.horizontal)
                    .padding(.vertical, 8)
                    .background(DatawatchColors.surface)
            }

            Picker("Section", selection: $section) {
                ForEach(AutomataSection.allCases, id: \.self) { s in
                    Text(s.rawValue).tag(s)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.vertical, 8)
            .accessibilityLabel("Automata section")

            switch section {
            case .prds:
                if let profile = selectedProfile {
                    PrdListView(profile: profile)
                }
            case .templates:
                if let profile = selectedProfile {
                    TemplatesView(profile: profile)
                }
            }
        }
    }

    private var profilePicker: some View {
        Picker("Server", selection: Binding(
            get: { selectedProfileId ?? store.profiles.first?.id ?? "" },
            set: { selectedProfileId = $0; store.selectActive($0) }
        )) {
            ForEach(store.profiles, id: \.id) { profile in
                Text(profile.displayName).tag(profile.id)
            }
        }
        .pickerStyle(.segmented)
        .accessibilityLabel("Select server profile")
    }

    // ── Empty states ──────────────────────────────────────────────────────

    private var noProfilesView: some View {
        VStack(spacing: 20) {
            Image(systemName: "arrow.trianglehead.2.counterclockwise")
                .font(.system(.largeTitle))
                .imageScale(.large)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No server connected")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("Connect a server in Settings to manage automata.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// ── Add automata type sheet ────────────────────────────────────────────────

struct AddAutomataTypeSheet: View {
    let profile: ServerProfile
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss

    @State private var label = ""
    @State private var description = ""
    @State private var customId = ""
    @State private var selectedColor: Color = DatawatchColors.primary
    @State private var isSaving = false
    @State private var errorMessage: String? = nil

    private var canSave: Bool {
        !label.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Identity") {
                    TextField("Label (required)", text: $label)
                        .autocorrectionDisabled()

                    TextField("ID (optional — auto-generated if blank)", text: $customId)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }

                Section("Details") {
                    TextField("Description (optional)", text: $description, axis: .vertical)
                        .lineLimit(3, reservesSpace: false)

                    ColorPicker("Color", selection: $selectedColor, supportsOpacity: false)
                        .foregroundStyle(DatawatchColors.onSurface)
                }

                if let err = errorMessage {
                    Section {
                        Text(err)
                            .foregroundStyle(DatawatchColors.error)
                            .font(DatawatchFonts.bodyMedium)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle("Add Automata Type")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                        .foregroundStyle(DatawatchColors.onSurface)
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                            .tint(DatawatchColors.primary)
                    } else {
                        Button("Save") { save() }
                            .disabled(!canSave)
                            .fontWeight(.semibold)
                            .foregroundStyle(canSave ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
                    }
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private func save() {
        let resolvedId = customId.trimmingCharacters(in: .whitespaces).isEmpty
            ? UUID().uuidString.lowercased()
            : customId.trimmingCharacters(in: .whitespaces)

        let req = AutomataTypeRequestDto(
            id: resolvedId,
            label: label.trimmingCharacters(in: .whitespaces),
            description: description.trimmingCharacters(in: .whitespaces).isEmpty
                ? nil
                : description.trimmingCharacters(in: .whitespaces),
            color: selectedColor.hexString
        )

        isSaving = true
        errorMessage = nil

        IosServiceLocator.shared.registerAutomataType(
            profile: profile,
            req: req,
            onSuccess: { _ in
                DispatchQueue.main.async {
                    isSaving = false
                    onSaved()
                    dismiss()
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    isSaving = false
                    errorMessage = msg
                }
            }
        )
    }
}

#if DEBUG
#Preview("With profiles") {
    NavigationStack { AutomataView() }
        .environmentObject(ServerProfileStore())
        .preferredColorScheme(.dark)
}

#Preview("Empty") {
    NavigationStack { AutomataView() }
        .environmentObject(ServerProfileStore())
        .preferredColorScheme(.dark)
}
#endif
