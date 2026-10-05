import SwiftUI
import DatawatchShared

/// What the council sheet opens on: a just-started run (live SSE watch) or a
/// past run (replay from GET /api/council/runs/{id}).
struct CouncilSheetTarget: Identifiable {
    let id: String
    let initial: IosCouncilLiveRun?
    let live: Bool
}

// PWA councilOpenLiveWatch palette.
private enum CouncilPalette {
    static let green = Color(red: 0.133, green: 0.773, blue: 0.369)
    static let accent = Color(red: 0.388, green: 0.400, blue: 0.945)
    static let amber = Color(red: 0.961, green: 0.620, blue: 0.043)
    static let purple = Color(red: 0.659, green: 0.333, blue: 0.969)
    static let red = Color(red: 0.937, green: 0.267, blue: 0.267)
}

/// Live council run (PWA `councilOpenLiveWatch`) / past-run replay
/// (PWA `councilViewRun`): rounds, persona replies streaming in, synthesis,
/// consensus + dissent.
struct CouncilLiveRunSheet: View {
    let profile: ServerProfile
    let target: CouncilSheetTarget
    let onFinished: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var run: IosCouncilLiveRun?
    @State private var sub: IosCouncilSubscription?
    @State private var notified = false
    @State private var confirmCancel = false
    @State private var loadError: String?

    var body: some View {
        NavigationStack {
            content
                .background(DatawatchColors.background)
                .navigationTitle("Council run")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { toolbarContent }
                .alert(Text(verbatim: cancelPrompt), isPresented: $confirmCancel) {
                    Button("Cancel run", role: .destructive) { cancelRun() }
                    Button("Close", role: .cancel) {}
                }
        }
        .onAppear(perform: begin)
        .onDisappear { sub?.cancel() }
    }

    private var cancelPrompt: String {
        L("Cancel council run") + " " + String(target.id.prefix(8)) + "?"
    }

    private var doneCount: Int {
        guard let run else { return 0 }
        var n: Int = 0
        for r in run.rounds {
            n += r.replies.filter { $0.status == "done" || $0.status == "error" }.count
        }
        return n
    }

    @ViewBuilder
    private var content: some View {
        if let run {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        CouncilRunHeader(run: run)
                        CouncilRunBody(run: run)
                        Color.clear.frame(height: 1).id("bottom")
                    }
                    .padding(16)
                }
                .onChange(of: doneCount) { _ in
                    if target.live && !run.terminal {
                        withAnimation { proxy.scrollTo("bottom", anchor: .bottom) }
                    }
                }
            }
        } else if let loadError {
            Text(verbatim: loadError)
                .foregroundStyle(DatawatchColors.error)
                .padding()
        } else {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .cancellationAction) {
            Button("Close") { dismiss() }
        }
        ToolbarItem(placement: .primaryAction) {
            if target.live, let run, !run.terminal, run.phase != "connecting" {
                Button("Cancel run", role: .destructive) { confirmCancel = true }
            }
        }
    }

    private func begin() {
        if target.live, let initial = target.initial {
            run = initial
            if sub != nil { return }
            sub = IosCouncilRuns.shared.watch(profile: profile, run: initial) { next in
                DispatchQueue.main.async {
                    run = next
                    if next.terminal && !notified {
                        notified = true
                        onFinished()
                    }
                }
            }
        } else {
            IosCouncilRuns.shared.open(profile: profile, runId: target.id, onSuccess: { loaded in
                DispatchQueue.main.async { run = loaded }
            }, onError: { msg in
                DispatchQueue.main.async { loadError = msg }
            })
        }
    }

    private func cancelRun() {
        IosCouncilRuns.shared.cancel(profile: profile, runId: target.id) { err in
            DispatchQueue.main.async {
                if let err { loadError = err }
            }
        }
    }
}

/// Status chip · short id · mode · persona count.
private struct CouncilRunHeader: View {
    let run: IosCouncilLiveRun

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                CouncilPhaseChip(phase: run.phase)
                Text(verbatim: run.shortId)
                    .font(DatawatchFonts.terminalSmall)
                    .bold()
                    .foregroundStyle(DatawatchColors.onSurface)
                if !run.mode.isEmpty {
                    Text(verbatim: run.mode)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                if !run.personas.isEmpty {
                    Text(verbatim: "\(run.personas.count) " + L("personas"))
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            if !run.proposal.isEmpty {
                Text(verbatim: run.proposal)
                    .font(DatawatchFonts.bodyMedium)
                    .fontWeight(.semibold)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .textSelection(.enabled)
            }
            Divider()
        }
    }
}

