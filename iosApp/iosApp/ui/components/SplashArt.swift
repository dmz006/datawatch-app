import SwiftUI

/// SwiftUI port of the PWA `splash-art.js` (itself a port of Android
/// `MatrixSplashScreen.kt`). Colours, radius fractions, PRNG seeds and timings
/// are copied 1:1 so all three platforms render the same Earthrise scene.
///
/// - `SplashSceneView(compact: false)` — app splash (tablet centred)
/// - `SplashSceneView(compact: true)`  — Settings → About (tablet in moon area)
/// - `SplashEyeView(bolt:)`            — eye + dot rain (+ lightning for session connect)
///
/// Honours Reduce Motion: draws one static frame (PWA `prefers-reduced-motion`).
enum SplashArt {
    // MARK: palette (splash-art.js PAL)
    static let bg = Color(hex: 0x0F1117)
    static let bezelDark = Color(hex: 0x0D0720)
    static let screenDark = Color(hex: 0x08031A)
    static let border = Color(hex: 0x8B5CF6)
    static let irisOuter = Color(hex: 0x3B0764)
    static let irisMid = Color(hex: 0x8B5CF6)
    static let irisInner = Color(hex: 0xA855F7)
    static let pupil = Color(hex: 0x04020E)
    static let crosshair = Color(hex: 0xE879F9)
    static let highlight = Color(hex: 0xF0ABFC)
    static let matrix = Color(hex: 0xA855F7)
    static let matrixBright = Color(hex: 0xC084FC)
    static let matrixLead = Color(hex: 0xF0ABFC)
    static let speaker = Color(hex: 0x2D1B4E)
    static let screenBorder = Color(red: 76 / 255, green: 29 / 255, blue: 149 / 255).opacity(0.8)
    static let matrixChars = Array("ABCDEF0123456789xWTCHR").map(String.init)

