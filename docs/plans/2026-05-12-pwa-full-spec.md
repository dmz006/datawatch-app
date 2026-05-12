# datawatch PWA Full Specification — alpha.50
*Generated 2026-05-12. Source: `internal/server/web/app.js` (19 557 lines) + `index.html` + `docs/api/openapi.yaml`.*
*Use this as the authoritative reference for mobile 1:1 parity work.*

---

## Table of contents
1. [App shell & navigation](#1-app-shell--navigation)
2. [Color palette & design tokens](#2-color-palette--design-tokens)
3. [Sessions list](#3-sessions-list)
4. [Session detail](#4-session-detail)
5. [Alerts page](#5-alerts-page)
6. [Automata page](#6-automata-page)
7. [Observer page](#7-observer-page)
8. [Settings page](#8-settings-page)
9. [New Session modal](#9-new-session-modal)
10. [Modals & dialogs](#10-modals--dialogs)
11. [Alert dock (global tray)](#11-alert-dock-global-tray)
12. [WebSocket events & real-time updates](#12-websocket-events--real-time-updates)
13. [Alpha.41–50 changes](#13-alpha4150-changes-new-since-mobile-arc-closed-at-alpha40)

---

## 1. App shell & navigation

### 1.1 Splash screen
- `<div id="splash" class="splash-overlay">` — rendered in HTML, fades out when WebSocket connects.
- Content: `/favicon.svg` logo + animated ring, title "datawatch", subtitle "AI Session Monitor", animated 3-dot loader.
- Removed by JS adding class or display:none once WS `sessions` message arrives.

### 1.2 Top header bar (`<header class="header">`)

Elements left → right:

| Element | ID | Visibility | Behaviour |
|---|---|---|---|
| Back chevron button | `backBtn` | Only in `session-detail` view | Navigates back (popstate / sessions list) |
| Header title text | `headerTitle` | Always | Shows view name ("Datawatch", "Alerts", etc.) or session name when in detail |
| Identity wizard button (🤖) | `headerIdentityBtn` | Only on `autonomous` (Automata) view | Opens 6-step operator identity wizard modal |
| Help link (?) | `headerHelpLink` | On `sessions`, `session-detail`, `autonomous`, `observer`, `settings` | Opens in-app docs viewer at view-relevant anchor |
| Search/filter toggle (🔍) | `headerSearchBtn` | On `sessions` and `autonomous` views only | Toggles filter row (collapsed by default) |
| Alert pill (🔔 N) | `headerAlertPill` | Always visible | Shows unread dock count; click → `toggleAlertDock()` |
| Status dot | `statusDot` | Always | Green = connected, red = disconnected; long-press → reconnect + update check |

**Alert pill states:**
- `dock.muted`: 🔕 muted, dimmed (opacity 0.55), grey border
- `total === 0`: 🔔 0, dimmed, grey border
- `total >= 1`: 🔔 N, full opacity, `var(--accent2)` (#60a5fa) border + tinted bg

### 1.3 Bottom navigation bar (`<nav class="nav">`)

Five buttons, left → right:

| Order | Label | Icon | `data-view` | Visibility |
|---|---|---|---|---|
| 1 | Sessions | 💬 (speech balloon U+1F4AC) | `sessions` | Always |
| 2 | Automata | 🤖 (U+1F916) | `autonomous` | **Hidden** (`display:none`) until `/api/autonomous/config` returns `enabled:true` |
| 3 | Alerts | ⚠ (U+26A0) | `alerts` | Always; has red badge `#alertBadge` for unread count |
| 4 | Observer | 📡 (U+1F4E1) | `observer` | Always |
| 5 | Settings | ⚙ (U+2699) | `settings` | Always; has red `#peerStaleBadge` for stale federated peers |

Active tab: `.active` CSS class on the selected `.nav-btn`.
Scroll into view on activate: `btn.scrollIntoView({ block:'nearest', inline:'nearest', behavior:'smooth' })`.

### 1.4 FAB (floating action button)
- `<button id="newSessionFab" class="new-session-fab">` — bottom-right corner.
- **Sessions view**: `+`, opens New Session modal.
- **Automata view**: `⚡`, opens Launch Automaton wizard.
- **All other views**: hidden (`fab.classList.add('hidden')`).

### 1.5 Navigation state rules
- `session-detail`: back button shown, bottom nav hidden, `viewEl.classList.add('view-full')`.
- All other views: back button hidden, bottom nav shown, `view-full` class removed.
- `destroyXterm()` called whenever leaving `session-detail`.
- Stats poll interval cleared when leaving `session-detail`.
- History entry pushed for Android/browser back support via `history.pushState({view, sessionId})`.
- `localStorage.setItem('cs_active_view', view)` persists view across refresh.

### 1.6 Version staleness reload
On every page load, `index.html` bakes `window.__DW_VERSION__` via `%%DW_VERSION%%` template variable, then fetches `/api/health` and compares. If mismatched, reloads once (sessionStorage sentinel prevents loops).

---

## 2. Color palette & design tokens

CSS custom properties (dark theme defaults from `style.css`; `data-theme` attribute on `<html>` switches light/dark):

| Token | Purpose | Approximate value (dark) |
|---|---|---|
| `--bg` | Page background | `#111827` |
| `--bg2` | Card/section background | `#1f2937` |
| `--bg3` | Input background / tertiary | `#111827` |
| `--border` | Border color | `#374151` |
| `--text` | Primary text | `#f9fafb` |
| `--text2` | Secondary/muted text | `#9ca3af` |
| `--accent` | Purple accent | `#7c3aed` / `#a855f7` |
| `--accent2` | Blue accent (links, badges) | `#60a5fa` |
| `--success` | Green | `#10b981` |
| `--warning` | Amber | `#f59e0b` |
| `--error` | Red | `#ef4444` |
| `--mono` | Monospace font stack | system monospace |
| `--radius-sm` | Small border radius | `6px` |

**Session state badge colors (border + text):**
- `running` → `var(--success)` (#10b981)
- `waiting_input` → `var(--warning)` (#f59e0b)
- `rate_limited` → `var(--error)` (#ef4444)
- `complete` → `var(--text2)` muted
- `failed` → `var(--error)` (#ef4444)
- `killed` → `var(--text2)` muted
- `unknown` → `var(--text2)` muted

**Automata type badge colors:**
- `software` → `#6366f1` (indigo)
- `research` → `#f59e0b` (amber)
- `operational` → `#10b981` (green)
- `personal` → `#ec4899` (pink)

**Backend/LLM icons (used in `backendIcon()`):**
- `claude` → `⚡`
- `opencode` → `🔮`
- `aider` → `🤖`
- `goose` → `🪿`
- `gemini` → `✨`
- `ollama` → `🦙`
- `openwebui` → `💬`
- `shell` → `🔧`
- default → `⚡`

**Theme:** `localStorage.getItem('cs_theme')` — values: `'dark'` | `'light'` | `'system'`. Default `'dark'`. Applied as `data-theme` on `<html>` before CSS paint.

---

## 3. Sessions list

**View id:** `sessions`  
**Header title:** "Datawatch"  
**FAB:** `+` (opens New Session modal)

### 3.1 Watermark
`<div class="sessions-watermark">` — `/favicon.svg` centered behind the list; purely decorative.

### 3.2 Toolbar (hidden by default)

Toggled by `#headerSearchBtn` in the header. State persisted in `localStorage.getItem('cs_filters_collapsed')` — new operators start collapsed.

When expanded, the toolbar shows:

| Element | Description |
|---|---|
| Filter text input | `#sessionFilterInput`, placeholder "Filter sessions…"; live search against `name`, `task`, `id`, `backend_family`, `llm_ref`, `compute_node_ref`. |
| Clear button | Shown only when `filterText` non-empty; `×` resets filter. |
| LLM button | `LLM (N)` or `LLM: <name>` — expands/collapses LLM filter badges. Only shown when `backendTypes.length > 1`. |
| State button | `State (N)` or `State: <name>` — expands/collapses state filter badges. |
| LLM badges (when open) | Per-backend pill: short label + count. Click toggles filter (click same to clear). Short labels: claude-code→"claude", opencode→"oc", opencode-acp→"acp", opencode-prompt→"oc-p", openwebui→"owui", ollama→"olla", aider→"aider", goose→"goose", gemini→"gem", shell→"sh". |
| State chips (when open) | All, Running, Waiting, Rate-limited, Complete, Failed, Killed. Only chips with count > 0 shown (or the currently-active chip). Color dot matches state color. |
| Server indicator | Shows current remote server name if not local; click to return to local. |
| Schedule badge | `#schedBadge` — clock icon + count of pending schedules; click opens dropdown list with cancel buttons. Hidden when 0. |
| History button | `History (N)` — toggles showing completed/killed/failed sessions. Active = included. |
| Select button | Checkbox icon — only shown when History is on AND there are historical sessions. Toggles batch select mode. |

**Filter behavior:**
- `chip=active` → filter to `running` or `rate_limited` states.
- `chip=waiting` → filter to `waiting_input`.
- `chip=done` → filter to done states.
- `chip=<specific_state>` → filter to that exact state.
- Picking a historical state chip (`complete`, `failed`, `killed`, `cancelled`, `archived`) auto-activates History toggle.
- Text filter: case-insensitive substring match across name, task, id, backend_family, llm_ref, compute_node_ref.

### 3.3 Empty state
Shown when no sessions match filters:
```
💬  No active sessions
Tap the + button to start a session,
or send commands via Signal.
```
(The History/toolbar row is still shown if there are historical sessions.)

### 3.4 Session card layout

Each session renders as `<div class="session-card state-{state}">`. Cards are draggable for reordering.

**Top row (header):**
- Checkbox (only in select mode, for inactive sessions)
- Task text (`sess.name || sess.task || '(no task)'`, truncated to 80 chars)
- Action buttons (right side):
  - **Active sessions:** `■ Stop` (red border/text) + `▶` quick-cmd button (only when `waiting_input`)
  - **Done sessions:** `↺ Restart` + `🗑 Delete` (red border)
- Pipe separator `|` (if action buttons present)
- State badge: `state-badge-{state}` CSS class, text = state value, border color = state color, rounded pill. Has `.stale-dot` inside (dot appears when no channel activity > 2s).
- Drag handle `⋮⋮` (grey, cursor grab)

**Meta row (below task):**
- Short ID pill: `sess.id`, monospace, `bg3` background, rounded.
- LLM badge: `sess.llm_ref || sess.backend_family`, blue tinted pill (`--accent2`).
- Server badge: `sess.server` (only when non-local), blue border pill.
- Agent/worker badge: `⬡ worker` (only when `sess.agent_id` non-null), purple tinted.
- (right-justified) `📄 Response` button (only when `sess.last_response` exists) — opens response viewer modal.
- Time ago (`timeAgo(sess.updated_at)`).

**Waiting input row** (only when `state === 'waiting_input'`):
- Shows last 4 lines of `sess.prompt_context` (or `sess.last_prompt`), max 100 chars each.
- `▶` button in action area opens quick-command popup.

**Quick command popup** (when `waiting_input` + ▶ tapped):
- Dropdown `<select>` with groups:
  - System: approve (`yes`), reject (`no`), continue, skip, ESC, tmux prefix (Ctrl-b), quit (`/exit`)
  - Saved: user-defined commands from `/api/commands`
  - Custom… (shows text input)
- Custom input: free-text + send arrow + cancel.

### 3.5 Batch select mode
- Fixed bottom bar `#selectBar`: "☑ All / None (N)" button + "🗑 Delete (N)" button (disabled when 0 selected) + Cancel.
- Individual card gets checkbox; clicking card toggles selection (instead of navigating).
- "All / None" selects/deselects all inactive sessions.

### 3.6 Drag-to-reorder
Cards have `draggable="true"`. `ondragstart`, `ondragover`, `ondrop`, `ondragend` handlers manipulate `state.sessionOrder` (persisted in `localStorage.getItem('cs_session_order')`).

### 3.7 Session list data source
- WebSocket `sessions` event updates `state.sessions`.
- On view render: splits into `active` (not in DONE_STATES), `recent` (done within last N minutes), `history` (all done).
- Default pool: active + recent (last 5 min). History toggle adds all.
- Sort: `sortSessionsByOrder()` respects manual `state.sessionOrder`; unordered sessions sorted by `updated_at` descending.

---

## 4. Session detail

**View id:** `session-detail`  
**Header title:** `sess.name || (no task)` (editable inline — tap title → rename field)  
**FAB:** hidden  
**Nav bar:** hidden  
**Back button:** shown

### 4.1 Info bar (top)

`<div class="session-info-bar">` contains:

| Element | Condition |
|---|---|
| LLM badge: `⚡ <llm_ref>` in green | `sess.llm_ref` exists (v7) |
| LLM badge: `⚡ <backend_family>` in grey | No `llm_ref`, `backend_family` exists |
| Compute node badge: `⚙ <compute_node_ref>` in purple | `sess.compute_node_ref` exists |
| `tmux` mode badge | Only when `sessionMode === 'tmux'` (not channel/acp) |
| State badge (clickable) | Always; click → `showStateOverride()` to manually override state |
| Stop button `■ Stop` | Active sessions |
| Restart + Delete buttons | Done sessions |
| Timeline button 🕐 | Always; `toggleSessionTimeline()` — collapses/expands timeline at top |
| Response button 📄 | Always; `showResponseViewer()` — shows last LLM response |

Timeline button and response button are right-justified via `margin-left:auto`.

### 4.2 Pending schedules strip
`<div id="sessionSchedules">` — hidden initially; shown when `/api/schedules?session_id=...` returns pending items. Each item: run-at time + cancel button.

### 4.3 Process stats panel
`<div id="statsPanel">` — populated by `loadSessionStats()` from `/api/observer/envelopes`. Updated every 5s while visible.

### 4.4 Connection status banner
Shown when session is active AND `sessionMode === 'channel'` or `'acp'` AND channel not yet confirmed ready:
```
[spinner] Waiting for MCP channel… [if waiting_input: "— answer the input prompt below first"]  [✕]
```
- `✕` calls `dismissConnBanner()` — permanently hides for this session, allows tmux-only use.
- Once channel ready, banner removed.
- If `waiting_input` while channel not ready: input is still enabled (user must answer).

### 4.5 Output tab bar

**Channel-mode sessions (claude / claude-code backend):**

| Tab | ID | Content |
|---|---|---|
| Tmux (or "Chat" for chat-mode) | `tabTmux` | xterm.js terminal or chat bubbles |
| Channel | `tabChannel` | MCP channel messages |
| Status | `tabStatus` | Status + Stats merged panel (with internal sub-tabs) |

Right side of tab bar: `?` channel help button (visible when Channel tab active) + font control dropdown.

**Non-channel sessions (tmux-only):**

| Tab | ID | Content |
|---|---|---|
| Tmux | `tabTmux` | xterm.js terminal |
| Status | `tabStatus` | Status + Stats merged panel |

Font control dropdown always shown.

**Chat-only sessions (`output_mode === 'chat'`):**
No tab bar — just `<div class="output-area chat-mode" id="chatArea">`.

### 4.6 Status tab — internal sub-tabs

When Status tab is active, internal sub-tabs appear:

| Sub-tab | Default | Content |
|---|---|---|
| Status | Yes (default) | Hook health + Current Focus + Sprint/PRD tree + Tests + Git cards |
| Stats | No | CPU donut + host metrics + Container/Compute/LLM cards |

Sub-tab strip: blue underline on active tab.

Polling: both sub-panes refresh every 5s while Status outer tab is open.

### 4.7 Status sub-tab content

**Hook health indicator** (top of status pane, right-aligned):
- `● hooks alive` (green) — `board.hook_health === 'alive'`, click re-polls.
- `● hooks stale` (amber) — `board.hook_health === 'stale'`, click re-polls + Docs link.
- `● no hooks installed` (grey) — no events received + Set up link.

**Cards** (each is a rounded bg2 card with uppercase 11px title):

| Card title | Content | Empty state |
|---|---|---|
| Current focus | `board.current_focus.task`, last event type + tool + timestamp, idle_since if present | "No hook events received yet." + setup link |
| Sprint / PRD tree | `JSON.stringify(board.sprint, null, 2)` in `<pre>` | "no sprint data — hook payload sprint=… expected" |
| Tests | `board.tests.pass` (green) + `board.tests.fail` (red) + optional skip | "no test signal yet" |
| Git | branch name + dirty indicator (amber) | "no git state" |

Footer: note about Council/Skills/Tracker landing in later alpha.

### 4.8 Stats sub-tab content

**Host card** (always shown):
- CPU donut chart (SVG): 60px, colored green/amber/red per threshold (70%/90%).
- CPU percentage + sparkline (80×18px SVG polyline, 60-point rolling history).
- RSS bytes (formatted) + sparkline.
- Threads count (if > 0).
- FDs count (if > 0).
- Net ↓ / Net ↑ in bytes/s (if non-zero).
- PID (root PID, with "+N" if tree has multiple).

**Container card** (conditional — only when `env.container_id` present):
- Container ID (first 12 chars), Image, Runtime.

**Compute Node card** (conditional — only when `sess.compute_node_ref` present):
- Name (link to navigate to Compute).
- GPU % (if `env.gpu_pct > 0`).
- GPU Mem (if `env.gpu_mem_bytes > 0`).

**LLM card** (conditional — only when `sess.llm_ref` present):
- LLM ref name.
- Note: "Token rate / latency / model state coming POST v7.0".
- Link to navigate to LLM.

No envelope found: shows text "No process envelope yet — observer plugin off, or process tree not attributed. Backend: {name}."

### 4.9 Channel tab content

Messages list where each entry has direction:
- `→ {text}` — outgoing (`channel-send-line`)
- `← {text}` — incoming (`channel-reply-line`)
- `⚡ {text}` — notify (`channel-notify-line`)

Seeded from `/api/channel/history?session_id=...` on first open (merged with WS updates, deduped, last 1000 entries, sorted by timestamp).

### 4.10 Tmux tab / terminal area

- xterm.js (`Terminal` instance) with `FitAddon`.
- Font size persisted in `localStorage.getItem('cs_term_font_size')` (default 9px).
- Font toolbar: `Aa ▾` button → dropdown with A−, size label, A+, Fit buttons.
- Scroll button: `⤒` (U+2912, 18px bold) → `toggleScrollMode()` — enters tmux scroll mode (Ctrl-b [).

**Loading splash** (while waiting for first pane_capture):
```
[favicon.svg 64px, opacity 0.3]
Connecting to session…
```
Watchdog retries every 5s, max 3 retries. On failure: "Unable to connect…" + Retry + "Use without terminal" buttons.

**Pane capture behavior:**
- First frame: `terminal.reset()` then write lines.
- Subsequent frames: skip if xterm viewport is scrolled up (preserves scroll-back).
- Scroll mode + time window (700ms): forces redraw for 700ms after scroll action to prevent race.

### 4.11 Chat mode

For `output_mode === 'chat'` sessions (OpenWebUI, Ollama, etc.):

- **Chat area** `#chatArea`: message bubbles, each with avatar (U/AI/S), role label, timestamp.
- **Collapsed older messages:** when > 6 messages, oldest wrapped in `<details>` with "N earlier messages" summary.
- **Empty state:** 💬 icon + "Send a message to begin" + memory commands hint.
- **Chat quick-command bar** at bottom: `📚 memories`, `🔍 recall`, `🔗 kg query`, `🔬 research` buttons.

### 4.12 Log mode

For ACP/headless sessions (`output_mode === 'log'`):
- Lines rendered in `<div class="log-line">`.
- CSS classes added based on content: `log-acp-status` (contains `[opencode-acp]`), `log-processing`, `log-ready`, `log-error`.

### 4.13 Quick commands / saved commands strip

`<div id="savedCmdsQuick">` — shown when session is active AND `input_mode !== 'none'`.

Right side of strip (always visible):
- ESC key `␛` button.
- Arrow keys ↑ ↓ ← → (hold-to-repeat: 250ms delay then 80ms interval).
- Enter `⏎` button.

Left side: loaded by `loadSavedCmdsQuick()` → shows saved command buttons from `/api/commands`.

### 4.14 Input bar

`<div class="input-bar" id="inputBar">` — shown when session is active AND `input_mode !== 'none'`.

CSS states:
- `.needs-input` (yellow border) — when `state === 'waiting_input'`.
- `.input-disabled` — when channel not yet ready (input field disabled).

Contents:
- `<input type="text" id="sessionInput">` with placeholder depending on state:
  - `!connReady` → "Waiting for connection…" (disabled)
  - `isWaiting` → "Type your response…"
  - `sessionMode === 'channel'` → "Send message…"
  - else → "Send command or input…"
- Send button (right):
  - Channel mode, not waiting: `▶ ch` (sends via MCP) or `▶` (sends via tmux) — switches with active tab.
  - All other: `▶` (standard send).
- Schedule button 🕐 — `showScheduleInputPopup()`.
- Voice input button 🎙 — `toggleVoiceInput()` — hold to record / click to start/stop.
- Enter key sends (not Shift+Enter). No auto-focus on touch devices.

### 4.15 Generating indicator

Placed inline in the saved-commands strip between Commands dropdown and the up-arrow button. Shows 3 animated dots when session is generating.

### 4.16 Session header rename

Tapping the header title when in `session-detail`:
- Replaces title with an `<input>` for inline rename.
- Confirm on Enter / blur → PUT `/api/sessions/{id}/name`.
- Cancel on Escape.

### 4.17 Timeline

`toggleSessionTimeline()` → fetches `/api/sessions/{id}/timeline` and renders events above the output area. Toggle shows/hides.

### 4.18 Response viewer modal

`showResponseViewer()` → fetches from state or `/api/sessions/{id}/last_response`. Modal with:
- Header: "Last Response" + ✕.
- Content: formatted text (markdown rendered).
- Copy button.
- Loading/error states.

---

## 5. Alerts page

**View id:** `alerts`  
**Header title:** "Alerts"  
**FAB:** hidden

### 5.1 Top tab bar

Three tabs:
- **Active** (count) — alerts from currently-running sessions.
- **Historical** (count) — alerts from done sessions.
- **System** (count) — alerts with `source === 'system'` or no `session_id`.

Active tab persisted in `localStorage.getItem('cs_alerts_active_tab')`.
Default: whichever has entries (prefers Active → Historical → System).

### 5.2 Top filter bar

`🔔 N alerts/alert` count + chip filter row:

| Chip | Filter | Color |
|---|---|---|
| all | All categories | grey |
| 🟡 prompts | waiting_input or title contains "needs input"/"prompt"/"waiting" | amber |
| 🔴 errors | `level === 'error'` | red |
| 🟠 warn | `level === 'warn'` | amber |
| ⚪ info | everything else | grey |

Right side controls:
- Search input (160px) — live filter by title+body text.
- Sort toggle: "⏷ by session" ↔ "🕒 chronological".
- `✕` dismiss all (server-side ack).
- `🔕` mute dock.
- `↻` refresh.

### 5.3 Per-alert card

Each alert card: left colored border (`level` color), content:
- Level badge (ERROR / WARN / INFO) — colored.
- Time ago.
- Title (bold, 13px).
- Body (12px, grey).
- **Quick reply dropdown** — only on the FIRST (latest) alert for a `waiting_input` session: dropdown of saved commands from `/api/commands`.

### 5.4 Active tab session grouping

Active sessions get sub-tabs (one per session). Only first session panel shown; click tab to switch. Each session section shows all its alerts expanded.

### 5.5 Historical tab session grouping

Each inactive session is a collapsible section (collapsed by default). Header shows: session name/id, state badge (color by state), alert count.

### 5.6 System tab

All system alerts rendered flat (not grouped by session).

### 5.7 Alert model fields

From `/api/alerts`:
- `id`, `session_id`, `level` (error|warn|info), `title`, `body`, `created_at`, `source`.

---

## 6. Automata page

**View id:** `autonomous`  
**Visibility:** nav button hidden until `/api/autonomous/config` returns `enabled:true`.  
**Header title:** "Automata"  
**FAB:** `⚡` (opens Launch Automaton wizard)  
**Header:** robot identity button (🤖) shown; search filter button shown.

### 6.1 Tab strip

`<div class="output-tabs automata-tabs-bar">`:

| Tab | Label | Default |
|---|---|---|
| Automata | "Automata" | Yes |
| Templates | "Templates" | No |

Right side (hidden on Templates tab):
- `☑` Select button — toggle batch select mode.
- `⊞` Filter button — toggle filter bar.
- `⏱` History button — toggle showing completed/cancelled automata.

On Templates tab only: `＋ Template` button (btn-primary).

### 6.2 Filter bar (when open)

- Text search input (flex:1).
- Status filter badges: draft, planning, needs_review, approved, running, blocked.
- Type filter badges: software, research, operational, personal.
- "All" checkbox (select all filtered).

### 6.3 Automata card layout

Each PRD card `<div class="prd-row prd-card prd-card-status-{status}">`:

**Title row:**
- Type badge (colored pill per type color map, bold lowercase).
- Template badge (purple "template") — only if `prd.is_template`.
- Title text (bold 15px, flex:1).
- Status pill (right-justified via `margin-left:auto`).

**Meta row (right-justified, monospace):**
- `prd.id` in `<code>`.
- `prd.updated_at` formatted.

**Progress bar** (from `renderProgressBar(prd)`):
- Visual bar showing task completion %.
- Position label (from `renderCurrentPosition(prd)`).

**Lifecycle strip** (compact single-line: `class="lifecycle-compact"`):
- Shows current step in the pipeline (from `renderLifecycleStrip(prd)`).

**Action row** (bottom, separated by border-top):
- `✕ Cancel` (btn-secondary, grey) — only when not in terminal state.
- (right side) `✓ Approve` (amber bg, bold) — only when `status` is `needs_review`, `revisions_asked`, or `waiting_input`; opens detail view.
- `📌/📍` Pin button — right of approve; pinned = amber, unpinned = dimmed.

**Expandable stories section:**
- `<details>` with "Stories & tasks (N)" summary.
- Expanded: `renderStory()` per story.

**Click anywhere on card (non-button):** opens `renderPRDDetailView(id)`.

### 6.4 Automata card status pills

`statusPill(status)` function renders colored badges:
- draft: grey/muted
- planning: blue
- needs_review: amber/orange
- approved: green
- running: green pulse
- blocked: red
- completed: grey
- cancelled: grey with strikethrough style
- rejected: red

### 6.5 Template card layout

`renderTemplateCard(tmpl)`:
- Title + `built-in` badge (grey pill, only when `tmpl.is_builtin`).
- Description (11px grey).
- Type badge (colored) + tags (grey pills) + var count + use count.
- Action buttons (right): `▶ Use` (always) + `✎` edit (non-builtin) + `✕` delete (non-builtin, red border).

### 6.6 Automata detail view

`renderPRDDetailView(prdId)` — replaces automata list with detail view. Breadcrumb navigation at top.

**Detail tab strip:**
- Overview
- Stories
- Decisions
- Rules
- Scan (conditional)

**Detail header:** Type + status badges, title, ID, last activity, lifecycle strip, action buttons (Approve/Reject/Cancel/Pause/Resume).

**Overview tab:** Description, spec text, assigned LLM, profile, metadata.

**Stories tab:** Tree of stories → tasks with status icons, task spec, backend, effort.

**Decisions tab:** Audit trail of decisions made by the automaton.

**Rules tab:** Configurable rules (guard conditions).

**Scan tab:** Code/spec scan results when available.

### 6.7 Launch Automaton wizard

`openLaunchAutomatonWizard()` — multi-step modal:
- Step 1: Intent text input (infers type from keywords).
- Type picker: software / research / operational / personal.
- Profile, LLM, advanced options (toggle).
- Step 2 onwards (when advanced): configures effort, model, max concurrency.
- Submit: POST `/api/autonomous/prds`.

### 6.8 Batch operations

When select mode active, `#automataBatchBar` appears at bottom:
- Approve all selected.
- Cancel all selected.
- Delete all selected.
- Cancel (exit select mode).

---

## 7. Observer page

**View id:** `observer`  
**Header title:** "Observer"  
**FAB:** hidden

All cards are collapsible sections (`settingsSectionHeader()`) with a disclosure chevron. State persisted per-key in localStorage.

### 7.1 System Statistics card

`#statsPanel` — rendered by `loadStatsPanel()` and `renderStatsData()`.

Fields shown from `/api/stats`:
- Sessions: total, running, waiting, complete, failed, killed, rate_limited.
- Memory: used/total bytes.
- CPU: per-core or aggregate %.
- Uptime.
- Daemon version.

Below stats:

**eBPF sub-section:**
- `#ebpfStatusLine` — loaded from `/api/ebpf/status`. Shows enabled/disabled + per-process net stats if enabled.

**Installed plugins sub-section:**
- `#pluginsStatusList` — loaded from `/api/plugins`. Lists enabled plugins with version + status.

**Federated peers sub-section** (inline summary, auto-refreshing every 8s):
- Count of connected peers with green/amber/red dots by last-push age (< 15s / < 60s / older).

**Cluster nodes sub-section** (initially hidden, shown when cluster data available):
- `#observerClusterList`.

**MCP channel bridge sub-section:**
- `#channelBridgeStatus` — from `/api/channel/bridge/status`.

### 7.2 Memory Browser card

- Search input + Role filter dropdown (All roles / Manual / Session / Learning / Chunks) + Since filter (All time / 7d / 30d / 90d).
- Search button + List button + Export button.
- `#memoryBrowserList` — results area (max-height 400px, scrollable).

### 7.3 Memory Maintenance card

2×2 grid:
- **Similarity-stale eviction** (sweeper.py): days input + Dry-run + Apply buttons + result area.
- **Spellcheck** (spellcheck.py): textarea + Run button + result.
- **Extract facts** (general_extractor.py): textarea + Extract button + result.
- **Schema version** (migrate.py): Check schema button + result.

### 7.4 Scheduled Events card

`#schedulesList` — from `/api/schedules`. List with:
- Schedule ID, run_at time (formatted), type, target session or deferred session name.
- Edit (pencil) + Delete (✕) per row.
- Checkbox for bulk select + Delete Selected.

### 7.5 Global Cooldown card

`#cooldownStatus` — from `/api/cooldown`. Shows:
- Current cooldown: active until time or "none".
- Set cooldown buttons: 5m / 15m / 30m / 1h.
- Clear button.

### 7.6 Session Analytics card

`#analyticsPanel` — from `/api/sessions/analytics`. Shows session counts, token usage, cost, duration stats (aggregated).

### 7.7 Audit Log card

`#auditPanel` — from `/api/audit`. Recent audit entries with timestamp, action, actor, resource.

### 7.8 Knowledge Graph card

`#kgPanel` — from `/api/kg`. Shows entity count, relation count, and actions (query input + results).

### 7.9 Daemon Log card

`#daemonLogPanel` — monospace, dark background, max-height 300px.
- `Newest` button: fetches latest 50 lines.
- `Older` button: fetches next 50.
- Offset counter display.

### 7.10 Federated Peers card (bottom of observer view)

`#observerPeersPanel` — auto-refreshes every 8s while Observer is active view.

Rendered by `renderObserverPeersCard()`:

**Stats row:** key/value pills (from `/api/observer/stats`).

**Config row:** key/value table (from `/api/observer/config`).

**Peers list (from `/api/observer/peers`):**
Per peer row:
- Colored dot: green < 15s, amber < 60s, red ≥ 60s, grey = never.
- Name (bold).
- Shape badge: A=agent, B=standalone, C=cluster (small outlined text).
- Last push age ("Xs ago").
- 📊 Snapshot button → `showObserverPeerSnapshot(name)` modal.
- `×` Remove button → `removeObserverPeer(name)` with confirm.

Empty: "no peers registered".

---

## 8. Settings page

**View id:** `settings`  
**Header title:** "Settings"  
**FAB:** hidden

### 8.1 Settings tab bar

Six tabs (horizontal scrollable bar):

| Order | Tab ID | Label |
|---|---|---|
| 1 | `general` | General |
| 2 | `plugins` | Plugins |
| 3 | `comms` | Comms |
| 4 | `compute` | Compute |
| 5 | `automata` | Automata |
| 6 | `about` | About |

Active tab persisted in `localStorage.getItem('cs_settings_tab')`. Default: `general`.
Each section card is collapsible (chevron ▶/▼); per-section state in localStorage.

### 8.2 General tab

Cards in order:

**Notifications:**
- Status text (Enabled / Blocked / Not requested).
- "Request Permission" button.

**Session Templates:**
- `#templatesList` — from `/api/templates`. Table with name, backend, task snippet.
- Per-row: Use (creates new session), Edit, Delete.

**Device Aliases:**
- `#deviceAliasesList` — from `/api/devices`. Maps signal/push device names to aliases.

**Backend Artifact Lifecycle (Tooling):**
- `#toolingStatusPanel` — from `/api/tooling`. Lists backend artifacts (aider cache, goose sessions, etc.) with cleanup options.
- ↻ Refresh button.

**Docs Search:**
- Search input + Search button.
- `#docsSearchResults` area.
- Pending sources list (trust decisions pending).
- Trusted sources list.
- Export YAML button.

### 8.3 Comms tab

Cards in order:

**Authentication:**
- Browser token (password input + "Save & Reconnect").
- Server bearer token (password input, `server.token`).
- MCP SSE bearer token (password input, `mcp.token`).

**Servers:**
- Connection status: dot + "Connected" / "Disconnected".
- This server: `location.host`.
- `#serverStatus` — additional server info.

**Communication Configuration cards** (from `COMMS_CONFIG_FIELDS` array):
Each backend service gets its own collapsible card with toggle + config fields. Backends:
- Signal: group_id, config_dir, device_name.
- Telegram: token, chat_id, auto_manage_group.
- Discord: token, channel_id, auto_manage_channel.
- Slack: OAuth token, channel_id, auto_manage_channel.
- Matrix: homeserver, user_id, access_token, room_id, auto_manage_room.
- Ntfy: server_url, topic, token.
- Email: SMTP host/port/username/password/from/to.
- Twilio: account_sid, auth_token, from/to numbers, webhook_addr.
- GitHub webhook: listen addr, secret.
- Webhook: listen addr, token.
- DNS channel: mode, domain, listen, upstream, secret, TTL, max_response_size, poll_interval, rate_limit.

**Signal Device linking (inline in Communication Configuration):**
- Status text + "Link Device" button.
- QR code row (hidden until linking started) — renders QR via qrcodejs library.
- Instructions: "Open Signal → Settings → Linked Devices → Link New Device".

**Proxy Resilience:**
- Fields for proxy settings (from `/api/config`).
- Toggles for resilience behaviors.

### 8.4 Compute tab

Cards in order:

**Cost Rates (USD / 1K tokens):**
- `#costRatesList` — from `/api/cost-rates`. Per-model input fields + Save.

**LLM Configuration sub-cards** (from `LLM_CONFIG_FIELDS`; non-legacy cards only):
- Memory configuration.
- RTK configuration.

**Detection Filters:**
- `#detectionFiltersList` — from `/api/detection-filters`. Pattern list + Create form.

**Saved Commands:**
- `#savedCmdsList` — from `/api/commands`. Per-row: name, command text, Edit, Delete.
- "+ Add Command" expandable form: name + command text + Save.

**Output Filters:**
- `#filtersList` — from `/api/filters`. Pattern + action + value.
- Actions: send_input, alert, schedule, detect_prompt.
- "+ Add Filter" expandable form.

**Cluster Profiles:**
- `#clusterProfilesPanel` — from `/api/profiles?kind=cluster`.
- Per profile: name row + Edit + Delete.
- Editor: form (name, base image, image tag, etc.) or YAML mode toggle.

**Compute Nodes:**
- `#computeNodesPanel` — from `/api/compute/nodes`.
- Intro text.
- Migration banner (amber) — if any nodes have deprecated Kind value. Click → per-Node Kind picker modal.
- Per node `<details>` row:
  - Summary: name + auto badge (purple "auto" if `auto_created`) + Kind (deprecated = red ⚠) + address + capacity + operator tags.
  - Sliding switch (green on, grey off; `!` badge if `last_dispatch_error`).
  - ✏️ Edit button, 📡 Details button, × Delete button (red).
  - Disabled rows dimmed (opacity 0.55, grayscale 0.6).
  - Expanded section: key/value table of all fields.
- "+ Add ComputeNode" button → `openComputeAddPanel()` panel-modal.

**LLMs:**
- `#llmsPanel` — from `/api/llms`.
- Intro text.
- Per LLM `<details>` row:
  - Summary: name + auto badge + kind + operator tags.
  - Sliding switch.
  - ✏️ Edit, × Delete.
  - Expanded: JSON dump `<pre>`.
  - "▾ In use…" button → expands in-use section with sessions/automata/personas.
- "+ Add LLM" button → `openLLMEditPanel()` panel-modal.

**LLM in-use section:**
- Filter input + page size select.
- Sections: Sessions (clickable → session detail), Automata, Personas.
- Pagination.

**Container Workers:**
- `#agentsConfigPanel` — fields: image_prefix, image_tag, docker_bin, kubectl_bin, callback_url, bootstrap_token_ttl_seconds, worker_bootstrap_deadline_seconds.
- All with label + input + hint text.
- Save button.

**Federated Observer (quicklink):**
- Mode + peers label.
- "Open Observer view →" button (navigates to Observer).

**Secrets Store:**
- Vault status row (`#vaultStatusRow`) — hidden when Vault backend not active.
- `#secretsListPanel` — secret name/tags list.
- "Add / Update Secret" `<details>` form: name, value (password), tags (comma-separated), description, scopes.

**Tailscale:**
- Status card — from `/api/tailscale/status`.
- Config card — from `/api/tailscale/config`.
- Generate Auth Key button.
- ACL Generate + ACL Generate & Push buttons.

### 8.5 Automata tab

Cards in order:

**Identity:**
- `#identityPanel` — from `/api/identity`. Shows/edits operator identity (role, goals, projects, values, focus, notes).
- Also accessible via 🤖 header button on Automata view.

**Algorithm Mode:**
- `#algorithmPanel` — lists sessions running algorithm-mode; configuration for auto-algorithm selection.

**Evals:**
- `#evalsPanel` — eval suites list + recent runs. Run button per suite.

**Council Mode:**
- `#councilPanel` — (see Council section below).

**Project Profiles:**
- `#projectProfilesPanel` — from `/api/profiles?kind=project`.
- Per profile: name row + Edit + Smoke Test + Delete.
- Editor: form mode (name, repo URL, branch, project dir, scripts, env vars) or YAML toggle.

**Pipeline Manager:**
- `#pipelinesPanel` — from `/api/pipelines`. Stages + triggers.

**Autonomous Config sub-cards** (from `GENERAL_CONFIG_FIELDS`, pipeline/autonomous/orchestrator sections):
- Autonomous settings, orchestrator settings.

### 8.6 Plugins tab

Cards from `GENERAL_CONFIG_FIELDS` where `sec.id === 'plugins'`:
- Plugin enable/disable toggles.
- Plugin-specific config fields.

Also:
- Plugins status list (from Observer).
- Routing config (from `/api/routing`).
- Orchestrator config (from `/api/autonomous/orchestrator`).
- Skills registries (from `/api/skills`).

### 8.7 About tab

Cards:
- **Version:** `#aboutVersion` — from `/api/health`, linked to GitHub release.
- **Orphaned Tmux sessions:** `#aboutOrphanedTmux` — list + "Kill all orphaned (N)" button.
- **Language:** locale override dropdown + Save.
- **Theme:** Dark / Light / System toggle buttons.
- **Branding/Splash:** custom splash image upload/clear.
- **Update check:** Check for Update button + Run Update button (when update available).
  - Progress overlay when updating: version, phase, download progress bar, meta text.
- **Daemon restart:** Restart Daemon button.

### 8.8 Council panel (inside Automata tab)

`_renderCouncilPanel()`:

**Intro:** description + "⚙ View / edit / add personas" button.

**Persona selection:** checkboxes for each configured persona (all checked by default). Shows `p.name` with tooltip = `p.system_prompt`.

**Run form:**
- Textarea: proposal text.
- Mode select: Quick (1 round) / Debate (3 rounds).
- "Run Council" button (btn-primary).

**Live watch area:** `#councilLiveRuns` — collapsible cards per active run, SSE-fed log.

**Recent runs list:** last 5 runs, each showing mode badge, persona count × round count, short ID, "detail" button.

**Subsystem config** (`<details>` collapsible):
- LLM registry entry input (e.g. "ollama").
- Max parallel input (0 = serial, default 2).
- Comm firehose checkbox.
- Persona-wizard draft retention (days).
- Save button.

**Persona management modal (`councilOpenPersonasView()`):**
- Per persona `<details>`:
  - Summary: name + role + ✎ Edit + 🤖 Re-interview + × Delete.
  - Expanded: `system_prompt` in `<pre>` + file path hint.
- "+ Add Persona" `<details>` form: name, role, AI backend selector (default/Ollama/OpenWebUI), 🤖 wizard launch button, system_prompt textarea, Add button.

**Council live watch card (per run):**
- `<details open>`: status badge, short ID, mode, persona count, ✕ Cancel button.
- SSE log area (monospace, max-height 60vh): colored event lines.

### 8.9 LLM Create/Edit form (full field list)

`openLLMEditPanel()` panel-modal. Fields:

**Always shown:**
- **Name** — text input (read-only when editing, opacity 0.7).
- **Kind** — select: ollama, openwebui, opencode, claude-code, opencode-acp, opencode-prompt, aider, goose, gemini, council, shell.
- **Enabled Models** — table (Node | Model | ×); "Add" row: node select + model text/datalist + "+ Add" button.
  - For SaaS kinds: node column hidden; model datalist probed from `/api/llm/claude/models` for claude-code.
  - For local kinds: node select probed → model datalist auto-populated from `/api/compute/nodes/{node}/models?kind=...`.
- **Auto-enable new models discovered on these Compute Nodes** — toggle switch.
- **API key reference** — text input, placeholder `${secret:anthropic-key}`.
- **Timeout** — number input (seconds, 0 = default).
- **Tags** — text input (comma-separated).

**Session backend section** (shown when kind is claude-code, aider, goose, gemini, opencode, opencode-acp, opencode-prompt, shell):
- **Binary path** — text input (e.g. `claude`, `aider`, `goose`).
- **Console width (cols)** — number.
- **Console height (rows)** — number.
- **Output mode** — select: (default), terminal, log, chat.
- **Input mode** — select: (default), tmux, none.
- **Auto git init** — toggle (create repo if missing).
- **Auto git commit** — toggle (commit before/after session).

**Claude-code section** (only when kind === 'claude-code'):
- **Skip permissions** — toggle (`--dangerously-skip-permissions`).
- **Channel mode** — toggle (MCP channel bridge).
- **Auto-accept startup disclaimers** — toggle.
- **Permission mode** — select: (none), plan, acceptEdits, auto, bypassPermissions, dontAsk, default.
- **Default effort** — select: (default), quick, normal, thorough.
- **Fallback chain** — text input (comma-separated LLM profile names).

**ComputeNodes multi-select** (hidden for SaaS kinds):
- `<select multiple size="4">` — ordered failover list.
- Hint: "Hold Ctrl/Cmd to multi-select. Order = failover order."

**Test row:**
- Test model selector + Test button.
- Status text area (`#llmEditStatus`).

**Footer buttons:**
- `</> YAML` — escape hatch to raw JSON editor.
- Cancel.
- Save (btn-primary).

---

## 9. New Session modal

`openNewSessionModal()` — panel-modal anchored inside `.app` shell. Triggered by `+` FAB or directly.

Header: `＋ New Session` + `?` help link (opens /diagrams.html#docs/howto/new-session.md) + `✕` close.

**Fields (top to bottom after alpha.36 reorder):**

1. **Session name** — text input, placeholder "e.g. Auth refactor".

2. **Task description** — `<details>` expandable textarea (optional), placeholder with example. Rows=5.

3. **Project directory** (moved ABOVE LLM per alpha.36):
   - `#sessDirRow` — text input + browse button (🗂). Opens `#dirBrowser` inline panel.
   - Dir browser: current path breadcrumb + `mkdir` button + directory/file listing.
   - Click directory: navigate in; click file: auto-populates dir with parent.

4. **Profile** — select with first option "— project directory (local checkout) —"; subsequent options = configured Project Profiles.

5. **Cluster** — `#sessClusterRow`, hidden unless profile selected. "— Local service instance —" first option.

6. **LLM** (v7 picker, `#sessV7Row`):
   - `#sessLLMSelect` — populated from `/api/llms` + `/api/backends`. Default "(use legacy backend dropdown above)".
   - `#sessComputeSelect` — hidden unless selected LLM has 2+ ComputeNodes; operator picks specific node.
   - Hint text `#sessV7Hint` — shown when LLM selected.

7. **LLM options** (hidden until LLM/backend selected, `#sessClaudeRow`):
   - `#sessPermissionMode` — permission mode select (claude-code only, hidden otherwise).
   - `#sessClaudeModel` — model select (default = config default).
   - `#sessClaudeEffort` — effort level select.

8. **Session backlog** — `renderSessionBacklog()` shows pending/queued sessions if any.

9. **Backend setup hint** — advisory text per backend if not fully configured.

**Submit:** "Start Session" button → POST `/api/sessions/start` (dir mode) or POST `/api/agents` (profile mode).

**Legacy fields** (hidden in UI, kept for back-compat):
- `#backendSelect` — old backend dropdown.
- `#profileSelect` — old session profile dropdown.

---

## 10. Modals & dialogs

### 10.1 Confirm modal (`showConfirmModal`)

Overlay with:
- Message text (not HTML-rendered — plain text).
- Cancel button.
- Confirm button (btn-danger / btn-primary depending on context).

Triggers:
- Stop session → "Are you sure you want to stop this session?"
- Restart → if session has important state (sometimes, varies).
- Delete session → confirm.
- Cancel automaton → confirm text includes automaton ID.
- Remove observer peer.
- Kill orphaned tmux.

### 10.2 Generic modal (`showModal`)

Same structure but with custom title + HTML body + confirm/cancel labels.
Used by: template create/edit, batch confirm guards (LLM batch operations), etc.

### 10.3 State override dropdown (`showStateOverride`)

Click state badge in session detail → inline dropdown on the badge element:
- Options: running, waiting_input, rate_limited, complete, failed, killed.
- Select → PUT `/api/sessions/{id}/state`.

### 10.4 Backend config popup (`showBackendConfigPopup`)

Triggered from Comms tab backend cards "Configure" action.
- Header: "Configure {service name}" + ✕.
- Field form (per BACKEND_FIELDS config — see §8.3).
- Footer: "Test & Load Models" (for Ollama/OpenWebUI) + "Save & Enable" + Cancel.
- Closes on ✕, Cancel, Escape, or overlay click.
- Enter submits (except in textarea).

### 10.5 Compute Kind migration modal

Triggered when deprecated Kind nodes exist (amber banner in Compute Nodes).
- Table: Node name | address | was-kind | new Kind select | Save per row.
- On last row saved: auto-closes + refreshes Compute Nodes panel.

### 10.6 Toast notifications (`showToast`)

- Auto-dismiss after 3.5s (info, success, warning).
- Fixed position, stacks newest on top.
- Types: info (blue), success (green), warning (amber), error (red).

### 10.7 Error toasts (`showError`)

Persistent (no auto-dismiss). Stack multiple errors. X to dismiss.
- 2× font size vs regular toast (16px).
- Red border + background.
- De-dup: same error increments `×N` counter instead of stacking duplicates.
- Special `detail = '__RECONNECT_BUTTON__'`: shows "⟳ Reconnect" button inline.

### 10.8 Update progress overlay

`#updateProgressOverlay` — fixed bottom banner during self-update:
- Title: "Updating to v{version}".
- Phase text (downloading / installing / restarting / failed).
- Progress bar (fills to 100%).
- Meta text (bytes/total or error message).
- Auto-removes after phase `restarting`; stays 15s on `failed`.

### 10.9 Debug panel (`showDebugPanel`)

Triggered by long-press on status dot (combined with reconnect).
- Shows WS state, last message times, session counts, config summary.

---

## 11. Alert dock (global tray)

`window._alertDock` object with: `alerts[]`, `expanded`, `muted`, `el`, `_expandedCards`.

### 11.1 Trigger conditions

**Never auto-opens.** The panel is only created when `dock.expanded === true`, which is set only by explicit operator action (clicking the header pill or alert badge).

### 11.2 Alert pill (always-on header badge)

`#headerAlertPill` — always visible. States:
- Muted: 🔕 muted, dimmed.
- 0 alerts: 🔔 0, dimmed grey.
- N alerts: 🔔 N, full opacity, blue border + tinted bg.

Click → `toggleAlertDock()`.

### 11.3 Alert badge (nav bar)

`#alertBadge` on the Alerts nav button. Shows unread count. Hidden when 0.

### 11.4 Dock panel behavior

Position: `position:absolute; top:48px; right:8px` inside `.app`. Max-width: `min(420px, 100%-16px)`.

**When `dock.expanded = false` or `dock.alerts.length = 0` and not expanded:**
- Panel removed (if it existed). Pill updated.

**When `dock.expanded = true` and `dock.alerts.length = 0`:**
- "🔔 no alerts" panel shown with ✕ close button.

**When `dock.expanded = true` and alerts exist:**

Header row:
- "🔔 N alert(s)".
- Per-type chips (e.g. "error ×3", "warning ×1").
- Collapse chevron ⌄/⌃ (click toggles dock).
- ✕ dismiss (clears all + collapses).
- 🔕 mute.

Body (max-height min(50vh, 360px), scrollable):
Per alert card (`#alertDock .alert-card`):
- Type badge + icon (✕=error, ⚠=warn, ✓=success, ℹ=info).
- Time (HH:MM:SS).
- Repeat count `×N` badge (blue bg, if n > 1).
- ✕ dismiss individual card.
- Message text (clamped to 3 lines if long; ▸ more / ▾ less toggle).
- Left color rail: error=red, warn=amber, success=green, info=blue.

### 11.5 Alert coalescing

Before adding: strips leading `[prefix]` tag. Extracts "family" (text before `—:,`). Key = `family + "||" + type`. If head entry matches key within 60s: increments `n`, updates `ts` + message; does NOT add new entry. Otherwise prepends. Max 100 entries.

### 11.6 `toggleAlertDock()` behavior
- If muted: un-mute + expand.
- If no alerts: show "no alerts" expanded panel.
- Toggle `dock.expanded`.

### 11.7 `muteAlertDock()`
- Sets `dock.muted = true`.
- Clears all alerts + closes panel.
- Persists `sessionStorage.getItem('cs_alert_muted')` (resets on tab close).

### 11.8 WS disconnect → auto-dock

"Connected" success entries are REMOVED from dock (not kept). Prior disconnected/reconnecting entries are also removed. `showError` (persistent banner) is used instead for connection-loss notice.

---

## 12. WebSocket events & real-time updates

Connection: `ws://host/ws?token=...` (or `wss://`). Auto-reconnects with exponential backoff starting at 1s.

### 12.1 Inbound WS message types

| Type | Handler | Effect |
|---|---|---|
| `sessions` | `handleMessage` | Updates `state.sessions`. If `msg.data.version` differs from known: `location.reload()`. Then `onSessionsUpdated()` → re-renders list if on sessions view. |
| `session_state` | `updateSession(msg.data)` | Updates single session in `state.sessions`. Triggers detail button refresh + input banner if active. |
| `output` | `appendOutput(session_id, lines)` | Appends to `state.outputBuffer`. Also forwards to channel tab as incoming lines. |
| `raw_output` | inline handler | Appends to `state.rawOutputBuffer`. For log-mode sessions renders into log area. xterm.js render is driven by `pane_capture`, not raw_output. |
| `stats` | inline handler | Caches `state.statsData`. Refreshes Settings stats panel if visible. Refreshes session detail Stats if visible. |
| `pane_capture` | inline handler | Writes to xterm.js terminal. Throttled to ~30fps. First frame: `terminal.reset()`. Skips if scrolled up (unless within 700ms scroll window). |
| `alert` | `handleAlert(a)` | Increments badge. Pushes to dock via `pushToAlertDock()`. On needs_input sessions may trigger toast. |
| `channel_reply` | `handleChannelReply(data)` | Appends to `state.channelReplies[session_id]`. Re-renders channel area if visible. |
| `channel_ready` | `handleChannelReadyEvent(session_id)` | Sets `state.channelReady[session_id] = true`. Re-renders session detail (removes conn banner, enables input). |
| `chat_message` | `handleChatMessage(data)` | Appends to `state.chatMessages[session_id]`. Re-renders chat bubbles. |
| `needs_input` | `handleNeedsInput(session_id, prompt)` | Updates session state. May show popup if session is not currently active view. |
| `update_progress` | `handleUpdateProgress(data)` | Shows/updates `#updateProgressOverlay`. |
| `generating` | `refreshGeneratingIndicator(session_id)` | Shows/hides generating dots in command strip. |

### 12.2 Outbound WS messages

| Type | When | Payload |
|---|---|---|
| `subscribe` | On session-detail open | `{session_id}` |
| `send_input` | User sends text | `{session_id, text, raw?}` |
| `command` | sendkey etc. | `{text: 'sendkey {id}: {key}'}` |

### 12.3 Polling intervals

| What | Where | Interval | Condition |
|---|---|---|---|
| Session Stats | Session detail Status tab | 5s | Tab is 'status' and session matches |
| Session Status board | Session detail Status tab | 5s | Tab is 'status' and session matches |
| Observer peers | Observer view | 8s | `state.activeView === 'observer'` |
| Sessions list (badge) | Always | via WS push | — |

---

## 13. Alpha.41–50 changes (new since mobile arc closed at alpha.40)

### alpha.41 — LLM registry breaking changes

- `LLM_CFG_SECTION` map added: maps backend kind names to config section keys.
- Legacy "LLM Configuration" card removed from Compute tab — fields (ollama.host, openwebui.url) now live in v7 LLM registry.
- Daemon auto-migrates legacy cfg blocks to ollama/openwebui LLM entries.
- LLM edit form restructured: kind-aware visibility (SaaS vs local, session-backend vs API), new Enabled Models table with per-node rows, auto-add-models toggle.
- New claude-code-specific fields: `skip_permissions`, `channel_enabled`, `auto_accept_disclaimer`, `permission_mode`, `default_effort`, `fallback_chain`.
- New session-backend-specific fields: `binary`, `console_cols`, `console_rows`, `output_mode`, `input_mode`, `auto_git_init`, `auto_git_commit`.

### alpha.42 — Sliding switch toggle + LLM/Compute node disable

- `slidingSwitchHTML()` helper introduced for both Compute Nodes and LLMs.
- Green = enabled, grey = disabled; `!` badge when `last_dispatch_error` present.
- `computeToggleEnabled()` and `llmToggleEnabled()` wire to backend.

### alpha.43 — LLM in-use section

- "▾ In use…" expander on each LLM row shows live bindings.
- Sections: Sessions (clickable to navigate), Automata, Personas.
- Pagination + filter input.
- Delete blocked with confirmation if active bindings exist (409 check pre-flight).

### alpha.44 — Compute node migration modal

- `_migrationBanner()` polls `/api/migration/compute-kinds`.
- Amber banner in Compute Nodes panel when deprecated kinds found.
- Per-Node Kind picker modal: table with node name, address, was-kind, new Kind select, Save per row.
- `saveComputeKindMigration()` → PUT `/api/migration/compute-kinds/{name}`.

### alpha.45 — Model probing in LLM form

- When editing/adding LLM: picking a ComputeNode auto-probes `/api/compute/nodes/{node}/models?kind=...` for available models.
- Populates `<datalist>` for model name input.
- Claude-code kind: probes `/api/llm/claude/models` for aliases + full names.
- `(custom — type below)` escape hatch in dropdown shows free-text input.

### alpha.46 — New Session modal panel → modal refactor

- `openNewSessionModal()` converted from full-screen view to panel-modal anchored inside `.app`.
- Matches Launch Automaton wizard pattern.
- Header with ? help link + ✕ close.
- Whitespace text-node cleanup (removes phantom blank rows).
- Project directory moved above LLM picker row.
- LLM Compute Node picker hidden unless selected LLM has 2+ pinned ComputeNodes.

### alpha.47 — Status+Stats tab merge (GATE alpha.36 → shipped)

- Stats and Status tabs merged into single "Status" outer tab.
- Internal sub-tab strip: Status (default) / Stats.
- Eager fetch status board on session-detail mount (badge reflects state without opening tab).
- `switchStatusSubtab('status'|'stats')` global function.

### alpha.48 — Session list filter improvements

- State chip filter persisted in localStorage (`cs_session_state_chip`).
- Selecting historical state chip auto-activates History toggle.
- State chips only show chips with count > 0 (or currently-active chip).
- LLM button collapses/expands per-backend badges (replaces always-visible badge row).
- State button same treatment.
- Buttons show "LLM: name" or "State: name" when a filter is active.
- Gate (2026-05-10): filter row hidden by default (collapsed); search icon in header reveals it.

### alpha.49 — Compute/LLM edit panel modal

- YAML popup replaced with structured form panel (matches Add panel pattern).
- `computeEditNode(name)` and `llmEdit(name)` fetch record then call `openComputeAddPanel(node)` / `openLLMEditPanel(existing)`.
- Form: all fields pre-populated; Save = PUT, with Test + YAML escape hatch.
- Dim full row when disabled in list view (opacity 0.55, grayscale 0.6).
- Tooltips on Edit/Details/Delete icons for accessibility.

### alpha.50 — Session card + Automata card UX polish (GATE alpha.36 batch)

**Session cards:**
- Stop/Restart/Delete styled as explicit bordered buttons (not icon-only).
- Last-response button moved to lower-right next to time (not with lifecycle buttons).
- State badge has `stale-dot` indicator for no channel activity > 2s.

**Session detail info bar:**
- Timeline and Response buttons icon-only (no text labels) — right-justified via `margin-left:auto`.

**Automata cards:**
- Open button removed (entire card click area opens detail).
- Pin button moved to bottom-right of action row.
- Lifecycle strip compact (`.lifecycle-compact`).
- Status pill right-justified.
- ID + last-activity in separate meta row, right-justified.

**Council v7.0 S4 — live watch:**
- `POST /api/council/run` with `async:true` returns `id` immediately.
- SSE subscription to `/api/council/runs/{id}/events`.
- Live watch card in `#councilLiveRuns` with per-event colored log.
- Auto-collapses on `run_completed`.

**Wear sessions list short ID + last-activity (Sprint 32):**
- `shortId` and `last_activity` fields now surfaced on session cards for Wear OS parity.

---

## Key API endpoints referenced

| Endpoint | Method | Usage |
|---|---|---|
| `/api/sessions` | GET | Session list |
| `/api/sessions/start` | POST | Start new session |
| `/api/sessions/kill` | POST | Stop session |
| `/api/sessions/restart` | POST | Restart session |
| `/api/sessions/{id}/status` | GET | Status board (hook health, focus, sprint, tests, git) |
| `/api/sessions/{id}/timeline` | GET | Event timeline |
| `/api/sessions/{id}/last_response` | GET | Last LLM response |
| `/api/sessions/{id}/state` | PUT | Override state |
| `/api/channel/history` | GET | Channel message history |
| `/api/observer/envelopes` | GET | Process stats envelopes |
| `/api/alerts` | GET/POST | Alerts list / mark-read |
| `/api/commands` | GET | Saved commands |
| `/api/llms` | GET/POST | LLM registry |
| `/api/llms/{name}` | GET/PUT/DELETE | LLM CRUD |
| `/api/llms/{name}/in_use` | GET | LLM binding audit |
| `/api/compute/nodes` | GET/POST | Compute node registry |
| `/api/compute/nodes/{name}` | GET/PUT/DELETE | Node CRUD |
| `/api/compute/nodes/{name}/models` | GET | Probe available models |
| `/api/autonomous/prds` | GET/POST | Automata list/create |
| `/api/autonomous/prds/{id}/pause` | POST | Pause automaton |
| `/api/autonomous/prds/{id}/resume` | POST | Resume automaton |
| `/api/autonomous/prds/{id}/cancel` | POST | Cancel automaton |
| `/api/autonomous/templates` | GET | Template library |
| `/api/council/personas` | GET/POST/DELETE | Persona CRUD |
| `/api/council/run` | POST | Run council (sync or async) |
| `/api/council/runs/{id}/events` | GET (SSE) | Live council run stream |
| `/api/observer/peers` | GET | Federated peers |
| `/api/observer/stats` | GET | Observer statistics |
| `/api/observer/config` | GET | Observer config |
| `/api/profiles` | GET/POST | Project/cluster profiles |
| `/api/schedules` | GET | Scheduled events |
| `/api/config` | GET/PUT | Daemon config |
| `/api/health` | GET | Version + health |
| `/api/stats` | GET | System stats |
| `/api/migration/compute-kinds` | GET/PUT | Node kind migration |
| `/ws` | WebSocket | Real-time event stream |

---

## Appendix: Component reuse patterns

**`settingsSectionHeader(key, title, docsPath)`** — collapsible section header with:
- Chevron (▶ collapsed / ▼ expanded).
- Section title.
- Optional docs link (from `docsPath`).
- Click toggles `secContent(key)` (display:none / '').
- State persisted per key in localStorage.

**`slidingSwitchHTML(safeJ, disabled, issueText, onChangeFn)`** — reusable toggle switch used by both Compute Nodes and LLMs. Returns HTML string.

**`showConfirmModal(message, onConfirm)`** — single-confirm pattern used throughout.

**`escHtml(str)`** — HTML-escape utility, used everywhere in template literals.

**`timeAgo(ts)`** — relative time formatter ("2s ago", "5m ago", "3h ago", "2d ago").

**`fmtBytes(n)` / `formatBytes(b)`** — byte formatter (B / KB / MB / GB).
