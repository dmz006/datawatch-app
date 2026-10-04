import SwiftUI
import DatawatchShared

// ── Automata tree (PWA _dashRenderTree) ───────────────────────────────────────

struct DashTreeCard: View {
    let tree: IosDashTree
    let onTarget: (DashTarget) -> Void
    let onToggle: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 1) {
                DashSectionCaption(text: "Automata")
                if tree.prds.isEmpty {
                    DashMutedLine(text: "no active automata")
                } else {
                    ForEach(tree.prds, id: \.id) { prd in
                        DashTreePrdRow(prd: prd, onTarget: onTarget, onToggle: onToggle)
                    }
                }
                if !tree.sessions.isEmpty {
                    DashSectionCaption(text: "Active Sessions")
                        .padding(.top, 6)
                    ForEach(tree.sessions, id: \.id) { s in
                        DashTreeSessionRow(session: s, onTarget: onTarget)
                    }
                }
            }
            .padding(.bottom, 6)
        }
    }
}

private struct DashTreePrdRow: View {
    let prd: IosDashTreePrd
    let onTarget: (DashTarget) -> Void
    let onToggle: (String) -> Void

    var body: some View {
        let color = dashTone(prd.tone)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 4) {
                Button { onToggle(prd.id) } label: {
                    Image(systemName: prd.expanded ? "chevron.down" : "chevron.right")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(color)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel(Text(L(prd.expanded ? "Collapse" : "Expand")))
                Button { onTarget(.prd(prd.id)) } label: {
                    Text(prd.title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(DatawatchColors.onSurface)
                        .lineLimit(1)
                }
                .buttonStyle(.plain)
                Spacer(minLength: 2)
                Text("\(Int(prd.pct))%")
                    .font(.caption2)
                    .foregroundStyle(color)
            }
            .padding(.horizontal, 7)
            .padding(.vertical, 5)
            DashBar(fraction: Double(prd.pct) / 100.0, color: color, height: 2)
                .padding(.horizontal, 8)
                .padding(.bottom, 4)
            if prd.expanded {
                ForEach(Array(prd.stories.enumerated()), id: \.offset) { _, st in
                    DashTreeStoryRow(story: st)
                        .contentShape(Rectangle())
                        .onTapGesture { onTarget(.prd(prd.id)) }
                }
            }
        }
        .overlay(alignment: .leading) { Rectangle().fill(color).frame(width: 2) }
        .padding(.horizontal, 6)
    }
}

private struct DashTreeStoryRow: View {
    let story: IosDashTreeStory

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: story.symbol)
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(dashTone(story.tone))
            Text(story.title)
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(1)
            Spacer(minLength: 2)
            if story.tasksTotal > 0 {
                Text("\(Int(story.tasksDone))/\(Int(story.tasksTotal))")
                    .font(.system(size: 9))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .padding(.leading, 14)
        .padding(.trailing, 7)
        .padding(.vertical, 2)
    }
}

private struct DashTreeSessionRow: View {
    let session: IosDashTreeSession
    let onTarget: (DashTarget) -> Void

    var body: some View {
        let color = dashTone(session.tone)
        HStack(spacing: 4) {
            DashHealthDot(health: session.health)
            VStack(alignment: .leading, spacing: 1) {
                Text(session.name)
                    .font(.caption)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(1)
                if !session.focus.isEmpty {
                    Text("▸ \(session.focus)")
                        .font(.system(size: 9))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 2)
            if !session.runtimeSymbol.isEmpty {
                Image(systemName: session.runtimeSymbol)
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            Button { onTarget(.expand(session.id)) } label: {
                Image(systemName: "rectangle.split.3x1")
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Open in Dashboard")
        }
        .padding(.horizontal, 7)
        .padding(.vertical, 4)
        .contentShape(Rectangle())
        .onTapGesture { onTarget(.session(session.id)) }
        .overlay(alignment: .leading) { Rectangle().fill(color).frame(width: 2) }
        .padding(.horizontal, 6)
    }
}

/// PWA hook-health glyph ● alive · ◐ stale · ○ none.
struct DashHealthDot: View {
    let health: String

    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 8))
            .foregroundStyle(color)
            .accessibilityLabel(Text(label))
    }

