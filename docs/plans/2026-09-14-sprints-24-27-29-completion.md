# Sprints 24–27 + 29 — Completion Audit & Test/Doc Sweep

**Date:** 2026-09-14
**Version at planning:** v1.9.3
**Scope:** `composeApp/ui/autonomous`, `composeApp/ui/sessions`, `composeApp/ui/alerts`,
           `composeApp/ui/compute`, `shared/transport`, locale bundles × 5
**Status:** Implementation shipped during v1.x BL arc; this plan tracks remaining
            tests, documentation, and sprint-plan.md closure.

---

## Finding

All five sprints were implemented during the v1.0–v1.9 BL arc even though
`docs/sprint-plan.md` still shows them as ⏳ PLANNED at v0.94–v0.99.
The code exists and is wired; what remains is unit tests, testing-tracker rows,
and sprint-plan.md status promotion.

| Sprint | Feature | Code present | Tests | Docs |
|--------|---------|-------------|-------|------|
| 24 | Automata pin/sort/inline actions | ✅ `AutonomousScreen.kt` `pinnedAutomataIds` + sort + pin button | ⏳ | ⏳ |
| 25 | Stats sectioned cards (Host/Container/ComputeNode/LLM) | ✅ `SessionStatsPanel.kt` 443 lines | ⏳ | ⏳ |
| 26 | Status 4th sub-tab + HookHealthPill + statusTabBadge | ✅ `SessionStatusPanel.kt` 461 lines + `SessionStatusViewModel.kt` | ⏳ | ⏳ |
| 27 | Ollama marketplace dialog + pull progress; Alerts Active/Historical/System tabs | ✅ `OllamaMarketplaceDialog` in `ComputeNodesCard.kt`; Alerts tabs in `AlertsScreen.kt` | ⏳ | ⏳ |
| 29 | Collapsible LLM `▸` + State filter buttons in sessions list | ✅ `llmExpanded` toggle + `llm_filter_btn_tip` in `SessionsScreen.kt` | ⏳ | ⏳ |

---

## Phases

### Phase 1 — Unit tests (all five sprints)  Status: Planned

#### Sprint 24 tests — `AutonomousViewModelTest`
- [ ] `pinnedAutomataIds persisted per profile via PinnedAutomataStore`
- [ ] `sort order: pinned before unpinned`
- [ ] `sort order: waiting/needs_review rank before running before done`
- [ ] `approve action enabled only when status is needs_review | revisions_asked | waiting_input`
- [ ] `cancel action enabled only when status is not done | killed`

**Files:** `composeApp/src/androidUnitTest/.../ui/autonomous/AutonomousViewModelTest.kt`
**Notes:** Check `PinnedAutomataStore` (or `SharedPreferences`-backed store) for the
           persistence test — verify key naming includes server profile ID.

#### Sprint 25 tests — `SessionStatsPanelTest`
- [ ] `Container card hidden when envelope.containerInfo is null`
- [ ] `ComputeNode card hidden when computeNodeRef is null or computeNode null`
- [ ] `LLM card hidden when llmRef is null`
- [ ] `HostCard always rendered`
- [ ] `sparkline buffer accumulates up to 60 CPU samples`

**Files:** `composeApp/src/androidUnitTest/.../ui/sessions/SessionStatsPanelTest.kt`
**Notes:** Extract pure state/visibility logic into testable helper functions if the
           composable doesn't already have them; mirror the BriefingTileTest pattern.

#### Sprint 26 tests — `SessionStatusViewModelTest`, `SessionStatusPanelTest`
- [ ] `statusTabBadge returns 🟢 when board.state == "green"`
- [ ] `statusTabBadge returns 🟠 when board.state == "orange" or "amber"`
- [ ] `statusTabBadge returns ⚪ when board is null`
- [ ] `HookHealthPill label: alive / stale / missing states`
- [ ] `refreshStatus called on 5-second tick while tab active`
- [ ] `polling stops on lifecycle destroy`

**Files:** `composeApp/src/androidUnitTest/.../ui/sessions/SessionStatusViewModelTest.kt`

#### Sprint 27 tests — `OllamaMarketplaceTest`, `AlertsViewModelTest` (tab filter)
- [ ] `catalog filtered by search term (case-insensitive)`
- [ ] `tag row shows ✓ when model fully-qualified name is in installedModels`
- [ ] `pull task progress reflected in tag row when taskId matches`
- [ ] `Alerts: active tab shows only non-dismissed alerts`
- [ ] `Alerts: historical tab shows only dismissed alerts`
- [ ] `Alerts: system tab shows only system-category alerts`
- [ ] `per-tab chip + sort + search filters are independent (changing one tab's filters doesn't affect another)`

