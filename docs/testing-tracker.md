# Testing Tracker

Per [AGENT.md](../AGENT.md) Testing Tracker Rules — every surface / transport / backend
gets two levels of validation:

- **Tested** (`Yes` / `No`): unit / integration tests exist and pass under `./gradlew test`.
- **Validated** (`Yes` / `No`): live end-to-end confirmed on a real device against a real
  datawatch server. Document environment in *Test Conditions*.

Pre-MVP scaffold — everything rolls out in the sprints enumerated in
[sprint-plan.md](sprint-plan.md). No rows are marked Yes yet.

## Interfaces

| Surface | Feature | Tested | Validated | Sprint | Test Conditions | Notes |
|---------|---------|--------|-----------|--------|-----------------|-------|
| Phone | Server profile add + keystore bind | No | No | 1 | | |
| Phone | REST transport (sessions list) | No | No | 1 | | |
| Phone | WebSocket `/ws` stream | No | No | 2 | | |
| Phone | xterm.js WebView | No | No | 2 | | |
| Phone | Voice capture (all 4 surfaces) | No | No | 3 | | |
| Phone | MCP SSE tool invocation | No | No | 3 | | |
| Phone | Intent-relay fallback (Signal / SMS) | No | No | 3 | | |
| Phone | FCM wake (dumb-ping) | No | No | 2 | | depends on dmz006/datawatch#1 |
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
