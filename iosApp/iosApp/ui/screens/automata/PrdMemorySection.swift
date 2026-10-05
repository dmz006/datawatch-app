import SwiftUI
import DatawatchShared

/// PRD memory UI (parity D77a; Android PrdDetailDialog BL385–387):
/// - Memory stats tile — PRD-shared / Story-shared / Session counts (only when > 0);
/// - Learning Report — fetched on demand for completed / archived PRDs;
/// - Memory Recall — scoped search over this PRD's memories.
struct PrdMemorySection: View {
    let profile: ServerProfile
    let prd: PrdDto

    @State private var report: String? = nil
    @State private var reportLoading = false
    @State private var reportExpanded = false
    @State private var reportMissing = false
    @State private var query = ""
    @State private var hits: [IosMemoryHit] = []
    @State private var searched = false
    @State private var recallLoading = false
    @State private var recallError: String? = nil

    private var counts: [(String, Int)] {
        var out: [(String, Int)] = []
        let prdShared: Int = prd.prdSharedCount.map { Int(truncating: $0) } ?? 0
        let storyShared: Int = prd.storySharedCount.map { Int(truncating: $0) } ?? 0
        let sessionLocal: Int = prd.sessionLocalCount.map { Int(truncating: $0) } ?? 0
        if prdShared > 0 { out.append(("Automaton-shared", prdShared)) }
        if storyShared > 0 { out.append(("Story-shared", storyShared)) }
        if sessionLocal > 0 { out.append(("Session", sessionLocal)) }
        return out
    }

    private var terminal: Bool {
        ["completed", "complete", "done", "archived"].contains(prd.status.lowercased())
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !counts.isEmpty { statsTile }
            if terminal { reportSection }
            recallCard
        }
        .padding(14)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 10))
    }

    // ── BL387 stats tile ───────────────────────────────────────────────────

    private var statsTile: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Memory")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            HStack(spacing: 16) {
                ForEach(counts, id: \.0) { c in
                    VStack(spacing: 1) {
                        Text("\(c.1)")
                            .font(DatawatchFonts.titleMedium)
                            .foregroundStyle(DatawatchColors.primary)
                        Text(L(c.0))
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 6))
    }

    // ── BL386 learning report ──────────────────────────────────────────────

    private var reportSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Button {
                if report != nil { reportExpanded.toggle() } else { fetchReport() }
            } label: {
                HStack {
                    Text("Learning Report")
                        .font(DatawatchFonts.labelSmall.weight(.semibold))
                        .foregroundStyle(DatawatchColors.primary)
                    Spacer()
                    if reportLoading {
                        ProgressView().controlSize(.small)
                    } else {
                        Text(reportExpanded ? L("Hide") : L("Show full report"))
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                }
            }
            .buttonStyle(.borderless)
            if reportMissing {
                Text("No report available yet.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            if reportExpanded, let report {
                Text(report)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(8)
                    .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 6))
            }
        }
    }

    private func fetchReport() {
        reportLoading = true
        reportMissing = false
        IosExtras.shared.memoryReport(profile: profile, prdId: prd.id) { text in
            DispatchQueue.main.async {
                reportLoading = false
                report = text
                reportMissing = text == nil
                reportExpanded = text != nil
            }
        }
    }

    // ── BL385 recall card ──────────────────────────────────────────────────

    private var recallCard: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Memory Recall")
                .font(DatawatchFonts.labelSmall.weight(.semibold))
                .foregroundStyle(DatawatchColors.onSurface)
            HStack(spacing: 8) {
                TextField("Search automaton memories…", text: $query)
                    .font(DatawatchFonts.bodyMedium)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.search)
                    .onSubmit(search)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 6))
                if recallLoading {
                    ProgressView().controlSize(.small)
                } else {
                    Button("Search", action: search)
                        .font(DatawatchFonts.labelSmall)
                        .buttonStyle(.borderless)
                        .disabled(query.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            if let recallError {
                Text(recallError).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
            } else if searched && !recallLoading && hits.isEmpty {
                Text("No results").font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            ForEach(Array(hits.enumerated()), id: \.offset) { _, hit in
                PrdMemoryHitRow(hit: hit)
            }
        }
    }

    private func search() {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return }
        recallLoading = true
        recallError = nil
        IosExtras.shared.recall(
            profile: profile, prdId: prd.id, projectDir: prd.projectDir ?? "", query: q,
            onSuccess: { list in
                DispatchQueue.main.async {
                    recallLoading = false
                    searched = true
                    hits = list
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    recallLoading = false
                    searched = true
                    hits = []
                    recallError = msg
                }
            }
        )
    }
}

private struct PrdMemoryHitRow: View {
    let hit: IosMemoryHit

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                if !hit.scope.isEmpty {
                    Text(hit.scope)
                        .font(DatawatchFonts.badge)
                        .foregroundStyle(DatawatchColors.primary)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(DatawatchColors.primary.opacity(0.16), in: RoundedRectangle(cornerRadius: 4))
                }
                if !hit.role.isEmpty {
                    Text(hit.role).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                if !hit.score.isEmpty {
                    Text(hit.score).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.6))
                }
            }
            Text(hit.text)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .lineLimit(4)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(DatawatchColors.surface2.opacity(0.6), in: RoundedRectangle(cornerRadius: 6))
    }
}