    private var symbol: String {
        switch health {
        case "alive": return "circle.fill"
        case "stale": return "circle.lefthalf.filled"
        default: return "circle"
        }
    }

    private var color: Color {
        switch health {
        case "alive": return DatawatchColors.success
        case "stale": return DatawatchColors.warning
        default: return DatawatchColors.onSurfaceMuted
        }
    }

    private var label: String {
        switch health {
        case "alive": return L("Hooks alive")
        case "stale": return L("Hooks stale")
        default: return L("No hook data")
        }
    }
}

// ── Live events ticker (PWA _dashRenderEventFeed) ─────────────────────────────

struct DashEventsCard: View {
    let events: [IosDashEvent]

    var body: some View {
        if events.isEmpty {
            DashMutedLine(text: "Waiting for hook events…")
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(events.enumerated()), id: \.offset) { _, e in
                        DashEventRow(event: e)
                    }
                }
            }
        }
    }
}

private struct DashEventRow: View {
    let event: IosDashEvent

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            HStack(spacing: 3) {
                Text(event.timeLabel)
                    .font(.system(size: 8, design: .monospaced))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                Image(systemName: event.symbol)
                    .font(.system(size: 8))
                    .foregroundStyle(dashTone(event.tone))
                Text(event.name)
                    .font(.system(size: 9, weight: .semibold, design: .monospaced))
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(1)
                    .frame(maxWidth: 96, alignment: .leading)
                if !event.tool.isEmpty {
                    Text(event.tool)
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundStyle(DatawatchColors.waiting)
                        .lineLimit(1)
                }
            }
            if !event.task.isEmpty {
                Text("▸ \(event.task)")
                    .font(.system(size: 9, design: .monospaced))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(1)
                    .padding(.leading, 10)
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Color.white.opacity(0.03)).frame(height: 1)
        }
        .accessibilityElement(children: .combine)
    }
}

// ── Timeline · 6h (PWA _drawGantt) ────────────────────────────────────────────

struct DashGanttCard: View {
    let engine: IosDashEngine
    let revision: Int
    let onTarget: (DashTarget) -> Void
    let onToggle: (String) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        // PWA redraws at ~10 fps; only the NOW edge moves, so 1 s is visually identical.
        TimelineView(.animation(minimumInterval: 1, paused: reduceMotion)) { ctx in
            DashGanttContent(
                gantt: engine.gantt(nowMs: ctx.date.timeIntervalSince1970 * 1000.0),
                onTarget: onTarget,
                onToggle: onToggle
            )
        }
    }
}

private struct DashGanttContent: View {
    let gantt: IosDashGantt
    let onTarget: (DashTarget) -> Void
    let onToggle: (String) -> Void

    var body: some View {
        GeometryReader { geo in
            let labelW: CGFloat = min(164, geo.size.width * 0.38)
            let barW: CGFloat = max(10, geo.size.width - labelW - 24)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    DashGanttHeader(ticks: gantt.ticks, labelW: labelW, barW: barW)
                    if gantt.prds.isEmpty {
                        VStack(spacing: 4) {
                            Text("No active automata — start an Automaton to see its timeline")
                                .font(.footnote)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.6))
                            Text("Live story bars appear here as hooks fire")
                                .font(.caption2)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.4))
                        }
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 16)
                    }
                    ForEach(gantt.prds, id: \.id) { prd in
                        DashGanttPrdBlock(prd: prd, labelW: labelW, barW: barW, onTarget: onTarget, onToggle: onToggle)
                            .padding(.top, 8)
                    }
                }
                .background(alignment: .topLeading) {
                    DashGanttGridLines(ticks: gantt.ticks, labelW: labelW, barW: barW)
                }
            }
        }
    }
}

private struct DashGanttGridLines: View {
    let ticks: [IosDashTick]
    let labelW: CGFloat
    let barW: CGFloat

