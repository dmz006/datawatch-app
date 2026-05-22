# SessionDetailScreen Layout Restructure

## Problem
- Terminal scrolling was severely limited (~2 lines only)
- Headers (SessionInfoBar + Tab Row) used zIndex overlay with artificial 80dp Spacer for offset
- When soft keyboard appeared, imePadding on Composer didn't push content up properly
- Mismatch between code order (headers at end) and visual order (headers on top via zIndex)

## Root Cause
Headers were implemented as **overlays** (position: fixed via zIndex) rather than **layout flow elements**. This created:
- Artificial space waste (80dp Spacer)
- Unpredictable scrolling behavior
- Disconnect between Composer keyboard response and content movement

## Solution
Restructured to proper **layout flow model** (like HTML/CSS flex):

### Before (broken)
```
Column(fillMaxSize)
  ├─ Column(weight=1f)  [CONTENT]
  │   ├─ Spacer(80.dp)  ← artificial space
  │   └─ Terminal
  ├─ Box(imePadding)    [COMPOSER]
  │   └─ ReplyComposer
  └─ Column(zIndex=2f)  [HEADERS]  ← overlay, wrong order
      ├─ SessionInfoBar
      └─ Tab Row
```

### After (fixed)
```
Column(fillMaxSize)
  ├─ Column()           [HEADERS]  ← first, no zIndex
  │   ├─ SessionInfoBar
  │   └─ Tab Row
  ├─ Column(weight=1f, imePadding) [CONTENT]  ← flex, responds to keyboard
  │   └─ Terminal(weight=1f)
  └─ Box(imePadding)    [COMPOSER]  ← bottom, responds to keyboard
      └─ ReplyComposer
```

## Changes Made

1. **Removed 80dp Spacer** (line 426)
   - Headers now reserve space naturally

2. **Moved headers to first position** (before content)
   - SessionInfoBar and Tab Row now at natural layout start
   - Removed `.zIndex(2f)` overlay behavior

3. **Added imePadding to content Column** (line 490)
   - Content area responds to keyboard insets
   - Pushes content up when soft keyboard appears

4. **Kept TerminalView with weight(1f)**
   - Scrolls within bounded viewport
   - Headers above, Composer below

## Behavior Verified

✓ **Headers fixed position** - SessionInfoBar/Tab Row don't scroll
✓ **Terminal scrolls** - Can scroll up to reveal history
✓ **Keyboard response** - Content moves up with imePadding when keyboard shows
✓ **No overlap** - Terminal content doesn't go behind headers
✓ **Proper bounds** - Terminal viewport = screen height minus headers minus composer

## Files Modified
- composeApp/src/androidMain/kotlin/com/dmzs/datawatchclient/ui/sessions/SessionDetailScreen.kt
  - Lines 414-732: Layout restructure
  - Removed line 426: 80dp Spacer
  - Added imePadding to line 490: content Column
  - Moved headers from line 669-732 to line 420-482
  - Removed duplicate headers section that had zIndex(2f)
