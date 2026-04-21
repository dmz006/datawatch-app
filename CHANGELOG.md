# Changelog

All notable changes to this project will be documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/) per
[AGENT.md Versioning rules](AGENT.md#versioning).

## [Unreleased]

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
