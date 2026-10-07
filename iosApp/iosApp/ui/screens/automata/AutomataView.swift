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
    @State private var showIdentityWizard = false
    /// PWA #headerSearchBtn (automata view): toggles the list's filter row.
    @State private var filterOpen = false

    // D22a/D25a: Automata | Templates. The type registry moved to
    // Settings › Automata › Type Registry (SettingsAutomataTypesView).
    private enum AutomataSection: String, CaseIterable {
        case prds = "Automata"
        case templates = "Templates"
    }

    private var selectedProfile: ServerProfile? {
        // D2a: under "All" the list aggregates every server; the wizard and
        // Templates use the active (first enabled) profile.
        if let id = selectedProfileId, let p = store.profiles.first(where: { $0.id == id }) {
            return p
        }
        return store.activeProfile
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
                    serverName: store.isAllServers ? L("All servers") : selectedProfile?.displayName
                )
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 4) {
                    // PWA #headerIdentityBtn: 🤖 opens the Identity Wizard (Automata view only).
                    if selectedProfile != nil {
                        Button { showIdentityWizard = true } label: {
                            Text("🤖").font(.system(size: 18))
                        }
                        .accessibilityLabel("Identity wizard")
                    }
                    DocsLinkButton(profile: selectedProfile, key: "view_automata")
                    if section == .prds {
                        Button {
                            withAnimation { filterOpen.toggle() }
                        } label: {
                            Image(systemName: filterOpen ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                                .foregroundStyle(DatawatchColors.primary)
                        }
                        .accessibilityLabel(filterOpen ? "Hide filter" : "Toggle Automata filters")
                    }
                    AlertsBellButton()
                    if !store.isAllServers {
                        ReachabilityDotView(profile: selectedProfile)
                    }
                }
            }
        }
        .sheet(isPresented: $showIdentityWizard) {
            if let profile = selectedProfile {
                IdentityWizardSheet(profile: profile)
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
            // D2a: shared PWA "Server:" chip bar (hidden with one server).
            ServerPickerBar(showsAll: true)

            Picker("Section", selection: $section) {
                ForEach(AutomataSection.allCases, id: \.self) { s in
                    Text(L(s.rawValue)).tag(s)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.vertical, 8)
            .accessibilityLabel("Automata section")

            switch section {
            case .prds:
                if let profile = selectedProfile {
                    PrdListView(
                        profile: profile,
                        allProfiles: store.isAllServers ? store.enabledProfiles : [],
                        onBrowseTemplates: { section = .templates },
                        headerFilterOpen: $filterOpen
                    )
                }
            case .templates:
                if let profile = selectedProfile {
                    TemplatesView(profile: profile)
                }
            }
        }
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
        .dwThemed()
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
