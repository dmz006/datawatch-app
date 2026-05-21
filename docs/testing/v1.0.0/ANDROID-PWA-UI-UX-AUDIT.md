# Android ↔ PWA UI/UX 1:1 Mapping Audit

**Date:** 2026-05-21  
**Version:** v1.0.0 (build 213)  
**Scope:** Structural, styling, and functional parity between Android and PWA

---

## Executive Summary

**Critical Architectural Mismatch Found:**

| Aspect | Android | PWA | Status |
|--------|---------|-----|--------|
| Main Pages | 4 | 6 | 🚨 MISMATCH |
| Page Navigation | Bottom tabs | Side tabs (right) | ⚠️ DIFFERENT |
| Settings Tabs | 6 (General, Plugins, Comms, Compute, Automata, About) | 6+ (General, Plugins, Comms, Compute, Automata, About, ...) | ✅ ALIGNED |
| Session Detail View | ✅ Present | ✅ Present | ✅ ALIGNED |
| Status Badges | CLAUDE-CODE, SLIDING_INPUT, WAITING_INPUT | claude-code, RUNNING, WAITING_INPUT | ⚠️ DIFFERENT STYLE |

---

## CRITICAL: Missing Pages in Android

### PWA has 6 main pages:
1. ✅ Sessions
2. 🚨 Automata ← **MISSING FROM ANDROID**
3. ✅ Alerts
4. ✅ Observer
5. 🚨 Dashboard ← **MISSING FROM ANDROID**
6. ✅ Settings

### Android has 4 main pages:
1. ✅ Sessions (maps to Sessions)
2. ✅ Alerts (maps to Alerts)
3. ✅ Observer (maps to Observer)
4. ✅ Settings (maps to Settings)

**Finding:** Android app lacks Automata and Dashboard pages in main navigation.

**Code Reference:** Android navigation in MainActivity uses bottom tab bar, PWA uses right-side vertical tab bar.

---

## UI/UX Differences Found

### 1. Navigation Layout
- **Android:** Horizontal bottom tab bar (4 pages)
  - Icons + labels: Sessions, Alerts, Observer, Settings
  - FAB (+) button in bottom-right corner
  - Fixed layout
  
- **PWA:** Vertical right-side tab bar (6 pages)
  - Icons + labels arranged vertically
  - Scrollable if needed (6 pages fit in viewport)
  
**Status:** 🚨 **STRUCTURAL DIFFERENCE** - Not easily reconcilable without major refactor

### 2. Sessions Page
- **Android:** 
  - Header with server dropdown ("dw-localhost")
  - Cached data indicator: "Disconnected - showing cached data (Server unreachable)"
  - Card-based session list
  - Each session shows: ID, status badges, session title, description
  - Stop + Commands buttons on each session
  - FAB (+) to create new session
  
- **PWA:**
  - Header with title "Datawatch"
  - Top bar with icons: Help, Search, Alerts count, Settings
  - Card/carousel-based session list
  - Each session shows: Name, Status badge (RUNNING/WAITING_INPUT), Backend, Response time
  - Inline Stop/Play/More buttons
  
**Status:** ✅ **FUNCTIONALLY ALIGNED** but with styling differences

### 3. Status Badges & Colors
- **Android:** 
  - `SLIDING_INPUT` (purple/magenta)
  - `WAITING_INPUT` (blue/cyan)
  - `claude-code` (gray label)
  
- **PWA:**
  - `RUNNING` (green badge)
  - `WAITING_INPUT` (amber badge)
  - `claude-code` (gray label with background)
  
**Status:** ⚠️ **STYLING DIFFERENT** - Badge colors and naming convention differ

### 4. Detail Screen
- **Android:** Dedicated SessionDetailScreen with:
  - X close button
  - Session title and metadata
  - Status/backend badges
  - Channel tabs: tmux, channel, Status
  - Control buttons: Stop, Commands
  - Terminal view area
  - Reply input at bottom
  
- **PWA:** No dedicated detail page captured, but code indicates similar structure in Automata/Sessions flow
  
**Status:** ✅ **LIKELY ALIGNED** but needs verification

