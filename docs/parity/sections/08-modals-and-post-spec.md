# 08 — New Session modal, modals/dialogs, post-spec PWA views

Sources: live PWA `dmz006/datawatch` `internal/server/web/app.js` (v8.38.x, 26,869 lines; refs are `app.js:LINE` or function name),
May spec §9–§10; Android `composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/…` (refs `File.kt:LINE`); iOS `iosApp/iosApp/ui/…`.
Verification was code-level (grep/read), not visual. `~ unverified` = present in the package but the exact element was not confirmed.

## A1 — New Session (spec §9) · PWA `openNewSessionModal` app.js:5478 → `renderNewSessionView` :5569 → `submitNewSession` :6368 · Android `sessions/NewSessionScreen.kt` (1,383 lines) · iOS none

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Entry point (`+` FAB / header) → new-session surface | ✓ FAB (`fab` ×16) → panel-modal | ✓ `Destinations.NewSession` full screen | ✓ Sessions FAB → NewSessionView | aligned | | |
| element | Session name input | ✓ :5569 `new_session_name` | ✓ NewSessionScreen.kt:337–345 | ✓ NewSessionView | aligned | | |
| element | Task description (expandable textarea) | ✓ `<details>` textarea | ✓ :368–431 + mic (`MicAttachableTextField`) | ✓ NewSessionView | aligned | | |
| element | Saved-command library picker inside task field | ✗ (0 hits `savedCmd\|saved_command` in modal) | ✓ `SavedCommandLibraryDropdown` :379 | ✓ NewSessionView "From library ▾" menu (`/api/commands`) | pwa-missing | decided D81a | Android-only convenience — D1 · iOS done 2026-10-04 |
| element | Project directory input | ✓ `#sessDirRow` :5560 | ✓ `new_session_working_dir` :714–726 | ✓ NewSessionView | aligned | | |
| interaction | Directory browser (breadcrumb, mkdir, click-to-navigate) | ✓ `#dirBrowser` :5558 | ✗ (`FilePickerDialog` not used here) | ✗ | android-missing | | iOS also missing |
| element | Profile select (project profiles, "— project directory —" first) | ✓ `#sessProfile` :5615 | ✓ `new_session_profile_label` :590 | ✓ NewSessionView | aligned | | |
| element | Cluster select (hidden until profile picked) | ✓ `#sessClusterRow` :5620 | ✓ `new_session_cluster_label` :608–629 | ✓ NewSessionView | aligned | | |
| element | LLM picker (v7 registry) + compute-node sub-select + hint | ✓ `#sessLLMSelect` :5648, `#sessComputeSelect` :5655, `#sessV7Hint` :5659 | ✓ `llmEntries`/`pickedComputeNode` :136–154 | ✓ NewSessionView | aligned | | |
| element | Permission mode / model / effort (Claude) | ✓ :5671 / :5674 / :5677 | ✓ :662–692 (`new_session_advanced_claude`) | ✓ NewSessionView | aligned | | |
| element | Model / effort for non-Claude + OpenCode grouped models | ~ (model select only) | ✓ :546–568, `openCodeModelGroups` :170 | ~ NewSessionView flat non-Claude model picker (as PWA; no OpenCode grouping) | misaligned | needs-decision | Android groups OpenCode models by provider — D2 |
| element | Chrome integration checkbox | ✓ `#newSessionChrome` :5681 (not in May spec) | ✓ `chromeIntegration` Switch :701 | ✓ NewSessionView | aligned | | spec drift: §9 omits it |
| element | Git auto-init / auto-commit toggles | ✓ (`auto_git`/`git_init`/`auto_commit` ×18) | ✓ :756–779 | ✓ NewSessionView git toggles | aligned | | |
| element | Resume previous session field | ✗ (0 hits `resume_session\|sessResume`) | ✓ `new_session_resume_label` :743 | ✓ NewSessionView "Resume previous (optional)" picker → `resume_id` | pwa-missing | decided D82a | D3 · iOS done 2026-10-04 |
| element | Session backlog / recent done sessions (restart) | ✓ `renderSessionBacklog` :5765 | ✓ :785–820 (20 most recent, restart) | ✓ "Recently finished" + Restart | aligned | | |
| element | Backend setup hint when backend not configured | ~ spec §9 item 9 (live ref not located) | ✗ not found | ✗ | misaligned | | verify in PWA before acting |
| element | Server picker (which datawatch server) | n/a (single server) | ✓ `new_session_server_label` :476 | ✗ | n/a | | mobile multi-server concept |
| interaction | Submit → `POST /api/sessions/start` (dir) or `POST /api/agents` (profile) | ✓ :6368–6481 | ✓ `startSession` (:60); `/api/agents` path ~ unverified | ✓ IosNewSession (dir / agents) | aligned | | check Android profile-mode submit |
| string | i18n of all labels | ✓ `t('new_session_*')` | ✓ `R.string.new_session_*` (en + 4 locales) | ✗ | ios-missing | | |

