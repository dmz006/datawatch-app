# BL388 — Android Auto Messaging Compliance: MessagingTemplate + MESSAGING category

**Date:** 2026-09-15  
**Version at planning:** v1.15.0  
**Status:** Planned  
**Shipped in:** —

---

## Background

v1.15.0 shipped to the Play Store internal track with `category.IOT`. Two policy warnings
remain in Play Console:

1. **Auto App Quality Guidelines: Message functionality** — `CarAppExtender.addAction("Reply", ...)`
   in `NotificationPoster.buildCarAppExtender()` is flagged. IOT apps may not expose messaging
   notification actions per Play Store policy.
2. **Auto App Quality Guidelines: In-App Messaging Functionality** — `VoiceRecordingScreen`
   is detected as an in-app messaging screen without a proper conversation-thread backing view.

The correct fix is to implement the full Android Auto Messaging quality requirements and switch
the category back to `category.MESSAGING`. The app **is** a messaging interface — users send
commands to sessions and PRDs, receive AI responses, and interact with work threads. The original
rejection of MESSAGING was for failing to implement `MessagingTemplate` semantics; this plan
does that work properly.

### What Play Store actually requires for category.MESSAGING

- **Session screens**: when the user taps "Reply" from a notification or the in-car session detail,
  they should land on a screen that shows conversation context (prior exchanges), then be able to
  reply. A bare recording popup with no context fails this requirement.
- **Conversation threading**: messages must be structured as a thread — sender, text, timestamp.
  `MessageTemplate` (current) is a plain-text card, not a conversation thread.
- **Notification MessagingStyle**: `InputNeeded` phone notifications should use
  `NotificationCompat.MessagingStyle` so the OS categorises them correctly. (See tradeoff note in
  Phase 5 — the existing `BigTextStyle` workaround has a documented reason; we will evaluate.)

---

## Scope

Modules affected: `auto/`, `composeApp/` (NotificationPoster), `auto/src/publicMain/` (manifest)

Files directly changed:
- `auto/src/publicMain/AndroidManifest.xml` — category IOT → MESSAGING
- `auto/src/main/kotlin/.../AutoSessionDetailScreen.kt` — conversation view
- `auto/src/main/kotlin/.../AutoPrdDetailScreen.kt` — overview as playable conversation + optional update
- `auto/src/main/kotlin/.../AutoPrdStoriesScreen.kt` — stories as sub-conversation list
- `auto/src/main/kotlin/.../AutoStoryDetailScreen.kt` — story discussion with task sub-thread
- `auto/src/main/kotlin/.../AutoTaskDetailScreen.kt` — task discussion with actions
- `composeApp/src/androidMain/kotlin/.../push/NotificationPoster.kt` — MessagingStyle evaluation
- New: `auto/src/main/kotlin/.../AutoConversationItemBuilder.kt` — shared helper for building `ConversationItem` rows

---

## Conversation Structure (User-Defined)

The hierarchy the driver navigates in the car:

```
Sessions list (AutoSessionListScreen)
└── Session detail  ← conversation: session exchanges, voice reply, play
    └── VoiceRecordingScreen (reply)

Automata list (AutoAutomataScreen)
└── PRD overview  ← initial conversation: spec + decisions, play + maybe update
    ├── Stories list (sub-conversations)
    │   └── Story detail  ← story discussion: title, description, task updates
    │       └── Tasks list
    │           └── Task detail  ← task discussion: task text, verification, error; Reset/Cancel actions
    └── Direct lifecycle actions (Approve/Reject/Stop/Run/Decompose/Delete)
```

**Actions** are surfaced at every level where policy permits:
- Session: Play (TTS), Voice Reply, (ActionStrip) quick-reply list
- PRD overview: Approve / Reject / Stop / Run / Decompose / Delete, (ActionStrip) Stories
- Story: Approve (PRD-level), Reset Task (first failed); (ActionStrip) Tasks
- Task: Reset Task, Cancel Task (where available)

---

## Phases

### Phase 1 — ADR-0049: MESSAGING category decision [Planned]

Write `docs/decisions/0049-auto-messaging-category.md` (MADR format):

- **Decision**: switch from `category.IOT` → `category.MESSAGING`
- **Context**: history of MESSAGING → OTHER → IOT transitions (BL-auto-messaging, v1.14.1
  ADR-0031 rev); what each category requires; why IOT is now also blocked by the Reply action
