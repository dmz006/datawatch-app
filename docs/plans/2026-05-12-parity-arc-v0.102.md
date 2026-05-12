# Parity Arc — v0.102.0 → v0.108.0 (Sprints 33–39)

**Status:** Planning  
**Started:** 2026-05-12  
**Objective:** Close all 22 PWA parity gaps identified in `2026-05-12-pwa-parity-audit.md`.  
**PWA baseline:** v7.0.0-alpha.50  
**App baseline:** v0.101.0/179  
**Source spec:** `2026-05-12-pwa-full-spec.md`

---

## Sprint status table

| Sprint | Version | Gaps | Status | Scope |
|--------|---------|------|--------|-------|
| 33 | v0.102.0/180 | G6, G7 | ☐ Planned | Session detail tab restructure |
| 34 | v0.103.0/181 | G17, G18, G19, G20 | ✅ Shipped | LLM edit form alpha.41 overhaul |
| 35 | v0.104.0/182 | G8, G9, G10, G12, G13 | ✅ Shipped | Stats endpoint + Status card fixes |
| 36 | v0.105.0/183 | G11, G14, G21, G22 | ✅ Shipped | Medium structural gaps |
| 37 | v0.106.0/184 | G1, G2 | ✅ Shipped | Observer nav tab + global header alert pill |
| 38 | v0.107.0/185 | G4, G16 | ✅ Shipped | Session list inline actions + Alerts sub-tabs |
| 39 | v0.108.0/186 | G3, G5, G15 | ✅ Shipped | Low-priority cosmetic gaps |

---

## Global rules gate (every sprint must pass all)

- [ ] **Version parity**: `gradle.properties` DATAWATCH_APP_VERSION + CODE == `Version.kt` == `composeApp/build.gradle.kts` == `wear/build.gradle.kts`
- [ ] **Locale parity**: every new string key in all 5 bundles: `values/` (EN), `values-de/`, `values-es/`, `values-fr/`, `values-ja/`
- [ ] **Build clean**: `./gradlew check detekt ktlintCheck` passes with zero new issues
- [ ] **Tests pass**: `./gradlew test` green
- [ ] **Spec alignment**: every changed feature visually verified against `2026-05-12-pwa-full-spec.md`
- [ ] **Backlog updated**: `docs/plans/README.md` shipped rows updated

---

## Sprint 33 — v0.102.0/180: Session detail tab restructure (G6, G7)

**Gaps closed:** G6 (Critical), G7 (Critical)  
**Version:** 0.101.0/179 → 0.102.0/180  
**Touch points:** `SessionDetailScreen.kt`, `SessionStatsPanel.kt`, `SessionStatusPanel.kt`

### G6 — Merge Stats into Status as a sub-tab (Critical)

**PWA behaviour (alpha.36):**
- Top-level tab bar: `Tmux` · [`Channel` — conditional] · `Status`
- Inside "Status" tab: a secondary sub-tab strip: `Status | Stats`
- Default: opens on "Status" sub-tab when the "Status" top-level tab is selected

**Required mobile change:**
1. Remove `Stats` from the top-level `PagerTab` enum in `SessionDetailScreen.kt`
2. The existing 4-tab enum (`Tmux, Channel, Stats, Status`) becomes 3 tabs: `Tmux, Channel, Status`
3. Inside the `Status` tab pane, add a `TabRow` with two tabs: `Status` (renders existing `SessionStatusPanel`) and `Stats` (renders existing `SessionStatsPanel`)
4. Use a nested `HorizontalPager` or simple `when(subTab)` state for the inner switch
5. Default inner sub-tab: `Status` (index 0)

**New locale keys (add to all 5 bundles):**
| Key | EN |
|-----|----|
| `session_detail_status_subtab_status` | "Status" |
| `session_detail_status_subtab_stats` | "Stats" |

### G7 — Channel tab conditional visibility (Critical)

**PWA behaviour:**
- Channel tab shown only when `session.mode == "channel"` or `session.mode == "acp"`
- Hidden for all other session modes (standard, tmux, etc.)

**Required mobile change:**
1. In `SessionDetailScreen.kt`, the tab list construction must filter out `Channel` unless `session.mode == "channel" || session.mode == "acp"`
2. The `SessionDto` already carries `mode: String?` — use it
3. When Channel tab is hidden, the pager must not include that page (page indices shift)

