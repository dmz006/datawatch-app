# BL30 — Android Auto task-level lifecycle parity

**Status:** ✅ SHIPPED v1.8.0 (2026-09-14)

---

## Problem

Auto was capped at 5 navigation levels: Home → Automata → PRDDetail → Stories → StoryDetail.
This left no room for a dedicated task detail screen, which meant:
- No per-task Requeue or Cancel Task actions
- No way to see full task error, verification result, or retry count
- Cancel Story required navigating into story detail without an action to do so

## Navigation restructure

Old:
```
Home(1) → Automata(2) → PRDDetail(3) → StoriesList(4) → StoryDetail(5=MAX)
```
New:
```
Home(1) → Automata(2) → PRDDetail(3) → AutoPrdStoriesScreen(4, stateful) → AutoTaskDetailScreen(5)
```

`AutoPrdStoriesScreen` is now stateful:
- **Stories-list mode**: story rows tap → `selectedStory = story; invalidate()` (no push)
- **Story-detail mode**: `ListTemplate` with story overview row + task rows; task tap → push `AutoTaskDetailScreen`
- ActionStrip in story-detail mode: "◀ Stories", Approve (if PRD in review + story awaiting_approval), Reset Task, Cancel Story
- System BACK from story-detail still pops to PRD Detail (correct — skipping stories list is acceptable)

## New screen: `AutoTaskDetailScreen` (depth 5)

`MessageTemplate` showing:
- Status, retry count
- Full task spec
- Error (up to 200 chars)
- Verification summary, severity, issues (up to 3)

Actions by task status:
- `failed` → "Requeue" (requeuePrdTask) + "Cancel Task" (cancelPrdTask)
- `pending` / `in_progress` → "Cancel Task" only

## `buildStoryBody` enhancements

- Failed tasks: show "  Retries: N" when `retryCount > 0`
- Completed tasks: show "  ✓ [verification summary]" when `verification.summary` present

## Deferred (future BL30 work)

- (b) PRD spec / story description edit from Auto
- (e) Progress indicators, estimated completion, last-run timestamps (not in DTOs today)