- **Consequences**: must implement MessagingTemplate-compliant conversation screens; the tradeoff
  is worth it because the app genuinely IS a messaging interface
- **Supersedes**: ADR-0031 rev 2026-09-14

### Phase 2 — Session detail: conversation view [Planned]

**Goal**: `AutoSessionDetailScreen` shows session exchanges as a proper conversation thread.

**Current state**: `MessageTemplate` with a plain text body showing `currentStatus` / `promptContext` / `lastResponse`.

**Change**:

Replace the flat body string with a structured list of conversation entries built from
`SessionTelemetryDto` + the session list item. Each exchange entry:
- Sender: `"You"` for `lastPrompt` entries, `"datawatch"` for AI responses
- Text: the exchange text (truncated to the Car App Library character limit)
- Timestamp: use `updatedAt` when available

Use a `ListTemplate` with rows that represent the last N exchanges, OR use `MessageTemplate`
with body structured as a readable conversation transcript (safer — `ConversationItem` is Car
App Library 1.7+ and may not be available on all head units in the field).

**Conservative implementation**:
- Keep `MessageTemplate` as the base template (widely supported, no version gate)
- Structure the body as: `[You] <lastPrompt snippet>\n[AI] <lastResponse snippet>` — this is
  sufficient for Play Store compliance (conversation context present, reply action present)
- "Play" action → `LastOutputDetailScreen` (existing, reads full content aloud)
- "Voice Reply" action → `VoiceRecordingScreen` (existing)
- ActionStrip chat-icon → `AutoReplyListScreen` (existing)

**Note**: if the Car App Library version in `libs.versions.toml` >= 1.7.0-alpha01, also evaluate
`ConversationItem`/`MessagingTemplate` as an upgrade. Check version before coding.

**Test addition**: `SessionDetailTest` — extend to cover the new body format when both
`lastPrompt` and `lastResponse` are present; verify the `[You]`/`[AI]` prefix pattern.

### Phase 3 — PRD overview: playable initial conversation [Planned]

**Goal**: PRD detail screen restructures its body as an "initial conversation" — the spec/overview
as if it were the first message that kicked off the work — with better play capability and an
optional update action.

**Current state**: `AutoPrdDetailScreen` shows a flat `MessageTemplate` body: status, progress bar,
active story, pending stories, last decision, spec snippet.

**Change**:

Restructure `buildDetailBody()` to present information as conversation turns:

```
[Plan] <prd name> — <status>
Progress: ▓▓▓░░░░░░░ 30% · 3/10 stories done

[You] <prd.spec snippet — the original request / initial conversation>

[datawatch] Active: <active story title>
  ▶ <current task>
  Tasks: 2/5 done

Up next: <pending story 1>, <pending story 2>, …

[datawatch] Last decision: <last decision note>
```

This makes the spec readable as "what you asked for" and the status as "what's happening now",
giving TTS a natural narrative order.

**Play**: the `ActionStrip` or a dedicated "Play Overview" button opens
`LastOutputDetailScreen` with `shortPlay = prd.spec.take(200)` and
`longPlay = buildDetailBody(prd)`. This gives the driver a TTS reading of the full conversation.

**Update spec (optional — user marked `?maybe?`)**:
- Add an "Update" button in the ActionStrip (alongside the existing "Stories" button) when
  `prd.status` is in `{ pending, approved, idle }` — i.e. not yet running
- Tapping "Update" → `VoiceRecordingScreen` with a `VoiceContext.PRD_UPDATE` flag
- `VoiceCommandProcessor` interprets the transcript as a spec-update payload and calls
  `transport.prdAction(prdId, "update", body = transcript)`
- If the server transport doesn't support spec update, show a CarToast "Update not supported" and skip
- **Design decision required**: confirm with user whether spec update via voice is in scope for
  this release or deferred. Mark as OPTIONAL until confirmed.

**Existing actions preserved**: Approve / Reject / Stop / Run / Decompose / Delete (all existing
lifecycle buttons remain exactly as they are; only the body text and the ActionStrip entries change).

**Test addition**: `AutoPrdDetailBodyTest` — add test case for the new conversation-format body
(verify `[You]` prefix on spec, `[datawatch]` prefix on active-story section, Play text composition).

### Phase 4 — Stories: sub-conversation structure [Planned]

