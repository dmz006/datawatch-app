# Backlog Plan — v0.58.0 → v1.0.0 roadmap

*Written 2026-05-04. Supersedes any informal ordering in sprint-plan.md §"v0.11–v0.33 feature sprints"
for items not yet assigned to a version. This document is the single authoritative sequencing
of open work until v1.0.0.*

**Current version:** v0.64.0 · **Rules:** each phase → new minor · i18n in one pass at end ·
Settings strict-mirrors PWA (Automata + Plugins tabs added; mobile-specific items like biometric stay) ·
Issues closed when code ships.

---

## Source inventory

Items drawn from two sources:

| Source | Items |
|--------|-------|
| Open GH issues (dmz006/datawatch-app) | #31, #40, #41, #43, #44, #45, #46, #47, #48, #49 |
| docs/plans/README.md non-GH backlog | BL19, BL21, B6, B31, store assets |

**#42 closed 2026-05-04** — fix shipped in commit `a6c8e49`.
**#31, #41, #49 shipped in v0.58.0 (2026-05-04).**
**#47, #48 shipped in v0.59.0 (2026-05-04).**
**#40 shipped in v0.60.0 (2026-05-04).**
**#44 shipped in v0.61.0 (2026-05-04).**
**#45 shipped in v0.62.0 (2026-05-04).**
**#43 shipped in v0.63.0 (2026-05-04).**
**BL21 shipped in v0.64.0 (2026-05-04).**

---

## ✅ v0.58.0 — Quick wins (issues #31, #41, #49) — SHIPPED 2026-05-04

**Goal:** ship three small, independent items in one minor. No new screens; all changes are
to existing composables or VM logic.

### #31 — Quick-commands from server config

**Upstream unblocked:** `datawatch#28` closed (merged).

| Step | Detail |
|------|--------|
| 1 | Add `TransportClient.fetchQuickCommands(): List<QuickCommandDto>` — `GET /api/config` key `quick_commands` (array of `{label, value}`). Returns empty list on 404 / key absent for backward compat with older daemons. |
| 2 | Add `QuickCommandDto(val label: String, val value: String)` to shared DTO layer. |
| 3 | Add `QuickCommandsViewModel.systemCommands: StateFlow<List<QuickCommandDto>>` that fetches on init and exposes the result; falls back to the current hard-coded list when empty. |
| 4 | `QuickCommandsSheet` consumes the VM's `systemCommands` instead of the hard-coded constant. |
| 5 | Unit test: mock returns non-empty list → rendered; mock returns empty / 404 → fallback list rendered. |

Files: `shared/.../transport/`, `shared/.../dto/QuickCommandDto.kt`,
`composeApp/.../ui/sessions/QuickCommandsViewModel.kt`,
`composeApp/.../ui/sessions/QuickCommandsSheet.kt`.

Acceptance: system commands section of the Quick Commands sheet reflects server config when
available; hard-coded fallback when server is old / config key absent.

---

### #41 — PRD card style alignment (B66 follow-up, parent v5.27.8)

**Goal:** `PrdRow` / Autonomous cards use same shape as `SessionRow` and show status-driven
left-border colour.

| Step | Detail |
|------|--------|
| 1 | Confirm `pwaCard()` modifier is already applied to `PrdRow` (B66 shipped in v0.47.0 per plans/README.md — verify actual code). |
| 2 | If already applied: add a 4 dp `start` border to the card whose colour is driven by `PrdDto.status`. Map table: `draft`→`surfaceVariant`, `decomposing`→`tertiary`, `needs_review`/`revisions_asked`→`warning` amber, `approved`→`primary` teal, `running`→`success` green, `completed`/`cancelled`→`surfaceVariant`, `blocked`/`rejected`→`error` red. |
| 3 | Remove any redundant "PRDs" sub-header composable above the `LazyColumn` in `AutonomousScreen`. |
| 4 | Snapshot / screenshot test the colour mapping. |

Files: `composeApp/.../ui/autonomous/AutonomousScreen.kt`,
`composeApp/.../ui/autonomous/PrdRow.kt`.

Acceptance: PRD cards visually match Sessions cards in padding + radius; left border colour
reflects status; no redundant "PRDs" heading above the list.

---

### #49 — Session refresh on reconnect + dismiss (BL249 + BL250, parent v6.5.1)

**Goal:** two small behavioral fixes so detail view auto-refreshes without operator nav.

