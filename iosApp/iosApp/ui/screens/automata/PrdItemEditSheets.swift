import SwiftUI
import DatawatchShared

/// What the story/task edit sheet is editing (parity B18; PWA story/task
/// edit groups: ✎ title/desc · 📁 files · + Add story · ✎ spec · + Add task).
enum PrdItemEdit: Identifiable {
    case editStory(PrdStoryDto)
    case storyFiles(PrdStoryDto)
    case addStory
    case taskSpec(PrdTaskDto)
    case taskFiles(PrdTaskDto)
    case addTask(PrdStoryDto)

    var id: String {
        switch self {
        case .editStory(let s): return "es-\(s.id)"
        case .storyFiles(let s): return "sf-\(s.id)"
        case .addStory: return "as"
        case .taskSpec(let t): return "ts-\(t.id)"
        case .taskFiles(let t): return "tf-\(t.id)"
        case .addTask(let s): return "at-\(s.id)"
        }
    }
}

struct PrdItemEditSheet: View {
    let profile: ServerProfile
    let prdId: String
    let edit: PrdItemEdit
    let onSaved: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var text = ""
    @State private var saving = false
    @State private var error: String? = nil

    private var navTitle: String {
        switch edit {
        case .editStory: return "Edit story"
        case .storyFiles: return "Story files"
        case .addStory: return "Add story"
        case .taskSpec: return "Edit task"
        case .taskFiles: return "Task files"
        case .addTask: return "Add task"
        }
    }

    private var hasTitle: Bool {
        switch edit {
        case .editStory, .addStory, .addTask: return true
        default: return false
        }
    }

    private var textLabel: String {
        switch edit {
        case .editStory, .addStory: return "Description"
        case .storyFiles, .taskFiles: return "Planned files (one per line)"
        case .taskSpec, .addTask: return "Spec"
        }
    }

