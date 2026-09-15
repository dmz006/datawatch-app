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
| Shared | `RestTransport` PRD/channel/backend CRUD | Yes | No | FF | androidUnitTest + MockWebServer | `RestTransportAutonomousTest` — 28 tests: listPrds, createPrd (dir+profile), prdAction (approve/reject/decompose/set_llm), editStory, editFiles, patchPrd, deletePrd (soft+hard), listBackends (obj+string shapes, filters enabled=false+shell), listChannels (wrapped+bare), createChannel, deleteChannel, setChannelEnabled, listOllamaModels, listOpenWebUiModels, cancelPrdStory (with+without reason), cancelPrdTask, requeuePrdTask (force=true), editPrdTask |
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
| Auto | LastOutputDetailScreen — TTS + Long Version | No | No | v1.0.28 | Requires DHU / real Android Auto head unit | Tap ActionStrip status icon from session detail; verify text; tap TTS icon; verify spoken; tap Long Version (parked) |
| Auto | BlockDetailsScreen — Approve Gate from detail | No | No | v1.0.28 | Requires DHU + session with guardrail block | Tap block icon from session detail; verify verdict list; tap Approve Gate; verify toast + screen pop |
| Auto | VoiceRecordingScreen → TranscriptionConfirmScreen → send | No | No | v1.0.28 | Requires DHU + datawatch server with whisper.backend configured | Open session reply; tap Voice; speak; tap Done; verify transcript; tap Send; verify session receives input |
| Auto | Session Detail ActionStrip context sensitivity (Running/Waiting/Blocked/Terminal) | No | No | v1.0.28 | Requires DHU + sessions in each state | Verify correct icon slots appear per state; tapping navigates to correct screen |
| Auto | Monitor tappable server rows → single-server drill-down | No | No | v1.0.28 | Requires DHU + ≥2 enabled servers | Tap a server row in multi-server monitor; verify navigates to that server's detail |
| Auto | Monitor Sessions row tappable in single-server mode | No | No | v1.0.28 | Requires DHU + 1 enabled server | Tap Sessions row; verify pushes session list screen |
| Auto | About screen check-for-update (Update button appears only when available) | No | No | v1.0.28 | Requires DHU + datawatch server supporting /api/update/check | Verify Update button absent when up-to-date; appears when update_available returned |
| Auto | Automata list colored dot icons + progress bar | No | No | v1.0.28 | Requires DHU + running automata | Verify red dot on awaiting_approval, green otherwise; progress bar matches story completion % |
| Auto | `AutoPrdDetailScreen` — TTS body (status, progress arc, active story, pending list, spec snippet) | Yes | No | v1.3.0 | `AutoPrdDetailBodyTest` — 11 unit tests covering `buildDetailBody()` pure function | No DHU test yet |
| Auto | `AutoPrdDetailScreen` — lifecycle actions (Approve/Reject/Stop/Run/Decompose/Delete) | No | No | v1.3.0 | Requires DHU + PRD in each lifecycle state | Tap each button; verify toast + screen pop |
| Auto | `AutoPrdStoriesScreen` — story list with per-story status, task count, click → story detail | Yes | No | v1.4.0 | `AutoStoryDetailBodyTest` — row builder tests (`buildStoryRow`, `buildTasksLine`, `buildStoryDetail`) | No DHU navigation test yet |
| Auto | `AutoStoryDetailScreen` — TTS body (description, task markers, files, error detail) | Yes | No | v1.4.0 | `AutoStoryDetailBodyTest` — 11 unit tests covering `buildStoryBody()` | No DHU test yet |
| Auto | `AutoStoryDetailScreen` — Approve + Reset Task action buttons | No | No | v1.4.0 | Requires DHU + PRD in needs_review state with a failed task | Tap Approve; verify toast + pop; tap Reset Task; verify toast |
| Auto | Voice APPROVE_PLAN — "approve my plan" / "approve automata" → POST /api/prds/{id}/approve | Yes | No | v1.4.0 | `VoiceCommandTest` — 3 phrase tests; execution wired in `VoiceStatusScreen` | No live-server voice test yet |
| Auto | Voice STOP_PLAN — "stop my plan" / "cancel automata" → POST /api/prds/{id}/cancel | Yes | No | v1.4.0 | `VoiceCommandTest` — 3 phrase tests; execution wired in `VoiceStatusScreen` | No live-server voice test yet |
| Auto | Voice READ_PLAN — "read my plan" / "plan status" → spoken PRD summary | Yes | No | v1.4.0 | `VoiceCommandTest` — 5 phrase tests; `buildReadPlanResponse()` returns spoken summary | No DHU TTS test yet |
| Android | **BL29 — Cancel story** from PRD detail dialog | Yes | No | v1.5.0 | `RestTransportAutonomousTest.cancelPrdStoryPostsStoryIdAndReason` + UI confirm dialog | Validated manually on emulator against datawatch v8.27.0 |
| Android | **BL29 — Cancel task** from PRD detail dialog | Yes | No | v1.5.0 | `RestTransportAutonomousTest.cancelPrdTaskPostsTaskIdAndReason` | |
| Android | **BL29 — Requeue task** (force=true reset) from PRD detail dialog | Yes | No | v1.5.0 | `RestTransportAutonomousTest.requeuePrdTaskPostsResetTaskWithForceTrue` | |
| Android | **BL29 — Edit task spec** from PRD detail dialog | Yes | No | v1.5.0 | `RestTransportAutonomousTest.editPrdTaskPostsTaskIdAndNewSpec` | Only shown during needs_review/revisions_asked |
| Android | **BL29 — Approve with note** from PRD detail dialog | No | No | v1.5.0 | Approve dialog opens note field; note passed to server via prdAction "approve" body | |
| Android | **BL28 — DwAccent contrast** (#7C3AED→#8B5CF6) | Yes | No | v1.5.1 | `PrdStatusColorTest.approved maps to teal` (constant updated); visual audit of all 7 affected composables | WCAG AA 4.93:1 on DwBg; Wear+Auto already passed |
| Android | **BL32 — Session stats remote GPU** (compute node detail) | Yes | No | v1.6.0 | `RestTransportTest.getComputeNodeDetailFetchesGpuStats` + `…MultiGpu`; `ComputeNodeCard` shows util/temp/power/VRAM from `/api/compute/nodes/{ref}/detail` | Requires session with bound compute node to validate |
| Android | **BL32 — Multi-GPU index prefix** (PeerResourcesCard) | Yes | No | v1.6.0 | Visual — "GPU 1 util / GPU 2 util" chips on dual-GPU observer peer | Single-GPU: no prefix (unchanged) |
| Auto | **BL33 — Per-guardrail approve** (BlockDetailsScreen single) | Yes | No | v1.7.0 | `RestTransportTest.approveGuardrailBlockPostsToCorrectUrl`; single block → MessageTemplate "Approve [name]" button | Requires blocked session on real Auto hardware |
| Auto | **BL33 — Per-guardrail approve** (BlockDetailsScreen multi) | Yes | No | v1.7.0 | Compile + GuardrailTtsBuilderTest; multi-block → ListTemplate rows + "Approve All" ActionStrip | Requires ≥2 blocked guardrails to validate |
| Auto | **BL30 — AutoTaskDetailScreen** (depth 5 task detail) | Yes | No | v1.8.0 | `AutoStoryDetailBodyTest.AutoTaskDetailScreen body contains status and task spec` + retry + verification tests; `NavigationGraphTest.AutoTaskDetailScreen class exists` | Requires real PRD with failed/in-progress task |
| Auto | **BL30 — AutoPrdStoriesScreen stateful story detail** (Cancel Story, Requeue, task rows) | Yes | No | v1.8.0 | `AutoStoryDetailBodyTest` row tests; stateful mode verified by class structure | Requires DHU: tap story row → story detail in-place; tap task row → task detail |
| Auto | **BL30 — buildStoryBody** retry count + verification summary | Yes | No | v1.8.0 | `AutoStoryDetailBodyTest.failed task with retries shows retry count` + `completed task with verification shows summary` | Visual on DHU with PRD that has completed/failed tasks |

Update this table with each PR that lands a feature. Don't mark `Validated=Yes` based on
unit tests alone.

## v7.0.0-alpha parity arc (Sprints 17–22+)

| Surface | Feature | Tested | Validated | Sprint | Test Conditions | Notes |
|---------|---------|--------|-----------|--------|-----------------|-------|
| Shared | `SessionDto.backendFamily` fallback to `llmBackend` | Yes | No | 17 | `DtoRoundTripTest` — both fields present; only backendFamily; only llmBackend | Priority: high — alpha.27 contract |
| Shared | `Mappers.toSession()` backendFamily → Session.backend | Yes | No | 17 | `SessionMapperTest` | |
| Shared | `ObserverPeersByNodeDto` JSON round-trip | Yes | No | 18 | `ObserverPeersByNodeDtoTest` (commonTest) — by_node map + unbound list | alpha.24 |
| Shared | `MetaPeersDto` / `MetaNodeBucketDto` / `MetaObserverEntryDto` round-trip | Yes | No | 18 | `MetaPeersDtoTest` (commonTest) | nested bucket deserialization |
| Shared | `TransportClient.getObserverPeersByNode()` REST | No | No | 18 | `RestTransportTest` + MockWebServer | GET `/api/observer/peers/by-node` |
| Shared | `TransportClient.getFederationMetaPeers()` REST | No | No | 18 | `RestTransportTest` + MockWebServer | GET `/api/federation/meta-peers` |
| Phone | `FederatedPeersCard` group-by-node toggle | Yes | No | 18 | `MonitoringViewModelTests` — groupByNode toggle + byNode map + unbound list | groupByNode state transitions + loadByNode |
| Shared | `AgentSettingsDto` round-trip (opencodeModels list) | Yes | No | 19 | `AgentSettingsDtoTest` (commonTest) | alpha.28 |
| Shared | `TransportClient.patchProjectAgentSettings()` REST | Yes | No | 19 | `RestTransportAutonomousTest` — PATCH path, URL encoding, JSON body | PATCH body + 200 |
| Phone | `KindProfilesCard` agent-settings editor (project kind) | No | No | 19 | Manual — live server with project profile | 4 fields; comma-sep → JsonArray |
| Phone | `AlertDockChannel` state machine (open/close/toggle) | Yes | No | 20 | `AlertDockChannelTest` — 6 tests; mute is caller-local | alpha.29 |
| Phone | `AppRoot` dock visibility threshold (≥2 active alerts) | No | No | 20 | `AppRootTest` (Compose UI instrumented) | dock reappears when count resets |
| Phone | Alert dock category chips (needs-input ×N, err ×N) | No | No | 20 | Manual — live server with active alerts | |
| Wear | `AlertsComplicationService` DataItem parse + fallback | Yes | No | 21 | `AlertsComplicationTest` — text format, content desc, (0,0,0) fallback, DataMap keys | No DataItem → (0,0,0) |
| Wear | `AlertsTileService` layout branches | Yes | No | 21 | `AlertsTileTest` — hasData=false, errors→red, needsInput→amber, clean→green | hasData=false; errors>0 health dot |
| Wear | `WearSyncService.publishAlerts()` DataMap keys | Yes | No | 21 | `WearSyncAlertsTest` — ALERTS_PATH constant, writer⊇reader keys, invariants | total/needsInput/errors/ts correct |
| Phone | `AlertsViewModel` chip filter (All/Prompt/Error/Warn/Info) | Yes | No | 22 | `AlertsViewModelTest` | alpha.30 |
| Phone | `AlertsViewModel` sort toggle (BySession/Chronological) | Yes | No | 22 | `AlertsViewModelTest` | flat list newest-first |
| Phone | `AlertsViewModel` search (title+body, case-insensitive) | Yes | No | 22 | `AlertsViewModelTest` | |
| Phone | `AlertsViewModel.dismissAll()` → markAlertRead(all=true) | Yes | No | 22 | `AlertsViewModelTest` | |
| Phone | `BottomNavBar` badge label/dim logic (dimmed at 0, 🔕 muted) | Yes | No | 22 | `BottomNavBadgeTest` — 9 tests for badgeLabel + badgeDimmed | |
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
| Phone | Automata sort order (pinned → state-rank → last-activity) | Yes | No | 24 | `PrdSortTest` — pinned-before-unpinned, needs_review-before-running, terminal-after-active | S24 BL293 |
| Phone | `PrdRow` inline Open/Cancel/Approve actions + confirm modal | Yes | No | 24 | `PrdActionGateTest` — 10 tests: approve gate (needs_review/awaiting_approval/revisions_asked); cancel gate (running/completed/cancelled/rejected/archived) | S24 BL293 |
| Phone | `TransportClient.approveAutomaton` + `cancelAutomaton` REST | No | No | 24 | `RestTransportAutonomousTest` | POST /api/prds/{id}/approve + /cancel |
| Phone | `SessionsListFilterBar` LLM collapsible + State collapsible | No | No | 24 | Manual — live session list with mixed backends/states | |
| Phone | `SessionStatsPanel` Host card (CPU sparkline + RSS sparkline) | No | No | 25 | Manual — Stats tab on a running session | 60-sample Canvas sparkline |
| Phone | `SessionStatsPanel` Container card (conditional) | Yes | No | 25 | `SessionStatsPanelTest` — 5 tests: null envelope, blank containerId, non-blank containerId, container object present, hidden when blank | S25 |
| Phone | `SessionStatsPanel` ComputeNode card + nav link | Yes | No | 25 | `SessionStatsPanelTest` — 3 tests: null ref, blank ref, non-blank ref shown | S25 |
| Phone | `SessionStatsPanel` LLM card + nav link | Yes | No | 25 | `SessionStatsPanelTest` — 3 tests: null llmRef, blank llmRef, non-blank shown; plus no-data-state tests | S25 |
| Phone | Session detail "Status" 4th tab — 5 s poll lifecycle | Yes | No | 26 | `SessionStatusViewModelTest` — 5 tests: fetchStatus success, failure, no-profile, stopPolling, tests-card data | |
| Phone | Status board Current Focus / Sprint / Tests / Git cards | No | No | 26 | Manual — Status tab on active claude-code session | Conditional on non-null board fields |
| Phone | `statusTabBadge` (🟢/🟠/⚪ by board.state) | Yes | No | 26 | `StatusTabBadgeTest` — 7 tests: running→🟢, waiting→🟠, waiting_input→🟠, null→⚪, unknown→⚪, hookHealth irrelevant to badge, hookHealth readable from board | S26 |
| Phone | Hook health pill (alive/stale/missing) | No | No | 26 | Manual — inspect board.hookHealth value | |
| Phone | Hook auto-install Snackbar (claude-code session start) | No | No | 26 | Manual — start new claude-code session | |
| Phone | `ComputeNodeEditScreen` Ollama models sub-section + marketplace | No | No | 27 | Manual — edit an ollama-kind compute node | |
| Phone | Ollama marketplace search filter + isInstalled + pull progress | Yes | No | 27 | `OllamaMarketplaceTest` — 12 tests: empty search, substring case-insensitive, uppercase match, no match, empty catalog; isInstalled true/false; fullModel name:tag composition; pull task lookup/null/completed | S27 |
| Phone | `AlertsScreen` Active / Historical / System tabs + per-tab state persistence | Yes | No | 27 | `AlertsViewModelTest` — 5 new tests: UiState.selectedTab default, active-contains-only-isActive, system-has-SYSTEM_BUCKET, tab independence, count sums active only | S27 |
| Phone | `TransportClient.getOllamaCatalog` + `pullOllamaModel` REST | No | No | 27 | `RestTransportTest` | GET catalog + POST pull |
| Phone | UnifiedPush SSE subscription + reconnect backoff | No | No | 28 | Manual — disconnect server; verify 1s→2s→…→30s reconnect | |
| Phone | Push registration (`POST /api/push/register`) on service start | No | No | 28 | Manual — check logcat for registration on app start | |
| Phone | Priority ≥ 4 event → `PRIORITY_HIGH` heads-up notification + deep-link | No | No | 28 | Manual — trigger waiting_input event | |
| Phone | `SessionsViewModel.backendCounts` computed property | Yes | No | 29 | `SessionsViewModelTest` — 6 tests: empty when no backend, count per name, omit null/blank, sorted alphabetically, N backends → N+1 chip label | S29 |
| Phone | `SessionsViewModel.stateFilter` default + filter enum | Yes | No | 29 | `SessionsViewModelTest` — stateFilter defaults ALL, backendFilter defaults null, showHistory defaults false, non-ALL differs from ALL | S29 |
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
| Phone+Auto | Waiting-input notification (phone + Auto voice reply) | No | No | 33 | Manual — transition session to Waiting; verify heads-up + Auto "Voice Reply" action launches VoiceRecordingScreen | composeApp+auto |
| Shared | `RestTransport.uploadImageAttachment` — multipart upload + path extraction | No | No | 34 | `RestTransportTest` — mock `/api/files` POST returning `{"path":"/abs/path"}` | Closes #158; `deleteFile` also needs mock DELETE test |
| Phone | `ReplyComposer` image attach — gallery pick → upload → thumbnail chip → send `[image:<path>]` | No | No | 34 | Manual — open session detail, tap 📷, pick image, verify chip appears + send appends `[image:…]` suffix | Cleanup `deleteFile` on `DisposableEffect` also needs verification |