    var body: some View {
        Canvas { gc, size in
            for t in ticks {
                let x: CGFloat = labelW + barW * CGFloat(t.fraction)
                var p = Path()
                p.move(to: CGPoint(x: x, y: 0))
                p.addLine(to: CGPoint(x: x, y: size.height))
                gc.stroke(p, with: .color(DatawatchColors.border), style: StrokeStyle(lineWidth: 0.5, dash: [2, 6]))
            }
            let nowX: CGFloat = labelW + barW
            var now = Path()
            now.move(to: CGPoint(x: nowX, y: 0))
            now.addLine(to: CGPoint(x: nowX, y: size.height))
            gc.stroke(now, with: .color(DatawatchColors.primary.opacity(0.6)), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
        }
        .allowsHitTesting(false)
    }
}

private struct DashGanttHeader: View {
    let ticks: [IosDashTick]
    let labelW: CGFloat
    let barW: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            Rectangle().fill(DatawatchColors.surface2)
            ForEach(Array(ticks.enumerated()), id: \.offset) { _, t in
                Text(t.label)
                    .font(.system(size: 9))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .fixedSize()
                    .position(x: labelW + barW * CGFloat(t.fraction), y: 14)
            }
            Text("NOW")
                .font(.system(size: 8, weight: .bold))
                .foregroundStyle(DatawatchColors.primary)
                .fixedSize()
                .position(x: labelW + barW, y: 6)
        }
        .frame(height: 24)
    }
}

private struct DashGanttPrdBlock: View {
    let prd: IosDashGanttPrd
    let labelW: CGFloat
    let barW: CGFloat
    let onTarget: (DashTarget) -> Void
    let onToggle: (String) -> Void

    var body: some View {
        let color = dashTone(prd.tone)
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Button { onToggle(prd.id) } label: {
                    Image(systemName: prd.expanded ? "chevron.down" : "chevron.right")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(color)
                }
                .buttonStyle(.borderless)
                Button { onTarget(.prd(prd.id)) } label: {
                    Text(prd.title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(DatawatchColors.onSurface)
                        .lineLimit(1)
                }
                .buttonStyle(.plain)
                Spacer(minLength: 4)
                DashBar(fraction: Double(prd.pct) / 100.0, color: color.opacity(0.8), height: 7)
                    .frame(width: 72)
                Text("\(Int(prd.pct))%")
                    .font(.system(size: 9))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .frame(width: 30, alignment: .leading)
            }
            .padding(.horizontal, 8)
            .frame(height: 28)
            .background(DatawatchColors.surface2)
            if prd.expanded {
                ForEach(Array(prd.stories.enumerated()), id: \.offset) { _, st in
                    DashGanttStoryRow(story: st, labelW: labelW, barW: barW)
                        .contentShape(Rectangle())
                        .onTapGesture { onTarget(.prd(prd.id)) }
                }
            }
        }
    }
}

private struct DashGanttStoryRow: View {
    let story: IosDashGanttStory
    let labelW: CGFloat
    let barW: CGFloat

    var body: some View {
        let color = dashTone(story.tone)
        ZStack(alignment: .topLeading) {
            labelColumn(color)
            bar(color)
        }
        .frame(maxWidth: .infinity, minHeight: 22, maxHeight: 22, alignment: .topLeading)
        .accessibilityElement(children: .combine)
    }

