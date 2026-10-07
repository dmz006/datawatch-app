import SwiftUI
import DatawatchShared

/// Settings tab (parity D31b): a native grouped list whose sections are the
/// PWA's six settings tabs — General · Plugins · Comms · Compute · Automata ·
/// About — in PWA order. Each PWA card is a row that pushes its detail screen
/// (`SettingsCardScreen`). Sections collapse like PWA cards (D27a); collapsed
/// state persists across launches (PWA `cs_settings_collapsed`).
struct SettingsView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @AppStorage("settingsCollapsedGroups") private var collapsedRaw: String = ""
    /// Card opened by an in-app deep link (`SettingsDeepLink`).
    @State private var deepCard: SettingsCard? = nil

    private var profile: ServerProfile? {
        store.activeProfile
    }

    var body: some View {
        NavigationStack {
            List {
                ForEach(SettingsCatalog.groups) { group in
                    groupSection(group)
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    HeaderView(title: "Settings")
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 4) {
                        DocsLinkButton(profile: profile, key: "view_settings")
                        AlertsBellButton()
                        ReachabilityDotView(profile: profile)
                    }
                }
            }
            .navigationDestination(isPresented: deepCardShown) {
                if let card = deepCard {
                    SettingsCardScreen(card: card)
                        .environmentObject(store)
                }
            }
            .onAppear { consumeDeepLink() }
            .onReceive(NotificationCenter.default.publisher(for: .dwNavigateToSettings)) { _ in
                consumeDeepLink()
            }
        }
    }

    private var deepCardShown: Binding<Bool> {
        Binding(get: { deepCard != nil }, set: { if !$0 { deepCard = nil } })
    }

    private func consumeDeepLink() {
        if let card = SettingsDeepLink.take() { deepCard = card }
    }

    // MARK: Sections

    private var collapsed: Set<String> {
        Set(collapsedRaw.split(separator: ",").map(String.init))
    }

    private func toggleCollapsed(_ id: String) {
        var s = collapsed
        if s.contains(id) { s.remove(id) } else { s.insert(id) }
        collapsedRaw = s.sorted().joined(separator: ",")
    }

    @ViewBuilder
    private func groupSection(_ group: SettingsGroup) -> some View {
        let isCollapsed: Bool = collapsed.contains(group.id)
        Section {
            if !isCollapsed {
                ForEach(group.cards) { card in
                    NavigationLink {
                        SettingsCardScreen(card: card)
                            .environmentObject(store)
                    } label: {
                        SettingsCardRowLabel(card: card)
                    }
                    .listRowBackground(DatawatchColors.surface)
                }
            }
        } header: {
            Button {
                withAnimation { toggleCollapsed(group.id) }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: isCollapsed ? "chevron.right" : "chevron.down")
                        .font(.caption.weight(.semibold))
                    Text(L(group.title))
                    Spacer()
                    if isCollapsed {
                        Text("\(group.cards.count)")
                            .font(DatawatchFonts.labelSmall)
                    }
                }
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            .buttonStyle(.plain)
            .accessibilityHint(isCollapsed ? Text("Expand") : Text("Collapse"))
        }
    }
}

/// Row label for one settings card: SF Symbol + localized PWA card title.
struct SettingsCardRowLabel: View {
    let card: SettingsCard

    var body: some View {
        Label {
            Text(L(card.title))
                .foregroundStyle(DatawatchColors.onSurface)
        } icon: {
            Image(systemName: card.icon)
                .foregroundStyle(DatawatchColors.primary)
        }
    }
}

#if DEBUG
#Preview {
    SettingsView()
        .environmentObject(ServerProfileStore())
        .preferredColorScheme(.dark)
}
#endif
