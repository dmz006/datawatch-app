import SwiftUI
import DatawatchShared

/// PWA `prd-story-verdicts` (first row of the story body): one solid badge per
/// guardrail verdict — "guardrail: outcome", white 9 pt on the outcome colour
/// (pass #10b981 · warn #f59e0b · block #ef4444 · else #6b7280). Tapping a badge
/// toggles the inline drill-down (PWA `toggleVerdictDrilldown`).
struct PrdStoryVerdictsRow: View {
    let verdicts: [GuardrailVerdictDto]
    @State private var open: Int?

    var body: some View {
        if verdicts.isEmpty {
            EmptyView()
        } else {
            VStack(alignment: .leading, spacing: 3) {
                FlowLayout(spacing: 4) {
                    ForEach(Array(verdicts.enumerated()), id: \.offset) { pair in
                        badge(pair.element, index: pair.offset)
                    }
                }
                if let i = open, i < verdicts.count {
                    PrdVerdictDrilldown(verdict: verdicts[i])
                }
            }
            .padding(.top, 4)
            .padding(.bottom, 6)
        }
    }

    private func badge(_ v: GuardrailVerdictDto, index: Int) -> some View {
        let g: String = v.guardrail.isEmpty ? "?" : v.guardrail
        let o: String = v.outcome.isEmpty ? "?" : v.outcome
        return Button {
            open = (open == index) ? nil : index
        } label: {
            Text(verbatim: g + ": " + o)
                .font(.system(size: 9))
                .foregroundStyle(Color.white)
                .padding(.horizontal, 5)
                .padding(.vertical, 1)
                .background(Self.color(o), in: RoundedRectangle(cornerRadius: 6))
        }
        .buttonStyle(.plain)
    }

    static func color(_ outcome: String) -> Color {
        switch outcome.lowercased() {
        case "pass": return Color(red: 16.0 / 255.0, green: 185.0 / 255.0, blue: 129.0 / 255.0)
        case "warn": return Color(red: 245.0 / 255.0, green: 158.0 / 255.0, blue: 11.0 / 255.0)
        case "block": return Color(red: 239.0 / 255.0, green: 68.0 / 255.0, blue: 68.0 / 255.0)
        default: return Color(red: 107.0 / 255.0, green: 114.0 / 255.0, blue: 128.0 / 255.0)
        }
    }
}

/// Drill-down panel: "guardrail — outcome [severity]", summary, issues (or "no issues").
private struct PrdVerdictDrilldown: View {
    let verdict: GuardrailVerdictDto

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            Rectangle()
                .fill(DatawatchColors.onSurfaceMuted.opacity(0.4))
                .frame(width: 2)
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: header)
                    .font(.system(size: 10, weight: .semibold))
                if !verdict.summary.isEmpty {
                    Text(verbatim: verdict.summary).font(.system(size: 10))
                }
                issues
            }
            .foregroundStyle(DatawatchColors.onSurface)
            .padding(.horizontal, 6)
            .padding(.vertical, 4)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(Color.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 3))
    }

    private var header: String {
        let g: String = verdict.guardrail.isEmpty ? "?" : verdict.guardrail
        let o: String = verdict.outcome.isEmpty ? "?" : verdict.outcome
        var s: String = g + " — " + o
        if !verdict.severity.isEmpty { s += " [" + verdict.severity + "]" }
        return s
    }

    @ViewBuilder
    private var issues: some View {
        if verdict.issues.isEmpty {
            Text("• " + L("no issues"))
                .font(.system(size: 10).italic())
                .opacity(0.7)
        } else {
            ForEach(verdict.issues, id: \.self) { issue in
                Text(verbatim: "• " + issue).font(.system(size: 10))
            }
        }
    }
}
