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
| iOS-skel | `:shared` framework link | No | No | 1 | | skeleton only |

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
