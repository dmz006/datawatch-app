# Keyboard Behavior - 5 Solution Iterations

## Problem Context
Terminal is now visible (not black), but space allocation and scrolling behavior need verification and optimization.

---

## Solution Iteration 1: Remove imePadding Completely, Use Explicit Height Distribution

**Theory:** Instead of imePadding (which adds padding), explicitly distribute height based on keyboard presence

```kotlin
val keyboardVisible = remember { mutableStateOf(false) }

Column(fillMaxSize) {
  // Headers - fixed height
  Column(modifier = Modifier.height(88.dp)) { 
    SessionInfoBar()
    TabRow()
  }
  
  // Content - gets remaining space minus composer
  Column(
    modifier = Modifier
      .weight(1f)
      .fillMaxWidth()
  ) {
    TerminalView()
  }
  
  // Composer - fixed height, always at bottom
  Box(modifier = Modifier.height(60.dp)) {
    ReplyComposer()
  }
}
```

**Pros:**
- Explicit control over each section's height
- No imePadding complications
- Clear space allocation

**Cons:**
- Manual height values hardcoded
- Doesn't respond to keyboard automatically
- Need to detect keyboard visibility separately

---

## Solution Iteration 2: Use navigationBarsPadding + imePadding Combo

**Theory:** Separate concerns - navigationBarsPadding for system bars, imePadding only for composer

```kotlin
Column(
  modifier = Modifier
    .fillMaxSize()
    .navigationBarsPadding()  // System nav bar
) {
  // Headers
  Column() { ... }
  
  // Content
  Column(modifier = Modifier.weight(1f)) {
    TerminalView()
  }
  
  // Composer - only this responds to keyboard
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .imePadding()  // ONLY the composer
  ) {
    ReplyComposer()
  }
}
```

**Theory:** Composer pads itself above keyboard. Content fills remaining space without padding interference.

**Pros:**
- Keyboard response localized to composer
- Content doesn't lose space to padding

**Cons:**
- Composer might not move up enough
- Gap possible between content and composer

---

## Solution Iteration 3: Proper Weight + Flex Layout

**Theory:** Use proper Compose flex semantics with correct weight distribution

```kotlin
Column(
  modifier = Modifier
    .fillMaxSize()
    .imePadding()
) {
  // Headers - don't flex, fixed size
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
  ) {
    SessionInfoBar()
    TabRow()
  }
  
  // Content - flexes to fill available
  Column(
    modifier = Modifier
      .weight(1f)  // Takes 100% of remaining after headers/composer
      .fillMaxWidth()
  ) {
    TerminalView(modifier = Modifier.weight(1f).fillMaxWidth())
  }
  
  // Composer - shrinks to fit content
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .wrapContentHeight()
  ) {
    ReplyComposer()
  }
}
```

**Theory:** Explicit flex hints tell Compose exactly how to distribute space

**Pros:**
- Clear flex hierarchy
- wrapContentHeight() prevents over-expansion
- weight(1f) only on content

**Cons:**
- More verbose
- Still uses imePadding on outer column

---

## Solution Iteration 4: Custom Window Inset Handler

**Theory:** Instead of imePadding, manually read keyboard height and create padding

```kotlin
val keyboardHeight = WindowInsets.ime.getBottom(LocalDensity.current)

Column(
  modifier = Modifier.fillMaxSize()
) {
  // Headers
  Column() { ... }
  
  // Content
  Column(modifier = Modifier.weight(1f)) {
    TerminalView()
  }
  
  // Composer
  Box(modifier = Modifier.fillMaxWidth()) {
    ReplyComposer()
  }
  
  // Explicit keyboard spacer
  if (keyboardHeight > 0) {
    Spacer(modifier = Modifier.height(keyboardHeight.dp))
  }
}
```

**Theory:** Detect keyboard height explicitly, add spacer only when needed

**Pros:**
- Full control over spacing
- Can add logic based on keyboard state
- Explicit and debuggable

**Cons:**
- Complex implementation
- Relies on density conversions
- Requires IME window inset access

---

## Solution Iteration 5: Compose LazyColumn with Correct Clipping

**Theory:** Terminal scroll behavior might need LazyColumn semantics instead of manual scroll

```kotlin
Column(
  modifier = Modifier
    .fillMaxSize()
    .imePadding()
) {
  // Headers with zIndex to stay on top during scroll
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .zIndex(1f)  // Stay above terminal during scroll
  ) {
    SessionInfoBar()
    TabRow()
  }
  
  // Scrollable content area
  LazyColumn(
    modifier = Modifier
      .weight(1f)
      .fillMaxWidth()
  ) {
    item {
      TerminalView(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(minHeight = 200.dp)
      )
    }
  }
  
  // Composer
  Box() {
    ReplyComposer()
  }
}
```

**Theory:** LazyColumn provides better scroll clipping and space management than manual Column

**Pros:**
- Better scroll behavior
- Proper clipping bounds
- zIndex ensures headers stay visible

**Cons:**
- LazyColumn adds complexity
- TerminalView not optimized for lazy loading
- Might have performance impact

---

## Ranking Solutions by Viability

### Most Likely to Fix (Iteration 2)
**navigationBarsPadding + localized imePadding on Composer**
- Simplest change
- Targets the real issue (double padding)
- Low risk of side effects

### Second Best (Iteration 3)
**Explicit weight + wrapContentHeight**
- More explicit control
- Better semantic description
- Slightly more complex

### Moderate Risk (Iteration 4)
**Custom Window Inset Handler**
- More control but complex
- Requires understanding IME API
- Good for debugging

### Avoid (Iteration 1)
**Hardcoded heights**
- Not responsive
- Breaks on different screen sizes

### Last Resort (Iteration 5)
**LazyColumn**
- Over-engineered for this use case
- Terminal rendering doesn't benefit from lazy loading

---

## Recommended Testing Sequence

1. **Test current state** - measure visible lines, scrollability
2. **Try Solution 2** - remove imePadding from outer column, keep only on composer
3. **Measure improvement** - count visible lines, check scrolling
4. **If not sufficient, try Solution 3** - add wrapContentHeight hints
5. **Validate** - ensure keyboard response works, no black space, optimal space usage

