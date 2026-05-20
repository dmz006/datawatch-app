# v1.0.0 Test Execution Instructions

**Purpose**: How to execute the complete v1.0.0 end-to-end test suite after setup is complete.

**Scope**: 369 stories across T1–T21 (phone, Wear OS, Android Auto surfaces)

**Duration**: 6–12 hours depending on network and system speed

---

## Pre-Execution Checklist (5 minutes)

Before starting, verify setup is complete:

```bash
# Navigate to app directory
cd ~/workspace/datawatch-app

# Verify all required files exist
ls -la docs/testing/v1.0.0/
# Should show: plan.md, cookbook.md, REQUIREMENTS.md, SETUP.md, RUN.md (this file)

# Verify repositories are in place
ls -la ~/workspace/datawatch/bin/datawatch
ls -la ~/workspace/datawatch-test-*/

# Verify APK is built
ls -la composeApp/build/outputs/apk/publicTrack/debug/composeApp-publicTrack-debug.apk

# Verify evidence directory will exist (gitignored)
grep "evidence/" docs/testing/v1.0.0/.gitignore
```

---

## Phase 1: Start Test Environment (10 minutes)

### Terminal 1: Start Emulator

```bash
# Open a dedicated terminal for emulator (keep it running)
$ANDROID_SDK_ROOT/emulator/emulator \
  -avd dw_test_phone \
  -no-snapshot-save \
  -no-audio \
  -gpu swiftshader_indirect \
  -no-boot-anim \
  -memory 2048 \
  -cores 4 \
  2>&1 | tee /tmp/emulator.log &

# Wait for boot
adb wait-for-device
sleep 10
adb shell getprop sys.boot_completed

# Verify device online
adb devices
```

### Terminal 2: Start Datawatch Server

```bash
# Working dir outside the repo — never commit test data
RUN_ID=$(openssl rand -hex 3)
TEST_WORK_DIR=~/workspace/datawatch-test-${RUN_ID}
TEST_DATA_DIR=${TEST_WORK_DIR}/.datawatch-test-${RUN_ID}
mkdir -p "$TEST_DATA_DIR"

# BL318: scope Claude config to this test instance so the daemon never
# writes MCP registrations into ~/.claude.json or ~/.mcp.json
mkdir -p "${TEST_DATA_DIR}/.claude"

cat > "${TEST_WORK_DIR}/config.yaml" <<EOF
data_dir: ${TEST_DATA_DIR}
server:
  port: 18080
  tls_port: 18443
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
  ~/workspace/datawatch/bin/datawatch start --foreground \
  --config "${TEST_WORK_DIR}/config.yaml" \
  >> "${TEST_WORK_DIR}/daemon.log" 2>&1 &
echo $! > "${TEST_WORK_DIR}/test-daemon.pid"

sleep 5
curl -sk https://127.0.0.1:18443/api/health
# Expected: {"status":"healthy",...}
```

### Terminal 3: Set Up Port Forwarding and Verify

```bash
# Set up ADB reverse forwarding
adb reverse tcp:18443 tcp:18443
adb reverse tcp:18080 tcp:18080
# Replace 18443/18080 with $TEST_SERVER_TLS_PORT/$TEST_SERVER_HTTP_PORT if using dynamic ports

# Verify forwarding
adb reverse --list

# Verify mobile app can reach server from emulator
adb shell curl -k https://10.0.2.2:18443/api/health
```

### Verify Mobile App is Installed

```bash
# Check if datawatch app is installed
adb shell pm list packages | grep datawatch

# If not installed, install now:
adb install -r ~/workspace/datawatch-app/composeApp/build/outputs/apk/publicTrack/debug/composeApp-publicTrack-debug.apk

# Launch app
adb shell am start -n com.anthropic.datawatch/.MainActivity
```

---

## Phase 2: Initialize Test Run (5 minutes)

### Create Evidence Directory

