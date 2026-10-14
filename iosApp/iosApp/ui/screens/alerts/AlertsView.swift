import SwiftUI
import DatawatchShared

// ── Main view ─────────────────────────────────────────────────────────────

struct AlertsView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var vm = AlertsViewModel()
    @State private var collapsed: Set<String> = []
    @State private var replying: String? = nil
    @State private var savedCommands: [IosSavedCommand] = []
    @ObservedObject private var focus = AlertDeepLinkFocus.shared

    var body: some View {
        VStack(spacing: 0) {
        ServerPickerBar(showsAll: true)
        Group {
            if store.profiles.isEmpty {
                noProfilesView
            } else if let fed = store.fedStatus(for: store.activeProfile?.id), fed.phase == "error" {
                // #236.2: an unreachable proxied remote shows why, not a loader.
                FedConnStatusView(status: fed)
            } else if vm.error != nil && vm.alerts.isEmpty && !vm.isLoading {
                ErrorCard(message: L("Failed to load alerts.")) { vm.refresh() }
            } else {
                // First load: splash eye + spinner; refreshes over cached
                // alerts show the "Refreshing…" pill.
                ScreenLoadState(
                    isFirstLoad: vm.isLoading && vm.alerts.isEmpty,
                    isRefreshing: vm.isRefreshing,
                    message: L("Loading alerts…")
                ) {
                    alertListView
                }
            }
        }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DatawatchColors.background)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                headerTitle
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 4) {
                    DocsLinkButton(
                        profile: store.activeProfile,
                        key: "view_alerts"
                    )
                    if !store.isAllServers {
                        ReachabilityDotView(profile: store.activeProfile)
                    }
                }
            }
        }
        .onAppear {
            vm.appear(with: shownProfiles)
            if let p = store.activeProfile {
                IosQuickCommands.shared.loadSaved(profile: p) { list in
                    DispatchQueue.main.async { savedCommands = list }
                }
            }
        }
        .onDisappear {
            vm.stopPolling()
        }
        .onChange(of: store.profiles) { _ in
            vm.load(from: shownProfiles)
        }
        .onChange(of: store.activeProfileId) { _ in
            vm.load(from: shownProfiles)
        }
    }

    /// D2a: the active server, or every enabled server when "All" is picked.
    private var shownProfiles: [ServerProfile] {
        if store.isAllServers { return store.enabledProfiles }
        return store.activeProfile.map { [$0] } ?? []
    }

    // ── Header ────────────────────────────────────────────────────────────

    private var headerTitle: some View {
        HStack(spacing: 6) {
            HeaderView(title: "Alerts")
            if vm.unreadCount > 0 {
                Text("\(vm.unreadCount)")
                    .font(DatawatchFonts.badge)
                    .foregroundStyle(DatawatchColors.background)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(DatawatchColors.error)
                    .clipShape(Capsule())
                    .accessibilityLabel("\(vm.unreadCount) unread alerts")
            }
        }
    }

    // ── States ────────────────────────────────────────────────────────────

    private var noProfilesView: some View {
        VStack(spacing: 20) {
            Image(systemName: "bell.slash")
                .font(.system(.largeTitle))
                .imageScale(.large)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .accessibilityHidden(true)
            Text("No server connected")
                .font(DatawatchFonts.titleMedium)
                .foregroundStyle(DatawatchColors.onSurface)
            Text("Add a server in Settings to view alerts.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
    }

    // ── Tab row ───────────────────────────────────────────────────────────

    private var tabRow: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                ForEach(AlertsViewModel.AlertTab.allCases, id: \.self) { tab in
                    let count = vm.tabCount(for: tab)
                    let isSelected = vm.selectedTab == tab
                    Button { vm.selectedTab = tab } label: {
                        VStack(spacing: 4) {
                            Text(count > 0 ? "\(tab.rawValue) (\(count))" : tab.rawValue)
                                .font(DatawatchFonts.badge)
                                .foregroundStyle(isSelected ? DatawatchColors.primary : DatawatchColors.onSurfaceMuted)
                                .lineLimit(1)
                            Rectangle()
                                .fill(isSelected ? DatawatchColors.primary : Color.clear)
                                .frame(height: 2)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                    }
                }
            }
            .background(DatawatchColors.surface)
            Divider().background(DatawatchColors.border)
        }
    }

    // ── Filter bar ────────────────────────────────────────────────────────

    private var filterBar: some View {
        VStack(spacing: 0) {
            // Row 1: 🔔 count + ✕ dismiss-all + 🔕 mute + ↻ refresh
            HStack(spacing: 8) {
                (Text(verbatim: "🔔 ") + Text(vm.tabAlerts.count == 1 ? "\(vm.tabAlerts.count) alert" : "\(vm.tabAlerts.count) alerts"))
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                Spacer()
                controlBtn(vm.sortMode == .session ? "⏷ by session" : "🕒 chronological") {
                    vm.sortMode = vm.sortMode == .session ? .chrono : .session
                }
                .accessibilityLabel("Toggle sort: by session or chronological")
                controlBtn("✕") { vm.dismissAll() }
                    .accessibilityLabel("Dismiss all")
                // D47a: 🔕 mutes the alert dock for this app session (PWA muteAlertDock).
                controlBtn("🔕") { AlertDock.shared.mute() }
                    .accessibilityLabel("Mute alerts for this session")
                controlBtn("↻") { vm.refresh() }
                    .accessibilityLabel("Refresh")
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)

            Divider().background(DatawatchColors.border.opacity(0.5))

            // Row 2: severity chips with emoji + ×N counts
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(AlertsViewModel.AlertSeverityFilter.allCases, id: \.self) { filter in
                        severityChip(filter)
                    }
                }
                .padding(.horizontal, 12)
            }
            .padding(.vertical, 6)

            // Row 3: search
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                TextField("Search alerts…", text: $vm.filterText)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                if !vm.filterText.isEmpty {
                    Button { vm.filterText = "" } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .accessibilityLabel("Clear search")
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(DatawatchColors.surface)

            Divider().background(DatawatchColors.border)
        }
        .background(DatawatchColors.background)
    }

    private func controlBtn(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(L(label))
                .font(DatawatchFonts.badge)
                .foregroundStyle(DatawatchColors.onSurface)
                .padding(.horizontal, 8)
                .padding(.vertical, 3)
                .background(DatawatchColors.surface2)
                .clipShape(RoundedRectangle(cornerRadius: 6))
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(DatawatchColors.border, lineWidth: 1))
        }
    }

    /// PWA `chipBtn`: bg2 + full-colour border, filled with the colour when active.
    @ViewBuilder
    private func severityChip(_ filter: AlertsViewModel.AlertSeverityFilter) -> some View {
        let selected: Bool = vm.severityFilter == filter
        let count: Int = vm.chipCount(for: filter)
        let color: Color = chipColor(filter)
        Button { vm.severityFilter = filter } label: {
            Text(verbatim: "\(filter.label) ×\(count)")
                .font(DatawatchFonts.badge)
                .foregroundStyle(selected ? DatawatchColors.background : DatawatchColors.onSurface)
                .padding(.horizontal, 10)
                .padding(.vertical, 3)
                .background(selected ? color : DatawatchColors.surface)
                .clipShape(RoundedRectangle(cornerRadius: DatawatchRadius.pill))
                .overlay(RoundedRectangle(cornerRadius: DatawatchRadius.pill).stroke(color, lineWidth: 1))
        }
    }

    private func chipColor(_ filter: AlertsViewModel.AlertSeverityFilter) -> Color {
        switch filter {
        case .all, .info: return DatawatchColors.onSurfaceMuted
        case .prompt, .warning: return DatawatchColors.warning
        case .error: return DatawatchColors.error
        }
    }

    // ── Alert list ────────────────────────────────────────────────────────

    private var alertListView: some View {
        VStack(spacing: 0) {
            tabRow
            filterBar
            if vm.allMode, let err = vm.error {
                Text(err)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.warning)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 4)
            }
            ScrollViewReader { proxy in
                alertList
                    .onAppear { applyFocus(proxy) }
                    .onChange(of: focus.pendingId) { _ in applyFocus(proxy) }
                    .onChange(of: vm.alerts.count) { _ in applyFocus(proxy) }
            }
        }
    }

    /// Alert deep link (Android AlertsViewModel.focusAlert parity): once the
    /// alert is loaded, pick its sub-tab, clear chip + search, expand its card
    /// and scroll to it.
    private func applyFocus(_ proxy: ScrollViewProxy) {
        guard let id = focus.pendingId else { return }
        guard vm.focus(alertId: id) else { return }
        focus.pendingId = nil
        if let gid = vm.groupId(containing: id) { collapsed.remove(gid) }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            withAnimation { proxy.scrollTo(id, anchor: .top) }
        }
    }

    private var alertList: some View {
            List {
                if vm.filteredAlerts.isEmpty {
                    HStack {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "bell.slash")
                                .font(.system(.title))
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                .accessibilityHidden(true)
                            Text("No alerts.")
                                .font(DatawatchFonts.bodyMedium)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                        .padding(.top, 48)
                        Spacer()
                    }
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                } else if vm.sortMode == .chrono {
                    ForEach(vm.filteredAlerts, id: \.id) { alert in
                        VStack(alignment: .leading, spacing: 0) {
                            sessionLabel(for: vm.session(for: alert), isSystem: (alert.sessionId ?? "").isEmpty, fallbackId: alert.sessionId)
                                .padding(.horizontal, 12)
                                .padding(.top, 6)
                            alertRow(alert)
                            // PWA chrono view: Quick reply on prompt alerts.
                            if vm.isPrompt(alert), !savedCommands.isEmpty, let s = vm.session(for: alert) {
                                quickReply(for: s)
                                    .padding(.horizontal, 12)
                                    .padding(.bottom, 8)
                            }
                        }
                        .listRowBackground(DatawatchColors.surface)
                        .listRowSeparatorTint(DatawatchColors.border)
                        .listRowInsets(EdgeInsets())
                        .id(alert.id)
                    }
                } else {
                    ForEach(vm.groups) { group in
                        Section {
                            if !collapsed.contains(group.id) {
                                ForEach(Array(group.alerts.enumerated()), id: \.element.id) { index, alert in
                                    VStack(alignment: .leading, spacing: 0) {
                                        alertRow(alert)
                                        if index == 0, group.session?.state == .waiting, !savedCommands.isEmpty, let s = group.session {
                                            quickReply(for: s)
                                                .padding(.horizontal, 12)
                                                .padding(.bottom, 8)
                                        }
                                    }
                                    .listRowBackground(DatawatchColors.surface)
                                    .listRowSeparatorTint(DatawatchColors.border)
                                    .listRowInsets(EdgeInsets())
                                    .id(alert.id)
                                }
                            }
                        } header: {
                            groupHeader(group)
                        }
                    }
                }
            }
            .listStyle(.plain)
            .background(DatawatchColors.background)
            .scrollContentBackground(.hidden)
            .refreshable { await vm.userRefreshAsync() }
    }

    /// D49a/D50d: no per-alert read state or swipe — opening the page acks all,
    /// ✕ in the filter bar dismisses all (PWA).
    private func alertRow(_ alert: DatawatchShared.Alert) -> some View {
        AlertRow(alert: alert, isPrompt: vm.isPrompt(alert), serverName: vm.server(for: alert)?.displayName)
    }

    // ── By-session card header (PWA renderSessionCard) ─────────────────────

    private func groupHeader(_ group: AlertsViewModel.AlertGroup) -> some View {
        let promptCount = group.alerts.filter { vm.isPrompt($0) }.count
        let last = group.alerts.first.map { alertClock($0) } ?? "—"
        // Two lines on a phone (PWA header is flex-wrap): name · server … last / state · count.
        return VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 8) {
                Image(systemName: collapsed.contains(group.id) ? "chevron.right" : "chevron.down")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                sessionLabel(for: group.session, isSystem: group.isSystem, fallbackId: group.id)
                    .layoutPriority(1)
                if let first = group.alerts.first, !group.isSystem, let server = vm.server(for: first) {
                    Text(server.displayName)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.7))
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                Text("last \(last)")
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    .lineLimit(1)
                    .fixedSize()
            }
            groupHeaderDetail(group: group, promptCount: promptCount)
                .padding(.leading, 18)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DatawatchColors.surface2)
        .contentShape(Rectangle())
        .onTapGesture {
            if collapsed.contains(group.id) { collapsed.remove(group.id) } else { collapsed.insert(group.id) }
        }
        .textCase(nil)
        .listRowInsets(EdgeInsets())
    }

    private func groupHeaderDetail(group: AlertsViewModel.AlertGroup, promptCount: Int) -> some View {
        HStack(spacing: 6) {
            if let state = stateText(group.session) {
                Text(state.text)
                    .foregroundStyle(state.color)
            }
            Text(group.alerts.count == 1 ? "\(group.alerts.count) alert" : "\(group.alerts.count) alerts")
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            if promptCount > 0 {
                Text("· 🟡 \(promptCount)")
                    .fontWeight(.bold)
                    .foregroundStyle(DatawatchColors.warning)
            }
        }
        .font(DatawatchFonts.labelSmall)
        .lineLimit(1)
    }

    /// Session name as a link to the session (PWA sessLink); plain text for System/unknown.
    @ViewBuilder
    private func sessionLabel(for session: DwSession?, isSystem: Bool, fallbackId: String?) -> some View {
        if isSystem {
            Text("System")
                .font(DatawatchFonts.bodyMedium.weight(.bold))
                .foregroundStyle(DatawatchColors.onSurface)
        } else if let session, let profile = vm.owner(of: session) {
            NavigationLink {
                SessionDetailView(session: session, profile: profile)
            } label: {
                Text(session.name?.isEmpty == false ? session.name! : session.id)
                    .font(DatawatchFonts.bodyMedium.weight(.bold))
                    .foregroundStyle(DatawatchColors.secondary)
                    .underline()
                    .lineLimit(1)
            }
            .buttonStyle(.borderless)
        } else {
            Text((fallbackId ?? "").components(separatedBy: "-").last ?? "")
                .font(DatawatchFonts.bodyMedium.weight(.bold))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        }
    }

    private func stateText(_ session: DwSession?) -> (text: String, color: Color)? {
        guard let s = session else { return nil }
        switch s.state {
        case .waiting: return ("🟠 waiting input", DatawatchColors.warning)
        case .running: return ("🟢 running", DatawatchColors.success)
        case .completed: return ("✅ complete", DatawatchColors.onSurfaceMuted)
        case .killed: return ("✅ killed", DatawatchColors.onSurfaceMuted)
        case .error: return ("✅ failed", DatawatchColors.onSurfaceMuted)
        default: return nil
        }
    }

    private func alertClock(_ a: DatawatchShared.Alert) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(a.createdAt.toEpochMilliseconds()) / 1000)
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f.string(from: date)
    }

    // ── Quick reply (grouped: latest alert of a waiting session; chrono: prompt alerts) ──

    private func quickReply(for session: DwSession) -> some View {
        // PWA `quick-cmd-select`: saved commands only (hidden when none).
        Menu {
            ForEach(savedCommands, id: \.name) { cmd in
                Button(cmd.name) { sendReply(cmd.command, to: session) }
            }
        } label: {
            HStack(spacing: 4) {
                if replying == session.id { ProgressView().controlSize(.mini) }
                Text("Quick reply…")
                Image(systemName: "chevron.down").font(.system(size: 9, weight: .bold))
            }
            .font(DatawatchFonts.labelSmall)
            .foregroundStyle(DatawatchColors.primary)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(DatawatchColors.primary.opacity(0.12), in: Capsule())
        }
        .disabled(replying != nil)
    }

    private func sendReply(_ value: String, to session: DwSession) {
        guard let profile = vm.owner(of: session) else { return }
        replying = session.id
        IosQuickCommands.shared.send(
            profile: profile,
            session: session,
            value: value,
            onSuccess: {
                DispatchQueue.main.async {
                    replying = nil
                    Task { await vm.refreshAsync() }
                }
            },
            onError: { _ in DispatchQueue.main.async { replying = nil } }
        )
    }
}

