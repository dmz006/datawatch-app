import SwiftUI
import UserNotifications
import DatawatchShared

/// Dispatcher for bespoke Settings cards (anything not schema/list driven).
struct SettingsCustomCardView: View {
    @EnvironmentObject private var store: ServerProfileStore
    let card: SettingsCustomCard
    let profile: ServerProfile?

    var body: some View {
        switch card {
        case .servers:
            ServerProfileListView().environmentObject(store)
        case .security:
            SettingsSecurityCard()
        case .notifications:
            SettingsNotificationsCard()
        case .push:
            if let p = profile { SettingsPushCard(profile: p) } else { SettingsNoServerView() }
        case .about:
            SettingsAboutCard(profile: profile)
        case .observerQuicklink:
            SettingsObserverQuicklinkCard()
        default:
            serverCard
        }
    }

    @ViewBuilder
    private var serverCard: some View {
        if let p = profile {
            SettingsServerCustomCard(card: card, profile: p)
        } else {
            SettingsNoServerView()
        }
    }
}

/// Server-backed bespoke cards (split out to keep each body small for the type checker).
private struct SettingsServerCustomCard: View {
    let card: SettingsCustomCard
    let profile: ServerProfile

    var body: some View {
        switch card {
        case .commBackends: SettingsCommBackendsCard(profile: profile)
        case .alertRules: AlertRulesView(profile: profile)
        case .savedCommands: SavedCommandsView(profile: profile)
        case .outputFilters: FiltersView(profile: profile)
        case .pipelineManager: PipelinesView(profile: profile)
        case .orchestratorGraphs: OrchestratorGraphsView(profile: profile)
        case .automataTypes: SettingsAutomataTypesView(profile: profile)
        case .identity: SettingsIdentityCard(profile: profile)
        case .algorithmMode: SettingsAlgorithmModeCard(profile: profile)
        case .docsSearch: SettingsDocsSearchCard(profile: profile)
        case .rawConfig: SettingsRawConfigCard(profile: profile)
        case .apiLinks: SettingsApiLinksCard(profile: profile)
        case .mcpTools: SettingsMcpToolsCard(profile: profile)
        case .mcpChannel: SettingsMcpChannelCard(profile: profile)
        case .subsystemReload: SettingsSubsystemReloadCard(profile: profile)
        case .communityPlugins: SettingsCommunityPluginsCard(profile: profile)
        case .encryption: SettingsEncryptionCard(profile: profile)
        case .exitHooks: SettingsExitHooksCard(profile: profile)
        case .workQueue: SettingsWorkQueueCard(profile: profile)
        case .fileService: SettingsFileServiceCard(profile: profile)
        default: EmptyView()
        }
    }
}

// MARK: - Security (iOS: biometric lock)

private struct SettingsSecurityCard: View {
    @AppStorage(BiometricGate.enabledKey) private var biometricLockEnabled = false
    @AppStorage(BiometricGate.siriAuthKey) private var siriRequiresAuth = true

    var body: some View {
        List {
            Section {
                if BiometricGate.isAvailable {
                    Toggle(isOn: lockBinding) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L(biometricLabel)).foregroundStyle(DatawatchColors.onSurface)
                            Text("Require authentication on launch and when returning to the app")
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                    }
                    .tint(DatawatchColors.primary)
                }
                if BiometricGate.canAuthenticate {
                    Toggle(isOn: $siriRequiresAuth) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(L(siriAuthLabel)).foregroundStyle(DatawatchColors.onSurface)
                            Text("Confirm it's you before Siri or Shortcuts sends a message to a session")
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                    }
                    .tint(DatawatchColors.primary)
                }
                if !BiometricGate.isAvailable {
                    Text("Biometric authentication is not available on this device.")
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    /// Turning the lock on asks for authentication first, so the user proves the
    /// device can unlock it before the app starts locking on launch/foreground.
    /// Turning it off is immediate (the app is already unlocked to get here).
    private var lockBinding: Binding<Bool> {
        Binding(
            get: { biometricLockEnabled },
            set: { on in
                guard on else { biometricLockEnabled = false; return }
                BiometricGate.authenticate(reason: L("Enable app lock")) { ok in
                    biometricLockEnabled = ok
                }
            }
        )
    }

    private var siriAuthLabel: String {
        switch BiometricGate.biometricType {
        case .faceID: return "Require Face ID for Siri"
        case .touchID: return "Require Touch ID for Siri"
        default: return "Require passcode for Siri"
        }
    }

    private var biometricLabel: String {
        switch BiometricGate.biometricType {
        case .faceID: return "Face ID Lock"
        case .touchID: return "Touch ID Lock"
        default: return "Biometric Lock"
        }
    }
}

// MARK: - Notifications (PWA gc_notifs: permission status + request)

private struct SettingsNotificationsCard: View {
    @State private var status: UNAuthorizationStatus = .notDetermined

