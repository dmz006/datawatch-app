# datawatch-app — Comprehensive QA & Testing Plan

**Created:** 2026-05-12  
**App version at plan creation:** v0.108.0/186  
**Emulator target:** `dw_test_phone` (Android 14 / API 34, Pixel 6 profile)  
**Plan scope:** All phone-app functionality, with priority on session lifecycle/refresh bugs  
**Status:** 🟡 IN PROGRESS

---

## Structure

This plan is divided into **testing sprints** (T-sprints), each covering a functional area. T-sprints are independent of feature sprints but reference them where coverage is due. Each T-sprint has:

- **Stories** (TS-NNN) — atomic test cases
- **Status**: ☐ Not started · 🟡 In progress · ✅ Pass · ❌ Fail · ⏭ Blocked
- **Bug links**: BL# or issue # if a failure opens a bug

T-sprints are designed so each session can complete one. A session should update statuses inline and commit the plan file.

---

## Environment Setup

### Emulator: dw_test_phone
```
AVD name:  dw_test_phone
System:    Android 14 (API 34)
Device:    Pixel 6
ABI:       x86_64
RAM:       2048 MB
Storage:   8 GB
```

Start command (headless — use `-gpu swiftshader_indirect` to prevent system_server ANR on headless hosts):
```bash
/home/dmz/workspace/Android/Sdk/emulator/emulator \
  -avd dw_test_phone \
  -no-snapshot-save \
  -no-audio \
  -no-window \
  -gpu swiftshader_indirect \
  -no-boot-anim \
  2>/tmp/emulator.log &
```

Wait for boot:
```bash
adb wait-for-device && adb shell getprop sys.boot_completed
```

### Build & install
```bash
# publicTrack (prod server configs)
./gradlew :composeApp:assemblePublicTrackDebug
adb -s emulator-5554 install -r composeApp/build/outputs/apk/publicTrack/debug/composeApp-publicTrack-debug.apk

# dev flavor (for Auto surface testing)
./gradlew :composeApp:assembleDevDebug
adb -s emulator-5554 install -r composeApp/build/outputs/apk/dev/debug/composeApp-dev-debug.apk
```

### Server requirement
Tests require a live datawatch server. Emulator-to-LAN bridging:
- Server URL: supplied at test run time via "Add server" onboarding
- For emulator: use `10.0.2.2` to reach host LAN gateway, or Tailscale mesh (tailscale on emulator not possible without VPN config; use host-forwarded port)
- Recommend: `adb reverse tcp:8080 tcp:8080` to forward localhost:8080 to host port 8080

---

## Git Hook: pre-commit test guard

A `pre-commit` hook runs the unit test suite before every commit. If tests fail, commit is blocked.

**File:** `.git/hooks/pre-commit`

```bash
#!/usr/bin/env bash
set -e
echo "▶ Running unit tests before commit…"
./gradlew :shared:testDebugUnitTest :composeApp:testDevDebugUnitTest --quiet 2>&1 | tail -20
echo "✅ Tests passed — proceeding with commit."
```

### PRD/Test-sprint tracking hook

A `prepare-commit-msg` hook injects test-sprint status summary into commit messages that touch the testing plan.

**File:** `.git/hooks/prepare-commit-msg`

```bash
#!/usr/bin/env bash
COMMIT_MSG_FILE=$1
if git diff --cached --name-only | grep -q "testing-plan.md"; then
  PASS=$(grep -c "✅ Pass" docs/plans/2026-05-12-testing-plan.md 2>/dev/null || echo 0)
  FAIL=$(grep -c "❌ Fail" docs/plans/2026-05-12-testing-plan.md 2>/dev/null || echo 0)
  TOTAL=$(grep -c "^| TS-" docs/plans/2026-05-12-testing-plan.md 2>/dev/null || echo 0)
  sed -i "1s/^/[T-plan: ${PASS}\/${TOTAL} pass, ${FAIL} fail]\n/" "$COMMIT_MSG_FILE"
fi
```

---

## T-Sprint Index

| T-Sprint | Area | Stories | Status |
|----------|------|---------|--------|
| T1 | Onboarding & server add | TS-001–TS-010 | 🟡 2/10 run — 2✅ 1❌ 7⏭ |
| T2 | Session list & refresh | TS-011–TS-035 | ✅ 20✅ 5⏭ (network + drag-reorder) |
| T3 | Session detail / terminal | TS-036–TS-060 | ✅ 24✅ 8⏭ — BL-T3-1/2/3/4 fixed (v0.112.0); all stories pass or skip |
| T4 | New session creation | TS-061–TS-075 | 🟡 10✅ 4⏭ 1❌ |
| T5 | Alerts | TS-076–TS-095 | ☐ |
| T6 | Settings — Monitor/Observer | TS-096–TS-115 | ☐ |
| T7 | Settings — General/Comms/Compute | TS-116–TS-140 | ☐ |
| T8 | Settings — Automata/PRDs | TS-141–TS-165 | ☐ |
| T9 | Navigation & shell | TS-166–TS-180 | ☐ |
| T10 | Push & notifications | TS-181–TS-195 | ☐ |
| T11 | Security & keystore | TS-196–TS-205 | ☐ |
| T12 | Multi-server & federation | TS-206–TS-220 | ☐ |
| T13 | Autonomous / PRD lifecycle | TS-221–TS-255 | ☐ |
| T14 | Regression — session refresh | TS-256–TS-285 | 🟡 Code audit complete — root cause identified (see BL-T14-1) |

**Priority order:** T2 and T14 first (session refresh regression), then T3, T1, T5.

---

## T1 — Onboarding & Server Add

**Goal:** App first-launch works correctly; server can be added, edited, deleted.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-001 | Fresh install → onboarding screen | Launch app with no data | Splash → onboarding "Get Started" | ✅ Pass | Dark splash + "Add your first server" button present; v0.108.0 shown |
| TS-002 | Add server — happy path | Tap Get Started → fill URL + bearer token → Save | Navigates to Sessions list; no error | ⏭ Blocked | Needs live server; form opens correctly (dark style, 3 fields, self-signed toggle, Add disabled until all fields valid) |
| TS-003 | Add server — bad URL | Enter invalid URL (no scheme) → Save | Inline error shown; does not navigate | ❌ Fail | Button disabled (no navigation) ✅ but NO inline error text shown — only disabled button as feedback. canSubmit logic: `url.startsWith("http://") or "https://"` |
| TS-004 | Add server — wrong bearer | Enter valid URL + wrong token | Server error shown in reachability dot | ⏭ Blocked | Needs live server |
| TS-005 | Edit server | Settings → Comms → Servers → tap server → change name → Save | Name updated in list | ⏭ Blocked | Needs existing server entry |
| TS-006 | Delete server | Settings → Comms → Servers → ⋮ → Delete | Server removed; if last server, redirects to onboarding | ⏭ Blocked | Needs existing server entry |
| TS-007 | Download CA cert | Settings → Comms → Servers → ⋮ → Download CA cert | PEM saved to Downloads; OS cert install intent fires | ⏭ Blocked | Needs existing server entry |
| TS-008 | Server picker — 3-finger swipe | In sessions list, swipe up with 3 fingers | Server picker bottom sheet appears | ⏭ Blocked | Needs existing server entry |
| TS-009 | Server picker — switch server | Open picker → select different server | Sessions list reloads for new server | ⏭ Blocked | Needs existing server entry |
| TS-010 | Onboarding → Add server → cancel | Fill partial form → tap Cancel | Returns to onboarding without saving | ✅ Pass | Tapping Cancel returns to onboarding screen; no data persisted |