```bash
cd ~/workspace/datawatch-app/docs/testing/v1.0.0

# Create evidence root (will hold all test artifacts)
mkdir -p evidence

# Verify directory exists
ls -la | grep evidence
```

### Start Logcat Capture (Optional but Recommended)

```bash
# In a dedicated terminal, capture logcat from the entire test run
adb logcat -c  # Clear prior logs
adb logcat > /tmp/test-run-logcat.txt &

# Note the logcat PID if you want to kill it later
# kill %1  # or: pkill -f "adb logcat"
```

### Initialize Cookbook Progress

```bash
# Open cookbook.md in editor
cd ~/workspace/datawatch-app/docs/testing/v1.0.0
nano cookbook.md  # or: vim, code, etc.

# Update header with run start time
# Change "Last updated: 2026-05-14" to today's date
# Add section at top:
#
# **Test Run Started**: 2026-05-16 14:30 UTC
# **Test Runner**: [your name]
# **Environment**: Secondary instance on [your hostname]
#
# Then save and exit
```

---

## Phase 3: Execute T-Sprints (4–10 hours)

### Test Execution Workflow

For each T-sprint (T1 through T21):

#### 1. Read Sprint Definition

```bash
# Example: T1 (Onboarding & Server Add)
# In plan.md, navigate to T1 section
# Review all stories TS-001 through TS-010
# Note any prerequisites or blockers
```

#### 2. Execute Each Story

For each story (e.g., TS-001):

**a) Read Story Details**
```
TS-001 — Fresh install → onboarding
**Tags**: [surface:phone]
**Steps**: 
  1. Clear app data: adb shell pm clear com.anthropic.datawatch
  2. Launch app: adb shell am start -n com.anthropic.datawatch/.MainActivity
  3. Observe: Splash screen appears, then onboarding flow
  4. Take screenshot
**Expected**: Onboarding screen shows "Add your first server" button
**Evidence**: evidence/TS-001/splash.png, evidence/TS-001/onboarding.png
```

**b) Create Evidence Directory**
```bash
mkdir -p docs/testing/v1.0.0/evidence/TS-001
```

**c) Execute Steps**
```bash
# Run step 1: Clear app data
adb shell pm clear com.anthropic.datawatch

# Run step 2: Launch app
adb shell am start -n com.anthropic.datawatch/.MainActivity

# Wait 3 seconds for app to load
sleep 3

# Run step 3: Take screenshot
adb shell screencap -p /sdcard/screen.png
adb pull /sdcard/screen.png docs/testing/v1.0.0/evidence/TS-001/splash.png
```

**d) Verify Expected Result**
- Look at screenshots
- Check logcat: `grep -i "error" /tmp/test-run-logcat.txt | tail -20`
- Verify app behavior matches "Expected" in plan

**e) Collect Evidence**
```bash
# Save logcat for this story
adb logcat -d > docs/testing/v1.0.0/evidence/TS-001/logcat.txt

# Save API responses (if applicable)
curl -sk https://127.0.0.1:18443/api/sessions > docs/testing/v1.0.0/evidence/TS-001/response.json

# Save any other relevant data (config, state, etc.)
```

**f) Update Cookbook Status**
```bash
# Open cookbook.md
nano docs/testing/v1.0.0/cookbook.md

# Find the T1 story table, locate TS-001 row
# Update Status column:
#   ✅ Pass      — if expected result matches
#   ❌ Fail      — if unexpected result
#   ⏭ Skip      — if intentionally skipped (note reason)
#   ⏳ Blocked   — if blocked by known issue (note blocker)

# Save and exit
```

**g) Commit Progress (After Each T-Sprint)**
```bash
# After completing all stories in a T-sprint
cd ~/workspace/datawatch-app

# Stage changes
git add docs/testing/v1.0.0/cookbook.md
git add docs/testing/v1.0.0/evidence/TS-*/

# Commit with message
git commit -m "test(v1.0.0): T1 complete — 7✅ 1❌ 2⏭ (Sprint 1/21)"

# Push to remote
git push origin main
```

