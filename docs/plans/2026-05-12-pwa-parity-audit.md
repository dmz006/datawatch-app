# PWA Parity Audit — 2026-05-12

**PWA version audited:** v7.0.0-alpha.50  
**Mobile version:** v1.01.0/179  
**Daemon baseline at arc close:** v7.0.0-alpha.40  
**Auditor:** Claude Code (automated deep-read of app.js + Kotlin sources)

---

## Executive summary

22 gaps found across 9 pages/areas.  
Critical (wrong behavior): 5.  
Missing features: 12.  
Layout/cosmetic: 4.  
Alpha.41–50 new work not yet in mobile: 3 distinct feature sets.

---

## Page-by-page audit

### Header / Nav bar

**PWA (`index.html` + `navigate()` in app.js):**
- Single top bar: `<back-btn>` · `<header-title>` · robot-icon (Automata page only) · `?` help link (page-specific) · 🔍 search button · global alert pill (`headerAlertPill`) · connection status dot
- Bottom nav: **Sessions** (💬) · **Automata** 🤖 (conditional — hidden when autonomous disabled) · **Alerts** ⚠️ (with badge) · **Observer** 📡 · **Settings** ⚙️ (with stale-peer badge)
- 5 tabs max; Observer is a full top-level nav destination (not inside Settings)
- FAB `+` for new session overlaid on top of bottom nav

**Mobile (`BottomNavBar.kt` + `AppRoot.kt` + `SessionsScreen.kt` TopAppBar):**
- Top bar: server picker title · optional refresh spinner · 🔍 search toggle · reachability dot
- Bottom nav: **Sessions** (Chat icon) · **Automata** 🤖 (conditional, only when `prdsSupported`) · **Alerts** (NotificationsActive icon, always-on badge) · **Settings** ⚙️ (with stale-peer badge)
- 4 tabs max; **Observer tab is missing** — its content is buried in Settings → Monitor
- FAB `+` overlaid on sessions list only

**Gaps:**
- **G1** `Observer` is a standalone bottom-nav tab in PWA (📡) but does not exist in mobile's bottom nav; its monitoring cards live in Settings → Monitor instead. PWA also has `peerStaleBadge` on Settings icon but mobile does **not** have the Observer nav tab at all.
- **G2** PWA header shows a persistent global **alert pill** (`headerAlertPill`) on every page that shows pending-alert count and opens the dock on tap. Mobile has no equivalent global header pill; alerts are only visible via the nav badge.
- **G3** PWA shows context-sensitive `?` help link in header per page. Mobile has no help link.

---

### Session list

**PWA (`sessionCard()`, `renderSessionsView()`):**
- Card shows: task text (80-char truncation) · state badge (colored border) · short-id monospace chip · LLM/backend badge (accent2 color) · server badge (when non-local) · agent/worker badge (⬡ purple) · action buttons inline (Stop / Quick-cmds / Restart / Delete as explicit bordered buttons) · "📄 Response" button (when `last_response` present) · waiting-input context preview (last 4 lines) · time-ago · hostname · drag handle (⠿⠿)
- Filter bar: text search + state chips (All/Running/Waiting/Rate-limited/Complete/Killed) + per-backend badge chips + LLM filter chips (alpha.36)
- Selection mode for bulk delete of done sessions (checkboxes)

**Mobile (`SessionsScreen.kt` `SessionCard`):**
- Card shows: session.id (shortId) · state pill (PwaStatePill) · backend badge · worker pill (⬡ purple) · council badge 🎭 · mute icon · ⋮ more menu (rename/watch/restart/delete) · task text (80-char truncation) · "📄 response" icon button (when lastResponse present) · hostname · time-ago · waiting-input context block (last 4 lines)
- Filter bar: text search + state chips (All/Active/Waiting/Complete/Killed) + backend filter chips + LLM filter chips (v0.97+) + Council virtual session chip (v0.74)
- Selection mode for bulk delete

**Gaps:**
- **G4** Mobile card lacks an inline **"Stop" button** at the card level — it requires opening the ⋮ menu. PWA puts `■ Stop` as an explicit button in the card header for active sessions, and separate `↺ Restart` + 🗑 `Delete` for done sessions. This is a noted discrepancy (BL299 fixed in PWA).
- **G5** PWA shows a **drag handle** (⠿⠿) icon for manual session reorder via drag-drop. Mobile has drag-to-reorder via long-press, but no visible drag handle glyph.