    private func labelColumn(_ color: Color) -> some View {
        HStack(spacing: 4) {
            VStack(alignment: .leading, spacing: 0) {
                Text(story.title)
                    .font(.system(size: 9))
                    .foregroundStyle(DatawatchColors.onSurface.opacity(0.8))
                    .lineLimit(1)
                if !story.tasks.isEmpty {
                    Text(story.tasks)
                        .font(.system(size: 8))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            Spacer(minLength: 2)
            Image(systemName: story.symbol)
                .font(.system(size: 9, weight: .bold))
                .foregroundStyle(color)
        }
        .padding(.leading, 8)
        .padding(.trailing, 8)
        .frame(width: labelW, height: 22)
    }

    @ViewBuilder
    private func bar(_ color: Color) -> some View {
        let x0: CGFloat = labelW + barW * CGFloat(story.startFraction)
        let x1: CGFloat = labelW + barW * CGFloat(story.endFraction)
        let width: CGFloat = max(3, x1 - x0)
        if story.pending {
            RoundedRectangle(cornerRadius: 2)
                .stroke(color.opacity(0.35), style: StrokeStyle(lineWidth: 0.8, dash: [3, 3]))
                .frame(width: width, height: 18)
                .offset(x: x0, y: 2)
        } else {
            RoundedRectangle(cornerRadius: 2)
                .fill(color.opacity(story.completed ? 0.55 : 0.82))
                .frame(width: width, height: 18)
                .overlay {
                    if width > 44 {
                        Text(story.running ? "▶ \(story.title)" : story.title)
                            .font(.system(size: 9))
                            .foregroundStyle(Color.white.opacity(0.92))
                            .lineLimit(1)
                            .padding(.horizontal, 3)
                    }
                }
                .offset(x: x0, y: 2)
        }
    }
}

// ── 30-Day Activity heatmap (PWA _drawHeatmap) ────────────────────────────────

struct DashHeatmapCard: View {
    let engine: IosDashEngine
    let revision: Int
    let effectiveSpan: Int

    var body: some View {
        GeometryReader { geo in
            let narrow: Bool = geo.size.width < 300 || effectiveSpan <= 3
            let model = engine.heatmap(narrow: narrow)
            if !model.loaded {
                DashMutedLine(text: "Loading…")
            } else if model.bars {
                DashHeatBars(model: model)
            } else {
                DashHeatGrid(model: model)
            }
        }
    }
}

private struct DashHeatBars: View {
    let model: IosDashHeatmap

    var body: some View {
        Canvas { gc, size in
            let pad: Double = 6
            let gap: Double = 2
            let count = model.cells.count
            guard count > 0 else { return }
            let w = Double(size.width)
            let h = Double(size.height)
            let barW: Double = max(4, floor((w - pad * 2 - gap * Double(count - 1)) / Double(count)))
            for (i, c) in model.cells.enumerated() {
                let bh: Double = max(2, (c.heightFraction * (h - pad * 2)).rounded())
                let x: Double = pad + Double(i) * (barW + gap)
                let rect = CGRect(x: x, y: h - pad - bh, width: barW, height: bh)
                gc.fill(Path(roundedRect: rect, cornerRadius: 2), with: .color(dashTone(c.tone).opacity(c.alpha)))
            }
            let label = gc.resolve(Text("7d").font(.system(size: 8, design: .monospaced)).foregroundColor(DatawatchColors.onSurfaceMuted))
            gc.draw(label, at: CGPoint(x: w - 3, y: h - 2), anchor: .bottomTrailing)
        }
        .accessibilityLabel(Text("Last 7 days of session activity"))
    }
}

private struct DashHeatGrid: View {
    let model: IosDashHeatmap

    var body: some View {
        Canvas { gc, size in
            let pad: Double = 10
            let labelH: Double = 14
            let cellPad: Double = 2
            let cols = model.cells.count
            guard cols > 0 else { return }
            let w = Double(size.width)
            let h = Double(size.height)
            let cellW: Double = max(4, floor((w - pad * 2) / Double(cols)) - cellPad)
            let cellH: Double = max(4, h - pad * 2 - labelH)
            for (i, c) in model.cells.enumerated() {
                let rect = CGRect(x: pad + Double(i) * (cellW + cellPad), y: pad, width: cellW, height: cellH)
                gc.fill(Path(roundedRect: rect, cornerRadius: 2), with: .color(dashTone(c.tone).opacity(c.alpha)))
            }
            let labelY: Double = pad + cellH + 10
            for l in model.labels {
                let t = gc.resolve(Text(l.label).font(.system(size: 8, design: .monospaced)).foregroundColor(DatawatchColors.onSurfaceMuted))
                gc.draw(t, at: CGPoint(x: pad + Double(l.index) * (cellW + cellPad), y: labelY), anchor: .leading)
            }
            let total = gc.resolve(Text("\(Int(model.total)) / 30d").font(.system(size: 8, design: .monospaced)).foregroundColor(DatawatchColors.onSurfaceMuted))
            gc.draw(total, at: CGPoint(x: w - pad, y: labelY), anchor: .trailing)
        }
        .accessibilityLabel(Text("\(Int(model.total)) sessions in the last 30 days"))
    }
}

// ── Guardrails (PWA _dashRenderGuardrails) ────────────────────────────────────

struct DashGuardrailsCard: View {
    let model: IosDashGuardrails

    var body: some View {
        if model.block + model.warn + model.pass == 0 {
            DashMutedLine(text: "No verdicts yet")
        } else {
            ScrollView {
                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 10) {
                        Label("\(Int(model.block))", systemImage: "nosign").foregroundStyle(DatawatchColors.error)
                        Label("\(Int(model.warn))", systemImage: "exclamationmark.triangle").foregroundStyle(DatawatchColors.warning)
                        Label("\(Int(model.pass))", systemImage: "checkmark").foregroundStyle(DatawatchColors.success)
                    }
                    .font(.caption.weight(.bold))
                    .padding(.bottom, 1)
                    ForEach(Array(model.rules.enumerated()), id: \.offset) { _, r in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(r.rule)
                                .font(.system(size: 9))
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                .lineLimit(1)
                            DashBar(fraction: r.fraction, color: dashTone(r.tone), height: 5)
                        }
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
            }
        }
    }
}

// ── Memory Scopes (BL387) + Search Usage (BL391) tiles ────────────────────────

struct DashMemoryCard: View {
    let stats: IosDashMemStats?