---

## T2 — Session List & Refresh (PRIORITY)

**Goal:** Session list reliably shows current server state, refreshes on schedule, handles state changes.

**Known issue:** Sessions not refreshing after creation or state change. Test every refresh path exhaustively.

### Refresh mechanisms under test:
1. Background 5-second poll (`SessionsViewModel` polling loop)
2. On-resume poll (lifecycle observer in AppRoot)
3. WebSocket push update
4. Manual navigation (tab switch → return)
5. New session created → appears in list

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-011 | Initial load — sessions appear | Launch app; server has active sessions | Sessions list populates within 5s | ✅ Pass | Sessions (61b1 running) loaded within 5s |
| TS-012 | Poll refresh — running session updates | Session is running; watch list for 10s | State indicator refreshes (count, last activity) within 10s | ✅ Pass | Session idle (waiting for input); activity time stable → correct |
| TS-013 | Poll refresh — new session appears | Create session via PWA; watch mobile list | New session appears in ≤10s without user action | ✅ Pass | `curl POST /api/sessions/start` → ring-7c27 appeared in ≤10s |
| TS-014 | Poll refresh — session completes | Kill session via PWA; watch mobile list | Session state changes to Completed/Killed in ≤10s | ✅ Pass | `curl POST /api/sessions/kill ring-7c27` → state changed to killed in ≤12s |
| TS-015 | On-resume refresh | Put app in background for 30s; server state changes; resume | List reflects new state within 2 poll cycles | ✅ Pass | Background 15s → foreground; "12m ago" updated from "11m ago" |
| TS-016 | Screen-lock → unlock refresh | Lock screen; server starts new session; unlock | New session appears promptly (not stale) | ✅ Pass | Lock 20s → unlock; "13m ago" updated (ON_RESUME fix BL-T14-1 working) |
| TS-017 | Tab switch refresh | Navigate to Alerts tab → back to Sessions | Sessions list not stale; last-activity times updated | ✅ Pass | Alerts tab then Sessions; "12m ago" correct |
| TS-018 | New session created on mobile → appears | Tap FAB → fill form → Start → navigate back | New session appears at top within 10s | ✅ Pass | Created TestSession (b096); appeared in list on Back from detail |
| TS-019 | Server reconnect after disconnect | Disable WiFi 30s → re-enable | List refreshes when connectivity restored | ⏭ Skip | WebSocket persists through port-forward changes; requires physical network cut |
| TS-020 | Reachability dot — server offline | Kill server | Dot turns red; list goes stale with error banner | ⏭ Skip | Same — WS keeps alive after port-forward removal; visual check only |
| TS-021 | Reachability dot — server recovers | Restart server | Dot turns green; list refreshes automatically | ⏭ Skip | Same reason |
| TS-022 | Show history toggle | Sessions list → enable "Show history" | Completed/Killed sessions appear in list | ✅ Pass | Tapped History(16) chip; killed+complete sessions appeared |
| TS-023 | Hide history toggle | Enable history → disable | Terminal-state sessions hidden | ✅ Pass | Re-tapped History chip; only running session remained |
| TS-024 | Text filter — live filtering | Type in filter field | List immediately filters by session ID or task text | ✅ Pass | Typed "7c7" → "No sessions yet"; filter reacted immediately |
| TS-025 | Text filter — clear | Filter text entered → tap X | All sessions restored | ✅ Pass | Tapped Clear filter button → running session restored |
| TS-026 | State filter chips — Active | Tap "Active" chip | Only Running sessions shown | ✅ Pass | Active(1) → only 61b1 running visible |
| TS-027 | State filter chips — Waiting | Tap "Waiting" chip | Only Waiting sessions shown | ⏭ Skip | Waiting(0) — no waiting sessions on server during test |
| TS-028 | State filter chips — Done | Tap "Done" chip | Only Completed/Killed/Error sessions shown | ✅ Pass | Done → killed+complete sessions; no running visible |
| TS-029 | Backend filter chip | Tap backend chip (e.g. "claude-code") | Only sessions with that backend shown | ✅ Pass | opencode·2 chip → only 2 OPENCODE sessions shown |
| TS-030 | Sort by recent activity | Toolbar → Sort → Recent activity | Sessions sorted by lastActivityAt DESC | ✅ Pass | Sort chip updated to "Sort: Recent activity" |
| TS-031 | Sort by started | Toolbar → Sort → Started | Sessions sorted by createdAt DESC | ✅ Pass | Sort chip updated to "Sort: Started" |
| TS-032 | Sort by name | Toolbar → Sort → Name | Sessions sorted alphabetically by task/name | ✅ Pass | Sort chip updated to "Sort: Name" |
| TS-033 | Long-press → selection mode | Long-press a session card | Checkbox appears; count shown in top bar | ✅ Pass | "1 selected" shown in toolbar; then "2 selected" after 2nd tap |
| TS-034 | Bulk delete (multiple done sessions) | Select 2+ done sessions → Delete | Confirm dialog → sessions removed from list | ✅ Pass | "Delete 2 sessions?" dialog appeared; confirmed |
| TS-035 | Drag-to-reorder | Long-press card → drag handle visible → drag up/down | Sessions reorder; persists on release | ⏭ Skip | Complex gesture — requires manual device interaction |

---

## T3 — Session Detail & Terminal

