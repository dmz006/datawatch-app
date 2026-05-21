# PWA vs Android App E2E Comparison Testing

**Date:** 2026-05-21  
**Version:** v1.0.0  
**Purpose:** Verify feature parity between PWA and native Android app across all major surfaces (phone, Wear OS, Android Auto)

---

## Overview

This document defines the end-to-end comparison testing between:

1. **PWA** — Web app running at https://localhost:8443
2. **Android Phone** — Native app on emulator (publicTrackRelease)
3. **Wear OS** — Companion watch app (optional, physical device required)
4. **Android Auto** — Vehicle-optimized interface (optional, DHU or head unit required)

All tests run against the same datawatch server instance to ensure identical backend state.

---

## Test Environment

### Server Configuration
- **Instance:** datawatch v8.6.1+ running at `https://localhost:8443`
- **Test Data:** 2-3 active sessions + automata + alerts
- **Token:** `dw-test-token-12345` (configured in both PWA and app)
- **Network:** Emulator bridge via `adb reverse` for app; direct localhost for PWA

### PWA
- **Browser:** Chromium (Puppeteer or manual)
- **Viewport:** 1080×1920 (portrait phone)
- **URL:** https://localhost:8443
- **Status:** ✅ Fully tested (v5 validation suite passing)

### Android Phone Emulator
- **Device:** dw_test_phone (Pixel 6, Android 14)
- **Build:** assemblePublicTrackRelease (signed, v1.0.0)
- **App ID:** com.dmzs.datawatchclient
- **Status:** ✅ Layout fixed (SessionDetailScreen.kt)

### Wear OS (Physical Device or Emulator)
- **Requirement:** Physical Wear OS watch (emulator support may be limited)
- **Build:** Included in same APK as phone
- **Tiles:** 5 dashboard tiles
- **Complications:** 8 complications
- **Status:** ⏳ Requires physical device for haptic/wear-specific validation

### Android Auto (DHU or Head Unit)
- **Requirement:** Android Automotive Development Head Unit or real vehicle
- **Build:** Included in same APK
- **Screens:** MissionControl, SessionList, SessionDetail, Automata
- **Status:** ⏳ Requires DHU or vehicle hardware

---

## Test Stories

All tests compare both PWA and native app side-by-side for the same interaction.

### TS-C01 — Page Navigation

**Goal:** Verify all 6 major pages are accessible and load correctly on both PWA and app.

| Page | PWA | Android Phone | Wear OS | Auto |
|------|-----|---------------|---------|------|
| Sessions | ✅ | ✅ | ❌ N/A | ❌ N/A |
| Automata | ✅ | ✅ | ❌ N/A | ❌ N/A |
| Alerts | ✅ | ✅ | ❌ N/A | ❌ N/A |
| Observer | ✅ | ✅ | ❌ N/A | ❌ N/A |
| Dashboard | ✅ | ✅ | ❌ N/A | ❌ N/A |
| Settings | ✅ | ✅ | ❌ N/A | ❌ N/A |

**Test Procedure:**

1. **PWA:**
   ```bash
   # Run v5 Puppeteer validation (all pages pass in 10s)
   node docs/testing/v1.0.0/run-e2e-validation-v5.js
   ```
   - Screenshot each page: `00_initial_load.png` → `06_settings_page.png`
   - Verify content loads within 5 seconds per page
   - Verify no console errors

2. **Android Phone:**
   - Open app
   - Tap each tab in bottom navigation bar (Sessions → Automata → Alerts → Observer → Dashboard → Settings)
   - Verify page loads and displays content
   - Document rendering time per page

**Expected Result:** Both PWA and app navigate to all 6 pages with identical content structure and data.

---

### TS-C02 — Session List & Detail View

**Goal:** Verify sessions display identically in both PWA and app, and detail drill-down works.

**Test Data Setup:**
- Create 2-3 test sessions on the backend
- Sessions should include: RUNNING, WAITING_INPUT, COMPLETE states