**Files:**
- `composeApp/src/androidUnitTest/.../ui/compute/OllamaMarketplaceTest.kt`
- `composeApp/src/androidUnitTest/.../ui/alerts/AlertsViewModelTest.kt` (extend existing)

#### Sprint 29 tests — `SessionsViewModelTest` (filter expand)
- [ ] `llmExpanded defaults to false`
- [ ] `toggling llmExpanded shows/hides backend-family badge row`
- [ ] `LLM filter button label shows (N+1) where N is backend count`
- [ ] `State filter highlights when non-ALL selection active`

**Files:** `composeApp/src/androidUnitTest/.../ui/sessions/SessionsViewModelTest.kt`

---

### Phase 2 — Testing-tracker rows  Status: Planned

Add rows to `docs/testing-tracker.md` under the appropriate sprint section:

| Surface | Feature | Sprint | Test class |
|---------|---------|--------|-----------|
| Phone | Automata pin/sort/inline actions | 24 | `AutonomousViewModelTest` |
| Phone | Stats sectioned cards | 25 | `SessionStatsPanelTest` |
| Phone | Status 4th sub-tab + hook health | 26 | `SessionStatusViewModelTest` |
| Phone | Ollama marketplace dialog + pull | 27 | `OllamaMarketplaceTest` |
| Phone | Alerts Active/Historical/System tabs | 27 | `AlertsViewModelTest` |
| Phone | Collapsible LLM/State filter | 29 | `SessionsViewModelTest` |

---

### Phase 3 — Sprint-plan.md status promotion  Status: Planned

For each sprint:
1. Flip all task rows from `⏳` to `✅`
2. Add "**Shipped:** vX.Y.Z" note
3. Append test status column linking to new test files
4. Update Sprints 26–29 server-blocker table — mark resolved blockers ✅

---

### Phase 4 — CHANGELOG + README + backlog refactor  Status: Planned

Per per-sprint rules audit (AGENT.md §Per-Sprint Rules Audit):
- [ ] CHANGELOG updated with all 5 sprints under a new version header
- [ ] README.md current-release line updated
- [ ] `docs/plans/README.md` — sprint rows marked ✅ and moved to Closed section
- [ ] Locale gate confirmed: all `automata_action_*`, `stats_card_*`, `status_hooks_*`,
      `ollama_*`, `alerts_*_tab_label`, `llm_filter_btn_tip` keys present in all 5 bundles
- [ ] `docs/testing-tracker.md` rows added (Phase 2)
- [ ] Version bump for the test+doc sprint (patch)

---

## Per-Sprint AGENT.md Checklist (run before each commit)

- [ ] AGENT.md rules re-read; applicable rules noted in commit message
- [ ] `docs/testing-tracker.md` updated for new surface or transport row
- [ ] Locale gate: all 5 bundles (EN/DE/ES/FR/JA) updated for every new user-facing string
- [ ] Version bump: `gradle.properties` + `Version.kt` in sync
- [ ] `./gradlew build` passes clean (zero new lint/detekt/ktlint warnings)
- [ ] `./gradlew test` passes (unit tests green)
- [ ] `CHANGELOG.md` updated under `[Unreleased]` or current version header
- [ ] `README.md` current-release line updated
- [ ] `docs/plans/README.md` backlog refactored (shipped items → closed section)

---

## Reuse Audit

| Sprint | Reuses |
|--------|--------|
| 24 | `PinnedAutomataStore` (SharedPreferences pattern from `PushTokenStore`) |
| 25 | `SessionStatsViewModel` already exists; extend, don't duplicate |
| 26 | `SessionStatusViewModel` already exists; `statusTabBadge` public fun — reuse in tests |
| 27 | `OllamaMarketplaceDialog` inline in `ComputeNodesCard`; `AlertsViewModel` already has filter state |
| 29 | `llmExpanded` toggle state already in `SessionsScreen`; extend `SessionsViewModelTest` |

---

## Version Target

Tests + doc sweep ships as a **patch** (no new features). Target **v1.9.4** if all
5 sprints' tests pass; otherwise hold until tests are green.