    var body: some View {
        List {
            Section {
                HStack {
                    Text("Status").foregroundStyle(DatawatchColors.onSurface)
                    Spacer()
                    Text(L(statusText))
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(status == .authorized ? DatawatchColors.success : DatawatchColors.onSurfaceMuted)
                }
                if status == .notDetermined {
                    Button("Request Permission") { request() }
                        .foregroundStyle(DatawatchColors.primary)
                } else {
                    Button("Open iOS Settings") { openSystemSettings() }
                        .foregroundStyle(DatawatchColors.primary)
                }
            }
            .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .task { refresh() }
    }

    private var statusText: String {
        switch status {
        case .authorized, .provisional, .ephemeral: return "Notifications enabled"
        case .denied: return "Notifications blocked (check iOS Settings)"
        default: return "Notifications not yet requested"
        }
    }

    private func refresh() {
        UNUserNotificationCenter.current().getNotificationSettings { s in
            DispatchQueue.main.async { status = s.authorizationStatus }
        }
    }

    private func request() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            DispatchQueue.main.async {
                if granted { UIApplication.shared.registerForRemoteNotifications() }
                refresh()
            }
        }
    }
}

func openSystemSettings() {
    if let url = URL(string: UIApplication.openSettingsURLString) {
        UIApplication.shared.open(url)
    }
}

// MARK: - Push Notifications (D88c: APNs registration status + send test)

private struct SettingsPushCard: View {
    let profile: ServerProfile
    @State private var tokenPresent = false
    @State private var registered = false
    @State private var message: String?
    @State private var busy = false

    var body: some View {
        List {
            Section {
                statusRow("APNs device token", ok: tokenPresent)
                statusRow("Registered with this server", ok: registered)
            } footer: {
                Text("iOS delivers datawatch alerts through Apple Push Notification service.")
            }
            .listRowBackground(DatawatchColors.surface)
            Section {
                Button("Re-register this device") { reregister() }
                    .foregroundStyle(DatawatchColors.primary)
                Button("Send test notification") { sendTest() }
                    .foregroundStyle(DatawatchColors.primary)
                if busy { ProgressView() }
                if let message {
                    Text(message)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                }
            }
            .disabled(busy)
            .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .task { refresh() }
    }

    private func statusRow(_ label: String, ok: Bool) -> some View {
        HStack {
            Text(L(label)).foregroundStyle(DatawatchColors.onSurface)
            Spacer()
            Image(systemName: ok ? "checkmark.circle.fill" : "xmark.circle")
                .foregroundStyle(ok ? DatawatchColors.success : DatawatchColors.onSurfaceMuted)
                .accessibilityLabel(ok ? Text("Yes") : Text("No"))
        }
    }

    private func refresh() {
        tokenPresent = IosSettingsConfig.shared.apnsTokenPresent()
        registered = IosSettingsConfig.shared.apnsRegistered(profile: profile)
    }

    private func reregister() {
        busy = true
        UIApplication.shared.registerForRemoteNotifications()
        IosServiceLocator.shared.reregisterAllProfiles {
            DispatchQueue.main.async {
                busy = false
                refresh()
                message = L("Registration refreshed.")
            }
        }
    }

    private func sendTest() {
        busy = true
        IosSettingsConfig.shared.sendTestPush(profile: profile) { err in
            DispatchQueue.main.async {
                busy = false
                message = err.map { L("Test failed") + ": " + L($0) } ?? L("Test notification sent.")
            }
        }
    }
}

// MARK: - Communication Configuration (PWA loadConfigStatus + BACKEND_FIELDS)

private struct SettingsCommBackendsCard: View {
    let profile: ServerProfile
    @State private var values: [String: String] = [:]
    @State private var isLoading = true
    @State private var error: String?
    @State private var restartNeeded = false

