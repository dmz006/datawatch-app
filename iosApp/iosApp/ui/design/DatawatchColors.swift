import SwiftUI
import UIKit

/// PWA colour palette — matches Android's DatawatchColors.kt and the CSS
/// custom properties in the PWA (style.css `:root` = dark, `[data-theme="light"]`
/// = light). Every token is a dynamic colour that resolves against the current
/// trait collection, so `.preferredColorScheme` (Settings › About › Theme) and
/// `.environment(\.colorScheme, .dark)` (terminal) both flip it.
///
/// Parity standard: PWA == Android == iOS (capability, not hex literal).
/// Hex values are authoritative here; update all three platforms together.
enum DatawatchColors {
    /// Page / screen background — PWA --bg (#0F1117 / #FFFFFF)
    static let background  = Color(dark: 0x0F1117, light: 0xFFFFFF)
    /// Card and surface background — PWA --bg2 (#1A1D27 / #F1F5F9)
    static let surface     = Color(dark: 0x1A1D27, light: 0xF1F5F9)
    /// Hover / pressed surface — PWA --bg3 (#22263A / #E2E8F0)
    static let surface2    = Color(dark: 0x22263A, light: 0xE2E8F0)
    /// Accent — primary interactive elements — PWA --accent (#7C3AED / #2563EB)
    static let primary     = Color(dark: 0x7C3AED, light: 0x2563EB)
    /// Accent2 — secondary badges — PWA --accent2 (#A855F7 / #7C3AED)
    static let secondary   = Color(dark: 0xA855F7, light: 0x7C3AED)
    /// Success / running sessions — PWA --success (#10B981 / #047857)
    static let success     = Color(dark: 0x10B981, light: 0x047857)
    /// Rate-limited / warnings — PWA --warning (#F59E0B / #B45309)
    static let warning     = Color(dark: 0xF59E0B, light: 0xB45309)
    /// Error / destructive — PWA --error (#EF4444 / #B91C1C)
    static let error       = Color(dark: 0xEF4444, light: 0xB91C1C)
    /// Waiting-input sessions — PWA --waiting (#3B82F6 / #1D4ED8)
    static let waiting     = Color(dark: 0x3B82F6, light: 0x1D4ED8)
    /// Body text — PWA --text (#E2E8F0 / #0F172A)
    static let onSurface   = Color(dark: 0xE2E8F0, light: 0x0F172A)
    /// Muted / disabled text — PWA --text2 (#94A3B8 / #475569)
    static let onSurfaceMuted = Color(dark: 0x94A3B8, light: 0x475569)
    /// Divider / border — PWA --border (#2D3148 / #CBD5E1)
    static let border      = Color(dark: 0x2D3148, light: 0xCBD5E1)
    /// Session-count chip background — PWA --bg3 (#22263A / #E2E8F0)
    static let chipBackground = Color(dark: 0x22263A, light: 0xE2E8F0)
    /// TopAppBar / navigation bar surface — PWA --surface (#1E2130 / #F8FAFC)
    static let surfaceBar = Color(dark: 0x1E2130, light: 0xF8FAFC)
}

extension UIColor {
    convenience init(hex: UInt32) {
        let r = CGFloat((hex >> 16) & 0xFF) / 255.0
        let g = CGFloat((hex >> 8) & 0xFF) / 255.0
        let b = CGFloat(hex & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b, alpha: 1.0)
    }
}

extension Color {
    /// Dynamic colour: `light` under a light trait collection, `dark` otherwise
    /// (dark is the PWA default palette, so `.unspecified` resolves dark).
    init(dark: UInt32, light: UInt32) {
        let darkColor = UIColor(hex: dark)
        let lightColor = UIColor(hex: light)
        let provider = UIColor { (traits: UITraitCollection) -> UIColor in
            traits.userInterfaceStyle == .light ? lightColor : darkColor
        }
        self.init(uiColor: provider)
    }

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

// MARK: - Theme (PWA BL278 Dark / Light / System; default Dark)

/// Persisted app theme (PWA `cs_theme` localStorage: dark | light | system).
enum AppTheme: String, CaseIterable, Identifiable {
    case dark, light, system

    static let storageKey = "dw.theme"

    var id: String { rawValue }

    /// Label key — literal copy matches the PWA `theme_dark/light/system` strings.
    var label: String {
        switch self {
        case .dark: return "Dark"
        case .light: return "Light"
        case .system: return "System"
        }
    }

    /// `nil` = follow the system appearance.
    var colorScheme: ColorScheme? {
        switch self {
        case .dark: return .dark
        case .light: return .light
        case .system: return nil
        }
    }
}

/// Applies the persisted theme to the nearest presentation (window / sheet).
/// Use on the app root and on any sheet or full-screen cover root.
struct DwThemeModifier: ViewModifier {
    @AppStorage(AppTheme.storageKey) private var themeRaw: String = AppTheme.dark.rawValue

    func body(content: Content) -> some View {
        let theme: AppTheme = AppTheme(rawValue: themeRaw) ?? .dark
        return content.preferredColorScheme(theme.colorScheme)
    }
}

extension View {
    /// Theme from Settings › About › Theme (default Dark).
    func dwThemed() -> some View {
        modifier(DwThemeModifier())
    }
}

#if DEBUG
#Preview("Colour palette") {
    ScrollView {
        VStack(spacing: 1) {
            swatch(DatawatchColors.background,      "background")
            swatch(DatawatchColors.surface,         "surface")
            swatch(DatawatchColors.surface2,        "surface2")
            swatch(DatawatchColors.primary,         "primary")
            swatch(DatawatchColors.secondary,       "secondary")
            swatch(DatawatchColors.success,         "success")
            swatch(DatawatchColors.warning,         "warning")
            swatch(DatawatchColors.error,           "error")
            swatch(DatawatchColors.waiting,         "waiting")
            swatch(DatawatchColors.onSurface,       "on-surface")
            swatch(DatawatchColors.onSurfaceMuted,  "muted")
            swatch(DatawatchColors.border,          "border")
            swatch(DatawatchColors.chipBackground,  "chip bg")
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
