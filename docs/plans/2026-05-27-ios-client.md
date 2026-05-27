# iOS Client — Full Implementation Plan

**Date:** 2026-05-27  
**Version at planning:** 1.0.1 (build 310)  
**Status:** Planning — decisions pending user interview  
**North star:** 1:1 PWA parity, zero mobile-only protocol inventions  
**Scope:** iPhone first; iPad split-view in same release; watchOS/CarPlay out of scope v1  

---

## Context

The KMP repo already has iOS foundation work:

| Artifact | State |
|----------|-------|
| `shared/src/iosMain/` | Darwin Ktor (HTTP + WS) + SQLDelight native driver — compilable, no encryption yet |
| `iosApp/iosApp.swift` | SwiftUI skeleton, single placeholder screen |
| `iosApp/project.yml` | xcodegen spec targeting iOS 16.0+ |
| `shared` KMP targets | `iosX64`, `iosArm64`, `iosSimulatorArm64` declared and building |

The iOS content phase (UI, push, auth, all screens) begins now. Android is production-released.

---

## Decisions Pending (Interview Queue)

| # | Question | Blocks |
|---|----------|--------|
| Q1 | Build & test environment on Linux — how do we compile and run the iOS Simulator? | All stories |
| Q2 | UI framework: SwiftUI (native) vs Compose Multiplatform (shared UI with Android) | Story 3 |
| Q3 | Minimum iOS version | Story 2, App Store |
| Q4 | Apple Developer Account — do you have one? Personal or Organization? | Story 15 |
| Q5 | Push: APNs only, or unified APNs + FCM bridge via the server? | Story 12 |
| Q6 | Database encryption on iOS: SQLCipher-ObjC wrapper, Apple CryptoKit + file protection, or unencrypted v1? | Story 2 |
| Q7 | App Store listing: same `com.dmzs.datawatchclient` listing (universal iOS/Android) or separate iOS app? | Story 15 |

---

## Stories

### Story 1 — Build Environment & Project Foundation

**Goal:** Every push to `main` produces a signed `.ipa` that can be installed via TestFlight. Developers can iterate on Linux; CI handles the macOS compile step.

#### Epic 1.1 — macOS CI Pipeline

- **Task 1.1.1** Add `.github/workflows/ios-build.yml`: macOS-14 runner, `xcodegen generate`, `xcodebuild archive`, upload to TestFlight via `xcrun altool` or `fastlane pilot`.
- **Task 1.1.2** Store secrets in GitHub Actions: `APPLE_ID`, `APP_SPECIFIC_PASSWORD`, `TEAM_ID`, `PROVISIONING_PROFILE_BASE64`, `SIGNING_CERTIFICATE_BASE64`.
- **Task 1.1.3** Configure `xcodegen` version pin in CI (Homebrew or binary download).
- **Task 1.1.4** Wire `./gradlew :shared:assembleXCFramework` as a pre-step before xcodebuild so the KMP framework is fresh on every CI run.
- **Task 1.1.5** Cache Gradle + Kotlin/Native artifacts in CI (`~/.gradle`, `~/.konan`) to keep build under 10 min.
- **Task 1.1.6** Add CI status badge to `README.md` for the iOS build workflow.
- **Task 1.1.7** Write `docs/operations.md` section: iOS build pipeline, secrets rotation, re-signing procedure.

#### Epic 1.2 — Xcode Project Hygiene

- **Task 1.2.1** Run `xcodegen generate` and commit the resulting `.xcodeproj` **OR** add it to `.gitignore` and generate on demand — decide and document.
- **Task 1.2.2** Configure `project.yml` with correct bundle ID (`com.dmzs.datawatchclient`), deployment target, capabilities (Push Notifications, Face ID, Background Modes).
- **Task 1.2.3** Add `Info.plist` keys: `NSFaceIDUsageDescription`, `NSMicrophoneUsageDescription` (voice reply), `NSCameraUsageDescription` (if needed), `UIBackgroundModes: [remote-notification]`.
- **Task 1.2.4** Configure Xcode scheme for Debug and Release; Release uses distribution signing.
- **Task 1.2.5** Add `iosApp` to AGENT.md module table and update AI-APP-SEED.md project structure diagram.

#### Epic 1.3 — KMP Framework Integration

- **Task 1.3.1** Verify `./gradlew :shared:assembleXCFramework` produces `DatawatchShared.xcframework` with all three slices (device arm64, simulator x86_64, simulator arm64).
- **Task 1.3.2** Update `project.yml` framework path to point to the Release XCFramework, not Debug (for CI).
- **Task 1.3.3** Create `scripts/build-ios-framework.sh` that developers run locally before opening Xcode (wraps the Gradle task + copies output).
- **Task 1.3.4** Document the `gradlew → xcodegen → xcodebuild` chain in `docs/setup.md` iOS section.
- **Task 1.3.5** Add framework build step to root `Makefile` or `justfile` if one exists.

#### Epic 1.4 — Version & Release Alignment

