# PWA audit sprint — 2026-04-21

**Ground-truth source:** `internal/server/web/{app.js, index.html, openapi.yaml}`
in `dmz006/datawatch` (pulled fresh 2026-04-21). Working copies cached at
`/tmp/pwa-app.js`, `/tmp/pwa-index.html`, `/tmp/openapi.yaml`.

**User directive (2026-04-21):** feature-by-feature parity pass, terminal
emulation full ANSI/xterm coverage, Auto-module ready. Iterate without
check-ins until done; user tests in the morning.

**Cadence rule (memory `feedback_phase_commit_cadence.md`):** phase end +
green tests = full-docs commit + patch bump + continue. Don't ask mid-flow.

---

## Phase 1 — PWA API surface (complete)

**REST endpoints the PWA calls** (grep `/tmp/pwa-app.js` for
`(apiFetch|fetch)\('/api/`):

| Endpoint | Methods | Mobile status |
|---|---|---|
| `/api/health` | GET | ✅ `ping()` |
| `/api/sessions` | GET | ✅ `listSessions()` |
| `/api/sessions/start` | POST | ✅ `startSession(...)` |
| `/api/sessions/rename` | POST | ✅ `renameSession()` |
| `/api/sessions/restart` | POST | ✅ `restartSession()` |
| `/api/sessions/kill` | POST | ✅ `killSession()` |
| `/api/sessions/state` | POST | ✅ `overrideSessionState()` |
| `/api/sessions/delete` | POST | ✅ `deleteSession()` / `deleteSessions()` |
| `/api/sessions/timeline` | GET | ✅ `fetchTimeline()` |
| `/api/stats` | GET | ✅ `stats()` |
| `/api/stats/kill-orphans` | POST | ❌ **MISSING** |
| `/api/backends` | GET | ✅ `listBackends()` |
| `/api/backends/active` | POST | ✅ `setActiveBackend()` |
| `/api/config` | GET/PUT | ✅ `fetchConfig()` / `writeConfig()` |
| `/api/info` | GET | ✅ `fetchInfo()` |
| `/api/alerts` | GET/POST | ✅ `listAlerts()` / `markAlertRead()` |
| `/api/schedules` | GET/POST/DELETE | ✅ `listSchedules()` / `createSchedule()` / `deleteSchedule()` |
| `/api/commands` | GET/POST/DELETE | ✅ `listCommands()` / `saveCommand()` (del check) |
| `/api/command` (singular) | POST | ❌ **MISSING** — direct text injection (tmux-kill, sendkey) |
| `/api/files` | GET | ✅ `browseFiles()` |
| `/api/servers` | GET | ✅ `listRemoteServers()` |
| `/api/servers/health` | GET | ❌ **MISSING** — per-peer health |
| `/api/interfaces` | GET | ✅ `listInterfaces()` |
| `/api/logs` | GET | ✅ `fetchLogs()` |
| `/api/restart` | POST | ✅ `restartDaemon()` |
| `/api/update` | POST | ✅ `updateDaemon()` (v0.22.1) |
| `/api/cert` | GET | ✅ `fetchCert()` |
| `/api/ollama/models` | GET | ✅ `listModels("ollama")` |
| `/api/openwebui/models` | GET | ✅ `listModels("openwebui")` |
| `/api/profiles` | GET | ✅ `listProfiles()` |
| `/api/profiles/<kind>s/<name>` | GET/PUT/DELETE | ❌ **MISSING** — F10 profile CRUD |
| `/api/channels` | GET | ✅ `listChannels()` |
| `/api/channels/<id>` | PATCH/DELETE | ✅ `setChannelEnabled()` (delete missing — but PWA uses PATCH to disable, not DELETE) |
| `/api/channel/send` | POST | ✅ `sendChannelTest()` |
| `/api/memory/stats` | GET | ✅ `memoryStats()` |
| `/api/memory/list` | GET | ✅ `memoryList()` |
| `/api/memory/search` | GET | ✅ `memorySearch()` |
| `/api/memory/delete` | POST | ✅ `memoryDelete()` |
| `/api/memory/export` | GET | ✅ `memoryExport()` |
| `/api/memory/test` | GET | ❌ **MISSING** — connection test |
| `/api/filters` | GET/POST/PATCH/DELETE | ❌ **MISSING** — notification filter rules |
| `/api/mcp/docs` | GET | ❌ **MISSING** — MCP tool catalogue |
| `/api/link/start` | POST | ⏩ skip (PWA-only messaging-backend QR pairing) |
| `/api/link/status` | GET | ⏩ skip |
| `/api/voice/transcribe` | POST | ✅ `transcribeAudio()` |
| `/api/devices/register` | POST | ✅ `registerDevice()` |
| `/api/federation/sessions` | GET | ✅ `federationSessions()` |
| `/api/output` | GET | ✅ `fetchOutput()` |
| `/api/sessions/reply` | POST | ✅ `replyToSession()` |

