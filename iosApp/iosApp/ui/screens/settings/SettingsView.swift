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
                    HStack {
                        Label("Version", systemImage: "info.circle")
                            .foregroundStyle(DatawatchColors.onSurface)
                        Spacer()
                        Text(appVersion)
                            .font(DatawatchFonts.labelSmall)
                            .foregroundStyle(DatawatchColors.onSurfaceMuted)
                    }
                    .listRowBackground(DatawatchColors.surface)
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(DatawatchColors.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(.principal) {
                    HeaderView(title: "Settings")
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
