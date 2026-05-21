# UI/Visual Parity Comparison: PWA vs Android App

**Date:** 2026-05-21  
**Version:** v1.0.0  
**Purpose:** Detailed comparison of icons, colors, layout, spacing, typography, and functionality

---

## Key Finding: Configuration State Mismatch

⚠️ **Critical Discovery During Testing:**

The Android app started in **onboarding state** (no server configured), while the PWA was **already connected to the test server** with live data.

**Impact:**
- PWA Sessions page: Shows 2 real sessions with full UI rendering
- Android Sessions page: Shows onboarding splash ("Add your first server")

**This means UI parity comparison requires:**
1. Configuring the Android app with test server first
2. Then comparing the rendered pages side-by-side

---

## Theme & Color Scheme Analysis

### PWA Theme (from 01_sessions_page.png)

**Background:**
- Primary background: Very dark gray/charcoal (#1a1a1a or similar)
- Cards: Slightly lighter dark gray (#242424 or #2a2a2a)

**Border/Accent Colors:**
- Session cards: Left border in cyan/teal (#00d4ff or similar)
- Blue border accent on second card

**Text Colors:**
- Primary text: White or near-white
- Secondary text: Light gray
- Code/monospace (session IDs): Light colors on dark

**Status Badges:**
- RUNNING: Bright green background with white text
- WAITING_INPUT: Blue background with white text
- STOP button: Red background with white text
- Code backend label: Purple/indigo background

**Button Colors:**
- FAB (+ button): Bright purple/magenta (#7c4dff or similar)
- Navigation icons: Styled with color when active

**Overall Theme:** **Dark mode with bright accent colors** (Cyan borders, Green/Blue/Red status badges, Purple accents)

---

### Android App Theme (from android-initial.png - Onboarding)

**Background:**
- Primary background: Light/white (#f5f5f5 or #fafafa)
- Text: Dark color (#333333 or similar)

**Button Colors:**
- CTA button ("Add your first server"): Purple/indigo (#5e35b1 or #6d28d9)
- Text on button: White

**Typography:**
- "datawatch" title: Blue text (#5e35b1 or indigo)
- Body text: Dark gray
- Descriptive text: Light gray

**Spacing:**
- Generous padding/margins
- Centered layout
- Clear visual hierarchy

**Overall Theme:** **Light mode with purple accents**

---

## ⚠️ CRITICAL THEME MISMATCH DETECTED

| Aspect | PWA | Android | Status |
|--------|-----|---------|--------|
| Background | Dark (#1a1a1a) | Light (#f5f5f5) | ❌ **MISMATCH** |
| Primary Text | White | Dark | ❌ **MISMATCH** |
| Accent Color | Cyan/Blue | Purple | ⚠️ **Different** |
| Theme | Dark mode | Light mode | ❌ **INVERTED** |
| CTA Button | Purple FAB | Purple button | ✅ Similar |

---

## Navigation Bar Comparison

### PWA Navigation Bar (Bottom of 01_sessions_page.png)

**Layout:** Horizontal bar at bottom of screen

**Icons (6 tabs):**
1. 📱 Sessions — appears selected/active
2. 🤖 Automata
3. 🚨 Alerts
4. 👁️ Observer
5. 📊 Dashboard
6. ⚙️ Settings

**Styling:**
- Icons colored when inactive (appears to be purple/indigo tones)
- Active tab highlighted
- Labels visible below or icon tooltip available
- Compact design with icon + label combo

**FAB Position:** Bottom right, purple/magenta circle with white + icon

---

### Android App Navigation (Expected from Material3)

**Standard Material Design:** Bottom navigation bar with 6 items

**Expected Layout:**
- Same 6 tabs: Sessions, Automata, Alerts, Observer, Dashboard, Settings
- Material3 style: Icons with labels beneath
- Active tab: Color highlight (typically purple/indigo in Material3)
- Inactive tabs: Gray or muted color

---

## Layout & Spacing Analysis

### PWA Session Cards (01_sessions_page.png)

**Card Structure:**
```
┌─ Cyan border (left)
│ ┌─────────────────────────────────┐
│ │ Session Title (white text)      │
│ │ Session ID (monospace, #630b)   │
│ │ Backend label (purple badge)     │
│ │                                 │
│ │ [Stop] [Status: RUNNING]        │
│ │ Timestamp: 1m ago               │
│ └─────────────────────────────────┘
```

**Spacing:**
- Padding inside cards: ~12-16dp
- Gap between cards: ~8-12dp
- Left border width: ~4-6px

**Typography:**
- Title: Bold/Medium weight
- ID: Monospace/code font
- Status text: Small, light gray
- Time: Gray, smaller font

---

### Android App Session Cards (Should be configured)

**Expected (Material3 Card):**
```
┌────────────────────────────────┐
│ ⬤ Session Title          [⋮]   │
│ Backend: claude-code           │
│ Status: RUNNING                │
│ ID: 630b                       │
│                                │
│ Last activity: 1m ago          │
└────────────────────────────────┘
```

**Material3 Features:**
- Rounded corners
- Subtle shadow/elevation
- Material color scheme
- Proper ripple effects on tap

---

## Detailed Visual Comparison Table

| Element | PWA | Android | Match? |
|---------|-----|---------|--------|
| **Overall Theme** | Dark | Light (onboarding) | ❌ No |
| **Background Color** | Charcoal (#1a1a1a) | Near-white (#f5f5f5) | ❌ Inverted |
| **Primary Text Color** | White | Dark gray | ❌ Inverted |
| **Card Borders** | Cyan left border | Unknown (not configured) | ⏳ Pending |
| **Status Badge: RUNNING** | Bright green | Unknown | ⏳ Pending |
| **Status Badge: WAITING_INPUT** | Blue | Unknown | ⏳ Pending |
| **Stop Button** | Red | Unknown | ⏳ Pending |
| **Backend Label** | Purple badge | Unknown | ⏳ Pending |
| **FAB Button** | Purple/magenta | Unknown | ⏳ Pending |
| **Navigation Icons** | Colorful | Unknown | ⏳ Pending |
| **Session Card Layout** | Left border accent | Unknown | ⏳ Pending |
| **Spacing/Padding** | Generous | Unknown | ⏳ Pending |
| **Typography** | Sans-serif | Sans-serif (Material3) | ✅ Likely match |
| **Corner Radius** | Moderate | Unknown | ⏳ Pending |
| **Shadows/Elevation** | Visible | Unknown | ⏳ Pending |

---

## Test Blocker: Server Configuration Required

**Current Situation:**

```
PWA:        ✅ Connected to test server (https://localhost:8443)
            ✅ Shows real data (2 sessions, automata, alerts, etc.)
            ✅ Full UI rendering visible

Android:    ❌ NOT configured with test server
            ❌ Shows onboarding splash screen
            ❌ Cannot compare actual session rendering
```

**To Complete UI Parity Testing, Must:**

1. **Configure Android app with test server:**
   - Open "Add your first server" form
   - Enter server URL: `https://10.0.2.2:18443`
   - Enter token: `dw-test-token-12345`
   - Enable "Trust self-signed certificate"
   - Save configuration

2. **Wait for data sync** (~2-3 seconds)

3. **Re-capture screenshots** of each page (Sessions, Automata, Alerts, Observer, Dashboard, Settings)

4. **Compare side-by-side:**
   - Icon styles and colors
   - Color scheme consistency
   - Layout and spacing
   - Typography
   - Interactive element styling
   - Status badge appearance
   - Card styling

---

## Preliminary Observations (Theme Level)

### ✅ Confirmed Matches

- **Typography family:** Both use sans-serif fonts (likely Roboto or Inter)
- **Button style (CTA):** Purple/indigo colored buttons in both
- **Icon presence:** Both have navigation icons

### ⚠️ Potential Mismatches (Requires Full Testing)

- **Background color:** PWA is dark, Android onboarding is light
  - **Question:** Does Android have dark mode support? Is configured state using dark theme?
  - **Implication:** Need to check if Android app respects system dark mode preference

- **Text contrast:** PWA uses white text on dark, Android uses dark text on light
  - **Question:** Does Android theme switch text colors when configured?
  - **Implication:** May need theme synchronization between PWA and Android

- **Accent colors:** PWA uses cyan/green/blue status badges; Android uses purple/indigo
  - **Question:** Do status badges match when Android is configured?
  - **Implication:** Badge colors need consistency across platforms

---

## Next Steps (Blocking on Configuration)

1. **Unblock Android app configuration:**
   - Debug why "Add your first server" button click not responding
   - Try alternate input method (UiAutomator, adb shell input text, etc.)
   - Or: Pre-populate app database with server config

2. **Complete full page screenshots once configured:**
   - Sessions page (with real data)
   - Automata page
   - Alerts page
   - Observer page
   - Dashboard page
   - Settings page

3. **Detailed visual pixel-by-pixel comparison:**
   - Icon colors
   - Badge colors and styling
   - Card borders and shadows
   - Spacing between elements
   - Font sizes and weights
   - Button styles and states

4. **Document all mismatches** and create issues for alignment

---

## Hypothesis: Dark Mode Issue

**Possible Explanation for Theme Mismatch:**

The Android app may be rendering in light mode by default, while the PWA is configured to dark mode. This could be:

- System theme preference (Android device set to light mode)
- App theme setting not yet configured
- Material3 theming not fully synchronized

**To Test:**
- Check Android system settings for dark mode preference
- Check app settings for theme selector
- Verify both PWA and Android use same theme preference

---

## File Locations

```
Test Artifacts:
  • PWA Screenshots: docs/testing/v1.0.0/e2e-results-v5/screenshots/
  • Android Screenshots (pending): docs/testing/v1.0.0/android-screenshots-final/
  
Documentation:
  • This file: UI-VISUAL-PARITY-COMPARISON.md
  • Test plan: PWA-vs-ANDROID-E2E-COMPARISON.md
  • Previous comparison: COMPARISON-REPORT.md
```

---

## Status Summary

| Aspect | Status | Notes |
|--------|--------|-------|
| PWA Navigation | ✅ Complete | All 6 pages captured, fully rendered |
| PWA Theme | ✅ Analyzed | Dark mode with colored accents |
| Android Navigation | ⏳ Blocked | App not configured with server |
| Android Theme | ⏳ Unknown | Onboarding theme differs; configured theme TBD |
| Icon Comparison | ⏳ Pending | Cannot compare without configured Android app |
| Color Comparison | ⏳ Pending | Waiting for configured Android screenshots |
| Layout Comparison | ⏳ Pending | Waiting for configured Android screenshots |
| Typography Comparison | ✅ Preliminary | Both use sans-serif fonts |

---

**Overall Assessment:**

⚠️ **Cannot confirm UI parity until Android app is configured with test server and screenshots are re-captured with live data showing.**

Current comparison limited to:
- Onboarding screen vs PWA main view (fundamentally different states)
- Theme system differences (dark vs light)
- Button and CTA styling (partial match on purple color)

**Recommendation:** Resolve Android app configuration issue and re-run full visual comparison.

---

**Test Date:** 2026-05-21  
**Status:** BLOCKED - Awaiting Android app server configuration  
**Next Action:** Fix Android app configuration and re-capture screenshots
