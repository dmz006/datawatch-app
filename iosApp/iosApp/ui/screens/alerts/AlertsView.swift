import SwiftUI
import Combine
import DatawatchShared

// ── ViewModel ─────────────────────────────────────────────────────────────

@MainActor
final class AlertsViewModel: ObservableObject {
    @Published var alerts: [DatawatchShared.Alert] = []
    /// Live session list — classifies alerts into Active / Historical (PWA: by session liveness).
    @Published private(set) var sessions: [DwSession] = []
    @Published var unreadCount: Int = 0 {
        didSet { publishBadge() }
    }
    /// D61a watched-badge filter: once any session is watched, the tab / bell badge
    /// counts only watched sessions' unread alerts (Android watchedAlertCount).
    private var watchSink: AnyCancellable? = nil

    func publishBadge() {
        let badge: Int = LocalSessionPrefs.badgeCount(
            serverUnread: unreadCount, alerts: alerts, sessions: sessions, profileId: allMode ? nil : profile?.id
        )
        UserDefaults.standard.set(badge, forKey: "dw.alert.badge")
    }
    @Published var isLoading: Bool = false
    @Published var error: String? = nil
    @Published var filterText: String = "" { didSet { persistTabState() } }
    @Published var severityFilter: AlertSeverityFilter = .all { didSet { persistTabState() } }
    @Published var sortMode: SortMode = .session { didSet { persistTabState() } }
    @Published var selectedTab: AlertTab = .active {
        didSet {
            UserDefaults.standard.set(selectedTab.rawValue, forKey: "dw.alerts.activeTab")
            loadTabState()
        }
    }

    enum AlertTab: String, CaseIterable {
        case active    = "Active"
        case historical = "Historical"
        case system    = "System"
    }

    enum AlertSeverityFilter: String, CaseIterable {
        case all = "All"
        case prompt = "Prompt"
        case error = "Error"
        case warning = "Warn"
        case info = "Info"

        /// PWA `alert_chip_*` copy (rawValue stays the persisted key).
        var label: String {
            switch self {
            case .all: return L("all")
            case .prompt: return "🟡 " + L("prompts")
            case .error: return "🔴 " + L("errors")
            case .warning: return "🟠 " + L("warn")
            case .info: return "⚪ " + L("info")
            }
        }
    }

    /// PWA `setAlertsSort`: grouped by session (default) or flat chronological.
    enum SortMode: String { case session, chrono }

    /// One by-session card (PWA `renderSessionCard`). `session == nil` with `isSystem` = System card.
    struct AlertGroup: Identifiable {
        let id: String
        let session: DwSession?
        let isSystem: Bool
        let alerts: [DatawatchShared.Alert]
    }

    private var loadingTabState = false

    init() {
        if let raw = UserDefaults.standard.string(forKey: "dw.alerts.activeTab"),
           let tab = AlertTab(rawValue: raw) {
            selectedTab = tab
        }
        loadTabState()
        watchSink = LocalSessionPrefs.shared.$revision.dropFirst().sink { [weak self] _ in
            Task { @MainActor [weak self] in self?.publishBadge() }
        }
    }

    // ── Session lookup / classification ──────────────────────────────────

    func session(for alert: DatawatchShared.Alert) -> DwSession? {
        guard let sid = alert.sessionId, !sid.isEmpty else { return nil }
        let pid: String? = alertServer[alert.id]
        return sessions.first { ($0.fullId == sid || $0.id == sid) && (pid == nil || $0.serverProfileId == pid) }
    }

    // ── D2a all-servers aggregate (Android AlertsViewModel allServersMode) ──

    /// True when alerts from every enabled server are merged ("All" chip).
    var allMode: Bool { profiles.count > 1 }

    /// The server an alert came from (nil outside all-servers mode).
    func server(for alert: DatawatchShared.Alert) -> ServerProfile? {
        guard allMode, let pid = alertServer[alert.id] else { return nil }
        return profiles.first { $0.id == pid }
    }

