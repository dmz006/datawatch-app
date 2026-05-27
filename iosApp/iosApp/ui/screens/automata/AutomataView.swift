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
    @StateObject private var vm = AutomataViewModel()
    @State private var selectedProfileId: String? = nil
    @State private var showAddSheet = false

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
            ToolbarItem(.principal) {
                HeaderView(
                    title: "Automata",
                    serverName: selectedProfile?.displayName
                )
            }
            if !store.profiles.isEmpty {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showAddSheet = true
                    } label: {
                        Image(systemName: "plus")
                            .foregroundStyle(DatawatchColors.primary)
                    }
                    .accessibilityLabel("Add automata type")
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            if let profile = selectedProfile {
                AddAutomataTypeSheet(profile: profile) {
                    vm.load(profile: profile)
                }
            }
        }
        .onChange(of: store.profiles) { profiles in
            // If the selected profile was removed, reset selection
            if let id = selectedProfileId, !profiles.contains(where: { $0.id == id }) {
                selectedProfileId = nil
            }
            if let profile = selectedProfile {
                vm.load(profile: profile)
            }
        }
        .onAppear {
            if let profile = selectedProfile {
                vm.load(profile: profile)
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

            if vm.isLoading && vm.types.isEmpty {
                LoadingIndicator(message: "Loading automata types…")
            } else if let err = vm.error, vm.types.isEmpty {
                ErrorCard(message: err) {
                    if let profile = selectedProfile {
                        vm.load(profile: profile)
                    }
                }
            } else if vm.types.isEmpty {
                emptyTypesView
            } else {
                typesList
            }
        }
        .task(id: selectedProfile?.id ?? "") {
            if let profile = selectedProfile {
                vm.load(profile: profile)
            }
        }
    }

    private var profilePicker: some View {
        Picker("Server", selection: Binding(
            get: { selectedProfileId ?? store.profiles.first?.id ?? "" },
            set: { selectedProfileId = $0 }
        )) {
            ForEach(store.profiles, id: \.id) { profile in
                Text(profile.displayName).tag(profile.id)
            }
        }
        .pickerStyle(.segmented)
        .accessibilityLabel("Select server profile")
    }

    // ── Types list ────────────────────────────────────────────────────────

    private var typesList: some View {
        List {
            ForEach(vm.types, id: \.id) { type_ in
                AutomataTypeRow(automataType: type_)
                    .listRowBackground(DatawatchColors.surface)
                    .listRowSeparatorTint(DatawatchColors.border)
                    .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                        Button(role: .destructive) {
                            if let profile = selectedProfile {
                                vm.delete(id: type_.id, profile: profile) {}
                            }
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                        .tint(DatawatchColors.error)
                    }
            }
        }
        .listStyle(.plain)
        .background(DatawatchColors.background)
        .scrollContentBackground(.hidden)
        .refreshable {
            if let profile = selectedProfile {
                vm.load(profile: profile)
            }
        }
    }

    // ── Empty states ──────────────────────────────────────────────────────

    private var noProfilesView: some View {
        VStack(spacing: 20) {
            Image(systemName: "arrow.trianglehead.2.counterclockwise")
                .font(.system(size: 56))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No server connected")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("Connect a server in Settings to manage automata types.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var emptyTypesView: some View {
        VStack(spacing: 20) {
            Image(systemName: "square.stack.3d.up.slash")
                .font(.system(size: 56))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No automata types")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("Tap + to register the first automata type.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

// ── Automata type row ──────────────────────────────────────────────────────

private struct AutomataTypeRow: View {
    let automataType: AutomataTypeDto

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(swatchColor(for: automataType.color))
                .frame(width: 14, height: 14)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 4) {
                Text(automataType.label.isEmpty ? automataType.id : automataType.label)
                    .font(DatawatchFonts.titleMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(1)

                if let desc = automataType.description_, !desc.isEmpty {
                    Text(desc)
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(2)
                }

                Text(automataType.id)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(1)
            }

            Spacer()
        }
        .padding(.vertical, 8)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(accessibilityDescription)
    }

    private var accessibilityDescription: String {
        var parts = [automataType.label.isEmpty ? automataType.id : automataType.label]
        if let desc = automataType.description_, !desc.isEmpty {
            parts.append(desc)
        }
        return parts.joined(separator: ", ")
    }

    /// Map common CSS color names to SwiftUI colors; fall back to primary.
    private func swatchColor(for color: String?) -> Color {
        guard let color, !color.isEmpty else { return DatawatchColors.primary }
        switch color.lowercased() {
        case "red":     return Color.red
        case "green":   return Color.green
        case "blue":    return Color.blue
        case "yellow":  return Color.yellow
        case "orange":  return Color.orange
        case "purple":  return Color.purple
        case "pink":    return Color.pink
        case "cyan", "teal": return Color.cyan
        case "white":   return Color.white
        case "gray", "grey": return Color.gray
        case "black":   return Color.black
        case "indigo":  return Color.indigo
        case "mint":    return Color.mint
        case "brown":   return Color.brown
        default:        return DatawatchColors.primary
        }
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
            color: nil
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
