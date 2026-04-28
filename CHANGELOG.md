# Changelog

All notable changes to this project will be documented in this file.

Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
This project adheres to [Semantic Versioning](https://semver.org/) per
[AGENT.md Versioning rules](AGENT.md#versioning).

## [Unreleased]

## [0.35.8] — 2026-04-28 (Wear voice via phone-relayed Whisper + popup polish)

### Changed

- **Wear Sessions row preview shows `lastResponse`** instead of
  `lastPrompt`. Running sessions are mostly interesting for what
  they just produced; falls back to `lastPrompt` → `taskSummary`
  → empty so the row is always anchored to readable content.
- **`SessionDetailPopup` mic button anchors to the right edge**
  of the safe area; the Send chip pops in on the left edge once a
  transcript is staged. Centre column carries title / state /
  last-line / transcript stack — thumb hits the controls without
  crossing the screen.
- **State-aware popup labels** — "Listening…" while recording (red),
  "…transcribing" while waiting on the phone, transcript text once
  Whisper replies. Stop icon (`■`) replaces the mic glyph during
  recording so the toggle state is unambiguous.

### Fixed

- **Wear voice replaced with phone-relayed Whisper.** The
  `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` flow added in v0.35.5
  didn't reliably return text on the Galaxy Watch — no on-device
  recognizer model + the Pixel-Watch-style Assistant flow doesn't
  hand the result back to the launcher. v0.35.8 records audio
  locally via the new `WearVoiceRecorder` (mirror of the phone's
  `VoiceRecorder`), ships raw bytes to the phone over MessageClient
  on the new `/datawatch/audio` path. Phone resolves the session's
  profile, posts to `/api/voice/transcribe`, and replies on
  `/datawatch/transcript` with `sessionId\n<text>` (or
  `sessionId\nerror:<cause>` on failure). Watch shows the transcript
  inside the existing popup for the user to validate before tapping
  Send. Reuses the v0.35.5 `/datawatch/reply` plumbing for the
  send_input forward.

## [0.35.7] — 2026-04-28 (PWA v5.1.0–v5.2.0 alignment + data freshness)

### Changed

- **Terminal toolbar always renders** (closes [#8](https://github.com/dmz006/datawatch-app/issues/8)). The badge-row `Aa ▾ / Aa ▴` toggle added in v0.35.6 is gone — PWA v5.1.0 ripped the toggle, and the row reads cleanly at every viewport size. Upstream design-sync issue [dmz006/datawatch#24](https://github.com/dmz006/datawatch/issues/24) is now obsolete and will be closed.
- **Sessions list — "Show / Hide history (N)" → "History (N)"** to match PWA v5.1.0 (drops the verb churn).
- **Session detail — tmux arrow-key row** (mirrors PWA v5.2.0). Four AssistChips (`↑ ↓ ← →`) above the composer send the corresponding ANSI escape sequences via the existing `WsOutbound.sendInput` path. Same chip styling as the saved-commands quick row.
- **Settings → About — Play Store row** added (mirrors PWA v5.2.0). Currently shows `(pending submission)`; URL slots in once the listing publishes.
- **Settings → About — `ConfigViewerCard` removed** to align with the PWA About surface (which carries no raw-config blob). The general-config keys remain reachable via `Settings → General` config panels — the only place users actually edit them.

### Fixed

- **Live `last_response` is now refetched on demand** (BL178 / [#9](https://github.com/dmz006/datawatch-app/issues/9)). Tapping the `💾 Response` button on the session info bar now triggers a forced server re-read before opening `LastResponseSheet`. The daemon's `Manager.GetLastResponse` re-captures from live tmux on every read for `running` / `waiting_input` sessions; cached value from the 5-second list poll was up to 5 s stale and would flash a slightly older snippet.
- **Input-Required banner refreshes on bulk WS state pushes** (mirrors PWA v5.26.49 fix). `SessionDetailViewModel.startStream` now triggers a `refreshFromServer()` on every `SessionEvent.StateChange` frame, so a session that flips to `waiting_input` via the bulk-sessions push surfaces the yellow banner + prompt-context text immediately instead of after the next 5-second poll. Closes the operator complaint *"if I'm in a session and it ends, the yellow box with prompt details doesn't show up, I have to exit and re-enter."*

## [0.35.6] — 2026-04-24 (Composer reshuffle + voice-reply fix + terminal toolbar toggle)

### Changed

- **Sessions list FAB lowered** further. `Modifier.offset(y = 36.dp)`
  on the FAB drops it past the Scaffold's inset reserve so the `+`
  button sits within thumb reach on a 6.8" screen rather than on
  the bottom-nav seam.
- **Sessions list header** — `ServerPickerTitle` drops its vertical
  padding; the TopAppBar already centre-aligns the title so the
  extra 4 dp was making the header feel hollow above the hostname.
- **Session detail — Response button moves back to the badge bar**
  (reverts v0.35.3's composer move). Composer row now stacks the
  **Saved Commands** button (📋 keyboard icon) under the mic. Taps
  open the existing `QuickCommandsSheet` with the session's saved
  commands from `/api/commands`.
- **QuickCommandsSheet custom reply** gets a mic button next to Send.
  Records via `VoiceRecorder`, transcribes, appends into the custom
  text field for review before sending.
- **Terminal toolbar hides by default behind a badge-row toggle.**
  SessionInfoBar now shows `Aa ▾` (expand) / `Aa ▴` (collapse) —
  per-session state, hidden on first open so the badge strip stays
  compact. Upstream design-sync issue [dmz006/datawatch#24](https://github.com/dmz006/datawatch/issues/24)
  filed for PWA parity.
- **Settings → Monitor slims down.** `KillOrphansCard` moves to About
  alongside `UpdateDaemonCard` + `RestartDaemonCard` (daemon-admin
  cluster); Monitor keeps Stats / Memory / Schedules / DaemonLog.

### Fixed

- **Session-detail voice reply routed to the wrong server.** The mic
  button resolved the target transport from `ActiveServerStore`
  instead of the session's own `serverProfileId`. When the active
  store pointed elsewhere (All-Servers mode, cross-server
  navigation) the transcribe call hit a server that didn't host
  that session — the user's "voice use to work but isn't anymore"
  report. Now looks up the session's profile via
  `SessionRepository.observeForProfileAny(sessionId)` and falls back
  to the active store only when the session row isn't cached yet.
- **Voice-failure toast surfaces the real cause.** Ktor's wrapped
  exceptions mask the underlying 404/500/TLS/bearer error in the
  top-level message; the composer now walks the cause chain and
  prints the inner `className: message` so the user can tell
  whether Whisper is disabled, the bearer is missing, or the profile
  is unreachable.
- **"No enabled server profile" toast** when no profile at all can
  be resolved — previously the mic button just silently reset
  after the record/stop cycle.

## [0.35.5] — 2026-04-24 (Wear Sessions tap-popup + voice reply)

### Added

- **Per-session list on the Wear Sessions page.** Phone's
  `WearSyncService` publishes the top-12 most-recently-active sessions
  on a new `/datawatch/sessions` DataItem (id, title, backend, state,
  last-line). Wear renders each as a coloured state-badge row
  (running green / waiting amber / rate-limited red) sorted by
  activity.
- **Tap a session on the watch → `SessionDetailPopup`** with title,
  state, last-line preview, and a microphone button. Mic launches the
  system speech-to-text activity via `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`;
  the transcribed text appears inside the popup for confirmation.
- **Voice-reply relay.** Watch sends the transcript + session id to
  the phone over MessageClient on `/datawatch/reply`; phone
  `WearSyncService` opens a transient WS subscription, emits
  `send_input`, and closes after the drain grace. This is the only
  path — the server doesn't expose a REST `/api/sessions/reply` (404
  pre-v0.34.6 regression that WS send_input was the fix for).

### Changed

- **Wear counts strip unchanged** (running/waiting/total tile trio
  stays at the top of the Sessions page) — the per-session rows
  slot below it so the glance read stays instant.

### Notes

- If a watch reply is issued while the phone is offline or the
  paired server is unreachable, the WS connect will time out during
  `WATCH_REPLY_SUBSCRIBE_GRACE_MS` and the `send_input` frame will
  be dropped silently. A later release should surface that failure
  back to the watch.
- `SessionDetailPopup` is baselined under `wear/detekt-baseline.xml`
  for `LongMethod` (107 vs 80 cap). Refactoring the stacked
  title/mic/send layout would only hurt legibility; accepting the
  baseline is the right call here.

## [0.35.4] — 2026-04-24 (Wear Monitor redesign — color gauges + GPU)

### Changed

- **Wear Monitor page rebuilt around round-bezel gauge rings.** CPU /
  Memory / Disk / GPU each render as a threshold-coloured
  `CircularProgressIndicator` (green < 60 % → amber 60–80 % → red
  ≥ 80 %) with the value printed in the centre of the ring. 2-up grid
  fits the Samsung Galaxy Watch bezel; content overflow scrolls via
  the existing `PageScaffold` vertical scroll.
- **GPU stats now published to the watch.** `WearSyncService` attaches
  `gpuUtilPct`, `gpuTempC`, `gpuMemUsedMb`, `gpuMemTotalMb`, and
  `gpuName` to the `/datawatch/stats` DataItem. Watch hides the GPU
  gauge when the phone hasn't published (graceful downgrade with older
  phone builds).
- **Uptime + VRAM summary** move below the gauge grid as captions
  instead of list rows — keeps the page dense without losing detail.

## [0.35.3] — 2026-04-24 (Sessions UX polish + Wear round cards + Auto-release workflow fix)

### Changed

- **Sessions list: filter / sort / history collapse behind a single
  search icon** in the TopAppBar, with the reachability dot moved to
  the right of the actions slot (matches PWA header layout). FAB
  lowered ~24 dp so it doesn't overlap the bottom nav.
- **Session detail: drop the `tmux` mode badge** — it was redundant
  with the tmux/channel TabRow immediately above. Only non-default
  modes (`channel`, `chat`, etc.) still surface as InfoBadge.
- **Response button relocated** from the SessionInfoBar to the composer
  row, stacked under the microphone (mic 40 dp + response 36 dp) so the
  info bar stays uncluttered while the saved-response surface stays
  one tap from the primary reply action.
- **Terminal toolbar hugs the info bar** — removed the extra 2 dp of
  vertical padding around the font/fit/scroll row so there's no empty
  line between the badges and the controls.
- **Wear: circular bordered card per page** (Monitor / Sessions / Servers
  / About) — `CircleShape` + 1.5 dp primary-tinted border matches the
  Samsung Galaxy Watch bezel geometry.
- **Auto: MagicNumber detekt cleanups** — byte-size and time constants
  in `AutoMonitorScreen` are now named (`BYTES_PER_MB`, `SECONDS_PER_DAY`,
  etc.) instead of inline literals.

### Fixed

- **Release workflow no longer 404s** — `bundlePublicRelease` was renamed
  to `bundlePublicTrackRelease` at v0.32 but `.github/workflows/release.yml`
  still pointed at the old task. Workflow now builds both publicTrack +
  dev bundles and assembles the publicTrack APK.

### Added

- **Slideshows in README** — phone / watch / PWA captures looping at
  ~2.5 s per frame. Watch frames are circle-cropped to match the bezel;
  PWA frames are sourced from Playwright against `localhost:8443`.
- **.gitignore excludes slideshow sources** (`docs/media/phone/`,
  `docs/media/watch/`, `docs/media/pwa/`) so per-frame PNGs stay out of
  git while the generated `*.gif` outputs are committed.
- **`docs/media/capture-{phone,watch}.sh`** — reproducible adb-driven
  navigation + screenshot scripts.
- **Wear detekt baseline** (`wear/detekt-baseline.xml`) to accept the
  NestedBlockDepth / TooManyFunctions findings pending the Wear UI
  refactor tracked in the Monitor redesign backlog.

### Upstream follow-ups

Filed against `dmz006/datawatch` to keep PWA ↔ mobile design in sync:

- [#21](https://github.com/dmz006/datawatch/issues/21) — voice-input UI
  parity (RemoteInput-style reply on PWA).
- [#22](https://github.com/dmz006/datawatch/issues/22) — PWA FAB migration
  for "New session" to match Android.
- [#23](https://github.com/dmz006/datawatch/issues/23) — Sessions list
  filter/sort/reachability-dot layout sync.

## [0.35.2] — 2026-04-24 (G11 inline header rename)

### Changed

- **G11 — Session-detail title is now tap-to-edit inline.** Clicking
  the header swaps the Text for a BasicTextField pre-filled with
  the current title; Enter or blur commits via `vm.rename`. Empty
  / unchanged values are ignored. Mirrors PWA `startHeaderRename`
  at app.js:1672 (which used contentEditable on the `<h2>`). The
  prior RenameDialog is still accessible via the overflow menu
  and elsewhere — inline is the primary path now.

### Notes

- Closes the last P2 row in the 2026-04-23 parity inventory.
  Remaining rows are P3 polish items (Monitor Plugins card,
  terminal toolbar Font/Fit/Scroll buttons, schedule popup field
  alignment) — captured in the audit doc and will ship on an
  opportunistic cadence.

## [0.35.1] — 2026-04-24 (Session-detail polish — state dropdown + Response)

### Changed

- **G12 — State override opens as a dropdown anchored to the pill**
  instead of a full-screen AlertDialog. Mirrors PWA's
  `showStateOverride(sessionId, element)` (app.js:2206) which
  pops a menu directly below the badge. Faster interaction —
  tap pill, tap target state, dismissed. SessionInfoBar now
  owns the dropdown's anchoring; the outer screen hands it
  `stateMenuOpen` + callbacks via new parameters.
- **G13 — Response button surfaces on the session info bar** for
  any session (active or terminal) whose `lastResponse` is non-blank.
  Opens the existing `LastResponseSheet` (was only reachable from
  the Sessions list 📄 icon before). Matches PWA's 💾 Response
  quick-panel under the composer (app.js:~1685).

### Notes

- The old `StateOverrideDialog` composable is retained as dead code
  for back-compat; a later cleanup release will remove it.
- `LastResponseSheet` promoted from `private` to `internal` so it
  can be used outside `SessionsScreen.kt` (from
  `SessionDetailScreen.kt` now).

## [0.35.0] — 2026-04-24 (LLM config paths + Settings compact inputs + eBPF banner)

### Fixed

- **G45 — LLM backend config was writing to nonexistent paths.** The
  Android schema used `backends.<name>.*` (e.g.
  `backends.ollama.enabled`), but the server actually stores each
  LLM's config under a top-level section named the same as the
  backend (e.g. `ollama.enabled`, `openwebui.url`,
  `session.claude_enabled`, `shell_backend.script_path`,
  `opencode_acp.acp_startup_timeout`). Verified against live
  `/api/config` on localhost:8443 and parent `app.js:4262-4288`
  `LLM_FIELDS` + `LLM_CFG_SECTION`. Every toggle + field save was a
  silent no-op — the server accepts unknown keys and discards them.
  - `LlmBackendSchemas.kt` rewritten to emit the canonical paths for
    each known backend, with a `section(name)` + `enabledKey(name)`
    helper to resolve section names consistently.
  - `LlmConfigCard` toggle writes now use `enabledKey(name)` (e.g.
    `session.claude_enabled`, `ollama.enabled`), so flipping the
    per-backend switch actually enables/disables the adapter.
  - `LlmConfigCard.isBackendConfigured` + `readBackendEnabled`
    scan the correct config section instead of the dead
    `backends.<name>` prefix.
  - `NewSessionScreen.backendEnabled` filter uses the same helper,
    so the backend picker correctly hides adapters whose section
    has `enabled=false`.
- **Dead LLM schemas dropped.** Removed `anthropic`, `openai`,
  `groq`, `openrouter`, `xai` — parent daemon has no adapters
  for these; the rows were dead UX. Added missing schemas the
  server does support: `aider`, `goose`, `gemini`, `opencode-acp`,
  `opencode-prompt`, `shell`. `KnownBackends` now matches the
  parent's `available_backends` list verbatim.
- **`StatsDto` missing `ebpf_enabled` + `ebpf_message`.** The
  server emits both (confirmed 2026-04-24 against live
  `/api/stats` on localhost:8443); adding the fields as nullable
  with server-driven defaults so older servers still parse.

### Changed

- **G9/G57 — Settings inputs now tight (compact density).** Swapped
  `OutlinedTextField` for a purpose-built `CompactInput` on every
  field in `ConfigFieldsPanel`: 36 dp tall, 13 sp text, 1 dp
  border, 6 dp vertical content padding. Fixes the user-flagged
  "buttons + input windows way larger than their text" report —
  M3's default 56 dp minimum-height OutlinedTextField dwarfed the
  actual content. The new field preserves password masking,
  number-only keyboards, placeholder text, and the cursor colour
  pull from theme.
- **G26 — Monitor tab eBPF Degraded banner.** Surfaces prominently
  when the daemon was built with eBPF capture but the kernel probes
  aren't loaded. Shows the server's
  `ebpf_message` (e.g. `"Degraded — run: datawatch setup ebpf"`)
  with an amber error-container background matching PWA.

### Notes

- No schema migration.
- Other G19-G28 Monitor cards (Network, Daemon, Infrastructure,
  RTK Token Savings, Memory, Ollama, Session Statistics ring) were
  already present on Android since earlier releases — verified
  coverage during this pass.

## [0.34.9] — 2026-04-24 (G6 Sessions drag-drop reorder)

### Changed

- **G6 — Sessions list reorder is now long-press drag.** Replaces
  the prior hamburger-menu reorder-mode toggle + up/down arrow
  buttons with a native Compose long-press drag on each row.
  Mirrors PWA HTML5 drag-drop (app.js:1412-1415). Approach:
  - Long-press a row → the row lifts (elevation + translationY).
  - Drag vertically → row floats; neighbours stay put (no live
    reordering — intentional for deterministic release).
  - Release → compute `(dragOffsetY / 72 dp).toInt()` and call
    `moveSessionByOffset(sessionId, shift)`, which updates the
    custom-order list in one shot.
  - Auto-seeds Custom sort + custom order snapshot on first drag
    so users don't have to toggle a reorder mode first.
- **Reorder-mode icon removed from the top app bar.** The VM's
  `toggleReorderMode` / `moveUp` / `moveDown` entries remain as
  accessibility fallbacks — they simply aren't surfaced in UI.

### Notes

- No schema or contract changes.
- Custom-order persistence across app restarts is still TBD
  (in-memory during VM lifetime, matching earlier behaviour).
  Follow-up: mirror PWA `localStorage('cs_session_order')` with
  SharedPreferences keyed on profile id.

## [0.34.8] — 2026-04-24 (G7 Alerts rebuild + Settings restart affordance)

### Added

- **G7 — Alerts screen rebuilt to PWA structure.** The old
  AlertsScreen derived rows from the session list (filtered to
  `Waiting && !muted`) and surfaced no actual `/api/alerts` payload.
  The new implementation:
  - Fetches `/api/alerts` every 5 s from the active server's
    transport.
  - Groups alerts by `session_id` (synthetic `"__system__"` bucket
    for session-less alerts) and classifies each group as **Active**
    (live session in running/waiting/rate-limited/new) or **Inactive**
    (terminal state or orphaned). Mirrors `app.js:5550-5627`.
  - Top-level `TabRow` with Active (N) / Inactive (N) tabs; the
    Active count drives the bottom-nav badge.
  - Per-session expandable cards with state pill + alert count +
    session link. Active groups default-expand, inactive
    default-collapse (matches PWA `.alert-session-group` chevron
    behaviour).
  - Per-alert cards with level-colored left border (3dp,
    error/warn/info palette), level badge, timestamp, bold title,
    body preview (500-char truncation). Matches PWA `.alert-card`.
  - Quick-reply dropdown button on the **first** alert of a
    waiting-input session (mirrors `app.js:5573-5580`); tapping
    opens session detail with the composer auto-focused.
  - Per-alert Schedule / Open / ✓ (mark-read) actions.
  - Legacy swipe-left-to-mute gesture retained since it's a useful
    shortcut the PWA lacks.

### Fixed

- **AlertDto was decoding no live fields.** The old DTO used
  `type/severity/message` (non-nullable `type`) but the server
  actually emits `level/title/body`. Every real alert was failing
  decode silently and the UI never showed `/api/alerts` content —
  it was falling back to the session-filter approach. DTO now
  matches the live shape with back-compat for the legacy triple;
  domain `Alert` gains a `title` field distinct from `message`.
- **Settings save + restart — user-reported 2026-04-24.** Any
  change in `ConfigFieldsPanel` auto-saves via `PUT /api/config`,
  but many fields require a daemon restart to take effect
  (TLS, bind, backends, channels). When `server.auto_restart_on_config`
  is off the user had no signal their save wasn't yet active.
  Added an always-visible `RestartNeededBanner` at the top of the
  Settings content area:
  - **Green** note when auto-restart is on ("changes apply
    immediately").
  - **Amber** banner with a prominent **Restart now** button when
    it's off; calls `POST /api/restart`. Post-click status surfaces
    inline ("Restarting daemon…", "Restart requested. Give the
    daemon 5–10 s to come back.", or error).

### Notes

- No schema migration.
- G10 (New-session tab in bottom nav) dropped from the parity plan
  per user direction 2026-04-24: PWA will migrate to the Android
  FAB + full-screen-create pattern this weekend (upstream
  [dmz006/datawatch#22](https://github.com/dmz006/datawatch/issues/22)).
- G41 (Signal QR device linking) WONTFIX — user direction
  2026-04-24: "already on phone, do not need signal setup; that can
  be a server only function." v0.35.2 release dropped from plan.
- Bidirectional parity rule applied: Android-extra gap flagged
  upstream this release —
  [dmz006/datawatch#21](https://github.com/dmz006/datawatch/issues/21)
  (voice-transcribe UI for PWA).

## [0.34.7] — 2026-04-24 (P1 fix pass — G5 + G8)

### Fixed

- **G8 — Session-detail composer hidden behind soft keyboard.** SDK-35
  edge-to-edge is enforced and the prior `imePadding()` was applied to
  the whole outer Column inside the Scaffold, which double-counted
  insets in some layouts and left the reply text field partially
  occluded. Moved `imePadding()` off the outer Column and onto the
  composer Row directly so **only** the input bar lifts above the
  keyboard (the terminal / chat surface keeps its original bounds —
  matches PWA behaviour). Fixes user report "the window with the text
  should lift up when keyboard pops up so you can read the message as
  it is being typed."
- **G5 — New Session LLM picker stuck on old server's backends.** The
  server dropdown seeded itself from `ActiveServerStore` only when
  `selectedProfileId == null` on first composition. If the user
  flipped the active server elsewhere and returned to New Session,
  the picker kept the previous server's backend list. Effect now
  syncs on every `(profiles, activeId)` change until the user makes
  an explicit pick from the dropdown (tracked via `userPickedServer`
  flag), after which auto-sync is suppressed so their manual choice
  sticks.

### Notes

- No schema migration.
- Per the 2026-04-23 parity inventory — G5 and G8 are the two P1
  items the user flagged as still broken post-v0.34.6. Remaining
  batches v0.34.8 → v0.35.2 cover structural parity gaps (Alerts
  rebuild, drag-drop reorder, Settings dimension sweep, session
  detail polish, optional Signal QR linking). See
  [`docs/plans/audit-2026-04-23/README.md`](docs/plans/audit-2026-04-23/README.md).

## [0.34.6] — 2026-04-23 (P0: session-id contract, delete UI, chat mode)

### Fixed

- **Session mutation endpoints were speaking a different dialect
  than the server.** All of `/api/sessions/kill`, `/state`, `/rename`,
  `/restart`, `/delete` were failing with a silent 404 because mobile
  sent either the wrong JSON key (`session_id`) or the short client
  id, while the server (internal/server/api.go) reads `{"id": fullId}`
  and keys its session store on the prefixed `ring-2db6` form.
  - `RestTransport.killSession` body key fixed: `session_id` → `id`.
  - `StateOverrideDto` SerialName fixed to `id`.
  - Every mutate-session VM call (kill, rename, state override,
    restart, delete) now resolves `session.fullId` (e.g.
    `"ring-2db6"`) before hitting the server. Domain `Session`
    exposes a computed `fullId = "$hostnamePrefix-$id"` helper
    with a falls-back-to-short-id safety for pre-cache calls.
  - Fixes the reported **"/api/sessions/kill 404 session not found"**
    on both the session-detail Stop button and the Sessions-list
    row Stop badge.

### Added

- **Delete-after-kill UI** surfaced on both the session-detail
  infobar and the Sessions-list row (prior release only exposed
  Delete from the row overflow menu, which the user flagged).
  Terminal-state rows now show a `🗑 Delete` OutlinedButton next to
  Restart; the detail-screen infobar shows the same once state is
  Killed / Completed / Error. Both go through a confirmation dialog
  before calling `/api/sessions/delete`. Gated on
  `state.deleteSupported` so older servers don't offer it.
- **Chat-mode session rendering.** Sessions whose `output_mode`
  reports `"chat"` (OpenWebUI, Ollama, any chat-transcript backend)
  now render a real transcript surface instead of a blank terminal.
  - New `SessionEvent.ChatMessage(role, content, streaming)` type
    parsed from WS `chat_message` frames in `EventMapper`.
  - New `ChatTranscriptPanel` composable: user / assistant / system
    bubbles with avatar + role + timestamp header. Assistant
    streaming chunks accumulate into a single live bubble; the
    `streaming=false` finaliser seals it (PWA app.js:562-605
    protocol). Transient system indicators
    ("processing...", "thinking...", "ready...") render as a single
    replaceable dot-row instead of piling up the history.
  - `SessionDetailScreen` branches on `session.isChatMode` ahead of
    the existing user-preference Terminal/Chat view toggle — the
    two concepts are distinct (server-side chat mode ≠ user's
    view-toggle choice).
  - SQLDelight migration **4.sqm** adds `session.output_mode` +
    `input_mode` columns so cold-start from cache picks the right
    surface without a round-trip. `SessionDto` + domain `Session`
    carry the new fields through.

### Notes

- Chat history is in-memory only per the PWA's design (server
  doesn't broadcast backfill). The `chatMessageBus` on
  `SessionEventRepository` uses `replay = 64` so late subscribers
  (opening the session after the first messages landed) still see
  the recent burst.
- Delete does not pass `delete_data=true` yet; that second-step
  "Delete tracking data on disk too" option from the PWA is a
  follow-up iteration.
- User's new-session LLM picker reloads from the active server
  (v0.34.5 fix) — follow-up test on this build will confirm
  whether that item from the Open-Not-Assessed list fully closes
  or needs a second pass.

## [0.34.5] — 2026-04-22 (LLM card polish + Settings reactivity + button audit)

### Added

- **`claude-code` LLM backend schema**. Full field set the PWA
  exposes: binary path, model, optional api key, max tokens, max
  turns, system prompt, skip-permissions toggle, working
  directory, timeout.
- **Settings cards reload on active-server change**. Before, the
  user had to bounce out of Settings (e.g. via Sessions tab) and
  back before new-server config appeared. Now `LlmConfigCard`,
  `ChannelsCard`, and the shared `ConfigFieldsPanel` all observe
  `ActiveServerStore.observe()` as a StateFlow, re-fetch on
  active-id change, and preserve the user's tab + scroll
  position. `ConfigFieldsPanel` clears its local value cache on
  server flip so the old server's numbers don't ghost-display
  while the new config is in flight.

### Changed

- **LLM Configuration card — state-aware per-row actions.**
  - When a backend is **configured** (any non-enabled key in
    `backends.<name>.*` has a value): row shows a compact
    enable/disable **Switch** + a pencil edit **IconButton**.
  - When **not configured**: row shows a single **Configure**
    outlined button.
  - The "Make default" action was removed — `session.llm_backend`
    on the General tab is the single place to set default (user
    feedback 2026-04-22, "that is on general tab").
- **LLM card includes `KnownBackends` merged into the
  server-reported list** so claude-code (and any other configured
  backend not yet registered with `/api/backends`) is always
  reachable for edit.
- **New Session backend picker filters to enabled-on-server.**
  Cross-references `/api/backends` names with
  `backends.<name>.enabled` from `/api/config`. Keeps the server's
  default active backend in the list even if its `enabled` flag
  isn't set, so the picker never ends up empty when a default
  exists.
- **New Session: Browse… and Restart are proper OutlinedButtons**
  instead of flat TextButton "text links" (user feedback).
- **Sessions row action buttons** (Stop / Commands / Restart) +
  the offline-sheet Retry are now OutlinedButtons.

### Removed

- **`MessagingBackendsCard`** (the Comms-tab "Messaging Backends"
  card). Redundant with Communication Configuration — user
  feedback 2026-04-22, "isn't needed anymore". Per-type editing
  for backends that don't have a channel instance yet is a
  follow-on via the existing Configure dialog once the instance
  row is created.

### Notes

- Dialog-slot TextButtons (confirm / dismiss inside AlertDialog)
  stay as TextButton per Material guidance — only in-content
  primary actions were converted to OutlinedButton this round.
- PWA-parity audit of every LLM / Comms field is a living task.
  Report any mismatched dot-path keys against your server's
  `config.yaml` and I'll update `LlmBackendSchemas.kt` /
  `ChannelBackendSchemas.kt` — each field is one line.

## [0.34.4] — 2026-04-22 (schema-driven LLM + Comms config dialogs)

### Added

- **Per-backend LLM config schemas** (`LlmBackendSchemas`). Each
  known backend type (ollama, openai, anthropic, groq, openrouter,
  gemini, xai, openwebui, opencode) has its own field list —
  enable toggle + model + api_key plus the type-specific knobs
  (host, base_url, temperature, max_tokens, system_prompt,
  context_window, timeout, site_url, app_name, …). Unknown
  backends fall back to the legacy three-field shape.
- **Schema-driven `BackendConfigDialog`** now renders through
  `ConfigFieldsPanel` so every LLM field prefills from
  `/api/config` and auto-saves on change via the flat dot-path
  patch — same mechanic as the General / Session / Monitor
  config panels. No more "only 3 fields, nothing prefills".
- **Per-channel `ChannelConfigDialog`** with schema per channel
  type (`signal`, `telegram`, `discord`, `slack`, `matrix`,
  `ntfy`, `email`, `twilio`, `webhook`, `github_webhook`). Each
  row in the Comms → Messaging Channels card now has a
  **Configure** button that opens the dialog with every field
  for that type, prefilled from the server, auto-saving.
- **`MessagingBackendsCard`** (new). Lists every known channel
  backend type and opens a global-per-type editor at
  `messaging.<type>.*`. Fixes the 2026-04-22 report —
  "signal is configured on one server but it is not in the
  list": `/api/channels` only returns channel *instances*, so a
  standalone global backend config never appeared. This card
  surfaces the type even without an instance row so users can
  configure signal (or any other backend) directly.

### Changed

- **Channels rows now expose Configure + Test + Toggle + Delete**,
  in that order — matches PWA's right-hand action cluster.
- **Field rendering reuses `ConfigFieldsPanel`** across General,
  LLM, and Comms tabs; adding a new field for any of them now
  takes one line in the corresponding `*Schemas.kt` object.

### Notes

- The dot-path keys in the new schemas reflect the naming
  conventions the parent daemon's `applyConfigPatch` uses
  (`backends.<name>.*` for LLM, `messaging.<type>.*` for global
  channel config, `channels.<id>.*` for per-instance). Where a
  field doesn't match the parent exactly, the save is a no-op
  rather than a crash — no data is lost.

## [0.34.3] — 2026-04-22 (LLM config actions)

### Added

- **LLM Configuration card now has per-backend actions.** Each row
  in Settings → LLM → LLM Configuration now shows a **Configure**
  button (opens the existing `BackendConfigDialog` to edit model /
  base URL / api key) and a **Make default** button on any
  non-default backend (writes `session.llm_backend` via the flat
  dot-path config patch so the picked backend becomes the default
  for every new session). Previously the card was read-only, which
  made the LLM tab feel featureless (user feedback 2026-04-22).

### Notes

- The New Session backend picker already filtered by active server
  via the per-profile `listBackends()` call — no change needed
  there. If the picker is showing stale data, it's the server's
  `/api/backends` response rather than the client.
- Voice debugging work deferred to a later session per user
  request; `SessionDetailScreen` mic + routing fix from 0.34.2
  remains the active baseline.

## [0.34.2] — 2026-04-22 (Monitor widget expansion + voice/widget fixes)

### Fixed

- **Voice transcription routed to wrong server.** The voice composer
  picked the first-enabled profile from the DB instead of honouring
  `ActiveServerStore`, so users with >1 configured server had their
  audio land on whichever server the DB returned first (reported
  2026-04-22 — "is it being processed by another server?"). Voice
  now uses the active profile and falls back to first-enabled only
  when there's no active selection. Transcribe failures now toast
  with the server name + error instead of silently dropping.
- **Microphone silently did nothing.** The composer's mic button
  launched `MediaRecorder` without first requesting `RECORD_AUDIO`,
  so `start()` threw and `runCatching` swallowed it. Now checks the
  runtime grant, requests via `ActivityResultContracts.RequestPermission`
  on first use, and toasts on denial or recorder failure.
- **Session Statistics card showed zero.** Many server builds either
  don't populate `sessions_total` in `/api/stats` or lag by a poll
  cycle. `StatsViewModel` now calls `listSessions()` alongside
  `stats()` and overrides the stats-reported counts with the live
  list count — matches the PWA Monitor's behaviour.
- **`SessionsWidget` ignored `ActiveServerStore`.** Silently pinned
  to the first enabled profile; now resolves active ? first-enabled
  like `MonitorWidget`.
- **Widgets flashed on refresh.** `onUpdate` pre-cleared the widget
  to a "loading…" state before the async refresh landed. Removed the
  pre-clear so the previous snapshot stays visible until new numbers
  arrive.
- **Widgets waited for 30-minute `AppWidgetManager` cadence.** Added
  `ServiceLocator.refreshHomeWidgets()` called from
  `StatsViewModel` + `SessionsViewModel` after each successful poll,
  so widgets update live while the app is open.

### Added

- **Monitor widget: Swap / Network / GPU temp / Daemon rows.**
  User request 2026-04-22 — the widget should show everything
  real-time-worthy. Added swap bar (conditional on `swap_total >
  0`), network row (`↓rx  ↑tx` cumulative bytes, label reflects
  `ebpf_active` vs system fallback), GPU temp folded into the GPU
  value text, and a daemon footer (`RSS · goroutines · fds`).
  Widget min-height bumped to 3×4 cells.

### Notes

- `RECORD_AUDIO` permission is declared in the manifest; if you
  had a previous build installed, the runtime grant carries over.
  Fresh installs will prompt on first mic tap.
- When upgrading between debug builds, always use `adb install
  -r -d` — `adb uninstall` wipes the SQLCipher DB and Android
  Keystore key; configured servers become unrecoverable.

## [0.34.0] — 2026-04-22 (glance surfaces — widgets + Wear tiles + multi-server)

Minor bump per SemVer: new user-facing surfaces (second home-screen
widget, second Wear tile) plus multi-server tap-to-cycle on both
widgets. Still pre-1.0; test track.

### Added

- **Home-screen Monitor widget.** New `MonitorWidget` +
  `widget_monitor.xml` — shows CPU load (vs cores), memory used
  over total, and a one-line session count for the active server.
  Uses `ProgressBar` for inline bars with the existing datawatch
  widget palette. Refreshes on the 30 min `AppWidgetManager`
  cadence; tap opens MainActivity; tap the server-name label
  cycles to the next enabled profile.
- **`SessionsWidget` tap-to-cycle.** Same affordance as
  MonitorWidget — tap the profile label → advance
  `ActiveServerStore` to the next enabled profile. Both widgets
  share `WidgetActions.cycleActiveServer` and refresh each other
  when one cycles, so any Monitor + Sessions pair on the same
  home screen stays in sync.
- **Wear Monitor tile (`MonitorTileService`).** Reads
  `/datawatch/stats` DataItem published by the phone's
  `WearSyncService`. Renders CPU (load/cores), memory pct,
  sessions total + waiting, uptime. Colour thresholds mirror the
  Monitor card (warn ≥70 %, error ≥90 %). Tap → opens the Wear
  companion's Server picker / Sessions pages.
- **Wear Sessions tile wired up (real data).** The pre-existing
  `SessionsTileService` was still the Phase-1 placeholder
  rendering zeros — now reads `/datawatch/counts` from the Data
  Layer. Restyled to the datawatch palette (teal accent / amber
  warning) instead of the legacy purple.
- **`docs/plans/2026-04-22-wear-auto-roadmap.md`.** New planning
  doc enumerating every reasonable Wear + Auto follow-on: 10
  Wear items (complications, ongoing notifications, quick-reply
  buttons, voice, pane-capture image, watch-face complications,
  rotary input, waiting-list tile, etc), 10 Auto items (voice
  reply, TTS incoming, notification deep-link, multi-server
  overview, saved commands in ActionStrip, map template, etc) +
  5 phone widget / quick-tile extensions. Ordered by impact × cost.

### Changed

- **Wear and Auto palette documentation.** Added a feedback
  memory so future Wear + Auto work stays on-brand (dark surface
  + teal / amber accents); stock Material is explicitly out.

### Notes

- `com.dmzs.datawatchclient.dev.debug` is an old sideload from
  before the package-id cleanup; the current debug package is
  `com.dmzs.datawatchclient.debug`. Uninstall the stale one if
  it shows up alongside the new build.

## [0.33.25] — 2026-04-22 (Monitor cards + Wear/Auto feature parity B30/B32/B33)

### Fixed

- **Monitor tab missing system cards + wrong Sessions layout (user 2026-04-22).**
  Rewrote `StatsScreen`:
  - New **System Statistics** card mirrors PWA's `renderStatsData` reads —
    CPU Load (`cpu_load_avg_1 / cpu_cores`), Memory (`mem_used / mem_total`),
    Disk (`disk_used / disk_total`), Swap (conditional), GPU util + temp,
    GPU VRAM. Uses bars with datawatch pct-threshold colours.
  - **Session Statistics** card replaces the flat 3-count row with a circle
    graph showing `sessionsTotal / session.max_sessions` (ring colour
    warns at 70 %, errors at 90 %) plus running / waiting / idle pills.
    `StatsViewModel` lazily reads `session.max_sessions` from
    `/api/config` to populate the denominator.
  - **Server Info** card drops the LLM-backend row (a fleet can run
    several backends; per-session badge + Backend Health card are the
    right surfaces) and adds live CPU and Memory rows for real-time
    display.
  - **Ollama Server** card only renders when the server reports
    `available = true`; previously showed an "offline" card on every
    server that merely listed Ollama as a candidate backend.

### Added

- **B30 Wear + Auto multi-server picker.** `ActiveServerStore` moved
  from `composeApp` to `shared/androidMain` so both surfaces can bind
  to the same SharedPreferences. Auto's Monitor screen ActionStrip
  gains a **Server** button opening `AutoServerPickerScreen` — tap a
  profile → writes through `ActiveServerStore`, phone's Sessions tab
  reflects it next observe tick. Wear adds a dedicated pager page
  with the enabled profile list; tap sends `/datawatch/setActive`
  over MessageClient, phone's `WearSyncService` receives it and
  flips the same store.
- **B32 Wear + Auto monitoring tab.** Auto's root screen is now
  `AutoMonitorScreen` (default per user request 2026-04-22) with
  CPU / memory / disk / VRAM / sessions / uptime rows. Wear's first
  pager page is Monitor, reading a new `/datawatch/stats` DataItem
  the phone republishes every 15 s with a light stats snapshot. The
  phone's `WearSyncService` now owns three DataItem paths (counts,
  profiles, stats) and a MessageClient listener for setActive.
- **B33 Wear + Auto About screen.** `AutoAboutScreen` shows the
  shared `Version.VERSION` + build + surface label. Wear gains an
  About page (page 4 in the pager) reading the same shared object.
  Both are styled with the datawatch dark palette + teal / amber
  accents instead of stock Material defaults (user feedback —
  "make wear and auto stylish, in style with datawatch").

### Changed

- **Auto entry screen is Monitor, not Sessions.** Per user direction
  2026-04-22, drivers see the fleet health first; Sessions becomes
  an ActionStrip-reachable secondary screen.
- **B31 (Wear + Auto sessions w/ snapshot + voice) on hold.** Auto
  already ships `AutoSummaryScreen` → `WaitingSessionsScreen` →
  `SessionReplyScreen` with Yes / No / Continue / Stop quick-reply
  flow; user evaluating before committing to watch snapshot + voice
  rollout.

## [0.33.11] — 2026-04-22 (Sprint FF — upstream fixes integrated)

Both outstanding upstream issues closed. Mobile wiring landed.

### Added

- **Channel create / delete (S9 + dmz006/datawatch#18).**
  ChannelsCard now has a + Add button that opens an
  AddChannelDialog (type dropdown over the 10 known backends:
  signal / telegram / discord / slack / matrix / ntfy / email /
  twilio / webhook / github_webhook; channel-id text; enabled
  toggle). Confirms via `POST /api/channels` which returns the
  created object. Each existing row picks up a Delete icon that
  fires `DELETE /api/channels/{id}`. Backend-specific config
  still flows through the existing BackendConfigDialog after
  create, matching PWA's two-step UX.

  Transport: new `createChannel(type, id, enabled, config?)`
  and `deleteChannel(channelId)` on `TransportClient` +
  `RestTransport`.

### Changed

- **Autonomous section schema (dmz006/datawatch#19).** Server
  added case branches for `autonomous.decomposition_effort`,
  `autonomous.verification_effort`, `autonomous.stale_task_seconds`
  alongside the existing autonomous/plugins/orchestrator keys.
  Mobile schema updated to expose all three new fields so
  autosave actually persists them. S9 closed: every rendered
  field in General, LLM, Comms, and Monitor writes to a server
  key that `applyConfigPatch` recognises.

### Docs

- parity-plan.md Add/remove-channel row flipped 🚧 → ✅.
- api-parity.md upstream-tracking table updated — both #18 and
  #19 marked closed with the mobile integration version noted.

## [0.33.10] — 2026-04-22 (Sprint EE — on-device triage)

Every fix in the 0.33.2 → 0.33.10 patch stream is ground-truthed
against the parent `dmz006/datawatch` source, verified on a live
Galaxy S24 Ultra + Galaxy Watch 7 paired with a v4.0.7 daemon.
Full punch list at
[dmz006/datawatch-app#1](https://github.com/dmz006/datawatch-app/issues/1).

### Fixed — Settings (S1–S9)

- **S7 Save doesn't persist.** `PUT /api/config` now sends a flat
  dot-path patch (`{"ntfy.enabled":true}`), matching the server's
  `applyConfigPatch` switch-case contract. Previously sent the
  entire nested tree (`{"ntfy":{"enabled":true}}`) which the
  server silently dropped for every field. Three call sites:
  ConfigFieldsPanel, DetectionFiltersCard, BackendConfigDialog.
- **S4 Autosave.** Save button removed from ConfigFieldsPanel.
  500 ms debounced autosave keyed on a derived diff of current
  vs loaded values. "Saving…" label appears while in-flight.
- **S5 Tab order.** Monitor → General → Comms → LLM → About,
  default Monitor, matching PWA `app.js:3089`.
- **S6 Monitor cards.** Memory Browser + Scheduled Events moved
  from General to Monitor to match PWA `data-group="monitor"`.
- **S8 LLM picker move.** Active-LLM radio list deleted from the
  LLM tab; picker is now `session.llm_backend` LlmSelect on
  General → Datawatch. LLM tab hosts per-backend config only.
- **S3 + S1 + S2 Settings density.** Nested MaterialTheme scales
  typography down (bodyLarge 16→14, bodyMedium 14→13, titleMedium
  16→14) only within the Settings surface.
  `CompositionLocalProvider(LocalTextStyle provides 13sp)` drops
  OutlinedTextField / OutlinedButton default text to 13sp without
  touching individual call sites. ConfigFieldsPanel rows use
  shared `INPUT_WIDTH = 160dp` + `ROW_PADDING` constants so every
  label-input row aligns.
- **S9 partial.** `autonomous.*`, `plugins.*`, `orchestrator.*`
  fields render but don't persist — server's `applyConfigPatch`
  doesn't include those in its switch. Upstream limitation
  (PWA has the same behaviour per `app.js:3620` comment).
- Transport `request {}` now catches
  `kotlinx.serialization.SerializationException` separately —
  previously bucketed under the generic `Throwable → Unreachable`
  branch, which misleadingly flipped the reachability dot red
  when the server had actually answered fine.

### Fixed — Transport (LLM + Comms tabs)

- `listBackends` accepts both shipped shapes: `{llm:[{name,...}]}`
  (current server) and `{llm:[String]}` (older). Extracts names
  either way.
- `listChannels` accepts `{channels:[...]}` envelope from current
  server plus the older bare-array shape.

### Fixed — Session detail (T1–T3)

- **T3 Single display source.** Raw-output fallback deleted. Only
  `pane_capture` ever writes to xterm. Matches parent
  `dmz006/datawatch@0393e262` which killed the legacy path as
  "broken for TUIs".
- **T2 Resize-race on re-entry.** TerminalController now stashes
  `pendingMinCols/Rows` + `pendingFrozen` and replays from
  `onPageFinished`. Prevents claude-code's 120×40 enforcement
  from dropping on the second session open.
- **T1 Font size 9 → 11.** PWA's 9px is a desktop default; 11 is
  the mobile-readable floor. User toolbar A+/A- still persists.
- `SessionEventRepository` no longer silently drops
  `PaneCapture`. Live captures flow through an in-memory
  `MutableStateFlow<PaneCapture?>` per session id merged into
  `observe()`.
- `LaunchedEffect` keys on the latest pane-capture timestamp so
  replacement captures retrigger render.
- xterm CSS mirrors PWA `.output-area`: `overflow-x:auto`,
  `overflow-y:hidden` on container, `width:fit-content;
  min-width:100%` on `.xterm`, scrollbar hidden,
  `box-sizing:content-box` reset per xterm.js #1283 / parent
  `a129d031`. Viewport gets `touch-action:pan-x` so swipe-up
  can't scroll a non-existent scrollback unless tmux scroll mode
  is entered.

### Fixed — Session detail header

- TopAppBar title column was squeezed to ~1 char width by five
  actions (StatePill + Stop + Timeline + Mute + More), causing
  session id + backend chip to render one character per line.
  Timeline + Mute moved into the overflow; title Texts get
  `softWrap=false, overflow=Ellipsis`.

### Fixed — About crash

- McpToolsCard nested a `verticalScroll` Column inside the outer
  Settings `verticalScroll` — Compose throws on infinite-height
  measure. Inner scroll removed; outer Settings Column already
  scrolls.

### Fixed — Wear (W1)

- Wear module was missing launcher icon resources entirely, so
  Galaxy Watch omitted the app from the drawer. Copied
  composeApp's adaptive-icon XMLs into `wear/src/main/res/`.
  Both `<application>` and `<activity>` now declare
  `android:icon/roundIcon`. Monochrome layer dropped from Wear
  (references `?attr/colorControlNormal` which doesn't resolve
  under `Theme.DeviceDefault`).

### Fixed — Android Auto (A1)

- Added `FOREGROUND_SERVICE_CONNECTED_DEVICE` permission required
  by Android 14+ for any service with
  `foregroundServiceType="connectedDevice"` — the CarAppService.
  Without it the Auto host's `startForegroundService()` for
  `DatawatchMessagingService` throws `SecurityException` on
  modern devices and the app never surfaces on the head unit.
  Verified in APK via `aapt2 dump xmltree`.

### Versioning

| Step | Subject |
|-----|---------|
| 0.33.2 | About crash fix (McpToolsCard nested scroll) |
| 0.33.3 | DTO shape fix (LLM, Comms) + error classification |
| 0.33.4 | pane_capture live bus + session-detail header density |
| 0.33.5 | xterm CSS + 9px default + box-sizing reset |
| 0.33.6 | flat dot-path patch + debounced autosave (S7, S4) |
| 0.33.7 | Settings tab order + card moves + typography (S5/6/8/3) |
| 0.33.8 | xterm T1/T2/T3 + Wear W1 |
| 0.33.9 | Auto A1 — FOREGROUND_SERVICE_CONNECTED_DEVICE |
| 0.33.10 | Settings LocalTextStyle polish + dead-code cleanup |

## [0.33.1] — 2026-04-22 (Sprint DD — full docs refresh)

### Changed

- **Eleven docs refreshed to v0.33 state** — every file under `docs/`
  that had been last-touched at v0.10.0–v0.14.0 now matches shipped
  reality:
  - `usage.md` — rewritten screen-by-screen for v0.33 (new Settings
    sections, Quick Commands sheet, chat bubbles, session reorder,
    recent-sessions grid, CertInstallCard, McpToolsCard,
    DetectionFiltersCard, KindProfilesCard).
  - `config-reference.md` — completed per AGENT.md Configuration
    Accessibility Rule; every user-settable value enumerated with UI
    path, type, default, persistence tier, server-echo behaviour.
  - `parity-status.md` — redirected to `parity-plan.md` as the single
    authoritative matrix (eliminates two-doc drift pattern that
    caused past staleness).
  - `architecture.md` — v0.33 status note, Auto bundling section,
    RelayComponent marked shipped.
  - `api-parity.md` — ~20 endpoint rows added (rename / restart /
    delete / reorder / timeline / logs / interfaces / restart /
    update / backends.active / ollama / openwebui / memory / channels
    / channel-send / cert / info / files / output / profiles /
    mcp-docs / federation), `POST /api/config` flipped to ✅
    structured-fields (v0.20 / v0.21 / v0.32), upstream-tracking
    section reduced to only the single still-open item (#18).
  - `data-flow.md` — four new sequence diagrams
    (profile create/edit, detection filters read+patch, session
    reorder save, MCP tools list).
  - `ux-navigation.md` — Settings section replaced with the current
    nine subsections (General / LLM / Memory / Comms / Profiles /
    Detection / Monitor / Operations / About).
  - `ux-session-detail.md` — chat bubble rendering block, Quick
    Commands sheet block, inline quick-reply buttons, session backlog
    grid.
  - `ux-voice.md` — v0.33 status note + Quick Commands arrow-key
    clarification.
  - `wear-os.md` — v0.33 bundling note (Wear has always been bundled
    via phone AAB; contrast with the Auto v0.33 fix).
  - `sprint-plan.md` — v0.11–v0.33 sprint summary table, v1.0.0
    parity roadmap (single remaining upstream blocker: #18).
- **`parity-plan.md` — Pagination row flipped 🚧 → ✅** (active+recent
  partition + Show History is parity-equivalent to PWA's pager; no
  separate "load more" needed). **eBPF viewer row** clarified from
  🚧 to ⏳ (post-1.0.0 per ADR-0019).

### Verified

- Built `publicTrack-debug` APK (93.7 MB,
  `composeApp-publicTrack-debug.apk`). Confirmed via `aapt2 dump
  xmltree`: `DatawatchMessagingService` + `CarAppService`
  intent-filter + `androidx.car.app.minCarApiLevel=1` +
  `CarAppPermissionActivity` + `CarAppNotificationBroadcastReceiver`
  all present in the shipped manifest. Ready for in-car testing.

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
