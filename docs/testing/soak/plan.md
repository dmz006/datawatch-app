# datawatch-app — Soak Test Plan

**Version**: v1.0.0 (Soak Extension)
**Date**: 2026-05-16
**Scope**: Session refresh regression loops — phone surface only
**Test host**: johnnyjohnny (32G GPU, Ollama `qwen3:1.7b`)
**Test environment**: Secondary datawatch instance (ports 18080/18443) + emulator `dw_test_phone`
**Story namespace**: SS-001–SS-020 (separate from TS-XXX)
**IMPORTANT**: ALL soak tests run against the secondary test instance (`https://10.0.2.2:18443`). Production johnnyjohnny (8080/8443) is only used for posting result summaries via the hook endpoint.

---

## 1. Purpose

Soak tests run the same session-refresh scenarios that passed in T14 (TS-256–TS-285) **repeatedly over 2–4 hours** to surface defects that only appear after 100–500 iterations:

- **Memory leaks** — heap grows unboundedly over hundreds of poll cycles
- **Coroutine leaks** — background coroutines accumulate across on-resume cycles
- **Connection degradation** — WebSocket reconnect count or connection pool size grows over time
- **State drift** — session list or badge count diverges from server truth after many refreshes
- **Crash / ANR** — app becomes unresponsive after extended exercise

The 20 soak stories in this plan (SS-001–SS-020) correspond to the 20 T14 stories (TS-261–TS-285 minus the 5 that passed normally) that were deferred from the v1.0.0 test run. The 10 T14 stories that already passed (single-iteration) remain passing in `docs/testing/v1.0.0/cookbook.md`.

---

## 2. Timing

| Story | Scenario | Duration | Iterations |
|-------|----------|----------|------------|
| SS-001 | Continuous poll refresh | 2 h | ~240 (every 30 s) |
| SS-002 | On-resume refresh cycles | ~50 min | 100x |
| SS-003 | Screen lock/unlock cycles | ~25 min | 50x |
| SS-004 | Tab switch cycles | ~15 min | 30x |
| SS-005 | Keep-alive session | 4 h | sustained |
| SS-006 | Alert dismiss cycles | ~30 min | 200x |
| SS-007 | Server switch cycles | ~25 min | 50x |
| SS-008 | New session + kill cycles | ~50 min | 100x |
| SS-009 | Autonomous PRD polling | 3 h | sustained |
| SS-010 | WebSocket reconnect storm | ~40 min | 20x server restart |
| SS-011 | Multi-server tab switch | ~25 min | 50x |
| SS-012 | Filter chip cycle | ~20 min | 100x |
| SS-013 | Sort order cycle | ~15 min | 75x |
| SS-014 | Session detail open/close | ~40 min | 150x |
| SS-015 | Bulk select + deselect | ~20 min | 50x |
| SS-016 | Alert badge refresh | 2 h | sustained |
| SS-017 | History toggle cycle | ~15 min | 60x |
| SS-018 | Terminal scroll cycle | ~30 min | 40x |
| SS-019 | Notification dismiss cycle | ~20 min | 80x |
| SS-020 | Full session lifecycle loop | 2 h | ~30 full cycles |

---

## 3. Setup Requirements

See `SETUP.md` in this directory for complete prerequisites. Summary:

1. `dw_test_phone` emulator running, app installed
2. Secondary datawatch instance healthy at `https://127.0.0.1:18443`
3. ADB reverse forwarding active: `tcp:18443` and `tcp:18080`
4. At least 6 GB free heap reported by Android Memory Profiler at baseline
5. `adb shell dumpsys meminfo com.dmzs.datawatchclient.dev.debug` captures baseline before each story
6. Production hook endpoint reachable: `curl -sk https://localhost:8443/api/test/message`

---

## 4. Pass Criteria (Global)

Unless overridden per-story:

| Metric | Pass Threshold |
|--------|---------------|
| Heap growth | < 50 MB over any 2-hour window |
| ANR / crash | 0 occurrences |
| WS reconnect count | Flat (±2 from baseline) after 1 hour |
| Session list count drift | 0 (list matches `GET /api/sessions` on server) |
| Alert badge count | Matches server at end of run |
| Background coroutines | No net increase from baseline after all iterations |

---

## 5. Evidence Collection

