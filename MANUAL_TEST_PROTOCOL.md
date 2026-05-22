# Manual Testing Protocol - Keyboard & Scrolling Behavior

## Current Implementation
- Outer Column: `fillMaxSize()`, `imePadding()`
- Headers Column: `fillMaxWidth()` (no special constraints)
- Content Column: `weight(1f)`, `fillMaxWidth()` (no imePadding)
- Composer Box: `fillMaxWidth()` (no imePadding)

This means:
- Entire outer Column responds to keyboard insets
- All children inherit the padding effect
- Headers should stay fixed
- Content should shrink to accommodate keyboard
- Composer should move above keyboard

---

## Step-by-Step Manual Test

### Part 1: Open App & Navigate to Session Detail

1. **Close the app completely**
   - Long-press datawatch icon → "App info" → "Force stop"
   - OR: Settings → Apps → Datawatch → Force stop

2. **Open datawatch app fresh**
   - Tap the Datawatch icon

3. **Tap "Sessions" tab** (bottom navigation)

4. **Tap on any session** (try the "datawatch server" or "Datawatch App" card)
   - You should now see the **SessionDetailScreen**
   - Headers at top: SessionInfoBar + Tab Row
   - Terminal content in middle
   - Reply input at bottom

**Checkpoint 1:** Confirm you see the session detail view with terminal content visible

---

### Part 2: Test Without Keyboard

**Screenshot 1: BEFORE keyboard**
- Take a mental note (or screenshot) of:
  - How many lines of terminal are visible?
  - Where does the terminal content end?
  - How much space between terminal and Reply input?

**Measure:**
- Count visible terminal text lines: _____ lines
- Terminal starts after: Headers
- Terminal ends before: Reply input

---

### Part 3: Test With Keyboard

1. **Tap on the "Reply..." input field**
   - The soft keyboard should appear from bottom
   - The app layout should adjust

**Checkpoint 2:** Confirm keyboard appears without the app crashing

**Screenshot 2: WITH keyboard visible**
- Take a mental note (or screenshot) of:
  - Are headers still visible at top?
  - How many terminal lines are now visible?
  - Is Reply input visible above keyboard?
  - Is there a gap between input and keyboard?
  - Is there black space anywhere?

**Measure:**
- Headers visible: YES / NO
- Terminal still visible: YES / NO
- How many terminal lines now visible: _____ lines
- Gap between Reply input and keyboard: None / Small / Large
- Black/unused space: YES / NO (where?)

**Comparison:**
- Difference in visible lines: _____ lines lost
- Expected loss: ~4-6 lines (keyboard is ~360dp on 1080p screen)
- If loss is much more: space is wasted
- If loss is much less: content overlapping keyboard

---

### Part 4: Test Scrolling

1. **With keyboard still visible, try scrolling the terminal**
   - Swipe UP in the terminal area (from lower to upper)
   - Should reveal earlier terminal output

**Checkpoint 3:** Terminal responds to scroll gesture

2. **Scroll up several times**
   - Does content move?
   - Can you scroll all the way to the beginning?
   - Do headers stay fixed?
   - Does content go behind headers?

**Measure:**
- Terminal scrollable: YES / NO
- Headers stay fixed: YES / NO
- Content visible above keyboard after scroll: YES / NO

---

### Part 5: Test Keyboard Close & Space Recovery

1. **Close the keyboard**
   - Tap outside input, or press back button
   - Keyboard should disappear

2. **Observe layout change**
   - Does terminal expand back to original size?
   - Do you get back to the same view as Screenshot 1?

**Measure:**
- Terminal lines back to original count: YES / NO
- Layout matches pre-keyboard: YES / NO

---

## Success Criteria Checklist

### Must Have ✓
- [ ] Headers visible at all times (with and without keyboard)
- [ ] Terminal visible with keyboard open (no black space)
- [ ] Reply input visible above keyboard
- [ ] Keyboard appears when input is tapped
- [ ] Keyboard disappears when dismissed
- [ ] Space recovers when keyboard closes

### Should Have ✓
- [ ] Terminal scrollable with keyboard open
- [ ] Reasonable space for terminal content (~6-10 lines visible with keyboard)
- [ ] Minimal gap between Reply input and keyboard
- [ ] Headers don't overlap content
- [ ] No wasted/unused space

### Nice to Have ✓
- [ ] Smooth animation when keyboard appears/disappears
- [ ] Scroll doesn't jump or stutter
- [ ] Headers remain accessible while scrolling

---

## Observations to Record

### Without Keyboard:
```
Visible terminal lines: _____
Space available: Good / Adequate / Cramped
Headers visibility: Clear / Partially obscured / Hidden
Composer position: Bottom of screen / Somewhere else
```

### With Keyboard:
```
Terminal still visible: YES / NO
Visible terminal lines now: _____
Lines lost to keyboard: _____ (expected ~4-6)
Reply input above keyboard: YES / NO
Space between Reply and keyboard: _____ dp / Small / Large
Headers still visible: YES / NO
Scrolling works: YES / NO
Content behind headers: YES / NO
Black space anywhere: YES / NO
```

### Scroll Test:
```
Scroll up works: YES / NO
Content moves: YES / NO
Reaches top: YES / NO
Headers stay fixed: YES / NO
Smooth/janky: Smooth / Janky / Stuttering
```

---

## Expected Ideal Behavior

**Without Keyboard:**
- ~12-14 lines of terminal visible
- Headers: 88dp
- Reply input: ~60dp
- Free space: 0 (fills screen)

**With Keyboard (360dp keyboard):**
- ~6-8 lines of terminal visible (roughly half)
- Headers: 88dp (same)
- Reply input: ~60dp (same)
- Keyboard: 360dp
- Total: 88 + 6*20 + 60 + 360 = 568dp used of 1080dp (some overlap/padding expected)

**Scrolling:**
- Should work smoothly
- Should reveal earlier content
- Headers should not scroll away
- No jumping or clipping

---

## If Something Is Wrong

### Issue: Terminal disappears (black screen)
- Solution: imePadding is over-constraining content
- Next iteration: Remove imePadding from outer column

### Issue: Only 2-3 lines visible with keyboard
- Solution: Space distribution is inefficient
- Next iteration: Check weight(1f) is working

### Issue: Keyboard overlaps terminal
- Solution: imePadding not working properly
- Next iteration: Verify imePadding is on correct element

### Issue: Gap between input and keyboard is huge
- Solution: Composer Box has excessive padding
- Next iteration: Check for nested padding issues

### Issue: Terminal doesn't scroll
- Solution: Parent Column constraining internal scroll
- Next iteration: Check scroll modifiers on parent

