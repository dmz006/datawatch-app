import SwiftUI
import DatawatchShared

// ── Tone → colour (PWA CSS vars) ──────────────────────────────────────────────

/// Maps shared-engine tone keys to DatawatchColors (PWA --accent, --warning …).
func dashTone(_ tone: String) -> Color {
    switch tone {
    case "accent": return DatawatchColors.primary
    case "accent2": return DatawatchColors.waiting
    case "warning": return DatawatchColors.warning
    case "success": return DatawatchColors.success
    case "error": return DatawatchColors.error
    case "text": return DatawatchColors.onSurface
    case "border": return DatawatchColors.border
    default: return DatawatchColors.onSurfaceMuted
    }
}

// ── 12-column card grid (PWA .dboard-card-grid) ───────────────────────────────

private struct DashSpanKey: LayoutValueKey {
    static let defaultValue: Int = 12
}

/// Row-packing 12-column grid: cards flow left→right and wrap when the next
/// span would overflow, each row as tall as its tallest card (CSS grid with
/// `grid-auto-flow: row`, which leaves the same holes as the PWA).
struct DashGridLayout: Layout {
    var gap: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width: CGFloat = proposal.width ?? 360
        let frames = place(width: width, subviews: subviews)
        var height: CGFloat = 0
        for f in frames { height = max(height, f.maxY) }
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let frames = place(width: bounds.width, subviews: subviews)
        for (index, subview) in subviews.enumerated() {
            let f = frames[index]
            let origin = CGPoint(x: bounds.minX + f.minX, y: bounds.minY + f.minY)
            subview.place(at: origin, proposal: ProposedViewSize(width: f.width, height: f.height))
        }
    }

    private func place(width: CGFloat, subviews: Subviews) -> [CGRect] {
        let column: CGFloat = max(1, (width - gap * 11) / 12)
        var frames: [CGRect] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var used = 0
        var rowHeight: CGFloat = 0
        for subview in subviews {
            let span = max(1, min(12, subview[DashSpanKey.self]))
            if used + span > 12 {
                y += rowHeight + gap
                x = 0
                used = 0
                rowHeight = 0
            }
            let w: CGFloat = column * CGFloat(span) + gap * CGFloat(span - 1)
            let size = subview.sizeThatFits(ProposedViewSize(width: w, height: nil))
            frames.append(CGRect(x: x, y: y, width: w, height: size.height))
            x += w + gap
            used += span
            rowHeight = max(rowHeight, size.height)
        }
        return frames
    }
}

struct DashCardGrid: View {
    @ObservedObject var vm: DashboardViewModel
    @ObservedObject var collapse: DashCollapseStore
    let profile: ServerProfile
    let viewportWidth: Double
    let onTarget: (DashTarget) -> Void

    var body: some View {
        let metrics = IosDashCatalog.shared.metrics(viewportWidth: viewportWidth)
        let gap = CGFloat(metrics.gap)
        DashGridLayout(gap: gap) {
            ForEach(vm.layout, id: \.id) { card in
                cardView(card, metrics: metrics)
                    .layoutValue(key: DashSpanKey.self, value: span(for: card))
            }
        }
        .padding(gap)
    }

    private func span(for card: IosDashCard) -> Int {
        Int(IosDashCatalog.shared.effectiveSpan(cs: card.cs, viewportWidth: viewportWidth))
    }

    private func cardView(_ card: IosDashCard, metrics: IosDashGridMetrics) -> some View {
        let rows = Double(card.rs)
        var height: Double = metrics.rowHeight * rows + metrics.gap * (rows - 1)
        if card.id == "heatmap" { height = min(height, 200) }
        return DashCardFrame(
            card: card,
            profile: profile,
            height: CGFloat(height),
            vm: vm,
            collapse: collapse
        ) {
            DashCardBody(cardId: card.id, effectiveSpan: span(for: card), vm: vm, revision: vm.revision, onTarget: onTarget)
        }
    }
}

// ── Card chrome (PWA .dboard-card + .dboard-card-hdr) ─────────────────────────

struct DashCardFrame<Content: View>: View {
    let card: IosDashCard
    let profile: ServerProfile
    let height: CGFloat
    @ObservedObject var vm: DashboardViewModel
    @ObservedObject var collapse: DashCollapseStore
    @ViewBuilder let content: () -> Content

    private var def: IosDashCardDef? { IosDashCatalog.shared.def(id: card.id) }
    private var collapsed: Bool { collapse.isCollapsed(card.id) }
    private var title: String { def?.label ?? card.id }

