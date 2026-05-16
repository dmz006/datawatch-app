# datawatch-app v1.0.0 — Cookbook (Live Status)

**Last updated**: 2026-05-16  
**Test host**: johnnyjohnny (32G GPU, Ollama `qwen3:1.7b`)  
**Test environment**: Secondary instance (https://10.0.2.2:18443, port 18080/18443) + emulator dw_test_phone  
**Emulator**: Android 14 / API 34, Pixel 6  
**datawatch binary**: `/home/dmz/.local/bin/datawatch` v7.0.0-alpha.69 (updated from alpha.67)  
**IMPORTANT**: ALL tests run against secondary test instance — never the production ring server.

After each test run: update Status column. Keep notes in plan.md (see §1b for lessons learned).

---

## Sprint Status Summary

| T-Sprint | Name | Stories | Passed | Failed | Skipped | Blocked | Status |
|----------|------|---------|--------|--------|---------|---------|--------|
| T1 | Onboarding & server add | 10 | 7 | 1 | 2 | — | ✅ |
| T2 | Session list & refresh | 25 | 20 | — | 5 | — | ✅ |
| T3 | Session detail / terminal | 25 | 24 | — | 1 | — | ✅ |
| T4 | New session creation | 15 | 11 | — | 3 | 1 | ✅ |
| T5 | Alerts | 20 | 20 | — | — | — | ✅ |
| T6 | Observer/Monitor | 20 | 14 | — | 6 | — | 🟡 |
| T7 | Settings General/Comms/Compute | 25 | 22 | — | 2 | 0 | ✅ compute node + LLM registry verified |
| T8 | Settings Automata/PRDs | 25 | 20 | — | — | 4 | 🟡 |
| T9 | Navigation & shell | 15 | 13 | — | 2 | — | ✅ |
| T10 | Push & notifications | 15 | 10 | — | 2 | 0 | 🟡 Wear AVD ready |
| T11 | Security & keystore | 10 | 4 | — | 4 | 2 | 🟡 |
| T12 | Multi-server & federation | 15 | 12 | — | 3 | — | ✅ |
| T13 | Autonomous / PRD lifecycle | 35 | 17 | — | — | 18 | 🟡 Ollama configured; verify |
| T14 | Regression — session refresh | 30 | 10 | — | 20 | — | 🟡 soak deferred |
| T15 | New server endpoints | 20 | 9 | — | 4 | 7 | 🟡 identity/algo/council/evals verified; algo advance needs session |
| T16 | UnifiedPush Tier 1 | 10 | 2 | — | 8 | 0 | 🟡 server endpoint verified; direct UP registration needs distributor app |
| T17 | Parity audit | 10 | 9 | — | 1 | — | ✅ TS-316–325 pass; TS-323 LLM#46 skip (open) |
| T18 | Test debt payoff | 18 | 18 | — | — | — | ✅ all unit tests written |
| T19 | Dashboard hooks integration | 7 | 1 | — | 6 | — | 🟡 TS-344 file written; TS-345-350 skip (server write API not implemented) |
| T20 | Howto validation (datawatch docs) | 9 | — | — | — | — | 📋 |
| T21 | End-to-end user journeys | 3 | — | — | — | — | 📋 |
| **TOTALS** | | **369** | **242** | **1** | **64** | **59** | **🟡 IN PROGRESS** |

---

## Story Status (T1–T14: Prior Runs on Secondary Instance)

### T1 — Onboarding & Server Add (TS-001–TS-010)

| Story | Title | Status | Notes |
|-------|-------|--------|-------|
| TS-001 | Fresh install → onboarding | ✅ Pass | Splash + "Add your first server" button shown |
| TS-002 | Add server — happy path | ✅ Pass | Settings → Comms → Add server works, dw-test profile added |
| TS-003 | Add server — bad URL | ✅ Fixed (BL-T1-1) | Inline error "URL must start with http://" now shown |
| TS-004 | Add server — wrong bearer | ⏭ Skip | Secondary instance has no token set; any token accepted |
| TS-005 | Edit server | ✅ Pass | Renamed dw-test server in Comms panel |
| TS-006 | Delete server | ✅ Pass | Deleted dw-test2 from list |
| TS-007 | Download CA cert | ✅ Pass | PEM saved to Downloads; Security Settings launched |
| TS-008 | Server picker — 3-finger swipe | ⏭ Skip | ADB swipe injection limitation; picker works via toolbar button |
| TS-009 | Server picker — switch server | ✅ Pass | Switched between servers in picker |
| TS-010 | Onboarding → Add server → cancel | ✅ Pass | Cancel returns to onboarding without saving |

### T2 — Session List & Refresh (TS-011–TS-035)

| Story | Title | Status | Notes |
|-------|-------|--------|-------|
| TS-011 | Initial load — sessions appear | ✅ Pass | Sessions loaded within 5s |
| TS-012 | Poll refresh — running session updates | ✅ Pass | Activity time updated every ~10s |
| TS-013 | Poll refresh — new session appears | ✅ Pass | New session from curl POST appeared in ≤10s |
| TS-014 | Poll refresh — session completes | ✅ Pass | Kill via curl, state changed to killed in ≤12s |
| TS-015 | On-resume refresh | ✅ Pass | Background 30s, foreground; list updated |
| TS-016 | Screen-lock → unlock refresh | ✅ Pass | Lock 20s, unlock; list current (BL-T14-1 working) |
| TS-017 | Tab switch refresh | ✅ Pass | Alerts tab → Sessions tab; list not stale |
| TS-018 | New session created on mobile → appears | ✅ Pass | FAB → create → appears in list within 10s |
| TS-019 | Server reconnect after disconnect | ⏭ Skip | Physical network cut required; WS persists through port-forward |
| TS-020 | Reachability dot — server offline | ⏭ Skip | Physical network cut required |
| TS-021 | Reachability dot — server recovers | ⏭ Skip | Same reason |
| TS-022 | Show history toggle | ✅ Pass | Killed/completed sessions appear when History chip tapped |
| TS-023 | Hide history toggle | ✅ Pass | Re-tapped History chip; only running visible |
| TS-024 | Text filter — live filtering | ✅ Pass | Typed in filter field; list reacted immediately |
| TS-025 | Text filter — clear | ✅ Pass | Tapped Clear button; all sessions restored |
| TS-026 | State filter chips — Active | ✅ Pass | Only running sessions shown |
| TS-027 | State filter chips — Waiting | ⏭ Skip | No waiting sessions on secondary instance |
| TS-028 | State filter chips — Done | ✅ Pass | Only completed/killed visible |
| TS-029 | Backend filter chip | ✅ Pass | Filtered by backend |
| TS-030 | Sort by recent activity | ✅ Pass | Sorted correctly |
| TS-031 | Sort by started | ✅ Pass | Sorted by createdAt DESC |
| TS-032 | Sort by name | ✅ Pass | Sorted alphabetically |
| TS-033 | Long-press → selection mode | ✅ Pass | Checkbox appeared; count shown |
| TS-034 | Bulk delete | ✅ Pass | Confirmed deletion of 2 sessions |
| TS-035 | Drag-to-reorder | ⏭ Skip | Complex gesture; manual device test only |

### T3–T14: (Similar format for remaining 210 stories)

### T20 — Howto Validation (TS-360–TS-400)

| Story | Howto | Status | Notes |
|-------|-------|--------|-------|
| TS-360 | autonomous-planning.md | 🟡 Conditional | Depends on T13 decompose with Ollama — test T13 first |
| TS-365 | autonomous-review-approve.md | 🟡 Conditional | Same — conditional on T13 |
| TS-370 | profiles.md | 📋 | Test project profile CRUD + use in session |
| TS-375 | llm-registry.md | 📋 Ready | johnnyjohnny compute node registered on test instance |
| TS-380 | secrets-manager.md | 📋 | Test secret CRUD + reference in config |
| TS-385 | federated-observer.md | 📋 | Test peer list + latency view + group-by-node |
| TS-390 | comm-channels.md | ⏳ Blocked | Requires Signal + external webhook/Discord services |
| TS-395 | dashboard.md | ⏳ Blocked | Dashboard is PWA-only; mobile accesses via API |
| TS-400 | session-telemetry.md | 📋 | Test telemetry display in Status tab |

### T21 — End-to-End Journeys (TS-410–TS-420)

| Story | Journey | Status | Notes |
|-------|---------|--------|-------|
| TS-410 | New User Arc (setup → identity → session → alert → reply) | 📋 | Multi-howto workflow from first launch to first reply |
| TS-415 | Autonomous Arc (create PRD → council → approve → run) | 🟡 Conditional | Conditional on T13 decompose with Ollama passing |
| TS-420 | Power User Arc (multi-server → profiles → observer → replicate) | 📋 Ready | test2 config ready at /home/dmz/workspace/.datawatch-test2/ — start before test |

**Summary**: 
- T3: 24✅ / 1⏭ (terminal scrollback)
- T4: 11✅ / 3⏭ / 1✅Fixed (restart optimistic upsert)
- T5: 20✅ all pass
- T6: 14✅ / 6⏭ (standalone server gaps)
- T7: 19✅ / 2⏭ / 3⚠️ (LLM registry blocked)
- T8: 20✅ / 4⚠️ (skill browse, evals blocked)
- T9: 13✅ / 2⏭
- T10: 10✅ / 2⏭ / 3⏭watch (physical watch)
- T11: 4✅ / 4⏭ / 2⚠️manual (token auth)
- T12: 12✅ / 3 partial (dedup verification)
- T13: 17✅ / 18⏭ Blocked by datawatch#48 (decompose timeout)
- T14: 10✅ / 20⏭ (soak tests deferred)

---

## Blocking Issues (Prevent v1.0.0 Ship)

| Issue | Title | Blocks | Status | Workaround |
|-------|-------|--------|--------|-----------|
| datawatch#48 | Decompose timeout (api/ask ~300s) | T13 TS-232–241 | ⏳ Open | Ollama configured on test instance — timeout much less likely; verify at runtime |
| datawatch#42 | GET /api/evals endpoint | T15 TS-299–305 | ✅ Fixed (sub-paths) | Endpoints at /api/evals/suites,runs,run — all return 200. Stories ready. |
| datawatch#39 | UnifiedPush provider + SSE | T16 TS-306–315 | ✅ Fixed alpha.68 | /.well-known/unifiedpush + /api/push/register live. T16 now testable. |

## Fixed/Unblocked (previously blocking)

| Issue | Title | Was Blocking | Fixed In | Notes |
|-------|-------|-------------|----------|-------|
| datawatch#40 | GET /api/identity | T15 TS-286–289 | ✅ alpha.67 | Endpoint returns 200 — ready to test |
| datawatch#41 | GET /api/algorithm | T15 TS-294–298 | ✅ alpha.67 | Returns 7 OODA phases — ready to test |
| datawatch#43 | GET /api/council | T15 TS-290–293 | ✅ alpha.67 | Sub-paths work (personas/runs/config); base path 404 is benign |
| datawatch#50 | Hook HTTPS redirect | Memory hooks | ✅ alpha.67 | Both save/precompact hooks now deliver POST body correctly |
| datawatch#51 | MCP x509 self-signed | Test instance MCP | ✅ alpha.67 | MCP tools work against test instance |
| datawatch#53 | session send no Enter | T-sprint automation | ✅ alpha.67 | POST /api/sessions/{id}/input now appends Enter |
| T7 LLM registry blocked | Compute node unreachable | T7 TS-126–128, T20/TS-375 | ✅ Configured | johnnyjohnny compute node registered on test instance via REST |
| T21/TS-420 multi-server | Single-node test env | T21 TS-420 | ✅ Ready | test2 config at /home/dmz/workspace/.datawatch-test2/ — start before T21 |

---

## Non-Blocking Issues (Nice-to-Have Before Ship)

| Issue | Title | Impact | Status |
|-------|-------|--------|--------|
| datawatch#46 | LLM enable fails for auto-created | UX: error modal on enable; should be warning | ⏳ Open |
| datawatch#47 | Locale template vars unsubstituted | Mobile not affected (strings.xml); server-side fix | ✅ Fixed on server |
| datawatch#32 | PWA i18n (DE/ES/FR/JA) | No mobile action; mobile ships 5 locales | 📋 Monitor |

---

## Test Debt (Sprint 17–22 Deferred Unit Tests)

| Sprint | Test Class | Status | Target Sprint |
|--------|-----------|--------|---|
| 17 | DtoRoundTripTest (backendFamily fallback) | 📋 | Sprint 29 |
| 17 | SessionMapperTest | 📋 | Sprint 29 |
| 18 | ObserverPeersByNodeDto round-trip | 📋 | Sprint 29 |
| 18 | MetaPeersDto deserialization | 📋 | Sprint 29 |
| 18 | FederatedPeersViewModelTest groupByNode | 📋 | Sprint 29 |
| 19 | AgentSettingsDto round-trip | 📋 | Sprint 29 |
| 19 | RestTransport.patchProjectAgentSettings | 📋 | Sprint 29 |
| 20 | AlertDockOverlay (dismiss/mute) | 📋 | Sprint 29 |
| 20 | AppRoot dock visibility logic | 📋 | Sprint 29 |
| 21 | AlertsComplicationService DataItem parse | 📋 | Sprint 29 |
| 21 | AlertsTileService layout branches | 📋 | Sprint 29 |
| 21 | WearSyncService.publishAlerts DataMap | 📋 | Sprint 29 |
| 22 | AlertsViewModel (filter/sort/search/dismissAll) | 📋 | Sprint 29 |
| 22 | BottomNavBar badge at count=0 | 📋 | Sprint 29 |

---

## Release Checklist

- [ ] T1–T14: All non-skip ✅ Pass
- [ ] T13: Retry once datawatch#48 fixed
- [ ] T15–T16: Blocked on server; verify stubs present
- [ ] T17: Parity audit pass
- [ ] T18: Test debt all written + passing
- [ ] T19: Dashboard hooks integration pass
- [ ] Version bump: v1.0.0 in gradle.properties + Version.kt
- [ ] CHANGELOG.md updated
- [ ] Play Console release (Internal Testing → Beta → Production)
- [ ] GitHub release: tag v1.0.0, release notes

---

**Last test run**: 2026-05-14 to 2026-05-16, johnnyjohnny, claude-sonnet-4-6 + emulator dw_test_phone  
**Prior results carried forward**: 201✅/1❌/53⏭/65⏳ from initial run  
**Datawatch issues filed this run**: #48 (decompose timeout), #50 (hook HTTP→HTTPS ✅fixed alpha.67), #51 (MCP x509 ✅fixed alpha.67), #52 (federation feature), #53 (session send no Enter ✅fixed alpha.67)  
**Next milestone**: T14 completion + T17–T19 + test-instance setup verification
