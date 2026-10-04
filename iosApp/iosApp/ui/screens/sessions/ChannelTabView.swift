import SwiftUI
import DatawatchShared

/// Session Channel tab (parity B7; PWA channelReplies / .channel-*-line).
/// Seeds from /api/channel/history, then appends live channel_reply /
/// channel_notify frames; de-duplicated on ts|text, capped at 1000 lines.
/// Lines: → sent (blue), ← reply (amber), ⚡ notify (purple).
struct ChannelTabView: View {
    let profile: ServerProfile
    let session: DwSession

    @State private var lines: [ChannelMessage] = []
    @State private var seen: Set<String> = []
    @State private var sub: IosSubscription? = nil

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    if lines.isEmpty {
                        Text("No channel messages yet.")
                            .font(DatawatchFonts.bodyMedium)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            .padding(16)
                    }
                    ForEach(Array(lines.enumerated()), id: \.offset) { i, m in
                        line(m).id(i)
                    }
                }
                .padding(.horizontal, 8)
            }
            .onChange(of: lines.count) { n in
                if n > 0 { withAnimation { proxy.scrollTo(n - 1, anchor: .bottom) } }
            }
        }
        .background(DatawatchColors.background)
        .onAppear(perform: start)
        .onDisappear { sub?.cancel(); sub = nil }
    }

    private func line(_ m: ChannelMessage) -> some View {
        let (prefix, color): (String, Color) = {
            switch m.direction {
            case "outgoing": return ("→ ", Color(hex: 0x3B82F6))
            case "notify": return ("⚡ ", Color(hex: 0xA855F7))
            default: return ("← ", Color(hex: 0xF59E0B))
            }
        }()
        return Text(prefix + m.text)
            .font(.system(size: 13))
            .foregroundStyle(DatawatchColors.onSurface)
            .textSelection(.enabled)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(color.opacity(0.08))
            .overlay(alignment: .leading) { Rectangle().fill(color).frame(width: 3) }
            .padding(.vertical, 4)
    }

    private func start() {
        guard sub == nil else { return }
        sub = IosChannel.shared.subscribe(sessionIds: [session.fullId, session.id]) { m in
            DispatchQueue.main.async { add([m]) }
        }
        IosChannel.shared.history(profile: profile, sessionId: session.fullId) { msgs in
            DispatchQueue.main.async { add(msgs) }
        }
    }

    private func add(_ msgs: [ChannelMessage]) {
        var merged = lines
        for m in msgs {
            let key = m.ts + "|" + m.text
            if seen.contains(key) { continue }
            seen.insert(key)
            merged.append(m)
        }
        merged.sort { $0.ts < $1.ts }
        lines = Array(merged.suffix(1000))
    }
}