| Step | Detail |
|------|--------|
| BL249 | In `SessionDetailViewModel` WS reconnect handler (`startStream` → reconnect branch), after the new connection is established call `refreshFromServer()`. This gives the same "fetch sessions on reconnect" behavior as the PWA's `connect()` handler. |
| BL250 | In `SessionDetailViewModel.dismissInputRequiredBanner()`, after the dismiss API call (or mute call), also call `refreshFromServer()` so the banner doesn't linger on stale state. |
| Tests | Unit test for each: simulate reconnect event → verify refreshFromServer called; simulate banner dismiss → verify refreshFromServer called. |

Files: `composeApp/.../ui/sessions/SessionDetailViewModel.kt`.

Acceptance: after daemon restart, detail view auto-updates on WS reconnect; after banner
dismiss, state is immediately fresh.

---

## ✅ v0.59.0 — Settings reorganization + Automata UX (issues #48, #47) — SHIPPED 2026-05-04

**Goal:** bring Settings tab structure and Automata tab UX into strict alignment with PWA
v6.5.1 (BL247 + BL246).

### #48 — Settings tab reorganization (BL247, parent v6.5.1)

PWA merged four standalone nav tabs into existing tabs. Mobile must match exactly.
Mobile-specific items (Biometric, Security card, etc.) stay in place.

**New tab structure:**

| Tab | Cards it gains | Cards removed from their old tab |
|-----|---------------|----------------------------------|
| `General` | Secrets card, Tailscale card | (currently in Comms or standalone) |
| `Comms` | Routing card | (currently standalone "Routing" tab) |
| `Automata` (new tab) | Orchestrator card, Pipelines card, Autonomous config card, PRD-DAG config card | (currently standalone tabs) |
| `Plugins` (new tab) | Plugin Framework config card | (currently standalone tab) |
| Remove | Standalone "Routing", "Orchestrator", "Pipelines", "Plugin Framework" tabs | — |

| Step | Detail |
|------|--------|
| 1 | Add `SettingsTab.AUTOMATA` and `SettingsTab.PLUGINS` to the tab enum / sealed class. Wire bottom icons if needed (or use existing icon pool — PWA uses ⚙ for both). |
| 2 | Move `RoutingCard` from its own tab into `CommsTab`. |
| 3 | Move `OrchestratorCard`, `PipelinesCard`, `AutonomousConfigCard`, `PrdDagConfigCard` into new `AutomataTab`. |
| 4 | Move `PluginFrameworkConfigCard` into new `PluginsTab`. |
| 5 | Move `SecretsCard` and `TailscaleCard` into `GeneralTab`. |
| 6 | Delete the four orphan top-level tab entries from `SettingsScreen` nav. |
| 7 | Verify all existing cards remain reachable; run full Settings UI test. |

Files: `composeApp/.../ui/settings/SettingsScreen.kt`,
`composeApp/.../ui/settings/SettingsTab.kt` (or equivalent enum),
individual card files where import paths change.

Note: If any of these cards (`RoutingCard`, `OrchestratorCard`, `PipelinesCard`,
`AutonomousConfigCard`, `PrdDagConfigCard`, `PluginFrameworkConfigCard`) don't yet
exist on mobile, they need to be created with the appropriate API wiring before the
tab restructure.

Acceptance: Settings navigation matches PWA's six-tab structure (General / Monitor /
Comms / LLM / Automata / Plugins). No orphan tabs. Biometric / Security card unaffected.

---

### #47 — Launch Automation FAB + UX fixes (BL246, parent v6.5.1)

Five targeted UX items on the Automata tab.

| Step | What |
|------|------|
| 1 — FAB | Add a `FloatingActionButton` on `AutonomousScreen` in the same position as the Sessions tab FAB. Tapping it opens `NewPrdDialog`. Any inline "Launch" button / form element that duplicates this is removed. |
| 2 — Stale help | Remove / replace the "how-to guide coming in 6.2.0-dev" placeholder string with either nothing or a link to the shipped docs. |
| 3 — Overflow menu anchor | Ensure the Automata row's "…" overflow menu is anchored right-aligned or opens as a `BottomSheet` on small viewports so it cannot render offscreen. |
| 4 — Workspace label | Change the working-directory field label from "Directory" (or equivalent) to "Workspace (profile or folder)" to match PWA wording. |
| 5 — Skills "coming soon" | Remove any "coming soon" qualifier from the Skills field in `NewPrdDialog` (skills shipped in BL221 / v6.1.1). |

Files: `composeApp/.../ui/autonomous/AutonomousScreen.kt`,
`composeApp/.../ui/autonomous/NewPrdDialog.kt`.

