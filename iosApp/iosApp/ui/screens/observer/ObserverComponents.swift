import SwiftUI
import DatawatchShared

// Shared building blocks for the Observer tab (parity B20–B25).
// PWA content (colours, copy, order) with native SwiftUI controls.

// ── Tone → colour token ───────────────────────────────────────────────────

/// Maps the `tone` strings produced by `IosObserver` (shared Kotlin) to
/// `DatawatchColors` tokens. PWA: --error / --warning / --success /
/// --accent / --accent2 / --text2 / --text.
enum ObsTone {
    static func color(_ tone: String) -> Color {
        switch tone {
        case "error": return DatawatchColors.error
        case "warning": return DatawatchColors.warning
        case "success": return DatawatchColors.success
        case "accent": return DatawatchColors.primary
        case "accent2": return DatawatchColors.secondary
        case "muted": return DatawatchColors.onSurfaceMuted
        default: return DatawatchColors.onSurface
        }
    }
}

// ── Collapsed-state persistence (D27a) ────────────────────────────────────

/// Per-card collapsed state persisted in UserDefaults (PWA:
/// `cs_settings_collapsed` in localStorage).
@MainActor
final class ObserverCollapseStore: ObservableObject {
    private static let defaultsKey = "dw.observer.collapsed"
    @Published private var collapsed: Set<String>

    init() {
        let saved = UserDefaults.standard.stringArray(forKey: Self.defaultsKey) ?? []
        collapsed = Set(saved)
    }

    func isCollapsed(_ key: String) -> Bool { collapsed.contains(key) }

    func toggle(_ key: String) {
        if collapsed.contains(key) {
            collapsed.remove(key)
        } else {
            collapsed.insert(key)
        }
        UserDefaults.standard.set(Array(collapsed), forKey: Self.defaultsKey)
    }
}

/// PWA `defsLink(title)` slug: non-alphanumerics → "-", lower-cased, trimmed.
func observerDocsSlug(_ title: String) -> String {
    var out = ""
    var lastDash = false
    for ch in title.lowercased() {
        if (ch >= "a" && ch <= "z") || (ch >= "0" && ch <= "9") {
            out.append(ch)
            lastDash = false
        } else if !lastDash {
            out.append("-")
            lastDash = true
        }
    }
    return out.trimmingCharacters(in: CharacterSet(charactersIn: "-"))
}

// ── Collapsible card (settingsSectionHeader + secContent) ─────────────────

struct ObsSection<Content: View>: View {
    let key: String
    /// English PWA title — localized via `L()` for display, slugged for the docs anchor (D26a).
    let title: String
    let profile: ServerProfile?
    @ObservedObject var store: ObserverCollapseStore
    @ViewBuilder let content: () -> Content

    private var collapsed: Bool { store.isCollapsed(key) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            if !collapsed {
                Divider().overlay(DatawatchColors.border)
                content()
                    .padding(12)
            }
        }
        .background(DatawatchColors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(DatawatchColors.border, lineWidth: 1))
    }

    private var header: some View {
        HStack(spacing: 6) {
            Button {
                withAnimation(.easeInOut(duration: 0.15)) { store.toggle(key) }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .rotationEffect(.degrees(collapsed ? 0 : 90))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    Text(L(title))
                        .font(DatawatchFonts.titleMedium)
                        .foregroundStyle(DatawatchColors.onSurface)
                    Spacer(minLength: 4)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(L(title)))
            .accessibilityHint(Text(collapsed ? "Expand" : "Collapse"))
            DocsLinkButton(profile: profile, anchor: observerDocsSlug(title))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
    }
}

// ── Sub-block header inside System Statistics (uppercase 11 pt label) ─────

struct ObsSubHeader: View {
    let title: String
    var live: Bool = false
    var note: String? = nil

    var body: some View {
        HStack(spacing: 8) {
            Text(L(title).uppercased())
                .font(.caption2.weight(.semibold))
                .tracking(0.5)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if let note {
                Text(note)
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.7))
            }
            if live { ObsLiveDot() }
            Spacer(minLength: 0)
        }
    }
}

/// Pulsing green "live" dot (PWA `livePulse 2s`).
struct ObsLiveDot: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var dim = false

    var body: some View {
        Circle()
            .fill(DatawatchColors.success)
            .frame(width: 7, height: 7)
            .opacity(dim ? 0.35 : 1)
            .onAppear {
                // Reduce Motion: steady dot, no pulse.
                guard !reduceMotion else { return }
                withAnimation(.easeInOut(duration: 1).repeatForever(autoreverses: true)) { dim = true }
            }
            .accessibilityHidden(true)
    }
}

struct ObsDot: View {
    let tone: String
    var size: CGFloat = 8

