# 05 — Automata (PRDs, templates, orchestrator touchpoints)

PWA spec §6 · live PWA `internal/server/web/app.js` (automata list 16456–17250, wizard 17278–17612, detail 17848–19006, stories/tasks 10274–10810, actions 11054–11370, markdown 15556–15610; `style.css` 391–585) · Android `ui/autonomous/*`, `ui/automata/*`, `ui/orchestrator/*`, `ui/memory/*` (PRD parts) · iOS `screens/automata/AutomataView.swift`, `PrdListView.swift`, `PrdDetailView.swift`.

Refs: `app:N` = app.js line, `css:N` = style.css line, `AS:N` = AutonomousScreen.kt, `AVM:N` = AutonomousViewModel.kt, `PDD:N` = PrdDetailDialog.kt, `NPD:N` = NewPrdDialog.kt, `MD:N` = MarkdownComponents.kt. iOS refs are view/type names.

| Cat | Feature | PWA | Android | iOS | Status | Decision | Notes |
|---|---|---|---|---|---|---|---|
| nav | Automata nav entry gated on `/api/autonomous/config.enabled` | ✓ app:21477 | ✓ AppRoot.kt:403 probeAutonomous (hidden when `autonomous.enabled=false`; visible by default / All servers) | ✗ RootView always shows Automata | ios-missing |  | iOS remaining: gate tab on /api/config autonomous.enabled |
| nav | Header title "Automata" + robot identity button 🤖 + search/filter button | ✓ app:225 | ✓ AS:197 robot identity button first in actions (title = server picker) | ~ HeaderView("Automata") + docs/bell/dot; no robot identity / filter button | ios-missing |  | iOS lacks robot identity shortcut |
| nav | Server scope: "All servers" aggregated (`/prds/aggregated`) | ✓ app:16825 | ✓ AS:1445 sessions_all_servers | ✗ AutomataView segmented profile picker; no aggregated scope | ios-missing |  | Sessions got All servers (d52b2d93), Automata not |
| interaction | FAB ⚡ → Launch Automaton wizard | ✓ app:17278 | ✓ AS:219 autonomous_fab_new → NewPrdDialog | ✓ PrdListView ⚡ FAB → NewPrdView | aligned |  |  |
| element | Tab strip: "Automata" · "Templates" | ✓ app:17159 automata_tab_* | ✓ AS:248 autonomous_tab_prds = "Automata" · Templates | ~ AutomataView segmented "PRDs" · "Templates" (Types tab removed per D25a) | misaligned | decided D22a | iOS label still "PRDs" — rename to "Automata" |
| interaction | ☑ Select (batch mode) toggle | ✓ app:17190 | ✓ AS:256 AutomataActionBtn | ✓ PrdListView "☑ Select" chip | aligned |  |  |
| interaction | ⊞ Filter bar toggle | ✓ app:17178 | ✓ AS:256 | ✓ PrdListView "⊞ Filter" chip | aligned |  |  |
| interaction | ⏱ History toggle (show completed/cancelled/archived) | ✓ app:17207, filter app:16482 | ✓ AS:109 historyOn, AS:267 | ✓ PrdListView "History" chip (activeStatuses gate) | aligned |  |  |
| element | Filter bar: text search | ✓ app:16488 automata_filter_search | ✓ "Search automata…" field (title/name + id) | ✓ PrdListView search field (title/name/id) | aligned |  | iOS has it · Android done 2026-10-04 (android-missing sweep) |
| element | Filter bar: status badges draft/planning/needs_review/approved/running/blocked (+archived) | ✓ app:17219, css:504–509 | ✓ AS:585–635 FilterChip | ✓ PrdListView status chips incl. archived (filterColor = css:504–509) | aligned |  | Android chip colors vs css:504–509 (unverified) |
| element | Filter bar: type badges software/research/operational/personal | ✓ app:17228 | ✓ AS:641 | ✓ PrdListView type chips | aligned |  |  |
| element | Filter bar: "All" select-all checkbox (select mode) | ✓ app:16606 | ✗ (only server All chip AS:1515; no PRD select-all) | ~ "All"/"None" in batch bar (not the filter bar) | misaligned |  |  |
| data | List sort: pinned first → state rank (active first) → updated | ✓ app:16491–16500 | ✓ pinned → PWA `_AUTOMATA_STATE_RANK` → updated_at/created_at desc | ✓ PrdListViewModel.visible: pinned → PWA state rank → updated | aligned |  | Android rank table + tiebreak differ from PWA |
| element | Card: type badge, per-type color (software #6366f1 · research #f59e0b · operational #10b981 · personal #ec4899) | ✓ app:16727 | ✓ AS:1225 TypeBadge (#6366f1/#f59e0b/#10b981/#ec4899) | ✗ PrdRow metaLine plain text | ios-missing |  |  |
| element | Card: "template" badge (#7c3aed) when `is_template` | ✓ app:16730 | ✓ AS:858 autonomous_template_label | ✗ (templates filtered out of list) | ios-missing |  |  |
| element | Card: title 15px/700 · status pill right-justified | ✓ app:16769 | ✓ AS:98,112 | ✓ PrdRow | aligned | | |
| element | Card meta row: `id` (code) + updated_at, mono, right-justified | ✓ app:16773 | ✓ AS:874–931 id.take(8) + server + updated_at (en-GB); ↗parent chip AS:887 | ~ PrdRow metaLine type·backend·counts + ↗parent chip; no id / updated_at | misaligned |  | iOS lacks id/ts; ↗parent link on both apps per D71a (PWA → #172) |
| element | Card: progress bar + "done/total tasks · pct%" (complete → success fill) | ✓ app:16682, css:513–526 | ✓ 4dp bar (success at 100 %) + "done/total tasks · pct%" | ~ PrdRow ProgressView, no pct label | ios-missing |  | iOS also lacks "done/total · pct%" label · Android done; iOS lacks the label (2026-10-04) |
| element | Card: current position line "▶ Story i: … · Task j: … (verifying/testing)" when running | ✓ app:16694 | ✓ AS:936 currentPositionLine | ✗ | ios-missing |  |  |
| element | Card: compact lifecycle strip plan→review→approve→run→done | ✓ app:11212, css:544–585 | ✓ AS LifecycleStrip | ✓ PrdLifecycleStrip (card, display-only) | aligned | | |
| interaction | Lifecycle steps clickable → plan/approve/reject/revise/run/instantiate/delete actions; `danger` style | ✓ app:11212 (clickable/danger) | ~ AS:1306 LifecycleStrip Plan/Approve/Run/■Cancel clickable, danger style; no Instantiate step | ~ PrdLifecycleStrip onAction on card + detail (.danger look); no Instantiate step | misaligned |  | PWA template cards show Instantiate step (app:11233) |
| element | Card action row: ✕ Cancel (non-terminal) · ✓ Approve amber (needs_review/revisions_asked/waiting_input) · 📌 pin | ✓ app:16785–16799 | ✓ AS:982–1010 Cancel + Approve + inline Reject/Revise; pin | ✓ PrdLifecycleStrip inline Approve(note)/Reject/Revise/Cancel via PrdReviewDialogs; 📌 via context menu | pwa-missing | decided D72a | inline Reject/Revise → #172 |
| interaction | Pin (persisted localStorage) | ✓ app:16456 | ✓ AVM:1202 pinnedAutomataStore (per profile) | ✓ PrdListViewModel.togglePin (UserDefaults) | aligned |  |  |
| element | Card: `<details>` "Stories & tasks (N)" expandable tree | ✓ app:16806 | ✓ AS:1045 "Stories & tasks (N)" accordion | ✗ (detail only) | ios-missing |  |  |
| interaction | Tap card (non-button) → detail | ✓ app:16766 | ✓ AS:57 onClick / long-press selects | ✓ NavigationLink | aligned | | Android long-press = select; PWA uses checkbox |
| token | Card left border color per status (`prd-card-status-*`) | ✓ css:400–411 | ✓ AS:834 4dp status-colour rail (drawBehind) | ✗ | ios-missing |  |  |
| token | Status pill = session state-badge style, 11px/600, 1px currentColor border; pulse on running/planning/decomposing | ✓ app:10114 (`state-badge-*`, `dw-running-pulse`) | ✓ AS:1184 PrdStatusPill (state-badge tint + pulse) | ~ PrdStatusChip app hex map, 18% fill, no border/pulse | misaligned | decided D23a | Android done; iOS must adopt PWA tokens |
| motion | Running/planning pulse animation on status pill | ✓ `.state-badge-running` | ✓ PrdStatusPill rememberRunningPulseAlpha | ✗ | ios-missing | decided D23a |  |
| element | Batch bar: Run · Approve · Cancel · Archive · Delete · Done/exit | ✓ app:16522 automata_batch_* | ✓ AS:321–342 (+ "✕ Clear") | ✓ PrdListView batchBar Run · Approve · Cancel · Archive · Delete · Done (+All/None) | aligned |  |  |
| interaction | Batch delete confirm dialog | ✓ automata_confirm_batch_delete | ✓ AS:415 | ✓ PrdListView confirmBatchDelete alert | aligned |  |  |
| interaction | Cancel confirm: special copy when status=planning ("Planning is running… will abort") | ✓ app:16802 | ~ AS:369 automata_confirm_cancel_body generic | ~ PrdDetailView "Running tasks are stopped…" | misaligned |  | Planning-abort copy missing on both apps |
| string | Empty state (no automata / no templates) | ✓ automata_empty_active/history/templates | ~ AS:726 "No plans match." (automata_empty_* strings defined but unused) | ~ "No PRDs" / "No active automata — turn on History…" | misaligned |  | PWA copy wins; Android has the strings, just unwired |
| element | Templates tab: card (title, built-in badge, description, type badge, tags, var count, use count) | ✓ app:16916 | ~ TemplatesTab.kt title, type, tags, description (no built-in badge / use count) | ~ TemplatesView card: title, type, description, tags, var count (no built-in badge / use count) | misaligned |  | Both apps lack built-in badge + use count |
| interaction | Template actions ▶ Use (instantiate modal) · ✎ edit · ✕ delete (non-builtin) · ＋ Template | ✓ app:16916–16990 | ✓ InstantiateTemplateDialog, CreateEditTemplateSheet | ✓ TemplatesView ▶ Use (InstantiateTemplateView) · ✎ Edit · swipe delete · ＋ Template | aligned |  |  |
| element | Template create/edit form: title, type, description, spec, tags, vars | ✓ automata_tmpl_field_* | ✓ CreateEditTemplateSheet.kt | ✓ TemplateEditView title/type/description/tags/spec (vars from `{{name}}`) | aligned |  |  |
| interaction | Clone PRD → template | ✓ prd_btn_clone_template | ✓ PDD:478 | ✓ PrdDetailView ⋯ "Save as template" (IosTemplates.clonePrd) | aligned |  |  |
| element | Type registry (id, label, description, color, built-in) | ✓ app:24702 in Settings → Automata | ✓ settings/AutomataTypesCard (SettingsScreen:420) | ✓ SettingsAutomataTypesView (Settings › Automata) | aligned | decided D25a | per D25a |
| element | Launch wizard: intent textarea + auto-detected type + type buttons | ✓ app:17278 wizardIntent/wizardDetectedType/wizard-type-btn | ✓ NPD:142 type auto-detect + NPD:307 type buttons | ✗ NewPrdView title + spec only | ios-missing |  |  |
| element | Wizard: title, workspace/profile (— project directory —), directory + Browse | ✓ wizardTitle/wizardProfile/wizardDirRow | ✓ NPD:318,332,339,262 | ~ NewPrdView title, profile picker, directory field; no Browse | ios-missing |  | iOS lacks directory Browse |
| element | Wizard: execution backend, model, effort | ✓ automata_wizard_backend/model/effort | ✓ NPD:392,426,463 | ✓ NewPrdView Execution: backend, model, effort | aligned |  |  |
| element | Wizard: planning backend + decomposition model | ✓ prd_new_planning_label, prdNewDecompModel* | ✓ NPD:534,563 | ✓ NewPrdView Planning: planning backend + decomposition model | aligned |  |  |
| element | Wizard: guided mode · story approval · rules enabled · scan enabled toggles (advanced) | ✓ wizardGuidedMode/StoryApproval/RulesEnabled/ScanEnabled | ✓ NPD:625–671 Advanced: guided, scan, rules, story approval | ✗ | ios-missing |  |  |
| element | Wizard: memory "promote to" scope | ✗ | ✓ NPD:694 new_prd_memory_promote_to | ✓ NewPrdView Memory section: seed / harvest + promote-to picker | pwa-missing | decided D73a | → #172 |
| interaction | Wizard: "Use template" link → Templates tab · skills hint link · help tip | ✓ automata_wizard_use_template/skills_hint_link/help_tip | ✓ NPD:260 template strip, NPD:238 `?` help, NPD:721 skills hint | ✗ | ios-missing |  |  |
| nav | Detail view replaces list; breadcrumb/back (`automata_detail_back`) | ✓ app:17848, app:19007 | ~ PDD full-screen dialog, Close | ✓ push + back | aligned |  | per D4a native containers (dialog / push) |
| element | Detail header: type + status badges, title (tap to edit tip), id, last activity, lifecycle strip | ✓ app:17848+ | ✓ PDD:201–260 | ~ PrdDetailView header + PrdLifecycleStrip (no id / last activity) | misaligned |  | iOS lacks id / last activity |
| element | Detail tabs: Overview · Stories · Decisions · Rules · Scan(cond.) | ✓ prd_tab_* | ✓ Overview · Stories · Decisions · Scan · Rules; Graph + Progress as Overview cards | ✗ single scroll | ios-missing | decided D24a | Android: add Rules/Scan tabs, Graph/Progress → cards; iOS: build five tabs · Android done (D24a); Scan/Rules always shown (PrdDto lacks scan/rules flags) (2026-10-04) |
| interaction | Actions: ✓ Approve | ✓ app:10917 `{actor:'operator'}` (no note) | ✓ PDD:395,868 approve with optional note | ✓ approve-with-note (`PrdReviewDialogs`) | pwa-missing | decided D74a | → #172 |
| interaction | Actions: ✗ Reject (reason prompt) | ✓ app:11188 prdActionPrompt | ✓ PDD:448,891 | ✓ alert + TextField | aligned | | |
| interaction | Actions: ↺ Request revision (note prompt) | ✓ prd_btn_request_revision | ✓ PDD:455,924 | ✓ alert "Request revision" | aligned | | |
| interaction | Actions: ▶ Start Planning / Re-plan (decompose) | ✓ app:18686 | ✓ PDD:435 prd_detail_decompose | ✓ PrdLifecycleStrip ▶ Plan / Re-plan (revisions_asked) + Decompose button | aligned |  |  |
| interaction | Actions: ▶ Run | ✓ | ✓ PDD:409 | ✓ | aligned | | |
| interaction | Actions: ✕ Cancel | ✓ | ✓ PDD:426 | ✓ | aligned | | copy drift noted above |
| interaction | Actions: ↺ Reset to draft | ✓ prd_btn_reset_to_draft | ✓ PDD:489 | ✓ PrdDetailView ⋯ "Reset to Draft" | aligned |  |  |
| interaction | Actions: Archive | ✓ app:5414/batch archive | ✓ PDD:380 onArchive (completed/rejected/cancelled) + batch AS:336 | ~ batch only (PrdListView batch bar) | ios-missing |  | iOS lacks detail Archive |
| interaction | Actions: Delete (with memory strategy keep/purge/archive, roles, scope) | ✓ prdDeleteArchiveOpts/Roles/Scope | ✓ PDD:992–1027 | ✓ MemoryStrategyDeleteSheet (strategy, roles, scope) | aligned |  |  |
| interaction | Actions: Edit title / Edit spec (prdEditMenu) | ✓ prd_btn_edit, prd_btn_edit_spec | ✓ PDD:1395–1421 EditPrdDialog | ✓ PrdDetailView ⋯ Edit title / spec → EditPrdView | aligned |  |  |
| element | Edit PRD: permission_mode field | ✗ (not in prdEdit* ids) | ✓ PDD:1421 new_prd_permission_mode_label | ✓ EditPrdView permission-mode picker (sent only when changed) | pwa-missing | decided D75a | → #172 |
| interaction | Actions: Set LLM (backend/effort/model + planning backend/decomp model) | ✓ prdSetModel*, prdSetDecompModel* | ✓ PDD:1192–1347 LlmOverrideDialog | ✓ SetPrdLlmView (backend/model/effort + planning backend/decomp model) | aligned |  |  |
| interaction | Actions: Repair depends_on (#202) | ✗ | ✓ PDD:501 | ✓ detail ⋯ menu "Repair Dependencies" | pwa-missing | decided D76a | → #172 |
| interaction | Actions: Run scan / Run rules (propose) | ✓ prd_btn_run_scan, prd_btn_run_rules | ✓ AVM triggerScan/proposeRules, ScanResultCard | ✓ PrdScanCard | aligned | | |
| interaction | Actions: View sessions | ✓ automata_actions_view_sessions | ✓ PDD:608 | ✓ → View sessions (SessionsNav) | aligned | | |
| interaction | Actions: Pause / Resume | ~ app:16973 automataPause/Resume defined, 0 callers | ✗ | ✗ | pwa-missing | decided D52b | → #172; Android + iOS must also wire Pause/Resume |
| element | Overview: spec collapsed/ellipsis/"show full"/"hide" | ✓ prdSpecCollapsed/Full, automata_spec_show_full | ✓ PDD:324 | ~ DisclosureGroup "Spec" | misaligned | | |
| element | Overview: markdown + GFM tables + Mermaid rendering of spec | ✓ app:15556 marked@12 + mermaid@10.9.6 (CDN) | ✓ MD:170 MarkdownView, MD:244 GfmTableView, MD:319 MermaidView (WebView, CDN) | ✗ plain Text (PrdDetailView specSection) | ios-missing | decided D53b | iOS: render markdown/Mermaid via runtime CDN like Android |
| element | Overview meta: backend, model, effort, concurrency, project, depth, created, guided mode, skills, read-only dirs, writable dirs | ✓ automata_detail_* | ✓ PDD:2228–2479 | ~ type/backend/model/effort/guided/projectDir only | misaligned | | |
| element | Settings panel: type, guided mode, continue-on-story-failure, skills picker, priority, read/write dirs, concurrency, model, decomp model | ✓ prdSettings* (app:11939) | ✓ PDD:2228–2479 rows (concurrency unverified) | ~ PrdSettingsView: type, guided, continue-on-failure, priority, read/write dirs (no skills, concurrency; model via Set LLM) | misaligned |  | iOS lacks skills + concurrency (unverified Android concurrency) |
| element | Scope warnings (dirs) | ✓ prd_scope_warnings_* | ✓ PDD:547 | ✓ scopeWarningsBanner | aligned | | |
| element | Capacity card (pool used/limit, wait queue) | ✓ prdCapacityCard | ✓ PDD:2845 PrdCapacityCard | ✓ PrdDetailView capacityCard (pools held/limit, waiting) | aligned |  |  |
| element | Active session card + "no active session" hint | ✓ prdActiveSessionCard, prd_no_active_session_* | ✓ PrdActiveSessionsCard | ✓ PrdActiveSessionCard | aligned | | |
| element | Status graphs while decomposing/running (decomposed · stories · tasks · progress) | ✓ _renderStatusGraphs, automata_sg_* | ✓ PDD:747–759 | ✓ PrdDetailView statusGraphs (decomposed · stories · tasks; progress bar in header) | aligned |  |  |
| element | Session resources (CPU/RAM; Android adds GPU, per-story CPU/RSS) | ✓ prdSgComputeResources prd_res_cpu/ram | ~ PDD:2563–2621, 3141–3181 + per-story (v1.23.28) | ~ PrdActiveSessionCard CPU/RAM/GPU bars (no per-story) | misaligned |  | Android richer; PWA has no GPU/per-story |
| element | Terminal-state hint · unstick steps | ✓ prd_terminal_state_hint, prd_unstick_* | ✓ PDD:368 (unstick not verified) | ✓ hint (completed/archived) + stuck Unstick/Cancel | aligned | | Android unstick steps (unverified) |
| element | Decisions tab (backend, cost, tokens, verdict; count; empty) | ✓ prd_decision_* | ✓ PDD:203,680 | ~ PrdDecisionsSection: all fields, collapsible section not a tab (layout per D7) | misaligned | decided D24a | iOS needs it as a tab |
| element | Rules tab (proposed rules, help) | ✓ prd_tab_rules, prd_rules_help | ~ AVM proposeRules (card, no tab) | ~ PrdScanCard "Propose rules" → sheet (card, no tab) | misaligned | decided D24a | both apps need Rules tab |
| element | Scan tab (Findings (N), help) | ✓ prd_tab_scan, "Findings" | ~ ScanResultCard FindingRow (card, no tab) | ~ PrdScanCard (card; tabs per D24) | misaligned | decided D24a | both apps need Scan tab |
| element | Graph tab (orchestrator DAG canvas) | ✗ in detail (orchestrator view lives in Settings app:25116) | ✓ PDD:207 Graph tab (PrdDagCanvas) | ✗ | misaligned | decided D24a | Android: move to a card in Overview |
| element | Progress tab (per-story CPU/RSS + compute node) | ✗ | ✓ PDD:208 Progress tab when running | ✗ | misaligned | decided D24a | Android: move to a card |
| element | Memory: stats tile (prd/story/session-local), report, recall search | ~ "Learnings" app:14289 only | ✓ PDD:2896–3010 (BL385–387) | ✓ `PrdMemorySection` (stats tile, Learning Report, Memory Recall) | pwa-missing | decided D77a | → #172 |
| element | Story card header: chevron, title, status pill, profile pill, LLM pill | ✓ app:10274 | ~ StoryRow + StoryStatusPill (profile/LLM pills not verified) | ~ storyGroup glyph+title+done/total | misaligned | | iOS lacks status/profile/LLM pills; Android pills (unverified) |
| element | Story body: description, verdicts, progress, planned files chips (⚠ conflict), files_touched, worker session link | ✓ app:10274–10388 | ~ StoryRow (conflict ⚠ not verified) | ~ description, planned-files chips (⚠ conflict), files touched, tasks (no verdicts / progress / worker link) | misaligned |  | Android ⚠ conflict (unverified) |
| interaction | Story: approve/reject when awaiting_approval (guided mode) | ✓ app:10297 | ✓ AVM approveStory/rejectStory | ✓ storyActions ✓ Approve / ✗ Reject (awaiting_approval) | aligned |  |  |
| interaction | Story: cancel (PRD running) | ✓ prdCancelStory | ✓ PDD:1732 | ✓ storyActions ⏹ Cancel story (confirm) | aligned |  |  |
| interaction | Story edit group (editable states): ✎ title/desc · 📁 files · ⚙ exec profile · 🤖 LLM · 🗑 remove · + Add story | ✓ app:10310–10330, 10437–10560 | ~ PDD:2194 edit, 1621 files, remove, 671 add; ⚙ profile + 🤖 story-LLM absent (AVM lacks setStoryProfile/setStoryLlm) | ~ ✎ Edit · 📁 Files · + Add task · + Add story (no ⚙ profile, 🤖 LLM, 🗑 remove) | misaligned |  | Android lacks ⚙/🤖; iOS lacks ⚙/🤖/🗑 |
| element | Task row: status glyph map ✓ ✗ ▶ ○ ⟳ 🧪 ⛔ ⏳ + "Waiting for capacity" badge | ✓ app:10631 | ✓ PDD:1851 (+cancelled ○) | ✓ PrdStatusStyle.taskGlyph (+wait reason text) | aligned | | |
| element | Task row: LLM badge · ↳ spawn badge · → child link · verdicts | ✓ app:10593–10604 | ~ VerdictBadge only; no LLM / ↳ spawn / → child | ✗ | misaligned |  |  |
| interaction | Task: → worker session link (navigates to session detail) | ✓ app:10606 | ✓ (View sessions) | ✓ tappable → Sessions filter | aligned | | |
| interaction | Task: ↺ Retry (failed/blocked) · ✕ Cancel · ↺ Requeue (completed/cancelled) | ✓ app:10617–10628 | ✓ PDD:2035–2077 | ✓ PrdTaskRow ↻ Retry · ⏹ Cancel · ↻ Re-run | aligned |  |  |
| interaction | Task edit group: ✎ spec+LLM · 📁 files · 🗑 remove · + Add task | ✓ app:10580–10590 | ~ PDD:2104 spec edit, 1621 files, 1722 remove; no task LLM | ~ ✎ spec · 📁 files · 🗑 · + Add task; no task LLM | misaligned |  | task LLM missing on both apps |
| element | Task body: planned files chips · output files chips → file viewer | ✓ _fileChip app:10350 | ✓ FilePill → FileViewerSheet (#181) | ✓ PrdFileChips → PrdFileViewerSheet | aligned | | |
| element | Task body: ⚠ error row · verification row (ok/fail, severity, issues) | ✓ app:10665–10680 | ✓ VerdictBadge | ~ error only | misaligned | | |
| interaction | Default expansion: active stories/tasks open | ✓ app:10284, 10572 | ✓ | ✓ (in_progress stories) | aligned | | |
| data | Live update: `prd_update` WS → in-place patch (pills, graphs; overview re-render on status change) | ✓ app:792, 17668 | ✓ AVM:1134 PrdHub via sentinel WS | ✓ subscribePrdUpdates → in-place patch (list + detail) | aligned |  |  |
| data | Progress polling while running | ✗ (WS only) | ✓ AVM:1026 5 s | ~ 30 s REST fallback alongside WS | misaligned |  |  |
| data | List reload preserves expanded cards on WS refresh | ✓ app:16817 | ✓ (expanded rows preserved) | n/a | aligned | | |
| motion | Decompose: SSE planning stream ("Planning… stories will appear here shortly") | ✓ app:11108 _startDecomposeStream | ~ PDD:2769 static "Decomposing PRD…" | ~ static "Decomposing — stories appear when planning finishes." | misaligned |  |  |
| interaction | Watch automata (per-PRD change notifications) | ✗ | ✓ AVM:1187 toggleWatchAutomata | ✓ bell toggle on PRD cards (`LocalSessionPrefs`, per profile) | pwa-missing | decided D61a | → #172 |
| element | Orchestrator graphs card · Pipeline manager card | ✓ app:24574/25116 Settings → Automata | ✓ SettingsScreen:405–406 (ui/automata) | ✓ OrchestratorGraphsView / PipelinesView (Settings links) | aligned | | Cross-ref section 07 |
| string | i18n coverage | ✓ en/de/es/fr/ja `locales/*.json` | ✓ 262 keys, 4 extra locales | ✓ L() + Resources/{de,es,fr,ja}.lproj (automata strings incl.) | aligned |  | iOS localized 2026-10-04 |

## Coverage
rows: 99 · aligned: 48 · ios-missing: 17 · android-missing: 0 · pwa-missing: 8 · misaligned: 26 · n/a: 0

## Decisions (resolved 2026-10-04)
1. Terminology/tab label → **D22a** "Automata | Templates" everywhere (iOS still "PRDs").
2. Parent-PRD link on card → **D71a** add to PWA (→ #172); both apps have it.
3. Inline Reject/Revise on list card → **D72a** add to PWA (→ #172); both apps have it.
4. Status pill colours + pulse → **D23a** apps adopt PWA state-badge tokens + pulse (Android done, iOS open).
5. Type registry placement → **D25a** Settings › Automata (both apps done).
6. Wizard "memory promote to" → **D73a** add to PWA (→ #172) + iOS (done).
7. Detail tabs → **D24a** PWA five tabs; Graph/Progress become cards (both apps open).
8. Approve with optional note → **D74a** add to PWA (→ #172) + iOS (done).
9. Edit PRD permission_mode → **D75a** add to PWA (→ #172); both apps have it.
10. Repair depends_on → **D76a** add to PWA (→ #172) + iOS (done).
11. Pause/Resume → **D52b** wire up everywhere (open on all three).
12. Markdown/Mermaid libraries → **D53b** keep runtime CDN (iOS renderer open).
13. Memory in PRD detail → **D77a** port to PWA (→ #172) + iOS (done).
14. Watch automata → **D61a** adopt in PWA (→ #172) + iOS (done).
