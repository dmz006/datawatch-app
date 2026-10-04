import SwiftUI
import DatawatchShared

// MARK: - Delete with memory strategy (PWA delete modal — shared by sessions and PRDs)

/// Keep / Purge / Archive memories, with role-prefix filter + scope for Archive.
/// `perform(strategy, roleFilter, scope, completion)` runs the delete and reports an error message or nil.
struct MemoryStrategyDeleteSheet: View {
    let title: String
    let question: String
    let perform: (_ strategy: String, _ roleFilter: String, _ scope: String, _ done: @escaping (String?) -> Void) -> Void
    var onDeleted: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var strategy = "keep"
    @State private var roleFilter = ""
    @State private var archiveScope = "project-shared"
    @State private var deleting = false
    @State private var errorMessage: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Memories", selection: $strategy) {
                        Text("Keep").tag("keep")
                        Text("Purge").tag("purge")
                        Text("Archive").tag("archive")
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                } header: {
                    Text(question)
                }
                if strategy == "archive" {
                    Section("Archive") {
                        TextField("role prefix filter (optional)", text: $roleFilter)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Picker("Scope", selection: $archiveScope) {
                            Text("Project (shared)").tag("project-shared")
                            Text("Global (shared)").tag("global-shared")
                        }
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
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .destructiveAction) {
                    if deleting {
                        ProgressView()
                    } else {
                        Button("Delete", role: .destructive) { delete() }
                            .foregroundStyle(DatawatchColors.error)
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .preferredColorScheme(.dark)
    }

    private func delete() {
        deleting = true
        errorMessage = nil
        perform(strategy, roleFilter, archiveScope) { err in
            DispatchQueue.main.async {
                deleting = false
                if let err {
                    errorMessage = err
                } else {
                    dismiss()
                    onDeleted()
                }
            }
        }
    }
}

struct SessionDeleteSheet: View {
    let profile: ServerProfile
    let session: DwSession
    var onDeleted: () -> Void

    var body: some View {
        MemoryStrategyDeleteSheet(
            title: "Delete this session?",
            question: "What should happen to this session's memories?",
            perform: { strategy, roles, scope, done in
                IosSessionOps.shared.delete(
                    profile: profile, session: session, strategy: strategy,
                    roleFilter: roles, archiveScope: scope,
                    onSuccess: { done(nil) }, onError: { done($0) }
                )
            },
            onDeleted: onDeleted
        )
    }
}

// MARK: - Timeline (GET /api/sessions/timeline)

struct SessionTimelineSheet: View {
    let profile: ServerProfile
    let session: DwSession

    @Environment(\.dismiss) private var dismiss
    @State private var lines: [String]? = nil
    @State private var failed = false

    var body: some View {
        NavigationStack {
            Group {
                if failed {
                    message("Failed to load timeline.")
                } else if let lines {
                    if lines.isEmpty {
                        message("No timeline events recorded yet.")
                    } else {
                        List(Array(lines.enumerated()), id: \.offset) { _, line in
                            timelineRow(line)
                                .listRowBackground(DatawatchColors.surface)
                        }
                        .listStyle(.plain)
                        .scrollContentBackground(.hidden)
                    }
                } else {
                    message("Loading timeline…")
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(DatawatchColors.background)
            .navigationTitle("Timeline")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onAppear(perform: load)
        }
        .presentationDetents([.medium, .large])
        .preferredColorScheme(.dark)
    }

    private func message(_ text: String) -> some View {
        Text(text)
            .font(DatawatchFonts.bodyMedium)
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// "<ts> | <event> | <detail>" → three columns; unparseable lines render as-is.
    private func timelineRow(_ line: String) -> some View {
        let parts = line.components(separatedBy: " | ")
        return VStack(alignment: .leading, spacing: 2) {
            if parts.count >= 3 {
                HStack(spacing: 8) {
                    Text(parts[0])
                        .font(DatawatchFonts.terminalSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Text(parts[1])
                        .font(DatawatchFonts.labelSmall.weight(.semibold))
                        .foregroundStyle(DatawatchColors.secondary)
                }
                Text(parts[2...].joined(separator: " | "))
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .textSelection(.enabled)
            } else {
                Text(line)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .textSelection(.enabled)
            }
        }
        .padding(.vertical, 2)
    }

    private func load() {
        guard lines == nil, !failed else { return }
        IosSessionOps.shared.timeline(
            profile: profile,
            session: session,
            onSuccess: { result in DispatchQueue.main.async { lines = result } },
            onError: { _ in DispatchQueue.main.async { failed = true } }
        )
    }
}

// MARK: - Schedule input (PWA showScheduleInputPopup)

struct ScheduleInputSheet: View {
    let profile: ServerProfile
    let session: DwSession
    var prefill: String = ""

    @Environment(\.dismiss) private var dismiss
    @State private var command = ""
    @State private var when = ""
    @State private var cron = ""
    @State private var saving = false
    @State private var errorMessage: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section("Command") {
                    TextField("e.g. continue", text: $command)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Section {
                    TextField("in 30 minutes", text: $when)
                        .textInputAutocapitalization(.never)
                } header: {
                    Text("When")
                } footer: {
                    Text("Examples: in 30m, at 14:00, tomorrow at 9am, next monday at 10:00. Leave blank to send on the next input prompt.")
                }
                Section {
                    TextField("*/5 * * * * (optional)", text: $cron)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(DatawatchFonts.terminalSmall)
                } footer: {
                    Text("5-field cron: minute hour dom month dow. Overrides When for recurrence.")
                }
                if let errorMessage {
                    Section { Text(errorMessage).foregroundStyle(DatawatchColors.error).font(DatawatchFonts.bodyMedium) }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle("Schedule input")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() } else {
                        Button("Schedule") { save() }
                            .fontWeight(.semibold)
                            .disabled(command.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            .onAppear { if command.isEmpty { command = prefill } }
        }
        .presentationDetents([.medium, .large])
        .preferredColorScheme(.dark)
    }

    private func save() {
        saving = true
        errorMessage = nil
        IosSessionOps.shared.scheduleInput(
            profile: profile, session: session, command: command, runAt: when, cronExpr: cron,
            onSuccess: { DispatchQueue.main.async { saving = false; dismiss() } },
            onError: { msg in DispatchQueue.main.async { saving = false; errorMessage = msg } }
        )
    }
}
