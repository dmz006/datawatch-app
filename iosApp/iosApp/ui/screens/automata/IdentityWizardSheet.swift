import SwiftUI
import DatawatchShared

/// PWA `openIdentityWizard` (BL257) / Android `IdentityWizardSheet`: a 6-step
/// interview — role · north-star goals · current projects · values · current
/// focus · context notes — pre-filled from `/api/identity`; the last step PUTs
/// the assembled document. Opened from the 🤖 button on the Automata header
/// and from Settings › Identity.
struct IdentityWizardSheet: View {
    let profile: ServerProfile
    var onSaved: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @State private var step: Int = 0
    @State private var role = ""
    @State private var goals = ""
    @State private var projects = ""
    @State private var values = ""
    @State private var focus = ""
    @State private var notes = ""
    @State private var updatedAt = ""
    @State private var loaded = false
    @State private var saving = false
    @State private var error: String? = nil

    private static let total: Int = 6
    private var isLast: Bool { step == Self.total - 1 }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text(verbatim: L("Step") + " \(step + 1) / \(Self.total)")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                ProgressView(value: Double(step + 1), total: Double(Self.total))
                    .tint(DatawatchColors.primary)
                Text(L(stepLabel))
                    .font(DatawatchFonts.titleMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                stepField
                if let error {
                    Text(error)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                }
                Spacer(minLength: 0)
                navRow
            }
            .padding(16)
            .background(DatawatchColors.background)
            .disabled(!loaded || saving)
            .overlay { if !loaded { ProgressView().tint(DatawatchColors.primary) } }
            .navigationTitle("Identity Wizard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .task { load() }
        }
        .presentationDetents([.medium, .large])
    }

    private var stepLabel: String {
        switch step {
        case 0: return "Role"
        case 1: return "North-Star Goals"
        case 2: return "Current Projects"
        case 3: return "Values"
        case 4: return "Current Focus"
        default: return "Context Notes"
        }
    }

    @ViewBuilder
    private var stepField: some View {
        switch step {
        case 0: singleLine($role, placeholder: "e.g. Staff engineer, platform team")
        case 1: listField($goals)
        case 2: listField($projects)
        case 3: listField($values)
        case 4: singleLine($focus, placeholder: "What you are working on right now")
        default: longText($notes)
        }
    }

    private func singleLine(_ text: Binding<String>, placeholder: String) -> some View {
        TextField(L(placeholder), text: text)
            .textFieldStyle(.roundedBorder)
    }

    private func listField(_ text: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("One per line")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            longText(text)
        }
    }

    private func longText(_ text: Binding<String>) -> some View {
        TextEditor(text: text)
            .font(DatawatchFonts.bodyMedium)
            .frame(minHeight: 120)
            .scrollContentBackground(.hidden)
            .padding(6)
            .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.border, lineWidth: 1))
    }

    private var navRow: some View {
        HStack {
            Button("← Back") { step -= 1 }
                .disabled(step == 0)
            Spacer()
            if saving {
                ProgressView()
            } else if isLast {
                Button("Save & Finish") { save() }
                    .buttonStyle(.borderedProminent)
                    .tint(DatawatchColors.primary)
            } else {
                Button("Next →") { step += 1 }
                    .buttonStyle(.borderedProminent)
                    .tint(DatawatchColors.primary)
            }
        }
    }

    private func load() {
        guard !loaded else { return }
        IosSettingsLists.shared.identity(profile: profile, onSuccess: { id in
            DispatchQueue.main.async {
                role = id.role
                goals = id.goals
                projects = id.projects
                values = id.values
                focus = id.focus
                notes = id.notes
                updatedAt = id.updatedAt
                loaded = true
            }
        }, onError: { _ in
            // PWA: no prior identity — start blank.
            DispatchQueue.main.async { loaded = true }
        })
    }

    private func save() {
        saving = true
        error = nil
        let identity = IosIdentity(role: role, goals: goals, projects: projects, values: values,
                                   focus: focus, notes: notes, updatedAt: updatedAt)
        IosSettingsLists.shared.saveIdentity(profile: profile, identity: identity) { err in
            DispatchQueue.main.async {
                saving = false
                if let err {
                    error = err
                } else {
                    AlertDock.shared.post(L("Identity saved"), level: .success)
                    onSaved()
                    dismiss()
                }
            }
        }
    }
}
