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
- Android driver: `AndroidSqliteDriver` wrapping `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`
  — AES-256 at rest with HMAC-SHA256 page integrity. Passphrase derived on open from the
  Android Keystore master key via [`KeystoreManager.deriveDatabasePassphrase`]. Requires
  `System.loadLibrary("sqlcipher")` at process start (done in `DatawatchApp.onCreate`).
- iOS driver: `NativeSqliteDriver` (plaintext placeholder; iOS encryption TBD when the
  iOS content phase begins).

## Security primitives (Sprint 1 Phase 2)

| Type | File | Role |
|------|------|------|
| `KeystoreManager` | `shared/src/androidMain/.../security/KeystoreManager.kt` | Master-key lifecycle; DB passphrase derivation; StrongBox-preferred |
| `TokenVault` | `shared/src/androidMain/.../security/TokenVault.kt` | EncryptedSharedPreferences-backed bearer-token storage, alias-keyed per profile |
| `DatabaseFactory` (Android actual) | `shared/src/androidMain/.../storage/AndroidDatabaseFactory.kt` | Opens SQLCipher-encrypted SQLite DB through SQLDelight |

Bearer tokens never appear in the SQLite database; `ServerProfile.bearerTokenRef`
contains only the alias string ("dw.profile.<id>") that the transport layer's
`tokenProvider` lambda resolves through `TokenVault.get()`.

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