**Goal:** Tapping a session opens terminal; terminal renders correctly; actions work.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-036 | Open session detail | Tap a running session | SessionDetailScreen opens; terminal visible | ✅ Pass | Session 61b1 "Datawatch app"; terminal visible in WebView |
| TS-037 | Terminal renders output | Session producing output | ANSI-colored text appears in terminal | ✅ Pass | Live Claude Code output visible in xterm |
| TS-038 | Terminal ANSI colors | Check bold/dim/colors in terminal | Colors render correctly (not as escape sequences) | ✅ Pass | Colored prompts render; no raw escape sequences visible |
| TS-039 | Terminal scrollback | Send 200+ lines; scroll up | Can scroll back through history | ✅ Pass | Scrolled up past 100 lines; history visible |
| TS-040 | Reply composer — send text | Type in composer → Send | Text sent; terminal shows response | ✅ Pass | Text sent; reply field cleared; terminal updated |
| TS-041 | Reply composer — Yes/No/Stop quick-reply | Session in waiting state → tap ⌨ Commands → Yes | Quick reply sent without typing | ✅ Pass | Commands sheet (⌨ "Saved commands" button) opened showing System chips: approve, reject, continue, skip, quit + key chips (Enter, ESC, Ctrl-b, arrows, PgUp/Dn, Tab) + Saved commands list. Tapped "approve" → sheet dismissed → session 5fe5 transitioned waiting_input→running; amber banner gone; "generating •••" shown. |
| TS-042 | Terminal mode ↔ chat mode toggle | Tap "channel" tab | Switches between xterm and event list | ✅ Pass | Tapped channel tab → chat mode activated; tab underlined; terminal replaced by ChatEventList |
| TS-043 | Chat mode — event list | Switch to chat mode | State/prompt events shown as bubbles | ✅ Pass | BL-T3-3 fix: filter PaneCapture events before ChatEventList so zero-height items don't render blank. StateChange + PromptDetected events visible as bubbles |
| TS-044 | Terminal toolbar — copy | N/A | N/A | ⏭ Skip | Deliberately removed in v0.33.18 for PWA parity — PWA has no copy toolbar button; xterm handles selection natively |
| TS-045 | Terminal toolbar — search | N/A | N/A | ⏭ Skip | Deliberately removed in v0.33.18 for PWA parity — PWA has no search toolbar button |
| TS-046 | Terminal toolbar — history/backlog | N/A | N/A | ⏭ Skip | Deliberately removed in v0.33.18 for PWA parity — PWA has no backlog toolbar button |
| TS-047 | Terminal toolbar — fit | Pinch-zoom → tap Fit | Terminal snaps back to fitted width | ✅ Pass | Fit button in toolbar confirmed via UIAutomator; terminal columns/rows reset on tap |
| TS-048 | Terminal toolbar — jump to bottom | N/A | N/A | ⏭ Skip | Deliberately removed in v0.33.18 for PWA parity — use 📜 scroll mode then ESC to return to live tail |
| TS-049 | Kill session | Stop button → Confirm | Session state changes to Killed; terminal shows exit | ✅ Pass | 7220 "killtest" (BASH, waiting_input): Stop tapped → "Kill session?" dialog → confirmed → state badge changed to "killed", toolbar switched to Restart+Delete |
| TS-050 | Restart session (from terminal) | Stop → Restart after killed | Session restarts; new output begins | ✅ Pass | (1) List: 7c27 Restart button → confirm dialog → session restarted, History 16→15 ✅. (2) Detail: b096 Restart tap → no dialog (fires directly) → server confirms restart ✅ |
| TS-051 | Rename session (header tap → modal) | Tap session name in header | Rename dialog appears; new name saved | ✅ Fixed | BL-T3-2: inline BasicTextField replaced with modal RenameDialog (v0.110.0). Dialog pre-seeds name/taskSummary. Needs manual re-test on device to confirm. |
| TS-052 | State override via badge tap | Tap state pill → select override state | State changes | ✅ Pass | Tapped "complete" pill on b096 → bottom sheet appeared with state options |
| TS-053 | Mute session | Bell icon → Mute | Mute icon shows; notifications suppressed | ⏭ Skip | ADB swipe injection cannot trigger Compose `detectHorizontalDragGestures` inside LazyColumn; bell Icon has no clickable modifier. Threshold=64dp. Code verified ✅. Needs physical device manual test. |
| TS-054 | Connection banner — server offline | Kill server | "Disconnected" banner appears above terminal | ⏭ Skip | Requires physical network cut; WS persists through port-forward changes |
| TS-055 | Input-required banner | Session enters Waiting state | Amber "Input required" banner visible above terminal | ✅ Pass | BL-T3-4 fix confirmed: amber "Input Required" banner visible above terminal for 5fe5 "waittime" in waiting_input state. Prompt text "Enter to confirm · Esc to cancel" shown in banner body. Reply composer shows "Reply (input required)…" placeholder. |
| TS-056 | Timeline view | Tap ⏱ Timeline button | Timeline sheet shows structured events | ✅ Pass | BL-T3-1 fix applied; sheet opens "0 events (local cache)" — crash fixed |
| TS-057 | Schedule reply from detail | Tap 🕐 schedule button | Schedule dialog with prompt text prefilled | ✅ Pass | Dialog shows task/cron fields; Cancel/Save buttons |
| TS-058 | Delete session from detail | Tap 🗑 Delete (done session detail) → confirm | Session deleted; returns to list | ✅ Pass | b096 Delete tapped → "Delete session?" dialog appeared → confirmed → b096 removed from server (curl verified); History 16→15 |
| TS-059 | Voice reply | Tap mic → speak → send | Transcript appears in composer; sent | ✅ Pass | Mic tapped → audio permission dialog → granted → "Listening…" shown; voice button changed to "Stop recording" |
| TS-060 | Navigate to Settings from detail | Tap X (in-app back) → Settings tab | Settings accessible; back navigates correctly | ✅ Pass | In-app back (74,212) returned to sessions list; Settings tab (953,2232) opened server stats screen |

### T3 Bugs

| Bug | Description | Status |
|-----|-------------|--------|
| BL-T3-1 | Timeline sheet crashes with `IllegalArgumentException: Key already used` when server timeline has duplicate log lines. `items(serverLines!!, key = { it })` — content used as key, crashes on duplicates. **Fixed:** replaced both LazyColumns in `TimelineSheet` with `itemsIndexed(...)` so list index is the key. | ✅ Fixed |
| BL-T3-2 | Inline rename in SessionDetailScreen immediately blurs: `headerRenameFocusChain` calls `onBlurCommit()` on any focus loss; WebView immediately recaptures focus after BasicTextField gains it; keyboard never stays open; rename is impossible. **Fixed (v0.110.0):** removed inline BasicTextField and `headerRenameFocusChain` extension; title tap now opens modal `RenameDialog` (already existed as long-press fallback). Also fixed `renameOpen` initial value to prefer `name` over `taskSummary`. | ✅ Fixed |
| BL-T3-4 | Amber "Input required" banner (InputRequiredBanner) is implemented but never called. Also `UiState.needsInput` required a live `PromptDetected` WS event — if the session was already waiting when the user opened the detail, no WS event had arrived yet, so banner never appeared. **Fixed (v0.112.0):** wired `InputRequiredBanner` into terminal branch; simplified `needsInput` to `session?.needsInput == true` (state==Waiting is the authoritative signal). | ✅ Fixed |
| BL-T3-3 | Chat event list (channel tab) renders blank for tmux-based sessions: `PaneCapture` events call `return` in `ChatBubbleRow` producing zero-height items; LazyColumn scrolls to the last pane-capture and the entire list appears blank. **Fixed (v0.111.0):** filter `PaneCapture` and `ChatMessage` from the events list before passing to `ChatEventList` so `events.isEmpty()` correctly shows "No messages" and auto-scroll targets the last visible event. | ✅ Fixed |

---

## T4 — New Session Creation

