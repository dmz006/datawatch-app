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
| Text filter + per-backend filter chips + Show/Hide history | ✅ | v0.14.0 — matches PWA toolbar. Previous state-quick-filter chip row was removed per user direction. |
| Multi-server picker | ✅ | Top-bar dropdown + 3-finger gesture |
| All-servers fan-out (`/api/federation/sessions`) | ✅ | "All servers" row |
| Per-row swipe-to-mute | ✅ | |
| Session rename (`/api/sessions/rename`) | ✅ | v0.11.0 — overflow menu on Sessions rows |
| Session restart (`/api/sessions/restart`) | ✅ | v0.11.0 — overflow menu, confirm dialog |
| Bulk delete completed (`/api/sessions/delete`) | ✅ | v0.11.0 — single + long-press multi-select. Parent shipped endpoint in v4.0.3 (closed: [dmz006/datawatch#5](https://github.com/dmz006/datawatch/issues/5)) |
| Per-row backend chip + hostname + time meta | ✅ | post-v0.12 — backend resolved from `/api/info` per profile; chip styled to match PWA monitor pills |
| Per-row inline Stop / Restart quick-actions | ✅ | post-v0.12 — Stop on running/waiting (confirm dialog), Restart on terminal states. Overflow menu still hosts Rename + Delete |
| Per-row waiting-input context preview | ✅ | v0.14.0 — multi-line `prompt_context` (4-line clamp), falls back to `last_prompt`. Persisted via migration `3.sqm`. |
| Per-row quick-commands popup (System / Saved / Custom) | ✅ | v0.14.0 — ▶ "Commands" button on waiting rows opens bottom sheet matching PWA `showCardCmds` |
| "View last response" icon on rows | ✅ | v0.14.0 icon + v0.14.2 bottom-sheet viewer |
| Per-row timeline view (`/api/sessions/timeline`) | ✅ | v0.13.1 — timeline sheet now prefers server feed (pipe-delimited lines), falls back to local WS filter. |
| Sort by last activity / start time | ✅ | v0.14.2 — "Sort" dropdown (Recent activity / Started / Name) in Sessions toolbar |
| Pagination / "load more" | ✅ | Active+recent partition (≤ 5 min) plus Show History toggle reveals the full list — parity-equivalent to PWA's pager. No separate "load more" button needed. |
| Schedule: list pending for a session (`/api/schedules`) | ✅ | v0.13.1 — per-session strip above composer. Openapi doc fix tracked in [dmz006/datawatch#16](https://github.com/dmz006/datawatch/issues/16). |

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
| Rename session | ✅ | v0.11.0 overflow + post-v0.12 inline header-tap on detail screen |
| Restart session | ✅ | v0.11.0 — Sessions overflow menu |
| State override via badge tap | ✅ | post-v0.12 — tap the detail-screen state pill to open the override menu |
| Connection banner above terminal | ✅ | post-v0.12 — surfaces when owning profile's `isReachable` flips false |
| Input-required banner above terminal | ✅ | post-v0.12 — amber strip on `waiting_input`, body shows latest prompt text |
| Delete session | ✅ | v0.11.0 — overflow + bulk. Parent shipped endpoint in v4.0.3 (closed: [dmz006/datawatch#5](https://github.com/dmz006/datawatch/issues/5)) |
| Terminal copy action | ✅ | v0.11.0 — terminal toolbar, copies `term.getSelection()` to system clipboard |
| Terminal search (`xterm-addon-search`) | ✅ | v0.11.0 — vendored `xterm-addon-search@0.13.0` + inline search toolbar |
| Inline schedule actions (create scheduled reply) | ✅ | post-v0.12 — composer "Schedule" button + overflow "Schedule reply…" both seed the schedule dialog with typed text → live prompt → task summary |
| Per-session schedules list | ✅ | v0.13.1 — per-session strip above composer. Openapi doc fix tracked in [dmz006/datawatch#16](https://github.com/dmz006/datawatch/issues/16). |
| Backlog pager (`/api/output`) | ✅ | v0.12.0 — terminal-toolbar history button fetches `GET /api/output?id=&n=1000` and prepends into xterm. One-shot per session. `/api/sessions/timeline` structured view still v0.13. |
| Terminal Fit + Jump-to-bottom toolbar | ✅ | post-v0.12 — manual fit (after pinch-zoom) + scroll-to-tail buttons via `dwFit` / `dwScrollToBottom` JS bridges |
| Pane-capture authoritative TUI rendering | ✅ | shipped earlier; mapper-level regression test added post-v0.12 to lock first/redraw frame contract |
| Chat-mode / terminal-mode toggle | ✅ | post-v0.12 — top-app-bar icon swaps between xterm and event list, choice persisted in SharedPreferences |
| Chat quick-reply buttons (Yes / No / Stop) | ✅ | post-v0.12 — appended under the latest PromptDetected in chat mode, fires `sendQuickReply` without touching composer draft |
| Prompt + rate-limit inline banners | ✅ | InlineNotices |

## 3. New session (`data-view="new"`)

| PWA capability | Mobile | Notes |
|---|---|---|
| Start session from form (`/api/sessions/start`) | ✅ | v0.11.0 — Sessions-tab FAB → `NewSessionScreen` |
| Pick LLM backend (`/api/backends`) | ✅ | Settings → LLM read; New Session form has a Backend dropdown that calls `setActiveBackend` server-wide before start (parent lacks per-session backend param) |
| Pick model variant | ✅ | v0.13.1 dropdown + v0.21.0 full swap via `BackendConfigDialog` (PUT /api/config writes `backends.<name>.model`) |
| Pick profile (`/api/profiles`) | ✅ | v0.15.0 — profile dropdown on New Session; passes as `profile` on POST /api/sessions/start |
| Directory picker (`/api/files`) | ✅ | v0.12.0 — `FilePickerDialog` wired into New Session working-dir. Modes: folder / file / both |
| Voice-to-new-session | ✅ | v0.15.0 — composer mic detects `new:` prefix on transcript and routes through `startSession` instead of composer insert |

## 4. Alerts (`data-view="alerts"`)

| PWA capability | Mobile | Notes |
|---|---|---|
| Pending-prompt list | ✅ | AlertsScreen |
| Badge count on nav icon | ✅ | |
| Mark-as-read / dismiss | ✅ | v0.11.0 — swipe-left on Alerts row dismisses (mutes the underlying session) |
| `/api/alerts` read | ✅ (client) | v0.11.0 — transport methods `listAlerts` / `markAlertRead` land; UI still derives from session list + mute projection. Wiring the UI to the dedicated endpoint tracked for v0.12 once the parent's Alert wire shape is fully locked |
| Schedule actions from alerts | ✅ | v0.19.0 — inline "Schedule reply…" on each alert row, seeds prompt text |

## 5. Settings (`data-view="settings"`)

The biggest single gap — PWA's Settings is a power-user control surface.
Mobile currently covers Servers + Security + About + Comms placeholder.

### 5a. Servers + federation

| PWA | Mobile | Notes |
|---|---|---|
| List servers (`/api/servers`) | ✅ | ServersCard |
| Add / edit / delete | ✅ | AddServer + EditServer screens |
| Per-server health indicator | ✅ | Status dot in picker |
| Federated server list (read-only) | ✅ | v0.20.0 — FederationPeersCard under Settings → Comms, via `/api/servers` |

### 5b. LLM backend config

| PWA | Mobile | Notes |
|---|---|---|
| Pick active backend | ✅ | v0.11.0 — Channels-tab radio picker calls `POST /api/backends/active`. Parent shipped endpoint in v4.0.3 (closed: [dmz006/datawatch#7](https://github.com/dmz006/datawatch/issues/7)) |
| Edit endpoint URL / API key per backend | ✅ | v0.21.0 — BackendConfigDialog on Settings → LLM, structured fields (model/base_url/api_key) per ADR-0019 |
| Pick Ollama model (`/api/ollama/models`) | ✅ | v0.13.1 informational picker + v0.21.0 full swap via BackendConfigDialog writes backend.model |
| Pick OpenWebUI model (`/api/openwebui/models`) | ✅ | v0.13.1 + v0.21.0 (same wire as ollama) |

### 5c. Channels (messaging backends)

| PWA | Mobile | Notes |
|---|---|---|
| List configured channels | ✅ | v0.18.0 — ChannelsCard in Settings → Comms |
| Add / remove channel | ✅ | v0.33.11 wires `POST /api/channels` (upstream [dmz006/datawatch#18](https://github.com/dmz006/datawatch/issues/18) shipped 2026-04-21) + `DELETE /api/channels/{id}`. ChannelsCard has a + Add button that pops a type/id/enabled dialog, plus a per-row delete icon. Backend-specific config still flows through the existing BackendConfigDialog after create. |
| Test message round-trip (`/api/channel/send`) | ✅ | v0.18.0 — "Test" button per row opens prompt dialog + POSTs |
| Per-channel enable / disable | ✅ | v0.18.0 — per-row Switch calls PATCH /api/channels/{id} |
| Download CA cert (`/api/cert`) | ✅ | v0.11.0 — Settings → Servers overflow menu → save to Downloads → OS install-cert intent. Parent shipped endpoint in v4.0.3 (closed: [dmz006/datawatch#6](https://github.com/dmz006/datawatch/issues/6)) |

### 5d. Daemon control + introspection

| PWA | Mobile | Notes |
|---|---|---|
| Show daemon version (`/api/health`) | ✅ | v0.11.0 — uses `GET /api/info` for richer data (hostname + version + backends). About card "Connected to" row |
| Connection status indicator | ✅ | v0.11.0 — 8 dp dot in Sessions TopAppBar + tap-to-open retry sheet |
| Config read (`GET /api/config`) | ✅ | v0.12.0 — Settings → Daemon config card; collapsible top-level tree + client-side secondary mask on secret field names |
| Config write (`PUT /api/config`) | ✅ | v0.20.0 BehaviourPreferencesCard + v0.21.0 BackendConfigDialog. v0.33.6 rewrote the wire shape from a nested tree to the flat dot-path patch the server's `applyConfigPatch` actually cases on — previous versions silently no-op'd every save. Autosave (500 ms debounce) replaced the explicit Save button in v0.33.6 to match PWA. |
| Recent logs (`/api/logs`) | ✅ | v0.16.0 — Settings → Monitor → DaemonLogCard, 10 s auto-refresh, colour-coded, paginated |
| Network interfaces (`/api/interfaces`) | ✅ | v0.16.0 — Settings → Monitor → InterfacesCard |
| Restart daemon (`/api/restart`) | ✅ | v0.16.0 — Settings → Monitor → RestartDaemonCard with confirm dialog |
| Update daemon (`/api/update`) | ✅ | v0.22.1 — endpoint exists in parent (PWA `runUpdate` at app.js:4149), UpdateDaemonCard under Settings → Monitor. Parent openapi.yaml doesn't document it yet; fell into the same stale-spec pattern as the schedules/profiles/timeline endpoints. |

### 5e. Session preferences

| PWA | Mobile | Notes |
|---|---|---|
| Input mode (tmux / channel / none) | ✅ | v0.22.0 — BehaviourPreferencesCard dropdown |
| Output mode | ✅ | v0.22.0 — BehaviourPreferencesCard dropdown (tmux/channel/both/none) |
| Recent-session retention window | ✅ | v0.20.0 — BehaviourPreferencesCard edits `recent_window_minutes` |
| Max concurrent sessions | ✅ | v0.20.0 — BehaviourPreferencesCard edits `max_concurrent` |
| Scrollback line count | ✅ | v0.20.0 — BehaviourPreferencesCard edits `scrollback_lines` |

### 5f. Notifications

| PWA | Mobile | Notes |
|---|---|---|
| Per-channel enable / disable | ✅ (system) | Android system settings |
| Active-session suppression | ✅ | v0.19.0 — ForegroundSessionTracker + NotificationPoster guard suppress InputNeeded for the visible session |
| Browser notification toggle | n/a | Android notifications are system-native |

### 5g. Memory / KG

| PWA | Mobile | Notes |
|---|---|---|
| Memory stats (`/api/memory/stats`) | ✅ | v0.17.0 — stats grid in MemoryCard |
| List memories (`/api/memory/list`) | ✅ | v0.17.0 |
| Search memories (`/api/memory/search`) | ✅ | v0.17.0 |
| Delete memory (`/api/memory/delete`) | ✅ | v0.17.0 |
| Export memory (`/api/memory/export`) | ✅ | v0.22.0 — "Export…" button uses SAF CreateDocument + bearer-auth GET, writes to user-chosen URI |

### 5h. Schedules

| PWA | Mobile | Notes |
|---|---|---|
| List all schedules (`/api/schedules`) | ✅ | SchedulesCard under Settings → General |
| Create scheduled reply for a session | ✅ | composer clock button + Alerts "Schedule reply…" |
| Cancel schedule | ✅ | per-row ✕ button in SchedulesCard + per-session strip |

### 5i. Stats

| PWA | Mobile | Notes |
|---|---|---|
| CPU / Memory / Disk / GPU | ✅ | StatsScreen |
| Uptime | ✅ | |
| Per-process eBPF network | ⏳ | Read-only viewer deferred to a post-1.0.0 batch per ADR-0019 (no write actions from mobile). Server already exposes the data; mobile UI not built. |
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
