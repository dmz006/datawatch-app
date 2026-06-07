# Changelog

All notable changes to this project will be documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/) per
[AGENT.md Versioning rules](AGENT.md#versioning).

## [Unreleased]

## [1.0.52] — 2026-06-07

### Fixed
- Android Auto: TTS race condition — `speak()` was called ~1 ms before the TTS engine finished binding, causing Play to silently drop the utterance; `LastOutputDetailScreen` and `VoiceRecordingScreen` now hold a `pendingSpeak` reference and play it the moment the binding callback fires
- Android Auto: Running sessions now show `[Play]` + `[Voice Reply]` as primary buttons with `[Kill]` + `[chat-icon]` in the strip — matches Waiting state; previously only `[Play]` was shown with no way to send a reply while the AI was working

## [1.0.51] — 2026-06-07

### Fixed
- Android Auto: after confirming a session kill, the app now returns to the session list instead of popping all the way to the root summary screen (`popToRoot()` → `pop()`)
- Android Auto: terminal sessions (Completed / Killed / Error) now show a `[Restart]` button alongside `[Play]`; tapping it calls `POST /api/sessions/restart` (warm-resume) and returns to the session list on success

## [1.0.50] — 2026-06-07

### Changed
- Android Auto voice recording: live RMS level meter (`▓▓▓▓░░░`) shown while listening so you can see the mic is picking up sound; updates at up to 5 fps (throttled to stay within Car App Library template rate limit)
- Android Auto voice recording: partial results appear in real-time below the meter as words are recognized, so you can see what the recognizer is hearing before you finish speaking
- Android Auto voice recording: audio focus claimed with `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` before each recording session — navigation guidance and music are silenced while the mic is active and the car mic is properly routed; focus is released on results, error, cancel, or screen stop
- Android Auto voice recording: `EXTRA_PARTIAL_RESULTS` flag added to the recognizer intent to enable the partial result callbacks

## [1.0.49] — 2026-06-07

### Fixed
- Android Auto: session detail redesigned — Play is now always the first primary button in Waiting/RateLimited state so you can hear the prompt the moment you enter the session; Voice Reply remains second primary; Quick Reply (typed yes/no/continue) moved to a chat icon in the action strip
- Android Auto: removed "Send" button from session detail — it was crashing on tap by switching to reply mode in states where reply context was unavailable; reply is now accessible only via Voice Reply (primary) or the strip chat icon (typed quick reply)
- Android Auto: Running state now shows only [Play] as primary; [Kill] remains in strip — no spurious "Send" shown while AI is actively working
- Android Auto: TTS now routes audio through car speakers instead of the phone speaker in `BlockDetailsScreen` and `VoiceRecordingScreen` — was missing `AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`; `LastOutputDetailScreen` was already fixed in v1.0.48

## [1.0.48] — 2026-06-06

### Fixed
- xterm terminal: restore Enter key — v1.0.47's `finishComposingText()` override fired on focus-loss and other non-commit IME events (not just word commits), keeping the spurious-Enter suppression window permanently open and blocking all intentional Enters; removed the override entirely
- xterm terminal: spurious-Enter window now only opens for multi-character `commitText` calls (autocomplete/autocorrect word commits); single-character commits (individual keystrokes) no longer trigger the window, so Enter typed immediately after any character goes through normally
- xterm terminal: suppression window tightened to 150 ms (was 300 ms) to further reduce false positives

## [1.0.47] — 2026-06-06

### Fixed
- xterm terminal: Android IME autocomplete no longer triggers spurious Enter / starts AI processing mid-sentence — the previous boolean `recentCommit` guard was cleared by any `sendKeyEvent` call (e.g. KEYCODE_SPACE fired by the keyboard between the word commit and Samsung/Gboard's phantom Enter), letting the phantom Enter through as an intentional Enter; replaced with a 300 ms timestamp window that only non-Enter keys cannot reset, so the spurious Enter stays suppressed regardless of intervening key events
- xterm terminal: added `finishComposingText()` override to catch the `setComposingText → finishComposingText` IME path (used by some keyboards instead of `commitText`) — same spurious-Enter suppression window applies
- xterm terminal: `performEditorAction` suppression now logs when it blocks a spurious action for easier debugging

## [1.0.46] — 2026-06-06

### Fixed
- Android Auto: comprehensive ActionStrip audit — Car App Library MESSAGING category enforces max 1 custom-titled action per ActionStrip across *all* template types, not just `ListTemplate`; fixed 4 remaining violations:
  - `AutoSessionDetailScreen` main detail view: "Play" strip action (listening to session prompt while Waiting) is now icon-only (voice icon); "Kill" retains its title as the single allowed titled action
  - `AutoSessionDetailScreen` Quick Reply strip: "Voice" and "Cancel" are now icon-only (voice + close icons); "Skip" removed from strip (rarely needed); "Continue" retains its title
  - `BlockDetailsScreen`: "Cancel" removed from ActionStrip (header Back button already serves this); "Listen" retains its title
  - `VoiceRecordingScreen` Confirmed state: "Cancel" removed from ActionStrip (header Back button already serves this); "Listen" retains its title
- Added `ic_auto_close` vector drawable for icon-only Cancel actions

## [1.0.45] — 2026-06-06

### Fixed
- Android Auto: `ListTemplate` ActionStrip now has exactly 1 custom-titled action — Car App Library MESSAGING category enforces a max of 1; v1.0.42 added `.setTitle()` to both actions in `AutoSummaryScreen` ("About" + "Monitor") and `AutoMonitorScreen` ("Sessions" + "Servers"), causing an immediate `IllegalArgumentException` crash on every Auto connect; fixed by making the secondary action icon-only in both screens

## [1.0.44] — 2026-05-31

### Fixed
- Quick commands sheet: ESC, arrow keys (↑ ↓ ← →), Tab, PgUp, PgDn now correctly route through `sendkey` (tmux path) instead of raw `send_input` — the `onSend` callback was appending `"\r"` for shell execution, causing e.g. `"\x1b\r"` to miss the Escape pattern match and `"[A\r"` to miss the Up arrow match; fixed by stripping the trailing CR before the `when` comparison, then passing the original text to `sendInput` for non-special-key commands
- Quick commands sheet pattern match now accepts both the full ANSI form (`"\x1b[A"`) and the short form (`"[A"`) for arrows; added Tab, PgUp, PgDn sendkey routing (were previously falling through to raw `sendInput`)

## [1.0.43] — 2026-05-31

### Fixed
- Android Auto: tapping the car alert banner (CarAppExtender) while inside a session now navigates directly to that session — previously the content intent was missing so tapping did nothing
- Android Auto: "Play Long" now appears in `LastOutputDetailScreen` for terminal and waiting sessions when the response is > 200 characters (previously `longText` was always null for non-running sessions)
- Android Auto: removed the redundant "Play Short"/"Stop" primary button from `LastOutputDetailScreen` — the ActionStrip "Listen"/"Stop" toggle already handles this; replaced with a "Close" button; "Play Long" remains as a primary button when a long form exists
- Android Auto: icon-only voice action in Quick Reply ActionStrip now has title "Voice" for Play Store accessibility compliance
- Android Auto: notification alert (`CarAppExtender`) now deep-links to the session detail screen when tapped; `onNewIntent()` in `DatawatchMessagingService` routes by session ID before checking for voice commands

## [1.0.42] — 2026-05-31

### Fixed
- Android Auto: multi-pass Car App Library compliance audit — navigation stack depth, TTS context, icon label accessibility
  - Merged `TranscriptionConfirmScreen` into `VoiceRecordingScreen` as a sealed `Confirmed` state — eliminates the Automata→SessionList→SessionDetail→VoiceRecording→TranscriptionConfirm depth-6 path that crashes Gearhead (max is 5)
  - `AutoMonitorScreen` forced-profile mode now pops itself before pushing `AutoSessionListScreen` — eliminates the Monitor→Monitor2→SessionList→SessionDetail→VoiceRecording depth-6 path
  - `BlockDetailsScreen`: TTS now uses `carContext.applicationContext` (was `carContext`); TTS ActionStrip action title set to "Listen" (was icon-only); icon changed from `ic_auto_info` to `ic_auto_voice`
  - `VoiceRecordingScreen` (rewritten): TTS uses `applicationContext`; `Confirmed` state auto-plays transcript and shows Send + Retry primary buttons + Listen + Cancel ActionStrip; `onStart` guard prevents re-starting recognition if already in Confirmed/Error state; scope cancelled in `onDestroy`
  - `AutoSummaryScreen` ActionStrip actions now have titles ("About", "Monitor") — required for Play Store accessibility review
  - `AutoMonitorScreen` server-picker ActionStrip action now has title ("Servers") — required for Play Store accessibility review
  - `LastOutputDetailScreen` ActionStrip voice toggle now shows dynamic title ("Listen" / "Stop") — required for Play Store accessibility review

## [1.0.41] — 2026-05-31

### Fixed
- Android Auto: session detail crash hardened further — `setTitle()` now uses `sessionId` as fallback when session name is blank (defensive guard against empty-name edge case); `refresh()` no longer swallows `CancellationException` (re-throws so cooperative cancellation works correctly on screen pop)

### Changed
- Android Auto: About screen now shows `v${version}` in the screen title (immediately visible at top) and `v${version}  (build ${code})` as the first line of the body; removed ASCII art that pushed version off-screen on some head units

## [1.0.40] — 2026-05-31

### Fixed
- Android Auto: fix crash when opening a terminal session (Completed or Killed) whose `lastResponse` is null or blank — `MessageTemplate` requires at least one action button; the `isTerminal` branch now always shows a **Play** button (navigates to `LastOutputDetailScreen` which handles null content gracefully) instead of emitting a zero-action template that Car App Library rejects

## [1.0.39] — 2026-05-31

### Fixed
- Android Auto: rewrote `AutoSessionDetailScreen` layout so all actions are labeled and immediately visible — no more unlabeled icon hunting:
  - **Waiting session**: body shows the actual prompt being asked; primary buttons are **Voice Reply** (goes directly to voice recording) and **Quick Reply** (opens Yes/No mode); ActionStrip has **Play** (hear the prompt) and **Kill**
  - **Running session**: body shows `currentStatus` (what the AI is doing); primary buttons are **Play** (hear status, auto-plays with long version available) and **Send**; ActionStrip has **Kill**
  - **Terminal session**: body shows last response; primary button is **Play**
  - **Quick Reply mode**: body shows the prompt; **Yes** / **No** buttons; ActionStrip has **Continue** / **Skip** / 🎤 voice / **Cancel**
- Voice is now a direct primary button on the waiting session screen — no longer buried behind "Send" → reply mode → unlabeled ActionStrip icon

## [1.0.38] — 2026-05-31

### Fixed
- Android Auto: waiting-input notifications now use `NotificationCompat.MessagingStyle` — the car head unit reads the prompt aloud via TTS and surfaces native Yes / No / Reply actions in the car shade; tapping Reply opens the car's voice input and sends the spoken text directly via `RemoteInput` to the session without requiring the app to be open (previously used `BigTextStyle` which Android Auto treats as a generic notification with no auto-read or inline voice reply)
- Android Auto: notification body now shows `lastPrompt` first (the actual prompt the session is waiting on), then `promptContext`, then `lastResponse`, rather than always using `lastResponse`
- Android Auto: removed the custom `CarAppExtender` "Voice Reply" action that launched `VoiceRecordingScreen` from the notification; the car now handles voice reply natively via the `RemoteInput` on the base notification's Reply action

## [1.0.37] — 2026-05-31

### Fixed
- Android Auto: TTS playback now auto-starts when `LastOutputDetailScreen` opens; added explicit **Play Short** and **Play Long** action buttons so playback options are visible (previously the play button was a hidden info icon in the ActionStrip); **Play Long** speaks `currentStatusLong` inline without a screen push, so it works while driving (removed the parked-only `LongOutputScreen` navigation)
- Android Auto: voice recording (`VoiceRecordingScreen`) now uses `applicationContext` instead of `CarContext` for `SpeechRecognizer` binding — fixes the immediate-pop-back behaviour caused by `isRecognitionAvailable` returning false in the Car App service context; also shows a clear error state instead of silently popping when recognition is unavailable

## [1.0.36] — 2026-05-31

### Changed
- Android Auto: removed four dead-code screens superseded by the v1.0.28 UX overhaul (`WaitingSessionsScreen`, `WaitingPrdsScreen`, `SessionReplyScreen`, `PreMvpPlaceholderScreen`); updated `NavigationGraphTest` accordingly
- Rewrote `docs/android-auto.md` to reflect the current multi-screen architecture (was v0.32.0 three-screen design)

## [1.0.35] — 2026-05-31

### Fixed
- Android Auto: TTS speaker icon now opens `LastOutputDetailScreen` — shows the current status summary text with a "Long Version" button for the full `currentStatusLong` narrative and a playback button to listen; previously the icon spoke inline without showing the text or offering the long version

## [1.0.34] — 2026-05-31

### Fixed
- Android Auto: voice recording now uses Android's built-in `SpeechRecognizer` (Google ASR) instead of `CarAudioRecord` + Whisper — works while driving without hitting the host's audio recording restriction; Whisper transcription is still used in the phone app
- Android Auto: voice command button restored to the reply strip (mic icon in ActionStrip) for all non-terminal sessions including running ones, not just waiting ones
- Android Auto: TTS "play" (speaker icon) now always visible in the session detail ActionStrip so you can hear the current status, last response, or prompt at any time without going into reply mode

## [1.0.33] — 2026-05-31

### Fixed
- Android Auto: quick-reply actions (Yes / No / Continue / Skip) now work while driving — the inline reply view now stays on `MessageTemplate` instead of switching to `ListTemplate`; template-type transitions in the Messaging category are blocked while the vehicle is in motion
- Android Auto: voice recording from the notification reply button now shows a clear "Microphone not available — try from the notification reply button" toast instead of silently failing when the host blocks recording (e.g. while driving in some OEM configurations)
- Android Auto: bumped `minCarApiLevel` from 1 to 2, properly declaring support for `CarAudioRecord` to the host

## [1.0.32] — 2026-05-31

### Fixed
- CI: wear bundle now built and published to `wear:internal` Play Store track on every release tag; previously only the phone AAB was published, leaving the Wear OS track frozen at v1.0.1

## [1.0.31] — 2026-05-31

### Fixed
- All remaining read-only dropdown pickers across the app now open on tap anywhere in the field (`ExposedDropdownMenuBox` + `menuAnchor()`): `DashboardCardsCard` (2 pickers), `PrdDetailDialog` (3 pickers — backend, effort, permission mode), `NewPrdDialog` (4 pickers — workspace, backend, effort, model), `NewSessionScreen` `GroupedModelDropdown`

## [1.0.30] — 2026-05-31

### Fixed
- New Session: task field is now optional — Start button enabled without a task (server starts an interactive shell)
- New Session: all dropdowns now open on tap anywhere in the field, not just the trailing chevron icon (`SimpleDropdown` and cluster picker converted to `ExposedDropdownMenuBox`)
- Session Detail: D-pad arrow (↑↓←→) and ESC quick-action buttons now route through tmux `sendkey` instead of raw `send_input`, matching PWA behavior and bypassing PTY line buffering that caused arrows to require a manual Enter press

## [1.0.29] — 2026-05-31

### Added
- Android: `SessionStateWatcher` — detects Waiting-state transitions across REST poll cycles and fires a high-importance `InputNeeded` notification with Yes / No / Reply inline actions; cancels the notification when the session leaves Waiting state
- Android Auto: `InputNeeded` notifications now include a `CarAppExtender` with a "Voice Reply" action; tapping it in the car notification shade launches `VoiceRecordingScreen` directly via `DatawatchMessagingService.onNewIntent`

## [1.0.28] — 2026-05-31

### Added
- Android Auto: Session Detail ActionStrip with context-sensitive icon slots — Running shows status+response, Waiting shows prompt+status, Blocked shows block details+status, Completed/Killed shows last response
- Android Auto: LastOutputDetailScreen — shows session output (status, prompt, or response) with TTS playback and optional Long Version (parked-only) when long text is available
- Android Auto: LongOutputScreen — full long-text view pushed from LastOutputDetailScreen, parked-only, with TTS
- Android Auto: BlockDetailsScreen — lists active guardrail verdicts with Approve Gate and Kill Session actions plus TTS; pushed from Session Detail ActionStrip when session is blocked
- Android Auto: VoiceRecordingScreen — captures audio via CarAudioRecord with Recording/Transcribing states and Done/Cancel buttons; submits to Whisper transcription endpoint
- Android Auto: TranscriptionConfirmScreen — shows Whisper transcript with Send/Retry buttons and auto-play TTS on enter; Send delivers transcript to session and pops back to session detail
- Android Auto: Voice row added to Session Detail reply list — tap to push VoiceRecordingScreen
- Android Auto: Kill Pending now shows Confirm Kill (red) + Cancel buttons — Cancel exits kill-pending mode without killing
- Android Auto: Session Detail ActionStrip tracks promptContext and lastPrompt for Waiting state display
- Android Auto: Automata list rows show colored dot icons (red=awaiting approval, green=active) and 8-char block progress bars in subtitle
- Android Auto: Monitor screen adds forcedProfile constructor param for single-server drill-down from multi-server list
- Android Auto: Monitor multi-server rows are now tappable and push AutoMonitorScreen with that profile
- Android Auto: Monitor Sessions row in single-server mode is tappable and pushes AutoSessionListScreen
- Android Auto: About screen checks for updates on start; Update button only shown when status=update_available; shows live update status in body

## [1.0.27] — 2026-05-30

### Fixed
- Android: Inline notification reply (RemoteInput from the notification shade) was missing `\r`; typed text was sent to the session but never committed — now appends `\r` so the input actually runs in tmux

## [1.0.26] — 2026-05-30

### Fixed
- Android + iOS: Session Summarizer settings panel was always hidden — config read used wrong path `cfg.raw["session.summarizer.enabled"]` (flat top-level key) instead of the correct `cfg.raw["session"]["summarizer.enabled"]` (nested under the `session` object); summarizer is now visible when `session.summarizer.enabled = true` on the server
- Android + iOS: "AI Xm ago" badge never appeared — server returns Go zero time `0001-01-01T00:00:00Z` when no summary has run yet, which parsed as a valid `Instant` (year 0001); mapper now filters zero-time values as `null` so the badge only shows after the summarizer has actually produced output

## [1.0.25] — 2026-05-30

### Added
- iOS: Whisper voice transcription in session detail — mic button in composer bar (shown only when server has `whisper.enabled`); recording overlay with pulsing mic icon, Cancel / Send; full AVAudioSession permission → VoiceRecorder (16 kHz mono AAC M4A) → POST /api/voice/transcribe → populate reply text flow; `NSMicrophoneUsageDescription` added to Info.plist
- Android + iOS: Session cards show "AI Xm ago" age badge in primary colour when server returns `summary_generated_at` (v8.9.5 field); badge positioned left of activity timestamp
- Android + iOS: Settings → Session Summarizer — "Test" button calls POST /api/summarizer/test (v8.9.5); shows `✓ ok · Xms` on success or `✗ <error>` on failure; `SummarizerTestResultDto` + `testSummarizer()` in transport layer

### Fixed
- Android + iOS + Auto: Reply/Enter submission broken end-to-end — `\r` added to all send paths (`sendReply`, `sendQuickReply`, quick-reply chips, saved commands); REST endpoint changed from defunct `/api/sessions/reply` (404) to `/api/sessions/{id}/input` with `SessionInputDto`; Samsung spurious Enter suppressed in `InputConnectionWrapper` via `recentCommit` flag; iOS routes reply text through `terminalInput` binding → xterm WebSocket `window.sendInput`
- iOS: `SessionReplyScreen` quick-reply rows were missing `\r` suffix; orphaned `isSendingReply` state variable removed (would have permanently disabled Send button after first use)

## [1.0.19] — 2026-05-30

### Changed
- Android Auto: session detail "Send" button now available for all non-terminal sessions (Running, Waiting, RateLimited) — not just Waiting — enabling command injection into any active session
- Android Auto: "Quick Reply" renamed to "Send Command"; expanded from 4 to 6 options (max): Yes, No, Continue, Skip, Status, Stop

## [1.0.18] — 2026-05-30

### Fixed
- Android Auto: Monitor screen crashed with DuplicateFormatFlagsException when CPU load was available — progress bar's literal "%" character was being parsed as a format flag when interpolated into a .format() call; changed to "${"%.2f".format(load1)}" to keep formatting scoped to the number only

## [1.0.17] — 2026-05-30

### Added
- Android: Settings → General → Session Summarizer card — toggle `session.summarizer.enabled` and pick `session.summarizer.llm_ref` (Ollama LLMs only) via PUT /api/config (closes #147)
- iOS: Settings → Session screen — same toggle + LLM picker; new IosServiceLocator callbacks: `fetchSummarizerConfig`, `writeConfigBool`, `writeConfigString`, `listOllamaLlmNames` (closes #147)

## [1.0.16] — 2026-05-29

### Changed
- Android Auto: Summary is now the root screen (was Monitor); Monitor is accessed via the icon in the ActionStrip
- Android Auto: Summary server row merges CPU/mem progress bars inline ("cpu ▓▓░░░░░░ 22%  mem ▓▓▓░░░░░ 38%") — eliminates the separate System row
- Android Auto: Session rows now show colored state-dot icons (green=running, amber=waiting, red=blocked/error, gray=done/killed) and unicode glyphs (◉ ⊙ ⊗ ✓ ✗) with block-char progress bars
- Android Auto: Monitor detail rows show block-character progress bars for CPU and Memory
- Android Auto: About screen upgraded to MessageTemplate with ASCII art header and Reboot/Update action buttons (calls restartDaemon / updateDaemon on the active server)
- Android Auto: Summary ActionStrip changed from Server+Monitor to Info(About)+Monitor; server picker accessible via server header row tap
- Android Auto: Summary removes alerts row and separate Waiting sessions row (access waiting sessions via Sessions list)

## [1.0.15] — 2026-05-29

### Fixed
- Android Auto: session detail screen crashed with "Message cannot be empty" when a session had no current task, no sprint, zero progress, no ETA, and no guardrail verdicts — body string was empty; fallback to state name

## [1.0.14] — 2026-05-29

### Fixed
- xterm: unconditionally suppress KEYCODE_DPAD_CENTER in both dispatchKeyEvent and InputConnectionWrapper.sendKeyEvent — timing-gate approach was unreliable when Samsung's spurious CENTER arrived >100 ms after the triggering key; added DPAD_CENTER to IC wrapper to also block the IME-side path
- xterm: add sendInput logging at the Java bridge layer to capture all bytes sent to server (both xterm onData path and direct evaluateJavascript path)

## [1.0.13] — 2026-05-29

### Fixed
- xterm: Samsung S24 Ultra fires KEYCODE_DPAD_CENTER after **every** keypress (not just directional keys); widened timing gate to track all key ACTION_DOWN events so every spurious DPAD_CENTER within 100 ms is suppressed

## [1.0.12] — 2026-05-29

### Fixed
- Android Auto: comprehensive standards audit — 6 violations fixed across 9 screens: CoroutineScope leaks (missing SupervisorJob + onDestroy cancel), unconditional invalidate() burning host template quota, VoiceStatusScreen push exceeding max stack depth (5), Monitor ActionStrip creating duplicate stack entry (popToRoot instead of push), reply-mode BACK action incorrectly popping the whole screen (replaced with Cancel in ActionStrip)
- xterm: Samsung D-pad sends spurious KEYCODE_DPAD_CENTER after every directional press; timing gate (100 ms) suppresses the phantom Enter while honouring intentional centre presses

## [1.0.11] — 2026-05-29

### Fixed
- Android Auto: second crash fixed — ForegroundCarColorSpan is not allowed in MESSAGING category templates; replaced with plain CarText across all Auto screens (AutoSummaryScreen, AutoMonitorScreen, AutoSessionListScreen, AutoServerPickerScreen, AutoAutomataScreen, AutoAboutScreen, SessionReplyScreen, AutoSessionDetailScreen)

## [1.0.10] — 2026-05-29

### Fixed
- Android Auto: crash on connect fixed — ActionStrip was exceeding the Car App Library hard limit of 2 actions (AutoMonitorScreen had 4, AutoSummaryScreen had 3); reduced to 2 on each screen

## [1.0.4] — 2026-05-27

### Changed
- Tagline updated from "AI Session Monitor" to "AI Orchestration" across splash screen and Wear OS about screen (all locales)

### Fixed
- iOS Automata tab: full CRUD screens replacing placeholder cards
- iOS terminal: keyboard no longer covers bottom rows (DwWKWebView layoutSubviews → dwExplicitSize)
- CI: Wear OS signing config made conditional; iOS XCFramework task name corrected; detekt Composable exemptions added

## [1.0.3] — 2026-05-27

### Fixed
- **Android Auto item-list overflow**: `AutoMonitorScreen` single-server mode added a profile header row plus up to 6 detail rows (CPU + Memory + Disk + GPU + Sessions + Uptime), reaching 7 items against the Car App Library's 6-item `ItemList` cap — resulting in `IllegalArgumentException` at `items.build()`. Removed the redundant profile header row; the template title now shows the server's display name in single-server mode.

## [1.0.2] — 2026-05-27

### Added — iOS client (Stories 1–15)
- **iOS app** — full SwiftUI client targeting iOS 16.0+; first-class platform alongside Android.
- **Build environment** (Story 1): `.github/workflows/ios-build.yml` macOS-15 CI; `xcodegen` project generation; KMP `XCFramework` DSL; version parity with `gradle.properties`; `scripts/build-ios-framework.sh`.
- **Shared module iOS hardening** (Story 2): `IosDatabaseFactory` with `NSFileProtectionComplete` (Apple Secure Enclave AES-256); `IosTokenStore` via CoreFoundation SecItem* Keychain API; Darwin Ktor trust-all challenge handler for self-signed certs.
- **Design system** (Story 3): `DatawatchColors`, `DatawatchFonts`, `HeaderView`, `ConnectionStatusBanner`, `SessionCountChip`, `LoadingIndicator`, `ErrorCard`; 6-tab `RootView`; `AppRouter` deep-link handling; `FlowAdapter` Kotlin→Swift async stream bridge.
- **Auth & Server Connection** (Story 4): `IosServiceLocator` Kotlin DI singleton with CoroutineScope + callback bridge; `ServerProfileStore` ObservableObject; `AddServerView`/`EditServerView` with connection probe; `ServerProfileListView`; `BiometricGate` (Face ID/Touch ID); `SettingsView`.
- **Sessions** (Story 5): `SessionsViewModel` with 10 s polling, `SessionsView` with state dots + backend chips, pull-to-refresh.
- **Terminal** (Story 6): `TerminalView` — WKWebView + CDN xterm.js, WebSocket to `/api/terminal/<id>?token=…`; `SessionDetailView` with Kill action.
- **Automata** (Story 7): placeholder cards per server with "View in web UI" link; full CRUD deferred to v1.1.
- **Alerts** (Story 8): `AlertsViewModel` polling, severity icons (teal/amber/red), unread badge, swipe-to-dismiss.
- **Observer** (Story 9): 5 s polling metrics grid (CPU/memory/disk/VRAM); color-coded bars; uptime display.
- **Dashboard** (Story 10): parallel multi-server cards with concurrent sessions + stats fetch; mini resource bars.
- **Push Notifications stub** (Story 12): `NotificationService` APNs authorization + device token registration (blocked on datawatch#107 server support); `AppDelegate` wired via `@UIApplicationDelegateAdaptor`.
- **iPad layout** (Story 13): `NavigationSplitView` on `.regular` horizontal size class.
- **App Store prep** (Story 15): `ios-build.yml` TestFlight upload job (commented, ready to enable after enrollment); `ExportOptions.plist`; `fastlane/Fastfile` + `Matchfile` stubs.
- **Android Auto black-screen fix** (v1.0.2 patch): `hosts_allowlist.xml` was excluded from the build because `auto/build.gradle.kts` `sourceSets` only added `java.srcDirs`/`manifest` from `publicMain`, not `res.srcDirs`. At runtime `getIdentifier()` returned 0; `addAllowedHosts(0)` threw `Resources.NotFoundException`, crashing the CarAppService before any screen rendered. Fixed by adding `res.srcDirs("src/main/res", "src/publicMain/res")` and switching to compile-time `R.array.hosts_allowlist`.

### iOS v1.1 known gaps (planned)
- Automata CRUD (requires `listAutomataTypes` server API)
- Push notifications (requires datawatch#107 server-side APNs endpoint)
- Session start from app (model/profile picker)
- Terminal IME keyboard-overlap fix (Android pattern: `onSizeChanged → dwExplicitSize`)
- Alert server-side dismiss

## [1.0.1] — 2026-05-26

### Added
- New Session: opt-in **Chrome integration** toggle in Advanced (claude options) — sends `chrome: true` on `POST /api/sessions/start`, enabling the parent datawatch v8.8.3 Chrome DevTools Protocol launch. Default off; no behaviour change for existing sessions.
- Session Detail: chip surfaces when the session was started with Chrome integration.
- Locale strings `session_chrome` translated across all 5 bundles (EN/DE/ES/FR/JA).
- Wear: `wearable.standalone=true` (Samsung companion compatibility) — first shipped in 100307 build, carried forward.

### Server compatibility
- Pairs with datawatch v8.8.3+ for Chrome integration; older servers ignore the unknown `chrome` field.

## [1.0.0] — 2026-05-21 (General Availability)

🚀 **v1.0.0 marks the first production-ready release of the datawatch Android companion suite.**

### Overview
This release delivers feature parity across all supported platforms (Android phone, Wear OS, Android Automotive OS) with comprehensive Play Store integration, security hardening, and production-quality test coverage. The app is now ready for alpha testing and public use.

### Major Features
- **Platform Parity**: Identical feature set across phone, Wear OS, and Android Auto (AAOS)
- **Live Session Streaming**: WebSocket-backed real-time chat, terminal output, and process metrics
- **Multi-Server Management**: Seamless switching between Tailscale, LAN, and public datawatch instances
- **Wearable Integration**: Wear OS tiles, complications, and Glance surfaces for at-a-glance monitoring
- **Automotive Support**: Native AAOS integration with day/night theme sync and voice input
- **Enterprise Security**: SQLCipher encryption at rest, Android Keystore integration, optional biometric unlock
- **Responsive Design**: Full support for phones, tablets, and foldables with two-pane layouts
- **Play Store Ready**: Gradle Play Publisher integration for automated releases and track management

### What's New in 1.0.0
- ✅ Complete PWA parity pass — all UI/UX goals from Sprint 44 shipped
- ✅ Fixed critical race conditions in Automata tab LazyColumn rendering
- ✅ Corrected orchestrator graph creation with proper PRD ID handling
- ✅ Fixed all Algorithm Mode action buttons (start, advance, abort, reset, edit, measure)
- ✅ Play Store integration with service account authentication and automated publishing
- ✅ Gradle Play Publisher v3.10.0 configured for internal/alpha/beta/production tracks
- ✅ Comprehensive E2E validation suite with Maestro instrumentation layer
- ✅ Certificate fingerprints registered for secure app signing

### Infrastructure
- Pairs with `datawatch v8.6.1+` server daemon
- Minimum: Android API 29 (Android 10), Wear OS 2.0+, AAOS 11+
- Target: Android API 35 (Android 15)
- Build system: Kotlin Multiplatform + Compose Multiplatform for cross-platform UI
- Testing: Robolectric + MockWebServer + Turbine for comprehensive JVM test coverage

### Security & Privacy
- SQLCipher-backed encrypted database for sensitive configuration
- Android Keystore protection for bearer tokens (ECDH key agreement)
- Biometric unlock option for protected sessions
- SSL certificate pinning via OkHttp TrustManager
- UnifiedPush support for secure push notifications (no FCM dependency)

### Play Store
- Available for internal testing on Google Play Console
- Ready for alpha/beta staged rollout
- Supports multiple release tracks (internal, alpha, beta, production)
- Automated rollout via gradle-play-publisher CLI

### Known Limitations
- Automata/Algorithm Mode fully functional; PRD decompose may timeout on slow networks
- Android Auto UI requires DHU (Drive Headless Unit) emulator for full testing
- Wear OS complications require paired phone for initial bearer token setup

### Migration Notes
Users upgrading from 0.x should note:
- App is now served from Play Store (com.dmzs.datawatchclient)
- Debug/dev builds use separate package (com.dmzs.datawatchclient.dev)
- Play Console enrollment moved from PWA-specific setup to Android app-specific workflows
- Bearer token and server configuration persist across updates via SQLCipher encrypted database

### Testing
261 unit tests pass across Robolectric/JVM test layer. End-to-end validation suite covers:
- Session creation, messaging, and termination
- Live process metrics (CPU, RSS, network)
- Multi-server failover and switching
- Wear OS tile/complication rendering
- Android Auto CarAppActivity navigation

### Acknowledgments
This release represents the completion of the PWA parity arc started in Sprint 1, with full platform coverage and production hardening across phone, wearable, and automotive surfaces. Special thanks to all testers and stakeholders who validated every surface.

## [0.123.0] — 2026-05-20 (PWA parity pass — Sprint 44)
### Added
- AlertsScreen: primary tabs (Active/Historical/System) always visible; chip filters (All/Prompt/Error/Warn/Info) moved to per-tab secondary filter row matching PWA layout
- AutonomousScreen: LifecycleStrip on PRD rows showing review→approved→decompose→run→done
- AutonomousScreen FAB: ⚡ emoji on PRDs tab, + on Templates tab (PWA parity)
- Observer, Dashboard: loading spinner when server changes
### Fixed
- #136: UnifiedPushSseService SSL trust gap — tracks transport signature per job; cancels and restarts with fresh OkHttpClient on trust-setting change
- #142: Automata LazyColumn duplicate-key crash — key now uses "profileName|prdId" composite
- #143: Orchestrator graph creation — add prd_ids to CreateOrchestratorGraphRequestDto so graph-creation wires automata at creation time
- #144: Algorithm Mode UI buttons — all 6 RestTransport methods now use correct subpath routes (start, advance, abort, reset, edit, measure)
- PwaStatePill: 1dp border matching PWA `border: 1px solid currentColor`
- BottomNavBar: Dashboard emoji ☷ → ⊞ (U+229E, matches PWA)
- AlertsScreen stateAccentColor: Running 0xFF22C55E → DwSuccess 0xFF10B981; global alignment of all ok/running/enabled indicators with PWA --success token
### Changed
- Version bump: 0.122.0/200 → 0.123.0/201

## [0.122.0] — 2026-05-20 (Observer/Dashboard loading spinners — Sprint 43)
### Added
- ObserverScreen: fullscreen spinner while loading (profile change or initial load)
- DashboardScreen: spinner on server switch via cardsLoaded flag reset
### Changed
- Version bump: 0.121.0/199 → 0.122.0/200

## [0.121.0] — 2026-05-20 (Automata race fix; alerts sub-tab labels; Dashboard all-servers — Sprint 43)
### Fixed
- AutonomousViewModel: fix race in refreshAllServers() — now uses profileRepository.observeAll().first() to wait for actual DB data (was reading empty _allProfiles.value)
- AlertsScreen: all-servers sub-tabs show "server/shortId" format instead of repeating session names
- AppRoot: set dashboardEnabled=true in all-servers mode early-return branch
### Changed
- Version bump: 0.120.0/198 → 0.121.0/199

## [0.120.0] — 2026-05-20 (Loading states + all-servers data — Sprint 43)
### Fixed
- Sessions: immediate refresh when switching to all-servers mode; CircularProgressIndicator during refresh
- Automata: show spinner while state.loading==true (was blank/watermark-only)
- Alerts: fix all-servers mode Active tab empty — sessionsFlow now combines sessions from all enabled profiles
### Changed
- Version bump: 0.119.0/197 → 0.120.0/198

## [0.119.0] — 2026-05-16 (PWA-parity UI pass — Sprint 42)
### Added
- In-app docs viewer (? button): howtos browser, add-source, all-servers mode; link in Settings General and all tab headers
- Alert pill matching PWA headerAlertPill (🔔 N style); dock explicit-open-only (never auto-spawns)
- All-servers / combined view for Autonomous and Alerts screens
- Alerts header server picker as dropdown title; autonomous combined mode skips unconfigured servers
- Standardized tab headers across all 6 tabs: server picker, status, alerts, filter, docs
- Algorithm Mode: Start, Reset, Edit, Measure UI actions wired to transport endpoints
- Autonomous: Cancel button expanded to all cancellable states; Clone to Template button added
- Dashboard tab added for PWA alpha.71 parity
- Nav icons all emoji matching PWA (⊞ for Dashboard, 🤖 for Automata, etc.)
- Hide mic button when server has no Whisper backend configured
### Fixed
- Transport: scan config endpoint + DTO field name mismatches; evals DTO id/cases SerialName fix
### Changed
- Version bump: 0.118.0/196 → 0.119.0/197

## [0.118.0] — 2026-05-15 (QA bug fixes — Sprint 34)
### Fixed
- BL-T1-1: AddServerScreen URL field now shows error + message "URL must start with http:// or https://" when scheme is missing (was silently disabling Submit)
- BL-T4-1: SessionsViewModel.restart() now optimistically upserts the returned Session before calling refresh(), preventing restart→invisible-session race
### Changed
- Version bump: 0.117.0/195 → 0.118.0/196

## [0.117.0] — 2026-05-15 (T13 QA fixes — Sprint 33)
### Added
- NewPrdDialog: spec field wired to NewPrdRequestDto (was missing from PRD create form)
- PrdDto.decisions: List<DecisionDto> replacing List<String>; DecisionDto(at, kind, actor, note) renders structured decision labels in PrdDetailDialog
### Changed
- Version bump: 0.116.0/194 → 0.117.0/195

## [0.116.0] — 2026-05-14 (Multi-server & federation QA — Sprint 32)
### Changed
- Multi-server dedup and federation story TS-206–220 completed; known gap: true session interleave requires two distinct backend URLs
- Version bump: 0.115.0/193 → 0.116.0/194

## [0.115.0] — 2026-05-14 (Security & keystore QA — Sprint 31)
### Changed
- T11 Security & Keystore QA complete: 6 pass, 4 skip (biometric requires physical device; SecretsStatusCard requires /api/secrets/status)
- Version bump: 0.114.0/192 → 0.115.0/193

## [0.114.0] — 2026-05-14 (Push SSE fixes — Sprint 30)
### Fixed
- SSE infinite stream: use prepareGet().execute{} instead of client.get() to avoid requestTimeout
- SSE event parsing: DefaultJson with ignoreUnknownKeys for ring server event shape
- ForegroundSessionTracker.isForeground("") forced onto main thread at app start
- Mute-preservation: INSERT OR REPLACE overwrote muted column on every server refresh; replaceAll() now captures mutedIds before delete and restores overrideMuted
### Changed
- Version bump: 0.113.0/191 → 0.114.0/192

## [0.113.0] — 2026-05-13 (Navigation & shell QA — Sprint 29)
### Changed
- T9 Navigation & Shell complete: bottom nav, back-stack, deep links (dwclient://session/<id>), splash, landscape two-pane, Autonomous tab visibility all verified
- Version bump: 0.112.0/190 → 0.113.0/191

## [0.112.0] — 2026-05-13 (Session detail terminal fixes — Sprint 28)
### Fixed
- BL-T3-3: filter PaneCapture+ChatMessage before ChatEventList; zero-height items no longer make the channel tab appear blank on tmux sessions
- BL-T3-4: InputRequiredBanner wired into terminal branch; needsInput simplified to session?.needsInput==true (no longer requires a live PromptDetected WS event)
### Changed
- Version bump: 0.110.0/188 → 0.112.0/190

## [0.110.0] — 2026-05-12 (Session rename modal — Sprint 27)
### Fixed
- BL-T3-2: replace inline rename BasicTextField with RenameDialog modal; inline field lost focus immediately due to WebView recapture via headerRenameFocusChain
### Changed
- Version bump: 0.109.0/187 → 0.110.0/188

## [0.109.0] — 2026-05-12 (Session list DB fixes — Sprint 26)
### Fixed
- BL-T14-1: ON_RESUME lifecycle observer on SessionsScreen calls vm.refresh() immediately on app-foreground/unlock/screen-return
- BL-T14-2: llmRef and computeNodeRef now persisted to SQLDelight; DB migration 7→8 adds llm_ref + compute_node_ref columns
### Changed
- Version bump: 0.108.0/186 → 0.109.0/187

## [0.108.0] — 2026-05-12 (Cosmetic PWA gaps — Sprint 39)
### Added
- Help links in Settings cards; drag handle glyph on bottom sheets; alert dock anchored to screen bottom
### Changed
- Version bump: 0.107.0/185 → 0.108.0/186

## [0.107.0] — 2026-05-12 (Alerts per-session sub-tabs — Sprint 38)
### Added
- AlertsScreen: ScrollableTabRow sub-tabs when 2+ sessions have alerts (per-session filtering; G16)
### Changed
- Version bump: 0.106.0/184 → 0.107.0/185

## [0.106.0] — 2026-05-12 (Observer nav tab + alert pill — Sprint 37)
### Added
- Observer screen promoted to bottom navigation tab (G1)
- Global header alert pill showing unread alert count (G2)
### Changed
- Version bump: 0.105.0/183 → 0.106.0/184

## [0.105.0] — 2026-05-12 (Sprint card JSON + Council config + PRD 4-tabs — Sprint 36)
### Added
- SprintCard: full JSON rendered via prettyPrint in scrollable SelectionContainer (matches PWA pre-block; G11)
- CouncilConfigDto: llm_ref, max_parallel, draft_retention_days; CouncilCard adds three config fields with Save button (G14/G22)
- PrdDetailDialog: 4-tab layout (Overview | Stories | Decisions | Scan); Decisions tab renders DecisionDto list (G21)
### Changed
- Version bump: 0.104.0/182 → 0.105.0/183

## [0.104.0] — 2026-05-12 (Observer stats + PID + Focus card — Sprint 35)
### Added
- SessionStatsViewModel: migrated from WebSocket StatsHub to polling GET /api/observer/envelopes?session_id={id} every 5 s (G8)
- Host card: PID row with child-process count when rootPid > 0 (G9)
- FocusCard: lastEvent subtitle (event · tool · timeAgo) + amber idle-since chip when idleSince > 5 min (G10)
- HookHealthPill: clickable — tap triggers immediate status refresh (G12)
- Docs ↗ TextButton next to hook pill when hooks are stale/missing (G13)
### Changed
- Version bump: 0.103.0/181 → 0.104.0/182

## [0.103.0] — 2026-05-12 (LLM alpha.41 overhaul — Sprint 34)
### Added
- LlmRegistryEntryDto: 16 new alpha.41 fields (api_key_ref, timeout, tags, session-backend binary/console/git/output/input, claude-code skip_permissions/channel_enabled/auto_accept/permission_mode/default_effort/fallback_chain)
- LLM add/edit dialog: three collapsible sections for new fields; pretest Checkbox → Switch (G20)
- 18 i18n keys in all 5 locales
### Changed
- Version bump: 0.102.0/180 → 0.103.0/181

## [0.102.0] — 2026-05-12 (Session detail tab restructure — Sprint 33)
### Added
- Session detail Status tab: Status|Stats sub-tabs matching PWA alpha.36 (G6)
- Channel tab gated to claude/claude-code/opencode-acp backends only (G7)
### Changed
- Removed obsolete statsMode flag; replaced with statusMode + statusSubStats state vars
- Version bump: 0.101.0/179 → 0.102.0/180

## [0.101.0] — 2026-05-12 (Wear sessions list + state badges — Sprint 32)
### Added
- `WearSyncService` publishes `/datawatch/sessions` DataItem with shortId, state, task, and lastActivity arrays (top 10 by last-activity); republishes on every session state or task change
- `WearMainActivity` sessions page: scrollable list replacing the previous count-only view; each row shows state badge dot, shortId, task text (30-char truncated), and "Xm ago" timestamp
- State badge colours aligned to datawatch dark palette: Running → teal `#1DE9B6`, Waiting → amber `#FFB300`, Error/Failed/Killed → error red `#EF4444`, Done → dim onSurface
- 5 new wear locale keys in all 5 bundles (EN/DE/ES/FR/JA): `wear_sessions_empty`, `wear_session_state_running`, `wear_session_state_waiting`, `wear_session_state_done`, `wear_session_state_error`
### Changed
- Version bump: 0.100.0/178 → 0.101.0/179

## [0.100.0] — 2026-05-12 (Council persona CRUD + alert dock audit — Sprint 31)
### Added
- Council settings persona list: `GET /api/council/personas` populates rows per persona; 4 built-in personas (platform-engineer, network-engineer, data-architect, privacy) shown with "Built-in" badge; Name field locked for built-ins; Delete button hidden for built-ins
- `CouncilPersonaWizardSheet` / persona edit dialog: Name, Description, System prompt fields; Save calls `PUT /api/council/personas/{name}`; Delete (custom only) calls `DELETE /api/council/personas/{name}` after AlertDialog confirm
- `TransportClient` council persona methods: `getCouncilPersonas`, `getCouncilPersona`, `setCouncilPersona`, `deleteCouncilPersona`
- 12 new locale keys in all 5 locales: `council_personas_title`, `council_persona_name`, `council_persona_description`, `council_persona_system_prompt`, `council_persona_builtin_badge`, `council_persona_add`, `council_persona_edit`, `council_persona_save`, `council_persona_delete`, `council_persona_none`, `council_persona_name_required`, `council_persona_delete_confirm_title`
### Fixed
- `AlertDockOverlay` auto-expand guard: dock panel starts `expanded=false`; only toggles on explicit chevron tap — passive alert arrivals update badge count only

### Changed
- Version bump: 0.99.0/177 → 0.100.0/178

## [0.99.0] — 2026-05-11 (LLM multi-node model table + Automata batch confirm — Sprint 30)
### Added
- `LlmConfigCard` row now shows `models[]` per-node pairs (up to 3 before collapse) replacing the single model string field
- Add/Edit LLM panel: per-node model table with Add/Remove rows; SaaS LLMs (no compute nodes) fall back to single model text input; `autoAddModels` flag shows Auto badge and display-only table
- `LlmDetailDrawer` two new tabs: Models tab (per-node table + Refresh) and In-use tab (paginated session list with 5/10/50 per-page picker)
- DELETE 409 (sessions active) flow: inline reassign prompt inside the drawer with LLM dropdown and "Reassign & delete" button; secondary "Force delete" link with confirmation
- Enable toggle for LLMs: `PATCH /api/llms/{name}/enabled` with spinner; failure reverts toggle and shows inline error
- Automata batch-delete confirm modal: `AlertDialog` with `automata_confirm_batch_delete` text replacing browser-style confirm
- New `TransportClient` methods: `setLlmEnabled`, `getLlmSessions`, `reassignLlmSessions`
- 15 new locale keys in all 5 locales: `llm_models_tab`, `llm_in_use_tab`, `llm_models_node_col`, `llm_models_model_col`, `llm_models_add_row`, `llm_models_auto_badge`, `llm_models_none`, `llm_in_use_none`, `llm_in_use_task_col`, `llm_delete_blocked_n`, `llm_reassign_to`, `llm_reassign_btn`, `llm_force_delete`, `llm_force_confirm`, `automata_confirm_batch_delete`
### Changed
- Version bump: 0.98.0/176 → 0.99.0/177

## [0.98.0] — 2026-05-11 (UnifiedPush SSE alerts stream — Sprint 28)
### Added
- UnifiedPush SSE supplement: on app start, registers each enabled server profile via `POST /api/push/register` using a stable per-server `client_id` stored in SharedPreferences
- Persistent SSE subscription to `GET /api/push/alerts` (ntfy-compat format); reconnects with exponential backoff (1 s → 2 s → 4 s → 30 s max) on disconnect
- Priority ≥ 4 events rendered as `PRIORITY_HIGH` heads-up notifications; `click` field wired to deep-link `PendingIntent` for session detail route
- New `TransportClient` methods: `registerPush`, `subscribePushAlerts` (SSE `Flow`)
- 3 new locale keys in all 5 locales: `push_registered`, `push_failed`, `push_notification_channel`
### Changed
- ntfy wake-on-alert path unchanged; UnifiedPush SSE runs in parallel as intentional redundancy
- Version bump: 0.97.0/175 → 0.98.0/176

## [0.97.0] — 2026-05-11 (Ollama marketplace + Alerts 3-tab redesign — Sprint 27)
### Added
- Compute Node edit: "Models" sub-section visible when `node.kind == "ollama"`; lists installed models with per-model remove button; "Browse marketplace" button opens catalog bottom sheet
- Ollama Marketplace bottom sheet: fetches catalog, search bar filters by name; per-model tag grid showing Size / Min RAM / Min VRAM / Fit / Pull button; pull progress polls every 2 s and shows "Pulling X (N%)"
- `AlertsScreen` redesigned with three tabs: Active (unresolved), Historical (resolved/acknowledged), System (daemon events); per-tab chip filter, sort mode, and search text persisted in SharedPreferences
- Alert dock: max-width increased to 420 dp; header font 14 sp, chip font 12 sp; per-type colour rail (left edge stripe)
- New `TransportClient` methods: `getOllamaCatalog`, `getInstalledOllamaModels`, `pullOllamaModel`, `getPullTask`, `deleteOllamaModel`
- 18 new locale keys in all 5 locales: `alerts_active_tab_label`, `alerts_historical_tab_label`, `alerts_system_tab_label`, `ollama_marketplace_title`, `ollama_browse_btn`, `ollama_installed_models`, `ollama_no_installed`, `ollama_pull_btn`, `ollama_remove_btn`, `ollama_pulling`, `ollama_pull_done`, `ollama_tag_size`, `ollama_tag_min_ram`, `ollama_tag_min_vram`, `ollama_tag_fits`, `ollama_tag_tight`, `compute_models_title`, `compute_field_models`
### Changed
- Version bump: 0.96.0/174 → 0.97.0/175

## [0.96.0] — 2026-05-11 (Session detail Status tab + hook arc — Sprint 26)
### Added
- Session detail "Status" 4th sub-tab (tab order: Tmux/Chat → Channel → Stats → Status); polls `GET /api/sessions/{id}/status` every 5 s while focused; stops on tab leave
- Status board cards (all conditional): Current Focus, Sprint (name + progress), Tests (passing/failing/total; failing count red), Git (branch, uncommitted files, commits ahead)
- Hook health pill below tab label: "Hooks alive" (green) / "Hooks stale" (amber) / "Hooks missing" (grey)
- Hook auto-install Snackbar fires on `claude-code` session start when daemon indicates hooks were installed
- Alert body display extended to handle `· last tool: X · <snippet>` format without over-truncation
- New DTO classes: `SessionStatusBoardDto`, `SprintStatusDto`, `TestStatusDto`, `GitStatusDto`
- New `TransportClient` method: `getSessionStatus` (`GET /api/sessions/{id}/status`)
- 14 new locale keys in all 5 locales: `session_detail_tab_status`, `status_card_focus`, `status_card_sprint`, `status_card_tests`, `status_card_git`, `status_no_focus`, `status_no_sprint`, `status_no_tests`, `status_no_git`, `status_hooks_alive`, `status_hooks_stale`, `status_hooks_missing`, `status_llm_more_soon`, `status_hooks_installed_toast`
### Changed
- Status tab works for all session backends via the universal state-change emit (no app-side special-casing required)
- Version bump: 0.95.0/173 → 0.96.0/174

## [0.95.0] — 2026-05-11 (Stats sub-tab sectioned cards + sparklines — Sprint 25)
### Added
- `SessionStatsPanel` redesigned with sectioned cards replacing the flat `StatEnvelopeDto` view: Host card (always shown; CPU dial + sparkline, RSS + sparkline, Threads, FDs, Net Rx/Tx), Container card (conditional on `envelope.container`), ComputeNode card (conditional on `session.computeNodeRef`; GPU stats when present; "Open in Compute →" nav link), LLM card (conditional on `session.llmRef`; "Open in LLM →" nav link)
- CPU and RSS sparklines: 60-sample circular buffer per session held in `SessionStatsViewModel`; drawn with `Canvas` + `DrawScope.drawPath` (no external chart library)
- `ContainerInfoDto` added to `StatEnvelopeDto` (`container` field, nullable)
- 16 new locale keys in all 5 locales: `stats_card_host`, `stats_card_container`, `stats_card_compute_node`, `stats_card_llm`, `stats_field_cpu`, `stats_field_rss`, `stats_field_threads`, `stats_field_fds`, `stats_field_net`, `stats_field_gpu`, `stats_open_compute`, `stats_open_llm`, `stats_llm_more_soon`, `stats_container_id`, `stats_container_image`, `stats_container_runtime`
### Changed
- Version bump: 0.94.0/172 → 0.95.0/173

## [0.94.0] — 2026-05-11 (Automata browse redesign + Sessions filter collapsible — Sprint 24+29)
### Added
- `PrdRow` pin button: toggles pinned state per automaton; pinned set persisted in DataStore per server profile (`pinned_automata_<serverProfileId>` key); pinned cards always render at top
- New automata sort order: pinned first → state rank (waiting-input/needs-review/revisions-asked → blocked → running → planning → done/cancelled/approved) → last-activity descending
- `PrdRow` inline action row: Open, Cancel, Approve buttons; Approve highlighted (amber tint) when state is `needs_review`, `revisions_asked`, or `waiting_input`; Cancel shows confirm modal (`automata_confirm_cancel`)
- `PrdRow` card additions: status pill, last-activity timestamp top-right, collapsible "Stories & tasks" section (shown when count > 0)
- Sessions filter bar redesigned: `LLM (N) ▸` collapsible button with backend-family badges; `State (N) ▸` collapsible with all real states and colour-dot rail; filter text input ~10% wider
- Alert dock max-width changed to `min(340.dp, screenWidth - 16.dp)` for narrow viewports
- Sprint 23 test debt: 4 ViewModel tests added to shared unit test suite: `toggleWatch_addsToWatchedIds`, `toggleWatch_removesFromWatchedIds`, `watchedAlertCount_reflectsWatchedSessions`, `bottomNavBar_selectedTabMatchesRoute`
- New `TransportClient` methods: `approveAutomaton`, `cancelAutomaton`
- 10 new locale keys in all 5 locales: `automata_action_open`, `automata_action_cancel`, `automata_action_approve`, `automata_pin`, `automata_unpin`, `automata_last_activity`, `automata_stories_tasks`, `automata_confirm_cancel`, `llm_filter_btn_tip`, `state_filter_btn_tip`
### Changed
- Version bump: 0.93.0/171 → 0.94.0/172

## [0.93.0] — 2026-05-10 (Watch toggle opt-in — Sprint 23)
### Added
- `WatchedSessionsStore`: SharedPreferences-backed per-profile set of watched session ids; reactive `watchedFlow()` via `callbackFlow`

## [0.92.0] — 2026-05-10 (Alerts redesign — alpha.30)
### Added
- `AlertsViewModel`: `ChipFilter` + `SortMode` enums; nested combine for filter/sort/search; `dismissAll()`; `flatChronoAlerts` in `UiState`
- `AlertsScreen`: custom top bar (title + mute + sort toggle + dismiss-all); horizontal chip filter row; always-visible search field; flat chronological view when `SortMode.Chronological`; prompt-type amber + error red tinting on `AlertCard`
- `BottomNavBar`: `alertsMuted` param; always-on badge (dims at zero, shows muted icon when muted)
- 11 new locale keys in EN/DE/ES/FR/JA
### Changed
- Version bump: 0.91.0/169 → 0.92.0/170

## [0.91.0] — 2026-05-10 (Alerts tile + complication — Wear)
### Added
- `AlertsTileService`: 30 s tile showing total/needs-input/error counts from DataLayer; health dot (amber/red/green); tap → WearMainActivity
- `AlertsComplicationService`: `SHORT_TEXT` badge reading same DataItem
- `WearSyncService`: publishes total/needsInput/errors counts to `/datawatch/alerts` DataItem
- Locale strings `tile_alerts_label` / `complication_alerts_label` in Wear EN/DE/ES/FR/JA
### Changed
- Version bump: 0.90.0/168 → 0.91.0/169

## [0.90.0] — 2026-05-10 (Alert dock overlay — alpha.29)
### Added
- `AlertDockOverlay`: floats top-right when ≥2 active alerts exist; collapsed pill shows count + category badges (needs-input, error) + expand chevron + dismiss + mute; expanded shows scrollable last-100-alerts list with health dot + title + message
- `AppRoot`: shows overlay when `activeAlerts ≥ 2`
- 5 locale strings in EN/DE/ES/FR/JA (`alert_dock_dismiss/mute/pill_tip/one/many`)
### Changed
- Version bump: 0.89.0/167 → 0.90.0/168

## [0.89.0] — 2026-05-10 (OpenCode multi-select models + agent-settings editor — alpha.28)
### Added
- `AgentSettingsDto`: `claude_auth_key_secret`, `opencode_ollama_url`, `opencode_model`, `opencode_models`; PATCH `/api/profiles/projects/{n}/agent-settings` wired in `TransportClient` + `RestTransport`
- `ProfileEditDialog` Agent Settings section for `kind == project`: Claude auth key secret, OpenCode Ollama URL, default model, opencode_models (comma-separated pool)
- 2 locale strings in all 5 bundles: `profile_ollama_models_label` / `profile_ollama_models_ph`
### Changed
- Version bump: 0.88.0/166 → 0.89.0/167

## [0.88.0] — 2026-05-10 (Observer by-node grouping + settings move — alpha.24/alpha.25)
### Added
- `ObserverPeerDto` gains `compute_node` field (alpha.24)
- `ObserverPeersByNodeDto` + `MetaPeersDto` for `/api/observer/peers/by-node` and `/api/federation/meta-peers`; `TransportClient` + `RestTransport` stubs wired
- `FederatedPeersCard`: "Group by Compute Node" toggle (ON → by-node bucketed view, OFF → flat filter pills); uses `peer.computeNode` for binding badge
- `SettingsScreen`: `SecretsCard` + `ObserverQuicklinkCard` moved General → Compute tab (mirrors PWA alpha.25 settings relocation)
- 3 locale strings in all 5 bundles: `peer_group_by_node` / tip / unbound
### Changed
- Version bump: 0.87.0/165 → 0.88.0/166

## [0.87.0] — 2026-05-10 (backend_family ↔ llm_backend compat — alpha.27)
### Fixed
- `SessionDto` gains `backendFamily` for new wire key while keeping `llmBackend` as compat alias; `Mappers` picks `backendFamily ?: llmBackend` so both pre- and post-alpha.27 daemons show the backend badge correctly
### Changed
- Version bump: 0.86.0/164 → 0.87.0/165

## [0.86.0] — 2026-05-10 (Android Auto voice command scaffold — Sprint 17)
### Added
- `VoiceCommandProcessor`: parses spoken input to STATUS/REPORT/CANCEL/REFRESH verbs
- `VoiceStatusScreen` (Car App Library `MessageTemplate`): displays status summary for TTS readout
- `AutoMonitorScreen`: "Status" `ActionStrip` button navigates to `VoiceStatusScreen`
- `DatawatchMessagingService.onNewIntent`: handles voice intent `EXTRA_RESULTS` from Google Assistant
- 13 voice audio strings in all 5 locales (EN/DE/ES/FR/JA)
### Changed
- Version bump: 0.85.0/163 → 0.86.0/164

## [0.85.0] — 2026-05-10 (Wear notifications + health tile + automata complication — Sprint 16)
### Added
- `WearAlertListenerService`: council consensus + error alert channels with `NotificationCompat` + `WearableExtender`; `needs_input` / `waiting_input` → high-priority notification; `council_consensus` → default-priority per-run notification
- `WearSyncService`: publishes council count to DataLayer for complication
- `AutomataComplicationService`: `SHORT_TEXT` complication showing active automata count
- `MonitorTileService`: health dot (green/yellow/red based on alert presence) + "last sync Xm ago" text
- Wear notification channels `dw_waiting` (HIGH) + `dw_council` (DEFAULT) registered in `Application.onCreate`
- 4 notification channel locale strings in Wear EN/DE/ES/FR/JA
### Changed
- Version bump: 0.84.0/162 → 0.85.0/163

## [0.84.0] — 2026-05-10 (Compute CRUD overhaul + free-observer mapping — Sprint 15)
### Added
- `ComputeNodesCard`: kind dropdown limited to `ollama` / `openai-compat`; amber deprecation banner for nodes using other kinds; per-row enabled `Switch`; `ComputeMigrationBannerCard` at top of Compute tab when deprecated nodes exist
- `LlmRegistryCard`: per-row enabled switch
- `FederatedPeersCard`: per-peer `⇄ <node>` attached badge or "free" pill
- `ComputeNodesCard` add/edit form: Observer Peer dropdown from `GET /api/observer/peers/free`
- `getFreePeers()` in `TransportClient` + `RestTransport`; `ObserverPeerDto.attachedNode` added
- 9 new locale keys in EN/DE/ES/FR/JA
### Changed
- Version bump: 0.83.0/161 → 0.84.0/162

## [0.83.0] — 2026-05-10 (Session LLM picker + badges + filter chips — Sprint 14)
### Added
- `NewSessionScreen`: v7 LLM dropdown (`GET /api/llms`, filter `disabled=false`) with kind label; cascading Compute Node picker from selected LLM's `compute_nodes`; hides legacy backend picker when v7 LLM selected
- `SessionDetailScreen`: `⚡ <llmRef>` green badge + `⚙ <computeNodeRef>` purple badge via `SessionInfoBar`
- `SessionsScreen`: 4-chip state filter row (All / Active / Waiting / Done with counts) persisted to `SharedPreferences` key `cs_session_state_filter`; text filter now matches `llmRef` + `computeNodeRef`
- `SessionDto` gains `llm_ref` + `compute_node_ref`; `Session` domain model + `Mappers` updated
- `StartSessionDto` gains `llm` + `compute_node_override` for v7 session start path
- `LlmRegistryEntryDto` gains `compute_nodes` list for picker cascade
- `listLlms()` in `TransportClient` + `RestTransport` (`GET /api/llms`)
- 14 locale keys in EN/DE/ES/FR/JA
### Changed
- Version bump: 0.82.0/160 → 0.83.0/161

## [0.82.0] — 2026-05-10 (General tab: Session Templates + Device Aliases + Tooling + Secrets + Observer quicklink — Sprint 13)
### Added
- `SessionTemplatesCard`: list/create/delete session templates (`GET/POST/DELETE /api/sessions/templates`)
- `DeviceAliasesCard`: list/alias/delete devices (`GET /api/devices`, `PATCH /api/devices/{name}`, `DELETE /api/devices/{name}`)
- `ToolingCard`: list artifacts + Install/Update/Remove per backend (`GET /api/tooling`, `POST /api/tooling/{backend}/install|update|remove`)
- `SecretsCard`: full CRUD for secrets store (`GET/POST /api/secrets`, `DELETE /api/secrets/{name}`)
- `ObserverQuicklinkCard`: single-row card with "Open Observer →" button navigating to Monitor tab
- New DTOs: `SessionTemplateDto/sDto`, `DeviceDto/sDto`, `ToolingArtifactDto/Dto`, `SecretDto/sDto`, `CreateSecretDto`
- 13 `TransportClient` methods + `RestTransport` implementations
- 19 locale keys in EN/DE/ES/FR/JA
### Changed
- Version bump: 0.81.0/159 → 0.82.0/160

## [0.81.0] — 2026-05-10 (Automata tab wiring + Pipeline Manager + Orchestrator Graphs — Sprint 12)
### Added
- Automata tab reordered to flat PWA v7.0.0-alpha.23c order: `IdentityCard` → `AlgorithmModeCard` → `EvalsCard` → `CouncilCard` → `KindProfilesCard(project)` → `PipelineManagerCard` → `OrchestratorGraphsCard` → `ScanConfigCard` → `ConfigFieldsPanel(Autonomous)` → `SkillRegistriesCard` → `AutomataTypesCard` → `ConfigFieldsPanel(Pipelines)` → `ConfigFieldsPanel(Orchestrator)`
- `PipelineManagerCard`: live pipeline list + cancel (`GET /api/pipelines`)
- `OrchestratorGraphsCard`: create/run/delete graphs (`GET/POST /api/orchestrator/graphs`, `POST ./{id}/run`, `DELETE ./{id}`)
- New DTOs: `PipelineTaskDto`, `PipelineListItemDto`, `OrchestratorGraphListItemDto`, `OrchestratorGraphsListDto`, `CreateOrchestratorGraphRequestDto`
- 5 `TransportClient` methods + `RestTransport` implementations
- Filed server issues #40–#43 (identity/algorithm/evals/council endpoints)
- 13 locale keys in EN/DE/ES/FR/JA
### Changed
- `ConfigFieldsPanel(Agents)` removed from Automata tab (moved to Compute in v0.80.0)
- Version bump: 0.80.0/158 → 0.81.0/159

## [0.80.0] — 2026-05-10 (Compute reorder + Cost Rates + Tailscale + Routing Rules — Sprint 11)
### Added
- `CostRatesCard`: GET/POST `/api/cost/rates`; table of backend → in/1K + out/1K with Save + Reset
- `TailscaleSettingsCard`: reads `cfg.tailscale`; enabled toggle, coordinator URL, image, auth key, API key (masked)
- `TailscaleMeshCard`: GET `/api/tailscale/status`; enabled/disabled badge, coordinator URL, node list with online/offline indicator
- `RoutingRulesCard`: GET/POST `/api/routing-rules` + POST `/api/routing-rules/test`; add/delete rules + inline test
- New DTOs: `CostRatesDto`, `CostRateDto`, `RoutingRulesDto`, `RoutingRuleDto`, `RoutingTestRequestDto`, `RoutingTestResultDto`, `TailscaleStatusDto`, `TailscaleNodeDto`
- 6 new `TransportClient` methods + `RestTransport` implementations
- Compute tab reordered to PWA v7.0.0-alpha.23c order: Memory → RTK → CostRates → ClusterProfiles → ComputeNodes → LLMs → ContainerWorkers → Detection → SavedCmds → Filters → TailscaleSettings → TailscaleMesh
- `RoutingRulesCard` added to Comms tab after Proxy section
- `ConfigFieldsPanel(Agents)` wired as Container Workers in Compute tab
- 23 locale keys in EN/DE/ES/FR/JA
### Changed
- Version bump: 0.79.0/157 → 0.80.0/158

## [0.79.0] — 2026-05-10 (Sprint 10 arc complete — battery + background optimization)
### Changed
- Version bump: 0.78.0/156 → 0.79.0/157; Sprint 10 upgrade arc finalized
- `WatchedAutomataStore`: same pattern for automata / PRD watch state
- `ServiceLocator`: exposes `watchedSessionsStore` and `watchedAutomataStore`
- `SessionsViewModel`: `watchedIds: StateFlow<Set<String>>` + `toggleWatch(sessionId)` — persists watch state to `WatchedSessionsStore`
- `AutonomousViewModel`: `watchedAutomataIds: StateFlow<Set<String>>` (lazy) + `toggleWatchAutomata(prdId)`
- `AlertsViewModel`: `watchedAlertCount` in `UiState`; when any sessions are watched, badge count shows only watched-session active alerts; empty set falls back to all (backward-compat)
- `AlertsViewModel`: `_watchedIds` flow merges into outer `combine` as 5th stream
- `SessionRow`: "Watching" / "Not watching" menu item with success-color highlight when watched
- Sessions list: `watchedIds by vm.watchedIds.collectAsState()` threaded to each `SessionRow`
- `AppRoot`: `BottomNavBar` now receives `alertsBadge = alertsState.watchedAlertCount`
- `WatchedSessionsStoreTest`: 5 unit tests (empty default, setWatched true/false, profile isolation, flow initial, flow emits on change)
- Locale: 7 new keys in composeApp EN/DE/ES/FR/JA (`session_watch_toggle/on/off/tip`, `automata_watch_toggle/on/off`)
- AGENT.md: Per-Sprint Rules Audit checklist + Reuse-and-Expand Principle (synced from parent `dmz006/datawatch` AGENT.md)
### Changed
- Version bump: 0.92.0/170 → 0.93.0/171
- `composeApp/build.gradle.kts`: added `libs.turbine` to `androidUnitTest` dependencies for Flow testing

## [0.78.0] — 2026-05-10
### Added
- VpnMonitor: ConnectivityManager VPN NetworkCallback detects Tailscale drops; non-always-on path notifies immediately (S10-1)
- WearHeartbeatWorker: 15-minute WorkManager periodic job replaces 15 s polling loop (S10-2)
- WearSyncService: demand-only sync via MessageClient `/datawatch/sync`; `fetchAndPublishDashboard()` callable from heartbeat worker (S10-2)
- WearSyncManager (wear module): watch-side sync requester sends `/datawatch/sync` to phone on app open, refresh tap, and tile render (S10-5/S10-6)
- AlertTierDetector: resolves Tier1=UnifiedPush / Tier2=CommChannel(Signal) / Tier3=Background; About card displays active tier with icon + colour coding (S10-4)
- Locale: 3 `alert_tier_*` keys in EN/DE/ES/FR/JA (S10-4)
### Changed
- NtfyFallbackService: `ACTION_DEVICE_IDLE_MODE_CHANGED` receiver; SSE stream pauses on Doze entry, resumes on exit (S10-3)
- MonitorTileService / SessionsTileService: trigger background dashboard sync on each tile render (S10-6)
- WearMainActivity: triggers `WearSyncManager.requestDashboard()` on every `onResume()` (S10-5)
### Removed
- `android.permission.WAKE_LOCK` from AndroidManifest — nothing in the codebase acquires a WakeLock (S10-7)
- 15 s polling loops from WearSyncService (5,760 BLE activations/day on Samsung Galaxy Watch) (S10-2)
### Infrastructure
- Filed dmz006/datawatch#39: first-party UnifiedPush provider + ntfy-compatible SSE endpoints (S10-8)

## [0.77.0] — 2026-05-10
### Added
- CouncilPersonaWizardSheet: 6-page HorizontalPager interview wizard (5 steps: focus/stance/tone/pushback/examples + final tune page), AI-refine row, backend picker (ollama/openwebui), edit-mode pre-fill (#92)
- CouncilCard: Manage Personas button opens persona list sheet with per-row Edit icon; Add button opens wizard in create mode; wizard wired to POST/PUT transport (#92)
- Transport: `councilListPersonas`, `councilListRuns`, `councilGetConfig`, `councilUpdateConfig`, `councilStartRun`, `councilStopRun`, `createCouncilPersona`, `updateCouncilPersona` + all backing Council DTOs (#92)
- Locale: 11 `council_wizard_*` keys in EN/DE/ES/FR/JA (#92)
- MicAttachableTextField: shared composable wrapper for Whisper-attachable text input (S7-4 stub, Sprint 8 integration)

## [0.76.0] — 2026-05-10
### Added
- AutonomousScreen: long-press multi-select bar with Run/Approve/Cancel/Archive/Delete chips above bottom nav; FAB hidden while selection active (#73)
- ThemePreference.kt: `ThemeMode` enum + `ThemePrefs` SharedPreferences persistence for Dark/Light/System; `LightColorScheme` added to Theme.kt (#77)
- ThemePickerCard: full RadioButton implementation using `ThemeMode.entries`, reads/writes via `ThemePrefs` (#77)
- MicAttachableTextField: composable wrapper for voice-capable text fields; mic icon shown when `whisperConfigured=true` and `minLines >= 2` (#91)
- PrdDetailDialog: terminal-state hint Surface shown for done/aborted/failed/archived status (#91)
- ScanConfigCard: Run Scan + Run Rules OutlinedButtons at card bottom (#91)
- NewPrdDialog: reduced spacing between profile-select and dir chip (1.dp); Spacer(2.dp) between guided mode toggle and skills; skills start padding aligned (#76)
- Migrated `OutlinedTextField` with `minLines >= 2` to `MicAttachableTextField` in CouncilCard, IdentityCard (#91)
- Locale: 7 new/updated keys in EN/DE/ES/FR/JA (`settings_theme_title`, `settings_theme_dark`, `settings_theme_light`, `settings_theme_system`, `prd_terminal_state_hint`, `action_run_scan`, `action_run_rules`)
### Changed
- Version bump: 0.75.0/153 → 0.76.0/154

## [0.75.0] — 2026-05-09
### Added
- DocsSearchCard: full-text doc search with index_kind badge (vector=teal/bm25=grey), pending trust queue with bulk accept/dismiss, trusted sources list with remove (#84, #85)
- Vault/Secrets status card: reachability dot, address, mount, last_success, last_error — shown when active_backend=vault (#82)
- ObserverCard stub added to Monitor tab (#88)
- Mic recording toast notification on voice start (#88)
### Changed
- Federated peer rows: stale indicator dot (green <1h, amber 1–6h, red ≥6h) alongside existing health dot (#74)
- Settings nav icon: red badge dot when any federated peer is >6h stale (#74)
- About card: single "System documentation & diagrams" link replaces multi-row links (#71)
- About card: inner padding normalized to 12dp horizontal / 8dp vertical (#87)
- Settings section headers: BL reference parentheticals removed from all visible strings (#86)
- DocsSearchCard added to General tab; bulk trust locale complete (#89)
- Transport audit: all BL274 docs methods verified (interface + RestTransport + DTO, 6 interface + 6 impl + 5 DTOs) (#90)

## [0.74.0] — 2026-05-09
### Added
- ComputeNodesCard: full CRUD for compute nodes (10 kinds: ollama/opencode/aider/goose/gemini/shell/remote/etc.), hardware spec collapsible display, kind-aware model discovery (#98)
- LlmRegistryCard: full CRUD for LLM registry (10 kinds including openwebui), enabled/disabled toggle, multi-select compute node, kind-aware model dropdown (#98, #100, #101)
- Migration banner: amber dismissible notice when legacy LLM configs were auto-migrated on v7 daemon first start (#99)
- LLM on/off toggle: Switch per LLM row, PATCH /api/llms/{name}/enabled {enabled, pretest} (#100, #102)
- Council virtual sessions: 🎭 badge in session list, transcript-only view hiding terminal/channel tabs, Council filter chip in sessions toolbar (#93)
- whisper.backend: active backend displayed at top of Voice/Whisper test section (#102)
### Changed
- OpenWebUI reclassified: kind=openwebui is an LLM entry (not a ComputeNode); removed from ComputeNode kind list (#100)
- LlmConfigCard retired from Compute tab; replaced by LlmRegistryCard (#93)
- Council filter also matches sessions by fullId prefix "council-" in addition to backend=="council-virtual" (#93)

## [0.72.0] — 2026-05-09
### Changed
- Toast UX: composable DatawatchToastHost with dedup (×N badge), strip-prefix key comparison (`[name] ` stripped before dedup), 75% width right-justified, 13sp, 8×12dp padding, Reconnect button on disconnect toasts (#93 alpha.9, #101, #102)
- Live polling (8s auto-refresh) on FederatedPeersCard with pulsing green dot; manual refresh removed (#95)
- Session reconnect: clear pane-capture dedup on reconnect, REST GET refresh, resize_term as first WS frame, re-enable input, ON_RESUME reconnect trigger when WS disconnected (#62, #64, #65, #66, #67)
- Scroll-mode page buttons use tmux-page-up/tmux-page-down daemon commands with graceful fallback (#63)
- Channel-state classifier: verified server-provided state field used exclusively — no client-side regex match (#70)

## [0.71.0] — 2026-05-09
### Changed
- Settings tab order: Monitor · General · Plugins · Comms · Compute · Automata · About (#52, #93, #96)
- LLM tab renamed to Compute; Agents tab (intermediate v6.7.6 state) skipped in favor of final v7 structure
- Compute tab: ComputeNodes and LLMs registry stubs added (full CRUD in v0.74.0)
- Compute tab card order: ComputeNodes → LLMs → Memory → Detection Filters → RTK → Saved Commands → Output Filters → Cluster Profiles (#96)
- Automata tab restructured into 3 groups: Settings / Templates / Lifecycle (#96)
- Project Profiles moved from General → Automata (Templates group)
- Cluster Profiles moved from General → Compute tab
- Stale tab preference keys ("llm", "agents") auto-migrate to "compute" on first launch

## [0.70.0] — 2026-05-09
### Changed
- Bottom nav items spread evenly across full width (#51)
- Automata tab: Pipeline/Orchestrator/Skills card inner padding normalized (#57)
- "PRD"/"PRDs" renamed to "Automaton"/"Automata" across all visible UI strings (#59)
- Identity Wizard 🤖 icon: visible only on Automata screen, placed left of search (#60, #61)
- Done-state session cards: action buttons (last-response, restart, delete) stay full opacity (#72)
- Theme picker card added to Settings → About, below Language picker (#80)

## [0.69.0] — 2026-05-05 (Memory Add + Timeline + Research — BL12)

### Added

- **Add memory button in MemoryCard (BL12).** [+] icon in the section header opens
  `AddMemoryDialog` with a text field + optional comma-separated tags. Saves via
  `POST /api/memory/remember`. List refreshes on success.

- **Memory Timeline tab.** Chronological view of memories from `GET /api/memory/list`,
  grouped by date with a left-side time marker showing role and content.

- **Memory Research tab.** Dedicated search field with results rendered as expandable
  cards showing content, similarity score, role chip, and pin/delete actions. Uses
  `GET /api/memory/search` — same endpoint as List search but displayed with
  similarity scores visible.

- **`memoryRemember(text, role, tags)` transport method** added to `TransportClient`
  and `RestTransport` → `POST /api/memory/remember`.

## [0.67.0] — 2026-05-05 (Adjustable terminal dimensions — BL13)

### Added

- **`TerminalDimensionsCard` in Settings → General (BL13).** Two steppers (Columns 0–250 step
  10; Rows 0–80 step 5) persisted to SharedPreferences. Value 0 = use backend default
  (80×24, or 120×40 for claude-code backends). Applied per-session in `SessionDetailScreen`
  via `terminalController.setMinSize()`.

- **`TerminalPrefs` constants** (`KEY_COLS`, `KEY_ROWS`, `DEFAULT_COLS`, `DEFAULT_ROWS`) in
  `prefs/TerminalPrefs.kt`.

## [0.66.0] — 2026-05-05 (Skill Registries — BL255 — issue #50)

### Added

- **`SkillRegistriesCard` in Settings → Automata (#50).** Per-registry rows with
  status dot (green=connected, grey=disconnected), name, URL, branch, built-in badge,
  and [Connect] [Browse] [Edit] [Delete] per-row actions. Empty state shows
  [+ Add default (PAI)] and [+ Add] buttons. Synced-skills summary renders a cross-registry
  flat list with name, description, tags, and source-registry badge.

- **`BrowseSkillsDialog`** — loads `GET /api/skills/registries/{name}/available`, renders a
  scrollable checkbox list with [Select all] / [None] header buttons and a [Sync selected]
  confirm action that calls `POST /api/skills/registries/{name}/sync`.

- **`AddEditRegistryDialog`** — name / URL / branch fields; name is read-only on edit.
  Create calls `POST /api/skills/registries`; edit calls `PUT /api/skills/registries/{name}`.

- **10 transport methods** (`listSkillRegistries`, `createSkillRegistry`, `updateSkillRegistry`,
  `deleteSkillRegistry`, `addDefaultSkillRegistry`, `connectSkillRegistry`, `listAvailableSkills`,
  `syncSkills`, `unsyncSkills`, `listSyncedSkills`) added to `TransportClient` and implemented
  in `RestTransport`.

- **6 DTOs** (`SkillRegistryDto`, `SkillRegistryRequestDto`, `SkillRegistryUpdateDto`,
  `SkillDto`, `AvailableSkillDto`, `SyncSkillsRequestDto`) added to `Dtos.kt`.

- **45 `skills_*` locale keys** added across EN/DE/ES/FR/JA string bundles. Closes [#50](https://github.com/dmz006/datawatch-app/issues/50).

## [0.65.0] — 2026-05-04 (i18n full sync BL252 — issue #46)

### Added

- **375 missing string keys added across all 5 locale bundles (EN/DE/ES/FR/JA).**
  Sources: PWA BL252 phases 1–7 (`dmz006/datawatch@v6.6.0`
  `internal/server/web/locales/`) plus stub keys from v0.58–v0.64.
  Topics: session filter, schedule inputs, new-session form, PRD modal lifecycle,
  stats section headings, alerts empty state, Settings identity cards, nav labels,
  session action buttons, terminal strings, voice status, LLM/memory/KG strings,
  Template Store, scan, type registry, Guided Mode, Skills, Signal linking.

- **22 signal/state/voice/toast keys added to `wear/` locale bundles.**
  Covers `signal_*`, `state_*`, `status_*`, `toast_*`, `voice_*` keys visible
  on Wear surfaces.

- **String resources wired in `SignalLinkingDialog` and `ChannelsCard`.** All
  user-visible text now goes through `stringResource()` so non-EN locales
  render correctly.

## [0.64.0] — 2026-05-04 (Signal device-linking — BL21 — datawatch#31)

### Added

- **`SignalLinkingDialog` in Settings → Comms.** Opens from a "Link Signal"
  button that appears in `ChannelsCard` when a Signal channel is configured.
  Streams QR frames from `GET /api/link/qr` (SSE) and renders each frame as
  a bitmap decoded from the server's base64 PNG. On successful pairing the
  dialog shows "Linked successfully." and the card reflects the linked state.

- **Signal linked state persisted per server profile.** Migration 6 adds
  `signal_linked INTEGER DEFAULT 0` to `server_profile`. `setSignalLinked()`
  in `ServerProfileRepository` writes the flag after pairing completes;
  `ServerProfile.signalLinked` exposes it to the UI.

- **Transport: Signal linking endpoints (datawatch#31).** `TransportClient`
  declares `startSignalLinking(): Flow<LinkQrFrameDto>`,
  `getSignalLinkStatus()`, `cancelSignalLink()`, `unlinkSignalDevice(deviceId)`.
  `RestTransport` implements all four using the SSE + REST patterns.

- **DTOs:** `LinkQrFrameDto`, `SignalLinkStatusDto`, `SignalLinkStartDto`.

## [0.63.0] — 2026-05-04 (Type registry + Guided Mode + Skills — BL221 Phase 4 — issue #43)

### Added

- **Type badge on `PrdRow` (#43).** Color-coded chip (software→blue,
  research→purple, operational→orange, personal→teal, custom→surfaceVariant)
  next to status pill. Renders only when `prd.type` is non-null.

- **Type / Guided Mode / Skills in `PrdDetailDialog`.** Type picker dropdown
  (editable when server returns the types list); Guided Mode toggle switch;
  Skills FlowRow with edit button opening a comma-separated input dialog.

- **Type / Guided Mode / Skills in `NewPrdDialog`.** Type picker (4 built-in
  types + blank=none), Guided Mode toggle switch, Skills comma-separated
  text field. All three sent on `POST /api/autonomous/prds`.

- **`AutomataTypesCard` in Settings → Automata.** Inline type registry card:
  lists `/api/autonomous/types`, "+" icon to create (id + label + color),
  delete per row. Loads on first render via `ServiceLocator` pattern.

- **`AutonomousViewModel`** gains `setPrdType`, `setPrdGuidedMode`,
  `setPrdSkills` (via `prdAction`), `loadAutomataTypes`,
  `createAutomataType`, `deleteAutomataType`; `automataTypes` in `UiState`.

- **3 transport methods** (`listAutomataTypes`, `registerAutomataType`,
  `deleteAutomataType`) + default methods `setPrdType`, `setPrdGuidedMode`,
  `setPrdSkills` in `TransportClient`.

- **2 DTOs** (`AutomataTypeDto`, `AutomataTypeRequestDto`) in `Dtos.kt`.

- **`PrdDto`** gains `type`, `guidedMode`, `skills`; **`NewPrdRequestDto`**
  gains same three fields.

## [0.62.0] — 2026-05-04 (Security scan — BL221 Phase 3 — issue #45)

### Added

- **`ScanResultCard` in `PrdDetailDialog` (#45).** Verdict badge (PASS/WARN/FAIL
  with semantic colors) + finding count + expandable `FindingRow` list (severity,
  file:line, message). "Run scan", "Fix PRD", and "Propose rules" text buttons
  appear inline. Proposed rules displayed in a monospace AlertDialog.

- **`ScanConfigCard` in Settings → Automata tab.** Toggles for enabled, SAST,
  secrets, deps, grader, fix-loop; `failOnSeverity` dropdown picker (info /
  warning / error); max-retries stepper (+/−). Loads current config via
  `GET /api/autonomous/scan_config`; saves incrementally via `PUT`.

- **Scan state in `AutonomousViewModel`.** `loadScanResult(prdId)`,
  `triggerScan(prdId)`, `createFixPrd(prdId, onSuccess)`, `proposeRules(prdId)`,
  `clearProposedRules()`, `clearScan()`. State: `scanResult`, `scanLoading`,
  `proposedRules` in `UiState`.

- **6 transport methods** (`triggerScan`, `getScanResult`, `createFixPrd`,
  `proposeRules`, `getScanConfig`, `updateScanConfig`) in `TransportClient` and
  `RestTransport`.

- **4 DTOs** (`ScanFindingDto`, `ScanResultDto`, `ScanConfigDto`,
  `RuleProposalDto`) in `Dtos.kt`.

- **Scan auto-loads** when `PrdDetailDialog` opens via `LaunchedEffect(prdId)`.
  State cleared on dismiss.

## [0.61.0] — 2026-05-04 (Template Store UI — BL221 Phase 2 — issue #44)

### Added

- **Template Store tab in Autonomous screen (#44).** Autonomous screen gains a
  PRDs / Templates `TabRow`. The Templates tab shows a `LazyColumn` of template
  cards (title, type badge, up to 3 tag labels, description preview) with
  Use / Edit / Delete actions per row.

- **`TemplatesTab` composable** — list view with empty state, banner, and
  inline `AlertDialog` for delete confirmation.

- **`CreateEditTemplateSheet`** — `AlertDialog` for creating or editing a
  template with title, spec (multiline), type, tags (comma-separated), and
  description fields. Reused for both create and edit flows.

- **`InstantiateTemplateDialog`** — detects `{{var}}` placeholders in the
  template spec, renders one `OutlinedTextField` per unique variable, and
  includes a project-dir field. Builds `InstantiateTemplateRequestDto` on
  confirm.

- **`TemplatesViewModel`** — MVVM VM with `refresh`, `createTemplate`,
  `updateTemplate`, `deleteTemplate`, `instantiateTemplate`, and
  `clonePrdToTemplate` operations, all following the `ProfileResolver.Default`
  pattern.

- **7 transport methods** (`listTemplates`, `createTemplate`, `getTemplate`,
  `updateTemplate`, `deleteTemplate`, `instantiateTemplate`,
  `clonePrdToTemplate`) added to `TransportClient` and implemented in
  `RestTransport`.

- **6 DTOs** (`TemplateDto`, `TemplateListDto`, `CreateTemplateRequestDto`,
  `UpdateTemplateRequestDto`, `InstantiateTemplateRequestDto`,
  `ClonePrdToTemplateRequestDto`) added to `Dtos.kt`.

## [0.60.0] — 2026-05-04 (language picker + whisper sync — issue #40)

### Added

- **`LanguagePickerCard` in Settings → About (#40).** Select dropdown with Auto +
  11 locales (en/de/es/fr/it/ja/ko/pt/ru/zh). Selecting a concrete locale PUTs
  `{"whisper.language": code}` to the server via new `TransportClient.setWhisperLanguage()`.
  Selecting "Auto" leaves server config unchanged. Current server value pre-loaded
  via `GET /api/config`.

- **`TransportClient.setWhisperLanguage(code: String)`** — convenience default method
  that builds the flat-patch JSON and calls `writeConfig()`.

### Changed

- **`whisper.language` removed from Whisper ConfigSection.** The text-field entry in
  Settings → General → Whisper config panel is removed; use the picker in About instead.
  `whisper.enabled`, `whisper.model`, and `whisper.venv_path` remain.

## [0.59.0] — 2026-05-04 (Settings Automata+Plugins tabs · workspace label · detekt/ktlint fixes)

### Changed

- **Settings tab structure aligned to PWA v6.5.1 (#48).** Two new tabs added:
  - **Automata** — Pipelines, Autonomous, Orchestrator, Agents config panels (moved from General).
  - **Plugins** — Plugin Framework config panel (moved from General).
  - General tab retains: Datawatch, Auto-Update, Session, Whisper, Project/Cluster profiles, Notifications.
  - Tab order: Monitor → General → Comms → LLM → Automata → Plugins → About.

- **New PRD workspace label (#47).** "Project directory" field renamed to
  "Workspace (profile or folder)" to match PWA wording.

### Fixed

- **Pre-existing detekt violations cleared (auto + wear modules).** `auto`: extract
  `PCT_MULTIPLIER` constant; `wear`: split long `MonitorPage`, `SessionPopupCentre`
  into helper composables; delete unused private `ServersPage`.

- **Gitleaks false positive suppressed.** `.gitleaks.toml` added to exclude vendored
  `xterm.min.js` from the `generic-api-key` scan.

- **Missing Wear watchface dependency committed.** `WaitingComplicationService.kt`
  (added in v0.56.0) referenced `watchface-complications-data-source` which was never
  committed to `wear/build.gradle.kts` or `gradle/libs.versions.toml`.

- **Three unused imports removed** (`McpChannelCard`, `PrdDetailDialog`,
  `SessionLoadingOverlay`) flagged by ktlint.

## [0.58.0] — 2026-05-04 (quick-commands from API · PRD card colors · reconnect refresh)

### Added

- **Quick-commands populated from server API (#31).** `GET /api/config` is now
  fetched when the quick-command sheet opens; `quick_commands` array items are
  rendered as `FilterChip`s. Falls back to 15 built-in tmux/shell commands
  (including proper ESC and Ctrl-b sequences) when the server returns nothing or
  is unreachable. `TransportClient.fetchSystemQuickCommands()` and
  `RestTransport` implementation added; `SessionsViewModel.fetchSystemQuickCommands()`
  wires it to the UI.

- **PRD card left-border color by status (#41).** `prdStatusColor()` maps all
  server-sent status strings to the PWA palette: running→green, approved→teal,
  needs_review/revisions_asked/awaiting_approval→amber, blocked/rejected→red,
  decomposing→purple, draft/complete/cancelled→grey.

### Fixed

- **Session detail auto-refreshes on WebSocket reconnect (BL249).** After a WS
  disconnect+reconnect the view model calls `refreshFromServer()` once to pick
  up any state changes that arrived during the gap.

- **Dismissing the reconnect banner triggers a REST refresh (BL250).** Tapping
  the banner now immediately re-fetches session state instead of leaving stale
  output until the next WS event.

- **`ChatMessageEventTest` short-id expectation corrected.** The stored event
  `sessionId` is normalised to `forSessionId` (matching the pattern used by
  `buildPaneCaptureEvents` / `buildOutputEvents`) so the DB exact-match query
  finds it. Test expectation updated from full wire id to short subscriber id.

## [0.42.12] — 2026-04-29 (toolbar fits Scroll button + header Response gone + About PWA-aligned)

### Fixed

- **Terminal toolbar Scroll button now visible on phone widths.**
  v0.42.11's `softWrap = false` fixed the wrap that was producing
  the empty band, but the rightmost button still overflowed past
  the screen edge because the toolbar was 306 dp wide on a 360 dp
  screen. Compact labels — "↕" / "⏹" instead of "↕ Scroll" /
  "⏹ Exit" — plus dropping the "{N}px" font-size readout and the
  decorative `|` separators trim the toolbar to ~140 dp; every
  button now fits at any phone width.

### Removed

- **Response button removed from `SessionInfoBar` (chip bar).**
  User direction 2026-04-29: redundant with the 📄 quick-action
  on the row above the composer. `hasResponse` always false on
  the chip bar now; the Response surface lives only in the
  bottom quick-actions row.

### Changed

- **Phone About card aligned to PWA's About surface
  (app.js:4233-4277).** Drops `Package` and `License` rows
  (PWA carries neither). Renames `Parent project` → `Project`
  and `Source` → `Mobile app` to match PWA's labels exactly.
  Adds the PWA's "Play Store link will land here once the app
  is published." caption directly under the Mobile app row.

## [0.42.11] — 2026-04-29 (close B35 + watch About + universal circle clip)

### Changed

- **Watch About page rewritten to mirror the phone's About card.**
  User direction 2026-04-29: drop the "About" page header (the
  body opens with the datawatch logotype) and bring the same
  details the phone surfaces — version + build code, active server
  + session counts, uptime, license (Polyform Noncommercial 1.0.0),
  source link. PWA About content kept as the parity reference.
- **All Wear pages now circle-clip their content.** User direction
  2026-04-29: `Modifier.clip(CircleShape)` on `PageScaffold` so
  long lists / multi-line text (sessions, PRDs, About body) get
  visually clipped to the round bezel — matches the popup clip
  added in v0.42.10. `PageScaffold` accepts a blank title to
  suppress the page header, used by the new About page.

### Fixed

- **Empty black band above the tmux/channel tabs row.** Root cause
  found via `uiautomator dump` 2026-04-29: the rightmost toolbar
  button ("↕ Scroll" / "⏹ Exit Scroll") had only ~10 dp of usable
  horizontal space after the font controls, so its label wrapped
  to ~5 stacked lines and the button rendered 154 dp tall. The
  Row's `verticalAlignment = CenterVertically` then stretched all
  siblings to that height, leaving the visible empty band above
  the short pills. Setting `maxLines = 1, softWrap = false` on
  `TermToolBtn`'s Text keeps every button on one line; the row
  collapses to its natural ~40 dp height. Closes backlog **B35**.

## [0.42.10] — 2026-04-29 (watch popup content clipped to circle + composer cleanup)

### Removed

- **Quick-reply chip row above the composer.** User direction
  2026-04-29: the redundant approve / reject / continue / skip /
  quit chips ate ~40 dp of vertical space the terminal viewport
  could use. The Saved Commands sheet (⌨ button below) already
  exposes those same five plus saved + custom commands.

### Fixed

- **Watch popup body no longer bleeds past the green bezel ring.**
  Long `last_response` bodies (now up to ~95 KB on demand from
  v0.42.9) rendered to the rectangular `Box` bounds, so scrolled
  text could leak into the corners of the round-bezel safe area.
  Added `Modifier.clip(CircleShape)` to the popup's outer Box so
  the `verticalScroll` column gets visually clipped to the
  circular surface — text disappears cleanly at the green ring
  instead of clipping at a square edge.

## [0.42.9] — 2026-04-28 (full-buffer last_response on watch popup, on-demand)

### Added

- **`ConfigSaveBus` event flow.** Every successful
  `transport.writeConfig` from `ConfigFieldsPanel` fires the bus.
  `AppRoot` subscribes and re-probes `autonomous.enabled` so the
  PRDs nav tab appears / disappears the instant the user toggles
  autonomous in Settings — no app restart, no polling. User
  direction 2026-04-28: *"there shouldn't be a timed refresh,
  there should be a queue and a message saying there is an
  event."*

### Changed

- **Watch popup body is now fetched on-demand for the open session
  only** — no longer rides the DataLayer broadcast. User direction
  2026-04-28: *"only 1 session will be displayed at a time. the
  list is there but the last message is only loaded when
  viewing."* New flow: tap a session → watch sends
  `/datawatch/refreshSession` → phone refetches `/api/sessions` →
  phone replies on `/datawatch/sessionDetail` with the full
  `last_response` body (capped at 95 KB to fit the
  100 KB MessageClient envelope). Watch popup renders the full
  buffer (vertically scrollable, no maxLines cap), with a
  "Loading…" placeholder until the reply arrives.
- **`lastResponses` array dropped from `/datawatch/sessions`
  broadcast.** Was 4000 chars × 12 sessions = 48 KB of bandwidth
  for content the user only ever read for one row at a time.
  Broadcast is now metadata-only (id + title + state + backend +
  one-line preview); the per-tap MessageClient reply carries the
  bulk. `SESSION_LAST_RESPONSE_MAX` retired; new
  `SESSION_DETAIL_BODY_MAX = 95_000` lives next to the new path.

## [0.42.8] — 2026-04-28 (WearSyncService periodically refetches /api/sessions + PRDs probe correctness)

### Fixed

- **Phone's PRDs nav tab now correctly hides on autonomous-disabled
  servers.** v0.42.5 probed `/api/autonomous/prds` and treated
  success as "supported", but that endpoint returns `200 OK
  {"prds":[]}` even when autonomous is off — so the tab was always
  visible. Now matches PWA v5.26.8: probe
  `transport.fetchConfig()` and check
  `autonomous.enabled == true`. Federated "All servers" mode
  still keeps the tab visible.
- **Watch's session list no longer goes stale.** Until v0.42.7,
  `WearSyncService` published whatever the local SQLDelight cache
  held — and that cache was only refreshed when the phone user
  opened the Sessions tab. Result: brand-new sessions never showed
  up on the wrist, and existing sessions' `last_response` froze at
  whatever value the phone last fetched. The 15-second poll loop
  now also calls `transport.listSessions()` and replaces the
  active profile's repository rows; the existing reactive
  `/datawatch/sessions` publisher fans the fresh snapshot through
  to the watch automatically. Closes the user-reported "selecting
  a session still doesn't show the last response" + "sessions
  list isn't updating" regressions.

## [0.42.7] — 2026-04-28 (closes 72h-audit gaps #2, #3, #5)

### Added

- **Test Whisper interactive card** — record/transcribe flow under
  the Whisper config panel on Settings → General. Tap 🎤 to start,
  ■ to stop; audio ships to `/api/voice/transcribe` against the
  active server, transcript renders inline with round-trip ms.
  Replaces the silent-WAV health check (which only proved the
  endpoint responded). PWA v5.26.56 parity, gap #3 (issue #24).
- **Memory schema_version probe** — new "Check" button on the
  Mempalace card. Hits `/api/memory/stats` and surfaces the
  reported `schema_version`, falling back to "(not reported by
  this backend)" when the active backend doesn't expose it. PWA
  v5.27.0 parity, gap #5 (issue #26).

### Fixed

- **Bulk WS `sessions` frames now nudge the active session detail
  view to refresh.** Previously the bulk frame was unconditionally
  dropped (`emptyList()` in `EventMapper`), so daemon state
  transitions delivered ONLY via the periodic broadcast (e.g.,
  waiting_input → running) left the input-required banner stale
  until the user exited and re-entered the session. The mapper
  now scans the bulk payload for the active session's id and
  emits a synthetic `SessionEvent.StateChange` when the state
  flips — `SessionDetailViewModel.startStream` already reacts to
  StateChange by calling `refreshFromServer`. De-duped per
  session so unchanged states don't spam REST. PWA v5.26.49
  parity, gap #2 (issue #25).

## [0.42.6] — 2026-04-28 (Container Workers ⬡ pill + cfg.Agents settings + Settings reorder + auto-restart banner cleanup)

### Changed

- **Settings → General tab reordered to mirror PWA's
  `GENERAL_CONFIG_FIELDS` array verbatim**: Datawatch → Auto-Update
  → Session → Pipelines → Autonomous → **Orchestrator (PRD-DAG)**
  → **Plugins** → Whisper, then explicit cards Project Profiles →
  Cluster Profiles → **Container Workers** → Notifications. User
  direction 2026-04-28: PRD-DAG orchestrator must precede the
  plugin framework. The duplicate General-tab `gc_rtk` card was
  retired — RTK lives in the LLM tab (`lc_rtk`) per PWA.
- **`RestartNeededBanner` no longer shows the green
  "auto-restarts on save" affirmation.** User direction
  2026-04-28: green-state was visual noise on every healthy
  server. Banner now renders only when there's something
  actionable — auto-restart OFF (amber + restart button) or a
  transient status ("Restarting daemon…").

### Added

- **`agent_id` deserialized + persisted.** New `agentId` field on
  `SessionDto`, `Session`, and the `session` SQLDelight table.
  Migration `5.sqm` runs `ALTER TABLE session ADD COLUMN agent_id
  TEXT` so existing local DBs upgrade in place — no data loss for
  sideload users.
- **Worker pill on Sessions list rows.** Sessions spawned by a
  Container Workers agent now render a purple `⬡ <agentId>` chip
  next to the backend badge (PWA v5.26.58 parity). User-spawned
  sessions are unchanged. Closes 72h-audit gap #1 (issue #22).
- **Container Workers (`cfg.Agents`) settings panel.** New section
  in the General settings tab with every server-side `agents.*`
  knob: `image_prefix`, `image_tag`, `docker_bin`, `kubectl_bin`,
  `callback_url`, `bootstrap_token_ttl_seconds`,
  `worker_bootstrap_deadline_seconds`. Matches PWA v5.26.56.
  Closes 72h-audit gap #4 (issue #23).

## [0.42.5] — 2026-04-28 (PRDs nav: full-color 🤖 + hide when not configured)

### Changed

- **PRDs bottom-nav glyph rendered as the literal 🤖 emoji**
  (U+1F916). Material's `Icons.Filled.SmartToy` was a flat
  single-color outline — the system emoji renders in full colour
  at the same size and matches the PWA exactly.

### Fixed

- **PRDs tab now hides on servers without the autonomous
  surface.** User direction 2026-04-28: the PWA's local-host
  profile doesn't show PRDs (no `/api/autonomous/prds`); the
  `ralfthewise` remote does. Mobile now mirrors that — `HomeShell`
  probes `transport.listPrds()` whenever the active server flips,
  and `BottomNavBar` filters the PRDs item out of the nav when
  the probe fails. Federated "All servers" mode keeps the tab
  visible since any one of the fanned-out profiles may carry it.

## [0.42.4] — 2026-04-28 (diagnostic logging on /datawatch/refreshSession)

### Added

- **Structured `Log.d("WearSync", …)` + `Log.d("WearMain", …)`** at
  every step of the `/datawatch/refreshSession` round-trip — message
  receipt, active-server resolution, `listSessions` success / failure,
  `upsert` (with `lastResponse.length` + 60-char preview),
  `publishSessions` (per-session `lr=` lengths), and watch-side
  `applySessions`. On-device verification 2026-04-28 walked the
  entire chain start to finish and confirmed the popup body matches
  the PWA / Android `last_response` — leaving the logs in so future
  Wear bridge investigations don't need a debug-only build cycle.

## [0.42.3] — 2026-04-28 (watch Sessions filters + popup re-resolves on republish)

### Fixed

- **Watch session popup never showed the post-refresh body.** Even
  though v0.42.2 wired the `/datawatch/refreshSession` round-trip,
  the popup captured the `SessionItem` at tap time and Compose
  never re-read `state.sessions` after the phone republished. The
  popup now re-resolves `openSession` against the latest published
  list on every recomposition; the freshly-refetched
  `lastResponse` body lands as soon as the phone fans the new
  snapshot through DataLayer.

### Added

- **Sessions page filter row.** User direction 2026-04-28: tap
  **wait** (default) for waiting_input only, **run** for running,
  **total** for everything in the published window including
  completed / killed / error. Order is wait / run / total because
  wait is the most actionable surface (sessions blocked on a
  reply). The existing count tiles double as the filter buttons —
  selected one is highlighted with a translucent fill in its
  state colour.



### Fixed

- **Wear session popup** still showed a stale/short response when
  what the PWA + Android app rendered had moved on. The watch now
  triggers a fresh refetch the moment the popup opens — no more
  waiting for the next phone-side WS frame to arrive.

### Added

- **`/datawatch/refreshSession` MessageClient round-trip.** Watch
  sends the sessionId on tap; phone calls
  `transportFor(profile).listSessions()`, upserts the matching
  record into `sessionRepository`, and the existing reactive
  `/datawatch/sessions` publisher ships the fresh
  `lastResponse` body to the wrist. Best-effort — failure leaves
  the cached snapshot in place. New constant `REFRESH_SESSION_PATH`
  on both phone and watch.

### Changed

- **`SESSION_LAST_RESPONSE_MAX` raised 600 → 4000 chars.** The
  watch popup is vertically scrollable, so trimming to 600 was
  hiding the bulk of long LLM replies. 12 sessions × 4000 ≈ 48 KB
  worst case, still well under the 100 KB DataLayer ceiling once
  the other arrays are accounted for.

## [0.42.1] — 2026-04-28 (Wear session popup shows last response)

### Fixed

- **Wear session-detail popup** now renders the same "view last
  response" content the phone shows in its `LastResponseSheet`
  instead of the one-line preview. User report 2026-04-28: tapping
  a session on the watch should auto-load the latest last response,
  it didn't. Phone publishes a new `lastResponses` string array on
  `/datawatch/sessions` (per-session, capped at 600 chars to stay
  well under the 100 KB DataLayer ceiling); the watch popup prefers
  it over `lastLine` and bumps `maxLines` from 4 → 12 so multi-line
  bodies stay readable. Older phone builds without `lastResponses`
  fall back to the previous one-liner — no contract break.

## [0.42.0] — 2026-04-28 (compact tmux/channel tabs + inline terminal toolbar)

### Changed

- **Session detail tabs row** rewritten to PWA's compact layout: the
  full-width Material `TabRow` (`tmux` / `channel`) is gone; the two
  buttons are now rounded pills sized to their label, left-justified,
  with the font / Fit / Scroll buttons inlined on the right of the
  same row. User direction 2026-04-28: *"tmux and channel tabs should
  be small (width of words) and the font buttons and scroll button
  should be on the same line as the tmux and channel tabs, like in
  PWA."* Saves a vertical strip of phone real estate and matches PWA
  muscle memory when switching surfaces.
- **`TerminalToolbar.kt` refactored** into a hoisted-state API so the
  controls can sit on the tabs row while the scroll-mode nav strip
  (PgUp / PgDn / ↑ / ↓ / ESC) renders separately under the terminal
  viewport — closest to where the user is reading. New public surface:
  `rememberTerminalToolbarState(controller, sessionId)`,
  `TerminalToolbarControls(state)`, `TerminalScrollModeStrip(state)`.
  The legacy `TerminalToolbar(controller, modifier, sessionId)` wrapper
  is kept for any out-of-tree caller and marked deprecated.

## [0.41.1] — 2026-04-28 (PRDs nav icon → SmartToy)

### Changed

- **Bottom-nav PRDs icon** swapped from `Icons.Filled.AutoAwesome` to
  `Icons.Filled.SmartToy` to match PWA's `🤖` (U+1F916) on the
  autonomous nav button — same rounded-robot silhouette.

## [0.41.0] — 2026-04-28 (VM unit tests via ProfileResolver shim)

### Added

- **`ProfileResolver` fun-interface** in `composeApp/.../ui/common/`.
  Wraps the `ServiceLocator.activeServerStore + transportFor` pair so
  the v0.36+ monitoring + autonomous + mempalace ViewModels can
  resolve their (profile, transport) tuple through a single
  injectable seam.
- **`ProfileResolver.Default`** — production singleton; reads
  `ServiceLocator` exactly as the VMs did before. Constructor
  defaults preserve every existing call-site.
- **VM unit tests** in `composeApp/src/androidUnitTest/`:
  - `AutonomousViewModelTest` — 8 tests covering `refresh` (success
    / failure / no-server), `create`, `approve`, `reject` (verifies
    the reason payload), `editStory` (verifies blank-stripped fields
    pass through as null), `editFiles`.
  - `MonitoringViewModelTests` — 12 tests covering
    `FederatedPeersViewModel` (load + filter + failure-as-empty),
    `ClusterNodesViewModel` (cluster.nodes mapping + empty when
    no cluster block), `EBpfStatusViewModel` (host.ebpf surfaces
    + null on failure), `PluginsCardViewModel` (subprocess vs
    native split), `OrchestratorGraphViewModel` (graph load + banner
    on failure), `MempalaceActionsViewModel` (sweep / spellcheck /
    extract result wiring).

### Changed

- All 7 new VMs (`AutonomousViewModel`, `OrchestratorGraphViewModel`,
  `MempalaceActionsViewModel`, `FederatedPeersViewModel`,
  `ClusterNodesViewModel`, `EBpfStatusViewModel`,
  `PluginsCardViewModel`) take a `ProfileResolver` constructor
  parameter, defaulting to `ProfileResolver.Default`. No behaviour
  change in production; tests pass a mockk-backed fake.

### Build

- `composeApp/build.gradle.kts` `androidUnitTest` source set adds
  `io.mockk:mockk:1.13.13` + `kotlinx-coroutines-test:1.9.0` to back
  the VM tests. Avoids hand-implementing the 60-method
  `TransportClient` interface.

## [0.40.2] — 2026-04-28 (DTO round-trip test coverage)

### Added

- **11 round-trip / parse tests** in `:shared:testDebugUnitTest` for
  every transport DTO added between v0.36.0 and v0.39.2:
  `ObserverPeersDto`, `ObserverStatsDto` (host.ebpf + cluster.nodes),
  `PluginsDto` (subprocess + native), `FilesMkdirDto`,
  `MemorySweepStaleResponseDto`, `MemorySpellcheckResponseDto`,
  `MemoryExtractFactsResponseDto` (incl. the `object` ↔ `obj`
  Kotlin-keyword remap), `PrdListDto` (depth + template + stories
  + files + files_touched), `NewPrdRequestDto`, `OrchestratorGraphDto`
  (incl. `observer_summary` per node), `StartAgentResponseDto`
  (accepts either `session_id` or `id`), `StartAgentRequestDto`.

### Notes

- VM unit tests for the new screens (`AutonomousViewModel`,
  `OrchestratorGraphViewModel`, `MempalaceActionsViewModel`,
  `FederatedPeersViewModel`, `ClusterNodesViewModel`,
  `EBpfStatusViewModel`, `PluginsCardViewModel`) need a
  `ServiceLocator` mock-out refactor that is genuinely out of
  scope for this round. The DTO tests guard the wire contract,
  which is where most regression risk lives.

## [0.40.1] — 2026-04-28 (Auto PRD review screen)

### Added

- **Auto — `WaitingPrdsScreen`** lists the active server's PRDs in
  `needs_review` / `revisions_asked` so a driver can hands-free
  approve / reject between drives. Reachable from the existing
  `AutoSummaryScreen` via a new "PRDs to review" row beneath the
  session counts.
- **Auto — `PrdActionScreen`** is the per-PRD action target: a
  `MessageTemplate` with two big buttons (Approve / Reject) plus a
  Back action. Reject ships an automatic "rejected from car"
  reason so the daemon's `request_revision` workflow records
  something without keyboard input on a moving vehicle. Full
  rejection-with-reason flow stays on the phone.
- Both screens reuse the existing 15 s poll cadence + the shared
  `AutoServiceLocator.transportFor(profile).listPrds() / prdAction`
  surfaces (added in v0.38.0 / v0.38.1). No new transport.

### Notes

- ADR-0031 Play-compliance preserved: every Auto template here is
  static `ListTemplate` / `MessageTemplate` — no free-form text
  input from the driver surface.

## [0.40.0] — 2026-04-28 (Wear PRDs glance with approve / reject)

### Added

- **5th Wear page — "PRDs"** between Sessions and Servers. Renders
  the active server's needs_review / running / revisions_asked
  PRDs with tap-to-approve (✓) and tap-to-reject (✕) glyphs. The
  page hides itself when the list is empty (no autonomous surface
  configured / no PRDs).
- **Phone-side PRD publish** — `WearSyncService` polls
  `transport.listPrds()` on the existing 15 s stats cadence and
  pushes a `/datawatch/prds` DataItem (id + title + status, capped
  at 8 entries to fit the DataLayer budget).
- **Watch → phone PRD action relay** — new MessageClient path
  `/datawatch/prdAction` with payload `prdId\naction\nreason?`.
  Phone resolves the active profile and posts to
  `/api/autonomous/prds/{id}/{action}`. Reject ships an automatic
  "rejected on watch" reason — full reject-with-reason flow stays
  on the phone.

### Notes

- `WearSessionCountsViewModel` now hosts 11 public methods (counts /
  profiles / stats / sessions / prds DataLayer + 4 send-* actions).
  Wear detekt baseline regenerated to accept the `TooManyFunctions`
  threshold; refactor against legibility on the round bezel
  intentionally deferred.

## [0.39.2] — 2026-04-28 (Memory pin + PRD conflict marker + decomposition profile + approval gate config)

### Added

- **Per-row pin button on `MemoryCard`**. Pinned entries show a
  filled pin icon in the primary tint; unpinned show the outlined
  pin in the surface-variant tint. Tap toggles via the
  `memoryPin()` transport that landed in v0.37.0.
- **File conflict marker on PRD story file pills.** When two
  pending stories plan the same path, every conflicting pill turns
  red and prefixes ⚠; a tooltip-style line under the pill names
  the other stories. Mirrors PWA v5.26.64 cross-story conflict
  detection.
- **Decomposition profile dropdown on `NewPrdDialog`** — independent
  of the project / execution profile (PWA v5.26.60-62). First
  option is `— inherit —`; remaining options are the configured
  project profiles. Routed through the existing
  `NewPrdRequestDto.decompositionProfile` field.
- **Settings → General → Autonomous: per-story approval gate
  toggle** (`autonomous.per_story_approval`) added to the config
  schema panel. Default OFF; when ON, every story spawns in
  `awaiting_approval` and needs an explicit Approve/Reject. Operator
  UI for that gate already shipped in v0.38.1's `PrdDetailDialog`.

## [0.39.1] — 2026-04-28 (New Session unified Profile + cluster routing)

### Added

- **Cluster sub-dropdown on `NewSessionScreen`** — closes
  [#20](https://github.com/dmz006/datawatch-app/issues/20)
  (PWA v5.26.63). Renders only when a project profile is selected;
  first option is the local-service-instance sentinel (empty
  string), remaining are the configured cluster profiles loaded
  via `listKindProfiles("cluster")`.
- **`startAgent(StartAgentRequestDto)`** on `TransportClient` +
  `RestTransport` — `POST /api/agents` for F10 ephemeral-agent
  sessions whose worker LLM is carried by the project profile's
  `image_pair` rather than a flat `backend` field.
- **DTOs** — `StartAgentRequestDto` (task / project_profile /
  cluster_profile? / branch? / name?), `StartAgentResponseDto`
  (server returns `session_id` or `id` depending on build).

### Changed

- **Start button branches by mode.** When a project profile is
  selected the spawn goes to `POST /api/agents` with the F10 body;
  when no profile is selected (project-directory mode) the spawn
  keeps the historic `POST /api/sessions/start` with workingDir +
  backend. Mirrors PWA v5.26.63 routing.

## [0.39.0] — 2026-04-28 (Orchestrator PRD-DAG graph + observer_summary)

### Added

- **`OrchestratorGraphDialog`** — closes
  [#7](https://github.com/dmz006/datawatch-app/issues/7) (S13).
  Reachable via the new "📊 Graph" button on `PrdDetailDialog`.
  Renders nodes as a list (no force-directed layout — overkill on a
  phone screen) with each row carrying its `observer_summary` badge:
  CPU %, RSS MB, envelope count. Outgoing edges render as
  `→ targetId (kind)` lines below each node so the DAG topology is
  legible without arrows.
- **`OrchestratorGraphViewModel`** loads `/api/orchestrator/graphs/{id}`
  on demand. Falls back gracefully when the daemon predates the
  S13 endpoint or when a node has no `observer_summary` payload.
- **Transport** — `orchestratorGraph(id)` on `TransportClient` +
  `RestTransport`.
- **DTOs** — `OrchestratorGraphDto` (id + name + nodes[] + edges[]),
  `OrchestratorNodeDto` (id, name, status, kind, observer_summary?),
  `ObserverSummaryDto` (cpu_pct, rss_mb, envelope_count, last_push_at),
  `OrchestratorEdgeDto` (from, to, kind?).

## [0.38.1] — 2026-04-28 (Autonomous tab features)

### Added

- **PRD list filter row** behind a magnifier toggle in the Autonomous
  TopAppBar ([#13](https://github.com/dmz006/datawatch-app/issues/13)).
  Status chips (`needs_review` / `running` / `complete` / `rejected`)
  + Templates checkbox; closed by default. FAB hides while a PRD
  detail dialog is open (PWA v5.26.36 parity).
- **Tap a PRD row → `PrdDetailDialog`** with story list rendering.
  Story description rendered below the title in `pre-wrap` style;
  `✎` button on each story while parent PRD is in `needs_review` /
  `revisions_asked` ([#12](https://github.com/dmz006/datawatch-app/issues/12)).
  Edit modal carries title + multi-line description fields.
- **Approve / Reject buttons** on the PRD detail dialog when status
  is `needs_review` or `revisions_asked`
  ([#18](https://github.com/dmz006/datawatch-app/issues/18)).
  Reject prompts for a required reason.
- **File pills on story rows** ([#19](https://github.com/dmz006/datawatch-app/issues/19)).
  `📝` blue pills for `story.files` (planned), green pills for
  `story.files_touched` (post-spawn). `✎ files` button opens the
  file-list edit modal (one path per line, 50-cap). Conflict
  detection across stories deferred to v0.38.2.
- **Transport** — new `editStory(prdId, storyId, newTitle?,
  newDescription?, actor?)` and `editFiles(prdId, storyId|taskId,
  files, actor?)` on `TransportClient` + `RestTransport`. Backed by
  `POST /api/autonomous/prds/{id}/edit_story` and `…/edit_files`.

### Notes

- v0.38.2 closes #20 (unified Profile dropdown in New Session modal —
  `NewSessionScreen` already has a profile picker; PWA v5.26.63's
  collapse onto a single Profile selector + Cluster sub-dropdown
  rebuild stays in scope).
- Per-story Decomposition profile dropdown (PWA v5.26.60-62) and
  Settings → Autonomous "per-story approval gate" toggle are part of
  #18; included as the Approve/Reject affordance, but the
  `autonomous.per_story_approval` config-key surface stays on the
  Settings → Autonomous tab (deferred to v0.38.2).

## [0.38.0] — 2026-04-28 (Autonomous tab foundation)

### Added

- **New "PRDs" bottom-nav tab** between Sessions and Alerts. PWA
  has a dedicated `data-view="autonomous"` view; mobile mirrors.
  `AutonomousScreen` lists every PRD the parent observer reports
  (`/api/autonomous/prds`) with status pill (decomposing / needs_review
  / running / complete / etc.), depth indicator, template badge,
  story count.
- **New PRD modal** with the **unified Profile dropdown** that PWA
  v5.26.30 collapsed (closes [#11](https://github.com/dmz006/datawatch-app/issues/11)).
  First option is the project-directory sentinel `__dir__`; remaining
  options are the configured F10 project profiles. When `__dir__` is
  selected the dialog shows directory + Backend / Effort / Model
  fields. When a profile is selected the dialog hides those and shows
  a Cluster dropdown (first option `— Local service instance —`,
  remaining are cluster profiles). Routes to either
  `POST /api/autonomous/prds` (with `project_dir`) or with
  `project_profile` per the PWA semantics.
- **Transport** — new `listPrds()`, `createPrd()`, `prdAction()` on
  `TransportClient` + `RestTransport`.
- **DTOs** — `PrdListDto`, `PrdDto`, `PrdStoryDto`, `NewPrdRequestDto`,
  `NewPrdResponseDto`.

### Notes

- v0.38.1 closes the rest of the autonomous backlog: story-level
  review/edit ([#12](https://github.com/dmz006/datawatch-app/issues/12)),
  PRD list FAB + filter toggle ([#13](https://github.com/dmz006/datawatch-app/issues/13)),
  per-story approval gate ([#18](https://github.com/dmz006/datawatch-app/issues/18)),
  file association pills ([#19](https://github.com/dmz006/datawatch-app/issues/19)),
  and the matching profile dropdown in the New Session modal
  ([#20](https://github.com/dmz006/datawatch-app/issues/20)).

## [0.37.0] — 2026-04-28 (Mempalace surfaces — sweep / spellcheck / extract)

### Added

- **Settings → Monitor — Mempalace card** ([#21](https://github.com/dmz006/datawatch-app/issues/21)).
  Surfaces the v5.27.0 mempalace operator endpoints:
  - **Sweep stale** — older-than-N-days input + `dry-run` checkbox
    (default on); *Estimate* / *Sweep now* button reports the count.
  - **Spellcheck** — text field + Check action, renders the
    Levenshtein suggestions inline (daemon never rewrites).
  - **Extract facts** — text field + Extract action, renders the
    SVO triples (`subject — verb → object`).
- **Transport** — new methods on `TransportClient` + `RestTransport`:
  `memoryPin(id, pinned)`, `memorySweepStale(olderThanDays, dryRun)`,
  `memorySpellcheck(text, extraWords)`, `memoryExtractFacts(text)`,
  `memoryWakeup(projectDir, agentId, parentAgentId, parentName)`.
- **DTOs** — `MemoryPinDto`, `MemorySweepStale*Dto`, `MemorySpellcheck*Dto`,
  `SpellcheckSuggestionDto`, `MemoryExtractFacts*Dto`, `SvoTripleDto`.

### Notes

- `pin` transport ships in this release but the per-row pin button on
  `MemoryCard` is queued for v0.37.1 — keeping the v0.37.0 UI delta
  contained to the new card.
- `wakeup` is a session-lifecycle helper rather than an operator UI;
  exposed on the transport so future agent-bootstrap flows can call
  it without another release.

## [0.36.2] — 2026-04-28 (Connection resilience completions)

### Changed

- **Reachability dot pulses while probing.** When `reachable == null`
  (no probe completed yet) the amber dot now scales 1.0 ↔ 1.4 on a
  900 ms reverse loop so the user sees that work is happening
  instead of a static unknown.
- **Screen-unlock lifecycle observer.** `AppRoot` registers a
  `LifecycleEventObserver` for `ON_RESUME` and pings every enabled
  profile so the reachability dots reflect current state instead
  of whatever was true when the screen turned off. The 5-second
  poll loop in `SessionsViewModel` continues to drive subsequent
  refresh; this just removes the "first-poll lag" on unlock.

## [0.36.1] — 2026-04-28 (Picker mkdir + response-noise filter)

### Added

- **File picker — "+ New folder" affordance** ([#14](https://github.com/dmz006/datawatch-app/issues/14)).
  Mirrors PWA v5.26.46. Inline form inside `FilePickerDialog`
  collects a folder name, refuses path-separator chars
  client-side, then POSTs `{path, action:"mkdir"}` via the new
  `TransportClient.mkdir()` and re-browses the parent so the new
  folder appears in the listing immediately.
- **`TransportClient.mkdir()` + `FilesMkdirDto`** — new `POST
  /api/files` body type so future actions (rename / delete) plug
  in without another DTO.

### Fixed

- **Response viewer strips TUI noise** ([#15](https://github.com/dmz006/datawatch-app/issues/15)).
  New `ResponseNoiseFilter` (in `:shared`) ports the PWA v5.26.31
  filter: drops pure box-drawing lines, labeled borders
  (`─── Status ───`), embedded status timers (`elapsed 0:01:23`,
  `12s`), spinner counters (`⠋ Thinking…`), pure digit lines
  (`12 / 100`), and a small set of broadened noise patterns
  (`tokens:`, `(esc to interrupt)`, etc.). Applied unconditionally
  per the v5.26.31 lesson — the v5.26.23 prose-detection gate was
  too charitable. `LastResponseSheet` runs the captured response
  through the filter before rendering. Full unit-test regression
  coverage in `:shared:testDebugUnitTest`.

## [0.36.0] — 2026-04-28 (Federated monitoring suite)

### Added

- **Settings → Monitor — federated peers card** ([#2](https://github.com/dmz006/datawatch-app/issues/2)).
  Mirrors PWA `loadObserverPeers()` (datawatch v4.4.0+). Lists Shape B / C
  / Agent peers from `/api/observer/peers` with a coloured health dot
  (green ≤15 s push age, amber ≤60 s, red >60 s, grey if never), shape
  badge, last-push age, hostname. Card hides on single-node setups
  (empty peers list).
- **Agents filter pill row** on the federated peers card
  ([#6](https://github.com/dmz006/datawatch-app/issues/6) — S13 parity).
  `All / Standalone / Cluster / Agents` chips at the top narrow the
  list client-side; matches PWA `host.shape == "agent"` semantics.
- **Settings → Monitor — cluster nodes card**
  ([#3](https://github.com/dmz006/datawatch-app/issues/3)). Mirrors PWA
  `loadObserverClusterNodes()` (datawatch v4.5.0). Renders only when
  `/api/observer/stats.cluster.nodes` is non-empty. Per-node row carries
  health dot (ready vs unhealthy), name, pressure flags
  (`memory|disk|pid` from kubelet), pod count, CPU + memory bars (CPU
  threshold-coloured the same way as the Wear Monitor gauges).
- **Settings → Monitor — eBPF status card**
  ([#4](https://github.com/dmz006/datawatch-app/issues/4)). Mirrors PWA
  `loadEBPFStatus()` (datawatch v4.1.1+). Three pill flags
  (`configured` / `capability` / `kprobes`) read from
  `/api/observer/stats.host.ebpf` plus the human-readable status
  message. Card hides for daemons that predate the observer endpoint.
- **Settings → Monitor — plugins card**
  ([#5](https://github.com/dmz006/datawatch-app/issues/5)). Mirrors PWA
  `loadPluginsStatus()` (datawatch v4.2.0+ / B41). Renders subprocess
  + native plugins in the same list with a `subprocess` / `native`
  kind badge so operators see datawatch-observer alongside subprocess
  hooks without confusion. Native plugins render first.

### Transport

- New `/api/observer/stats`, `/api/observer/peers`, and `/api/plugins`
  endpoints on `TransportClient` + `RestTransport` impl. New DTOs:
  `ObserverStatsDto` (host + cluster), `ObserverPeersDto`,
  `ObserverPeerDto`, `ObserverPeerHostDto`, `ObserverHostDto`,
  `ObserverEbpfDto`, `ObserverClusterDto`, `ObserverClusterNodeDto`,
  `PluginsDto`, `PluginDto`.

### Notes

- Each card self-hides when its backing endpoint returns nothing
  useful (older daemon, single-node deployment) so the Monitor tab
  stays readable on minimal setups and grows on richer ones.
- Issue [#7](https://github.com/dmz006/datawatch-app/issues/7)
  (`observer_summary` per-node badges on the PRD-DAG graph) deferred
  to v0.36.1 — the orchestrator graph is its own screen and warrants
  a focused release.

## [0.35.10] — 2026-04-28 (Session detail force-refresh on open)

### Fixed

- **Session detail force-refreshes from server on open.** v0.35.8
  added `SessionDetailViewModel.refreshFromServer()` for the Response
  button + WS state changes; this release also calls it from the VM
  `init` block so the moment the user opens the detail screen they
  see current `last_response` / `last_prompt` / state instead of
  whatever the 5-second list poll last cached. Closes the user's
  *"should refresh to get the current state like going in from
  session list"* complaint (2026-04-28).

### Notes

Connection resilience pass (task #209) continues — pending items
slated for v0.36.x:

- Animated processing indicator alongside the reachability dot.
- Throttle reconnect attempts when the network is offline / phone
  is locked (avoid Tailscale-loop battery burn).
- Screen-unlock lifecycle observer that probes + refreshes the
  active session on resume.

## [0.35.9] — 2026-04-28 (Session detail layout rebuild)

### Changed

- **Badges row moves above the tmux/channel TabRow.** PWA carries
  the chips at the top of the session-info-bar; mobile aligns —
  Stop / Timeline / state-override / Last-Response are reachable
  without scrolling past the tabs.
- **Unified quick-actions strip above the composer.** New row
  carries the Last-Response viewer (`Description` icon) + Saved
  Commands sheet (`Keyboard` icon) + four ANSI arrow chips
  (`↑ ↓ ← →`). Replaces both the standalone arrow-key LazyRow
  *and* the under-mic Saved-Commands stack.
- **Composer row reordered** to PWA order: text field → **Send** →
  Schedule → Mic. Saved-Commands button removed from under the mic
  (now on the quick-actions row above).
- **Last Response icon is consistent** across surfaces — same
  `Icons.Filled.Description` glyph on the SessionInfoBar badge
  bar AND the new quick-actions row.

## [0.35.8] — 2026-04-28 (Wear voice via phone-relayed Whisper + popup polish)

### Changed

- **Wear Sessions row preview shows `lastResponse`** instead of
  `lastPrompt`. Running sessions are mostly interesting for what
  they just produced; falls back to `lastPrompt` → `taskSummary`
  → empty so the row is always anchored to readable content.
- **`SessionDetailPopup` mic button anchors to the right edge**
  of the safe area; the Send chip pops in on the left edge once a
  transcript is staged. Centre column carries title / state /
  last-line / transcript stack — thumb hits the controls without
  crossing the screen.
- **State-aware popup labels** — "Listening…" while recording (red),
  "…transcribing" while waiting on the phone, transcript text once
  Whisper replies. Stop icon (`■`) replaces the mic glyph during
  recording so the toggle state is unambiguous.

### Fixed

- **Wear voice replaced with phone-relayed Whisper.** The
  `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` flow added in v0.35.5
  didn't reliably return text on the Galaxy Watch — no on-device
  recognizer model + the Pixel-Watch-style Assistant flow doesn't
  hand the result back to the launcher. v0.35.8 records audio
  locally via the new `WearVoiceRecorder` (mirror of the phone's
  `VoiceRecorder`), ships raw bytes to the phone over MessageClient
  on the new `/datawatch/audio` path. Phone resolves the session's
  profile, posts to `/api/voice/transcribe`, and replies on
  `/datawatch/transcript` with `sessionId\n<text>` (or
  `sessionId\nerror:<cause>` on failure). Watch shows the transcript
  inside the existing popup for the user to validate before tapping
  Send. Reuses the v0.35.5 `/datawatch/reply` plumbing for the
  send_input forward.

## [0.35.7] — 2026-04-28 (PWA v5.1.0–v5.2.0 alignment + data freshness)

### Changed

- **Terminal toolbar always renders** (closes [#8](https://github.com/dmz006/datawatch-app/issues/8)). The badge-row `Aa ▾ / Aa ▴` toggle added in v0.35.6 is gone — PWA v5.1.0 ripped the toggle, and the row reads cleanly at every viewport size. Upstream design-sync issue [dmz006/datawatch#24](https://github.com/dmz006/datawatch/issues/24) is now obsolete and will be closed.
- **Sessions list — "Show / Hide history (N)" → "History (N)"** to match PWA v5.1.0 (drops the verb churn).
- **Session detail — tmux arrow-key row** (mirrors PWA v5.2.0). Four AssistChips (`↑ ↓ ← →`) above the composer send the corresponding ANSI escape sequences via the existing `WsOutbound.sendInput` path. Same chip styling as the saved-commands quick row.
- **Settings → About — Play Store row** added (mirrors PWA v5.2.0). Currently shows `(pending submission)`; URL slots in once the listing publishes.
- **Settings → About — `ConfigViewerCard` removed** to align with the PWA About surface (which carries no raw-config blob). The general-config keys remain reachable via `Settings → General` config panels — the only place users actually edit them.

### Fixed

- **Live `last_response` is now refetched on demand** (BL178 / [#9](https://github.com/dmz006/datawatch-app/issues/9)). Tapping the `💾 Response` button on the session info bar now triggers a forced server re-read before opening `LastResponseSheet`. The daemon's `Manager.GetLastResponse` re-captures from live tmux on every read for `running` / `waiting_input` sessions; cached value from the 5-second list poll was up to 5 s stale and would flash a slightly older snippet.
- **Input-Required banner refreshes on bulk WS state pushes** (mirrors PWA v5.26.49 fix). `SessionDetailViewModel.startStream` now triggers a `refreshFromServer()` on every `SessionEvent.StateChange` frame, so a session that flips to `waiting_input` via the bulk-sessions push surfaces the yellow banner + prompt-context text immediately instead of after the next 5-second poll. Closes the operator complaint *"if I'm in a session and it ends, the yellow box with prompt details doesn't show up, I have to exit and re-enter."*

## [0.35.6] — 2026-04-24 (Composer reshuffle + voice-reply fix + terminal toolbar toggle)

### Changed

- **Sessions list FAB lowered** further. `Modifier.offset(y = 36.dp)`
  on the FAB drops it past the Scaffold's inset reserve so the `+`
  button sits within thumb reach on a 6.8" screen rather than on
  the bottom-nav seam.
- **Sessions list header** — `ServerPickerTitle` drops its vertical
  padding; the TopAppBar already centre-aligns the title so the
  extra 4 dp was making the header feel hollow above the hostname.
- **Session detail — Response button moves back to the badge bar**
  (reverts v0.35.3's composer move). Composer row now stacks the
  **Saved Commands** button (📋 keyboard icon) under the mic. Taps
  open the existing `QuickCommandsSheet` with the session's saved
  commands from `/api/commands`.
- **QuickCommandsSheet custom reply** gets a mic button next to Send.
  Records via `VoiceRecorder`, transcribes, appends into the custom
  text field for review before sending.
- **Terminal toolbar hides by default behind a badge-row toggle.**
  SessionInfoBar now shows `Aa ▾` (expand) / `Aa ▴` (collapse) —
  per-session state, hidden on first open so the badge strip stays
  compact. Upstream design-sync issue [dmz006/datawatch#24](https://github.com/dmz006/datawatch/issues/24)
  filed for PWA parity.
- **Settings → Monitor slims down.** `KillOrphansCard` moves to About
  alongside `UpdateDaemonCard` + `RestartDaemonCard` (daemon-admin
  cluster); Monitor keeps Stats / Memory / Schedules / DaemonLog.

### Fixed

- **Session-detail voice reply routed to the wrong server.** The mic
  button resolved the target transport from `ActiveServerStore`
  instead of the session's own `serverProfileId`. When the active
  store pointed elsewhere (All-Servers mode, cross-server
  navigation) the transcribe call hit a server that didn't host
  that session — the user's "voice use to work but isn't anymore"
  report. Now looks up the session's profile via
  `SessionRepository.observeForProfileAny(sessionId)` and falls back
  to the active store only when the session row isn't cached yet.
- **Voice-failure toast surfaces the real cause.** Ktor's wrapped
  exceptions mask the underlying 404/500/TLS/bearer error in the
  top-level message; the composer now walks the cause chain and
  prints the inner `className: message` so the user can tell
  whether Whisper is disabled, the bearer is missing, or the profile
  is unreachable.
- **"No enabled server profile" toast** when no profile at all can
  be resolved — previously the mic button just silently reset
  after the record/stop cycle.

## [0.35.5] — 2026-04-24 (Wear Sessions tap-popup + voice reply)

### Added

- **Per-session list on the Wear Sessions page.** Phone's
  `WearSyncService` publishes the top-12 most-recently-active sessions
  on a new `/datawatch/sessions` DataItem (id, title, backend, state,
  last-line). Wear renders each as a coloured state-badge row
  (running green / waiting amber / rate-limited red) sorted by
  activity.
- **Tap a session on the watch → `SessionDetailPopup`** with title,
  state, last-line preview, and a microphone button. Mic launches the
  system speech-to-text activity via `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`;
  the transcribed text appears inside the popup for confirmation.
- **Voice-reply relay.** Watch sends the transcript + session id to
  the phone over MessageClient on `/datawatch/reply`; phone
  `WearSyncService` opens a transient WS subscription, emits
  `send_input`, and closes after the drain grace. This is the only
  path — the server doesn't expose a REST `/api/sessions/reply` (404
  pre-v0.34.6 regression that WS send_input was the fix for).

### Changed

- **Wear counts strip unchanged** (running/waiting/total tile trio
  stays at the top of the Sessions page) — the per-session rows
  slot below it so the glance read stays instant.

### Notes

- If a watch reply is issued while the phone is offline or the
  paired server is unreachable, the WS connect will time out during
  `WATCH_REPLY_SUBSCRIBE_GRACE_MS` and the `send_input` frame will
  be dropped silently. A later release should surface that failure
  back to the watch.
- `SessionDetailPopup` is baselined under `wear/detekt-baseline.xml`
  for `LongMethod` (107 vs 80 cap). Refactoring the stacked
  title/mic/send layout would only hurt legibility; accepting the
  baseline is the right call here.

## [0.35.4] — 2026-04-24 (Wear Monitor redesign — color gauges + GPU)

### Changed

- **Wear Monitor page rebuilt around round-bezel gauge rings.** CPU /
  Memory / Disk / GPU each render as a threshold-coloured
  `CircularProgressIndicator` (green < 60 % → amber 60–80 % → red
  ≥ 80 %) with the value printed in the centre of the ring. 2-up grid
  fits the Samsung Galaxy Watch bezel; content overflow scrolls via
  the existing `PageScaffold` vertical scroll.
- **GPU stats now published to the watch.** `WearSyncService` attaches
  `gpuUtilPct`, `gpuTempC`, `gpuMemUsedMb`, `gpuMemTotalMb`, and
  `gpuName` to the `/datawatch/stats` DataItem. Watch hides the GPU
  gauge when the phone hasn't published (graceful downgrade with older
  phone builds).
- **Uptime + VRAM summary** move below the gauge grid as captions
  instead of list rows — keeps the page dense without losing detail.

## [0.35.3] — 2026-04-24 (Sessions UX polish + Wear round cards + Auto-release workflow fix)

### Changed

- **Sessions list: filter / sort / history collapse behind a single
  search icon** in the TopAppBar, with the reachability dot moved to
  the right of the actions slot (matches PWA header layout). FAB
  lowered ~24 dp so it doesn't overlap the bottom nav.
- **Session detail: drop the `tmux` mode badge** — it was redundant
  with the tmux/channel TabRow immediately above. Only non-default
  modes (`channel`, `chat`, etc.) still surface as InfoBadge.
- **Response button relocated** from the SessionInfoBar to the composer
  row, stacked under the microphone (mic 40 dp + response 36 dp) so the
  info bar stays uncluttered while the saved-response surface stays
  one tap from the primary reply action.
- **Terminal toolbar hugs the info bar** — removed the extra 2 dp of
  vertical padding around the font/fit/scroll row so there's no empty
  line between the badges and the controls.
- **Wear: circular bordered card per page** (Monitor / Sessions / Servers
  / About) — `CircleShape` + 1.5 dp primary-tinted border matches the
  Samsung Galaxy Watch bezel geometry.
- **Auto: MagicNumber detekt cleanups** — byte-size and time constants
  in `AutoMonitorScreen` are now named (`BYTES_PER_MB`, `SECONDS_PER_DAY`,
  etc.) instead of inline literals.

### Fixed

- **Release workflow no longer 404s** — `bundlePublicRelease` was renamed
  to `bundlePublicTrackRelease` at v0.32 but `.github/workflows/release.yml`
  still pointed at the old task. Workflow now builds both publicTrack +
  dev bundles and assembles the publicTrack APK.

### Added

- **Slideshows in README** — phone / watch / PWA captures looping at
  ~2.5 s per frame. Watch frames are circle-cropped to match the bezel;
  PWA frames are sourced from Playwright against `localhost:8443`.
- **.gitignore excludes slideshow sources** (`docs/media/phone/`,
  `docs/media/watch/`, `docs/media/pwa/`) so per-frame PNGs stay out of
  git while the generated `*.gif` outputs are committed.
- **`docs/media/capture-{phone,watch}.sh`** — reproducible adb-driven
  navigation + screenshot scripts.
- **Wear detekt baseline** (`wear/detekt-baseline.xml`) to accept the
  NestedBlockDepth / TooManyFunctions findings pending the Wear UI
  refactor tracked in the Monitor redesign backlog.

### Upstream follow-ups

Filed against `dmz006/datawatch` to keep PWA ↔ mobile design in sync:

- [#21](https://github.com/dmz006/datawatch/issues/21) — voice-input UI
  parity (RemoteInput-style reply on PWA).
- [#22](https://github.com/dmz006/datawatch/issues/22) — PWA FAB migration
  for "New session" to match Android.
- [#23](https://github.com/dmz006/datawatch/issues/23) — Sessions list
  filter/sort/reachability-dot layout sync.

## [0.35.2] — 2026-04-24 (G11 inline header rename)

### Changed

- **G11 — Session-detail title is now tap-to-edit inline.** Clicking
  the header swaps the Text for a BasicTextField pre-filled with
  the current title; Enter or blur commits via `vm.rename`. Empty
  / unchanged values are ignored. Mirrors PWA `startHeaderRename`
  at app.js:1672 (which used contentEditable on the `<h2>`). The
  prior RenameDialog is still accessible via the overflow menu
  and elsewhere — inline is the primary path now.

### Notes

- Closes the last P2 row in the 2026-04-23 parity inventory.
  Remaining rows are P3 polish items (Monitor Plugins card,
  terminal toolbar Font/Fit/Scroll buttons, schedule popup field
  alignment) — captured in the audit doc and will ship on an
  opportunistic cadence.

## [0.35.1] — 2026-04-24 (Session-detail polish — state dropdown + Response)

### Changed

- **G12 — State override opens as a dropdown anchored to the pill**
  instead of a full-screen AlertDialog. Mirrors PWA's
  `showStateOverride(sessionId, element)` (app.js:2206) which
  pops a menu directly below the badge. Faster interaction —
  tap pill, tap target state, dismissed. SessionInfoBar now
  owns the dropdown's anchoring; the outer screen hands it
  `stateMenuOpen` + callbacks via new parameters.
- **G13 — Response button surfaces on the session info bar** for
  any session (active or terminal) whose `lastResponse` is non-blank.
  Opens the existing `LastResponseSheet` (was only reachable from
  the Sessions list 📄 icon before). Matches PWA's 💾 Response
  quick-panel under the composer (app.js:~1685).

### Notes

- The old `StateOverrideDialog` composable is retained as dead code
  for back-compat; a later cleanup release will remove it.
- `LastResponseSheet` promoted from `private` to `internal` so it
  can be used outside `SessionsScreen.kt` (from
  `SessionDetailScreen.kt` now).

## [0.35.0] — 2026-04-24 (LLM config paths + Settings compact inputs + eBPF banner)

### Fixed

- **G45 — LLM backend config was writing to nonexistent paths.** The
  Android schema used `backends.<name>.*` (e.g.
  `backends.ollama.enabled`), but the server actually stores each
  LLM's config under a top-level section named the same as the
  backend (e.g. `ollama.enabled`, `openwebui.url`,
  `session.claude_enabled`, `shell_backend.script_path`,
  `opencode_acp.acp_startup_timeout`). Verified against live
  `/api/config` on localhost:8443 and parent `app.js:4262-4288`
  `LLM_FIELDS` + `LLM_CFG_SECTION`. Every toggle + field save was a
  silent no-op — the server accepts unknown keys and discards them.
  - `LlmBackendSchemas.kt` rewritten to emit the canonical paths for
    each known backend, with a `section(name)` + `enabledKey(name)`
    helper to resolve section names consistently.
  - `LlmConfigCard` toggle writes now use `enabledKey(name)` (e.g.
    `session.claude_enabled`, `ollama.enabled`), so flipping the
    per-backend switch actually enables/disables the adapter.
  - `LlmConfigCard.isBackendConfigured` + `readBackendEnabled`
    scan the correct config section instead of the dead
    `backends.<name>` prefix.
  - `NewSessionScreen.backendEnabled` filter uses the same helper,
    so the backend picker correctly hides adapters whose section
    has `enabled=false`.
- **Dead LLM schemas dropped.** Removed `anthropic`, `openai`,
  `groq`, `openrouter`, `xai` — parent daemon has no adapters
  for these; the rows were dead UX. Added missing schemas the
  server does support: `aider`, `goose`, `gemini`, `opencode-acp`,
  `opencode-prompt`, `shell`. `KnownBackends` now matches the
  parent's `available_backends` list verbatim.
- **`StatsDto` missing `ebpf_enabled` + `ebpf_message`.** The
  server emits both (confirmed 2026-04-24 against live
  `/api/stats` on localhost:8443); adding the fields as nullable
  with server-driven defaults so older servers still parse.

### Changed

- **G9/G57 — Settings inputs now tight (compact density).** Swapped
  `OutlinedTextField` for a purpose-built `CompactInput` on every
  field in `ConfigFieldsPanel`: 36 dp tall, 13 sp text, 1 dp
  border, 6 dp vertical content padding. Fixes the user-flagged
  "buttons + input windows way larger than their text" report —
  M3's default 56 dp minimum-height OutlinedTextField dwarfed the
  actual content. The new field preserves password masking,
  number-only keyboards, placeholder text, and the cursor colour
  pull from theme.
- **G26 — Monitor tab eBPF Degraded banner.** Surfaces prominently
  when the daemon was built with eBPF capture but the kernel probes
  aren't loaded. Shows the server's
  `ebpf_message` (e.g. `"Degraded — run: datawatch setup ebpf"`)
  with an amber error-container background matching PWA.

### Notes

- No schema migration.
- Other G19-G28 Monitor cards (Network, Daemon, Infrastructure,
  RTK Token Savings, Memory, Ollama, Session Statistics ring) were
  already present on Android since earlier releases — verified
  coverage during this pass.

## [0.34.9] — 2026-04-24 (G6 Sessions drag-drop reorder)

### Changed

- **G6 — Sessions list reorder is now long-press drag.** Replaces
  the prior hamburger-menu reorder-mode toggle + up/down arrow
  buttons with a native Compose long-press drag on each row.
  Mirrors PWA HTML5 drag-drop (app.js:1412-1415). Approach:
  - Long-press a row → the row lifts (elevation + translationY).
  - Drag vertically → row floats; neighbours stay put (no live
    reordering — intentional for deterministic release).
  - Release → compute `(dragOffsetY / 72 dp).toInt()` and call
    `moveSessionByOffset(sessionId, shift)`, which updates the
    custom-order list in one shot.
  - Auto-seeds Custom sort + custom order snapshot on first drag
    so users don't have to toggle a reorder mode first.
- **Reorder-mode icon removed from the top app bar.** The VM's
  `toggleReorderMode` / `moveUp` / `moveDown` entries remain as
  accessibility fallbacks — they simply aren't surfaced in UI.

### Notes

- No schema or contract changes.
- Custom-order persistence across app restarts is still TBD
  (in-memory during VM lifetime, matching earlier behaviour).
  Follow-up: mirror PWA `localStorage('cs_session_order')` with
  SharedPreferences keyed on profile id.

## [0.34.8] — 2026-04-24 (G7 Alerts rebuild + Settings restart affordance)

### Added

- **G7 — Alerts screen rebuilt to PWA structure.** The old
  AlertsScreen derived rows from the session list (filtered to
  `Waiting && !muted`) and surfaced no actual `/api/alerts` payload.
  The new implementation:
  - Fetches `/api/alerts` every 5 s from the active server's
    transport.
  - Groups alerts by `session_id` (synthetic `"__system__"` bucket
    for session-less alerts) and classifies each group as **Active**
    (live session in running/waiting/rate-limited/new) or **Inactive**
    (terminal state or orphaned). Mirrors `app.js:5550-5627`.
  - Top-level `TabRow` with Active (N) / Inactive (N) tabs; the
    Active count drives the bottom-nav badge.
  - Per-session expandable cards with state pill + alert count +
    session link. Active groups default-expand, inactive
    default-collapse (matches PWA `.alert-session-group` chevron
    behaviour).
  - Per-alert cards with level-colored left border (3dp,
    error/warn/info palette), level badge, timestamp, bold title,
    body preview (500-char truncation). Matches PWA `.alert-card`.
  - Quick-reply dropdown button on the **first** alert of a
    waiting-input session (mirrors `app.js:5573-5580`); tapping
    opens session detail with the composer auto-focused.
  - Per-alert Schedule / Open / ✓ (mark-read) actions.
  - Legacy swipe-left-to-mute gesture retained since it's a useful
    shortcut the PWA lacks.

### Fixed

- **AlertDto was decoding no live fields.** The old DTO used
  `type/severity/message` (non-nullable `type`) but the server
  actually emits `level/title/body`. Every real alert was failing
  decode silently and the UI never showed `/api/alerts` content —
  it was falling back to the session-filter approach. DTO now
  matches the live shape with back-compat for the legacy triple;
  domain `Alert` gains a `title` field distinct from `message`.
- **Settings save + restart — user-reported 2026-04-24.** Any
  change in `ConfigFieldsPanel` auto-saves via `PUT /api/config`,
  but many fields require a daemon restart to take effect
  (TLS, bind, backends, channels). When `server.auto_restart_on_config`
  is off the user had no signal their save wasn't yet active.
  Added an always-visible `RestartNeededBanner` at the top of the
  Settings content area:
  - **Green** note when auto-restart is on ("changes apply
    immediately").
  - **Amber** banner with a prominent **Restart now** button when
    it's off; calls `POST /api/restart`. Post-click status surfaces
    inline ("Restarting daemon…", "Restart requested. Give the
    daemon 5–10 s to come back.", or error).

### Notes

- No schema migration.
- G10 (New-session tab in bottom nav) dropped from the parity plan
  per user direction 2026-04-24: PWA will migrate to the Android
  FAB + full-screen-create pattern this weekend (upstream
  [dmz006/datawatch#22](https://github.com/dmz006/datawatch/issues/22)).
- G41 (Signal QR device linking) WONTFIX — user direction
  2026-04-24: "already on phone, do not need signal setup; that can
  be a server only function." v0.35.2 release dropped from plan.
- Bidirectional parity rule applied: Android-extra gap flagged
  upstream this release —
  [dmz006/datawatch#21](https://github.com/dmz006/datawatch/issues/21)
  (voice-transcribe UI for PWA).

## [0.34.7] — 2026-04-24 (P1 fix pass — G5 + G8)

### Fixed

- **G8 — Session-detail composer hidden behind soft keyboard.** SDK-35
  edge-to-edge is enforced and the prior `imePadding()` was applied to
  the whole outer Column inside the Scaffold, which double-counted
  insets in some layouts and left the reply text field partially
  occluded. Moved `imePadding()` off the outer Column and onto the
  composer Row directly so **only** the input bar lifts above the
  keyboard (the terminal / chat surface keeps its original bounds —
  matches PWA behaviour). Fixes user report "the window with the text
  should lift up when keyboard pops up so you can read the message as
  it is being typed."
- **G5 — New Session LLM picker stuck on old server's backends.** The
  server dropdown seeded itself from `ActiveServerStore` only when
  `selectedProfileId == null` on first composition. If the user
  flipped the active server elsewhere and returned to New Session,
  the picker kept the previous server's backend list. Effect now
  syncs on every `(profiles, activeId)` change until the user makes
  an explicit pick from the dropdown (tracked via `userPickedServer`
  flag), after which auto-sync is suppressed so their manual choice
  sticks.

### Notes

- No schema migration.
- Per the 2026-04-23 parity inventory — G5 and G8 are the two P1
  items the user flagged as still broken post-v0.34.6. Remaining
  batches v0.34.8 → v0.35.2 cover structural parity gaps (Alerts
  rebuild, drag-drop reorder, Settings dimension sweep, session
  detail polish, optional Signal QR linking). See
  [`docs/plans/audit-2026-04-23/README.md`](docs/plans/audit-2026-04-23/README.md).

## [0.34.6] — 2026-04-23 (P0: session-id contract, delete UI, chat mode)

### Fixed

- **Session mutation endpoints were speaking a different dialect
  than the server.** All of `/api/sessions/kill`, `/state`, `/rename`,
  `/restart`, `/delete` were failing with a silent 404 because mobile
  sent either the wrong JSON key (`session_id`) or the short client
  id, while the server (internal/server/api.go) reads `{"id": fullId}`
  and keys its session store on the prefixed `ring-2db6` form.
  - `RestTransport.killSession` body key fixed: `session_id` → `id`.
  - `StateOverrideDto` SerialName fixed to `id`.
  - Every mutate-session VM call (kill, rename, state override,
    restart, delete) now resolves `session.fullId` (e.g.
    `"ring-2db6"`) before hitting the server. Domain `Session`
    exposes a computed `fullId = "$hostnamePrefix-$id"` helper
    with a falls-back-to-short-id safety for pre-cache calls.
  - Fixes the reported **"/api/sessions/kill 404 session not found"**
    on both the session-detail Stop button and the Sessions-list
    row Stop badge.

### Added

- **Delete-after-kill UI** surfaced on both the session-detail
  infobar and the Sessions-list row (prior release only exposed
  Delete from the row overflow menu, which the user flagged).
  Terminal-state rows now show a `🗑 Delete` OutlinedButton next to
  Restart; the detail-screen infobar shows the same once state is
  Killed / Completed / Error. Both go through a confirmation dialog
  before calling `/api/sessions/delete`. Gated on
  `state.deleteSupported` so older servers don't offer it.
- **Chat-mode session rendering.** Sessions whose `output_mode`
  reports `"chat"` (OpenWebUI, Ollama, any chat-transcript backend)
  now render a real transcript surface instead of a blank terminal.
  - New `SessionEvent.ChatMessage(role, content, streaming)` type
    parsed from WS `chat_message` frames in `EventMapper`.
  - New `ChatTranscriptPanel` composable: user / assistant / system
    bubbles with avatar + role + timestamp header. Assistant
    streaming chunks accumulate into a single live bubble; the
    `streaming=false` finaliser seals it (PWA app.js:562-605
    protocol). Transient system indicators
    ("processing...", "thinking...", "ready...") render as a single
    replaceable dot-row instead of piling up the history.
  - `SessionDetailScreen` branches on `session.isChatMode` ahead of
    the existing user-preference Terminal/Chat view toggle — the
    two concepts are distinct (server-side chat mode ≠ user's
    view-toggle choice).
  - SQLDelight migration **4.sqm** adds `session.output_mode` +
    `input_mode` columns so cold-start from cache picks the right
    surface without a round-trip. `SessionDto` + domain `Session`
    carry the new fields through.

### Notes

- Chat history is in-memory only per the PWA's design (server
  doesn't broadcast backfill). The `chatMessageBus` on
  `SessionEventRepository` uses `replay = 64` so late subscribers
  (opening the session after the first messages landed) still see
  the recent burst.
- Delete does not pass `delete_data=true` yet; that second-step
  "Delete tracking data on disk too" option from the PWA is a
  follow-up iteration.
- User's new-session LLM picker reloads from the active server
  (v0.34.5 fix) — follow-up test on this build will confirm
  whether that item from the Open-Not-Assessed list fully closes
  or needs a second pass.

## [0.34.5] — 2026-04-22 (LLM card polish + Settings reactivity + button audit)

### Added

- **`claude-code` LLM backend schema**. Full field set the PWA
  exposes: binary path, model, optional api key, max tokens, max
  turns, system prompt, skip-permissions toggle, working
  directory, timeout.
- **Settings cards reload on active-server change**. Before, the
  user had to bounce out of Settings (e.g. via Sessions tab) and
  back before new-server config appeared. Now `LlmConfigCard`,
  `ChannelsCard`, and the shared `ConfigFieldsPanel` all observe
  `ActiveServerStore.observe()` as a StateFlow, re-fetch on
  active-id change, and preserve the user's tab + scroll
  position. `ConfigFieldsPanel` clears its local value cache on
  server flip so the old server's numbers don't ghost-display
  while the new config is in flight.

### Changed

- **LLM Configuration card — state-aware per-row actions.**
  - When a backend is **configured** (any non-enabled key in
    `backends.<name>.*` has a value): row shows a compact
    enable/disable **Switch** + a pencil edit **IconButton**.
  - When **not configured**: row shows a single **Configure**
    outlined button.
  - The "Make default" action was removed — `session.llm_backend`
    on the General tab is the single place to set default (user
    feedback 2026-04-22, "that is on general tab").
- **LLM card includes `KnownBackends` merged into the
  server-reported list** so claude-code (and any other configured
  backend not yet registered with `/api/backends`) is always
  reachable for edit.
- **New Session backend picker filters to enabled-on-server.**
  Cross-references `/api/backends` names with
  `backends.<name>.enabled` from `/api/config`. Keeps the server's
  default active backend in the list even if its `enabled` flag
  isn't set, so the picker never ends up empty when a default
  exists.
- **New Session: Browse… and Restart are proper OutlinedButtons**
  instead of flat TextButton "text links" (user feedback).
- **Sessions row action buttons** (Stop / Commands / Restart) +
  the offline-sheet Retry are now OutlinedButtons.

### Removed

- **`MessagingBackendsCard`** (the Comms-tab "Messaging Backends"
  card). Redundant with Communication Configuration — user
  feedback 2026-04-22, "isn't needed anymore". Per-type editing
  for backends that don't have a channel instance yet is a
  follow-on via the existing Configure dialog once the instance
  row is created.

### Notes

- Dialog-slot TextButtons (confirm / dismiss inside AlertDialog)
  stay as TextButton per Material guidance — only in-content
  primary actions were converted to OutlinedButton this round.
- PWA-parity audit of every LLM / Comms field is a living task.
  Report any mismatched dot-path keys against your server's
  `config.yaml` and I'll update `LlmBackendSchemas.kt` /
  `ChannelBackendSchemas.kt` — each field is one line.

## [0.34.4] — 2026-04-22 (schema-driven LLM + Comms config dialogs)

### Added

- **Per-backend LLM config schemas** (`LlmBackendSchemas`). Each
  known backend type (ollama, openai, anthropic, groq, openrouter,
  gemini, xai, openwebui, opencode) has its own field list —
  enable toggle + model + api_key plus the type-specific knobs
  (host, base_url, temperature, max_tokens, system_prompt,
  context_window, timeout, site_url, app_name, …). Unknown
  backends fall back to the legacy three-field shape.
- **Schema-driven `BackendConfigDialog`** now renders through
  `ConfigFieldsPanel` so every LLM field prefills from
  `/api/config` and auto-saves on change via the flat dot-path
  patch — same mechanic as the General / Session / Monitor
  config panels. No more "only 3 fields, nothing prefills".
- **Per-channel `ChannelConfigDialog`** with schema per channel
  type (`signal`, `telegram`, `discord`, `slack`, `matrix`,
  `ntfy`, `email`, `twilio`, `webhook`, `github_webhook`). Each
  row in the Comms → Messaging Channels card now has a
  **Configure** button that opens the dialog with every field
  for that type, prefilled from the server, auto-saving.
- **`MessagingBackendsCard`** (new). Lists every known channel
  backend type and opens a global-per-type editor at
  `messaging.<type>.*`. Fixes the 2026-04-22 report —
  "signal is configured on one server but it is not in the
  list": `/api/channels` only returns channel *instances*, so a
  standalone global backend config never appeared. This card
  surfaces the type even without an instance row so users can
  configure signal (or any other backend) directly.

### Changed

- **Channels rows now expose Configure + Test + Toggle + Delete**,
  in that order — matches PWA's right-hand action cluster.
- **Field rendering reuses `ConfigFieldsPanel`** across General,
  LLM, and Comms tabs; adding a new field for any of them now
  takes one line in the corresponding `*Schemas.kt` object.

### Notes

- The dot-path keys in the new schemas reflect the naming
  conventions the parent daemon's `applyConfigPatch` uses
  (`backends.<name>.*` for LLM, `messaging.<type>.*` for global
  channel config, `channels.<id>.*` for per-instance). Where a
  field doesn't match the parent exactly, the save is a no-op
  rather than a crash — no data is lost.

## [0.34.3] — 2026-04-22 (LLM config actions)

### Added

- **LLM Configuration card now has per-backend actions.** Each row
  in Settings → LLM → LLM Configuration now shows a **Configure**
  button (opens the existing `BackendConfigDialog` to edit model /
  base URL / api key) and a **Make default** button on any
  non-default backend (writes `session.llm_backend` via the flat
  dot-path config patch so the picked backend becomes the default
  for every new session). Previously the card was read-only, which
  made the LLM tab feel featureless (user feedback 2026-04-22).

### Notes

- The New Session backend picker already filtered by active server
  via the per-profile `listBackends()` call — no change needed
  there. If the picker is showing stale data, it's the server's
  `/api/backends` response rather than the client.
- Voice debugging work deferred to a later session per user
  request; `SessionDetailScreen` mic + routing fix from 0.34.2
  remains the active baseline.

## [0.34.2] — 2026-04-22 (Monitor widget expansion + voice/widget fixes)

### Fixed

- **Voice transcription routed to wrong server.** The voice composer
  picked the first-enabled profile from the DB instead of honouring
  `ActiveServerStore`, so users with >1 configured server had their
  audio land on whichever server the DB returned first (reported
  2026-04-22 — "is it being processed by another server?"). Voice
  now uses the active profile and falls back to first-enabled only
  when there's no active selection. Transcribe failures now toast
  with the server name + error instead of silently dropping.
- **Microphone silently did nothing.** The composer's mic button
  launched `MediaRecorder` without first requesting `RECORD_AUDIO`,
  so `start()` threw and `runCatching` swallowed it. Now checks the
  runtime grant, requests via `ActivityResultContracts.RequestPermission`
  on first use, and toasts on denial or recorder failure.
- **Session Statistics card showed zero.** Many server builds either
  don't populate `sessions_total` in `/api/stats` or lag by a poll
  cycle. `StatsViewModel` now calls `listSessions()` alongside
  `stats()` and overrides the stats-reported counts with the live
  list count — matches the PWA Monitor's behaviour.
- **`SessionsWidget` ignored `ActiveServerStore`.** Silently pinned
  to the first enabled profile; now resolves active ? first-enabled
  like `MonitorWidget`.
- **Widgets flashed on refresh.** `onUpdate` pre-cleared the widget
  to a "loading…" state before the async refresh landed. Removed the
  pre-clear so the previous snapshot stays visible until new numbers
  arrive.
- **Widgets waited for 30-minute `AppWidgetManager` cadence.** Added
  `ServiceLocator.refreshHomeWidgets()` called from
  `StatsViewModel` + `SessionsViewModel` after each successful poll,
  so widgets update live while the app is open.

### Added

- **Monitor widget: Swap / Network / GPU temp / Daemon rows.**
  User request 2026-04-22 — the widget should show everything
  real-time-worthy. Added swap bar (conditional on `swap_total >
  0`), network row (`↓rx  ↑tx` cumulative bytes, label reflects
  `ebpf_active` vs system fallback), GPU temp folded into the GPU
  value text, and a daemon footer (`RSS · goroutines · fds`).
  Widget min-height bumped to 3×4 cells.

### Notes

- `RECORD_AUDIO` permission is declared in the manifest; if you
  had a previous build installed, the runtime grant carries over.
  Fresh installs will prompt on first mic tap.
- When upgrading between debug builds, always use `adb install
  -r -d` — `adb uninstall` wipes the SQLCipher DB and Android
  Keystore key; configured servers become unrecoverable.

## [0.34.0] — 2026-04-22 (glance surfaces — widgets + Wear tiles + multi-server)

Minor bump per SemVer: new user-facing surfaces (second home-screen
widget, second Wear tile) plus multi-server tap-to-cycle on both
widgets. Still pre-1.0; test track.

### Added

- **Home-screen Monitor widget.** New `MonitorWidget` +
  `widget_monitor.xml` — shows CPU load (vs cores), memory used
  over total, and a one-line session count for the active server.
  Uses `ProgressBar` for inline bars with the existing datawatch
  widget palette. Refreshes on the 30 min `AppWidgetManager`
  cadence; tap opens MainActivity; tap the server-name label
  cycles to the next enabled profile.
- **`SessionsWidget` tap-to-cycle.** Same affordance as
  MonitorWidget — tap the profile label → advance
  `ActiveServerStore` to the next enabled profile. Both widgets
  share `WidgetActions.cycleActiveServer` and refresh each other
  when one cycles, so any Monitor + Sessions pair on the same
  home screen stays in sync.
- **Wear Monitor tile (`MonitorTileService`).** Reads
  `/datawatch/stats` DataItem published by the phone's
  `WearSyncService`. Renders CPU (load/cores), memory pct,
  sessions total + waiting, uptime. Colour thresholds mirror the
  Monitor card (warn ≥70 %, error ≥90 %). Tap → opens the Wear
  companion's Server picker / Sessions pages.
- **Wear Sessions tile wired up (real data).** The pre-existing
  `SessionsTileService` was still the Phase-1 placeholder
  rendering zeros — now reads `/datawatch/counts` from the Data
  Layer. Restyled to the datawatch palette (teal accent / amber
  warning) instead of the legacy purple.
- **`docs/plans/2026-04-22-wear-auto-roadmap.md`.** New planning
  doc enumerating every reasonable Wear + Auto follow-on: 10
  Wear items (complications, ongoing notifications, quick-reply
  buttons, voice, pane-capture image, watch-face complications,
  rotary input, waiting-list tile, etc), 10 Auto items (voice
  reply, TTS incoming, notification deep-link, multi-server
  overview, saved commands in ActionStrip, map template, etc) +
  5 phone widget / quick-tile extensions. Ordered by impact × cost.

### Changed

- **Wear and Auto palette documentation.** Added a feedback
  memory so future Wear + Auto work stays on-brand (dark surface
  + teal / amber accents); stock Material is explicitly out.

### Notes

- `com.dmzs.datawatchclient.dev.debug` is an old sideload from
  before the package-id cleanup; the current debug package is
  `com.dmzs.datawatchclient.debug`. Uninstall the stale one if
  it shows up alongside the new build.

## [0.33.25] — 2026-04-22 (Monitor cards + Wear/Auto feature parity B30/B32/B33)

### Fixed

- **Monitor tab missing system cards + wrong Sessions layout (user 2026-04-22).**
  Rewrote `StatsScreen`:
  - New **System Statistics** card mirrors PWA's `renderStatsData` reads —
    CPU Load (`cpu_load_avg_1 / cpu_cores`), Memory (`mem_used / mem_total`),
    Disk (`disk_used / disk_total`), Swap (conditional), GPU util + temp,
    GPU VRAM. Uses bars with datawatch pct-threshold colours.
  - **Session Statistics** card replaces the flat 3-count row with a circle
    graph showing `sessionsTotal / session.max_sessions` (ring colour
    warns at 70 %, errors at 90 %) plus running / waiting / idle pills.
    `StatsViewModel` lazily reads `session.max_sessions` from
    `/api/config` to populate the denominator.
  - **Server Info** card drops the LLM-backend row (a fleet can run
    several backends; per-session badge + Backend Health card are the
    right surfaces) and adds live CPU and Memory rows for real-time
    display.
  - **Ollama Server** card only renders when the server reports
    `available = true`; previously showed an "offline" card on every
    server that merely listed Ollama as a candidate backend.

### Added

- **B30 Wear + Auto multi-server picker.** `ActiveServerStore` moved
  from `composeApp` to `shared/androidMain` so both surfaces can bind
  to the same SharedPreferences. Auto's Monitor screen ActionStrip
  gains a **Server** button opening `AutoServerPickerScreen` — tap a
  profile → writes through `ActiveServerStore`, phone's Sessions tab
  reflects it next observe tick. Wear adds a dedicated pager page
  with the enabled profile list; tap sends `/datawatch/setActive`
  over MessageClient, phone's `WearSyncService` receives it and
  flips the same store.
- **B32 Wear + Auto monitoring tab.** Auto's root screen is now
  `AutoMonitorScreen` (default per user request 2026-04-22) with
  CPU / memory / disk / VRAM / sessions / uptime rows. Wear's first
  pager page is Monitor, reading a new `/datawatch/stats` DataItem
  the phone republishes every 15 s with a light stats snapshot. The
  phone's `WearSyncService` now owns three DataItem paths (counts,
  profiles, stats) and a MessageClient listener for setActive.
- **B33 Wear + Auto About screen.** `AutoAboutScreen` shows the
  shared `Version.VERSION` + build + surface label. Wear gains an
  About page (page 4 in the pager) reading the same shared object.
  Both are styled with the datawatch dark palette + teal / amber
  accents instead of stock Material defaults (user feedback —
  "make wear and auto stylish, in style with datawatch").

### Changed

- **Auto entry screen is Monitor, not Sessions.** Per user direction
  2026-04-22, drivers see the fleet health first; Sessions becomes
  an ActionStrip-reachable secondary screen.
- **B31 (Wear + Auto sessions w/ snapshot + voice) on hold.** Auto
  already ships `AutoSummaryScreen` → `WaitingSessionsScreen` →
  `SessionReplyScreen` with Yes / No / Continue / Stop quick-reply
  flow; user evaluating before committing to watch snapshot + voice
  rollout.

## [0.33.11] — 2026-04-22 (Sprint FF — upstream fixes integrated)

Both outstanding upstream issues closed. Mobile wiring landed.

### Added

- **Channel create / delete (S9 + dmz006/datawatch#18).**
  ChannelsCard now has a + Add button that opens an
  AddChannelDialog (type dropdown over the 10 known backends:
  signal / telegram / discord / slack / matrix / ntfy / email /
  twilio / webhook / github_webhook; channel-id text; enabled
  toggle). Confirms via `POST /api/channels` which returns the
  created object. Each existing row picks up a Delete icon that
  fires `DELETE /api/channels/{id}`. Backend-specific config
  still flows through the existing BackendConfigDialog after
  create, matching PWA's two-step UX.

  Transport: new `createChannel(type, id, enabled, config?)`
  and `deleteChannel(channelId)` on `TransportClient` +
  `RestTransport`.

### Changed

- **Autonomous section schema (dmz006/datawatch#19).** Server
  added case branches for `autonomous.decomposition_effort`,
  `autonomous.verification_effort`, `autonomous.stale_task_seconds`
  alongside the existing autonomous/plugins/orchestrator keys.
  Mobile schema updated to expose all three new fields so
  autosave actually persists them. S9 closed: every rendered
  field in General, LLM, Comms, and Monitor writes to a server
  key that `applyConfigPatch` recognises.

### Docs

- parity-plan.md Add/remove-channel row flipped 🚧 → ✅.
- api-parity.md upstream-tracking table updated — both #18 and
  #19 marked closed with the mobile integration version noted.

## [0.33.10] — 2026-04-22 (Sprint EE — on-device triage)

Every fix in the 0.33.2 → 0.33.10 patch stream is ground-truthed
against the parent `dmz006/datawatch` source, verified on a live
Galaxy S24 Ultra + Galaxy Watch 7 paired with a v4.0.7 daemon.
Full punch list at
[dmz006/datawatch-app#1](https://github.com/dmz006/datawatch-app/issues/1).

### Fixed — Settings (S1–S9)

- **S7 Save doesn't persist.** `PUT /api/config` now sends a flat
  dot-path patch (`{"ntfy.enabled":true}`), matching the server's
  `applyConfigPatch` switch-case contract. Previously sent the
  entire nested tree (`{"ntfy":{"enabled":true}}`) which the
  server silently dropped for every field. Three call sites:
  ConfigFieldsPanel, DetectionFiltersCard, BackendConfigDialog.
- **S4 Autosave.** Save button removed from ConfigFieldsPanel.
  500 ms debounced autosave keyed on a derived diff of current
  vs loaded values. "Saving…" label appears while in-flight.
- **S5 Tab order.** Monitor → General → Comms → LLM → About,
  default Monitor, matching PWA `app.js:3089`.
- **S6 Monitor cards.** Memory Browser + Scheduled Events moved
  from General to Monitor to match PWA `data-group="monitor"`.
- **S8 LLM picker move.** Active-LLM radio list deleted from the
  LLM tab; picker is now `session.llm_backend` LlmSelect on
  General → Datawatch. LLM tab hosts per-backend config only.
- **S3 + S1 + S2 Settings density.** Nested MaterialTheme scales
  typography down (bodyLarge 16→14, bodyMedium 14→13, titleMedium
  16→14) only within the Settings surface.
  `CompositionLocalProvider(LocalTextStyle provides 13sp)` drops
  OutlinedTextField / OutlinedButton default text to 13sp without
  touching individual call sites. ConfigFieldsPanel rows use
  shared `INPUT_WIDTH = 160dp` + `ROW_PADDING` constants so every
  label-input row aligns.
- **S9 partial.** `autonomous.*`, `plugins.*`, `orchestrator.*`
  fields render but don't persist — server's `applyConfigPatch`
  doesn't include those in its switch. Upstream limitation
  (PWA has the same behaviour per `app.js:3620` comment).
- Transport `request {}` now catches
  `kotlinx.serialization.SerializationException` separately —
  previously bucketed under the generic `Throwable → Unreachable`
  branch, which misleadingly flipped the reachability dot red
  when the server had actually answered fine.

### Fixed — Transport (LLM + Comms tabs)

- `listBackends` accepts both shipped shapes: `{llm:[{name,...}]}`
  (current server) and `{llm:[String]}` (older). Extracts names
  either way.
- `listChannels` accepts `{channels:[...]}` envelope from current
  server plus the older bare-array shape.

### Fixed — Session detail (T1–T3)

- **T3 Single display source.** Raw-output fallback deleted. Only
  `pane_capture` ever writes to xterm. Matches parent
  `dmz006/datawatch@0393e262` which killed the legacy path as
  "broken for TUIs".
- **T2 Resize-race on re-entry.** TerminalController now stashes
  `pendingMinCols/Rows` + `pendingFrozen` and replays from
  `onPageFinished`. Prevents claude-code's 120×40 enforcement
  from dropping on the second session open.
- **T1 Font size 9 → 11.** PWA's 9px is a desktop default; 11 is
  the mobile-readable floor. User toolbar A+/A- still persists.
- `SessionEventRepository` no longer silently drops
  `PaneCapture`. Live captures flow through an in-memory
  `MutableStateFlow<PaneCapture?>` per session id merged into
  `observe()`.
- `LaunchedEffect` keys on the latest pane-capture timestamp so
  replacement captures retrigger render.
- xterm CSS mirrors PWA `.output-area`: `overflow-x:auto`,
  `overflow-y:hidden` on container, `width:fit-content;
  min-width:100%` on `.xterm`, scrollbar hidden,
  `box-sizing:content-box` reset per xterm.js #1283 / parent
  `a129d031`. Viewport gets `touch-action:pan-x` so swipe-up
  can't scroll a non-existent scrollback unless tmux scroll mode
  is entered.

### Fixed — Session detail header

- TopAppBar title column was squeezed to ~1 char width by five
  actions (StatePill + Stop + Timeline + Mute + More), causing
  session id + backend chip to render one character per line.
  Timeline + Mute moved into the overflow; title Texts get
  `softWrap=false, overflow=Ellipsis`.

### Fixed — About crash

- McpToolsCard nested a `verticalScroll` Column inside the outer
  Settings `verticalScroll` — Compose throws on infinite-height
  measure. Inner scroll removed; outer Settings Column already
  scrolls.

### Fixed — Wear (W1)

- Wear module was missing launcher icon resources entirely, so
  Galaxy Watch omitted the app from the drawer. Copied
  composeApp's adaptive-icon XMLs into `wear/src/main/res/`.
  Both `<application>` and `<activity>` now declare
  `android:icon/roundIcon`. Monochrome layer dropped from Wear
  (references `?attr/colorControlNormal` which doesn't resolve
  under `Theme.DeviceDefault`).

### Fixed — Android Auto (A1)

- Added `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission required
  by Android 14+ for any service with
  `foregroundServiceType="connectedDevice"` — the CarAppService.
  Without it the Auto host's `startForegroundService()` for
  `DatawatchMessagingService` throws `SecurityException` on
  modern devices and the app never surfaces on the head unit.
  Verified in APK via `aapt2 dump xmltree`.

### Versioning

| Step | Subject |
|-----|---------|
| 0.33.2 | About crash fix (McpToolsCard nested scroll) |
| 0.33.3 | DTO shape fix (LLM, Comms) + error classification |
| 0.33.4 | pane_capture live bus + session-detail header density |
| 0.33.5 | xterm CSS + 9px default + box-sizing reset |
| 0.33.6 | flat dot-path patch + debounced autosave (S7, S4) |
| 0.33.7 | Settings tab order + card moves + typography (S5/6/8/3) |
| 0.33.8 | xterm T1/T2/T3 + Wear W1 |
| 0.33.9 | Auto A1 — FOREGROUND_SERVICE_CONNECTED_DEVICE |
| 0.33.10 | Settings LocalTextStyle polish + dead-code cleanup |

## [0.33.1] — 2026-04-22 (Sprint DD — full docs refresh)

### Changed

- **Eleven docs refreshed to v0.33 state** — every file under `docs/`
  that had been last-touched at v0.10.0–v0.14.0 now matches shipped
  reality:
  - `usage.md` — rewritten screen-by-screen for v0.33 (new Settings
    sections, Quick Commands sheet, chat bubbles, session reorder,
    recent-sessions grid, CertInstallCard, McpToolsCard,
    DetectionFiltersCard, KindProfilesCard).
  - `config-reference.md` — completed per AGENT.md Configuration
    Accessibility Rule; every user-settable value enumerated with UI
    path, type, default, persistence tier, server-echo behaviour.
  - `parity-status.md` — redirected to `parity-plan.md` as the single
    authoritative matrix (eliminates two-doc drift pattern that
    caused past staleness).
  - `architecture.md` — v0.33 status note, Auto bundling section,
    RelayComponent marked shipped.
  - `api-parity.md` — ~20 endpoint rows added (rename / restart /
    delete / reorder / timeline / logs / interfaces / restart /
    update / backends.active / ollama / openwebui / memory / channels
    / channel-send / cert / info / files / output / profiles /
    mcp-docs / federation), `POST /api/config` flipped to ✅
    structured-fields (v0.20 / v0.21 / v0.32), upstream-tracking
    section reduced to only the single still-open item (#18).
  - `data-flow.md` — four new sequence diagrams
    (profile create/edit, detection filters read+patch, session
    reorder save, MCP tools list).
  - `ux-navigation.md` — Settings section replaced with the current
    nine subsections (General / LLM / Memory / Comms / Profiles /
    Detection / Monitor / Operations / About).
  - `ux-session-detail.md` — chat bubble rendering block, Quick
    Commands sheet block, inline quick-reply buttons, session backlog
    grid.
  - `ux-voice.md` — v0.33 status note + Quick Commands arrow-key
    clarification.
  - `wear-os.md` — v0.33 bundling note (Wear has always been bundled
    via phone AAB; contrast with the Auto v0.33 fix).
  - `sprint-plan.md` — v0.11–v0.33 sprint summary table, v1.0.0
    parity roadmap (single remaining upstream blocker: #18).
- **`parity-plan.md` — Pagination row flipped 🚧 → ✅** (active+recent
  partition + Show History is parity-equivalent to PWA's pager; no
  separate "load more" needed). **eBPF viewer row** clarified from
  🚧 to ⏳ (post-1.0.0 per ADR-0019).

### Verified

- Built `publicTrack-debug` APK (93.7 MB,
  `composeApp-publicTrack-debug.apk`). Confirmed via `aapt2 dump
  xmltree`: `DatawatchMessagingService` + `CarAppService`
  intent-filter + `androidx.car.app.minCarApiLevel=1` +
  `CarAppPermissionActivity` + `CarAppNotificationBroadcastReceiver`
  all present in the shipped manifest. Ready for in-car testing.

## [0.33.0] — 2026-04-22 (Sprint CC — Auto actually ships in the APK + docs refresh)

### Fixed

- **`:auto` module is now bundled into the composeApp APK.**
  Previously the auto library built cleanly but wasn't a
  dependency of `:composeApp`, so the `CarAppService` never
  reached the APK and Android Auto never saw the app. Added
  `implementation(project(":auto"))` with
  `missingDimensionStrategy("surface", ...)` to bridge the two
  modules' flavor dimensions. AAPT dump confirms the
  CarAppService intent-filter + `automotive_app_desc` metadata
  + `minCarApiLevel` metadata all merge into the final APK.

### Added

- **`androidx.car.app.minCarApiLevel = 1`** manifest metadata in
  `auto/publicMain/AndroidManifest.xml`. Required by modern Auto
  hosts to negotiate the template protocol.
- **`docs/android-auto.md` rewritten** for current v0.32+ reality
  — three-screen nav graph, AutoServiceLocator DI, DHU /
  in-car test instructions, known gaps (TTS announcement,
  voice-reply, allowlist Play-submit verification).
- **`docs/README.md` refreshed** — upstream-issue section
  updated to reflect 18 closed + 1 open-meta state.

### Parity-plan

- Three rows flipped from stale ❌/🚧 to ✅ (Pick model variant,
  Pick profile, Voice-to-new-session) — all shipped in earlier
  sprints, the labels were just stale.

## [0.32.0] — 2026-04-22 (Sprint BB — close the PWA-parity gap list)

Closes every concrete PWA-parity gap the 2026-04-21 honest-audit
surfaced.

### Added

- **Detection filters card** under Settings → LLM. Mirrors PWA
  `loadDetectionFilters` — four pattern lists (prompt /
  completion / rate-limit / input-needed) plus two timing
  fields (prompt_debounce, notify_cooldown). Distinct from
  Output Filters (`/api/filters` CRUD) — these live in
  `config.detection.*_patterns`.
- **Arrow keys + Tab + PageUp/Down in the Quick Commands sheet.**
  Seven new chips send the raw ANSI control sequences
  (`\x1B[A`/`B`/`C`/`D`, `\x1B[5~`/`[6~`, `\t`) via the existing
  reply path. Matches PWA f00f534.
- **MCP tools in-app viewer** under Settings → About. Reads
  `/api/mcp/docs` and renders tool catalogues whether the server
  emits a flat array or grouped categories.
- **CA cert install card** under Settings → Comms. Download
  button + system-security-settings shortcut + expandable
  Android and iPhone step-by-step instructions verbatim from
  the PWA install block.
- **Project + Cluster profile create + edit dialogs**. Minimal
  fields (name + description) exposed; nested blocks
  (image_pair / git / memory / kubernetes context) preserved
  from server response so mobile round-trips without nuking the
  schema. Full deep edits still happen on the PWA.
- **Update daemon progress bar** — indeterminate
  `LinearProgressIndicator` shows while the update call is in
  flight, matching PWA v4.0.6's "Downloading update…" strip.
- **Chat-mode bubble rendering** — session-detail chat tab now
  renders events as avatar + role + body bubbles with role-
  tinted surface colours instead of plain flat rows. Mirrors
  PWA `renderChatBubble` styling (markdown rendering is a
  post-1.0 polish).
- **Session backlog grid** at the bottom of the New Session
  form — up to 20 most-recent done sessions with per-row
  Restart button. Matches PWA `renderSessionBacklog`.

### Changed

- **Auto HostValidator is now strict on release builds** — debug
  APKs keep `ALLOW_ALL_HOSTS_VALIDATOR` so the Desktop Head Unit
  simulator binds freely; release builds consult a new
  `R.array.hosts_allowlist` with the Google-published Auto /
  DHU / emulator signing-cert pairs (ADR-0031 compliance).

### Transport

- `putKindProfile(kind, name, body)`, `fetchMcpDocs()`.

## [0.31.0] — 2026-04-21 (Sprint AA — session reorder mode + Custom sort)

### Added

- **Reorder mode** on the Sessions tab. A new ⇅ icon in the top
  app bar toggles an arrangement mode where each row swaps its
  overflow menu for ↑ / ↓ buttons. Tap moves the session one
  slot. Entering reorder mode auto-sets the Sort dropdown to
  `Custom` (new value) so what the user arranged is what gets
  shown.
- **`SortOrder.Custom`** — fourth sort option alongside Recent /
  Started / Name. Uses the user's reorder-mode arrangement; ids
  not in the custom list fall to the tail sorted by
  last-activity.

### Notes

- Compose doesn't ship a drag-reorder LazyColumn; the up/down
  arrow pattern avoids the pointerInput + animated-offset
  complexity of a hand-rolled drag implementation while still
  letting users arrange the list. Custom ordering is
  in-memory — cross-restart persistence is a post-1.0 polish.

## [0.30.0] — 2026-04-21 (Sprint Z — Android Auto live data)

### Added

- **`AutoServiceLocator`** — mirrors phone-side `ServiceLocator`
  but lives in `:auto`. Reuses `:shared`'s `KeystoreManager` +
  `DatabaseFactory` + `ServerProfileRepository` +
  `SessionRepository` + `RestTransport` + `createHttpClient()`
  so the Auto module reads the same SQLCipher DB and hits the
  same server without touching `:composeApp` (library-to-app
  dep rule). Initialised from
  `DatawatchMessagingService.onCreate()`.
- **Auto screens now show live session counts** — Summary polls
  `TransportClient.listSessions()` every 15 s for the first
  enabled profile; Waiting list filters to `waiting_input`;
  Reply screen POSTs to `/api/sessions/reply`. All three screens
  show real data rather than placeholder.

### Play-compliance

- Templates stay ListTemplate / MessageTemplate; no free-form UI
  per ADR-0031. Quick-reply strip (Yes / No / Continue / Stop)
  is the sole action surface.

## [0.29.0] — 2026-04-21 (Sprint Y — About API links + Notifications card + cleanup)

### Added

- **API links card** under Settings → About. Four clickable rows
  open the active server's Swagger UI, OpenAPI YAML, MCP tools
  catalogue, and architecture diagrams in the system browser.
  Mirrors PWA `api` section.
- **Notifications card** under Settings → General. Shows whether
  the app currently holds POST_NOTIFICATIONS permission and
  opens the system app-notification settings for adjustments.
  Per-channel importance + sound stays in system UI (Android
  native pattern). Mirrors PWA `gc_notifs`.

### Removed

- Dead `BehaviourPreferencesCard.kt` — superseded by
  `ConfigFieldsPanel(Session)` in v0.26.0. Was writing to
  invented keys never honoured server-side; now fully out of
  tree.

## [0.28.0] — 2026-04-21 (Sprint X — Project + Cluster profile CRUD + Proxy Resilience)

### Added

- **Project Profiles + Cluster Profiles cards** under Settings →
  General. Shared `KindProfilesCard` renderer keyed on
  `kind ∈ {project, cluster}`; each row shows name + summary
  (image_pair/git for project, k8s kind/context/namespace for
  cluster), with per-row **Smoke** test + **Delete** actions.
  Create / edit stays on the PWA per ADR-0019 (nested
  image_pair / git / memory / kubernetes schema is too rich
  for a mobile dialog).
- **Proxy Resilience** section under Settings → Comms via the
  generic `ConfigFieldsPanel`. Exposes the six `proxy.*` fields
  PWA `loadProxySettings` shows: enabled, health_interval,
  request_timeout, offline_queue_size, circuit_breaker_threshold,
  circuit_breaker_reset.

### Transport

- `listKindProfiles(kind)`, `deleteKindProfile(kind, name)`,
  `smokeKindProfile(kind, name)`.

## [0.27.0] — 2026-04-21 (Sprint W — filters CRUD + New Session form fields)

### Added

- **Output filters card under Settings → LLM.** Pattern (regex)
  + action (`send_input` / `alert` / `schedule` /
  `detect_prompt`) + value (optional) per row. Per-row enable
  switch flips the rule; red ✕ deletes. Add-filter form at the
  bottom. Matches PWA `loadFilters` / `createFilter` /
  `toggleFilter` / `deleteFilter` in app.js lines 6284–6396.
- **New Session form PWA-parity fields.** Four additions on top
  of the existing task / profile / directory inputs:
  - `Session name` — distinct from the task prompt (sent as
    `name` on POST /api/sessions/start).
  - `Resume previous` dropdown — up to 30 most-recent done
    sessions; picking sets `resume_id` so the server warm-
    restarts that session's state instead of starting fresh.
  - `Auto git init` toggle (default off).
  - `Auto git commit` toggle (default on, matches PWA default).

### Transport

- `listFilters()`, `createFilter(pattern, action, value, enabled)`,
  `updateFilter(id, pattern, action, value, enabled)` (nullable
  args for partial updates), `deleteFilter(id)`.
- `startSession` gains `name`, `backend`, `resumeId`,
  `autoGitInit`, `autoGitCommit` optional parameters. DTO
  extended to match PWA payload.

## [0.26.0] — 2026-04-21 (Sprint V — ConfigFieldsPanel + 12 PWA Settings sections)

Ports the biggest PWA-parity gap flagged in the Settings audit:
mobile was missing ~25 config sections / ~70 editable fields that
the PWA exposes.

### Added

- **Generic `ConfigFieldsPanel` renderer.** One composable that
  reads a `List<ConfigField>` schema (Toggle / NumberField /
  TextField / Select / InterfaceSelect / LlmSelect), fetches
  `/api/config`, renders per-field native widgets, and writes
  a merged document back via `PUT /api/config` on Save. Deep-
  merges dotted keys (e.g. `session.log_level`) so siblings at
  any nesting depth are preserved. Empty-preserving password
  fields protect server-side secrets from accidental nuke.
- **`ConfigFieldSchemas.kt`** — schemas for **12 PWA sections**
  byte-for-byte ported from `app.js` COMMS_CONFIG_FIELDS,
  LLM_CONFIG_FIELDS, GENERAL_CONFIG_FIELDS:
  - **Comms:** CommsAuth, WebServer, McpServer
  - **LLM:** Memory (17 fields), LlmRtk (7 fields)
  - **General:** Datawatch, AutoUpdate, Session (18 fields),
    Rtk, Pipelines, Autonomous (7 fields), Plugins,
    Orchestrator, Whisper
- Wired into Settings tabs — Comms tab gets three new cards,
  LLM tab gets two, General tab gets nine. Adding a field
  server-side now means adding one line to
  `ConfigFieldSchemas.kt`; no new composable required.

### Superseded

- `BehaviourPreferencesCard` — replaced by
  `ConfigFieldsPanel(Session)` which uses the correct PWA keys
  (`session.max_sessions` / `session.tail_lines` /
  `server.recent_session_minutes` instead of the invented
  `recent_window_minutes` / `max_concurrent` / `scrollback_lines`
  that weren't being honoured server-side). The old card remains
  in-tree as dead code until a cleanup pass.

### Skipped (non-field sections)

- `proxy` Proxy Resilience — free-form server-config panel; the
  PWA renders it via a custom `proxySettings` endpoint with
  bespoke logic. Needs its own transport + card.
- `gc_projectprofiles` / `gc_clusterprofiles` — CRUD lists,
  not field-based. Follow-up sprint.
- `gc_notifs` — browser notification permission request; Android
  uses system settings.
- `detection` / `filters` — rule lists with regex / action /
  value. Follow-up sprint.

## [0.25.0] — 2026-04-21 (Sprint U — terminal extras + kill-orphans + memory test + Auto scaffolding)

### Added

- **Terminal scroll-mode toggle.** Two new buttons on the terminal
  toolbar: enter tmux copy-mode (`⇈`) sends
  `command:tmux-copy-mode <sid>` over WS; exit (`⇊`) sends
  `sendkey <sid>: Escape`. Matches PWA v2.3.2 scroll-mode flow.
- **Terminal auto-fit-to-width** (`⇅`). Shrinks xterm font size
  iteratively until the viewport fits the container horizontally,
  mirroring PWA `termFitToWidth` (app.js line 1890). Useful when
  `setMinSize(120, 40)` forces a wide terminal.
- **Terminal 5 s watchdog.** If no pane_capture event arrives
  within 5 seconds of a session open, the `resetPaneCaptureSeen`
  marker is cleared and the WebView cleared so whichever frame
  arrives next is treated as a fresh first-frame. Logs under
  `DwTerm` tag.
- **Kill-orphans card** under Settings → Monitor. Red "Kill
  orphans" button opens a confirm dialog, POSTs
  `/api/stats/kill-orphans`, reports `killed: N`.
- **Memory test button** on the Memory card. Calls
  `GET /api/memory/test`; green "Memory connection OK." or red
  error message inline.
- **Android Auto screen scaffolding.** Replaced the pre-MVP
  single-screen placeholder with a three-screen nav graph:
  AutoSummaryScreen → WaitingSessionsScreen → SessionReplyScreen.
  Templates wired; data is still placeholder pending a Sprint T
  ServiceLocator migration (auto module can't see composeApp's
  DI today — detailed in `docs/plans/2026-04-21-auto-audit.md`).

### Transport

- `listRemoteServerHealth()`, `killOrphans()`, `memoryTest()`.
- `WsOutbound.sendCommand(sessionId, text)` — generic `command`
  WS frame sender (used by the scroll-mode toolbar and the ESC
  exit).

### Deferred

- **Android Auto live session counts** — requires DI unification.
- **Scroll-mode PageUp / PageDown chip bar** — current sprint has
  toggle + ESC but not the on-screen navigation chips PWA shows.

## [0.24.0] — 2026-04-21 (Sprint S — terminal functional parity: resize_term + throttle + freeze)

Closes the biggest terminal-emulation gap flagged in the
2026-04-21 terminal audit: mobile never told the server its xterm
cols/rows, so claude-code (120-col TUI) rendered broken on phone
widths. Five behavioural fixes this release, matching PWA
accumulated terminal corrections from v0.13.0 through v4.0.7.

### Added

- **Outbound WS channel.** New `WsOutbound` object with
  `sendResizeTerm(sessionId, cols, rows)` and
  `sendCommand(sessionId, text)`. `WebSocketTransport.events()`
  now launches a per-session writer that relays queued frames
  onto the live socket. Previously WS was read-only.
- **resize_term WS frame** on every xterm fit-settle.
  `host.html` safeFit() fires `DwBridge.onResize(cols, rows)` when
  dimensions change; the JS bridge hands off to Kotlin, which
  emits `{type:"resize_term", data:{session_id, cols, rows}}`.
  Server resizes the tmux pane and the next pane_capture arrives
  at the new width.
- **configCols / configRows enforcement.** `TerminalController.
  setMinSize(cols, rows)`; `SessionDetailScreen` reads
  `session.backend` and calls `setMinSize(120, 40)` when it's
  claude-code (per parent v0.14.1). xterm resizes to at least
  that dimension; the container scrolls horizontally when phone
  width is narrower. TUIs now render at their intended widths.
- **30 fps pane_capture throttle.** `dwPaneCapture` in host.html
  skips frames arriving < 33 ms apart — mirrors PWA app.js line
  328 protection against xterm buffer overload when the server
  drains aggressively.
- **Skip DATAWATCH_COMPLETE transitional frames.** host.html
  checks each line for the marker; skipping prevents the
  shell-prompt flash between LLM exit and session-state update.
- **Freeze on terminal state.** `TerminalController.setFrozen`
  called from SessionDetailScreen when session transitions to
  complete/killed/failed. Further pane_captures are ignored so
  the final screenshot persists.

### Docs

- New `docs/plans/2026-04-21-terminal-audit.md` — catalogue of
  22 parent datawatch commits shaping terminal behaviour,
  behavioural lessons from each, side-by-side PWA-vs-mobile
  capability table, and breakdown of remaining gaps (scroll-mode
  + watchdog + auto fit-to-width + send_input WS).
- New `docs/plans/2026-04-21-auto-audit.md` — current Auto module
  state (Sprint 4 placeholder), Play-compliance constraints per
  ADR-0031, minimum-viable scope for testability.
- Expanded `docs/plans/2026-04-21-pwa-audit-sprint.md` (Sprint K
  commit) with full Settings surface catalog — ~25 sections /
  ~70 config fields — and the ConfigFieldsPanel renderer
  strategy that avoids hand-coding each one.

### Remaining terminal gaps (tracked, not shipped this sprint)

- Scroll-mode (tmux copy-mode + PageUp/Down/ESC bar)
- 5-second no-pane_capture watchdog with auto re-subscribe
- Auto fit-to-width (shrink font until no horizontal scroll)
- send_input WS (composer's REST reply is functionally adequate)

## [0.23.0] — 2026-04-21 (Sprint K — terminal palette aligned byte-for-byte to PWA)

### Changed

- **Terminal palette now exactly matches the PWA** (`app.js` line 2010+).
  Background `#0f1117` (charcoal, was pure black), foreground `#e2e8f0`,
  cursor `#a855f7` with accent `#0f1117`, selection
  `rgba(168,85,247,0.3)`. Full 16-colour ANSI palette replaced to
  PWA's tailwind-based mapping (ef4444 red, 10b981 green,
  3b82f6 blue, a855f7 magenta, …). WebView background colour in
  `TerminalView.kt` also flipped to `#0F1117` so the flash during
  WebView reflow doesn't strobe black.

### Docs

- New sprint plan `docs/plans/2026-04-21-pwa-audit-sprint.md`
  enumerating the full PWA API surface, cross-referencing mobile
  coverage, listing the 8 remaining REST gaps + 1 WS gap
  (`resize_term`), and breaking remaining work into sprints K–R
  targeting v1.0.0-rc1. Written at user's directive: "document and
  make a plan so you don't forget."

## [0.22.1] — 2026-04-21 (daemon /api/update wiring + version-drift fix)

### Fixed

- **About card was stuck at v0.12.0** because `Version.kt` in the
  shared module was never bumped across ten release commits since
  v0.12. Gradle's versionCode was advancing correctly (read from
  `gradle.properties`) but the UI read `Version.VERSION` directly.
  Synced both files to v0.22.1 / code 28. Memory rule added so
  future commits bump both.

### Added

- **Daemon update card under Settings → Monitor.** Calls
  `POST /api/update`. Button says "Check for update"; server
  responds with `up_to_date` (green banner with version) or
  installs + restarts automatically. Endpoint is undocumented in
  parent openapi.yaml but shipped — PWA `runUpdate` at app.js
  line 4149 uses the same wire path. Closes the last ❌ row on
  parity-plan.

### Audit

- Closed upstream dmz006/datawatch#17 (update-daemon) and #18
  (add-channel). Both were filed in error: #17's endpoint already
  exists (stale-spec pattern, same as #14/#15), #18's "missing"
  endpoint is intentional upstream design — adds route through
  `PUT /api/config`, which mobile already does via
  BackendConfigDialog. No open mobile-filed issues remain against
  parent.

## [0.22.0] — 2026-04-21 (Sprint J — memory export + input/output mode + ESC/Ctrl-b)

### Added

- **Memory export via SAF.** Settings → General → Memory gets an
  "Export…" button. It fetches `GET /api/memory/export` (raw
  bytes with bearer auth, which ACTION_VIEW couldn't do) and
  launches `CreateDocument` so the user picks the destination;
  bytes are written to the chosen content-URI with a
  timestamped filename.
- **Input mode / Output mode dropdowns** on the Behaviour
  Preferences card. `input_mode` options: tmux / channel /
  none. `output_mode` options: tmux / channel / both / none.
  Blank preserves the server's existing value on save.
- **ESC and Ctrl-b chips in the Quick Commands sheet.** Rather
  than introduce a separate WS command channel for the list
  surface, mobile sends the raw ASCII control bytes (0x1B for
  ESC, 0x02 for Ctrl-b) via the existing `/api/sessions/reply`
  path. TUIs that interpret replies through the tmux pane pick
  them up the same way the PWA's `sendkey` command does.

### Transport

- `memoryExport()` → `Result<ByteArray>` for the SAF download.

### Parity-plan

- 3 more 🚧 rows flip to ✅: memory export, input mode, output
  mode. ESC/Ctrl-b wasn't tracked as its own row but was noted
  as deferred in the quick-commands docstring.

## [0.21.0] — 2026-04-20 (Sprint I — per-backend config editor + final parity close-out)

Closes the remaining structured-write gap on parity-plan.

### Added

- **Configure-backend dialog** on Settings → LLM. Tap "Configure…"
  next to any listed LLM backend to open a structured editor with
  three inputs: model / base_url / api_key. Other fields on the
  backend block are preserved when the dialog is saved (the full
  config document is round-tripped). API key field is empty-
  preserving — leaving it blank keeps the existing stored secret
  rather than nuking it, so users can change just the model
  without re-typing credentials.
- Per-backend config edit completes the round-trip for the v0.13.1
  model picker: mobile now fully owns the "swap Ollama model"
  flow by writing `backends.ollama.model` via `PUT /api/config`.

### Parity-plan

- 18 rows flipped from ❌ / 🚧 to ✅ in this final sweep, covering
  everything shipped across Sprints A–I.
- Four rows remain 🚧: pagination / "load more" (partitioning
  covers the UX); memory export (needs SAF); input/output mode
  fields in preferences (backend-specific; same machinery ready);
  eBPF viewer (ADR-0019 deferred).
- One row remains ❌: `/api/update` — upstream doesn't expose the
  endpoint; will file the issue before 1.0.0.

## [0.20.0] — 2026-04-20 (Sprint H — Behaviour preferences + federation peers)

### Added

- **Behaviour preferences card under Settings → General.** Reads
  `recent_window_minutes`, `max_concurrent`, and
  `scrollback_lines` from `/api/config` and exposes them as integer
  inputs. Save merges the edited values back into the full config
  object and writes via `PUT /api/config`, preserving every other
  field the parent returned (per ADR-0019 — we never touch raw
  YAML, only specific structured fields we understand).
- **Federation peers card under Settings → Comms.** Read-only
  list of remote datawatch servers the active server is
  federated with, from `GET /api/servers`. Shows name / base URL
  / enabled flag so users can verify the parent's federation
  config without opening the web UI.

### Transport

- `listRemoteServers()` → raw `List<JsonObject>` so the UI can
  pick fields as the schema evolves.

## [0.19.0] — 2026-04-20 (Sprint G — Alerts schedule + foreground suppression)

### Added

- **"Schedule reply…" action on Alert rows.** Each row now has an
  inline button that opens the existing ScheduleDialog, seeded
  with the session's last prompt text so users can answer a
  blocking question on a cron rather than dropping everything.
- **Foreground-session suppression for input-needed
  notifications.** A new `ForegroundSessionTracker` records which
  session is visible via `DisposableEffect` on the detail screen
  and watches process lifecycle. `NotificationPoster` consults it
  before posting an `InputNeeded` wake notification — matches the
  PWA's "don't ring the bell for the tab you're already on".
- Alert rows now prefer `session.name` over `id` for the header
  title, matching the Sessions list and detail screen.

### Dependencies

- Adds `androidx.lifecycle:lifecycle-process:2.8.4` for
  `ProcessLifecycleOwner`.

## [0.18.0] — 2026-04-20 (Sprint F — Channels/Comms Settings)

### Added

- **ChannelsCard under Settings → Comms.** Lists configured
  messaging channels from `GET /api/channels`. Each row has a
  per-channel enable/disable switch (PATCH /api/channels/{id})
  and a "Test" button that opens a prompt dialog for a test
  message body and fires `POST /api/channel/send`. Empty-state
  hint steers users toward the server-side config UI for adds
  since the parent still 501s on `POST /api/channels`.

### Transport

- `listChannels()`, `setChannelEnabled(id, enabled)`,
  `sendChannelTest(channelId, text)`.

## [0.17.0] — 2026-04-20 (Sprint E — Memory tab)

### Added

- **Episodic memory browser under Settings → General.** Stats grid
  (total / manual / session / learnings / chunks / DB size),
  searchable + deletable list of stored memories. Matches the PWA
  memory panel. Shows a helpful fallback when the server's
  memory subsystem is disabled.
- Transport: `memoryStats()`, `memoryList(limit, role, sinceIso)`,
  `memorySearch(q)`, `memoryDelete(id)`.

### Deferred

- **Memory export** — parent exposes `GET /api/memory/export` as a
  downloadable blob; mobile needs a SAF intent-launcher wiring,
  tracked for a follow-up sprint.
- **Memory remember** (create from UI) — PWA surfaces this inside
  session chat, not in the standalone panel; follows when the
  session-detail memory wiring lands.

## [0.16.0] — 2026-04-20 (Sprint D — Ops: logs viewer + interfaces + restart)

### Added

- **Daemon log viewer under Settings → Monitor.** Paginated
  `GET /api/logs?lines=50&offset=…` with 10 s auto-refresh,
  PWA-matching colour coding (error/warn/info), and newer/older
  buttons. Matches the PWA's `loadDaemonLog` behaviour.
- **Network interfaces card under Settings → Monitor.** Read-only
  list from `GET /api/interfaces` — each row shows name / IPs /
  MAC. Renders in under a second after opening the tab.
- **Restart daemon card.** Red "Restart daemon" button with confirm
  dialog. Calls `POST /api/restart`; the server re-execs and
  active sessions briefly drop their WS connection (noted in the
  card body). Surfaces a banner on success / failure.

### Transport

- New `TransportClient.fetchLogs(lines, offset, level)` returning
  `LogsView(lines, total)`.
- New `TransportClient.restartDaemon()` and
  `TransportClient.listInterfaces()` (raw `List<JsonObject>` so UI
  can pick fields without locking the schema).

## [0.15.0] — 2026-04-20 (Sprint C — /api/profiles picker + voice-to-new)

### Added

- **Profile picker on New Session form.** Pulls `GET /api/profiles`
  for the selected server and renders a dropdown above the Working
  Directory field. "Default (no profile)" is the top option; each
  profile shows name + backend. Selection is passed as `profile`
  on `POST /api/sessions/start`, matching the PWA's populateProfile
  dropdown.
- **Voice-to-new-session.** When voice transcription in an existing
  session's composer starts with `"new:"` or `"new "` (case-
  insensitive), the client now posts `/api/sessions/start` with the
  remaining text instead of inserting it into the composer. Toast
  confirms the new session id; on failure, the transcript falls
  back to the composer so the user doesn't lose the dictation.

### Transport

- New `TransportClient.listProfiles()` returning the raw
  `Map<String, JsonObject>` — callers extract the `backend` field
  for display and pass the profile name to startSession.
- New `TransportClient.writeConfig(raw)` — PUT /api/config with the
  full document. Unused by UI yet; lands for the per-backend
  config editor in a follow-up sprint.
- `startSession` gains an optional `profileName` parameter.

## [0.14.3] — 2026-04-20 (Sprint B — session detail + terminal polish)

### Fixed

- **Terminal no longer paints "datawatch terminal ready" banner**
  on session open. The bright-white banner was noise that mangled
  pane captures on session switch — the xterm container now stays
  empty until real output arrives (matching PWA behaviour).
- **"Load backlog" button re-enables on failure.** Previously the
  button flipped permanently to disabled after the first click,
  even if the call returned an error, leaving users with no retry
  path on a transient network blip. Now it re-enables when the
  fetch fails and is also reset on session switch.

### Added

- **Channel / messaging-backend badge in session-detail header.**
  New middle-subtitle item next to the LLM badge shows the server's
  active messaging channel (signal / telegram / …) fetched from
  `/api/info` once on session open. Matches the PWA header.

## [0.14.2] — 2026-04-20 (Sprint A — Sessions list auto-refresh + sort + last-response viewer)

### Fixed

- **Reachability dot is now an unmistakable traffic light.** Theme
  primary/error swapped for explicit green (#22C55E) / red (#EF4444)
  / amber (#F59E0B), and the dot is 12 dp (was 8 dp). Users asked
  for green/red so "is the server up" reads at a glance.

### Changed

- **Sessions tab auto-refreshes every 5 s** (mirrors the StatsVM
  cadence). The explicit Refresh IconButton is removed; a small
  spinner appears inline in the app bar while a refresh is in
  flight so users can tell the poll is live.

### Added

- **Sort order dropdown in Sessions toolbar.** Three orders matching
  the PWA: Recent activity (default), Started, Name. State-bucket
  ordering (waiting → running → …) still wins at the top; the
  user's pick governs within-bucket ordering.
- **Last-response viewer.** Tapping the 📄 icon on a session row
  opens a ModalBottomSheet with the full `last_response` payload
  (scrollable, monospace, PWA-matching).

### Deferred

- Drag-to-reorder sessions — non-trivial in Compose without an
  external library; tracked for a later sprint. The Name / Started
  / Recent-activity sort covers most of the "I want different
  ordering" cases meanwhile.

## [0.14.1] — 2026-04-20 (Session-detail header + composer PWA parity)

Second round of PWA parity fixes after a side-by-side walkthrough
with the user. Note: v0.14.0 landed on the `dev` build but the
user's phone was running `publicTrack` (the normal debug build),
so none of the v0.14.0 changes were visible yet. v0.14.1 ships on
publicTrack — the user-visible variant.

### Fixed

- **State pill labels now match PWA wire format exactly.** Previously
  mobile showed "WAITING"/"DONE"/"FAILED"; now `waiting_input`,
  `complete`, `failed`, `rate_limited` (lowercase, with underscores).
  Users can describe a session state to someone on the PWA and both
  are looking at the same token.

### Added

- **Session detail header meta row.** Below the title now shows
  `<session-id> · <BACKEND> · <hostname>`, matching the PWA detail
  header. Title itself now prefers user-assigned `name` over the
  raw task prompt.
- **Stop + Timeline promoted to top-level action icons** in the
  session-detail app bar. Previously both lived in the overflow
  menu — matching PWA's always-visible header buttons.
- **tmux | channel tabs below the app bar.** Replaces the v0.14.0
  Chat/Terminal icon toggle with a proper `TabRow` so the active
  output surface is always visible. Selection still persists in
  SharedPreferences across restarts.
- **Composer quick-reply chips when session is `waiting_input`.**
  Row of AssistChips above the text field: approve / reject /
  continue / skip / quit. Matches the PWA's in-composer quick
  commands. Fires `vm.sendQuickReply` without touching the typed
  draft; placeholder text changes to "Reply (input required)…"
  so the state is obvious even before expanding the chips.

## [0.14.0] — 2026-04-20 (Sessions tab matches PWA)

Ripped the state-quick-filters the user explicitly called out ("the
current quick filters on the sessions tab should not be there") and
rebuilt the Sessions tab to match the PWA monitor's shape in
`internal/server/web/app.js` row-for-row.

### Changed

- **Sessions toolbar now matches the PWA.** The old `FilterChipRow`
  (All / Running / Waiting / Completed / Error) is gone. Replaced
  with the PWA's three-row toolbar: a free-text filter input
  (search by name / task / id / backend), per-backend filter
  chips with inline counts (only shown when the pool spans >1
  backend), and a Show/Hide history toggle with the done-session
  count. Default pool: active + recently-completed
  (< 5 min); hit Show History to see everything. Row ordering
  mirrors the PWA — waiting → running → rate-limited → new →
  terminal, then most-recent activity within each bucket.
- **Session rows render name over task in the body, matching PWA.**
  When the user has renamed a session, the row shows the assigned
  name; falls back to the original task prompt, then to `(no task)`.
  Row header still shows the short id + state + backend chip.

### Added

- **Quick commands sheet on waiting_input rows.** New ▶ "Commands"
  button next to Stop opens a PWA-style bottom sheet with three
  stacks: System chips (yes / no / continue / skip / /exit), Saved
  commands lazily pulled from `GET /api/commands`, and a Custom
  text input. Taps fire `POST /api/sessions/reply` without
  navigating into the session. ESC / Ctrl-b keys deferred — they
  need the WS `command` channel which the Sessions tab doesn't
  subscribe to.
- **Multi-line `prompt_context` preview under waiting rows.** When
  the server emits `prompt_context`, the row now renders the last
  4 lines (100-char clamp per line), matching the PWA's trust-
  prompt rendering so users see both the imperative ("press 1") and
  the action ("Enter to confirm"). Falls back to single-line
  `last_prompt` on older servers.
- **"View last response" icon inline with the task body.** Shows
  only when `session.last_response` is non-empty. Click handler is
  a TODO placeholder — the response-viewer modal lands in the next
  batch (tracked with the existing #112 follow-up).
- **Per-session `backend` / `name` / `promptContext` / `lastResponse`
  persisted to DB via migration `3.sqm`** so the PWA-matching row
  chrome renders on cold-open from cache, not just after the first
  REST refresh.

## [0.13.1] — 2026-04-20 (follow-up — correct stale spec paths + wire shipped endpoints)

Discovered that three parity-plan rows previously tagged
upstream-blocked were actually server-ready — parent openapi.yaml
was just stale vs the shipped server (tracked
[dmz006/datawatch#16](https://github.com/dmz006/datawatch/issues/16)).
Fixed the mobile transport to match what the server actually
serves, then wired the UIs the gaps were blocking.

### Fixed

- **Schedules endpoint was wrong path.** Mobile was calling
  `/api/schedule` (singular, per stale spec); the shipped server
  exposes `/api/schedules` (plural). Corrected list + create + delete
  + unit tests.

### Added

- **Per-session schedules strip in session detail.** Mirrors the
  PWA's `loadSessionSchedules(sessionId)` — renders a small strip
  above the composer listing every pending schedule attached to the
  current session, with a ✕ cancel per row. Calls
  `GET /api/schedules?session_id=<id>&state=pending`; hides when the
  session has no pending schedules or the server predates the
  filter. Schedule-reply dialog now passes `sessionId` so new
  schedules show up in the strip immediately.

- **Session timeline overlay prefers server feed.** The existing
  bottom-sheet now tries
  `GET /api/sessions/timeline?id=<sessionId>` first and renders the
  pipe-delimited `{lines: [...]}` response with PWA-matching
  event-colour rules (state → tertiary, input → primary, rate →
  secondary). Falls back to the local WS-event filter when the
  endpoint isn't reachable, with a subtitle flag so users can tell
  which source they're looking at.

- **Model picker in New Session form.** When the picked backend is
  `ollama` or `openwebui`, a second dropdown enumerates installed
  models via `GET /api/<backend>/models`. Informational only today
  — parent's `POST /api/sessions/start` doesn't accept a model
  field (PWA sends `backend` + `profile` only), so the picker lets
  users see what's available. Actually changing the model from
  mobile will need a backend-config PUT, tracked for v0.14.

## [0.13.0] — 2026-04-20 (PWA parity sweep)

A focused sweep over the gaps the user called out after v0.12.0:
session-list rows now match the PWA monitor view, the session-detail
header gains the affordances PWA users expect, and the
new-session form picks up an LLM backend dropdown. Most upstream-
blocked rows on parity-plan are explicitly tagged so future-me
doesn't accidentally re-implement them client-side.

### Added

- **New Session: LLM backend picker.** The Start-session form now shows
  a Backend dropdown populated from `/api/backends` for the selected
  server. The currently-active backend is annotated `active` in the
  list; picking a different one calls `setActiveBackend` server-wide
  before posting `/api/sessions/start` (parent has no per-session
  backend param yet, so this is the closest mobile can get today). If
  the server doesn't expose `/api/backends`, the picker is hidden
  entirely. Mode + model-variant pickers stay parked on
  upstream-blocked rows in parity-plan (`/api/profiles`,
  `/api/ollama/models`, `/api/openwebui/models` not in parent
  openapi).

- **Session timeline bottom-sheet overlay.** A new "Timeline…" entry
  in the session-detail overflow opens a modal sheet that filters the
  cached event stream to non-Output entries (state changes, prompts,
  rate-limits, completions, errors) and renders them as a
  chronological strip — what-happened-when at a glance, without
  scrolling raw output. Composed entirely from
  `SessionEventRepository`'s WS-backed cache; will swap to a server
  feed when parent ships `/api/sessions/timeline`.

- **Chat-mode / Terminal-mode toggle in session detail.** A new icon in
  the top app bar (Chat ↔ Terminal) swaps between the existing xterm
  surface and a chat-style event list. Choice persists via
  `SharedPreferences("dw.session.detail.v1", "chat_mode")`. Chat mode
  appends a Yes / No / Stop **quick-reply** button row under the
  latest `PromptDetected` event so users can blast through approval
  prompts without typing — taps fire `vm.sendQuickReply(...)` which
  sends directly without touching the composer draft.

- **Terminal toolbar: Fit + Jump-to-bottom buttons.** Two new icons next
  to font ± / Backlog: a Fit button forces a manual `safeFit()` pass
  (helps after a pinch-zoom or rotation that didn't fire a resize
  callback), and a Jump-to-bottom button calls
  `term.scrollToBottom()` to snap back to the live tail after
  scrolling up to read backlog. JS-side bridges `dwFit` and
  `dwScrollToBottom` added to host.html.
- **pane_capture: regression test locking the first/redraw frame
  contract.** The end-to-end path (WS frame → EventMapper →
  SessionEvent.PaneCapture → TerminalView → `dwPaneCapture`) is
  audited; `EventMapperTest` now asserts isFirst flips correctly
  across reset boundaries so the next refactor can't silently break
  TUI rendering.

- **Schedule-from-composer in session detail.** A new clock icon next to
  Mic/Send opens the existing schedule dialog pre-seeded with the
  typed reply text — turns "draft a reply, then schedule it for
  later" into a single tap. Falls back to the live prompt → task
  summary → session id when the composer is empty (priority order
  matches the existing overflow-menu Schedule action).

- **Session detail screen gains PWA header affordances and live banners.**
  Tapping the session title in the top app bar opens an inline rename
  dialog (same wire as the Sessions-list overflow), and tapping the
  state pill opens the state-override menu (used to require an
  overflow-menu tap-then-pick). When the owning profile's transport is
  unreachable, a top-of-terminal connection banner now appears (the
  PWA shows an equivalent strip when WS / REST drops). When the
  session is `waiting_input`, an amber input-required banner sits
  directly above the terminal showing the most-recent prompt text
  (live `PromptDetected` event preferred, falling back to
  `Session.lastPrompt`) so triage doesn't require backlog scrolling.

- **Session list rows now match the PWA monitor view.** Each card surfaces
  the active LLM backend chip alongside the state pill (sourced from
  `/api/info` per server), the hostname + relative time meta line, an
  inline "Stop" button on running/waiting rows and "Restart" on
  terminal rows so the most-common actions skip the overflow menu, and
  — for `waiting_input` rows — a two-line quote preview of the
  `last_prompt` so you can triage from the list without opening the
  detail screen. Persistence: added `session.last_prompt` column via
  migration `2.sqm` so the preview survives cold starts.

### Fixed

- **Theme was defaulting to Material You dynamic colors** (`DatawatchTheme`
  had `dynamicColor = true` by default). On Android 12+ that overrode the
  datawatch purple palette with the user's wallpaper and the whole app
  stopped looking like the parent PWA. Flipped default to `false`. Also
  expanded the color scheme + added a `LocalDatawatchColors`
  CompositionLocal exposing PWA-equivalent `bg/bg2/bg3/border/waiting`
  slots Material3 doesn't have.
- **Sticky "server unreachable" on Channels / Schedules / Saved commands
  / Daemon config cards.** Each VM's `init { refresh() }` was a brittle
  one-shot that stuck on unreachable forever if the cold-boot probe
  happened before the network was up. Now each VM observes the active
  profile's `TransportClient.isReachable` flow and re-fires `refresh()`
  every time reachability flips to true, so the banner self-clears.
- **Splash version text was invisible.** Was rendering at `#6D28D9` (dark
  purple) against the `#0F1117` black splash. Bumped to `#A855F7` at 0.75
  alpha and added an "AI Session Monitor" subtitle between the name and
  version to match the PWA splash hierarchy.
- **Terminal rendering didn't match the PWA.** The PWA uses
  `pane_capture` frames (authoritative tmux pane snapshot) as its only
  display source; mobile was routing `raw_output` straight to xterm, so
  TUIs like Claude Code that cursor-position in a 120-col pane ended up
  as scattered-garbage on a 39-col mobile viewport. Added
  `SessionEvent.PaneCapture`, `WsFrame.type == "pane_capture"` mapper,
  and a host-side `window.dwPaneCapture(lines, isFirst)` that replicates
  the PWA's `term.reset() + write` (first) / `ESC[2J ESC[3J ESC[H +
  write` (subsequent) pattern. `raw_output` is suppressed when
  `pane_capture` is present; legacy fallback kicks in against older
  servers.

### Added

- **PWA visual primitives** (`ui/theme/PwaComponents.kt`):
  - `PwaStatePill(state)` — 10sp uppercase state badge with
    tinted-0.15-alpha background, matching `.state-badge-*` in the PWA.
  - `Modifier.pwaStateEdge(state)` — 4dp state-coloured left stripe on
    session cards.
  - `Modifier.pwaCard()` — 12dp-radius `bg2` surface with 1dp border,
    matching `.session-card` / `.settings-section`.
  - `PwaSectionTitle(title)` — 11sp uppercase 0.8sp-letter-spacing
    section heading.
- **Sessions tab** — rows rendered as proper cards with the state edge
  + inline state pill, replacing the old AssistChip + full-width
  HorizontalDivider layout.
- **Settings cards** — all sections (`Servers`, `Security`, `Schedules`,
  `Saved commands`, `Daemon config`, `Comms`, `About`) now wrap in
  `pwaCard()` with a proper `PwaSectionTitle` header. Also added
  explicit refresh buttons to the 4 data-bound cards so manual retry
  is always available.

## [0.12.0] — 2026-04-20

Schedules + file picker + saved commands + config viewer + terminal
backlog pager. 6 phases across 5 UI surfaces, all against endpoints
confirmed in parent v4.0.3+. Plan:
[docs/plans/2026-04-20-v0.12-schedules-files-config.md](docs/plans/2026-04-20-v0.12-schedules-files-config.md).

Paired with parent `dmz006/datawatch` v4.0.3+ which shipped nine new
endpoints (#5–#13 closed) — all v0.11 `(client)`-gated flows (session
delete/rename/restart, `/api/cert`, `/api/backends/active`) now work
against a real server without the NotFound grey-out fallback kicking
in.

### Added — v0.12 sprint

- **Transport layer** (shipped `93351fa`) — 8 new
  `TransportClient` methods:
  `listSchedules` / `createSchedule` / `deleteSchedule` (`/api/schedule`),
  `browseFiles` (`/api/files?path=`),
  `listCommands` / `saveCommand` / `deleteCommand` (`/api/commands`),
  `fetchConfig` (`/api/config`). 4 new domain types
  (`Schedule`, `FileEntry`/`FileList`, `SavedCommand`, `ConfigView`).
  9 new MockWebServer tests.
- **Schedules UI** (shipped `cdf01ea`) — new Settings → Schedules card
  lists scheduled commands with per-row task/cron/enabled chip +
  delete, plus a **+ New schedule** dialog (task multi-line, cron
  free-form with inline hint, enabled toggle). Session detail
  overflow menu gets a **Schedule reply…** action that pre-seeds the
  dialog with the current prompt's text.
- **Server-side file picker** (Phase 3) — reusable
  `FilePickerDialog` under `ui/files/`. Breadcrumb path, `..` to
  go up, dirs-first sort, "Pick this folder" for dir-mode, tap-a-file
  for file-mode. Modes: `FolderOnly` / `FileOnly` / `FolderOrFile`.
  Wired into `NewSessionScreen` as the new optional
  **Working directory** field (lands on `/api/sessions/start` as
  `cwd` — server ignores the field on pre-v4.0.3 builds).
- **Saved command library** (Phase 4, closes BL20) — Settings →
  Saved commands card lists name + command snippets from
  `/api/commands`, with tap-to-expand for long commands and delete
  icon. **+** opens a save dialog. `NewSessionScreen` gains a
  "From library ▾" dropdown next to the Task heading that inlines
  the picked command into the field; hidden entirely when no
  commands are saved so first-time use isn't cluttered.
- **Daemon config viewer (read-only)** (Phase 5) — Settings → Daemon
  config card shows `GET /api/config` as collapsible top-level rows
  that expand into pretty-printed JSON. Belt-and-braces client-side
  secondary mask catches common secret field names (`*token*`,
  `*secret*`, `*key*`, `password`, `passphrase`, etc.) in case the
  parent's mask misses one. Write is explicitly deferred to v0.13 per
  ADR-0019 (structured form).
- **Terminal backlog pager** (Phase 6) — third button in the terminal
  toolbar (history icon) fetches up to 1000 lines of pre-subscription
  PTY output via `GET /api/output` and prepends them into xterm.
  Host bridge: `window.dwPrependBacklog(s)`; controller method
  `TerminalController.prepend(text)`. Button disables after first use
  per session to prevent double-prepending.

### Fixed

- **B7 — CI `ktlintCheck` parse failure on `FederationDtos.kt`**
  (shipped `c4f003f`). Every CI run since 2026-04-20 was red.
  Root cause: `ktlint-plugin` 12.1.1 bundled ktlint 1.1, pre-Kotlin-2.0.
  Bumped to 12.3.0 (ktlint 1.5.0). Added `.editorconfig` and
  `config/detekt/detekt.yml` with Compose-friendly rule exemptions
  (`@Composable` escapes `function-naming`; `MagicNumber` excludes
  UI packages). Rewrote 3 KDocs that contained parser-tripping
  `{ ... }` / `<name>` / `[Foo...]` tokens. CI green again.

### Changed

- **Parent `dmz006/datawatch` v4.0.3 landed** the 9 endpoints filed
  (#5–#13 closed). Docs/parity-plan + parity-status rows for the v0.11
  `(client)`-gated features flipped to plain ✅ with "shipped in
  parent v4.0.3" annotation. The v0.11 client-side
  `TransportError.NotFound` grey-out fallbacks auto-unstick against
  any v4.0.3+ server; no mobile code change needed.

## [0.11.0] — 2026-04-20

Session power-user parity sprint — closes the 8 items in
`docs/parity-plan.md` §6 v0.11 plus two opportunistic pickups from the
2026-04-20 PWA re-audit. Plan:
[docs/plans/2026-04-20-v0.11-session-power-user.md](docs/plans/2026-04-20-v0.11-session-power-user.md).

### Added

- **`:shared` transport — 10 new methods** (`renameSession`,
  `restartSession`, `deleteSession` + bulk `deleteSessions`, `fetchCert`,
  `setActiveBackend`, `listAlerts`, `markAlertRead`, `fetchInfo`,
  `fetchOutput`). New domain types `Alert` / `AlertSeverity` /
  `ServerInfo`. 14 new MockWebServer tests.
- **Session row overflow menu** — Rename, Restart, Delete dialogs on
  every row in the Sessions tab.
- **Bulk multi-select** — long-press a row to enter selection mode;
  TopAppBar flips to "N selected" with Cancel + bulk-Delete.
- **Connection-status dot** in Sessions TopAppBar — green/grey/red
  against `TransportClient.isReachable`. Tap for a bottom sheet with
  last-probe time and Retry.
- **About card "Connected to" row** — hostname + daemon version from
  `GET /api/info`.
- **Settings → Servers overflow menu** — per-server "Download CA cert"
  (saves PEM under `Download/datawatch/` and fires the Android
  install-cert intent) + "Delete server".
- **New Session screen** — floating "+" button on Sessions tab →
  form with task text + server picker → `startSession`.
- **Active-backend radio picker** in the Channels tab —
  `POST /api/backends/active`.
- **Swipe-left alerts dismiss** — mutes the underlying session so the
  projection drops the row and the badge re-counts.
- **Terminal toolbar** above the xterm view — inline search bar
  (prev/next/close) plus a clipboard button. Uses vendored
  `xterm-addon-search@0.13.0` (12.2 KB) in
  `composeApp/src/androidMain/assets/xterm/`.

### Changed

- `ServiceLocator.transportFor(profile)` now caches a
  `TransportClient` per profile (keyed on `baseUrl + trustAnchor +
  bearerRef`) so downstream observers of `isReachable` see a stable
  Flow across refreshes.

### Parent-confirmation gates

- `POST /api/sessions/delete`, `GET /api/cert`, and
  `POST /api/backends/active` are not in the parent's v3.0.0
  `openapi.yaml`. Client transport calls them anyway; each returns
  `TransportError.NotFound` on pre-parity servers, at which point the
  Android UI greys the affected control and surfaces a toast / banner
  pointing at the upstream gap. Users can still run every other flow.

### Known follow-ups for v0.12+

- Wire `AlertsScreen` directly to `GET /api/alerts` + `POST /api/alerts`
  once the parent's Alert wire shape is fully locked (transport methods
  already exist).
- Compose UI test infrastructure in `composeApp` (no `androidTest`
  sourceset today).
- Saved command library — `GET/POST/DELETE /api/commands` — BL20.
- Signal device linking — `/api/link/*` with SSE QR stream — BL21.

## [0.10.1] — 2026-04-19

Previously tagged **v1.0.1**; renumbered 2026-04-20 — the 1.0 label
is reserved for the full-PWA-parity milestone.

### Fixed
- **Session detail now uses xterm.js as its primary surface.** v0.10.0
  rendered events as a chat-style spine and tucked the terminal behind
  an icon; the result looked like scrolling text with no ANSI / cursor /
  real scrollback. This release swaps the default: the terminal fills
  the body, prompt + rate-limit notices become an `InlineNotices`
  banner above the composer, the reply composer stays below. Matches
  the PWA's session UX.

### Added
- `docs/parity-plan.md` — complete audit of PWA → mobile gaps grouped
  by screen, with a v0.11 → v0.14 roadmap. Grounded against the parent
  repo's `internal/server/web/` and `docs/api/openapi.yaml` at the
  v3.0.0 tag.

## [0.10.0] — 2026-04-19

Previously tagged **v1.0.0** as "first production release";
renumbered 2026-04-20 — v1.0 is reserved for the milestone where
every row in `docs/parity-status.md` flips to ✅. v0.10.0 closes
Sprint 6 (every ADR-0042 scope item shipped) and pairs with parent
datawatch v3.0.0.

### What's in v0.10.0 — highlights

- **Live session management.** REST + WebSocket (`/ws?session=<id>`) with
  auto-reconnect + jittered exponential backoff. Session detail, chat-style
  event stream, reply composer, kill-with-confirm, state override.
- **xterm.js terminal** bottom sheet, ANSI-colour, 5000-line scrollback.
- **Multi-server.** In-app picker + edit server + 3-finger-swipe gesture
  (BL9) + all-servers mode via `/api/federation/sessions`.
- **Push notifications.** FCM primary + ntfy fallback; per-event-type
  channels; inline RemoteInput reply on input-needed prompts;
  `dwclient://session/<id>` deep link.
- **Voice reply.** In-composer mic button → `/api/voice/transcribe`
  (Whisper-backed, parent issue #2).
- **Stats, Alerts, Channels** tabs with bottom-nav badge counter.
- **Home-screen widget** (BL6) with running / waiting / total counts.
- **Wear OS dashboard + Tile** (BL4) with the same glance counters.
- **Android Auto** Messaging-template list screen (BL10 scaffolding).
- **Biometric unlock** (BL2) — opt-in Class-3 enforcement at app entry.
- **Encrypted storage.** SQLCipher-backed SQLDelight DB + Keystore-bound
  bearer-token vault. Schema migrations verified (1 → 2 backfill for
  session_event shipped in v0.3.0).

### What moves to v0.11+

- Biometric-bound DB passphrase (wraps Keystore key with biometric auth
  requirement)
- Bulk-delete completed sessions
- Per-channel enable/disable REST (depends on parent exposing channel state)
- xterm search + copy affordances, quick-tile voice launch wiring
- Wear Data Layer pairing flow (counts live through phone → watch bridge)

### Notes
- No app-store release in this commit — APK is a release-candidate
  artefact. Play submission is a separate manual step per ADR-0019.
- JDK 21 required for builds (see memory/feedback_build_jdk21.md).

## [0.9.0] — 2026-04-19

Sprint 5 — hardening + biometric unlock. Release-candidate milestone.

### Added
- **Biometric unlock (BL2, ADR-0042)** — optional, off by default. When
  enabled in Settings → Security, app entry prompts for Class-3 biometric
  (fingerprint/face) before AppRoot composes. Failure leaves the app
  locked (no bypass). Uses `androidx.biometric` 1.2.0-alpha05.
- **Security card** in Settings with an enable toggle; greyed out when
  no Class-3 biometric is enrolled.
- MainActivity migrates from `ComponentActivity` to `FragmentActivity`
  (required by BiometricPrompt).

### Known follow-ups for v0.10.0
- Biometric-bound DB passphrase (wrap `deriveDatabasePassphrase` in a
  Keystore key that requires biometric auth) — current build gates
  only the UI, not the underlying passphrase derivation.

## [0.5.0] — 2026-04-19

Sprint 4 — Wear OS + Android Auto surfaces.

### Added
- **Wear OS dashboard** — three-count tiles (running / waiting / total) in
  the watch app with a waiting accent, paired-server name footer, and a
  pre-pairing empty state.
- **Wear Tile (BL4, ADR-0042)** — ProtoLayout 1.2 glance tile mirroring the
  dashboard counts, registered via TileService intent filter.
- **Android Auto ListTemplate** — upgraded PreMvpPlaceholderScreen to a
  proper three-row list (running / waiting / total) with app-icon header;
  Play-compliant for the Messaging category since rows are static counts.
- Gradle deps: `androidx.wear.protolayout` (+ expression + material),
  `com.google.guava` for the Tile ListenableFuture contract.

### Known follow-ups for v0.6.0
- Wear Data Layer (play-services-wearable) pairing flow — counts on Tile
  + dashboard are placeholder zeros until this lands.
- Auto Tile (BL10) — Android Auto API doesn't have a direct "tile" concept;
  the driving-mode glance is covered by the ListTemplate Auto screen. Keeping
  BL10 open for dev-flavor passenger experiments.

## [0.4.0] — 2026-04-19

Sprint 3 — MVP milestone. Voice, all-servers, stats, channels, widget, MCP-SSE bones.

### Added
- **All-servers mode** backed by `/api/federation/sessions` (parent issue #3).
  Picker gets an "All servers" row; SessionsViewModel fans out in parallel,
  merges by most-recent-wins, collates per-server errors into a banner.
- **Live stats dashboard** at `/api/stats` — CPU/Memory/Disk/GPU bars with
  colour-coded thresholds, session counts, daemon uptime. Polls every 5 s.
  Replaces the stale Sprint-1-Phase-4 placeholder.
- **Channels tab** — live LLM backends from `/api/backends` with active
  chipped; static note pointing at server-side messaging config (Signal/
  Telegram/etc. — per-channel REST surface tracked for v0.5.0).
- **Voice reply** — `POST /api/voice/transcribe` (parent issue #2, Whisper).
  Mic/Stop button in the session reply composer records AAC/M4A, POSTs
  multipart with `auto_exec=false` so the transcript lands in the composer
  rather than auto-sending (PWA parity).
- **Home-screen widget (BL6, ADR-0042)** — running / waiting / total counts
  for the active profile; tap opens the app. Resizable, lock-screen allowed.
- **MCP-SSE transport skeleton** — `McpSseTransport` consumes `text/event-
  stream` frames with exponential-backoff reconnect. Not yet wired into the
  UI (post-MVP work turns it into a full MCP client).

### Fixed
- **SQLDelight migration 1 → 2** backfills the `session_event` table for
  v0.2.x-origin encrypted DBs; pre-fix, opening any session in v0.3.0
  crashed with `no such table: session_event` because Auto Backup carried
  the v0.2.x schema across in-place upgrade.

## [0.3.0] — 2026-04-19

Sprint 2 — session UX, multi-server, push.

### Added
- **WebSocket session event stream** (`/ws?session=<id>`) with automatic
  reconnect + exponential backoff + jitter. New `SessionEvent` sealed
  hierarchy + `SessionEventRepository` ring buffer (5000 events / session).
- **Session detail screen** with reply composer, kill confirm, state-override
  dialog, mute toggle, banner-on-failure.
- **xterm.js terminal sheet** (vendored xterm@5.3.0) bound to live WS output;
  dim ANSI markers for state-change / completed / error events.
- **Multi-server picker** dropdown on Sessions top bar + bottom-sheet variant.
- **3-finger upward swipe gesture (BL9, ADR-0042)** opens the server picker
  from any screen; debounced 500 ms.
- **Edit server screen** with two-step probe + delete confirmation; clears
  ActiveServerStore if the deleted profile was active.
- **FCM + ntfy push notifications** (parent issue #1, parent v3.0.0).
  Five notification channels (input_needed/completed/rate_limited/error/
  foreground), inline RemoteInput "Reply" action that posts to
  `/api/sessions/reply`, deep-link tap intent
  (`dwclient://session/<id>`) → SessionDetail. NTFY foreground service
  fallback for servers without Firebase.
- **Filter chips** on Sessions: All / Running / Waiting / Completed / Error.
- **Swipe-to-mute** on session rows.
- **Alerts tab** + bottom-nav badge counter for sessions where
  `needsInput && !muted`.
- Transport: `registerDevice` / `unregisterDevice` /
  `overrideSessionState` REST endpoints (matches parent v3.0.0 wire format
  verbatim).
- 33 unit tests (12 RestTransport / 10 EventMapper / 4 WebSocketUrl / 4
  SessionState / 3 Mappers).

### Changed
- `SessionRepository.db` made `internal` (was private) to enable
  `observeForProfileAny` extension lookup.
- `SessionsViewModel` now combines persisted `ActiveServerStore` selection
  with the live profile list — degrades gracefully on profile delete.

### Scope expansion — ADR-0042
Promotes five items from post-MVP backlog to v0.10.0 requirements (the
release originally mislabelled v1.0.0; the 1.0 label is now reserved for
full PWA parity):
- BL9: 3-finger-swipe-up server picker → Sprint 2 ✅ shipped here
- BL6: home-screen widget → Sprint 3
- BL4: Wear Tile → Sprint 4
- BL10: Android Auto Tile (dev flavor) → Sprint 4
- BL2: biometric unlock → Sprint 5 (amends ADR-0011)

Timelines hold: MVP 2026-06-12, production 2026-07-10.

### Changed
- **Scope expansion — ADR-0042** promotes five items from post-MVP backlog to
  v0.10.0 requirements:
    - BL9: 3-finger-swipe-up server picker → Sprint 2
    - BL6: home-screen widget → Sprint 3
    - BL4: Wear Tile → Sprint 4
    - BL10: Android Auto Tile (dev flavor) → Sprint 4
    - BL2: biometric unlock → Sprint 5 (amends ADR-0011)
  Timelines hold: MVP 2026-06-12, production 2026-07-10. Full rationale + per-
  sprint landing in `docs/decisions/README.md#mvp-scope-adjustment` and
  `docs/sprint-plan.md#scope-change--adr-0042`.

### Added
- **Launcher icon promoted to B8** (Earthrise composition): adaptive foreground
  + background + monochrome variants. Old phone-silhouette Concept B is gone;
  the launcher now shows the lunar surface with pronounced craters, Earth
  rising above the horizon, and the datawatch tablet (with eye + matrix-rain
  stand-in) sitting on the moon. Monochrome silhouette for Android 13+ themed
  icons.
- **Splash animation rewritten to match B8**: live Compose-canvas Earthrise
  scene around the existing animated tablet — starfield, Earth disc with halo
  + continent + cloud + specular highlight, lunar surface with eight
  pronounced craters, Earthshine rim along the horizon, plus the existing
  matrix rain animation + pulsing pupil + scan line on the tablet.
- **`docs/parity-status.md`**: complete PWA feature → mobile sprint matrix.
  Honest accounting of what's shipped, what's planned, and which sprint
  delivers each item. Reviewed every sprint close.

### Fixed
- **Boot crash on first launch** (`java.lang.IllegalArgumentException: Empty key` in
  `KeystoreManager.deriveDatabasePassphrase`). Root cause: Android Keystore refuses to
  expose `SecretKey.encoded` for non-extractable keys, so the HMAC derivation received
  an empty byte array and `SecretKeySpec` rejected it. Rewrote `KeystoreManager` to
  delegate to Jetpack Security's `EncryptedSharedPreferences` (MasterKey-wrapped
  AES-256-GCM), which is the recommended pattern for storing 32-byte passphrases —
  same at-rest protection, simpler, correct. `DatabaseFactory` + `ServiceLocator`
  updated to pass `Context` into the manager.

## [0.2.0] — 2026-04-18

**Sprint 1 delivery — first working Android build.** A fresh install of the debug APK
now successfully onboards against a live datawatch server: add profile, health probe,
session list rendering from a real `/api/sessions` response.

Not yet shipped (Sprint 2+): WebSocket `/ws` streaming, xterm terminal, FCM push,
MCP SSE, voice capture, Wear OS live app, Android Auto live surface.

### Added

- Sprint 1 Phase 4 — tests:
  - `RestTransportTest` MockWebServer suite (9 tests) covering success paths plus
    every `TransportError` mode (Unauthorized, ServerError, RateLimited, Unreachable,
    ProtocolMismatch), reply body shape, start-session id extraction, stats
    deserialization.
  - CI version-parity check rewritten to actually verify `gradle.properties` +
    `Version.kt` agree (previous regex looked for hard-coded literals in `.gradle.kts`
    and silently passed).

### Fixed (CI iteration during this sprint)

- SQLDelight `NULLS LAST` → `IS NULL` workaround (dialect 3.18 compatibility).
- `androidx.car.app` pinned to `1.7.0` stable (`1.7.0-rc02` didn't exist on Maven).
- Kotlin package `auto.public` → `auto.messaging` (`public` keyword conflict).
- Auto flavor source-set sharing (placeholder moved to `src/main/kotlin`).
- Material Components dep added so XML `Theme.Material3.*` parent resolves.
- `shared`'s public signatures expose Ktor + datetime + SQLDelight runtime via `api(...)`.
- Android Lint + ktlint switched to warnings-only for Sprint 1; Sprint 5 flips back.
- Removed phone manifest's `android.hardware.type.watch` / `.automotive` uses-features
  (surface-specific features belong in their respective modules).

## [0.1.0-pre] — 2026-04-18

### Added
- Gradle wrapper committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
  Gradle 8.9) — no first-clone bootstrap step needed.
- Concept B icon set (ADR-0037): Android adaptive icon foreground + background +
  monochrome vector drawables under `composeApp/src/androidMain/res/drawable/`;
  dev-flavor variant with amber "DEV" chip under `composeApp/src/dev/res/drawable/`;
  master SVG + Play Store feature graphic under `assets/`.
- Icon review set — `assets/variants/icon-B1..B4.svg` — four Concept B iterations for
  user pick; winner replaces the committed launcher foreground.
- Sprint 1 plan document: `docs/plans/2026-04-18-sprint-1-foundation.md`.
- Shared-module foundation (Sprint 1 Phase 1):
  - Domain types: `Session`, `SessionState`, `Principal`, `Prompt`, `ReachabilityProfile`
  - Transport: expanded `TransportClient` interface, typed `TransportError` hierarchy,
    Ktor-based `RestTransport` (health / sessions / start / reply / kill / stats)
  - Storage: `DatabaseFactory` expect/actual (plaintext placeholder; SQLCipher swap in
    Phase 2), `ServerProfileRepository`, `SessionRepository`
  - SQLDelight schema split into `profile.sq` + `session.sq`
  - Unit tests: `SessionStateTest`, `MappersTest`
- Sprint 1 Phase 3 — composeApp onboarding + sessions list (first user-visible flow):
  - `ServiceLocator` hand-wired DI — DB, repositories, token vault, shared HttpClient
  - `createHttpClient()` expect/actual: Android OkHttp engine, iOS Darwin engine
  - `OnboardingScreen` → `AddServerScreen` (form + live health probe + token-vault
    persistence with roll-back on probe failure)
  - Home shell: Material 3 `NavigationBar` with Sessions / Channels / Stats / Settings
  - `SessionsScreen` / `SessionsViewModel` — cached list + live refresh; disconnect
    banner per ADR-0013
  - `SettingsScreen` with basic server list + About section
  - `MainActivity` now launches `AppRoot` (Compose Navigation)
  - `compose.materialIconsExtended` added for bottom-nav icons
- Sprint 1 Phase 2 — Android storage + crypto:
  - `KeystoreManager` — AES-256-GCM master key in Android Keystore (StrongBox-preferred
    on capable devices); HMAC-SHA256 derivation of the SQLCipher passphrase.
  - `TokenVault` — EncryptedSharedPreferences for bearer tokens, alias-keyed per
    profile; never persisted to the SQLite DB.
  - `AndroidDatabaseFactory` now opens a SQLCipher-encrypted database via
    `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` with the derived
    passphrase; `DatawatchApp.onCreate` loads `libsqlcipher.so` at process start.
  - Completed backlog: BL8.

### Changed
- App display name set to `datawatch` (lowercase) for all user-facing surfaces — Play
  Store listing, launcher label, Wear watchface, Android Auto, iOS bundle display
  (ADR-0041, supersedes name portion of ADR-0030). Dev variant reads `datawatch (dev)`.
  Technical identifiers (applicationId `com.dmzs.datawatchclient[.dev]`, Kotlin packages,
  repo name `dmz006/datawatch-app`, keystore file names) are unchanged.

## [0.1.0-pre] — 2026-04-18

### Added
- Complete design package under `docs/`: 18 documents covering architecture, data flow,
  data model, API parity, security, threat model, UX, Wear OS, Android Auto, branding,
  sprint plan, Play Store registration, privacy policy, and data safety.
- 40 Architecture Decision Records consolidated in `docs/decisions/README.md`, approved
  during the four-batch design Q&A on 2026-04-17.
- `AGENT.md` operating rules adapted from
  [dmz006/datawatch](https://github.com/dmz006/datawatch), Kotlin-stacked.
- KMP + Compose Multiplatform + Wear OS + Android Auto + iOS-skeleton Gradle scaffold.
- GitHub Actions CI skeleton (`ci`, `release`, `security`).
- Upstream cross-references to `dmz006/datawatch` issues #1, #2, #3.

### Security
- Polyform Noncommercial 1.0.0 license (matches parent project).
- SQLCipher + Android Keystore data-at-rest model documented.
- Closed-loop telemetry stance documented (no Crashlytics / Sentry / Firebase Analytics).

### Status
- Pre-MVP. Implementation begins Sprint 1 (2026-05-02). MVP target 2026-06-12; public
  production 2026-07-10.

[Unreleased]: https://github.com/dmz006/datawatch-app/compare/v0.12.0...HEAD
[0.12.0]: https://github.com/dmz006/datawatch-app/compare/v0.11.0...v0.12.0
[0.11.0]: https://github.com/dmz006/datawatch-app/compare/v0.10.1...v0.11.0
[0.10.1]: https://github.com/dmz006/datawatch-app/compare/v0.10.0...v0.10.1
[0.10.0]: https://github.com/dmz006/datawatch-app/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/dmz006/datawatch-app/compare/v0.5.0...v0.9.0
[0.5.0]: https://github.com/dmz006/datawatch-app/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/dmz006/datawatch-app/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/dmz006/datawatch-app/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/dmz006/datawatch-app/compare/v0.1.0-pre...v0.2.0
[0.1.0-pre]: https://github.com/dmz006/datawatch-app/releases/tag/v0.1.0-pre