    var body: some View {
        List {
            Section {
                if isLoading {
                    CardSkeleton()
                } else {
                    ForEach(SettingsCatalog.commServices, id: \.self) { svc in
                        backendRow(svc)
                    }
                }
            }
            .listRowBackground(DatawatchColors.surface)
            // PWA: "Signal Device" row (status + Link Device → QR) inside this card.
            SignalDeviceSection(profile: profile)
            if restartNeeded {
                Section { RestartNeededRow(profile: profile) }
            }
            if let error {
                Section {
                    Text(error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
                }
                .listRowBackground(DatawatchColors.surface)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .task { load() }
        .refreshable { load() }
    }

    private func configured(_ svc: String) -> Bool {
        let keys = SettingsCatalog.credentialKeys(svc)
        if keys.isEmpty { return true }
        return keys.contains { k in
            let v = values[svc + "." + k] ?? ""
            return !v.isEmpty
        }
    }

    private func label(_ svc: String) -> String {
        svc.replacingOccurrences(of: "_", with: " ").capitalized
    }

    @ViewBuilder
    private func backendRow(_ svc: String) -> some View {
        let on: Bool = (values[svc + ".enabled"] ?? "").lowercased() == "true"
        HStack {
            NavigationLink {
                SettingsConfigCardView(profile: profile, fields: SettingsCatalog.backendFields(svc), extra: .none)
                    .background(DatawatchColors.background)
                    .navigationTitle(L("Configure") + " " + label(svc))
                    .navigationBarTitleDisplayMode(.inline)
            } label: {
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: label(svc)).foregroundStyle(DatawatchColors.onSurface)
                    if !configured(svc) {
                        Text("not configured")
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                }
            }
            if configured(svc) {
                Toggle("", isOn: Binding(get: { on }, set: { setEnabled(svc, $0) }))
                    .labelsHidden()
                    .tint(DatawatchColors.primary)
            }
        }
    }

    private func load() {
        IosSettingsConfig.shared.load(profile: profile, onSuccess: { map in
            DispatchQueue.main.async {
                values = map
                isLoading = false
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                isLoading = false
                error = msg
            }
        })
    }

    private func setEnabled(_ svc: String, _ on: Bool) {
        let key = svc + ".enabled"
        values[key] = on ? "true" : "false"
        IosSettingsConfig.shared.write(profile: profile, key: key, kind: "toggle", value: on ? "true" : "false") { err in
            DispatchQueue.main.async {
                if let err {
                    error = err
                    load()
                } else {
                    restartNeeded = true
                }
            }
        }
    }
}

// MARK: - Community Plugins (web UI GH#191 / datawatch v8.66; Android parity)

/// Browse a registry's plugins and install one. Registries come from Skill
/// Registries: "community" by default, a picker when there is more than one, and a
/// Connect action when the registry isn't connected (shared `CommunityPlugins`).
struct SettingsCommunityPluginsCard: View {
    let profile: ServerProfile?
    @State private var registries: [String]?
    @State private var registry: String?
    @State private var plugins: [IosCommunityPlugin]?
    @State private var notConnected = false
    @State private var connecting = false
    @State private var message: String?
    @State private var failed = false
    @State private var installing: Set<String> = []
    @State private var installed: Set<String> = []

    var body: some View {
        List {
            Section {
                if let registries, registries.count > 1 {
                    Picker(selection: Binding(get: { registry ?? "" }, set: { select($0) })) {
                        ForEach(registries, id: \.self) { Text(verbatim: $0).tag($0) }
                    } label: {
                        Text("Registry").foregroundStyle(DatawatchColors.onSurface)
                    }
                    .pickerStyle(.menu)
                    .tint(DatawatchColors.primary)
                }
                content
            } footer: {
                if let message {
                    Text(L(message)).foregroundStyle(failed ? DatawatchColors.error : DatawatchColors.success)
                }
            }
            .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .task { loadRegistries() }
        .refreshable { loadRegistries() }
    }

    @ViewBuilder
    private var content: some View {
        if registries == nil {
            CardSkeleton()
        } else if registries?.isEmpty == true {
            Text("No registries configured yet.")
                .font(DatawatchFonts.bodyMedium)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
        } else if notConnected {
            VStack(alignment: .leading, spacing: 8) {
                Text("This registry isn't connected yet.")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                Button(connecting ? L("Connecting…") : L("Connect")) { connect() }
                    .buttonStyle(.borderedProminent)
                    .tint(DatawatchColors.primary)
                    .disabled(connecting)
            }
        } else if let plugins {
            if plugins.isEmpty && message == nil {
                Text("No plugins found in this registry.")
                    .font(DatawatchFonts.bodyMedium)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            ForEach(plugins, id: \.name) { p in
                HStack(alignment: .center, spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: p.name).foregroundStyle(DatawatchColors.onSurface)
                        if !p.detail.isEmpty {
                            Text(verbatim: p.detail)
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                        }
                    }
                    Spacer(minLength: 4)
                    installButton(p.name)
                }
            }
        } else {
            CardSkeleton()
        }
    }

    @ViewBuilder
    private func installButton(_ name: String) -> some View {
        if installed.contains(name) {
            Text("Installed").font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.success)
        } else if installing.contains(name) {
            ProgressView().controlSize(.small)
        } else {
            Button("Install") { install(name) }
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.primary)
                .buttonStyle(.borderless)
        }
    }