- **Task 1.4.1** Sync iOS `CFBundleShortVersionString` with `DATAWATCH_APP_VERSION` from `gradle.properties` at build time (sed/script in CI or `project.yml` variable substitution).
- **Task 1.4.2** Sync `CFBundleVersion` (iOS build number) with `DATAWATCH_APP_VERSION_CODE`.
- **Task 1.4.3** Add iOS version files to the AGENT.md "version sync" check — CI must fail if iOS bundle version diverges from `gradle.properties`.
- **Task 1.4.4** Update AGENT.md §Versioning: add iOS `Info.plist` as a fourth version location that must be updated in every release commit.

---

### Story 2 — Shared Module iOS Hardening

**Goal:** The `shared` KMP module compiles cleanly for iOS with production-quality storage (encrypted), proper Ktor trust-anchor handling, and all `expect/actual` pairs resolved.

#### Epic 2.1 — Database Encryption (iOS)

- **Task 2.1.1** Research and decide: SQLCipher-ObjC bridge, Apple CryptoKit + Secure Enclave file protection, or Data Protection class A (hardware encrypted, no SQLCipher). [DECISION: Q6]
- **Task 2.1.2** Implement `IosDatabaseFactory.driver()` with chosen encryption strategy.
- **Task 2.1.3** Add passphrase derivation: use iOS Keychain to store the DB key (generated once, stored in `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`).
- **Task 2.1.4** Write unit test for `IosDatabaseFactory` using an in-memory driver stub.
- **Task 2.1.5** Document encryption approach in `docs/security-model.md` iOS section.

#### Epic 2.2 — iOS Keychain Token Storage

- **Task 2.2.1** Create `iosMain/storage/IosTokenStore.kt` implementing the same `TokenStore` interface as Android's `EncryptedSharedPreferences`-backed store.
- **Task 2.2.2** Use `Security` framework (`SecItemAdd`, `SecItemCopyMatching`, `SecItemUpdate`, `SecItemDelete`) with `kSecAttrService = "datawatch"`, `kSecAttrAccount = profileId`.
- **Task 2.2.3** Set `kSecAttrAccessible = kSecAttrAccessibleWhenUnlocked` (not After First Unlock, to prevent background token leakage).
- **Task 2.2.4** Add `IosTokenStore` to the `expect/actual` token-store factory.
- **Task 2.2.5** Write unit test verifying round-trip store → retrieve → delete for a bearer token.
- **Task 2.2.6** Confirm `ServerProfile.token` never appears in logs: add `toString()` redaction test for iOS as well as Android.

#### Epic 2.3 — Ktor Darwin Trust Anchor (Self-Signed Certs)

- **Task 2.3.1** Implement `createHttpClientWithWebSockets(trustAll = true)` using Darwin's `challengeHandler` to accept user-configured trust anchors (for self-signed datawatch servers). Mirror Android's `NetworkSecurityConfig` approach.
- **Task 2.3.2** Never implement blanket trust-all — only trust certs pinned per-profile (same semantic as Android).
- **Task 2.3.3** Document in `docs/transports.md` iOS section: ATS exceptions required for `http://` servers, how to add `NSExceptionDomains` at runtime or via config.
- **Task 2.3.4** Add App Transport Security (ATS) configuration in `Info.plist` for arbitrary loads only when user explicitly enables insecure mode in Settings.

#### Epic 2.4 — iOS `expect/actual` Completeness Audit

- **Task 2.4.1** List every `expect` declaration in `commonMain` and verify each has an `actual` in both `androidMain` and `iosMain`.
- **Task 2.4.2** Implement any missing `iosMain` actuals (likely: `BiometricAuth`, `PushTokenStore`, `platform name`).
- **Task 2.4.3** Run `./gradlew :shared:linkDebugFrameworkIosSimulatorArm64` and fix all compilation errors.
- **Task 2.4.4** Add `:shared` iOS compile step to the existing CI `build` workflow (currently Android-only).

---

### Story 3 — UI Framework & Design System [DECISION Q2]

**Goal:** iOS app has the same dark PWA visual language: black/near-black surfaces, teal (`#00E5FF`) primary accent, amber waiting state, monospace terminal text. Every screen matches the Android phone layout adapted for iOS HIG conventions.

> **Architectural fork (Q2):** If Compose Multiplatform → shared Compose UI. If SwiftUI → native iOS views, matching Android layouts by specification.

#### Epic 3.1 — Framework Setup [TBD Q2]

**Option A — Compose Multiplatform:**
- **Task 3.1.A.1** Add `composeMultiplatform` plugin to `shared/build.gradle.kts`.
- **Task 3.1.A.2** Create `iosApp/ComposeEntryPoint.swift` bridging UIViewController to ComposeUIViewController.
- **Task 3.1.A.3** Extract Android Compose screens into `commonMain/ui/` shared composables.
- **Task 3.1.A.4** Handle iOS-specific Compose quirks: UIKit interop for WKWebView (xterm), status bar insets, safe area.
- **Task 3.1.A.5** Validate Compose MP renders on both Android emulator (Linux) and iOS Simulator (macOS CI).