After each story run, the script writes:
- `docs/testing/soak/evidence/run-TIMESTAMP.json` — structured result (heap delta, iteration count, pass/fail)
- `/tmp/soak-run-TIMESTAMP.log` — full execution log
- `adb shell dumpsys meminfo` snapshot at start and end (embedded in JSON)

Evidence directory is gitignored. Preserve failed runs for diagnosis.

---

## 6. How to Interpret Results

**Heap growth** is computed as: `(end_PSS_MB - start_PSS_MB)`. Values above 50 MB are a FAIL. Values between 20–50 MB are a warning (investigate before shipping).

**WS reconnect count** is read from `adb logcat -d | grep "WebSocket reconnect"` counts. If the count grows linearly with time rather than stabilizing, the session is leaking reconnects.

**Coroutine leak** evidence: logcat tag `CoroutineScope` lines with `Job was cancelled` that keep appearing after iterations complete (not just at teardown).

**State drift** is checked by comparing `adb shell curl -sk -H "Authorization: Bearer dw-test-token-12345" https://10.0.2.2:18443/api/sessions | jq length` against the on-screen session count (read via `adb shell uiautomator dump`).

---

## 7. Sprint Table — Soak Stories

| Story | Title | Duration | Surface | T14 Source |
|-------|-------|----------|---------|------------|
| SS-001 | 2-hour continuous poll refresh | 2 h | phone | TS-261 |
| SS-002 | 100x on-resume refresh cycle | ~50 min | phone | TS-262 |
| SS-003 | 50x screen lock/unlock cycle | ~25 min | phone | TS-263 |
| SS-004 | 30x tab switch refresh cycle | ~15 min | phone | TS-264 |
| SS-005 | 4-hour keep-alive session | 4 h | phone | TS-265 |
| SS-006 | 200x alert dismiss cycle | ~30 min | phone | TS-266 |
| SS-007 | 50x server switch cycle | ~25 min | phone | TS-267 |
| SS-008 | 100x new session + kill cycle | ~50 min | phone | TS-268 |
| SS-009 | 3-hour autonomous PRD poll | 3 h | phone | TS-269 |
| SS-010 | WebSocket reconnect storm (20x) | ~40 min | phone | TS-270 |
| SS-011 | 50x multi-server tab switch | ~25 min | phone | TS-271 |
| SS-012 | 100x filter chip cycle | ~20 min | phone | TS-272 |
| SS-013 | 75x sort order cycle | ~15 min | phone | TS-273 |
| SS-014 | 150x session detail open/close | ~40 min | phone | TS-274 |
| SS-015 | 50x bulk select + deselect | ~20 min | phone | TS-275 |
| SS-016 | 2-hour alert badge refresh | 2 h | phone | TS-276 |
| SS-017 | 60x history toggle cycle | ~15 min | phone | TS-277 |
| SS-018 | 40x terminal scroll cycle | ~30 min | phone | TS-278 |
| SS-019 | 80x notification dismiss cycle | ~20 min | phone | TS-279 |
| SS-020 | 2-hour full session lifecycle loop | 2 h | phone | TS-280 |

---

## 8. Individual Story Definitions

---

### SS-001 — 2-Hour Continuous Poll Refresh

**Title**: Heap stability over 240 poll refresh cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:2h]` `[feature:sessions]`
**Source**: TS-261

**Setup**:
1. Start secondary datawatch instance; verify health
2. Boot `dw_test_phone` emulator; set up ADB reverse forwarding
3. Launch datawatch-app; navigate to Sessions list
4. Create 3 running sessions on test server via curl
5. Capture baseline: `adb shell dumpsys meminfo com.dmzs.datawatchclient.dev.debug > /tmp/ss001-heap-start.txt`
6. Note WS reconnect count baseline from logcat: `adb logcat -d | grep -c "WebSocket reconnect"` → `$WS_BASELINE`

**Steps**:
1. Leave app on Sessions screen (poll interval ~10 s; with 30 s check interval = ~240 iterations in 2 h)
2. Every 30 minutes, capture heap snapshot via `adb shell dumpsys meminfo` and append to log
3. After exactly 2 hours, capture final heap: `adb shell dumpsys meminfo com.dmzs.datawatchclient.dev.debug > /tmp/ss001-heap-end.txt`
4. Capture final WS reconnect count: `adb logcat -d | grep -c "WebSocket reconnect"` → `$WS_END`

**Pass criteria**:
- Heap growth (end PSS − start PSS) < 50 MB
- `$WS_END − $WS_BASELINE` ≤ 2 (no reconnect accumulation)
- 0 ANR dialogs observed
- 0 crashes (check `adb shell dumpsys dropbox | grep ANR`)
- Session list item count matches server: `GET /api/sessions | jq length` at end

**Evidence**:
- `/tmp/ss001-heap-start.txt`, `/tmp/ss001-heap-end.txt`
- Logcat grep for `WebSocket reconnect`
- `docs/testing/soak/evidence/run-TIMESTAMP.json` (written by script)

---

### SS-002 — 100x On-Resume Refresh Cycle

**Title**: Coroutine leak check — 100 background/foreground cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:50min]` `[feature:sessions]`
**Source**: TS-262