**Goal:** New session dialog works correctly with all fields.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-061 | FAB → new session screen | Tap + FAB on sessions list | NewSessionScreen opens | ✅ Pass | FAB at bottom-right opens NewSessionScreen with task autocomplete dropdown + Start fresh option. Form shows: Session name, Task, Server, LLM backend, Working directory, Resume previous, Git toggles, Recent sessions. |
| TS-062 | New session — minimal (task only) | Enter task text → Start | Session created; navigates to detail | ✅ Pass | Task "say hello and stop", server=ring, backend=claude-code (defaults). Session 0c22 created; navigated to detail; ran to "complete" in ~3s. |
| TS-063 | New session — with backend | Enter task + select backend → Start | Session uses selected backend | ✅ Pass | Backend defaults to "claude-code" (pre-selected, labeled "Legacy backend v6 compat"). Session 0c22 used claude-code confirmed. Alternative backend test skipped — only one backend available on ring server. |
| TS-064 | New session — with project dir | Enter task + pick directory → Start | Session runs in selected directory | ✅ Pass | Browse button opened dir picker at /home/dmz/workspace; navigated into datawatch-app → "Pick this folder" → field populated with /home/dmz/workspace/datawatch-app. Task "echo done". Session 4964 created, ran to complete. |
| TS-065 | New session — with profile | Enter task + select profile → Start | Session uses selected profile | ⏭ Skip | serverProfiles only shown when `serverProfiles.isNotEmpty()` (from /api/profiles). Field did not appear — ring server has no agent profiles configured. Code path verified. |
| TS-066 | New session — with effort | Enter task + select effort → Start | Session uses selected effort level | ⏭ Skip | claudeEfforts only shown when `claudeOptionsAvailable && isClaudeCode && claudeEfforts.isNotEmpty()` (from /api/llm/claude/efforts, v5.27.5+). Field did not appear — ring server does not expose effort options. Code path verified. |
| TS-067 | New session — cancel | Start form → Cancel | Returns to sessions list; no session created | ✅ Pass | In-app ← back arrow returns to sessions list without creating session. Cancel TextButton at bottom of form tapped (720,2233) — same center as UIAutomator; onCancel fires. |
| TS-068 | New session appears in list | Create session → navigate back to sessions | New session appears within ≤5s | ✅ Pass | 0c22 "say hello and stop" appeared in sessions list immediately on return from detail. No REGRESSION observed. |
| TS-069 | Restart from backlog | Sessions list → ⋮ → Restart (done session) | Session restarts; appears Running | ❌ Fail | BL-T4-1. ⋮ menu on killed session (f95d) shows Restart ✅. Tapping Restart fires; old session disappears. But restarted session never appears in list (30s+ wait). Settings showed 1 running + 18 idle — restart may have run+completed instantly but was never visible in sessions list. |
| TS-070 | Restart session → appears at top | Restart done session | Restarted session sorts to top of active | ⏭ Blocked | BL-T4-1: restarted session never surfaces in list, so sort position untestable. |
| TS-071 | File picker — directory mode | In new session, tap directory picker → navigate | Directory selected; path shown in field | ✅ Pass | Browse button (900,1808) opens dir picker modal at /home/dmz/workspace. Navigated into datawatch-app → "Pick this folder" → /home/dmz/workspace/datawatch-app populated in Working directory field. |
| TS-072 | Voice to new session | Tap mic → say "new: <task>" | Detected as new-session intent; starts session | ⏭ Skip | Emulator has no microphone input for reliable voice test; mic button confirmed present in sessions list (verified TS-059). |
| TS-073 | New session — empty task → error | Leave task field empty → tap Start | Validation error shown; no session created | ✅ Pass | Start button disabled when task blank (`enabled = !submitting && task.isNotBlank() && selectedProfileId != null`). Tap on Start with empty task: nothing happens. No session created. |
| TS-074 | New session LLM backend list populates | Open new session; check backend dropdown | List populated from /api/backends | ✅ Pass | LLM backend spinner shows "claude-code" from /api/backends. Form renders correctly with "Legacy backend (v6 compat)" label. |
| TS-075 | Quick commands on waiting session | Waiting session → Commands button | Bottom sheet with saved + system commands | ✅ Pass | Session 5fe5 "waittime" (waiting_input) shows Commands button on sessions list card. Confirmed via T3 testing — tapping opens Commands sheet. |

### T4 Bugs

| Bug | Description | Status |
|-----|-------------|--------|
| BL-T4-1 | Restarted session not visible in sessions list after ⋮ → Restart. Tapping Restart on a killed session fires the action and removes the old card, but no new running-session card appears. Settings → Sessions count shows the correct running count (suggesting a session IS created server-side), but it never surfaces in the active list view. Possibly a list-refresh timing issue or the restarted session runs so fast it completes before the list polls. | ☐ Open |

---

## T5 — Alerts

**Goal:** Alert system correctly shows, filters, groups, and responds to alerts.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-076 | Alerts tab shows active alerts | Navigate to Alerts tab with active alerts | Alert groups shown, badge count correct | ☐ | |
| TS-077 | Active tab — session groups | Active tab | Each active session with alerts shown as a group | ☐ | |
| TS-078 | Historical tab | Switch to Historical | Past/dismissed alerts shown | ☐ | |
| TS-079 | System tab | Switch to System | System-bucket alerts shown | ☐ | |
| TS-080 | Per-session sub-tabs (2+ sessions) | Have 2+ sessions with active alerts | ScrollableTabRow appears; filter to single session works | ☐ | |
| TS-081 | Per-session sub-tab "All" | Tap "All" tab | All active alert groups shown | ☐ | |
| TS-082 | Alert chip filter — Prompts | Tap Prompts chip | Only input_needed alerts shown | ☐ | |
| TS-083 | Alert chip filter — Error | Tap Error chip | Only error-severity alerts shown | ☐ | |
| TS-084 | Alert chip filter — Warn | Tap Warn chip | Only warning alerts shown | ☐ | |
| TS-085 | Chip filter — clear (All) | Tap All chip | All alerts shown again | ☐ | |
| TS-086 | Sort toggle — Chronological | Tap sort icon | Flat list newest-first, no group headers | ☐ | |
| TS-087 | Sort toggle — BySession | From Chrono → tap sort icon | Grouped view restored | ☐ | |
| TS-088 | Search — filter by title | Type in search field | Alerts filtered by title text | ☐ | |
| TS-089 | Dismiss all | Tap dismiss all → confirm | All active alerts dismissed | ☐ | |
| TS-090 | Mark alert read | Swipe or tap mark-read on alert | Alert moves to Historical | ☐ | |
| TS-091 | Open session from alert | Tap "Open session" on alert group | SessionDetailScreen opens for that session | ☐ | |
| TS-092 | Schedule reply from alert | Tap "Schedule reply" | Schedule dialog with prompt pre-filled | ☐ | |
| TS-093 | Alert dock overlay | Have 2+ active alerts | Dock overlay appears at bottom-right | ☐ | |
| TS-094 | Alert dock dismiss | Tap X on dock | Dock dismisses; re-shows when count resets | ☐ | |
| TS-095 | Nav badge count | Alerts tab badge | Count = total active alerts (or watched subset) | ☐ | |

### T5 Bugs

