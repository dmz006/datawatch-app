# datawatch-app v1.0.0 — Cookbook (Live Status)

**Last updated**: 2026-05-14  
**Test environment**: Secondary instance (https://10.0.2.2:18443) + emulator dw_test_phone  
**Emulator**: Android 14 / API 34, Pixel 6

After each test run: update Status column. Keep notes in plan.md.

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
| T7 | Settings General/Comms/Compute | 25 | 19 | — | 2 | 3 | 🟡 |
| T8 | Settings Automata/PRDs | 25 | 20 | — | — | 4 | 🟡 |
| T9 | Navigation & shell | 15 | 13 | — | 2 | — | ✅ |
| T10 | Push & notifications | 15 | 10 | — | 2 | 3 | 🟡 |
| T11 | Security & keystore | 10 | 4 | — | 4 | 2 | 🟡 |
| T12 | Multi-server & federation | 15 | 12 | — | 3 | — | ✅ |
| T13 | Autonomous / PRD lifecycle | 35 | 17 | — | — | 18 | 🟡 #48 closed; Cancel+Clone fixed (064993d); re-run pending |
| T14 | Regression — session refresh | 30 | 10 | — | 20 | — | 🟡 |
| T15 | New server endpoints | 20 | — | — | — | 20 | 🟡 #40-43 all closed; client fully implemented |
| T16 | UnifiedPush Tier 1 | 10 | — | — | — | 10 | 🟡 #39 closed; ready to run |
| T17 | Parity audit | 10 | — | — | — | — | 📋 |
| T18 | Test debt payoff | 18 | — | — | — | — | 📋 |
| T19 | Dashboard hooks integration | 7 | — | — | — | — | 📋 |
| T20 | Howto validation (datawatch docs) | 9 | — | — | — | — | 📋 |
| T21 | End-to-end user journeys | 3 | — | — | — | — | 📋 |
| T22 | Wear OS surface tests | 15 | — | — | — | 13 | 📋 Blocked (physical watch) |
| T23 | Android Auto surface tests | 15 | — | — | — | 14 | 📋 Blocked (DHU required) |
| T24 | Algorithm Mode tests | 12 | — | — | — | — | 📋 |
| T26 | Dashboard Cards CRUD (Android) | 10 | — | — | — | — | 📋 |
| T27 | Automata Orchestrator E2E (Android) | 20 | — | — | — | — | 📋 |
| **TOTALS** | | **441** | **201** | **1** | **53** | **92** | **🟡 IN PROGRESS** |

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
| TS-360 | autonomous-planning.md | 🟡 Unblocked | datawatch#48 closed; ready to validate decompose flow |
| TS-365 | autonomous-review-approve.md | 🟡 Unblocked | datawatch#48 closed; test daemon has PRDs in all review states |
| TS-370 | profiles.md | 📋 | Test project profile CRUD + use in session |
| TS-375 | llm-registry.md | ⏳ Blocked | Compute daemon unreachable on secondary instance |
| TS-380 | secrets-manager.md | 📋 | Test secret CRUD + reference in config |
| TS-385 | federated-observer.md | 📋 | Test peer list + latency view + group-by-node |
| TS-390 | comm-channels.md | ⏳ Blocked | Requires Signal + external webhook/Discord services |
| TS-395 | dashboard.md | 🟡 Unblocked | datawatch#58 closed; dashboard API exists; DashboardScreen + DashboardCardsCard fully implemented |
| TS-400 | session-telemetry.md | 📋 | Test telemetry display in Status tab |

### T21 — End-to-End Journeys (TS-410–TS-420)

| Story | Journey | Status | Notes |
|-------|---------|--------|-------|
| TS-410 | New User Arc (setup → identity → session → alert → reply) | 📋 | Multi-howto workflow from first launch to first reply |
| TS-415 | Autonomous Arc (create PRD → council → approve → run) | 🟡 Unblocked | datawatch#48 closed; Cancel+Clone fixes landed |
| TS-420 | Power User Arc (multi-server → profiles → observer → replicate) | ⏳ Blocked | Requires two distinct servers; secondary is single-node |

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
| datawatch#48 | Decompose timeout (api/ask ~300s) | T13 TS-232–241 | ✅ Closed (2026-05-17) | Cancel button expanded to all cancellable states (064993d); Clone to Template button added |
| datawatch#40 | GET /api/identity endpoint | T15 TS-286–289 | ✅ Closed | IdentityCard fully implemented; GET+POST /api/identity wired |
| datawatch#41 | GET /api/algorithm endpoint | T15 TS-294–298 | ✅ Closed (2026-05-17) | AlgorithmModeCard: Start/Reset/Edit/Measure added to Transport; Reset+Start UI added |
| datawatch#42 | GET /api/evals endpoint | T15 TS-299–303 | ✅ Closed | EvalsCard fixed (BL-T15-2: id default + effectiveId + case_count SerialName) |
| datawatch#43 | GET /api/council endpoint | T15 TS-290–293 | ✅ Closed | CouncilCard fully implemented (personas, runs, config, wizard) |
| datawatch#39 | UnifiedPush provider + SSE | T16 TS-306–315 | ✅ Closed | UnifiedPushSseService fully wired; T16 now runnable |

---

## Non-Blocking Issues (Nice-to-Have Before Ship)

| Issue | Title | Impact | Status |
|-------|-------|--------|--------|
| datawatch#46 | LLM enable fails for auto-created | UX: error modal on enable; should be warning | ✅ Closed — server now returns warning, client enableLlm discards body so no modal |
| datawatch#47 | Locale template vars unsubstituted | Mobile not affected (strings.xml); server-side fix | ✅ Closed on server |
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

### T26 — Dashboard Cards CRUD (TS-465–TS-474)

| Story | Title | Status | Notes |
|-------|-------|--------|-------|
| TS-465 | DashboardCardsCard section visible (Settings → Monitor) | 📋 | |
| TS-466 | Empty state when no cards configured | 📋 | |
| TS-467 | Add card — smoke type, cs=12 | 📋 | |
| TS-468 | Add card — tree type, cs=6, rs=2 | 📋 | |
| TS-469 | Card list shows both entries with correct metadata | 📋 | |
| TS-470 | Edit card inline (change column-span, Save) | 📋 | |
| TS-471 | Delete dashboard card | 📋 | |
| TS-472 | All 9 valid card types in add dropdown | 📋 | |
| TS-473 | Card section hidden when server returns 404 | 📋 | |
| TS-474 | CRUD round-trip (API create → mobile visible → mobile delete → API confirms) | 📋 | |

### T27 — Automata Orchestrator E2E (TS-475–TS-494)

| Story | Title | Status | Notes |
|-------|-------|--------|-------|
| TS-475 | Orchestrator subsystem enabled (config check) | 📋 | |
| TS-476 | Create graph via API (POST /api/orchestrator/graphs) | 📋 | |
| TS-477 | List graphs via API (GET — graph appears) | 📋 | |
| TS-478 | Get graph detail via API (nodes + edges arrays) | 📋 | |
| TS-479 | Run graph via API (status advances from draft) | 📋 | |
| TS-480 | Cancel graph via API (status → cancelled) | 📋 | |
| TS-481 | Delete graph via API (cleanup) | 📋 | |
| TS-482 | OrchestratorGraphsCard section visible (Settings → Automata) | 📋 | |
| TS-483 | Empty state — "No orchestrator graphs" | 📋 | |
| TS-484 | Create graph via mobile UI (title + dir form) | 📋 | |
| TS-485 | Graph row: title, status dot, automata count, ▶/✕ buttons | 📋 | |
| TS-486 | Title required validation (blank → inline error) | 📋 | |
| TS-487 | Run graph via mobile ▶ button (status dot changes) | 📋 | |
| TS-488 | Status dot colors (running=purple, done=green, failed=red, cancelled=grey) | 📋 | |
| TS-489 | Delete graph via mobile ✕ button | 📋 | |
| TS-490 | OrchestratorGraphDialog accessible from PRD detail (Graph button) | 📋 | |
| TS-491 | Graph dialog shows node list (name, status) | 📋 | |
| TS-492 | Graph dialog shows edges (→ target lines) | 📋 | |
| TS-493 | Graph dialog node status colors (running=green, approved=blue, review=amber, rejected=red) | 📋 | |
| TS-494 | Full E2E arc: API create → mobile run → API cancel → mobile shows cancelled | 📋 | |

---

### T22 — Wear OS Surface Tests (TS-500–TS-514)

| Story | Title | Status | Notes |
|-------|-------|--------|-------|
| TS-500 | WearMainActivity launches — health ring visible | ⏳ Blocked | Physical watch: ADB not enabled at 192.168.1.244 |
| TS-501 | BriefingTileService renders session counts | ⏳ Blocked | Physical watch |
| TS-502 | AlertsTileService renders unread alert count | ⏳ Blocked | Physical watch |
| TS-503 | MonitorTileService renders CPU/memory | ⏳ Blocked | Physical watch |
| TS-504 | SessionsTileService renders session list | ⏳ Blocked | Physical watch |
| TS-505 | WaitingTileService renders waiting session count | ⏳ Blocked | Physical watch |
| TS-506 | StatusComplicationService on watch face | ⏳ Blocked | Physical watch |
| TS-507 | CpuComplicationService + MemoryComplicationService | ⏳ Blocked | Physical watch |
| TS-508 | ServerSwitchComplicationService cycles active server | ⏳ Blocked | Physical watch |
| TS-509 | Guardrail block notification + triple-buzz haptic | ⏳ Blocked | Physical watch required for haptic |
| TS-510 | WearApproveScreen confirms approve action | ⏳ Blocked | Physical watch |
| TS-511 | Voice query "status" returns spoken TTS | ⏳ Blocked | Physical watch |
| TS-512 | Voice query "any blocks?" triggers blocked summary | ⏳ Blocked | Physical watch |
| TS-513 | Wear JVM unit tests pass (88 tests) | 📋 | `./gradlew :wear:testDebugUnitTest` |
| TS-514 | Wear APK compiles and installs on emulator | 📋 | `./gradlew :wear:assembleDebug` |

### T23 — Android Auto Surface Tests (TS-515–TS-529)

| Story | Title | Status | Notes |
|-------|-------|--------|-------|
| TS-515 | AutoMissionControlScreen renders session counts | ⏳ Blocked | DHU required |
| TS-516 | AutoSessionListScreen — blocked-first sort | ⏳ Blocked | DHU required |
| TS-517 | AutoSessionDetailScreen — task + guardrail verdict | ⏳ Blocked | DHU required |
| TS-518 | Action buttons: max 2 per template (Drive compliance) | ⏳ Blocked | DHU required |
| TS-519 | Kill session requires 2-tap confirmation + 15s auto-cancel | ⏳ Blocked | DHU required |
| TS-520 | AutoAutomataScreen lists running automata | ⏳ Blocked | DHU required |
| TS-521 | Voice command: "status" reads server summary | ⏳ Blocked | DHU required |
| TS-522 | Voice command: "switch to {name}" resolves by profile name | ⏳ Blocked | DHU required |
| TS-523 | Voice command: "what failed" → most recent BLOCKED session | ⏳ Blocked | DHU required |
| TS-524 | Ambient mode: monochrome, no action buttons, 60s refresh | ⏳ Blocked | DHU required |
| TS-525 | Alert dismiss from Auto | ⏳ Blocked | DHU required |
| TS-526 | Drive compliance: ListTemplate row count ≤ 6 | ⏳ Blocked | DHU required |
| TS-527 | Multi-server quick-switch row in mission control | ⏳ Blocked | DHU required |
| TS-528 | Back-stack: MissionControl → SessionList → SessionDetail → back x2 | ⏳ Blocked | DHU required |
| TS-529 | Auto JVM unit tests pass (92 tests) | 📋 | `./gradlew :composeApp:testDevDebugUnitTest --tests "*.auto*"` |

### T24 — Algorithm Mode Tests (TS-530–TS-541)

| Story | Title | Status | Notes |
|-------|-------|--------|-------|
| TS-530 | Algorithm Mode card visible in Settings → Automata | 📋 | |
| TS-531 | Start algorithm session by session ID | 📋 | |
| TS-532 | Advance phase (observe → orient → … → improve) | 📋 | |
| TS-533 | Abort session — red dot, Advance/Abort hidden | 📋 | |
| TS-534 | Reset restores to observe phase | 📋 | |
| TS-535 | Edit phase output (text field → Edit button) | 📋 | |
| TS-536 | Measure: run eval suite by name | 📋 | |
| TS-537 | Phase strip dot colors (done=teal, current=blue pulse, aborted=red, future=grey) | 📋 | |
| TS-538 | Edit/Measure fields hidden when session aborted | 📋 | |
| TS-539 | Algorithm list populated on card open (LaunchedEffect) | 📋 | |
| TS-540 | Multiple sessions shown with HorizontalDivider | 📋 | |
| TS-541 | Session ID field clears after successful Start | 📋 | |

---

## Release Checklist

- [ ] T1–T14: All non-skip ✅ Pass
- [ ] T13: Re-run — #48 closed; Cancel+Clone fixes landed
- [ ] T15: Re-run — all server endpoints (#40-43) closed; client implemented
- [ ] T16: Re-run — #39 closed; UnifiedPush client wired
- [ ] T17: Parity audit pass
- [ ] T18: Test debt all written + passing
- [ ] T19: Dashboard hooks integration pass
- [ ] T22: JVM tests (TS-513/514) pass; physical-watch stories ⏳ Blocked acceptable
- [ ] T23: JVM tests (TS-529) pass; DHU stories ⏳ Blocked acceptable
- [ ] T24: All 12 Algorithm Mode stories ✅ Pass
- [ ] T26: Dashboard Cards CRUD (Android) pass
- [ ] T27: Automata Orchestrator E2E (Android) pass
- [ ] Version bump: v1.0.0 in gradle.properties + Version.kt
- [ ] CHANGELOG.md updated
- [ ] Play Console release (Internal Testing → Beta → Production)
- [ ] GitHub release: tag v1.0.0, release notes

---

**Last test run**: (none yet — plan created 2026-05-14)
