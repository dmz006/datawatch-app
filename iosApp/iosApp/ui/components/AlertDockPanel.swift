import SwiftUI

/// Floating alert dock (PWA `renderAlertDock`, D3a): anchored top-right under
/// the header, shown only after the user taps the 🔔 pill. Header: total +
/// per-type ×N chips + ⌄ + ✕ (clear) + 🔕 (mute). Body: newest-first cards with
/// a coloured left rail, type, clock time, ×N, per-card ✕ and a 3-line clamp
/// with ▸ more / ▾ less. No open/close animation (D36b — the PWA dock is static).
struct AlertDockPanel: View {
    @ObservedObject var dock: AlertDock

    var body: some View {
        VStack(spacing: 0) {
            if dock.entries.isEmpty {
                emptyHeader
            } else {
                header
                if dock.entries.count > 3 {
                    // Capped body height (PWA min(50vh, 360px)) — scrolls past that.
                    ScrollView { cards }.frame(height: 340)
                } else {
                    cards
                }
            }
        }
        .frame(maxWidth: 420)
        .background(DatawatchColors.background)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(DatawatchColors.border, lineWidth: 1))
        .shadow(color: .black.opacity(0.35), radius: 8, x: 0, y: 6)
        .padding(.horizontal, 8)
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Alert dock")
    }

    private var cards: some View {
        LazyVStack(spacing: 6) {
            ForEach(dock.entries) { entry in
                AlertDockCard(entry: entry, dock: dock)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
    }

    private var emptyHeader: some View {
        HStack(spacing: 10) {
            Text("🔔 " + L("no alerts"))
                .font(DatawatchFonts.bodyMedium.weight(.bold))
                .foregroundStyle(DatawatchColors.onSurface)
            Spacer(minLength: 8)
            iconButton("✕", label: "Close") { dock.dismiss() }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(DatawatchColors.surface)
    }

    private var header: some View {
        HStack(spacing: 8) {
            Text(totalLabel)
                .font(DatawatchFonts.bodyMedium.weight(.bold))
                .foregroundStyle(DatawatchColors.onSurface)
                .fixedSize()
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 3) {
                    ForEach(dock.countsByLevel, id: \.0) { pair in
                        typeChip(pair.0, pair.1)
                    }
                }
            }
            Text("⌄")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            iconButton("✕", label: "Dismiss all") { dock.dismiss() }
            iconButton("🔕", label: "Mute alerts for this session") { dock.mute() }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(DatawatchColors.surface)
        .contentShape(Rectangle())
        .onTapGesture { dock.toggle() }
    }

    private var totalLabel: String {
        let n: Int = dock.total
        return "🔔 \(n) " + (n == 1 ? L("alert") : L("alerts"))
    }

    private func typeChip(_ level: DockLevel, _ n: Int) -> some View {
        Text("\(level.rawValue) ×\(n)")
            .font(DatawatchFonts.labelSmall)
            .foregroundStyle(DatawatchColors.onSurface)
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 8))
    }

    private func iconButton(_ glyph: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(glyph)
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .frame(minWidth: 30, minHeight: 30)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(L(label))
    }
}

/// One dock card (PWA `.alert-card`).
private struct AlertDockCard: View {
    let entry: DockEntry
    @ObservedObject var dock: AlertDock

    private var isLong: Bool { entry.message.count > 140 || entry.message.contains("\n") }
    private var expanded: Bool { dock.expandedCards.contains(entry.id) }

    var body: some View {
        HStack(spacing: 0) {
            Rectangle().fill(rail).frame(width: 3)
            VStack(alignment: .leading, spacing: 4) {
                topRow
                Text(entry.message)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(isLong && !expanded ? 3 : nil)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if isLong {
                    Button(expanded ? L("▾ less") : L("▸ more")) { toggleExpanded() }
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 8)
        }
        .background(DatawatchColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.border, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }

    private var topRow: some View {
        HStack(spacing: 8) {
            Text(icon).font(DatawatchFonts.labelSmall.weight(.bold)).foregroundStyle(rail)
            Text(entry.level.rawValue)
                .font(DatawatchFonts.badge)
                .foregroundStyle(DatawatchColors.onSurface)
                .padding(.horizontal, 6)
                .padding(.vertical, 1)
                .background(DatawatchColors.background, in: RoundedRectangle(cornerRadius: 4))
            Text(Self.clock(entry.ts))
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if entry.count > 1 {
                Text("×\(entry.count)")
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.background)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 1)
                    .background(AlertsBellButton.accent2, in: Capsule())
            }
            Spacer(minLength: 4)
            Button { dock.remove(entry.id) } label: {
                Text("✕")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .frame(minWidth: 28, minHeight: 24)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Dismiss")
        }
    }

    private func toggleExpanded() {
        if expanded { dock.expandedCards.remove(entry.id) } else { dock.expandedCards.insert(entry.id) }
    }

    private var rail: Color {
        switch entry.level {
        case .error: return DatawatchColors.error
        case .warning: return DatawatchColors.warning
        case .success: return DatawatchColors.success
        case .info: return AlertsBellButton.accent2
        }
    }

    private var icon: String {
        switch entry.level {
        case .error: return "✕"
        case .warning: return "⚠"
        case .success: return "✓"
        case .info: return "ℹ"
        }
    }

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    static func clock(_ d: Date) -> String { formatter.string(from: d) }
}

/// Root overlay: the dock panel under the header, top-right, while open.
struct AlertDockOverlay: ViewModifier {
    @ObservedObject private var dock = AlertDock.shared

    func body(content: Content) -> some View {
        content.overlay(alignment: .topTrailing) {
            if dock.open {
                AlertDockPanel(dock: dock)
                    .padding(.top, 48)
            }
        }
    }
}

extension View {
    func alertDockOverlay() -> some View { modifier(AlertDockOverlay()) }
}
