# Usage guide

*Last updated 2026-04-22 for v0.33.0.*

A screen-by-screen walkthrough of the datawatch mobile app. This
document reflects the v0.33.0 shipped state. For the authoritative
feature matrix see [parity-plan.md](parity-plan.md); for Auto
specifically, see [android-auto.md](android-auto.md).

## Launch flow

1. **Splash** — animated Matrix/Earthrise scene; stays until the
   encrypted DB has unlocked.
2. **Biometric prompt** *(only if enabled in Settings)* — fingerprint
   or face. Cancel closes the app.
3. **Onboarding** — shown only on first install with zero servers.
   Tap **Get started** to add your first server.
4. **Home shell** — bottom nav with Sessions / Alerts / Stats /
   Settings / About.

## Sessions tab

The default tab. Lists sessions for the currently-active profile (or
federation view).

### Top bar

- **Server name + chevron** — tap to open server picker. The picker
  lists every configured profile plus an **All servers** row that
  aggregates via `/api/federation/sessions`.
- **Connection dot** — 8 dp circle next to the server name.
  **Green** = last probe succeeded. **Grey** = probing / unknown.
  **Red** = last probe failed. Tap for retry sheet + last-success
  timestamp.
- **Reorder toggle (⇅)** (v0.31.0) — enters reorder mode on the
  sessions list. Drag handles appear on each row; drag to set a
  Custom sort order, tap ⇅ again to save via
  `POST /api/sessions/reorder`.
- **Sort dropdown** — Recent activity / Started / Name / Custom.

### Toolbar

Text search + per-backend filter chips + Show/Hide history toggle.
Matches the PWA's Sessions toolbar. No state-quick-filter row (removed
in v0.14.0 per user direction).

### Session rows

Each row shows: session name (renamed or truncated id), backend chip,
hostname, last activity timestamp, state pill, waiting-prompt preview
(up to 4 lines when `waiting_input`), mute indicator.

Row interactions:

- **Tap** — open session detail (or toggle selection in multi-select).
- **Horizontal swipe (≥64 dp)** — toggle mute.
- **Inline Stop / Restart** — visible on running/waiting
  (Stop with confirm) and terminal-state rows (Restart) respectively.
- **▶ Commands** — on waiting rows, opens the **Quick Commands** sheet:
  - **System** presets (Yes / No / Continue / Stop)
  - **Saved** — named commands from Settings → Saved commands
  - **Custom** — free-form text
  - **Arrow keys** (v0.32.0) — ↑ ↓ ← → Tab PageUp PageDown chips send
    the corresponding ANSI escape sequences via `session_reply`