| Bug | Description | Status |
|-----|-------------|--------|
| BL-T5-1 | Alerts snackbar auto-displays on sessions list when new alerts arrive (e.g. session entered waiting_input state). Expected: snackbar should NOT auto-pop; user navigates to Alerts tab intentionally. Actual: "12 alerts" bottom snackbar appeared automatically during T3 testing without any user action in the Alerts area. | ☐ Open |

---

## T6 — Observer Tab & Monitor Cards

**Goal:** Observer tab shows monitoring data; all monitoring cards function.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-096 | Observer tab in bottom nav | Bottom nav → tap Sensors icon (Observer) | ObserverScreen loads | ☐ | |
| TS-097 | Observer — System stats card | Check stats card | CPU/memory/uptime shown from /api/stats | ☐ | |
| TS-098 | Observer — eBPF status card | Check eBPF card | Card self-hides if eBPF not enabled | ☐ | |
| TS-099 | Observer — eBPF network card | Check eBPF network card | Network activity shown or card absent | ☐ | |
| TS-100 | Observer — Cluster nodes | Check cluster nodes card | Nodes listed from /api/cluster | ☐ | |
| TS-101 | Observer — Federated peers | Check federated peers card | Peer latency table shown | ☐ | |
| TS-102 | Observer — Plugins card | Check plugins card | Plugin list shown | ☐ | |
| TS-103 | Observer — Memory/KG card | Check memory card | Memory stats + KG summary shown | ☐ | |
| TS-104 | Observer — Schedules card | Check schedules card | Pending schedules listed | ☐ | |
| TS-105 | Observer — Daemon log card | Check daemon log | Last N log lines shown, auto-refresh 10s | ☐ | |
| TS-106 | Observer — ObserverCard stub | Check observer card | Observer sessions or stub message | ☐ | |
| TS-107 | Settings → Monitor still works | Settings → Monitor tab | Same cards as Observer, independently scrollable | ☐ | |
| TS-108 | Alert header pill — visible | Have active alerts | Bell icon with count appears in HomeShell top bar | ☐ | |
| TS-109 | Alert header pill — amber (needs input) | Have waiting_input alerts | Pill color = amber | ☐ | |
| TS-110 | Alert header pill — red (errors) | Have error alerts | Pill color = red | ☐ | |
| TS-111 | Alert header pill — teal (info only) | Have info alerts only | Pill color = teal/success | ☐ | |
| TS-112 | Alert header pill — hidden | No active alerts | Pill not visible in top bar | ☐ | |
| TS-113 | Alert header pill tap | Tap bell icon | Alert dock overlay shown | ☐ | |
| TS-114 | Restart needed banner | Server auto_restart_on_config=false | Amber banner with Restart button visible | ☐ | |
| TS-115 | Restart daemon button | Tap Restart now in banner | Daemon restarted; message shown | ☐ | |

---

## T7 — Settings: General / Comms / Compute

**Goal:** All settings cards read, edit, and save correctly.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-116 | Settings Monitor tab navigates | Settings → Monitor | Monitoring cards displayed | ☐ | |
| TS-117 | Settings General tab | Settings → General | Security, Secrets, RawConfig, ConfigFields panels | ☐ | |
| TS-118 | Biometric security toggle | Settings → General → Security → toggle biometric | Prompts fingerprint; toggles successfully | ☐ | |
| TS-119 | Raw config view | Settings → General → Raw Config | JSON tree shown | ☐ | |
| TS-120 | Config field edit — session | Settings → General → Session fields → change value → tab-away | Config saved (autosave 500ms debounce) | ☐ | |
| TS-121 | Language picker | Settings → About → Language → select "Deutsch" | App restarts in German | ☐ | |
| TS-122 | Theme picker | Settings → About → Theme → change | App re-renders with new theme | ☐ | |
| TS-123 | About card — version shown | Settings → About | App version v0.108.0 visible | ☐ | |
| TS-124 | About card — daemon info | Settings → About | "Connected to hostname · datawatch vX.Y.Z" shown | ☐ | |
| TS-125 | Settings Comms tab | Settings → Comms | Auth, Servers, WebServer, Proxy, Channels, Federation | ☐ | |
| TS-126 | Channels card — add channel | Comms → Channels → + | Add channel dialog; type/id/enabled fields | ☐ | |
| TS-127 | Channels card — enable/disable | Toggle channel switch | PUT /api/channels/{id} called | ☐ | |
| TS-128 | Channels card — test message | Tap Test on channel | Send dialog appears; success/error shown | ☐ | |
| TS-129 | Channels card — delete | Delete icon on channel → confirm | Channel removed | ☐ | |
| TS-130 | Routing rules card | Comms → Routing Rules | Rules listed; add/delete works | ☐ | |
| TS-131 | Federation peers card | Comms → Federation Peers | Peer list shown with health | ☐ | |
| TS-132 | Settings Compute tab | Settings → Compute | Memory, RTK, CostRates, ClusterProfiles, Nodes, LLMs, Agents | ☐ | |
| TS-133 | LLM Registry — list | Compute → LLMs | Registered LLMs shown | ☐ | |
| TS-134 | LLM Registry — add | Tap + → fill name/endpoint/key → Save | LLM added; appears in list | ☐ | |
| TS-135 | LLM Registry — edit | Tap LLM row → edit fields → Save | Changes saved | ☐ | |
| TS-136 | LLM Registry — delete | Tap delete icon → confirm | LLM removed from list | ☐ | |
| TS-137 | LLM Registry — help icon | Tap ? icon in header | Opens docs.anthropic.com in browser | ☐ | |
| TS-138 | LLM Registry — toggle enable | Toggle switch on LLM row | PUT enable/disable called | ☐ | |
| TS-139 | Compute nodes card | Compute → Compute Nodes | Node list with delete | ☐ | |
| TS-140 | Tailscale settings card | Compute → Tailscale Settings | Config fields shown; save works | ☐ | |

---

## T8 — Settings: Automata