**Setup**:
1. Launch app; navigate to Sessions list
2. Capture baseline heap + `adb shell dumpsys activity processes | grep coroutine` if available
3. Ensure ≥ 2 running sessions exist on test server

**Steps**:
1. For each iteration (1–100):
   a. Press Home key: `adb shell input keyevent KEYCODE_HOME`
   b. Wait 5 seconds
   c. Resume app: `adb shell monkey -p com.dmzs.datawatchclient.dev.debug -c android.intent.category.LAUNCHER 1`
   d. Wait 3 seconds for refresh to complete
   e. Verify session list is not stale (session count visible)
2. After 100 iterations, capture final heap snapshot

**Pass criteria**:
- Heap growth < 50 MB total (100 iterations)
- No crash / restart of the app process at any point
- Session list shows correct count matching server at end
- No `JobCancellationException` flood in logcat (occasional is fine; > 10/iteration is a FAIL)

**Evidence**:
- Heap start/end via `dumpsys meminfo`
- `adb logcat -d | grep -c "JobCancellationException"` — must be < 1000 total

---

### SS-003 — 50x Screen Lock/Unlock Cycle

**Title**: WebSocket reconnect stability over 50 lock/unlock cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:25min]` `[feature:sessions]`
**Source**: TS-263

**Setup**:
1. App open on Sessions screen
2. Baseline WS reconnect count: `adb logcat -d | grep -c "WebSocket reconnect"` → `$WS_BASELINE`
3. Baseline heap captured

**Steps**:
1. For each iteration (1–50):
   a. Lock screen: `adb shell input keyevent KEYCODE_POWER`
   b. Wait 10 seconds
   c. Unlock: `adb shell input keyevent KEYCODE_POWER` then `adb shell input keyevent KEYCODE_MENU` (dismiss keyguard)
   d. Wait 5 seconds for WS to re-establish
   e. Verify sessions screen is visible and not crashed

**Pass criteria**:
- `$WS_END − $WS_BASELINE` ≤ 5 (one reconnect per cycle is acceptable; growth beyond 50 + 5 buffer is a FAIL)
- WS reconnect count stabilizes (does not grow after last iteration)
- 0 crashes
- Heap growth < 30 MB

**Evidence**:
- WS reconnect count before/after
- Heap delta
- Logcat for `WebSocket` and `SocketException`

---

### SS-004 — 30x Tab Switch Refresh Cycle

**Title**: No state drift after 30 tab switch cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:15min]` `[feature:sessions]` `[feature:alerts]`
**Source**: TS-264

**Setup**:
1. App open; navigate to Sessions tab
2. Ensure ≥ 3 sessions and ≥ 1 alert exist on test server
3. Baseline: session count = N, alert badge count = A

**Steps**:
1. For each iteration (1–30):
   a. Tap Alerts tab (bottom nav)
   b. Wait 2 seconds
   c. Tap Sessions tab (bottom nav)
   d. Wait 3 seconds
   e. Verify session list count matches N

**Pass criteria**:
- Session count = N at all 30 iterations (0 drift)
- Alert badge count = A at all 30 iterations (0 drift)
- Heap growth < 20 MB over full run
- 0 crashes

**Evidence**:
- ADB uiautomator dump at iteration 1, 15, 30 to check displayed counts
- Heap delta

---

### SS-005 — 4-Hour Keep-Alive Session

**Title**: Session remains alive and connected for 4 hours without user interaction
**Tags**: `[surface:phone]` `[type:soak]` `[duration:4h]` `[feature:sessions]`
**Source**: TS-265

