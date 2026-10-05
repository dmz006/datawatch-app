import SwiftUI
import UIKit
import DatawatchShared

// MARK: - Session mode (PWA getSessionMode)

enum SessionMode {
    static func of(_ s: DwSession) -> String {
        let b = (s.backend ?? "").lowercased()
        if b == "opencode-acp" { return "acp" }
        if b == "claude" || b == "claude-code" { return "channel" }
        return "tmux"
    }
}

// MARK: - Connection banner (PWA connBanner, channel / acp sessions)

/// "Waiting for MCP channel… — answer the input prompt below first ✕", shown
/// until the server reports `channel_ready`. ✕ = use tmux only (PWA
/// `dismissConnBanner`).
struct ChannelConnectionBanner: View {
    let mode: String
    let waiting: Bool
    var onDismiss: () -> Void

    private var label: String {
        let target: String = mode == "acp" ? L("ACP server") : L("MCP channel")
        return String(format: L("Waiting for %@…"), target)
    }

    var body: some View {
        HStack(spacing: 8) {
            ProgressView().controlSize(.mini).tint(DatawatchColors.warning)
            VStack(alignment: .leading, spacing: 1) {
                Text(label)
                    .foregroundStyle(DatawatchColors.onSurface)
                if waiting {
                    Text("— answer the input prompt below first")
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .font(DatawatchFonts.labelSmall)
            Spacer(minLength: 4)
            Button(action: onDismiss) {
                Text("✕").font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Dismiss — use tmux only")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(DatawatchColors.warning.opacity(0.10))
    }
}

// MARK: - Inline process-stats bar (D45a; PWA statsPanel, 5 s poll)

struct InlineProcessStatsBar: View {
    let profile: ServerProfile
    let session: DwSession

    @State private var env: StatEnvelopeDto? = nil

    var body: some View {
        Group {
            if let env {
                ScrollView(.horizontal, showsIndicators: false) {
                    statsRow(env)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 4)
                }
                .background(DatawatchColors.surface)
                .overlay(Divider().background(DatawatchColors.border), alignment: .bottom)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Process stats")
            }
        }
        .task(id: session.id) { await poll() }
    }

    private func statsRow(_ e: StatEnvelopeDto) -> some View {
        let net: String = "↓" + Self.bytes(e.netRxBps) + "/s ↑" + Self.bytes(e.netTxBps) + "/s"
        return HStack(spacing: 12) {
            stat("CPU", String(format: "%.0f%%", e.cpuPct))
            stat("RAM", Self.bytes(e.rssBytes))
            stat("Threads", "\(e.threads)")
            stat("FDs", "\(e.fds)")
            stat("Net", net)
            if e.gpuPct > 0 { stat("GPU", String(format: "%.0f%%", e.gpuPct)) }
        }
    }

    private func stat(_ label: String, _ value: String) -> some View {
        HStack(spacing: 3) {
            Text(L(label)).foregroundStyle(DatawatchColors.onSurfaceMuted)
            Text(value).foregroundStyle(DatawatchColors.onSurface)
        }
        .font(DatawatchFonts.terminalSmall)
    }

    private func poll() async {
        while !Task.isCancelled {
            let snap: IosSessionStatsSnapshot = await withCheckedContinuation { cont in
                IosSessionStats.shared.load(profile: profile, session: session) { cont.resume(returning: $0) }
            }
            env = snap.envelope
            try? await Task.sleep(nanoseconds: 5_000_000_000)
        }
    }

    static func bytes(_ n: Int64) -> String {
        let v: Double = Double(n)
        if v >= 1_073_741_824 { return String(format: "%.1fG", v / 1_073_741_824) }
        if v >= 1_048_576 { return String(format: "%.0fM", v / 1_048_576) }
        if v >= 1024 { return String(format: "%.0fK", v / 1024) }
        return "\(n)B"
    }
}

// MARK: - Response viewer (D43a: fresh fetch + 🤖 Summary)

/// PWA `showResponseViewer`: the cached response shows with "(updating…)" while
/// GET /api/sessions/response fetches the fresh one; inline markdown, 📋 copy.
/// 🤖 Summary asks the server summarizer (POST /summarize) and shows the result.
struct SessionResponseSheet: View {
    let profile: ServerProfile
    let session: DwSession
    var onDismiss: () -> Void

    @State private var text: String? = nil
    @State private var updating = true
    @State private var summary: String? = nil
    @State private var summarizing = false

    var body: some View {
        NavigationStack {
            ScrollView { content.padding() }
                .background(DatawatchColors.background)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { toolbar }
        }
        .dwThemed()
        .onAppear(perform: load)
    }

    private var content: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let summary { summaryCard(summary) }
            if let text, !text.isEmpty {
                Text(Self.markdown(text))
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else if !updating {
                Text("(no response captured)")
                    .font(DatawatchFonts.bodyMedium)
                    .italic()
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            HStack(spacing: 6) {
                Text("Last Response").font(DatawatchFonts.titleMedium)
                if updating {
                    Text("(updating…)")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
        }
        ToolbarItemGroup(placement: .navigationBarTrailing) {
            Button(action: summarize) { Text(summarizing ? "⏳" : "🤖") }
                .disabled(summarizing)
                .accessibilityLabel(summarizing ? "Summarizing…" : "Summary")
            Button(action: copy) { Text("📋") }
                .disabled((text ?? "").isEmpty)
                .accessibilityLabel("Copy to clipboard")
            Button(action: onDismiss) { Text("✕") }
                .accessibilityLabel("Close")
        }
    }

    private func summaryCard(_ s: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("🤖 Summary")
                .font(DatawatchFonts.labelSmall.weight(.bold))
                .foregroundStyle(DatawatchColors.secondary)
            Text(s)
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurface)
                .textSelection(.enabled)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 8))
    }

    private func copy() {
        UIPasteboard.general.string = text ?? ""
        AlertDock.shared.post(L("Copied to clipboard"), level: .success)
    }

    private func load() {
        if let cached = session.lastResponse, !cached.isEmpty { text = cached }
        updating = true
        IosSessionComposer.shared.fetchResponse(profile: profile, sessionId: session.fullId) { fresh, err in
            DispatchQueue.main.async {
                updating = false
                if let fresh {
                    text = fresh
                } else if text == nil {
                    text = "(" + L("failed to load response") + ")"
                    if let err { AlertDock.shared.post(err, level: .error) }
                }
            }
        }
    }

    private func summarize() {
        summarizing = true
        IosServiceLocator.shared.resummarizeSession(
            sessionId: session.id,
            profile: profile,
            onSuccess: { s in
                DispatchQueue.main.async {
                    summarizing = false
                    summary = s.isEmpty ? L("(no summary)") : s
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    summarizing = false
                    AlertDock.shared.post(msg, level: .error)
                }
            }
        )
    }

    static func markdown(_ s: String) -> AttributedString {
        let opts = AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        return (try? AttributedString(markdown: s, options: opts)) ?? AttributedString(s)
    }
}
