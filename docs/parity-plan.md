# Parity Plan — catching mobile up to the PWA

**Context.** v0.10.0 (originally mis-tagged v1.0.0 as "first production
release") closed Sprint 6's ADR-0042 scope, but the PWA
(`internal/server/web/` in the parent repo) exposes a broader feature
surface than the mobile app currently wires. **v1.0.0 is reserved for the
release that closes this plan — every row ✅.** This document is the
honest accounting of the gap, grouped by PWA screen, plus a concrete
sprint plan to close it.

**Source of truth.** PWA HTML + `app.js` live in the parent repo at
`internal/server/web/`. API endpoints enumerated in
`docs/api/openapi.yaml` and the server source under `internal/server/`.

Legend: ✅ shipped · 🚧 in progress · ⏳ planned · ❌ not started

---

## 1. Sessions view (`data-view="sessions"`)

| PWA capability | Mobile | Notes |
|---|---|---|
| Live session list | ✅ | SessionsScreen + WS stream |
| State filter chips | ✅ | All / Running / Waiting / Completed / Error |
| Multi-server picker | ✅ | Top-bar dropdown + 3-finger gesture |
| All-servers fan-out (`/api/federation/sessions`) | ✅ | "All servers" row |
| Per-row swipe-to-mute | ✅ | |
| Session rename (`/api/sessions/rename`) | ❌ | v0.11 |
| Session restart (`/api/sessions/restart`) | ❌ | v0.11 |
| Bulk delete completed (`/api/sessions/delete`) | ❌ | v0.11 |
| Per-row timeline view (`/api/sessions/timeline`) | ❌ | v0.12 |
| Sort by last activity / start time | ❌ | v0.11 |
| Pagination / "load more" | ❌ | v0.12 |
| Schedule: list pending for a session (`/api/schedules`) | ❌ | v0.12 |

## 2. Session detail

| PWA capability | Mobile | Notes |
|---|---|---|
| xterm.js terminal as primary view | ✅ | Shipped in v0.10.1 (terminal swap) |
| ANSI colour rendering | ✅ | xterm built-in |
| 5000-line scrollback | ✅ | xterm config in `host.html` |
| Reply composer | ✅ | |
| Voice reply (Whisper) | ✅ | |
| Kill session (confirm dialog) | ✅ | |
| State override | ✅ | |
| Mute per-session | ✅ | |
| Rename session | ❌ | v0.11 |
| Restart session | ❌ | v0.11 |
| Delete session | ❌ | v0.11 |
| Terminal copy action | ❌ | xterm selection works but no clipboard button |
| Terminal search (`xterm-addon-search`) | ❌ | v0.11 |
| Inline schedule actions (create scheduled reply) | ❌ | v0.12 |
| Backlog pager (`/api/sessions/timeline`) | ❌ | v0.12 |
| Prompt + rate-limit inline banners | ✅ | InlineNotices |

## 3. New session (`data-view="new"`)

| PWA capability | Mobile | Notes |
|---|---|---|
| Start session from form (`/api/sessions/start`) | ❌ | v0.11 — transport has `startSession`, UI missing |
| Pick LLM backend (`/api/backends`) | ✅ (read-only) | Channels tab; per-session backend selection during start is v0.11 |
| Pick model variant | ❌ | v0.11 — needs `/api/ollama/models` + `/api/openwebui/models` |
| Pick profile (`/api/profiles`) | ❌ | v0.11 — F10 ephemeral-agent profiles |
| Directory picker (`/api/files`) | ❌ | v0.12 |
| Voice-to-new-session | ❌ | v0.12 — composer mic exists; "new:" prefix auto-exec wiring needed |

## 4. Alerts (`data-view="alerts"`)

| PWA capability | Mobile | Notes |
|---|---|---|
| Pending-prompt list | ✅ | AlertsScreen |
| Badge count on nav icon | ✅ | |
| Mark-as-read / dismiss | ❌ | v0.11 — swipe-to-mute exists; explicit dismiss missing |
| `/api/alerts` read | ❌ | v0.11 — mobile currently derives from session list; PWA reads a dedicated endpoint for richer metadata |
| Schedule actions from alerts | ❌ | v0.12 |

## 5. Settings (`data-view="settings"`)

The biggest single gap — PWA's Settings is a power-user control surface.
Mobile currently covers Servers + Security + About + Comms placeholder.

### 5a. Servers + federation

| PWA | Mobile | Notes |
|---|---|---|
| List servers (`/api/servers`) | ✅ | ServersCard |
| Add / edit / delete | ✅ | AddServer + EditServer screens |
| Per-server health indicator | ✅ | Status dot in picker |
| Federated server list (read-only) | ❌ | v0.12 — shows peers of a server |

### 5b. LLM backend config

| PWA | Mobile | Notes |
|---|---|---|
| Pick active backend | ❌ | v0.11 — `POST /api/backends/active` (parent to confirm shape) |
| Edit endpoint URL / API key per backend | ❌ | v0.12 — structured form per ADR-0019 |
| Pick Ollama model (`/api/ollama/models`) | ❌ | v0.12 |
| Pick OpenWebUI model (`/api/openwebui/models`) | ❌ | v0.12 |

### 5c. Channels (messaging backends)

| PWA | Mobile | Notes |
|---|---|---|
| List configured channels | ❌ | v0.12 — awaits parent `/api/channels` exposure |
| Add / remove channel | ❌ | v0.12 |
| Test message round-trip (`/api/channel/send`) | ❌ | v0.12 |
| Per-channel enable / disable | ❌ | v0.12 |
| Download CA cert (`/api/cert`) | ❌ | v0.11 — drives self-signed TLS trust |

### 5d. Daemon control + introspection

| PWA | Mobile | Notes |
|---|---|---|
| Show daemon version (`/api/health`) | ❌ | v0.11 — trivial; add to About card |
| Connection status indicator | ❌ | v0.11 — transport already has `isReachable` Flow |
| Config read (`GET /api/config`) | ❌ | v0.12 — read-only view |
| Config write (`PUT /api/config`) | ❌ | v0.13 — guarded per ADR-0019 |
| Recent logs (`/api/logs`) | ❌ | v0.12 — streaming viewer |
| Network interfaces (`/api/interfaces`) | ❌ | v0.12 |
| Restart daemon (`/api/restart`) | ❌ | v0.12 — confirm dialog |
| Update daemon (`/api/update`) | ❌ | v0.13 |

### 5e. Session preferences

| PWA | Mobile | Notes |
|---|---|---|
| Input mode (tmux / channel / none) | ❌ | v0.12 |
| Output mode | ❌ | v0.12 |
| Recent-session retention window | ❌ | v0.12 |
| Max concurrent sessions | ❌ | v0.12 |
| Scrollback line count | ❌ | v0.12 |

### 5f. Notifications

| PWA | Mobile | Notes |
|---|---|---|
| Per-channel enable / disable | ✅ (system) | Android system settings |
| Active-session suppression | ❌ | v0.11 — don't fire for sessions in foreground |
| Browser notification toggle | n/a | Android notifications are system-native |

### 5g. Memory / KG

| PWA | Mobile | Notes |
|---|---|---|
| Memory stats (`/api/memory/stats`) | ❌ | v0.13 |
| List memories (`/api/memory/list`) | ❌ | v0.13 |
| Search memories (`/api/memory/search`) | ❌ | v0.13 |
| Delete memory (`/api/memory/delete`) | ❌ | v0.13 |
| Export memory (`/api/memory/export`) | ❌ | v0.13 |

### 5h. Schedules

| PWA | Mobile | Notes |
|---|---|---|
| List all schedules (`/api/schedules`) | ❌ | v0.12 |
| Create scheduled reply for a session | ❌ | v0.12 |
| Cancel schedule | ❌ | v0.12 |

### 5i. Stats

| PWA | Mobile | Notes |
|---|---|---|
| CPU / Memory / Disk / GPU | ✅ | StatsScreen |
| Uptime | ✅ | |
| Per-process eBPF network | ❌ (view-only) | v0.13 — post-MVP per ADR-0019 |
| Session counts | ✅ | |

---

## 6. Consolidated roadmap

### v0.11.0 — Session power-user parity
Scope: bring session detail and Settings to 80 % PWA parity without
touching memory/schedules/config-edit (which need more design).

Targets:
- Session rename / restart / delete (single + bulk)
- Connection status indicator in TopAppBar
- Daemon version in About card (`/api/health`)
- CA cert download (`/api/cert`) + import helper
- Terminal search + copy actions (`xterm-addon-search`)
- Explicit alerts dismiss
- Start-session form (re-uses `transport.startSession`)
- Active backend picker (`POST /api/backends/active` — pending parent confirmation)

### v0.12.0 — Channels + schedules + file picker
- Channels list / add / remove / test round-trip (`/api/channels` — needs parent)
- Schedule CRUD per session (`/api/schedules`)
- File picker for New Session working dir (`/api/files`)
- Session preferences panel (retention, max concurrent, scrollback)
- Session timeline viewer (`/api/sessions/timeline`)
- Per-session LLM backend + model pickers (`/api/ollama/models`, `/api/openwebui/models`)
- Daemon log viewer (`/api/logs`) + interfaces (`/api/interfaces`)
- Restart daemon action

### v0.13.0 — Memory / KG + daemon config
- Memory panel (list, search, delete, stats, export)
- Config read-write editor (structured fields, blocked raw YAML per ADR-0019)
- eBPF per-process network (view-only)
- Update daemon action

### v0.14.0 — Federation polish
- Federated servers view (PWA's Multi-Machine summary)
- Cross-server memory diff / KG contradiction surfacing
- Peer broker status from mobile

---

## 7. Ground truth for tracking

- Source feature list: `internal/server/web/index.html` + `app.js` in the
  parent repo at the tag of each mobile release.
- APIs: `docs/api/openapi.yaml`.
- Each mobile release closes by re-running the audit and flipping
  rows in `docs/parity-status.md` + this plan.

## 8. Out of scope for parity

Deliberately skipped, matched against the parent's own PWA non-goals:
- Raw YAML config editing (ADR-0019).
- Direct Signal / Telegram channel-app embedding (Intent handoff only).
- Creating / editing LLM-backend Go plugins from mobile.
