# Implementation notes

Per [AGENT.md](../AGENT.md) Documentation Rules, every new config field, backend, or
transport gets a dedicated entry here. Pre-MVP scaffold — this is the index skeleton.

## Modules

| Module | Purpose | Sprint |
|--------|---------|--------|
| `:shared` | KMP core: transport, MCP, storage, voice, domain | 1 |
| `:composeApp` | Android phone app + flavors (`public`, `dev`) | 1 |
| `:wear` | Wear OS extension | 4 |
| `:auto` | Android Auto extension (`publicMessaging`, `devPassenger` flavors) | 4 |
| `iosApp` | iOS skeleton pre-wired to `:shared` | 1 Day 2 |

## Transports (v0.1.0-pre placeholders)

| Transport | Location | Sprint |
|-----------|----------|--------|
| `RestTransport` | `shared/.../transport/rest/` | 1 |
| `WebSocketTransport` | `shared/.../transport/ws/` | 2 |
| `McpSseTransport` | `shared/.../transport/mcp/` | 3 |
| `DnsTxtTransport` | `shared/.../transport/dns/` | 3 |
| `BackendRelay` (Intent handoff) | `shared/.../relay/` | 3 |

## Settings (to be populated Sprint 1+)

_None yet._ Each setting added to `config/Settings.kt` must also appear here with its
type, default, and the five access methods per AGENT.md "Configuration Accessibility Rule".

## Permissions

See [security-model.md](security-model.md) § "Permissions requested".

## Build variants

| Variant | applicationId | Track |
|---------|---------------|-------|
| `publicTrackDebug` | `com.dmzs.datawatchclient.debug` | local |
| `publicTrackRelease` | `com.dmzs.datawatchclient` | Play Store |
| `devDebug` | `com.dmzs.datawatchclient.dev.debug` | local |
| `devRelease` | `com.dmzs.datawatchclient.dev` | Play Console Internal only |
