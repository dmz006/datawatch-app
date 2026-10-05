import SwiftUI
import DatawatchShared

/// Guardrail verdicts card body for the session Status tab (parity 03 › Guardrail
/// verdicts card, 08 › Guardrail verdicts inline). PWA `renderSessionGuardrailVerdicts`
/// + `approveGuardrailVerdict`, Android `GuardrailVerdictsCard`: one row per verdict,
/// "approve" on a blocked verdict (✓ once approved), then "Run guardrail" with the
/// built-in sast / secrets / deps scans. Results go to the alert dock.
struct GuardrailVerdictsBody: View {
    let profile: ServerProfile
    let session: DwSession
    let verdicts: [GuardrailVerdictDto]
    /// Called after a guardrail run finishes so the parent re-polls telemetry.
    var onChanged: () -> Void = {}

    @State private var approved: Set<String> = []
    @State private var approving: String? = nil
    @State private var running: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if verdicts.isEmpty {
                Text("No verdicts yet.")
                    .font(DatawatchFonts.labelSmall.italic())
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            ForEach(Array(verdicts.enumerated()), id: \.offset) { _, v in
                GuardrailVerdictRow(
                    verdict: v,
                    approved: approved.contains(v.guardrail),
                    busy: approving == v.guardrail,
                    onApprove: { approve(v.guardrail) }
                )
            }
            Text("Run guardrail")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .padding(.top, 4)
            runChips
        }
    }

    private var runChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(IosGuardrails.shared.builtins, id: \.self) { name in
                    GuardrailRunChip(name: name, running: running == name, disabled: running != nil) {
                        run(name)
                    }
                }
            }
        }
    }

    private func approve(_ name: String) {
        guard approving == nil else { return }
        approving = name
        IosGuardrails.shared.approve(
            profile: profile, sessionId: session.id, guardrail: name,
            onSuccess: { unblocked in
                let freed: Bool = unblocked.boolValue
                DispatchQueue.main.async {
                    approving = nil
                    approved.insert(name)
                    let suffix: String = freed ? L("approved — session unblocked") : L("approved")
                    AlertDock.shared.post(name + ": " + suffix, level: .success)
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    approving = nil
                    AlertDock.shared.post(L("approve failed") + ": " + msg, level: .error)
                }
            }
        )
    }

    private func run(_ name: String) {
        guard running == nil else { return }
        running = name
        IosGuardrails.shared.run(
            profile: profile, sessionId: session.id, guardrail: name,
            onSuccess: { r in
                let outcome: String = r.outcome
                let summary: String = r.summary
                DispatchQueue.main.async {
                    running = nil
                    var line: String = name + ": " + outcome
                    if !summary.isEmpty { line += " — " + String(summary.prefix(60)) }
                    let level: DockLevel = outcome == "pass" ? .success : (outcome == "warn" ? .warning : .error)
                    AlertDock.shared.post(line, level: level)
                    onChanged()
                }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    running = nil
                    AlertDock.shared.post(L("Guardrail error") + ": " + msg, level: .error)
                    onChanged()
                }
            }
        )
    }
}

/// One verdict: OUTCOME · name, optional summary, and approve / ✓ on a block.
private struct GuardrailVerdictRow: View {
    let verdict: GuardrailVerdictDto
    let approved: Bool
    let busy: Bool
    let onApprove: () -> Void

    private var color: Color {
        switch verdict.outcome {
        case "pass": return DatawatchColors.success
        case "block": return DatawatchColors.error
        default: return DatawatchColors.warning
        }
    }

    var body: some View {
        HStack(alignment: .center, spacing: 6) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(verdict.outcome.uppercased())
                        .font(DatawatchFonts.badge)
                        .foregroundStyle(color)
                    Text(verdict.guardrail)
                        .font(DatawatchFonts.labelSmall.bold())
                        .foregroundStyle(DatawatchColors.onSurface)
                }
                if !verdict.summary.isEmpty {
                    Text(verdict.summary)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            Spacer(minLength: 4)
            if verdict.outcome == "block" { approveControl }
        }
    }

    @ViewBuilder
    private var approveControl: some View {
        if approved {
            Text("✓")
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityLabel("Approved")
        } else if busy {
            ProgressView().controlSize(.mini)
        } else {
            Button(action: onApprove) {
                Text("approve")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.error)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .overlay(RoundedRectangle(cornerRadius: 4).stroke(DatawatchColors.error, lineWidth: 1))
            }
            .buttonStyle(.borderless)
            .accessibilityHint("Approve this blocked verdict")
        }
    }
}

/// "▶ sast-scan" chip; shows "… name" while that guardrail runs.
private struct GuardrailRunChip: View {
    let name: String
    let running: Bool
    let disabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text((running ? "… " : "▶ ") + name)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurface)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(DatawatchColors.chipBackground, in: Capsule())
        }
        .buttonStyle(.borderless)
        .disabled(disabled)
        .opacity(disabled && !running ? 0.5 : 1.0)
    }
}