**Setup**:
1. Create 1 long-running session on test server: `curl -sk -X POST -H "Authorization: Bearer dw-test-token-12345" https://127.0.0.1:18443/api/sessions -d '{"title":"soak-keepalive"}'`
2. Open session detail on phone
3. Baseline heap; disable screen timeout (`adb shell settings put system screen_off_timeout 0`)

**Steps**:
1. Leave app open on session detail screen for 4 hours
2. Every 30 minutes: verify WS connection alive via logcat (no `CLOSED` event without subsequent `OPENED`)
3. Every 60 minutes: capture heap snapshot
4. At 4 hours: verify session still shows `running` state

**Pass criteria**:
- Session state = `running` at T+4h
- 0 disconnects not followed by reconnect within 30 seconds
- Heap growth < 50 MB over 4 hours
- 0 ANR / crashes
- No `DISCONNECTED` banner shown for > 30 s at any point

**Evidence**:
- Heap snapshots at 0/1/2/3/4 hours
- Logcat WS event timeline
- `GET /api/sessions/{id}` state at end

---

### SS-006 — 200x Alert Dismiss Cycle

**Title**: No alert badge leak over 200 dismiss cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:30min]` `[feature:alerts]`
**Source**: TS-266

**Setup**:
1. Create script to POST 200 alerts to test server (1 per iteration)
2. Navigate to Alerts screen on phone
3. Baseline: badge count = 0

**Steps**:
1. For each iteration (1–200):
   a. POST a new alert via API: `curl -sk -X POST -H "Authorization: Bearer dw-test-token-12345" https://127.0.0.1:18443/api/alerts -d '{"message":"soak-alert-N","level":"info"}'`
   b. Wait for badge to increment (verify via adb uiautomator dump)
   c. Dismiss alert by tapping dismiss button in app
   d. Verify badge returns to 0

**Pass criteria**:
- Badge count = 0 after each dismiss (200/200)
- No residual unread count after full run
- Heap growth < 40 MB (200 iterations of alert object creation/destruction)
- 0 crashes

**Evidence**:
- Badge count sampled at iterations 50/100/150/200
- `GET /api/alerts?unread=true | jq length` at end — must be 0
- Heap delta

---

### SS-007 — 50x Server Switch Cycle

**Title**: No connection pool growth over 50 server switch cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:25min]` `[feature:multiserver]`
**Source**: TS-267

**Setup**:
1. Configure 2 servers on phone: `dw-test` (18443) and `dw-test2` (if available; else toggle same server off/on)
2. Baseline: `adb logcat -d | grep -c "OkHttp"` → `$HTTP_BASELINE`
3. Baseline heap

**Steps**:
1. For each iteration (1–50):
   a. Open server picker (3-finger swipe or toolbar button)
   b. Select server 2 (or toggle)
   c. Wait 3 seconds for reconnect
   d. Switch back to server 1
   e. Wait 3 seconds
2. After 50 iterations: capture `adb logcat | grep -c "OkHttp connection"` → `$HTTP_END`
3. Final heap snapshot

**Pass criteria**:
- `$HTTP_END − $HTTP_BASELINE` growth is linear with iterations, not exponential (no pooling leak)
- No `OutOfMemoryError` in logcat
- Sessions list loads within 5 s on each switch
- Heap growth < 40 MB

**Evidence**:
- OkHttp connection log count before/after
- Heap delta

---

### SS-008 — 100x New Session + Kill Cycle

**Title**: Session list length stable after 100 create/kill cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:50min]` `[feature:sessions]`
**Source**: TS-268

**Setup**:
1. Sessions list is empty (or history hidden)
2. Baseline: list count = 0 (running only)
3. Baseline heap

**Steps**:
1. For each iteration (1–100):
   a. Create session via FAB or API: `curl -sk -X POST ... /api/sessions`
   b. Wait for session to appear in list (≤ 10 s)
   c. Kill session via API: `curl -sk -X DELETE ... /api/sessions/{id}`
   d. Wait for session to disappear from running list (≤ 12 s)
   e. Verify running count = 0

**Pass criteria**:
- Running list count = 0 at all 100 iteration endpoints
- Heap growth < 50 MB (100 allocations/deallocations of session VM objects)
- No duplicate session cards visible at any point
- 0 crashes

**Evidence**:
- List count sampled at iterations 25/50/75/100
- Heap delta
- `GET /api/sessions?state=running | jq length` at end — must be 0

---

### SS-009 — 3-Hour Autonomous PRD Polling

