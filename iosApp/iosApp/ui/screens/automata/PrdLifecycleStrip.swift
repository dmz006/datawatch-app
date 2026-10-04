import SwiftUI
import DatawatchShared

/// Lifecycle strip plan › review › approve › run › done with the current-step
/// hint (PWA renderLifecycleStrip app.js:11227, style.css .lifecycle-*).
/// `onAction` nil → display-only (list cards); otherwise current steps are
/// tappable: "decompose", "approve", "reject", "request_revision", "run", "cancel".
struct PrdLifecycleStrip: View {
    let prd: PrdDto
    var compact: Bool = true
    var onAction: ((String) -> Void)? = nil

    private var status: String { prd.status.isEmpty ? "draft" : prd.status.lowercased() }

    private static let steps = ["plan", "review", "approve", "run", "done"]
    private var stepIdx: Int {
        let map: [String: String] = [
            "draft": "plan", "planning": "plan", "revisions_asked": "plan",
            "needs_review": "review", "approved": "approve", "running": "run",
            "completed": "done", "rejected": "done", "cancelled": "done", "archived": "done",
        ]
        return Self.steps.firstIndex(of: map[status] ?? "plan") ?? 0
    }

    private enum Look { case idle, done, current, clickable, danger, warn, rejected, faded }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(L(hint).uppercased())
                .font(.system(size: 11, weight: .semibold))
                .kerning(0.4)
                .foregroundStyle(DatawatchColors.primary)
                .lineLimit(2)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 0) {
                    planStep; sep; reviewStep; sep; approveStep; sep; runStep; sep; doneStep
                }
            }
        }
    }

    private var hint: String {
        switch Self.steps[stepIdx] {
        case "plan": return "Next: Plan — break your spec into stories + tasks"
        case "review":
            return status == "needs_review"
                ? "Plan ready — review the stories below and Approve / Reject / Revise"
                : "Planning… stories will appear here shortly"
        case "approve": return "Next: Review the plan and Approve / Reject / Revise"
        case "run": return status == "running" ? "Running — Cancel below if needed" : "Next: Run the approved automaton"
        default:
            switch status {
            case "completed": return "✓ Completed"
            case "rejected": return "✗ Rejected"
            case "cancelled": return "Cancelled"
            case "archived": return "Archived"
            default: return "✓ Done"
            }
        }
    }

    private var sep: some View {
        Text("›").font(.system(size: compact ? 10 : 11)).foregroundStyle(DatawatchColors.onSurfaceMuted).padding(.horizontal, 3)
    }

    @ViewBuilder private var planStep: some View {
        if stepIdx > 0 { step("✓ Plan", .done) }
        else { step("▶ " + (status == "revisions_asked" ? "Re-plan" : "Plan"), .current, action: "decompose") }
    }

    @ViewBuilder private var reviewStep: some View {
        if stepIdx > 1 { step("✓ Review", .done) }
        else if stepIdx == 1 { step("Review", .current) }
        else { step("Review", .idle) }
    }

    @ViewBuilder private var approveStep: some View {
        if stepIdx > 2 { step("✓ Approve", .done) }
        else if stepIdx == 2 {
            HStack(spacing: 2) {
                step("Approve", .current, action: "approve")
                step("✗", .danger, action: "reject")
                step("↩", .warn, action: "request_revision")
            }
        } else { step("Approve", .idle) }
    }

    @ViewBuilder private var runStep: some View {
        if status == "running" { step("■ Cancel", .danger, action: "cancel") }
        else if stepIdx > 3 { step("✓ Run", .done) }
        else if stepIdx == 3 && status == "approved" { step("▶ Run", .current, action: "run") }
        else { step("Run", .idle) }
    }

    @ViewBuilder private var doneStep: some View {
        switch status {
        case "completed": step("✓ Done", .done)
        case "rejected": step("✗ Rejected", .rejected)
        case "cancelled": step("Cancelled", .faded)
        default: step("Done", .idle)
        }
    }

    @ViewBuilder
    private func step(_ label: String, _ look: Look, action: String? = nil) -> some View {
        let enabled = action != nil && onAction != nil
        let text = Text(L(label))
            .font(.system(size: compact ? 10 : 11, weight: .medium))
            .lineLimit(1)
            .padding(.horizontal, compact ? 6 : 9)
            .padding(.vertical, compact ? 2 : 3)
            .foregroundStyle(fg(look))
            .background(bg(look), in: RoundedRectangle(cornerRadius: 4))
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(stroke(look), lineWidth: 1))
            .opacity(look == .faded ? 0.6 : 1)
        if enabled, let action {
            Button { onAction?(action) } label: { text }
                .buttonStyle(.borderless)
        } else {
            text
        }
    }

    private func fg(_ l: Look) -> Color {
        switch l {
        case .current, .done: return .white
        case .danger, .rejected: return DatawatchColors.error
        case .warn: return DatawatchColors.warning
        case .clickable: return DatawatchColors.primary
        default: return DatawatchColors.onSurfaceMuted
        }
    }
    private func bg(_ l: Look) -> Color {
        switch l {
        case .current: return DatawatchColors.primary
        case .done: return DatawatchColors.success
        default: return DatawatchColors.background
        }
    }
    private func stroke(_ l: Look) -> Color {
        switch l {
        case .current, .clickable: return DatawatchColors.primary
        case .done: return DatawatchColors.success
        case .danger, .rejected: return DatawatchColors.error
        case .warn: return DatawatchColors.warning
        default: return DatawatchColors.border
        }
    }
}
