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
| Shared | `RestTransport` happy path | No | No | 1 | | Phase 4 — MockWebServer |
| Shared | `RestTransport` 401 → Unauthorized | No | No | 1 | | Phase 4 |
| Shared | `RestTransport` 5xx → ServerError | No | No | 1 | | Phase 4 |
| Shared | `RestTransport` network error → Unreachable | No | No | 1 | | Phase 4 |
| Shared | `ServerProfileRepository` CRUD | No | No | 1 | | Phase 4 — in-memory SqlDriver |
| Shared | `SessionRepository` upsert + observe | No | No | 1 | | Phase 4 |
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
