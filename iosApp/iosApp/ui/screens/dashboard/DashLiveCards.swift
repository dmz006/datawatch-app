import SwiftUI
import DatawatchShared

// Animated cards. PWA `_dashLoop` cadences, reproduced with TimelineView:
//   • Multi-EKG — every animation frame (60 fps sweep)
//   • Network (constellation) — physics at 60 Hz, redraw ~10 fps
//   • Sessions sparklines — ~5 fps
// Reduce Motion pauses the timelines (static frame, redrawn on data changes)
// and settles the constellation physics in one go.

private func dashNow(_ date: Date) -> Double { date.timeIntervalSince1970 * 1000.0 }

// ── Network (orbital / constellation) ─────────────────────────────────────────

struct DashNetworkCard: View {
    @ObservedObject var vm: DashboardViewModel
    let revision: Int
    let onTarget: (DashTarget) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            if geo.size.width < 400 {
                // Issue #98 narrow fallback: compact text list.
                DashCompactSessionList(rows: vm.engine.compactRows(nowMs: dashNow(Date())), onTarget: onTarget)
            } else {
                TimelineView(.animation(minimumInterval: 0.1, paused: reduceMotion)) { ctx in
                    DashConstellationFrame(
                        sim: vm.constellation,
                        nowMs: dashNow(ctx.date),
                        size: geo.size,
                        reduceMotion: reduceMotion,
                        revision: revision
                    )
                }
                .contentShape(Rectangle())
                .onTapGesture(coordinateSpace: .local) { point in
                    let id = vm.constellation.hit(x: Double(point.x), y: Double(point.y))
                    guard !id.isEmpty else { return }
                    onTarget(vm.constellation.isPrd(id: id) ? .prd(id) : .session(id))
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text("Session network graph"))
            }
        }
    }
}

private struct DashCompactSessionList: View {
    let rows: [IosDashCompactRow]
    let onTarget: (DashTarget) -> Void

