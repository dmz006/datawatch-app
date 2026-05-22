# Layout Fix Strategy

## Root Cause

The current code treats headers as **overlays** (zIndex=2f positioned after content).
This creates a mismatch between:
- **Code order**: Headers defined last (lines 669-732)
- **Visual order**: Headers on top (via zIndex)
- **Layout flow**: Headers don't reserve space, Spacer (80dp) fills it instead

This disconnects makes scrolling behavior unpredictable.

## Correct Model (HTML/CSS equivalent)

```css
/* Outer container */
body {
  display: flex;
  flex-direction: column;
  height: 100%;
}

/* Fixed header group - reserve space */
header {
  flex: 0 0 auto;  /* Don't grow, don't shrink, take natural size */
  height: auto;    /* Size based on content */
}

/* Scrollable content area - takes remaining space */
main {
  flex: 1 1 auto;  /* Grow to fill, allow shrinking, auto size */
  overflow-y: auto;  /* Scrollable */
}

/* Composer - fixed at bottom, responds to keyboard */
footer {
  flex: 0 0 auto;
  height: auto;
}
```

## Current Compose Code Structure

```
Box(fillMaxSize)  // Outer container
  └─ Column(fillMaxSize)
      ├─ Column(weight=1f)  [CONTENT] 
      │   ├─ Spacer(80.dp)  ← PROBLEM: artificial space for headers
      │   ├─ Terminal
      │   └─ ...
      │
      ├─ Box(imePadding)  [COMPOSER]
      │   └─ ReplyComposer
      │
      └─ Column(zIndex=2f)  [HEADERS] ← PROBLEM: overlay, no space reservation
          ├─ SessionInfoBar
          └─ Tab Row
```

## Correct Compose Structure

```
Box(fillMaxSize)
  └─ Column(fillMaxSize)
      ├─ Column()  [HEADERS] ← Move to FIRST, no overlay
      │   ├─ SessionInfoBar
      │   └─ Tab Row
      │
      ├─ Column(weight=1f, imePadding)  [CONTENT] ← Takes remaining space, responds to keyboard
      │   ├─ Terminal(weight=1f)  ← Scrolls within viewport
      │   └─ ...
      │
      └─ Box(imePadding)  [COMPOSER] ← At bottom
          └─ ReplyComposer
```

## Key Changes

1. **Remove 80.dp Spacer** (line 426)
   - Headers now reserve their own space
   
2. **Move headers to FIRST position** (before content)
   - Cut lines 669-732 (Column with zIndex)
   - Paste before line 414 (before scrollable content)
   
3. **Remove zIndex(2f)** from headers
   - No longer needed - headers are in natural layout flow
   
4. **Add imePadding to content Column** (line 420)
   - Content area responds to keyboard insets
   - Pushes content up when keyboard appears
   
5. **TerminalView keeps weight(1f)**
   - Scrolls within bounded viewport (between headers and composer)

## Expected Scrolling Behavior

### Before keyboard:
```
┌─────────────────────────────────────┐
│ SessionInfoBar  (fixed by position) │  ← ~40dp
│ Tab Row         (fixed by position) │  ← ~48dp
├─────────────────────────────────────┤
│ Terminal View   (scrollable)        │  ← remaining ~350dp
│ (space for 20+ lines of output)     │
├─────────────────────────────────────┤
│ Composer        (input box)         │  ← ~60dp
└─────────────────────────────────────┘
```

### When keyboard appears (imePadding activates):
```
┌─────────────────────────────────────┐
│ SessionInfoBar  (fixed, stays)      │  ← ~40dp
│ Tab Row         (fixed, stays)      │  ← ~48dp
├─────────────────────────────────────┤
│ Terminal View   (scrollable, shrinks)│  ← now ~250dp
│ (space for 14+ lines, can scroll)   │
├─────────────────────────────────────┤
│ Composer        (moves up)          │  ← ~60dp
│ ┌─────────────────────────────────┐ │
│ │ Soft Keyboard                   │ │  ← Android system
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

## Implementation Steps

1. Find line 426: `Spacer(modifier = Modifier.height(80.dp))` → DELETE
2. Find lines 669-732: Column with headers and zIndex(2f) → CUT
3. Find line 414: `Column(modifier = Modifier.fillMaxSize(),)` → MOVE cut block BEFORE this line
4. Remove `.zIndex(2f)` from the pasted headers Column
5. Add `.imePadding()` to the scrollable content Column at line 420
6. Verify TerminalView still has `.weight(1f).fillMaxWidth()`

## Result

- Headers reserve space naturally
- Content area scrolls within bounds between headers and composer
- Terminal scroll works within visible viewport
- When keyboard appears, content moves up with imePadding
- No artificial Spacer, no zIndex overlays