    var body: some View {
        if let stats {
            ScrollView {
                VStack(alignment: .leading, spacing: 5) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text("\(Int(stats.total))")
                            .font(.title3.weight(.bold))
                            .foregroundStyle(DatawatchColors.onSurface)
                        Text("total entries")
                            .font(.system(size: 9))
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    ForEach(stats.rows, id: \.label) { row in
                        DashCountRow(row: row)
                    }
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
            }
        } else {
            DashMutedLine(text: "Memory stats unavailable")
        }
    }
}

struct DashSearchUsageCard: View {
    let stats: IosDashWebSearch?

    var body: some View {
        if let stats {
            ScrollView {
                VStack(alignment: .leading, spacing: 5) {
                    HStack(alignment: .firstTextBaseline, spacing: 6) {
                        Text("\(Int(stats.total))")
                            .font(.title3.weight(.bold))
                            .foregroundStyle(DatawatchColors.onSurface)
                        Text("total searches")
                            .font(.system(size: 9))
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    counters(stats)
                    if stats.series.count >= 2 {
                        DashMiniSparkline(values: stats.series.map { $0.doubleValue })
                            .frame(width: 140, height: 28)
                    }
                    providers(stats)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 6)
            }
        } else {
            DashMutedLine(text: "Search usage stats unavailable")
        }
    }

    private func counters(_ s: IosDashWebSearch) -> some View {
        HStack(spacing: 10) {
            Text("today: \(Int(s.today))")
            Text("week: \(Int(s.week))")
            Text("month: \(Int(s.month))")
            Text("cached: \(Int(s.cached))")
        }
        .font(.system(size: 9))
        .foregroundStyle(DatawatchColors.onSurfaceMuted)
    }

    @ViewBuilder
    private func providers(_ s: IosDashWebSearch) -> some View {
        if s.providers.isEmpty {
            Text(L(s.enabled ? "No providers configured" : "Web search disabled"))
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        } else {
            ForEach(s.providers, id: \.label) { row in
                DashCountRow(row: row)
            }
        }
    }
}

private struct DashCountRow: View {
    let row: IosDashCountBar

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Text(row.label).lineLimit(1)
                if row.errors > 0 {
                    Text("(\(Int(row.errors)) err)").foregroundStyle(DatawatchColors.error)
                }
                Spacer(minLength: 4)
                Text("\(Int(row.count))").foregroundStyle(DatawatchColors.onSurface)
            }
            .font(.system(size: 9))
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
            DashBar(fraction: row.fraction, color: DatawatchColors.primary, height: 4)
        }
    }
}

/// PWA `_sparkline(values, 140, 28)` polyline.
private struct DashMiniSparkline: View {
    let values: [Double]