**Title**: Memory stability while polling PRD status for 3 hours
**Tags**: `[surface:phone]` `[type:soak]` `[duration:3h]` `[feature:autonomous]`
**Source**: TS-269

**Setup**:
1. Create 1 PRD in `pending` state on test server via API
2. Navigate to Automata → PRD detail on phone
3. Baseline heap
4. Confirm PRD status polling is active (logcat shows periodic `GET /api/automata/prds/{id}`)

**Steps**:
1. Leave app on PRD detail screen for 3 hours (PRD remains in pending — not advanced)
2. Every 60 minutes: capture heap snapshot
3. At 3 hours: final heap and verify PRD still shows `pending`

**Pass criteria**:
- Heap growth < 50 MB over 3 hours
- PRD detail screen shows correct state at T+3h
- No polling error toasts accumulate
- 0 crashes

**Evidence**:
- Heap snapshots at 0/1/2/3 hours
- Logcat PRD poll event count (approximate iterations: ~1080 in 3h at 10s interval)

---

### SS-010 — WebSocket Reconnect Storm (20x Server Restart)

**Title**: WebSocket reconnects successfully after 20 server stop/start cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:40min]` `[feature:sessions]`
**Source**: TS-270

**Setup**:
1. App open on Sessions list; WS connected
2. Baseline WS reconnect count from logcat
3. Note PID of test server: `cat /tmp/test-daemon-$TEST_RUN_HASH.pid`

**Steps**:
1. For each iteration (1–20):
   a. Kill test server: `kill $(cat /tmp/test-daemon-$TEST_RUN_HASH.pid)`
   b. Verify app shows disconnect indicator (≤ 5 s)
   c. Restart server: `datawatch start --foreground --config .datawatch-test-$TEST_RUN_HASH/config.yaml &`
   d. Wait for server health: `until curl -sk https://127.0.0.1:18443/api/health | grep ok; do sleep 1; done`
   e. Wait for app to reconnect (≤ 15 s; logcat `WebSocket opened`)
   f. Verify sessions list populated (not empty)

**Pass criteria**:
- App successfully reconnects on all 20 iterations (20/20)
- Reconnect latency after server up ≤ 15 s in all cases
- 0 crashes during disconnect/reconnect
- Session list repopulated without stale data after each reconnect
- Heap growth < 30 MB over 20 restart cycles

**Evidence**:
- Per-iteration reconnect latency (logged by script)
- Logcat `WebSocket opened` count = 20 (plus baseline connects)
- Heap delta

---

### SS-011 — 50x Multi-Server Tab Switch

