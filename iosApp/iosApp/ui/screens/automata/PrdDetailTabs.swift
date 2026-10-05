import SwiftUI
import DatawatchShared

/// D24a: PWA automaton detail sub-tabs (`_renderDetailTabStrip`). Graph/Progress
/// stay as cards on the Overview tab. Order = PWA tab strip (Scan before Rules).
enum PrdDetailTab: String, CaseIterable, Hashable {
    case overview = "Overview"
    case stories = "Stories"
    case decisions = "Decisions"
    case scan = "Scan"
    case rules = "Rules"
}

/// Native segmented control carrying the PWA tab content ("PWA content, iOS controls").
struct PrdDetailTabStrip: View {
    @Binding var selection: PrdDetailTab

    var body: some View {
        Picker("Automaton section", selection: $selection) {
            ForEach(PrdDetailTab.allCases, id: \.self) { t in
                Text(L(t.rawValue)).tag(t)
            }
        }
        .pickerStyle(.segmented)
    }
}

/// PWA `_renderDetailRulesTab`: AGENT.md / project-rules check. "Run Rules Check"
/// POSTs `/prds/{id}/scan/rules` (the server's rule-edit proposal) and shows the result.
struct PrdRulesCard: View {
    let profile: ServerProfile
    let prdId: String

    @State private var running = false
    @State private var result: String? = nil
    @State private var error: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("AGENT.md / project-rules check. Verifies the Automaton spec and any produced files comply with the operator-defined rules.")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            HStack(spacing: 8) {
                Button(action: run) {
                    Label("Run Rules Check", systemImage: "play.fill")
                        .font(DatawatchFonts.labelSmall)
                }
                .buttonStyle(.bordered)
                .disabled(running)
                if running { ProgressView().controlSize(.small) }
            }
            resultView
            if let error {
                Text(error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: DatawatchRadius.card))
    }

    @ViewBuilder
    private var resultView: some View {
        if let result {
            Text(result)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .textSelection(.enabled)
                .padding(8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 4))
        } else {
            Text("No rules check results yet")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.6))
        }
    }

    private func run() {
        running = true
        error = nil
        IosPrdScan.shared.proposeRules(
            profile: profile, prdId: prdId,
            onSuccess: { text in
                DispatchQueue.main.async {
                    running = false
                    result = text.isEmpty ? L("No rules proposed.") : text
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    running = false
                    error = msg
                }
            }
        )
    }
}
