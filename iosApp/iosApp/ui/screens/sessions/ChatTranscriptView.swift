import SwiftUI
import DatawatchShared

/// Chat-mode transcript (parity B7; PWA appendChatBubble / Android ChatTranscriptPanel)
/// for sessions with `outputMode == "chat"`, which emit `chat_message` frames instead
/// of `pane_capture`. Keeps its own session socket open so replies can be sent.
struct ChatTranscriptView: View {
    let profile: ServerProfile
    let session: DwSession

    private struct Entry: Identifiable {
        let id = UUID()
        let role: String
        var content: String
        var streaming: Bool
    }

    @State private var entries: [Entry] = []
    @State private var streamingIndex: Int? = nil
    @State private var transient: String? = nil
    @State private var subscription: IosSubscription? = nil
    @State private var connected = false
    private static let maxEntries = 200

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 10) {
                    if entries.isEmpty {
                        if connected {
                            // PWA chat-empty (app.js renderChat) + memory hint (D68a).
                            VStack(spacing: 6) {
                                Text("💬").font(.system(size: 36)).opacity(0.3).accessibilityHidden(true)
                                Text("Send a message to begin the conversation")
                                    .font(.system(size: 13))
                                    .foregroundStyle(DatawatchColors.onSurface)
                                Text("Memory commands work here: remember, recall, kg, research")
                                    .font(.system(size: 11))
                                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            }
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 40)
                        } else {
                            Text("connecting…")
                                .font(DatawatchFonts.bodyMedium)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                .frame(maxWidth: .infinity)
                                .padding(.top, 40)
                        }
                    }
                    ForEach(entries) { e in bubble(e).id(e.id) }
                    if let transient {
                        Text(transient)
                            .font(DatawatchFonts.labelSmall.italic())
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .frame(maxWidth: .infinity)
                            .id("transient")
                    }
                }
                .padding(12)
            }
            .onChange(of: entries.count) { _ in
                if let last = entries.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
            }
        }
        .background(DatawatchColors.background)
        .onAppear(perform: start)
        .onDisappear { subscription?.cancel(); subscription = nil }
    }

    @ViewBuilder
    private func bubble(_ e: Entry) -> some View {
        switch e.role {
        case "user":
            HStack {
                Spacer(minLength: 40)
                Text(e.content)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .padding(10)
                    .background(DatawatchColors.primary.opacity(0.25), in: RoundedRectangle(cornerRadius: 12))
                    .textSelection(.enabled)
            }
        case "assistant":
            HStack {
                Text(markdown(e.content))
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .padding(10)
                    .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 12))
                    .overlay(alignment: .bottomTrailing) {
                        if e.streaming { ProgressView().controlSize(.mini).padding(4) }
                    }
                    .textSelection(.enabled)
                Spacer(minLength: 40)
            }
        default:
            Text(e.content)
                .font(DatawatchFonts.labelSmall.italic())
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .frame(maxWidth: .infinity)
        }
    }

    private func markdown(_ s: String) -> AttributedString {
        (try? AttributedString(markdown: s, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)))
            ?? AttributedString(s)
    }

    private func start() {
        guard subscription == nil else { return }
        subscription = IosServiceLocator.shared.subscribeSessionEvents(profile: profile, session: session) { event in
            DispatchQueue.main.async { handle(event) }
        }
    }

    private func handle(_ event: SessionEvent) {
        if event is SessionEventError { connected = false; return }
        connected = true
        guard let chat = event as? SessionEventChatMessage else { return }
        let role = IosSessionOps.shared.chatRole(event: chat)
        switch role {
        case "assistant":
            if chat.streaming {
                if let i = streamingIndex, entries.indices.contains(i) {
                    entries[i].content += chat.content
                } else {
                    entries.append(Entry(role: role, content: chat.content, streaming: true))
                    streamingIndex = entries.count - 1
                }
            } else if let i = streamingIndex, entries.indices.contains(i) {
                if !chat.content.isEmpty { entries[i].content = chat.content }
                entries[i].streaming = false
                streamingIndex = nil
            } else if !chat.content.trimmingCharacters(in: .whitespaces).isEmpty {
                entries.append(Entry(role: role, content: chat.content, streaming: false))
            }
            transient = nil
        case "user":
            entries.append(Entry(role: role, content: chat.content, streaming: false))
        default:
            let lc = chat.content.trimmingCharacters(in: .whitespaces).lowercased()
            if lc == "processing..." || lc == "thinking..." {
                transient = chat.content
            } else if lc.hasPrefix("ready") {
                transient = nil
            } else {
                entries.append(Entry(role: role, content: chat.content, streaming: false))
            }
        }
        if entries.count > Self.maxEntries {
            let drop = entries.count - Self.maxEntries
            entries.removeFirst(drop)
            if let i = streamingIndex { streamingIndex = i - drop >= 0 ? i - drop : nil }
        }
    }
}