Acceptance: Automata tab has a FAB; stale placeholder gone; overflow menus don't go
offscreen; workspace label correct; no "coming soon" on Skills.

---

## ✅ v0.60.0 — Language picker + whisper sync (issue #40, parent v5.28.3) — SHIPPED 2026-05-04

**Goal:** language picker moves to the top of Settings → About and selecting a concrete
locale pushes `whisper.language` to the server.

| Step | Detail |
|------|--------|
| 1 | Move the language-picker composable (currently in Settings → General or wherever it lives) to the top of `AboutCard` / `AboutTab`, right under the eye icon + "AI Session Monitor & Bridge" header. Keep a read-only reference row in General for discoverability. |
| 2 | Add `TransportClient.setWhisperLanguage(code: String)` — `PUT /api/config` with `{"whisper.language": code}`. Best-effort; failure non-fatal. |
| 3 | In the language-picker `onChange` callback: when the operator selects a concrete locale (`en`/`de`/`es`/`fr`/`ja`), call `setWhisperLanguage(code)` after applying the in-app locale. When `Auto` is selected, do not call (preserve existing server config). |
| 4 | If a standalone `whisper.language` picker exists in Settings → LLM (Whisper card), make it read-only with a caption "Tracks app language (Settings → About → Language)" — same as PWA. |
| 5 | Test: pick `de` → verify `PUT /api/config` called with `whisper.language=de`; pick `Auto` → verify no PUT. |

Files: `composeApp/.../ui/settings/AboutCard.kt` (or `AboutTab.kt`),
`composeApp/.../ui/settings/GeneralTab.kt`,
`composeApp/.../ui/settings/LlmTab.kt` (Whisper card read-only),
`shared/.../transport/TransportClient.kt`.

Acceptance: language picker prominent at top of About; concrete locale pick syncs whisper;
Auto pick leaves whisper alone; LLM Whisper field read-only.

---

## ✅ v0.61.0 — BL221 Phase 2: Template Store (issue #44, parent v6.2.0) — SHIPPED 2026-05-04

**Goal:** full Templates CRUD surface inside the Automata tab.

### New transport methods

```kotlin
suspend fun listTemplates(): List<TemplateDto>
suspend fun createTemplate(req: CreateTemplateRequest): TemplateDto
suspend fun getTemplate(id: String): TemplateDto
suspend fun updateTemplate(id: String, req: UpdateTemplateRequest): TemplateDto
suspend fun deleteTemplate(id: String)
suspend fun instantiateTemplate(id: String, req: InstantiateRequest): PrdDto
suspend fun clonePrdToTemplate(prdId: String, description: String, actor: String): TemplateDto
```

Endpoints: `GET/POST /api/autonomous/templates`,
`GET/PUT/DELETE /api/autonomous/templates/{id}`,
`POST /api/autonomous/templates/{id}/instantiate`,
`POST /api/autonomous/prds/{id}/clone_to_template`.

### TemplateDto

```kotlin
data class TemplateDto(
    val id: String,
    val title: String,
    val spec: String,
    val type: String? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val createdAt: String? = null
)
```

### UI work

| Component | Detail |
|-----------|--------|
| `TemplatesTab` | Third tab in `AutonomousScreen` (alongside Active / History). `LazyColumn` of `TemplateRow` cards. |
| `TemplateRow` | Title, type badge (reuse BL221 Phase 4 badge system — see v0.63.0), tags chips, created date, "Use" button that opens `InstantiateTemplateDialog`. |
| `InstantiateTemplateDialog` | Detects `{{var}}` placeholders in spec; renders a text field per var. Project-dir picker + vars form + "Instantiate" button → creates PRD. |
| `CreateTemplateSheet` | Title (required), spec (required), type picker, tags field (comma-separated), description. |
| `EditTemplateSheet` | Same fields as create, pre-populated. |
| Clone from PRD | Long-press on a completed `PrdRow` → contextual menu option "Save as template" → opens `CreateTemplateSheet` pre-filled from PRD spec. Calls `clonePrdToTemplate`. |
| Swipe-to-delete | `SwipeToDismiss` on `TemplateRow` → confirm dialog → `deleteTemplate`. |

Files: `composeApp/.../ui/autonomous/` (new: `TemplatesTab.kt`, `TemplateRow.kt`,
`InstantiateTemplateDialog.kt`, `CreateTemplateSheet.kt`, `EditTemplateSheet.kt`),
`AutonomousScreen.kt` (add tab),
`shared/.../transport/` (new transport methods + DTO).

