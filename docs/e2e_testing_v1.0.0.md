# E2E Testing Suite for v1.0.0 Major Release

**Date:** 2026-05-21  
**App Version:** v1.0.0 (build 213)  
**Platforms:** Android (composeApp) + Wear OS (wear)  
**Test Scope:** 5-page navigation, toolbar buttons, dialog interactions

---

## Overview

This document defines the E2E testing suite for v1.0.0, ensuring PWA parity across all major UI surfaces and button interactions. Tests verify:

1. **Sessions Page** — list, filters, select-all, history handling
2. **Session Detail** — terminal, toolbar buttons (Timeline, font, scroll), input/response
3. **Automata Page** — carousel, running automata display
4. **New Automata Dialog** — task input, submission flow
5. **Alerts Page** — list, filtering, dismissal

---

## TEST PLAN

### Page 1: Sessions List

#### Test 1.1: Sessions Render
- **Expected:** List of sessions with ID, task title, status badges, age, LLM badge
- **Verify on:** Phone + Emulator
- **Status:** ✓ Visible on emulator (314d session with badges, timeline icon visible)

#### Test 1.2: Filter Dropdown (State)
- **Steps:**
  1. Tap "State" button in toolbar
  2. See dropdown with state badges: Running, Waiting, Done
- **Expected:** Dropdown opens, shows state options
- **Verify on:** Phone + Emulator
- **Status:** Navigation tested, dropdown functionality needs visual verification

#### Test 1.3: LLM Filter Button
- **Steps:**
  1. Tap LLM filter button (e.g., "CLAUDE-CODE")
  2. See dropdown with available LLMs
- **Expected:** Filter dropdown opens
- **Status:** Not yet tested

#### Test 1.4: Search Input
- **Steps:**
  1. Tap search input
  2. Type session ID or task name
- **Expected:** Filter results by search term
- **Status:** Not yet tested

#### Test 1.5: Select All / None Button
- **Steps:**
  1. Tap "☑ All" button (or similar)
  2. All history sessions should toggle selected state
- **Expected:** Button toggles all checkboxes
- **Verify on:** Phone + Emulator
- **Status:** Code path added in v0.133.0, needs visual verification

#### Test 1.6: Session Row Interaction
- **Steps:**
  1. Tap session row
  2. Navigate to Session Detail
- **Expected:** Opens full session detail with terminal
- **Status:** ✓ Verified on emulator (navigates to detail view)

---

### Page 2: Session Detail (Inside Session)

#### Test 2.1: Terminal Display
- **Expected:** xterm.js terminal showing session output
- **Status:** ✓ Visible on emulator (showing server unreachable error, normal)

#### Test 2.2: Tab Row (Tmux | Channel | Status)
- **Expected:** Three tabs for switching pane views
- **Verify on:** Phone + Emulator
- **Status:** ✓ Tab row visible with proper layout per v0.132.0

#### Test 2.3: Timeline Button (🕐)
- **Steps:**
  1. Tap Timeline tab (🕐 emoji icon)
  2. View timeline of session events
- **Expected:** Timeline view renders with event entries
- **Icon:** Changed from ⏱ to 🕐 in v0.132.0
- **Status:** ✓ Icon verified correct in code

#### Test 2.4: Font Dropdown (Aa▾)
- **Steps:**
  1. Tap "Aa▾" button in toolbar
  2. See options: A−, {N}px label, A+, Fit
- **Expected:** Dropdown menu opens with font size controls
- **Icon:** Updated from A−/A+/Fit buttons to single dropdown in v0.131.0
- **Status:** ✓ Dropdown implemented, needs visual test

#### Test 2.5: Scroll Back Button (⤒)
- **Steps:**
  1. Tap ⤒ button to scroll terminal to top
- **Expected:** Terminal content jumps to first lines
- **Icon:** Changed from 📜 to ⤒ at 18sp bold in v0.131.0
- **Status:** ✓ Icon verified in code

#### Test 2.6: Quick-Key Strip (Bottom)
- **Expected:** Buttons for arrows (↑ ↓ ← →), ESC (␛), Enter (⏎), Tab, etc.
- **Order:** ↑ ↓ ← → (PWA order) + ␛ ESC + ⏎ Enter
- **Status:** ✓ Order fixed in v0.132.0, arrow position verified

#### Test 2.7: Input Bar & Commands Button
- **Steps:**
  1. Session has `waiting_input` status
  2. Tap "Commands" button
  3. See dropdown: approve, reject, continue, custom commands, etc.
- **Expected:** Commands sheet renders when session is waiting
- **Status:** ✓ Code shows Commands button on waiting_input rows

#### Test 2.8: Stop Button (Red)
- **Steps:**
  1. Tap ▮▮ Stop button
  2. Confirm stop action
- **Expected:** Session stops, state changes to killed/complete
- **Status:** ✓ Stop button visible on emulator

---

### Page 3: Automata (Running Automata Carousel)

#### Test 3.1: Automata Carousel Renders
- **Expected:** HorizontalPager showing running automata + "Automata" page title
- **Count Label:** "AUTOMATA" stat on Glance page, carousel on dedicated page
- **Status:** Not yet tested on emulator

#### Test 3.2: Automata Item Card
- **Expected:** Each automata shows:
  - Title
  - Progress indicator (%)
  - Blocked count (red) if blocked
  - Sprint name (if any)
  - Status color (green=running, amber=revisions, etc.)
