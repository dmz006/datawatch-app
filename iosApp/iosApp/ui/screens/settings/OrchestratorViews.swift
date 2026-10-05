import SwiftUI
import DatawatchShared

/// PWA status-dot palette for graphs / pipelines.
private func orchestratorStatusColor(_ status: String) -> Color {
    switch status {
    case "running": return Color(hex: 0x6366F1)
    case "done", "completed": return DatawatchColors.success
    case "failed": return DatawatchColors.error
    case "cancelled": return DatawatchColors.warning
    default: return DatawatchColors.onSurfaceMuted
    }
}

/// "● live" marker (PWA live-dot: auto-refreshing every 8 s).
private struct LiveDot: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var pulse = false
    var body: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(DatawatchColors.success)
                .frame(width: 7, height: 7)
                .opacity(pulse ? 0.35 : 1)
                .animation(.easeInOut(duration: 1).repeatForever(autoreverses: true), value: pulse)
            Text("live").font(.system(size: 10)).foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
        .onAppear { pulse = !reduceMotion }
        .accessibilityLabel("Auto-refreshing every 8 seconds")
    }
}

/// Automata Orchestrator (parity B19; PWA Settings → Automata →
/// loadOrchestratorPanel). New-graph form (title + project dir), live list
/// with ▶ run and × cancel/delete.
struct OrchestratorGraphsView: View {
    let profile: ServerProfile

    @State private var graphs: [OrchestratorGraphListItemDto]? = nil
    @State private var loadError: String? = nil
    @State private var actionError: String? = nil
    @State private var title = ""
    @State private var projectDir = ""
    @State private var creating = false
    @State private var confirmDelete: OrchestratorGraphListItemDto? = nil

    var body: some View {
        List {
            Section("New Automaton graph") {
                TextField("Title (required)", text: $title)
                TextField("Project directory (optional)", text: $projectDir)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button {
                    creating = true
                    actionError = nil
                    IosOrchestrator.shared.createGraph(profile: profile, title: title, projectDir: projectDir) { err in
                        DispatchQueue.main.async {
                            creating = false
                            if let err { actionError = err } else { title = ""; projectDir = ""; Task { await reload() } }
                        }
                    }
                } label: {
                    HStack { Text("Create").fontWeight(.semibold); if creating { Spacer(); ProgressView() } }
                }
                .disabled(creating || title.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .listRowBackground(DatawatchColors.surface)

            Section {
                if let graphs {
                    if graphs.isEmpty {
                        Text("no graphs — create one above")
                            .font(DatawatchFonts.bodyMedium)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    ForEach(graphs, id: \.id) { g in graphRow(g) }
                } else if let loadError {
                    Text(loadError).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error)
                } else {
                    ProgressView()
                }
            } header: {
                HStack(spacing: 8) {
                    Text("Graphs (\(graphs?.count ?? 0))")
                    LiveDot()
                }
            }
            .listRowBackground(DatawatchColors.surface)

            if let actionError {
                Section { Text(actionError).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error) }
            }
        }
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .navigationTitle("Automata Orchestrator")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            while !Task.isCancelled {
                await reload()
                try? await Task.sleep(nanoseconds: 8_000_000_000)
            }
        }
        .refreshable { await reload() }
        .alert("Cancel/delete this graph?", isPresented: Binding(
            get: { confirmDelete != nil }, set: { if !$0 { confirmDelete = nil } }
        )) {
            Button("Delete", role: .destructive) {
                guard let g = confirmDelete else { return }
                IosOrchestrator.shared.deleteGraph(profile: profile, id: g.id) { err in
                    DispatchQueue.main.async { actionError = err; Task { await reload() } }
                }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func graphRow(_ g: OrchestratorGraphListItemDto) -> some View {
        let status = g.status.isEmpty ? "pending" : g.status
        let count = g.prdIds.count
        return HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Circle().fill(orchestratorStatusColor(status)).frame(width: 8, height: 8)
                    Text(g.title.isEmpty ? (g.id.isEmpty ? "untitled" : g.id) : g.title)
                        .font(DatawatchFonts.bodyMedium.weight(.semibold))
                        .foregroundStyle(DatawatchColors.onSurface)
                    Text(status).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                if count > 0 {
                    (count == 1 ? Text("\(count) automaton") : Text("\(count) automata"))
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                Text(g.id).font(.system(size: 10, design: .monospaced)).foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.8))
            }
            Spacer(minLength: 4)
            Button("▶") {
                IosOrchestrator.shared.runGraph(profile: profile, id: g.id) { err in
                    DispatchQueue.main.async { actionError = err; Task { await reload() } }
                }
            }
            .buttonStyle(.borderless)
            .accessibilityLabel("Run \(g.title)")
            Button("×") { confirmDelete = g }
                .buttonStyle(.borderless)
                .foregroundStyle(DatawatchColors.error)
                .accessibilityLabel("Cancel \(g.title)")
        }
    }