    // MARK: timing helpers
    static func triangleWave(_ t: Double, _ durationMs: Double, _ delayMs: Double = 0) -> Double {
        let local = ((t - delayMs).truncatingRemainder(dividingBy: durationMs) + durationMs)
            .truncatingRemainder(dividingBy: durationMs)
        let phase = local / durationMs
        return phase < 0.5 ? phase * 2 : 2 - phase * 2
    }
    static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }
    static func lerp(_ a: Double, _ b: Double, _ f: Double) -> Double { a + (b - a) * f }
    static func sawtooth(_ t: Double, _ durationMs: Double) -> Double {
        ((t.truncatingRemainder(dividingBy: durationMs)) + durationMs).truncatingRemainder(dividingBy: durationMs) / durationMs
    }

    /// mulberry32 — deterministic column layout identical to the PWA.
    struct Mulberry32 {
        var seed: UInt32
        init(_ seed: UInt32) { self.seed = seed }
        mutating func next() -> Double {
            seed = seed &+ 0x6D2B_79F5
            var t = (seed ^ (seed >> 15)) &* (1 | seed)
            t = (t &+ ((t ^ (t >> 7)) &* (61 | t))) ^ t
            return Double(t ^ (t >> 14)) / 4_294_967_296
        }
    }

    struct Column { let xFrac: Double; let delayFrac: Double; let charCount: Int }

    static func makeColumns(_ n: Int, seed: UInt32, xStart: Double, xSpan: Double, range: ClosedRange<Int>) -> [Column] {
        var rng = Mulberry32(seed)
        let step: Double = xSpan / Double(max(1, n - 1))
        let width: Double = Double(range.upperBound - range.lowerBound + 1)
        var cols: [Column] = []
        for i in 0..<n {
            let delay: Double = rng.next()
            let extra: Int = Int(floor(rng.next() * width))
            let x: Double = xStart + Double(i) * step
            cols.append(Column(xFrac: x, delayFrac: delay, charCount: range.lowerBound + extra))
        }
        return cols
    }

    static func assignChars(_ cols: [Column], seed: UInt32) -> [[String]] {
        var rng = Mulberry32(seed)
        let count: Double = Double(matrixChars.count)
        var out: [[String]] = []
        for c in cols {
            var chars: [String] = []
            for _ in 0..<c.charCount {
                let idx: Int = Int(floor(rng.next() * count))
                chars.append(matrixChars[idx])
            }
            out.append(chars)
        }
        return out
    }

    static func makeFlickers(_ n: Int) -> [(dur: Double, delay: Double)] {
        (0..<n).map { i in (Double(900 + (i * 73) % 900), Double((i * 41) % 500)) }
    }

    // Scene constants (computed once, like the JS `_cols` caches).
    static let sceneCols = makeColumns(9, seed: 4242, xStart: 0.12, xSpan: 0.76, range: 7...10)
    static let sceneChars = assignChars(sceneCols, seed: 13)
    static let sceneFlick = makeFlickers(32)
    static let eyeCols = makeColumns(7, seed: 7979, xStart: 0.08, xSpan: 0.84, range: 6...9)
    static let eyeFlick = makeFlickers(16)

    static let stars: [(Double, Double)] = [
        (0.05, 0.06), (0.12, 0.10), (0.20, 0.04), (0.32, 0.08), (0.42, 0.05),
        (0.62, 0.07), (0.72, 0.04), (0.84, 0.10), (0.92, 0.06), (0.08, 0.18),
        (0.94, 0.20), (0.34, 0.16), (0.66, 0.18),
    ]
    static let craters: [(Double, Double, Double)] = [
        (0.12, 0.78, 0.08), (0.88, 0.80, 0.09), (0.32, 0.55, 0.06), (0.68, 0.55, 0.06),
        (0.06, 0.58, 0.05), (0.94, 0.58, 0.04), (0.22, 0.94, 0.04), (0.78, 0.94, 0.05),
    ]

    // MARK: drawing
    static func circle(_ cx: Double, _ cy: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
    }
    static func ellipse(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
    }

    static func drawEye(_ ctx: inout GraphicsContext, cx: Double, cy: Double, radius: Double, pupilScale: Double, glowAlpha: Double) {
        if glowAlpha > 0 {
            ctx.fill(circle(cx, cy, radius * 2.3), with: .color(irisMid.opacity(glowAlpha * 0.55)))
            ctx.fill(circle(cx, cy, radius * 1.75), with: .color(irisInner.opacity(glowAlpha * 0.28)))
        }
        let erx = radius * 1.92, ery = radius * 1.20
        let outer = ellipse(cx, cy, erx, ery)
        ctx.fill(outer, with: .color(Color(hex: 0x080518)))
        ctx.stroke(outer, with: .color(border), lineWidth: 3.5)
        ctx.stroke(ellipse(cx, cy, erx * 0.91, ery * 0.91), with: .color(irisMid.opacity(0.40)), lineWidth: 1.5)

        ctx.fill(circle(cx, cy, radius), with: .color(irisOuter))
        ctx.fill(circle(cx, cy, radius * 0.82), with: .color(irisMid))
        ctx.fill(circle(cx, cy, radius * 0.52), with: .color(irisInner.opacity(0.75)))
        ctx.stroke(circle(cx, cy, radius * 0.80), with: .color(irisInner.opacity(0.55)), lineWidth: 1.8)

        ctx.fill(circle(cx, cy, radius * 0.38 * pupilScale), with: .color(pupil))
        ctx.stroke(circle(cx, cy, radius * 0.42 * pupilScale), with: .color(irisOuter.opacity(0.65)), lineWidth: 1.2)

        let cr = radius * 0.40, gap = radius * 0.13
        var cross = Path()
        cross.move(to: CGPoint(x: cx, y: cy - cr)); cross.addLine(to: CGPoint(x: cx, y: cy - gap))
        cross.move(to: CGPoint(x: cx, y: cy + gap)); cross.addLine(to: CGPoint(x: cx, y: cy + cr))
        cross.move(to: CGPoint(x: cx - cr, y: cy)); cross.addLine(to: CGPoint(x: cx - gap, y: cy))
        cross.move(to: CGPoint(x: cx + gap, y: cy)); cross.addLine(to: CGPoint(x: cx + cr, y: cy))
        ctx.stroke(cross, with: .color(crosshair), style: StrokeStyle(lineWidth: 4.5, lineCap: .round))

        ctx.fill(circle(cx, cy, radius * 0.09 * pupilScale), with: .color(highlight))

        var glint = ctx
        glint.translateBy(x: cx - radius * 0.42, y: cy - radius * 0.28)
        glint.rotate(by: .degrees(-30))
        glint.fill(ellipse(-radius * 0.13, -radius * 0.04, radius * 0.14, radius * 0.065), with: .color(.white.opacity(0.13)))
    }

    static func drawScene(_ ctx: inout GraphicsContext, size: CGSize, compact: Bool, t: Double) {
        let w = size.width, h = size.height
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(bg))
        let cx = w / 2, cy = h / 2
        let discRadius = min(w, h) / 2.2
        let pupilScale = lerp(0.90, 1.10, smooth(triangleWave(t, 2000)))
        let rainTime = sawtooth(t, 5000)
        let scanY = sawtooth(t, 9000)

        for s in stars { ctx.fill(circle(w * s.0, h * s.1, 1.2), with: .color(.white.opacity(0.55))) }

        // earth
        let earthR = w * 0.065, ex = cx, ey = h * 0.16
        ctx.fill(circle(ex, ey, earthR * 1.28), with: .color(Color(hex: 0x7AB8E8).opacity(0.30)))
        ctx.fill(circle(ex, ey, earthR), with: .color(Color(hex: 0x0B2A55)))
        ctx.fill(circle(ex - earthR * 0.10, ey - earthR * 0.10, earthR * 0.78), with: .color(Color(hex: 0x4988C8)))
        ctx.fill(circle(ex - earthR * 0.20, ey - earthR * 0.20, earthR * 0.40), with: .color(Color(hex: 0xA8D4F2)))
        ctx.fill(circle(ex + earthR * 0.20, ey + earthR * 0.10, earthR * 0.30), with: .color(Color(hex: 0x2F5E36).opacity(0.7)))
        ctx.fill(circle(ex - earthR * 0.30, ey - earthR * 0.30, earthR * 0.18), with: .color(.white.opacity(0.35)))

        // moon
        let horizonY = h * 0.28
        let moonRect = Path(CGRect(x: 0, y: horizonY, width: w, height: h - horizonY))
        ctx.fill(moonRect, with: .linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0x7A6B5F), location: 0),
                .init(color: Color(hex: 0x332B25), location: 0.5),
                .init(color: Color(hex: 0x0F0A08), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: horizonY), endPoint: CGPoint(x: 0, y: h)
        ))
        var horizon = Path()
        horizon.move(to: CGPoint(x: 0, y: horizonY)); horizon.addLine(to: CGPoint(x: w, y: horizonY))
        ctx.stroke(horizon, with: .color(Color(hex: 0x7AB8E8).opacity(0.4)), lineWidth: 2)
        for c in craters {
            let rx = w * c.2
            let p = ellipse(w * c.0, h * c.1, rx, rx * 0.28)
            ctx.fill(p, with: .color(Color(hex: 0x1A1310)))
            ctx.stroke(p, with: .color(Color(hex: 0x8B7B6E).opacity(0.6)), lineWidth: 1.2)
        }

        // tablet
        let tw = discRadius * 1.8, th = discRadius * 1.4
        let tl = cx - tw / 2
        let moonAreaCenter = (horizonY + h) / 2
        let tt = compact ? moonAreaCenter - th / 2 : cy - th / 2
        ctx.fill(ellipse(tl + tw / 2, tt + th - 6, tw / 2 + 6, 9), with: .color(.black.opacity(0.5)))
        let bezel = Path(roundedRect: CGRect(x: tl, y: tt, width: tw, height: th), cornerRadius: 28)
        ctx.fill(bezel, with: .color(bezelDark))
        ctx.stroke(bezel, with: .color(border), lineWidth: 3)
        ctx.fill(Path(roundedRect: CGRect(x: cx - 20, y: tt + 8, width: 40, height: 4), cornerRadius: 2), with: .color(speaker))

        let pad = 14.0
        let screen = CGRect(x: tl + pad, y: tt + pad + 14, width: tw - 2 * pad, height: th - 2 * pad - 14)
        let screenPath = Path(roundedRect: screen, cornerRadius: 18)
        ctx.fill(screenPath, with: .color(screenDark))
        ctx.stroke(screenPath, with: .color(screenBorder), lineWidth: 1)

        var rain = ctx
        rain.clip(to: screenPath)
        for (ci, col) in sceneCols.enumerated() {
            let phase = (rainTime + col.delayFrac).truncatingRemainder(dividingBy: 1)
            let colY = screen.minY - 20 + phase * (screen.height + 40)
            let colX = screen.minX + col.xFrac * screen.width
            for (ri, ch) in sceneChars[ci].enumerated() {
                let y = colY + Double(ri) * 16
                if y < screen.minY - 16 || y > screen.maxY { continue }
                let f = sceneFlick[(ci * 7 + ri) % sceneFlick.count]
                let base = lerp(0.25, 0.85, smooth(triangleWave(t, f.dur, f.delay)))
                let posW = 1 - Double(ri) / Double(col.charCount)
                let alpha = max(0, min(1, base * (0.45 + 0.55 * posW)))
                let tint = ri == 0 ? matrixLead : (ri % 3 == 0 ? matrixBright : matrix)
                rain.draw(
                    Text(ch).font(.system(size: 13, design: .monospaced)).foregroundColor(tint.opacity(alpha)),
                    at: CGPoint(x: colX, y: y), anchor: .top
                )
            }
        }
        rain.fill(Path(CGRect(x: screen.minX, y: screen.minY + scanY * screen.height, width: screen.width, height: 2)),
                  with: .color(border.opacity(0.30)))

        drawEye(&ctx, cx: cx, cy: screen.midY, radius: discRadius * 0.44, pupilScale: pupilScale, glowAlpha: 0)
    }

    static func drawEyeOnly(_ ctx: inout GraphicsContext, size: CGSize, t: Double) {
        let w = size.width, h = size.height
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(bg))
        let pupilScale = lerp(0.90, 1.10, smooth(triangleWave(t, 2000)))
        let glowAlpha = lerp(0.10, 0.28, smooth(triangleWave(t, 3200)))
        let rainTime = sawtooth(t, 5000)
        for (ci, col) in eyeCols.enumerated() {
            let phase = (rainTime + col.delayFrac).truncatingRemainder(dividingBy: 1)
            let colY = -20 + phase * (h + 40)
            let colX = col.xFrac * w
            for ri in 0..<col.charCount {
                let y = colY + Double(ri) * 18
                if y < -16 || y > h { continue }
                let f = eyeFlick[(ci * 5 + ri) % eyeFlick.count]
                let base = lerp(0.15, 0.60, smooth(triangleWave(t, f.dur, f.delay)))
                let alpha = max(0, min(1, base * (0.4 + 0.6 * (1 - Double(ri) / Double(col.charCount)))))
                ctx.fill(circle(colX, y, 3), with: .color(matrix.opacity(alpha * 0.6)))
            }
        }
        drawEye(&ctx, cx: w / 2, cy: h / 2, radius: min(w, h) * 0.37, pupilScale: pupilScale, glowAlpha: glowAlpha)
    }

    static func drawLightningBolt(_ ctx: inout GraphicsContext, size: CGSize, t: Double) {
        let cx = size.width / 2, cy = size.height / 2
        let boltAlpha = lerp(0.55, 1.0, triangleWave(t, 220))
        let jitter = lerp(-1.5, 1.5, triangleWave(t, 80))
        let stroke = lerp(2.0, 3.5, smooth(triangleWave(t, 600)))
        let bh = min(size.width, size.height) * 0.085, bw = bh * 0.55
        let x = cx + jitter
        var p = Path()
        p.move(to: CGPoint(x: x + bw * 0.35, y: cy - bh))
        p.addLine(to: CGPoint(x: x - bw * 0.25, y: cy - bh * 0.08))
        p.addLine(to: CGPoint(x: x + bw * 0.55, y: cy - bh * 0.08))
        p.addLine(to: CGPoint(x: x - bw * 0.35, y: cy + bh))
        let style = { (lw: Double) in StrokeStyle(lineWidth: lw, lineCap: .round, lineJoin: .round) }
        ctx.stroke(p, with: .color(Color(hex: 0x00E5A0).opacity(boltAlpha * 0.30)), style: style(stroke * 3.5))
        ctx.stroke(p, with: .color(Color(hex: 0xE879F9).opacity(boltAlpha)), style: style(stroke))
        ctx.stroke(p, with: .color(.white.opacity(boltAlpha * 0.55)), style: style(stroke * 0.4))
    }
}

