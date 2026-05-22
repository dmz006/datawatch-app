# Keyboard Behavior - 5 Design Iterations

## Problem
When soft keyboard appears, terminal becomes invisible (black space). This suggests:
- imePadding is being applied in conflicting ways
- Terminal view is being squeezed to zero height
- Multiple imePadding modifiers competing for space

## Current (Broken) Structure
```
Column(fillMaxSize)
  ├─ Headers (auto)
  ├─ Column(weight=1f, imePadding)  [CONTENT]
  │   └─ TerminalView(weight=1f)
  └─ Box(imePadding)                [COMPOSER]
```

**Problem:** Both content Column AND Composer Box have imePadding. This creates:
- Content Column adds bottom padding (shrinks terminal)
- Composer Box adds bottom padding (pushes composer up)
- Double padding causes excessive space loss
- TerminalView → 0 height

---

## Iteration 1: Remove imePadding from Content Column
```
Column(fillMaxSize)
  ├─ Headers (auto)
  ├─ Column(weight=1f)  [CONTENT, no imePadding]
  │   └─ TerminalView(weight=1f)
  └─ Box(imePadding)    [COMPOSER, keeps imePadding]
```

**Theory:** Only Composer responds to keyboard. Content Column doesn't add padding.
**Expected:** Composer moves up, content doesn't lose space as much
**Risk:** Terminal might still be occluded if Composer's imePadding isn't enough

---

## Iteration 2: Move imePadding to Outer Column Only
```
Column(fillMaxSize, imePadding)  [OUTER, has imePadding]
  ├─ Headers (auto, no imePadding)
  ├─ Column(weight=1f)  [CONTENT, no imePadding]
  │   └─ TerminalView(weight=1f)
  └─ Box()              [COMPOSER, no imePadding]
```

**Theory:** Single point of keyboard response - outer Column handles all insets
**Expected:** Entire layout responds uniformly, no double-padding
**How it works:** 
- Outer Column padding-bottom = keyboard height
- All children pushed up together
- Composer naturally ends up above keyboard

---

## Iteration 3: Add Minimum Height to Terminal
```
Column(fillMaxSize, imePadding)  [OUTER]
  ├─ Headers (auto)
  ├─ Column(weight=1f, modifier.heightIn(minHeight=100.dp))  [CONTENT]
  │   └─ TerminalView(weight=1f, modifier.heightIn(minHeight=100.dp))
  └─ Box()  [COMPOSER]
```

**Theory:** Even with space constraints, TerminalView has minimum visible size
**Expected:** Terminal always visible, won't disappear completely
**Risk:** Might be too restrictive, could overflow

---

## Iteration 4: Weighted Space Distribution
```
Column(fillMaxSize, imePadding)  [OUTER]
  ├─ Headers (auto, weight=0)
  ├─ Column(weight=0.7f)  [CONTENT - 70% of remaining]
  │   └─ TerminalView(weight=1f)
  ├─ Spacer(height=0.3f)  [MIDDLE - 30% of remaining]
  └─ Box()  [COMPOSER]
```

**Theory:** Explicitly reserve space for terminal
**Expected:** Terminal gets 70% of space, Composer gets 30% when keyboard appears
**How it works:** Weight distribution ensures terminal doesn't vanish

---

## Iteration 5: Nested Box with Scoped Padding
```
Column(fillMaxSize)  [OUTER, no imePadding]
  ├─ Headers (auto)
  ├─ Box(modifier.weight(1f))  [CONTENT WRAPPER]
  │   └─ Column()
  │       └─ TerminalView(weight=1f)
  └─ Box(imePadding)  [COMPOSER WITH KEYBOARD RESPONSE]
      └─ ReplyComposer
```

**Theory:** Wrap content in Box to control flex behavior separately from padding
**Expected:** Content Box flexes, Composer Box only has imePadding
**How it works:** Box respects weight(1f) independently from imePadding

---

## Testing Each Iteration

### Success Criteria:
1. ✓ Terminal visible when keyboard appears
2. ✓ Composer above keyboard
3. ✓ Headers stay fixed
4. ✓ Terminal can still scroll
5. ✓ No black space/invisible areas

### Test Sequence:
1. Click reply input
2. Wait for keyboard (1 second)
3. Screenshot
4. Check if terminal visible
5. Scroll terminal
6. Check response

---

## Most Likely Solution: Iteration 2
Moving imePadding to the outer Column is the cleanest approach because:
- Single source of keyboard response (outer Column)
- No conflicting nested imePadding calls
- Natural layout space distribution
- Children inherit padding via parent

**Implementation:**
```
Box(
  modifier = Modifier
    .padding(padding)  // Scaffold padding
    .navigationBarsPadding()
    .fillMaxSize()
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .imePadding()  // MOVED HERE FROM INNER COLUMN
  ) {
    // Headers
    // Content
    // Composer
  }
}
```