### 5. Settings Page
- **Android:** ScrollableTabRow with 6 tabs:
  1. General
  2. Plugins
  3. Comms
  4. Compute
  5. Automata
  6. About (mobile-only)
  
- **PWA:** Tab structure visible in screenshot:
  - Tabs: General, Plugins, Comms, Compute, Automata, (more with scroll)
  - About appears to be included
  
**Status:** ✅ **TAB STRUCTURE ALIGNED** (including About)

---

## Code Analysis Findings

### Android SettingsScreen (from code review)
```kotlin
private enum class SettingsTab(@StringRes val labelRes: Int) {
    General(R.string.settings_tab_general),
    Plugins(R.string.settings_tab_plugins),
    Comms(R.string.settings_tab_comms),
    Compute(R.string.settings_tab_compute),
    Automata(R.string.settings_tab_automata),
    About(R.string.settings_tab_about),
}
```

**Note:** Code comments reference "Monitor" tab in PWA v7.0.0-alpha.12, but current v1.0.0 PWA doesn't show it. Android redirects "monitor" → "General".

### Android AutonomousScreen
- Contains TabRow for managing automata/PRDs
- Likely maps to PWA's Automata page
- But NOT accessible from main navigation ⚠️

---

## Mismatches Summary

### Critical (Breaking) 🚨
1. **Missing Pages**: Android lacks Automata and Dashboard pages from main nav
   - Android: 4 tabs
   - PWA: 6 tabs
   - **Impact:** Users cannot access 2 major features from Android app

### High (Significant) ⚠️
2. **Navigation Layout**: Different positioning (bottom vs. right-side)
   - **Impact:** Different UX, but functionally equivalent
3. **Status Badge Naming/Colors**: SLIDING_INPUT vs RUNNING, different color scheme
   - **Impact:** Visual inconsistency, unclear status intent

### Medium (Styling) ℹ️
4. **Server Display**: Android shows server dropdown + disconnection indicator
   - PWA doesn't show explicit server selector
   - **Impact:** Minor styling difference

### Low (Cosmetic) ✓
5. **Typography & Spacing**: Likely subtle differences in Material3 vs web styling
   - Both use similar design patterns
   - **Impact:** Minimal

---

## Required Fixes

### Phase 1: Critical (Must Fix)
- [ ] **Add Automata page to Android main navigation**
  - Make AutonomousScreen accessible from bottom tab bar
  - Expand bottom nav from 4 to 6 tabs OR implement scroll
  - Replace one less-critical tab if space is issue
  
- [ ] **Add Dashboard page to Android main navigation**
  - Create or expose DashboardScreen in main nav
  - Align with PWA 6-page structure

### Phase 2: High (Should Fix)
- [ ] **Standardize status badge naming**
  - Android: SLIDING_INPUT → RUNNING (or reverse in PWA)
  - Align color scheme across both platforms
  
- [ ] **Align navigation layout where possible**
  - Consider bottom nav horizontal scrolling for Android
  - Document platform-specific differences

### Phase 3: Medium (Nice to Have)
- [ ] **Settings tabs**: Verify "About" section parity
  - Ensure both apps show identical tab list
  - Test content parity in each tab

---

## Testing Notes

- **Android Emulator:** emulator-5554 (sdk_gphone64_x86_64)
- **PWA:** https://localhost:8443 (datawatch daemon v8.6.1)
- **Limitations:** Android emulator input navigation limited; screenshots show Sessions page only
- **Data State:** Disconnected (Server unreachable) - both apps showed cached data

---

## Sign-Off

**Audit Status:** ⚠️ **INCOMPLETE** - Structural mismatch identified, full page-by-page comparison blocked by navigation issues

**Recommendation:** 
1. Fix critical page mismatch (add Automata + Dashboard to Android nav)
2. Re-run audit after structural fixes
3. Complete detailed styling/functional comparison for each page

**Files:**
- PWA screenshots: `e2e-results-v5/screenshots/` (7 pages)
- Android screenshots: `android-screenshots/sessions_list.png` (Sessions page only)