**Title**: State isolation maintained across 50 server-switch tab cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:25min]` `[feature:multiserver]`
**Source**: TS-271

**Setup**:
1. Two server profiles configured: dw-test (18443) and a stub/alias pointing to same instance with different project
2. Baseline: server 1 session count = N1, server 2 session count = N2

**Steps**:
1. For each iteration (1–50):
   a. Switch to server 2 (picker)
   b. Verify session count = N2
   c. Switch back to server 1
   d. Verify session count = N1

**Pass criteria**:
- N1 and N2 consistent at all 50 iterations (0 bleed-through)
- No server 2 sessions visible when server 1 active
- Heap growth < 30 MB
- 0 crashes

**Evidence**:
- Count sampled at iterations 10/25/50
- Heap delta

---

### SS-012 — 100x Filter Chip Cycle

**Title**: No state leak after 100 filter chip activations
**Tags**: `[surface:phone]` `[type:soak]` `[duration:20min]` `[feature:sessions]`
**Source**: TS-272

**Setup**:
1. Sessions list with ≥ 5 sessions in mixed states (running, done, waiting)
2. Baseline: unfiltered count = N

**Steps**:
1. For each iteration (1–100):
   a. Tap "Active" filter chip
   b. Verify only running sessions shown
   c. Tap "Active" chip again (deactivate)
   d. Verify all sessions shown (count = N)

**Pass criteria**:
- Unfiltered count = N after every deactivate (100/100)
- No sessions permanently hidden
- Heap growth < 20 MB (100 filter operations)
- 0 crashes

**Evidence**:
- Count at start/end of full cycle
- Heap delta

---

### SS-013 — 75x Sort Order Cycle

**Title**: Sort order resets correctly over 75 cycle iterations
**Tags**: `[surface:phone]` `[type:soak]` `[duration:15min]` `[feature:sessions]`
**Source**: TS-273

**Setup**:
1. Sessions list with ≥ 5 sessions with different names and activity times
2. Baseline: default sort = by recent activity

**Steps**:
1. For each iteration (1–75):
   a. Change sort to "By name"
   b. Verify alphabetical order
   c. Change sort to "By started"
   d. Verify createdAt DESC order
   e. Change sort back to "Recent activity"
   f. Verify default order restored

**Pass criteria**:
- Sort order correct at all 75 iterations (225 sort changes total)
- No sort order frozen/stuck at any point
- Heap growth < 20 MB
- 0 crashes

**Evidence**:
- First/last session name sampled at iterations 25/50/75
- Heap delta

---

### SS-014 — 150x Session Detail Open/Close

**Title**: No memory accumulation from 150 session detail navigations
**Tags**: `[surface:phone]` `[type:soak]` `[duration:40min]` `[feature:sessions]`
**Source**: TS-274

**Setup**:
1. Sessions list with ≥ 1 running session (has terminal output)
2. Baseline heap

**Steps**:
1. For each iteration (1–150):
   a. Tap session card to open detail
   b. Wait 2 seconds (terminal renders)
   c. Press Back to return to list
   d. Wait 1 second

**Pass criteria**:
- Heap growth < 50 MB over 150 navigations
- No `OutOfMemoryError` in logcat
- Session detail renders correctly at all iterations (not blank)
- 0 crashes

**Evidence**:
- Heap at 0/50/100/150 iterations
- `adb shell dumpsys activity | grep -c "ActivityRecord"` before/after (fragment back-stack leak check)

---

### SS-015 — 50x Bulk Select + Deselect

**Title**: Selection mode exits cleanly over 50 long-press cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:20min]` `[feature:sessions]`
**Source**: TS-275

**Setup**:
1. Sessions list with ≥ 5 sessions
2. Baseline: selection mode inactive, checkbox count = 0

**Steps**:
1. For each iteration (1–50):
   a. Long-press first session card (enter selection mode)
   b. Tap 2 more sessions (3 selected)
   c. Press Back to exit selection mode
   d. Verify no checkboxes visible, normal list restored

**Pass criteria**:
- Selection mode exits cleanly at all 50 iterations
- No phantom checkboxes after exit
- Heap growth < 20 MB
- 0 crashes

**Evidence**:
- Screenshot at iteration 1, 25, 50 (via adb screencap)
- Heap delta

---

### SS-016 — 2-Hour Alert Badge Refresh

**Title**: Alert badge count stays accurate over 2 hours of activity
**Tags**: `[surface:phone]` `[type:soak]` `[duration:2h]` `[feature:alerts]`
**Source**: TS-276

**Setup**:
1. No unread alerts at start (badge = 0)
2. Automation script posts 1 alert every 30 seconds for 2 hours (240 alerts)
3. Alerts screen visible on phone
4. Baseline heap

**Steps**:
1. Start alert generation script
2. Every 15 minutes: verify badge count on Alerts tab matches `GET /api/alerts?unread=true | jq length`
3. At 1 hour: dismiss all alerts (batch dismiss)
4. Continue monitoring badge for second hour
5. At 2 hours: final badge count check

**Pass criteria**:
- Badge count matches server at all 8 sampling points (0 drift)
- After batch dismiss, badge returns to 0 and stays 0 until next alert posted
- Heap growth < 50 MB over 2 hours
- 0 crashes

**Evidence**:
- Badge vs server count at 8 sample points
- Heap delta
- `GET /api/alerts?unread=true` at end

---

### SS-017 — 60x History Toggle Cycle

**Title**: History toggle state consistent over 60 cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:15min]` `[feature:sessions]`
**Source**: TS-277

**Setup**:
1. Sessions list with ≥ 5 completed/killed sessions and ≥ 2 running
2. Baseline: History OFF, running count = R, history count = H

**Steps**:
1. For each iteration (1–60):
   a. Tap History chip (ON) — wait 1 s
   b. Verify total count = R + H
   c. Tap History chip (OFF) — wait 1 s
   d. Verify count = R

**Pass criteria**:
- Running count = R and total count = R + H at all 60 iterations
- No sessions permanently disappeared
- Heap growth < 20 MB
- 0 crashes

**Evidence**:
- Count at start/end
- Heap delta

---

### SS-018 — 40x Terminal Scroll Cycle

**Title**: Terminal view scrolls without memory accumulation over 40 cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:30min]` `[feature:sessions]`
**Source**: TS-278