### Example: Full T1 Execution

```bash
# T1: Onboarding & Server Add (TS-001–TS-010)

# TS-001: Fresh install → onboarding
mkdir -p evidence/TS-001
adb shell pm clear com.anthropic.datawatch
adb shell am start -n com.anthropic.datawatch/.MainActivity
sleep 3
adb shell screencap -p /sdcard/TS-001.png
adb pull /sdcard/TS-001.png evidence/TS-001/splash.png
# Verify onboarding shows
# Update cookbook.md: TS-001 → ✅ Pass

# TS-002: Add server — happy path
mkdir -p evidence/TS-002
# Follow plan.md steps: Settings → Comms → Add server
# Enter: Name=dw-test, URL=https://10.0.2.2:18443, Token=dw-test-token-12345
adb shell screencap -p /sdcard/TS-002-server-added.png
adb pull /sdcard/TS-002-server-added.png evidence/TS-002/server_added.png
# Verify server appears in list
# Update cookbook.md: TS-002 → ✅ Pass

# ... continue for TS-003 through TS-010

# After all T1 stories complete
git add docs/testing/v1.0.0/cookbook.md docs/testing/v1.0.0/evidence/TS-00*/
git commit -m "test(v1.0.0): T1 complete — 10✅ (Onboarding & server add)"
git push origin main
```

---

## Phase 4: Handling Test Failures

### ❌ When a Story Fails

**1. Document the Failure**
```bash
# In cookbook.md, mark story as ❌ Fail
# Add note: "Expected X but got Y; see evidence/TS-NNN/failure.md"

# In evidence directory, create failure.md
cat > docs/testing/v1.0.0/evidence/TS-NNN/failure.md <<EOF
## TS-NNN Failure Report

**Date**: 2026-05-16  
**Expected**: [from plan.md]  
**Actual**: [what you observed]  
**Steps to Reproduce**:
1. [step]
2. [step]

**Logcat Error**:
\`\`\`
[relevant error lines]
\`\`\`

**Screenshots**: See TS-NNN/failure.png

**Suspected Cause**: [your analysis]

**Workaround**: [if applicable]

**Upstream Issue**: [link to datawatch#X if applicable]
EOF
```

**2. Collect Detailed Evidence**
```bash
# Get logcat around failure time
adb logcat -d | grep -i "error\|exception\|crash" > evidence/TS-NNN/errors.log

# Get app state
adb shell dumpsys > evidence/TS-NNN/dumpsys.txt

# Get server logs if relevant
tail -50 "${TEST_WORK_DIR}/daemon.log" > evidence/TS-NNN/server-logs.txt
```

**3. Decide Action**
- **If fixable bug**: File issue in datawatch-app or datawatch repo, mark story ❌, continue testing
- **If blocker**: Mark story ⏳ Blocked with issue reference, skip dependent stories
- **If skip-worthy**: Mark story ⏭ Skip with reason

**4. Continue**
After documenting: move to next story, don't stop test run

---

## Phase 5: Handling Blocked Stories

### ⏳ When a Story is Blocked

Examples from v1.0.0:
- **T13 (TS-232–241)**: Blocked by `datawatch#48` (decompose timeout)
- **T15 (TS-286–305)**: Blocked by `datawatch#40-43` (missing endpoints)
- **T16 (TS-306–315)**: Blocked by `datawatch#39` (UnifiedPush)

**Actions**:
```bash
# Mark story as blocked in cookbook.md
# Format: "⏳ Blocked on datawatch#48"

# If workaround exists, note it
# Format: "⏳ Blocked on #48; workaround: use quick decompose only"

# Skip all dependent stories with same blocker
# Don't waste time re-testing the same blocker

# Link back to blocker in cookbook.md blocking issues table
```

---

## Phase 6: Handling Skipped Stories

### ⏭ When a Story is Skipped

