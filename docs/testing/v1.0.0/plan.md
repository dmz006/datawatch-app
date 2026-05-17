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
| T13 | Autonomous / PRD lifecycle | TS-221–TS-255 | ⏳ Blocked: datawatch#48 |
| T14 | Regression — session refresh | TS-256–TS-285 | ✅ Pass |
| T15 | New server endpoints | TS-286–TS-305 | ⏳ Blocked: datawatch#40-43 |
| T16 | UnifiedPush Tier 1 | TS-306–TS-315 | ⏳ Blocked: datawatch#39 |
| T17 | Parity audit | TS-316–TS-325 | 📋 Planned |
| T18 | Test debt payoff | TS-326–TS-343 | 📋 Planned |
| T19 | Dashboard hooks integration | TS-344–TS-350 | 📋 Planned |
| T20 | Howto validation (datawatch docs) | TS-360–TS-400 | 📋 Planned |
| T21 | End-to-end user journeys | TS-410–TS-420 | 📋 Planned |
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

**Conflict tags**:
- `[conflict:physical-watch]` — Requires physical Wear device
- `[conflict:biometric]` — Requires biometric enrollment
- `[conflict:compute-daemon]` — Requires compute node/LLM
- `[conflict:signal]` — Requires Signal comm channel
- `[conflict:network]` — Requires network state change

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

## Release Gate

**v1.0.0 ship criteria**:
- T1–T14: all non-skip stories ✅ Pass
- T15–T16: ✅ Pass (once server ships #40-43, #39)
- T17: ✅ Pass (parity audit)
- T18: ✅ Pass (test debt)
- T19: ✅ Pass (dashboard hooks)
- T20: ✅ Pass or ⏳ Blocked with known issue (howto validation)
- T21: ✅ Pass or ⏳ Blocked with known issue (end-to-end journeys)
- T26: ✅ Pass (Dashboard Cards CRUD — Android)
- T27: ✅ Pass (Automata Orchestrator E2E — Android)
- No P0/P1 critical bugs
- Cookbook shows final pass counts

---

**For future releases**: Copy this plan to `docs/testing/vX.Y.Z/plan.md`, update version + date, adjust T-sprints for new features.
