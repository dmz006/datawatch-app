import SwiftUI

/// PWA `.chat-bubble` (style.css) / Android `ChatBubble`: header inside the
/// bubble (22 pt avatar U/AI/S · uppercase 10 pt role · 9 pt HH:MM), PWA chat
/// palette (user #3b82f6, assistant #10b981, system #64748b), radius 12 with a
/// 2 pt tail corner (system radius 8), 13 pt body. Completed assistant
/// messages render markdown, thinking sections and images (PWA `renderChatMarkdown`).
struct ChatBubbleView: View {
    let role: String
    let content: String
    let streaming: Bool
    let ts: Date

    private struct Style {
        let avatar: String
        let label: String
        let avatarBg: Color
        let roleColor: Color
        let bubbleBg: Color
        let border: Color
    }

    private static func hex(_ v: UInt32) -> Color {
        Color(
            red: Double((v >> 16) & 0xFF) / 255.0,
            green: Double((v >> 8) & 0xFF) / 255.0,
            blue: Double(v & 0xFF) / 255.0
        )
    }

    private var isUser: Bool { role == "user" }
    private var isSystem: Bool { role != "user" && role != "assistant" }

    private var style: Style {
        if isUser {
            let c = Self.hex(0x3B82F6)
            return Style(avatar: "U", label: "You", avatarBg: c, roleColor: Self.hex(0x60A5FA),
                         bubbleBg: c.opacity(0.15), border: c.opacity(0.25))
        }
        if !isSystem {
            let c = Self.hex(0x10B981)
            return Style(avatar: "AI", label: "Assistant", avatarBg: c, roleColor: Self.hex(0x34D399),
                         bubbleBg: c.opacity(0.10), border: c.opacity(0.20))
        }
        let c = Self.hex(0x94A3B8)
        return Style(avatar: "S", label: "System", avatarBg: Self.hex(0x64748B), roleColor: c,
                     bubbleBg: c.opacity(0.08), border: c.opacity(0.15))
    }

    private var shape: ChatBubbleShape {
        if isSystem { return ChatBubbleShape(radius: 8, tail: 8, tailOnRight: false) }
        return ChatBubbleShape(radius: 12, tail: 2, tailOnRight: isUser)
    }

    private static let timeFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm"
        return f
    }()

    var body: some View {
        HStack(spacing: 0) {
            if isUser { Spacer(minLength: 40) }
            bubble
            if !isUser { Spacer(minLength: isSystem ? 40 : 24) }
        }
    }

    private var bubble: some View {
        let s: Style = style
        return VStack(alignment: .leading, spacing: 4) {
            header(s)
            bodyText
        }
        .padding(.horizontal, isSystem ? 12 : 14)
        .padding(.vertical, isSystem ? 6 : 10)
        .background(s.bubbleBg, in: shape)
        .overlay(shape.stroke(s.border, lineWidth: 1))
    }

    private func header(_ s: Style) -> some View {
        HStack(spacing: 6) {
            Text(verbatim: s.avatar)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(Color.white)
                .frame(width: 22, height: 22)
                .background(s.avatarBg, in: Circle())
                .accessibilityHidden(true)
            Text(L(s.label).uppercased())
                .font(.system(size: 10, weight: .semibold))
                .kerning(0.5)
                .foregroundStyle(s.roleColor)
            if streaming {
                Text("· typing")
                    .font(.system(size: 10))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            Spacer(minLength: 8)
            Text(verbatim: Self.timeFormatter.string(from: ts))
                .font(.system(size: 9))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .opacity(0.6)
        }
    }

    @ViewBuilder
    private var bodyText: some View {
        if role == "assistant" && !streaming {
            // Markdown + collapsible thinking + inline images (ChatSegmentViews).
            ChatAssistantContentView(content: content)
        } else {
            Text(content)
                .font(.system(size: isSystem ? 12 : 13))
                .foregroundStyle(isSystem ? DatawatchColors.onSurfaceMuted : DatawatchColors.onSurface)
                .textSelection(.enabled)
        }
    }
}

/// Rounded rectangle with one small "tail" corner at the bottom (iOS 16 has
/// no UnevenRoundedRectangle).
struct ChatBubbleShape: Shape {
    let radius: Double
    let tail: Double
    let tailOnRight: Bool

    func path(in rect: CGRect) -> Path {
        let r: Double = min(radius, Double(min(rect.width, rect.height)) / 2.0)
        let br: Double = tailOnRight ? min(tail, r) : r
        let bl: Double = tailOnRight ? r : min(tail, r)
        let minX: Double = Double(rect.minX), maxX: Double = Double(rect.maxX)
        let minY: Double = Double(rect.minY), maxY: Double = Double(rect.maxY)
        var p = Path()
        p.move(to: CGPoint(x: minX + r, y: minY))
        p.addLine(to: CGPoint(x: maxX - r, y: minY))
        p.addArc(tangent1End: CGPoint(x: maxX, y: minY), tangent2End: CGPoint(x: maxX, y: minY + r), radius: r)
        p.addLine(to: CGPoint(x: maxX, y: maxY - br))
        p.addArc(tangent1End: CGPoint(x: maxX, y: maxY), tangent2End: CGPoint(x: maxX - br, y: maxY), radius: br)
        p.addLine(to: CGPoint(x: minX + bl, y: maxY))
        p.addArc(tangent1End: CGPoint(x: minX, y: maxY), tangent2End: CGPoint(x: minX, y: maxY - bl), radius: bl)
        p.addLine(to: CGPoint(x: minX, y: minY + r))
        p.addArc(tangent1End: CGPoint(x: minX, y: minY), tangent2End: CGPoint(x: minX + r, y: minY), radius: r)
        p.closeSubpath()
        return p
    }
}
