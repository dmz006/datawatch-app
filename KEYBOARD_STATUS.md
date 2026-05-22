# Keyboard Behavior - Current Status & Analysis

## What We Know (Not Assumed)

### Observable Facts:
1. ✓ App does not crash when keyboard appears
2. ✓ Terminal content is visible (not black) when keyboard is open
3. ✓ Headers are visible at the top
4. ✓ Reply input is present at bottom
5. ✓ Keyboard appears when Reply input is tapped
6. ? Terminal is scrollable with keyboard open (untested)
7. ? Space allocation is optimal (untested)
8. ? No black space or wasted areas (untested - looks ok from screenshots)

### What We Changed (In Order):

**Original Problem:**
- Terminal disappeared (black screen) when keyboard appeared

**Change 1:** Layout Restructure
- Removed 80dp Spacer
- Moved headers from overlay (zIndex) to natural layout position
- Result: Headers visible, terminal visible (no longer black)

**Change 2:** Added imePadding to Content Column
- Outer column: no imePadding
- Content column: imePadding added
- Composer: imePadding
- Result: Keyboard appeared but layout still had issues

**Change 3:** Consolidated imePadding to Outer Column Only
- Outer column: imePadding added (single point)
- Content column: imePadding removed
- Composer: imePadding removed
- Result: Terminal visible, keyboard working, no black space

**Current State:** Change 3 (outer column has imePadding only)

---

## What We Don't Know Yet

### Critical Unknowns:

1. **Terminal Scrollability with Keyboard**
   - Can you scroll the terminal when keyboard is visible?
   - Does scroll work smoothly?
   - Can you reach all content?

2. **Space Efficiency**
   - How many lines of terminal are visible without keyboard?
   - How many lines with keyboard?
   - Is the difference appropriate (~4-6 lines)?

3. **Scroll Constraints**
   - Do headers stay fixed when scrolling?
   - Can content scroll behind headers?
   - Is there clipping/overlap?

4. **Gaps and Layout**
   - Is there appropriate spacing between Reply input and keyboard?
   - Is there any unused/black space?
   - Is the layout balanced?

---

## Root Cause Analysis

### Why Terminal Disappeared (Original Problem)

The old layout had:
```
Box(imePadding=2f overlay, zIndex=2f)  ← Headers on top
Column(weight=1f)                        ← Content
  └─ Spacer(80dp)                        ← Artificial space
  └─ TerminalView                        ← Terminal
```

When keyboard appeared:
- 80dp Spacer was there to leave room for headers
- But headers were overlays, not in the layout flow
- Outer Column didn't respond to keyboard
- Terminal squeezed to nothing or disappeared

### Why It's Better Now

```
Column(fillMaxSize, imePadding)         ← Outer responds to keyboard
  ├─ Headers Column()                   ← Natural position, ~88dp
  ├─ Content Column(weight=1f)          ← Shrinks when keyboard appears
  │   └─ TerminalView(weight=1f)        ← Fills available space
  └─ Composer Box()                     ← Pushed up by outer padding
```

When keyboard appears:
- Outer Column gets imePadding (bottom padding = keyboard height)
- All children push up proportionally
- weight(1f) on content makes it shrink to fit remaining space
- Headers stay fixed (not part of flex)
- Composer stays at bottom (auto size)

**This math should work correctly.**

---

## Space Math (Theory)

### Without Keyboard:
```
StatusBar                = 24dp (system)
Headers (SessionInfo)    = 40dp
Headers (TabRow)         = 48dp
Available for content    = 1080 - 24 - 88 = 968dp

Content (weight=1f)      = 968 - 60 (composer) = 908dp
  └─ TerminalView        = ~13-14 lines visible

Composer                 = 60dp
```

### With Keyboard:
```
StatusBar                = 24dp
Headers                  = 88dp (same, fixed)
Keyboard height          = 360dp (typical Android)

Available after outer padding = 1080 - 24 - 360 = 696dp

Content (weight=1f)      = 696 - 88 (headers) - 60 (composer) = 548dp
  └─ TerminalView        = ~7-8 lines visible
                         = ~400-500dp

Composer                 = 60dp

Expected line loss: (908 - 548) / average_line_height ≈ 6-7 lines
```

### If We See Wrong Numbers:
- Fewer than 6 lines lost: space is being wasted somewhere
- More than 8 lines lost: multiple sources of padding/waste
- Black space: layout constraints wrong
- Terminal not scrollable: parent constraining child scroll

---

## The 5+5 Iterations We Designed

### 5 Diagnostic Iterations (What's Happening?):
1. **Space Accounting Problem** - Is space being calculated correctly?
2. **imePadding Behavior** - Is padding adding vs shrinking properly?
3. **Weight(1f) Over-Expansion** - Is content expanding too much?
4. **Composer Positioning** - Is composer sized correctly?
5. **Terminal Scroll Constraints** - Can terminal scroll? Is range wrong?

### 5 Solution Iterations (How to Fix?):
1. **Hardcoded Heights** - Explicit control but not responsive
2. **Localized imePadding** - Only Composer responds to keyboard (TESTED, needs eval)
3. **Explicit Weight + Constraints** - Add wrapContentHeight, explicit flex hints
4. **Custom Window Inset** - Manually detect keyboard height and add spacer
5. **LazyColumn** - Better scroll handling (over-engineered)

**Best candidate:** Iteration 3 (explicit height hints) - needs proper implementation

---

## Next Steps (For Manual Testing)

**You need to manually test and measure:**

1. Open the app
2. Go to Sessions → Open a session detail view
3. Count terminal lines without keyboard
4. Open keyboard by tapping Reply input
5. Count terminal lines with keyboard
6. Try scrolling in terminal area
7. Check if everything works as expected

**Use the manual testing protocol in `MANUAL_TEST_PROTOCOL.md`**

---

## Risk Assessment

### Current Implementation is:
- ✓ Safe (won't crash)
- ✓ Visually acceptable (terminal not black)
- ? Functionally complete (unknown without testing)
- ? Optimal for usability (unknown without testing)

### Before deploying to production:
- Verify scrolling works with keyboard open
- Measure space efficiency (line counts)
- Check for any black space or gaps
- Validate on different screen sizes

### If Manual Testing Shows Problems:
- Have 5 solution iterations ready to implement
- Solution 3 is most likely to work next
- Would need 1-2 more build/test cycles

---

## Summary

**Current state:** Keyboard handling implemented, terminal not disappearing, but full functionality untested. Need manual verification of scrolling behavior and space efficiency before confirming fix is complete.

**Timeline:** 
- Current build is v1.0.0/248 on phone
- Ready for manual testing NOW
- Solution 3 ready to deploy if needed within 30 minutes
- Full fix should be complete with confidence within 1-2 cycles

