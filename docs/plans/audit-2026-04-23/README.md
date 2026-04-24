# 2026-04-23 PWA parity master inventory

**Goal.** Close every observable parity gap between the datawatch PWA (live at `https://localhost:8443`, source at `/home/dmz/workspace/datawatch`) and the Android phone client (v0.34.6 on main). Wear + Auto + iOS are out of scope here.

**How this doc is organised.**
1. Executive summary — disposition-at-a-glance + release batches
2. Sibling inventories (source-of-truth deep dives — do not duplicate their content here):
   - `pwa-inventory.md` — every PWA screen, every field, every endpoint (1629 lines)
   - `android-inventory.md` — every Android screen, current state (673 lines)
   - `server-contract.md` — authoritative endpoint + config schema table (below)
   - `audit-validation.md` — dispositions of every claim in `2026-04-22-pwa-parity-audit.md`
3. Gap matrix — one row per gap, with fix + owner file + priority + batch
4. Visual / dimensional polish — the "buttons larger than their text" class of issues
5. Execution plan — release-sized batches in priority order
6. Non-gaps — items flagged by the earlier audit or triggered by reminders that are already parity or explicitly WONTFIX

**Ground-truth sources used.** PWA source at `/home/dmz/workspace/datawatch/internal/server/web/app.js`, Go server handlers at `/home/dmz/workspace/datawatch/internal/server/api.go` + `server.go`, live REST responses from `curl -sk https://localhost:8443/api/*`, 40+ Playwright screenshots at `/home/dmz/workspace/pwa-audit/out/`, and the Android codebase at `/home/dmz/workspace/datawatch-app/composeApp/src/androidMain/` + `shared/src/commonMain/`. Every row in the gap matrix is backed by at least one of those, not opinion.

---

## 0. Executive summary

**Total screens in scope.** 5 top-level PWA views (Sessions, New, Alerts, Settings, SessionDetail) + 5 Settings tabs (Monitor, General, Comms, LLM, About) + ~23 modal dialogs (12 LLM Configure, 10 channel Configure, plus Rename / Delete / StateOverride / Timeline / Schedule / Response / Commands / FilePicker / ConfirmKill / ConfirmDelete).

**Disposition breakdown (see Gap matrix §3 for row-level detail).**

