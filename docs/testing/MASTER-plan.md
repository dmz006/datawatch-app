# datawatch-app TEST PLAN TEMPLATE — MASTER

**Template Version**: 1.0  
**Purpose**: Baseline for all release-specific test plans (copy to `docs/testing/vX.Y.Z/plan.md` for each release)

---

## Before Using This Template

1. **Copy this file** to `docs/testing/vX.Y.Z/plan.md` (replace X.Y.Z with your release version)
2. **Update placeholders**: 
   - Replace `{{VERSION}}` with your release version (e.g., `v1.0.0`, `v1.0.1`)
   - Replace `{{RELEASE_DATE}}` with plan creation date
   - Replace `{{SCOPE_CHANGES}}` with new features/areas added in this release
3. **Adjust T-sprints**: Add new T-sprints for new features; mark old T-sprints as "regression" if unchanged
4. **Update blockers**: Note server or infrastructure blockers that prevent certain tests from running
5. **Copy MASTER-cookbook.md** to `docs/testing/vX.Y.Z/cookbook.md` for live status tracking

---

# datawatch-app {{VERSION}} End-to-End Test Plan

**Version**: {{VERSION}} (Mobile Client Release)  
**Date**: {{RELEASE_DATE}}  
**Scope**: Android phone + Wear OS + Android Auto surfaces  
**Scope changes**: {{SCOPE_CHANGES}}  
**Test environment**: Secondary datawatch instance (ports 18080/18443) + emulator `dw_test_phone`  
**Success criterion**: All non-skip T-sprints pass; if tests pass, ship {{VERSION}}

---

## Overview

This plan covers all mobile, Wear, and Auto surfaces in a comprehensive test structure. We test **only the client-side behavior** — server infrastructure is tested upstream in the datawatch repo.

### Plan vs Cookbook vs Evidence

| Artifact | Location | Persisted? | Purpose |
|---|---|---|---|
| **Plan** (`plan.md`) | `docs/testing/vX.Y.Z/plan.md` | ✅ Yes | Defines every story: steps, expected, evidence filenames. Immutable reference. |
| **Cookbook** (`cookbook.md`) | `docs/testing/vX.Y.Z/cookbook.md` | ✅ Yes | Live status table updated during test run. Only persistent record of results. |
| **Evidence** (`evidence/TS-NNN/`) | `docs/testing/vX.Y.Z/evidence/` | ❌ Gitignored + deleted | Screenshots, logcat, JSON. Exists only during a run. Preserved on FAIL for diagnosis. |

### Design decisions

| Decision | Value |
|---|---|
| Secondary instance | Same host; working dir outside repo at `../datawatch-soak-<id>/`; default ports 18080/18443 (OS-free fallback) |
| Emulator | `dw_test_phone` (Android 14 / API 34, Pixel 6) |
| Mobile server URL | `https://10.0.2.2:18443` (`TEST_SERVER_TLS_PORT`, default 18443; via adb reverse) |
| Test token | `dw-test-token-12345` |
| Evidence root | `docs/testing/{{VERSION}}/evidence/` (gitignored) |

---

## Environment Setup

### Secondary Test Instance (datawatch server)

**Prerequisites**: 
- datawatch binary at `~/workspace/datawatch/bin/datawatch`
- Emulator `dw_test_phone` running

**Start**:
```bash
# Working dir is created OUTSIDE the repo (never commit test data)
SOAK_RUN_ID=$(openssl rand -hex 3)
TEST_WORK_DIR="../datawatch-soak-${SOAK_RUN_ID}"
TEST_DATA_DIR="${TEST_WORK_DIR}/.datawatch-test-${SOAK_RUN_ID}"
mkdir -p "$TEST_DATA_DIR"
mkdir -p "${TEST_DATA_DIR}/.claude"  # BL318: scope MCP config to this instance

# Ports: prefer 18080/18443; fall back to OS-free if busy
TEST_TLS_PORT="${TEST_SERVER_TLS_PORT:-18443}"
TEST_HTTP_PORT="${TEST_SERVER_HTTP_PORT:-18080}"

cat > "${TEST_WORK_DIR}/config.yaml" <<EOF
data_dir: ${TEST_DATA_DIR}
server:
  port: ${TEST_HTTP_PORT}
  tls_port: ${TEST_TLS_PORT}
  token: "dw-test-token-12345"
  tls_enabled: true
  tls_auto_generate: true
session:
  skip_permissions: true
autonomous:
  enabled: true
memory:
  enabled: true
EOF

CLAUDE_CONFIG_DIR="${TEST_DATA_DIR}/.claude" \
  datawatch start --foreground --config "${TEST_WORK_DIR}/config.yaml" \
  >> "${TEST_WORK_DIR}/daemon.log" 2>&1 &
echo $! > "${TEST_WORK_DIR}/test-daemon.pid"
sleep 3
curl -sk "https://127.0.0.1:${TEST_TLS_PORT}/api/health"
```

**Emulator bridge**:
```bash
adb reverse tcp:$TEST_TLS_PORT tcp:$TEST_TLS_PORT
adb reverse tcp:$TEST_HTTP_PORT tcp:$TEST_HTTP_PORT
```

