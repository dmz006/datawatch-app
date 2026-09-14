# BL29 — PRD Full Lifecycle Management (v1.5.0) ✅ SHIPPED

**Date:** 2026-09-14  
**Version:** 1.5.0  
**Server prerequisite:** datawatch v8.27.0 (cancel_story, cancel_task, reset_task?force=true)

## New server endpoints (v8.27.0)

| Action | HTTP | Body |
|--------|------|------|
| Cancel story | `POST /api/autonomous/prds/{id}/cancel_story` | `{story_id, actor?, reason?}` |
| Cancel task | `POST /api/autonomous/prds/{id}/cancel_task` | `{task_id, actor?, reason?}` |
| Requeue task | `POST /api/autonomous/prds/{id}/reset_task` | `{task_id, force: true}` |
| Edit task spec | `POST /api/autonomous/prds/{id}/edit_task` | `{task_id, new_spec}` |

## Files changed

| File | Change |
|------|--------|
| `shared/.../dto/Dtos.kt` | Add `PrdStoryCancelRequestDto`, `PrdTaskCancelRequestDto`, `EditTaskRequestDto`; add `force` field to `PrdTaskResetRequestDto` |
| `shared/.../TransportClient.kt` | Add `cancelStory`, `cancelTask`, `requeueTask`, `editTask` |
| `shared/.../rest/RestTransport.kt` | Implement 4 new transport methods |
| `composeApp/.../AutonomousViewModel.kt` | Add `cancelStory`, `cancelTask`, `requeueTask`, `editTask`; add `approve(prdId, note?)` overload |
| `composeApp/.../PrdDetailDialog.kt` | StoryRow: Cancel button; TaskRow: Cancel/Re-run/Edit buttons; Approve: optional note dialog |
| `shared/.../RestTransportAutonomousTest.kt` | 6 new tests |
| `Version.kt` + `gradle.properties` | 1.4.0 → 1.5.0, 440 → 450 |

## UI rules

- Cancel story/task: confirm dialog with optional reason field; only shown for non-terminal statuses
- Requeue task: shown for `complete` and `cancelled` tasks; re-runs without confirmation
- Edit task spec: shown when PRD status is `needs_review`/`revisions_asked`; opens text edit dialog pre-populated with current spec
- Approve with note: approve button in PrdDetailDialog opens a dialog with optional note field; inline approve from card list remains one-tap (no note)

## Test plan

- 6 MockWebServer unit tests in `RestTransportAutonomousTest`
- Existing 252 tests must continue passing