Locale keys needed: `tmpl_tab_title`, `tmpl_empty`, `tmpl_use`, `tmpl_create`,
`tmpl_edit`, `tmpl_delete_confirm`, `tmpl_instantiate`, `tmpl_clone_from_prd`,
`tmpl_vars_title`, `tmpl_tags_label`, `tmpl_description_label`. (EN only; full i18n in v0.65.0.)

Acceptance: templates list, create, edit, delete, instantiate, clone-from-PRD all functional
on device against a v6.2.0+ daemon.

---

## ✅ v0.62.0 — BL221 Phase 3: Security scan (issue #45, parent v6.2.0) — SHIPPED 2026-05-04

**Goal:** security scan results surface inside `PrdDetailDialog` and a Scan Config card
in Settings → Automata.

### New transport methods

```kotlin
suspend fun triggerScan(prdId: String): ScanResultDto
suspend fun getScanResult(prdId: String): ScanResultDto
suspend fun createFixPrd(prdId: String): PrdDto
suspend fun proposeRules(prdId: String): RuleProposalDto
suspend fun getScanConfig(): ScanConfigDto
suspend fun updateScanConfig(patch: Map<String, Any>)
```

### DTOs

```kotlin
data class ScanFindingDto(
    val scanner: String,
    val file: String,
    val line: Int? = null,
    val severity: String,   // "info"|"warning"|"error"
    val ruleId: String? = null,
    val message: String,
    val fixable: Boolean = false
)

data class ScanResultDto(
    val at: String,
    val prdId: String,
    val pass: Boolean,
    val verdict: String,    // "pass"|"warn"|"fail"
    val notes: String? = null,
    val findings: List<ScanFindingDto> = emptyList()
)

data class ScanConfigDto(
    val enabled: Boolean,
    val sast: Boolean,
    val secrets: Boolean,
    val deps: Boolean,
    val failOnSeverity: String,
    val grader: Boolean,
    val fixLoop: Boolean,
    val maxRetries: Int
)
```

### UI work

| Component | Detail |
|-----------|--------|
| `ScanResultCard` in `PrdDetailDialog` | Verdict badge (PASS green / WARN amber / FAIL red) + finding count + timestamp. Expandable `FindingsList` showing severity chip, file:line, rule ID, message per finding. |
| Run Scan button | `IconButton` in `PrdDetailDialog` toolbar → calls `triggerScan` → shows progress indicator while polling for result. |
| Fix PRD button | Shown when findings exist → `createFixPrd` → navigates to child PRD detail. |
| Propose Rules button | Shown when findings exist → `proposeRules` → displays result in a `ModalBottomSheet` (AGENT.md diff text in a monospace code block). |
| `ScanConfigCard` | New card in Settings → Automata tab. Toggles for enabled/SAST/secrets/deps; `failOnSeverity` picker (info/warning/error); grader toggle; fix-loop toggle + max-retries `Stepper`. |

Files: `composeApp/.../ui/autonomous/` (new: `ScanResultCard.kt`, `FindingsList.kt`,
`ScanConfigCard.kt`), `PrdDetailDialog.kt` (add scan section + buttons),
`composeApp/.../ui/settings/AutomataTab.kt` (add `ScanConfigCard`),
`shared/.../transport/` (new transport methods + DTOs).

Locale keys needed (EN only): `scan_verdict_pass`, `scan_verdict_warn`, `scan_verdict_fail`,
`scan_run`, `scan_findings_count`, `scan_fix_prd`, `scan_propose_rules`, `scan_config_title`,
`scan_config_enabled`, `scan_fail_on`, `scan_fix_loop`, `scan_max_retries`.

Acceptance: scan card visible in PRD detail on daemons ≥v6.2.0; verdict badge correct colour;
findings expandable; Run/Fix/Propose buttons functional; Scan Config card in Settings → Automata.

---

## ✅ v0.63.0 — BL221 Phase 4: Type registry + Guided Mode + Skills (issue #43, parent v6.2.0) — SHIPPED 2026-05-04

**Goal:** PRD type system, Guided Mode flag, and skills chips across list + detail + create.

### New transport methods

```kotlin
suspend fun listAutomataTypes(): List<AutomataTypeDto>
suspend fun registerAutomataType(req: AutomataTypeRequest): AutomataTypeDto
suspend fun setPrdType(prdId: String, type: String)
suspend fun setPrdGuidedMode(prdId: String, guidedMode: Boolean)
suspend fun setPrdSkills(prdId: String, skills: List<String>)
```