**Cleanup** (after run):
```bash
# Stop daemon via saved PID (never grep ps)
kill $(cat "../datawatch-soak-${SOAK_RUN_ID}/test-daemon.pid") 2>/dev/null || true
# Remove working dir (script auto-cleans on success via EXIT trap)
rm -rf "../datawatch-soak-${SOAK_RUN_ID}"
# Evidence dir is inside the working dir — no separate cleanup needed
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

---

## T-Sprint Index

| T-Sprint | Area | Stories | Status |
|----------|------|---------|--------|
| T1 | {{SPRINT_1_NAME}} | TS-{{STORY_START}}–TS-{{STORY_END}} | ⏳ |
| T2 | {{SPRINT_2_NAME}} | TS-{{STORY_START}}–TS-{{STORY_END}} | ⏳ |
| ... | (add more rows as needed) | | |

---

## Tag Taxonomy

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
- `[feature:push]` — Push / UnifiedPush notifications
- `[feature:security]` — Auth, keystore, biometric
- `[feature:multiserver]` — Multi-server + federation
- `[feature:algorithm]` — Algorithm Mode OODA loop
- `[feature:wear-tiles]` — Wear OS tiles
- `[feature:wear-complications]` — Wear OS complications
- `[feature:wear-voice]` — Wear OS voice query
- `[feature:wear-notifications]` — Wear OS guardrail notifications + haptics
- `[feature:auto-screens]` — Android Auto screens
- `[feature:auto-voice]` — Android Auto voice commands
- `[feature:dashboard]` — Dashboard Cards CRUD
- `[feature:orchestrator]` — Automata Orchestrator DAG graphs

**Conflict tags**:
- `[conflict:physical-watch]` — Requires physical Wear OS device
- `[conflict:physical-auto]` — Requires Android Auto DHU or head unit
- `[conflict:wear-haptic]` — Requires physical Wear device for haptic verification
- `[conflict:biometric]` — Requires biometric enrollment
- `[conflict:compute-daemon]` — Requires compute node/LLM daemon
- `[conflict:signal]` — Requires Signal comm channel
- `[conflict:network]` — Requires physical network state change

---

## T1–T{{N}}: Stories (Regression or New)

**For regression T-sprints** (unchanged from prior release):
- Copy story details from prior release's plan.md
- Update Status column in cookbook.md with fresh results

**For new T-sprints** (first-time coverage):
- Expand with full story definitions (Steps, Expected, Evidence, Status)
- Example template below:

### TS-NNN — Story Title
**Tags**: [surface:phone] [feature:x] [conflict:y if applicable]
**Steps**: 
1. Step 1
2. Step 2
**Expected**: Expected outcome
**Evidence**: `filename.json`
**Status**: 📋 Planned (or ✅/⏭/⚠️/⏳)

---

## T22 — Wear OS Surface Tests

**Goal**: Verify Wear OS tiles (5), complications (8), voice query, guardrail notifications, and DataLayer proxy work end-to-end.

**Note**: Stories requiring physical Wear device should be marked `[conflict:physical-watch]`. JVM unit test stories (88 Wear tests) are always runnable.

**Stories**: TS-500–TS-514 (15 stories)
- TS-500–TS-512: Wear tiles, complications, voice, notifications (physical watch required)
- TS-513: Wear JVM unit tests (88 tests pass)
- TS-514: Wear APK build check

---

## T23 — Android Auto Surface Tests

**Goal**: Verify Android Auto screens (MissionControl, SessionList, SessionDetail, Automata), voice commands (12+), ambient mode, and Drive compliance.

**Note**: Stories requiring DHU should be marked `[conflict:physical-auto]`. JVM unit test stories (92 Auto tests) are always runnable.

**Stories**: TS-515–TS-529 (15 stories)
- TS-515–TS-528: All Auto screens, voice commands, ambient, Drive compliance (DHU required)
- TS-529: Auto JVM unit tests (92 tests pass)

---

## T24 — Algorithm Mode Tests

**Goal**: Verify the Algorithm Mode OODA-loop card (Settings → Automata) — all 6 actions (Start, Advance, Abort, Reset, Edit, Measure) and UI state (phase strip, dot colors, field clearing).

**Stories**: TS-530–TS-541 (12 stories; all runnable on phone emulator)

---

## T20 — Howto Validation (Datawatch Documentation Workflows)

**Goal**: Verify that each datawatch howto's documented workflow works end-to-end in the mobile app.

Copy the howto validation stories from v1.0.0/plan.md (TS-360–TS-400). These stories test that:
- Each documented workflow in datawatch/docs/howto/*.md actually works in the mobile app
- Users can follow the howto and complete the workflow successfully
- Any blockers are documented and linked to upstream issues

**Stories**: TS-360–TS-400 (9 stories, one per howto)

---

## T21 — End-to-End User Journeys

**Goal**: Verify complete multi-T-sprint workflows that combine multiple howtos and surfaces.

Copy the end-to-end journey stories from v1.0.0/plan.md (TS-410–TS-420). These stories test:
- Multi-step user flows that span multiple T-sprints (e.g., setup → identity → create → alert → reply)
- Cross-surface workflows (phone → watch → auto)
- Power user scenarios (multi-server, profile management, federation)

**Stories**: TS-410–TS-420 (3+ stories)

---

## Release Gate

**{{VERSION}} ship criteria**:
- All regression T-sprints: ✅ Pass (or ⏭ Skip with reason)
- All new T-sprints: ✅ Pass (or ⏳ Blocked with blocker issue)
- No P0/P1 critical bugs
- Cookbook shows final pass/fail counts
- Version parity: gradle.properties ↔ Version.kt ↔ Play Console

---

## How to Update This Template

1. After using this plan for a release (e.g., v1.0.0):
   - Review what worked well and what didn't
   - Update this MASTER-plan.md with lessons learned
   - Document any recurring issues or patterns
2. For the next release (e.g., v1.0.1):
   - Copy MASTER-plan.md to `docs/testing/v1.0.1/plan.md`
   - Adjust T-sprints: mark regressions with `[regression]` tag, add new T-sprints for new features
   - Keep regression T-sprints brief; they mirror prior release
3. Keep MASTER-plan.md and MASTER-cookbook.md in sync with best practices

---

**For questions or updates to this template**: refer to the most recent completed release plan in `docs/testing/` or contact the test maintainer.