Examples from v1.0.0:
- **TS-004**: Secondary instance has no token validation; skip token auth test
- **TS-008**: ADB swipe injection limitation; picker works via UI instead
- **TS-019–021**: Physical network cut required; skip network disconnect tests

**When to Skip**:
- Physical device required (watch, car, biometric)
- External service required (Signal, Discord, compute daemon)
- Known limitation of secondary instance
- Regression test from prior release that's passing

**Mark in Cookbook**:
```
TS-004 | Add server — wrong bearer | ⏭ Skip | Secondary has no token set; any token accepted
```

**Don't skip**:
- Core functionality that *could* be tested
- Tests that just take longer (run them)
- Tests that require waiting (schedule them)

---

## Phase 7: Monitor Progress and Health

### Periodic Health Checks (Every 1–2 Hours)

```bash
# Emulator still responsive
adb devices

# Server still running and healthy
curl -sk https://127.0.0.1:18443/api/health

# Disk space not filling up
df -h ~/workspace/

# No ADB crashes in logcat
adb logcat -d | grep -c "FATAL\|CRASH"

# No server crashes in logs
tail -20 "${TEST_WORK_DIR}/daemon.log" | grep -i "error\|panic"
```

### If Something Hangs

```bash
# Story takes >5 minutes to complete?
# Kill hanging process
adb shell am force-stop com.anthropic.datawatch

# Restart app
adb shell am start -n com.anthropic.datawatch/.MainActivity
sleep 5

# Mark story as ⏭ Skip with note "App hung; timeout >5min"
```

### If Server Crashes

```bash
# Check logs
tail -50 "${TEST_WORK_DIR}/daemon.log"

# Stop by PID — never grep ps
kill $(cat "${TEST_WORK_DIR}/test-daemon.pid") 2>/dev/null || true
sleep 2

CLAUDE_CONFIG_DIR="${TEST_DATA_DIR}/.claude" \
  ~/workspace/datawatch/bin/datawatch start --foreground \
  --config "${TEST_WORK_DIR}/config.yaml" \
  >> "${TEST_WORK_DIR}/daemon.log" 2>&1 &
echo $! > "${TEST_WORK_DIR}/test-daemon.pid"

# Wait for startup
sleep 5
curl -sk https://127.0.0.1:18443/api/health

# Resume testing from where you left off
# Mark interrupted story as ⏭ Skip with note "Server crashed; restarted"
```

---

## Phase 8: Complete Test Run (Final 10 minutes)

### Update Cookbook Summary

After all T-sprints complete:

```bash
# Open cookbook.md
nano docs/testing/v1.0.0/cookbook.md

# Update Sprint Status Summary table:
# - Fill in actual Passed, Failed, Skipped, Blocked counts per T-sprint
# - Calculate totals at bottom row

# Example:
| T1 | Onboarding & server add | 10 | 10 | 0 | 0 | 0 | ✅ |
| T2 | Session list & refresh | 25 | 23 | 0 | 2 | 0 | ✅ |
| ... (all T-sprints)
| TOTALS | | 369 | 320 | 2 | 28 | 19 | 🟡 IN PROGRESS |

# Calculate pass rate: 320 / (369 - 28 skip) × 100 = XX%
```

### Update Test Run Summary

```bash
# In cookbook.md, scroll to "Test Run Summary" section
# Fill in:

Test runner: [your name]
Run date: 2026-05-16 14:30 to 2026-05-16 23:45 (9 hours 15 min)
Environment: Secondary instance on [hostname], emulator dw_test_phone
Total stories: 369
Total passed: 320 (86.7%)
Total failed: 2 (0.5%)
Total skipped: 28 (7.6%)
Total blocked: 19 (5.1%)

Notes: [observations about test run — environment issues, surprises, recommendations]
```

### Verify All Evidence is Saved

