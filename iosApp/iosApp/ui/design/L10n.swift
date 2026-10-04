import Foundation

/// Runtime localization lookup for UI copy held in `String` values (label
/// helpers, tab titles, computed hints). Returns plain `String` so `Text`
/// renders it verbatim (no markdown parsing). Unknown keys fall back to the
/// English key itself. Literal `Text("…")` strings are localized by SwiftUI
/// directly and don't need this.
func L(_ key: String) -> String {
    NSLocalizedString(key, comment: "")
}
