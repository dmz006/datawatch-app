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
        let ts: Date
    }

    @State private var entries: [Entry] = []
    @State private var streamingIndex: Int? = nil
    @State private var transient: String? = nil
    @State private var subscription: IosSubscription? = nil
    @State private var connected = false
    /// PWA BL82 `<details class="chat-thread">`: > 6 messages collapse all but the last 4.
    @State private var earlierExpanded = false
    private static let maxEntries = 200
    private static let collapseThreshold = 6
    private static let recentKept = 4

    private var collapsible: Bool { entries.count > Self.collapseThreshold }

    private var shownEntries: [Entry] {
        guard collapsible && !earlierExpanded else { return entries }
        return Array(entries.suffix(Self.recentKept))
    }

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
                    if collapsible { earlierHeader }
                    ForEach(shownEntries) { e in bubble(e).id(e.id) }
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

    /// PWA chat-thread-header: "💬 N earlier messages" (tap to expand / collapse).
    private var earlierHeader: some View {
        let older: Int = entries.count - Self.recentKept
        return Button {
            earlierExpanded.toggle()
        } label: {
            Text((earlierExpanded ? "▾ " : "▸ ") + "💬 " + String(format: L("%lld earlier messages"), Int64(older)))
                .font(.system(size: 11))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .buttonStyle(.borderless)
        .accessibilityHint(earlierExpanded ? "Collapse earlier messages" : "Expand earlier messages")
    }

    /// PWA `.chat-bubble` header + palette (Android ChatBubble); markdown for
    /// completed assistant messages.
    private func bubble(_ e: Entry) -> some View {
        ChatBubbleView(role: e.role, content: e.content, streaming: e.streaming, ts: e.ts)
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
        let ts = Date(timeIntervalSince1970: Double(chat.ts.toEpochMilliseconds()) / 1000.0)
        switch role {
        case "assistant":
            if chat.streaming {
                if let i = streamingIndex, entries.indices.contains(i) {
                    entries[i].content += chat.content
                } else {
                    entries.append(Entry(role: role, content: chat.content, streaming: true, ts: ts))
                    streamingIndex = entries.count - 1
                }
            } else if let i = streamingIndex, entries.indices.contains(i) {
                if !chat.content.isEmpty { entries[i].content = chat.content }
                entries[i].streaming = false
                streamingIndex = nil
            } else if !chat.content.trimmingCharacters(in: .whitespaces).isEmpty {
                entries.append(Entry(role: role, content: chat.content, streaming: false, ts: ts))
            }
            transient = nil
        case "user":
            entries.append(Entry(role: role, content: chat.content, streaming: false, ts: ts))
        default:
            let lc = chat.content.trimmingCharacters(in: .whitespaces).lowercased()
            if lc == "processing..." || lc == "thinking..." {
                transient = chat.content
            } else if lc.hasPrefix("ready") {
                transient = nil
            } else {
                entries.append(Entry(role: role, content: chat.content, streaming: false, ts: ts))
            }
        }
        if entries.count > Self.maxEntries {
            let drop = entries.count - Self.maxEntries
            entries.removeFirst(drop)
            if let i = streamingIndex { streamingIndex = i - drop >= 0 ? i - drop : nil }
        }
    }
}
