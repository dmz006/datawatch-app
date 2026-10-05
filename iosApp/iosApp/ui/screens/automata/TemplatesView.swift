import SwiftUI
import DatawatchShared

/// Automata Templates tab (parity B15; PWA Templates / Android TemplatesTab):
/// cards with type + tags + variable count, ▶ Use (instantiate), ✎ edit,
/// delete, and ＋ Template.
struct TemplatesView: View {
    let profile: ServerProfile

    @State private var templates: [TemplateDto]? = nil
    @State private var error: String? = nil
    @State private var editing: TemplateDto? = nil
    @State private var creating = false
    @State private var using: TemplateDto? = nil
    @State private var deleting: TemplateDto? = nil

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            content
            // Android Templates-tab FAB (＋ → create template); operator 2026-10-05, PWA #182.
            TemplatesFab { creating = true }
        }
    }

    private var content: some View {
        Group {
            if let templates {
                List {
                    Section {
                        Button {
                            creating = true
                        } label: {
                            Label("Template", systemImage: "plus")
                                .foregroundStyle(DatawatchColors.primary)
                        }
                    }
                    if templates.isEmpty {
                        Text("No templates yet.")
                            .font(DatawatchFonts.bodyMedium)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .listRowBackground(Color.clear)
                    }
                    ForEach(templates, id: \.id) { tmpl in
                        templateCard(tmpl)
                            .listRowBackground(DatawatchColors.surface)
                            .swipeActions(edge: .trailing) {
                                // PWA: built-in templates are read-only (no ✎ / ✕).
                                if !tmpl.isBuiltin {
                                    Button(role: .destructive) { deleting = tmpl } label: {
                                        Label("Delete", systemImage: "trash")
                                    }
                                }
                            }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .refreshable { await reload() }
            } else if let error {
                ErrorCard(message: error) { Task { await reload() } }
            } else {
                LoadingIndicator(message: "Loading templates…")
            }
        }
        .background(DatawatchColors.background)
        .task { await reload() }
        .sheet(isPresented: $creating) {
            TemplateEditView(profile: profile, template: nil) { Task { await reload() } }
        }
        .sheet(item: Binding(get: { editing.map(IdentifiedTemplate.init) }, set: { editing = $0?.template })) { item in
            TemplateEditView(profile: profile, template: item.template) { Task { await reload() } }
        }
        .sheet(item: Binding(get: { using.map(IdentifiedTemplate.init) }, set: { using = $0?.template })) { item in
            InstantiateTemplateView(profile: profile, template: item.template)
        }
        .alert(
            "Delete template?",
            isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } })
        ) {
            Button("Delete", role: .destructive) {
                if let t = deleting { delete(t) }
                deleting = nil
            }
            Button("Cancel", role: .cancel) { deleting = nil }
        } message: {
            Text("\"\(deleting?.title ?? "")\" will be removed. Automata already created from it are not affected.")
        }
    }

    private func templateCard(_ t: TemplateDto) -> some View {
        let vars = TemplateVars.names(in: t.spec)
        return VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(t.title.isEmpty ? t.id : t.title)
                    .font(DatawatchFonts.titleMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(2)
                if t.isBuiltin { PrdMiniPill(text: L("built-in")) }
                Spacer(minLength: 6)
                if let type = t.type, !type.isEmpty {
                    Text(type)
                        .font(DatawatchFonts.badge)
                        .foregroundStyle(DatawatchColors.secondary)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(DatawatchColors.secondary.opacity(0.14), in: Capsule())
                }
            }
            if let d = t.description_, !d.isEmpty {
                Text(d)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(3)
            }
            HStack(spacing: 6) {
                ForEach(t.tags, id: \.self) { tag in
                    Text("#\(tag)")
                        .font(DatawatchFonts.badge)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                if !vars.isEmpty {
                    (vars.count == 1 ? Text("\(vars.count) var") : Text("\(vars.count) vars"))
                        .font(DatawatchFonts.badge)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                if t.useCount > 0 {
                    Text("Used \(Int(t.useCount))×")
                        .font(DatawatchFonts.badge)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                Spacer()
                if !t.isBuiltin {
                    Button("✎ Edit") { editing = t }
                        .font(DatawatchFonts.labelSmall)
                        .buttonStyle(.borderless)
                }
                Button("▶ Use") { using = t }
                    .font(DatawatchFonts.labelSmall.weight(.semibold))
                    .buttonStyle(.borderless)
                    .foregroundStyle(DatawatchColors.primary)
            }
        }
        .padding(.vertical, 6)
    }

    private func reload() async {
        let result: Result<[TemplateDto], Error> = await withCheckedContinuation { cont in
            IosTemplates.shared.list(
                profile: profile,
                onSuccess: { cont.resume(returning: .success($0)) },
                onError: { cont.resume(returning: .failure(ServiceLocatorAsync.TransportError(message: $0))) }
            )
        }
        await MainActor.run {
            switch result {
            case .success(let list): templates = list; error = nil
            case .failure(let e): error = e.localizedDescription
            }
        }
    }

    private func delete(_ t: TemplateDto) {
        IosTemplates.shared.delete(
            profile: profile, id: t.id,
            onSuccess: { Task { await reload() } },
            onError: { msg in DispatchQueue.main.async { error = msg } }
        )
    }
}

/// `{{name}}` placeholders in a template spec (Android VAR_REGEX).
enum TemplateVars {
    static func names(in spec: String) -> [String] {
        guard let re = try? NSRegularExpression(pattern: "\\{\\{(\\w+)\\}\\}") else { return [] }
        let ns = spec as NSString
        var seen: [String] = []
        for m in re.matches(in: spec, range: NSRange(location: 0, length: ns.length)) {
            let name = ns.substring(with: m.range(at: 1))
            if !seen.contains(name) { seen.append(name) }
        }
        return seen
    }
}

/// Android Templates FAB: accent2 (`secondary`) circle with ＋, same chrome as
/// the Automata ⚡ FAB in PrdListView.
private struct TemplatesFab: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "plus")
                .font(.system(.title2).weight(.semibold))
                .foregroundStyle(.white)
                .frame(width: 56, height: 56)
                .background(DatawatchColors.secondary, in: Circle())
                .shadow(color: .black.opacity(0.35), radius: 6, y: 3)
        }
        .padding(.trailing, 20)
        .padding(.bottom, 20)
        .accessibilityLabel("New template")
    }
}

