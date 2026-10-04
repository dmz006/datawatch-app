import SwiftUI
import DatawatchShared

/// Security scan card for a PRD (parity B16; PWA prd_btn_run_scan /
/// prd_btn_run_rules + Scan tab, Android ScanResultCard). Shown as a card in
/// the single-scroll detail until the detail-tabs decision (D24) lands.
struct PrdScanCard: View {
    let profile: ServerProfile
    let prdId: String
    /// Called with the new PRD id after "Fix PRD" creates a child automaton.
    var onFixPrdCreated: (String) -> Void = { _ in }

    @State private var result: ScanResultDto? = nil
    @State private var loading = true
    @State private var expanded = false
    @State private var error: String? = nil
    @State private var proposedRules: String? = nil
    @State private var fixCreated = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("SECURITY SCAN")
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer()
                if loading {
                    ProgressView().controlSize(.small)
                } else {
                    Button("Run scan") { runScan() }
                        .font(DatawatchFonts.labelSmall)
                        .buttonStyle(.borderless)
                }
            }
            if let r = result {
                Button { expanded.toggle() } label: {
                    HStack(spacing: 8) {
                        verdictBadge(r.verdict)
                        (r.findings.count == 1 ? Text("\(r.findings.count) finding") : Text("\(r.findings.count) findings"))
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        if !r.findings.isEmpty {
                            Text(expanded ? "▴" : "▾").font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                        Spacer()
                    }
                }
                .buttonStyle(.plain)
                .disabled(r.findings.isEmpty)
                if !r.findings.isEmpty {
                    HStack(spacing: 14) {
                        Button("Fix PRD") { createFix() }
                        Button("Propose rules") { propose() }
                    }
                    .font(DatawatchFonts.labelSmall)
                    .buttonStyle(.borderless)
                }
                if expanded {
                    ForEach(Array(r.findings.enumerated()), id: \.offset) { _, f in
                        findingRow(f)
                    }
                }
                if let notes = r.notes, !notes.isEmpty {
                    Text(notes).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            } else if !loading {
                Text("No scan has been run for this automaton yet.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            if let error {
                Text(error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
            }
        }
        .padding(14)
        .background(DatawatchColors.surface, in: RoundedRectangle(cornerRadius: 10))
        .task(id: prdId) { load() }
        .alert("Fix PRD created", isPresented: $fixCreated) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("A child automaton was created to fix the findings. Find it in the PRD list.")
        }
        .sheet(isPresented: Binding(get: { proposedRules != nil }, set: { if !$0 { proposedRules = nil } })) {
            NavigationStack {
                ScrollView {
                    Text(proposedRules ?? "")
                        .font(DatawatchFonts.terminalSmall)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                }
                .background(DatawatchColors.background)
                .navigationTitle("Proposed rules")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Close") { proposedRules = nil }
                    }
                }
            }
        }
    }

    private func verdictBadge(_ verdict: String) -> some View {
        let v = verdict.lowercased()
        let color: Color = v == "pass" ? DatawatchColors.success : (v == "warn" ? DatawatchColors.warning : DatawatchColors.error)
        let label = v == "pass" ? "PASS" : (v == "warn" ? "WARN" : "FAIL")
        return Text(label)
            .font(DatawatchFonts.badge)
            .foregroundStyle(color)
            .padding(.horizontal, 6)
            .padding(.vertical, 1)
            .background(color.opacity(0.18), in: RoundedRectangle(cornerRadius: 4))
    }

    private func findingRow(_ f: ScanFindingDto) -> some View {
        let sev = f.severity.lowercased()
        let color: Color = sev == "error" ? DatawatchColors.error : (sev == "warning" ? DatawatchColors.warning : DatawatchColors.onSurfaceMuted)
        let location = f.file + (f.line.map { ":\($0.intValue)" } ?? "")
        return HStack(alignment: .top, spacing: 6) {
            Text(String(f.severity.prefix(4)).uppercased())
                .font(DatawatchFonts.badge)
                .foregroundStyle(color)
                .padding(.top, 1)
            VStack(alignment: .leading, spacing: 1) {
                Text(location).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                Text(f.message).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.onSurface).lineLimit(2)
            }
        }
        .padding(.leading, 8)
    }

    private func load() {
        loading = true
        IosPrdScan.shared.load(profile: profile, prdId: prdId) { r in
            DispatchQueue.main.async { result = r; loading = false }
        }
    }

    private func runScan() {
        loading = true
        error = nil
        IosPrdScan.shared.run(
            profile: profile, prdId: prdId,
            onSuccess: { r in DispatchQueue.main.async { result = r; loading = false } },
            onError: { msg in DispatchQueue.main.async { error = msg; loading = false } }
        )
    }

    private func createFix() {
        error = nil
        IosPrdScan.shared.createFixPrd(
            profile: profile, prdId: prdId,
            onSuccess: { id in DispatchQueue.main.async { fixCreated = true; onFixPrdCreated(id) } },
            onError: { msg in DispatchQueue.main.async { error = msg } }
        )
    }

    private func propose() {
        error = nil
        IosPrdScan.shared.proposeRules(
            profile: profile, prdId: prdId,
            onSuccess: { text in DispatchQueue.main.async { proposedRules = text.isEmpty ? "No rules proposed." : text } },
            onError: { msg in DispatchQueue.main.async { error = msg } }
        )
    }
}
