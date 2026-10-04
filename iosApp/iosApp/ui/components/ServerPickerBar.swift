import SwiftUI
import DatawatchShared

/// PWA `_serverPickerBar` (D2a): "Server:" + one chip per enabled server,
/// active chip filled accent2. Hidden when only one server is configured.
struct ServerPickerBar: View {
    @EnvironmentObject private var store: ServerProfileStore

    var body: some View {
        let servers = store.enabledProfiles
        if servers.count > 1 {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    Text("Server:")
                        .font(.system(size: 11))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    ForEach(servers, id: \.id) { p in chip(p) }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 4)
            }
            .background(DatawatchColors.surface)
            .overlay(alignment: .bottom) { Rectangle().fill(DatawatchColors.border).frame(height: 1) }
        }
    }

    private func chip(_ p: ServerProfile) -> some View {
        let active = store.activeProfile?.id == p.id
        return Button { store.selectActive(p.id) } label: {
            Text(p.displayName)
                .font(.system(size: 11, weight: active ? .semibold : .regular))
                .foregroundStyle(active ? Color.white : DatawatchColors.onSurface)
                .padding(.horizontal, 9)
                .padding(.vertical, 2)
                .background(active ? DatawatchColors.secondary : DatawatchColors.surface2, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(DatawatchColors.border, lineWidth: 1))
        }
        .buttonStyle(.borderless)
        .accessibilityAddTraits(active ? .isSelected : [])
    }
}
