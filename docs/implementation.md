# Implementation notes

Per [AGENT.md](../AGENT.md) Documentation Rules, every new config field, backend, or
transport gets a dedicated entry here.

## Modules

| Module | Purpose | Sprint |
|--------|---------|--------|
| `:shared` | KMP core: transport, MCP, storage, voice, domain | 1 |
| `:composeApp` | Android phone app + flavors (`public`, `dev`) | 1 |
| `:wear` | Wear OS extension | 4 |
| `:auto` | Android Auto extension (`publicMessaging`, `devPassenger`) | 4 |
| `iosApp` | iOS skeleton pre-wired to `:shared` | 1 Day 2 |

## Shared-module packages (Sprint 1 Phase 1)

| Package | Contents |
|---------|----------|
| `com.dmzs.datawatchclient.domain` | `Session`, `SessionState`, `Principal`, `Prompt`, `ReachabilityProfile`, `ServerProfile` |
| `com.dmzs.datawatchclient.transport` | `TransportClient` interface, `TransportError` hierarchy |
| `com.dmzs.datawatchclient.transport.dto` | Wire DTOs mapped to datawatch `openapi.yaml` |
| `com.dmzs.datawatchclient.transport.rest` | Ktor-based `RestTransport` + DTO mappers |
| `com.dmzs.datawatchclient.storage` | `DatabaseFactory` (expect/actual), `ServerProfileRepository`, `SessionRepository` |
| `com.dmzs.datawatchclient.db` | SQLDelight-generated: `DatawatchDb`, `ProfileQueries`, `SessionQueries` |

## Transports

| Transport | Location | Sprint | Status |
|-----------|----------|--------|--------|
| `RestTransport` | `shared/.../transport/rest/` | 1 | Phase 1 ✅ |
| `WebSocketTransport` | `shared/.../transport/ws/` | 2 | |
| `McpSseTransport` | `shared/.../transport/mcp/` | 3 | |
| `DnsTxtTransport` | `shared/.../transport/dns/` | 3 | |
| `BackendRelay` (Intent handoff) | `shared/.../relay/` | 3 | |

## Storage

- SQLDelight generated database: `DatawatchDb` (package `com.dmzs.datawatchclient.db`).
- Schema sources: `shared/src/commonMain/sqldelight/com/dmzs/datawatchclient/db/{profile,session}.sq`.
- Current tables: `server_profile`, `reachability_profile`, `session`, `session_message`,
  `settings_kv`.
- Android driver: `AndroidSqliteDriver` (plaintext, Phase 1). Sprint 1 Phase 2 swaps to
  `sqlcipher-android` with Keystore-wrapped master key per ADR-0016.
- iOS driver: `NativeSqliteDriver` (plaintext placeholder; iOS encryption TBD).

## Settings (to be populated Sprint 1 Phase 3)

_None exposed yet._ Each setting added to `config/Settings.kt` must also appear here
with its type, default, and the five access methods per AGENT.md
"Configuration Accessibility Rule".

## Permissions

See [security-model.md](security-model.md) § "Permissions requested".

## Build variants

| Variant | applicationId | Track |
|---------|---------------|-------|
| `publicTrackDebug` | `com.dmzs.datawatchclient.debug` | local |
| `publicTrackRelease` | `com.dmzs.datawatchclient` | Play Store |
| `devDebug` | `com.dmzs.datawatchclient.dev.debug` | local |
| `devRelease` | `com.dmzs.datawatchclient.dev` | Play Console Internal only |