### Rules gate

- [ ] Version = 0.102.0/180 in all 4 files
- [ ] Top-level tab strip shows exactly 2 tabs (no Channel) for non-channel sessions: Tmux · Status
- [ ] Top-level tab strip shows exactly 3 tabs for channel/acp sessions: Tmux · Channel · Status
- [ ] "Status" top-level tab contains a `Status | Stats` sub-tab row
- [ ] Stats panel renders inside the inner Stats sub-tab (not at top level)
- [ ] Status panel renders inside the inner Status sub-tab
- [ ] Default: opening session detail lands on Tmux tab; tapping Status opens Status sub-tab (not Stats)
- [ ] 2 new locale keys in 5 locales
- [ ] Build clean, tests pass

---

## Sprint 34 — v0.103.0/181: LLM edit form alpha.41 overhaul (G17, G18, G19, G20)

**Gaps closed:** G17 (Critical), G18 (Critical), G19 (Medium), G20 (Low)  
**Version:** 0.102.0/180 → 0.103.0/181  
**Touch points:** `LlmRegistryCard.kt`, `LlmRegistryEntryDto.kt`, `TransportClient.kt`, `RestTransport.kt`

### G17 + G18 — Session-backend + Claude-specific fields (Critical)

**PWA form fields to add (alpha.41 — kind-aware sections):**

Session-backend section (visible when kind ∈ session-backend kinds):
| Field | API key | Type | Control |
|-------|---------|------|---------|
| Binary path | `binary` | String | TextField |
| Console width | `console_cols` | Int | NumberField |
| Console height | `console_rows` | Int | NumberField |
| Output mode | `output_mode` | Enum: terminal/log/chat | DropdownMenu |
| Input mode | `input_mode` | Enum: standard/direct | DropdownMenu |
| Auto git init | `auto_git_init` | Boolean | Switch |
| Auto git commit | `auto_git_commit` | Boolean | Switch |

Claude-specific section (visible when kind == "claude-code"):
| Field | API key | Type | Control |
|-------|---------|------|---------|
| Skip permissions | `skip_permissions` | Boolean | Switch |
| Channel mode | `channel_enabled` | Boolean | Switch |
| Auto-accept disclaimer | `auto_accept_disclaimer` | Boolean | Switch |
| Permission mode | `permission_mode` | Enum: default/acceptEdits/bypassPermissions | DropdownMenu |
| Default effort | `default_effort` | Enum: low/normal/high | DropdownMenu |
| Fallback chain | `fallback_chain` | List<String> (LLM names) | MultiSelectChips |

### G19 — API key ref, timeout, tags (Medium)

| Field | API key | Type | Control |
|-------|---------|------|---------|
| API key reference | `api_key_ref` | String | TextField |
| Request timeout | `timeout` | Int (seconds) | NumberField |
| Tags | `tags` | List<String> | ChipInputRow |

### G20 — Checkbox → Switch for pretestEnabled (Low)

Replace `Checkbox(checked = entry.pretestEnabled)` with `Switch` in `LlmRegistryCard.kt`.

### DTO changes

Extend `LlmRegistryEntryDto` (and its update/create variants):
```kotlin
val binary: String? = null,
val consoleCols: Int? = null,
val consoleRows: Int? = null,
val outputMode: String? = null,
val inputMode: String? = null,
val autoGitInit: Boolean? = null,
val autoGitCommit: Boolean? = null,
val skipPermissions: Boolean? = null,
val channelEnabled: Boolean? = null,
val autoAcceptDisclaimer: Boolean? = null,
val permissionMode: String? = null,
val defaultEffort: String? = null,
val fallbackChain: List<String>? = null,
val apiKeyRef: String? = null,
val timeout: Int? = null,
val tags: List<String>? = null,
```

JSON serialization: `@SerialName("binary")`, `@SerialName("console_cols")`, etc.

### New locale keys (add to all 5 bundles)