## A2 — Modals & dialogs (spec §10) · PWA `show*` functions

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| interaction | Confirm modal — stop / kill / restart / delete session | ✓ `showConfirmModal` :14870 (`dialog_stop_session_title`, `dialog_delete_sessions_title`) | ✓ SessionsScreen.kt:925–927 AlertDialogs | ✓ SessionsView.swift:91–107, SessionDetailView.swift:73–79 | aligned | | copy/button styling to verify visually |
| interaction | Confirm modal — bulk delete sessions | ✓ `dialog_delete_sessions_title` | ✓ SessionsScreen.kt:454–479 | ✗ | ios-missing | | iOS has no multi-select |
| interaction | Confirm modal — other sites (delete template/LLM/compute node/scheduled events, cancel graph, kill orphaned tmux) | ✓ 13 call sites | ✓ per card (autonomous/, compute/, schedules/) ~ unverified each | ~ delete template (TemplatesView), cancel graph (OrchestratorViews), batch delete (PrdListView); LLM/compute node/schedules/orphaned tmux ✗ | misaligned | | |
| interaction | Generic modal (`showModal`) — template create/edit, batch guards | ✓ :16849 | ✓ `CreateEditTemplateSheet.kt`, `InstantiateTemplateDialog.kt` | ✓ TemplateEditView / InstantiateTemplateView sheets; batch confirm (PrdListView) | aligned | | |
| interaction | State override dropdown on state badge → `PUT /api/sessions/state` | ✓ `showStateOverride` :14843; live states `running, waiting_input, complete, killed, failed` | ✓ `StateOverrideDialog` SessionDetailScreen.kt:2578 | ✓ SessionDetailView state menu (B6) | aligned | | spec §10.3 lists `rate_limited` — removed in live PWA |
| interaction | Backend config popup (Comms → Configure) | ✓ `showBackendConfigPopup` :14028 | ✓ `channels/BackendConfigDialog.kt` | ✗ | ios-missing | | |
| interaction | Compute Kind migration modal (deprecated kinds) | ✓ `deprecatedKinds` :7920 | ✗ not found | ✗ | android-missing | | iOS also missing; spec §10.5 |
| motion | Toast — 3.5 s auto-dismiss, 4 types, stacks newest-first | ✓ `showToast` :14988 (`_duration = 3500`) | ✓ `common/DatawatchToast.kt` `DatawatchToastHost` :107 (duration ~ unverified) | ✗ | ios-missing | | iOS surfaces errors via alerts/banners only |
| interaction | Response viewer modal (last response) | ✓ `showResponseViewer` :14890 | ✓ SessionDetailScreen.kt:583–656 | ✓ `LastResponseSheet` SessionDetailView.swift:91 | aligned | | |
| interaction | Schedule input popup | ✓ `showScheduleInputPopup` :3681 | ✓ `schedules/ScheduleDialog.kt` | ✓ ScheduleInputSheet | aligned | | |
| interaction | Card quick commands + command edit | ✓ `showCardCmds` :2610, `showCmdEdit` :21012 | ✓ `commands/` package | ✓ QuickCommandsSheet + SavedCommandsView | aligned | | |
| interaction | Filter edit dialog | ✓ `showFilterEdit` :21124 | ✓ `filters/` package | ✓ FiltersView edit sheet | aligned | | |
| interaction | Channel help popup | ✓ `showChannelHelp` :4525 | ✗ not found | ✗ | android-missing | | iOS also missing |
| interaction | Remote server / federation peer forms | ✓ `showServerForm` :14550, `showFedPeerForm` :23619 | ✓ `federation/FederationPeersCard.kt` | ✗ | ios-missing | | |
| interaction | Web search provider form | ✓ `showWebSearchProviderForm` :12768 | ✓ `websearch/WebSearchRegistryCard.kt` | ✗ | ios-missing | | |
| interaction | Observer peer snapshot modal | ✓ `showObserverPeerSnapshot` :20224 | ~ `monitoring/PeerResourcesCard.kt` unverified | ✗ | ios-missing | | |
| interaction | Debug panel | ✓ `showDebugPanel` :15427 | ✗ | ✗ | android-missing | needs-decision | dev tool — n/a on mobile? D4 |
| interaction | Mobile-only sheets: voice recording, docs viewer, server picker, file picker | n/a | ✓ `common/VoiceRecordingDialog.kt`, `DocsViewerSheet.kt`, `servers/ServerPickerSheet.kt`, `files/FilePickerDialog.kt` | ~ `VoiceRecorder.swift`, `DocsLinkButton` (Safari), none, none | n/a | | platform affordances; iOS file picker missing |