    private func loadRegistries() {
        guard let profile else { registries = []; message = "No server connected"; failed = true; return }
        IosSettingsConfig.shared.pluginRegistries(profile: profile, onSuccess: { names in
            DispatchQueue.main.async {
                registries = names
                registry = CommunityPlugins.shared.pickRegistry(names: names, current: registry)
                loadPlugins()
            }
        }, onError: { err in
            DispatchQueue.main.async { registries = []; message = err; failed = true }
        })
    }

    private func select(_ name: String) {
        guard name != registry else { return }
        registry = name
        message = nil
        loadPlugins()
    }

    private func loadPlugins() {
        guard let profile, let registry else { return }
        plugins = nil
        notConnected = false
        IosSettingsConfig.shared.browsePlugins(profile: profile, registry: registry, onSuccess: { list in
            DispatchQueue.main.async { plugins = list }
        }, onError: { err in
            DispatchQueue.main.async {
                plugins = []
                if CommunityPlugins.shared.isNotConnected(error: err) {
                    notConnected = true
                } else {
                    message = L("Browse unavailable") + " — " + err
                    failed = true
                }
            }
        })
    }

    private func connect() {
        guard let profile, let registry else { return }
        connecting = true
        IosSettingsConfig.shared.connectPluginRegistry(profile: profile, registry: registry) { err in
            DispatchQueue.main.async {
                connecting = false
                if let err {
                    message = L("Connect failed") + ": " + err
                    failed = true
                } else {
                    loadPlugins()
                }
            }
        }
    }

    private func install(_ name: String) {
        guard let profile, let registry else { return }
        installing.insert(name)
        IosSettingsConfig.shared.installPlugin(profile: profile, registry: registry, name: name) { err in
            DispatchQueue.main.async {
                installing.remove(name)
                if let err {
                    message = L("Install failed") + ": " + err
                    failed = true
                } else {
                    installed.insert(name)
                    message = L("Installed") + " " + name
                    failed = false
                }
            }
        }
    }
}
