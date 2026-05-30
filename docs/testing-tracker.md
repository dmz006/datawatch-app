# Testing Tracker

Per [AGENT.md](../AGENT.md) Testing Tracker Rules — every surface / transport / backend
gets two levels of validation:

- **Tested** (`Yes` / `No`): unit / integration tests exist and pass under `./gradlew test`.
- **Validated** (`Yes` / `No`): live end-to-end confirmed on a real device against a real
  datawatch server. Document environment in *Test Conditions*.

## Interfaces

| Surface | Feature | Tested | Validated | Sprint | Test Conditions | Notes |
|---------|---------|--------|-----------|--------|-----------------|-------|
| Shared | `SessionState.fromWire` mapping | Yes | — | 1 | JVM commonTest | 4 tests: canonical, synonyms, case-insensitive, unknown → Error |
| Shared | DTO → domain `Session` mapper | Yes | — | 1 | JVM commonTest | 2 tests: happy path + unknown state degrade |
| Shared | `RestTransport` happy path | Yes | No | 1 | androidUnitTest + MockWebServer | `RestTransportTest.pingSucceedsOn200`, `listSessionsDeserializesHappyPath`, `replyPostsExpectedBody`, `startSessionReturnsIdFromResponse`, `statsDeserializesAllFields` |
| Shared | `RestTransport` 401 → Unauthorized | Yes | No | 1 | androidUnitTest | `unauthorizedMapsTo401Type` |
| Shared | `RestTransport` 5xx → ServerError | Yes | No | 1 | androidUnitTest | `serverErrorMapsTo5xxType` |
| Shared | `RestTransport` 429 → RateLimited | Yes | No | 1 | androidUnitTest | `rateLimitedMapsTo429Type` |
| Shared | `RestTransport` network error → Unreachable | Yes | No | 1 | androidUnitTest | `networkUnreachableMapsToUnreachable` |
| Shared | `RestTransport` PRD/channel/backend CRUD | Yes | No | FF | androidUnitTest + MockWebServer | `RestTransportAutonomousTest` — 23 tests: listPrds, createPrd (dir+profile), prdAction (approve/reject/decompose/set_llm), editStory, editFiles, patchPrd, deletePrd (soft+hard), listBackends (obj+string shapes, filters enabled=false+shell), listChannels (wrapped+bare), createChannel, deleteChannel, setChannelEnabled, listOllamaModels, listOpenWebUiModels |
| Shared | `ServerProfileRepository` CRUD | Yes | No | 1 | androidUnitTest (JdbcSqliteDriver in-memory) | 9 tests: upsert+observe, idempotent replace, delete, delete non-existent, empty list, touchLastSeen, touchLastSeen unknown, enabled flag round-trip, ordering by last_seen_ts DESC |
| Shared | `SessionRepository` upsert + observe | Yes | No | 1 | androidUnitTest (JdbcSqliteDriver + PRAGMA foreign_keys=ON) | 9 tests: upsert+observe, replace, profile isolation, replaceAll atomic+empty, setMuted true+false, ordering, FK cascade delete |
| Android | SQLCipher open + key unwrap | No | No | 1 | | Phase 4 androidTest — needs instrumented runner |
| Android | Keystore master-key round-trip | No | No | 1 | | Phase 4 androidTest — needs instrumented runner |
| Android | `KeystoreManager.deriveDatabasePassphrase` (Phase 2) | No | No | 1 | | Phase 4 |
| Android | `TokenVault` put / get / remove round-trip | No | No | 1 | | Phase 4 |
| Phone | Onboarding + add-server happy path | No | No | 1 | | Phase 3 |
| Phone | Live session list against running datawatch | No | No | 1 | | Phase 3 |
| Phone | WebSocket `/ws` stream | No | No | 2 | | |
| Phone | xterm.js WebView | No | No | 2 | | |
| Phone | Voice capture (all 4 surfaces) | No | No | 3 | | |
| Phone | MCP SSE tool invocation | No | No | 3 | | |
| Phone | Intent-relay fallback (Signal / SMS) | No | No | 3 | | |
| Phone | FCM wake (dumb-ping) | No | No | 2 | | dmz006/datawatch#1 |
| Phone | ntfy fallback subscription | No | No | 2 | | |
| Phone | DNS TXT covert channel | No | No | 3 | | |
| Phone | Proxy drill-down (breadcrumb) | No | No | 3 | | |
| Phone | All-servers fan-out | No | No | 3 | | |
| Phone | `AutonomousViewModel` — refresh, create, approve, reject, decompose, setLlm, run, cancel, requestRevision, editPrd, hardDelete, editStory, editFiles | Yes | No | FF | androidUnitTest (`AutonomousViewModelTest`) | 15 tests; explicit stubs for `listPrds` + `listBackends` + each CRUD op |
| Phone | Autonomous PRD list + filter UI | No | No | FF | | `AutonomousScreen` — requires live server with `autonomous.enabled=true` |
| Phone | Autonomous New PRD dialog (profile / dir / cluster / backend / effort) | No | No | FF | | `NewPrdDialog` — all three mode paths |
| Phone | Autonomous PRD detail — all action buttons + story edit + file association | No | No | FF | | `PrdDetailDialog` — test each action path against a real PRD |
| Wear | Notification + reply actions | No | No | 4 | | |
| Wear | Watchface complication | No | No | 4 | | |
| Wear | Rich Wear app dictation | No | No | 4 | | |
| Wear | Voice fallback chain (Whisper → native STT) | No | No | 4 | | ADR-0038 |
| Auto public | Messaging template TTS readout | No | No | 4 | | |
| Auto public | Voice reply via Car App | No | No | 4 | | |
| Auto dev | Full passenger UI | No | No | 4 | | `.dev` flavor only |
| iOS | `IosTokenStore` Keychain round-trip | No | No | iOS-4 | Requires macOS/XCTest runner | CoreFoundation SecItem* put/get/remove |
| iOS | `IosDatabaseFactory` NSFileProtectionComplete | No | No | iOS-4 | Requires real iOS device + XCTest | Verify file attr after driver init |
| iOS | `IosServiceLocator` callback bridge (saveProfile) | No | No | iOS-4 | Requires macOS/XCTest + MockWebServer | Probe → persist → verify DB |
| iOS | Sessions list (live polling 10 s) | No | No | iOS-5 | Requires real iPhone + running datawatch | SessionsView with real session data |
| iOS | Terminal WKWebView + xterm.js WS | No | No | iOS-6 | Requires real iPhone + running datawatch | Connect, type input, receive output |
| iOS | Kill session from SessionDetailView | No | No | iOS-6 | Requires real iPhone + running datawatch | Confirm kill via server session list |
| iOS | Alerts list (polling, severity icons) | No | No | iOS-8 | Requires real iPhone + running datawatch | AlertsView with real alert data |
| iOS | Observer metrics grid (5 s polling) | No | No | iOS-9 | Requires real iPhone + running datawatch | CPU/mem/disk values match server |
| iOS | Dashboard multi-server cards | No | No | iOS-10 | Requires real iPhone + ≥2 datawatch servers | Parallel fetch, both cards show stats |
| iOS | Server profile add/edit/delete + probe | No | No | iOS-4 | Requires real iPhone + running datawatch | Round-trip add → probe → persist → delete |
| iOS | Bearer token Keychain storage | No | No | iOS-4 | Requires real iPhone | Add server with token; verify token survives app restart |
| iOS | Face ID / Touch ID lock | No | No | iOS-4 | Requires real iPhone with Face ID or Touch ID | Enable in Settings; relaunch; verify gate appears |
| iOS | iPad NavigationSplitView | No | No | iOS-13 | Requires real iPad or iPad Simulator (macOS) | Sidebar + detail layout on regular size class |
| iOS | APNs device token registration stub | No | No | iOS-12 | Requires real iPhone (APNs not available on Simulator) | Token printed to console; registration to server pending datawatch#107 |
| iOS | xcodebuild simulator build (CI) | No | No | iOS-1 | GitHub Actions macos-15 — check CI run | Build succeeds; no compilation errors |
| iOS | Automata CRUD (list / add / delete types) | No | No | iOS-16 | Requires real iPhone + running datawatch with autonomous.enabled=true | AutomataView: list types, add via sheet, swipe-to-delete |
| iOS | Terminal IME keyboard resize (DwWKWebView) | No | No | iOS-17 | Requires real iPhone (keyboard on Simulator may differ) | Open session terminal; raise/dismiss keyboard; verify xterm cols/rows adjust via dwExplicitSize |
| iOS | Whisper voice transcription (VoiceRecorder → /api/voice/transcribe) | No | No | iOS-18 | Requires real iPhone + datawatch server with whisper.backend configured | Tap mic; grant permission; speak; tap Send; verify transcript populates reply field |
| Android + iOS | Reply/Enter submission (\r fix, /api/sessions/{id}/input) | No | No | v1.0.25 | Requires real device + running session in waiting_input state | Send reply via composer bar; verify shell executes (not just inputs without running) |
| Android + iOS | summary_generated_at "AI Xm ago" badge on session cards | No | No | v1.0.25 | Requires datawatch v8.9.5+ with summarizer enabled | Check session card shows "AI Xm ago" badge left of activity timestamp after summarization |
| Android + iOS | Settings → Session Summarizer "Test" button (POST /api/summarizer/test) | No | No | v1.0.25 | Requires datawatch v8.9.5+ with summarizer configured | Tap Test; verify "✓ ok · Xms" shown; disable summarizer LLM; verify error shown |