**PWA Test:**
1. Navigate to Sessions page
2. Verify session cards display:
   - Session ID (last 4 chars)
   - Status badge (RUNNING/WAITING_INPUT/COMPLETE)
   - Timestamp / duration
   - Backend identifier
3. Click on a session card
4. Verify detail view shows:
   - Full session output/log
   - Input field (if WAITING_INPUT)
   - Toolbar buttons (Stop, Maximize, etc.)
5. Screenshot: `pwa_session_detail.png`

**Android Phone Test:**
1. Tap Sessions tab
2. Verify session cards display identically:
   - Session ID
   - Status badge
   - Timestamp
   - Backend identifier
3. Tap on a session card
4. Verify detail view shows:
   - Terminal output with correct content
   - Input field above keyboard when visible
   - Toolbar buttons functional
5. Screenshot: `android_session_detail.png`

**Comparison:**
- [ ] Session card layout identical (ID, status, timestamp positioning)
- [ ] Detail view shows same output
- [ ] Status badges match (color, text)
- [ ] Input field accessible on both

---

### TS-C03 — Automata (PRD) List & Detail View

**Goal:** Verify automata/PRD display and state machine transitions work identically.

**Test Data Setup:**
- Create 2-3 automata with different statuses (planning, running, complete)

**PWA Test:**
1. Navigate to Automata page
2. Verify automata cards display:
   - PRD ID / title
   - Current phase (Observe, Improve, Measure, etc.)
   - Progress indicator
   - Task counts (N/M complete)
3. Click on an automaton
4. Verify detail view shows:
   - Task list with status
   - Story tree (if applicable)
   - Action buttons (Start, Advance, Abort, Reset)
5. Screenshot: `pwa_automata_detail.png`

**Android Phone Test:**
1. Tap Automata tab
2. Verify automata display identically:
   - PRD ID / title
   - Phase indicator
   - Progress
   - Task counts
3. Tap on an automaton
4. Verify detail shows identical data and layout
5. Test Algorithm Mode card (if visible):
   - Phase strip displays
   - Dot colors match phase
6. Screenshot: `android_automata_detail.png`

**Comparison:**
- [ ] Automata list layout identical
- [ ] Phase indicators match
- [ ] Detail view shows same tasks/story structure
- [ ] Action buttons present and functional

---

### TS-C04 — Alerts & Guardrails

**Goal:** Verify alert display and guardrail verdicts match between PWA and app.

**Test Data Setup:**
- Trigger 1-2 guardrail violations (e.g., SAST scan with findings)
- Alerts should appear in Alerts tab

**PWA Test:**
1. Navigate to Alerts page
2. Verify alert list shows:
   - Alert type (guardrail, scan result, etc.)
   - Severity level (info, warning, error, critical)
   - Timestamp
   - Summary text
3. Click on an alert
4. Verify detail shows full verdict details
5. Screenshot: `pwa_alerts.png`

**Android Phone Test:**
1. Tap Alerts tab
2. Verify alerts display identically:
   - Type and severity
   - Timestamp
   - Summary
3. Tap on alert
4. Verify detail shows identical data
5. Screenshot: `android_alerts.png`

**Comparison:**
- [ ] Alert list layout and styling identical
- [ ] Severity badge colors match
- [ ] Alert counts match (if displayed)
- [ ] Detail view structure identical

---

### TS-C05 — Observer (Monitoring)

**Goal:** Verify server monitoring dashboard works identically on both platforms.

**PWA Test:**
1. Navigate to Observer page
2. Verify monitoring data displays:
   - CPU, Memory, Disk metrics
   - Session counts by state (RUNNING, WAITING_INPUT, etc.)
   - LLM backend status
   - Uptime / version info
3. Screenshot: `pwa_observer.png`

**Android Phone Test:**
1. Tap Observer tab
2. Verify identical metrics display:
   - CPU, Memory, Disk
   - Session counts
   - Backend status
3. Screenshot: `android_observer.png`

**Comparison:**
- [ ] Metric layout identical
- [ ] Numeric values match (synced from same server)
- [ ] Color coding for health status matches
- [ ] Chart/gauge rendering similar