| Key | EN |
|-----|----|
| `llm_section_session_backend` | "Session backend" |
| `llm_field_binary` | "Binary path" |
| `llm_field_console_cols` | "Console width" |
| `llm_field_console_rows` | "Console height" |
| `llm_field_output_mode` | "Output mode" |
| `llm_field_input_mode` | "Input mode" |
| `llm_field_auto_git_init` | "Auto git init" |
| `llm_field_auto_git_commit` | "Auto git commit" |
| `llm_section_claude` | "Claude Code" |
| `llm_field_skip_permissions` | "Skip permissions" |
| `llm_field_channel_enabled` | "Channel mode" |
| `llm_field_auto_accept` | "Auto-accept disclaimer" |
| `llm_field_permission_mode` | "Permission mode" |
| `llm_field_default_effort` | "Default effort" |
| `llm_field_fallback_chain` | "Fallback chain" |
| `llm_field_api_key_ref` | "API key reference" |
| `llm_field_timeout` | "Request timeout (s)" |
| `llm_field_tags` | "Tags" |

### Rules gate

- [ ] Version = 0.103.0/181 in all 4 files
- [ ] `LlmRegistryEntryDto` carries all 16 new fields; JSON round-trip test covers each
- [ ] LLM edit dialog shows session-backend section only when kind is session-backend type
- [ ] LLM edit dialog shows claude-code section only when kind == "claude-code"
- [ ] All boolean fields use `Switch` (not `Checkbox`)
- [ ] Fallback chain renders as multi-select chip list of known LLM names
- [ ] API key ref, timeout, tags visible in dialog for all kinds
- [ ] 18 new locale keys in 5 locales
- [ ] Build clean, tests pass

---

## Sprint 35 — v0.104.0/182: Stats endpoint + Status card fixes (G8, G9, G10, G12, G13)

**Gaps closed:** G8 (High), G9 (Medium), G10 (High), G12 (Medium), G13 (Low)  
**Version:** 0.103.0/181 → 0.104.0/182  
**Touch points:** `SessionStatsViewModel.kt`, `StatEnvelopeDto.kt`, `SessionStatsPanel.kt`, `SessionStatusPanel.kt`, `SessionStatusBoardDto.kt`

### G8 — Migrate stats from /api/stats to /api/observer/envelopes (High)

**PWA endpoint:** `GET /api/observer/envelopes?session_id={id}`  
**Mobile current endpoint:** `GET /api/stats/{sessionId}` (old)

Changes:
1. Add `TransportClient.getSessionEnvelope(sessionId: String): EnvelopeDto` mapping to `GET /api/observer/envelopes?session_id={id}`
2. Add `EnvelopeDto` with fields matching the observer envelope schema
3. Update `SessionStatsViewModel` to call the new method
4. Poll interval stays 5 s

### G9 — PID / child-process count in Host card (Medium)

PWA renders: `root_pid + (n+1)` where n = child count.  
Add `rootPid: Int?` and `childProcessCount: Int?` to `StatEnvelopeDto`; render in `SessionStatsPanel` Host card as "PID {pid} (+{n} children)".

### G10 — Focus card last-event sub-fields (High)

**PWA renders:** event type · tool name · timestamp as subtitle under focus text, plus idle-since amber warning.

Changes to `SessionStatusBoardDto`:
```kotlin
// Replace: val lastEvent: String? = null
// With:
data class LastEventDto(
    val ts: Long? = null,
    val event: String? = null,
    val tool: String? = null,
)
val lastEvent: LastEventDto? = null
val idleSince: Long? = null   // amber warning when set
```

Update `FocusCard` composable in `SessionStatusPanel.kt`:
- Render subtitle row: `{lastEvent.event} · {lastEvent.tool} · {timeAgo(lastEvent.ts)}`
- Show amber "Idle since {timeAgo(idleSince)}" chip when `idleSince` is set and > 5 min ago

### G12 — Hook health pill clickable re-fetch (Medium)

Make `HookHealthPill` composable in `SessionStatusPanel.kt` accept an `onClick: () -> Unit` lambda.  
Trigger: calls `viewModel.refreshStatus()` (same as the existing status poll).  
Wrap the existing `Row` in a `Modifier.clickable`.

### G13 — "Docs ↗" link when hooks stale/missing (Low)

Add a `TextButton("Docs ↗")` next to the hook health pill when `hookStatus != "alive"`.  
On tap: `LocalUriHandler.current.openUri("https://docs.anthropic.com/en/docs/claude-code/hooks")`.