### DTOs

```kotlin
data class AutomataTypeDto(
    val id: String,          // "software"|"research"|"operational"|"personal"|custom
    val label: String,
    val description: String? = null,
    val color: String? = null   // hex, optional
)
```

`PrdDto` gains: `val type: String? = null`, `val guidedMode: Boolean = false`,
`val skills: List<String> = emptyList()`.

### UI work

| Component | Detail |
|-----------|--------|
| Type badge on `PrdRow` | Small colour-coded chip next to status pill. Colour map: `software`→blue, `research`→purple, `operational`→orange, `personal`→teal, custom→`surfaceVariant`. Falls back gracefully when `type == null`. |
| `PrdDetailDialog` — Guided Mode | Checkmark row "Guided Mode" shown when `prd.guidedMode == true`. Tapping opens a toggle sheet calling `setPrdGuidedMode`. |
| `PrdDetailDialog` — Skills | `FlowRow` of skill chips below type badge. "Edit skills" icon opens a text field dialog (comma-separated) calling `setPrdSkills`. |
| `NewPrdDialog` additions | Type picker dropdown (4 built-ins + custom text field), Guided Mode toggle switch, Skills text input. Sent as `type`, `guided_mode`, `skills[]` on `POST /api/autonomous/prds`. |
| Type registry screen | New `AutomataTypesScreen` reachable from Settings → Automata tab. Lists `/api/autonomous/types`; "+" FAB → `CreateTypeSheet` (`id`, `label`, `description`, `color` picker); per-row edit + delete via `PUT`/`DELETE /api/autonomous/types/{id}` (if server exposes). |

Files: `composeApp/.../ui/autonomous/` (update `PrdRow.kt`, `PrdDetailDialog.kt`,
`NewPrdDialog.kt`; new `AutomataTypesScreen.kt`, `CreateTypeSheet.kt`),
`composeApp/.../ui/settings/AutomataTab.kt` (link to type registry),
`shared/.../dto/PrdDto.kt` + `AutomataTypeDto.kt`,
`shared/.../transport/` (new methods).

Locale keys needed (EN only): `automata_detail_type`, `automata_detail_guided_mode`,
`automata_detail_skills`, `automata_type_registry_title`, `automata_type_create`.

Acceptance: type badge visible on PRD cards; Guided Mode + Skills shown in detail; create form
includes all three fields; Type registry screen lists + allows adding custom types.

---

## ✅ v0.64.0 — Signal device-linking (BL21) — SHIPPED 2026-05-04

**Condition:** ships when `datawatch#31` (`/api/link/*` + QR SSE endpoint) is merged and
deployed. If still blocked at this point in the sequence, skip to v0.65.0 and park BL21
as first item of v0.66.0.

### Work

| Step | Detail |
|------|--------|
| 1 | Add transport: `startSignalLinking(): Flow<LinkQrFrame>` — SSE `GET /api/link/qr` emitting base64 QR frames. `confirmSignalLink()`, `cancelSignalLink()`, `getSignalLinkStatus(): SignalLinkStatusDto`. |
| 2 | `QrCodeView` composable — renders a `Bitmap` from the base64 frame using the `zxing` Android library (already available if cert-install path uses it, else add). |
| 3 | `SignalLinkingDialog` — opened from Settings → Comms → Channels card "Link Signal" button. Shows `QrCodeView`, "Scan with Signal on another device" instruction, status indicator (linking / linked / error), Cancel button. On success, shows paired-state chip in the Channels card. |
| 4 | Persist `signalLinked: Boolean` in `ServerProfileEntity` (new migration). Channels card reads this to show the linked indicator without a round-trip. |

Files: `shared/.../transport/`, `shared/.../db/` (migration),
`composeApp/.../ui/settings/comms/SignalLinkingDialog.kt`,
`composeApp/.../ui/settings/comms/ChannelsCard.kt`.

Acceptance: "Link Signal" button opens QR dialog; QR refreshes from SSE; linked state
persists and shows in Channels card.

---

## v0.65.0 — i18n: full sync BL252 (~190 keys) (issue #46, parent v6.5.5–v6.6.0)

**Goal:** one dedicated pass to sync all 5 locale bundles (en/de/es/fr/ja) with the
190 new keys from PWA BL252 phases 1–7, plus any keys added by v0.58.0–v0.64.0 work above
that were filed as EN-only stubs.