    private func reload() async {
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            IosOrchestrator.shared.graphs(
                profile: profile,
                onSuccess: { list in DispatchQueue.main.async { graphs = list; loadError = nil; cont.resume() } },
                onError: { msg in DispatchQueue.main.async { if graphs == nil { loadError = msg }; cont.resume() } }
            )
        }
    }
}

/// Pipeline manager (parity B19; PWA loadPipelinesPanel). Live list, Cancel
/// on running/pending pipelines.
struct PipelinesView: View {
    let profile: ServerProfile

    @State private var pipelines: [PipelineListItemDto]? = nil
    @State private var loadError: String? = nil
    @State private var actionError: String? = nil

    var body: some View {
        List {
            Section {
                if let pipelines {
                    if pipelines.isEmpty {
                        Text("No pipelines — start one via REST or CLI.")
                            .font(DatawatchFonts.bodyMedium)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    ForEach(pipelines, id: \.id) { p in row(p) }
                } else if let loadError {
                    Text(loadError).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error)
                } else {
                    ProgressView()
                }
            } header: {
                HStack(spacing: 8) {
                    let n = pipelines?.count ?? 0
                    (n == 1 ? Text("\(n) pipeline") : Text("\(n) pipelines"))
                    LiveDot()
                }
            }
            .listRowBackground(DatawatchColors.surface)
            if let actionError {
                Section { Text(actionError).font(DatawatchFonts.bodyMedium).foregroundStyle(DatawatchColors.error) }
            }
        }
        .scrollContentBackground(.hidden)
        .background(DatawatchColors.background)
        .navigationTitle("Pipeline Manager")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            while !Task.isCancelled {
                await reload()
                try? await Task.sleep(nanoseconds: 8_000_000_000)
            }
        }
        .refreshable { await reload() }
    }

    private func row(_ p: PipelineListItemDto) -> some View {
        let state = p.state.isEmpty ? "pending" : p.state
        let done = p.tasks.filter { $0.state == "completed" }.count
        let canCancel = state == "running" || state == "pending"
        return VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                Circle().fill(orchestratorStatusColor(state == "cancelled" ? "" : state)).frame(width: 8, height: 8)
                Text(p.name.isEmpty ? p.id : p.name)
                    .font(DatawatchFonts.bodyMedium.weight(.semibold))
                    .foregroundStyle(DatawatchColors.onSurface)
                Text(state).font(.system(size: 10)).foregroundStyle(DatawatchColors.onSurfaceMuted)
                Spacer(minLength: 4)
                if !p.tasks.isEmpty {
                    Text("\(done)/\(p.tasks.count) tasks").font(.system(size: 10)).foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
                if canCancel {
                    Button("Cancel") {
                        IosOrchestrator.shared.cancelPipeline(profile: profile, id: p.id) { err in
                            DispatchQueue.main.async { actionError = err; Task { await reload() } }
                        }
                    }
                    .font(DatawatchFonts.labelSmall)
                    .buttonStyle(.borderless)
                    .foregroundStyle(DatawatchColors.error)
                }
            }
            Text(p.id).font(.system(size: 10, design: .monospaced)).foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.8))
        }
    }

    private func reload() async {
        await withCheckedContinuation { (cont: CheckedContinuation<Void, Never>) in
            IosOrchestrator.shared.pipelines(
                profile: profile,
                onSuccess: { list in DispatchQueue.main.async { pipelines = list; loadError = nil; cont.resume() } },
                onError: { msg in DispatchQueue.main.async { if pipelines == nil { loadError = msg }; cont.resume() } }
            )
        }
    }
}
