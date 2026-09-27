# Android Auto MESSAGING Category Constraints

Screens in `category.MESSAGING` (set in `DatawatchCarAppService`) are subject to
Samsung gearhead driving-mode enforcement that differs from ADAS-style head units.
Violations surface as "can't do that while driving" or "more than two actions" dialogs
and are caught at **runtime**, not at build time. Keep this reference when editing any
`auto/` screen.

## Hard rules (Samsung + standard AAOS enforcement)

| Rule | Limit | Violation symptom |
|---|---|---|
| ActionStrip actions per template | **≤ 2** | "more than two actions" / template rejected |
| ActionStrip action titles while driving | **Icon-only** (no `setTitle`) | "can't do that while driving" |
| `MessageTemplate.addAction()` buttons while driving | **0** (parked-only) | counted toward action cap → crash if total > 2 |
| Template type change across `invalidate()` on same screen | **Forbidden** | template rejected |
| `ListTemplate` pushed from a `MessageTemplate` screen while driving | **Blocked** | "can't do that while driving" |
| Screen depth (Car App Library hard cap) | **5 screens** | push rejected |

## Counting actions correctly

Samsung counts **all actions on a template** toward the cap, not just ActionStrip actions.
`MessageTemplate.addAction()` buttons count even though they are individually parked-only.

Example that **crashes** in driving mode:
```kotlin
MessageTemplate.Builder(body)
    .setActionStrip(ActionStrip.Builder()
        .addAction(icon1)   // #1
        .addAction(icon2)   // #2
        .build())
    .addAction(Action.Builder().setTitle("Send").build())   // #3 → crash
    .addAction(Action.Builder().setTitle("Retry").build())  // #4 → crash
    .build()
```

Correct (2 total, all in ActionStrip):
```kotlin
MessageTemplate.Builder(body)
    .setActionStrip(ActionStrip.Builder()
        .addAction(icon1)   // #1 send
        .addAction(icon2)   // #2 re-record
        .build())
    // NO .addAction() calls — use body text to guide the user instead
    .build()
```

## Guiding users without extra actions

When driving limits prevent labeled buttons, put the instruction in the **message body**:

```kotlin
MessageTemplate.Builder("\"$transcript\"\n\nTap ✉ to send  ·  tap 🎤 to re-record  ·  ← to cancel")
```

The `Action.BACK` header action always acts as a free cancel — use it.

## Screen depth budget (MESSAGING path in this app)

```
AutoSummaryScreen          depth 1
  AutoSessionListScreen    depth 2
    AutoSessionDetailScreen  depth 3
      VoiceRecordingScreen   depth 4   ← max useful depth on session path
AutoPrdDetailScreen          depth 3
  (story detail in-place — no push)
  AutoTaskDetailScreen       depth 4
    VoiceRecordingScreen     depth 5   ← absolute limit
```

Never push beyond depth 5. Use in-place `selectedItem + invalidate()` patterns for
sub-detail views to avoid consuming depth slots.

## Checklist before shipping any `auto/` screen change

- [ ] Every `onGetTemplate()` path returns the **same template type** on the same screen instance
- [ ] Every ActionStrip has **≤ 2** icon-only actions
- [ ] **No** `MessageTemplate.addAction()` calls on any MESSAGING-path screen
- [ ] Navigation pushes stay within the depth budget above
- [ ] `ListTemplate` is never pushed from a `MessageTemplate` screen
