# datawatch-app v1.0.0 End-to-End Test Plan

**Version**: v1.0.0 (Mobile Client Release)  
**Date**: 2026-05-14 (updated 2026-05-16)  
**Scope**: Android phone + Wear OS + Android Auto surfaces  
**Test host**: johnnyjohnny (32G GPU, Ollama small models)  
**Test environment**: Secondary datawatch instance (ports 18080/18443) + emulator `dw_test_phone`  
**IMPORTANT**: ALL tests run against the secondary test instance (`https://10.0.2.2:18443`). Never use the production ring server during test runs.  
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

**Prerequisite**: datawatch binary at `/home/dmz/.local/bin/datawatch` (v7.0.0-alpha.64+)

**LLM policy**: Ollama only for test-instance LLM calls. Claude (claude-haiku-4-5, quick effort) reserved for final major release validation only. johnnyjohnny 32G GPU handles `qwen3:1.7b` comfortably.

**Start**:
```bash
mkdir -p /home/dmz/workspace/.datawatch-test
# Config already exists at /home/dmz/workspace/.datawatch-test/config.yaml
# If missing, recreate:
cat > /home/dmz/workspace/.datawatch-test/config.yaml <<EOF
data_dir: /home/dmz/workspace/.datawatch-test
hostname: johnnyjohnny-test
server:
  enabled: true
  host: 0.0.0.0
  port: 18080
  tls_port: 18443
  token: "dw-test-token-12345"
  tls_enabled: true
  tls_auto_generate: true
autonomous:
  enabled: true
memory:
  enabled: true
  backend: sqlite
  embedder_model: nomic-embed-text
  embedder_host: http://localhost:11434
mcp:
  enabled: false
ollama:
  enabled: true
  model: qwen3:1.7b
  host: http://localhost:11434
  embedder: nomic-embed-text
session:
  skip_permissions: true
  max_sessions: 10
  llm_backend: ollama
  default_project_dir: /home/dmz/workspace/datawatch-test-workspace
  root_path: /home/dmz/workspace
# NOTE: root_path must be /home/dmz/workspace — never /home/dmz directly
EOF

mkdir -p /home/dmz/workspace/datawatch-test-workspace
/home/dmz/.local/bin/datawatch start --foreground \
  --config /home/dmz/workspace/.datawatch-test/config.yaml \
  > /tmp/test-server.log 2>&1 &
TEST_DAEMON_PID=$!
echo "$TEST_DAEMON_PID" > /tmp/test-daemon.pid
sleep 5
curl -sk https://127.0.0.1:18443/api/health
# MUST return {"hostname":"johnnyjohnny-test","status":"ok"} — if not, check /tmp/test-server.log
# Always kill by PID from /tmp/test-daemon.pid — never grep for the process
```

**Emulator bridge**:
```bash
adb reverse tcp:18443 tcp:18443
adb reverse tcp:18080 tcp:18080
```

**Cleanup** — use saved PID, never grep:
```bash
TEST_DAEMON_PID=$(cat /tmp/test-daemon.pid 2>/dev/null)
if [ -n "$TEST_DAEMON_PID" ] && kill -0 "$TEST_DAEMON_PID" 2>/dev/null; then
  if ss -tlnp 2>/dev/null | grep -q "18080.*pid=$TEST_DAEMON_PID"; then
    kill "$TEST_DAEMON_PID"
  else
    echo "PID $TEST_DAEMON_PID not on port 18080 — refusing to kill"
  fi
fi
rm -f /tmp/test-daemon.pid
rm -rf /home/dmz/workspace/.datawatch-test docs/testing/v1.0.0/evidence/
# NEVER: pkill -f datawatch / kill $(grep datawatch ...) — kills production
```

### Mobile App (emulator)

**Start emulator**:
```bash
/home/dmz/workspace/Android/Sdk/emulator/emulator \
  -avd dw_test_phone \
  -no-snapshot-save \
  -no-audio \
  -gpu swiftshader_indirect \
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

### Second Test Instance (johnnyjohnny-test2 — for T21/TS-420 multi-server)

Config at `/home/dmz/workspace/.datawatch-test2/config.yaml` (ports 28080/28443, token `dw-test2-token-67890`).

**Start**:
```bash
/home/dmz/.local/bin/datawatch start --foreground \
  --config /home/dmz/workspace/.datawatch-test2/config.yaml \
  > /tmp/test2-server.log 2>&1 &
