import SwiftUI
import DatawatchShared

// PWA Settings › Compute: BL356 Exit Hooks + BL357 Work Queue (app.js
// settings-sec-exit_hooks / settings-sec-work_queue). Backed by
// IosSettingsCrud → the shared `// ---- Android-missing parity ----` transport.

// MARK: - Exit Hooks

struct SettingsExitHooksCard: View {
    let profile: ServerProfile

    @State private var hooks: [IosExitHook] = []
    @State private var loaded = false
    @State private var error: String?
    @State private var pendingDelete: IosExitHook?
    @State private var showAdd = false

    var body: some View {
        List {
            Section {
                Text("Exit hooks fire when a named session goes zombie (Claude process exits) or enters failed/killed state. Action restart relaunches the session with the same task. Action notify sends a message to another named session.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .listRowBackground(DatawatchColors.surface)
            }
            Section { hookRows }
            Section {
                Button { showAdd = true } label: {
                    Label("Add Exit Hook", systemImage: "plus")
                        .foregroundStyle(DatawatchColors.primary)
                }
                .listRowBackground(DatawatchColors.surface)
            }
            if let error {
                Section {
                    Text(error)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .task { load() }
        .refreshable { load() }
        .sheet(isPresented: $showAdd) {
            ExitHookAddSheet(profile: profile) { load() }
        }
        .confirmationDialog(
            L("Delete") + " \(pendingDelete?.name ?? "")?",
            isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let h = pendingDelete { delete(h) }
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        }
    }

    @ViewBuilder
    private var hookRows: some View {
        if !loaded {
            HStack(spacing: 8) {
                CardSkeleton()
            }
            .listRowBackground(DatawatchColors.surface)
        } else if hooks.isEmpty {
            Text("No exit hooks configured.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .listRowBackground(DatawatchColors.surface)
        } else {
            ForEach(hooks, id: \.id) { h in
                ExitHookRow(hook: h) { on in setEnabled(h, on) }
                    .listRowBackground(DatawatchColors.surface)
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) { pendingDelete = h } label: {
                            Label("Delete", systemImage: "trash")
                        }
                        .tint(DatawatchColors.error)
                    }
            }
        }
    }

    private func load() {
        IosSettingsCrud.shared.exitHooks(profile: profile, onSuccess: { list in
            DispatchQueue.main.async {
                hooks = list
                loaded = true
                error = nil
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                loaded = true
                error = msg
            }
        })
    }

    private func setEnabled(_ h: IosExitHook, _ on: Bool) {
        IosSettingsCrud.shared.setExitHookEnabled(profile: profile, id: h.id, enabled: on) { err in
            DispatchQueue.main.async {
                error = err
                load()
            }
        }
    }

    private func delete(_ h: IosExitHook) {
        pendingDelete = nil
        IosSettingsCrud.shared.deleteExitHook(profile: profile, id: h.id) { err in
            DispatchQueue.main.async {
                error = err
                load()
            }
        }
    }
}

private struct ExitHookRow: View {
    let hook: IosExitHook
    let onToggle: (Bool) -> Void

    private var detail: String {
        var s = hook.action
        if hook.action == "notify" && !hook.notifySession.isEmpty {
            s += " → " + hook.notifySession
        }
        let last = hook.lastFiredAt.isEmpty ? L("never") : hook.lastFiredAt
        return s + "  " + L("cooldown") + ":\(hook.cooldownSeconds)s · " + L("last") + ": " + last
    }

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: hook.name)
                    .font(DatawatchFonts.bodyLarge)
                    .foregroundStyle(DatawatchColors.onSurface)
                Text(verbatim: detail)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(2)
            }
            Spacer(minLength: 4)
            Toggle("", isOn: Binding(get: { hook.enabled }, set: { onToggle($0) }))
                .labelsHidden()
                .tint(DatawatchColors.primary)
        }
        .opacity(hook.enabled ? 1.0 : 0.6)
    }
}

private struct ExitHookAddSheet: View {
    let profile: ServerProfile
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var action = "restart"
    @State private var notifySession = ""
    @State private var notifyMessage = ""
    @State private var cooldown = "300"
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(L("Session name to watch (exact)"), text: $name)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Picker("Action", selection: $action) {
                        Text("restart — relaunch with same task").tag("restart")
                        Text("notify — send message to another session").tag("notify")
                    }
                    if action == "notify" {
                        TextField(L("Target session name (for notify)"), text: $notifySession)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        TextField(L("Message (optional)"), text: $notifyMessage)
                    }
                    TextField(L("Cooldown seconds (default 300)"), text: $cooldown)
                        .keyboardType(.numberPad)
                }
                if let error {
                    Section {
                        Text(error)
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.error)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle("Add Exit Hook")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if saving {
                        ProgressView()
                    } else {
                        Button("Save") { save() }
                            .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
        }
    }

    private func save() {
        saving = true
        error = nil
        IosSettingsCrud.shared.createExitHook(
            profile: profile, name: name, action: action,
            notifySession: notifySession, notifyMessage: notifyMessage, cooldown: cooldown
        ) { err in
            DispatchQueue.main.async {
                saving = false
                if let err {
                    error = err
                } else {
                    onSaved()
                    dismiss()
                }
            }
        }
    }
}

