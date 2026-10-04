import SwiftUI
import DatawatchShared

/// Settings › Automata › Type Registry (D25a: moved here from the Automata
/// tab's former "Types" segment; PWA automata_type_registry card).
struct SettingsAutomataTypesView: View {
    let profile: ServerProfile
    @StateObject private var vm = AutomataViewModel()
    @State private var showAdd = false
    @State private var pendingDelete: AutomataTypeDto?

    var body: some View {
        List {
            Section {
                if vm.isLoading && vm.types.isEmpty {
                    ProgressView()
                } else if vm.types.isEmpty {
                    Text("No automata types. Tap + to register the first automata type.")
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                } else {
                    ForEach(vm.types, id: \.id) { t in
                        SettingsAutomataTypeRow(item: t)
                            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                                Button(role: .destructive) { pendingDelete = t } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                                .tint(DatawatchColors.error)
                            }
                    }
                }
            }
            .listRowBackground(DatawatchColors.surface)
            if let err = vm.error {
                Section {
                    Text(err).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
                }
                .listRowBackground(DatawatchColors.surface)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { showAdd = true } label: {
                    Image(systemName: "plus").foregroundStyle(DatawatchColors.primary)
                }
                .accessibilityLabel("Add automata type")
            }
        }
        .sheet(isPresented: $showAdd) {
            AddAutomataTypeSheet(profile: profile) { vm.load(profile: profile) }
        }
        .confirmationDialog(
            L("Delete") + " \(pendingDelete?.label ?? "")?",
            isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Delete", role: .destructive) {
                if let t = pendingDelete { vm.delete(id: t.id, profile: profile) {} }
                pendingDelete = nil
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        }
        .task { vm.load(profile: profile) }
        .refreshable { vm.load(profile: profile) }
    }
}

private struct SettingsAutomataTypeRow: View {
    let item: AutomataTypeDto

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(swatch)
                .frame(width: 14, height: 14)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: item.label.isEmpty ? item.id : item.label)
                    .font(DatawatchFonts.bodyLarge)
                    .foregroundStyle(DatawatchColors.onSurface)
                if let desc = item.description_, !desc.isEmpty {
                    Text(verbatim: desc)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(2)
                }
                Text(verbatim: item.id)
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private var swatch: Color {
        guard let c = item.color, c.hasPrefix("#"), c.count == 7,
              let hex = UInt32(c.dropFirst(), radix: 16) else { return DatawatchColors.primary }
        return Color(hex: hex)
    }
}