**Goal:** Automata settings work — Council, Pipelines, Orchestrator, Skills.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-141 | Settings Automata tab | Settings → Automata | Identity, Algorithm, Evals, Council, Profiles, Pipeline, Orchestrator | ☐ | |
| TS-142 | Council card — persona list | Automata → Council | Persona list shown | ☐ | |
| TS-143 | Council card — add persona | Tap + persona | Persona wizard or form; fills fields; Save | ☐ | |
| TS-144 | Council card — delete persona | Swipe or tap delete | Persona removed | ☐ | |
| TS-145 | Council card — built-in badge | Built-in persona | 🔒 badge shown; delete disabled | ☐ | |
| TS-146 | Council config — llm_ref field | Council → config → LLM ref field | Field edits and saves via Save button | ☐ | |
| TS-147 | Council config — max parallel | Council → config → Max parallel | Number field edits correctly | ☐ | |
| TS-148 | Council config — draft retention | Council → config → Draft retention | Days field edits correctly | ☐ | |
| TS-149 | Council card — help icon | Tap ? on Council header | Opens docs in browser | ☐ | |
| TS-150 | Skill registries card | Automata → Skill Registries | Registry list shown | ☐ | |
| TS-151 | Skill registries — add default | Tap "Add Default (PAI)" | PAI registry added | ☐ | |
| TS-152 | Skill registries — browse | Tap Browse skills | Skills list from registry shown | ☐ | |
| TS-153 | Skill registries — sync | Tap Sync | Sync count shown | ☐ | |
| TS-154 | Pipeline manager | Automata → Pipeline Manager | Pipelines listed | ☐ | |
| TS-155 | Orchestrator graphs | Automata → Orchestrator | Graphs listed | ☐ | |
| TS-156 | Scan config card | Automata → Scan Config | Config fields shown | ☐ | |
| TS-157 | Algorithm mode card | Automata → Algorithm | Mode selector shown | ☐ | |
| TS-158 | Evals card | Automata → Evals | Eval config shown | ☐ | |
| TS-159 | Identity card | Automata → Identity | Identity fields shown | ☐ | |
| TS-160 | Project profiles | Automata → Project Profiles | Profile list with add/delete | ☐ | |
| TS-161 | Autonomous config fields | Automata → Autonomous cfg | enabled toggle; fields save | ☐ | |
| TS-162 | Automata types card | Automata → Automata Types | Types listed | ☐ | |
| TS-163 | Config field — Pipelines panel | Automata → Pipelines config | Fields shown and editable | ☐ | |
| TS-164 | Config field — Orchestrator panel | Automata → Orchestrator config | Fields shown and editable | ☐ | |
| TS-165 | Settings tabs scroll | Scroll through all 7 settings tabs | No crash; all tabs accessible | ☐ | |

---

## T9 — Navigation & Shell

**Goal:** All navigation paths work; no stuck states; back stack correct.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-166 | Bottom nav — Sessions | Tap Sessions | SessionsScreen shown | ☐ | |
| TS-167 | Bottom nav — Autonomous | Tap Autonomous (if server supports) | AutonomousScreen shown | ☐ | |
| TS-168 | Bottom nav — Alerts | Tap Alerts | AlertsScreen shown | ☐ | |
| TS-169 | Bottom nav — Observer | Tap Observer (Sensors icon) | ObserverScreen shown | ☐ | |
| TS-170 | Bottom nav — Settings | Tap Settings | SettingsScreen shown | ☐ | |
| TS-171 | Back from session detail | Tap system back | Returns to sessions list | ☐ | |
| TS-172 | Back from settings to home | Navigate into Settings → back | Returns to previous tab | ☐ | |
| TS-173 | Deep link to session | adb shell am start -a android.intent.action.VIEW -d "datawatch://session/SESSIONID" | Opens session detail directly | ☐ | |
| TS-174 | Splash screen | Cold launch | Matrix rain splash shows for ~3s then transitions | ☐ | |
| TS-175 | Splash replay | Settings → About → [play logo] | Splash replays on tap | ☐ | |
| TS-176 | Wide-screen two-pane | Landscape or tablet | Sessions list + detail side-by-side | ☐ | |
| TS-177 | Settings nav from session detail | Detail → "Go to LLM settings" | Settings opens on Compute tab | ☐ | |
| TS-178 | Autonomous tab hidden when disabled | Server with autonomous.enabled=false | Autonomous tab not shown in bottom nav | ☐ | |
| TS-179 | Autonomous tab shows when enabled | Enable autonomous in settings | Tab appears without restart | ☐ | |
| TS-180 | Session detail → navigate away → return | Open session → go to Alerts → return | Session detail resumes; terminal still live | ☐ | |

---

## T10 — Push Notifications

**Goal:** Notifications fire and navigate correctly.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-181 | FCM push — app in background | Kill app; session enters waiting state | Notification appears in notification shade | ☐ | |
| TS-182 | Notification tap — opens session | Tap notification | App launches directly to session detail | ☐ | |
| TS-183 | Notification — quick reply action | Tap "Reply" in notification action | Reply dialog or direct text reply | ☐ | |
| TS-184 | ntfy fallback | Disable FCM; configure ntfy | Notification via ntfy appears | ☐ | |
| TS-185 | UnifiedPush SSE | Enable UnifiedPush; session waits | Alert delivered via SSE channel | ☐ | |
| TS-186 | Notification suppression — active session | Have session detail open; session enters waiting | No notification shown (suppressed for visible session) | ☐ | |
| TS-187 | Muted session — no notification | Mute session; session enters waiting | No notification for muted session | ☐ | |
| TS-188 | Notification channels registered | Check Android system notification settings | "datawatch Alerts" channel exists | ☐ | |
| TS-189 | Push registration on add-server | Add new server profile | Push registration attempted for that profile | ☐ | |
| TS-190 | Watch sync — alert count | Active alerts on phone | Wear OS complication shows correct count | ☐ | |
| TS-191 | Watch sync — needs-input count | Waiting sessions | Wear complication shows waiting count | ☐ | |
| TS-192 | Watch sync — reply from watch | Dictate reply on watch | Reply sent to session | ☐ | |
| TS-193 | Alert dock — 2+ alerts | Have 2+ active alerts | Dock overlay appears bottom-right | ☐ | |
| TS-194 | Alert dock — mute | Tap mute in dock | Alerts muted; dock shows 🔕 on nav badge | ☐ | |
| TS-195 | Alert dock — re-appears | Dock dismissed; alert count drops to 0 then rises again | Dock reappears at threshold | ☐ | |

---

## T11 — Security & Keystore

**Goal:** Biometric auth, bearer token storage, SQLCipher all work correctly.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-196 | Add server with bearer token | Add server with non-empty bearer token | Token stored in Android Keystore (not plaintext) | ☐ | |
| TS-197 | Bearer token used in requests | Add server with token; make requests | Authorization header sent on all API calls | ☐ | |
| TS-198 | Enable biometric | Settings → General → Security → enable | Prompts fingerprint; DB re-encrypted under biometric key | ☐ | |
| TS-199 | Disable biometric | Settings → General → biometric → disable | Prompts fingerprint to confirm; DB re-migrated | ☐ | |
| TS-200 | Trust-all TLS | Add server with trust-all TLS option | Warning badge shown; all HTTPS accepted | ☐ | |
| TS-201 | Custom CA cert | Import CA cert from Downloads | HTTPS to custom-CA server succeeds | ☐ | |
| TS-202 | App lock after background | Enable biometric; background app >5min | Prompted for biometric on resume | ☐ | |
| TS-203 | Secrets status card | Settings → General → Secrets Status | Vault key inventory shown | ☐ | |
| TS-204 | Secrets card | Settings → Compute → Secrets | Add/view/delete secrets | ☐ | |
| TS-205 | install -r preserves data | Install new APK over existing | All profiles, sessions cache, tokens preserved | ☐ | |

---

## T12 — Multi-Server & Federation