    var body: some View {
        if rows.isEmpty {
            Text("No active sessions")
                .font(.footnote)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(rows, id: \.sessionId) { row in
                        DashCompactRowView(row: row, onTarget: onTarget)
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }
}

private struct DashCompactRowView: View {
    let row: IosDashCompactRow
    let onTarget: (DashTarget) -> Void

    var body: some View {
        let color = dashTone(row.tone)
        HStack(spacing: 6) {
            Text("• \(row.name)")
                .font(.caption)
                .foregroundStyle(color)
                .lineLimit(1)
            Spacer(minLength: 4)
            Image(systemName: row.symbol)
                .font(.caption2.weight(.bold))
                .foregroundStyle(color)
            Text(row.age)
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .frame(minWidth: 24, alignment: .trailing)
        }
        .padding(.horizontal, 8)
        .frame(minHeight: 20)
        .contentShape(Rectangle())
        .onTapGesture { onTarget(.session(row.sessionId)) }
        .contextMenu {
            Button { onTarget(.session(row.sessionId)) } label: { Label("Open session", systemImage: "terminal") }
            if row.active {
                Button { onTarget(.expand(row.sessionId)) } label: {
                    Label("Open in Dashboard", systemImage: "rectangle.split.3x1")
                }
            }
        }
    }
}

/// One rendered constellation frame. Advancing the shared simulation here is
/// what makes the TimelineView cadence drive the physics.
private struct DashConstellationFrame: View {
    let sim: IosDashConstellation
    let nowMs: Double
    let size: CGSize
    let reduceMotion: Bool
    let revision: Int

    var body: some View {
        let nodes: [IosDashNode] = stepAndSnapshot()
        let edges: [IosDashEdge] = sim.edgeLines()
        let phase: Double = reduceMotion ? 0 : nowMs
        Canvas { gc, canvasSize in
            if nodes.isEmpty {
                drawCentered(gc, size: canvasSize, text: L("No active sessions"))
                return
            }
            for e in edges { drawEdge(gc, e) }
            for n in nodes { drawNode(gc, n, phase: phase) }
            drawHint(gc, size: canvasSize)
        }
    }

    private func stepAndSnapshot() -> [IosDashNode] {
        let w = Double(size.width)
        let h = Double(size.height)
        if reduceMotion {
            sim.settle(width: w, height: h)
        } else {
            sim.advance(nowMs: nowMs, width: w, height: h)
        }
        return sim.snapshot()
    }

    private func drawCentered(_ gc: GraphicsContext, size: CGSize, text: String) {
        let t = gc.resolve(Text(text).font(.footnote).foregroundColor(DatawatchColors.onSurfaceMuted))
        gc.draw(t, at: CGPoint(x: size.width / 2, y: size.height / 2), anchor: .center)
    }

    private func drawHint(_ gc: GraphicsContext, size: CGSize) {
        let t = gc.resolve(Text("Tap a node to inspect").font(.system(size: 10)).foregroundColor(DatawatchColors.onSurfaceMuted.opacity(0.5)))
        gc.draw(t, at: CGPoint(x: 8, y: size.height - 6), anchor: .bottomLeading)
    }

    private func drawEdge(_ gc: GraphicsContext, _ e: IosDashEdge) {
        var p = Path()
        p.move(to: CGPoint(x: e.x1, y: e.y1))
        p.addLine(to: CGPoint(x: e.x2, y: e.y2))
        gc.stroke(p, with: .color(DatawatchColors.border), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
    }

    private func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    private func drawNode(_ gc: GraphicsContext, _ n: IosDashNode, phase: Double) {
        let color = dashTone(n.tone)
        let r: Double = n.radius
        if n.active {
            let pulseR: Double = r + 6 + sin(phase / 500.0) * 4
            let alpha: Double = 0.25 + sin(phase / 400.0) * 0.15
            gc.stroke(circle(n.x, n.y, pulseR), with: .color(color.opacity(alpha)), lineWidth: 2)
        }
        if n.ring == "alive" {
            gc.stroke(circle(n.x, n.y, r + 4), with: .color(color.opacity(0.4)), lineWidth: 1)
        } else if n.ring == "stale" {
            gc.stroke(circle(n.x, n.y, r + 4), with: .color(DatawatchColors.warning.opacity(0.4)), lineWidth: 1)
        }
        let fillOpacity: Double = n.dim ? 0.4 : 0.9
        gc.fill(circle(n.x, n.y, r), with: .color(color.opacity(fillOpacity)))
        gc.stroke(circle(n.x, n.y, r), with: .color(Color.white.opacity(0.15)), lineWidth: 1.5)
        drawGlyph(gc, n)
        if n.threats > 0 { drawThreats(gc, n) }
        drawLabels(gc, n)
    }

    private func drawGlyph(_ gc: GraphicsContext, _ n: IosDashNode) {
        let size: CGFloat = n.prd ? 11 : 9
        var img = gc.resolve(Image(systemName: n.symbol).renderingMode(.template))
        img.shading = .color(.white)
        var g = gc
        g.opacity = 0.85
        let rect = CGRect(x: n.x - Double(size) / 2, y: n.y - Double(size) / 2, width: Double(size), height: Double(size))
        g.draw(img, in: rect)
    }

    private func drawThreats(_ gc: GraphicsContext, _ n: IosDashNode) {
        let cx: Double = n.x + n.radius - 4
        let cy: Double = n.y - n.radius + 4
        gc.fill(circle(cx, cy, 6), with: .color(DatawatchColors.error.opacity(0.95)))
        let t = gc.resolve(Text("\(Int(n.threats))").font(.system(size: 8, weight: .bold)).foregroundColor(.white))
        gc.draw(t, at: CGPoint(x: cx, y: cy), anchor: .center)
    }

    private func drawLabels(_ gc: GraphicsContext, _ n: IosDashNode) {
        let label = gc.resolve(Text(n.label).font(.system(size: 10)).foregroundColor(DatawatchColors.onSurface.opacity(0.75)))
        gc.draw(label, at: CGPoint(x: n.x, y: n.y + n.radius + 10), anchor: .center)
        let state = gc.resolve(Text(n.state).font(.system(size: 9)).foregroundColor(DatawatchColors.onSurfaceMuted.opacity(0.55)))
        gc.draw(state, at: CGPoint(x: n.x, y: n.y + n.radius + 21), anchor: .center)
    }
}

// ── Multi-EKG (+ burn-rate panel) ─────────────────────────────────────────────

struct DashEkgCard: View {
    let engine: IosDashEngine
    let revision: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            // Issue #99 — narrow: hide the trace, burn-rate panel only.
            if geo.size.width < 280 {
                DashBurnRatePanel(stats: engine.statBar(), narrow: true)
            } else {
                HStack(spacing: 0) {
                    TimelineView(.animation(minimumInterval: nil, paused: reduceMotion)) { ctx in
                        DashEkgCanvas(ekg: engine.ekg(nowMs: dashNow(ctx.date), windowSec: DashEkgCanvas.windowSec))
                    }
                    DashBurnRatePanel(stats: engine.statBar(), narrow: false)
                        .frame(width: 72)
                        .overlay(alignment: .leading) {
                            Rectangle().fill(DatawatchColors.border).frame(width: 1)
                        }
                }
            }
        }
        .accessibilityElement(children: .combine)
    }
}

private struct DashBurnRatePanel: View {
    let stats: IosDashStatBar
    let narrow: Bool