    var body: some View {
        Canvas { gc, size in
            guard values.count >= 2 else { return }
            let w = Double(size.width)
            let h = Double(size.height)
            let step: Double = w / Double(values.count - 1)
            var p = Path()
            for (i, v) in values.enumerated() {
                let pt = CGPoint(x: Double(i) * step, y: h - 1 - v * (h - 2))
                if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
            }
            gc.stroke(p, with: .color(DatawatchColors.primary), lineWidth: 1.5)
        }
        .accessibilityHidden(true)
    }
}

// ── Smoke Run (PWA _dashRenderSmoke, multi-envelope) ──────────────────────────

struct DashSmokeCard: View {
    let view: IosDashSmokeView
    @ObservedObject var vm: DashboardViewModel

    var body: some View {
        if view.runs.isEmpty {
            Text("No runs — start release-smoke.sh or run-tests.sh")
                .font(.caption)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .padding(12)
        } else {
            VStack(spacing: 0) {
                runList
                Rectangle().fill(DatawatchColors.border).frame(height: 2)
                detail
            }
        }
    }

    private var runList: some View {
        let listHeight: CGFloat = view.runs.count > 1 ? min(CGFloat(view.runs.count) * 34 + 22, 140) : 34
        return ScrollView {
            VStack(spacing: 0) {
                ForEach(view.runs, id: \.id) { run in
                    DashSmokeRunRow(run: run, selected: run.id == view.selectedId, vm: vm)
                }
                if view.runs.count > 1 {
                    HStack {
                        Spacer()
                        Button("Clear all") { vm.deleteSmoke("") }
                            .font(.caption2)
                            .buttonStyle(.borderless)
                            .tint(DatawatchColors.onSurfaceMuted)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                }
            }
        }
        .frame(height: listHeight)
    }

    @ViewBuilder
    private var detail: some View {
        if view.selectedId.isEmpty {
            Label("Tap a run to expand", systemImage: "arrow.up")
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else if view.loading {
            DashMutedLine(text: "Loading…")
        } else {
            DashSmokeDetailView(view: view, vm: vm)
        }
    }
}

private struct DashSmokeRunRow: View {
    let run: IosDashSmokeRun
    let selected: Bool
    @ObservedObject var vm: DashboardViewModel

    private var statusColor: Color {
        if run.active { return DatawatchColors.warning }
        return run.fail > 0 ? DatawatchColors.error : DatawatchColors.success
    }

    private var statusSymbol: String {
        if run.active { return "play.fill" }
        return run.fail > 0 ? "xmark" : "checkmark"
    }

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: statusSymbol)
                .font(.caption.weight(.bold))
                .foregroundStyle(statusColor)
            typeTag
            Text(run.version.isEmpty ? run.id : run.version)
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(1)
            Spacer(minLength: 2)
            Text(counts)
                .font(.system(size: 9))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .lineLimit(1)
            DashBar(fraction: min(100, run.pct) / 100.0, color: statusColor, height: 4)
                .frame(width: 36)
            Button { vm.deleteSmoke(run.id) } label: {
                Image(systemName: "xmark").font(.caption2)
            }
            .buttonStyle(.borderless)
            .tint(DatawatchColors.onSurfaceMuted)
            .accessibilityLabel("Delete run")
        }
        .padding(.horizontal, 8)
        .frame(minHeight: 34)
        .background(selected ? DatawatchColors.primary.opacity(0.08) : Color.clear)
        .overlay(alignment: .leading) {
            Rectangle().fill(selected ? DatawatchColors.primary : Color.clear).frame(width: 2)
        }
        .overlay(alignment: .bottom) {
            Rectangle().fill(DatawatchColors.border).frame(height: 1)
        }
        .contentShape(Rectangle())
        .onTapGesture { vm.selectSmokeRun(run.id) }
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private var counts: String {
        var s = "\(Int(run.pass))✓"
        if run.fail > 0 { s += " \(Int(run.fail))✗" }
        if !run.timeLabel.isEmpty { s += " \(run.timeLabel)" }
        return s
    }