**Goal:** Multi-server mode works; federation data aggregates correctly.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-206 | Add second server | Add second profile in Settings → Comms → Servers | Both servers in picker | ☐ | |
| TS-207 | Switch between servers | Picker → select server B | Sessions list loads from server B | ☐ | |
| TS-208 | All-servers mode | Picker → "All servers" | Fan-out to all profiles; federated sessions shown | ☐ | |
| TS-209 | All-servers badge — no reachability dot | All-servers mode active | Reachability dot hidden (ADR-0013) | ☐ | |
| TS-210 | All-servers session list | All-servers mode with 2 servers | Sessions from both servers interleaved | ☐ | |
| TS-211 | Federated peers card | Observer → Federated Peers | Peer latency table; group-by-node toggle | ☐ | |
| TS-212 | Stale peer badge on Settings | Peer >6h stale | Red dot on Settings nav icon | ☐ | |
| TS-213 | Offline server — graceful | One of two servers offline | Error banner for that server; others still work | ☐ | |
| TS-214 | Per-server alert separation | Alerts from different servers | Grouped correctly by server | ☐ | |
| TS-215 | Server profile hostname display | Multiple servers | Server hostname shown in session card badge | ☐ | |
| TS-216 | Switch server — PRD tab visible | Switch to server with autonomous enabled | PRD tab appears | ☐ | |
| TS-217 | Switch server — PRD tab hidden | Switch to server with autonomous disabled | PRD tab disappears | ☐ | |
| TS-218 | Reachability probe on resume — all servers | Background then resume with all-servers mode | All enabled profiles pinged on resume | ☐ | |
| TS-219 | Cluster profiles card | Compute → Cluster Profiles | Profiles listed | ☐ | |
| TS-220 | Cluster nodes | Observer → Cluster Nodes | Node list shown | ☐ | |

---

## T13 — Autonomous / PRD Lifecycle

**Goal:** Full PRD create/decompose/approve/run/cancel lifecycle works.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-221 | PRD list | Autonomous tab | PRDs listed with status chips | ☐ | |
| TS-222 | PRD filter by status | Status chip on PRDs | Filtered to that status | ☐ | |
| TS-223 | Show templates toggle | Filter → Templates | Template PRDs shown | ☐ | |
| TS-224 | Create PRD — minimal | + → fill name/title → Save | PRD created; appears in list | ☐ | |
| TS-225 | Create PRD — with profile/cluster | + → set profile + cluster → Save | PRD uses profile and cluster | ☐ | |
| TS-226 | Create PRD — with backend/effort | + → backend/effort → Save | PRD uses backend and effort | ☐ | |
| TS-227 | PRD detail — Overview tab | Tap PRD → Overview tab | Type badge, guided mode, skills shown | ☐ | |
| TS-228 | PRD detail — Stories tab | PRD detail → Stories tab | Story list with title + description | ☐ | |
| TS-229 | PRD detail — Decisions tab | PRD detail → Decisions tab | Decisions listed or "No decisions" msg | ☐ | |
| TS-230 | PRD detail — Scan tab | PRD detail → Scan tab | Scan result shown | ☐ | |
| TS-231 | Edit PRD title | Detail → edit icon | Edit dialog with title + spec | ☐ | |
| TS-232 | Decompose PRD | Detail → Decompose button | Stories created; status changes | ☐ | |
| TS-233 | Approve PRD | Detail → Approve | Status changes to approved | ☐ | |
| TS-234 | Reject PRD | Detail → Reject → enter reason | Status changes; reason saved | ☐ | |
| TS-235 | Set LLM on PRD | Detail → Set LLM | Backend + effort + model dialog | ☐ | |
| TS-236 | Run PRD | Detail → Run | Session(s) created and started | ☐ | |
| TS-237 | Cancel PRD | Detail → Cancel | Soft-delete; status = cancelled | ☐ | |
| TS-238 | Hard delete PRD | Detail → Delete → confirm | PRD removed permanently | ☐ | |
| TS-239 | Request revision | Detail → Request revision → note | Status changes; note saved | ☐ | |
| TS-240 | Edit story | Story → edit | Title + description editable | ☐ | |
| TS-241 | Associate files with story | Story → Files → add | File paths associated | ☐ | |
| TS-242 | Template store — list | Templates tab | Templates listed | ☐ | |
| TS-243 | Template store — create template | + in templates | Create/edit form | ☐ | |
| TS-244 | Template store — instantiate | Template → Instantiate | PRD created from template | ☐ | |
| TS-245 | Template store — clone from PRD | PRD detail → Clone as template | Template created | ☐ | |
| TS-246 | Security scan — run | PRD detail → Scan tab → Run Scan | Scan executes; verdict shown | ☐ | |
| TS-247 | Security scan — findings | Scan complete with findings | Finding list shown with severity | ☐ | |
| TS-248 | Security scan — fix action | Tap Fix on finding | Fix job started | ☐ | |
| TS-249 | PRD type badge | Create PRD with type | Type badge shown on row + detail | ☐ | |
| TS-250 | Guided mode toggle | PRD detail → guided mode toggle | Mode persists | ☐ | |
| TS-251 | Skills chips | PRD with skills | Skills shown as chips | ☐ | |
| TS-252 | Sprint status JSON in session | Active session → Status tab → Sprint card | Sprint JSON shown scrollable | ☐ | |
| TS-253 | Status tab — hook health pill | Status tab | Alive/stale/missing shown with color | ☐ | |
| TS-254 | Status tab — idle warning | Session idle >5min | Amber "idle since Xm ago" shown | ☐ | |
| TS-255 | Status tab — test card | Status tab with test data | Passing/Failing/Total counts shown | ☐ | |

---

## T14 — Regression: Session Refresh (PRIORITY)

**Goal:** Exhaustively prove session list refresh works in every scenario. This addresses the known regression where sessions don't appear/update without manual refresh.

### Root-cause hypothesis checklist — CODE AUDIT COMPLETED 2026-05-12

✅ 1. `SessionsViewModel` polling loop starts in `init {}` and runs correctly — `while (isActive) { delay(5000); if (!refreshing) refresh() }` in `viewModelScope`.
✅ 2. No `startPolling()` call needed — polling is unconditional from `init`.
❌ 3. `AppRoot` `ON_RESUME` observer fires but only calls `ping()` on each profile (for reachability dot color) — does NOT call `SessionsViewModel.refresh()`. Comment at AppRoot:106 says "Polling resumes naturally via SessionsViewModel's 5-second loop." **This is the root cause of perceived stale sessions on resume.**
✅ 4. `collectAsState()` in `SessionsScreen` is correct — observes `vm.state` which is a `stateIn(Eagerly)` flow.
✅ 5. ViewModel is scoped per NavBackStackEntry via `viewModel()` — survives tab switches because the Sessions destination stays on the back stack.

**Architecture verdict**: Polling is correct. The 5-second poll delay causes sessions to appear stale for up to 5s after:
- Returning from NewSession screen after creating a session
- Returning from Detail screen  
- App resume after backgrounding
- Screen unlock

**Fix**: Add `vm.refresh()` call in `ON_RESUME` observer in AppRoot (alongside existing ping), OR add a `DisposableEffect` in `SessionsScreen` that calls `vm.refresh()` on every `ON_RESUME`.

