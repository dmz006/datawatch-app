import SwiftUI
import DatawatchShared

/// Skill registry "Browse" (PWA Settings › Automata › Skill Registries):
/// lists the registry's available skills with their synced state; tap a row to
/// sync / unsync it, or sync every unsynced skill at once.
struct SkillBrowseSheet: View {
    let profile: ServerProfile
    let registry: String

    @Environment(\.dismiss) private var dismiss
    @State private var skills: [IosAvailableSkill] = []
    @State private var loading = true
    @State private var busy: String? = nil
    @State private var error: String? = nil

    private var unsynced: [String] { skills.filter { !$0.synced }.map { $0.name } }

    var body: some View {
        NavigationStack {
            List {
                if loading {
                    CardSkeleton()
                        .listRowBackground(DatawatchColors.surface)
                } else if skills.isEmpty {
                    Text("No skills available in this registry — try Connect first.")
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .listRowBackground(DatawatchColors.surface)
                } else {
                    Section {
                        ForEach(skills, id: \.name) { skill in
                            skillRow(skill)
                        }
                    } footer: {
                        Text("Tap a skill to sync or unsync it.")
                    }
                }
                if let error {
                    Text(error)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle(registry)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Sync all") { sync(unsynced, unsync: false) }
                        .disabled(unsynced.isEmpty || busy != nil)
                }
            }
            .task { load() }
        }
    }

    private func skillRow(_ skill: IosAvailableSkill) -> some View {
        Button {
            sync([skill.name], unsync: skill.synced)
        } label: {
            HStack(alignment: .top, spacing: 10) {
                Image(systemName: skill.synced ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(skill.synced ? DatawatchColors.success : DatawatchColors.onSurfaceMuted)
                VStack(alignment: .leading, spacing: 2) {
                    Text(skill.name)
                        .font(DatawatchFonts.bodyMedium.weight(.semibold))
                        .foregroundStyle(DatawatchColors.onSurface)
                    if !skill.summary.isEmpty {
                        Text(skill.summary)
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .lineLimit(3)
                    }
                    if !skill.tags.isEmpty {
                        Text(skill.tags.joined(separator: " · "))
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.secondary)
                    }
                }
                Spacer(minLength: 4)
                if busy == skill.name { ProgressView().controlSize(.small) }
            }
        }
        .buttonStyle(.plain)
        .disabled(busy != nil)
        .listRowBackground(DatawatchColors.surface)
        .accessibilityHint(skill.synced ? Text("Unsync") : Text("Sync"))
    }

    private func load() {
        IosSkillBrowse.shared.available(profile: profile, registry: registry, onSuccess: { list in
            DispatchQueue.main.async {
                skills = list
                loading = false
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                error = msg
                loading = false
            }
        })
    }

    private func sync(_ names: [String], unsync: Bool) {
        guard !names.isEmpty else { return }
        busy = names.count == 1 ? names[0] : "__all__"
        error = nil
        IosSkillBrowse.shared.sync(profile: profile, registry: registry, names: names, unsync: unsync) { err in
            DispatchQueue.main.async {
                busy = nil
                if let err { error = err } else { load() }
            }
        }
    }
}
