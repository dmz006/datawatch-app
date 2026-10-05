import SwiftUI
import DatawatchShared

/// Server timestamps (RFC 3339, with or without fractional seconds) → local
/// date-time, like the PWA `toLocaleString()`.
enum PrdDates {
    private static let isoFrac: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()
    private static let iso: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()
    private static let out: DateFormatter = {
        let f = DateFormatter()
        f.dateStyle = .short
        f.timeStyle = .medium
        return f
    }()

    static func display(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty, !raw.hasPrefix("0001-") else { return nil }
        if let d = isoFrac.date(from: raw) ?? iso.date(from: raw) { return out.string(from: d) }
        return raw
    }

    /// PWA last activity: `updated_at`, falling back to `created_at`.
    static func lastActivity(_ prd: PrdDto) -> String? {
        display(prd.updatedAt) ?? display(prd.createdAt)
    }
}

/// PWA card / detail meta row: `<code>id</code>` + last activity, mono,
/// right-justified (app.js automata card + `prd-detail-row2`).
struct PrdIdMetaRow: View {
    let prd: PrdDto

    var body: some View {
        HStack(spacing: 10) {
            Spacer(minLength: 0)
            Text(verbatim: prd.id)
                .lineLimit(1)
                .truncationMode(.middle)
            if let ts = PrdDates.lastActivity(prd) {
                Text(verbatim: ts)
                    .lineLimit(1)
                    .accessibilityLabel(L("Last activity") + " " + ts)
            }
        }
        .font(DatawatchFonts.terminalSmall)
        .foregroundStyle(DatawatchColors.onSurfaceMuted)
    }
}

/// PWA `prd-detail-spec-row`: first 280 chars with "… show full", the full
/// text (markdown, D53b) with "collapse"; accent2 left rule on bg2.
struct PrdSpecSnippet: View {
    let spec: String
    @State private var expanded = false

    private static let limit: Int = 280

    var body: some View {
        let isLong: Bool = spec.count > Self.limit
        HStack(spacing: 0) {
            Rectangle()
                .fill(DatawatchColors.secondary)
                .frame(width: 3)
            VStack(alignment: .leading, spacing: 4) {
                if expanded || !isLong {
                    PrdMarkdownView(source: spec)
                } else {
                    Text(verbatim: String(spec.prefix(Self.limit)) + "…")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                if isLong { toggleButton }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .background(DatawatchColors.surface)
    }

    private var toggleButton: some View {
        Button { expanded.toggle() } label: {
            Text(expanded ? "collapse" : "show full")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.primary)
        }
        .buttonStyle(.borderless)
    }
}

/// Overview meta rows the PWA (`automata_detail_*`) and Android (PrdSkillsRow,
/// PrdScopeDirsRow, PrdDepthCreatedMeta) show: depth (when > 0), skills, the
/// scope directories and the created date (PWA `_fmtDate`, "—" when unset).
struct PrdOverviewMeta: View {
    let prd: PrdDto

    var body: some View {
        let skills: [String] = prd.skills
        let write: [String] = prd.writeDirs
        let read: [String] = prd.readDirs
        let depth: Int = Int(prd.depth)
        let created: String = PrdDates.display(prd.createdAt) ?? "—"
        VStack(alignment: .leading, spacing: 6) {
            if depth > 0 { metaLine("Depth", String(depth)) }
            if !skills.isEmpty { metaLine("Skills", skills.joined(separator: ", ")) }
            if !write.isEmpty { metaLine("Writable dirs", write.joined(separator: ", ")) }
            if !read.isEmpty { metaLine("Read-only dirs", read.joined(separator: ", ")) }
            metaLine("Created", created)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: DatawatchRadius.card))
    }

    private func metaLine(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(L(label))
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            Text(verbatim: value)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
        }
    }
}

/// Story header status pill (PWA `prd-story-status-pill`; Android StoryStatusPill).
struct PrdStoryStatusPill: View {
    let status: String

    var body: some View {
        let color: Color = Self.color(status)
        Text(verbatim: status.lowercased().replacingOccurrences(of: "_", with: " "))
            .font(DatawatchFonts.badge)
            .foregroundStyle(color)
            .lineLimit(1)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .background(color.opacity(0.18), in: RoundedRectangle(cornerRadius: DatawatchRadius.sm))
            .fixedSize()
    }

    static func color(_ status: String) -> Color {
        switch status.lowercased() {
        case "complete", "completed": return DatawatchColors.waiting
        case "in_progress": return DatawatchColors.success
        case "awaiting_approval": return DatawatchColors.warning
        case "rejected", "failed": return DatawatchColors.error
        default: return DatawatchColors.onSurfaceMuted
        }
    }
}

/// PWA `prd-task-verif` row: ✓/✗ Verification (severity): summary + issues.
/// The DTO has no `ok` flag, so ok = severity not error/high/critical (Android).
struct PrdTaskVerificationRow: View {
    let verification: PrdTaskVerificationDto

    var body: some View {
        let sev: String = verification.severity ?? ""
        let ok: Bool = !["error", "high", "critical"].contains(sev.lowercased())
        let color: Color = ok ? DatawatchColors.success : DatawatchColors.warning
        VStack(alignment: .leading, spacing: 2) {
            Text(verbatim: header(ok: ok, severity: sev))
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(color)
            ForEach(verification.issues, id: \.self) { issue in
                Text(verbatim: "• " + issue)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(color.opacity(0.85))
                    .padding(.leading, 8)
            }
        }
    }

    private func header(ok: Bool, severity: String) -> String {
        var s: String = (ok ? "✓ " : "✗ ") + L("Verification")
        if !severity.isEmpty { s += " (" + severity + ")" }
        s += ":"
        if let sum = verification.summary, !sum.isEmpty { s += " " + sum }
        return s
    }
}
