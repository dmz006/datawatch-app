# Changelog

All notable changes to this project will be documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/) per
[AGENT.md Versioning rules](AGENT.md#versioning).

## [Unreleased]

## [0.33.0] — 2026-04-22 (Sprint CC — Auto actually ships in the APK + docs refresh)

### Fixed

- **`:auto` module is now bundled into the composeApp APK.**
  Previously the auto library built cleanly but wasn't a
  dependency of `:composeApp`, so the `CarAppService` never
  reached the APK and Android Auto never saw the app. Added
  `implementation(project(":auto"))` with
  `missingDimensionStrategy("surface", ...)` to bridge the two
  modules' flavor dimensions. AAPT dump confirms the
  CarAppService intent-filter + `automotive_app_desc` metadata
  + `minCarApiLevel` metadata all merge into the final APK.

### Added

- **`androidx.car.app.minCarApiLevel = 1`** manifest metadata in
  `auto/publicMain/AndroidManifest.xml`. Required by modern Auto
  hosts to negotiate the template protocol.
- **`docs/android-auto.md` rewritten** for current v0.32+ reality
  — three-screen nav graph, AutoServiceLocator DI, DHU /
  in-car test instructions, known gaps (TTS announcement,
  voice-reply, allowlist Play-submit verification).
- **`docs/README.md` refreshed** — upstream-issue section
  updated to reflect 18 closed + 1 open-meta state.

### Parity-plan

- Three rows flipped from stale ❌/🚧 to ✅ (Pick model variant,
  Pick profile, Voice-to-new-session) — all shipped in earlier
  sprints, the labels were just stale.

## [0.32.0] — 2026-04-22 (Sprint BB — close the PWA-parity gap list)

Closes every concrete PWA-parity gap the 2026-04-21 honest-audit
surfaced.

### Added

- **Detection filters card** under Settings → LLM. Mirrors PWA
  `loadDetectionFilters` — four pattern lists (prompt /
  completion / rate-limit / input-needed) plus two timing
  fields (prompt_debounce, notify_cooldown). Distinct from
  Output Filters (`/api/filters` CRUD) — these live in
  `config.detection.*_patterns`.
- **Arrow keys + Tab + PageUp/Down in the Quick Commands sheet.**
  Seven new chips send the raw ANSI control sequences
  (`\x1B[A`/`B`/`C`/`D`, `\x1B[5~`/`[6~`, `\t`) via the existing
  reply path. Matches PWA f00f534.
- **MCP tools in-app viewer** under Settings → About. Reads
  `/api/mcp/docs` and renders tool catalogues whether the server
  emits a flat array or grouped categories.
- **CA cert install card** under Settings → Comms. Download
  button + system-security-settings shortcut + expandable
  Android and iPhone step-by-step instructions verbatim from
  the PWA install block.
- **Project + Cluster profile create + edit dialogs**. Minimal
  fields (name + description) exposed; nested blocks
  (image_pair / git / memory / kubernetes context) preserved
  from server response so mobile round-trips without nuking the
  schema. Full deep edits still happen on the PWA.
- **Update daemon progress bar** — indeterminate
  `LinearProgressIndicator` shows while the update call is in
  flight, matching PWA v4.0.6's "Downloading update…" strip.
- **Chat-mode bubble rendering** — session-detail chat tab now
  renders events as avatar + role + body bubbles with role-
  tinted surface colours instead of plain flat rows. Mirrors
  PWA `renderChatBubble` styling (markdown rendering is a
  post-1.0 polish).
- **Session backlog grid** at the bottom of the New Session
  form — up to 20 most-recent done sessions with per-row
  Restart button. Matches PWA `renderSessionBacklog`.

### Changed

- **Auto HostValidator is now strict on release builds** — debug
  APKs keep `ALLOW_ALL_HOSTS_VALIDATOR` so the Desktop Head Unit
  simulator binds freely; release builds consult a new
  `R.array.hosts_allowlist` with the Google-published Auto /
  DHU / emulator signing-cert pairs (ADR-0031 compliance).

### Transport

- `putKindProfile(kind, name, body)`, `fetchMcpDocs()`.

## [0.31.0] — 2026-04-21 (Sprint AA — session reorder mode + Custom sort)

### Added

- **Reorder mode** on the Sessions tab. A new ⇅ icon in the top
  app bar toggles an arrangement mode where each row swaps its
  overflow menu for ↑ / ↓ buttons. Tap moves the session one
  slot. Entering reorder mode auto-sets the Sort dropdown to
  `Custom` (new value) so what the user arranged is what gets
  shown.
- **`SortOrder.Custom`** — fourth sort option alongside Recent /
  Started / Name. Uses the user's reorder-mode arrangement; ids
  not in the custom list fall to the tail sorted by
  last-activity.

### Notes

- Compose doesn't ship a drag-reorder LazyColumn; the up/down
  arrow pattern avoids the pointerInput + animated-offset
  complexity of a hand-rolled drag implementation while still
  letting users arrange the list. Custom ordering is
  in-memory — cross-restart persistence is a post-1.0 polish.

## [0.30.0] — 2026-04-21 (Sprint Z — Android Auto live data)

### Added

- **`AutoServiceLocator`** — mirrors phone-side `ServiceLocator`
  but lives in `:auto`. Reuses `:shared`'s `KeystoreManager` +
  `DatabaseFactory` + `ServerProfileRepository` +
  `SessionRepository` + `RestTransport` + `createHttpClient()`
  so the Auto module reads the same SQLCipher DB and hits the
  same server without touching `:composeApp` (library-to-app
  dep rule). Initialised from
  `DatawatchMessagingService.onCreate()`.
- **Auto screens now show live session counts** — Summary polls
  `TransportClient.listSessions()` every 15 s for the first
  enabled profile; Waiting list filters to `waiting_input`;
  Reply screen POSTs to `/api/sessions/reply`. All three screens
  show real data rather than placeholder.

### Play-compliance

- Templates stay ListTemplate / MessageTemplate; no free-form UI
  per ADR-0031. Quick-reply strip (Yes / No / Continue / Stop)
  is the sole action surface.

## [0.29.0] — 2026-04-21 (Sprint Y — About API links + Notifications card + cleanup)

### Added

- **API links card** under Settings → About. Four clickable rows
  open the active server's Swagger UI, OpenAPI YAML, MCP tools
  catalogue, and architecture diagrams in the system browser.
  Mirrors PWA `api` section.
- **Notifications card** under Settings → General. Shows whether
  the app currently holds POST_NOTIFICATIONS permission and
  opens the system app-notification settings for adjustments.
  Per-channel importance + sound stays in system UI (Android
  native pattern). Mirrors PWA `gc_notifs`.

### Removed

- Dead `BehaviourPreferencesCard.kt` — superseded by
  `ConfigFieldsPanel(Session)` in v0.26.0. Was writing to
  invented keys never honoured server-side; now fully out of
  tree.

## [0.28.0] — 2026-04-21 (Sprint X — Project + Cluster profile CRUD + Proxy Resilience)

### Added

- **Project Profiles + Cluster Profiles cards** under Settings →
  General. Shared `KindProfilesCard` renderer keyed on
  `kind ∈ {project, cluster}`; each row shows name + summary
  (image_pair/git for project, k8s kind/context/namespace for
  cluster), with per-row **Smoke** test + **Delete** actions.
  Create / edit stays on the PWA per ADR-0019 (nested
  image_pair / git / memory / kubernetes schema is too rich
  for a mobile dialog).
- **Proxy Resilience** section under Settings → Comms via the
  generic `ConfigFieldsPanel`. Exposes the six `proxy.*` fields
  PWA `loadProxySettings` shows: enabled, health_interval,
  request_timeout, offline_queue_size, circuit_breaker_threshold,
  circuit_breaker_reset.

### Transport

- `listKindProfiles(kind)`, `deleteKindProfile(kind, name)`,
  `smokeKindProfile(kind, name)`.

## [0.27.0] — 2026-04-21 (Sprint W — filters CRUD + New Session form fields)

### Added

- **Output filters card under Settings → LLM.** Pattern (regex)
  + action (`send_input` / `alert` / `schedule` /
  `detect_prompt`) + value (optional) per row. Per-row enable
  switch flips the rule; red ✕ deletes. Add-filter form at the
  bottom. Matches PWA `loadFilters` / `createFilter` /
  `toggleFilter` / `deleteFilter` in app.js lines 6284–6396.
- **New Session form PWA-parity fields.** Four additions on top
  of the existing task / profile / directory inputs:
  - `Session name` — distinct from the task prompt (sent as
    `name` on POST /api/sessions/start).
  - `Resume previous` dropdown — up to 30 most-recent done
    sessions; picking sets `resume_id` so the server warm-
    restarts that session's state instead of starting fresh.
  - `Auto git init` toggle (default off).
  - `Auto git commit` toggle (default on, matches PWA default).

### Transport

- `listFilters()`, `createFilter(pattern, action, value, enabled)`,
  `updateFilter(id, pattern, action, value, enabled)` (nullable
  args for partial updates), `deleteFilter(id)`.
- `startSession` gains `name`, `backend`, `resumeId`,
  `autoGitInit`, `autoGitCommit` optional parameters. DTO
  extended to match PWA payload.

## [0.26.0] — 2026-04-21 (Sprint V — ConfigFieldsPanel + 12 PWA Settings sections)

Ports the biggest PWA-parity gap flagged in the Settings audit:
mobile was missing ~25 config sections / ~70 editable fields that
the PWA exposes.

### Added

- **Generic `ConfigFieldsPanel` renderer.** One composable that
  reads a `List<ConfigField>` schema (Toggle / NumberField /
  TextField / Select / InterfaceSelect / LlmSelect), fetches
  `/api/config`, renders per-field native widgets, and writes
  a merged document back via `PUT /api/config` on Save. Deep-
  merges dotted keys (e.g. `session.log_level`) so siblings at
  any nesting depth are preserved. Empty-preserving password
  fields protect server-side secrets from accidental nuke.
- **`ConfigFieldSchemas.kt`** — schemas for **12 PWA sections**
  byte-for-byte ported from `app.js` COMMS_CONFIG_FIELDS,
  LLM_CONFIG_FIELDS, GENERAL_CONFIG_FIELDS:
  - **Comms:** CommsAuth, WebServer, McpServer
  - **LLM:** Memory (17 fields), LlmRtk (7 fields)
  - **General:** Datawatch, AutoUpdate, Session (18 fields),
    Rtk, Pipelines, Autonomous (7 fields), Plugins,
    Orchestrator, Whisper
- Wired into Settings tabs — Comms tab gets three new cards,
  LLM tab gets two, General tab gets nine. Adding a field
  server-side now means adding one line to
  `ConfigFieldSchemas.kt`; no new composable required.

### Superseded

- `BehaviourPreferencesCard` — replaced by
  `ConfigFieldsPanel(Session)` which uses the correct PWA keys
  (`session.max_sessions` / `session.tail_lines` /
  `server.recent_session_minutes` instead of the invented
  `recent_window_minutes` / `max_concurrent` / `scrollback_lines`
  that weren't being honoured server-side). The old card remains
  in-tree as dead code until a cleanup pass.

### Skipped (non-field sections)

- `proxy` Proxy Resilience — free-form server-config panel; the
  PWA renders it via a custom `proxySettings` endpoint with
  bespoke logic. Needs its own transport + card.
- `gc_projectprofiles` / `gc_clusterprofiles` — CRUD lists,
  not field-based. Follow-up sprint.
- `gc_notifs` — browser notification permission request; Android
  uses system settings.
- `detection` / `filters` — rule lists with regex / action /
  value. Follow-up sprint.

## [0.25.0] — 2026-04-21 (Sprint U — terminal extras + kill-orphans + memory test + Auto scaffolding)

### Added

- **Terminal scroll-mode toggle.** Two new buttons on the terminal
  toolbar: enter tmux copy-mode (`⇈`) sends
  `command:tmux-copy-mode <sid>` over WS; exit (`⇊`) sends
  `sendkey <sid>: Escape`. Matches PWA v2.3.2 scroll-mode flow.
- **Terminal auto-fit-to-width** (`⇅`). Shrinks xterm font size
  iteratively until the viewport fits the container horizontally,
  mirroring PWA `termFitToWidth` (app.js line 1890). Useful when
  `setMinSize(120, 40)` forces a wide terminal.
- **Terminal 5 s watchdog.** If no pane_capture event arrives
  within 5 seconds of a session open, the `resetPaneCaptureSeen`
  marker is cleared and the WebView cleared so whichever frame
  arrives next is treated as a fresh first-frame. Logs under
  `DwTerm` tag.
- **Kill-orphans card** under Settings → Monitor. Red "Kill
  orphans" button opens a confirm dialog, POSTs
  `/api/stats/kill-orphans`, reports `killed: N`.
- **Memory test button** on the Memory card. Calls
  `GET /api/memory/test`; green "Memory connection OK." or red
  error message inline.
- **Android Auto screen scaffolding.** Replaced the pre-MVP
  single-screen placeholder with a three-screen nav graph:
  AutoSummaryScreen → WaitingSessionsScreen → SessionReplyScreen.
  Templates wired; data is still placeholder pending a Sprint T
  ServiceLocator migration (auto module can't see composeApp's
  DI today — detailed in `docs/plans/2026-04-21-auto-audit.md`).

### Transport

- `listRemoteServerHealth()`, `killOrphans()`, `memoryTest()`.
- `WsOutbound.sendCommand(sessionId, text)` — generic `command`
  WS frame sender (used by the scroll-mode toolbar and the ESC
  exit).

### Deferred

- **Android Auto live session counts** — requires DI unification.
- **Scroll-mode PageUp / PageDown chip bar** — current sprint has
  toggle + ESC but not the on-screen navigation chips PWA shows.

## [0.24.0] — 2026-04-21 (Sprint S — terminal functional parity: resize_term + throttle + freeze)

Closes the biggest terminal-emulation gap flagged in the
2026-04-21 terminal audit: mobile never told the server its xterm
cols/rows, so claude-code (120-col TUI) rendered broken on phone
widths. Five behavioural fixes this release, matching PWA
accumulated terminal corrections from v0.13.0 through v4.0.7.

### Added

- **Outbound WS channel.** New `WsOutbound` object with
  `sendResizeTerm(sessionId, cols, rows)` and
  `sendCommand(sessionId, text)`. `WebSocketTransport.events()`
  now launches a per-session writer that relays queued frames
  onto the live socket. Previously WS was read-only.
- **resize_term WS frame** on every xterm fit-settle.
  `host.html` safeFit() fires `DwBridge.onResize(cols, rows)` when
  dimensions change; the JS bridge hands off to Kotlin, which
  emits `{type:"resize_term", data:{session_id, cols, rows}}`.
  Server resizes the tmux pane and the next pane_capture arrives
  at the new width.
- **configCols / configRows enforcement.** `TerminalController.
  setMinSize(cols, rows)`; `SessionDetailScreen` reads
  `session.backend` and calls `setMinSize(120, 40)` when it's
  claude-code (per parent v0.14.1). xterm resizes to at least
  that dimension; the container scrolls horizontally when phone
  width is narrower. TUIs now render at their intended widths.
- **30 fps pane_capture throttle.** `dwPaneCapture` in host.html
  skips frames arriving < 33 ms apart — mirrors PWA app.js line
  328 protection against xterm buffer overload when the server
  drains aggressively.
- **Skip DATAWATCH_COMPLETE transitional frames.** host.html
  checks each line for the marker; skipping prevents the
  shell-prompt flash between LLM exit and session-state update.
- **Freeze on terminal state.** `TerminalController.setFrozen`
  called from SessionDetailScreen when session transitions to
  complete/killed/failed. Further pane_captures are ignored so
  the final screenshot persists.

### Docs

- New `docs/plans/2026-04-21-terminal-audit.md` — catalogue of
  22 parent datawatch commits shaping terminal behaviour,
  behavioural lessons from each, side-by-side PWA-vs-mobile
  capability table, and breakdown of remaining gaps (scroll-mode
  + watchdog + auto fit-to-width + send_input WS).
- New `docs/plans/2026-04-21-auto-audit.md` — current Auto module
  state (Sprint 4 placeholder), Play-compliance constraints per
  ADR-0031, minimum-viable scope for testability.
- Expanded `docs/plans/2026-04-21-pwa-audit-sprint.md` (Sprint K
  commit) with full Settings surface catalog — ~25 sections /
  ~70 config fields — and the ConfigFieldsPanel renderer
  strategy that avoids hand-coding each one.

### Remaining terminal gaps (tracked, not shipped this sprint)

- Scroll-mode (tmux copy-mode + PageUp/Down/ESC bar)
- 5-second no-pane_capture watchdog with auto re-subscribe
- Auto fit-to-width (shrink font until no horizontal scroll)
- send_input WS (composer's REST reply is functionally adequate)

## [0.23.0] — 2026-04-21 (Sprint K — terminal palette aligned byte-for-byte to PWA)

### Changed

- **Terminal palette now exactly matches the PWA** (`app.js` line 2010+).
  Background `#0f1117` (charcoal, was pure black), foreground `#e2e8f0`,
  cursor `#a855f7` with accent `#0f1117`, selection
  `rgba(168,85,247,0.3)`. Full 16-colour ANSI palette replaced to
  PWA's tailwind-based mapping (ef4444 red, 10b981 green,
  3b82f6 blue, a855f7 magenta, …). WebView background colour in
  `TerminalView.kt` also flipped to `#0F1117` so the flash during
  WebView reflow doesn't strobe black.

### Docs

- New sprint plan `docs/plans/2026-04-21-pwa-audit-sprint.md`
  enumerating the full PWA API surface, cross-referencing mobile
  coverage, listing the 8 remaining REST gaps + 1 WS gap
  (`resize_term`), and breaking remaining work into sprints K–R
  targeting v1.0.0-rc1. Written at user's directive: "document and
  make a plan so you don't forget."

## [0.22.1] — 2026-04-21 (daemon /api/update wiring + version-drift fix)

### Fixed

- **About card was stuck at v0.12.0** because `Version.kt` in the
  shared module was never bumped across ten release commits since
  v0.12. Gradle's versionCode was advancing correctly (read from
  `gradle.properties`) but the UI read `Version.VERSION` directly.
  Synced both files to v0.22.1 / code 28. Memory rule added so
  future commits bump both.

### Added

- **Daemon update card under Settings → Monitor.** Calls
  `POST /api/update`. Button says "Check for update"; server
  responds with `up_to_date` (green banner with version) or
  installs + restarts automatically. Endpoint is undocumented in
  parent openapi.yaml but shipped — PWA `runUpdate` at app.js
  line 4149 uses the same wire path. Closes the last ❌ row on
  parity-plan.

### Audit

- Closed upstream dmz006/datawatch#17 (update-daemon) and #18
  (add-channel). Both were filed in error: #17's endpoint already
  exists (stale-spec pattern, same as #14/#15), #18's "missing"
  endpoint is intentional upstream design — adds route through
  `PUT /api/config`, which mobile already does via
  BackendConfigDialog. No open mobile-filed issues remain against
  parent.

## [0.22.0] — 2026-04-21 (Sprint J — memory export + input/output mode + ESC/Ctrl-b)

### Added

- **Memory export via SAF.** Settings → General → Memory gets an
  "Export…" button. It fetches `GET /api/memory/export` (raw
  bytes with bearer auth, which ACTION_VIEW couldn't do) and
  launches `CreateDocument` so the user picks the destination;
  bytes are written to the chosen content-URI with a
  timestamped filename.
- **Input mode / Output mode dropdowns** on the Behaviour
  Preferences card. `input_mode` options: tmux / channel /
  none. `output_mode` options: tmux / channel / both / none.
  Blank preserves the server's existing value on save.
- **ESC and Ctrl-b chips in the Quick Commands sheet.** Rather
  than introduce a separate WS command channel for the list
  surface, mobile sends the raw ASCII control bytes (0x1B for
  ESC, 0x02 for Ctrl-b) via the existing `/api/sessions/reply`
  path. TUIs that interpret replies through the tmux pane pick
  them up the same way the PWA's `sendkey` command does.

### Transport

- `memoryExport()` → `Result<ByteArray>` for the SAF download.

### Parity-plan

- 3 more 🚧 rows flip to ✅: memory export, input mode, output
  mode. ESC/Ctrl-b wasn't tracked as its own row but was noted
  as deferred in the quick-commands docstring.

## [0.21.0] — 2026-04-20 (Sprint I — per-backend config editor + final parity close-out)

Closes the remaining structured-write gap on parity-plan.

### Added

- **Configure-backend dialog** on Settings → LLM. Tap "Configure…"
  next to any listed LLM backend to open a structured editor with
  three inputs: model / base_url / api_key. Other fields on the
  backend block are preserved when the dialog is saved (the full
  config document is round-tripped). API key field is empty-
  preserving — leaving it blank keeps the existing stored secret
  rather than nuking it, so users can change just the model
  without re-typing credentials.
- Per-backend config edit completes the round-trip for the v0.13.1
  model picker: mobile now fully owns the "swap Ollama model"
  flow by writing `backends.ollama.model` via `PUT /api/config`.

### Parity-plan

- 18 rows flipped from ❌ / 🚧 to ✅ in this final sweep, covering
  everything shipped across Sprints A–I.
- Four rows remain 🚧: pagination / "load more" (partitioning
  covers the UX); memory export (needs SAF); input/output mode
  fields in preferences (backend-specific; same machinery ready);
  eBPF viewer (ADR-0019 deferred).
- One row remains ❌: `/api/update` — upstream doesn't expose the
  endpoint; will file the issue before 1.0.0.

## [0.20.0] — 2026-04-20 (Sprint H — Behaviour preferences + federation peers)

### Added

- **Behaviour preferences card under Settings → General.** Reads
  `recent_window_minutes`, `max_concurrent`, and
  `scrollback_lines` from `/api/config` and exposes them as integer
  inputs. Save merges the edited values back into the full config
  object and writes via `PUT /api/config`, preserving every other
  field the parent returned (per ADR-0019 — we never touch raw
  YAML, only specific structured fields we understand).
- **Federation peers card under Settings → Comms.** Read-only
  list of remote datawatch servers the active server is
  federated with, from `GET /api/servers`. Shows name / base URL
  / enabled flag so users can verify the parent's federation
  config without opening the web UI.

### Transport

- `listRemoteServers()` → raw `List<JsonObject>` so the UI can
  pick fields as the schema evolves.

## [0.19.0] — 2026-04-20 (Sprint G — Alerts schedule + foreground suppression)

### Added

- **"Schedule reply…" action on Alert rows.** Each row now has an
  inline button that opens the existing ScheduleDialog, seeded
  with the session's last prompt text so users can answer a
  blocking question on a cron rather than dropping everything.
- **Foreground-session suppression for input-needed
  notifications.** A new `ForegroundSessionTracker` records which
  session is visible via `DisposableEffect` on the detail screen
  and watches process lifecycle. `NotificationPoster` consults it
  before posting an `InputNeeded` wake notification — matches the
  PWA's "don't ring the bell for the tab you're already on".
- Alert rows now prefer `session.name` over `id` for the header
  title, matching the Sessions list and detail screen.

### Dependencies

- Adds `androidx.lifecycle:lifecycle-process:2.8.4` for
  `ProcessLifecycleOwner`.

## [0.18.0] — 2026-04-20 (Sprint F — Channels/Comms Settings)

### Added

- **ChannelsCard under Settings → Comms.** Lists configured
  messaging channels from `GET /api/channels`. Each row has a
  per-channel enable/disable switch (PATCH /api/channels/{id})
  and a "Test" button that opens a prompt dialog for a test
  message body and fires `POST /api/channel/send`. Empty-state
  hint steers users toward the server-side config UI for adds
  since the parent still 501s on `POST /api/channels`.

### Transport

- `listChannels()`, `setChannelEnabled(id, enabled)`,
  `sendChannelTest(channelId, text)`.

## [0.17.0] — 2026-04-20 (Sprint E — Memory tab)

### Added

- **Episodic memory browser under Settings → General.** Stats grid
  (total / manual / session / learnings / chunks / DB size),
  searchable + deletable list of stored memories. Matches the PWA
  memory panel. Shows a helpful fallback when the server's
  memory subsystem is disabled.
- Transport: `memoryStats()`, `memoryList(limit, role, sinceIso)`,
  `memorySearch(q)`, `memoryDelete(id)`.

### Deferred

- **Memory export** — parent exposes `GET /api/memory/export` as a
  downloadable blob; mobile needs a SAF intent-launcher wiring,
  tracked for a follow-up sprint.
- **Memory remember** (create from UI) — PWA surfaces this inside
  session chat, not in the standalone panel; follows when the
  session-detail memory wiring lands.

## [0.16.0] — 2026-04-20 (Sprint D — Ops: logs viewer + interfaces + restart)

### Added

- **Daemon log viewer under Settings → Monitor.** Paginated
  `GET /api/logs?lines=50&offset=…` with 10 s auto-refresh,
  PWA-matching colour coding (error/warn/info), and newer/older
  buttons. Matches the PWA's `loadDaemonLog` behaviour.
- **Network interfaces card under Settings → Monitor.** Read-only
  list from `GET /api/interfaces` — each row shows name / IPs /
  MAC. Renders in under a second after opening the tab.
- **Restart daemon card.** Red "Restart daemon" button with confirm
  dialog. Calls `POST /api/restart`; the server re-execs and
  active sessions briefly drop their WS connection (noted in the
  card body). Surfaces a banner on success / failure.

### Transport

- New `TransportClient.fetchLogs(lines, offset, level)` returning
  `LogsView(lines, total)`.
- New `TransportClient.restartDaemon()` and
  `TransportClient.listInterfaces()` (raw `List<JsonObject>` so UI
  can pick fields without locking the schema).

## [0.15.0] — 2026-04-20 (Sprint C — /api/profiles picker + voice-to-new)

### Added

- **Profile picker on New Session form.** Pulls `GET /api/profiles`
  for the selected server and renders a dropdown above the Working
  Directory field. "Default (no profile)" is the top option; each
  profile shows name + backend. Selection is passed as `profile`
  on `POST /api/sessions/start`, matching the PWA's populateProfile
  dropdown.
- **Voice-to-new-session.** When voice transcription in an existing
  session's composer starts with `"new:"` or `"new "` (case-
  insensitive), the client now posts `/api/sessions/start` with the
  remaining text instead of inserting it into the composer. Toast
  confirms the new session id; on failure, the transcript falls
  back to the composer so the user doesn't lose the dictation.

### Transport

- New `TransportClient.listProfiles()` returning the raw
  `Map<String, JsonObject>` — callers extract the `backend` field
  for display and pass the profile name to startSession.
- New `TransportClient.writeConfig(raw)` — PUT /api/config with the
  full document. Unused by UI yet; lands for the per-backend
  config editor in a follow-up sprint.
- `startSession` gains an optional `profileName` parameter.

## [0.14.3] — 2026-04-20 (Sprint B — session detail + terminal polish)

### Fixed

- **Terminal no longer paints "datawatch terminal ready" banner**
  on session open. The bright-white banner was noise that mangled
  pane captures on session switch — the xterm container now stays
  empty until real output arrives (matching PWA behaviour).
- **"Load backlog" button re-enables on failure.** Previously the
  button flipped permanently to disabled after the first click,
  even if the call returned an error, leaving users with no retry
  path on a transient network blip. Now it re-enables when the
  fetch fails and is also reset on session switch.

### Added

- **Channel / messaging-backend badge in session-detail header.**
  New middle-subtitle item next to the LLM badge shows the server's
  active messaging channel (signal / telegram / …) fetched from
  `/api/info` once on session open. Matches the PWA header.

## [0.14.2] — 2026-04-20 (Sprint A — Sessions list auto-refresh + sort + last-response viewer)

### Fixed

- **Reachability dot is now an unmistakable traffic light.** Theme
  primary/error swapped for explicit green (#22C55E) / red (#EF4444)
  / amber (#F59E0B), and the dot is 12 dp (was 8 dp). Users asked
  for green/red so "is the server up" reads at a glance.

### Changed

- **Sessions tab auto-refreshes every 5 s** (mirrors the StatsVM
  cadence). The explicit Refresh IconButton is removed; a small
  spinner appears inline in the app bar while a refresh is in
  flight so users can tell the poll is live.

### Added

- **Sort order dropdown in Sessions toolbar.** Three orders matching
  the PWA: Recent activity (default), Started, Name. State-bucket
  ordering (waiting → running → …) still wins at the top; the
  user's pick governs within-bucket ordering.
- **Last-response viewer.** Tapping the 📄 icon on a session row
  opens a ModalBottomSheet with the full `last_response` payload
  (scrollable, monospace, PWA-matching).

### Deferred

- Drag-to-reorder sessions — non-trivial in Compose without an
  external library; tracked for a later sprint. The Name / Started
  / Recent-activity sort covers most of the "I want different
  ordering" cases meanwhile.

## [0.14.1] — 2026-04-20 (Session-detail header + composer PWA parity)

Second round of PWA parity fixes after a side-by-side walkthrough
with the user. Note: v0.14.0 landed on the `dev` build but the
user's phone was running `publicTrack` (the normal debug build),
so none of the v0.14.0 changes were visible yet. v0.14.1 ships on
publicTrack — the user-visible variant.

### Fixed

- **State pill labels now match PWA wire format exactly.** Previously
  mobile showed "WAITING"/"DONE"/"FAILED"; now `waiting_input`,
  `complete`, `failed`, `rate_limited` (lowercase, with underscores).
  Users can describe a session state to someone on the PWA and both
  are looking at the same token.

### Added

- **Session detail header meta row.** Below the title now shows
  `<session-id> · <BACKEND> · <hostname>`, matching the PWA detail
  header. Title itself now prefers user-assigned `name` over the
  raw task prompt.
- **Stop + Timeline promoted to top-level action icons** in the
  session-detail app bar. Previously both lived in the overflow
  menu — matching PWA's always-visible header buttons.
- **tmux | channel tabs below the app bar.** Replaces the v0.14.0
  Chat/Terminal icon toggle with a proper `TabRow` so the active
  output surface is always visible. Selection still persists in
  SharedPreferences across restarts.
- **Composer quick-reply chips when session is `waiting_input`.**
  Row of AssistChips above the text field: approve / reject /
  continue / skip / quit. Matches the PWA's in-composer quick
  commands. Fires `vm.sendQuickReply` without touching the typed
  draft; placeholder text changes to "Reply (input required)…"
  so the state is obvious even before expanding the chips.

## [0.14.0] — 2026-04-20 (Sessions tab matches PWA)

Ripped the state-quick-filters the user explicitly called out ("the
current quick filters on the sessions tab should not be there") and
rebuilt the Sessions tab to match the PWA monitor's shape in
`internal/server/web/app.js` row-for-row.

### Changed

- **Sessions toolbar now matches the PWA.** The old `FilterChipRow`
  (All / Running / Waiting / Completed / Error) is gone. Replaced
  with the PWA's three-row toolbar: a free-text filter input
  (search by name / task / id / backend), per-backend filter
  chips with inline counts (only shown when the pool spans >1
  backend), and a Show/Hide history toggle with the done-session
  count. Default pool: active + recently-completed
  (< 5 min); hit Show History to see everything. Row ordering
  mirrors the PWA — waiting → running → rate-limited → new →
  terminal, then most-recent activity within each bucket.
- **Session rows render name over task in the body, matching PWA.**
  When the user has renamed a session, the row shows the assigned
  name; falls back to the original task prompt, then to `(no task)`.
  Row header still shows the short id + state + backend chip.

### Added

- **Quick commands sheet on waiting_input rows.** New ▶ "Commands"
  button next to Stop opens a PWA-style bottom sheet with three
  stacks: System chips (yes / no / continue / skip / /exit), Saved
  commands lazily pulled from `GET /api/commands`, and a Custom
  text input. Taps fire `POST /api/sessions/reply` without
  navigating into the session. ESC / Ctrl-b keys deferred — they
  need the WS `command` channel which the Sessions tab doesn't
  subscribe to.
- **Multi-line `prompt_context` preview under waiting rows.** When
  the server emits `prompt_context`, the row now renders the last
  4 lines (100-char clamp per line), matching the PWA's trust-
  prompt rendering so users see both the imperative ("press 1") and
  the action ("Enter to confirm"). Falls back to single-line
  `last_prompt` on older servers.
- **"View last response" icon inline with the task body.** Shows
  only when `session.last_response` is non-empty. Click handler is
  a TODO placeholder — the response-viewer modal lands in the next
  batch (tracked with the existing #112 follow-up).
- **Per-session `backend` / `name` / `promptContext` / `lastResponse`
  persisted to DB via migration `3.sqm`** so the PWA-matching row
  chrome renders on cold-open from cache, not just after the first
  REST refresh.

## [0.13.1] — 2026-04-20 (follow-up — correct stale spec paths + wire shipped endpoints)

Discovered that three parity-plan rows previously tagged
upstream-blocked were actually server-ready — parent openapi.yaml
was just stale vs the shipped server (tracked
[dmz006/datawatch#16](https://github.com/dmz006/datawatch/issues/16)).
Fixed the mobile transport to match what the server actually
serves, then wired the UIs the gaps were blocking.

### Fixed

- **Schedules endpoint was wrong path.** Mobile was calling
  `/api/schedule` (singular, per stale spec); the shipped server
  exposes `/api/schedules` (plural). Corrected list + create + delete
  + unit tests.

### Added

- **Per-session schedules strip in session detail.** Mirrors the
  PWA's `loadSessionSchedules(sessionId)` — renders a small strip
  above the composer listing every pending schedule attached to the
  current session, with a ✕ cancel per row. Calls
  `GET /api/schedules?session_id=<id>&state=pending`; hides when the
  session has no pending schedules or the server predates the
  filter. Schedule-reply dialog now passes `sessionId` so new
  schedules show up in the strip immediately.

- **Session timeline overlay prefers server feed.** The existing
  bottom-sheet now tries
  `GET /api/sessions/timeline?id=<sessionId>` first and renders the
  pipe-delimited `{lines: [...]}` response with PWA-matching
  event-colour rules (state → tertiary, input → primary, rate →
  secondary). Falls back to the local WS-event filter when the
  endpoint isn't reachable, with a subtitle flag so users can tell
  which source they're looking at.

- **Model picker in New Session form.** When the picked backend is
  `ollama` or `openwebui`, a second dropdown enumerates installed
  models via `GET /api/<backend>/models`. Informational only today
  — parent's `POST /api/sessions/start` doesn't accept a model
  field (PWA sends `backend` + `profile` only), so the picker lets
  users see what's available. Actually changing the model from
  mobile will need a backend-config PUT, tracked for v0.14.

## [0.13.0] — 2026-04-20 (PWA parity sweep)

A focused sweep over the gaps the user called out after v0.12.0:
session-list rows now match the PWA monitor view, the session-detail
header gains the affordances PWA users expect, and the
new-session form picks up an LLM backend dropdown. Most upstream-
blocked rows on parity-plan are explicitly tagged so future-me
doesn't accidentally re-implement them client-side.

### Added

- **New Session: LLM backend picker.** The Start-session form now shows
  a Backend dropdown populated from `/api/backends` for the selected
  server. The currently-active backend is annotated `active` in the
  list; picking a different one calls `setActiveBackend` server-wide
  before posting `/api/sessions/start` (parent has no per-session
  backend param yet, so this is the closest mobile can get today). If
  the server doesn't expose `/api/backends`, the picker is hidden
  entirely. Mode + model-variant pickers stay parked on
  upstream-blocked rows in parity-plan (`/api/profiles`,
  `/api/ollama/models`, `/api/openwebui/models` not in parent
  openapi).

- **Session timeline bottom-sheet overlay.** A new "Timeline…" entry
  in the session-detail overflow opens a modal sheet that filters the
  cached event stream to non-Output entries (state changes, prompts,
  rate-limits, completions, errors) and renders them as a
  chronological strip — what-happened-when at a glance, without
  scrolling raw output. Composed entirely from
  `SessionEventRepository`'s WS-backed cache; will swap to a server
  feed when parent ships `/api/sessions/timeline`.

- **Chat-mode / Terminal-mode toggle in session detail.** A new icon in
  the top app bar (Chat ↔ Terminal) swaps between the existing xterm
  surface and a chat-style event list. Choice persists via
  `SharedPreferences("dw.session.detail.v1", "chat_mode")`. Chat mode
  appends a Yes / No / Stop **quick-reply** button row under the
  latest `PromptDetected` event so users can blast through approval
  prompts without typing — taps fire `vm.sendQuickReply(...)` which
  sends directly without touching the composer draft.

- **Terminal toolbar: Fit + Jump-to-bottom buttons.** Two new icons next
  to font ± / Backlog: a Fit button forces a manual `safeFit()` pass
  (helps after a pinch-zoom or rotation that didn't fire a resize
  callback), and a Jump-to-bottom button calls
  `term.scrollToBottom()` to snap back to the live tail after
  scrolling up to read backlog. JS-side bridges `dwFit` and
  `dwScrollToBottom` added to host.html.
- **pane_capture: regression test locking the first/redraw frame
  contract.** The end-to-end path (WS frame → EventMapper →
  SessionEvent.PaneCapture → TerminalView → `dwPaneCapture`) is
  audited; `EventMapperTest` now asserts isFirst flips correctly
  across reset boundaries so the next refactor can't silently break
  TUI rendering.

- **Schedule-from-composer in session detail.** A new clock icon next to
  Mic/Send opens the existing schedule dialog pre-seeded with the
  typed reply text — turns "draft a reply, then schedule it for
  later" into a single tap. Falls back to the live prompt → task
  summary → session id when the composer is empty (priority order
  matches the existing overflow-menu Schedule action).

- **Session detail screen gains PWA header affordances and live banners.**
  Tapping the session title in the top app bar opens an inline rename
  dialog (same wire as the Sessions-list overflow), and tapping the
  state pill opens the state-override menu (used to require an
  overflow-menu tap-then-pick). When the owning profile's transport is
  unreachable, a top-of-terminal connection banner now appears (the
  PWA shows an equivalent strip when WS / REST drops). When the
  session is `waiting_input`, an amber input-required banner sits
  directly above the terminal showing the most-recent prompt text
  (live `PromptDetected` event preferred, falling back to
  `Session.lastPrompt`) so triage doesn't require backlog scrolling.

- **Session list rows now match the PWA monitor view.** Each card surfaces
  the active LLM backend chip alongside the state pill (sourced from
  `/api/info` per server), the hostname + relative time meta line, an
  inline "Stop" button on running/waiting rows and "Restart" on
  terminal rows so the most-common actions skip the overflow menu, and
  — for `waiting_input` rows — a two-line quote preview of the
  `last_prompt` so you can triage from the list without opening the
  detail screen. Persistence: added `session.last_prompt` column via
  migration `2.sqm` so the preview survives cold starts.

### Fixed

- **Theme was defaulting to Material You dynamic colors** (`DatawatchTheme`
  had `dynamicColor = true` by default). On Android 12+ that overrode the
  datawatch purple palette with the user's wallpaper and the whole app
  stopped looking like the parent PWA. Flipped default to `false`. Also
  expanded the color scheme + added a `LocalDatawatchColors`
  CompositionLocal exposing PWA-equivalent `bg/bg2/bg3/border/waiting`
  slots Material3 doesn't have.
- **Sticky "server unreachable" on Channels / Schedules / Saved commands
  / Daemon config cards.** Each VM's `init { refresh() }` was a brittle
  one-shot that stuck on unreachable forever if the cold-boot probe
  happened before the network was up. Now each VM observes the active
  profile's `TransportClient.isReachable` flow and re-fires `refresh()`
  every time reachability flips to true, so the banner self-clears.
- **Splash version text was invisible.** Was rendering at `#6D28D9` (dark
  purple) against the `#0F1117` black splash. Bumped to `#A855F7` at 0.75
  alpha and added an "AI Session Monitor" subtitle between the name and
  version to match the PWA splash hierarchy.
- **Terminal rendering didn't match the PWA.** The PWA uses
  `pane_capture` frames (authoritative tmux pane snapshot) as its only
  display source; mobile was routing `raw_output` straight to xterm, so
  TUIs like Claude Code that cursor-position in a 120-col pane ended up
  as scattered-garbage on a 39-col mobile viewport. Added
  `SessionEvent.PaneCapture`, `WsFrame.type == "pane_capture"` mapper,
  and a host-side `window.dwPaneCapture(lines, isFirst)` that replicates
  the PWA's `term.reset() + write` (first) / `ESC[2J ESC[3J ESC[H +
  write` (subsequent) pattern. `raw_output` is suppressed when
  `pane_capture` is present; legacy fallback kicks in against older
  servers.

### Added

- **PWA visual primitives** (`ui/theme/PwaComponents.kt`):
  - `PwaStatePill(state)` — 10sp uppercase state badge with
    tinted-0.15-alpha background, matching `.state-badge-*` in the PWA.
  - `Modifier.pwaStateEdge(state)` — 4dp state-coloured left stripe on
    session cards.
  - `Modifier.pwaCard()` — 12dp-radius `bg2` surface with 1dp border,
    matching `.session-card` / `.settings-section`.
  - `PwaSectionTitle(title)` — 11sp uppercase 0.8sp-letter-spacing
    section heading.
- **Sessions tab** — rows rendered as proper cards with the state edge
  + inline state pill, replacing the old AssistChip + full-width
  HorizontalDivider layout.
- **Settings cards** — all sections (`Servers`, `Security`, `Schedules`,
  `Saved commands`, `Daemon config`, `Comms`, `About`) now wrap in
  `pwaCard()` with a proper `PwaSectionTitle` header. Also added
  explicit refresh buttons to the 4 data-bound cards so manual retry
  is always available.

## [0.12.0] — 2026-04-20

Schedules + file picker + saved commands + config viewer + terminal
backlog pager. 6 phases across 5 UI surfaces, all against endpoints
confirmed in parent v4.0.3+. Plan:
[docs/plans/2026-04-20-v0.12-schedules-files-config.md](docs/plans/2026-04-20-v0.12-schedules-files-config.md).

Paired with parent `dmz006/datawatch` v4.0.3+ which shipped nine new
endpoints (#5–#13 closed) — all v0.11 `(client)`-gated flows (session
delete/rename/restart, `/api/cert`, `/api/backends/active`) now work
against a real server without the NotFound grey-out fallback kicking
in.

### Added — v0.12 sprint

- **Transport layer** (shipped `93351fa`) — 8 new
  `TransportClient` methods:
  `listSchedules` / `createSchedule` / `deleteSchedule` (`/api/schedule`),
  `browseFiles` (`/api/files?path=`),
  `listCommands` / `saveCommand` / `deleteCommand` (`/api/commands`),
  `fetchConfig` (`/api/config`). 4 new domain types
  (`Schedule`, `FileEntry`/`FileList`, `SavedCommand`, `ConfigView`).
  9 new MockWebServer tests.
- **Schedules UI** (shipped `cdf01ea`) — new Settings → Schedules card
  lists scheduled commands with per-row task/cron/enabled chip +
  delete, plus a **+ New schedule** dialog (task multi-line, cron
  free-form with inline hint, enabled toggle). Session detail
  overflow menu gets a **Schedule reply…** action that pre-seeds the
  dialog with the current prompt's text.
- **Server-side file picker** (Phase 3) — reusable
  `FilePickerDialog` under `ui/files/`. Breadcrumb path, `..` to
  go up, dirs-first sort, "Pick this folder" for dir-mode, tap-a-file
  for file-mode. Modes: `FolderOnly` / `FileOnly` / `FolderOrFile`.
  Wired into `NewSessionScreen` as the new optional
  **Working directory** field (lands on `/api/sessions/start` as
  `cwd` — server ignores the field on pre-v4.0.3 builds).
- **Saved command library** (Phase 4, closes BL20) — Settings →
  Saved commands card lists name + command snippets from
  `/api/commands`, with tap-to-expand for long commands and delete
  icon. **+** opens a save dialog. `NewSessionScreen` gains a
  "From library ▾" dropdown next to the Task heading that inlines
  the picked command into the field; hidden entirely when no
  commands are saved so first-time use isn't cluttered.
- **Daemon config viewer (read-only)** (Phase 5) — Settings → Daemon
  config card shows `GET /api/config` as collapsible top-level rows
  that expand into pretty-printed JSON. Belt-and-braces client-side
  secondary mask catches common secret field names (`*token*`,
  `*secret*`, `*key*`, `password`, `passphrase`, etc.) in case the
  parent's mask misses one. Write is explicitly deferred to v0.13 per
  ADR-0019 (structured form).
- **Terminal backlog pager** (Phase 6) — third button in the terminal
  toolbar (history icon) fetches up to 1000 lines of pre-subscription
  PTY output via `GET /api/output` and prepends them into xterm.
  Host bridge: `window.dwPrependBacklog(s)`; controller method
  `TerminalController.prepend(text)`. Button disables after first use
  per session to prevent double-prepending.

### Fixed

- **B7 — CI `ktlintCheck` parse failure on `FederationDtos.kt`**
  (shipped `c4f003f`). Every CI run since 2026-04-20 was red.
  Root cause: `ktlint-plugin` 12.1.1 bundled ktlint 1.1, pre-Kotlin-2.0.
  Bumped to 12.3.0 (ktlint 1.5.0). Added `.editorconfig` and
  `config/detekt/detekt.yml` with Compose-friendly rule exemptions
  (`@Composable` escapes `function-naming`; `MagicNumber` excludes
  UI packages). Rewrote 3 KDocs that contained parser-tripping
  `{ ... }` / `<name>` / `[Foo...]` tokens. CI green again.

### Changed

- **Parent `dmz006/datawatch` v4.0.3 landed** the 9 endpoints filed
  (#5–#13 closed). Docs/parity-plan + parity-status rows for the v0.11
  `(client)`-gated features flipped to plain ✅ with "shipped in
  parent v4.0.3" annotation. The v0.11 client-side
  `TransportError.NotFound` grey-out fallbacks auto-unstick against
  any v4.0.3+ server; no mobile code change needed.

## [0.11.0] — 2026-04-20

Session power-user parity sprint — closes the 8 items in
`docs/parity-plan.md` §6 v0.11 plus two opportunistic pickups from the
2026-04-20 PWA re-audit. Plan:
[docs/plans/2026-04-20-v0.11-session-power-user.md](docs/plans/2026-04-20-v0.11-session-power-user.md).

### Added

- **`:shared` transport — 10 new methods** (`renameSession`,
  `restartSession`, `deleteSession` + bulk `deleteSessions`, `fetchCert`,
  `setActiveBackend`, `listAlerts`, `markAlertRead`, `fetchInfo`,
  `fetchOutput`). New domain types `Alert` / `AlertSeverity` /
  `ServerInfo`. 14 new MockWebServer tests.
- **Session row overflow menu** — Rename, Restart, Delete dialogs on
  every row in the Sessions tab.
- **Bulk multi-select** — long-press a row to enter selection mode;
  TopAppBar flips to "N selected" with Cancel + bulk-Delete.
- **Connection-status dot** in Sessions TopAppBar — green/grey/red
  against `TransportClient.isReachable`. Tap for a bottom sheet with
  last-probe time and Retry.
- **About card "Connected to" row** — hostname + daemon version from
  `GET /api/info`.
- **Settings → Servers overflow menu** — per-server "Download CA cert"
  (saves PEM under `Download/datawatch/` and fires the Android
  install-cert intent) + "Delete server".
- **New Session screen** — floating "+" button on Sessions tab →
  form with task text + server picker → `startSession`.
- **Active-backend radio picker** in the Channels tab —
  `POST /api/backends/active`.
- **Swipe-left alerts dismiss** — mutes the underlying session so the
  projection drops the row and the badge re-counts.
- **Terminal toolbar** above the xterm view — inline search bar
  (prev/next/close) plus a clipboard button. Uses vendored
  `xterm-addon-search@0.13.0` (12.2 KB) in
  `composeApp/src/androidMain/assets/xterm/`.

### Changed

- `ServiceLocator.transportFor(profile)` now caches a
  `TransportClient` per profile (keyed on `baseUrl + trustAnchor +
  bearerRef`) so downstream observers of `isReachable` see a stable
  Flow across refreshes.

### Parent-confirmation gates

- `POST /api/sessions/delete`, `GET /api/cert`, and
  `POST /api/backends/active` are not in the parent's v3.0.0
  `openapi.yaml`. Client transport calls them anyway; each returns
  `TransportError.NotFound` on pre-parity servers, at which point the
  Android UI greys the affected control and surfaces a toast / banner
  pointing at the upstream gap. Users can still run every other flow.

### Known follow-ups for v0.12+

- Wire `AlertsScreen` directly to `GET /api/alerts` + `POST /api/alerts`
  once the parent's Alert wire shape is fully locked (transport methods
  already exist).
- Compose UI test infrastructure in `composeApp` (no `androidTest`
  sourceset today).
- Saved command library — `GET/POST/DELETE /api/commands` — BL20.
- Signal device linking — `/api/link/*` with SSE QR stream — BL21.

## [0.10.1] — 2026-04-19

Previously tagged **v1.0.1**; renumbered 2026-04-20 — the 1.0 label
is reserved for the full-PWA-parity milestone.

### Fixed
- **Session detail now uses xterm.js as its primary surface.** v0.10.0
  rendered events as a chat-style spine and tucked the terminal behind
  an icon; the result looked like scrolling text with no ANSI / cursor /
  real scrollback. This release swaps the default: the terminal fills
  the body, prompt + rate-limit notices become an `InlineNotices`
  banner above the composer, the reply composer stays below. Matches
  the PWA's session UX.

### Added
- `docs/parity-plan.md` — complete audit of PWA → mobile gaps grouped
  by screen, with a v0.11 → v0.14 roadmap. Grounded against the parent
  repo's `internal/server/web/` and `docs/api/openapi.yaml` at the
  v3.0.0 tag.

## [0.10.0] — 2026-04-19

Previously tagged **v1.0.0** as "first production release";
renumbered 2026-04-20 — v1.0 is reserved for the milestone where
every row in `docs/parity-status.md` flips to ✅. v0.10.0 closes
Sprint 6 (every ADR-0042 scope item shipped) and pairs with parent
datawatch v3.0.0.

### What's in v0.10.0 — highlights

- **Live session management.** REST + WebSocket (`/ws?session=<id>`) with
  auto-reconnect + jittered exponential backoff. Session detail, chat-style
  event stream, reply composer, kill-with-confirm, state override.
- **xterm.js terminal** bottom sheet, ANSI-colour, 5000-line scrollback.
- **Multi-server.** In-app picker + edit server + 3-finger-swipe gesture
  (BL9) + all-servers mode via `/api/federation/sessions`.
- **Push notifications.** FCM primary + ntfy fallback; per-event-type
  channels; inline RemoteInput reply on input-needed prompts;
  `dwclient://session/<id>` deep link.
- **Voice reply.** In-composer mic button → `/api/voice/transcribe`
  (Whisper-backed, parent issue #2).
- **Stats, Alerts, Channels** tabs with bottom-nav badge counter.
- **Home-screen widget** (BL6) with running / waiting / total counts.
- **Wear OS dashboard + Tile** (BL4) with the same glance counters.
- **Android Auto** Messaging-template list screen (BL10 scaffolding).
- **Biometric unlock** (BL2) — opt-in Class-3 enforcement at app entry.
- **Encrypted storage.** SQLCipher-backed SQLDelight DB + Keystore-bound
  bearer-token vault. Schema migrations verified (1 → 2 backfill for
  session_event shipped in v0.3.0).

### What moves to v0.11+

- Biometric-bound DB passphrase (wraps Keystore key with biometric auth
  requirement)
- Bulk-delete completed sessions
- Per-channel enable/disable REST (depends on parent exposing channel state)
- xterm search + copy affordances, quick-tile voice launch wiring
- Wear Data Layer pairing flow (counts live through phone → watch bridge)

### Notes
- No app-store release in this commit — APK is a release-candidate
  artefact. Play submission is a separate manual step per ADR-0019.
- JDK 21 required for builds (see memory/feedback_build_jdk21.md).

## [0.9.0] — 2026-04-19

Sprint 5 — hardening + biometric unlock. Release-candidate milestone.

### Added
- **Biometric unlock (BL2, ADR-0042)** — optional, off by default. When
  enabled in Settings → Security, app entry prompts for Class-3 biometric
  (fingerprint/face) before AppRoot composes. Failure leaves the app
  locked (no bypass). Uses `androidx.biometric` 1.2.0-alpha05.
- **Security card** in Settings with an enable toggle; greyed out when
  no Class-3 biometric is enrolled.
- MainActivity migrates from `ComponentActivity` to `FragmentActivity`
  (required by BiometricPrompt).

### Known follow-ups for v0.10.0
- Biometric-bound DB passphrase (wrap `deriveDatabasePassphrase` in a
  Keystore key that requires biometric auth) — current build gates
  only the UI, not the underlying passphrase derivation.

## [0.5.0] — 2026-04-19

Sprint 4 — Wear OS + Android Auto surfaces.

### Added
- **Wear OS dashboard** — three-count tiles (running / waiting / total) in
  the watch app with a waiting accent, paired-server name footer, and a
  pre-pairing empty state.
- **Wear Tile (BL4, ADR-0042)** — ProtoLayout 1.2 glance tile mirroring the
  dashboard counts, registered via TileService intent filter.
- **Android Auto ListTemplate** — upgraded PreMvpPlaceholderScreen to a
  proper three-row list (running / waiting / total) with app-icon header;
  Play-compliant for the Messaging category since rows are static counts.
- Gradle deps: `androidx.wear.protolayout` (+ expression + material),
  `com.google.guava` for the Tile ListenableFuture contract.

### Known follow-ups for v0.6.0
- Wear Data Layer (play-services-wearable) pairing flow — counts on Tile
  + dashboard are placeholder zeros until this lands.
- Auto Tile (BL10) — Android Auto API doesn't have a direct "tile" concept;
  the driving-mode glance is covered by the ListTemplate Auto screen. Keeping
  BL10 open for dev-flavor passenger experiments.

## [0.4.0] — 2026-04-19

Sprint 3 — MVP milestone. Voice, all-servers, stats, channels, widget, MCP-SSE bones.

### Added
- **All-servers mode** backed by `/api/federation/sessions` (parent issue #3).
  Picker gets an "All servers" row; SessionsViewModel fans out in parallel,
  merges by most-recent-wins, collates per-server errors into a banner.
- **Live stats dashboard** at `/api/stats` — CPU/Memory/Disk/GPU bars with
  colour-coded thresholds, session counts, daemon uptime. Polls every 5 s.
  Replaces the stale Sprint-1-Phase-4 placeholder.
- **Channels tab** — live LLM backends from `/api/backends` with active
  chipped; static note pointing at server-side messaging config (Signal/
  Telegram/etc. — per-channel REST surface tracked for v0.5.0).
- **Voice reply** — `POST /api/voice/transcribe` (parent issue #2, Whisper).
  Mic/Stop button in the session reply composer records AAC/M4A, POSTs
  multipart with `auto_exec=false` so the transcript lands in the composer
  rather than auto-sending (PWA parity).
- **Home-screen widget (BL6, ADR-0042)** — running / waiting / total counts
  for the active profile; tap opens the app. Resizable, lock-screen allowed.
- **MCP-SSE transport skeleton** — `McpSseTransport` consumes `text/event-
  stream` frames with exponential-backoff reconnect. Not yet wired into the
  UI (post-MVP work turns it into a full MCP client).

### Fixed
- **SQLDelight migration 1 → 2** backfills the `session_event` table for
  v0.2.x-origin encrypted DBs; pre-fix, opening any session in v0.3.0
  crashed with `no such table: session_event` because Auto Backup carried
  the v0.2.x schema across in-place upgrade.

## [0.3.0] — 2026-04-19

Sprint 2 — session UX, multi-server, push.

### Added
- **WebSocket session event stream** (`/ws?session=<id>`) with automatic
  reconnect + exponential backoff + jitter. New `SessionEvent` sealed
  hierarchy + `SessionEventRepository` ring buffer (5000 events / session).
- **Session detail screen** with reply composer, kill confirm, state-override
  dialog, mute toggle, banner-on-failure.
- **xterm.js terminal sheet** (vendored xterm@5.3.0) bound to live WS output;
  dim ANSI markers for state-change / completed / error events.
- **Multi-server picker** dropdown on Sessions top bar + bottom-sheet variant.
- **3-finger upward swipe gesture (BL9, ADR-0042)** opens the server picker
  from any screen; debounced 500 ms.
- **Edit server screen** with two-step probe + delete confirmation; clears
  ActiveServerStore if the deleted profile was active.
- **FCM + ntfy push notifications** (parent issue #1, parent v3.0.0).
  Five notification channels (input_needed/completed/rate_limited/error/
  foreground), inline RemoteInput "Reply" action that posts to
  `/api/sessions/reply`, deep-link tap intent
  (`dwclient://session/<id>`) → SessionDetail. NTFY foreground service
  fallback for servers without Firebase.
- **Filter chips** on Sessions: All / Running / Waiting / Completed / Error.
- **Swipe-to-mute** on session rows.
- **Alerts tab** + bottom-nav badge counter for sessions where
  `needsInput && !muted`.
- Transport: `registerDevice` / `unregisterDevice` /
  `overrideSessionState` REST endpoints (matches parent v3.0.0 wire format
  verbatim).
- 33 unit tests (12 RestTransport / 10 EventMapper / 4 WebSocketUrl / 4
  SessionState / 3 Mappers).

### Changed
- `SessionRepository.db` made `internal` (was private) to enable
  `observeForProfileAny` extension lookup.
- `SessionsViewModel` now combines persisted `ActiveServerStore` selection
  with the live profile list — degrades gracefully on profile delete.

### Scope expansion — ADR-0042
Promotes five items from post-MVP backlog to v0.10.0 requirements (the
release originally mislabelled v1.0.0; the 1.0 label is now reserved for
full PWA parity):
- BL9: 3-finger-swipe-up server picker → Sprint 2 ✅ shipped here
- BL6: home-screen widget → Sprint 3
- BL4: Wear Tile → Sprint 4
- BL10: Android Auto Tile (dev flavor) → Sprint 4
- BL2: biometric unlock → Sprint 5 (amends ADR-0011)

Timelines hold: MVP 2026-06-12, production 2026-07-10.

### Changed
- **Scope expansion — ADR-0042** promotes five items from post-MVP backlog to
  v0.10.0 requirements:
    - BL9: 3-finger-swipe-up server picker → Sprint 2
    - BL6: home-screen widget → Sprint 3
    - BL4: Wear Tile → Sprint 4
    - BL10: Android Auto Tile (dev flavor) → Sprint 4
    - BL2: biometric unlock → Sprint 5 (amends ADR-0011)
  Timelines hold: MVP 2026-06-12, production 2026-07-10. Full rationale + per-
  sprint landing in `docs/decisions/README.md#mvp-scope-adjustment` and
  `docs/sprint-plan.md#scope-change--adr-0042`.

### Added
- **Launcher icon promoted to B8** (Earthrise composition): adaptive foreground
  + background + monochrome variants. Old phone-silhouette Concept B is gone;
  the launcher now shows the lunar surface with pronounced craters, Earth
  rising above the horizon, and the datawatch tablet (with eye + matrix-rain
  stand-in) sitting on the moon. Monochrome silhouette for Android 13+ themed
  icons.
- **Splash animation rewritten to match B8**: live Compose-canvas Earthrise
  scene around the existing animated tablet — starfield, Earth disc with halo
  + continent + cloud + specular highlight, lunar surface with eight
  pronounced craters, Earthshine rim along the horizon, plus the existing
  matrix rain animation + pulsing pupil + scan line on the tablet.
- **`docs/parity-status.md`**: complete PWA feature → mobile sprint matrix.
  Honest accounting of what's shipped, what's planned, and which sprint
  delivers each item. Reviewed every sprint close.

### Fixed
- **Boot crash on first launch** (`java.lang.IllegalArgumentException: Empty key` in
  `KeystoreManager.deriveDatabasePassphrase`). Root cause: Android Keystore refuses to
  expose `SecretKey.encoded` for non-extractable keys, so the HMAC derivation received
  an empty byte array and `SecretKeySpec` rejected it. Rewrote `KeystoreManager` to
  delegate to Jetpack Security's `EncryptedSharedPreferences` (MasterKey-wrapped
  AES-256-GCM), which is the recommended pattern for storing 32-byte passphrases —
  same at-rest protection, simpler, correct. `DatabaseFactory` + `ServiceLocator`
  updated to pass `Context` into the manager.

## [0.2.0] — 2026-04-18

**Sprint 1 delivery — first working Android build.** A fresh install of the debug APK
now successfully onboards against a live datawatch server: add profile, health probe,
session list rendering from a real `/api/sessions` response.

Not yet shipped (Sprint 2+): WebSocket `/ws` streaming, xterm terminal, FCM push,
MCP SSE, voice capture, Wear OS live app, Android Auto live surface.

### Added

- Sprint 1 Phase 4 — tests:
  - `RestTransportTest` MockWebServer suite (9 tests) covering success paths plus
    every `TransportError` mode (Unauthorized, ServerError, RateLimited, Unreachable,
    ProtocolMismatch), reply body shape, start-session id extraction, stats
    deserialization.
  - CI version-parity check rewritten to actually verify `gradle.properties` +
    `Version.kt` agree (previous regex looked for hard-coded literals in `.gradle.kts`
    and silently passed).

### Fixed (CI iteration during this sprint)

- SQLDelight `NULLS LAST` → `IS NULL` workaround (dialect 3.18 compatibility).
- `androidx.car.app` pinned to `1.7.0` stable (`1.7.0-rc02` didn't exist on Maven).
- Kotlin package `auto.public` → `auto.messaging` (`public` keyword conflict).
- Auto flavor source-set sharing (placeholder moved to `src/main/kotlin`).
- Material Components dep added so XML `Theme.Material3.*` parent resolves.
- `shared`'s public signatures expose Ktor + datetime + SQLDelight runtime via `api(...)`.
- Android Lint + ktlint switched to warnings-only for Sprint 1; Sprint 5 flips back.
- Removed phone manifest's `android.hardware.type.watch` / `.automotive` uses-features
  (surface-specific features belong in their respective modules).

## [0.1.0-pre] — 2026-04-18

### Added
- Gradle wrapper committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
  Gradle 8.9) — no first-clone bootstrap step needed.
- Concept B icon set (ADR-0037): Android adaptive icon foreground + background +
  monochrome vector drawables under `composeApp/src/androidMain/res/drawable/`;
  dev-flavor variant with amber "DEV" chip under `composeApp/src/dev/res/drawable/`;
  master SVG + Play Store feature graphic under `assets/`.
- Icon review set — `assets/variants/icon-B1..B4.svg` — four Concept B iterations for
  user pick; winner replaces the committed launcher foreground.
- Sprint 1 plan document: `docs/plans/2026-04-18-sprint-1-foundation.md`.
- Shared-module foundation (Sprint 1 Phase 1):
  - Domain types: `Session`, `SessionState`, `Principal`, `Prompt`, `ReachabilityProfile`
  - Transport: expanded `TransportClient` interface, typed `TransportError` hierarchy,
    Ktor-based `RestTransport` (health / sessions / start / reply / kill / stats)
  - Storage: `DatabaseFactory` expect/actual (plaintext placeholder; SQLCipher swap in
    Phase 2), `ServerProfileRepository`, `SessionRepository`
  - SQLDelight schema split into `profile.sq` + `session.sq`
  - Unit tests: `SessionStateTest`, `MappersTest`
- Sprint 1 Phase 3 — composeApp onboarding + sessions list (first user-visible flow):
  - `ServiceLocator` hand-wired DI — DB, repositories, token vault, shared HttpClient
  - `createHttpClient()` expect/actual: Android OkHttp engine, iOS Darwin engine
  - `OnboardingScreen` → `AddServerScreen` (form + live health probe + token-vault
    persistence with roll-back on probe failure)
  - Home shell: Material 3 `NavigationBar` with Sessions / Channels / Stats / Settings
  - `SessionsScreen` / `SessionsViewModel` — cached list + live refresh; disconnect
    banner per ADR-0013
  - `SettingsScreen` with basic server list + About section
  - `MainActivity` now launches `AppRoot` (Compose Navigation)
  - `compose.materialIconsExtended` added for bottom-nav icons
- Sprint 1 Phase 2 — Android storage + crypto:
  - `KeystoreManager` — AES-256-GCM master key in Android Keystore (StrongBox-preferred
    on capable devices); HMAC-SHA256 derivation of the SQLCipher passphrase.
  - `TokenVault` — EncryptedSharedPreferences for bearer tokens, alias-keyed per
    profile; never persisted to the SQLite DB.
  - `AndroidDatabaseFactory` now opens a SQLCipher-encrypted database via
    `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` with the derived
    passphrase; `DatawatchApp.onCreate` loads `libsqlcipher.so` at process start.
  - Completed backlog: BL8.

### Changed
- App display name set to `datawatch` (lowercase) for all user-facing surfaces — Play
  Store listing, launcher label, Wear watchface, Android Auto, iOS bundle display
  (ADR-0041, supersedes name portion of ADR-0030). Dev variant reads `datawatch (dev)`.
  Technical identifiers (applicationId `com.dmzs.datawatchclient[.dev]`, Kotlin packages,
  repo name `dmz006/datawatch-app`, keystore file names) are unchanged.

## [0.1.0-pre] — 2026-04-18

### Added
- Complete design package under `docs/`: 18 documents covering architecture, data flow,
  data model, API parity, security, threat model, UX, Wear OS, Android Auto, branding,
  sprint plan, Play Store registration, privacy policy, and data safety.
- 40 Architecture Decision Records consolidated in `docs/decisions/README.md`, approved
  during the four-batch design Q&A on 2026-04-17.
- `AGENT.md` operating rules adapted from
  [dmz006/datawatch](https://github.com/dmz006/datawatch), Kotlin-stacked.
- KMP + Compose Multiplatform + Wear OS + Android Auto + iOS-skeleton Gradle scaffold.
- GitHub Actions CI skeleton (`ci`, `release`, `security`).
- Upstream cross-references to `dmz006/datawatch` issues #1, #2, #3.

### Security
- Polyform Noncommercial 1.0.0 license (matches parent project).
- SQLCipher + Android Keystore data-at-rest model documented.
- Closed-loop telemetry stance documented (no Crashlytics / Sentry / Firebase Analytics).

### Status
- Pre-MVP. Implementation begins Sprint 1 (2026-05-02). MVP target 2026-06-12; public
  production 2026-07-10.

[Unreleased]: https://github.com/dmz006/datawatch-app/compare/v0.12.0...HEAD
[0.12.0]: https://github.com/dmz006/datawatch-app/compare/v0.11.0...v0.12.0
[0.11.0]: https://github.com/dmz006/datawatch-app/compare/v0.10.1...v0.11.0
[0.10.1]: https://github.com/dmz006/datawatch-app/compare/v0.10.0...v0.10.1
[0.10.0]: https://github.com/dmz006/datawatch-app/compare/v0.9.0...v0.10.0
[0.9.0]: https://github.com/dmz006/datawatch-app/compare/v0.5.0...v0.9.0
[0.5.0]: https://github.com/dmz006/datawatch-app/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/dmz006/datawatch-app/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/dmz006/datawatch-app/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/dmz006/datawatch-app/compare/v0.1.0-pre...v0.2.0
[0.1.0-pre]: https://github.com/dmz006/datawatch-app/releases/tag/v0.1.0-pre