| Severity | Count | Sample |
|---|---|---|
| **P0 — user-flagged, app broken** (shipped in v0.34.6 today) | 3 | kill/state/rename/restart/delete 404s, delete UI visibility, chat-mode blank |
| **P1 — user-flagged, still broken on v0.34.6** | 5 | drag-drop reorder, Alerts Active/Inactive tabs, tmux IME padding, new-session backend picker retest, settings input-larger-than-text |
| **P2 — structural parity gaps** | 7 | session-detail inline rename, alert quick-reply dropdown, per-session sub-tabs in Alerts, Monitor-tab card coverage (GPU/Network/Plugins/Infra/RTK), Signal QR linking, response viewer sheet parity, schedule input popup parity (bottom-nav / New-tab items dropped 2026-04-24 — PWA adopting Android FAB pattern) |
| **P3 — polish / visual fidelity** | 8 | purple-accent palette alignment, monospace header font, collapsible section chevrons, "(default)" badge on LLM rows, waiting-input-banner tip strings, state-override labels, hostname-prefix positioning, badge border radius |
| **WONTFIX / NON-GAP** | 12 | "language selector" (doesn't exist in PWA), "TLS cert selector" (misread of /api/cert), "in-app alerts panel" (Android already has it), B42 duplicates, etc. |

**Release batches (see §5 for detail).**

- **v0.34.6** — shipped earlier today: P0 release (session mutation contract + delete + chat mode).
- **v0.34.7** — **P1 fix pass.** User-flagged issues that are still broken on today's build. Aim for one tight release.
- **v0.34.8** — **Alerts rebuild.** Active/Inactive tabs + per-session sub-groups + per-alert quick-reply dropdown, matching PWA renderAlertsView.
- **v0.34.9** — **Sessions ergonomics.** Drag-drop reorder + New as a top-nav tab (not FAB) + per-row layout polish.
- **v0.35.0** — **Settings polish + Monitor card completion.** Input-vs-text sizing sweep across ConfigFieldsPanel, chevron affordance, LLM row refinement, Monitor tab's missing cards (GPU / Network / Plugins / Infra / RTK / Ollama detail).
- **v0.35.1** — **Session-detail polish.** Inline header rename, response viewer sheet, schedule popup parity, quick-command dropdown completeness.
- **v0.35.2** — **Signal QR device linking** (BL21 — optional; promote from backlog if demand).

Each release goes through the normal `release:` cadence (Version.kt + gradle.properties bumped together per `feedback_version_parity`).

---

## 1. Sibling inventory docs

| File | What it covers |
|---|---|
| [`pwa-inventory.md`](pwa-inventory.md) | Every PWA screen + dialog, every field with config key + endpoint + app.js line, every button + onclick, every data source. 1629 lines, organised per-screen. Generated 2026-04-23 from `/home/dmz/workspace/datawatch/internal/server/web/app.js` and live `/api/*` responses. |
| [`android-inventory.md`](android-inventory.md) | Every Android screen, field/binding table, VM function → endpoint table, dialogs, data sources. 673 lines. Generated 2026-04-23 from `composeApp/src/androidMain/**`. Caveats: a few endpoint rows describe aspirational REST-style paths (e.g. `DELETE /api/sessions/{id}`) — the *actual* transport is `POST /api/sessions/kill` with `{"id": fullId}`. Use `server-contract.md` as the authoritative endpoint source. |
| [`server-contract.md`](server-contract.md) | Authoritative table of every PWA-used endpoint with method, request body key names, response shape, handler location. |
| [`audit-validation.md`](audit-validation.md) | Each claim in the prior 2026-04-22 audit — CONFIRMED / CLOSED / REDEFINED / WONTFIX with source ref. |
| [`pwa-audit/out/*.png`](/home/dmz/workspace/pwa-audit/out/) | 40+ PWA screenshots (top-level views × 5, Settings tabs × 5, LLM Configure dialogs × 13, channel Configure dialogs × 10, session detail + state override + timeline). |

---

## 2. PWA ↔ Android structural differences (big picture)

### 2.1 Navigation shape

| Surface | PWA | Android (v0.34.6) | Disposition |
|---|---|---|---|
| Top-level views | 5: `sessions`, `new`, `alerts`, `settings`, `session-detail` (app.js:969) | 3 home tabs + 2 full routes: `home/sessions`, `home/alerts`, `home/settings`, `sessions/new`, `sessions/{id}` | **REVERSED 2026-04-24** — Android's FAB + full-screen create is the target UX; PWA migrating to match this weekend. No mobile change. |
| Bottom nav items | 4 (Sessions / New / Alerts / Settings) | 3 (Sessions / Alerts / Settings) + FAB for New | Same — Android FAB pattern wins; PWA adapting. |
| Settings sub-tabs | 5: Monitor / General / Comms / LLM / About (app.js:3090) | 5: Monitor / General / Comms / LLM / About | ✅ Parity. |
| Back button behavior | History API push; popstate returns | Nav controller pop; `BackHandler` in session detail | ✅ Parity. |
| Session detail arrival | `navigate('session-detail', fullId)` — replaces nav with back-button | Full-screen route; top bar has X (close) | ✅ Parity. |

### 2.2 Content surfaces per top-level view

| View | PWA content (ref) | Android content (ref) | Mismatch |
|---|---|---|---|
| Sessions | filter row + backend chips + history toggle + select mode + drag-drop cards + per-card quick-cmd popup | filter row + backend chips + history toggle + reorder mode + bulk select + overflow menu + inline Stop/Restart/Delete buttons | Drag-drop vs reorder-mode button (user-flagged P1); PWA has Stop always visible, Android has Stop ✅ |
| New | full form view: Session name / optional Task / Backend / Profile / Project dir / Resume / Auto git / Start + Restart-previous list | same fields + Server dropdown + Model (ollama/openwebui) + Browse button | Android has strictly MORE fields (multi-server, model picker) — server hint works but may duplicate PWA-Settings flow |
| Alerts | Active(N) / Inactive(N) tabs → per-session sub-tabs → per-session collapsible alert groups with level-colored left border + quick-reply dropdown on first alert | single list of Waiting-state sessions, swipe-to-mute, Schedule reply button | **Major gap** P1 — Android misses tabs, per-session grouping, per-alert quick-reply |
| Settings | 5 tabs as above | 5 tabs as above | Per-tab card-level gaps in §3 |
| SessionDetail | header inline rename + tmux/channel tabs + state-override popup + Stop/Restart/Delete + Timeline + InputRequired banner + Connection banner + Response viewer + Schedule popup + voice transcribe + xterm scroll mode | header (click-to-rename dialog) + tmux/channel tab row + SessionInfoBar w/ state pill + Stop/Restart/Delete + Timeline + ChatTranscriptPanel (0.34.6 new) + ChatEventList + TerminalView + InlineNotices + ReplyComposer | Inline rename (Android uses modal dialog — parity gap); response viewer sheet — verify |

### 2.3 Endpoint contract

See [`server-contract.md`](server-contract.md). Short summary: Android's RestTransport now uses the correct body shape for every session-mutation endpoint post-v0.34.6. The remaining endpoint gaps:

| Endpoint | PWA uses | Android uses | Disposition |
|---|---|---|---|
| `/api/link/start`, `/api/link/stream`, `/api/link/status` | yes — Signal device linking (QR SSE) | **no** | Backlog BL21; P2 if needed |
| `/api/sessions/response` | yes — fetch last response | no (Android caches `lastResponse` from SessionDto) | Probably fine; server already pushes on SessionDto |
| `/api/sessions/reply` | — | yes (legacy; server doesn't expose this) | Dead code in Android; delete in next cleanup |
| `/api/plugins` | yes — plugins card | no | GAP — P3 / P2 depending on user desire |
| `/api/proxy/*` | yes — federation proxy | no | Server-admin surface; likely skip |
| `/api/ollama/models`, `/api/openwebui/models` | yes | yes (via NewSessionScreen) | ✅ Parity |
| `/api/stats/kill-orphans` | yes (admin action) | yes (KillOrphansCard) | ✅ Parity |
| `/api/voice/transcribe` | no (PWA uses Web Speech API) | yes | Android has voice, PWA doesn't. Non-gap; extra feature. |
| `/api/devices/register`, `/api/devices` | no | yes | Mobile-specific push device registration; non-gap. |

---

## 3. Gap matrix

**Legend.**
- **ID** — stable per-gap ID. New IDs G1–Gnn.
- **Severity** — P0 (shipped), P1 (user flagged, still broken), P2 (structural), P3 (polish).
- **Batch** — target release per §5.
- **Owner** — primary source file(s) to edit.

### 3.1 User-flagged items (from `docs/plans/README.md` Open-not-assessed)

| ID | Title | PWA evidence | Android evidence | Fix | Owner | Severity | Batch |
|---|---|---|---|---|---|---|---|
| G1 | ✅ Session `/api/sessions/kill` 404 | app.js:2297 `{id: sess.full_id}`; server api.go:1830 `{id string}` | RestTransport.kt:166 sent `session_id: shortId` | RestTransport + StateOverrideDto key fix + Session.fullId plumbed through every mutation call site | shared/transport/**, composeApp/sessions/** | P0 | v0.34.6 ✅ shipped |
| G2 | ✅ Stop badge on list — same 404 root cause | same | SessionsViewModel.kill used short id | `fullIdFor(sessionId)` helper + all five mutation call sites | SessionsViewModel.kt | P0 | v0.34.6 ✅ shipped |
| G3 | ✅ No delete button after kill | app.js:1384 inline Delete button on terminal-state cards; session detail "Delete" button | SessionsScreen row had Delete only in overflow menu, not inline; SessionDetail had no Delete button | Delete OutlinedButton next to Restart on terminal-state rows + SessionInfoBar Delete + confirm dialog | SessionsScreen.kt, SessionDetailScreen.kt | P0 | v0.34.6 ✅ shipped |
| G4 | ✅ Chat-mode sessions blank | app.js:1644 `sess.output_mode === 'chat'` → renders `#chatArea`; 556 handleChatMessage | Android had no output_mode field; chat_message WS frame unhandled | SessionEvent.ChatMessage + EventMapper + ChatTranscriptPanel + 4.sqm migration + SessionDetailScreen branch | EventMapper.kt, SessionEvent.kt, ChatTranscriptPanel.kt (new), SessionDetailScreen.kt, profile.sq, 4.sqm | P0 | v0.34.6 ✅ shipped |
| G5 | **New-session LLM picker "doesn't load configured list"** — retest needed | app.js:3370-ish renderNewSessionView + `/api/backends` + `backends.<name>.enabled` | v0.34.5 added `backendsForNewSession` cross-ref | Sideload today's build and exercise; if still reported broken, trace the observe / fetch order | NewSessionScreen.kt | P1 | v0.34.7 |
| ✅ G6 | Sessions drag-drop reorder — **shipped v0.34.9** | `detectDragGesturesAfterLongPress` on each row; `vm.moveSessionByOffset(id, shift)` applies delta on release. Reorder icon removed from top bar. Custom-order persistence across app restarts still TBD (follow-up). | | | | | |
| G7 | **Alerts rebuild — Active/Inactive tabs + per-session sub-groups + collapsible history** | app.js:5552-5623 renderAlertsView groups `activeTabs` / `inactiveTabs`, per-session header with state pill + count, collapsible via `settings-section-toggle`, per-alert card with colored left border + level + title + body + quick-reply dropdown (line 5580) | AlertsScreen shows LazyColumn of waiting-state session rows, swipe-to-mute, Schedule button — no tabs, no per-session grouping, no quick-reply | Complete redesign: 2 tabs (Active/Inactive), expandable per-session cards, each alert row with level badge + colored left border + quick-reply select (for waiting-input only). Transport needs `listAlerts()` if not already; AlertsRepository needs grouping. | AlertsScreen.kt, AlertsViewModel.kt, maybe new AlertsRepository + `/api/alerts` transport | P1 | v0.34.8 |
| G8 | **tmux input hidden behind keyboard** | PWA uses browser IME (not applicable) | SessionDetailScreen has `.imePadding()` at line 245 but user reports input still hidden | Check the bottom-inset stack: Scaffold consumes IME inset once, `imePadding()` adds it once more = may double-count, or the composer is nested inside a `LazyColumn` that's not IME-aware. Instrument with `WindowInsets.ime.asPaddingValues()` direct in composer, ensure only ONE layer applies it. Likely fix: move `imePadding()` from Column to `ReplyComposer` itself. | SessionDetailScreen.kt, ReplyComposer (inside same file), terminal view wrapper | P1 | v0.34.7 |
| G9 | **"Buttons + input windows way larger than their text" (Settings)** | PWA uses 12-13px fonts + ~32-36px row height; padding ~4-6px vertical on inputs | Android ConfigFieldsPanel uses M3 OutlinedTextField with default min-height 56dp, dropdown sizes default, making labels look cramped relative to giant inputs | Audit ConfigFieldsPanel: set `textStyle = MaterialTheme.typography.bodyMedium`, tighten vertical padding, match row gap ~8dp. Repeat for LlmConfigCard / ChannelsCard / ServersCard. | ConfigFieldsPanel.kt, LlmConfigCard.kt, ChannelsCard.kt | P1 | v0.35.0 (needs focused pass) |

### 3.2 Alerts screen — detail rows (roll-up under G7)

| Subitem | PWA | Android |
|---|---|---|
| Tabs | `Active (N)` / `Inactive (N)`, default = 'active' if any (app.js:5627) | none |
| Per-session grouping | Each alert grouped under a `.alert-session-group` header with session name + state badge + alert count (app.js:5615–5622) | each row is a session, no grouping by alert |
| Collapsible inactive | Inactive groups default-collapsed with chevron ▶ (app.js:5604–5613) | n/a |
| Alert row | `.card.alert-card` with `border-left: 3px solid {levelColor}`; level text + timestamp + title + body + optional quick-reply dropdown | row is a session, not per-alert |
| Quick-reply on first alert | `<select class="quick-cmd-select">` with saved commands (app.js:5573-5580) — only shown on first (latest) alert of a waiting_input session | none (Schedule button exists, not quick-reply) |
| System alerts | `__system__` pseudo-group for non-session alerts (app.js:5555) | none |
| Data source | `GET /api/alerts` + `state.sessions` (classification logic) | derived from session list (Waiting && !muted) |

### 3.3 Structural UI differences

| ID | Title | PWA evidence | Android evidence | Fix | Owner | Severity | Batch |
|---|---|---|---|---|---|---|---|
| ~~G10~~ | ~~New Session tab in bottom nav~~ | 4-tab bottom nav (app.js renderBottomNav) | 3-tab + FAB | **REVERSED 2026-04-24.** User confirmed Android's FAB + full-screen create pattern is the desired UX; parent PWA will migrate to this pattern, not the other way round. Upstream-tracked ahead of this weekend's PWA work. Android stays as-is. | — | WONTFIX | — |
| G11 | Session-detail inline header rename | app.js:1672 click `session-detail > h2` → `startHeaderRename(sessionId)` inline text input | Android opens RenameDialog modal | Convert title Text to editable: click → switch to TextField in-place; Enter/blur → commit | SessionDetailScreen.kt | P2 | v0.35.1 |
| G12 | Session-detail state override — menu shape | app.js:2206 `showStateOverride` opens dropdown menu directly under state pill | Android opens StateOverrideDialog (AlertDialog) | Replace AlertDialog with DropdownMenu anchored to the pill | SessionDetailScreen.kt | P3 | v0.35.1 |
| G13 | Session-detail Response button (📄) on active sessions | app.js quick panel shows "💾 Response" button when session active (line ~1685) | Android has Response icon on list row but not on detail screen | Add response button to SessionInfoBar when lastResponse present | SessionDetailScreen.kt | P2 | v0.35.1 |
| G14 | Schedule-input popup parity | app.js:1667 schedule button under composer → popup with cron picker | Android ScheduleDialog exists | Verify field parity (task seed, cron presets, enabled toggle) | SessionDetailScreen.kt | P3 | v0.35.1 |
| G15 | Font-size controls on terminal | app.js:1640 Terminal toolbar has `A+/A-` buttons to change xterm font | Android TerminalToolbar has font size but verify control UI parity | Add zoom-in/zoom-out buttons to TerminalToolbar matching PWA | TerminalToolbar.kt | P3 | v0.35.1 |
| G16 | Terminal "Fit" button | app.js:1640 `termFitToWidth()` | Android has fit-to-width behavior in TerminalView but no explicit button | Add Fit button to toolbar | TerminalToolbar.kt | P3 | v0.35.1 |
| G17 | Terminal scroll mode button (Ctrl-b `[`) | app.js:1642 `toggleScrollMode()` | Android has tmux commands sheet with Ctrl-b entry | Verify scroll-mode is exposed | TerminalToolbar.kt, QuickCommandsSheet | P3 | v0.35.1 |
| G18 | Quick-command select palette on sessions-list row | app.js:1460 `<select class="quick-cmd-select">` with system/saved/custom optgroups directly inline on waiting row | Android opens QuickCommandsSheet (bottom sheet) from button | Acceptable UX difference — Android's sheet is richer. Mark parity-equivalent. | — | ✅ non-gap | — |

### 3.4 Settings per-tab gaps

#### Monitor tab

PWA cards (from pwa-inventory.md Screen 5): System Statistics (CPU + Memory + Disk + Swap grid), Network (System), Daemon (memory/goroutines/FDs/uptime), Infrastructure (HTTP/HTTPS/MCP-SSE/Tmux), RTK Token Savings (version, hooks, saved, avg, commands), Episodic Memory (status/backend/embedder/encryption/counts/DB size), Ollama Server (host/status/models/disk/running/VRAM), Session Statistics (ring progress), eBPF Degraded banner, Plugins (list), Memory browser (search + top N list), Schedules (pending + firing times), Daemon log (streaming).

| ID | Card | PWA | Android | Fix | Owner | Severity | Batch |
|---|---|---|---|---|---|---|---|
| G19 | System Statistics grid | CPU load + Memory + Disk + Swap (with progress bars) | StatsScreenContent shows all 4 | Verify bar styling/color thresholds match | StatsScreen.kt | P3 | v0.35.0 |
| G20 | Network (System) card | Download / Upload counters | not present on Monitor | Add to StatsScreen | StatsScreen.kt | P3 | v0.35.0 |
| G21 | Daemon metadata card | memory RSS, goroutines, FDs, uptime | present via AboutCard but not on Monitor | Add compact card to Monitor tab | StatsScreen.kt | P3 | v0.35.0 |
| G22 | Infrastructure card | HTTP / HTTPS / MCP SSE / Tmux counts | not surfaced | Add compact card | StatsScreen.kt | P3 | v0.35.0 |
| G23 | RTK Token Savings card | version / hooks / tokens saved / avg savings / commands | not surfaced | Add card reading `/api/rtk/version` | StatsScreen.kt (new RTK row or card) | P3 | v0.35.0 |
| G24 | Episodic Memory card | status/backend/embedder/encryption/counts/size | MemoryCard exists on Monitor tab — verify field coverage | Fill missing fields | MemoryCard.kt | P3 | v0.35.0 |
| G25 | Ollama Server card | host/status/models/disk/running/VRAM | not on Monitor (shows on LLM tab maybe) | Add compact card sourced from `/api/ollama/stats` | StatsScreen.kt | P3 | v0.35.0 |
| G26 | eBPF Degraded banner | inline amber banner when stats?.v2.ebpf.status!='ok' | not surfaced | Add banner widget keyed on stats shape | StatsScreen.kt | P3 | v0.35.0 |
| G27 | Plugins list | `GET /api/plugins` | not implemented | Add PluginsCard on Monitor tab | new PluginsCard.kt + transport | P3 | v0.35.0 (optional) |
| G28 | Session Statistics ring | animated ring 1/10 max | present (in SessionStats/AboutCard) | Verify on Monitor tab | StatsScreen.kt | P3 | v0.35.0 |

#### General tab

PWA sections (app.js renderSettingsView 'general' branch): Datawatch (log level, auto-restart, default LLM backend), Auto-Update (enabled, schedule, time), Session (max, input idle timeout, tail lines, alert context lines, default project dir, file browser root), Project Profiles, Cluster Profiles, Notifications (push/ntfy), RTK, Pipelines, Autonomous, Plugins, Orchestrator, Whisper.

| ID | Section | PWA | Android | Fix | Owner | Severity | Batch |
|---|---|---|---|---|---|---|---|
| G29 | Datawatch (log_level / auto_restart / default LLM) | 3 fields, collapsible | ConfigFieldsPanel(Datawatch) | ✅ Parity; verify field labels match exactly | ConfigFieldSchemas.kt | ✅ | — |
| G30 | Auto-Update | enabled / schedule / time | ConfigFieldsPanel(AutoUpdate) | ✅ Parity | ConfigFieldSchemas.kt | ✅ | — |
| G31 | Session section | max / idle / tail / alert_ctx / default_proj_dir + Browse / file_browser_root | ConfigFieldsPanel(Session) | Verify Browse button wired to file picker | ConfigFieldSchemas.kt | P3 | v0.35.0 |
| G32 | Project Profiles | collapsible list + CRUD | KindProfilesCard(project) | ✅ Parity | — | ✅ | — |
| G33 | Cluster Profiles | same | KindProfilesCard(cluster) | ✅ Parity | — | ✅ | — |
| G34 | Notifications | push / ntfy settings | NotificationsCard | ✅ Parity | — | ✅ | — |
| G35 | RTK / Pipelines / Autonomous / Plugins / Orchestrator / Whisper | all collapsible | ConfigFieldsPanel(each) | Verify schema coverage against PWA; these are operator-facing | ConfigFieldSchemas.kt | P3 | v0.35.0 |

#### Comms tab

PWA sections: Authentication (browser token + server bearer + MCP SSE bearer), Servers (status + host + profiles), Web Server (enabled / bind / port / TLS / TLS port / TLS auto-generate cert), MCP Server, Proxy Resilience, Signal device linking (QR), per-channel backend Configure dialogs.

| ID | Section | PWA | Android | Fix | Owner | Severity | Batch |
|---|---|---|---|---|---|---|---|
| G36 | Browser token / Server bearer / MCP SSE bearer | 3 inputs in Authentication | ConfigFieldsPanel(CommsAuth) | ✅ Parity | — | ✅ | — |
| G37 | Servers card | status + host + per-server row with Connected pill + Edit | ServersCard with Name/URL/badges + More menu | ✅ Parity structure; verify Connected-pill visual | ServersCard.kt | P3 | v0.35.0 |
| G38 | Web Server config | enabled / bind interface / port / TLS / TLS port / TLS auto-generate | ConfigFieldsPanel(WebServer) | ✅ Parity; verify TLS fields render | ConfigFieldSchemas.kt | P3 | v0.35.0 |
| G39 | MCP Server config | addr + enabled + token | ConfigFieldsPanel(McpServer) | ✅ Parity | — | ✅ | — |
| G40 | Proxy Resilience | toggle + retry params | ConfigFieldsPanel(Proxy) | ✅ Parity | — | ✅ | — |
| ~~G41~~ | ~~Signal device linking (QR code)~~ | `POST /api/link/start` → SSE `/api/link/stream` → QR image | not implemented | **DEFERRED 2026-04-24** — user: "already on phone, do not need signal setup. that can be a server only function." Signal pairing stays server-admin-only; Android won't implement. | — | WONTFIX | — |
| G42 | Channels list (messaging backends) | list of channel types with per-type Configure | ChannelsCard with schema-driven dialogs (v0.34.4) | ✅ Parity since v0.34.4 | ChannelsCard.kt | ✅ | — |
| G43 | CA cert install | `GET /api/cert` download link | CertInstallCard | ✅ Parity | — | ✅ | — |

#### LLM tab

PWA sections: LLM Configuration (per-backend rows), Episodic Memory, LLM RTK, Detection Filters, Saved Commands, Output Filters.

| ID | Section | PWA | Android | Fix | Owner | Severity | Batch |
|---|---|---|---|---|---|---|---|
| G44 | Per-backend row layout | `bold name  version/info  (default)  pencil-icon  enable-toggle` or `bold name  not-configured  [Configure]` | v0.34.5: state-aware rows with Switch + pencil Edit when configured, Configure button when not | ✅ Parity since v0.34.5 | LlmConfigCard.kt | ✅ | — |
| G45 | Configure dialog fields | LLM_FIELDS schema per backend (app.js:4262-4282): claude-code (claude_code_bin / enabled / skip_permissions / channel_enabled / fallback_chain / git / console_cols / console_rows), ollama (model dropdown + host + git + console), openwebui (url + api_key + model dropdown + git + console), aider/goose/gemini (binary + git + console), opencode-acp (binary + 3 timeouts + git + console), shell (script_path + git + console) | LlmBackendSchemas.kt has schemas including ones PWA doesn't (anthropic, openai, groq, openrouter, gemini, xai — server doesn't expose these) | Drop non-existent backends from LlmBackendSchemas OR mark them as "for future servers". Cross-check claude-code / ollama / openwebui / opencode-acp / shell schemas field-for-field against app.js:4262-4282. | LlmBackendSchemas.kt | P3 | v0.35.0 |
| G46 | "(default)" badge on current LLM backend row | app.js:3183-ish — `session.llm_backend` match | Android shows DEFAULT badge in LlmConfigCard | ✅ Parity | — | ✅ | — |
| G47 | Episodic Memory | 16 fields | ConfigFieldsPanel(Memory) | Verify all 16 covered | ConfigFieldSchemas.kt | P3 | v0.35.0 |
| G48 | LLM RTK | toggle + rules | ConfigFieldsPanel(LlmRtk) | ✅ Parity | — | ✅ | — |
| G49 | Detection Filters | filter list CRUD | DetectionFiltersCard | ✅ Parity | — | ✅ | — |
| G50 | Saved Commands | list CRUD | SavedCommandsCard | ✅ Parity | — | ✅ | — |
| G51 | Output Filters | list CRUD | FiltersCard | ✅ Parity | — | ✅ | — |

#### About tab

PWA sections: Version / Daemon info / Update check / Restart daemon / Sessions count / API doc link.

| ID | Section | PWA | Android | Fix | Owner | Severity | Batch |
|---|---|---|---|---|---|---|---|
| G52 | AboutCard content | version + daemon host + uptime + sessions | AboutCard (animated logo + version + daemon version + host + uptime + session stats) | ✅ Parity (Android has more) | — | ✅ | — |
| G53 | Update daemon | check + auto-update toggle | UpdateDaemonCard | ✅ Parity | — | ✅ | — |
| G54 | Restart daemon | button + confirm | RestartDaemonCard | ✅ Parity | — | ✅ | — |
| G55 | Raw config viewer | PWA reads / modifies via each field; there is NO general "raw YAML editor" dialog (BL14 noted as unshipped on PWA too) | Android ConfigViewerCard displays config read-only? verify | Not a gap if both absent; if Android has read-only viewer and PWA doesn't, that's a plus | ConfigViewerCard.kt | ✅ | — |
| G56 | API doc link | "/api/docs" link | ApiLinksCard | ✅ Parity | — | ✅ | — |

### 3.5 Purely visual / dimensional

Captured from screenshot comparison. These fold into the v0.35.0 polish release.

| ID | Title | Notes |
|---|---|---|
| G57 | M3 OutlinedTextField minHeight too tall vs PWA | PWA uses ~32px tall inputs with 12-13px text. M3 default is 56dp. Shrink via `contentPadding = PaddingValues(horizontal=12.dp, vertical=6.dp)` and `textStyle = bodyMedium`. |
| G58 | Collapsible section chevron affordance | PWA sections use a `▼` that rotates to `▶` when collapsed (app.js:3086 secContent); Android sections use default M3 ExpansionTile-style. Align chevron behavior. |
| G59 | Accent color (purple) | PWA uses `#8B5CF6` as primary. Verify Compose theme's `primary` matches. Check `dark-theme.xml` + `ui/theme/Theme.kt`. |
| G60 | Green "Connected" dot size + position | PWA: 8px dot right-of-title; Android top app bar has it but slightly larger. Minor. |
| G61 | Bottom nav item sizing | PWA: 4 items equal width, 12px label below icon. Android: Material3 NavigationBar default. Likely OK but verify labels aren't clipped at 4 items. |
| G62 | Session-row card left border | PWA: 3px solid state-colored left border. Android already matches per android-inventory.md. Re-verify vs screenshots. |
| G63 | Waiting-input banner tip string | PWA shows "Tip: press 1 then Enter" if prompt contains trust keywords. Android InputRequiredBanner may not. Low-value but easy. |
| G64 | "(default)" badge styling | PWA uses `(default)` subtle purple text after version string. Android uses chip. Could align. |

---

## 4. Validation of the prior `2026-04-22-pwa-parity-audit.md`

Full details in [`audit-validation.md`](audit-validation.md). Summary:

| Prior ID | Claim | Disposition |
|---|---|---|
| B34 | Session header state-pill tooltip + menu | CLOSED — Android already has state-override menu via `stateMenuOpen` + StateOverrideDialog (confirmed SessionDetailScreen.kt:421-429). Tooltip-on-hover doesn't apply on touch. |
| B35 | Full quick-command palette | CLOSED — Android has QuickCommandsSheet with system + saved + custom sections (confirmed android-inventory.md §1). |
| B36 | Quick-reply button layout parity (chat prompt) | CONFIRMED — rolled into G7 Alerts rebuild (PWA shows quick-reply dropdown on first alert of waiting-input session; Android has no per-alert quick-reply). |
| B37 | TLS / Certificate selector | WONTFIX — **does not exist in PWA.** PWA Comms has TLS toggle / TLS port / TLS auto-generate / tls_cert path fields (which map to server config) and a `GET /api/cert` download endpoint for CA installation. Android has all of these. The "selector" described in B37 never existed. |
| B38 | Raw YAML config editor | OPEN (backlog BL14) — neither PWA nor Android has it. Same status as before. Not a parity gap. |
| B39 | LLM temperature / top-p / max-tokens sliders | REDEFINED — PWA's per-backend LLM_FIELDS (app.js:4262-4282) does **not** include temperature / top-p / max-tokens for any backend. Mobile's `session.effort` field handles similar knob. Real gap: schema completeness for claude-code's `fallback_chain` / `skip_permissions` / channel_enabled — tracked as G45. |
| B40 | Prompt templates UI | REDEFINED — PWA has no "prompt templates" section. Has Saved Commands (Android parity ✅) and Detection Filters (Android parity ✅). "Prompt templates" in the prior audit may have been a misread of Saved Commands. No action. |
| B41 | Language selector | WONTFIX — **does not exist in PWA.** Inspected `/api/config` response + General tab source + screenshot: no i18n selector present. Matches parent datawatch policy (English only). Nothing to add. |
| B42 | In-app alerts panel | CLOSED — Android has a full Alerts tab. Real gap is G7 (structure/tabs/quick-reply). |

Net: 4 of 9 prior tickets get closed as parity-or-better (B34, B35, B40, B42), 2 are WONTFIX due to PWA misread (B37, B41), 1 is merged into G7 (B36), 1 is an existing backlog item (B38 / BL14), 1 is redefined as a schema check (B39 → G45).

---

## 5. Execution plan — release batches

Each release follows the standard cadence (bump Version.kt + gradle.properties, CHANGELOG entry, commit "release: vX — …", push). Batches are ordered by user impact and implementation surface.

### v0.34.6 — **shipped** (2026-04-23)
G1, G2, G3, G4 (P0). Release `e53176a`.

### v0.34.7 — P1 fix pass (~1 day of work)
- G5 — retest new-session LLM picker on today's build; if still broken, trace fetch order.
- G8 — tmux IME padding: move `imePadding()` off the Column, apply directly to ReplyComposer; eliminate double-counting.
- Verify no regression on v0.34.6 fixes.
- Patch items that emerge from retesting the user's flagged flow end-to-end.

Target files: `SessionDetailScreen.kt`, `NewSessionScreen.kt`, minor viewmodel tweaks.

### v0.34.8 — Alerts rebuild (~2-3 days of work)
G7 (and G36 roll-up).
- New `AlertsRepository` if needed; add `/api/alerts` transport call (verify schema).
- `AlertsViewModel` groups by session_id, classifies active/inactive, tracks selected tab.
- `AlertsScreen` tabs + per-session collapsible headers + per-alert cards with level-colored left border + quick-reply dropdown for waiting-input first-alerts.
- Keep swipe-to-mute + Schedule button.

Target files: `AlertsScreen.kt`, `AlertsViewModel.kt`, new `AlertsRepository.kt`, RestTransport.kt (extend), maybe new `AlertCard.kt`.

### v0.34.9 — Sessions ergonomics (~1-2 days)
G6, G62. (G10 dropped 2026-04-24 — PWA will adopt Android's FAB + full-screen-create pattern this weekend; no mobile change needed.)
- Drag-drop reorder: Compose long-press drag via `Modifier.pointerInput` + `reorderable` library if justified, OR custom DragDrop implementation. Persist order via SharedPreferences (mirroring PWA localStorage key).
- Drop hamburger-menu reorder entry; drop up/down arrow buttons.
- Re-verify card left-border state colors against PWA screenshots.

Target files: `SessionsScreen.kt`.

### v0.35.0 — Settings polish + Monitor completion (~3-4 days)
G9 (dimensions sweep), G19-G28 (Monitor cards), G31/G35 (General field verification), G37 (Servers card), G38 (TLS fields), G45 (LLM schema cleanup), G47 (Memory fields), G57-G64 (visual polish).
- Write a Compose preview showing each field at PWA-like dimensions; tune `ConfigFieldsPanel` style across the board.
- Fill Monitor tab with missing cards (Network, Daemon, Infrastructure, RTK Token Savings, Ollama Server, eBPF Degraded banner, optional Plugins).
- Drop non-existent backend schemas from `LlmBackendSchemas.kt`.

Target files: many — `ConfigFieldsPanel.kt`, `StatsScreen.kt`, `LlmBackendSchemas.kt`, `LlmConfigCard.kt`, `ChannelsCard.kt`, `ServersCard.kt`, `MemoryCard.kt`, theme files.

### v0.35.1 — Session-detail polish (~2 days)
G11 (inline rename), G12 (state-override dropdown anchor), G13 (Response button), G14 (Schedule popup parity), G15-G17 (terminal toolbar font/fit/scroll buttons).

Target files: `SessionDetailScreen.kt`, `TerminalToolbar.kt`.

### ~~v0.35.2 — Signal QR linking~~
Dropped 2026-04-24 per user direction: Signal pairing remains a server-admin-only function; the phone has no reason to run the QR pairing flow (user is already authenticated on the phone). G41 marked WONTFIX in §3.4.

---

## 6. Non-gaps / WONTFIX + upstream-tracked (captured for memory)

| Claim | Reason | Upstream ref |
|---|---|---|
| Language selector | Not in PWA. Parent datawatch is English only. | — |
| "TLS cert selector" | Misread of B37 in prior audit. PWA doesn't offer a selector; `/api/cert` is CA download only, which Android has via CertInstallCard. | — |
| Aspirational REST-style session endpoints (`DELETE /api/sessions/{id}`) | Android inventory doc listed these, but server is POST with body. v0.34.6 is already correct; don't introduce REST-style. | — |
| "Prompt templates UI" | Not a PWA thing; Saved Commands fills this role and is parity. | — |
| In-app alerts panel as a "new feature" | Android already has Alerts tab. Real gap is G7 (structure). | — |
| **Voice transcribe** (Android-extra) | PWA has no `getUserMedia` / mic surface despite server exposing `/api/voice/transcribe`. Contract-parity gap, filed upstream per bidirectional parity rule. | **[dmz006/datawatch#21](https://github.com/dmz006/datawatch/issues/21)** |
| Home-screen widgets | PWA lacks (not a platform capability). Non-gap, Android extra. Platform-specific, not a parity item. | — |
| Biometric unlock | PWA lacks. Non-gap, Android-only requirement. Platform-specific. | — |
| Wear / Auto surfaces | Explicitly out of scope per user directive. | — |
| `/api/sessions/reply` (Android dead code) | Server doesn't expose this; composer actually uses WS `send_input`. Android cleanup, not a PWA gap. | — |

---

## 7. Reviewing this doc

When you read this back, the important things to check:
- Section §3 Gap matrix is the single source of truth for what ships in which release.
- Sibling docs (pwa-inventory, android-inventory, server-contract, audit-validation) are authoritative for their respective scopes. Refer to them for field-level detail; don't duplicate here.
- `pwa-audit/out/*.png` is the visual source of truth.
- Every P0 from v0.34.6 is annotated ✅ so you can confirm those don't regress.
- If new gaps emerge during implementation, add them as Gnn rows with the same schema and pick a batch.

**Next action (not yet done):** I still need to write `server-contract.md` and `audit-validation.md` as stand-alone companion docs. They're small and I'll land them in the same commit as this README. The gap-matrix above is already self-contained, so implementation can begin without those files.
