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
            serverUnread: unreadCount, alerts: alerts, sessions: sessions, profileId: profile?.id
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
        return sessions.first { $0.fullId == sid || $0.id == sid }
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

    func isPrompt(_ a: DatawatchShared.Alert) -> Bool {
        a.type.contains("input") || a.type.contains("prompt")
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
        switch severityFilter {
        case .all: break
        case .prompt:  result = result.filter { isPrompt($0) }
        case .error:   result = result.filter { $0.severity == .error }
        case .warning: result = result.filter { $0.severity == .warning }
        case .info:    result = result.filter { $0.severity != .error && $0.severity != .warning && !$0.type.contains("input") }
        }
        return result.sorted { $0.createdAt.toEpochMilliseconds() > $1.createdAt.toEpochMilliseconds() }
    }

    /// By-session cards: waiting → running → others (PWA stateRank), System card last.
    var groups: [AlertGroup] {
        var bySession: [String: [DatawatchShared.Alert]] = [:]
        var system: [DatawatchShared.Alert] = []
        for a in filteredAlerts {
            if let sid = a.sessionId, !sid.isEmpty {
                bySession[sid, default: []].append(a)
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
        var out = bySession.map { sid, list in
            AlertGroup(id: sid, session: sessions.first { $0.fullId == sid || $0.id == sid }, isSystem: false, alerts: list)
        }
        out.sort { l, r in
            let lr = rank(l), rr = rank(r)
            if lr != rr { return lr < rr }
            return (l.alerts.first?.createdAt.toEpochMilliseconds() ?? 0) > (r.alerts.first?.createdAt.toEpochMilliseconds() ?? 0)
        }
        if !system.isEmpty { out.append(AlertGroup(id: "__system__", session: nil, isSystem: true, alerts: system)) }
        return out
    }

    func tabCount(for tab: AlertTab) -> Int { alerts.filter { belongs($0, to: tab) }.count }

    func chipCount(for filter: AlertSeverityFilter) -> Int {
        let base = tabAlerts
        switch filter {
        case .all:     return base.count
        case .prompt:  return base.filter { isPrompt($0) }.count
        case .error:   return base.filter { $0.severity == .error }.count
        case .warning: return base.filter { $0.severity == .warning }.count
        case .info:    return base.filter { $0.severity != .error && $0.severity != .warning && !$0.type.contains("input") }.count
        }
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

    private(set) var profile: ServerProfile?
    private var pollTask: Task<Void, Never>? = nil
    private var inFlight = false
    private static let pollInterval: Duration = .seconds(5)

    func load(from profiles: [ServerProfile]) {
        let newActive = profiles.first
        guard newActive?.id != profile?.id else { return }
        profile = newActive
        if newActive != nil {
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
        guard let profile, !inFlight else { return }
        inFlight = true
        defer { inFlight = false }
        async let alertsResult = ServiceLocatorAsync.listAlerts(profile: profile)
        async let sessionsResult = ServiceLocatorAsync.listSessions(profile: profile)
        do {
            let result = try await alertsResult
            alerts = result.alerts
            unreadCount = result.unreadCount
            error = nil
        } catch {
            self.error = error.localizedDescription
        }
        if let live = try? await sessionsResult { sessions = live }
        publishBadge()
        isLoading = false
    }

    /// Mark an alert as read on server and remove it locally.
    func dismiss(alert: DatawatchShared.Alert) {
        alerts.removeAll { $0.id == alert.id }
        if !alert.read, unreadCount > 0 {
            unreadCount -= 1
        }
        guard let profile else { return }
        IosServiceLocator.shared.markAlertRead(
            profile: profile,
            alertId: alert.id,
            onSuccess: {},
            onError: { _ in }
        )
    }

    /// Dismiss all alerts (clear locally and on server).
    func dismissAll() {
        guard let profile else { return }
        IosServiceLocator.shared.markAllAlertsRead(
            profile: profile,
            onSuccess: { [weak self] in
                DispatchQueue.main.async {
                    self?.unreadCount = 0
                    self?.alerts = []
                }
            },
            onError: { _ in }
        )
    }
}

// ── Main view ─────────────────────────────────────────────────────────────

struct AlertsView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @StateObject private var vm = AlertsViewModel()
    @State private var collapsed: Set<String> = []
    @State private var replying: String? = nil
    @State private var savedCommands: [IosSavedCommand] = []

    var body: some View {
        Group {
            if store.profiles.isEmpty {
                noProfilesView
            } else if vm.isLoading && vm.alerts.isEmpty {
                LoadingIndicator(message: "Loading alerts…")
            } else if let err = vm.error, vm.alerts.isEmpty {
                ErrorCard(message: err) { vm.refresh() }
            } else {
                alertListView
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
                        profile: store.profiles.first,
                        anchor: "alerts"
                    )
                    ReachabilityDotView(profile: store.profiles.first)
                }
            }
        }
        .onAppear {
            vm.load(from: store.profiles)
            if let p = store.profiles.first {
                IosQuickCommands.shared.loadSaved(profile: p) { list in
                    DispatchQueue.main.async { savedCommands = list }
                }
            }
        }
        .onDisappear {
            vm.stopPolling()
        }
        .onChange(of: store.profiles) { newProfiles in
            vm.load(from: newProfiles)
        }
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
                Text("🔔 \(vm.tabAlerts.count) \(vm.tabAlerts.count == 1 ? "alert" : "alerts")")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurface)
                Spacer()
                controlBtn(vm.sortMode == .session ? "⏷ by session" : "🕒 chronological") {
                    vm.sortMode = vm.sortMode == .session ? .chrono : .session
                }
                .accessibilityLabel("Toggle sort: by session or chronological")
                controlBtn("✕") { vm.dismissAll() }
                    .accessibilityLabel("Dismiss all")
                controlBtn("🔕") { vm.dismissAll() }
                    .accessibilityLabel("Mute all")
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

    @ViewBuilder
    private func severityChip(_ filter: AlertsViewModel.AlertSeverityFilter) -> some View {
        let selected = vm.severityFilter == filter
        let count = vm.chipCount(for: filter)
        let (emoji, color): (String, Color) = {
            switch filter {
            case .all:     return ("", DatawatchColors.onSurfaceMuted)
            case .prompt:  return ("🟡 ", DatawatchColors.warning)
            case .error:   return ("🔴 ", DatawatchColors.error)
            case .warning: return ("🟠 ", DatawatchColors.warning)
            case .info:    return ("⚪ ", DatawatchColors.onSurfaceMuted)
            }
        }()
        let label = "\(emoji)\(filter.rawValue) ×\(count)"
        Button { vm.severityFilter = filter } label: {
            Text(label)
                .font(DatawatchFonts.badge)
                .foregroundStyle(selected ? DatawatchColors.background : color)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(selected ? color : color.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(color.opacity(0.4), lineWidth: 1))
        }
    }

    // ── Alert list ────────────────────────────────────────────────────────

    private var alertListView: some View {
        VStack(spacing: 0) {
            tabRow
            filterBar
            List {
                if vm.filteredAlerts.isEmpty {
                    HStack {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "bell.slash")
                                .font(.system(.title))
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                .accessibilityHidden(true)
                            Text("No \(vm.selectedTab.rawValue.lowercased()) alerts")
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
                        }
                        .listRowBackground(DatawatchColors.surface)
                        .listRowSeparatorTint(DatawatchColors.border)
                        .listRowInsets(EdgeInsets())
                    }
                } else {
                    ForEach(vm.groups) { group in
                        Section {
                            if !collapsed.contains(group.id) {
                                ForEach(Array(group.alerts.enumerated()), id: \.element.id) { index, alert in
                                    VStack(alignment: .leading, spacing: 0) {
                                        alertRow(alert)
                                        if index == 0, group.session?.state == .waiting, let s = group.session {
                                            quickReply(for: s)
                                                .padding(.horizontal, 12)
                                                .padding(.bottom, 8)
                                        }
                                    }
                                    .listRowBackground(DatawatchColors.surface)
                                    .listRowSeparatorTint(DatawatchColors.border)
                                    .listRowInsets(EdgeInsets())
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
    }

    private func alertRow(_ alert: DatawatchShared.Alert) -> some View {
        AlertRow(alert: alert)
            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                Button(role: .destructive) {
                    vm.dismiss(alert: alert)
                } label: {
                    Label("Dismiss", systemImage: "xmark.circle")
                }
                .tint(DatawatchColors.error)
            }
    }

    // ── By-session card header (PWA renderSessionCard) ─────────────────────

    private func groupHeader(_ group: AlertsViewModel.AlertGroup) -> some View {
        let promptCount = group.alerts.filter { vm.isPrompt($0) }.count
        let last = group.alerts.first.map { alertClock($0) } ?? "—"
        return HStack(spacing: 8) {
            Image(systemName: collapsed.contains(group.id) ? "chevron.right" : "chevron.down")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
            sessionLabel(for: group.session, isSystem: group.isSystem, fallbackId: group.id)
            if let state = stateText(group.session) {
                Text(state.text)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(state.color)
            }
            HStack(spacing: 4) {
                Text("\(group.alerts.count) \(group.alerts.count == 1 ? "alert" : "alerts")")
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                if promptCount > 0 {
                    Text("· 🟡 \(promptCount)")
                        .fontWeight(.bold)
                        .foregroundStyle(DatawatchColors.warning)
                }
            }
            .font(DatawatchFonts.labelSmall)
            Spacer(minLength: 4)
            Text("last \(last)")
                .font(DatawatchFonts.terminalSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
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

    /// Session name as a link to the session (PWA sessLink); plain text for System/unknown.
    @ViewBuilder
    private func sessionLabel(for session: DwSession?, isSystem: Bool, fallbackId: String?) -> some View {
        if isSystem {
            Text("System")
                .font(DatawatchFonts.bodyMedium.weight(.bold))
                .foregroundStyle(DatawatchColors.onSurface)
        } else if let session, let profile = vm.profile {
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

    // ── Quick reply on the latest alert of a waiting session ─────────────

    private func quickReply(for session: DwSession) -> some View {
        Menu {
            Button("approve") { sendReply("yes", to: session) }
            Button("reject") { sendReply("no", to: session) }
            Button("continue") { sendReply("continue", to: session) }
            Button("skip") { sendReply("skip", to: session) }
            Button("ESC") { sendReply("__esc__", to: session) }
            if !savedCommands.isEmpty {
                Section("Saved") {
                    ForEach(savedCommands, id: \.name) { cmd in
                        Button(cmd.name) { sendReply(cmd.command, to: session) }
                    }
                }
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
        guard let profile = vm.profile else { return }
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
                    if !alert.read {
                        Circle()
                            .fill(DatawatchColors.primary)
                            .frame(width: 7, height: 7)
                            .accessibilityLabel("Unread")
                    }
                }

                // Title
                Text(alert.title.isEmpty ? alert.type : alert.title)
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(alert.read ? DatawatchColors.onSurfaceMuted : DatawatchColors.onSurface)
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

    private var isPrompt: Bool {
        alert.type.contains("input") || alert.type.contains("prompt") || alert.type.contains("waiting")
    }

    private var borderColor: Color {
        if isPrompt { return DatawatchColors.warning }
        switch alert.severity {
        case .error: return DatawatchColors.error
        case .warning: return DatawatchColors.warning
        default: return DatawatchColors.border
        }
    }

    private var alertBackground: Color {
        if isPrompt { return DatawatchColors.warning.opacity(0.06) }
        switch alert.severity {
        case .error:   return DatawatchColors.error.opacity(0.05)
        case .warning: return DatawatchColors.warning.opacity(0.04)
        default:       return Color.clear
        }
    }

    @ViewBuilder
    private var badgeView: some View {
        if isPrompt {
            badgeLabel("🟡 PROMPT", fg: Color(hex: 0x0F1117), bg: DatawatchColors.warning)
        } else {
            switch alert.severity {
            case .error:
                badgeLabel("🔴 ERROR", fg: .white, bg: DatawatchColors.error)
            case .warning:
                badgeLabel("🟠 WARNING", fg: Color(hex: 0x0F1117), bg: DatawatchColors.warning)
            default:
                badgeLabel("⚪ info", fg: DatawatchColors.onSurfaceMuted, bg: DatawatchColors.surface2)
            }
        }
    }

    private func badgeLabel(_ text: String, fg: Color, bg: Color) -> some View {
        Text(text)
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
