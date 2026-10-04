import SwiftUI
import DatawatchShared

/// Alert Rules (parity B34; PWA Settings → Alert Rules). Rows show on/off,
/// name, "metric op threshold → action" and description; ⏸/▶ toggles,
/// swipe deletes; the Add section mirrors the PWA inline form.
struct AlertRulesView: View {
    let profile: ServerProfile

    @State private var rules: [AlertRuleDto]? = nil
    @State private var loadError: String? = nil
    @State private var actionError: String? = nil

    // Add form (PWA defaults)
    @State private var name = ""
    @State private var description = ""
    @State private var metric = "cpu_pct"
    @State private var op = ">"
    @State private var threshold = ""
    @State private var sourceFilter = ""
    @State private var windowSeconds = "60"
    @State private var actionKind = "alert"
    @State private var cooldownSeconds = "300"
    @State private var creating = false

    private static let metrics: [(String, String)] = [
        ("cpu_pct", "CPU %"), ("mem_pct", "Memory %"), ("gpu_pct", "GPU %"),
        ("rss_bytes", "RSS bytes"), ("net_rx_bps", "Net RX bps"), ("net_tx_bps", "Net TX bps"),
    ]
    private static let operators: [(String, String)] = [
        (">", "greater than"), ("<", "less than"), (">=", "greater than or equal"), ("<=", "less than or equal"),
    ]
    private static let actions: [(String, String)] = [
        ("alert", "create system alert"), ("scale_up", "scale up"), ("scale_down", "scale down"),
    ]

    var body: some View {
        List {
            Section("Rules") {
                if let rules {
                    if rules.isEmpty {
                        Text("No alert rules configured.")
                            .font(DatawatchFonts.bodyMedium)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    ForEach(rules, id: \.name) { rule in
                        ruleRow(rule)
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    IosAlertRules.shared.delete(profile: profile, name: rule.name) { err in
                                        DispatchQueue.main.async { actionError = err; Task { await reload() } }
                                    }
                                } label: { Label("Delete", systemImage: "trash") }
                            }
                    }
                } else if let loadError {
                    Text(loadError).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error)
                } else {
                    ProgressView()
                }
            }
            .listRowBackground(DatawatchColors.surface)

            Section("Add rule") {
                TextField("Name", text: $name)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Description (optional)", text: $description)
                Picker("Metric", selection: $metric) {
                    ForEach(Self.metrics, id: \.0) { m in Text("\(m.0) — \(m.1)").tag(m.0) }
                }
                Picker("Operator", selection: $op) {
                    ForEach(Self.operators, id: \.0) { o in Text("\(o.0) — \(o.1)").tag(o.0) }
                }
                TextField("Threshold", text: $threshold)
                    .keyboardType(.decimalPad)
                TextField("Source filter (optional)", text: $sourceFilter)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Window (seconds)", text: $windowSeconds)
                    .keyboardType(.numberPad)
                Picker("Action", selection: $actionKind) {
                    ForEach(Self.actions, id: \.0) { a in Text("\(a.0) — \(a.1)").tag(a.0) }
                }
                TextField("Cooldown (seconds)", text: $cooldownSeconds)
                    .keyboardType(.numberPad)
                Button {
                    create()
                } label: {
                    HStack {
                        Text("Add rule").fontWeight(.semibold)
                        if creating { Spacer(); ProgressView() }
                    }
                }
                .disabled(creating || name.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .listRowBackground(DatawatchColors.surface)

            if let actionError {
                Section {
                    Text(actionError).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error)
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .navigationTitle("Alert Rules")
        .navigationBarTitleDisplayMode(.inline)
        .task { await reload() }
        .refreshable { await reload() }
    }

    private func ruleRow(_ rule: AlertRuleDto) -> some View {
        HStack(alignment: .center, spacing: 10) {
            Text(rule.enabled ? "on" : "off")
                .font(DatawatchFonts.badge)
                .foregroundStyle(rule.enabled ? DatawatchColors.success : DatawatchColors.error)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background((rule.enabled ? DatawatchColors.success : DatawatchColors.error).opacity(0.14), in: Capsule())
            VStack(alignment: .leading, spacing: 2) {
                Text(rule.name)
                    .font(DatawatchFonts.bodyMedium.weight(.semibold))
                    .foregroundStyle(DatawatchColors.onSurface)
                Text(IosAlertRules.shared.summary(rule: rule))
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                if let d = rule.description_, !d.isEmpty {
                    Text(d).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            Spacer(minLength: 6)
            Button {
                IosAlertRules.shared.setEnabled(profile: profile, name: rule.name, enabled: !rule.enabled) { err in
                    DispatchQueue.main.async { actionError = err; Task { await reload() } }
                }
            } label: {
                Text(rule.enabled ? "⏸" : "▶").font(.system(size: 16))
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(rule.enabled ? "Disable \(rule.name)" : "Enable \(rule.name)")
        }
    }

    private func reload() async {
        let result: Result<[AlertRuleDto], Error> = await withCheckedContinuation { cont in
            IosAlertRules.shared.list(
                profile: profile,
                onSuccess: { cont.resume(returning: .success($0)) },
                onError: { cont.resume(returning: .failure(ServiceLocatorAsync.TransportError(message: $0))) }
            )
        }
        await MainActor.run {
            switch result {
            case .success(let list): rules = list; loadError = nil
            case .failure(let e): loadError = e.localizedDescription
            }
        }
    }

    private func create() {
        creating = true
        actionError = nil
        IosAlertRules.shared.create(
            profile: profile, name: name, description: description, metric: metric, comparison: op,
            threshold: Double(threshold) ?? 0, sourceFilter: sourceFilter,
            windowSeconds: Int32(Int(windowSeconds) ?? 60), actionKind: actionKind,
            cooldownSeconds: Int32(Int(cooldownSeconds) ?? 300)
        ) { err in
            DispatchQueue.main.async {
                creating = false
                if let err { actionError = err } else {
                    name = ""; description = ""; threshold = ""; sourceFilter = ""
                    Task { await reload() }
                }
            }
        }
    }
}
