import SwiftUI
import DatawatchShared

/// 🤖 Council persona wizard (PWA persona modal wizard / Android
/// `CouncilPersonaWizardSheet`): five interview steps — focus · stance · tone ·
/// pushback · examples — each with "Refine with AI…" (server LLM), then a tune
/// page with the assembled prompt, name and description.
struct CouncilPersonaWizardSheet: View {
    let profile: ServerProfile
    var onSaved: () -> Void = {}

    @Environment(\.dismiss) private var dismiss
    @State private var page: Int = 0
    @State private var answers: [String] = ["", "", "", "", ""]
    @State private var backend: String = ""
    @State private var refineInput: String = ""
    @State private var refining = false
    @State private var prompt: String = ""
    @State private var name: String = ""
    @State private var summary: String = ""
    @State private var saving = false
    @State private var error: String? = nil

    private static let total: Int = 6
    private static let stepKeys: [String] = ["focus", "stance", "tone", "pushback", "examples"]
    private static let stepLabels: [String] = ["Focus", "Stance", "Tone", "Pushback", "Examples"]
    private static let questions: [String] = [
        "What is this persona's primary area of focus?",
        "How does this persona approach problems?",
        "How would you describe this persona's communication style?",
        "What does this persona push back against?",
        "Provide 2-3 example responses this persona might give.",
    ]

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 10) {
                ProgressView(value: Double(page + 1), total: Double(Self.total))
                    .tint(DatawatchColors.primary)
                if page < 5 { stepPage } else { tunePage }
                if let error {
                    Text(error)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.error)
                }
                navRow
            }
            .padding(16)
            .background(DatawatchColors.background)
            .navigationTitle("Persona Wizard")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    // MARK: Pages

    private var stepPage: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(L(Self.questions[page]))
                .font(DatawatchFonts.bodyMedium.weight(.semibold))
                .foregroundStyle(DatawatchColors.onSurface)
            editor($answers[page], minHeight: 120)
            if page == 0 { backendPicker }
            refineRow
            Spacer(minLength: 0)
        }
    }

    private var backendPicker: some View {
        HStack(spacing: 8) {
            Text("AI assist backend")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            ForEach(["ollama", "openwebui"], id: \.self) { b in
                Button {
                    backend = backend == b ? "" : b
                } label: {
                    Text(b)
                        .font(DatawatchFonts.badge)
                        .foregroundStyle(backend == b ? Color.white : DatawatchColors.onSurface)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(backend == b ? DatawatchColors.primary : DatawatchColors.surface2, in: Capsule())
                }
                .buttonStyle(.borderless)
            }
        }
    }

    private var refineRow: some View {
        HStack(spacing: 8) {
            TextField("Refine with AI…", text: $refineInput)
                .textFieldStyle(.roundedBorder)
            if refining {
                ProgressView().controlSize(.small)
            } else {
                Button("→") { refine() }
                    .buttonStyle(.bordered)
                    .disabled(refineInput.trimmingCharacters(in: .whitespaces).isEmpty)
                    .accessibilityLabel("Refine with AI")
            }
        }
    }

    private var tunePage: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Review and tune the assembled persona prompt")
                .font(DatawatchFonts.bodyMedium.weight(.semibold))
                .foregroundStyle(DatawatchColors.onSurface)
            editor($prompt, minHeight: 140)
            TextField("Name", text: $name)
                .textFieldStyle(.roundedBorder)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            TextField("Description (optional)", text: $summary)
                .textFieldStyle(.roundedBorder)
            Spacer(minLength: 0)
        }
    }

    private func editor(_ text: Binding<String>, minHeight: Double) -> some View {
        TextEditor(text: text)
            .font(DatawatchFonts.bodyMedium)
            .frame(minHeight: CGFloat(minHeight))
            .scrollContentBackground(.hidden)
            .padding(6)
            .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 6))
            .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.border, lineWidth: 1))
    }

    private var navRow: some View {
        HStack {
            Button("Back") { goTo(page - 1) }
                .disabled(page == 0 || saving)
            Spacer()
            if page < Self.total - 1 {
                Button("Next") { goTo(page + 1) }
                    .buttonStyle(.borderedProminent)
                    .tint(DatawatchColors.primary)
            } else if saving {
                ProgressView()
            } else {
                Button("Save Persona") { save() }
                    .buttonStyle(.borderedProminent)
                    .tint(DatawatchColors.primary)
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
    }

    // MARK: Actions

    private func goTo(_ target: Int) {
        refineInput = ""
        error = nil
        if target == Self.total - 1 { prompt = assembledPrompt() }
        page = max(0, min(Self.total - 1, target))
    }

    /// Android `assembledPrompt`: "Label: answer" lines for non-blank answers.
    private func assembledPrompt() -> String {
        var lines: [String] = []
        for i in 0..<answers.count {
            let a: String = answers[i].trimmingCharacters(in: .whitespacesAndNewlines)
            if !a.isEmpty { lines.append("\(Self.stepLabels[i]): \(a)") }
        }
        return lines.joined(separator: "\n")
    }

    private func refine() {
        let idx: Int = page
        refining = true
        error = nil
        IosCouncilSettings.shared.refineStep(
            profile: profile,
            step: Self.stepKeys[idx],
            currentAnswer: answers[idx],
            instruction: refineInput,
            onSuccess: { refined in
                DispatchQueue.main.async {
                    refining = false
                    answers[idx] = refined
                    refineInput = ""
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    refining = false
                    error = L("Refine failed: ") + msg
                }
            }
        )
    }

    private func save() {
        saving = true
        error = nil
        IosCouncilSettings.shared.createPersona(
            profile: profile, name: name, prompt: prompt, summary: summary, assistBackend: backend
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