Update this table with each PR that lands a feature. Don't mark `Validated=Yes` based on
unit tests alone.

## v7.0.0-alpha parity arc (Sprints 17–22+)

| Surface | Feature | Tested | Validated | Sprint | Test Conditions | Notes |
|---------|---------|--------|-----------|--------|-----------------|-------|
| Shared | `SessionDto.backendFamily` fallback to `llmBackend` | No | No | 17 | `DtoRoundTripTest` — both fields present; only backendFamily; only llmBackend | Priority: high — alpha.27 contract |
| Shared | `Mappers.toSession()` backendFamily → Session.backend | No | No | 17 | `SessionMapperTest` | |
| Shared | `ObserverPeersByNodeDto` JSON round-trip | No | No | 18 | `DtoRoundTripTest` — by_node map + unbound list | alpha.24 |
| Shared | `MetaPeersDto` / `MetaNodeBucketDto` / `MetaObserverEntryDto` round-trip | No | No | 18 | `DtoRoundTripTest` | nested bucket deserialization |
| Shared | `TransportClient.getObserverPeersByNode()` REST | No | No | 18 | `RestTransportTest` + MockWebServer | GET `/api/observer/peers/by-node` |
| Shared | `TransportClient.getFederationMetaPeers()` REST | No | No | 18 | `RestTransportTest` + MockWebServer | GET `/api/federation/meta-peers` |
| Phone | `FederatedPeersCard` group-by-node toggle | No | No | 18 | `FederatedPeersViewModelTest` | groupByNode state transitions + loadByNode |
| Shared | `AgentSettingsDto` round-trip (opencodeModels list) | No | No | 19 | `DtoRoundTripTest` | alpha.28 |
| Shared | `TransportClient.patchProjectAgentSettings()` REST | No | No | 19 | `RestTransportTest` + MockWebServer | PATCH body + 200 |
| Phone | `KindProfilesCard` agent-settings editor (project kind) | No | No | 19 | Manual — live server with project profile | 4 fields; comma-sep → JsonArray |
| Phone | `AlertDockOverlay` dismiss / mute callbacks | No | No | 20 | `AlertDockTest` (Compose UI) | alpha.29 |
| Phone | `AppRoot` dock visibility threshold (≥2 active alerts) | No | No | 20 | `AppRootTest` | dock reappears when count resets |
| Phone | Alert dock category chips (needs-input ×N, err ×N) | No | No | 20 | Manual — live server with active alerts | |
| Wear | `AlertsComplicationService` DataItem parse + fallback | No | No | 21 | `AlertsComplicationTest` | No DataItem → (0,0,0) |
| Wear | `AlertsTileService` layout branches | No | No | 21 | `AlertsTileTest` | hasData=false; errors>0 health dot |
| Wear | `WearSyncService.publishAlerts()` DataMap keys | No | No | 21 | `WearSyncServiceTest` | total/needsInput/errors/ts correct |
| Phone | `AlertsViewModel` chip filter (All/Prompt/Error/Warn/Info) | No | No | 22 | `AlertsViewModelTest` | alpha.30 |
| Phone | `AlertsViewModel` sort toggle (BySession/Chronological) | No | No | 22 | `AlertsViewModelTest` | flat list newest-first |
| Phone | `AlertsViewModel` search (title+body, case-insensitive) | No | No | 22 | `AlertsViewModelTest` | |
| Phone | `AlertsViewModel.dismissAll()` → markAlertRead(all=true) | No | No | 22 | `AlertsViewModelTest` | |
| Phone | `BottomNavBar` always-on badge (dimmed at 0, 🔕 muted) | No | No | 22 | `BottomNavBarTest` (Compose UI) | |
| Phone | `AlertsScreen` custom top bar + PROMPT/ERROR tinting | No | No | 22 | Manual — live server with mixed-severity alerts | |
| Shared | `WatchedSessionsStore` set/get/flow + profile isolation | Yes | — | 23 | `WatchedSessionsStoreTest` (5 tests: default empty, setWatched true/false, profile isolation, flow init, flow emits on change) | |
| Phone | `SessionsViewModel.toggleWatch` + `watchedIds` StateFlow | No | No | 23 | `SessionsViewModelTest` | deferred (test debt) |
| Phone | `AutonomousViewModel.toggleWatchAutomata` + `watchedAutomataIds` | No | No | 23 | `AutonomousViewModelTest` | deferred (test debt) |
| Phone | `AlertsViewModel` watched-filter (empty=all, nonempty=filter) | No | No | 23 | `AlertsViewModelTest` | deferred (test debt) |
| Phone | `BottomNavBar` watchedAlertCount badge | No | No | 23 | `BottomNavBarTest` | deferred (test debt) |
| Phone | Watch toggle menu item — sessions list + detail | No | No | 23 | Manual — tap More menu on session row | |
| Phone | Watch toggle — automata list + detail | No | No | 23 | Manual — PRD row More menu | |
| Shared | `toggleWatch_addsToWatchedIds` | Yes | No | 24 | `SessionsViewModelTest` — Sprint 23 test debt resolved in v0.94.0 | |
| Shared | `toggleWatch_removesFromWatchedIds` | Yes | No | 24 | `SessionsViewModelTest` | |
| Shared | `watchedAlertCount_reflectsWatchedSessions` | Yes | No | 24 | `AlertsViewModelTest` | |
| Shared | `bottomNavBar_selectedTabMatchesRoute` | Yes | No | 24 | `BottomNavBarTest` | |
| Phone | `PrdRow` pin button + DataStore persistence | No | No | 24 | `AutonomousViewModelTest` — pin/unpin state; DataStore write not mocked | |
| Phone | Automata sort order (pinned → state-rank → last-activity) | No | No | 24 | `AutonomousViewModelTest` | |
| Phone | `PrdRow` inline Open/Cancel/Approve actions + confirm modal | No | No | 24 | `AutonomousViewModelTest` — cancel confirm: `requestCancelId` set/cleared | |
| Phone | `TransportClient.approveAutomaton` + `cancelAutomaton` REST | No | No | 24 | `RestTransportAutonomousTest` | POST /api/prds/{id}/approve + /cancel |
| Phone | `SessionsListFilterBar` LLM collapsible + State collapsible | No | No | 24 | Manual — live session list with mixed backends/states | |
| Phone | `SessionStatsPanel` Host card (CPU sparkline + RSS sparkline) | No | No | 25 | Manual — Stats tab on a running session | 60-sample Canvas sparkline |
| Phone | `SessionStatsPanel` Container card (conditional) | No | No | 25 | Manual — session with `envelope.container != null` | |
| Phone | `SessionStatsPanel` ComputeNode card + nav link | No | No | 25 | Manual — session with `computeNodeRef` set | |
| Phone | `SessionStatsPanel` LLM card + nav link | No | No | 25 | Manual — session with `llmRef` set | |
| Phone | Session detail "Status" 4th tab — 5 s poll lifecycle | Yes | No | 26 | `SessionStatusViewModelTest` — 5 tests: fetchStatus success, failure, no-profile, stopPolling, tests-card data | |
| Phone | Status board Current Focus / Sprint / Tests / Git cards | No | No | 26 | Manual — Status tab on active claude-code session | Conditional on non-null board fields |
| Phone | Hook health pill (alive/stale/missing) | No | No | 26 | Manual — inspect board.hookHealth value | |
| Phone | Hook auto-install Snackbar (claude-code session start) | No | No | 26 | Manual — start new claude-code session | |
| Phone | `ComputeNodeEditScreen` Ollama models sub-section + marketplace | No | No | 27 | Manual — edit an ollama-kind compute node | |
| Phone | Ollama marketplace pull progress poll (2 s) | No | No | 27 | Manual — pull a model from marketplace | |
| Phone | `AlertsScreen` Active / Historical / System tabs + per-tab state persistence | No | No | 27 | Manual — switch tabs; verify filter+sort+search restored | |
| Phone | `TransportClient.getOllamaCatalog` + `pullOllamaModel` REST | No | No | 27 | `RestTransportTest` | GET catalog + POST pull |
| Phone | UnifiedPush SSE subscription + reconnect backoff | No | No | 28 | Manual — disconnect server; verify 1s→2s→…→30s reconnect | |
| Phone | Push registration (`POST /api/push/register`) on service start | No | No | 28 | Manual — check logcat for registration on app start | |
| Phone | Priority ≥ 4 event → `PRIORITY_HIGH` heads-up notification + deep-link | No | No | 28 | Manual — trigger waiting_input event | |
| Phone | `LlmConfigCard` per-node model pairs display (up to 3 before collapse) | No | No | 30 | Manual — LLM row with models[] configured | |
| Phone | Add/Edit LLM panel per-node model table (add/remove rows) | No | No | 30 | Manual — add LLM with compute node | |
| Phone | `LlmDetailDrawer` Models tab + In-use tab (pagination 5/10/50) | No | No | 30 | Manual — open LLM detail drawer | |
| Phone | LLM DELETE 409 → inline reassign prompt + force delete | No | No | 30 | Manual — delete LLM with active sessions | |
| Phone | LLM enable toggle with spinner + failure revert | No | No | 30 | Manual — toggle LLM enabled on/off | |
| Phone | Automata batch-delete confirm `AlertDialog` | No | No | 30 | `AutonomousViewModelTest` — `requestCancel` / batchDelete confirm flow | |
| Phone | `CouncilCard` persona list (built-ins + custom, Built-in badge) | No | No | 31 | Manual — Council settings section | |
| Phone | `CouncilPersonaWizardSheet` create / edit / delete persona | No | No | 31 | Manual — Add Persona + edit + delete custom | |
| Shared | `TransportClient` council persona CRUD methods | No | No | 31 | `RestTransportTest` | GET/PUT/DELETE /api/council/personas/{name} |
| Phone | `AlertDockOverlay` no-auto-expand guard (badge-only on passive alert) | No | No | 31 | Manual — receive alert passively; verify dock stays closed | Confirmed no code change required |
| Wear | `WearSyncService` publishes `/datawatch/sessions` DataItem | No | No | 32 | Manual — verify DataItem path on watch side | shortId + state + lastActivity arrays |
| Wear | `WearMainActivity` sessions list — state badge + shortId + task + timestamp | No | No | 32 | Manual — open Sessions page on watch | |
| Wear | State badge colours (teal/amber/red/dim) | No | No | 32 | Manual — verify badge colours against palette | Running #1DE9B6 · Waiting #FFB300 · Error #EF4444 |