    var body: some View {
        Circle()
            .fill(ObsTone.color(tone))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// Bordered block separator used between System Statistics sub-blocks.
struct ObsBlock<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Divider().overlay(DatawatchColors.border)
            content()
        }
        .padding(.top, 4)
    }
}

// ── Gauge bar (PWA bar()) ─────────────────────────────────────────────────

struct ObsBarView: View {
    let bar: IosObsBar
    var barHeight: CGFloat = 5

    private var fillFraction: CGFloat {
        let f: Double = min(1.0, max(0.0, bar.fraction))
        return CGFloat(f)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Text(L(bar.label))
                    .font(.caption2)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(1)
                Spacer(minLength: 4)
                Text(bar.value)
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule().fill(DatawatchColors.background)
                    Capsule()
                        .fill(ObsTone.color(bar.tone))
                        .frame(width: geo.size.width * fillFraction)
                }
            }
            .frame(height: barHeight)
        }
        .accessibilityElement(children: .combine)
    }
}

// ── Key/value row + stat card ─────────────────────────────────────────────

struct ObsKvRow: View {
    let kv: IosObsKv

    var body: some View {
        if kv.key.isEmpty {
            Text(kv.value)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(ObsTone.color(kv.tone))
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text(L(kv.key))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(1)
                Spacer(minLength: 6)
                Text(kv.value)
                    .foregroundStyle(ObsTone.color(kv.tone))
                    .multilineTextAlignment(.trailing)
                    .textSelection(.enabled)
            }
            .font(DatawatchFonts.terminalSmall)
        }
    }
}

struct ObsLineView: View {
    let line: IosObsLine

    var body: some View {
        Text(line.text)
            .font(DatawatchFonts.labelSmall)
            .foregroundStyle(ObsTone.color(line.tone))
            .frame(maxWidth: .infinity, alignment: .leading)
            .textSelection(.enabled)
    }
}

/// PWA `.stat-card`: label + key/value lines; optional copy-command row.
struct ObsStatCard: View {
    let card: IosObsCard
    var onCopy: ((String) -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(L(card.title))
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            ForEach(Array(card.rows.enumerated()), id: \.offset) { _, row in
                ObsKvRow(kv: row)
            }
            if !card.clipboardCommand.isEmpty {
                Button {
                    onCopy?(card.clipboardCommand)
                } label: {
                    HStack(alignment: .top, spacing: 4) {
                        Text("Upgrade:")
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        Text(card.clipboardCommand)
                            .foregroundStyle(DatawatchColors.primary)
                            .multilineTextAlignment(.leading)
                        Image(systemName: "doc.on.doc")
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .font(.system(.caption2, design: .monospaced))
                    .padding(6)
                    .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Copy upgrade command")
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .background(DatawatchColors.background.opacity(0.5))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(DatawatchColors.border, lineWidth: 1))
    }
}

/// Small bordered tag (shape badge, "native", "free", attach tag).
struct ObsTag: View {
    let text: String
    var tone: String = "muted"
    var dashed: Bool = false

    var body: some View {
        Text(text)
            .font(.caption2)
            .foregroundStyle(ObsTone.color(tone))
            .padding(.horizontal, 4)
            .padding(.vertical, 1)
            .overlay(
                RoundedRectangle(cornerRadius: 3)
                    .stroke(ObsTone.color(tone).opacity(0.7), style: StrokeStyle(lineWidth: 1, dash: dashed ? [3, 2] : []))
            )
    }
}

/// Muted placeholder text ("Loading…", "none installed", …).
struct ObsMuted: View {
    let text: String

    var body: some View {
        Text(text)
            .font(DatawatchFonts.labelSmall)
            .foregroundStyle(DatawatchColors.onSurfaceMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Compact secondary button matching the PWA `.btn-secondary` 11 px buttons.
struct ObsButton: View {
    let title: String
    var tone: String = "text"
    var destructive: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(L(title))
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(destructive ? DatawatchColors.error : ObsTone.color(tone))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(
                    (destructive ? DatawatchColors.error.opacity(0.15) : DatawatchColors.surface2),
                    in: RoundedRectangle(cornerRadius: 6)
                )
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.border, lineWidth: 1))
        }
        .buttonStyle(.borderless)
    }
}

/// Lightweight toast used by Observer actions (PWA showToast).
struct ObsToast: View {
    let text: String

    var body: some View {
        Text(text)
            .font(DatawatchFonts.bodyMedium)
            .foregroundStyle(DatawatchColors.onSurface)
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(DatawatchColors.surface2, in: Capsule())
            .overlay(Capsule().stroke(DatawatchColors.border, lineWidth: 1))
            .shadow(radius: 6)
            .padding(.bottom, 16)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .accessibilityAddTraits(.isStaticText)
    }
}