## B — Live-PWA views/features not in the May spec (§1–§10)

Live nav (`index.html data-view`): sessions · alerts · autonomous · observer · **dashboard** · settings. Spec has no Dashboard section at all.

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Dashboard view (bottom-nav tab) | ✓ `renderDashboardView` :22898 | ✓ `dashboard/DashboardScreen.kt` | ✓ `dashboard/DashboardView.swift` — PWA 12-col card grid (D34a; multi-server overview removed), server picker, `live · ws` | aligned | D34a | B36; shared `dashboard/IosDash*` engine |
| element | Dashboard stat tiles — sessions / tasks / guardrails / burn rate | ✓ `#dashStatSessions/Tasks/Guardrails/BurnRate` | ~ unverified | ✓ `DashStatBar` (sess · active · $ · tasks · blk/warn · burn rate) | aligned | | iOS reads `/api/cost` `total_usd` (PWA reads `total_cost_usd`, never shows $) |
| element | Dashboard card: Constellation | ✓ (`constellation` ×8) | ✓ `ConstellationCard` :222 | ✓ `DashNetworkCard` — force graph (physics 60 Hz, redraw 10 fps, pulse/health rings, threat badges, tap → session/automaton); <400 pt compact list (#98) | aligned | | Reduce Motion: settled static frame; edges from hook `parent_session_id` |
| element | Dashboard card: Pulse / EKG | ✓ (`ekg\|pulse` ×12) | ✓ `PulseCard` :384 | ✓ `DashEkgCard` — per-session channels (≤6), hook blips sweep a 60 s window at display rate + burn-rate panel; <280 pt burn-rate only (#99) | aligned | | fed by WS `hook_update` (new `HookHub`) |
| element | Dashboard card: Sparklines (SVG, ~5 fps) | ✓ `_sparkline` :3919, :22097 | ✓ `SparklineCard` :593 | ✓ `DashSparklinesCard` — 60×2 s hook-event buckets per active session, TimelineView 5 fps | aligned | | iOS buckets per session (PWA counts all events in every row) |
| element | Dashboard card: Recent events | ~ unverified | ✓ `RecentEventsCard` :469 | ✓ `DashEventsCard` (PWA `events` Live Events ticker, last 40 hook events) | aligned | | |
| element | Dashboard card: Gantt / pipeline | ✓ (`gantt` ×22) | ✓ `PipelineCard` :529 | ✓ `DashGanttCard` (Timeline · 6h: hour ticks, NOW line, automaton rows + story bars, collapsible) + `DashTreeCard` (Automata tree) | aligned | | redraw 1 s (PWA 10 fps; only the NOW edge moves) |
| element | Dashboard card: Heatmap | ✓ `_heatmapData` :16430 | ✓ `HeatmapCard` :649 | ✓ `DashHeatmapCard` — 30-day strip; 7-day bars when <300 pt or cs ≤ 3 (#101) | aligned | | |
| element | Dashboard card: Guardrails overview | ✓ (`'guardrails'` ×2) | ✓ `GuardrailsOverviewCard` :736 | ✓ `DashGuardrailsCard` (block/warn/pass + per-rule bars) | aligned | | |
| element | Dashboard card: Smoke progress | ✓ (`smoke` ×74) | ✓ `SmokeProgressCard` :796 ("Clear run") | ✓ `DashSmokeCard` — multi-run envelopes, select → detail, filter pills, delete / Clear all; 2.5 s poll while a run is active | aligned | | Android still parses the legacy single-run shape |
| interaction | Dashboard add/edit cards + expand panel (tree + verdicts) | ✓ `#dashAddCardBtn`, `#dashEditBtn`, `#dashExpand*` | ✓ `settings/DashboardCardsCard` + BL303 expand mode | ✓ Edit/Done (+ Card sheet, Nw/Nh cyclers, remove non-system, ↑/↓ reorder) → PUT `/api/dashboard/layout`; `DashExpandView` (Task Tree · Status · Verdicts; `DashExpandNav.open(sid)` from any tab); collapsible cards persisted (D27a), per-card docs links (D26a) | aligned | D27a D26a | drag-reorder replaced by move buttons; Memory Scopes + Search Usage tiles also built |
| nav | Orchestrator graphs view | ✓ `renderOrchestratorView` :25115 (`orchestrator/graph`) | ✓ `automata/OrchestratorGraphsCard.kt`, `OrchestratorGraphDialog.kt` | ✓ OrchestratorGraphsView | aligned | | app#184 |
| element | Guardrail verdicts inline in session detail (+ Approve on blocked) | ✓ `renderSessionGuardrailVerdicts` :4281 | ~ unverified in sessions/ | ~ SessionStatusView "Guardrail verdicts" card (Status tab); no Approve on blocked | misaligned | | |
| element | Guardrail library (Settings) | ✓ (`guardrail` ×67) | ✓ `settings/GuardrailLibraryCard.kt` | ✗ | ios-missing | | |
| element | Council live run (WS `run_started/round_*/persona_*`), decisions, verdicts | ✓ `renderDetailDecisions` :17649, `renderVerdicts` | ✓ `settings/CouncilCard.kt`, `CouncilPersonaWizardSheet.kt` | ✗ | ios-missing | | spec §8.8 covers config only, not live runs |
| element | Status board: lifecycle strip, sprint breadcrumb, failed drill-down | ✓ :11212 / :4199 / :4312 | ~ unverified | ~ SessionStatusView sprint breadcrumb + task tree; no failed drill-down | misaligned | | |
| element | Sessions rendered as tree + parent-session link + page controls | ✓ `renderSessionsAsTree` :2124, `renderParentSessionLink`, `renderPageControls` :6544 | ~ unverified | ✗ | ios-missing | | |
| element | Discussion Scopes card (Settings → General, BL332) | ✓ :7246 | ~ `memory/` (#191 scope dirs) unverified | ✗ | ios-missing | | |
| element | Algorithm Mode settings section | ✓ :6902 | ✓ `settings/AlgorithmModeCard.kt` | ✓ `SettingsAlgorithmModeCard` (Settings › Automata) | aligned | | |
| interaction | Ollama marketplace modal / catalog | ✓ `openOllamaMarketplace` :8224, `renderOllamaCatalog` :8247 | ~ `compute/LlmRegistryCard.kt` unverified | ✗ | ios-missing | | |
| element | Project profiles panel + cluster/project YAML editors | ✓ `renderProfilesPanel` :15778, `renderClusterEditorForm` :15852, `renderProjectEditorForm` | ✗ not found | ✗ | android-missing | | iOS also missing; smoke buttons per profile |
| element | Web search stats + provider registry | ✓ `renderWebSearchStatsHTML` :20397 | ✓ `websearch/WebSearchRegistryCard.kt` + Observer (v1.23.107) | ✗ | ios-missing | | |
| interaction | Identity wizard (header button on Automata page + Settings card) | ✓ :1805–1809 | ✓ `settings/IdentityWizardSheet.kt` | ✗ | ios-missing | | |
| element | Tailscale card (Settings → Compute) | ✓ (`tailscale` ×56) | ✓ `tailscale/TailscaleSettingsCard.kt`, `TailscaleMeshCard.kt` | ✗ | ios-missing | | detail in 07 |
| element | Skills registry (automata skills, registries) | ✓ (`skills` ×115) | ~ unverified | ✗ | ios-missing | | |
| element | Secrets vault card (Settings → General) | ✓ (`secret` ×58) | ✗ not found | ✗ | android-missing | | iOS also missing |
| element | Compute node telemetry / declared capacity | ✓ :7911, :8120 | ✓ `compute/` (#192 capacity-aware) | ✗ | ios-missing | | |
| nav | Docs / diagrams help links (`/diagrams.html#…`) | ✓ ×17 | ✓ `common/DocsViewerSheet.kt`, `settings/DocsSearchCard.kt` | ✓ `DocsLinkButton` (SFSafariViewController) | aligned | | Android in-app viewer vs iOS Safari — verify intent |
| element | Memory recall / scope inventory / lifecycle UI (BL385–387) | ✗ (0 hits `memory/recall\|memory_scope`; only Observer "Memory Browser" card) | ✓ `memory/` (#174–176) | ~ PRD memory UI (`PrdMemorySection`: stats, report, recall); no standalone scope inventory | pwa-missing | decided D77a | D6 · iOS done 2026-10-04 |
| element | Inline file viewer for story/task file chips | ✗ (0 hits `api/files\|browseFiles`) | ✓ `autonomous/FileViewerSheet.kt` (#181) | ✓ PrdFileViewerSheet (PrdItemEditSheets.swift) | pwa-missing | decided D83a | D7 · iOS verified 2026-10-04 |
| element | Knowledge Graph card | ✓ ("Knowledge Graph" ×4) | ✓ `observer/KnowledgeGraphCard.kt` | ✗ | ios-missing | | |
| nav | Onboarding flow, deep links, multi-server profile picker, biometric lock | n/a | ✓ `onboarding/`, `DeepLinks.kt`, `servers/ServerPickerSheet.kt`, biometric | ~ `BiometricGate.swift` ✓; onboarding/deep links ✗ | n/a | | mobile-only; iOS lacks onboarding + deep links (track in 01) |
| nav | Version staleness reload (spec §1.6) | ✗ (0 hits `staleVersion\|serverVersion\|checkVersion`) | ~ unverified | ✗ | pwa-missing | | spec item not found in live PWA — likely removed/renamed; verify |
| interaction | Batch select mode, FAB, Channel tab, Kind migration (spec items spot-checked) | ✓ (`selectMode\|bulkDelete` ×28, `fab` ×16, channel ×17, :7920) | — | — | aligned | | spec items confirmed still live; no removals found besides §1.6 and `rate_limited` |

## Coverage
rows: 71 · aligned: 22 · ios-missing: 29 · android-missing: 6 · pwa-missing: 5 · misaligned: 6 · n/a: 3

## Decisions needed
1. **D1 Saved-command library in New Session task field** — Android-only (`SavedCommandLibraryDropdown` NewSessionScreen.kt:379). Options: (a) adopt in PWA + iOS, (b) drop from Android, (c) keep as mobile-only.
2. **D2 OpenCode models grouped by provider in New Session** — Android groups (`openCodeModelGroups` :170), PWA flat `#sessClaudeModel`. Options: (a) PWA adopts grouping, (b) Android flattens.
3. **D3 "Resume previous session" field** — Android only (NewSessionScreen.kt:743); PWA has no equivalent. Options: (a) adopt in PWA + iOS, (b) remove from Android, (c) mobile-only.
4. **D4 Debug panel** (`showDebugPanel` app.js:15427) — Options: (a) n/a on mobile, (b) port to Android + iOS behind a developer toggle.
5. **D5 Dashboard design** — PWA/Android: configurable card grid (constellation, pulse, sparklines, events, gantt, heatmap, guardrails, smoke) + stat tiles + expand panel; iOS: per-server stats + sessions summary cards (`DashboardView.swift`). Options: (a) iOS rebuilds the PWA card grid (parity), (b) keep iOS's multi-server overview and add it to PWA/Android as a card, (c) both.
6. **D6 Memory recall / scopes / lifecycle UI** — Android has full BL385–387 UI (#174–176); live PWA has only the Observer "Memory Browser" card. Options: (a) PWA adopts Android's UI, (b) Android trims to PWA scope, (c) confirm PWA has it under different names before deciding.
7. **D7 Inline file viewer for story/task file chips** — Android `FileViewerSheet` (#181); PWA has no `/api/files` usage. Options: (a) adopt in PWA + iOS, (b) mobile-only.

Spec-drift notes (no decision, fix the spec): §9 omits the Chrome integration checkbox (live `#newSessionChrome`); §10.3 lists `rate_limited` but live states are `running, waiting_input, complete, killed, failed`; §1.6 version-staleness reload not found in live code; Dashboard view, Orchestrator view, guardrail verdicts, council live runs, identity wizard, discussion scopes, algorithm mode, Ollama marketplace, profiles/cluster editors, web search stats, skills, secrets and telemetry/capacity are all live but unspecified.