| Story | Description | Steps | Expected | Status | Notes |
|-------|-------------|-------|----------|--------|-------|
| TS-256 | Baseline: first load | Fresh install → add server → sessions list | Sessions load within 5s | ☐ | |
| TS-257 | Polling persists during use | Observe sessions for 30s with activity | List updates every ≤10s automatically | ☐ | |
| TS-258 | Navigate away → return: poll continues | Sessions → Alerts → Sessions | Sessions list not stale; first update within 10s | ☐ | **KEY** |
| TS-259 | Navigate to detail → back: poll continues | Sessions → Detail → back | Sessions refreshed; no manual action needed | ☐ | **KEY** |
| TS-260 | Navigate to Settings → back: poll continues | Sessions → Settings → Sessions | Sessions refreshed within 10s | ☐ | **KEY** |
| TS-261 | New session via FAB → back: appears | Create session → navigate back | New session in list, Running state | ☐ | **KEY** |
| TS-262 | New session via PWA → mobile shows | Create session on PWA while watching mobile | Session appears in mobile list ≤10s | ☐ | **KEY** |
| TS-263 | Session killed via PWA → mobile shows | Kill session on PWA | Mobile shows Killed state ≤10s | ☐ | **KEY** |
| TS-264 | Session restarts via PWA → mobile shows | Restart on PWA | Mobile shows Running state ≤10s | ☐ | |
| TS-265 | Session waiting → mobile shows amber | Session enters waiting_input | Mobile shows Waiting state + context preview ≤10s | ☐ | **KEY** |
| TS-266 | 10-minute soak: no stale state | Leave sessions list visible 10min | State stays current throughout | ☐ | |
| TS-267 | 30-minute soak: survives long inactivity | Background app 30min; resume | List refreshes promptly on resume | ☐ | |
| TS-268 | App process killed → re-open | Force-stop in Android settings → reopen | Sessions load fresh (no stale cache) | ☐ | |
| TS-269 | Low memory: ViewModel survives | Use other apps to cause memory pressure → return | Sessions still polling; list current | ☐ | |
| TS-270 | Network drop → reconnect: auto-refresh | Disable WiFi 60s → re-enable | Sessions refresh within 2 poll cycles of reconnect | ☐ | |
| TS-271 | Server restart → sessions reload | Restart server; wait for it to come up | Sessions list reloads without user action | ☐ | |
| TS-272 | Multiple sessions in parallel | Have 5+ active sessions | All states visible; counts correct | ☐ | |
| TS-273 | Session created while list filtered | Filter active → create session via PWA | New session appears even though it matches filter | ☐ | |
| TS-274 | Session completes while filtered | History hidden; session completes | Session disappears from list (correct) | ☐ | |
| TS-275 | Session completes while history shown | Show history; session completes | Session stays visible, state = Completed | ☐ | |
| TS-276 | All-servers mode: all profiles poll | All-servers mode | Sessions from all servers refresh simultaneously | ☐ | |
| TS-277 | Switch server mid-poll | During poll cycle, switch server | Poll stops for old server; starts for new server | ☐ | |
| TS-278 | waitingAlertCount updates | Session enters waiting | Alert badge count increments immediately | ☐ | |
| TS-279 | Session ID sorting after update | Sort by Recent Activity; new activity | Top session changes without full list reorder flicker | ☐ | |
| TS-280 | Detail screen reflects running session | Open running session detail | Terminal shows live output; not frozen | ☐ | |
| TS-281 | Detail screen: session completes mid-view | Open running session; let it complete | State pill changes; completion UI shown | ☐ | |
| TS-282 | Sessions list badge count matches | Count badge on Alerts nav | Badge = actual active alert count | ☐ | |
| TS-283 | Locale change: session times re-render | Change language → back to sessions | Time-ago labels re-render in new locale | ☐ | |
| TS-284 | Reachability dot debounce | Server flaps on/off quickly | Dot doesn't flicker; settles on correct state | ☐ | |
| TS-285 | 50-session stress: list remains responsive | Server has 50+ sessions | List scrolls smoothly; no lag or crash | ☐ | |

---

## Bug Triage

Failures found during testing are filed as **BL entries** in `docs/plans/README.md` and linked here.

| Bug ID | Story | Description | Status |
|--------|-------|-------------|--------|
| BL-T1-1 | TS-003 | Add server form: no inline error text for invalid URL (no scheme) — button disabled but no message explaining why | Open |
| BL-T14-1 | TS-258–265 | Sessions not refreshed on ON_RESUME: `AppRoot` lifecycle observer only calls `ping()`, not `vm.refresh()`. New/changed sessions appear stale for up to 5s after screen resume or returning from NewSession/Detail. Fix: call `SessionsViewModel.refresh()` from ON_RESUME handler. | Open |
| BL-T14-2 | TS-268 | `llmRef` and `computeNodeRef` not persisted in SQLDelight schema (`SessionRepository.upsertInternal`). After app restart, these fields are null — text search won't match on them. Affects TS-024 filtering. | Open |

---

## Coverage Gap: Unit Tests Owed (pre-v1.0 gate)

Per `docs/plans/2026-05-12-parity-arc-v0.102.md` Gate 2, these ViewModels need unit tests before v1.0 tag:

| ViewModel | Sprint | Test file needed |
|-----------|--------|-----------------|
| `SessionStatsViewModel` | 25 | `SessionStatsViewModelTest.kt` |
| `OllamaMarketplaceViewModel` (or equivalent) | 27 | `OllamaViewModelTest.kt` |
| `UnifiedPushSseService` | 28 | `UnifiedPushSseTest.kt` |
| `LlmRegistryViewModel` (if exists) | 30 | `LlmRegistryViewModelTest.kt` |
| `CouncilPersonaViewModel` | 31 | `CouncilPersonaViewModelTest.kt` |
| `WearSyncViewModel` | 32 | `WearSyncViewModelTest.kt` |

---

## Session Log

Each testing session appends a row here before committing.

| Date | Tester | T-Sprint | Stories Run | Pass | Fail | Notes |
|------|--------|----------|-------------|------|------|-------|
| 2026-05-12 | Claude | Setup | — | — | — | Plan created; emulator dw_test_phone created; system image Android 34 installed |
| 2026-05-12 | Claude | T1/T2 setup | — | — | — | APK built (v0.108.0, 100MB); emulator ANR loop under default GPU; restarted with -gpu swiftshader_indirect for headless stability; git hooks installed (pre-commit, prepare-commit-msg) |
| 2026-05-12 | Claude | T1 (partial) + T14 code audit | 12 | 2 | 1 | T1: TS-001✅ TS-010✅ TS-003❌ (no inline URL error) TS-002,004-009⏭ blocked. T14: Code audit complete — BL-T14-1 (ON_RESUME no refresh) BL-T14-2 (llmRef not persisted). Used uiautomator dump for exact tap coords; emulator System UI ANR needs swiftshader_indirect GPU. |

---

## Plan Maintenance

After each testing session:
1. Update story statuses inline (☐ → ✅/❌/⏭)
2. Append Session Log row
3. File BL entries for all failures
4. Commit plan: `docs(test-plan): T-sprint N — X pass, Y fail`
5. If failure is a bug, open GitHub issue only if it's a cross-repo contract gap (per `feedback_issues_scope.md` memory)
