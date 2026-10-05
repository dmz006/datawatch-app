import SwiftUI
import DatawatchShared

// Settings › Project / Cluster Profiles add/edit (PWA renderProfileEditor:
// renderProjectEditorForm / renderClusterEditorForm + "YAML view" toggle).
// One editor, two views — like Home Assistant's "Edit in YAML": the overflow
// menu switches between the native form and a monospace YAML editor. Switching
// back (or saving) parses the YAML; a parse error is shown inline and the text
// is kept. Unknown keys survive the round trip; literal secrets are masked in
// both views and restored by IosProfileEditor on save.

struct ProfileEditorSheet: View {
    let profile: ServerProfile
    let kind: String
    let name: String?
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var docJson = ""
    @State private var values: [String: String] = [:]
    @State private var yamlMode = false
    @State private var yamlText = ""
    @State private var loaded = false
    @State private var saving = false
    @State private var error: String?

    private var isCluster: Bool { kind == "cluster_profiles" }

    var body: some View {
        NavigationStack {
            content
                .background(DatawatchColors.background)
                .navigationTitle(title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { toolbarContent }
                .task { load() }
        }
        .dwThemed()
    }

    private var title: String {
        if let name { return name }
        return isCluster ? L("New cluster profile") : L("New project profile")
    }

    @ViewBuilder
    private var content: some View {
        if !loaded {
            ProfileEditorLoading(error: error)
        } else if yamlMode {
            ProfileYamlEditor(text: $yamlText, error: error)
        } else {
            ProfileFormView(
                fields: IosProfileEditor.shared.fields(kind: kind),
                values: $values,
                nameLocked: name != nil,
                error: error
            )
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Cancel") { dismiss() }
        }
        ToolbarItem(placement: .primaryAction) {
            Menu {
                Button {
                    toggle()
                } label: {
                    Label(yamlMode ? L("Form view") : L("YAML view"), systemImage: toggleIcon)
                }
            } label: {
                Image(systemName: "ellipsis.circle")
                    .accessibilityLabel(Text("More"))
            }
            .disabled(!loaded || saving)
        }
        ToolbarItem(placement: .confirmationAction) {
            if saving {
                ProgressView()
            } else {
                Button("Save") { save() }.disabled(!loaded)
            }
        }
    }

    private var toggleIcon: String {
        yamlMode ? "list.bullet.rectangle" : "chevron.left.forwardslash.chevron.right"
    }

    // MARK: actions

    private func load() {
        IosProfileEditor.shared.load(profile: profile, kind: kind, name: name ?? "", onSuccess: { draft in
            DispatchQueue.main.async {
                docJson = draft.docJson
                values = draft.values
                loaded = true
            }
        }, onError: { msg in
            DispatchQueue.main.async { error = msg }
        })
    }

    private func toggle() {
        error = nil
        if yamlMode {
            let r = IosProfileEditor.shared.fromYaml(kind: kind, yaml: yamlText)
            if r.error.isEmpty {
                docJson = r.docJson
                values = r.values
                yamlMode = false
            } else {
                error = r.error
            }
        } else {
            yamlText = IosProfileEditor.shared.toYaml(kind: kind, docJson: docJson, values: values)
            yamlMode = true
        }
    }

    private func save() {
        saving = true
        error = nil
        IosProfileEditor.shared.save(
            profile: profile,
            kind: kind,
            originalName: name,
            docJson: docJson,
            values: values,
            yamlMode: yamlMode,
            yamlText: yamlText
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

/// Spinner while the profile loads (or the load error).
private struct ProfileEditorLoading: View {
    let error: String?

    var body: some View {
        VStack {
            if let error {
                FormStatusLine(text: error, tone: 2)
            } else {
                ProgressView()
            }
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Form view: the PWA fields in order, Agent Settings in their own section.
private struct ProfileFormView: View {
    let fields: [ProfileField]
    @Binding var values: [String: String]
    let nameLocked: Bool
    let error: String?

    private var mainFields: [ProfileField] { fields.filter { $0.section.isEmpty } }
    private var agentFields: [ProfileField] { fields.filter { !$0.section.isEmpty } }

    var body: some View {
        Form {
            if let error {
                Section {
                    FormStatusLine(text: error, tone: 2)
                }
                .listRowBackground(DatawatchColors.surface)
            }
            Section {
                ForEach(mainFields, id: \.key) { f in
                    ProfileFieldRow(field: f, value: binding(f.key), locked: nameLocked && f.key == "name")
                }
            }
            .listRowBackground(DatawatchColors.surface)
            if !agentFields.isEmpty {
                Section {
                    ForEach(agentFields, id: \.key) { f in
                        ProfileFieldRow(field: f, value: binding(f.key), locked: false)
                    }
                } header: {
                    Text("Agent Settings")
                }
                .listRowBackground(DatawatchColors.surface)
            }
        }
        .scrollContentBackground(.hidden)
        .scrollDismissesKeyboard(.interactively)
    }

    private func binding(_ key: String) -> Binding<String> {
        Binding(get: { values[key] ?? "" }, set: { values[key] = $0 })
    }
}

/// One form row: text / number / list → text field, select → menu picker, bool → toggle.
private struct ProfileFieldRow: View {
    let field: ProfileField
    @Binding var value: String
    let locked: Bool

    var body: some View {
        switch field.type {
        case "bool":
            Toggle(isOn: boolBinding) {
                Text(L(field.label)).foregroundStyle(DatawatchColors.onSurface)
            }
            .tint(DatawatchColors.primary)
        case "select":
            FormChoiceRow(
                label: field.label,
                value: $value,
                options: IosProfileEditor.shared.options(field: field),
                emptyLabel: IosProfileEditor.shared.hasEmptyOption(field: field) ? "(none)" : ""
            )
        default:
            FormTextField(
                label: field.label,
                text: $value,
                placeholder: field.placeholder.isEmpty ? "" : L(field.placeholder),
                numeric: field.type == "number",
                disabled: locked
            )
        }
    }

    private var boolBinding: Binding<Bool> {
        Binding(get: { value == "true" }, set: { value = $0 ? "true" : "false" })
    }
}

/// YAML view: the whole profile in a monospace editor; parse errors inline.
private struct ProfileYamlEditor: View {
    @Binding var text: String
    let error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Edit the whole profile as YAML. Masked values (••••) are kept unchanged on save.")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            TextEditor(text: $text)
                .font(DatawatchFonts.terminalSmall)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .scrollContentBackground(.hidden)
                .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 8))
            if let error {
                FormStatusLine(text: error, tone: 2)
            }
        }
        .padding()
        .frame(maxHeight: .infinity, alignment: .top)
    }
}