    var body: some View {
        NavigationStack {
            Form {
                if hasTitle {
                    Section("Title") { TextField("Title", text: $title) }
                        .listRowBackground(DatawatchColors.surface)
                }
                Section(textLabel) {
                    TextEditor(text: $text)
                        .font(isFiles ? DatawatchFonts.terminalSmall : DatawatchFonts.bodyMedium)
                        .frame(minHeight: 160)
                        .textInputAutocapitalization(isFiles ? .never : .sentences)
                        .autocorrectionDisabled(isFiles)
                }
                .listRowBackground(DatawatchColors.surface)
                if let error {
                    Section { Text(error).foregroundStyle(DatawatchColors.error).font(DatawatchFonts.bodyMedium) }
                }
            }
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationTitle(navTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if saving { ProgressView() } else {
                        Button("Save") { save() }
                            .disabled(hasTitle && title.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            .onAppear(perform: prefill)
        }
    }

    private var isFiles: Bool {
        if case .storyFiles = edit { return true }
        if case .taskFiles = edit { return true }
        return false
    }

    private func prefill() {
        switch edit {
        case .editStory(let s): title = s.title; text = s.description_ ?? ""
        case .storyFiles(let s): text = s.files.joined(separator: "\n")
        case .taskSpec(let t): text = t.spec
        case .taskFiles(let t): text = t.files.joined(separator: "\n")
        case .addStory, .addTask: break
        }
    }

    private func save() {
        saving = true
        error = nil
        let done: (String?) -> Void = { err in
            DispatchQueue.main.async {
                saving = false
                if let err { error = err } else { onSaved(); dismiss() }
            }
        }
        let ops = IosPrdItemEdit.shared
        switch edit {
        case .editStory(let s): ops.editStory(profile: profile, prdId: prdId, storyId: s.id, title: title, description: text, onDone: done)
        case .storyFiles(let s): ops.editStoryFiles(profile: profile, prdId: prdId, storyId: s.id, files: text, onDone: done)
        case .addStory: ops.addStory(profile: profile, prdId: prdId, title: title, description: text, onDone: done)
        case .taskSpec(let t): ops.editTaskSpec(profile: profile, prdId: prdId, taskId: t.id, spec: text, onDone: done)
        case .taskFiles(let t): ops.editTaskFiles(profile: profile, prdId: prdId, taskId: t.id, files: text, onDone: done)
        case .addTask(let s): ops.addTask(profile: profile, prdId: prdId, storyId: s.id, title: title, spec: text, onDone: done)
        }
    }
}

// ── File chips + viewer (PWA _fileChip; Android FilePill → FileViewerSheet #181) ──

enum PrdFiles {
    static let viewable: Set<String> = [
        "md", "txt", "json", "yaml", "yml", "go", "js", "ts", "jsx", "tsx",
        "py", "rb", "sh", "css", "html", "xml", "csv", "log", "toml", "ini",
        "conf", "cfg", "sql", "rs", "c", "cpp", "h", "java", "kt", "swift",
    ]
    static func isViewable(_ path: String) -> Bool {
        viewable.contains((path as NSString).pathExtension.lowercased())
    }
}

/// Wrapping row of file chips. `conflicts` marks planned files shared by
/// more than one open story (⚠). Viewable files open the viewer.
struct PrdFileChips: View {
    let label: String
    let files: [String]
    var conflicts: Set<String> = []
    var onOpen: (String) -> Void

    var body: some View {
        if !files.isEmpty {
            VStack(alignment: .leading, spacing: 3) {
                Text(L(label)).font(DatawatchFonts.badge).foregroundStyle(DatawatchColors.onSurfaceMuted)
                FlowLayout(spacing: 4) {
                    ForEach(files, id: \.self) { f in chip(f) }
                }
            }
        }
    }

    private func chip(_ f: String) -> some View {
        let conflict = conflicts.contains(f)
        let viewable = PrdFiles.isViewable(f)
        return Button { onOpen(f) } label: {
            Text((conflict ? "⚠ " : "") + (f as NSString).lastPathComponent)
                .font(.system(size: 10, design: .monospaced))
                .foregroundStyle(conflict ? DatawatchColors.warning : (viewable ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted))
                .padding(.horizontal, 6).padding(.vertical, 2)
                .background(DatawatchColors.surface2, in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!viewable)
        .accessibilityLabel(conflict ? "\(f), planned by more than one story" : f)
    }
}

/// Minimal flow layout for chips (iOS 16 Layout).
struct FlowLayout: Layout {
    var spacing: CGFloat = 4
    /// Pin the last subview to the trailing edge of its row (CSS
    /// `margin-left:auto` in a `flex-wrap` row — the PWA card meta row).
    var trailingLast: Bool = false

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0, widest: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > maxW { x = 0; y += rowH + spacing; rowH = 0 }
            x += s.width + spacing
            rowH = max(rowH, s.height)
            widest = max(widest, x - spacing)
        }
        if trailingLast, maxW.isFinite { widest = maxW }
        return CGSize(width: min(widest, maxW), height: y + rowH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowH: CGFloat = 0
        // Break into rows first so each row's height is known before placing.
        var rows: [[(Int, CGSize)]] = [[]]
        for (i, v) in subviews.enumerated() {
            let s = v.sizeThatFits(.unspecified)
            if x > bounds.minX && x + s.width > bounds.maxX { x = bounds.minX; rows.append([]) }
            rows[rows.count - 1].append((i, s))
            x += s.width + spacing
        }
        for row in rows {
            rowH = row.map { $0.1.height }.max() ?? 0
            x = bounds.minX
            for (i, s) in row {
                var px = x
                if trailingLast && i == subviews.count - 1 { px = max(x, bounds.maxX - s.width) }
                // Meta rows centre items vertically (CSS align-items:center);
                // other flow uses keep their top alignment.
                let dy: CGFloat = trailingLast ? (rowH - s.height) / 2 : 0
                subviews[i].place(at: CGPoint(x: px, y: y + dy), proposal: ProposedViewSize(s))
                x += s.width + spacing
            }
            y += rowH + spacing
        }
    }
}

struct PrdOpenFile: Identifiable {
    let path: String
    var id: String { path }
}

struct PrdFileViewerSheet: View {
    let profile: ServerProfile
    let path: String
    let projectDir: String?

    @Environment(\.dismiss) private var dismiss
    @State private var content: String? = nil
    @State private var absPath: String = ""
    @State private var failed = false

    var body: some View {
        NavigationStack {
            Group {
                if let content {
                    ScrollView {
                        Group {
                            if path.lowercased().hasSuffix(".md"),
                               let md = try? AttributedString(markdown: content, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)) {
                                Text(md).font(DatawatchFonts.bodyMedium)
                            } else {
                                Text(content).font(.system(size: 11, design: .monospaced))
                            }
                        }
                        .foregroundStyle(DatawatchColors.onSurface)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                    }
                } else if failed {
                    Text("Unable to load file").foregroundStyle(DatawatchColors.error)
                } else {
                    CardSkeleton()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(DatawatchColors.background)
            .navigationTitle((path as NSString).lastPathComponent)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
                if let content {
                    ToolbarItem(placement: .primaryAction) {
                        ShareLink(item: content, subject: Text((absPath as NSString).lastPathComponent)) {
                            Image(systemName: "square.and.arrow.up")
                        }
                        .accessibilityLabel("Share / download")
                    }
                }
            }
        }
        .task {
            IosPrdItemEdit.shared.fileContent(
                profile: profile, path: path, projectDir: projectDir,
                onSuccess: { abs, text in DispatchQueue.main.async { absPath = abs; content = text } },
                onError: { _ in DispatchQueue.main.async { failed = true } }
            )
        }
    }
}