    /// The profile that owns a session — used for links and quick replies.
    func owner(of session: DwSession) -> ServerProfile? {
        profiles.first { $0.id == session.serverProfileId } ?? profile
    }

    private func isDone(_ s: DwSession) -> Bool {
        s.state == .completed || s.state == .killed || s.state == .error
    }

    /// PWA: a session alert is Active only while its session is in the live list and not finished.
    private func isActive(_ alert: DatawatchShared.Alert) -> Bool {
        guard let s = session(for: alert) else { return false }
        return !isDone(s)
    }

    private func belongs(_ alert: DatawatchShared.Alert, to tab: AlertTab) -> Bool {
        let hasSession = !(alert.sessionId ?? "").isEmpty
        switch tab {
        case .system:     return !hasSession
        case .active:     return hasSession && isActive(alert)
        case .historical: return hasSession && !isActive(alert)
        }
    }

    /// Alerts for the currently selected tab (before severity/text filtering).
    var tabAlerts: [DatawatchShared.Alert] { alerts.filter { belongs($0, to: selectedTab) } }

    /// PWA `catOf` (app.js renderAlertsView): an alert is a prompt when its
    /// session is `waiting_input` or its title mentions needs input / prompt /
    /// waiting; otherwise its level decides. Categories are exclusive.
    func isPrompt(_ a: DatawatchShared.Alert) -> Bool {
        if session(for: a)?.state == .waiting { return true }
        return a.title.range(of: #"\b(needs input|prompt|waiting)\b"#, options: [.regularExpression, .caseInsensitive]) != nil
    }

    func category(_ a: DatawatchShared.Alert) -> AlertSeverityFilter {
        if isPrompt(a) { return .prompt }
        switch a.severity {
        case .error: return .error
        case .warning: return .warning
        default: return .info
        }
    }

    var filteredAlerts: [DatawatchShared.Alert] {
        var result = tabAlerts
        if !filterText.isEmpty {
            let q = filterText.lowercased()
            result = result.filter {
                $0.title.lowercased().contains(q) ||
                $0.message.lowercased().contains(q)
            }
        }
        if severityFilter != .all {
            let wanted = severityFilter
            result = result.filter { category($0) == wanted }
        }
        return result.sorted { $0.createdAt.toEpochMilliseconds() > $1.createdAt.toEpochMilliseconds() }
    }

    /// By-session cards: waiting → running → others (PWA stateRank), System card last.
    var groups: [AlertGroup] {
        var bySession: [String: [DatawatchShared.Alert]] = [:]
        var system: [DatawatchShared.Alert] = []
        for a in filteredAlerts {
            if let sid = a.sessionId, !sid.isEmpty {
                // All servers: two servers can share a session id — key by server too.
                let key: String = allMode ? "\(alertServer[a.id] ?? "")|\(sid)" : sid
                bySession[key, default: []].append(a)
            } else {
                system.append(a)
            }
        }
        func rank(_ g: AlertGroup) -> Int {
            switch g.session?.state {
            case .some(.waiting): return 0
            case .some(.running): return 1
            default: return 2
            }
        }
        var out: [AlertGroup] = bySession.map { key, list in
            let s: DwSession? = list.first.flatMap { session(for: $0) }
            return AlertGroup(id: key, session: s, isSystem: false, alerts: list)
        }
        out.sort { l, r in
            let lr = rank(l), rr = rank(r)
            if lr != rr { return lr < rr }
            return (l.alerts.first?.createdAt.toEpochMilliseconds() ?? 0) > (r.alerts.first?.createdAt.toEpochMilliseconds() ?? 0)
        }
        if !system.isEmpty { out.append(AlertGroup(id: "__system__", session: nil, isSystem: true, alerts: system)) }
        return out
    }

    /// Alert deep link: select the sub-tab holding `alertId` and clear chip +
    /// search. False while the alert hasn't loaded yet.
    func focus(alertId: String) -> Bool {
        guard let a = alerts.first(where: { $0.id == alertId }) else { return false }
        let tab: AlertTab = AlertTab.allCases.first(where: { belongs(a, to: $0) }) ?? .active
        if selectedTab != tab { selectedTab = tab }
        filterText = ""
        severityFilter = .all
        return true
    }

    /// Id of the by-session card that contains `alertId` (current tab/filters).
    func groupId(containing alertId: String) -> String? {
        groups.first(where: { g in g.alerts.contains(where: { $0.id == alertId }) })?.id
    }

    func tabCount(for tab: AlertTab) -> Int { alerts.filter { belongs($0, to: tab) }.count }

    func chipCount(for filter: AlertSeverityFilter) -> Int {
        let base = tabAlerts
        if filter == .all { return base.count }
        return base.filter { category($0) == filter }.count
    }

    // ── Per-tab persisted filter state (PWA cs_alerts_tab_state_<tab>) ──

    private func tabKey(_ tab: AlertTab) -> String { "dw.alerts.tab.\(tab.rawValue)" }

    private func loadTabState() {
        loadingTabState = true
        defer { loadingTabState = false }
        let d = UserDefaults.standard.dictionary(forKey: tabKey(selectedTab)) ?? [:]
        filterText = d["search"] as? String ?? ""
        severityFilter = AlertSeverityFilter(rawValue: d["chip"] as? String ?? "") ?? .all
        sortMode = SortMode(rawValue: d["sort"] as? String ?? "") ?? .session
    }

    private func persistTabState() {
        guard !loadingTabState else { return }
        UserDefaults.standard.set(
            ["search": filterText, "chip": severityFilter.rawValue, "sort": sortMode.rawValue],
            forKey: tabKey(selectedTab)
        )
    }

    // ── Loading ──────────────────────────────────────────────────────────

    /// Primary profile (the single active server, or the first in "All").
    private(set) var profile: ServerProfile?
    /// Every server being shown — one normally, every enabled one in "All" (D2a).
    private(set) var profiles: [ServerProfile] = []
    /// alert id → server profile id (alert ids are server-random hex, unique across servers).
    @Published private(set) var alertServer: [String: String] = [:]
    private var pollTask: Task<Void, Never>? = nil
    private var inFlight = false
    private static let pollInterval: Duration = .seconds(5)

    func load(from newProfiles: [ServerProfile]) {
        guard newProfiles.map({ $0.id }) != profiles.map({ $0.id }) else { return }
        profiles = newProfiles
        profile = newProfiles.first
        alerts = []
        alertServer = [:]
        if profile != nil {
            refresh()
            startPolling()
        } else {
            stopPolling()
            alerts = []
            sessions = []
            unreadCount = 0
            error = nil
        }
    }

    /// onAppear: load when the server changed, else refetch now and resume the
    /// poll `onDisappear` stopped (`load(from:)` alone returned early for the
    /// same server, so a revisited tab never polled again).
    func appear(with newProfiles: [ServerProfile]) {
        if newProfiles.map({ $0.id }) != profiles.map({ $0.id }) {
            load(from: newProfiles)
            return
        }
        guard profile != nil else { return }
        refresh()
        startPolling()
    }

    /// Sequential loop: the next fetch is scheduled only after the previous one
    /// completes, so a slow server can't stack requests (the Android v1.23.112
    /// pile-up). Visibility-gated by the view's onAppear / onDisappear.
    func startPolling() {
        stopPolling()
        pollTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: Self.pollInterval)
                guard let self, !Task.isCancelled else { return }
                await self.refreshAsync()
            }
        }
    }

    func stopPolling() {
        pollTask?.cancel()
        pollTask = nil
    }

    func refresh() {
        if alerts.isEmpty { isLoading = true }
        Task { await refreshAsync() }
    }

    func refreshAsync() async {
        if allMode {
            await refreshAllAsync()
            return
        }
        guard let profile, !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        async let alertsResult = ServiceLocatorAsync.listAlerts(profile: profile)
        async let sessionsResult = ServiceLocatorAsync.listSessions(profile: profile)
        let fetched: Result<(alerts: [DatawatchShared.Alert], unreadCount: Int), Error>
        do {
            fetched = .success(try await alertsResult)
        } catch {
            fetched = .failure(error)
        }
        let live: [DwSession]? = try? await sessionsResult
        // #236.6/7: the server changed while this ran — drop the stale result and
        // fetch the new server now (its own refresh() hit the in-flight guard).
        if self.profile?.id != profile.id || allMode {
            Task { [weak self] in await self?.refreshAsync() }
            return
        }
        do {
            let result = try fetched.get()
            alerts = result.alerts
            alertServer = [:]
            unreadCount = result.unreadCount
            error = nil
            // D49a (PWA renderAlertsView): opening the page acknowledges everything.
            if unreadCount > 0 && !alerts.isEmpty { acknowledgeAll(profile) }
        } catch {
            self.error = error.localizedDescription
        }
        if let live { sessions = live }
        publishBadge()
        isLoading = false
    }

    /// D2a: fetch every enabled server in turn (sequential, like Sessions) and
    /// merge newest-first; an unreachable server is named in the error line.
    private func refreshAllAsync() async {
        guard !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        let targets: [ServerProfile] = profiles
        var merged: [DatawatchShared.Alert] = []
        var owners: [String: String] = [:]
        var liveSessions: [DwSession] = []
        var failures: [String] = []
        for p in targets {
            do {
                let result = try await ServiceLocatorAsync.listAlerts(profile: p)
                for a in result.alerts { owners[a.id] = p.id }
                merged.append(contentsOf: result.alerts)
                if result.unreadCount > 0 && !result.alerts.isEmpty { acknowledgeAll(p) }
            } catch {
                failures.append("\(p.displayName): \(error.localizedDescription)")
            }
            if let live = try? await ServiceLocatorAsync.listSessions(profile: p) {
                liveSessions.append(contentsOf: live)
            }
        }
        guard targets.map({ $0.id }) == profiles.map({ $0.id }) else { return }
        alerts = merged.sorted { $0.createdAt.toEpochMilliseconds() > $1.createdAt.toEpochMilliseconds() }
        alertServer = owners
        sessions = liveSessions
        // Opening the page acknowledged each server's alerts (D49a).
        unreadCount = 0
        error = failures.isEmpty ? nil : L("Some servers unreachable: ") + failures.prefix(2).joined(separator: "; ")
        publishBadge()
        isLoading = false
    }

    private func acknowledgeAll(_ profile: ServerProfile) {
        unreadCount = 0
        IosServiceLocator.shared.markAllAlertsRead(profile: profile, onSuccess: {}, onError: { _ in })
    }

    /// Dismiss all alerts — D48a: deletes on the server like the PWA ✕.
    func dismissAll() {
        for p in profiles {
            let pid: String = p.id
            IosServiceLocator.shared.deleteAllAlerts(
                profile: p,
                onSuccess: { [weak self] in
                    DispatchQueue.main.async {
                        guard let self else { return }
                        self.unreadCount = 0
                        self.alerts = self.alerts.filter { self.alertServer[$0.id] != nil && self.alertServer[$0.id] != pid }
                    }
                },
                onError: { _ in }
            )
        }
    }
}

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
            } else if vm.isLoading && vm.alerts.isEmpty {
                LoadingIndicator(message: L("Loading…"))
            } else if let err = vm.error, vm.alerts.isEmpty {
                ErrorCard(message: L("Failed to load alerts.")) { vm.refresh() }
            } else {
                alertListView
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
            .refreshable { await vm.refreshAsync() }
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
