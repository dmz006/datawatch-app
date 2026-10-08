import SwiftUI
import DatawatchShared

/// PWA `_serverPickerBar` (D2a): "Server:" + one chip per enabled server,
/// active chip filled accent2. Hidden when there is only one choice.
/// #234: each remote reached through a server's `/api/proxy/<name>` gets a chip
/// right after its parent ("parent › remote", or just "remote" with one server).
struct ServerPickerBar: View {
    @EnvironmentObject private var store: ServerProfileStore
    /// Only screens that can aggregate (Sessions) offer "All".
    var showsAll: Bool = false

    private var allChip: some View {
        let active = store.isAllServers
        return Button { store.selectActive(ServerProfileStore.allServersId) } label: {
            Text("All")
                .font(.system(size: 11, weight: active ? .semibold : .regular))
                .foregroundStyle(active ? Color.white : DatawatchColors.onSurface)
                .padding(.horizontal, 9)
                .padding(.vertical, 2)
                .background(active ? DatawatchColors.secondary : DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: DatawatchRadius.pill))
                .overlay(RoundedRectangle(cornerRadius: DatawatchRadius.pill).stroke(DatawatchColors.border, lineWidth: 1))
        }
        .buttonStyle(.borderless)
        .accessibilityAddTraits(active ? .isSelected : [])
    }

    var body: some View {
        let servers = store.pickerProfiles
        let realCount = Int32(store.enabledProfiles.count)
        if servers.count > 1 {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    Text("Server:")
                        .font(.system(size: 11))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    // "All" aggregates real servers, so it needs two of them
                    // (ServerProfileStore.isAllServers), not one server + remotes.
                    if showsAll && store.enabledProfiles.count > 1 { allChip }
                    ForEach(servers, id: \.id) { p in chip(p, realCount: realCount) }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)
            }
            .background(DatawatchColors.surface)
            .overlay(alignment: .bottom) { Rectangle().fill(DatawatchColors.border).frame(height: 1) }
            .onAppear { store.refreshProxied() }
        } else {
            // Discover remotes even while the bar is hidden (one server, list not loaded yet).
            Color.clear.frame(height: 0).onAppear { store.refreshProxied() }
        }
    }

    private func chip(_ p: ServerProfile, realCount: Int32) -> some View {
        let active = !store.isAllServers && store.activeProfile?.id == p.id
        return Button { store.selectActive(p.id) } label: {
            Text(IosProxiedServers.shared.chipLabel(profile: p, realEnabledCount: realCount))
                .font(.system(size: 11, weight: active ? .semibold : .regular))
                .foregroundStyle(active ? Color.white : DatawatchColors.onSurface)
                .padding(.horizontal, 9)
                .padding(.vertical, 2)
                .background(active ? DatawatchColors.secondary : DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: DatawatchRadius.pill))
                .overlay(RoundedRectangle(cornerRadius: DatawatchRadius.pill).stroke(DatawatchColors.border, lineWidth: 1))
        }
        .buttonStyle(.borderless)
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}