TEST2_DAEMON_PID=$!
echo "$TEST2_DAEMON_PID" > /tmp/test2-daemon.pid
sleep 5
curl -sk https://127.0.0.1:28443/api/health
# Must return {"hostname":"johnnyjohnny-test2","status":"ok"}
```

**Emulator bridge (test2)**:
```bash
adb reverse tcp:28443 tcp:28443
adb reverse tcp:28080 tcp:28080
```

**In-app**: add second server at `https://10.0.2.2:28443` with token `dw-test2-token-67890`.

**Cleanup**:
```bash
TEST2_DAEMON_PID=$(cat /tmp/test2-daemon.pid 2>/dev/null)
if [ -n "$TEST2_DAEMON_PID" ] && kill -0 "$TEST2_DAEMON_PID" 2>/dev/null; then
  if ss -tlnp 2>/dev/null | grep -q "28080.*pid=$TEST2_DAEMON_PID"; then
    kill "$TEST2_DAEMON_PID"
  fi
fi
rm -f /tmp/test2-daemon.pid
rm -rf /home/dmz/workspace/.datawatch-test2
```

### Wear OS Emulator (for T10 Wear stories)

**AVD**: `dw_test_watch` — Wear OS Large Round (android-33/android-wear/x86_64)

**Start**:
```bash
/home/dmz/workspace/Android/Sdk/emulator/emulator \
  -avd dw_test_watch \
  -no-snapshot-save \
  -no-audio \
  -gpu swiftshader_indirect \
  -no-boot-anim \
  2>/tmp/watch-emulator.log &
# Wait for boot, then bridge
adb -s emulator-5556 reverse tcp:18443 tcp:18443
adb -s emulator-5556 reverse tcp:18080 tcp:18080
```

**Build Wear APK**:
```bash
rtk ./gradlew :wearApp:assembleDebug
adb -s emulator-5556 install -r wearApp/build/outputs/apk/debug/*.apk
```

### Android Auto Emulator (for Auto surface stories)

**AVD**: `dw_test_auto` — Automotive 1024p Landscape (android-33/android-automotive/x86_64)

**Start**:
```bash
/home/dmz/workspace/Android/Sdk/emulator/emulator \
  -avd dw_test_auto \
  -no-snapshot-save \
  -no-audio \
  -gpu swiftshader_indirect \
  -no-boot-anim \
  2>/tmp/auto-emulator.log &
# Bridge
adb -s emulator-5558 reverse tcp:18443 tcp:18443
adb -s emulator-5558 reverse tcp:18080 tcp:18080
```

---

## 1b. Lessons Learned (from this test run)

These patterns were discovered during the v1.0.0 run and should carry forward to future plans.

