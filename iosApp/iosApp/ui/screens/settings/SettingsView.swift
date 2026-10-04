import SwiftUI
import DatawatchShared

/// Settings tab: server profiles + app preferences.
struct SettingsView: View {
    @EnvironmentObject private var store: ServerProfileStore
    @AppStorage("biometricLockEnabled") private var biometricLockEnabled = false
    @State private var biometricAvailable = BiometricGate.isAvailable

    var body: some View {
        NavigationStack {
            List {
                // ── Servers ──────────────────────────────────────────────
                Section {
                    NavigationLink {
                        ServerProfileListView()
                            .environmentObject(store)
                            .navigationTitle("Servers")
                            .navigationBarTitleDisplayMode(.inline)
                            .background(DatawatchColors.background)
                    } label: {
                        Label {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Servers")
                                    .foregroundStyle(DatawatchColors.onSurface)
                                Text("\(store.profiles.count) configured")
                                    .font(DatawatchFonts.labelSmall)
                                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            }
                        } icon: {
                            Image(systemName: "server.rack")
                                .foregroundStyle(DatawatchColors.primary)
                        }
                    }
                    .listRowBackground(DatawatchColors.surface)
                }

                // ── Session ──────────────────────────────────────────────
                Section {
                    NavigationLink {
                        SettingsSessionView()
                            .environmentObject(store)
                    } label: {
                        Label {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Session")
                                    .foregroundStyle(DatawatchColors.onSurface)
                                Text("Summarizer, LLM selector")
                                    .font(DatawatchFonts.labelSmall)
                                    .foregroundStyle(DatawatchColors.onSurfaceMuted)
                            }
                        } icon: {
                            Image(systemName: "brain")
                                .foregroundStyle(DatawatchColors.primary)
                        }
                    }
                    .listRowBackground(DatawatchColors.surface)
                }

                // ── Alert rules (PWA Settings → Alert Rules) ─────────────
                if let profile = store.profiles.first {
                    Section {
                        NavigationLink {
                            AlertRulesView(profile: profile)
                        } label: {
                            Label {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Alert Rules")
                                        .foregroundStyle(DatawatchColors.onSurface)
                                    Text("Metric thresholds that raise alerts or scale")
                                        .font(DatawatchFonts.labelSmall)
                                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                }
                            } icon: {
                                Image(systemName: "bell.badge")
                                    .foregroundStyle(DatawatchColors.primary)
                            }
                        }
                        .listRowBackground(DatawatchColors.surface)
                    }

                    // ── Automata (PWA Settings → Automata; final grouping per D31) ──
                    Section {
                        NavigationLink {
                            OrchestratorGraphsView(profile: profile)
                        } label: {
                            Label {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Automata Orchestrator").foregroundStyle(DatawatchColors.onSurface)
                                    Text("Graphs that run several automata together")
                                        .font(DatawatchFonts.labelSmall)
                                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                }
                            } icon: {
                                Image(systemName: "point.3.connected.trianglepath.dotted")
                                    .foregroundStyle(DatawatchColors.primary)
                            }
                        }
                        .listRowBackground(DatawatchColors.surface)
                        NavigationLink {
                            PipelinesView(profile: profile)
                        } label: {
                            Label {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Pipeline Manager").foregroundStyle(DatawatchColors.onSurface)
                                    Text("Live pipelines and their task progress")
                                        .font(DatawatchFonts.labelSmall)
                                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                }
                            } icon: {
                                Image(systemName: "arrow.triangle.branch")
                                    .foregroundStyle(DatawatchColors.primary)
                            }
                        }
                        .listRowBackground(DatawatchColors.surface)
                    }
                }

                // ── Security ─────────────────────────────────────────────
                if biometricAvailable {
                    Section("Security") {
                        Toggle(isOn: $biometricLockEnabled) {
                            Label {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(biometricLabel)
                                        .foregroundStyle(DatawatchColors.onSurface)
                                    Text("Require authentication on launch")
                                        .font(DatawatchFonts.labelSmall)
                                        .foregroundStyle(DatawatchColors.onSurfaceMuted)
                                }
                            } icon: {
                                Image(systemName: biometricIcon)
                                    .foregroundStyle(DatawatchColors.primary)
                            }
                        }
                        .tint(DatawatchColors.primary)
                        .listRowBackground(DatawatchColors.surface)
                    }
                }

                // ── About ────────────────────────────────────────────────
                Section("About") {
                    // Compact Earthrise scene (PWA About: startScene compact; Android MatrixLogoAnimated).
                    VStack(spacing: 10) {
                        SplashSceneView(compact: true)
                            .frame(height: 220)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        SplashTextBlock(version: appVersion)
                    }
                    .padding(.vertical, 6)
                    .listRowBackground(DatawatchColors.surface)

                    HStack {
                        Label("Version", systemImage: "info.circle")
                            .foregroundStyle(DatawatchColors.onSurface)
                        Spacer()
                        Text(appVersion)
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .listRowBackground(DatawatchColors.surface)

                    Button {
                        if let url = URL(string: "https://github.com/dmz006/datawatch") {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        HStack {
                            Label("Project (server)", systemImage: "link")
                                .foregroundStyle(DatawatchColors.onSurface)
                            Spacer()
                            Text("github.com/dmz006/datawatch")
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.primary)
                        }
                    }
                    .listRowBackground(DatawatchColors.surface)

                    Button {
                        if let url = URL(string: "https://github.com/dmz006/datawatch-app") {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        HStack {
                            Label("Mobile app", systemImage: "link")
                                .foregroundStyle(DatawatchColors.onSurface)
                            Spacer()
                            Text("github.com/dmz006/datawatch-app")
                                .font(DatawatchFonts.labelSmall)
                                .foregroundStyle(DatawatchColors.primary)
                        }
                    }
                    .listRowBackground(DatawatchColors.surface)
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
                        DocsLinkButton(
                            profile: store.profiles.first,
                            anchor: "settings"
                        )
                        AlertsBellButton()
                        ReachabilityDotView(profile: store.profiles.first)
                    }
                }
            }
        }
    }

    private var biometricLabel: String {
        switch BiometricGate.biometricType {
        case .faceID: return "Face ID Lock"
        case .touchID: return "Touch ID Lock"
        default: return "Biometric Lock"
        }
    }

    private var biometricIcon: String {
        switch BiometricGate.biometricType {
        case .faceID: return "faceid"
        case .touchID: return "touchid"
        default: return "lock.fill"
        }
    }

    private var appVersion: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(v) (\(b))"
    }
}

#if DEBUG
#Preview {
    SettingsView()
        .environmentObject(ServerProfileStore())
        .preferredColorScheme(.dark)
}
#endif
