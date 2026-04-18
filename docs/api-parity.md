# API + MCP Parity Matrix

Confirms the client reaches 1:1 functional parity (ADR-0005) with everything the parent
datawatch server exposes to a remote client. Source of truth: `internal/server/web/openapi.yaml`
and `docs/mcp.md` in the parent repo.

Legend:
- ✅ MVP (Sprints 1–4)
- 🧪 MVP — read-only on phone (ADR-0019 flagged exclusion)
- 🚫 Not exposed on mobile (blocked by ADR-0019)
- ⏩ Post-MVP (Sprints 5+ or next release train)
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
| `/api/config` | PUT | ⏩🚫 | Structured fields only; raw YAML blocked (ADR-0019). Token & crypto fields excluded. |
| `/api/stats` | GET | 🧪 | View-only dashboard; GPU/eBPF view-only |
| `/api/stats/kill-orphans` | POST | ✅🔐 | Confirm, no biometric in v1 |
| `/api/schedules` | GET/POST/PUT/DELETE | ✅ | Full CRUD on phone; list-only on Wear/Auto |
| `/api/interfaces` | GET | 🧪 | Read-only |
| `/api/backends` | GET | ✅ | Show availability; drives "configure" flows |
| `/api/memory/*` | GET/POST | ✅ | Memory tab in session detail |
| `/ws` | WebSocket | ✅ | Realtime session output + state |
| `/api/test/message` | GET | ✅ | "Test" button in channel settings |
| `/api/docs` | GET | ⏩ | External link only (Swagger in browser) |
| `/mcp/sse` | SSE | ✅ | MCP tool invocation (see MCP table) |
| `/api/devices/register` (proposed) | POST | ✅ | Tracked upstream: [dmz006/datawatch#1](https://github.com/dmz006/datawatch/issues/1). Fallback = ntfy-only subscription. |
| `/api/voice/transcribe` (proposed) | POST | ✅ | Tracked upstream: [dmz006/datawatch#2](https://github.com/dmz006/datawatch/issues/2). Fallback = Telegram-path reuse. |

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

All three raised as issues on the parent project — the mobile app ships the documented
workarounds until they land.

| # | Issue | Mobile sprint that uses it | Workaround if not available |
|---|---|---|---|
| 1 | [dmz006/datawatch#1](https://github.com/dmz006/datawatch/issues/1) — device registration | Sprint 2 | ntfy-only subscription; user configures ntfy backend on server |
| 2 | [dmz006/datawatch#2](https://github.com/dmz006/datawatch/issues/2) — voice transcribe | Sprint 3 | Telegram-path reuse with fake chat_id |
| 3 | [dmz006/datawatch#3](https://github.com/dmz006/datawatch/issues/3) — federation fan-out | Post-MVP (Sprint 5+) | Client-side parallel per-profile loop |

When an upstream issue is merged + released, the mobile app's corresponding transport
switches to the new endpoint in the next minor version; the workaround path stays in
code behind a feature flag for a full release cycle before removal.