    var body: some View {
        VStack(spacing: 0) {
            header
            if !collapsed {
                content()
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    .clipped()
            }
        }
        .frame(height: collapsed ? nil : height)
        .background(DatawatchColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(
            RoundedRectangle(cornerRadius: 6)
                .stroke(vm.editing ? DatawatchColors.primary : DatawatchColors.border, lineWidth: 1)
        )
    }

    private var header: some View {
        HStack(spacing: 5) {
            if vm.editing { moveButtons }
            Button {
                withAnimation(.easeInOut(duration: 0.15)) { collapse.toggle(card.id) }
            } label: {
                HStack(spacing: 5) {
                    Image(systemName: "chevron.right")
                        .font(.caption2.weight(.semibold))
                        .rotationEffect(.degrees(collapsed ? 0 : 90))
                    Image(systemName: def?.symbol ?? "square")
                        .font(.caption2)
                    Text(L(title).uppercased())
                        .font(.caption2.weight(.bold))
                        .lineLimit(1)
                    Spacer(minLength: 2)
                }
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(L(title)))
            .accessibilityHint(Text(L(collapsed ? "Expand" : "Collapse")))
            if vm.editing { editButtons }
            DocsLinkButton(profile: profile, anchor: IosDashCatalog.shared.docsSlug(label: title))
                .font(.caption)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .frame(minHeight: 26)
        .overlay(alignment: .bottom) {
            if !collapsed {
                Rectangle().fill(DatawatchColors.border).frame(height: 1)
            }
        }
    }

    /// iOS stand-in for the PWA drag handle (reorder by one slot).
    private var moveButtons: some View {
        HStack(spacing: 2) {
            Button { vm.move(card.id, by: -1) } label: { Image(systemName: "chevron.up") }
                .accessibilityLabel("Move card earlier")
            Button { vm.move(card.id, by: 1) } label: { Image(systemName: "chevron.down") }
                .accessibilityLabel("Move card later")
        }
        .font(.caption2.weight(.bold))
        .buttonStyle(.borderless)
        .tint(DatawatchColors.primary)
    }

    /// PWA `Nw` / `Nh` span cyclers and the × remove button.
    private var editButtons: some View {
        HStack(spacing: 4) {
            Button { vm.cycleSpan(card.id) } label: { Text("\(Int(card.cs))w") }
                .accessibilityLabel("Card width")
            Button { vm.cycleRows(card.id) } label: { Text("\(Int(card.rs))h") }
                .accessibilityLabel("Card height")
            if !card.system {
                Button(role: .destructive) { vm.remove(card.id) } label: { Image(systemName: "xmark") }
                    .accessibilityLabel("Remove card")
            }
        }
        .font(.caption2.monospaced())
        .buttonStyle(.bordered)
        .controlSize(.mini)
        .tint(DatawatchColors.onSurfaceMuted)
    }
}

// ── Card body dispatch (DASH_CARD_DEFS body()) ────────────────────────────────

struct DashCardBody: View {
    let cardId: String
    let effectiveSpan: Int
    @ObservedObject var vm: DashboardViewModel
    let revision: Int
    let onTarget: (DashTarget) -> Void

    var body: some View {
        switch cardId {
        case "tree":
            DashTreeCard(tree: vm.engine.tree(), onTarget: onTarget, onToggle: { vm.toggleTreeRow($0) })
        case "orbital":
            DashNetworkCard(vm: vm, revision: revision, onTarget: onTarget)
        case "events":
            DashEventsCard(events: vm.engine.events())
        case "sparklines":
            DashSparklinesCard(engine: vm.engine, revision: revision)
        case "gantt":
            DashGanttCard(engine: vm.engine, revision: revision, onTarget: onTarget, onToggle: { vm.toggleTreeRow($0) })
        case "heatmap":
            DashHeatmapCard(engine: vm.engine, revision: revision, effectiveSpan: effectiveSpan)
        case "guardrails":
            DashGuardrailsCard(model: vm.engine.guardrails())
        case "ekg":
            DashEkgCard(engine: vm.engine, revision: revision)
        case "smoke":
            DashSmokeCard(view: vm.engine.smokeView(), vm: vm)
        case "memory-scope":
            DashMemoryCard(stats: vm.engine.memStats())
        case "websearch-usage":
            DashSearchUsageCard(stats: vm.engine.webSearch())
        default:
            EmptyView()
        }
    }
}

// ── Shared bits ───────────────────────────────────────────────────────────────

/// Small muted placeholder line (PWA `color:var(--text2);font-size:10px`).
struct DashMutedLine: View {
    let text: String

    var body: some View {
        Text(L(text))
            .font(.caption2)
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
    }
}

/// Thin horizontal progress bar (PWA 2–5 px bars on var(--border)).
struct DashBar: View {
    let fraction: Double
    let color: Color
    var height: CGFloat = 4

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(DatawatchColors.border)
                Capsule().fill(color).frame(width: geo.size.width * CGFloat(max(0, min(1, fraction))))
            }
        }
        .frame(height: height)
    }
}

/// Section caption (PWA uppercase 9px letter-spaced label).
struct DashSectionCaption: View {
    let text: String

    var body: some View {
        Text(L(text).uppercased())
            .font(.caption2.weight(.bold))
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
            .padding(.horizontal, 10)
            .padding(.top, 5)
            .padding(.bottom, 3)
    }
}
