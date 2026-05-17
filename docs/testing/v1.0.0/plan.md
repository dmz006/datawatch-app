# datawatch-app v1.0.0 End-to-End Test Plan

**Version**: v1.0.0 (Mobile Client Release)  
**Date**: 2026-05-14  
**Scope**: Android phone + Wear OS + Android Auto surfaces  
**Test environment**: Secondary datawatch instance (ports 18080/18443) + emulator `dw_test_phone`  
**Success criterion**: T1–T19 stories pass; if tests pass, ship v1.0.0  

---

## Overview

This plan covers **all mobile, Wear, and Auto surfaces** in a comprehensive test structure. Unlike the server's v7.0.0 plan, we test **only the client-side behavior** — server infrastructure is tested upstream in datawatch repo.

### Plan vs Cookbook vs Evidence

| Artifact | Location | Persisted? | Purpose |
|---|---|---|---|
| **Plan** (`plan.md`) | `docs/testing/v1.0.0/plan.md` | ✅ Yes | Defines every story: steps, expected, evidence filenames. Immutable reference. |
| **Cookbook** (`cookbook.md`) | `docs/testing/v1.0.0/cookbook.md` | ✅ Yes | Live status table updated after every story. Only persistent record of what passed/failed. |
| **Evidence** (`evidence/TS-NNN/`) | `docs/testing/v1.0.0/evidence/` | ❌ Gitignored + deleted | Screenshots, logcat, JSON responses. Exists only during a run. Preserved on FAIL for diagnosis. |

### Design decisions

| Decision | Value |
|---|---|
| Secondary instance | Same host; data dir `.datawatch-test/`; ports 18080/18443 |
| Emulator | `dw_test_phone` (Android 14 / API 34, Pixel 6) |
| Mobile server URL | `https://10.0.2.2:18443` (adb reverse tcp:18443 tcp:18443) |
| Test token | `dw-test-token-12345` |
| Wear device | Physical Wear OS watch (192.168.1.244:44631) or emulator pairing |
| Auto surface | Android Auto emulator (separate from phone emulator) |

---

## 1. Environment Setup

### Secondary Test Instance (datawatch server)

**Prerequisite**: datawatch binary at `~/workspace/datawatch/bin/datawatch`

**Start**:
```bash
mkdir -p .datawatch-test
cat > .datawatch-test/config.yaml <<EOF
server:
  port: 18080
  tls_port: 18443
  token: "dw-test-token-12345"
session:
  skip_permissions: true
autonomous:
  enabled: true
memory:
  enabled: true
EOF

rtk ./bin/datawatch serve --data-dir .datawatch-test 2>&1 | tee /tmp/test-server.log &
sleep 3
curl -sk https://127.0.0.1:18443/api/health
```

**Emulator bridge**:
```bash
adb reverse tcp:18443 tcp:18443
adb reverse tcp:18080 tcp:18080
```

**Cleanup** (after run):
```bash
pkill -f "datawatch serve --data-dir .datawatch-test"
rm -rf .datawatch-test evidence/
```

### Mobile App (emulator)

**Start emulator**:
```bash
/home/dmz/workspace/Android/Sdk/emulator/emulator \
  -avd dw_test_phone \
  -no-snapshot-save \
  -no-audio \
  -gpu swiftshaker_indirect \
  -no-boot-anim \
  2>/tmp/emulator.log &

adb wait-for-device
adb shell getprop sys.boot_completed
```

**Build & install**:
```bash
rtk ./gradlew :composeApp:assemblePublicTrackDebug
adb -s emulator-5554 install -r composeApp/build/outputs/apk/publicTrack/debug/*.apk
```

**Configure test server** (in-app):
- Settings → Comms → Add server
- Name: `dw-test`
- URL: `https://10.0.2.2:18443`
- Bearer token: `dw-test-token-12345`
- Trust-all TLS: `true`
- Tap Save

---

## 2. T-Sprint Index

