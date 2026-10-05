import SwiftUI

/// PWA corner-radius tokens (style.css `:root` `--radius` / `--radius-sm`, pill
/// chips) — Android `pwaCard` 12dp / `PwaStatePill` 10dp.
enum DatawatchRadius {
    /// Cards — PWA `--radius` 12px.
    static let card: CGFloat = 12
    /// Small surfaces (inputs, small boxes) — PWA `--radius-sm` 8px.
    static let sm: CGFloat = 8
    /// Pills / chips — PWA `border-radius: 10px`.
    static let pill: CGFloat = 10
}