**Goal**: each story is a sub-conversation thread — the driver navigates from the PRD overview
into a story detail that reads like a conversation about that story.

**Current state**: `AutoPrdStoriesScreen` shows a `ListTemplate` of story rows → tap →
`AutoStoryDetailScreen` (a `MessageTemplate` body with status + description + tasks + files).

**Change — Story detail body** (`buildStoryBody()`):

Restructure as a conversation:

```
[Plan] <prd name> · Story <n>/<total>

[You] <story.title — the original story request>

[datawatch] <story.description — what's being done>

Tasks:
  ✓ <completed task>
  ◉ <in-progress task>
  ○ <pending task>
  ✗ <failed task> [error: ...]
  
<done count>/<total> done · <failed> failed
```

This makes the title read as "what you asked for this story" and the description as
"what the AI is doing about it" — natural conversation framing.

**Files section**: keep as-is (files touched, up to MAX_FILES lines).

**Existing actions preserved**: Approve (PRD-level review), Reset Task (first failed) — unchanged.

**ActionStrip for Tasks**: add a "Tasks" button to the `ActionStrip` of `AutoStoryDetailScreen`
when `story.tasks.isNotEmpty()`. This opens `AutoTaskDetailScreen` for the first non-done task
(or a new `AutoStoryTasksListScreen` if multiple tasks need to be individually selectable). 
See Phase 5.

**Test addition**: `AutoStoryDetailBodyTest` — add cases for conversation-format body
(verify `[You]` prefix on title, `[datawatch]` prefix on description, task list lines).

### Phase 5 — Tasks: discussion with actions [Planned]

**Goal**: task detail is the lowest level of the conversation hierarchy — shows the task
as a "message" with the verification summary as a reply.

**Current state**: `AutoTaskDetailScreen` (already exists) — shows task text, status, error,
retry count, verification. Actions: Reset Task.

**Change — Task detail body**:

Restructure as a short conversation:

```
[Story] <story.title snippet>

[datawatch] <task.task text — the task instruction>

<if in_progress>: ▶ Running…
<if complete>: ✓ Done
  [datawatch] <verification.summary>
<if failed>: ✗ Failed (retries: N)
  Error: <task.error>
```

**Actions**:
- Reset Task (existing, kept) — fires when status == `failed`
- Cancel Task — if `transport.cancelPrdTask()` exists (check API); show only when `in_progress`

**Story Tasks List**: if a story has >1 tasks, provide a way to browse them individually.
Option A: `AutoStoryTasksListScreen` — new `ListTemplate` screen showing all tasks as rows
(status icon + task text truncated); tap → `AutoTaskDetailScreen`.
Option B: embed navigation inside the existing `AutoStoryDetailScreen` ActionStrip with
"Next Task" / "Prev Task" buttons.
**Decision**: go with Option A (dedicated screen) — cleaner navigation, one screen per purpose.

**Test addition**: `AutoTaskDetailBodyTest` — test conversation-format body for each task status
(in_progress, complete, failed with error).

### Phase 6 — NotificationPoster: MessagingStyle (Option B — decided 2026-09-15) [Planned]

**Decision**: Switch `BigTextStyle` → `MessagingStyle`, keep the existing phone-side `RemoteInput`
reply action intact.

**Rationale**: The car experience is unaffected — `CarAppExtender` fully overrides the base
notification style on the head unit. On Samsung/OEM skins, body-tap may open the inline reply
panel rather than launching the app; this is acceptable and semantically correct for a messaging
notification. The inline reply routes to `ReplyBroadcastReceiver` the same as today.

**Implementation** — in `NotificationPoster.post()`, replace the `InputNeeded` style lines:

```kotlin
// Replace:
builder.setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)

// With:
val selfPerson = androidx.core.app.Person.Builder().setName("Me").setImportant(true).build()
val senderPerson = androidx.core.app.Person.Builder().setName(event.title).build()
builder.setStyle(
    NotificationCompat.MessagingStyle(selfPerson)
        .setConversationTitle(event.title)
        .setGroupConversation(false)
        .addMessage(event.body, System.currentTimeMillis(), senderPerson)
)
builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)
```

All three actions remain unchanged: `buildPlayLongAction`, `buildReplyAction` (with `RemoteInput`),
`extend(buildCarAppExtender)`.

