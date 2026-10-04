import SwiftUI
import DatawatchShared

// Settings › About group (PWA About + API cards; D80a subsystem reload + MCP
// cards; D90a encryption status).

/// Opens `<server>/<path>` in the system browser (docs / API links).
func openServerPath(_ profile: ServerProfile?, _ path: String) {
    guard let profile else { return }
    var base = profile.baseUrl
    if base.hasSuffix("/") { base = String(base.dropLast()) }
    if let url = URL(string: base + path) {
        UIApplication.shared.open(url)
    }
}

private func openExternal(_ s: String) {
    if let url = URL(string: s) { UIApplication.shared.open(url) }
}

// MARK: - About

struct SettingsAboutCard: View {
    let profile: ServerProfile?

    @State private var info: IosAboutInfo?
    @State private var infoError: String?
    @State private var updateStatus: String?
    @State private var updateAvailable = false
    @State private var busy = false
    @State private var confirmRestart = false
    @State private var confirmKill = false
    @State private var confirmUpdate = false
    @State private var replaySplash = false

    var body: some View {
        List {
            Section {
                VStack(spacing: 10) {
                    SplashSceneView(compact: true)
                        .frame(height: 200)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    SplashTextBlock(version: appVersion)
                }
                .padding(.vertical, 6)
                // D59a (Android Destinations.SplashReplay): ungated full-screen replay.
                Button {
                    replaySplash = true
                } label: {
                    Label("Replay splash", systemImage: "play.circle")
                        .foregroundStyle(DatawatchColors.primary)
                }
            }
            .listRowBackground(DatawatchColors.surface)

            Section { preferencesRows }
                .listRowBackground(DatawatchColors.surface)

            if profile != nil {
                Section { serverRows }
                    .listRowBackground(DatawatchColors.surface)
                Section { orphanRows }
                    .listRowBackground(DatawatchColors.surface)
            }

            Section { linkRows }
                .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .task { load() }
        .refreshable { load() }
        .fullScreenCover(isPresented: $replaySplash) {
            LaunchSplashView(replay: true) { replaySplash = false }
        }
        .confirmationDialog("Restart the datawatch daemon?", isPresented: $confirmRestart, titleVisibility: .visible) {
            Button("Restart", role: .destructive) { restart() }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog("Kill all orphaned tmux sessions?", isPresented: $confirmKill, titleVisibility: .visible) {
            Button("Kill all", role: .destructive) { killOrphans() }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog("Install the update and restart the daemon?", isPresented: $confirmUpdate, titleVisibility: .visible) {
            Button("Update", role: .destructive) { runUpdate() }
            Button("Cancel", role: .cancel) {}
        }
    }

    // MARK: rows

    @ViewBuilder
    private var preferencesRows: some View {
        // iOS: per-app language lives in the system Settings app (D4a native control).
        Button { openSystemSettings() } label: {
            valueRow("Language", L("System setting"), icon: "globe")
        }
        valueRow("Theme", L("Dark"), icon: "circle.lefthalf.filled")
    }

    @ViewBuilder
    private var serverRows: some View {
        valueRow("Version", info?.serverVersion ?? infoError ?? "—", icon: "server.rack")
        valueRow("App version", appVersion, icon: "iphone")
        HStack {
            Label("Update", systemImage: "arrow.down.circle")
                .foregroundStyle(DatawatchColors.onSurface)
            Spacer()
            if let updateStatus {
                Text(verbatim: updateStatus)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            if updateAvailable {
                Button("Update") { confirmUpdate = true }
                    .buttonStyle(.borderless)
                    .foregroundStyle(DatawatchColors.primary)
            } else {
                Button("Check now") { checkUpdate() }
                    .buttonStyle(.borderless)
                    .foregroundStyle(DatawatchColors.primary)
            }
        }
        .disabled(busy)
        HStack {
            Label("Daemon", systemImage: "power")
                .foregroundStyle(DatawatchColors.onSurface)
            Spacer()
            Button("Restart") { confirmRestart = true }
                .buttonStyle(.borderless)
                .foregroundStyle(DatawatchColors.primary)
        }
        valueRow("Sessions", sessionsText, icon: "terminal")
        valueRow("Uptime", uptimeText, icon: "clock")
    }

    @ViewBuilder
    private var orphanRows: some View {
        let orphans: [String] = info?.orphanedTmux ?? []
        VStack(alignment: .leading, spacing: 6) {
            Text("Orphaned tmux sessions")
                .foregroundStyle(DatawatchColors.onSurface)
            if info == nil {
                Text("Checking…")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            } else if orphans.isEmpty {
                Text("No orphan tmux sessions detected.")
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            } else {
                Text(verbatim: orphans.joined(separator: " · "))
                    .font(DatawatchFonts.terminalSmall)
                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
            }
            Button(orphans.isEmpty ? L("Kill all orphaned") : L("Kill all orphaned") + " (\(orphans.count))") {
                confirmKill = true
            }
            .buttonStyle(.borderless)
            .foregroundStyle(orphans.isEmpty ? DatawatchColors.onSurfaceMuted : DatawatchColors.error)
            .disabled(orphans.isEmpty || busy)
        }
    }

    @ViewBuilder
    private var linkRows: some View {
        Button { openExternal("https://github.com/dmz006/datawatch") } label: {
            valueRow("Project", "github.com/dmz006/datawatch", icon: "link")
        }
        Button { openExternal("https://github.com/dmz006/datawatch-app") } label: {
            valueRow("Mobile app", "github.com/dmz006/datawatch-app", icon: "link")
        }
        if profile != nil {
            Button { openServerPath(profile, "/diagrams.html") } label: {
                valueRow("Docs", L("System documentation & diagrams"), icon: "book")
            }
        }
    }

    private func valueRow(_ label: String, _ value: String, icon: String) -> some View {
        HStack {
            Label(L(label), systemImage: icon)
                .foregroundStyle(DatawatchColors.onSurface)
            Spacer()
            Text(verbatim: value)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.trailing)
        }
    }

    // MARK: derived

    private var appVersion: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(v) (\(b))"
    }

    private var sessionsText: String {
        guard let info else { return "—" }
        return "\(info.sessionCount) " + L("in store")
    }

    private var uptimeText: String {
        guard let info, info.uptimeSeconds > 0 else { return "—" }
        let s: Int64 = info.uptimeSeconds
        let d: Int64 = s / 86_400
        let h: Int64 = (s % 86_400) / 3_600
        let m: Int64 = (s % 3_600) / 60
        if d > 0 { return "\(d)d \(h)h" }
        if h > 0 { return "\(h)h \(m)m" }
        return "\(m)m"
    }

    // MARK: actions

    private func load() {
        guard let profile else { return }
        IosSettingsConfig.shared.about(profile: profile, onSuccess: { i in
            DispatchQueue.main.async {
                info = i
                infoError = nil
            }
        }, onError: { msg in
            DispatchQueue.main.async { infoError = msg }
        })
    }

    private func checkUpdate() {
        guard let profile else { return }
        busy = true
        updateStatus = L("Checking…")
        IosSettingsConfig.shared.checkUpdate(profile: profile, onSuccess: { status, version in
            DispatchQueue.main.async {
                busy = false
                updateAvailable = status == "update_available"
                updateStatus = updateAvailable ? version : L("Up to date")
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                busy = false
                updateStatus = msg
            }
        })
    }

    private func runUpdate() {
        guard let profile else { return }
        busy = true
        updateStatus = L("Installing…")
        IosSettingsConfig.shared.runUpdate(profile: profile, onSuccess: { status in
            DispatchQueue.main.async {
                busy = false
                updateAvailable = false
                updateStatus = status
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                busy = false
                updateStatus = msg
            }
        })
    }

    private func restart() {
        guard let profile else { return }
        IosSettingsConfig.shared.restartDaemon(profile: profile) { err in
            DispatchQueue.main.async {
                infoError = err
                DispatchQueue.main.asyncAfter(deadline: .now() + 4) { load() }
            }
        }
    }

    private func killOrphans() {
        guard let profile else { return }
        busy = true
        IosSettingsConfig.shared.killOrphans(profile: profile) { err in
            DispatchQueue.main.async {
                busy = false
                if let err { infoError = err }
                load()
            }
        }
    }
}

// MARK: - API links (PWA 'api' card)

struct SettingsApiLinksCard: View {
    let profile: ServerProfile

    var body: some View {
        List {
            Section {
                link("Swagger UI", "/api/docs")
                link("OpenAPI Spec", "/api/openapi.yaml")
                link("System documentation & diagrams", "/diagrams.html")
                link("MCP Tools", "/api/mcp/docs")
            }
            .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    private func link(_ label: String, _ path: String) -> some View {
        Button { openServerPath(profile, path) } label: {
            HStack {
                Text(L(label)).foregroundStyle(DatawatchColors.onSurface)
                Spacer()
                Text(verbatim: path)
                    .font(DatawatchFonts.labelSmall)
                    .foregroundStyle(DatawatchColors.primary)
            }
        }
    }
}

// MARK: - MCP tools (D80a)

struct SettingsMcpToolsCard: View {
    let profile: ServerProfile
    @State private var tools: [IosMcpTool] = []
    @State private var isLoading = true
    @State private var error: String?
    @State private var query = ""

    private var filtered: [IosMcpTool] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        if q.isEmpty { return tools }
        return tools.filter { $0.name.lowercased().contains(q) || $0.description_.lowercased().contains(q) }
    }

    var body: some View {
        List {
            Section {
                if isLoading {
                    ProgressView()
                } else if let error {
                    Text(error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
                } else {
                    ForEach(filtered, id: \.name) { tool in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(verbatim: tool.name)
                                .font(DatawatchFonts.terminalSmall)
                                .foregroundStyle(DatawatchColors.onSurface)
                            if !tool.description_.isEmpty {
                                Text(verbatim: tool.description_)
                                    .font(DatawatchFonts.labelSmall)
                                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                    .lineLimit(3)
                            }
                        }
                    }
                }
            } header: {
                Text("\(tools.count) tools")
            }
            .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .searchable(text: $query)
        .task { load() }
    }

    private func load() {
        IosSettingsConfig.shared.mcpTools(profile: profile, onSuccess: { list in
            DispatchQueue.main.async {
                tools = list
                isLoading = false
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                error = msg
                isLoading = false
            }
        })
    }
}

// MARK: - MCP channel (D80a)

struct SettingsMcpChannelCard: View {
    let profile: ServerProfile
    @State private var text: String?
    @State private var error: String?

    var body: some View {
        ScrollView {
            Group {
                if let text {
                    Text(verbatim: text)
                        .font(DatawatchFonts.terminalSmall)
                        .foregroundStyle(DatawatchColors.onSurface)
                        .textSelection(.enabled)
                } else if let error {
                    Text(error).font(DatawatchFonts.labelSmall).foregroundStyle(DatawatchColors.error)
                } else {
                    ProgressView()
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
        .task {
            IosSettingsConfig.shared.channelInfo(profile: profile, onSuccess: { s in
                DispatchQueue.main.async { text = s }
            }, onError: { msg in
                DispatchQueue.main.async { error = msg }
            })
        }
    }
}

// MARK: - Subsystem reload (D80a)

struct SettingsSubsystemReloadCard: View {
    let profile: ServerProfile
    @State private var busy: String?
    @State private var result: String?
    @State private var failed = false

    var body: some View {
        List {
            Section {
                ForEach(["config", "filters", "memory"], id: \.self) { sub in
                    Button {
                        reload(sub)
                    } label: {
                        HStack {
                            Text(L("Reload") + " " + sub).foregroundStyle(DatawatchColors.primary)
                            Spacer()
                            if busy == sub { ProgressView().controlSize(.small) }
                        }
                    }
                    .disabled(busy != nil)
                }
            } footer: {
                Text("Hot-reloads a subsystem without restarting the daemon.")
            }
            .listRowBackground(DatawatchColors.surface)
            if let result {
                Section {
                    Text(verbatim: result)
                        .font(DatawatchFonts.labelSmall)
                        .foregroundStyle(failed ? DatawatchColors.error : DatawatchColors.onSurfaceMuted)
                }
                .listRowBackground(DatawatchColors.surface)
            }
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
    }

    private func reload(_ sub: String) {
        busy = sub
        IosSettingsConfig.shared.reload(profile: profile, subsystem: sub, onSuccess: { msg in
            DispatchQueue.main.async {
                busy = nil
                failed = false
                result = msg
            }
        }, onError: { msg in
            DispatchQueue.main.async {
                busy = nil
                failed = true
                result = msg
            }
        })
    }
}

// MARK: - Encryption status (D90a: Data Protection class + Keychain state)

struct SettingsEncryptionCard: View {
    @EnvironmentObject private var store: ServerProfileStore
    let profile: ServerProfile

    @State private var files: [ProtectedFileInfo] = []
    @State private var serverStatus: String?

    var body: some View {
        List {
            Section {
                row("Protected data available", UIApplication.shared.isProtectedDataAvailable ? L("Yes") : L("No"))
                ForEach(files) { f in
                    row(f.name, f.protection)
                }
            } header: {
                Text("Data Protection (this device)")
            } footer: {
                Text("Local data is encrypted by iOS Data Protection; files are readable only after the device is unlocked.")
            }
            .listRowBackground(DatawatchColors.surface)

            Section {
                row("Item accessibility", L("When unlocked, this device only"))
                row("Server tokens stored", "\(tokenCount) / \(store.profiles.count)")
            } header: {
                Text("Keychain")
            }
            .listRowBackground(DatawatchColors.surface)

            Section {
                row("Server secure mode", serverStatus ?? "—")
            } header: {
                Text("Server")
            }
            .listRowBackground(DatawatchColors.surface)
        }
        .listStyle(.insetGrouped)
        .scrollContentBackground(.hidden)
        .task { load() }
    }

    /// Profiles whose bearer token is present in the Keychain (value never read into UI).
    private var tokenCount: Int {
        store.profiles.filter { p in
            !p.bearerTokenRef.isEmpty && IosServiceLocator.shared.getToken(alias: p.bearerTokenRef) != nil
        }.count
    }

    private func row(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(L(label)).foregroundStyle(DatawatchColors.onSurface)
            Spacer()
            Text(verbatim: value)
                .font(DatawatchFonts.labelSmall)
                .foregroundStyle(DatawatchColors.onSurfaceMuted)
                .multilineTextAlignment(.trailing)
        }
    }

    private func load() {
        files = SettingsEncryptionCard.protectionClasses()
        IosSettingsConfig.shared.serverEncryption(profile: profile, onSuccess: { secure, encrypted, total in
            DispatchQueue.main.async {
                let on: Bool = secure.boolValue
                serverStatus = (on ? L("On") : L("Off")) + " · \(encrypted.intValue)/\(total.intValue) " + L("files encrypted")
            }
        }, onError: { msg in
            DispatchQueue.main.async { serverStatus = msg }
        })
    }

    /// File-protection class of the app's local stores (Application Support).
    static func protectionClasses() -> [ProtectedFileInfo] {
        let fm = FileManager.default
        guard let base = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first,
              let walker = fm.enumerator(at: base, includingPropertiesForKeys: nil) else { return [] }
        var out: [ProtectedFileInfo] = []
        for case let url as URL in walker {
            var isDir: ObjCBool = false
            guard fm.fileExists(atPath: url.path, isDirectory: &isDir), !isDir.boolValue else { continue }
            let attrs = (try? fm.attributesOfItem(atPath: url.path)) ?? [:]
            let cls = attrs[.protectionKey] as? FileProtectionType
            out.append(ProtectedFileInfo(name: url.lastPathComponent, protection: describe(cls)))
            if out.count >= 8 { break }
        }
        return out
    }

    private static func describe(_ cls: FileProtectionType?) -> String {
        guard let cls else { return L("Default") }
        switch cls {
        case .complete: return L("Complete")
        case .completeUnlessOpen: return L("Complete unless open")
        case .completeUntilFirstUserAuthentication: return L("Until first unlock")
        case .none: return L("None")
        default: return cls.rawValue
        }
    }
}

struct ProtectedFileInfo: Identifiable {
    let name: String
    let protection: String
    var id: String { name }
}
