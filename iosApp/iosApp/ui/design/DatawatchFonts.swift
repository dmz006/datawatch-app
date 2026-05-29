import SwiftUI

/// Typography system — mirrors the PWA / Android font roles.
/// SF Pro is the iOS system default (no custom font needed).
/// SF Mono is used for terminal content, matching the PWA's monospace feel.
enum DatawatchFonts {
    // ── UI text (SF Pro) ─────────────────────────────────────────────────
    static let titleLarge   = Font.system(.title2,   design: .default, weight: .semibold)
    static let titleMedium  = Font.system(.headline, design: .default, weight: .semibold)
    static let bodyLarge    = Font.system(.body,     design: .default)
    static let bodyMedium   = Font.system(.callout,  design: .default)
    static let labelSmall   = Font.system(.caption,  design: .default)
    static let badge        = Font.system(.caption2, design: .default, weight: .bold)

    // ── Terminal / code text (SF Mono) ────────────────────────────────────
    static let terminalBody = Font.system(.body,    design: .monospaced)
    static let terminalSmall = Font.system(.caption, design: .monospaced)
}

extension View {
    /// Apply the standard body text style.
    func datawatchBody() -> some View {
        self.font(DatawatchFonts.bodyLarge).foregroundStyle(DatawatchColors.onSurface)
    }

    /// Apply the standard terminal text style.
    func datawatchTerminal() -> some View {
        self.font(DatawatchFonts.terminalBody).foregroundStyle(DatawatchColors.primary)
    }
}
