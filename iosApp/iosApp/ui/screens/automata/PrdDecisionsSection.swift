import SwiftUI
import DatawatchShared

/// PRD decisions timeline (parity B17; PWA _renderDetailDecisionsTab):
/// newest first, kind · by actor · time, note, then backend/model, chars,
/// cost and verdict when recorded. Collapsible section in the single-scroll
/// detail until D24 decides the detail-tabs layout.
struct PrdDecisionsSection: View {
    let decisions: [DecisionDto]
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button { withAnimation { expanded.toggle() } } label: {
                HStack {
                    Text("DECISIONS (\(decisions.count))")
                        .font(DatawatchFonts.badge)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.caption2)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .buttonStyle(.plain)
            if expanded {
                if decisions.isEmpty {
                    Text("No decisions recorded yet.")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                } else {
                    ForEach(Array(decisions.reversed().enumerated()), id: \.offset) { _, d in
                        card(d)
                    }
                }
            }
        }
        .padding(14)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 10))
    }

    private func card(_ d: DecisionDto) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Text(d.kind ?? "?")
                    .font(DatawatchFonts.terminalSmall.weight(.semibold))
                    .foregroundStyle(DatawatchColors.primary)
                if let a = d.actor, !a.isEmpty {
                    Text("by \(a)").font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                Spacer()
                Text(Self.formatTime(d.at))
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            if let n = d.note, !n.isEmpty {
                Text(n).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurface)
            }
            ForEach(Self.pairs(d), id: \.0) { pair in
                let (k, v, color) = pair
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    Text(k).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .frame(width: 64, alignment: .leading)
                    Text(v).font(DatawatchFonts.labelSmall.weight(color == nil ? .regular : .semibold))
                        .foregroundStyle(color ?? DatawatchColors.onSurface)
                }
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.onSurfaceMuted.opacity(0.25), lineWidth: 1))
    }

    private static func pairs(_ d: DecisionDto) -> [(String, String, Color?)] {
        var out: [(String, String, Color?)] = []
        let backend = d.backend ?? ""
        let model = d.model ?? ""
        if !backend.isEmpty || !model.isEmpty {
            out.append(("Backend", backend + (model.isEmpty ? "" : " · \(model)"), nil))
        }
        if d.promptChars > 0 || d.responseChars > 0 {
            let f = NumberFormatter(); f.numberStyle = .decimal
            let p = f.string(from: NSNumber(value: d.promptChars)) ?? "\(d.promptChars)"
            let r = f.string(from: NSNumber(value: d.responseChars)) ?? "\(d.responseChars)"
            out.append(("Chars", "\(p) in · \(r) out", nil))
        }
        if d.costUsd > 0 { out.append(("Cost", String(format: "$%.4f", d.costUsd), nil)) }
        if let v = d.verdictOutcome, !v.isEmpty {
            let c: Color = v == "pass" ? DatawatchColors.success : v == "warn" ? DatawatchColors.warning
                : v == "block" ? DatawatchColors.error : DatawatchColors.onSurfaceMuted
            out.append(("Verdict", v, c))
        }
        return out
    }

    private static func formatTime(_ iso: String?) -> String {
        guard let iso, !iso.isEmpty else { return "" }
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = f.date(from: iso) ?? { f.formatOptions = [.withInternetDateTime]; return f.date(from: iso) }()
        guard let date else { return iso }
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}
