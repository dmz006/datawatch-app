# iOS local build host (Mac)

A Mac on the home LAN is used for fast local iOS compiles and for driving the iOS
Simulator during parity / E2E work. CI (`.github/workflows/ios-build.yml`, `macos-26`)
remains the authoritative build; the Mac is for iteration speed and screenshots.

Address and credentials are not recorded in the repo. Access is key-based SSH from the
Linux workstation as the Mac's normal user.

## Hardware / OS (as provisioned 2026-10-04)

| Item | Value | Notes |
|---|---|---|
| Machine | 2018 iMac, Intel Core i5-8500 (6 cores) | x86_64 → Simulator runs the KMP `iosX64` slice |
| RAM | 8 GB | Tight for Kotlin/Native + a running Simulator. Quit heavy background apps (e.g. Adobe updaters) during sessions. |
| Disk | ~376 GB free | Simulator runtime is ~8 GB |
| macOS | 15.7.x (Sequoia) | |
| Xcode | 26.3 in `/Applications/Xcode.app` | Required: App Store Connect only accepts iOS 26 SDK builds |

## One-time setup

### Owner (needs the Mac password — run in Terminal on the Mac)

```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
sudo xcodebuild -license accept
sudo xcodebuild -runFirstLaunch          # installs CoreSimulator etc.
xcodebuild -downloadPlatform iOS         # iOS Simulator runtime (~8 GB, 10–30 min)
```

Status 2026-10-04: **pending** (xcode-select still points at Command Line Tools).

### Done without admin rights (in the user's home folder)

| Component | Location | Why |
|---|---|---|
| Temurin JDK 21 | `~/dev/jdk-21/Contents/Home` | System Java is 15; Gradle/Kotlin need 21. Always `export JAVA_HOME=~/dev/jdk-21/Contents/Home`. |
| Repo clone | `~/dev/datawatch-app` | `git pull` before each session |
| xcodegen | not yet | download the release zip into `~/dev` (no Homebrew on this Mac) |

## Build on the Mac

```bash
export JAVA_HOME=~/dev/jdk-21/Contents/Home
cd ~/dev/datawatch-app && git pull
./gradlew :shared:assembleDatawatchSharedDebugXCFramework --no-daemon
cd iosApp && ~/dev/xcodegen/bin/xcodegen generate --spec project.yml
xcodebuild build -project DatawatchClient.xcodeproj -scheme DatawatchClient \
  -destination 'generic/platform=iOS Simulator' -configuration Debug CODE_SIGNING_ALLOWED=NO
```

## Testing rules (same as Android — see `docs/testing/test-isolation-guide.md`)

- The Simulator points **only** at the sandbox test daemon (port 18443 on the workstation,
  token from the test config), never the production daemon.
- Start the test daemon with the PID-file recipe from the isolation guide; stop it by PID only.
- After a session: `xcrun simctl shutdown all`, stop the test daemon, remove its work dir.

## Gotchas learned bringing the iOS build up (2026-10-04)

- `Info.plist` keys belong in `iosApp/project.yml` → `info.properties`; xcodegen regenerates the
  file and drops anything added to it directly. CI fails if required keys are missing.
- The domain `Session` class is `DwSession` in Swift (SQLDelight also generates `db.Session`).
- Kotlin funs starting with `new…`/`copy…`/`init…` are renamed in ObjC; keep Swift-facing
  types top-level; `Double?` → `KotlinDouble?`, `Boolean` lambda args → `KotlinBoolean`.
- The app links `-lsqlite3` (SQLDelight native driver).