**WebSocket frame types the PWA sends:**

| Frame | Mobile status |
|---|---|
| `subscribe` | ✅ WsTransport sends after connect |
| `send_input` | ❌ not sent — mobile uses REST `/api/sessions/reply` (functionally equivalent for text) |
| `command` | ❌ not sent — would overlap with REST `/api/command` (gap #1 above) |
| `resize_term` | ❌ **MISSING** — mobile never tells the server its xterm cols/rows, so TUIs (Claude Code 120-col) render at server default |

### Gaps summary (8 missing REST + 1 WS)

1. `/api/stats/kill-orphans` — Monitor card button
2. `/api/command` (singular) — text-injection primitive
3. `/api/servers/health` — per-peer health dot on FederationPeersCard
4. `/api/profiles/<kind>s/<name>` CRUD — F10 profile editor
5. `/api/memory/test` — Memory card "Test connection"
6. `/api/filters` — notification filter rules (full CRUD UI)
7. `/api/mcp/docs` — MCP catalogue viewer
8. `/api/channels/<id>` DELETE (low priority; disable via PATCH is the PWA's happy path)
9. WS `resize_term` — PTY sync on xterm resize (**critical for TUI parity**)

---

## Phase 2 — Terminal emulation audit

**PWA terminal init** (app.js ~line 2010+):

```js
theme: {
  background: '#0f1117', foreground: '#e2e8f0',
  cursor: '#a855f7', cursorAccent: '#0f1117',
  selectionBackground: 'rgba(168,85,247,0.3)',
  black:'#1a1d27', red:'#ef4444', green:'#10b981', yellow:'#f59e0b',
  blue:'#3b82f6', magenta:'#a855f7', cyan:'#06b6d4', white:'#e2e8f0',
  brightBlack:'#94a3b8', brightRed:'#f87171', brightGreen:'#34d399',
  brightYellow:'#fbbf24', brightBlue:'#60a5fa', brightMagenta:'#c084fc',
  brightCyan:'#22d3ee', brightWhite:'#f8fafc',
}
scrollback: 5000
```

**Addons loaded:** FitAddon (only — SearchAddon is separate in PWA).

**`resize_term` flow:** after `fitAddon.fit()` settles, PWA sends
`{type: 'resize_term', data: {session_id, cols, rows}}` over WS. Server
resizes the tmux pane to match. Subsequent `pane_capture` arrives at
the new width.

**configCols / configRows:** server-driven minimum (e.g. 120 for
claude-code). PWA reads it from session config; if container is narrower,
PWA resizes xterm to the minimum and lets the container scroll
horizontally.

### Mobile terminal state (after host.html palette alignment, committed
this turn but not yet released):

| Feature | PWA | Mobile |
|---|---|---|
| Background / fg | `#0f1117` / `#e2e8f0` | ✅ aligned this sprint |
| 16-colour ANSI palette | tailwind-based | ✅ aligned this sprint |
| FitAddon | loaded | ✅ |
| SearchAddon | loaded (separate init) | ✅ (vendored 0.13.0) |
| pane_capture first → reset + write | yes | ✅ (`dwPaneCapture` in host.html) |
| pane_capture redraw → `\x1b[2J\x1b[3J\x1b[H` prefix | yes | ✅ |
| `resize_term` WS | yes | ❌ **MISSING** — biggest functional gap for TUIs |
| configCols minimum (120 for claude) | yes | ❌ **MISSING** — mobile fits to WebView width, which may break claude rendering |
| Backlog pager (`/api/output`) | yes | ✅ |
| Font size ± | localStorage | ✅ SharedPreferences |
| Fit / jump-to-bottom buttons | yes | ✅ |
| Copy selection to clipboard | yes | ✅ |
| Terminal search | xterm-addon-search | ✅ |
| Initial welcome banner | none | ✅ removed Sprint B |

### Terminal audit plan (Phase 2 work)

1. **resize_term WS frame** — requires adding an outbound write path to
   `WebSocketTransport`. Options:
   - Refactor `events()` callbackFlow into a hub with an outbound channel.
   - Spawn a one-shot WS per `resize_term` call (simpler; wasteful).
   - Keep a long-lived `WsHub` singleton per profile, shared between
     `events()` and a new `send(frame)` method.
   Recommendation: hub refactor. 2h of work but clean.

2. **configCols minimum** — read `session.config.cols` or equivalent from
   the session DTO. Parent may or may not emit it; fallback to
   `backend`-specific defaults (claude-code → 120). If container < cols,
   force `term.resize(cols, rows)` and let container scroll.

3. **Final palette check** — done this turn. Host.html matches PWA
   byte-for-byte.

---

## Phase 3 — Settings surface audit (tab-by-tab, section-by-section)

**On deeper read of PWA `app.js` lines 3100–3350 + constants 3532–3716,
mobile is missing ~25 sections / ~70 config fields. One-at-a-time
cards is infeasible; the right move is a generic
`ConfigFieldsPanel(fields: List<Field>, config: JsonObject, onSave)` that
mirrors PWA's field definitions in one place.**

### Field types PWA uses

| Type | Widget | Mobile equivalent |
|---|---|---|
| `toggle` | checkbox | `Switch` |
| `number` | numeric input | `OutlinedTextField(keyboardType = Number)` |
| `text` | text input | `OutlinedTextField` |
| `select` | `<select>` with options[] | Compose `DropdownMenu` |
| `interface_select` | populated from `/api/interfaces` | DropdownMenu fed by transport |
| `llm_select` | populated from `/api/backends` | DropdownMenu fed by transport |
| `dir_browse` | opens file picker | Existing `FilePickerDialog` |
| `html` | raw HTML link block | Compose `ClickableText` / `TextButton` with hint |

### Comms tab sections

| Section | Fields | Mobile |
|---|---|---|
| `comms_auth` Authentication | Browser token · Server bearer · MCP SSE bearer | ❌ |
| `servers` | connection status + this server + remote list | ✅ (ServersCard + FederationPeersCard) |
| `cc_websrv` Web Server | 10 fields (enabled / host / port / TLS pair) incl. `/api/cert` link | ❌ |
| `cc_mcpsrv` MCP Server | 8 fields | ❌ |
| `proxy` Proxy Resilience | — | ❌ |
| `backends` Communication Configuration | config status + Signal device linking | ❌ (linking deferred) |

### LLM tab sections

| Section | Fields | Mobile |
|---|---|---|
| `llm` LLM Configuration | list of LLM backends (per-backend detail panels) | ✅ partial (LlmBackendCard + BackendConfigDialog with model/base_url/api_key only) |
| `lc_memory` Episodic Memory | **17 fields** — enabled, backend, embedder, embedder_model, embedder_host, top_k, auto_save, learnings, storage_mode, entity_detection, session_awareness, session_broadcast, auto_hooks, hook_save_interval, retention_days, db_path, postgres_url | ❌ |
| `lc_rtk` RTK Token Savings | 7 fields | ❌ |
| `detection` Detection Filters | list of regex → action rules | ❌ (same machinery as `filters`) |
| `cmds` Saved Commands | list + add form | ✅ SavedCommandsCard |
| `filters` Output Filters | list + add form (pattern / action / value) | ❌ |

### General tab sections

| Section | Fields | Mobile |
|---|---|---|
| `gc_dw` Datawatch | log_level, auto_restart_on_config, default LLM backend | ❌ |
| `gc_autoupdate` Auto-Update | enabled, schedule, time_of_day | ❌ |
| `gc_sess` Session | **18 fields** — max_sessions, input_idle_timeout, tail_lines, alert_context_lines, default_project_dir, root_path, console_cols, console_rows, recent_session_minutes, skip_permissions, channel_enabled, auto_git_init, auto_git_commit, kill_sessions_on_exit, mcp_max_retries, schedule_settle_ms, default_effort, suppress_active_toasts | ~5 via BehaviourPreferencesCard (recent_window, max_concurrent, scrollback, input_mode, output_mode — using PWA-renamed keys) |
| `gc_rtk` RTK Token Savings | 5 fields | ❌ |
| `gc_pipeline` Pipelines | 2 fields | ❌ |
| `gc_autonomous` Autonomous PRD decomposition | 7 fields | ❌ |
| `gc_plugins` Plugin framework | 3 fields | ❌ |
| `gc_orchestrator` PRD-DAG orchestrator | 4 fields | ❌ |
| `gc_whisper` Voice Input (Whisper) | 4 fields | ❌ |
| `gc_projectprofiles` Project Profiles | CRUD list | ❌ |
| `gc_clusterprofiles` Cluster Profiles | CRUD list | ❌ |
| `gc_notifs` Notifications | browser permission status + request button | ❌ (mobile uses system settings) |

### Monitor tab sections

| Section | Mobile |
|---|---|
| `stats` System Statistics | ✅ StatsScreenContent |
| `membrowser` Memory Browser | ✅ (placed under General on mobile — acceptable divergence) |
| `schedules` Scheduled Events | ✅ (placed under General on mobile) |
| `daemonlog` Daemon Log | ✅ DaemonLogCard |
| **kill-orphans button** | ❌ |

### About tab sections

| Section | Mobile |
|---|---|
| App version + daemon info | ✅ AboutCard |
| Config viewer | ✅ ConfigViewerCard |
| `api` API links (Swagger, OpenAPI, MCP Tools, Architecture diagrams) | ❌ |

### Key architectural insight

Hand-crafting 25+ cards is waste. The right move is **one generic
`ConfigFieldsPanel` composable** keyed off a list of `Field` definitions
(mirroring PWA's `COMMS_CONFIG_FIELDS` / `LLM_CONFIG_FIELDS` /
`GENERAL_CONFIG_FIELDS`). Fields live in a single Kotlin constants file;
the panel renders each widget type and binds save-on-change to
`writeConfig`. New server-side fields reach mobile by adding a line to
the constants file.

### Non-terminal UI gaps not in Settings

- **New Session form** is missing four PWA fields:
  - Session name (distinct from task prompt — currently mobile treats
    the task textarea as the name; PWA has both).
  - Resume previous session dropdown (warm-resume from a completed
    session's state, or enter a custom session id).
  - Auto git init toggle.
  - Auto git commit toggle (defaults on in PWA).
- **Session backlog list** at bottom of New Session page — restart
  grid for recent completed/killed/failed sessions.
- **Backend not-installed warning panel** — shown when the picked
  backend lacks install/config (PWA has specific install hints).
- Session detail composer direct text injection (`/api/command`) —
  low priority power-user affordance.
- API links (Swagger / OpenAPI / MCP Tools) — just clickable links.

---

## Phase 4 — Android Auto module audit

**Current state** (inspect `/home/dmz/workspace/datawatch-app/auto/`):
- Module exists; modules.gradle.kts wires it.
- Services: `DatawatchMessagingService` (publicMain), `DatawatchPassengerService` (devMain).
- Uses shared transport so all mobile work propagates.

**Audit checklist:**
- [ ] Compiles under `:auto:compileDevDebugKotlinAndroid`
- [ ] Manifest has correct `automotive-app` metadata
- [ ] Voice-command intent wired to session-list / reply
- [ ] Session list summary screen renders active sessions
- [ ] Reply flow uses RemoteInput (in-car keyboard is limited)
- [ ] Deep-link from Auto to Phone when user switches modes

---

## Sprint plan

Ordered by user impact × implementation cost. Each sprint lands a
patch-release commit per AGENT.md rules.

| # | Sprint | Scope | Ver | Status |
|---|---|---|---|---|
| K | terminal palette alignment | host.html + TerminalView bg | 0.23.0 | **ready to commit** |
| L | ConfigFieldsPanel renderer + Field schema port | generic infra for all cfg tabs | 0.24.0 | pending |
| M | wire ConfigFieldsPanel into Comms tab (websrv + mcpsrv + proxy + auth) | 4 section cards | 0.24.1 | pending |
| N | wire ConfigFieldsPanel into LLM tab (lc_memory + lc_rtk) + Filters CRUD UI + Detection | 4 cards | 0.24.2 | pending |
| O | wire ConfigFieldsPanel into General tab (gc_dw/autoupdate/sess/rtk/pipeline/autonomous/plugins/orchestrator/whisper) | 9 cards; extend BehaviourPreferencesCard → generic | 0.24.3 | pending |
| P | Project Profiles + Cluster Profiles CRUD | new transport + cards | 0.25.0 | pending |
| Q | Monitor kill-orphans + Memory test + MCP docs viewer + servers/health | small ops | 0.25.1 | pending |
| R | About API-links section + `/api/command` direct inject + Stop action no-confirm option | polish | 0.25.2 | pending |
| S | resize_term WS + configCols minimum | **terminal functional parity** | 0.26.0 | pending |
| T | Android Auto audit + wiring | in-car usability | 0.27.0 | pending |
| U | final pass + CI check-version + docs + drag-reorder if time | 1.0.0-rc | 1.0.0-rc1 | pending |

**Scope adjustment rationale:** the original plan didn't know the
scale. PWA has ~70 individual config fields. Rather than ship each
as a custom card, we ship one generic renderer (sprint L) and bulk-
wire the schemas in sprints M/N/O.

---

## Already-committed this audit turn (not yet in a release commit)

- `composeApp/src/androidMain/assets/xterm/host.html` — palette aligned
  to PWA byte-for-byte.
- `composeApp/src/androidMain/kotlin/.../TerminalView.kt` — WebView
  background colour `#0F1117`.

Both land with Sprint K's release commit.

---

## Non-goals / explicit deferrals

- `/api/link/start` / `/api/link/status` — messaging-backend QR pairing
  flow; server-admin UX, not user-facing on mobile. Skip entirely.
- Drag-to-reorder sessions list — Compose lacks a reorderable
  `LazyColumn` without an external dep. Sort (Recent / Started / Name)
  covers the common case. Revisit post-1.0.
- eBPF per-process view-only — ADR-0019 flagged for post-1.0.0.
- `/api/sessions/reply` direct WS `send_input` equivalent — we already
  reply via REST; WS send_input would matter only for key-by-key input
  like a real terminal session, which isn't in the composer's UX today.
