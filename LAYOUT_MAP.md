# SessionDetailScreen Layout Analysis

## Current Structure (Lines 406-732)

```
Box(fillMaxSize)
  └─ Column(fillMaxSize)
      ├─ Column(weight=1f, fillMaxWidth)  [SCROLLABLE CONTENT]
      │   ├─ Spacer(height=80.dp) ← ARTIFICIAL SPACE FOR HEADER OFFSET
      │   ├─ LastResponseSheet (conditional)
      │   ├─ Banners (ConnectionBanner, ErrorBanner)
      │   ├─ Main Content Layer:
      │   │   ├─ Terminal (TerminalView with weight=1f)
      │   │   ├─ TerminalScrollModeStrip
      │   │   ├─ InlineNotices
      │   │   └─ ChatTranscriptPanel / ChatEventList / SessionStatusPanel
      │   └─ SessionSchedulesStrip (conditional)
      │
      ├─ Box(fillMaxWidth, imePadding)  [COMPOSER - Responds to keyboard]
      │   └─ Column
      │       └─ ReplyComposer (text input + buttons)
      │
      └─ Column(fillMaxWidth, zIndex=2f)  [FIXED HEADERS - Overlay on top]
          ├─ SessionInfoBar (line 671-691)
          │   - Backend/LLM info
          │   - State pill / Stop / Timeline buttons
          │   - Close button
          │
          └─ Row(fillMaxWidth, zIndex not set)  [TAB ROW - line 695-730]
              - background(colorScheme.surface)
              - Tmux / Channel / Status tabs
              - TerminalToolbarControls (PgUp/PgDn if not in chat mode)
```

## Problems Identified

### Problem 1: Spacer Waste
- 80.dp Spacer at line 426 is **artificial space** taking up precious vertical real estate
- Pushes actual content down by 80px
- This space should be used by Terminal, not wasted

### Problem 2: Headers Position in Code vs. Visual
- Headers (lines 669-732) defined **AFTER** content in code
- But rendered **ON TOP** via zIndex(2f) overlay
- This creates a positioning disconnect - headers float above content they don't reserve space for

### Problem 3: Scrolling Boundaries
- TerminalView inside weight(1f) Column, but Column doesn't have scroll modifier
- TerminalView itself handles internal scroll via rememberScrollState
- When keyboard appears (imePadding on Composer), the scrollable content Column doesn't move
- **Headers overlay is fixed to screen, not to content** - unclear scroll relationship

### Problem 4: Keyboard Layout Response
- Composer has imePadding (moves up when keyboard appears)
- Content Column has NO imePadding
- When keyboard appears: Composer slides up, but content behind headers stays in place
- Terminal can't scroll up to make room for keyboard

### Problem 5: Stacking Order
- Scrollable content (lines 420-666) comes first in code
- Fixed headers (lines 669-732) come last in code
- Visual stacking order via zIndex(2f) inverts this, but spatial layout is confused

## Expected Behavior Model

Like HTML/CSS grid or flexbox:

```
┌─────────────────────────────────────┐
│     SessionInfoBar (fixed)          │  ← Not scrollable
│     Tab Row (fixed)                 │  ← Not scrollable
├─────────────────────────────────────┤
│                                     │
│  Terminal Content                   │  ← Scrollable
│  (scrolls up to reveal more)        │  ← When keyboard: shifts up
│                                     │
├─────────────────────────────────────┤
│  ReplyComposer (input box)          │  ← Responds to keyboard insets
│  Buttons                            │  ← Moves up with keyboard
└─────────────────────────────────────┘
```

## What Should Happen

1. **Headers occupy fixed space at top** - They should reserve height, not overlay
2. **Content scrolls under headers** - When terminal content scrolls up, it goes BEHIND headers
3. **Scrollable region starts below headers** - Terminal viewport is everything between headers and composer
4. **Terminal scrolls within its viewport** - Internal TerminalView scroll is constrained by visible bounds
5. **When keyboard appears**:
   - Composer (imePadding) slides up
   - Scrollable content area shrinks
   - Terminal content is pushed up (can scroll to reveal more)
   - Headers stay fixed at their position

## Key Layout Constraints

- SessionInfoBar height: ~56dp (standard app bar)
- Tab Row height: ~48dp (material tab bar)
- **Total header height: ~104dp** (currently approximated by 80dp Spacer - too small!)
- Composer height: Variable (56dp text field + padding)
- TerminalView: Fills remaining space

## Next Steps

1. Remove 80.dp Spacer (line 426)
2. Restructure to make headers actual space-reserving elements, not overlays
3. Apply imePadding to the scrollable content area, not just Composer
4. Ensure TerminalView scroll works within bounded viewport