---

### TS-C06 — Dashboard Card Management

**Goal:** Verify dashboard card CRUD operations work on both platforms.

**PWA Test:**
1. Navigate to Dashboard page
2. Verify existing cards display
3. Click "Add Card" button
4. Select a card type from menu
5. Verify card appears and is immediately editable
6. Test card removal button
7. Refresh page; verify changes persist
8. Screenshot: `pwa_dashboard.png`

**Android Phone Test:**
1. Tap Dashboard tab
2. Verify existing cards display
3. Tap "Add Card" or plus button
4. Select a card type
5. Verify card appears
6. Test card removal
7. Navigate away and back to Dashboard
8. Verify changes persist
9. Screenshot: `android_dashboard.png`

**Comparison:**
- [ ] Add card UI/UX identical
- [ ] Card layout identical
- [ ] Persistence works on both
- [ ] No data loss on navigation

---

### TS-C07 — Settings & Configuration

**Goal:** Verify all settings tabs and functionality match between platforms.

**PWA Test:**
1. Navigate to Settings page
2. Verify tabs visible and clickable:
   - General
   - Plugins
   - Comms (servers)
   - Compute
   - Automata
   - (additional tabs via scroll)
3. In Comms tab:
   - Verify test server is configured
   - Show server details (name, URL, token status)
4. In General tab (if available):
   - Check theme/display settings
5. Screenshot: `pwa_settings.png`

**Android Phone Test:**
1. Tap Settings tab
2. Verify tabs accessible:
   - Settings tabs should match PWA layout
   - Comms tab shows configured servers
   - General/display options present
3. Verify test server configuration matches PWA
4. Screenshot: `android_settings.png`

**Comparison:**
- [ ] Tabs identical in both (same list, order)
- [ ] Settings values display identically
- [ ] Server configuration shows same data
- [ ] No missing settings in either version

---

### TS-C08 — Keyboard & Input Handling (Phone Only)

**Goal:** Verify input fields and keyboard interaction work correctly on Android app.

**Test Procedure:**
1. Navigate to a session detail page
2. Verify terminal output displays correctly above keyboard area
3. Tap in the input field
4. Verify keyboard appears
5. Verify input field remains visible above keyboard (not pushed down)
6. Type test message
7. Verify text appears in field without truncation
8. Dismiss keyboard
9. Verify full terminal output is visible again

**Expected Result:** 
- Input field stays above keyboard (SessionDetailScreen.kt fix working)
- No layout shift or hidden content
- Terminal remains readable while keyboard is open

**Screenshot:** `android_keyboard_interaction.png`

---

### TS-C09 — Multi-Server Navigation (Settings → Comms)

**Goal:** Verify switching between multiple configured servers works on both platforms.

**Test Data Setup:**
- Configure 2+ datawatch instances (if available)
- Add both to app and PWA settings

**PWA Test:**
1. Go to Settings → Comms
2. Click on each server
3. Verify you switch to that server's data
4. Navigate to Sessions page
5. Verify sessions are from the selected server
6. Switch servers via Comms
7. Return to Sessions; verify different sessions appear

**Android Phone Test:**
1. Go to Settings → Comms
2. Tap each server to switch
3. Go to Sessions tab
4. Verify sessions match selected server
5. Return to Settings and switch again
6. Verify session list updates

**Comparison:**
- [ ] Server switching works on both
- [ ] Session list updates to reflect selected server
- [ ] No stale data from previous server

---

### TS-C10 — Real-Time Updates (WebSocket)

**Goal:** Verify live data updates work identically on both platforms.

**Test Procedure:**
1. On both PWA and app, navigate to Sessions page
2. Create a new session on the backend (or trigger one via CLI)
3. Observe: Does the new session appear in the list?
4. Modify session state (change status)
5. Observe: Does the UI update automatically?
6. Check timing: How long does update take (≤ 2 seconds expected)?