### Source

`internal/server/web/locales/{en,de,es,fr,ja}.json` at `dmz006/datawatch@v6.6.0`
is the authoritative translation source. 1:1 key mapping applies — PWA key names are
already used in the KMP string resources.

### Phase breakdown

| Phase | Version shipped on PWA | Key count | Topic |
|-------|----------------------|-----------|-------|
| 1+2 | v6.5.5 | 53 | Session filter, schedule inputs, new-session form, channel help, chat roles |
| 3+4 | v6.5.6 | 70 | PRD modal lifecycle strip, stats section headings, alerts empty state, btn consolidation |
| 5 | v6.5.7 | 24 | Settings identity card, language, version, update, daemon, sessions, project |
| 6 | v6.5.8 / v6.6.0 | 26 | Nav labels, session action buttons, terminal connection strings, voice status |
| 7 | v6.6.0 | 43 | Status/state strings, LLM, memory, KG, stats, signal, toast strings |
| Stub keys from v0.58–v0.64 | — | ~35 est. | Template Store, scan, type registry, Guided Mode, Skills, Signal linking |

### Work

| Step | Detail |
|------|--------|
| 1 | Extract the complete key list from `locales/en.json` at v6.6.0. Diff against `composeApp/src/androidMain/res/values/strings.xml`. |
| 2 | Add all missing keys to `strings.xml` (EN). |
| 3 | Add the same keys to `values-de/`, `values-es/`, `values-fr/`, `values-ja/` using the PWA's translations as source (already committed in the parent repo). |
| 4 | Repeat for `wear/src/main/res/` if any Wear-visible strings are in the gap set. |
| 5 | Wire any hardcoded English literals in new composables (from v0.58–v0.64) to string resources. |
| 6 | Run `./gradlew lintRelease` — missing string resource warnings must be zero. |

Acceptance: all ~190+ keys present in all 5 locale dirs; no hardcoded English visible in
the running app when locale is set to DE/ES/FR/JA; lint clean.

---

## Parking lot (no version assigned)

These items have a dependency or decision gate that prevents scheduling.

| ID | Status | Item | Note |
|----|--------|------|------|
| BL19 | ❄️ FROZEN | Local-LLM orchestration — in-app PRD/HLD authoring + Ollama backend + task fire-off | Frozen 2026-05-04. No ADR, no schedule. Revisit only on explicit user unfreeze. |
| B6 | ❄️ FROZEN | FCM push notifications | Frozen 2026-05-04. ntfy-only is the declared policy. Revisit only on explicit user unfreeze. |
| B31 | HOLD | Wear + Auto: Sessions snapshot + quick-command + voice | User still evaluating whether existing Auto scope (`WaitingSessionsScreen` + `SessionReplyScreen`) counts as done. No action until user decides. |
| BL5 | HOLD | iOS app content (beyond skeleton) | Android parity stabilises first; auto-promoted when user signals Android is stable. |
| Store assets | PENDING | `docs/media/store/phone/`, `tablet-10/`, `tablet-7/` untracked | Commit in next convenient version bump. |

---

## Sequencing summary

```
v0.58.0  ─ #31 quick-commands API · #41 PRD card style · #49 reconnect refresh
v0.59.0  ─ #48 settings tab reorg (Automata+Plugins tabs) · #47 automata FAB+UX
v0.60.0  ─ #40 language picker top-of-About + whisper.language sync
v0.61.0  ─ #44 BL221 Phase 2: Template Store
v0.62.0  ─ #45 BL221 Phase 3: Security scan results
v0.63.0  ─ #43 BL221 Phase 4: Type registry + Guided Mode + Skills
v0.64.0  ─ BL21 Signal device-linking QR (if datawatch#31 ready; else skip → v0.66.0)
v0.65.0  ─ #46 i18n full sync BL252 (~190 keys + v0.58–v0.64 stubs)
v1.0.0   ─ USER DECISION — user will explicitly call this
```

### Definition of done (each version)

Per sprint-plan.md §"Definition of Done" — applies unchanged:

- New/changed logic has close to 100% unit test coverage.
- `./gradlew test connectedCheck detekt ktlint lintRelease` all green.
- Live-tested on a real device against a running datawatch server.
- `docs/testing-tracker.md` updated.
- `CHANGELOG.md` updated.
- `gradle.properties` + `Version.kt` version bumped (both must match — CI enforces).
- Branch merged to `main`; AAB uploaded to Play Console Internal Testing.
- GH issues closed with the closing commit message.