**Option B — SwiftUI:**
- **Task 3.1.B.1** Create `iosApp/ui/` SwiftUI source tree mirroring Android `composeApp/src/androidMain/kotlin/.../ui/` structure.
- **Task 3.1.B.2** Use `@Observable` / `ObservableObject` ViewModels that wrap the KMP shared ViewModels via Kotlin/Native coroutine adapters.
- **Task 3.1.B.3** Create a KMP → Swift ViewModel bridge (`KotlinNativeFlowAdapter`) converting `Flow<T>` to `AsyncStream<T>`.
- **Task 3.1.B.4** Define SwiftUI `PreviewProvider` equivalents for every screen so UI can be previewed in Xcode without a running server.

#### Epic 3.2 — Colour & Typography Tokens

- **Task 3.2.1** Create `DatawatchColors.swift` (SwiftUI) or update `composeApp`-shared tokens with the full PWA palette: background `#000000`, surface `#060A0E`, primary `#00E5FF`, secondary `#FFB300`, error `#FF4444`, on-surface `#D0E8ED`.
- **Task 3.2.2** Map all PWA CSS variables to the iOS token set — document in `docs/design-system.md`.
- **Task 3.2.3** Use SF Mono for terminal text (free, matches monospace intent); use SF Pro for UI text. No custom fonts to avoid binary size.
- **Task 3.2.4** Configure dark-only appearance — no light mode variant (matches PWA and Android).
- **Task 3.2.5** Write a SwiftUI `PreviewProvider` that renders the colour palette as a reference sheet.

#### Epic 3.3 — Navigation Architecture

- **Task 3.3.1** Tab bar with 6 tabs matching Android bottom nav: Sessions, Alerts, Automata, Observer, Dashboard, Settings.
- **Task 3.3.2** Use `NavigationStack` for push navigation within each tab (iOS 16+).
- **Task 3.3.3** Deep-link support: `datawatch://session/<id>`, `datawatch://alert/<id>` — handle in `App.openURL` / Universal Links.
- **Task 3.3.4** Persist selected tab and navigation stack across app restarts (UserDefaults).
- **Task 3.3.5** Match Android badge overlay: red badge on Alerts tab, session-count badge on Sessions tab.

#### Epic 3.4 — Shared Components

- **Task 3.4.1** `HeaderView` — title + subtitle + server name in top bar, matching Android `HeaderComponents.kt`.
- **Task 3.4.2** `ConnectionStatusBanner` — disconnected / reconnecting banner, same semantics as Android.
- **Task 3.4.3** `SessionCountChip` — Wait / Run / Total count chips, teal on black.
- **Task 3.4.4** `LoadingIndicator` — circular + "Connecting…" text, matching Android splash.
- **Task 3.4.5** `ErrorCard` — fail-fast error with Retry button; no hidden retry loops.
- **Task 3.4.6** Accessibility: `accessibilityLabel` and `accessibilityHint` on all interactive elements; VoiceOver traversal order matches visual order.

---

### Story 4 — Authentication & Server Connection

**Goal:** User adds a server profile (URL + token) from Settings, authenticates, and the app connects. Biometrics lock re-auth. Self-signed certs work.

#### Epic 4.1 — Server Profile Management

- **Task 4.1.1** `ServerProfileListView` — list of configured servers with active indicator; matches Android equivalent.
- **Task 4.1.2** `AddServerView` / `EditServerView` — URL field, token field (secure, masked), name field, TLS toggle.
- **Task 4.1.3** Validate URL on input: `https://` required unless user explicitly enables insecure mode.
- **Task 4.1.4** Test connectivity button: `GET /api/health` → show latency or error inline.
- **Task 4.1.5** Delete/reorder server profiles with swipe-to-delete and drag-reorder.
- **Task 4.1.6** Config export/import: share-sheet JSON export and Files-app import (matches Android export).

#### Epic 4.2 — Token Storage & Security

- **Task 4.2.1** Write `IosTokenStore` to Keychain (Epic 2.2 implementation wired to UI).
- **Task 4.2.2** Mask token in all UI (password field); never log.
- **Task 4.2.3** Copy-token action (explicit user tap) — one explicit action only, no auto-copy.
- **Task 4.2.4** On profile delete, remove token from Keychain atomically.

#### Epic 4.3 — Biometrics (Face ID / Touch ID)

- **Task 4.3.1** Add `LAContext` evaluation on app foreground after 5-minute background (matching Android BiometricPrompt timer).
- **Task 4.3.2** `NSFaceIDUsageDescription` in `Info.plist`: "datawatch uses Face ID to protect your server credentials."
- **Task 4.3.3** Fallback: PIN/passcode if biometrics unavailable.
- **Task 4.3.4** Setting toggle: "Require biometrics on resume" (default on).

#### Epic 4.4 — TLS & ATS