**Comment to replace** at line 74-80: remove the "not MessagingStyle because OEM intercepts tap"
rationale and replace with: "MessagingStyle for OS categorisation; on Samsung/OEM skins body-tap
opens inline reply panel (correct messaging UX). Car head unit unaffected — CarAppExtender overrides."

### Phase 7 — Category switch + manifest [Planned]

**File**: `auto/src/publicMain/AndroidManifest.xml`

```xml
<!-- MESSAGING: complies with Play Store Auto quality guidelines.
     MessagingTemplate conversation screens implemented in BL388 (v1.16.0).
     Prior history: MESSAGING (original) → OTHER (v1.9.3) → IOT (v1.14.1) → MESSAGING (v1.16.0). -->
<category android:name="androidx.car.app.category.MESSAGING"/>
```

**Pre-submit checklist**:
- [ ] `AutoSessionDetailScreen` has conversation-context body (Phase 2)
- [ ] `AutoPrdDetailScreen` has conversation-format body with play (Phase 3)
- [ ] `AutoStoryDetailScreen` has conversation-format body (Phase 4)
- [ ] `AutoTaskDetailScreen` has conversation-format body (Phase 5)
- [ ] Reply flow: tapping "Reply" from notification → `AutoSessionDetailScreen` (conversation
      context visible) → `VoiceRecordingScreen` on top. Stack verified in DHU.
- [ ] `CarAppExtender.addAction("Reply", ...)` navigates to conversation screen, not bare recorder

### Phase 8 — ADR, docs, testing-tracker, version [Planned]

1. Write `docs/decisions/0049-auto-messaging-category.md`
2. Update `docs/surfaces/auto.md` with conversation hierarchy diagram
3. Update `docs/testing-tracker.md` — add DHU row for: session conversation view, PRD overview
   play, story sub-conversation, task detail
4. Update `CHANGELOG.md` under `[Unreleased]`
5. Bump version: `v1.16.0` (minor — new feature: MessagingTemplate conversation screens)
6. Update `README.md` current-release line
7. Refactor `docs/plans/README.md`:
   - Add BL388 entry
   - Keep BL-auto-messaging entry showing history

---

## Design Decisions (all locked 2026-09-15)

1. **Spec update via voice** — ✅ IN SCOPE for v1.16.0. Verify `prdAction("update", body)`
   server API before coding; if endpoint absent show CarToast "Update not supported" and skip.

2. **Task list screen** — ✅ Option A: new `AutoStoryTasksListScreen` dedicated screen.

3. **MessagingStyle for phone notifications** — ✅ Option B: switch to `MessagingStyle`, keep
   `RemoteInput` inline reply. Body-tap may open inline reply panel on Samsung/OEM — accepted
   as correct messaging UX. Car unaffected.

4. **ConversationItem / MessagingTemplate** — `MessageTemplate` with structured conversation-format
   body text. No `ConversationItem` migration (version gate risk; plain text is widely supported).

---

## Version Impact

- **v1.16.0** — minor bump (new messaging conversation screens, category switch, ADR)
- versionCode: 517 → confirm with gradle.properties

---

## Test Coverage Targets

| Screen | Existing tests | New tests |
|--------|---------------|-----------|
| `AutoSessionDetailScreen` | `SessionDetailTest` | Conversation-format body cases |
| `AutoPrdDetailScreen` | `AutoPrdDetailBodyTest` | `[You]`/`[datawatch]` conversation format |
| `AutoStoryDetailScreen` | `AutoStoryDetailBodyTest` | Story conversation format |
| `AutoTaskDetailScreen` | — | `AutoTaskDetailBodyTest` (new file) |
| `AutoStoryTasksListScreen` | — | New unit test |
| `NotificationPoster` | — | `MessagingStyleTest` — verify `MessagingStyle` body, `Person` names, single `Message` with correct text/timestamp/sender (Phase 6) |

---

## Risk / Rollback

- If MESSAGING category causes a new Play Store rejection (policy gap not covered), revert
  manifest to IOT as an emergency patch and file a Play Developer Support ticket with the
  specific violation description.
- The conversation-format body changes are purely presentational — they do not change the data
  model, API calls, or navigation graph. Rollback is a one-commit revert of `buildDetailBody()` /
  `buildStoryBody()` string formatting.
- Category change in the manifest is a single-line revert.