struct CouncilPhaseChip: View {
    let phase: String

    private var color: Color {
        switch phase {
        case "running": return CouncilPalette.green
        case "synthesizing": return CouncilPalette.purple
        case "completed": return CouncilPalette.accent
        case "cancelled", "error": return CouncilPalette.red
        default: return DatawatchColors.onSurfaceMuted
        }
    }

    var body: some View {
        Text(L(phase))
            .font(DatawatchFonts.badge)
            .foregroundStyle(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .background(color.opacity(0.2), in: RoundedRectangle(cornerRadius: DatawatchRadius.sm))
    }
}

/// Rounds + footer (synthesizing / consensus / dissent / lost connection).
private struct CouncilRunBody: View {
    let run: IosCouncilLiveRun

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(run.rounds, id: \.index) { round in
                CouncilRoundView(round: round, total: Int(run.roundsTotal))
            }
            if run.rounds.isEmpty && !run.terminal {
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text("Running council…")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            if run.phase == "synthesizing" {
                HStack(spacing: 8) {
                    ProgressView().controlSize(.small)
                    Text("◆ Synthesizing…")
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(CouncilPalette.purple)
                }
            }
            CouncilVerdictView(consensus: run.consensus, dissent: run.dissent)
            if run.phase == "error" {
                Text("⚠ Lost connection to the council run.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(CouncilPalette.red)
            }
        }
    }
}

private struct CouncilRoundView: View {
    let round: IosCouncilRound
    let total: Int

    private var title: String {
        let idx: Int = Int(round.index)
        if total > 0 {
            return L("Round") + " \(idx) / \(max(total, idx))"
        }
        return L("Round") + " \(idx)"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(verbatim: title)
                .font(DatawatchFonts.labelSmall)
                .fontWeight(.semibold)
                .foregroundStyle(CouncilPalette.accent)
            ForEach(round.replies, id: \.persona) { reply in
                CouncilReplyView(reply: reply)
            }
        }
    }
}

private struct CouncilReplyView: View {
    let reply: IosCouncilReply

    private var marker: String {
        switch reply.status {
        case "done": return "✓"
        case "error": return "✗"
        case "responding": return "⏳"
        default: return "·"
        }
    }

    private var tint: Color {
        switch reply.status {
        case "done": return CouncilPalette.green
        case "error": return CouncilPalette.red
        case "responding": return CouncilPalette.amber
        default: return DatawatchColors.onSurfaceMuted
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                Text(verbatim: marker).foregroundStyle(tint)
                Text(verbatim: reply.persona)
                    .fontWeight(.semibold)
                    .foregroundStyle(tint)
                statusLabel
            }
            .font(DatawatchFonts.labelSmall)
            if !reply.text.isEmpty {
                Text(verbatim: reply.text)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(reply.status == "error" ? CouncilPalette.red : DatawatchColors.onSurface)
                    .textSelection(.enabled)
                    .padding(.leading, 18)
            }
        }
    }

    @ViewBuilder
    private var statusLabel: some View {
        if reply.status == "responding" {
            ProgressView().controlSize(.mini)
            Text("responding…").foregroundStyle(CouncilPalette.amber)
        } else if reply.status == "waiting" {
            Text("waiting").foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }
}

private struct CouncilVerdictView: View {
    let consensus: String
    let dissent: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !consensus.isEmpty {
                Divider()
                Text("Consensus")
                    .font(DatawatchFonts.labelSmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(CouncilPalette.green)
                Text(verbatim: consensus)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .textSelection(.enabled)
            }
            if !dissent.isEmpty {
                Text("Dissent")
                    .font(DatawatchFonts.labelSmall)
                    .fontWeight(.semibold)
                    .foregroundStyle(CouncilPalette.amber)
                    .padding(.top, 4)
                Text(verbatim: dissent)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .textSelection(.enabled)
            }
        }
    }
}