---

### Session detail — tab structure

**PWA (`renderSessionDetailView()`, alpha.36 gate):**
- Without Channel: **Tmux** (default) · **Status** [merged tab containing sub-tabs: Status / Stats]
- With Channel: **Tmux** (or Chat) · **Channel** · **Status** [merged tab]
- Stats is a **sub-tab** inside Status, not a top-level tab; the outer tab is always labeled "Status"
- The "Status" tab has an internal sub-tab bar: `Status | Stats`

**Mobile (`SessionDetailScreen.kt` lines 504–512):**
- **Tmux** (default) · **Channel** · **Stats** · **Status** — four separate top-level tabs, always visible
- Stats and Status are **separate top-level tabs** in mobile but in PWA they share one "Status" tab with sub-tabs

**Gaps:**
- **G6 (Critical):** PWA merged Stats + Status into one top-level "Status" tab with an internal `Status | Stats` sub-tab strip at alpha.36. Mobile still has four separate top-level tabs (Tmux · Channel · Stats · Status). The tab structure is wrong; mobile should collapse Stats into a sub-tab under Status.
- **G7:** Mobile always shows the Channel tab in the tab strip regardless of session mode. PWA hides the Channel tab unless the session is in `channel` or `acp` mode (i.e., the session uses MCP channel bridge). Mobile shows it unconditionally.

---

### Session detail — Stats tab

**PWA (`renderSessionStatsInner()` — alpha.32 section cards):**

Stats are rendered via `/api/observer/envelopes` into sectioned cards:

| Card | Fields |
|------|--------|
| Host (always) | CPU % (donut chart) · RSS (fmtBytes) · Threads · FDs · Net ↓/↑ (bps) · PID(s) + child count · sparkline for CPU history · sparkline for RSS history |
| Container (conditional: `env.container_id`) | Container ID (first 12) · Image · Runtime |
| Compute Node (conditional: `sess.compute_node_ref`) | Name · GPU % · GPU Mem · "Open Compute Node →" link |
| LLM (conditional: `sess.llm_ref`) | LLM ref name · "Token rate / latency coming POST v7.0" placeholder · "Open LLM →" link |

Fallback: if no host envelope, shows backend family name + "No process envelope yet" hint text.  
Data source: `GET /api/observer/envelopes` (not `/api/stats` — migrated at alpha.32).  
Poll interval: 5 s while tab is open.

**Mobile (`SessionStatsPanel.kt`):**

| Card | Fields |
|------|--------|
| Host (always) | CPU % (CircularProgressIndicator) · RSS · Threads (if > 0) · FDs (if > 0) · Net ↓/↑ (if > 0) · sparkline for CPU · sparkline for RSS |
| Container (conditional: `envelope.container != null`) | Container ID (first 12) · Image · Runtime |
| Compute Node (conditional: `session.computeNodeRef`) | Name · GPU % · GPU Mem · "Open Compute Node →" tap target |
| LLM (conditional: `session.llmRef`) | LLM ref · Backend family · "Open LLM →" tap target · placeholder "Token rate / latency coming" |

PWA fields present in mobile: CPU %, RSS, Threads, FDs, Net, Container, ComputeNode, LLM — **all match**.

**Gaps:**
- **G8 (Medium):** PWA stats panel pulls data from `GET /api/observer/envelopes`. Mobile's `SessionStatsViewModel` pulls from `GET /api/stats` (the older endpoint). The PWA migrated to `observer/envelopes` at alpha.32. Mobile may be missing newer envelope fields or hitting a different code path.
- **G9 (Medium):** PWA's Host card includes **PID(s) + child-process count** (`root_pid + (n+1)` display). Mobile `StatEnvelopeDto` has no `rootPid`/`pids` fields rendered in `SessionStatsPanel.kt`.

---

### Session detail — Status tab

**PWA (`renderSessionStatusBoardInner()`):**

Cards rendered from `GET /api/sessions/{id}/status`:

| Card | PWA content |
|------|-------------|
| Hook health pill | `●` dot (green=alive / amber=stale / grey=missing) + label + clickable re-fetch + "Docs ↗" link when stale/missing |
| Current focus | `current_focus.task` text · last event type · last tool used · timestamp · idle-since warning (amber) when `board.idle_since` is set |
| Sprint / PRD tree | Raw JSON `<pre>` if `board.sprint` present |
| Tests | pass count · fail count · skip count (colored) |
| Git | branch name · dirty flag (amber) |