/// Animated driver: TimelineView(.animation) while motion is allowed, one
/// static frame (t = 0) under Reduce Motion.
private struct SplashCanvas: View {
    let draw: (inout GraphicsContext, CGSize, Double) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var start = Date()

    var body: some View {
        if reduceMotion {
            Canvas { ctx, size in draw(&ctx, size, 0) }
        } else {
            TimelineView(.animation) { tl in
                let t = tl.date.timeIntervalSince(start) * 1000
                Canvas { ctx, size in draw(&ctx, size, t) }
            }
        }
    }
}

/// Full Earthrise scene (PWA DWSplashArt.startScene).
struct SplashSceneView: View {
    var compact: Bool = false
    var body: some View {
        SplashCanvas { ctx, size, t in SplashArt.drawScene(&ctx, size: size, compact: compact, t: t) }
            .accessibilityHidden(true)
    }
}

/// Eye + dot rain (PWA startEyeOnly), optionally with the lightning bolt
/// used by the session-connect overlay (startSessionLoading).
struct SplashEyeView: View {
    var bolt: Bool = false
    var body: some View {
        SplashCanvas { ctx, size, t in
            SplashArt.drawEyeOnly(&ctx, size: size, t: t)
            if bolt { SplashArt.drawLightningBolt(&ctx, size: size, t: t) }
        }
        .accessibilityHidden(true)
    }
}

/// Splash text block (PWA index.html:66-70 / style.css:107-126):
/// "datawatch" 24/700 accent2 lowercase, letter-spacing 2 · "AI Orchestration"
/// 12 text2 · version 11 accent2 @85%.
struct SplashTextBlock: View {
    let version: String
    var body: some View {
        VStack(spacing: 4) {
            Text("datawatch")
                .font(.system(size: 24, weight: .bold))
                .kerning(2)
                .foregroundStyle(DatawatchColors.secondary)
            Text("AI Orchestration")
                .font(.system(size: 12))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Text("v\(version)")
                .font(.system(size: 11))
                .foregroundStyle(DatawatchColors.secondary.opacity(0.85))
        }
        .accessibilityElement(children: .combine)
    }
}