```bash
# Count evidence directories
cd docs/testing/v1.0.0/evidence
find . -type d -name "TS-*" | wc -l

# Should be at least equal to (Total Stories - Total Skipped - Total Blocked)
# = 369 - 28 - 19 = 322 expected directories (if all non-skip/non-block tests ran)
```

---

## Phase 9: Final Commit and Cleanup

### Commit Test Results

```bash
cd ~/workspace/datawatch-app

# Stage all changes
git add docs/testing/v1.0.0/cookbook.md
git add docs/testing/v1.0.0/evidence/

# Commit with comprehensive message
git commit -m "test(v1.0.0): Full test run complete — 320✅ 2❌ 28⏭ 19⏳ (86.7% pass)

- T1–T14: Regression tests all pass
- T15–T16: Blocked on server endpoints and UnifiedPush (#40-43, #39)
- T17–T19: Parity audit and test debt complete
- T20–T21: Howto validation and journeys blocked on #48 (decompose timeout)
- Run time: 9h 15m on secondary instance
- Full evidence preserved in evidence/

See docs/testing/v1.0.0/cookbook.md for detailed results."

# Push to remote
git push origin main
```

### Create GitHub Release (if ship-ready)

```bash
# If test pass rate meets ship criteria (90%+ non-skip):
cd ~/workspace/datawatch-app

# Create version tag
git tag -a v1.0.0 -m "Release v1.0.0 — Mobile app with autonomous PRD lifecycle"

# Push tag
git push origin v1.0.0

# Or create release via gh CLI:
gh release create v1.0.0 \
  --title "datawatch-app v1.0.0" \
  --notes-file docs/testing/v1.0.0/RELEASE_NOTES.md \
  --draft  # Remove --draft when ready to ship
```

### Optional: Archive Evidence for Future Reference

```bash
# Create tarball of evidence (for diagnosis if issues arise post-release)
cd ~/workspace/datawatch-app/docs/testing/v1.0.0
tar -czf evidence-v1.0.0-$(date +%Y%m%d).tar.gz evidence/

# Store safely
mv evidence-v1.0.0-*.tar.gz ~/Downloads/ || cp evidence-v1.0.0-*.tar.gz /mnt/backup/
```

### Clean Up Test Environment (Optional)

```bash
# If you won't run tests again on this system:

# Stop server by PID — never grep ps
kill $(cat "${TEST_WORK_DIR}/test-daemon.pid") 2>/dev/null || true
adb emu kill

# Remove evidence (already committed to git, so safe)
# rm -rf docs/testing/v1.0.0/evidence/

# Remove test working dir (outside the repo) — includes TEST_DATA_DIR/.claude/
# and TEST_DATA_DIR/.mcp.json so no stale MCP registrations survive cleanup.
rm -rf "${TEST_WORK_DIR}"

# Keep these for archival:
# - docs/testing/v1.0.0/plan.md (immutable reference)
# - docs/testing/v1.0.0/cookbook.md (final results, committed to git)
```

---

## Troubleshooting Test Execution

### App Crashes During Test

```bash
# Capture crash logs
adb logcat -d | grep -A 20 "AndroidRuntime\|FATAL"

# Save to evidence
adb logcat -d > evidence/TS-NNN/crash.log

# Restart app
adb shell am force-stop com.anthropic.datawatch
adb shell am start -n com.anthropic.datawatch/.MainActivity

# Mark story ❌ Fail or ⏭ Skip and continue
```

### Story Timeout (App Unresponsive)

```bash
# If story takes >5 minutes to reach expected result:

# Option 1: Force stop and skip
adb shell am force-stop com.anthropic.datawatch
# Mark story ⏭ Skip with note "Timeout >5min; app unresponsive"

# Option 2: Wait up to 10 minutes for slow network
# If second attempt succeeds, mark ✅ Pass
# If second attempt also fails, mark ⏭ Skip with note about slowness
```

### Network Issues Between Emulator and Server

