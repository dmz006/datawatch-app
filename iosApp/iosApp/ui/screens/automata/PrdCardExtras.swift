import SwiftUI
import DatawatchShared

/// PWA automata-card extras (parity 05 › Card rows): type / template badges,
/// running position line, "Stories & tasks (N)" tree, status left border.
enum PrdCardStyle {
    /// PWA `.prd-card-status-*` border-left colours (css:400–411).
    static func edgeColor(_ status: String) -> Color {
        switch status.lowercased() {
        case "decomposing", "planning": return DatawatchColors.secondary
        case "needs_review", "revisions_asked": return DatawatchColors.warning
        case "approved": return DatawatchColors.primary
        case "running": return DatawatchColors.success
        case "blocked", "rejected": return DatawatchColors.error
        default: return DatawatchColors.onSurfaceMuted
        }
    }

    /// PWA type badge per-type colour (app:16727 / Android TypeBadge).
    static func typeColor(_ type: String) -> Color {
        switch type.lowercased() {
        case "software": return Color(hex: 0x6366F1)
        case "research": return Color(hex: 0xF59E0B)
        case "operational": return Color(hex: 0x10B981)
        case "personal": return Color(hex: 0xEC4899)
        default: return DatawatchColors.onSurfaceMuted
        }
    }

    /// PWA/Android `currentPositionLine`: first active task while running.
    static func positionLine(_ prd: PrdDto) -> String? {
        guard prd.status.lowercased() == "running" else { return nil }
        for (si, story) in prd.stories.enumerated() {
            for (ti, task) in story.tasks.enumerated() {
                let st = task.status
                guard st == "in_progress" || st == "verifying" || st == "running_tests" else { continue }
                let icon: String = st == "verifying" ? "⟳" : (st == "running_tests" ? "🧪" : "▶")
                let suffix: String = st == "verifying" ? " (" + L("verifying") + ")" : (st == "running_tests" ? " (" + L("testing") + ")" : "")
                let storyTitle: String = story.title.isEmpty ? "?" : story.title
                let taskTitle: String = task.task.isEmpty ? "?" : task.task
                return "\(icon) \(L("Story")) \(si + 1): \(storyTitle) · \(L("Task")) \(ti + 1): \(taskTitle)\(suffix)"
            }
        }
        return nil
    }
}

/// Outlined type badge (PWA `.automata-filter-badge.type-badge`).
struct PrdTypeBadge: View {
    let type: String
    var body: some View {
        let color = PrdCardStyle.typeColor(type)
        Text(type.lowercased())
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(color)
            .padding(.horizontal, 7)
            .padding(.vertical, 1)
            .background(color.opacity(0.13), in: RoundedRectangle(cornerRadius: 8))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(color, lineWidth: 1))
            .accessibilityLabel("Type: \(type)")
    }
}

/// Solid "template" badge (#7c3aed) when `is_template`.
struct PrdTemplateBadge: View {
    var body: some View {
        Text("template")
            .font(.system(size: 10, weight: .semibold))
            .foregroundStyle(Color.white)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .background(Color(hex: 0x7C3AED), in: RoundedRectangle(cornerRadius: 8))
    }
}

/// List-row background: PWA `.prd-card` — bg2, radius 12, 4 pt status edge,
/// no outline, 14 pt gap between cards (7 pt above + below each row).
struct PrdRowBackground: View {
    let status: String
    var body: some View {
        HStack(spacing: 0) {
            Rectangle().fill(PrdCardStyle.edgeColor(status)).frame(width: 4)
            DatawatchColors.surface
        }
        .clipShape(RoundedRectangle(cornerRadius: DatawatchRadius.card))
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(DatawatchColors.background)
    }

    /// Row insets matching the background: PWA card `padding:14px` + 4 pt edge + outer gap.
    static let insets = EdgeInsets(top: 21, leading: 30, bottom: 21, trailing: 26)
}

/// PWA card `<details>` "Stories & tasks (N)" → renderDetailStoriesTree (compact).
struct PrdStoriesTree: View {
    let prd: PrdDto
    @State private var open = false

    /// A borderless toggle rather than DisclosureGroup so taps don't fire the
    /// row's NavigationLink.
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button { open.toggle() } label: {
                // PWA `<details><summary>`: 12 px accent, ▶ closed / ▼ open marker.
                Text(verbatim: (open ? "▼ " : "▶ ") + "\(L("Stories & tasks")) (\(prd.stories.count))")
                    .font(.system(size: 12))
                    .foregroundStyle(DatawatchColors.primary)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(open ? "Hide stories and tasks" : "Show stories and tasks")
            if open { treeBody }
        }
    }

    private var treeBody: some View {
        VStack(alignment: .leading, spacing: 4) {
            if prd.stories.isEmpty {
                Text("no stories yet")
                    .italic()
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            ForEach(Array(prd.stories.enumerated()), id: \.offset) { si, story in
                storyBlock(si, story)
            }
        }
    }

    private func storyBlock(_ index: Int, _ story: PrdStoryDto) -> some View {
        let done: Int = story.tasks.filter { PrdStatusStyle.isDone($0.status) }.count
        return VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                Text("\(index + 1). " + (story.title.isEmpty ? story.id : story.title))
                    .font(DatawatchFonts.labelSmall.weight(.semibold))
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(2)
                Spacer(minLength: 4)
                Text("\(done)/\(story.tasks.count)")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            ForEach(Array(story.tasks.enumerated()), id: \.offset) { _, task in
                taskLine(task)
            }
        }
    }

    private func taskLine(_ task: PrdTaskDto) -> some View {
        let (glyph, color) = PrdStatusStyle.taskGlyph(task.status)
        return HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(glyph).foregroundStyle(color).frame(width: 14)
            Text(task.task.isEmpty ? task.id : task.task)
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(1)
            Spacer(minLength: 4)
            Text(task.status).foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .font(DatawatchFonts.labelSmall)
        .padding(.leading, 10)
    }
}
