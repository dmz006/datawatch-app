import SwiftUI

/// PWA colour palette — matches Android's DatawatchColors.kt and the
/// CSS custom properties in the PWA. Dark-only: no light-mode variants.
///
/// Parity standard: PWA == Android == iOS (capability, not hex literal).
/// Hex values are authoritative here; update all three platforms together.
enum DatawatchColors {
    /// Page / screen background (#0F1117)
    static let background  = Color(hex: 0x0F1117)
    /// Card and surface background (#1A1D27)
    static let surface     = Color(hex: 0x1A1D27)
    /// Hover / pressed surface bg3 (#22263A)
    static let surface2    = Color(hex: 0x22263A)
    /// Purple accent — primary interactive elements (#7C3AED)
    static let primary     = Color(hex: 0x7C3AED)
    /// Purple2 accent — secondary badges (#A855F7)
    static let secondary   = Color(hex: 0xA855F7)
    /// Success / running sessions (#10B981)
    static let success     = Color(hex: 0x10B981)
    /// Rate-limited / warnings (#F59E0B)
    static let warning     = Color(hex: 0xF59E0B)
    /// Error / destructive (#EF4444)
    static let error       = Color(hex: 0xEF4444)
    /// Waiting-input sessions (#3B82F6)
    static let waiting     = Color(hex: 0x3B82F6)
    /// Body text on dark surfaces (#E2E8F0)
    static let onSurface   = Color(hex: 0xE2E8F0)
    /// Muted / disabled text (#94A3B8)
    static let onSurfaceMuted = Color(hex: 0x94A3B8)
    /// Divider / border (#2D3148)
    static let border      = Color(hex: 0x2D3148)
    /// Session-count chip background (#22263A)
    static let chipBackground = Color(hex: 0x22263A)
    /// TopAppBar / navigation bar surface (#1E2130)
    static let surfaceBar = Color(hex: 0x1E2130)
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red:   Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >>  8) & 0xFF) / 255,
            blue:  Double( hex        & 0xFF) / 255
        )
    }

    var hexString: String {
        let ui = UIColor(self)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: nil)
        return String(format: "#%02X%02X%02X", Int(r * 255), Int(g * 255), Int(b * 255))
    }
}

#if DEBUG
#Preview("Colour palette") {
    ScrollView {
        VStack(spacing: 1) {
            swatch(DatawatchColors.background,      "#0F1117 background")
            swatch(DatawatchColors.surface,         "#1A1D27 surface")
            swatch(DatawatchColors.surface2,        "#22263A surface2")
            swatch(DatawatchColors.primary,         "#7C3AED primary")
            swatch(DatawatchColors.secondary,       "#A855F7 secondary")
            swatch(DatawatchColors.success,         "#10B981 success")
            swatch(DatawatchColors.warning,         "#F59E0B warning")
            swatch(DatawatchColors.error,           "#EF4444 error")
            swatch(DatawatchColors.waiting,         "#3B82F6 waiting")
            swatch(DatawatchColors.onSurface,       "#E2E8F0 on-surface")
            swatch(DatawatchColors.onSurfaceMuted,  "#94A3B8 muted")
            swatch(DatawatchColors.border,          "#2D3148 border")
            swatch(DatawatchColors.chipBackground,  "#22263A chip bg")
        }
    }
    .background(DatawatchColors.background)
}

private func swatch(_ color: Color, _ label: String) -> some View {
    HStack {
        Rectangle()
            .fill(color)
            .frame(width: 44, height: 44)
            .cornerRadius(4)
        Text(label)
            .font(.system(.caption, design: .monospaced))
            .foregroundStyle(DatawatchColors.onSurface)
        Spacer()
    }
    .padding(.horizontal)
}
#endif