**Expected Result:**
- New sessions appear within 2 seconds on both
- Session status updates reflected immediately
- No manual refresh needed

**Screenshot (if applicable):** `websocket_update_timing.md`

---

## Test Execution Checklist

Before running tests:

- [ ] Datawatch server running at https://localhost:8443
- [ ] Server health check passes: `curl -sk https://localhost:8443/api/health`
- [ ] PWA accessible: https://localhost:8443/
- [ ] Android emulator running with app installed
- [ ] App configured with test server (Settings → Comms → Add server)
- [ ] Test token matches server configuration
- [ ] 2-3 sample sessions/automata exist on server

### Run PWA Tests

```bash
# Run automated Puppeteer suite
node docs/testing/v1.0.0/run-e2e-validation-v5.js

# Results appear in:
# docs/testing/v1.0.0/e2e-results-v5/
# - report.json (test results)
# - screenshots/ (page images)
```

### Run Android Phone Tests

```bash
# Build and install app
./gradlew :composeApp:assemblePublicTrackRelease
adb install -r composeApp/build/outputs/apk/publicTrack/release/*.apk

# Manual test execution:
# 1. Open app
# 2. Navigate through each tab
# 3. Test interactions described in TS-C01 through TS-C10
# 4. Screenshot key states
# 5. Document any differences in comparison table below
```

---

## Comparison Results

| Test Case | PWA | Android | Wear | Auto | Status |
|-----------|-----|---------|------|------|--------|
| TS-C01: Page Navigation | ✅ Pass | ⏳ | N/A | N/A | |
| TS-C02: Session Detail | ✅ Pass | ⏳ | N/A | N/A | |
| TS-C03: Automata Detail | ✅ Pass | ⏳ | N/A | N/A | |
| TS-C04: Alerts | ✅ Pass | ⏳ | N/A | N/A | |
| TS-C05: Observer | ✅ Pass | ⏳ | N/A | N/A | |
| TS-C06: Dashboard | ✅ Pass | ⏳ | N/A | N/A | |
| TS-C07: Settings | ✅ Pass | ⏳ | N/A | N/A | |
| TS-C08: Keyboard (Phone only) | N/A | ⏳ | N/A | N/A | Fixed in SessionDetailScreen.kt |
| TS-C09: Multi-Server | ✅ Pass | ⏳ | N/A | N/A | |
| TS-C10: Real-Time Updates | ✅ Pass | ⏳ | N/A | N/A | |

---

## Issues & Resolutions

### Known Issues (Resolved)

| Issue | Symptom | Root Cause | Resolution |
|-------|---------|-----------|-----------|
| SessionDetailScreen Layout | Input field hidden below keyboard | `imePadding()` applied to entire Column | Split layout into weight(1f) terminal Column + separate Box for composer (commit da3bb99) |
| GIF Animation | All screenshots identical (static image) | Screenshot capture process failed | Cleared corrupted screenshots, captured fresh frames with server connected |
| Screenshot Corruption | Emulator form fields appended text | Multiple input attempts without field reset | Cleared form, captured fresh with proper ADB field tap |

---

## Sign-Off

**Comparison Test Plan:** Complete  
**Status:** Ready for execution  
**Next Step:** Run manual Android tests against running server

---

## Files & Artifacts

```
docs/testing/v1.0.0/
├── PWA-vs-ANDROID-E2E-COMPARISON.md (this file)
├── e2e-results-v5/                  (PWA test results)
│   ├── report.json
│   └── screenshots/
│       ├── 00_initial_load.png
│       ├── 01_sessions_page.png
│       ├── 02_automata_page.png
│       ├── 03_alerts_page.png
│       ├── 04_observer_page.png
│       ├── 05_dashboard_page.png
│       └── 06_settings_page.png
├── android-screenshots/             (Android test results - to be captured)
│   ├── sessions.png
│   ├── automata.png
│   ├── alerts.png
│   ├── observer.png
│   ├── dashboard.png
│   └── settings.png
```

---

**Last Updated:** 2026-05-21  
**Test Version:** v1.0.0
