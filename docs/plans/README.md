# Plans, bugs, and backlog tracker

Single source of truth per AGENT.md "Project Tracking". IDs are permanent —
never reuse a bug (B#), backlog (BL#), or feature (F#) number.

Plans: individual dated documents live as `YYYY-MM-DD-<slug>.md` in this
directory when work warrants formal planning (3+ files or non-trivial
architecture).

## Active roadmap plan

**Active arc: PWA parity alignment (Sprints 33–39)**. 22 gaps across 9 pages. Daemon baseline: v7.0.0-alpha.50.

Full arc plan: [`2026-05-12-parity-arc-v0.102.md`](2026-05-12-parity-arc-v0.102.md)  
PWA full spec: [`2026-05-12-pwa-full-spec.md`](2026-05-12-pwa-full-spec.md)  
Gap audit: [`2026-05-12-pwa-parity-audit.md`](2026-05-12-pwa-parity-audit.md)

Current state: **v0.101.0/179**. Arc: v0.102.0 → v0.108.0, then v1.0 GH release tag.

Shipped arc history (moved to `historical-plans/`):
- `2026-05-09-v0.70-upgrade-arc.md` — v0.70–v0.79 (Sprints 1–10; issues #51–#96 + alpha alignment)
- `2026-05-10-v0.80-parity-arc.md` — v0.80–v0.86 (Sprints 11–17; PWA parity + #96 #104–#110)
- `2026-05-11-v0.94-alpha40-arc.md` — v0.94.0–v0.101.0 (Sprints 24–32; alpha.31–alpha.40 follow-on)

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

## Open — alpha.31–alpha.36 follow-on (Sprints 24–29)

Issues filed under epic #94. Ordered by dependency: Automata (24) and Stats (25) are independent; Status tab (26) builds on Stats; Alerts tabs (27) are parallel; UnifiedPush (28) is independent; Sessions filter (29) is independent.

### Sprint 24 — Automata browse redesign (#117 / alpha.31)

| Surface | Change |
|---------|--------|
| Automata list | Pin button per row (📌); persisted per server profile; pinned cards always at top |
| Sort order | pinned → state-rank (waiting/needs_review → blocked → running → planning → done) → last-activity desc |
| Card content | Inline `Open` / `Cancel` / `Approve` buttons; `Approve` highlighted in `needs_review`/`revisions_asked`/`waiting_input`; status pill + last-activity timestamp top-right; `Stories & tasks` collapsible |
| Alert dock | Shrink max-width on phone screens to match alpha.31 change |
| Locale | `automata_action_*` keys (5 bundles) |
| Wear | Optional: `needs_approval` automata count complication |

### Sprint 25 — Per-session Stats sub-tab redesign (#118 / alpha.32)

| Surface | Change |
|---------|--------|
| Session detail Stats screen | Sectioned cards: Host (always) · Container (if `env.container_id`) · ComputeNode (if `compute_node_ref`) · LLM (if `llm_ref`) |
| Sparklines | CPU/RSS sparklines with 60-sample history buffer; use Compose Charts or raw Canvas |
| Click-through | ComputeNode card → Compute panel; LLM card → LLM panel |
| Locale | `stats_card_*`, `stats_field_*`, `stats_open_*`, `stats_llm_more_soon` (5 bundles) |

### Sprint 26 — Status sub-tab + hook arc (#120 #121 / alpha.34/34d)

| Surface | Change |
|---------|--------|
| Session detail | Add "Status" 4th sub-tab (Tmux → Channel → Stats → Status) |
| Status tab badge | 🟢/🟠/⚪ from `board.state`; 5s poll via `GET /api/sessions/<id>/status` while tab open |
| Status board cards | Current focus · Sprint · Tests · Git — conditional render |
| Hook health pill | "● hooks alive/stale/missing" indicator |
| All backends | Status board lights up for opencode/openwebui/ollama/council via alpha.34d universal emit |
| Alert content | Verify truncation handles longer body (`· last tool: X · <snippet>` suffix from alpha.34b) |
| Toast | One-time "hooks installed in `<project>/.claude/`" on claude-code session launch |
| Locale | `session_detail_tab_status`, `status_card_*`, `status_no_*`, `status_current_focus`, `status_hooks_*` (5 bundles) |

### Sprint 27 — Ollama marketplace + Alerts tabs (#119 / alpha.33)

| Surface | Change |
|---------|--------|
| Compute Node edit | Models sub-section for ollama-kind: list installed models + Browse marketplace button |
| Marketplace screen | Curated catalog from `GET /api/marketplace/ollama/catalog`; search + per-model tag grid (size/Min RAM/Min VRAM/fit ✓⚠/Pull) |
| Pull progress | Poll `GET /api/marketplace/ollama/tasks/<id>` every 2s; show progress in alert dock or inline |
| Alerts screen | Restore Active / Historical / System tabs; per-tab chip+sort+search persistence |
| Alert dock sizing | Match alpha.33 middle-ground (max-width 420, bigger fonts, per-type color rail) |
| Locale | `alerts_*_tab_label`, `ollama_*`, `compute_models_*`, `compute_field_models` (5 bundles) |

### Sprint 28 — UnifiedPush integration (#122 / alpha.35)

| Surface | Change |
|---------|--------|
| App start | POST `/api/push/register` with app's UnifiedPush distributor endpoint + stable `client_id` |
| Topic subscription | Subscribe to `alerts` (catch-all) and/or `session-<id>` via SSE `EventSource` or registered push endpoint |
| Notification render | `title` = headline, `message` = body, `priority` 4-5 = high, `tags` = session/backend hints, `click` = deep-link |
| Tailscale fallback | Treat UnifiedPush and Signal/comm-channel as redundant safety nets (not primary+fallback) |
| Note | alpha.35a (council/error/algorithm events) pending operator topic-taxonomy confirmation |

### Sprint 29 — Sessions filter UX (#123 / alpha.36)

| Surface | Change |
|---------|--------|
| Sessions list filter bar | `LLM (N) ▸` button collapses 8 backend-family badges; tap to expand inline |
| State filter | `State (N) ▸` button collapses all real states with colored dot rail (Running 🟢 / Waiting 🟠 / Rate-limited 🔴 / Complete ⚪ / Failed 🔴 / Killed ⚪) |
| Filter input | ~10% wider relative to previous flex weight |
| Highlight | LLM/State buttons highlight when filter selection is non-default |
| Locale | `llm_filter_btn_tip`, `state_filter_btn_tip` (5 bundles) |

---

## Open — organised by sprint (fastest resolution first)

Refactored 2026-04-22: everything still active (bugs + unscheduled
backlog) grouped into proposed sprint batches. Batches are ordered
to land the highest-user-impact items first with the smallest code
surface per batch. All sprints are **pre-1.0** per user direction.

### Sprint FF+ — Wear & Auto feature parity (user request 2026-04-22)

| ID | Title | Notes |
|----|-------|-------|
| B31 | Wear + Auto: Sessions tab with snapshot + quick-command + voice | **HOLD** 2026-04-22: Auto already ships `AutoSummaryScreen` / `WaitingSessionsScreen` / `SessionReplyScreen` with Yes/No/Continue/Stop quick-reply. Wear's Sessions page (v0.33.25) shows counts only. User evaluating whether existing Auto scope counts as "done" before scheduling watch snapshot + voice work. Tracked at [datawatch-app#36](https://github.com/dmz006/datawatch-app/issues/36). |
| BL22 | ✅ Wear Monitor: active-server selection indicator | Done (v0.53.0) — active row shows `✓` prefix in teal + bold weight; inactive online rows show `●` in onSurface; offline shows `○` in error red. `WearMainActivity.kt MonitorPage` multi-server branch. |
| BL23 | ✅ i18n: add action_yes / action_no / common_loading / common_no_alerts keys | Done (v0.53.0) — added to all 5 locale dirs in both composeApp and wear modules; `common_loading` wired into LlmConfigCard, DaemonOpsCards, ConfigFieldsPanel, SettingsScreen, McpToolsCard replacing hardcoded literals. Closes [datawatch-app#39](https://github.com/dmz006/datawatch-app/issues/39). |
| BL24 | ✅ Settings → About: MCP channel bridge info card | Done (v0.53.0) — new `McpChannelCard` in Settings → About tab mirrors `GET /api/channel/info`. Shows bridge kind (Go ✓ / JS ⚠ coloured with success/error), status, path, and collapsible stale-.mcp.json list. `TransportClient.fetchChannelInfo()` + `RestTransport` impl added. Closes [datawatch-app#38](https://github.com/dmz006/datawatch-app/issues/38). |
| BL25 | ✅ New-session loading overlay | Done (v0.54.0) — `SessionLoadingOverlay` (`AnimatedVisibility` + `EyeOnlyAnimated` + Canvas lightning bolt + pulsing "Loading" label) shown when navigating from `NewSessionScreen`. Dismisses with 400 ms fade on first `pane_capture` or terminal session state. `isNew` query-param added to `sessions/{sessionId}` route; `AppRoot` passes `isNew=true` from `onStarted`. |
| BL26 | ✅ Session detail: GeneratingIndicator + state badge missing on session open | Done (v0.54.0) — Root cause: `startStream()` was called before `refreshFromServer()` so WS delivered the first `pane_capture` (dismissing the overlay) while `state.session` was still null (REST in flight). Fixed by awaiting `doRefreshFromServer()` before `startStream()` in `init`. Added `wsSessionRefreshFired` safety-net: if REST failed and session is still null on first WS event, one more REST refresh is triggered. |
| BL27 | ✅ Wear: background battery drain + wake-on-alert | Done (v0.56.0) — Watch is fully passive (DataLayer only; no WS on watch). Wake-on-alert: `WearAlertListenerService` (`WearableListenerService`) receives `/datawatch/alert` message from phone and posts a high-priority notification. Phone side: `WearSyncService` tracks `prevWaitingIds`; when new sessions enter `waiting` state it calls `alertWatchNodes()` which pushes the message to connected nodes via `MessageClient`. |

### Sprint FF3 — claude-code advanced options (v5.27.5 parity)

| ID | Title | Notes |
|----|-------|-------|
| B70 | New Session: claude-code Advanced options block (permission mode + model + effort) | Done (v0.49.0) — fetches from /api/llm/claude/{models,efforts,permission_modes}; shown when backend = claude-code AND server ≥v5.27.5 (404 = hidden). Passes to POST /api/sessions/start. Closes [datawatch-app#32](https://github.com/dmz006/datawatch-app/issues/32). |
| B71 | PRD dialog: permission mode dropdown | Done (v0.49.0) — added to `NewPrdDialog` alongside backend/effort/model. Fetches from /api/llm/claude/permission_modes (same probe as #32). Sent as `permission_mode` on PRD create. Closes [datawatch-app#33](https://github.com/dmz006/datawatch-app/issues/33). |

### Sprint FF2 — PRD/About/icon polish (user request 2026-04-29)

| ID | Title | Notes |
|----|-------|-------|
| B36 | New PRD dialog: LLM backend list shows all, not filtered | PWA's `renderBackendSelect` skips `enabled=false` + `shell`. `listBackends()` now filters correctly (v0.43.x). |
| B37 | New PRD dialog: model should be a per-backend dropdown, not free text | PWA fetches `/api/ollama/models` and `/api/openwebui/models` separately; model field is hidden for all other backends. Implemented in `NewPrdDialog` (v0.43.x). |
| B38 | About → "Check for Update" auto-installs without confirmation | Should check first; if update found show "Install Update vX.Y.Z" button + progress bar. Two-step UX implemented in `UpdateDaemonCard` (v0.43.x). Blocked on [dmz006/datawatch#25](https://github.com/dmz006/datawatch/issues/25) for true check-only. |
| B39 | App launcher icon: eye too small | Eye sclera expanded to ±40×26 (from ±22×14), iris to r=22, pupil to r=9 to fill most of the 108dp canvas. Done (v0.43.x). |
| B40 | Play Store + in-app alternate icon variants | Assets done (v0.43.0) — `docs/media/store/store_icon_teal.{svg,png}`, `store_icon_contrast.{svg,png}`, `store_icon_mono.{svg,png}`. Submission to Play Console is a manual step. In-app icon chooser via ActivityInfo alias switching is optional / post-v1. Issue [#35](https://github.com/dmz006/datawatch-app/issues/35) closed. |
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
| B63 | ✅ Stats: per-process eBPF network viewer | Done (v0.57.0) — `EBpfNetworkCard` in Settings → Monitor, after `EBpfStatusCard`. Reads `StatsDto.envelopes`, filters rows where `netRxBps + netTxBps > 0`, sorts by total bandwidth. Renders live table when `ebpfActive==true`; when `ebpfEnabled==true && !ebpfActive` shows "configured but not active" amber placeholder (datawatch-app#42 fix). Related server bugs: datawatch#35 (capability detection false negative), datawatch#36 (string vs bool config). |
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
| B5 | Stats density + GPU / eBPF detail | Done (v0.50.0) — StatsScreen has GPU row, BackendHealthCard, EnvelopesCard, OllamaStatsCard, eBPF Degraded banner, RTK card. Per-core CPU strip added: `StatsDto.cpuCoresDetail` field (`cpu_cores_detail: []`), rendered as a compact 8-per-row grid of colour-coded mini-bars (green/amber/red thresholds at 60%/80%) below the aggregate CPU Load bar. Renders only when server emits the field; absent on older daemons. |
| B10 | Live system-stats streaming | Done (v0.47.0) — `StatsHub` singleton (SharedFlow) added to `shared/transport/ws/`. `WebSocketTransport` routes `stats`-type frames to `StatsHub` before EventMapper sees them. `StatsViewModel` subscribes to `StatsHub.flow` and overlays live values on top of the 5 s REST poll — when a session WS is active the Monitor tab updates at the server's broadcast cadence without a separate REST round-trip. |
| B11 | ✅ Per-session stats panel w/ wheels + graphs | Done (v0.57.0) — `SessionStatsPanel` composable added to session detail as a third "stats" tab (alongside tmux / channel). Finds matching `StatEnvelopeDto` from `StatsDto.envelopes` by sessionId prefix match. Shows CPU ring (threshold-coloured), RSS, threads, FDs, net Rx/Tx, GPU when present. Self-hides data rows with a message when no envelope matches. |

### Sprint HH — BL backlog pulls (v0.35.x)

Unscheduled backlog items that align with the PWA-parity push —
pulled out of the BL pool into real sprints since everything needs
to land pre-1.0.

| ID | Title | Notes |
|----|-------|-------|
| BL1 | Split consolidated `decisions/README.md` into per-ADR MADR files | Done (v0.48.0) — 43 individual MADR files written to `docs/decisions/`; README replaced with a linked 43-row index table. |
| BL3 | Tablet two-pane layout | Done (v0.48.0) — responsive pass: `HomeScreen` NavHost wrapped in a centered `Box` with `widthIn(max = 840.dp)` so single-column content doesn't stretch to full tablet width. On phones (<840 dp) `fillMaxWidth` wins and layout is unchanged. Full two-pane sessions-list + detail side by side deferred to post-v1 (requires NavController restructuring + tablet to test). |
| BL5 | ✅ iOS content phase | Done (v1.0.4) — SwiftUI native client; all 6 tabs functional (Sessions, Terminal, Alerts, Automata CRUD, Observer, Dashboard); Stories 1-17 shipped. |
| BL7 | ✅ Foldable layout (Pixel Fold / Z Fold) | Done (v0.57.0) — `HomeShell` in `AppRoot.kt` detects `LocalConfiguration.current.screenWidthDp >= 600`. On wide screens: `Row` layout with 360 dp left pane (existing Scaffold + BottomNavBar + NavHost) and `weight(1f)` right pane showing `SessionDetailScreen` for the selected session. Sessions and Alerts tabs' `onOpenSession` intercepts to set `selectedSessionId` state instead of pushing a nav destination. Narrow phones keep existing single-pane nav unchanged. |
| BL13 | Adjustable terminal dimensions | Done (v0.48.0) — `resize_term` is sent automatically on every xterm fit event: `host.html:safeFit()` calls `DwBridge.onResize(cols, rows)` → `WsOutbound.sendResizeTerm()`. Backend minimum sizes (claude-code: 120×40, else 80×24) trigger resize on session open. Manual "Fit" button re-syncs after font changes. |
| BL14 | Raw YAML config editor (behind biometric + confirm) | Done (v0.48.0) — `RawConfigCard` in Settings → General tab. Fetches /api/config, renders pretty-printed JSON in a monospace 360dp dialog, gated behind explicit Overwrite confirm dialog per ADR-0019. |
| BL15 | Localization (DE, ES, FR, JA) | Done (v0.52.0) — Full string-resource extraction across all UI screens (SettingsScreen, StatsScreen, AlertsScreen, AutonomousScreen, SessionsScreen, SessionDetailScreen, NewSessionScreen, NewPrdDialog, PrdDetailDialog, BottomNavBar) + Wear OS (WearMainActivity: page titles, session popup, filter row, transcript review, Autonomous/Server pages, About). EN base + DE/ES/FR/JA translations for both composeApp and wear modules. `SettingsTab` enum migrated to `@StringRes Int` labels; SecurityCard migration error uses format string. PWA parity gap: [datawatch#32](https://github.com/dmz006/datawatch/issues/32). |
| BL16 | Biometric-bound DB passphrase | Done (v0.50.0) — `KeystoreManager` gains `ensureBiometricKeyExists()` (AES-256-GCM Keystore key with `setUserAuthenticationRequired(true)` + 30 s window, API-level-split for API 29 vs 30+). `migratePassphraseToBiometricKey()` / `migratePassphraseFromBiometricKey()` run within the biometric auth window. `AndroidDatabaseFactory.driver()` prefers the biometric key when `hasBiometricPassphrase()` is true, with silent fallback to EncryptedSharedPreferences copy. `SecurityCard` in Settings triggers the biometric confirmation prompt on toggle instead of toggling directly — migration runs on success. |
| BL19 | ❄️ FROZEN — Local-LLM orchestration — in-app PRD/HLD authoring + Ollama backend + task fire-off | Frozen 2026-05-04 per user direction. No ADR, no schedule. Revisit only when user explicitly unfreezes. |
| BL21 | Signal device-linking (`/api/link/*` + QR SSE) | Needs QR rendering from SSE frames + paired-state persistence. Server issue: [datawatch#31](https://github.com/dmz006/datawatch/issues/31). |

### Parking lot (waiting on upstream / user gesture)

| ID | Title | Waiting on |
|----|-------|-----------|
| B6 | ❄️ FROZEN — Push via FCM | Frozen 2026-05-04 per user direction. ntfy-only is the policy. Revisit only when user explicitly unfreezes. |
| B31 | **HOLD** — Wear + Auto: Sessions snapshot + quick-command + voice | User still evaluating whether existing Auto scope counts as done. No action until user decides. |
| Store assets | `docs/media/store/phone/`, `tablet-10/`, `tablet-7/` untracked | Commit in the next convenient version bump. |

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
| #92 | ✅ Council persona wizard (5-step interview, AI-assist refine, edit/re-interview parity) | v0.77.0 |
| #96 | ✅ Sprint 11 — Compute reorder + CostRatesCard + TailscaleCard + RoutingRulesCard | v0.80.0 |
| (parity-12) | ✅ Sprint 12 — Automata tab flat order + PipelineManagerCard + OrchestratorGraphsCard | v0.81.0 |
| (parity-13) | ✅ Sprint 13 — SessionTemplates + DeviceAliases + Tooling + Secrets + ObserverQuicklink | v0.82.0 |
| #104 #105 | ✅ Sprint 14 — Session LLM picker + LLMRef/ComputeNodeRef badges + state filter chips | v0.83.0 |
| #106 #110 | ✅ Sprint 15 — Compute CRUD overhaul (kind restriction, migration banner, enabled switch) + free-observer mapping | v0.84.0 |
| #107 #108 | ✅ Sprint 16 — Wear notifications (council+error) + health tile + automata complication | v0.85.0 |
| #109 | ✅ Sprint 17 — Android Auto voice command scaffold (status/report/cancel/refresh) | v0.86.0 |
| (alpha.27) | ✅ backend_family ↔ llm_backend compat fix | v0.87.0 |
| #111 | ✅ Observer by-node grouping + alpha.25 settings move | v0.88.0 |
| #113 | ✅ OpenCode multi-select models + agent-settings editor | v0.89.0 |
| #114 | ✅ Alert dock overlay (alpha.29) | v0.90.0 |
| W-#114 | ✅ Wear alerts tile + complication | v0.91.0 |
| #115 | ✅ Alerts redesign — chip filter + sort + search + dismiss-all (alpha.30) | v0.92.0 |
| #116 | ✅ Watch toggle opt-in (Sprint 23) — per-session/automata badge filtering | v0.93.0 |
| #117 | ✅ Automata browse redesign + sessions filter collapsible (Sprint 24+29) | v0.94.0 |
| #118 | ✅ Stats sub-tab sectioned cards + sparklines (Sprint 25) | v0.95.0 |
| #120 | ✅ Session detail Status 4th tab (Sprint 26) | v0.96.0 |
| #121 | ✅ Hook arc all-backends (Sprint 26) | v0.96.0 |
| #119 | ✅ Ollama marketplace + Alerts 3-tab redesign (Sprint 27) | v0.97.0 |
| #122 | ✅ UnifiedPush SSE alerts stream (Sprint 28) | v0.98.0 |
| #123 | ✅ Sessions filter UX polish (Sprint 24+29) | v0.94.0 |
| #124 | ✅ LLM per-node model table + Automata batch confirm guards (Sprint 30) | v0.99.0 |
| #125 | ✅ Council persona CRUD + alert dock gating audit (Sprint 31) | v0.100.0 |
| B31  | ✅ Wear sessions list + state badges (Sprint 32) | v0.101.0 |
| BL21 | ✅ Signal device-linking (`/api/link/*` + QR SSE flow) | v0.64.0 |

---

## v1.0.0 target

PWA parity through alpha.23c was closed at v0.86.0. Alpha.31–alpha.36 follow-on features are tracked
in Sprints 24–29 above (7 open issues, no external blockers).

Remaining pre-v1.0 work after Sprints 24–29:
- Sprint 23 test debt (4 ViewModelTests for toggleWatch/watchedIds/watchedAlertCount/BottomNavBar) — deferred, see B31 hold
- alpha.35a UnifiedPush topic taxonomy (council/error/algorithm events) — awaiting operator confirmation from parent project
- B31 HOLD (Wear snapshot + voice) — user evaluating whether existing Auto scope counts as done

See `docs/parity-status.md` for the parity matrix.
