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
- datawatch binary at `/home/dmz/.local/bin/datawatch`
- Ollama running with `qwen3:1.7b` (LLM) and `nomic-embed-text` (embedder) — small models only for testing
- Emulator `dw_test_phone` running

**LLM policy**: Use Ollama (`qwen3:1.7b`) for all test-instance LLM calls. Claude (claude-haiku-4-5, quick effort) only for major release final validation. This host (johnnyjohnny) has a 32G GPU — sufficient for small models. datawatch server (ralfthewise) has 128G but is not used for app testing.

**Start** (save the PID — never grep for it):
```bash
mkdir -p /home/dmz/workspace/datawatch-test-workspace
mkdir -p .datawatch-test
cat > .datawatch-test/config.yaml <<EOF
data_dir: $(pwd)/.datawatch-test
hostname: johnnyjohnny-test
server:
  enabled: true
  host: 0.0.0.0
  port: 18080
  tls_port: 18443
  token: "dw-test-token-12345"
  tls_enabled: true
  tls_auto_generate: true
session:
  skip_permissions: true
  max_sessions: 10
  llm_backend: ollama
  default_project_dir: /home/dmz/workspace/datawatch-test-workspace
  root_path: /home/dmz/workspace
autonomous:
  enabled: true
memory:
  enabled: true
ollama:
  enabled: true
  model: qwen3:1.7b
  url: http://localhost:11434
  embedder: nomic-embed-text
mcp:
  enabled: false
EOF

/home/dmz/.local/bin/datawatch start --foreground \
  --config "$(pwd)/.datawatch-test/config.yaml" \
  > /tmp/test-server.log 2>&1 &
TEST_DAEMON_PID=$!
echo "$TEST_DAEMON_PID" > /tmp/test-daemon.pid
sleep 5
curl -sk https://127.0.0.1:18443/api/health
# Must return {"hostname":"johnnyjohnny-test","status":"ok"} before proceeding.
```

**Emulator bridge**:
```bash
adb reverse tcp:18443 tcp:18443
adb reverse tcp:18080 tcp:18080
```

**Cleanup** — always use the saved PID, never grep:
```bash
# CORRECT: kill by PID saved at startup
TEST_DAEMON_PID=$(cat /tmp/test-daemon.pid 2>/dev/null)
if [ -n "$TEST_DAEMON_PID" ] && kill -0 "$TEST_DAEMON_PID" 2>/dev/null; then
  # Verify it's actually the test instance (port 18080) before killing
  if ss -tlnp 2>/dev/null | grep -q "18080.*pid=$TEST_DAEMON_PID"; then
    kill "$TEST_DAEMON_PID"
  else
    echo "PID $TEST_DAEMON_PID is NOT listening on 18080 — refusing to kill"
  fi
fi
rm -f /tmp/test-daemon.pid
rm -rf .datawatch-test docs/testing/{{VERSION}}/evidence/

# WRONG (never do this — it will kill production if test daemon exited):
# pkill -f "datawatch"
# kill $(pgrep -f "datawatch")
# kill $(ps aux | grep datawatch | ...)
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

---

## Lessons Learned (carry forward to each release)

Add new entries here after each release. These patterns should be applied to every future test plan.

### datawatch hooks — use HTTPS not HTTP (v1.0.0)
Hook scripts in `~/.datawatch/hooks/` must set `DATAWATCH_URL=https://localhost:8443`. curl silently drops POST body on HTTP→HTTPS redirects. Both save and precompact hooks had this bug (datawatch#50). Always test hook delivery with `curl -sk -X POST https://localhost:8443/api/test/message -H "Content-Type: application/json" -d '{"text":"test"}'` before starting a test run.

### datawatch session send — append empty send for Enter (v1.0.0)
`datawatch session send <id> "msg"` does not send Enter (datawatch#53). Always follow with `datawatch session send <id> ""` until fixed. Applies to cross-host SSH send patterns too.

### Verify datawatch health after any update or restart (v1.0.0)
After any binary update, config change, or restart: `curl -sk https://localhost:8443/api/health` must return ok before continuing. If not healthy, check logs and fix. Silent failures waste test time.

### LLM policy for test runs (v1.0.0)
Use Ollama small models (e.g. `qwen3:1.7b`) for all test-instance LLM calls. Claude is reserved for final major release validation only. This keeps costs low and testing fast.

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