    var body: some View {
        VStack(alignment: narrow ? .center : .trailing, spacing: 2) {
            if stats.costUsd > 0 {
                Text(String(format: "$%.2f", stats.costUsd))
                    .font(.caption2.monospaced().weight(.bold))
                    .foregroundStyle(DatawatchColors.success)
                Text("today")
                    .font(.system(size: 8).monospaced())
                    .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.6))
            }
            Text("\(Int(stats.running)) running")
                .font(.caption2.monospaced())
                .foregroundStyle(DatawatchColors.primary)
            if stats.automata > 0 {
                Text("\(Int(stats.automata)) automata")
                    .font(.system(size: 8).monospaced())
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .padding(.horizontal, 6)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: narrow ? .center : .trailing)
    }
}

/// PWA `_drawMultiEKG`: one channel per active session (max 6), hook-event
/// blips sweeping right→left over a 60 s window with exponential decay.
private struct DashEkgCanvas: View {
    static let windowSec: Double = 60
    static let labelWidth: Double = 72
    let ekg: IosDashEkg

    var body: some View {
        Canvas { gc, size in
            let w = Double(size.width)
            let h = Double(size.height)
            gc.fill(Path(CGRect(origin: .zero, size: size)), with: .color(DatawatchColors.background))
            let channels = ekg.channels
            if channels.isEmpty {
                drawTrace(gc, pulses: ekg.idlePulses, color: dashTone(ekg.idleTone), mid: h / 2, amp: h * 0.38, x0: 0, width: w)
            } else {
                let chH: Double = h / Double(channels.count)
                for (i, ch) in channels.enumerated() {
                    drawChannel(gc, ch, index: i, count: channels.count, chH: chH, width: w)
                }
                drawFade(gc, height: h)
            }
            drawNowEdge(gc, width: w, height: h)
        }
    }

    private func drawChannel(_ gc: GraphicsContext, _ ch: IosDashEkgChannel, index: Int, count: Int, chH: Double, width: Double) {
        let top: Double = Double(index) * chH
        let color = dashTone(ch.tone)
        if index % 2 == 1 {
            gc.fill(Path(CGRect(x: 0, y: top, width: width, height: chH)), with: .color(DatawatchColors.onSurface.opacity(0.03)))
        }
        let name = gc.resolve(Text(ch.name).font(.system(size: 9, weight: .bold, design: .monospaced)).foregroundColor(color))
        gc.draw(name, at: CGPoint(x: 3, y: top + chH * 0.5), anchor: .leading)
        drawHealth(gc, health: ch.health, at: CGPoint(x: 6, y: top + chH * 0.82))
        let mid: Double = top + chH / 2
        let x0: Double = Self.labelWidth
        if ch.pulses.isEmpty {
            var base = Path()
            base.move(to: CGPoint(x: x0, y: mid))
            base.addLine(to: CGPoint(x: width, y: mid))
            gc.stroke(base, with: .color(color.opacity(0.25)), lineWidth: 0.5)
        } else {
            drawTrace(gc, pulses: ch.pulses, color: color, mid: mid, amp: chH * 0.38, x0: x0, width: width - x0)
        }
        if index < count - 1 {
            var sep = Path()
            sep.move(to: CGPoint(x: 0, y: top + chH))
            sep.addLine(to: CGPoint(x: width, y: top + chH))
            gc.stroke(sep, with: .color(DatawatchColors.border), style: StrokeStyle(lineWidth: 0.5, dash: [2, 4]))
        }
    }

    private func drawHealth(_ gc: GraphicsContext, health: String, at point: CGPoint) {
        let r: Double = 3
        let rect = CGRect(x: Double(point.x) - r, y: Double(point.y) - r, width: r * 2, height: r * 2)
        let ring = Path(ellipseIn: rect)
        switch health {
        case "alive":
            gc.fill(ring, with: .color(DatawatchColors.success))
        case "stale":
            gc.stroke(ring, with: .color(DatawatchColors.warning), lineWidth: 1)
            let half = Path(CGRect(x: Double(point.x) - r, y: Double(point.y) - r, width: r, height: r * 2))
            var clipped = gc
            clipped.clip(to: ring)
            clipped.fill(half, with: .color(DatawatchColors.warning))
        default:
            gc.stroke(ring, with: .color(DatawatchColors.onSurfaceMuted), lineWidth: 1)
        }
    }

