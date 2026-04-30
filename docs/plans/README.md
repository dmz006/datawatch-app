# Plans, bugs, and backlog tracker

Single source of truth per AGENT.md "Project Tracking". IDs are permanent —
never reuse a bug (B#), backlog (BL#), or feature (F#) number.

Plans: individual dated documents live as `YYYY-MM-DD-<slug>.md` in this
directory when work warrants formal planning (3+ files or non-trivial
architecture).

---
## Open - Not Assessed
*(cleared 2026-04-29 — items converted to B52–B62 below)*

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

### Master parity inventory (2026-04-23)

Everything below is captured with per-gap IDs (G1–G64) in the
master inventory — see [`audit-2026-04-23/README.md`](audit-2026-04-23/README.md)
for the full matrix, per-screen inventories, server contract, and
disposition of the prior 2026-04-22 audit.

Release batches:
- **v0.34.7** — P1 fix pass: G5 (new-session LLM picker retest), G8 (tmux IME padding)
- **v0.34.8** — G7 Alerts rebuild (Active/Inactive tabs + per-session groups + per-alert quick-reply)
- **v0.34.9** — Sessions ergonomics: G6 (drag-drop reorder), G10 (New tab in bottom nav)
- **v0.35.0** — Settings polish + Monitor cards: G9 (input sizing), G19–G28 (Monitor cards), G45 (LLM schema cleanup), G57–G64 (visual)
- **v0.35.1** — Session-detail polish: G11 (inline rename), G12 (state-override dropdown), G13–G17 (terminal toolbar + response)
- **v0.35.2** — G41 Signal QR device linking (optional)

## Open — organised by sprint (fastest resolution first)

Refactored 2026-04-22: everything still active (bugs + unscheduled
backlog) grouped into proposed sprint batches. Batches are ordered
to land the highest-user-impact items first with the smallest code
surface per batch. All sprints are **pre-1.0** per user direction.

### Sprint FF+ — Wear & Auto feature parity (user request 2026-04-22)

| ID | Title | Notes |
|----|-------|-------|
| B31 | Wear + Auto: Sessions tab with snapshot + quick-command + voice | **HOLD** 2026-04-22: Auto already ships `AutoSummaryScreen` / `WaitingSessionsScreen` / `SessionReplyScreen` with Yes/No/Continue/Stop quick-reply. Wear's Sessions page (v0.33.25) shows counts only. User evaluating whether existing Auto scope counts as "done" before scheduling watch snapshot + voice work. Tracked at [datawatch-app#36](https://github.com/dmz006/datawatch-app/issues/36). |

### Sprint FF2 — PRD/About/icon polish (user request 2026-04-29)

| ID | Title | Notes |
|----|-------|-------|
| B36 | New PRD dialog: LLM backend list shows all, not filtered | PWA's `renderBackendSelect` skips `enabled=false` + `shell`. `listBackends()` now filters correctly (v0.43.x). |
| B37 | New PRD dialog: model should be a per-backend dropdown, not free text | PWA fetches `/api/ollama/models` and `/api/openwebui/models` separately; model field is hidden for all other backends. Implemented in `NewPrdDialog` (v0.43.x). |
| B38 | About → "Check for Update" auto-installs without confirmation | Should check first; if update found show "Install Update vX.Y.Z" button + progress bar. Two-step UX implemented in `UpdateDaemonCard` (v0.43.x). Blocked on [dmz006/datawatch#25](https://github.com/dmz006/datawatch/issues/25) for true check-only. |
| B39 | App launcher icon: eye too small | Eye sclera expanded to ±40×26 (from ±22×14), iris to r=22, pupil to r=9 to fill most of the 108dp canvas. Done (v0.43.x). |
| B40 | Play Store + in-app alternate icon variants | Need 2-3 icon variants (teal, dark-high-contrast, monochrome) in SVG/PNG for Play Store chooser. Pending. Tracked at [datawatch-app#35](https://github.com/dmz006/datawatch-app/issues/35). |
| B41 | Server: `GET /api/update/check` check-only endpoint | Filed at [dmz006/datawatch#25](https://github.com/dmz006/datawatch/issues/25). Enables true "check → confirm → install" UX on mobile without double-calling POST /api/update. |
| B42 | Splash + About eye: bigger/bolder; standalone eye in About | Splash eye radius ↑ (0.34→0.44×discRadius), sclera stroke 3.5 dp, crosshair 4.5 dp; `EyeOnlyAnimated` composable added; About card now shows large standalone animated eye instead of tiny tablet-scene eye. Done (v0.44.0). |
| B43 | Wear session popup: voice transcription review before send | After transcription, show `TranscriptReviewPopup` (full-screen overlay over session popup) with transcript text + Cancel/Send. Removed inline transcript text + left-edge Send chip from session popup. Done (v0.44.0). Also fixed pre-existing curly-quote compile errors in WearMainActivity. |
| B44 | Session detail: animated processing indicators when Running | Added pulsing Running badge (alpha 0.55→1.0, 700 ms) in `SessionInfoBar`. Added `GeneratingIndicator` (three animated dots) below terminal when state=Running. Done (v0.44.0). |
| B45 | Session terminal scroll-back broken — page up/down ignored | Fixed (v0.45.0): (1) host.html now pauses pane_capture writes while scroll mode is active (ESC[2J wipe no longer kicks the user out of their scroll position); (2) `_userScrolled` flag tracks manual scroll and skips `scrollToBottom()` when set; (3) `TerminalController.setScrollMode()` syncs JS flag from Kotlin when the 📜 button is tapped; (4) chat/event LazyColumns respect user scroll via `derivedStateOf { isAtBottom }`. |
| B46 | Session toolbar: remove ↑↓ arrows; scroll mode overlay | ↑↓ removed from quick-actions row. Scroll icon changed to 📜. When scroll mode is on the full composer+mic+send area is replaced with big PgUp/PgDn/↑/↓/ESC overlay. Done (v0.45.0). |
| B47 | New session start: error shown even though session starts | `StartSessionResponseDto.state` was non-nullable — if server omits it, parse fails after session is created. Made optional (v0.44.0). |
| B48 | Settings → LLM → claude-code: add auto-accept disclaimer toggle | `session.claude_auto_accept_disclaimer` toggle added to LlmBackendSchemas claude-code block. Done (v0.45.0). Tracked at datawatch-app#27. |
| B49 | Settings → Operations: subsystem hot-reload card | `SubsystemReloadCard` added (POST /api/reload?subsystem=config|filters|memory). Shows applied[] + requires_restart[] from response. Done (v0.45.0). Tracked at datawatch-app#28. |
| B50 | Update check: use GET /api/update/check (404-fallback to POST) | `checkUpdate()` added to TransportClient + RestTransport. `UpdateDaemonCard` now calls GET for check, POST only for install. Older daemons fallback transparently. Done (v0.45.0). Tracked at datawatch-app#30. |
| B51 | Quick commands: add Enter key | `\n` → "Enter" added to system commands in `QuickCommandsSheet`. Hard-coded pending datawatch#28 (server-side quick commands). Tracked at datawatch-app#31. Done (v0.45.0). |
| B52 | PRD: `PrdDto` missing `spec` field — EditPrdDialog starts blank | `spec: String? = null` added to `PrdDto`. `EditPrdDialog` now pre-populates the spec text area from `prd.spec`. Done (v0.45.0). |
| B53 | PRD: AutonomousScreen filter chips missing statuses | Added `revisions_asked`, `approved`, `decomposing`, `cancelled` chips to the filter row — all 8 PRD statuses now reachable. Done (v0.45.0). |

### Sprint II — session connection resilience (v0.46.x)

Items from live-device testing 2026-04-29. Layout items (B54–B57) were already implemented in v0.35.9/v0.42.0 and closed on review. Remaining items are the connection-resilience + visual-polish gaps.

| ID | Title | Notes |
|----|-------|-------|
| B54 | Session detail: input bar order doesn't match PWA | ✅ Already done (v0.35.9) — Send → Schedule → Mic in `ReplyComposer`. |
| B55 | Session detail: quick-actions row layout — last-response + quick-cmd icon + arrows | ✅ Already done (v0.35.9) — Row: Description icon → Keyboard icon → ← →. Quick-cmd button removed from under mic. |
| B56 | Session detail: status badge above tabs; tabs tighter + left-justified | ✅ Already done (v0.35.9 badges above, v0.42.0 compact tabs row with font/scroll right). |
| B57 | Session detail: delete only shown when stopped; show restart when stoppable | ✅ Already done — `SessionInfoBar` shows Restart+Delete only when `isDone` (Completed/Killed/Error). |
| B58 | Session detail: refresh state on open or screen unlock | `LifecycleEventObserver(ON_RESUME)` added in `SessionDetailScreen` calls `vm.refreshFromServer()` on each resume. VM's init already handles first-open; the observer catches lock/unlock and background→foreground transitions. Done (v0.45.0). |
| B59 | Connection status indicator should go red on disconnect | Fixed (v0.45.0): `startStream.onEach` now sets `_reachable = false` on `SessionEvent.Error` (WS disconnect). Non-Error events set `_reachable = true`. REST-based `isReachable` also writes on each poll, so REST-only failures also go red. |
| B60 | Tailscale awareness: back-off + retry on unreachable server | Fixed (v0.45.0): `pauseStream()` / `resumeStream()` added to `SessionDetailViewModel`. Lifecycle observer in `SessionDetailScreen` calls `pauseStream()` on `ON_STOP` (screen locked/app backgrounded) and `resumeStream()` on `ON_START`. WS reconnect loop no longer runs while the screen is off. |
| B61 | Session terminal: per-message generation spinner (PWA parity) | ✅ Already done (B44) — `GeneratingIndicator` (3 animated dots + "generating" label) shown below terminal when state=Running. Pulsing badge in `SessionInfoBar`. |
| B62 | Session detail: last-response icon inconsistent across surfaces | ✅ Already consistent — `Icons.Filled.Description` used in both the quick-actions row and the sessions-list row. SessionInfoBar's response button is suppressed (`hasResponse=false`). |
| B63 | Stats: per-process eBPF network viewer | Read-only panel under Settings → Monitor; server already exposes data. Deferred post-1.0. |
| B64 | Docs: README screenshots, Android section, Android Auto demo GIF | Done (v0.46.0) — phone+watch+auto slideshows, Android/Wear/Auto sections added. Auto: 5 dark-mode AAOS screenshots + debug day-mode GIF, both in README side-by-side. |
| B65 | PRDs tab: rename to "Autonomous", match PWA icon + header name | Done (v0.47.0) — bottom nav label, TopAppBar title, FAB contentDescription, empty-state text, Wear page title all updated to "Autonomous". |
| B66 | PRDs/Autonomous page: card-style layout matching Sessions page | Done (v0.47.0) — `PrdRow` now wrapped in `pwaCard()` with horizontal+vertical padding matching Sessions cards. Eye watermark (ic_launcher_foreground, 85% width, 10% alpha) added as Box background behind LazyColumn, matching Sessions page. |
| B67 | AAOS: app crashes on launch — AppWidgetManager null on Automotive | Done (v0.46.0) — `SessionsWidget.requestUpdate` crashes with NPE on AAOS because `AppWidgetManager.getInstance()` returns null (no home-screen widgets on Automotive OS). Fixed: null-check guard added in `SessionsWidget.kt:155`. |
| B68 | Android Auto: app icon eye overflows icon boundary | Done (v0.47.0) — Foreground sclera shrunk from ±40×26 to ±33×21 (fits within the 66dp safe zone, positions 21–87 on 108dp canvas). Iris r=22→18, pupil r=9→7, crosshairs and matrix rain repositioned within safe zone. |
| B69 | Android Auto: Autonomous tab with contextual actions | Done (v0.47.0) — `WaitingPrdsScreen` expanded to include running plans (needs_review + revisions_asked + running). `PrdActionScreen` is now status-aware: shows Approve/Reject for review states, Stop (cancel) for running plans. `AutoSummaryScreen` row renamed to "Autonomous". `WaitingPrdsScreen` title updated to "Autonomous plans". Both text strings de-PRD'd to "plans". |

### Sprint FF — live-device polish (next, v0.33.24+)

In-flight fixes from the current test pass. Small / cosmetic; aim
for one commit per batch.

| ID | Title | Notes |
|----|-------|-------|
| B28 | Watch + Auto need to view monitoring stats for all connected servers | Done (v0.48.0) — **Auto**: `AutoMonitorScreen` refactored to fetch all enabled servers in parallel (`coroutineScope { async{}.awaitAll() }`); single-server shows full detail gauge rows, multi-server shows one compact summary row per server (CPU · Mem · sessions). **Wear**: `WearSyncService` adds an all-servers parallel poll that publishes to `/datawatch/allStats` (parallel float arrays); `WearMainActivity` ViewModel consumes the new DataItem and `MonitorPage` switches between gauge grid (1 server) and `MultiServerMonitor` compact list (2+ servers). |

### Sprint GG — unified monitoring Phase 1 (v0.34.x)

Issue dmz006/datawatch#20 is closed — these items are now unblocked.

| ID | Title | Notes |
|----|-------|-------|
| B5 | Stats density + GPU / eBPF detail | Mostly done (v0.46.x) — StatsScreen has GPU row (gpuName/gpuUtilPct/gpuMemUsedMb/gpuMemTotalMb), BackendHealthCard, EnvelopesCard (observer envelopes), OllamaStatsCard, eBPF Degraded banner, RTK card. **Remaining:** per-core CPU strip (needs server to emit `cpu_cores_detail: []`). Unblocked now that issue #20 closed. |
| B10 | Live system-stats streaming | Done (v0.47.0) — `StatsHub` singleton (SharedFlow) added to `shared/transport/ws/`. `WebSocketTransport` routes `stats`-type frames to `StatsHub` before EventMapper sees them. `StatsViewModel` subscribes to `StatsHub.flow` and overlays live values on top of the 5 s REST poll — when a session WS is active the Monitor tab updates at the server's broadcast cadence without a separate REST round-trip. |
| B11 | Per-session stats panel w/ wheels + graphs | [Phase 3 of spec](2026-04-22-unified-monitoring.md) — per-process eBPF taps from the cluster container. Mobile surface is view-only per ADR-0019 — but ships pre-1.0. Unblocked. |

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
| B34 | Watch popup loads stale last_response on first open | v0.42.13 | Removed `lastLine` fallback — popup now always shows "Loading…" until the fresh `/datawatch/sessionDetail` reply arrives. |
| B35 | Phone session-detail empty band between SessionInfoBar and tabs row | v0.42.11 | Composer chip-row removed; empty-space root cause eliminated. |
| B29 | Session-detail TopAppBar title sometimes cropped | v0.42.14 | Added `fillMaxWidth()` to title Column, title Text, and subtitle Row so Ellipsis kicks in before the connection dot is displaced. |

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