// ── Alert row ─────────────────────────────────────────────────────────────

private struct AlertRow: View {
    let alert: DatawatchShared.Alert
    let isPrompt: Bool
    /// D2a: server tag shown when Alerts aggregates every server.
    var serverName: String? = nil

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            // Left colored border (3px)
            Rectangle()
                .fill(borderColor)
                .frame(width: 3)

            VStack(alignment: .leading, spacing: 6) {
                // Top: badge + timestamp
                HStack(spacing: 8) {
                    badgeView
                    Text(alertTime(from: alert.createdAt))
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(0.7))
                    Spacer()
                    if let serverName { serverTag(serverName) }
                }

                // Title
                Text(alert.title.isEmpty ? alert.type : alert.title)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                    .lineLimit(2)

                // Message
                if !alert.message.isEmpty {
                    Text(String(alert.message.prefix(500)))
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        .lineLimit(3)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(alertBackground)
        }
        .accessibilityElement(children: .combine)
    }

    private func serverTag(_ name: String) -> some View {
        Text(name)
            .font(DatawatchFonts.badge)
            .foregroundStyle(DatawatchColors.secondary)
            .lineLimit(1)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(DatawatchColors.secondary.opacity(0.12), in: RoundedRectangle(cornerRadius: 3))
    }

    /// PWA `renderAlert`: only prompt and error get a colour; warn/info use the
    /// plain border and a transparent background.
    private var borderColor: Color {
        if isPrompt { return DatawatchColors.warning }
        return alert.severity == .error ? DatawatchColors.error : DatawatchColors.border
    }

    private var alertBackground: Color {
        if isPrompt { return DatawatchColors.warning.opacity(0.08) }
        return alert.severity == .error ? DatawatchColors.error.opacity(0.06) : Color.clear
    }

    @ViewBuilder
    private var badgeView: some View {
        if isPrompt {
            badgeLabel("🟡 PROMPT", fg: Color(hex: 0x0F1117), bg: DatawatchColors.warning)
        } else {
            // PWA kindBadge: 🔴 ERROR, otherwise "⚪ <level>" on bg2 (warn included).
            switch alert.severity {
            case .error:
                badgeLabel("🔴 ERROR", fg: .white, bg: DatawatchColors.error)
            case .warning:
                badgeLabel("⚪ warn", fg: DatawatchColors.onSurfaceMuted, bg: DatawatchColors.surface)
            default:
                badgeLabel("⚪ info", fg: DatawatchColors.onSurfaceMuted, bg: DatawatchColors.surface)
            }
        }
    }

    private func badgeLabel(_ text: String, fg: Color, bg: Color) -> some View {
        Text(L(text))
            .font(DatawatchFonts.badge)
            .foregroundStyle(fg)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(bg)
            .clipShape(RoundedRectangle(cornerRadius: 3))
    }

    private func alertTime(from instant: Kotlinx_datetimeInstant) -> String {
        let epochMs = instant.toEpochMilliseconds()
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000.0)
        let cal = Calendar.current
        let h = cal.component(.hour, from: date)
        let m = cal.component(.minute, from: date)
        let s = cal.component(.second, from: date)
        return String(format: "%02d:%02d:%02d", h, m, s)
    }
}

#if DEBUG
#Preview {
    NavigationStack { AlertsView() }
        .preferredColorScheme(.dark)
}
#endif