    /// Baseline with one heartbeat blip per hook event; x = age in the window.
    private func drawTrace(_ gc: GraphicsContext, pulses: [IosDashPulse], color: Color, mid: Double, amp: Double, x0: Double, width: Double) {
        let sorted = pulses.sorted { $0.ageSec > $1.ageSec }
        var path = Path()
        path.move(to: CGPoint(x: x0, y: mid))
        for p in sorted {
            let x: Double = x0 + width * (1.0 - p.ageSec / Self.windowSec)
            let dec: Double = exp(-p.ageSec / 20.0)
            let peak: Double = p.dy * dec * amp
            path.addLine(to: CGPoint(x: x - 3, y: mid))
            path.addLine(to: CGPoint(x: x, y: mid - peak))
            path.addLine(to: CGPoint(x: x + 2, y: mid + peak * 0.4))
            path.addLine(to: CGPoint(x: x + 4, y: mid))
        }
        path.addLine(to: CGPoint(x: x0 + width, y: mid))
        gc.stroke(path, with: .color(color), lineWidth: 1.2)
    }

    private func drawFade(_ gc: GraphicsContext, height: Double) {
        let x0: Double = Self.labelWidth
        let rect = CGRect(x: x0, y: 0, width: 16, height: height)
        let gradient = Gradient(colors: [DatawatchColors.background, DatawatchColors.background.opacity(0)])
        gc.fill(Path(rect), with: .linearGradient(gradient, startPoint: CGPoint(x: x0, y: 0), endPoint: CGPoint(x: x0 + 16, y: 0)))
    }

    private func drawNowEdge(_ gc: GraphicsContext, width: Double, height: Double) {
        var p = Path()
        p.move(to: CGPoint(x: width - 1, y: 0))
        p.addLine(to: CGPoint(x: width - 1, y: height))
        gc.stroke(p, with: .color(DatawatchColors.primary), style: StrokeStyle(lineWidth: 0.5, dash: [2, 3]))
    }
}

// ── Sessions sparklines (~5 fps) ──────────────────────────────────────────────

struct DashSparklinesCard: View {
    let engine: IosDashEngine
    let revision: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        TimelineView(.animation(minimumInterval: 0.2, paused: reduceMotion)) { ctx in
            DashSparkCanvas(rows: engine.sparkRows(nowMs: dashNow(ctx.date)))
        }
    }
}

/// PWA `_drawSparklines`: per active session (max 5) 60 × 2 s hook-event buckets.
private struct DashSparkCanvas: View {
    let rows: [IosDashSparkRow]
    static let labelWidth: Double = 76

    var body: some View {
        Canvas { gc, size in
            let w = Double(size.width)
            let h = Double(size.height)
            if rows.isEmpty {
                let t = gc.resolve(Text("no sessions").font(.system(size: 9, design: .monospaced)).foregroundColor(DatawatchColors.onSurfaceMuted))
                gc.draw(t, at: CGPoint(x: 8, y: 14), anchor: .leading)
                return
            }
            let rowH: Double = floor(h / Double(rows.count))
            for (i, row) in rows.enumerated() {
                drawRow(gc, row, top: Double(i) * rowH, rowH: rowH, width: w, first: i == 0)
            }
        }
    }

    private func drawRow(_ gc: GraphicsContext, _ row: IosDashSparkRow, top: Double, rowH: Double, width: Double, first: Bool) {
        let color = dashTone(row.tone)
        if !first {
            var sep = Path()
            sep.move(to: CGPoint(x: 0, y: top))
            sep.addLine(to: CGPoint(x: width, y: top))
            gc.stroke(sep, with: .color(DatawatchColors.border), lineWidth: 0.5)
        }
        let name = gc.resolve(Text(row.name).font(.system(size: 9, weight: .bold, design: .monospaced)).foregroundColor(color))
        gc.draw(name, at: CGPoint(x: 4, y: top + rowH * 0.38), anchor: .leading)
        let state = gc.resolve(Text(row.state).font(.system(size: 8, design: .monospaced)).foregroundColor(DatawatchColors.onSurfaceMuted))
        gc.draw(state, at: CGPoint(x: 4, y: top + rowH * 0.72), anchor: .leading)
        let buckets = row.buckets
        guard !buckets.isEmpty else { return }
        let sparkW: Double = width - Self.labelWidth - 4
        let barW: Double = sparkW / Double(buckets.count)
        for (bi, value) in buckets.enumerated() {
            let v: Double = value.doubleValue
            let bh: Double = max(0, v * (rowH - 5))
            if bh < 0.5 { continue }
            let rect = CGRect(x: Self.labelWidth + Double(bi) * barW, y: top + rowH - 3 - bh, width: max(1, barW - 0.5), height: bh)
            gc.fill(Path(rect), with: .color(color.opacity(0.65)))
        }
    }
}