**Setup**:
1. Open a running session with ≥ 100 lines of terminal output
2. Baseline heap

**Steps**:
1. For each iteration (1–40):
   a. Scroll to bottom of terminal: `adb shell input swipe 500 800 500 200`
   b. Wait 1 s
   c. Scroll to top: `adb shell input swipe 500 200 500 800`
   d. Wait 1 s
   e. Send 5 short commands to generate more output

**Pass criteria**:
- Heap growth < 50 MB over 40 iterations (terminal buffer bounded)
- No terminal freeze or blank screen at any iteration
- Scroll position accurate at top/bottom
- 0 crashes

**Evidence**:
- Heap at 0/20/40 iterations
- Logcat for `RecyclerView` errors

---

### SS-019 — 80x Notification Dismiss Cycle

**Title**: Notification state cleared correctly after 80 dismiss cycles
**Tags**: `[surface:phone]` `[type:soak]` `[duration:20min]` `[feature:alerts]` `[feature:push]`
**Source**: TS-279

**Setup**:
1. Push notifications enabled (test server webhook configured)
2. Baseline: notification shade empty

**Steps**:
1. For each iteration (1–80):
   a. Trigger 1 alert on test server: `curl -sk -X POST ... /api/alerts`
   b. Wait for notification to appear in shade (≤ 10 s)
   c. Dismiss notification from shade: `adb shell input keyevent KEYCODE_NOTIFICATION`
   d. Wait 2 s
   e. Verify badge updated (if applicable)

**Pass criteria**:
- Notification appears within 10 s for all 80 iterations
- Dismissed notifications don't re-appear
- Notification count in shade = 0 after each dismiss
- 0 crashes

**Evidence**:
- Notification arrival latency log (script records timestamp per iteration)
- Heap delta

---

### SS-020 — 2-Hour Full Session Lifecycle Loop

**Title**: Complete session lifecycle (create → interact → kill) stable over 2 hours
**Tags**: `[surface:phone]` `[type:soak]` `[duration:2h]` `[feature:sessions]` `[feature:autonomous]`
**Source**: TS-280

**Setup**:
1. Empty sessions list (no running sessions)
2. Baseline heap; baseline WS reconnect count

**Steps**:
1. Repeat the following cycle for 2 hours (~30 full cycles at ~4 min each):
   a. Create new session via API
   b. Open session detail on phone — wait for terminal to show prompt
   c. Send 3 commands via API input endpoint
   d. Wait for responses to appear in terminal
   e. Return to session list
   f. Kill session via API
   g. Verify session moves to completed state (history)
   h. Verify running count = 0
2. At 2 hours: final heap, WS count, session list validation

**Pass criteria**:
- All ~30 cycles complete without error
- Running count = 0 at end
- Heap growth < 50 MB over 2 hours
- WS reconnect count stable (≤ 5 above baseline)
- 0 crashes

**Evidence**:
- Cycle count and per-cycle timing (script log)
- Heap snapshots at 0/1/2 hours
- `GET /api/sessions?state=running | jq length` = 0 at end

---

## 9. Excluded Stories (Already Passed in T14)

The following T14 stories ran as single-iteration tests and passed. They are NOT included in soak:

| T14 Story | Title | v1.0.0 Status |
|-----------|-------|---------------|
| TS-256 | Poll refresh — running session updates | ✅ Pass (TS-012 regression) |
| TS-257 | On-resume refresh | ✅ Pass (TS-015 regression) |
| TS-258 | Screen-lock → unlock refresh | ✅ Pass (TS-016 regression) |
| TS-259 | Tab switch refresh | ✅ Pass (TS-017 regression) |
| TS-260 | New session → appears in list | ✅ Pass (TS-018 regression) |
| TS-281 | Server reconnect after disconnect | ✅ Pass |
| TS-282 | Reachability dot goes offline | ✅ Pass |
| TS-283 | Reachability dot recovers | ✅ Pass |
| TS-284 | History toggle persistent | ✅ Pass |
| TS-285 | Text filter persistent across resume | ✅ Pass |

---

*Soak plan created 2026-05-16. Run via `bash docs/testing/soak/scripts/run-soak.sh --story=SS-001`.*
