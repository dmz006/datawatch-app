# Keyboard Behavior - Detailed Diagnosis & Solutions

## Current State Screenshot Analysis

**What I See:**
- SessionInfoBar: VISIBLE at top (datawatch server 0229 claude-code johnnyjohnny)
- Tab Row: VISIBLE (tmux, channel, Status, Asr, ↑)
- Terminal Content: VISIBLE (multiple lines of build logs)
- Composer: VISIBLE (Reply... input at bottom)
- Soft Keyboard: VISIBLE at very bottom

**What's NOT black anymore:** ✓ Terminal is visible

**But what needs investigation:**
- How much terminal space is actually available?
- Is terminal content scrollable when keyboard is visible?
- Are headers taking too much space?
- Is there wasted/unused space somewhere?
- Is the space distribution balanced?

---

## 5 Diagnostic Iterations - What's Actually Happening?

### Iteration 1: Space Accounting Problem
**Hypothesis:** Total screen height is being consumed incorrectly
- StatusBar (system) = ~24dp
- Headers (SessionInfoBar + Tab Row) = ~88dp
- Keyboard = ~360dp (typical Android keyboard)
- Composer (Reply input) = ~60dp
- **Terminal Available = 1080 - 24 - 88 - 360 - 60 = ~548dp**

**What to check:** Is terminal getting ~548dp of space? Or less?

**Current issue:** Terminal might be getting less than optimal due to:
- imePadding adding padding instead of shrinking
- Weight distribution not accounting for keyboard height
- Composer taking more space than needed

---

### Iteration 2: imePadding Behavior Problem
**Hypothesis:** imePadding adds padding, not margin - this might be creating unused space

```
Outer Column with imePadding:
├─ Headers (88dp) ← FIXED, no padding
├─ Content (weight=1f) ← GETS PADDING APPLIED INSIDE
│   └─ TerminalView ← SQUEEZED BY PADDING
└─ Composer (60dp) ← GETS PADDING APPLIED INSIDE
```

**Problem:** When imePadding is on outer Column, it adds bottom padding to the entire Column. This padding is INSIDE the Column bounds, pushing content UP but not actually creating new space outside.

**Effect:**
- Outer Column: fillMaxSize = 1080dp
- With imePadding: adds 360dp bottom padding
- Available height: 1080 - 360 = 720dp for all children
- Headers: 88dp
- Content + Composer: 720 - 88 = 632dp total

But this is divided by weight(1f) for content and auto for composer, which might not be optimal.

---

### Iteration 3: Weight(1f) Over-Expansion Problem
**Hypothesis:** Content Column with weight(1f) expands to fill ALL remaining space, even when it shouldn't

**Current layout:**
```
Column(fillMaxSize, imePadding)
  ├─ Headers (auto, ~88dp)
  ├─ Content (weight=1f) ← EXPANDS TO FILL ALL REMAINING
  └─ Composer (auto, ~60dp)
```

**When keyboard appears:**
- Remaining space = 1080 - imePadding(360) - Headers(88) - Composer(60) = 572dp
- Content gets ALL 572dp
- But composer is outside this weight calculation...

**Problem:** Weight distribution might be working against us when keyboard is involved.

---

### Iteration 4: Composer Positioning Problem
**Hypothesis:** Composer Box needs its own space management, not just imePadding

**Current issue:**
- Composer has NO width constraint issues (fillMaxWidth ✓)
- But Composer height might be expanding unnecessarily
- imePadding on Composer might add padding when it shouldn't

**Effect:** Even though we removed imePadding from Composer, the layout might still be giving it too much space or wrong positioning.

---

### Iteration 5: Terminal Scrolling Constraint Problem
**Hypothesis:** Terminal is visible but maybe not scrollable, or scroll range is wrong

**What we can't see from screenshot:**
- Can the terminal scroll? Is it locked?
- How many lines are actually visible?
- Can you scroll past the headers?

**Possible issue:**
- TerminalView might be getting bounded correctly visually, but scroll behavior might be constrained by parent Column
- Parent Column might be clipping scroll attempts
- Scroll state might not match visual bounds

---

## Summary of Diagnostic Findings

1. **Terminal IS visible** (good) ✓
2. **Space allocation might be suboptimal** (unknown)
3. **Keyboard response works** (visible keyboard appears) ✓
4. **Terminal scrollability unknown** (needs testing)
5. **Space might be wasted** (headers, composer, padding positioning unclear)

**Key Questions to Answer:**
- How many lines of terminal are visible with keyboard open vs. closed?
- Can terminal scroll when keyboard is open?
- Is there large unused space somewhere?
- Are headers taking too much space?

---

## Indicators to Look For

**In next screenshot with keyboard visible:**
1. Count visible lines of terminal text
2. Check if there's a gap/space between terminal and keyboard
3. Look for any unused black areas
4. See if headers are taking up too much space
5. Verify composer isn't unnecessarily large

**Expected ideal behavior:**
- Terminal starts after headers
- Terminal fills as much space as possible
- Composer sits directly above keyboard with minimal gap
- No wasted/unused space
- Headers stay fixed and visible