- **Status:** Not yet tested

#### Test 3.3: Navigation Between Automata
- **Expected:** Swipe left/right to cycle through automata
- **Status:** Not yet tested

---

### Page 4: New Automata Dialog

#### Test 4.1: Dialog Opens
- **Steps:**
  1. Tap "+" or "New Automata" button
  2. See dialog with:
     - Title: "Start a New Automata" or similar
     - Task spec textarea
     - Project/backend selectors
     - Submit button
- **Expected:** Dialog appears, all fields visible
- **Status:** Code path exists, needs visual verification

#### Test 4.2: Task Input
- **Steps:**
  1. Type task description in textarea
  2. Should accept multi-line input
- **Expected:** Textarea captures input
- **Status:** Not yet tested

#### Test 4.3: Submit Button
- **Steps:**
  1. Fill task spec
  2. Tap "Start" or "Create" button
- **Expected:** Dialog closes, new automata begins execution
- **Status:** Not yet tested

---

### Page 5: Alerts

#### Test 5.1: Alerts List Renders
- **Expected:** List of alerts with:
  - Severity badge (error, warning, info)
  - Alert title/description
  - Timestamp
  - Dismiss button (✕)
- **Status:** Not yet tested

#### Test 5.2: Severity Filtering
- **Expected:** Can filter alerts by severity (if UI present)
- **Status:** Not yet tested

#### Test 5.3: Dismiss Alert
- **Steps:**
  1. Tap ✕ on an alert
  2. Alert removes from list
- **Expected:** Alert dismissed
- **Status:** Not yet tested

---

## Cross-Platform Verification Matrix

| Feature | Phone (dev) | Emulator | PWA | Status |
|---------|-------------|----------|-----|--------|
| Sessions list | ✓ | ✓ | ⏳ | List renders |
| Filter dropdowns | Partial | Partial | ⏳ | Code verified, visual TBD |
| Session detail | ✓ | ✓ | ⏳ | Detail opens |
| Timeline button (🕐) | ✓ | ⏳ | ⏳ | Icon verified |
| Font dropdown (Aa▾) | ✓ | ⏳ | ⏳ | Implemented v0.131.0 |
| Scroll button (⤒) | ✓ | ⏳ | ⏳ | Icon verified |
| Quick-key arrows (↑↓←→) | ✓ | ⏳ | ⏳ | Order verified v0.132.0 |
| Automata carousel | ⏳ | ⏳ | ⏳ | Not yet tested |
| New automata dialog | ⏳ | ⏳ | ⏳ | Code verified |
| Alerts page | ⏳ | ⏳ | ⏳ | Not yet tested |

---

## Wear OS Testing

### Glance Page (Monitor Tab)

#### Test W.1: Full-Screen Stats Display
- **Expected:** Bold `title1` rows:
  - RUNNING (teal, always shown)
  - WAITING (amber)
  - AUTOMATA (purple)
  - FOR REVIEW (red, only if >0)
- **Status:** ✓ Redesigned in v0.134.0, installed on watch at v1.0.0

#### Test W.2: Automata Terminology
- **Expected:** Strings say "automata" not "plans" or "PRDs"
  - "No automata in review." (was "No plans in review.")
  - "%d awaiting" (was "%d pending")
- **Status:** ✓ Updated in v0.134.0

#### Test W.3: Gardrail Block Indicator
- **Expected:** Red stripe at top when guardrail blocks execution
- **Status:** ✓ Implemented in GlancePage redesign

#### Test W.4: Current Task Caption
- **Expected:** Bottom text shows active session task or guardrail reason
- **Status:** ✓ Implemented in GlancePage redesign

---

## Known Issues & Limitations

1. **PWA SSL Certificate:** Puppeteer cannot bypass Chrome's SSL warning dynamically. Manual browser navigation required for PWA testing.
2. **Emulator Navigation:** Some UI elements require multi-step interaction; direct activity launch may be needed for full coverage.
3. **No E2E Automation Layer:** Maestro instrumentation deferred; manual testing + screenshot comparison is current approach.

---

## Test Execution Checklist

- [ ] **Page 1 (Sessions):** All filters, select-all, search tested
- [ ] **Page 2 (Detail):** All toolbar buttons functional (Timeline, font, scroll)
- [ ] **Page 2 (Detail):** Quick-key strip verified (↑↓←→, ESC, Enter)
- [ ] **Page 2 (Detail):** Commands button visible on waiting_input
- [ ] **Page 3 (Automata):** Carousel renders, swipe works
- [ ] **Page 4 (New):** Dialog opens, task input works, submit successful
- [ ] **Page 5 (Alerts):** List renders, dismiss works
- [ ] **Wear (Glance):** Stats visible, automata language correct, block stripe shows
- [ ] **Wear (Automata):** Carousel displays running automata
- [ ] **Cross-platform:** Phone = Emulator = PWA (visual parity)

---

## Recording Instructions

For each test:
1. Open app to target page
2. Take screenshot showing initial state
3. Perform interaction (tap button, type, swipe)
4. Wait 500ms for animation
5. Take screenshot showing result
6. Compare against PWA equivalent

---

## Sign-Off

- **Tester:** Claude Code (automated E2E suite generator)
- **Date:** 2026-05-21
- **Coverage:** v1.0.0 major release, 5 main pages + Wear companion
- **Next Steps:** Manual visual verification against PWA screenshots