```bash
# Test connectivity from emulator
adb shell curl -v https://10.0.2.2:18443/api/health

# Check port forwarding
adb reverse --list

# If missing, re-enable
adb reverse tcp:18443 tcp:18443
adb reverse tcp:18080 tcp:18080
# Replace 18443/18080 with $TEST_SERVER_TLS_PORT/$TEST_SERVER_HTTP_PORT if using dynamic ports

# Verify from host
curl -sk https://127.0.0.1:18443/api/health
```

### Server Out of Memory or Disk Full

```bash
# Check server logs
tail -50 "${TEST_WORK_DIR}/daemon.log"

# Check disk
df -h ~/workspace/

# If disk >90% full:
# - Delete old evidence if accumulated
# - Reduce emulator snapshot size
# - Restart server with clean data

# Stop by PID — never grep ps
kill $(cat "${TEST_WORK_DIR}/test-daemon.pid") 2>/dev/null || true
rm -rf "${TEST_WORK_DIR}"

# Re-create working dir and config from scratch
RUN_ID=$(openssl rand -hex 3)
TEST_WORK_DIR=~/workspace/datawatch-test-${RUN_ID}
TEST_DATA_DIR=${TEST_WORK_DIR}/.datawatch-test-${RUN_ID}
mkdir -p "$TEST_DATA_DIR"
mkdir -p "${TEST_DATA_DIR}/.claude"
cat > "${TEST_WORK_DIR}/config.yaml" <<EOF
data_dir: ${TEST_DATA_DIR}
server:
  port: 18080
  tls_port: 18443
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

# Restart server
CLAUDE_CONFIG_DIR="${TEST_DATA_DIR}/.claude" \
  ~/workspace/datawatch/bin/datawatch start --foreground \
  --config "${TEST_WORK_DIR}/config.yaml" \
  >> "${TEST_WORK_DIR}/daemon.log" 2>&1 &
echo $! > "${TEST_WORK_DIR}/test-daemon.pid"
```

### Certificate Issues (TLS Errors)

```bash
# Server auto-generates self-signed cert on first start
# Mobile app configured with Trust-all TLS: true for secondary instance

# If cert errors persist:
rm -rf "${TEST_WORK_DIR}/.datawatch-test-"*/tls/
# Restart server to regenerate

# Verify from emulator
adb shell curl -k https://10.0.2.2:18443/api/health
# -k flag ignores cert validation (same as Trust-all in app)
```

---

## Test Metrics and Reporting

### Calculate Pass Rate

```bash
# From cookbook.md final counts:
PASS=320
TOTAL_RUNNABLE=$((369 - 28))  # Exclude skipped

PERCENT=$((PASS * 100 / TOTAL_RUNNABLE))
echo "Pass rate: $PERCENT% ($PASS / $TOTAL_RUNNABLE)"
```

### Identify Critical Issues

```bash
# Review all ❌ failures
grep "❌ Fail" docs/testing/v1.0.0/cookbook.md

# For each failure:
# - Check if it's a P0 (ship blocker) or P2 (nice-to-have)
# - P0: Must fix before ship
# - P1: Should fix before ship
# - P2: Defer to next release
```

### Document Blockers

```bash
# Review all ⏳ blocked stories
grep "⏳ Blocked" docs/testing/v1.0.0/cookbook.md

# For each blocker:
# - Link to upstream issue (datawatch#X)
# - Document workaround if available
# - Note target resolution date
```

---

## Reference

| File | Purpose |
|------|---------|
| `plan.md` | 369 story definitions (immutable) |
| `cookbook.md` | Live status table (updated during run) |
| `REQUIREMENTS.md` | System specs and prerequisites |
| `SETUP.md` | Step-by-step setup walkthrough |
| `RUN.md` | This file — test execution guide |
| `evidence/` | Test artifacts (screenshots, logcat, JSON) |

---

**Last Updated**: 2026-05-16  
**For v1.0.0 Release**  
**Datawatch Server**: v7.0.0+  
**Mobile App**: v1.0.0
