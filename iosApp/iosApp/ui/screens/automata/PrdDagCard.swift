import SwiftUI
import DatawatchShared

/// Automaton dependency graph card on the detail Overview — port of Android's
/// PrdDetailDialog "Graph" card + PrdDagCanvas (#184; operator 2026-10-05,
/// PWA side dmz006/datawatch#182). Shown while loading and when the graph has
/// nodes; hidden when the server has no graph (same as Android). Layout is the
/// shared `PrdDagLayout` so both apps draw the identical layered DAG.
struct PrdDagCard: View {
    let profile: ServerProfile
    let prdId: String

    @State private var layout: PrdDagLayoutResult? = nil
    @State private var loading: Bool = true

    var body: some View {
        Group {
            if loading {
                PrdDagCardFrame { PrdDagLoadingBody() }
            } else if let l = layout, !l.nodes.isEmpty {
                PrdDagCardFrame { PrdDagCanvasView(layout: l) }
            }
        }
        .task(id: prdId) { await load() }
    }

    private func load() async {
        loading = true
        let result: PrdDagLayoutResult? = await withCheckedContinuation { cont in
            IosPrdGraph.shared.load(profile: profile, prdId: prdId) { cont.resume(returning: $0) }
        }
        await MainActor.run {
            layout = result
            loading = false
        }
    }
}

/// Card chrome: "GRAPH" title (Android PrdOverviewCardTitle) over the content.
private struct PrdDagCardFrame<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Graph")
                .textCase(.uppercase)
                .font(DatawatchFonts.badge)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            content()
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: DatawatchRadius.card))
    }
}

private struct PrdDagLoadingBody: View {
    var body: some View {
        VStack(spacing: 8) {
            ProgressView()
            Text("Loading graph…")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
    }
}

/// 400-pt canvas with pinch-zoom (0.3×–4×) and pan, like Android's
/// detectTransformGestures.
struct PrdDagCanvasView: View {
    let layout: PrdDagLayoutResult

    @State private var scale: Double = 1.0
    @GestureState private var pinch: Double = 1.0
    @State private var offset: CGSize = .zero
    @GestureState private var drag: CGSize = .zero

    private var effectiveScale: Double {
        let s: Double = scale * pinch
        return min(4.0, max(0.3, s))
    }

    var body: some View {
        canvas
            .scaleEffect(CGFloat(effectiveScale))
            .offset(x: offset.width + drag.width, y: offset.height + drag.height)
            .frame(maxWidth: .infinity)
            .frame(height: 400)
            .clipped()
            .contentShape(Rectangle())
            .gesture(panGesture.simultaneously(with: zoomGesture))
            .accessibilityLabel(Text("Graph"))
    }

    private var canvas: some View {
        Canvas { ctx, size in
            PrdDagDrawing.draw(layout: layout, ctx: ctx, width: Double(size.width))
        }
    }

    private var panGesture: some Gesture {
        DragGesture(minimumDistance: 8)
            .updating($drag) { value, state, _ in state = value.translation }
            .onEnded { value in
                offset = CGSize(
                    width: offset.width + value.translation.width,
                    height: offset.height + value.translation.height
                )
            }
    }

    private var zoomGesture: some Gesture {
        MagnificationGesture()
            .updating($pinch) { value, state, _ in state = Double(value) }
            .onEnded { value in
                let next: Double = scale * Double(value)
                scale = min(4.0, max(0.3, next))
            }
    }
}

/// Stateless drawing helpers — kept out of the view body for the type-checker.
enum PrdDagDrawing {
    static let radius: Double = 28.0 // PrdDagLayout.NODE_RADIUS

    /// Android PrdDagCanvas nodeColor().
    static func color(_ status: String) -> Color {
        switch status {
        case "running", "in_progress": return Color(hex: 0x3B82F6)
        case "complete", "completed", "done": return Color(hex: 0x10B981)
        case "failed": return Color(hex: 0xEF4444)
        case "blocked": return Color(hex: 0xF59E0B)
        case "cancelled", "canceled": return Color(hex: 0x94A3B8)
        case "needs_review", "revisions_asked": return Color(hex: 0x8B5CF6)
        default: return Color(hex: 0x64748B)
        }
    }

    static func draw(layout: PrdDagLayoutResult, ctx: GraphicsContext, width: Double) {
        let cx: Double = width / 2.0
        for edge in layout.edges {
            drawEdge(edge, cx: cx, ctx: ctx)
        }
        for node in layout.nodes {
            drawNode(node, cx: cx, ctx: ctx)
        }
    }

    private static func drawEdge(_ e: PrdDagEdge, cx: Double, ctx: GraphicsContext) {
        let x1: Double = cx + e.fromX
        let y1: Double = e.fromY
        let x2: Double = cx + e.toX
        let y2: Double = e.toY
        let angle: Double = atan2(y2 - y1, x2 - x1)
        let start = CGPoint(x: x1 + radius * cos(angle), y: y1 + radius * sin(angle))
        let endX: Double = x2 - radius * cos(angle)
        let endY: Double = y2 - radius * sin(angle)
        let end = CGPoint(x: endX, y: endY)
        let a1: Double = angle - 0.4
        let a2: Double = angle + 0.4
        var path = Path()
        path.move(to: start)
        path.addLine(to: end)
        path.move(to: end)
        path.addLine(to: CGPoint(x: endX - 10.0 * cos(a1), y: endY - 10.0 * sin(a1)))
        path.move(to: end)
        path.addLine(to: CGPoint(x: endX - 10.0 * cos(a2), y: endY - 10.0 * sin(a2)))
        ctx.stroke(path, with: .color(DatawatchColors.onSurfaceMuted.opacity(0.6)), lineWidth: 2)
    }

    private static func circle(cx: Double, cy: Double, r: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2.0, height: r * 2.0))
    }

    private static func drawNode(_ n: PrdDagNode, cx: Double, ctx: GraphicsContext) {
        let x: Double = cx + n.x
        let y: Double = n.y
        let tint: Color = color(n.status)
        ctx.fill(circle(cx: x, cy: y, r: radius + 4.0), with: .color(tint.opacity(0.15)))
        ctx.fill(circle(cx: x, cy: y, r: radius), with: .color(tint.opacity(0.85)))
        ctx.stroke(circle(cx: x, cy: y, r: radius), with: .color(tint), lineWidth: 2)
        let glyph: Text = Text(verbatim: n.kindGlyph)
            .font(.system(.footnote).bold())
            .foregroundColor(.white)
        ctx.draw(glyph, at: CGPoint(x: x, y: y))
        let label: Text = Text(verbatim: n.label)
            .font(.system(.caption2))
            .foregroundColor(DatawatchColors.onSurface)
        ctx.draw(label, at: CGPoint(x: x, y: y + radius + 2.0), anchor: .top)
    }
}
