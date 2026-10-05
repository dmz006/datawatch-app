import SwiftUI
import DatawatchShared

/// Completed assistant bubble body — PWA `renderChatMarkdown`: markdown plus
/// collapsible thinking sections and inline images (shared `ChatContentSplitter`).
struct ChatAssistantContentView: View {
    let content: String

    var body: some View {
        let segments: [ChatSegment] = ChatContentSplitter.shared.split(content: content)
        VStack(alignment: .leading, spacing: 4) {
            ForEach(Array(segments.enumerated()), id: \.offset) { pair in
                ChatSegmentView(segment: pair.element)
            }
        }
    }
}

private struct ChatSegmentView: View {
    let segment: ChatSegment

    var body: some View {
        if let md = segment as? ChatMarkdownSegment {
            PrdMarkdownView(source: md.text)
                .textSelection(.enabled)
        } else if let th = segment as? ChatThinkingSegment {
            ChatThinkingBlock(text: th.text)
        } else if let img = segment as? ChatImageSegment {
            ChatInlineImage(alt: img.alt, url: img.url, loadable: img.loadable)
        }
    }
}

/// PWA `.chat-thinking` (`<details><summary>🧠 Thinking...</summary>`), closed by default.
private struct ChatThinkingBlock: View {
    let text: String
    @State private var open = false

    private static let purple: Color = Color(red: 168.0 / 255.0, green: 85.0 / 255.0, blue: 247.0 / 255.0)

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { open.toggle() } label: { summary }
                .buttonStyle(.plain)
            if open { detail }
        }
        .background(Self.purple.opacity(0.06), in: RoundedRectangle(cornerRadius: 6))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Self.purple.opacity(0.15), lineWidth: 1))
        .padding(.vertical, 6)
    }

    private var summary: some View {
        HStack(spacing: 4) {
            Image(systemName: open ? "chevron.down" : "chevron.right")
                .font(.system(size: 9, weight: .semibold))
            Text("🧠 Thinking...")
                .font(.system(size: 11))
            Spacer(minLength: 0)
        }
        .foregroundStyle(DatawatchColors.primary)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .contentShape(Rectangle())
    }

    private var detail: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle()
                .fill(Self.purple.opacity(0.10))
                .frame(height: 1)
            Text(verbatim: text)
                .font(.system(size: 11))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .textSelection(.enabled)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// PWA `.chat-image`: full-width image (radius 6) + 9 pt alt caption; hidden on
/// load error (PWA `onerror`). Only absolute http(s) URLs are fetched.
private struct ChatInlineImage: View {
    let alt: String
    let url: String
    let loadable: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if loadable, let u = URL(string: url) {
                AsyncImage(url: u) { phase in
                    if let image = phase.image {
                        image
                            .resizable()
                            .scaledToFit()
                            .frame(maxWidth: .infinity, maxHeight: 480, alignment: .leading)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                    } else {
                        EmptyView()
                    }
                }
                .padding(.vertical, 4)
            }
            if !alt.isEmpty {
                Text(verbatim: alt)
                    .font(.system(size: 9))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .padding(.vertical, 6)
    }
}
