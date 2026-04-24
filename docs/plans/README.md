# Plans, bugs, and backlog tracker

Single source of truth per AGENT.md "Project Tracking". IDs are permanent —
never reuse a bug (B#), backlog (BL#), or feature (F#) number.

Plans: individual dated documents live as `YYYY-MM-DD-<slug>.md` in this
directory when work warrants formal planning (3+ files or non-trivial
architecture).

---
## Open - Not Assessed

### P0 closed in v0.34.6
- ✅ **/api/sessions/kill 404** — mobile sent `session_id` key + short id;
  server reads `id` + full id. Fixed across every mutation endpoint.
- ✅ **Stop badge in list** — same root cause as the above.
- ✅ **No delete button after kill** — detail infobar + list row now
  show `🗑 Delete` for terminal-state rows.
- ✅ **Chat-mode sessions blank** — `ChatTranscriptPanel` renders
  role-badged bubbles from WS `chat_message` frames; schema
  migration 4.sqm persists `output_mode`/`input_mode` so cold-open
  from cache picks the right surface.

### Still open
- **PWA parity deep-dive audit.** Validate every finding in
  `2026-04-22-pwa-parity-audit.md` and expand. Every settings edit
  dialog, every config field, every variable. Buttons + input
  windows are visibly larger than their own text in Settings.
- **New Session LLM backend picker** — user reported it doesn't
  load the configured/active LLM list for the selected server.
  v0.34.5 added per-server filtering; retest on 0.34.6 before
  calling closed or doing another pass.
- **Sessions hamburger-menu reorder** — user wants drag-and-drop
  instead, PWA style.
- **Alerts UI rebuild** — PWA has active/inactive tabs + per-type
  sub-tabs + collapsible envelope history for older sessions. No
  swipe-to-close / swipe-to-delete on Android currently.
- **tmux input IME padding** — text window doesn't lift above
  keyboard on the session-detail composer; message gets hidden
  behind the soft keyboard as user types.

## Open — organised by sprint (fastest resolution first)

Refactored 2026-04-22: everything still active (bugs + unscheduled
backlog) grouped into proposed sprint batches. Batches are ordered
to land the highest-user-impact items first with the smallest code
surface per batch. All sprints are **pre-1.0** per user direction.

### Sprint FF+ — Wear & Auto feature parity (user request 2026-04-22)

| ID | Title | Notes |
|----|-------|-------|
| B31 | Wear + Auto: Sessions tab with snapshot + quick-command + voice | **HOLD** 2026-04-22: Auto already ships `AutoSummaryScreen` / `WaitingSessionsScreen` / `SessionReplyScreen` with Yes/No/Continue/Stop quick-reply. Wear's Sessions page (v0.33.25) shows counts only. User evaluating whether existing Auto scope counts as "done" before scheduling watch snapshot + voice work. |

### Sprint FF — live-device polish (next, v0.33.24+)

In-flight fixes from the current test pass. Small / cosmetic; aim
for one commit per batch.

| ID | Title | Notes |
|----|-------|-------|
| B28 | Watch + Auto need to view monitoring stats for all connected servers | User request (2026-04-22). Depends on server-side shape landing from [dmz006/datawatch#20](https://github.com/dmz006/datawatch/issues/20); mobile-side plan in [docs/plans/2026-04-22-unified-monitoring.md](2026-04-22-unified-monitoring.md). **Wear** subscribes to the stats DataItem (infra from v0.33.12) and renders a compact dashboard. **Auto** adds a Monitoring screen with one row per connected server. |
| B29 | Session-detail TopAppBar title is sometimes cropped | On wide-name sessions (long task summary) the title + connection dot compete. Put connection dot in a fixed-width pill and ellipsize the title column with trailing ID. |

### Sprint GG — unified monitoring Phase 1 (v0.34.x)

Depends on parent server shipping the v2 stats shape per
[dmz006/datawatch#20](https://github.com/dmz006/datawatch/issues/20).

| ID | Title | Notes |
|----|-------|-------|
| B5 | Stats density + GPU / eBPF detail | [Phase 1 of spec](2026-04-22-unified-monitoring.md) — structured StatsDto, per-core strip, GPU rows, backend health dots. |
| B10 | Live system-stats streaming | [Phase 2 of spec](2026-04-22-unified-monitoring.md) — subscribe to `MsgStats` WS broadcast, replace 5 s poll. Server already broadcasts (ws.go:42); EventMapper needs a `stats` branch. |
| B11 | Per-session stats panel w/ wheels + graphs | [Phase 3 of spec](2026-04-22-unified-monitoring.md) — per-process eBPF taps from the cluster container. Mobile surface is view-only per ADR-0019 — but ships pre-1.0. |

### Sprint HH — BL backlog pulls (v0.35.x)

Unscheduled backlog items that align with the PWA-parity push —
pulled out of the BL pool into real sprints since everything needs
to land pre-1.0.

| ID | Title | Notes |
|----|-------|-------|
| BL1 | Split consolidated `decisions/README.md` into per-ADR MADR files | 41 ADRs. Docs-only; can land at any time. |
| BL3 | Tablet two-pane layout | Responsive audit + design pass. |
| BL5 | iOS content phase | Skeleton-only today; real content after Android parity stabilises. |
| BL7 | Foldable layout (Pixel Fold / Z Fold) | Post-tablet. |
| BL13 | Adjustable terminal dimensions | Depends on xterm reflow UX. |
| BL14 | Raw YAML config editor (behind biometric + confirm) | Revisits ADR-0019 scope. |
| BL15 | Localization (DE, ES, FR, JA) | i18n extraction + translation pipeline. |
| BL16 | Biometric-bound DB passphrase | Wrap the Keystore key with an auth-required spec so the DB can't open without a challenge. |
| BL19 | Local-LLM orchestration — in-app PRD/HLD authoring + Ollama backend + task fire-off | User vision. Needs its own ADR for the orchestration model. |
| BL21 | Signal device-linking (`/api/link/*` + QR SSE) | Needs QR rendering from SSE frames + paired-state persistence. |

### Parking lot (waiting on upstream / user gesture)

| ID | Title | Waiting on |
|----|-------|-----------|
| B6 | Push via FCM | User/operator decision. FCM removed v0.33.17; ntfy-only ships today. If FCM is desired, wire `google-services.json` + re-add the plugin. Otherwise close as WONTFIX. |

### Reclassified

Items originally filed as bugs but not PWA-parity gaps. Deferred
or retracted rather than scheduled.

| ID | Title | Disposition |
|----|-------|-------------|
| B12 | Active-sessions list on Monitor | PWA doesn't put this on Monitor. Retracted. |
| B13 | Chat-channel status summary on Monitor | PWA doesn't put this on Monitor. Retracted. |
| B14 | LLM-backend status summary on Monitor | Lives on Settings → LLM via LlmConfigCard (v0.33.14). Retracted. |
| B15 | List of disabled chats on Monitor | ChannelsCard already shows enabled/disabled per-row. Retracted. |

---

## Closed

### Bugs

| ID | Title | Closed in | Notes |
|----|-------|-----------|-------|
| B1 | Terminal TUI unreadable | v0.23.0 + v0.33.5 + v0.33.8 | `resize_term` WS frame (BL18 path), single-source pane_capture renderer, host.html horizontal-scroll, 11px (then 9px) default font, pane_capture live bus bypassing the DB. |
| B2 | Android Auto doesn't list datawatch | v0.33.0 + v0.33.9 | Bundled `:auto` into composeApp APK + `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission. |
| B3 | Swipe-to-mute doesn't toggle | v0.33.15 | `pointerInput` registered AFTER `combinedClickable` so clickable consumed drag gestures on the main pass. Reordered. |
| B4 | Settings LLM / Comms say "server unreachable" | v0.33.6 | Two DTO shapes accepted; serialization errors no longer bucket under Unreachable. |
| B6 | FCM not active | v0.33.17 | Removed — datawatch ships ntfy-only per privacy posture. Open for user re-enablement if desired. |
| B7 | CI ktlint parse error | v0.33.15 | File rename + exclude build/generated/** from the scan. |
| B8 | Terminal blank after refresh | v0.33.13 | 5 s watchdog LaunchedEffect captured stale events closure; removed. |
| B9 | Eye watermark on sessions list | v0.33.15 | Background image at 85% width / 10% alpha. |
| B16 | "Schedules" → "Scheduled Events" + auto-sync | v0.33.13 | Refresh button dropped; 15 s auto-poll; WS sync follow-up tracked under B10. |
| B17 | Scheduled Events pagination | v0.33.13 | 10 rows/page + prev/next navigator. |
| B18 | Drop Network Interfaces from Monitor | v0.33.13 | Removed. |
| B19 | Move Update + Restart daemon cards to About | v0.33.13 | Moved. |
| B20 | Monitor order Stats → KillOrphans → Memory → Schedules → Log | v0.33.13 | Reordered. |
| B21 | Channels title "Communication Configuration" | v0.33.13 | Renamed. |
| B22 | Missing LLM Configuration card | v0.33.14 | `LlmConfigCard` at top of LLM tab. |
| B23 | Detection Filters empty when server returns null | v0.33.15 | PWA's `builtinDefaults` rendered greyed-out until user overrides. |
| B24 | MCP tools list doesn't belong on About | v0.33.13 | Dropped. |
| B25 | About sessions-details footer | v0.33.14 | Sessions + Uptime rows from `/api/stats`. |
| B26 | Input-required banner yellow + ✕ | v0.33.22 + v0.33.23 | v0.33.22 rebuilt the banner to PWA's big amber style. v0.33.23 wired `session.promptContext` so it actually renders content (was empty). |
| B27 | Live pane-capture updates | v0.33.20 | **Root cause**: SessionEventRepository keyed its live-capture map by session id — `insert()` stored under full server id, `observe()` read short client id. Replaced with a SharedFlow + prefix-match filter. |
| S1-S9 / T1-T3 / W1 / A1 | v0.33 on-device testing punch list | v0.33.x | See [dmz006/datawatch-app#1](https://github.com/dmz006/datawatch-app/issues/1). |
| reply-send-404 | Composer "connection error" | v0.33.22 | Server doesn't expose `/api/sessions/reply`; switched to WS `send_input` (PWA path). |
| composer-invisible | Reply text black-on-black | v0.33.23 | Explicit `textStyle.color = onSurface` + OutlinedTextFieldDefaults colors so the field doesn't inherit LocalContentColor from the amber banner's Surface. |
| channel-tab-crash | Clicking channel tab → IllegalArgumentException duplicate-key | v0.33.23 | LazyColumn key collided when live-capture SharedFlow replayed + live-emitted the same PaneCapture. Switched to `itemsIndexed`. |
| monitor-missing-cards | Settings/Monitor missing CPU/Mem/Disk/GPU/VRAM + wrong Sessions card + LLM row on Server card + Ollama rendered offline | v0.33.25 | Rewrote `StatsScreen` to PWA's `renderStatsData` reads: `cpu_load_avg_1 / cpu_cores`, `mem_used / mem_total`, `disk_used / disk_total`, `swap_*`, `gpu_*`. Session card switched to ring showing X of `session.max_sessions`. Server card adds live CPU + memory rows and drops LLM backend (fleet can run many). Ollama card hidden unless server reports `available = true`. |
| B30 | Wear + Auto multi-server picker | v0.33.25 | Auto gets `AutoServerPickerScreen` reachable from the Monitor ActionStrip; Wear gets a dedicated "Server" page that sends a `/datawatch/setActive` MessageClient message the phone's `WearSyncService` consumes. `ActiveServerStore` moved from `composeApp` to `shared/androidMain` so both composeApp and :auto can bind to the same prefs file. |
| B32 | Wear + Auto monitoring tab | v0.33.25 | Auto's root screen is now `AutoMonitorScreen` (CPU load, memory, disk, VRAM, sessions, uptime). Wear's default page is Monitor, reading a new `/datawatch/stats` DataItem the phone publishes every 15 s. User requested Monitor be the default landing page. |
| B33 | Wear + Auto About screen | v0.33.25 | Auto adds `AutoAboutScreen` with Version + build + surface. Wear adds an About page (4th in pager) reading shared `Version.VERSION`. Both styled with datawatch dark palette + teal accent, not stock Material defaults. |
| widgets-monitor | Home-screen Monitor widget + tap-to-cycle servers | v0.34.0 | New `MonitorWidget` renders CPU / memory / session counts from the active profile; both Sessions and Monitor widgets share `WidgetActions.cycleActiveServer` so tapping the profile label advances `ActiveServerStore` to the next enabled profile and refreshes both widget types in lockstep. |
| tile-sessions-wired | Wear Sessions tile reads DataLayer | v0.34.0 | `SessionsTileService` was still the Phase-1 placeholder rendering zeros. Now reads `/datawatch/counts` from the phone's `WearSyncService` and uses the datawatch palette (teal / amber) instead of legacy purple. Tap → launches Wear companion. |
| tile-monitor | Wear Monitor tile | v0.34.0 | New `MonitorTileService` reading `/datawatch/stats` (CPU load / cores, memory %, session summary, uptime). Colour thresholds mirror the PWA Monitor card. |

### Backlog (already shipped)

| ID | Title | Shipped in | Notes |
|----|-------|-----------|-------|
| BL2 | Biometric unlock | v0.9.0 | Promoted per ADR-0042. Passphrase-bound variant now tracked as BL16. |
| BL4 | Wear Tile | v0.5.0 | Data Layer pipe lives under v0.33.12 WearSyncService. |
| BL6 | Home-screen widget | v0.4.0 | |
| BL8 | SQLCipher Android driver swap | v0.1.x | Keystore-derived passphrase. |
| BL9 | 3-finger swipe-up server picker | v0.3.0 | |
| BL10 | Android Auto ListTemplate | v0.5.0 | |
| BL11 | Full schedule editor CRUD | v0.12.0 | |
| BL12 | KG Add / Timeline / Research views | v0.13.0 | |
| BL17 | Wear Data Layer pairing | v0.33.12 | WearSyncService publishes `/datawatch/counts` DataItem. |
| BL18 | WS PTY-resize negotiation | v0.23.0 | `resize_term` outbound frame. |
| BL20 | Saved command library CRUD | v0.12.0 | |

### Features (sprint completions)

| ID | Title | Shipped in |
|----|-------|-----------|
| F1 | Sprint 1 — foundation | v0.2.0 |
| F2 | Sprint 2 — WebSocket + session detail + xterm + push + multi-server | v0.3.0 |
| F3 | Sprint 3 — voice + MCP SSE + federation + widget + stats + channels | v0.4.0 |
| F4 | Sprint 4 — Wear + Android Auto | v0.5.0 |
| F5 | Sprint 5 — harden + biometric | v0.9.0 |
| F6 | Sprint 6 — ADR-0042 scope close | v0.10.0 |
| F7 | Sprint 7 — session power-user parity | v0.11.0 |
| F8 | Sprint 8 — schedules + files + config | v0.12.0 |
| F9 | v0.13–v0.22 — memory, channels, federation, behaviour prefs, update daemon | v0.13–v0.22 |
| F10 | v0.23–v0.32 — terminal parity + ConfigFieldsPanel + filters CRUD + profile CRUD + proxy resilience + Auto data | v0.23–v0.32 |
| F11 | v0.33 — on-device triage + unified monitoring spec + PWA header parity | v0.33 series |

---

## v1.0.0 target

v1.0.0 tags the release that closes full PWA parity — every row in
[`parity-plan.md`](../parity-plan.md) ✅. Current open items in the
Sprint FF / GG / HH batches above are all scheduled pre-1.0 per user
direction 2026-04-22.

See `docs/parity-status.md` (now redirected to `parity-plan.md`) for
the single authoritative matrix.