- **👁 icon** — bottom-sheet viewer for the session's last LLM response.
- **⏱ icon** — opens the session timeline sheet
  (`/api/sessions/timeline` pipe-delimited feed, falls back to local
  event stream if the server doesn't have a feed yet).
- **Three-dot overflow** — Rename / Schedule reply / Delete.
- **Long-press** — multi-select mode with bulk delete.

### New Session FAB

A floating "+" button (visible whenever a server is active). Opens
**NewSessionScreen**:

- **Task** — multi-line text. **From library ▾** dropdown inlines a
  saved command when at least one exists.
- **Backend** — dropdown populated from `/api/backends`; calls
  `setActiveBackend` server-wide before `POST /api/sessions/start`
  (parent lacks a per-session backend param).
- **Profile** (v0.15.0) — dropdown from `/api/profiles`; passes as
  `profile` on start.
- **Working directory** — type or **Browse…** opens the server-side
  directory picker (`/api/files` with a `files` / `folders` / `both`
  mode, breadcrumb + parent nav).
- **Start** — posts `/api/sessions/start`, navigates into the new
  session.
- **Recent sessions backlog grid** (v0.32.0) — below Start, shows the
  20 most recent done sessions with a **Restart** action per row.

### 3-finger swipe up

Anywhere in the app, swipe **three fingers upward** to pop the server
picker as a bottom sheet.

## Session detail

Opens via tapping a session row, a notification, or a deep link
(`dwclient://session/<id>`).

### Top bar

- **Session name / id** as title (tap-to-rename inline since post-v0.12).
- **State pill** subtitle — tap to open the state-override menu.
- **Bell icon** — mute / unmute.
- **Mode toggle** — switches between **Terminal** (xterm.js WebView)
  and **Chat** (event-stream list). Choice persists in SharedPrefs.
- **Overflow menu** — Kill (confirm), Schedule reply, Copy link, etc.

### Terminal mode

Full xterm.js WebView, ANSI colour, 5000-line scrollback. Renders
pane-capture frames byte-for-byte identically to the PWA (first capture
resets and writes full pane; subsequent captures wipe-and-redraw). Old
servers still emitting `raw_output` frames degrade gracefully. The
terminal toolbar provides:

- **Search** — inline query + up/down navigation + highlight, backed
  by `xterm-addon-search@0.13.0` (vendored).
- **Copy** — pulls `term.getSelection()` into the system clipboard.
- **Load backlog** — one-shot `GET /api/output?id=&n=1000` prepended
  into the buffer.
- **Fit** — re-fits the terminal to the WebView width after pinch-zoom
  (sends `resize_term` over WS).
- **Jump-to-bottom** — scrolls to the live tail.

Terminal parity behaviors (v0.23.0+): `resize_term` WS frame on
fit-settle, `configCols` enforcement per backend (claude-code locks at
120×40), 30 fps `pane_capture` throttle, freeze-on-done with a
`DATAWATCH_COMPLETE` marker, 5 s watchdog, auto-fit-to-width,
scroll-mode enter/exit.

### Chat mode

Chat-bubble rendering (v0.32.0): avatar + role label + tinted surface
colors per speaker (user, assistant, system). Markdown rendering of
assistant text is deferred to post-1.0.0; plain text for now.

**Inline quick-reply buttons** appear under the latest PromptDetected:
Yes / No / Continue / Stop. These fire `sendQuickReply` without
touching the composer draft.

Event types rendered:

| Type | Renders as |
|------|------------|
| `Output` | Body in mono font, stderr tinted red, system dimmed |
| `StateChange` | `[state] from → to` italic line |
| `PromptDetected` | Highlighted bubble + inline quick-reply buttons |
| `RateLimited` | Amber inline note with retry-after if provided |
| `Completed` | `✓ completed (exit N)` dimmed line |
| `Error` | Red line with message |

Banners above the content:

- **Connection banner** (post-v0.12) — surfaces when the owning
  profile's `isReachable` flips false.
- **Input-required banner** (post-v0.12) — amber strip on
  `waiting_input`, shows the latest prompt text.

### Reply composer

- **Text field** — expands to 4 lines; disabled while a send is in flight.
- **Mic button** — AAC / 16 kHz mono → multipart POST to
  `/api/voice/transcribe` with `auto_exec=false`. Transcript lands in
  the text field for review + send. Prefix matching
  (`new:` / `reply:` / `status:` / `remember`) applies — see
  [ux-voice.md](ux-voice.md).
- **Clock button** — opens a **Schedule reply** dialog pre-seeded with
  the drafted text.
- **Send** — POST `/api/sessions/reply`. Disabled until there's
  non-whitespace text.

Per [ADR-0013](decisions/README.md), failed sends show an inline
banner; nothing is queued.

## Alerts tab

Surfaces sessions where `needsInput && !muted` for the active profile.
A badge on the bottom-nav Alerts icon shows the count. Tap a row to
jump into that session.

- **Swipe left** dismisses (mutes the underlying session).
- **Schedule reply…** (v0.19.0) — inline on each alert row, seeds the
  schedule dialog with the detected prompt.

## Stats tab

Live polling of `GET /api/stats` every 5 seconds.

- CPU / Memory / Disk / GPU progress bars (disk / GPU only when the
  server provides them). Bar colour flips amber >70 %, red >90 %.
- Session counts (total / running / waiting).
- Daemon uptime, formatted as `Nd Nh Nm`.

## Settings tab

Grouped into cards by subject area. Every tab renders through the
`ConfigFieldsPanel` generic renderer (v0.26.0) where applicable, so
label / type / validation / save are consistent across sections.

### Settings → General

- **Servers card** — list, add, edit, delete profiles. Each row
  overflow menu: **Download CA cert** (`GET /api/cert` → Downloads →
  OS security settings shortcut + expandable Android/iPhone install
  steps via CertInstallCard).
- **Security card** — biometric unlock toggle.
- **Schedules card** — all schedules (`GET /api/schedules`), cancel
  via ✕, create via **+** (task / cron / enabled).
- **Saved commands card** — named command snippets from
  `GET /api/commands`; recalled via the New Session form's
  "From library ▾".
- **Session backup** — export state (sessions + settings) to a local
  encrypted archive.
- **BehaviourPreferencesCard** (v0.20.0 / v0.22.0) — input mode
  (tmux / channel / none), output mode (tmux / channel / both / none),
  recent-window minutes, max-concurrent, scrollback lines.

### Settings → LLM

- **LLM backends card** — radio-picker calls
  `POST /api/backends/active`.
- **BackendConfigDialog** (v0.21.0) — structured fields per backend
  (model / base_url / api_key) per ADR-0019. Routes:
  Ollama → `/api/ollama/models`, OpenWebUI → `/api/openwebui/models`.
- **DetectionFiltersCard** (v0.32.0) — four pattern lists
  (prompt / completion / rate_limit / input_needed) plus
  debounce / cooldown milliseconds. PUT `/api/config` patches
  `config.detection.*`.

### Settings → Memory

- **Memory stats** — live totals from `/api/memory/stats`.
- **List / search / delete** — `/api/memory/list`, `/memory/search`,
  `/memory/delete`.
- **Remember** — add fact composer.
- **Export** (v0.22.0) — SAF `CreateDocument` → bearer-auth GET
  `/api/memory/export` → writes to user-chosen URI.
- **KG timeline / graph** — read-only viewer (v0.17.0+).

### Settings → Comms

- **ChannelsCard** (v0.18.0) — list configured channels.
- Per-row **Test** button prompts for a message → POST
  `/api/channel/send`.
- Per-row Switch toggles enable via PATCH `/api/channels/{id}`.
- **Add / remove channel** (🚧) — parent returns 501 on
  POST `/api/channels`; tracked upstream at
  [dmz006/datawatch#18](https://github.com/dmz006/datawatch/issues/18).
- **FederationPeersCard** (v0.20.0) — read-only peer list from
  `/api/servers`.
- **CertInstallCard** (v0.32.0) — download button + security-settings
  shortcut + expandable Android/iPhone install steps.

### Settings → Profiles

- **Active profile selector** (v0.15.0).
- **KindProfilesCard** (v0.32.0) — Project and Cluster kinds. Tap +
  or a row to open **ProfileEditDialog**: name / description fields,
  nested config blocks preserved. PUT
  `/api/profiles/<kind>s/<name>`.

### Settings → Detection

DetectionFiltersCard (same card referenced under LLM; appears here if
the user expands the Detection-first surface).

### Settings → Monitor

- **DaemonLogCard** (v0.16.0) — 10 s auto-refresh, colour-coded,
  paginated view of `/api/logs`.
- **InterfacesCard** (v0.16.0) — `/api/interfaces`.
- **RestartDaemonCard** (v0.16.0) — confirm → `/api/restart`.
- **UpdateDaemonCard** (v0.22.1) — progress bar during check (v0.32.0)
  → `/api/update`.
- **Daemon config viewer** (v0.12.0) — collapsible top-level tree of
  `GET /api/config`, sensitive fields double-masked.

### Settings → Operations

- **Session reorder mode** control (v0.31.0).
- **Behaviour preferences** duplicated surface for quick access.

### Settings → About

- App version, build SHA (`BuildConfig.GIT_SHA`), license, package id,
  source + parent links.
- **Connected to** row — live `GET /api/info` (hostname + daemon
  version + backend).
- **NotificationsCard** (v0.29.0) — channel overview.
- **ApiLinksCard** (v0.29.0) — deep links to `/api/docs` Swagger,
  `/api/stats`, `/api/health`, etc.
- **McpToolsCard** (v0.32.0) — renders `/api/mcp/docs` response as
  flat tool list or grouped categories.

## Notifications

Five Android channels. Toggle any subset from
**Settings → Apps → datawatch → Notifications**:

| Channel | Importance | When it fires |
|---------|------------|---------------|
| Input needed | High | Session pauses waiting for your reply |
| Completed | Default | Session finished (exit code either way) |
| Rate limited | Default | Upstream LLM rate-limited; session will resume |
| Errors | High | Session hit an error |
| Background services | Low | ntfy fallback foreground service marker |

**Inline reply** — the Input-needed notification has a **Reply**
action backed by Android `RemoteInput`. The phone POSTs
`/api/sessions/reply` via `ReplyBroadcastReceiver` without bringing
the app to the foreground.

**Deep links** — tapping any notification opens
`dwclient://session/<session-id>`.

**Active-session suppression** (v0.19.0) — `ForegroundSessionTracker`
+ `NotificationPoster` suppress `InputNeeded` for the session currently
on screen.

## Widget

Home screen → Widgets → datawatch → **Sessions** widget. Shows
running / waiting / total for the active profile.

## Wear OS

- **Notification on the watch** (v0.3.0+) with RemoteInput reply.
- **Complication** (v0.5.0+) on watchfaces that accept it.
- **Rich Wear app** (v0.5.0+) — three-count dashboard + dictation
  composer. Wear never holds the bearer token; all calls proxy through
  the phone via the Wearable Data Layer.

See [wear-os.md](wear-os.md) for full details.

## Android Auto

Under the **Messaging** category on your head unit. Three-screen
navigation:

1. **Summary** — Running / Waiting / Total counts for the first
   enabled profile.
2. **Waiting list** — sessions blocking on you.
3. **Reply** — Yes / No / Continue / Stop quick-action buttons.

Bundled into the public APK since v0.33.0 (before then, the
CarAppService was never shipped despite the code existing).

See [android-auto.md](android-auto.md) for full details + DHU test
guide.

## Gestures summary

| Gesture | Result |
|---------|--------|
| Tap session row | Open detail |
| Horizontal swipe on session row | Toggle mute |
| Long-press session row | Multi-select |
| 3-finger swipe up (anywhere) | Open server picker bottom sheet |
| Pinch-zoom on terminal | Adjust font size (then Fit to re-measure) |

## Voice

Four surfaces (ADR-0025):

1. **Composer mic** inside a session — default reply path.
2. **Global FAB** from any screen when a server is reachable.
3. **Android quick-tile** — launches `dwclient://voice/new`.
4. **ASSIST intent** — Google Assistant handoff.

Prefix matching (v0.26.0) auto-routes transcripts:
`new:` → starts a session, `reply:` → session reply,
`status:` → stats query, `remember` → `memory_remember`.
See [ux-voice.md](ux-voice.md).