### New locale keys

| Key | EN |
|-----|----|
| `status_idle_since` | "Idle since {0}" |
| `status_hook_docs_link` | "Docs ↗" |
| `status_event_subtitle` | "{0} · {1}" |

### Rules gate

- [ ] Version = 0.104.0/182 in all 4 files
- [ ] Stats panel calls `GET /api/observer/envelopes` not `/api/stats`; confirmed via MockWebServer test
- [ ] Host card renders PID + child count when `rootPid != null`
- [ ] Focus card subtitle shows event · tool · timestamp
- [ ] Idle-since amber chip appears when `idleSince` is set
- [ ] Hook pill is tappable; tapping triggers `refreshStatus()`
- [ ] "Docs ↗" link appears and opens correct URL when hook is not alive
- [ ] 3 new locale keys in 5 locales
- [ ] Build clean, tests pass

---

## Sprint 36 — v0.105.0/183: Medium structural gaps (G11, G14, G21, G22)

**Gaps closed:** G11 (Medium), G14 (Medium), G21 (Medium), G22 (Medium)  
**Version:** 0.104.0/182 → 0.105.0/183  
**Touch points:** `SessionStatusPanel.kt`, `CouncilConfigDto.kt`, `CouncilCard.kt`, `PrdDetailDialog.kt`

### G11 — Sprint card shows full JSON (Medium)

PWA shows entire sprint object as formatted JSON in a scrollable `<pre>`.  
Mobile: replace the `name + progress` display with a `SelectionContainer { Text(json.encodeToString(sprint)) }` inside a vertically-scrollable box.  
`sprint` field in `SessionStatusBoardDto` should be typed as `JsonObject?` (using kotlinx-serialization) rather than a parsed `SprintStatusDto`, or keep `SprintStatusDto` but add a `rawJson: String?` field.

### G14 + G22 — Council config missing fields (Medium)

Extend `CouncilConfigDto`:
```kotlin
val llmRef: String? = null,           // @SerialName("llm_ref")
val maxParallel: Int? = null,          // @SerialName("max_parallel")
val draftRetentionDays: Int? = null,   // @SerialName("draft_retention_days")
// existing:
val commFirehose: Boolean? = null,
val spawnRealSessions: Boolean? = null,
```

Add to `CouncilCard.kt` config section:
- LLM ref: `DropdownMenu` populated from known LLM names
- Max parallel: `NumberField` (1–10)
- Draft retention: `NumberField` (days)

### G21 — PRD detail 4 sub-tabs (Medium)

Replace the single-scroll `PrdDetailDialog` with a tabbed layout:

| Tab | Content |
|-----|---------|
| Overview | Status badge · created/updated · description · tags |
| Stories | Stories list (existing) |
| Decisions | Decisions log (if server returns `decisions` field) |
| Scan | Scan results (existing) |

Add `TabRow` at the top of the dialog with 4 tabs. Existing content maps to Overview + Stories + Scan.

### New locale keys

| Key | EN |
|-----|----|
| `council_config_llm_ref` | "LLM reference" |
| `council_config_max_parallel` | "Max parallel" |
| `council_config_draft_retention` | "Draft retention (days)" |
| `prd_tab_overview` | "Overview" |
| `prd_tab_stories` | "Stories" |
| `prd_tab_decisions` | "Decisions" |
| `prd_tab_scan` | "Scan" |

### Rules gate

- [ ] Version = 0.105.0/183 in all 4 files
- [ ] Sprint card shows full JSON representation of sprint object
- [ ] Council config block shows llm_ref, max_parallel, draft_retention_days
- [ ] PRD detail has 4 tabs (Overview / Stories / Decisions / Scan)
- [ ] 7 new locale keys in 5 locales
- [ ] Build clean, tests pass

---

## Sprint 37 — v0.106.0/184: Observer nav tab + global header alert pill (G1, G2)

**Gaps closed:** G1 (High), G2 (High)  
**Version:** 0.105.0/183 → 0.106.0/184  
**Touch points:** `BottomNavBar.kt`, `AppRoot.kt`, `ObserverScreen.kt` (new or move from Settings)

### G1 — Observer as top-level nav tab (High)

**PWA:** Observer is the 4th bottom-nav item (📡) between Alerts and Settings.

