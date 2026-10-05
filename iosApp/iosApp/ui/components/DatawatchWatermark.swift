import SwiftUI

/// PWA `.sessions-watermark` (style.css): the `/favicon.svg` eye centred
/// behind the Sessions and Automata lists at min(85vw, 400px), opacity .045,
/// not hit-testable. Android draws the launcher foreground the same way.
/// Drawn from the favicon geometry (32×32 viewBox) so no image asset is needed.
struct DatawatchWatermark: View {
    var body: some View {
        GeometryReader { geo in
            let side: Double = min(Double(geo.size.width) * 0.85, 400.0)
            Canvas { ctx, size in
                Self.draw(&ctx, size: size)
            }
            .frame(width: side, height: side)
            .position(x: geo.size.width / 2, y: geo.size.height / 2)
        }
        .opacity(0.045)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private static func pt(_ x: Double, _ y: Double, _ s: Double) -> CGPoint {
        CGPoint(x: x * s, y: y * s)
    }

    private static func line(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ s: Double) -> Path {
        var p = Path()
        p.move(to: pt(x1, y1, s))
        p.addLine(to: pt(x2, y2, s))
        return p
    }

    private static func oval(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double, _ s: Double) -> Path {
        Path(ellipseIn: CGRect(x: (cx - rx) * s, y: (cy - ry) * s, width: rx * 2 * s, height: ry * 2 * s))
    }

    static func draw(_ ctx: inout GraphicsContext, size: CGSize) {
        let s: Double = Double(min(size.width, size.height)) / 32.0
        let violet = Color(red: 0x4C / 255.0, green: 0x1D / 255.0, blue: 0x95 / 255.0)
        let purple = Color(red: 0x7C / 255.0, green: 0x3A / 255.0, blue: 0xED / 255.0)
        let iris = Color(red: 0x6D / 255.0, green: 0x28 / 255.0, blue: 0xD9 / 255.0)
        let dark = Color(red: 0x0A / 255.0, green: 0x0A / 255.0, blue: 0x14 / 255.0)
        let pink = Color(red: 0xE8 / 255.0, green: 0x79 / 255.0, blue: 0xF9 / 255.0)
        ctx.fill(oval(16, 16, 16, 16, s), with: .color(Color(red: 0x1A / 255.0, green: 0x0A / 255.0, blue: 0x2E / 255.0)))
        let ticks: [(Double, Double, Double, Double)] = [
            (16, 1.5, 16, 3.5), (16, 28.5, 16, 30.5), (1.5, 16, 3.5, 16), (28.5, 16, 30.5, 16),
            (5.5, 5.5, 7, 7), (25, 25, 26.5, 26.5), (26.5, 5.5, 25, 7), (7, 25, 5.5, 26.5),
        ]
        for t in ticks {
            ctx.stroke(line(t.0, t.1, t.2, t.3, s), with: .color(violet), lineWidth: 0.8 * s)
        }
        ctx.fill(oval(16, 16, 11, 6.5, s), with: .color(Color(red: 0x0F / 255.0, green: 0x0A / 255.0, blue: 0x1F / 255.0)))
        ctx.stroke(oval(16, 16, 11, 6.5, s), with: .color(purple), lineWidth: 0.6 * s)
        ctx.fill(oval(16, 16, 5, 5, s), with: .color(iris))
        ctx.fill(oval(16, 16, 2, 2, s), with: .color(dark))
        let reticle: [(Double, Double, Double, Double)] = [
            (16, 14.3, 16, 15.2), (16, 16.8, 16, 17.7), (14.3, 16, 15.2, 16), (16.8, 16, 17.7, 16),
        ]
        for r in reticle {
            ctx.stroke(line(r.0, r.1, r.2, r.3, s), with: .color(pink), lineWidth: 0.5 * s)
        }
        ctx.fill(oval(16, 16, 0.6, 0.6, s), with: .color(pink))
    }
}
