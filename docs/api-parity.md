# API + MCP Parity Matrix

Confirms the client reaches 1:1 functional parity (ADR-0005) with
everything the parent datawatch server exposes to a remote client.

*Last updated 2026-04-22 for v0.33.0.*

**Source of truth is the parent PWA** (`internal/server/web/app.js`),
not `openapi.yaml` — the OpenAPI spec lags shipped endpoints by a few
releases (pattern recognized 2026-04-20 when we filed-and-closed
upstream issues for endpoints that already existed). Always grep
`app.js` to confirm current endpoint availability.

Legend:
- ✅ Shipped
- 🧪 Read-only on mobile (ADR-0019 flagged exclusion)
- 🚫 Not exposed on mobile (blocked by ADR-0019)
- 🚧 Pending upstream
- 🔐 Requires extra confirmation dialog

## REST endpoints

| Endpoint | Method | Mobile coverage | Notes |
|---|---|---|---|
| `/api/health` | GET | ✅ | Transport ping |
| `/api/sessions` | GET | ✅ | List (active server + all-servers fan-out) |
| `/api/sessions/start` | POST | ✅ | "new:" command or Quick Command |
| `/api/sessions/kill` | POST | ✅🔐 | Confirm dialog |
| `/api/sessions/state` | POST | ✅🔐 | State override; confirm dialog |
| `/api/sessions/reply` | POST | ✅ | Chat composer + voice + notification reply |
| `/api/config` | GET | ✅ | Settings → Server config (read) |
| `/api/config` | PUT | ✅ | BehaviourPreferencesCard (v0.20), BackendConfigDialog (v0.21), DetectionFiltersCard (v0.32). Structured fields only; raw YAML blocked (ADR-0019). |
| `/api/stats` | GET | 🧪 | Stats tab — CPU / mem / disk / GPU. eBPF per-process viewer deferred ⏳ post-1.0 |
| `/api/sessions/rename` | POST | ✅ | Sessions row overflow + inline header tap (v0.11) |
| `/api/sessions/restart` | POST | ✅ | Sessions row overflow + inline (v0.11) |
| `/api/sessions/delete` | POST | ✅🔐 | Single + long-press multi-select (v0.11) |
| `/api/sessions/reorder` | POST | ✅ | Reorder mode ⇅ toggle (v0.31) |
| `/api/sessions/timeline` | GET | ✅ | Per-session timeline sheet (v0.13.1) |
| `/api/stats/kill-orphans` | POST | ✅🔐 | Confirm, no biometric in v1 |
| `/api/schedules` | GET/POST/PUT/DELETE | ✅ | Full CRUD on phone; list-only on Wear/Auto |
| `/api/interfaces` | GET | ✅ | Settings → Monitor (v0.16) |
| `/api/logs` | GET | ✅ | Settings → Monitor, 10 s auto-refresh (v0.16) |
| `/api/restart` | POST | ✅🔐 | Settings → Monitor, confirm dialog (v0.16) |
| `/api/update` | POST | ✅🔐 | Settings → Monitor, progress bar (v0.22.1 / v0.32) |
| `/api/backends` | GET | ✅ | Show availability; drives "configure" flows |
| `/api/backends/active` | POST | ✅ | Settings → LLM radio picker (v0.11) |
| `/api/ollama/models` + `/api/openwebui/models` | GET | ✅ | Model selector (v0.13.1 / v0.21) |
| `/api/memory/*` | GET/POST | ✅ | Memory tab (v0.17); export via SAF (v0.22) |
| `/api/channels` + `/api/channels/{id}` | GET/PATCH | ✅ | ChannelsCard (v0.18). POST returns 501 upstream ([#18](https://github.com/dmz006/datawatch/issues/18)). |
| `/api/channel/send` | POST | ✅ | "Test" button per channel (v0.18) |
| `/api/cert` | GET | ✅ | Settings → Servers overflow + CertInstallCard (v0.11 / v0.32) |
| `/api/info` | GET | ✅ | About card "Connected to" row (v0.11) |
| `/api/files` | GET | ✅ | New Session directory picker (v0.12) |
| `/api/output` | GET | ✅ | Terminal Load-backlog toolbar (v0.12) |
| `/api/profiles` + `/api/profiles/<kind>s/<name>` | GET/PUT | ✅ | Profile picker + KindProfilesCard create/edit (v0.15 / v0.32) |
| `/api/mcp/docs` | GET | ✅ | McpToolsCard in Settings → About (v0.32) |
| `/ws` | WebSocket | ✅ | Realtime session output + state; outbound `resize_term` frames (v0.23) |
| `/api/docs` | GET | ✅ | External link in ApiLinksCard (v0.29) |
| `/mcp/sse` | SSE | ✅ | MCP tool invocation (see MCP table) |
| `/api/devices/register` | POST | ✅ | Shipped parent v4.0.3; integrated v0.11 |
| `/api/voice/transcribe` | POST | ✅ | Shipped parent v4.0.3; integrated v0.11–v0.12 |
| `/api/federation/sessions` | GET | ✅ | All-servers view |

## MCP tools (37)

Grouped by the categories listed in `docs/mcp.md`.

### Session management (6)

| Tool | Coverage | Surface(s) |
|---|---|---|
| `session_list` | ✅ | Phone home, Wear complication, Auto summary |
| `session_new` | ✅ | Phone FAB, voice "new: ...", Auto voice |
| `session_send` | ✅ | Phone chat, voice, Wear reply, Auto reply |
| `session_status` | ✅ | Phone detail, Wear notification body |
| `session_kill` | ✅🔐 | Phone only (confirm) |
| `session_reply` | ✅ | Phone / Wear / Auto reply paths |

### Memory (5)

| Tool | Coverage | Surface(s) |
|---|---|---|
| `memory_recall` | ✅ | Phone memory tab, voice query |
| `memory_remember` | ✅ | Phone memory tab + long-press chat "Remember" |
| `memory_research` | ⏩ | Phone only; post-MVP (heavier UI) |
| `memory_learnings` | ✅ | Phone memory tab |
| `memory_remember_session` | ✅ | Phone session menu |

### Knowledge graph (4)

| Tool | Coverage | Surface(s) |
|---|---|---|
| `kg_query` | ✅ | Phone memory tab → graph view |
| `kg_add` | ⏩ | Phone only; post-MVP (complex UI) |
| `kg_timeline` | ✅ | Phone memory tab → timeline |
| `kg_stats` | 🧪 | Phone stats tab |

### System + config (remaining ~22)

| Tool | Coverage | Surface(s) |
|---|---|---|
| `system_stats` | 🧪 | Phone stats, Wear complication count, Auto status |
| `backend_list` | ✅ | Settings → Channels |
| `backend_enable`/`disable` | ✅ | Settings → Channels |
| `config_get` / `config_set` | ✅ | Settings (structured only) |
| `schedule_list`/`add`/`remove`/`update` | ✅ | Schedules tab |
| Other admin tools (orphans, backups, restart, encryption config) | 🚫/⏩ | Blocked or deferred; see ADR-0019 |

The final list of 37 is pulled from the parent repo at scaffold time — any tool added
upstream later is appended to a follow-up ADR rather than silently shipped.

## Voice vs text parity

Every text command the server accepts has a voice path via the voice pipeline (ADR-0006,
ADR-0026). The voice path never creates a server capability that doesn't exist in text
form — voice is input modality only.

## Intent-handoff relay parity

When direct API is unreachable (ADR-0004 fallback), the app composes an outbound message
equivalent to the REST call:

| REST | Relay text | Channel |
|---|---|---|
| `POST /api/sessions/start` | `new: <task>` | Whatever the user's profile prefers (Signal/SMS/Slack) |
| `POST /api/sessions/reply` | `reply <session_id>: <text>` | Same |
| `GET /api/stats` | `status` | Same |

The datawatch server's existing messaging backends already parse these commands, so no
new server endpoints are required for relay fallback.

## Upstream-tracked parity items (ADR-0039)

As of v0.33.11 there are no open upstream items blocking mobile parity:

| # | Issue | Status | Mobile integration |
|---|---|---|---|
| 18 | [dmz006/datawatch#18](https://github.com/dmz006/datawatch/issues/18) — `POST /api/channels` | ✅ Closed | Wired in v0.33.11: ChannelsCard + Add button + AddChannelDialog + DELETE per-row |
| 19 | [dmz006/datawatch#19](https://github.com/dmz006/datawatch/issues/19) — `applyConfigPatch` `autonomous.*` / `plugins.*` / `orchestrator.*` cases | ✅ Closed | v0.33.11 schema updated to match all new server-writable keys. Autosave now actually persists those fields (S9 closed). |

Upstream gaps will continue to be filed as issues on
`dmz006/datawatch` per ADR-0039 when discovered.
