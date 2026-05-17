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
| Secondary instance | Same host; data dir `.datawatch-test/`; ports 18080/18443 |
| Emulator | `dw_test_phone` (Android 14 / API 34, Pixel 6) |
| Mobile server URL | `https://10.0.2.2:18443` (adb reverse tcp:18443 tcp:18443) |
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
rm -rf .datawatch-test docs/testing/{{VERSION}}/evidence/
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
- `[feature:{{FEATURE_NAME}}]` — (define for this release)

**Conflict tags**:
- `[conflict:{{BLOCKER_NAME}}]` — (define if applicable)

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