### datawatch hooks — use HTTPS not HTTP ✅ Fixed in v7.0.0-alpha.67
Hook scripts in `~/.datawatch/hooks/` must use `DATAWATCH_URL=https://localhost:8443`. curl silently drops POST body on HTTP→HTTPS redirect (3xx). Both `datawatch_save_hook.sh` and `datawatch_precompact_hook.sh` had this bug (filed as datawatch#50, fixed in alpha.67).

### datawatch session send — append empty send for Enter ✅ Fixed in v7.0.0-alpha.67
`datawatch session send <session-id> "msg"` was not sending an Enter keystroke (datawatch#53, fixed in alpha.67). The new `POST /api/sessions/{id}/input` endpoint now appends Enter automatically. The two-call workaround is no longer needed on alpha.67+.

### MCP x509 on test instance ✅ Fixed in v7.0.0-alpha.67
MCP tools against self-signed certs now work (datawatch#51, fixed in alpha.67).

### Never grep to kill the test daemon — always use the saved PID ✅ Lesson from this run
`kill $(ps aux | grep "datawatch start --foreground" ...)` will match both the test daemon AND the production daemon — production daemon also shows as `start --foreground` internally. This was the exact mistake that killed production during v1.0.0 testing.

**Correct pattern**: Save the PID at startup (`echo $! > /tmp/test-daemon.pid`), validate it's listening on port 18080 before killing, never use grep-to-kill. See `test-isolation-guide.md` for the full `_validate_test_daemon_pid()` pattern from datawatch v7.0.0.

### CLI commands against test instance need `--config`
Any `datawatch` CLI command without `--config .datawatch-test/config.yaml` will hit the production daemon. Always pass `--config` when testing against the isolated instance.

### Verify datawatch is running after any update or restart
After `datawatch update`, config change, or restart: always run `curl -sk https://localhost:8443/api/health`. If not healthy, check logs and fix before continuing. Silent failures waste test time.

### johnnyjohnny ↔ ralfthewise are distinct instances, not federated
They share memory only via manual cross-host SSH patterns. Cross-host session federation is a future feature (datawatch#52). To send commands from johnnyjohnny to ralfthewise:
```bash
ssh ralfthewise '/home/dmz/.local/bin/datawatch session send ralfthewise-5ed0 "your command"'
ssh ralfthewise '/home/dmz/.local/bin/datawatch session send ralfthewise-5ed0 ""'
```

### MCP x509 on test instance
MCP tools (`get_config`, etc.) may throw x509 errors against the test instance (self-signed cert, datawatch#51). Use REST `curl -sk` directly for health checks and config reads against the test instance.

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

**Status as of alpha.67**:
- `/api/identity` — ✅ 200 (datawatch#40 fixed)
- `/api/algorithm` — ✅ 200 (datawatch#41 fixed)
- `/api/council/*` (personas/runs/config) — ✅ 200 (datawatch#43 fixed; base path `/api/council` returns 404 but sub-paths work)
- `/api/evals` — ❌ 404 (datawatch#42 still open — T15 evals stories remain blocked)

### TS-286 — Identity endpoint GET
**Tags**: [surface:phone] [feature:identity]
**Steps**: 
1. Settings → Automata → Identity card
2. GET /api/identity returns `{role, current_focus, context_notes}`
3. Verify card populates with server data
**Expected**: Identity card populates with server data; PATCH updates persist
**Evidence**: `identity_get.json`, `identity_card.png`
**Status**: 📋 Ready (datawatch#40 fixed in alpha.67)

### TS-287 — Identity PATCH (update role/focus)
**Tags**: [surface:phone] [feature:identity]
**Status**: 📋 Ready

### TS-288 — Algorithm mode: list phases
**Tags**: [surface:phone] [feature:algorithm]
**Steps**: 1. Settings → Automata → Algorithm  2. Verify phases list: observe/orient/decide/act/measure/learn/improve
**Expected**: All 7 OODA phases shown; current phase highlighted
**Evidence**: `algorithm_phases.json`
**Status**: 📋 Ready (datawatch#41 fixed in alpha.67)

### TS-289 — Algorithm advance phase
**Tags**: [surface:phone] [feature:algorithm]
**Status**: 📋 Ready

### TS-290 — Council personas list
**Tags**: [surface:phone] [feature:council]
**Steps**: 1. Settings → Automata → Council  2. GET /api/council/personas
**Expected**: Persona list shows; each persona has role/system_prompt
**Evidence**: `council_personas.json`
**Status**: 📋 Ready (datawatch#43 sub-paths fixed in alpha.67)

### TS-291–TS-293: (3 stories: council config GET/SET, council run start)
**Status**: 📋 Ready

### TS-294–TS-298: (5 stories: algorithm start/advance/abort/reset/measure)
**Status**: 📋 Ready

### TS-299–TS-303: (5 stories: evals list, run suite, view results, compare, export)
**Status**: ⏳ Blocked (datawatch#42 — /api/evals still 404 as of alpha.67)

### TS-304–TS-305: (2 stories: evals history, eval suite CRUD)
**Status**: ⏳ Blocked (datawatch#42)

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
**Steps**:
1. Start johnnyjohnny-test (port 18443) and johnnyjohnny-test2 (port 28443) — see §1 for setup
2. Add both servers in app Settings → Comms
3. Use server picker to switch between them; verify session lists are distinct
4. Settings → Observer → verify no cross-contamination of data
**Expected**: Both servers operate independently; switcher shows correct server context
**Evidence**: `multi_server_picker.png`, `multi_server_sessions.json`
**Status**: 📋 Ready (test2 config at `/home/dmz/workspace/.datawatch-test2/`; start before testing)

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
- No P0/P1 critical bugs
- Cookbook shows final pass counts

---

**For future releases**: Copy this plan to `docs/testing/vX.Y.Z/plan.md`, update version + date, adjust T-sprints for new features.
