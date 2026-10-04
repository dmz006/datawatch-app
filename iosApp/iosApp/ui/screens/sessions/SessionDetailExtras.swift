import SwiftUI
import UIKit

/// Rate-limit inline notice (parity D67a; Android `InlineNotices`): yellow
/// #FEF3C7 / #92400E strip, "Rate-limited · retry at <time>", dismissible.
struct RateLimitNotice: View {
    let retryAt: Date?
    var onDismiss: () -> Void

    private var label: String {
        guard let retryAt else { return L("Rate-limited") }
        let f = DateFormatter()
        f.timeStyle = .short
        f.dateStyle = .none
        return String(format: L("Rate-limited · retry at %@"), f.string(from: retryAt))
    }

    var body: some View {
        HStack(spacing: 8) {
            Text(label)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(Color(hex: 0x92400E))
                .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onDismiss) {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Color(hex: 0x92400E))
            }
            .accessibilityLabel("Dismiss")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 5)
        .background(Color(hex: 0xFEF3C7))
    }
}

/// Terminal search + copy strip (parity D69a; Android TerminalView dwSearch* /
/// dwCopySelection controller, revived as UI on all three platforms).
struct TerminalSearchBar: View {
    @ObservedObject var controller: TerminalController
    var onClose: () -> Void

    @State private var query = ""
    @State private var noMatch = false
    @State private var copiedNote: String? = nil

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(DatawatchColors.onSurfaceMuted)
            TextField("Search terminal…", text: $query)
                .font(DatawatchFonts.bodyMedium)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .onSubmit { find(forward: true) }
                .onChange(of: query) { _ in noMatch = false }
            if noMatch {
                Text("No match").font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.warning)
            } else if let copiedNote {
                Text(copiedNote).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.success)
            }
            iconButton("chevron.up", label: "Previous match") { find(forward: false) }
                .disabled(query.isEmpty)
            iconButton("chevron.down", label: "Next match") { find(forward: true) }
                .disabled(query.isEmpty)
            iconButton("doc.on.doc", label: "Copy selection or visible text") { copy() }
            iconButton("xmark", label: "Close search") {
                controller.clearSearch()
                onClose()
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(DatawatchColors.surface)
        .overlay(Divider().background(DatawatchColors.border), alignment: .bottom)
    }

    private func iconButton(_ symbol: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(DatawatchColors.onSurface)
                .frame(minWidth: 32, minHeight: 32)
                .contentShape(Rectangle())
        }
        .buttonStyle(.borderless)
        .accessibilityLabel(L(label))
    }

    private func find(forward: Bool) {
        guard !query.isEmpty else { return }
        controller.search(query, forward: forward) { found in
            DispatchQueue.main.async { noMatch = !found }
        }
    }

    private func copy() {
        controller.copyText { text in
            DispatchQueue.main.async {
                guard !text.isEmpty else { return }
                UIPasteboard.general.string = text
                copiedNote = L("Copied")
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) { copiedNote = nil }
            }
        }
    }
}

/// PWA chat memory quick-command bar (parity D68a; app.js `chat-cmd-bar`,
/// `chatQuickCmd`): each chip pre-fills the composer with a memory command.
struct ChatMemoryCmdBar: View {
    var onPick: (String) -> Void

    private static let commands: [(label: String, prefix: String)] = [
        ("📚 memories", "memories"),
        ("🔍 recall", "recall: "),
        ("🔗 kg query", "kg query "),
        ("🔬 research", "research: "),
    ]

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(Self.commands, id: \.prefix) { cmd in
                    Button { onPick(cmd.prefix) } label: {
                        Text(cmd.label)
                            .font(.system(size: 10))
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 2)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(DatawatchColors.border, lineWidth: 1))
                    }
                    .buttonStyle(.borderless)
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 6)
        }
    }
}

/// Transient top toast (Android snackbar stand-in), e.g. the D67a hooks-installed notice.
struct SessionToast: View {
    let text: String

    var body: some View {
        Text(text)
            .font(DatawatchFonts.labelSmall)
            .foregroundStyle(DatawatchColors.onSurface)
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(DatawatchColors.surface2, in: Capsule())
            .overlay(Capsule().stroke(DatawatchColors.border, lineWidth: 1))
            .padding(.top, 8)
            .transition(.move(edge: .top).combined(with: .opacity))
    }
}