Changes:
1. Add `Observer` to the `BottomNavItem` enum between `Alerts` and `Settings`
2. Move the monitoring content currently in `Settings → Monitor tab` to a new `ObserverScreen.kt` top-level destination
3. Update `AppRoot.kt` nav graph to route to `ObserverScreen`
4. Icon: `Icons.Default.Radar` or `Icons.Default.Sensors` (📡 equivalent)
5. Settings → Monitor tab: either remove or keep as a shortcut alias pointing to the Observer screen

**BottomNavBar order:** Sessions · Automata (conditional) · Alerts · Observer · Settings

### G2 — Global header alert pill (High)

**PWA:** A pill in the top header on every page shows pending-alert count and opens the dock on tap.

Changes:
1. In the top `TopAppBar` in `AppRoot.kt` (or per-screen app bars), add a `BadgedBox` icon button showing alert count
2. Tapping opens the `AlertDockOverlay` (set `expanded = true` on the shared dock state)
3. Only visible when `alertCount > 0` (matches PWA `if (alertCount === 0) return` guard)
4. Color: amber when `needsInput > 0`, red when `errors > 0`, else teal

### New locale keys

| Key | EN |
|-----|----|
| `nav_observer` | "Observer" |
| `header_alerts_pill_cd` | "{0} alerts" |

### Rules gate

- [ ] Version = 0.106.0/184 in all 4 files
- [ ] Observer appears as 4th bottom-nav tab (between Alerts and Settings)
- [ ] Tapping Observer navigates to observer/monitoring content (not Settings)
- [ ] Header pill appears on all pages when alertCount > 0
- [ ] Tapping header pill opens alert dock
- [ ] Pill hidden when alertCount == 0
- [ ] 2 new locale keys in 5 locales
- [ ] Build clean, tests pass

---

## Sprint 38 — v0.107.0/185: Session list inline actions + Alerts per-session sub-tabs (G4, G16)

**Gaps closed:** G4 (High), G16 (Medium)  
**Version:** 0.106.0/184 → 0.107.0/185  
**Touch points:** `SessionCard.kt` (or inline in `SessionsScreen.kt`), `AlertsScreen.kt`

### G4 — Inline Stop / Restart / Delete buttons on session cards (High)

**PWA layout:**
- Active session card header: `■ Stop` button (bordered, red tint) visible inline
- Done session card header: `↺ Restart` + `🗑 Delete` buttons visible inline

Changes:
1. In `SessionCard.kt`, add inline `OutlinedButton("Stop")` below the state pill, visible when `session.state == "running" || session.state == "waiting"`
2. For done/killed sessions, show `OutlinedButton("Restart")` and `OutlinedButton("Delete")`
3. The existing ⋮ menu can remain for other actions (rename, watch, quick commands)
4. Confirm dialog before Delete (existing `deleteSession` flow)

### G16 — Alerts Active tab per-session sub-tabs (Medium)

**PWA:** When 2+ active sessions have alerts, the Active tab shows a row of session-name tab buttons; only selected session's alerts shown.

Changes:
1. In `AlertsScreen.kt` Active tab, when `activeSessionAlerts.size > 1`, render a secondary `ScrollableTabRow` of session short-ids above the alert list
2. Each secondary tab shows that session's alerts only
3. When only 1 session has alerts (or 0), show all in a flat list (existing behavior)

### New locale keys

None required (button labels already exist: `session_stop`, `session_restart`, `session_delete`).

### Rules gate

- [ ] Version = 0.107.0/185 in all 4 files
- [ ] Active sessions show inline Stop button; tapping calls stopSession
- [ ] Done/killed sessions show inline Restart + Delete buttons
- [ ] Delete still requires confirmation dialog
- [ ] Alerts Active tab shows per-session sub-tabs when 2+ sessions have alerts
- [ ] Tapping a session sub-tab filters alert list to that session only
- [ ] Build clean, tests pass

---

## Sprint 39 — v0.108.0/186: Low-priority cosmetic gaps (G3, G5, G15)

**Gaps closed:** G3 (Low), G5 (Low), G15 (Low)  
**Version:** 0.107.0/185 → 0.108.0/186  
**Touch points:** Various — small targeted changes

