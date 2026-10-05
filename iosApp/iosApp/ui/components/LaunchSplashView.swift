import SwiftUI

/// Splash gating (parity D37a; Android `SplashGate`, PWA `cs_splash_time/version`):
/// show on first launch, after an app version change, or when more than 24 h have
/// passed since it was last shown. Settings › About › Replay splash is not gated.
enum SplashGate {
    private static let keyTime = "dw.splash.last_shown_ms"
    private static let keyVersion = "dw.splash.last_shown_version"
    static let intervalMs: Int64 = 24 * 60 * 60 * 1000

    static var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
    }

    static func shouldShow(nowMs: Int64, lastShownMs: Int64, lastVersion: String?, currentVersion: String) -> Bool {
        lastShownMs <= 0 || lastVersion != currentVersion || nowMs - lastShownMs >= intervalMs
    }

    /// Decides whether this cold launch shows the splash and, when it does, records
    /// the time + version (same point the PWA / Android write them).
    static func consume() -> Bool {
        let d = UserDefaults.standard
        let now: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
        let last: Int64 = (d.object(forKey: keyTime) as? NSNumber)?.int64Value ?? 0
        let lastVersion: String? = d.string(forKey: keyVersion)
        let show = shouldShow(nowMs: now, lastShownMs: last, lastVersion: lastVersion, currentVersion: appVersion)
        if show {
            d.set(NSNumber(value: now), forKey: keyTime)
            d.set(appVersion, forKey: keyVersion)
        }
        return show
    }
}

/// Full-screen brand splash (parity D59a; Android `MatrixSplashScreen`): Earthrise
/// scene, text block, and the status line "unlocking vault… → loading profiles… →
/// starting services… → ready" pulsing 0.45↔1.0 every 900 ms. Launch mode advances
/// on Android's timeline (800 + 1200 + 1200 ms, then "ready" for 180 ms) and the
/// caller fades it out; replay mode has no status line and a Close button.
struct LaunchSplashView: View {
    var replay: Bool = false
    var onFinished: () -> Void = {}

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var status: String = "unlocking vault…"
    @State private var bright = false

    var body: some View {
        ZStack {
            SplashArt.bg.ignoresSafeArea()
            SplashSceneView(compact: false)
                .ignoresSafeArea()
            VStack(spacing: 0) {
                Spacer()
                SplashTextBlock(version: SplashGate.appVersion)
                if replay {
                    Button("Close", action: onFinished)
                        .font(DatawatchFonts.bodyMedium)
                        .foregroundStyle(DatawatchColors.secondary)
                        .padding(.top, 16)
                } else {
                    Text(L(status))
                        .font(.system(size: 11, design: .monospaced))
                        .kerning(1.5)
                        .foregroundStyle(DatawatchColors.onSurfaceMuted.opacity(statusAlpha))
                        .padding(.top, 12)
                        .accessibilityLabel(L(status))
                }
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 56)
        }
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { bright = true }
        }
        .task {
            guard !replay else { return }
            await step("loading profiles…", afterMs: 800)
            await step("starting services…", afterMs: 1200)
            await step("ready", afterMs: 1200)
            try? await Task.sleep(nanoseconds: 180_000_000)
            onFinished()
        }
    }

    private var statusAlpha: Double {
        if reduceMotion { return 1.0 }
        return bright ? 1.0 : 0.45
    }

    private func step(_ next: String, afterMs: UInt64) async {
        try? await Task.sleep(nanoseconds: afterMs * 1_000_000)
        status = next
    }
}