// MARK: - Work Queue

struct SettingsWorkQueueCard: View {
    let profile: ServerProfile

    @State private var items: [IosQueueItem] = []
    @State private var loaded = false
    @State private var error: String?
    @State private var roleFilter = ""
    @State private var stateFilter = ""
    @State private var pendingDelete: IosQueueItem?
    @State private var showPush = false

    private let states: [String] = ["", "pending", "claimed", "complete", "failed"]

    var body: some View {
        List {
            Section {
                Text("WAL-backed durable queue. Sessions holding a role claim items atomically. Uncompleted items whose lease expires automatically return to pending.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .listRowBackground(DatawatchColors.surface)
            }
            Section { filters }
            Section { itemRows }
            Section {
                Button { showPush = true } label: {
                    Label("Push Work Item", systemImage: "plus")
                        .foregroundStyle(DatawatchColors.primary)
                }
                .listRowBackground(DatawatchColors.surface)
            }
            if let error {
                Section {
                    Text(error)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                        .listRowBackground(DatawatchColors.surface)
                }
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .task { load() }
        .refreshable { load() }
        .onChange(of: stateFilter) { _ in load() }
        .sheet(isPresented: $showPush) {
            QueuePushSheet(profile: profile) { load() }
        }
        .confirmationDialog(
            L("Delete queue item") + " \(pendingDelete?.id ?? "")?",
            isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let it = pendingDelete { delete(it) }
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        }
    }

    @ViewBuilder
    private var filters: some View {
        HStack(spacing: 8) {
            TextField(L("Filter role (e.g. worker)"), text: $roleFilter)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onSubmit { load() }
            Button("Refresh") { load() }
                .buttonStyle(.borderless)
                .foregroundStyle(DatawatchColors.primary)
        }
        .listRowBackground(DatawatchColors.surface)
        Picker("Filter state", selection: $stateFilter) {
            ForEach(states, id: \.self) { s in
                Text(s.isEmpty ? L("all") : L(s)).tag(s)
            }
        }
        .listRowBackground(DatawatchColors.surface)
    }

    @ViewBuilder
    private var itemRows: some View {
        if !loaded {
            HStack(spacing: 8) {
                CardSkeleton()
            }
            .listRowBackground(DatawatchColors.surface)
        } else if items.isEmpty {
            Text("No queue items found.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .listRowBackground(DatawatchColors.surface)
        } else {
            ForEach(items, id: \.id) { it in
                QueueItemRow(item: it)
                    .listRowBackground(DatawatchColors.surface)
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) { pendingDelete = it } label: {
                            Label("Delete", systemImage: "trash")
                        }
                        .tint(DatawatchColors.error)
                    }
            }
        }
    }

    private func load() {
        IosSettingsCrud.shared.queue(profile: profile, role: roleFilter, state: stateFilter, onSuccess: { list in
            DispatchQueue.main.async {
                items = list
                loaded = true
                error = nil
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                loaded = true
                error = msg
            }
        })
    }

    private func delete(_ it: IosQueueItem) {
        pendingDelete = nil
        IosSettingsCrud.shared.deleteQueue(profile: profile, id: it.id) { err in
            DispatchQueue.main.async {
                error = err
                load()
            }
        }
    }
}

private struct QueueItemRow: View {
    let item: IosQueueItem

    private var stateColor: Color {
        switch item.state {
        case "pending": return DatawatchColors.success
        case "claimed": return DatawatchColors.warning
        case "complete": return DatawatchColors.onSurfaceMuted
        default: return DatawatchColors.error
        }
    }

    private var meta: String {
        [item.role, item.state, item.claimedBy].filter { !$0.isEmpty }.joined(separator: " · ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(verbatim: item.id)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
            Text(verbatim: meta)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(stateColor)
            if !item.payload.isEmpty && item.payload != "{}" {
                Text(verbatim: item.payload)
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(2)
            }
        }
    }
}

private struct QueuePushSheet: View {
    let profile: ServerProfile
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var role = ""
    @State private var payload = ""
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(L("Role (e.g. worker)"), text: $role)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField(L("Payload JSON (e.g. {\"task\":\"do thing\"})"), text: $payload, axis: .vertical)
                        .font(DatawatchFonts.terminalSmall)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .lineLimit(2...6)
                }
                if let error {
                    Section {
                        Text(error)
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.error)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle("Push Work Item")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if saving {
                        ProgressView()
                    } else {
                        Button("Push Item") { save() }
                            .disabled(role.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
        }
    }

    private func save() {
        saving = true
        error = nil
        IosSettingsCrud.shared.pushQueue(profile: profile, role: role, payload: payload) { err in
            DispatchQueue.main.async {
                saving = false
                if let err {
                    error = err
                } else {
                    onSaved()
                    dismiss()
                }
            }
        }
    }
}
