import SwiftUI
import DatawatchShared

/// Compute Node edit form › Models (PWA `fe_compute_models_list` +
/// `refreshOllamaModelsList` / `ollamaRemoveModel`; Android ComputeNodeDialog
/// models sub-section). Ollama nodes in edit mode only: installed models,
/// ✕ removes one, "Browse marketplace" opens the Ollama marketplace.
struct ComputeModelsSection: View {
    let profile: ServerProfile
    let nodeName: String

    @State private var models: [String] = []
    @State private var loading = true
    @State private var error: String? = nil
    @State private var removing: String? = nil
    @State private var showMarketplace = false

    var body: some View {
        Section {
            content
            Button { showMarketplace = true } label: {
                Text("Browse marketplace").foregroundStyle(DatawatchColors.primary)
            }
        } header: {
            Text("Models")
        }
        .listRowBackground(DatawatchColors.surface)
        .task { load() }
        .sheet(isPresented: $showMarketplace, onDismiss: load) {
            NavigationStack { OllamaMarketplaceView(profile: profile) }
        }
    }

    @ViewBuilder
    private var content: some View {
        if loading {
            ProgressView()
        } else if let error {
            Text(verbatim: error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
        } else if models.isEmpty {
            Text("no models installed")
                .font(DatawatchFonts.labelSmall.italic())
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        } else {
            ForEach(models, id: \.self) { m in
                modelRow(m)
            }
        }
    }

    private func modelRow(_ m: String) -> some View {
        HStack(spacing: 8) {
            Text(verbatim: m)
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurface)
            Spacer(minLength: 8)
            if removing == m {
                ProgressView().controlSize(.small)
            } else {
                Button { remove(m) } label: {
                    Image(systemName: "xmark").foregroundStyle(DatawatchColors.error)
                }
                .buttonStyle(.borderless)
                .accessibilityLabel("Remove this model")
            }
        }
    }

    private func load() {
        IosComputeModels.shared.list(
            profile: profile, nodeName: nodeName,
            onSuccess: { list in
                DispatchQueue.main.async { models = list; error = nil; loading = false }
            },
            onError: { msg in
                DispatchQueue.main.async { error = msg; loading = false }
            }
        )
    }

    private func remove(_ m: String) {
        removing = m
        IosComputeModels.shared.remove(
            profile: profile, nodeName: nodeName, model: m,
            onSuccess: {
                DispatchQueue.main.async { removing = nil; load() }
            },
            onError: { msg in
                DispatchQueue.main.async {
                    removing = nil
                    AlertDock.shared.post(msg, level: .error)
                }
            }
        )
    }
}