private struct IdentifiedTemplate: Identifiable {
    let template: TemplateDto
    var id: String { template.id }
}

// MARK: - Create / edit

struct TemplateEditView: View {
    let profile: ServerProfile
    let template: TemplateDto?
    var onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var type = "software"
    @State private var description = ""
    @State private var tags = ""
    @State private var spec = ""
    @State private var saving = false
    @State private var errorMessage: String? = nil

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Title", text: $title)
                    Picker("Type", selection: $type) {
                        ForEach(["software", "research", "operational", "personal"], id: \.self) { Text($0).tag($0) }
                    }
                    TextField("Description", text: $description, axis: .vertical)
                    TextField("Tags (comma-separated)", text: $tags)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                Section {
                    TextEditor(text: $spec)
                        .frame(minHeight: 200)
                        .font(DatawatchFonts.terminalSmall)
                        .scrollContentBackground(.hidden)
                } header: {
                    Text("Spec")
                } footer: {
                    Text("Use {{name}} for values filled in when the template is used.")
                }
                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundStyle(DatawatchColors.error).font(DatawatchFonts.bodyMedium)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle(template == nil ? "New Template" : "Edit Template")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() } else {
                        Button("Save") { save() }
                            .fontWeight(.semibold)
                            .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            .onAppear {
                guard let t = template else { return }
                title = t.title
                type = t.type ?? "software"
                description = t.description_ ?? ""
                tags = t.tags.joined(separator: ", ")
                spec = t.spec
            }
        }
        .dwThemed()
    }

    private func save() {
        saving = true
        errorMessage = nil
        IosTemplates.shared.save(
            profile: profile, id: template?.id ?? "", title: title, type: type,
            description: description, tags: tags, spec: spec,
            onSuccess: { DispatchQueue.main.async { saving = false; onSaved(); dismiss() } },
            onError: { msg in DispatchQueue.main.async { saving = false; errorMessage = msg } }
        )
    }
}

// MARK: - Use (instantiate)

struct InstantiateTemplateView: View {
    let profile: ServerProfile
    let template: TemplateDto

    @Environment(\.dismiss) private var dismiss
    @State private var projectDir = ""
    @State private var values: [String: String] = [:]
    @State private var working = false
    @State private var errorMessage: String? = nil
    @State private var createdId: String? = nil

    private var varNames: [String] { TemplateVars.names(in: template.spec) }

    var body: some View {
        NavigationStack {
            Form {
                Section("Workspace") {
                    TextField("Project directory", text: $projectDir, prompt: Text("/path/to/project"))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                if !varNames.isEmpty {
                    Section("Variables") {
                        ForEach(varNames, id: \.self) { name in
                            TextField(name, text: Binding(get: { values[name] ?? "" }, set: { values[name] = $0 }))
                        }
                    }
                }
                if let createdId {
                    Section {
                        Label("Automaton created (\(createdId)). Find it in the Automata tab.", systemImage: "checkmark.circle")
                            .foregroundStyle(DatawatchColors.success)
                    }
                }
                if let errorMessage {
                    Section {
                        Text(errorMessage).foregroundStyle(DatawatchColors.error).font(DatawatchFonts.bodyMedium)
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle(template.title.isEmpty ? "Use Template" : template.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(createdId == nil ? "Cancel" : "Done") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if working { ProgressView() } else if createdId == nil {
                        Button("Create") { create() }.fontWeight(.semibold)
                    }
                }
            }
        }
        .dwThemed()
    }

    private func create() {
        working = true
        errorMessage = nil
        IosTemplates.shared.instantiate(
            profile: profile, id: template.id, projectDir: projectDir,
            vars: values.filter { !$0.value.isEmpty },
            onSuccess: { id in DispatchQueue.main.async { working = false; createdId = id } },
            onError: { msg in DispatchQueue.main.async { working = false; errorMessage = msg } }
        )
    }
}
