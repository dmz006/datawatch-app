# 05 — Automata (PRDs, templates, orchestrator touchpoints)

PWA spec §6 · live PWA `internal/server/web/app.js` (automata list 16456–17250, wizard 17278–17612, detail 17848–19006, stories/tasks 10274–10810, actions 11054–11370, markdown 15556–15610; `style.css` 391–585) · Android `ui/autonomous/*`, `ui/automata/*`, `ui/orchestrator/*`, `ui/memory/*` (PRD parts) · iOS `screens/automata/AutomataView.swift`, `PrdListView.swift`, `PrdDetailView.swift`.

Refs: `app:N` = app.js line, `css:N` = style.css line, `AS:N` = AutonomousScreen.kt, `AVM:N` = AutonomousViewModel.kt, `PDD:N` = PrdDetailDialog.kt, `NPD:N` = NewPrdDialog.kt, `MD:N` = MarkdownComponents.kt. iOS refs are view/type names.

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Automata nav entry gated on `/api/autonomous/config.enabled` | ✓ app:21477 | ~ AS (tab always present; PRD tab shown immediately v1.23.73) | ~ AutomataView (always) | misaligned | | PWA hides nav until enabled; apps always show |
| nav | Header title "Automata" + robot identity button 🤖 + search/filter button | ✓ app:225 | ~ AS:1430 server-picker title, no robot btn | ~ HeaderView("Automata"), no robot/filter | misaligned | | Robot identity button missing on both apps |
| nav | Server scope: "All servers" aggregated (`/prds/aggregated`) | ✓ app:16825 | ✓ AS:1445 sessions_all_servers | ✗ (single profile picker) | ios-missing | | |
| interaction | FAB ⚡ → Launch Automaton wizard | ✓ app:17278 | ✓ AS:219 autonomous_fab_new → NewPrdDialog | ✗ | ios-missing | | |
| element | Tab strip: "Automata" · "Templates" | ✓ app:17159 automata_tab_* | ~ AS:246 "PRDs" · "Templates" | ~ AutomataView Picker "PRDs" · "Types" | misaligned | needs-decision | D1 terminology; iOS second tab is the type registry, not templates |
| interaction | ☑ Select (batch mode) toggle | ✓ app:17190 | ✓ AS:256 AutomataActionBtn | ✗ | ios-missing | | |
| interaction | ⊞ Filter bar toggle | ✓ app:17178 | ✓ AS:256 | ✗ | ios-missing | | |
| interaction | ⏱ History toggle (show completed/cancelled/archived) | ✓ app:17207, filter app:16482 | ✓ AS:109 historyOn, AS:267 | ✗ (iOS always shows all) | misaligned | | iOS shows terminal PRDs unconditionally |
| element | Filter bar: text search | ✓ app:16488 automata_filter_search | ✗ (chips only) | ✗ | android-missing | | iOS also missing |
| element | Filter bar: status badges draft/planning/needs_review/approved/running/blocked (+archived) | ✓ app:17219, css:504–509 | ✓ AS:585–635 FilterChip | ✗ | ios-missing | | Android chip colors not verified vs css:504–509 |
| element | Filter bar: type badges software/research/operational/personal | ✓ app:17228 | ✓ AS:641 | ✗ | ios-missing | | |
| element | Filter bar: "All" select-all checkbox (select mode) | ✓ app:16606 | ~ AS:648 "templates" include chip instead | ✗ | misaligned | | |
| data | List sort: pinned first → state rank (active first) → updated | ✓ app:16491–16500 | ~ AS:273 pinnedIds (rank order not verified) | ✗ server order | misaligned | | |
| element | Card: type badge, per-type color (software #6366f1 · research #f59e0b · operational #10b981 · personal #ec4899) | ✓ app:16727 | ~ AS:96 TypeBadge (colors not verified) | ~ PrdRow metaLine plain text | misaligned | | iOS has no badge |
| element | Card: "template" badge (#7c3aed) when `is_template` | ✓ app:16730 | ✓ AS:829 autonomous_template_label | ✗ | ios-missing | | |
| element | Card: title 15px/700 · status pill right-justified | ✓ app:16769 | ✓ AS:98,112 | ✓ PrdRow | aligned | | |
| element | Card meta row: `id` (code) + updated_at, mono, right-justified | ✓ app:16773 | ~ AS:139 id.take(8) + ts; adds parent link ↗pid AS:128 | ~ PrdRow (type·backend·counts, no id/ts) | misaligned | | Android parent-link is extra (see D2) |
| element | Card: progress bar + "done/total tasks · pct%" (complete → success fill) | ✓ app:16682, css:513–526 | ~ AS progress (label format not verified) | ~ PrdRow ProgressView, no pct label | misaligned | | |
| element | Card: current position line "▶ Story i: … · Task j: … (verifying/testing)" when running | ✓ app:16694 | ✗ | ✗ | android-missing | | iOS also missing |
| element | Card: compact lifecycle strip plan→review→approve→run→done | ✓ app:11212, css:544–585 | ✓ AS LifecycleStrip | ✗ | ios-missing | | |
| interaction | Lifecycle steps clickable → plan/approve/reject/revise/run/instantiate/delete actions; `danger` style | ✓ app:11212 (clickable/danger) | ~ LifecycleStrip (click actions not verified) | ✗ | misaligned | | |
| element | Card action row: ✕ Cancel (non-terminal) · ✓ Approve amber (needs_review/revisions_asked/waiting_input) · 📌 pin | ✓ app:16785–16799 | ~ AS:12–26 adds inline Reject + Revise | ✗ | misaligned | needs-decision | D3 |
| interaction | Pin (persisted localStorage) | ✓ app:16456 | ~ AVM togglePin (persistence not verified) | ✗ | ios-missing | | |
| element | Card: `<details>` "Stories & tasks (N)" expandable tree | ✓ app:16806 | ✓ AS (accordion, header-only click v1.23.32) | ✗ (detail only) | ios-missing | | |
| interaction | Tap card (non-button) → detail | ✓ app:16766 | ✓ AS:57 onClick / long-press selects | ✓ NavigationLink | aligned | | Android long-press = select; PWA uses checkbox |
| token | Card left border color per status (`prd-card-status-*`) | ✓ css:400–411 | ~ AS AutonomousStatusDot (not border) | ✗ | misaligned | | |
| token | Status pill = session state-badge style, 11px/600, 1px currentColor border; pulse on running/planning/decomposing | ✓ app:10114 (`state-badge-*`, `dw-running-pulse`) | ~ AS:1396 prdStatusColor hex map, no pulse | ~ PrdStatusChip same hex map, no pulse | misaligned | needs-decision | D4 colors; pulse missing on both apps |
| motion | Running/planning pulse animation on status pill | ✓ `.state-badge-running` | ✗ | ✗ | android-missing | | iOS also missing |
| element | Batch bar: Run · Approve · Cancel · Archive · Delete · Done/exit | ✓ app:16522 automata_batch_* | ✓ AS:321–342 (+ "✕ Clear") | ✗ | ios-missing | | |
| interaction | Batch delete confirm dialog | ✓ automata_confirm_batch_delete | ✓ AS:415 | ✗ | ios-missing | | |
| interaction | Cancel confirm: special copy when status=planning ("Planning is running… will abort") | ✓ app:16802 | ~ AS:369 generic title | ~ PrdDetailView "Running tasks are stopped…" | misaligned | | Copy drift on both apps |
| string | Empty state (no automata / no templates) | ✓ automata_empty_templates, "no stories yet" | ✓ AS:697 autonomous_empty_state | ✓ PrdListView "No PRDs" | misaligned | | Copy differs; PWA wins |
| element | Templates tab: card (title, built-in badge, description, type badge, tags, var count, use count) | ✓ app:16916 | ✓ TemplatesTab.kt | ✗ | ios-missing | | |
| interaction | Template actions ▶ Use (instantiate modal) · ✎ edit · ✕ delete (non-builtin) · ＋ Template | ✓ app:16916–16990 | ✓ InstantiateTemplateDialog, CreateEditTemplateSheet | ✗ | ios-missing | | |
| element | Template create/edit form: title, type, description, spec, tags, vars | ✓ automata_tmpl_field_* | ✓ CreateEditTemplateSheet.kt | ✗ | ios-missing | | |
| interaction | Clone PRD → template | ✓ prd_btn_clone_template | ✓ PDD:478 | ✗ | ios-missing | | |
| element | Type registry (id, label, description, color, built-in) | ✓ app:24702 in Settings → Automata | ~ AVM loadAutomataTypes/createAutomataType (surface not located in Settings) | ~ AutomataView "Types" tab + AddAutomataTypeSheet | misaligned | needs-decision | D5 placement |
| element | Launch wizard: intent textarea + auto-detected type + type buttons | ✓ app:17278 wizardIntent/wizardDetectedType/wizard-type-btn | ~ NPD:273 "What do you want to accomplish?" (type picker not verified) | ✗ | misaligned | | |
| element | Wizard: title, workspace/profile (— project directory —), directory + Browse | ✓ wizardTitle/wizardProfile/wizardDirRow | ✓ NPD:318,332,339,262 | ✗ | ios-missing | | |
| element | Wizard: execution backend, model, effort | ✓ automata_wizard_backend/model/effort | ✓ NPD:392,426,463 | ✗ | ios-missing | | |
| element | Wizard: planning backend + decomposition model | ✓ prd_new_planning_label, prdNewDecompModel* | ✓ NPD:534,563 | ✗ | ios-missing | | |
| element | Wizard: guided mode · story approval · rules enabled · scan enabled toggles (advanced) | ✓ wizardGuidedMode/StoryApproval/RulesEnabled/ScanEnabled | ~ NPD (toggles not found) | ✗ | misaligned | | Verify Android advanced toggles |
| element | Wizard: memory "promote to" scope | ✗ | ✓ NPD:694 new_prd_memory_promote_to | ✗ | pwa-missing | needs-decision | D6 |
| interaction | Wizard: "Use template" link → Templates tab · skills hint link · help tip | ✓ automata_wizard_use_template/skills_hint_link/help_tip | ✗ | ✗ | android-missing | | iOS also missing |
| nav | Detail view replaces list; breadcrumb/back (`automata_detail_back`) | ✓ app:17848, app:19007 | ~ PDD full-screen dialog, Close | ✓ push + back | misaligned | | Container differs (view vs dialog vs push) |
| element | Detail header: type + status badges, title (tap to edit tip), id, last activity, lifecycle strip | ✓ app:17848+ | ✓ PDD:201–260 | ~ PrdDetailView header (no id/last activity/lifecycle) | misaligned | | |
| element | Detail tabs: Overview · Stories · Decisions · Rules · Scan(cond.) | ✓ prd_tab_* | ~ PDD:201–205 Overview · Stories · Decisions · Graph · Progress | ✗ (single scroll) | misaligned | needs-decision | D7 |
| interaction | Actions: ✓ Approve | ✓ app:10917 `{actor:'operator'}` | ~ PDD:395,868 approve with optional note | ✓ perform("approve") | misaligned | needs-decision | D8 note |
| interaction | Actions: ✗ Reject (reason prompt) | ✓ app:11188 prdActionPrompt | ✓ PDD:448,891 | ✓ alert + TextField | aligned | | |
| interaction | Actions: ↺ Request revision (note prompt) | ✓ prd_btn_request_revision | ✓ PDD:455,924 | ✓ alert "Request revision" | aligned | | |
| interaction | Actions: ▶ Start Planning / Re-plan (decompose) | ✓ app:18686 | ✓ PDD:435 prd_detail_decompose | ✓ "Decompose" (draft only) | misaligned | | iOS label/state gating differs (PWA shows Re-plan on revisions_asked) |
| interaction | Actions: ▶ Run | ✓ | ✓ PDD:409 | ✓ | aligned | | |
| interaction | Actions: ✕ Cancel | ✓ | ✓ PDD:426 | ✓ | aligned | | copy drift noted above |
| interaction | Actions: ↺ Reset to draft | ✓ prd_btn_reset_to_draft | ✓ PDD:489 | ✗ | ios-missing | | |
| interaction | Actions: Archive | ✓ app:5414/batch archive | ~ batch only AS:336; no detail action (AVM lacks archive) | ✗ | android-missing | | iOS also missing |
| interaction | Actions: Delete (with memory strategy keep/purge/archive, roles, scope) | ✓ prdDeleteArchiveOpts/Roles/Scope | ✓ PDD:992–1027 | ✗ | ios-missing | | |
| interaction | Actions: Edit title / Edit spec (prdEditMenu) | ✓ prd_btn_edit, prd_btn_edit_spec | ✓ PDD:1395–1421 EditPrdDialog | ✗ | ios-missing | | |
| element | Edit PRD: permission_mode field | ✗ (not in prdEdit* ids) | ✓ PDD:1421 new_prd_permission_mode_label | ✗ | pwa-missing | needs-decision | D9 |
| interaction | Actions: Set LLM (backend/effort/model + planning backend/decomp model) | ✓ prdSetModel*, prdSetDecompModel* | ✓ PDD:1192–1347 LlmOverrideDialog | ✗ | ios-missing | | |
| interaction | Actions: Repair depends_on (#202) | ✗ | ✓ PDD:501 | ✗ | pwa-missing | needs-decision | D10 |
| interaction | Actions: Run scan / Run rules (propose) | ✓ prd_btn_run_scan, prd_btn_run_rules | ✓ AVM triggerScan/proposeRules, ScanResultCard | ✗ | ios-missing | | |
| interaction | Actions: View sessions | ✓ automata_actions_view_sessions | ✓ PDD:608 | ✗ | ios-missing | | |
| interaction | Actions: Pause / Resume | ~ app:16808 defined, 0 callers (dead) | ✗ | ✗ | n/a | needs-decision | D11 spec lists them |
| element | Overview: spec collapsed/ellipsis/"show full"/"hide" | ✓ prdSpecCollapsed/Full, automata_spec_show_full | ✓ PDD:324 | ~ DisclosureGroup "Spec" | misaligned | | |
| element | Overview: markdown + GFM tables + Mermaid rendering of spec | ✓ app:15556 marked@12 + mermaid@10.9.6 (CDN) | ✓ MD:170 MarkdownView, MD:244 GfmTableView, MD:319 MermaidView (WebView, CDN) | ✗ plain Text | ios-missing | needs-decision | D12 CDN vs vendored |
| element | Overview meta: backend, model, effort, concurrency, project, depth, created, guided mode, skills, read-only dirs, writable dirs | ✓ automata_detail_* | ✓ PDD:2228–2479 | ~ type/backend/model/effort/guided/projectDir only | misaligned | | |
| element | Settings panel: type, guided mode, continue-on-story-failure, skills picker, priority, read/write dirs, concurrency, model, decomp model | ✓ prdSettings* (app:11939) | ✓ PDD:2228–2479 rows (concurrency not verified) | ✗ | ios-missing | | |
| element | Scope warnings (dirs) | ✓ prd_scope_warnings_* | ✓ PDD:547 | ✗ | ios-missing | | |
| element | Capacity card (pool used/limit, wait queue) | ✓ prdCapacityCard | ✓ PDD:2845 PrdCapacityCard | ✗ | ios-missing | | |
| element | Active session card + "no active session" hint | ✓ prdActiveSessionCard, prd_no_active_session_* | ✓ PrdActiveSessionsCard | ✗ | ios-missing | | |
| element | Status graphs while decomposing/running (decomposed · stories · tasks · progress) | ✓ _renderStatusGraphs, automata_sg_* | ✓ PDD:747–759 | ✗ | ios-missing | | |
| element | Session resources (CPU/RAM; Android adds GPU, per-story CPU/RSS) | ✓ prdSgComputeResources prd_res_cpu/ram | ~ PDD:2563–2621, 3141–3181 + per-story (v1.23.28) | ✗ | misaligned | | Android richer; PWA has no GPU/per-story |
| element | Terminal-state hint · unstick steps | ✓ prd_terminal_state_hint, prd_unstick_* | ✓ PDD:368 (unstick not verified) | ✗ | ios-missing | | |
| element | Decisions tab (backend, cost, tokens, verdict; count; empty) | ✓ prd_decision_* | ✓ PDD:203,680 | ✗ | ios-missing | | |
| element | Rules tab (proposed rules, help) | ✓ prd_tab_rules, prd_rules_help | ~ AVM proposeRules (card, no tab) | ✗ | misaligned | | |
| element | Scan tab (Findings (N), help) | ✓ prd_tab_scan, "Findings" | ~ ScanResultCard FindingRow (card, no tab) | ✗ | misaligned | | |
| element | Graph tab (orchestrator DAG canvas) | ✗ in detail (orchestrator view lives in Settings app:25116) | ✓ PDD:204,723 PrdDagCanvas; OrchestratorGraphDialog PDD:1078 | ✗ | pwa-missing | needs-decision | D7 |
| element | Progress tab (per-story CPU/RSS + compute node) | ✗ | ✓ PDD:205 (v1.23.28) | ✗ | pwa-missing | needs-decision | D7 |
| element | Memory: stats tile (prd/story/session-local), report, recall search | ~ "Learnings" app:14289 only | ✓ PDD:2896–3010 (BL385–387) | ✗ | pwa-missing | needs-decision | D13 |
| element | Story card header: chevron, title, status pill, profile pill, LLM pill | ✓ app:10274 | ~ StoryRow + StoryStatusPill (profile/LLM pills not verified) | ~ storyGroup glyph+title+done/total | misaligned | | |
| element | Story body: description, verdicts, progress, planned files chips (⚠ conflict), files_touched, worker session link | ✓ app:10274–10388 | ~ StoryRow (conflict ⚠ not verified) | ~ description + tasks only | misaligned | | |
| interaction | Story: approve/reject when awaiting_approval (guided mode) | ✓ app:10297 | ✓ AVM approveStory/rejectStory | ✗ | ios-missing | | |
| interaction | Story: cancel (PRD running) | ✓ prdCancelStory | ✓ PDD:1732 | ✗ | ios-missing | | |
| interaction | Story edit group (editable states): ✎ title/desc · 📁 files · ⚙ exec profile · 🤖 LLM · 🗑 remove · + Add story | ✓ app:10310–10330, 10437–10560 | ~ PDD:2194 edit, 1621 files, remove, 671 add; ⚙ profile + 🤖 story-LLM absent (AVM lacks setStoryProfile/setStoryLlm) | ✗ | misaligned | | Android missing 2 of 5 |
| element | Task row: status glyph map ✓ ✗ ▶ ○ ⟳ 🧪 ⛔ ⏳ + "Waiting for capacity" badge | ✓ app:10631 | ✓ PDD:1851 (+cancelled ○) | ✓ PrdStatusStyle.taskGlyph (+wait reason text) | aligned | | |
| element | Task row: LLM badge · ↳ spawn badge · → child link · verdicts | ✓ app:10593–10604 | ~ (child/spawn not verified; parent link on card) | ✗ | misaligned | | |
| interaction | Task: → worker session link (navigates to session detail) | ✓ app:10606 | ✓ (View sessions) | ~ "session <id>" text, not tappable | misaligned | | |
| interaction | Task: ↺ Retry (failed/blocked) · ✕ Cancel · ↺ Requeue (completed/cancelled) | ✓ app:10617–10628 | ✓ PDD:2035–2077 | ✗ | ios-missing | | |
| interaction | Task edit group: ✎ spec+LLM · 📁 files · 🗑 remove · + Add task | ✓ app:10580–10590 | ✓ PDD:2104, 1621, 1722–1799 | ✗ | ios-missing | | |
| element | Task body: planned files chips · output files chips → file viewer | ✓ _fileChip app:10350 | ✓ FilePill → FileViewerSheet (#181) | ✗ | ios-missing | | |
| element | Task body: ⚠ error row · verification row (ok/fail, severity, issues) | ✓ app:10665–10680 | ✓ VerdictBadge | ~ error only | misaligned | | |
| interaction | Default expansion: active stories/tasks open | ✓ app:10284, 10572 | ✓ | ✓ (in_progress stories) | aligned | | |
| data | Live update: `prd_update` WS → in-place patch (pills, graphs; overview re-render on status change) | ✓ app:792, 17668 | ✓ AVM:1134 PrdHub via sentinel WS | ✗ REST poll 15 s list / 10 s detail | ios-missing | | |
| data | Progress polling while running | ✗ (WS only) | ✓ AVM:1026 5 s | ~ 10 s detail poll | misaligned | | |
| data | List reload preserves expanded cards on WS refresh | ✓ app:16817 | ✓ (expanded rows preserved) | n/a | aligned | | |
| motion | Decompose: SSE planning stream ("Planning… stories will appear here shortly") | ✓ app:11108 _startDecomposeStream | ~ PDD:2769 "Decomposing PRD…" static | ~ static hint | misaligned | | |
| interaction | Watch automata (per-PRD change notifications) | ✗ | ✓ AVM:1187 toggleWatchAutomata | ✗ | pwa-missing | needs-decision | D14 |
| element | Orchestrator graphs card · Pipeline manager card | ✓ app:24574/25116 Settings → Automata | ✓ SettingsScreen:405–406 (ui/automata) | ✗ | ios-missing | | Cross-ref section 07 |
| string | i18n coverage | ✓ en/fr/es `locales/*.json` | ✓ 262 keys, 4 extra locales | ✗ 36 hard-coded en strings | ios-missing | | |

## Coverage
rows: 96 · aligned: 9 · ios-missing: 44 · android-missing: 4 · pwa-missing: 7 · misaligned: 31 · n/a: 1

## Decisions needed
1. **Terminology/tab label** — PWA "Automata | Templates"; Android "PRDs | Templates"; iOS "PRDs | Types" — options: (a) all use "Automata" per PWA (b) adopt "PRDs" in PWA (c) keep per-platform — refs app:17159, AS:246, AutomataView.
2. **Parent-PRD link on card** — Android shows ↗parent id on child PRDs; PWA card has none — options: (a) add to PWA card (b) drop from Android — refs AS:128.
3. **Inline Reject/Revise on list card** — Android only; PWA card has Cancel/Approve/Pin and keeps reject/revise in detail — options: (a) add to PWA (b) remove from Android — refs AS:12–26, app:16785.
4. **Status pill colors + pulse** — PWA uses session state-badge CSS vars (approved=accent blue, planning/decomposing=accent2, pulse on active); Android/iOS share a hex map (approved #8B5CF6, decomposing #A855F7, no pulse) — options: (a) apps adopt PWA tokens + pulse (b) PWA adopts app map — refs app:10114, AS:1396, PrdStatusStyle.
5. **Type registry placement** — PWA: Settings → Automata; iOS: "Types" tab inside Automata; Android surface unclear — options: (a) move iOS to Settings per PWA (b) keep a Types tab everywhere — refs app:24702, AutomataView.
6. **Wizard "memory promote to" field** — Android-only — options: (a) add to PWA wizard (+iOS) (b) remove — refs NPD:694.
7. **Detail tabs** — PWA: Overview/Stories/Decisions/Rules/Scan; Android: Overview/Stories/Decisions/Graph/Progress (Rules/Scan as cards) — options: (a) all adopt PWA five tabs, Graph/Progress become cards (b) PWA adopts Graph + Progress tabs — refs prd_tab_*, PDD:201–205.
8. **Approve with optional note** — Android prompts for a note; PWA posts `{actor:'operator'}` — options: (a) add note to PWA + iOS (b) drop on Android — refs PDD:868, app:10917.
9. **Edit PRD permission_mode** — Android edit dialog exposes it; PWA edit modal does not — options: (a) add to PWA (b) remove — refs PDD:1421.
10. **Repair depends_on button** — Android only (#202) — options: (a) add to PWA detail edit menu (+iOS) (b) keep Android-only — refs PDD:501.
11. **Pause/Resume** — in spec §6.6 and defined in app.js but unreachable; no app has it — options: (a) remove from spec and app.js (b) wire up everywhere — refs app:16808.
12. **Markdown/Mermaid libraries from CDN at runtime** — PWA loads marked@12 + mermaid@10.9.6 from jsdelivr; Android WebView loads mermaid@10 from jsdelivr; iOS pending — options: (a) vendor into each client (as done for xterm) (b) keep CDN — refs app:15573, MD:329.
13. **Memory in PRD detail** — Android ships stats tile/report/recall (BL385–387); PWA has only "Learnings" — options: (a) port Android memory cards to PWA + iOS (b) trim Android to PWA — refs PDD:2896–3010, app:14289.
14. **Watch automata** (per-PRD notification subscription) — Android only — options: (a) add to PWA + iOS (b) drop — refs AVM:1187.