### G3 — Context-sensitive help link in header (Low)

PWA shows a `?` link in the header whose destination changes per page.  
Add a `helpUrl: String?` parameter to each screen's `TopAppBar`. When non-null, show an `IconButton(Icons.Default.HelpOutline)` that calls `uriHandler.openUri(helpUrl)`.  
Priority pages: Sessions (Claude Code hooks docs), LLM (registry docs), Council (council docs).

### G5 — Visible drag-handle glyph on session cards (Low)

PWA shows ⠿⠿ (braille drag handle) on each card.  
Add `Icon(Icons.Default.DragHandle)` as a leading icon on `SessionCard` that is only visible in reorder mode (long-press activates).

### G15 — Alert dock anchor position (Low)

PWA docks at the bottom of the viewport. Mobile docks top-right.  
Evaluate moving `AlertDockOverlay` to `Alignment.BottomEnd` to match PWA. Low risk, single-line change.

### Rules gate

- [ ] Version = 0.108.0/186 in all 4 files
- [ ] Help icon visible on Sessions, LLM, Council screens when `helpUrl` is set
- [ ] Drag handle glyph visible on session cards in reorder mode
- [ ] Alert dock anchored to bottom-right (not top-right)
- [ ] Build clean, tests pass

---

## Pre-v1.0 release gates (unchanged from prior arc)

All three gates must pass before cutting the v1.0 GH release tag:

### Gate 1 — Device test pass
Single `adb install -r` pass on both devices after v0.108.0 builds:
- Phone: `100.64.0.1:41633` — `composeApp-publicTrack-release.apk`
- Watch: last known `192.168.15.52:45021` (port changes) — `wear-release.apk`

### Gate 2 — ViewModel tests (pre-existing debt)
Write unit tests for: Sprint 25 (Stats), Sprint 27 (Ollama), Sprint 28 (UnifiedPush), Sprint 30 (LLM nodes), Sprint 31 (Council persona), Sprint 32 (WearSync sessions).  
These were deferred from the prior arc and must be written before v1.0 tagging.

### Gate 3 — Parity spec check
Every row in `docs/plans/2026-05-12-pwa-parity-audit.md` gap table must be marked resolved.  
Re-verify against `docs/plans/2026-05-12-pwa-full-spec.md` for the 22 gaps.

---

## Gap index quick-reference

| Gap | Sprint | Severity | One-line description |
|-----|--------|----------|----------------------|
| G1 | 37 | High | No Observer nav tab |
| G2 | 37 | High | No global header alert pill |
| G3 | 39 | Low | No context help link in header |
| G4 | 38 | High | No inline Stop/Restart/Delete on session cards |
| G5 | 39 | Low | No drag-handle glyph on cards |
| G6 | 33 | Critical | Stats+Status should be sub-tabs, not separate top-level tabs |
| G7 | 33 | Critical | Channel tab always visible; should be conditional on session mode |
| G8 | 35 | High | Stats fetches from /api/stats; should be /api/observer/envelopes |
| G9 | 35 | Medium | PID + child-count missing from Host stats card |
| G10 | 35 | High | Focus card missing last-event type/tool/timestamp/idle-since |
| G11 | 36 | Medium | Sprint card shows name+progress only; should show full JSON |
| G12 | 35 | Medium | Hook health pill not clickable |
| G13 | 35 | Low | No "Docs ↗" link when hooks stale/missing |
| G14 | 36 | Medium | CouncilConfigDto missing llm_ref, max_parallel, draft_retention_days |
| G15 | 39 | Low | Alert dock anchored top-right; PWA anchors bottom |
| G16 | 38 | Medium | Alerts Active tab: no per-session sub-tabs when 2+ sessions |
| G17 | 34 | Critical | LLM edit missing session-backend section (alpha.41 fields) |
| G18 | 34 | Critical | LLM edit missing claude-code section (alpha.41 fields) |
| G19 | 34 | Medium | LLM edit missing api_key_ref, timeout, tags |
| G20 | 34 | Low | LLM pretestEnabled uses Checkbox not Switch |
| G21 | 36 | Medium | PRD detail single-scroll; should be 4 sub-tabs |
| G22 | 36 | Medium | Council config missing llm_ref, max_parallel, draft_retention_days |