The focus card in PWA renders: **event type + tool name + timestamp** in a subtitle line under the focus text.  
The Sprint card shows **raw JSON** (entire sprint object in a scrollable pre).

**Mobile (`SessionStatusPanel.kt` → `SessionStatusBoardDto`):**

| Card | Mobile content |
|------|----------------|
| Hook health pill | `●` dot (green=alive / amber=stale / grey=missing) + label — no clickable re-fetch, no "Docs" link |
| Focus | `board.currentFocus` string only — no event type, no tool, no timestamp, no idle-since |
| Sprint | `sprint.name` + `sprint.progress` string (not raw JSON) |
| Tests | passing · failing · total (StatChip row) |
| Git | branch · uncommitted · ahead |

**Gaps:**
- **G10 (High):** Focus card in mobile is missing: last-event **type**, last **tool** used, last-event **timestamp**, and **idle-since** indicator. PWA shows all four in the subtitle line under the focus text. The `SessionStatusBoardDto` does not carry `lastEvent.ts`, `lastEvent.event`, or `lastEvent.tool` — only `lastEvent: String? = null` (mapped from the server's `last_event` as a plain string, which loses sub-fields).
- **G11 (Medium):** Sprint card in mobile shows only `name` + `progress` fields. PWA shows the entire sprint object as pretty-printed JSON, which surfaces all sprint sub-fields (phase, tasks, etc.) without needing a schema change.
- **G12 (Medium):** Hook health pill is not clickable in mobile — PWA pill is `onclick="event.stopPropagation();renderSessionStatusBoard(...)"`  to re-fetch immediately on click. Mobile `HookHealthPill` is a static Row with no click handler.
- **G13 (Low):** PWA hook health pill shows a "Docs ↗" link when hooks are stale/missing (links to the Claude hooks howto). Mobile shows only a text label.
- **G14 (Medium):** Mobile `CouncilConfigDto` is missing `llm_ref`, `max_parallel`, and `draft_retention_days` fields. The PWA's Council subsystem config block reads and writes all three, but the DTO only has `commFirehose` and `spawnRealSessions`.

---

### Alert dock behavior

**PWA (`window._alertDock`, `renderAlertDock()`, `toggleAlertDock()`):**

```js
window._alertDock = window._alertDock || {
  alerts: [],
  muted: false,
  expanded: false,   // ← default collapsed
  el: null,
};
```

Guard in `renderAlertDock()`:
```js
if (!dock.expanded) {
  // Only render the pill/badge, never the full panel
  return;
}
```

The dock panel is **never auto-opened**. Only the header pill count updates automatically. The full panel appears only when the user explicitly calls `toggleAlertDock()` (clicking the pill). This was fixed by commit `22de7657` (BL300).

**Mobile (`AlertDockOverlay.kt`):**

```kotlin
var expanded by remember { mutableStateOf(false) }
```

Initial state is `false` (collapsed) — **correct**. The expand button is the chevron `IconButton` in the header row. There is no auto-expand path.

**Gaps:**
- None detected for this specific item — **both implementations default to collapsed**. The user's bug report ("alert dock auto-opens") may have been the pre-BL300 behavior; current code is correct in both.
- **G15 (Low):** The alert dock in mobile is positioned at the **top-right** of whatever screen it overlays via `Alignment.End + top=8.dp`. PWA alert dock anchors to the bottom of the viewport. Position choice is a layout difference but not a behavioral one.

---

### Alerts page (3-tab design)

**PWA (`renderAlertsView()`):**
- **3 tabs**: Active (N) · Historical (N) · System (N)
- Top bar: count · category chips (all / 🟡 prompts / 🔴 errors / 🟠 warn / ⚪ info) + search box
- Active tab: per-session sub-tabs (one tab per active session); each session section shows expanded alerts
- Historical tab: all inactive-session groups collapsed
- System tab: system-source alerts (no session)
- Quick-reply dropdown on first alert of `waiting_input` sessions
- Per-tab filter persistence via `localStorage`

**Mobile (`AlertsScreen.kt`):**
- **3 tabs**: Active · Historical · System — matches PWA
- Top bar: chip filters (all/prompts/errors/warn/info) + sort toggle + search + dismiss-all — matches PWA
- Active groups expand by default; others collapse — matches PWA
- Quick-reply dropdown on `waiting_input` sessions — matches PWA

**Gaps:**
- **G16 (Medium):** PWA Active tab has **per-session sub-tabs** when multiple active sessions have alerts (one tab button per active session, only first shown). Mobile Active tab shows all active-session groups in a single scrollable list with no sub-tab switching. Minor UX divergence for multi-session scenarios.

---

### LLM page (Settings → Compute tab)

**PWA LLM edit panel (`openLLMEditPanel()`, alpha.41 + alpha.49):**

The LLM add/edit form has **kind-aware sections**:

| Section | Fields | Visible when |
|---------|--------|-------------|
| Core | Name · Kind · Compute Node(s) multi-select · Enabled Models table (per-node) · Auto-add toggle · API key ref · Timeout · Tags | Always |
| Session-backend | Binary path · Console width/height · Output mode · Input mode · Auto git init (toggle) · Auto git commit (toggle) | `kind` in session-backend set |
| Claude-specific | Skip permissions (toggle) · Channel mode (toggle) · Auto-accept disclaimer (toggle) · Permission mode · Default effort · Fallback chain | `kind == "claude-code"` |
| Test row | Test model selector + Test button | Always |

All booleans use `toggle-switch` CSS pattern as of alpha.49 (not checkboxes).

**Mobile LLM edit dialog (`LlmRegistryCard.kt` `LlmDialog`):**

| Section | Fields present |
|---------|----------------|
| Core | Name · Kind · Per-node model table · Auto-add toggle · Pretest toggle | 
| Missing | API key ref · Timeout · Tags · all of "Session-backend" section · all of "Claude-specific" section |

Mobile `LlmRegistryEntryDto` does **not** carry `binary`, `console_cols`, `console_rows`, `output_mode`, `input_mode`, `auto_git_init`, `auto_git_commit`, `skip_permissions`, `channel_enabled`, `auto_accept_disclaimer`, `permission_mode`, `default_effort`, or `fallback_chain`. These fields were added in alpha.41.

**Gaps:**
- **G17 (High):** Mobile LLM edit form is missing the entire **Session-backend section** (binary, console dimensions, output/input mode, auto-git) introduced in alpha.41. Fields missing from DTO and UI both.
- **G18 (High):** Mobile LLM edit form is missing the entire **Claude-specific section** (skip permissions, channel enabled, auto-accept disclaimer, permission mode, default effort, fallback chain) introduced in alpha.41. These were moved FROM session config (YAML) INTO the LLM registry — the old session-level knobs no longer work in v7.
- **G19 (Medium):** Mobile LLM edit form is missing **API key ref**, **timeout**, and **tags** fields present in PWA.
- **G20 (Low):** Mobile LLM edit form still uses a `Checkbox` for `pretestEnabled` — PWA uses the `toggle-switch` pattern for all booleans as of alpha.49.

---

### Automata page

**PWA (`renderAutonomousView()`):**
- 2 tabs: **Automata** · **Templates**
- Action buttons: Select ☑ · Filter ⊞ · History ⏱ (hidden when on Templates tab)
- Filter bar: text search + status filter chips (draft/planning/needs_review/approved/running/blocked) + type badges (software/research/operational/personal)
- PRD detail: 4 sub-tabs: **Overview · Stories · Decisions · Scan**

**Mobile (`AutonomousScreen.kt`, `PrdDetailDialog.kt`):**
- 2 tabs: **Automata** · **Templates** — matches PWA
- Action buttons present: Select, Filter, History equivalents
- Filter bar: search + status chips + type badges — matches PWA

PRD detail `PrdDetailDialog.kt`: displays status badge, stories section, spec preview, scan results — but uses a **single scrolling column** with no internal sub-tabs. No `Overview | Stories | Decisions | Scan` tab bar.

**Gaps:**
- **G21 (Medium):** PWA PRD detail has 4 sub-tabs (Overview · Stories · Decisions · Scan). Mobile `PrdDetailDialog` is a single scrolling sheet with all sections collapsed into one vertical scroll. No tab switching.

---

### Council page (Settings → Automata → CouncilCard)

**PWA (`_renderCouncilPanel()`, BL295/BL296 alpha.40):**
- Persona list with: name · role · system_prompt (collapsed `<details>`) · ✎ Edit · 🤖 Re-interview · × Delete buttons
- "+ Add Persona" inline form with AI-assist backend picker + 🤖 wizard launch
- Run form: persona checkboxes · proposal textarea · mode (Quick/Debate) · Run button
- Recent Runs list: mode badge · persona count × round count · shortId · detail button
- Council config block (`<details>`): llm_ref · max_parallel · comm_firehose · draft_retention_days
- Live-watch section (`councilLiveRuns`) for in-progress run monitoring

**Mobile (`CouncilCard.kt`):**
- Persona list with: name · description · builtin badge · Edit · Delete (non-builtin only) — matches PWA
- "+" add persona button — matches PWA
- Run form: persona picker sheet · proposal textarea (MicAttachableTextField) · mode · Run button — matches PWA
- Recent Runs list — matches PWA
- Council config: only `commFirehose` toggle present

**Gaps:**
- **G22 (Medium):** Council config block in mobile is missing `llm_ref`, `max_parallel`, and `draft_retention_days` fields. `CouncilConfigDto` only has `commFirehose` and `spawnRealSessions`. PWA surfaces all four. The InferenceFn wiring (BL295) requires `llm_ref` to be set to dispatch debates through the v7 LLM registry.

---

### Compute / Nodes page (Settings → Compute tab)

**PWA (`loadComputeNodesPanel()`):**
- Each node row: name · `auto` badge (with tooltip) · kind (deprecated kinds flagged ⚠) · address · capacity · operator tags · enable/disable sliding switch · ✏️ Edit · 📡 Detail · × Delete
- Deprecated-kinds migration banner
- "Add ComputeNode" button → panel-modal form

**Mobile (`ComputeNodesCard.kt`):**
- Each node row: name · kind (with deprecated warning) · address · enable toggle · Edit · Delete — matches PWA
- Add node dialog — matches PWA

**Gaps:**
- None significant. Mobile matches the core fields. The PWA has a `📡 Detail` live-monitoring button that opens a live-watch panel; mobile doesn't appear to have this, but it's not critical.

---

### Settings / Profiles page

**PWA Settings tabs:** General · Plugins · Comms · Compute · Automata · About  
(Note: no Monitor/Observer top-level tab in Settings — moved to top-level Observer nav in v6.7.3)

**Mobile Settings tabs:** Monitor · General · Plugins · Comms · Compute · Automata · About

**Gaps:**
- **G1 (restated):** Mobile has Settings → Monitor tab that is a substitute for the PWA's standalone Observer top-level nav tab. Functionality is equivalent but navigation structure differs.

---

## Consolidated gap list

| # | Page | Gap description | Severity |
|---|------|----------------|----------|
| G1 | Nav bar | PWA has Observer as 5th bottom-nav tab (📡); mobile has no Observer nav tab — content buried in Settings → Monitor | High |
| G2 | Header | PWA shows global alert pill in top header on every page (count + dock toggle); mobile has no header pill | High |
| G3 | Header | PWA shows context-sensitive `?` help link per page; mobile has none | Low |
| G4 | Session list | PWA has inline Stop/Restart/Delete buttons on session cards; mobile requires ⋮ menu (BL299 already fixed in PWA) | High |
| G5 | Session list | PWA shows drag-handle glyph (⠿⠿) on each card; mobile uses long-press with no visible handle | Low |
| G6 | Session detail tabs | **Critical:** PWA merged Stats+Status into one "Status" top-level tab (sub-tabs inside); mobile still has 4 separate top-level tabs (Tmux · Channel · Stats · Status) — wrong structure since alpha.36 | Critical |
| G7 | Session detail tabs | Mobile shows Channel tab unconditionally; PWA only shows it when session is in channel/acp mode | Critical |
| G8 | Stats tab | Mobile fetches stats from `/api/stats`; PWA migrated to `/api/observer/envelopes` at alpha.32 — different endpoint, potentially missing envelope fields | High |
| G9 | Stats tab | Mobile stats panel missing PID / child-process-count display in Host card | Medium |
| G10 | Status tab | Focus card missing last-event type, last tool, last-event timestamp, and idle-since indicator | High |
| G11 | Status tab | Sprint card shows only name+progress; PWA shows full raw JSON of sprint object | Medium |
| G12 | Status tab | Hook health pill is not clickable in mobile (PWA re-fetches on click) | Medium |
| G13 | Status tab | No "Docs ↗" link when hooks stale/missing | Low |
| G14 | Council | CouncilConfigDto missing llm_ref, max_parallel, draft_retention_days | Medium |
| G15 | Alert dock | Dock anchors top-right in mobile vs bottom in PWA (layout difference only; default-collapsed is correct in both) | Low |
| G16 | Alerts page | PWA Active tab has per-session sub-tabs when 2+ active sessions have alerts; mobile uses single scroll list | Medium |
| G17 | LLM edit form | **Critical:** entire Session-backend section missing (binary, console dims, output/input mode, auto-git) — alpha.41 fields not in mobile DTO or UI | Critical |
| G18 | LLM edit form | **Critical:** entire Claude-specific section missing (skip-perms, channel-enabled, auto-accept, permission-mode, default-effort, fallback-chain) — alpha.41 fields moved from session YAML to LLM registry; old session knobs no longer work in v7 | Critical |
| G19 | LLM edit form | API key ref, timeout, tags missing from mobile LLM edit form | Medium |
| G20 | LLM edit form | pretestEnabled still uses Checkbox; PWA uses toggle-switch (alpha.49) | Low |
| G21 | Automata PRD detail | PWA PRD detail has 4 sub-tabs (Overview · Stories · Decisions · Scan); mobile is single-scroll sheet | Medium |
| G22 | Council config | llm_ref, max_parallel, draft_retention_days missing from Council config edit | Medium |

Severity: Critical (wrong behavior / data loss risk), High (missing feature), Medium (layout/cosmetic or partial feature), Low (minor)

---

## Alpha.41–50 new features not yet in mobile

| Feature | Alpha | Status in mobile |
|---------|-------|-----------------|
| **Per-LLM session fields** — Binary path, console dims, output/input mode, auto-git, skip-permissions, channel-enabled, auto-accept-disclaimer, permission-mode, default-effort, fallback-chain added to LLM registry (clean move from SessionConfig; v7 breaking change) | alpha.41 | **Missing** — `LlmRegistryEntryDto` does not carry these fields; LLM edit dialog has no kind-aware sections for them. Any session started via a claude-code LLM from the mobile app will use server-side defaults only and cannot be configured. |
| **LLM edit form boolean toggle-switches** — all 6 bool fields converted from checkboxes to PWA-style `toggle-switch` pattern for visual consistency | alpha.49 | **Partial** — mobile uses `Switch` for `enabled` and `Checkbox` for `pretestEnabled`; the 6 new session-backend/claude booleans don't exist yet. The toggle-switch pattern exists in `CouncilCard.kt` but not in LlmRegistryCard. |
| **auto-restart after update + concurrent-update mutex** — `fix(update)` BL299 — updater now auto-restarts daemon after successful install and uses a file lock to prevent two concurrent updates | alpha.50 (fix) | Not applicable to mobile (mobile has its own update flow), but the PWA in-app updater behavior is now: pull → install → auto-restart, which changes what the "Update" action in Settings → About does in the PWA. Mobile's update flow (`Settings → About → "Update"`) needs to be verified against the daemon restart behavior. |

---

## Notes for fix plan

1. **G6 is the highest-priority structural fix:** The tab strip in `SessionDetailScreen.kt` must be reorganized. The top-level "Status" tab should host an internal horizontal sub-tab bar (`Status | Stats`) matching the PWA alpha.36 gate. This requires changes to `SessionDetailScreen.kt` (tab strip), `SessionStatsPanel.kt` (now rendered inside the Status pane), and the `statusMode`/`statsMode` state variables.

2. **G17/G18 are a paired breaking-change fix:** `LlmRegistryEntryDto` must be extended with the alpha.41 fields. The LLM edit dialog needs kind-aware section expansion (session-backend section + claude-code section). Without this, claude-code sessions spawned from mobile cannot have binary path, permission mode, channel mode, or auto-accept configured — they silently inherit (possibly absent) server defaults.

3. **G10 (Status focus card) requires a DTO fix:** `SessionStatusBoardDto.lastEvent` is currently typed as `String?` (just the event name). The PWA's `board.last_event` is an object with `{ts, event, tool, payload}`. The DTO needs a nested `LastEventDto` class and the `FocusCard` composable needs to render the three sub-fields.

4. **G14 / G22 (Council config):** `CouncilConfigDto` needs `llmRef: String? = null`, `maxParallel: Int? = null`, `draftRetentionDays: Int? = null`. The `CouncilCard.kt` config section needs three new fields to match the PWA's config block.