    private var typeTag: some View {
        let e2e = run.type == "e2e"
        return Text(e2e ? "E2E" : "SMOKE")
            .font(.system(size: 9, weight: .bold))
            .foregroundStyle(Color.white)
            .padding(.horizontal, 4)
            .padding(.vertical, 1)
            .background(RoundedRectangle(cornerRadius: 3).fill(e2e ? Color(hex: 0x6366F1) : Color(hex: 0x0891B2)))
    }
}

private struct DashSmokeDetailView: View {
    let view: IosDashSmokeView
    @ObservedObject var vm: DashboardViewModel

    private var barColor: Color {
        if view.fail > 0 { return DatawatchColors.error }
        return view.status == "running" ? DatawatchColors.warning : DatawatchColors.success
    }

    var body: some View {
        VStack(spacing: 0) {
            summary
            pills
            rows
        }
    }

    private var summary: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                statusLabel
                Text("\(Int(view.done))/\(Int(view.total)) · \(Int(view.pct))%")
                    .font(.system(size: 9))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer(minLength: 2)
                Text("\(Int(view.pass))✓ \(Int(view.fail))✗ \(Int(view.skip))⏭")
                    .font(.system(size: 9))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            DashBar(fraction: Double(view.pct) / 100.0, color: barColor, height: 4)
                .animation(.easeOut(duration: 0.5), value: view.pct)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var statusLabel: some View {
        switch view.status {
        case "running":
            Label("Running", systemImage: "arrow.triangle.2.circlepath").foregroundStyle(DatawatchColors.warning)
                .font(.caption2.weight(.bold))
        case "failed":
            Label("Failed", systemImage: "xmark.circle.fill").foregroundStyle(DatawatchColors.error)
                .font(.caption2.weight(.bold))
        default:
            Label("Done", systemImage: "checkmark.circle.fill").foregroundStyle(DatawatchColors.success)
                .font(.caption2.weight(.bold))
        }
    }

    private var pills: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 3) {
                ForEach(view.pills, id: \.key) { pill in
                    Button { vm.setSmokeFilter(pill.key) } label: {
                        Text(pill.label)
                            .font(.system(size: 9))
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2)
                            .foregroundStyle(pill.selected ? DatawatchColors.background : DatawatchColors.onSurfaceMuted)
                            .background(Capsule().fill(pill.selected ? DatawatchColors.primary : Color.clear))
                            .overlay(Capsule().stroke(pill.selected ? DatawatchColors.primary : DatawatchColors.border, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(pill.selected ? .isSelected : [])
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
        }
    }

    @ViewBuilder
    private var rows: some View {
        if view.pendingOnly {
            Label("\(Int(view.remaining)) pending", systemImage: "hourglass")
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else if view.rows.isEmpty {
            Text("— none —")
                .font(.caption2)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            ScrollView {
                VStack(spacing: 0) {
                    ForEach(Array(view.rows.enumerated()), id: \.offset) { _, r in
                        DashSmokeSectionRow(row: r)
                    }
                    if view.remaining > 0 && view.pills.first(where: { $0.selected })?.key == "all" {
                        Label("\(Int(view.remaining)) pending", systemImage: "hourglass")
                            .font(.caption2.italic())
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
    }
}

private struct DashSmokeSectionRow: View {
    let row: IosDashSmokeRow

    private var style: (String, Color, Color) {
        switch row.result {
        case "fail": return ("xmark", DatawatchColors.error, DatawatchColors.error.opacity(0.06))
        case "skip": return ("forward.end", DatawatchColors.onSurfaceMuted, Color.clear)
        case "active": return ("play.fill", DatawatchColors.warning, DatawatchColors.warning.opacity(0.08))
        default: return ("checkmark", DatawatchColors.success, Color.clear)
        }
    }

    var body: some View {
        let s = style
        HStack(spacing: 5) {
            Image(systemName: s.0)
                .font(.caption2.weight(.bold))
                .foregroundStyle(s.1)
                .frame(width: 12)
            Text(row.name)
                .font(.caption2)
                .foregroundStyle(row.result == "skip" ? DatawatchColors.onSurfaceMuted : DatawatchColors.onSurface)
                .lineLimit(1)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(s.2)
        .overlay(alignment: .bottom) {
            Rectangle().fill(DatawatchColors.border).frame(height: 1)
        }
    }
}