- **Task 4.4.1** Default ATS: no arbitrary loads. `NSAllowsArbitraryLoads = NO`.
- **Task 4.4.2** Per-profile insecure mode: dynamically configure `NSExceptionDomains` at runtime (requires ATS entitlement approach or a local proxy; research Apple's restriction).
- **Task 4.4.3** For self-signed certs: iOS `URLSession` delegate `didReceive challenge` — accept only certs the user has pinned in Settings.
- **Task 4.4.4** Certificate pinning UI: display cert fingerprint, let user approve/deny unknown certs (same flow as Android's `NetworkSecurityConfig` trust anchors).

---

### Story 5 — Sessions Screen

**Goal:** Sessions tab shows live session list with Wait/Run/Total filter chips, real-time counts pushed via WebSocket, tap opens detail.

#### Epic 5.1 — Sessions List

- **Task 5.1.1** `SessionsView`: `List` of `SessionRowView` cells, each showing session name, state badge, last-activity timestamp, wait/run counts.
- **Task 5.1.2** Filter row: Wait / Run / Total chips — tap to filter; persists across app lifecycle.
- **Task 5.1.3** Pull-to-refresh: re-fetch session list from `GET /api/sessions`.
- **Task 5.1.4** Real-time updates: subscribe to WebSocket `session_update` events; update list without flicker (diff-based).
- **Task 5.1.5** Empty state: "No sessions — start a datawatch session to see it here" with datawatch icon.
- **Task 5.1.6** Error state: `ConnectionStatusBanner` when WebSocket disconnected; sessions show stale indicator.

#### Epic 5.2 — Session State & Badges

- **Task 5.2.1** Map `SessionState` (Wait, Run, Done, Error) to colour badges matching Android and PWA: teal=Run, amber=Wait, grey=Done, red=Error.
- **Task 5.2.2** Session tab badge: total count of Wait+Run sessions; badge clears when all done.
- **Task 5.2.3** Animate state transitions (opacity fade, not jump) matching PWA behaviour.

#### Epic 5.3 — Session Context Menu

- **Task 5.3.1** Long-press context menu: "Open Detail", "Copy Session ID", "Copy Last Response".
- **Task 5.3.2** Swipe actions: none in v1 (avoid unintentional destructive actions).

---

### Story 6 — Session Detail & Terminal

**Goal:** Tap a session → full terminal view via WKWebView + xterm.js, reply composer, PRD actions. Live tail visible when keyboard is up. Matches PWA terminal behaviour exactly.

#### Epic 6.1 — WKWebView + xterm.js Integration

- **Task 6.1.1** Create `TerminalWebView.swift`: `WKWebView` subclass configured with `WKWebViewConfiguration`, JavaScript enabled, `allowsContentJavaScript`, no navigation allowed outside the host HTML.
- **Task 6.1.2** Vendor the same `xterm.js` + `xterm-addon-fit.js` already used in Android (`composeApp/src/androidMain/assets/`); copy to `iosApp/Resources/`. Single source of truth for vendored JS.
- **Task 6.1.3** Port `host.html` to iOS: DPR-corrected `dwExplicitSize`, write serialiser (`_pendingCap` / `_writeInFlight`), `DATAWATCH_COMPLETE:` line filter, `setMinSize(120, 0)` — same logic as Android (ref: AI-APP-SEED.md §xterm).
- **Task 6.1.4** `WKScriptMessageHandler` bridge replacing Android's `addJavascriptInterface`; receive pane_capture data from Swift, post to JS via `evaluateJavaScript`.
- **Task 6.1.5** Disable WKWebView scroll (`scrollView.isScrollEnabled = false`, `bounces = false`) — xterm owns its own scroll, same as Android `TerminalWebView` overrides.
- **Task 6.1.6** Verify terminal renders correct on iPhone SE (small), iPhone 15 Pro (standard), iPhone 15 Pro Max (large) — column count changes with viewport.

#### Epic 6.2 — Keyboard & IME Handling

- **Task 6.2.1** Subscribe to `UIResponder.keyboardWillShowNotification` / `keyboardWillHideNotification`; dispatch `dwExplicitSize(w, newH)` with keyboard-adjusted height.
- **Task 6.2.2** Confirm DPR correction works on all iPhone models — log `UIScreen.main.scale` alongside `dwExplicitSize` values in Debug builds.
- **Task 6.2.3** `safeAreaInsets.bottom` excluded from terminal height calculation when keyboard is visible (iOS equivalent of Android `imePadding`).
- **Task 6.2.4** No double-inset: only one layer handles safe-area padding. Document the chosen layout in code, same discipline as Android AI-APP-SEED.md §xterm.
- **Task 6.2.5** Scroll-to-bottom on every pane_capture (`safeFit` → `term.scrollToBottom()`), matching PWA and Android.

#### Epic 6.3 — Terminal Data Pipeline

- **Task 6.3.1** WebSocket session stream → `pane_capture` events dispatched to `TerminalWebView.dispatchPaneCapture(_:)` on main thread.
- **Task 6.3.2** Frame throttle: max 30 fps dispatch (same as Android 33 ms), drop intermediate frames if previous write still in flight.
- **Task 6.3.3** Write correct `clear_screen` sequence: `\x1b[2J\x1b[3J\x1b[H` (not `term.reset()` — causes flash).
- **Task 6.3.4** `DATAWATCH_COMPLETE:` filter in `dwPaneCapture`: filter matching lines, keep the rest (PWA `app.js:609` logic).
- **Task 6.3.5** Trailing whitespace-only row stripping before writing to xterm.

#### Epic 6.4 — Reply Composer

- **Task 6.4.1** `ReplyComposerView`: multiline `TextEditor`, send button, char count. Matches Android `ReplyComposer`.
- **Task 6.4.2** `POST /api/sessions/{id}/reply` on send; show sending indicator; clear on success.
- **Task 6.4.3** Keyboard dismiss: tap outside composer, swipe down.
- **Task 6.4.4** Saved commands: long-press send → "Use saved command" popover. Saved commands stored in local DB.
- **Task 6.4.5** Error handling: inline error if `POST` fails; user can retry.

#### Epic 6.5 — Voice Reply

- **Task 6.5.1** Microphone button in composer: record audio via `AVAudioRecorder`, 16kHz mono WAV (same spec as Android).
- **Task 6.5.2** `NSMicrophoneUsageDescription` in `Info.plist`: "datawatch uses the microphone to send voice replies to AI sessions."
- **Task 6.5.3** Upload recorded blob to `POST /api/voice/transcribe` on the connected profile; display transcript for confirmation before sending.
- **Task 6.5.4** Transcription confirmation UI: show text, Edit button, Send button — same flow as Android.
- **Task 6.5.5** Error handling: transcription failure shows inline error; user can retry or type manually.

---

### Story 7 — Alerts

**Goal:** Alerts tab shows all pending alerts with dismiss action; tapping opens detail with full message and action buttons. Real-time via WebSocket.

#### Epic 7.1 — Alert List

- **Task 7.1.1** `AlertsView`: `List` of `AlertRowView`; each shows alert type icon, message preview, timestamp, session name.
- **Task 7.1.2** Alerts tab badge: count of unacknowledged alerts; clears on dismiss.
- **Task 7.1.3** Real-time WebSocket `alert_created` / `alert_dismissed` events update list.
- **Task 7.1.4** Swipe-to-dismiss in list: `DELETE /api/alerts/{id}`.
- **Task 7.1.5** Empty state: "No alerts" with icon.

#### Epic 7.2 — Alert Detail

- **Task 7.2.1** `AlertDetailView`: full alert message, session context, timestamp, action buttons.
- **Task 7.2.2** Guardrail alert: shows guardrail rule name + "Block" / "Allow" buttons → `POST /api/alerts/{id}/approve` or `/deny`.
- **Task 7.2.3** Dismiss alert: `DELETE /api/alerts/{id}`; pop navigation on success.

---

### Story 8 — Automata / PRD

**Goal:** Automata tab shows PRD list; users can view detail, run, pause, or stop PRDs.

#### Epic 8.1 — PRD List

- **Task 8.1.1** `AutomataView`: `List` of `PrdRowView`; name, status badge, last-run timestamp.
- **Task 8.1.2** Filter: All / Running / Paused / Error.
- **Task 8.1.3** Real-time WebSocket `prd_status` events update list.
- **Task 8.1.4** Pull-to-refresh.

#### Epic 8.2 — PRD Detail

- **Task 8.2.1** `PrdDetailView`: PRD name, description, execution log (scrollable), current status.
- **Task 8.2.2** Action buttons: Run / Pause / Stop — each calls the corresponding API endpoint.
- **Task 8.2.3** Execution log: scrollable, monospace font, auto-scroll to bottom on new entries.
- **Task 8.2.4** Error state shown inline.

---

### Story 9 — Observer / Process Stats

**Goal:** Observer tab shows real-time CPU, memory, network stats and process timeline via eBPF observer data. Matches PWA Observer screen.

#### Epic 9.1 — Stats Charts

- **Task 9.1.1** CPU usage: line chart (Swift Charts, iOS 16+) showing last 60 seconds of samples.
- **Task 9.1.2** Memory usage: area chart with high-water mark annotation.
- **Task 9.1.3** Network I/O: dual-line chart (rx/tx).
- **Task 9.1.4** Chart colour tokens: CPU=teal, Memory=amber, Network rx=green, tx=orange — matching PWA palette.
- **Task 9.1.5** Stats via WebSocket `stats_update` events.

#### Epic 9.2 — Process Table

- **Task 9.2.1** `ProcessTableView`: `List` of top-N processes by CPU, sortable.
- **Task 9.2.2** Each row: PID, name, CPU%, MEM, state.
- **Task 9.2.3** Tap process: inline expanded detail (same session context).

#### Epic 9.3 — Timeline

- **Task 9.3.1** Horizontal timeline of session events (session start, guardrail hits, completions) for the selected server.
- **Task 9.3.2** Pinch-to-zoom on timeline (iOS native gesture).
- **Task 9.3.3** Tap event on timeline navigates to the related session/alert.

---

### Story 10 — Dashboard (Multi-Server)

**Goal:** Dashboard tab aggregates counts across all configured servers; same as Android Dashboard.

#### Epic 10.1 — Aggregated View

- **Task 10.1.1** `DashboardView`: card per server showing Wait / Run counts, connection status, server name.
- **Task 10.1.2** Tap server card → navigate to that server's Sessions tab (switch active server).
- **Task 10.1.3** "Add server" shortcut from Dashboard if no servers configured.
- **Task 10.1.4** Auto-refresh: poll all servers' `/api/health` + `/api/sessions` every 15 s when app is foregrounded.

#### Epic 10.2 — Server Switching

- **Task 10.2.1** Persist active server selection (same as Android `ActiveServerStore`).
- **Task 10.2.2** Server switcher in navigation bar: shows active server name, tap to switch.
- **Task 10.2.3** Switching server reconnects WebSocket and clears stale session/alert state.

---

### Story 11 — Settings

**Goal:** Settings tab covers all user-configurable values. No hard-coded settings (Configuration Accessibility Rule).

#### Epic 11.1 — Settings Structure

- **Task 11.1.1** `SettingsView`: `Form`-based, grouped sections: Servers, Security, Notifications, Terminal, About.
- **Task 11.1.2** Servers section: list of profiles + "Add Server" button → navigates to `AddServerView`.
- **Task 11.1.3** Security section: Biometrics toggle, "Require on resume" timer (1 min / 5 min / always).
- **Task 11.1.4** Notifications section: Enable/disable per-server push; notification types to receive.
- **Task 11.1.5** Terminal section: font size, scroll behaviour, DATAWATCH_COMPLETE filter toggle (for power users who need to see marker lines).
- **Task 11.1.6** About section: app version, build number, open-source licenses, privacy policy link.

#### Epic 11.2 — Config Export / Import

- **Task 11.2.1** "Export Config" button → generates JSON blob (same schema as Android export) → iOS Share Sheet.
- **Task 11.2.2** "Import Config" button → Files picker → parse JSON → create/update server profiles.
- **Task 11.2.3** Import merges (not replaces) existing profiles; shows diff before apply.
- **Task 11.2.4** Round-trip test: Android-exported config imports correctly on iOS and vice versa.

#### Epic 11.3 — Diagnostics

- **Task 11.3.1** `DiagnosticsView`: WebSocket event log (last 100 events), connection stats (reconnect count, last ping round-trip).
- **Task 11.3.2** "Export logs" → redacted log file via Share Sheet (tokens never appear).
- **Task 11.3.3** "Reset app data" → confirm dialog → wipe DB + Keychain entries.

---

### Story 12 — Push Notifications (APNs)

**Goal:** Watch receives push when a session needs input. iOS uses APNs; the datawatch server must send to APNs. [DECISION Q5]

#### Epic 12.1 — APNs Registration

- **Task 12.1.1** Request push permission on first launch (`UNUserNotificationCenter.requestAuthorization`).
- **Task 12.1.2** Register for remote notifications; capture device token from `didRegisterForRemoteNotificationsWithDeviceToken`.
- **Task 12.1.3** `POST /api/device/register` with token, platform=`apns`, profile ID (matching Android FCM registration).
- **Task 12.1.4** Refresh token on each launch; deregister on profile delete.

#### Epic 12.2 — APNs Server-Side (Coordination)

- **Task 12.2.1** File `dmz006/datawatch` issue: datawatch server needs APNs send capability alongside FCM. Provide APNs payload schema matching existing FCM schema.
- **Task 12.2.2** Define APNs payload format: `{ "alert": { "title": "Session waiting", "body": "<session name>" }, "data": { "sessionId": "...", "type": "wait" } }`.
- **Task 12.2.3** Document in `docs/transports.md` iOS section: APNs certificate setup, server configuration.

#### Epic 12.3 — Notification Handling

- **Task 12.3.1** `UNUserNotificationCenterDelegate.didReceive(_:withCompletionHandler:)`: parse payload, navigate to relevant session/alert.
- **Task 12.3.2** Background push (`content-available: 1`): wake app, pre-fetch session update, update badge count.
- **Task 12.3.3** Notification categories: "Dismiss" action on alert notifications (requires no app open).
- **Task 12.3.4** App badge: total unread alerts (updated on receive + after dismiss).

---

### Story 13 — iPad Layout

**Goal:** On iPad, Sessions + Session Detail appear side-by-side (same as Android tablet ≥600dp 2-pane layout).

#### Epic 13.1 — Split View

- **Task 13.1.1** Use `NavigationSplitView` (iOS 16+): sidebar = session list, detail = terminal view.
- **Task 13.1.2** On iPad, default to expanded (both panes visible). On iPhone, column-style navigation.
- **Task 13.1.3** Alerts + Dashboard also use split-view on iPad (list | detail).
- **Task 13.1.4** Test on iPad Mini (smallest) and iPad Pro 12.9" (largest).

#### Epic 13.2 — iPad-Specific Adaptations

- **Task 13.2.1** Toolbar items: on iPad, show active server name as a `ToolbarItem(.principal)` in the nav bar.
- **Task 13.2.2** Context menus: on iPad, right-click on session row shows full context menu.
- **Task 13.2.3** Keyboard shortcuts (hardware keyboard on iPad): Cmd+R = refresh, Cmd+K = clear terminal, Cmd+1-6 = tab switch.

---

### Story 14 — PWA Parity Audit & Hardening

**Goal:** Every row in `docs/parity-status.md` for iOS is ✅. Zero mobile-only protocol inventions.

#### Epic 14.1 — Protocol Parity

- **Task 14.1.1** Read `app.js` and `openapi.yaml` from a live datawatch server; compare every endpoint call from iOS against Android implementation. Flag any divergence.
- **Task 14.1.2** WebSocket frame handling: verify iOS handles all frame types the PWA handles (`pane_capture`, `session_update`, `alert_created`, `stats_update`, etc.).
- **Task 14.1.3** `DATAWATCH_COMPLETE:` filter parity: iOS must filter the same way as PWA `app.js:609`.
- **Task 14.1.4** Reconnect behaviour: same exponential backoff (1s → 2s → 4s → max 30s) as PWA and Android.
- **Task 14.1.5** Transition-frame skip: verify iOS skips transitional pane_capture frames the same way as PWA.

#### Epic 14.2 — Feature Matrix Audit

- **Task 14.2.1** Go through every row in `docs/parity-status.md`; add iOS column; mark as ✅/❌/⏸️.
- **Task 14.2.2** Add iOS row to `docs/testing-tracker.md` for every surface.
- **Task 14.2.3** File issues in `dmz006/datawatch` for any server-side change required to support iOS (e.g., APNs, any iOS-specific API path).
- **Task 14.2.4** Live-validate every screen on iPhone SE (small) and iPhone 15 Pro (medium/large) in iOS Simulator.

#### Epic 14.3 — Security Audit

- **Task 14.3.1** Run `xcrun analyze` (Xcode static analyzer) on the iOS build; fix all warnings.
- **Task 14.3.2** Verify no token logged: add iOS-specific `token-in-log` grep to CI.
- **Task 14.3.3** Verify Keychain items have correct `kSecAttrAccessible` flags.
- **Task 14.3.4** ATS: verify `NSAllowsArbitraryLoads = NO` in release builds.
- **Task 14.3.5** Confirm biometric gate works with Face ID Simulator in Xcode.

#### Epic 14.4 — Performance Hardening

- **Task 14.4.1** Profile xterm rendering on lowest supported device (iPhone XR = A12, if still supported under iOS 16).
- **Task 14.4.2** Memory check: 30-minute session with 15 KB/s pane_capture throughput; no memory growth > 50 MB.
- **Task 14.4.3** Cold start time: < 2 seconds to first pixel on iPhone 15 Pro.
- **Task 14.4.4** Battery: background WebSocket keep-alive ping must not prevent Doze-equivalent (`BGTaskScheduler`) from kicking in.

---

### Story 15 — App Store Submission

**Goal:** App available on the App Store under the same developer account as the Android app (Play Store).

#### Epic 15.1 — App Store Connect Setup [DECISION Q4, Q7]

- **Task 15.1.1** Create App Store Connect record for `com.dmzs.datawatchclient` (or iOS-specific ID — Q7).
- **Task 15.1.2** Fill in metadata: name "datawatch", subtitle, description mirroring Play Store listing.
- **Task 15.1.3** Age rating questionnaire (same answers as Play Store IARC content rating).
- **Task 15.1.4** Privacy nutrition label: data collected (none collected by app itself — user's server data stays on their server).
- **Task 15.1.5** App category: Productivity.

#### Epic 15.2 — TestFlight Setup

- **Task 15.2.1** Internal TestFlight group: same testers as Play Store internal testing group.
- **Task 15.2.2** CI auto-uploads every `main` build to TestFlight (Story 1 Epic 1.1).
- **Task 15.2.3** External TestFlight group (if desired) after basic QA pass.

#### Epic 15.3 — Screenshots & Metadata

- **Task 15.3.1** Screenshots for iPhone 6.9" (required), iPhone 6.5" (required), iPad 12.9" (if iPad is supported). Captured from Simulator.
- **Task 15.3.2** App preview video (optional but recommended for terminal-heavy app to show live tail).
- **Task 15.3.3** Keywords: "AI session monitor, Claude Code, terminal, datawatch, coding AI".

#### Epic 15.4 — Review & Launch

- **Task 15.4.1** Submit for App Review. Prepare review notes: "This app connects to user-owned datawatch servers. Demo server credentials: …".
- **Task 15.4.2** Respond to any App Review questions or rejections.
- **Task 15.4.3** Enable phased release (same discipline as Android).
- **Task 15.4.4** Create GitHub release `vX.Y.Z-ios` with IPA + dSYM attached.
- **Task 15.4.5** Update `README.md` with App Store badge link.

---

## Dependency Map

```
Story 1 (Build Env)
  └── blocks all others

Story 2 (Shared Hardening)
  └── blocks Story 4 (Auth), Story 6 (Terminal data), Story 12 (Push)

Story 3 (UI Framework)  [Q2 decision]
  └── blocks Story 4–13 (all UI stories)

Story 4 (Auth)
  └── blocks Story 5–11 (all data stories need a connected session)

Story 5 (Sessions)
  └── blocks Story 6 (need list before detail)

Story 6 (Terminal) — most complex; can parallelize with 7, 8, 9, 10, 11

Story 12 (Push) — requires server-side APNs support; file issue early

Story 14 (Parity Audit)
  └── depends on all feature stories complete

Story 15 (App Store)
  └── depends on Story 14 complete
```

---

## Rough Milestone Timeline (post-Q1 answer)

| Milestone | Stories | Estimate |
|-----------|---------|----------|
| M1 — CI green, framework builds | 1, 2 | 2 weeks |
| M2 — Auth + basic Sessions screen | 3, 4, 5 | 3 weeks |
| M3 — Terminal live tail working | 6 | 2 weeks |
| M4 — All screens complete | 7–13 | 4 weeks |
| M5 — Parity audit + App Store | 14, 15 | 2 weeks |
| **Total estimate** | | **~13 weeks** |

Estimates are after Q1–Q7 decisions. UI framework choice (Q2) has the largest variance: Compose MP saves ~4 weeks on UI work but adds complexity in WKWebView interop; SwiftUI is more iOS-idiomatic but requires writing all UI twice (Android Compose + iOS SwiftUI).

---

## Pass 1 Complete — Pass 2 Review

**Gaps found in Pass 1 → added in Pass 2:**

- Added Task 2.4.4 (shared iOS compile in CI — was missing from CI plan)
- Added Epic 9.3 (Timeline — was in Observer on Android but missing from plan)
- Added Task 10.1.4 (auto-refresh poll — Dashboard needs a data source when WebSocket isn't the right mechanism)
- Added Epic 11.3 (Diagnostics — was in Android Settings but missing)
- Added Task 12.3.3 (Notification categories for actionable alerts — critical for UX parity)
- Added Task 13.2.3 (iPad keyboard shortcuts — hardware keyboard is common on iPad)
- Added Task 14.4.4 (background battery — `BGTaskScheduler` is iOS-specific concern)
- Added Task 15.4.4 (dSYM in GitHub release — crash symbolication, parallel to Android mapping.txt)

---

## Pass 2 Complete — Pass 3 Review

**Gaps found in Pass 2 → added in Pass 3:**

- Added Task 4.1.5 (delete/reorder server profiles — basic UX that was implicit but not listed)
- Added Task 4.4.4 (cert pinning UI — self-signed cert UX was described but no UI task)
- Added Task 5.3 (session context menu — long-press is natural iOS interaction, was missing)
- Added Task 6.3.2 (frame throttle — 30fps cap was in Android but not listed for iOS)
- Added Task 8.2.3 (PRD execution log auto-scroll — was in design but not a task)
- Added Task 9.2.1–9.2.3 (Process Table — was in Observer description but epics only had charts)
- Added Task 11.1.5 (Terminal section in Settings — font size, scroll behaviour)
- Added Task 15.1.3–15.1.5 (App Store metadata details — age rating, privacy, category)

---

## Pass 3 Complete — Pass 4 Review

**Gaps found in Pass 3 → added in Pass 4:**

- Added Epic 13.2 (iPad-specific adaptations — Story 13 only had split-view, missed context menus and keyboard shortcuts)
- Added Task 4.3.3 (Biometrics fallback to PIN — was implicit)
- Added Task 6.4.4 (saved commands — was in Android but missing from iOS reply composer)
- Added Task 11.2.3 (import merge semantics — replace vs merge, must be documented and testable)
- Added Task 14.3.1 (Xcode static analyzer in CI — iOS-specific security step)
- Added `docs/design-system.md` reference in Task 3.2.2 (colour token documentation)

---

## Pass 4 Complete — Pass 5 Review

**Pass 5: No new gaps found.** All features from Android + PWA are represented in iOS stories. All iOS-specific platform concerns (APNs, Keychain, ATS, Face ID, `BGTaskScheduler`, Swift Charts, `NavigationSplitView`) are covered. Dependency map is consistent. Milestone timeline reflects the decision variance.

**Plan is complete.**

---

## Open Questions (Decision Log)

| Q# | Asked | Answered | Decision |
|----|-------|----------|----------|
| Q1 | 2026-05-27 | **Hybrid E** — GitHub Actions macOS CI now; cloud Mac tonight/tomorrow for interactive Simulator | Build & test environment |
| Q2 | 2026-05-27 | **SwiftUI (native iOS)** | Users get native iOS UX; parity standard is capability not implementation |
| Q3 | 2026-05-27 | **iOS 16.0** | NavigationSplitView + Swift Charts; ~91% active device coverage |
| Q4 | 2026-05-27 | **Individual — enrolling now** | No D-U-N-S needed; unblocks signing secrets in CI |
| Q5 | – | Pending | APNs strategy |
| Q6 | – | Pending | Database encryption on iOS |
| Q7 | – | Pending | App Store listing ID strategy |