| T-Sprint | Area | Stories | Target |
|----------|------|---------|--------|
| T1 | Onboarding & server add | TS-001–TS-010 | ✅ Pass |
| T2 | Session list & refresh | TS-011–TS-035 | ✅ Pass |
| T3 | Session detail / terminal | TS-036–TS-060 | ✅ Pass |
| T4 | New session creation | TS-061–TS-075 | ✅ Pass |
| T5 | Alerts | TS-076–TS-095 | ✅ Pass |
| T6 | Observer/Monitor | TS-096–TS-115 | ✅ Pass |
| T7 | Settings — General/Comms/Compute | TS-116–TS-140 | ✅ Pass |
| T8 | Settings — Automata/PRDs | TS-141–TS-165 | ✅ Pass (blocked on #48) |
| T9 | Navigation & shell | TS-166–TS-180 | ✅ Pass |
| T10 | Push & notifications | TS-181–TS-195 | ✅ Pass (Wear blocked) |
| T11 | Security & keystore | TS-196–TS-205 | ✅ Pass |
| T12 | Multi-server & federation | TS-206–TS-220 | ✅ Pass |
| T13 | Autonomous / PRD lifecycle | TS-221–TS-255 | 🟡 #48 closed; re-run pending |
| T14 | Regression — session refresh | TS-256–TS-285 | ✅ Pass |
| T15 | New server endpoints | TS-286–TS-305 | 🟡 #40-43 all closed; client implemented |
| T16 | UnifiedPush Tier 1 | TS-306–TS-315 | 🟡 #39 closed; ready to run |
| T17 | Parity audit | TS-316–TS-325 | 📋 Planned |
| T18 | Test debt payoff | TS-326–TS-343 | 📋 Planned |
| T19 | Dashboard hooks integration | TS-344–TS-350 | 📋 Planned |
| T20 | Howto validation (datawatch docs) | TS-360–TS-400 | 📋 Planned |
| T21 | End-to-end user journeys | TS-410–TS-420 | 📋 Planned |
| T22 | Wear OS surface tests | TS-500–TS-514 | 📋 Planned |
| T23 | Android Auto surface tests | TS-515–TS-529 | 📋 Planned |
| T24 | Algorithm Mode tests | TS-530–TS-541 | 📋 Planned |
| T26 | Dashboard Cards CRUD (Android) | TS-465–TS-474 | 📋 Planned |
| T27 | Automata Orchestrator E2E (Android) | TS-475–TS-494 | 📋 Planned |

---

## 3. Tag Taxonomy

**Surface tags**:
- `[surface:phone]` — Phone (Pixel 6, emulator)
- `[surface:wear]` — Wear OS watch
- `[surface:auto]` — Android Auto car surface
- `[surface:api]` — REST API interaction

**Feature tags**:
- `[feature:sessions]` — Session lifecycle
- `[feature:alerts]` — Alert system
- `[feature:autonomous]` — Automata/PRD lifecycle
- `[feature:settings]` — Settings panels
- `[feature:push]` — Push notifications
- `[feature:security]` — Auth, keystore, biometric
- `[feature:multiserver]` — Multi-server + federation
- `[feature:identity]` — Identity card (server #40)
- `[feature:algorithm]` — Algorithm mode (server #41)
- `[feature:evals]` — Evals card (server #42)
- `[feature:council]` — Council sessions (server #43)
- `[feature:unifiedpush]` — Tier 1 push (server #39)
- `[feature:parity]` — Cross-surface feature parity
- `[feature:dashboard]` — Dashboard Cards CRUD (Settings → Monitor tab)
- `[feature:orchestrator]` — Automata Orchestrator DAG graphs
- `[feature:wear-tiles]` — Wear OS tiles (Briefing, Alerts, Monitor, Sessions, Waiting)
- `[feature:wear-complications]` — Wear OS complications (Status, CPU, Memory, Sessions, Alerts, Automata, ServerSwitch, Waiting)
- `[feature:wear-voice]` — Wear OS voice query dispatcher
- `[feature:wear-notifications]` — Wear OS guardrail block notifications + haptics
- `[feature:auto-screens]` — Android Auto screens (MissionControl, SessionList, SessionDetail, Automata)
- `[feature:auto-voice]` — Android Auto voice commands
- `[feature:algorithm]` — Algorithm Mode OODA loop (Start/Advance/Abort/Reset/Edit/Measure)

**Conflict tags**:
- `[conflict:physical-watch]` — Requires physical Wear device
- `[conflict:biometric]` — Requires biometric enrollment
- `[conflict:compute-daemon]` — Requires compute node/LLM
- `[conflict:signal]` — Requires Signal comm channel
- `[conflict:network]` — Requires network state change
- `[conflict:physical-auto]` — Requires Android Auto head unit or DHU emulator
- `[conflict:wear-haptic]` — Requires physical Wear device for haptic verification

---

## T1–T14: Migrated Stories (Existing Tests)

Stories TS-001 through TS-285 from the prior test plan. See `cookbook.md` for current status and prior run notes. All stories run against the secondary test instance (not production `ring` server).

**Key changes from prior runs**:
- Server URL: `https://10.0.2.2:18443` (secondary instance) instead of ring
- Token: `dw-test-token-12345` instead of production token
- Evidence saved to `evidence/TS-NNN/` (gitignored)

---

## T15 — New Server Endpoints (Identity / Algorithm / Evals / Council)

**Prerequisite**: datawatch server ships #40, #41, #42, #43 endpoints

### TS-286 — Identity endpoint GET
**Tags**: [surface:phone] [feature:identity]
**Steps**: 
1. Settings → Automata → Identity card should be empty (waiting for endpoint)
2. Once server #40 ships: GET /api/identity returns `{role, current_focus, context_notes}`
3. Refresh settings
**Expected**: Identity card populates with server data
**Evidence**: `identity_get.json`, `identity_card.png`
**Status**: ⏳ Blocked on datawatch#40

### TS-287–TS-305: (14 stories similar pattern for Council/Algorithm/Evals CRUD)

---

## T16 — UnifiedPush Tier 1 Integration

**Prerequisite**: datawatch server ships #39 (UnifiedPush provider + `/api/push/*` endpoints)

### TS-306 — Mobile registers for push
**Tags**: [surface:phone] [feature:unifiedpush] [feature:push]
**Steps**:
1. App launches, `UnifiedPushSseService` discovers `/.well-known/unifiedpush`
2. Registers device endpoint: POST /api/push/register with `{endpoint, keys}`
3. Check secondary instance logs: registration accepted
**Expected**: 200 OK, device endpoint stored on server
**Evidence**: `push_register.json`, logcat
**Status**: ⏳ Blocked on datawatch#39

### TS-307–TS-315: (9 stories for push receipt, notification display, quick-reply, fallback chain)

---

## T17 — Parity Audit

**Goal**: Verify mobile surfaces match server feature surfaces.

### TS-316 — Sessions API contract
**Tags**: [surface:phone] [surface:api] [feature:sessions] [feature:parity]
**Steps**:
1. GET /api/sessions against secondary instance
2. Create session in mobile app
3. GET /api/sessions again, verify new session appears
4. DELETE session from mobile
5. GET /api/sessions, verify deleted
**Expected**: All CRUD operations match server state
**Evidence**: `sessions_contract.json`
**Status**: 📋 Planned

### TS-317–TS-325: (9 stories for Alerts, Autonomous, Config, Memory, Channels, Locale, LLM #46, Locale #47, Token auth)

---

## T18 — Test Debt Payoff

**Goal**: Execute all deferred unit tests from sprints 17–22 (backlog in sprint-plan.md).

### TS-326 — DtoRoundTripTest: backendFamily fallback
**Tags**: [surface:api]
**Steps**:
1. Run: `rtk ./gradlew :shared:testDebugUnitTest -k DtoRoundTrip`
2. Assert: `SessionDto` with `backendFamily=ollama, llmBackend=null` → `Session.backend=ollama`
3. Assert: `SessionDto` with `backendFamily=null, llmBackend=claude-code` → `Session.backend=claude-code`
**Expected**: Test passes; fallback logic verified
**Evidence**: test output, coverage report
**Status**: 📋 Planned

### TS-327–TS-343: (17 stories, one per deferred test class)

---

## T19 — Dashboard Hooks Integration

**Goal**: Verify mobile test runner integrates with datawatch dashboard (smoke-progress tracking).

### TS-344 — smoke-progress.json writes before first T-sprint
**Tags**: [surface:api]
**Steps**:
1. Test runner writes `~/.datawatch-test/smoke-progress.json` before T1 starts
2. Verify file contains: `{"active": true, "phase": "T1", "pass": 0, "fail": 0}`
**Expected**: File exists and has correct JSON shape
**Evidence**: `smoke_progress_init.json`
**Status**: 📋 Planned

### TS-345–TS-350: (6 stories for progress updates, Smoke Run card, history, cleanup)

---

## T20 — Howto Validation (Datawatch Documentation Workflows)

**Goal**: Verify that each datawatch howto's documented workflow works end-to-end in the mobile app.

### TS-360 — autonomous-planning.md: Create PRD, decompose, review
**Tags**: [surface:phone] [feature:autonomous]
**Steps**:
1. Follow autonomous-planning.md workflow: Create PRD → fill task/project → tap Decompose
2. Verify PRD enters `planning` state; stories appear in Stories tab
3. Review each story; tap Approve
**Expected**: Workflow completes as documented (or notes blocker if datawatch#48 prevents decompose)
**Evidence**: `autonomous_planning_workflow.json`, screenshots of each step
**Status**: ⏳ Blocked (datawatch#48 decompose timeout)

### TS-365 — autonomous-review-approve.md: Review & approve PRD
**Tags**: [surface:phone] [feature:autonomous]
**Status**: ⏳ Blocked (datawatch#48)

### TS-370 — profiles.md: Create/switch/use project profiles
**Tags**: [surface:phone] [feature:sessions]
**Status**: 📋 Planned

### TS-375 — llm-registry.md: Register LLM, enable, set default, use in session
**Tags**: [surface:phone] [feature:settings]
**Status**: ⏳ Blocked (compute daemon unreachable)

### TS-380 — secrets-manager.md: Create secret, use in config, rotate
**Tags**: [surface:phone] [feature:security]
**Status**: 📋 Planned

### TS-385 — federated-observer.md: View federated peer stats and latency
**Tags**: [surface:phone] [feature:multiserver]
**Status**: 📋 Planned

### TS-390 — comm-channels.md: Set up channels (Signal, Webhook, Discord, ntfy)
**Tags**: [surface:phone] [feature:push]
**Status**: ⏳ Blocked (requires external services)

### TS-395 — dashboard.md: Navigate to dashboard, configure cards
**Tags**: [surface:phone] [feature:settings]
**Status**: ⏳ Blocked (PWA-only; mobile accesses via API)

### TS-400 — session-telemetry.md: Capture and view session telemetry data
**Tags**: [surface:phone] [feature:sessions]
**Status**: 📋 Planned

---

## T21 — End-to-End User Journeys

**Goal**: Verify complete multi-T-sprint workflows that combine multiple howtos.

### TS-410 — New User Arc: setup → identity → create session → alert → reply
**Tags**: [surface:phone] [feature:bootstrap]
**Steps**:
1. Fresh install: Splash → Onboarding → Add server (setup-and-install.md)
2. Settings → Automata → Identity → fill role/focus (identity-and-telos.md)
3. Sessions tab → FAB → Create session (sessions-deep-dive.md)
4. Session enters waiting_input state
5. Notification arrives; tap to open (push-notifications.md)
6. Reply via composer; session continues
**Expected**: Complete new user onboarding through first interaction works end-to-end
**Evidence**: `new_user_arc_workflow.json`
**Status**: 📋 Planned

### TS-415 — Autonomous Arc: create PRD → council review → approve → run
**Tags**: [surface:phone] [feature:autonomous]
**Status**: ⏳ Blocked (datawatch#48)

### TS-420 — Power User Multi-Server Arc: setup profiles → switch servers → observer → config replication
**Tags**: [surface:phone] [feature:multiserver]
**Status**: ⏳ Blocked (requires two distinct servers)

---

## T26 — Dashboard Cards CRUD (Android)

**Goal**: Verify the `DashboardCardsCard` (Settings → Monitor tab, BL303/alpha.75 #132) — list, add, edit, and delete dashboard card entries used to configure the PWA dashboard layout from mobile.

**Location in app**: Settings → Monitor tab → scroll to "DASHBOARD CARDS" section.

**Valid card IDs**: `tree`, `orbital`, `events`, `sparklines`, `gantt`, `heatmap`, `guardrails`, `ekg`, `smoke`.

**API**: `GET /api/dashboard/cards`, `POST /api/dashboard/cards`, `PUT /api/dashboard/cards/{id}`, `DELETE /api/dashboard/cards/{id}`.

### TS-465 — DashboardCardsCard section visible in Settings → Monitor
**Tags**: [surface:phone] [feature:dashboard]
**Steps**:
1. Settings → Monitor tab → scroll to bottom
2. Verify "DASHBOARD CARDS" section heading is present
3. Verify the add-card dropdown and "Add" button are visible
**Expected**: Section renders; no crash
**Evidence**: `t26_dashboard_cards_section.png`
**Status**: 📋 Planned

### TS-466 — DashboardCardsCard empty state
**Tags**: [surface:phone] [feature:dashboard]
**Steps**:
1. Ensure no dashboard cards are configured on test server: `curl -sk https://127.0.0.1:18443/api/dashboard/cards -H "Authorization: Bearer dw-test-token-12345"`
2. If cards exist, delete them
3. Navigate to Settings → Monitor → DASHBOARD CARDS
4. Verify empty-state text is shown (e.g. "No cards configured")
**Expected**: Empty state label visible; add form still present
**Evidence**: `t26_empty_state.png`
**Status**: 📋 Planned

### TS-467 — Add dashboard card (smoke type, full-width)
**Tags**: [surface:phone] [feature:dashboard]
**Steps**:
1. Settings → Monitor → DASHBOARD CARDS
2. Tap the Card ID dropdown → select "smoke"
3. Leave column-span slider at 12 (default)
4. Leave row-span blank
5. Tap "Add Card"
6. Verify card appears in list: `smoke · cs=12`
**Expected**: Card appears in list; API confirms: `GET /api/dashboard/cards` returns entry with `id=smoke, cs=12`
**Evidence**: `t26_add_smoke_card.png`, `t26_cards_after_add.json`
**Status**: 📋 Planned

### TS-468 — Add dashboard card (tree type, cs=6, rs=2)
**Tags**: [surface:phone] [feature:dashboard]
**Steps**:
1. Tap Card ID dropdown → select "tree"
2. Set column-span slider to 6
3. Enter row-span = 2
4. Tap "Add Card"
5. Verify card appears: `tree · cs=6 rs=2`
**Expected**: Card added; API confirms `id=tree, cs=6, rs=2`
**Evidence**: `t26_add_tree_card.png`
**Status**: 📋 Planned

### TS-469 — Card list shows both cards with correct metadata
**Tags**: [surface:phone] [feature:dashboard]
**Steps**:
1. After TS-467 and TS-468, scroll through card list
2. Verify both `smoke` (cs=12) and `tree` (cs=6 rs=2) appear
3. Verify each row shows id + cs/rs in subtitle
**Expected**: Both cards listed; metadata accurate
**Evidence**: `t26_card_list.png`
**Status**: 📋 Planned

### TS-470 — Edit card inline (change column-span)
**Tags**: [surface:phone] [feature:dashboard]
**Steps**:
1. Tap `smoke` card row to expand inline editor
2. Move column-span slider from 12 to 6
3. Tap "Save"
4. Verify row updates: `smoke · cs=6`
5. Confirm via API: `GET /api/dashboard/cards` shows `cs=6`
**Expected**: Inline save persists change; list reflects update
**Evidence**: `t26_edit_card.png`, `t26_cards_after_edit.json`
**Status**: 📋 Planned

### TS-471 — Delete dashboard card
**Tags**: [surface:phone] [feature:dashboard]
**Steps**:
1. Tap trash icon on `tree` card row
2. Verify `tree` row disappears from list
3. Confirm via API: `GET /api/dashboard/cards` no longer includes `tree`
**Expected**: Card deleted; list refreshed
**Evidence**: `t26_after_delete.json`
**Status**: 📋 Planned

### TS-472 — All 9 valid card types appear in the add dropdown
**Tags**: [surface:phone] [feature:dashboard]
**Steps**:
1. Tap Card ID dropdown in the add section
2. Verify all 9 types listed: tree, orbital, events, sparklines, gantt, heatmap, guardrails, ekg, smoke
**Expected**: Exactly 9 options visible; no extras or missing
**Evidence**: `t26_dropdown_options.png`
**Status**: 📋 Planned

### TS-473 — DashboardCardsCard hidden when server returns 404
**Tags**: [surface:phone] [feature:dashboard]
**Steps**:
1. Temporarily point app at a server that returns 404 for `/api/dashboard/cards`
   (workaround: delete the cards endpoint or use a stub that returns 404)
2. Navigate to Settings → Monitor
3. Verify DashboardCardsCard section is **not** visible (card hides itself on 404)
**Expected**: Card self-hides gracefully; no error banner shown to user
**Evidence**: `t26_hidden_on_404.png`
**Status**: 📋 Planned

### TS-474 — Dashboard cards CRUD round-trip (API + mobile consistency)
**Tags**: [surface:phone] [surface:api] [feature:dashboard]
**Steps**:
1. POST card via API: `curl -sk -X POST https://127.0.0.1:18443/api/dashboard/cards -H "Authorization: Bearer dw-test-token-12345" -d '{"id":"ekg","cs":12}'`
2. Navigate to Settings → Monitor → DASHBOARD CARDS — verify ekg card appears
3. Tap trash icon on ekg card in mobile UI
4. GET /api/dashboard/cards — verify ekg is gone
**Expected**: API-created card visible in mobile; mobile delete removes from API
**Evidence**: `t26_roundtrip.json`
**Status**: 📋 Planned

---

## T27 — Automata Orchestrator E2E (Android)

**Goal**: Full end-to-end test of the Automaton-DAG orchestrator — from API graph creation through topological execution, via the mobile `OrchestratorGraphsCard` (Settings → Automata) and the `OrchestratorGraphDialog` (PRD detail → "Graph" button). Covers create/list/run/cancel at API level and the corresponding mobile UI interactions.

**Location in app**:
- `OrchestratorGraphsCard`: Settings → Automata tab → scroll to "ORCHESTRATOR GRAPHS"
- `OrchestratorGraphDialog`: Autonomous tab → tap PRD → PRD detail → tap "Graph" button (top-right of header)

**API base**: `/api/orchestrator/graphs`

**State machine**: `draft` → `planning` → `needs_review` → `approved` → `running` → `paused` / `done` / `failed` / `cancelled`

### TS-475 — Orchestrator subsystem enabled
**Tags**: [surface:api] [feature:orchestrator]
**Steps**:
1. `curl -sk https://127.0.0.1:18443/api/config -H "Authorization: Bearer dw-test-token-12345" | jq '.orchestrator.enabled'`
**Expected**: `true` (or equivalent — orchestrator is on by default in alpha.71+)
**Evidence**: `t27_orchestrator_config.json`
**Status**: 📋 Planned

### TS-476 — Create orchestrator graph via API
**Tags**: [surface:api] [feature:orchestrator]
**Steps**:
1. `POST /api/orchestrator/graphs` with `{"title": "t27-graph-a", "project_dir": "/home/dmz/workspace/datawatch-test-workspace"}`
2. Verify 200/201; capture `id`
**Expected**: Graph created with status `draft`; id returned
**Evidence**: `t27_graph_create.json`
**Status**: 📋 Planned

### TS-477 — List orchestrator graphs via API
**Tags**: [surface:api] [feature:orchestrator]
**Steps**:
1. `GET /api/orchestrator/graphs`
2. Verify response includes `t27-graph-a` with correct `title` and `status: draft`
**Expected**: Graph appears in list; `prd_ids` initially empty
**Evidence**: `t27_graph_list.json`
**Status**: 📋 Planned

### TS-478 — Get graph detail via API
**Tags**: [surface:api] [feature:orchestrator]
**Steps**:
1. `GET /api/orchestrator/graphs/{id}` (from TS-476)
2. Verify response includes `nodes` array and `edges` array (may be empty at draft)
**Expected**: Detail endpoint returns graph structure; status=draft
**Evidence**: `t27_graph_detail.json`
**Status**: 📋 Planned

### TS-479 — Run orchestrator graph via API
**Tags**: [surface:api] [feature:orchestrator]
**Steps**:
1. `POST /api/orchestrator/graphs/{id}/run`
2. Verify 200; re-GET graph detail
3. Verify `status` transitions from `draft` toward `running` or `planning`
**Expected**: Graph status advances; no 4xx/5xx error
**Evidence**: `t27_graph_run.json`
**Status**: 📋 Planned

### TS-480 — Cancel orchestrator graph via API
**Tags**: [surface:api] [feature:orchestrator]
**Steps**:
1. With graph in running/planning state (from TS-479), `POST /api/orchestrator/graphs/{id}/cancel`
2. Verify 200; re-GET graph
3. Verify `status: cancelled`
**Expected**: Cancel transitions graph to cancelled; no orphan tasks
**Evidence**: `t27_graph_cancel.json`
**Status**: 📋 Planned

### TS-481 — Delete orchestrator graph via API (cleanup)
**Tags**: [surface:api] [feature:orchestrator]
**Steps**:
1. `DELETE /api/orchestrator/graphs/{id}`
2. Verify 200/204
3. `GET /api/orchestrator/graphs` — verify graph no longer listed
**Expected**: Graph deleted; list clean
**Evidence**: `t27_graph_delete.json`
**Status**: 📋 Planned

### TS-482 — OrchestratorGraphsCard section visible in Settings → Automata
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. Settings → Automata tab → scroll to "ORCHESTRATOR GRAPHS" section
2. Verify section heading, title input field, directory input field, and "Create Graph" button are present
**Expected**: Card renders; no crash
**Evidence**: `t27_orchestrator_card.png`
**Status**: 📋 Planned

### TS-483 — OrchestratorGraphsCard empty state
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. Ensure no graphs exist on test server (delete any from prior tests)
2. Navigate to Settings → Automata → ORCHESTRATOR GRAPHS
3. Verify "No orchestrator graphs" (or equivalent empty) text is shown
**Expected**: Empty state label visible; create form present
**Evidence**: `t27_empty_state.png`
**Status**: 📋 Planned

### TS-484 — Create graph via mobile UI
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. Settings → Automata → ORCHESTRATOR GRAPHS
2. Enter title: `t27-mobile-graph`
3. Enter directory: `/home/dmz/workspace/datawatch-test-workspace`
4. Tap "Create Graph"
5. Verify graph appears in list below form
**Expected**: Graph entry shows title `t27-mobile-graph`, status dot (grey/pending), "0 automata"
**Evidence**: `t27_mobile_create.png`
**Status**: 📋 Planned

### TS-485 — Graph list shows title, status dot, automata count
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. After TS-484, verify row shows:
   - Status indicator dot (grey = pending/draft)
   - Title: `t27-mobile-graph`
   - Subtitle: `pending · 0 automata` (or equivalent)
   - ▶ run button and ✕ delete button
**Expected**: All row elements render correctly
**Evidence**: `t27_graph_row.png`
**Status**: 📋 Planned

### TS-486 — Title required validation
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. Leave title field blank; tap "Create Graph"
2. Verify error state on the title field (red outline + error text)
3. Verify no graph is created (list unchanged)
**Expected**: Inline validation fires; no API call made with blank title
**Evidence**: `t27_title_validation.png`
**Status**: 📋 Planned

### TS-487 — Run graph via mobile ▶ button
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. Tap ▶ button on `t27-mobile-graph` row
2. Wait ~2s; list reloads
3. Verify status dot changes to purple (running) or updated status
4. Confirm via API: `GET /api/orchestrator/graphs/{id}` shows non-draft status
**Expected**: Run action dispatched; status updates in UI
**Evidence**: `t27_mobile_run.png`, `t27_mobile_run_status.json`
**Status**: 📋 Planned

### TS-488 — Status dot colors (running=purple, done=green, failed=red, cancelled=grey)
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. Create graphs in different terminal states via API: one running, one done, one failed
2. Navigate to mobile OrchestratorGraphsCard
3. Verify dot colors match: `running`=purple (0xFF6366F1), `done`=green (0xFF10B981), `failed`=red (error color), `cancelled`=grey
**Expected**: All 4 status dot colors render correctly per the implementation
**Evidence**: `t27_status_colors.png`
**Status**: 📋 Planned

### TS-489 — Delete graph via mobile ✕ button
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. Tap ✕ button on `t27-mobile-graph` row
2. Verify graph disappears from list
3. Confirm via API: `GET /api/orchestrator/graphs` no longer includes it
**Expected**: Delete fires; list refreshes; API confirms removal
**Evidence**: `t27_mobile_delete.json`
**Status**: 📋 Planned

### TS-490 — OrchestratorGraphDialog accessible from PRD detail
**Tags**: [surface:phone] [feature:orchestrator] [feature:autonomous]
**Steps**:
1. Create a PRD via API (or from Autonomous tab)
2. Autonomous tab → tap the PRD to open PrdDetailDialog
3. In the dialog header row, find the "Graph" TextButton (top-right, next to story count)
4. Tap it — verify OrchestratorGraphDialog opens
**Expected**: Dialog opens; title shows PRD name or graph ID
**Evidence**: `t27_graph_dialog_open.png`
**Status**: 📋 Planned

### TS-491 — OrchestratorGraphDialog shows node list
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. With a graph that has nodes (create via API with `nodes` array), open OrchestratorGraphDialog
2. Verify each node row shows: status dot, node name/id, status label
**Expected**: Nodes rendered as list; status dot visible per node
**Evidence**: `t27_graph_dialog_nodes.png`
**Status**: 📋 Planned

### TS-492 — OrchestratorGraphDialog shows edges
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. With a graph that has edges (A → B dependency), open OrchestratorGraphDialog
2. Verify edges shown under the source node as "→ B (kind)" lines
**Expected**: DAG topology legible without arrows; edge lines indented under source node
**Evidence**: `t27_graph_dialog_edges.png`
**Status**: 📋 Planned

### TS-493 — OrchestratorGraphDialog node status colors
**Tags**: [surface:phone] [feature:orchestrator]
**Steps**:
1. Open dialog for a graph with nodes in different states
2. Verify node dot colors: running=green, complete/approved=blue, needs_review=amber, rejected/cancelled=red, other=grey
**Expected**: All 5 status color branches render correctly
**Evidence**: `t27_node_status_colors.png`
**Status**: 📋 Planned

### TS-494 — Full E2E arc: API create → mobile run → cancel → mobile confirms cancelled
**Tags**: [surface:phone] [surface:api] [feature:orchestrator]
**Steps**:
1. `POST /api/orchestrator/graphs` → create `t27-e2e-graph`
2. Navigate to Settings → Automata → ORCHESTRATOR GRAPHS — verify graph listed (grey dot)
3. Tap ▶ run button — verify dot turns purple
4. `POST /api/orchestrator/graphs/{id}/cancel` — cancel via API
5. Refresh card (navigate away and back to Settings → Automata)
6. Verify dot is now grey/cancelled
7. Tap ✕ — verify cleanup
**Expected**: Full state machine arc visible in mobile UI; API and mobile stay in sync
**Evidence**: `t27_e2e_arc.json`, `t27_e2e_arc_screenshots/`
**Status**: 📋 Planned

---

## T22 — Wear OS Surface Tests

**Goal**: Verify Wear OS tiles, complications, voice query, and guardrail notification flows work end-to-end via DataLayer proxy (no direct server access from watch).

**Conflict**: TS-509/510 require physical Wear device. All others run against emulator + DataLayer simulation.

### TS-500 — WearMainActivity launches and shows health ring
**Tags**: [surface:wear] [feature:wear-tiles] [conflict:physical-watch]
**Steps**:
1. Pair Wear emulator (or physical watch at 192.168.1.244:44631)
2. Install wear APK: `adb -s <watch> install -r wear/build/outputs/apk/debug/*.apk`
3. Launch datawatch watch app
**Expected**: Progress ring visible; server name header shown; no crash
**Evidence**: `t22_wear_main.png`
**Status**: ⏳ Blocked (physical watch: ADB not enabled at 192.168.1.244)

### TS-501 — BriefingTileService renders with session counts
**Tags**: [surface:wear] [feature:wear-tiles] [conflict:physical-watch]
**Steps**:
1. Add BriefingTile to watch face
2. Verify tile shows: server name, running count, blocked count
**Expected**: All fields populated from DataLayer; tile refreshes within 30s of phone state change
**Evidence**: `t22_briefing_tile.png`
**Status**: ⏳ Blocked (physical watch)

### TS-502 — AlertsTileService renders unread alert count
**Tags**: [surface:wear] [feature:wear-tiles] [conflict:physical-watch]
**Steps**:
1. POST alert via API; wait for DataLayer sync
2. View Alerts tile on watch
**Expected**: Alert count updates; tap tile → opens WearMainActivity
**Evidence**: `t22_alerts_tile.png`
**Status**: ⏳ Blocked (physical watch)

### TS-503 — MonitorTileService renders CPU/memory
**Tags**: [surface:wear] [feature:wear-tiles] [conflict:physical-watch]
**Steps**:
1. View Monitor tile on watch
**Expected**: CPU%, memory% displayed; server name in subtitle
**Evidence**: `t22_monitor_tile.png`
**Status**: ⏳ Blocked (physical watch)

### TS-504 — SessionsTileService renders session list
**Tags**: [surface:wear] [feature:wear-tiles] [conflict:physical-watch]
**Steps**:
1. View Sessions tile; verify running session names appear
**Expected**: Session names + state chips visible; tap → WearSessionListScreen
**Evidence**: `t22_sessions_tile.png`
**Status**: ⏳ Blocked (physical watch)

### TS-505 — WaitingTileService renders waiting session count
**Tags**: [surface:wear] [feature:wear-tiles] [conflict:physical-watch]
**Steps**:
1. Create session that enters waiting_input state
2. View Waiting tile
**Expected**: "1 waiting" count shown; dot amber
**Evidence**: `t22_waiting_tile.png`
**Status**: ⏳ Blocked (physical watch)

### TS-506 — StatusComplicationService renders on watch face
**Tags**: [surface:wear] [feature:wear-complications] [conflict:physical-watch]
**Steps**:
1. Add Status complication to watch face
**Expected**: SHORT_TEXT shows running/blocked counts; RANGED_VALUE shows progress float
**Evidence**: `t22_status_complication.png`
**Status**: ⏳ Blocked (physical watch)

### TS-507 — CpuComplicationService + MemoryComplicationService
**Tags**: [surface:wear] [feature:wear-complications] [conflict:physical-watch]
**Steps**:
1. Add CPU and Memory complications to watch face
**Expected**: CPU% and Memory% values shown; update on DataLayer change
**Evidence**: `t22_resource_complications.png`
**Status**: ⏳ Blocked (physical watch)

### TS-508 — ServerSwitchComplicationService switches active server
**Tags**: [surface:wear] [feature:wear-complications] [conflict:physical-watch]
**Steps**:
1. Add ServerSwitch complication; tap it
**Expected**: Active server cycles to next enabled profile; WearSyncService publishes updated activeServer DataItem
**Evidence**: `t22_server_switch.png`
**Status**: ⏳ Blocked (physical watch)

### TS-509 — Guardrail block notification fires on watch
**Tags**: [surface:wear] [feature:wear-notifications] [conflict:physical-watch] [conflict:wear-haptic]
**Steps**:
1. Trigger a guardrail block in a session (via secondary instance)
2. Watch for notification on watch within 5s
**Expected**: Notification title = guardrail name; text = block summary; triple-buzz haptic pattern
**Evidence**: `t22_block_notification.png`
**Status**: ⏳ Blocked (physical watch required for haptic verification)

### TS-510 — WearApproveScreen confirms approve action
**Tags**: [surface:wear] [feature:wear-notifications] [conflict:physical-watch]
**Steps**:
1. Tap [Approve] on guardrail notification
2. WearApproveScreen opens; tap Confirm
**Expected**: Approve dispatched via DataLayer; phone calls `/api/autonomous/prds/{id}/approve`; ascending double-tap haptic
**Evidence**: `t22_approve_flow.png`
**Status**: ⏳ Blocked (physical watch)

### TS-511 — Voice query "status" returns spoken response
**Tags**: [surface:wear] [feature:wear-voice] [conflict:physical-watch]
**Steps**:
1. Open VoiceQueryDispatcher on watch; speak "status"
2. Listen for TTS response
**Expected**: Response reads running/blocked counts; under 15 seconds
**Evidence**: `t22_voice_status.png`
**Status**: ⏳ Blocked (physical watch)

### TS-512 — Voice query "any blocks?" triggers blocked session summary
**Tags**: [surface:wear] [feature:wear-voice] [conflict:physical-watch]
**Steps**:
1. Speak "any blocks?" while a session has a guardrail block
**Expected**: TTS reads block summary; navigates to WearApproveScreen if available
**Evidence**: `t22_voice_blocks.png`
**Status**: ⏳ Blocked (physical watch)

### TS-513 — WearSyncService DataLayer heartbeat (JVM unit test)
**Tags**: [surface:wear] [feature:wear-tiles]
**Steps**:
1. `rtk ./gradlew :wear:testDebugUnitTest`
2. Verify all 88 Wear JVM unit tests pass
**Expected**: 88 tests, 0 failures, 0 errors
**Evidence**: test output
**Status**: 📋 Planned

### TS-514 — Wear APK compiles and installs on emulator
**Tags**: [surface:wear]
**Steps**:
1. `rtk ./gradlew :wear:assembleDebug`
2. Install on Wear OS emulator (if paired)
**Expected**: Build succeeds; no lint errors in Wear module
**Evidence**: build output
**Status**: 📋 Planned

---

## T23 — Android Auto Surface Tests

**Goal**: Verify Android Auto screens, voice commands, and ambient mode work with the secondary test instance via the Auto DHU emulator.

**Conflict**: All TS-515–TS-528 require Android Auto DHU (Desktop Head Unit) or physical head unit. TS-529 is a JVM-only build check.

### TS-515 — AutoMissionControlScreen renders session counts
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. Start DHU: `cd $ANDROID_HOME/extras/google/auto && ./desktop-head-unit`
2. Launch app via Auto
3. Verify mission control entry screen: running/waiting/blocked counts + server header
**Expected**: ListTemplate renders; counts accurate; server name in header
**Evidence**: `t23_mission_control.png`
**Status**: ⏳ Blocked (DHU required)

### TS-516 — AutoSessionListScreen shows sessions sorted by urgency
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. From mission control, navigate to session list
2. Verify BLOCKED sessions appear first, then RUNNING, then recency
**Expected**: Sort order matches: blocked-first, then running, then recency
**Evidence**: `t23_session_list.png`
**Status**: ⏳ Blocked (DHU required)

### TS-517 — AutoSessionDetailScreen shows task + guardrail verdict
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. Tap a running session row
2. Verify MessageTemplate: current task, sprint ancestry, health status
**Expected**: Body ≤ 500 chars; ETA shown; no crash
**Evidence**: `t23_session_detail.png`
**Status**: ⏳ Blocked (DHU required)

### TS-518 — AutoSessionDetailScreen action buttons: max 2 per template
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. View BLOCKED session detail — verify [Approve Gate] + [Kill Session] shown (max 2)
2. View non-blocked session detail — verify [Reply] + [Kill Session] shown
**Expected**: Never more than 2 action buttons; correct pair per state
**Evidence**: `t23_action_buttons.png`
**Status**: ⏳ Blocked (DHU required)

### TS-519 — Kill session requires 2-tap confirmation
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. Tap [Kill Session] in AutoSessionDetailScreen
2. Verify confirmation dialog appears with 15s auto-cancel
3. Confirm — verify session killed
**Expected**: 2-tap with auto-cancel; no accidental kill
**Evidence**: `t23_kill_confirm.png`
**Status**: ⏳ Blocked (DHU required)

### TS-520 — AutoAutomataScreen lists running automata
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. Navigate to Automata screen from mission control
2. Verify automata rows: name + story/task position + progress arc
**Expected**: ListTemplate rows ≤ 5; "N more" overflow for 6+
**Evidence**: `t23_automata_screen.png`
**Status**: ⏳ Blocked (DHU required)

### TS-521 — Voice command: "status" reads server summary
**Tags**: [surface:auto] [feature:auto-voice] [conflict:physical-auto]
**Steps**:
1. Trigger voice in DHU; speak "status"
2. Listen for TTS response (or verify REFRESH command fires)
**Expected**: Running/blocked counts spoken; response under 15 seconds
**Evidence**: `t23_voice_status.png`
**Status**: ⏳ Blocked (DHU required)

### TS-522 — Voice command: "switch to {name}" resolves server by name
**Tags**: [surface:auto] [feature:auto-voice] [conflict:physical-auto]
**Steps**:
1. Speak "switch to dw-test" (must match a profile displayName)
**Expected**: Active server switches; spoken confirmation "Switched to dw-test"
**Evidence**: `t23_voice_switch.png`
**Status**: ⏳ Blocked (DHU required)

### TS-523 — Voice command: "what failed" navigates to most recent BLOCKED session
**Tags**: [surface:auto] [feature:auto-voice] [conflict:physical-auto]
**Steps**:
1. Ensure a BLOCKED session exists; speak "what failed"
**Expected**: AutoSessionDetailScreen opens for most recent blocked session; guardrail verdict read aloud
**Evidence**: `t23_voice_whatfailed.png`
**Status**: ⏳ Blocked (DHU required)

### TS-524 — Ambient mode: session list renders monochrome, no action buttons
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. Let Auto session go ambient
2. Verify session list: monochrome; no tap targets; refreshes every 60s
**Expected**: Simplified content; no button rendering in ambient
**Evidence**: `t23_ambient_mode.png`
**Status**: ⏳ Blocked (DHU required)

### TS-525 — Alert dismiss from Auto
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. Create alert on secondary instance
2. In AutoSummaryScreen, tap [Dismiss alert] or speak "dismiss alert"
3. Verify alert count drops to 0
**Expected**: `/api/alerts` marked-read; UI updates; idempotent on second tap
**Evidence**: `t23_alert_dismiss.png`
**Status**: ⏳ Blocked (DHU required)

### TS-526 — Drive compliance: ListTemplate row count ≤ 6
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. Create 10 sessions on secondary instance
2. Navigate to AutoSessionListScreen
3. Verify at most 5 session rows + 1 "… N more" row
**Expected**: MAX_ROWS = 5 enforced; overflow row present
**Evidence**: `t23_row_limit.png`
**Status**: ⏳ Blocked (DHU required)

### TS-527 — Multi-server quick-switch row in mission control
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. Have 2+ server profiles configured
2. Navigate to AutoMissionControlScreen
3. Verify server quick-switch row at bottom
4. Tap to switch; verify server name spoken
**Expected**: Server name changes; mission control re-fetches from new server
**Evidence**: `t23_server_switch.png`
**Status**: ⏳ Blocked (DHU required)

### TS-528 — Back-stack navigation: all screens return correctly
**Tags**: [surface:auto] [feature:auto-screens] [conflict:physical-auto]
**Steps**:
1. Navigate: MissionControl → SessionList → SessionDetail → back → back → back
**Expected**: Returns to MissionControl; no ghost screens; no crash
**Evidence**: `t23_back_stack.png`
**Status**: ⏳ Blocked (DHU required)

### TS-529 — Auto JVM unit tests pass (92 tests)
**Tags**: [surface:auto]
**Steps**:
1. `rtk ./gradlew :composeApp:testDevDebugUnitTest --tests "*.auto*"`
2. Verify 92 Auto JVM tests pass
**Expected**: 92 tests, 0 failures, 0 errors (7 test suites)
**Evidence**: test output
**Status**: 📋 Planned

---

## T24 — Algorithm Mode Tests

**Goal**: Verify the Algorithm Mode OODA-loop card in Settings — all 6 actions (Start, Advance, Abort, Reset, Edit, Measure) work against the secondary instance.

**Prerequisite**: Secondary test instance running; a session ID available to enter algorithm mode.

### TS-530 — Algorithm Mode card visible in Settings → Automata
**Tags**: [surface:phone] [feature:algorithm]
**Steps**:
1. Settings → Automata tab → scroll to "ALGORITHM MODE" section
2. Verify card heading, session ID text field, and Start button present
**Expected**: Card renders; no crash; "No active algorithm-mode sessions" shown initially
**Evidence**: `t24_algo_card.png`
**Status**: 📋 Planned

### TS-531 — Start algorithm session by session ID
**Tags**: [surface:phone] [feature:algorithm] [surface:api]
**Steps**:
1. Create a session via API; copy its ID
2. Paste into Algorithm Mode session ID field; tap Start
3. Verify session row appears with phase strip (7 dots, first blue/pulsing)
**Expected**: POST /api/algorithm/{id} → 200; row renders with phase=observe, pulse on dot 0
**Evidence**: `t24_algo_start.json`, `t24_algo_start.png`
**Status**: 📋 Planned

### TS-532 — Advance phase (observe → orient → decide…)
**Tags**: [surface:phone] [feature:algorithm] [surface:api]
**Steps**:
1. Expand session row; tap [Advance]
2. Verify phase strip advances (dot 0 turns teal, dot 1 pulses blue)
3. Repeat through all 7 phases
**Expected**: Each Advance call updates `current` field; PATCH action=advance → 200 each time
**Evidence**: `t24_algo_advance.json`
**Status**: 📋 Planned

### TS-533 — Abort session
**Tags**: [surface:phone] [feature:algorithm] [surface:api]
**Steps**:
1. Expand session row; tap [Abort]
2. Verify phase strip dot changes to error color (red dot at current position)
3. Verify Advance/Abort buttons hidden; only Reset remains
**Expected**: `aborted=true` in response; dot color = error; action buttons filtered
**Evidence**: `t24_algo_abort.json`, `t24_algo_abort.png`
**Status**: 📋 Planned

### TS-534 — Reset restores session to observe phase
**Tags**: [surface:phone] [feature:algorithm] [surface:api]
**Steps**:
1. With session aborted (or mid-run), tap [Reset]
2. Verify phase strip resets: all dots grey except dot 0 (pulsing blue)
**Expected**: PATCH action=reset → `current=observe, aborted=false`; strip resets
**Evidence**: `t24_algo_reset.json`, `t24_algo_reset.png`
**Status**: 📋 Planned

### TS-535 — Edit phase output
**Tags**: [surface:phone] [feature:algorithm] [surface:api]
**Steps**:
1. Expand session row; type text in "Edit phase output…" field
2. Tap [Edit]
3. Verify "Phase output" section updates with new text
**Expected**: PATCH action=edit,output=… → 200; last history entry shows new output; field clears
**Evidence**: `t24_algo_edit.json`, `t24_algo_edit.png`
**Status**: 📋 Planned

### TS-536 — Measure: run eval suite
**Tags**: [surface:phone] [feature:algorithm] [surface:api]
**Steps**:
1. Expand session row; type suite name (e.g. "default") in "Eval suite" field
2. Tap [Measure]
3. Verify history updates with measurement result
**Expected**: PATCH action=measure,suite=… → 200; updated session returned; field clears
**Evidence**: `t24_algo_measure.json`
**Status**: 📋 Planned

### TS-537 — Phase strip dot colors: done=teal, current=blue pulse, aborted=red, future=grey
**Tags**: [surface:phone] [feature:algorithm]
**Steps**:
1. Advance session to "decide" (3rd phase)
2. Verify: dots 0-1 = teal (done), dot 2 = pulsing blue (current), dots 3-6 = grey (future)
3. Abort; verify dot 2 turns red
**Expected**: Color mapping correct per PhaseStrip logic
**Evidence**: `t24_phase_strip.png`
**Status**: 📋 Planned

### TS-538 — Edit/Measure fields hidden when session is aborted
**Tags**: [surface:phone] [feature:algorithm]
**Steps**:
1. Abort a session
2. Expand the row
3. Verify Edit and Measure input rows are not rendered
**Expected**: `if (!state.aborted)` guard hides both input rows; only Reset button shown
**Evidence**: `t24_aborted_state.png`
**Status**: 📋 Planned

### TS-539 — Algorithm list loaded on card open
**Tags**: [surface:phone] [feature:algorithm] [surface:api]
**Steps**:
1. Start an algorithm session via API directly
2. Navigate away and back to Settings → Automata
3. Verify the existing session appears in the card (loaded via LaunchedEffect)
**Expected**: GET /api/algorithm → sessions list populated on card init
**Evidence**: `t24_algo_list.json`
**Status**: 📋 Planned

### TS-540 — Multiple sessions shown with dividers
**Tags**: [surface:phone] [feature:algorithm]
**Steps**:
1. Start algorithm mode on 2 different sessions
2. Navigate to Settings → Automata → Algorithm Mode
3. Verify both rows appear with `HorizontalDivider` between them
**Expected**: 2 rows; divider visible; each row independently expandable
**Evidence**: `t24_multi_session.png`
**Status**: 📋 Planned

### TS-541 — Session ID field clears after successful Start
**Tags**: [surface:phone] [feature:algorithm]
**Steps**:
1. Enter a valid session ID and tap Start
2. On success, verify session ID field is empty
**Expected**: `startSessionId = ""` fires on `onSuccess`; field blank after operation
**Evidence**: `t24_field_clear.png`
**Status**: 📋 Planned

---

## Release Gate

**v1.0.0 ship criteria**:
- T1–T14: all non-skip stories ✅ Pass
- T15–T16: ✅ Pass (server #40-43, #39 all closed; client implemented)
- T17: ✅ Pass (parity audit)
- T18: ✅ Pass (test debt)
- T19: ✅ Pass (dashboard hooks)
- T20: ✅ Pass or ⏳ Blocked with known issue (howto validation)
- T21: ✅ Pass or ⏳ Blocked with known issue (end-to-end journeys)
- T22: ✅ Pass (emulator JVM tests); ⏳ Blocked acceptable for physical-watch stories
- T23: ✅ Pass (Auto JVM tests); ⏳ Blocked acceptable for DHU-required stories
- T24: ✅ Pass (Algorithm Mode — all 12 stories runnable on emulator)
- T26: ✅ Pass (Dashboard Cards CRUD — Android)
- T27: ✅ Pass (Automata Orchestrator E2E — Android)
- No P0/P1 critical bugs
- Cookbook shows final pass counts

---

**For future releases**: Copy this plan to `docs/testing/vX.Y.Z/plan.md`, update version + date, adjust T-sprints for new features.
