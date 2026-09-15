# BL388 — Android Auto Messaging Compliance: Conversation Screens + MESSAGING category

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
rejection of MESSAGING was for failing to implement conversation-thread semantics; this plan
does that work properly.

### What Play Store requires for category.MESSAGING

- **Session screens**: when the user taps "Reply" from a notification, they land on a screen that
  shows conversation context (prior exchanges) before the voice recorder. A bare recording popup
  with no context fails this requirement.
- **Conversation threading**: messages must read as a thread — sender, content, natural order.
  `MessageTemplate` with a flat body string qualifies IF it shows the conversation exchange
  (not just current status). This is the approach chosen (see Design Decisions).
- **Notification MessagingStyle**: `InputNeeded` phone notifications use
  `NotificationCompat.MessagingStyle` so the OS categorises them as messaging conversations.

---

## Scope

Modules affected: `auto/`, `composeApp/` (`NotificationPoster`), `auto/src/publicMain/` (manifest)

Files directly changed:
- `auto/src/publicMain/AndroidManifest.xml` — category IOT → MESSAGING
- `auto/src/main/kotlin/.../AutoSessionDetailScreen.kt` — conversation-context body
- `auto/src/main/kotlin/.../AutoPrdDetailScreen.kt` — initial-conversation body + voice update
- `auto/src/main/kotlin/.../AutoStoryDetailScreen.kt` — story sub-conversation body
- `auto/src/main/kotlin/.../AutoTaskDetailScreen.kt` — task-discussion body
- New: `auto/src/main/kotlin/.../AutoStoryTasksListScreen.kt` — task list per story
- `auto/src/main/kotlin/.../voice/VoiceCommandProcessor.kt` — `PRD_UPDATE` command
- `composeApp/src/androidMain/kotlin/.../push/NotificationPoster.kt` — `MessagingStyle`
- `docs/decisions/0049-auto-messaging-category.md` — ADR

New string resources (must appear in **all 5 locale files** — see Locale Gate section):
- `auto_action_update` ("Update") — PRD overview ActionStrip button
- `auto_action_tasks` ("Tasks") — story detail ActionStrip button  
- `auto_action_play_overview` ("Play Overview") — PRD overview play button
- `auto_conv_you` ("You") — conversation prefix for user turn
- `auto_conv_datawatch` ("datawatch") — conversation prefix for AI turn
- `auto_conv_story` ("Story") — conversation prefix for story context header
- `auto_conv_plan` ("Plan") — conversation prefix for PRD context header

---

## Conversation Structure

The hierarchy the driver navigates in the car:

```
Sessions list (AutoSessionListScreen)
└── Session detail  ← conversation: [You]/[datawatch] exchange, Play, Voice Reply
    └── VoiceRecordingScreen (reply)

Automata list (AutoAutomataScreen)
└── PRD overview  ← initial conversation: spec as [You], status as [datawatch], Play + Update
    ├── Stories list → tap story
    │   └── Story detail  ← sub-conversation: story title as [You], description as [datawatch]
    │       └── Tasks list (AutoStoryTasksListScreen) → tap task
    │           └── Task detail  ← task discussion: instruction + verification/error
    └── Direct lifecycle actions (Approve / Reject / Stop / Run / Decompose / Delete)
```

Actions at every level where Auto policy permits:
- Session: Play (TTS), Voice Reply, ActionStrip chat-icon → quick-reply list
- PRD overview: Approve / Reject / Stop / Run / Decompose / Delete; ActionStrip: Stories, Update
- Story: Approve (PRD-level), Reset Task (first failed); ActionStrip: Tasks
- Task: Reset Task, Cancel Task (where supported)

---

## Design Decisions (all locked 2026-09-15)

1. **Spec update via voice** — ✅ IN SCOPE for v1.16.0. Check `prdAction("update", body)`
   server API first; show `CarToast("Update not supported")` and skip if absent.

2. **Task list navigation** — ✅ Option A: new `AutoStoryTasksListScreen` (`ListTemplate`);
   each row = status icon + task text; tap → `AutoTaskDetailScreen`.

3. **MessagingStyle for phone notifications** — ✅ Option B: `MessagingStyle` with `RemoteInput`
   kept. Samsung/OEM body-tap opens inline reply panel — accepted as correct messaging UX.
   Car head unit unaffected (CarAppExtender overrides base style entirely).

4. **ConversationItem** — ❌ Not used anywhere. Reason corrected 2026-09-15: `ConversationItem`
   and `CarMessage` ARE present in Car App Library 1.7.0 (already our dependency — no version
   gate). However, `ConversationItem` is wrong for this app's UX:
   - It renders as a message-inbox row (last message preview, sender name, unread badge)
   - `AutoSessionListScreen` is a **status monitor**, not a message inbox — it needs colored
     status dots, state text, urgency sort, and progress bars
   - `ItemList` cannot mix `Row` and `ConversationItem` — switching would drop all status
     signals from the list
   - The Play Store compliance requirement is met by showing conversation context on the
     **detail** screen before the reply flow, not by using `ConversationItem` on the list
   Keep `Row`-based `ListTemplate` for all list screens. Keep `MessageTemplate` with structured
   `[You]`/`[datawatch]` body text for all detail screens.

---

## Phases

### Phase 1 — ADR-0049: MESSAGING category decision [Planned]

Write `docs/decisions/0049-auto-messaging-category.md` (MADR format):

- **Decision**: switch from `category.IOT` → `category.MESSAGING`
- **Context**: full history — MESSAGING (original) → OTHER (v1.9.3, BL-auto-messaging) →
  IOT (v1.14.1, ADR-0031 rev) → MESSAGING (v1.16.0, this ADR). Why IOT is now also blocked
  (IOT category prohibits messaging notification actions). Why MESSAGING is semantically correct
  for this app (users send commands to AI sessions and receive responses = messaging interface).
- **Consequences**: conversation-thread screens implemented in BL388; Play Store warnings
  expected to clear; rollback path is a one-line manifest revert to IOT.
- **Supersedes**: ADR-0031 rev 2026-09-14

**Commit**: `docs(adr): ADR-0049 — switch Auto category MESSAGING (BL388)`

---

### Phase 2 — Session detail: conversation-context body [Planned]

**File**: `AutoSessionDetailScreen.kt` → `buildBody()`

**Change**: restructure the flat body string into a conversation exchange. Use string resources
for prefixes (locale-safe).

```
[You] <lastPrompt first non-blank line — truncated to ~120 chars>

[datawatch] <lastResponse / currentStatus / promptContext — existing priority logic>
```

When `lastPrompt` is null (session never waited for input), show only the AI turn.
When both are null, fall back to `sessionState.name` as today.

All existing actions unchanged: Play → `LastOutputDetailScreen`, Voice Reply → `VoiceRecordingScreen`,
ActionStrip chat-icon → `AutoReplyListScreen`.

**String resources added**: `auto_conv_you`, `auto_conv_datawatch` (see Locale Gate).

**Test addition**: `SessionDetailTest` — add cases: both turns present, only AI turn, neither turn.
Verify `[You]` prefix appears on `lastPrompt`, `[datawatch]` prefix on response.

**Commit**: `feat(auto): session detail — conversation-context body (BL388 Phase 2)`

---

### Phase 3 — PRD overview: initial conversation + voice update [Planned]

**File**: `AutoPrdDetailScreen.kt` → `buildDetailBody()` + `onGetTemplate()`

**Change — body** (restructure `buildDetailBody()`):

```
[Plan] <prd name> — <status>
Progress: ▓▓▓░░░░░░░ 30% · 3/10 stories done

[You] <prd.spec — the original request>

[datawatch] Active: <active story title>
  ▶ <current task>  Tasks: 2/5 done

Up next: <pending story 1>, <pending story 2>, …

[datawatch] Last decision (<actor>): <note>
```

**Change — Play Overview**: the existing lifecycle buttons already include an ActionStrip. Add a
"Play Overview" action alongside "Stories" that opens `LastOutputDetailScreen` with
`shortPlay = prd.spec?.take(200)` and `longPlay = buildDetailBody(prd)`.

**Change — Update spec**: add an "Update" ActionStrip button when
`prd.status.lowercase() in { "pending", "approved", "idle", "" }`. Tapping pushes
`VoiceRecordingScreen(carContext, prdId, prdName, context = VoiceContext.PRD_UPDATE)`.
`VoiceCommandProcessor` handles `PRD_UPDATE`: calls `transport.prdAction(prdId, "update", body = transcript)`.
If transport returns `UnsupportedOperationException`, show `CarToast("Update not supported")`.

**String resources added**: `auto_conv_you`, `auto_conv_datawatch`, `auto_conv_plan`,
`auto_action_play_overview`, `auto_action_update` (see Locale Gate).

All existing lifecycle actions (Approve/Reject/Stop/Run/Decompose/Delete) preserved unchanged.

**Test addition**: `AutoPrdDetailBodyTest` — add cases: `[You]` on spec, `[datawatch]` on
active-story section, `[Plan]` header, progress bar format. Verify Play text composition.
Add `VoiceCommandProcessorTest` case for `PRD_UPDATE` parse + dispatch.

**Commit**: `feat(auto): PRD overview — initial conversation body + voice spec update (BL388 Phase 3)`

---

### Phase 4 — Stories: sub-conversation structure [Planned]

**File**: `AutoStoryDetailScreen.kt` → `buildStoryBody()`

**Change** (restructure body):

```
[Plan] <prd name> · Story <n>/<total>

[You] <story.title>

[datawatch] <story.description>

Tasks:
  ✓ <completed task>
  ◉ <in-progress task>
  ○ <pending task>
  ✗ <failed task>  Error: <error>

<done>/<total> done · <failed> failed

Files: <file 1>, <file 2>, …
```

**ActionStrip for Tasks**: add "Tasks" button when `story.tasks.isNotEmpty()`. Tapping pushes
`AutoStoryTasksListScreen(carContext, prdId, prdStatus, story)`.

**String resources added**: `auto_conv_you`, `auto_conv_datawatch`, `auto_conv_story`,
`auto_action_tasks` (see Locale Gate).

Existing actions preserved: Approve (PRD review), Reset Task (first failed).

**Test addition**: `AutoStoryDetailBodyTest` — add cases: `[You]` on title, `[datawatch]` on
description, `[Plan]` context header, task list markers. Cover the "Tasks" button condition.

**Commit**: `feat(auto): story detail — sub-conversation body + Tasks strip button (BL388 Phase 4)`

---

### Phase 5 — Tasks: list screen + discussion detail [Planned]

**New file**: `AutoStoryTasksListScreen.kt`

A `ListTemplate` screen. Each row: status icon (`ic_dot_green` / `ic_dot_red` / `ic_dot_gray`) +
task text (truncated to list-row limit) + `setOnClickListener` → `AutoTaskDetailScreen`.
Header action: BACK. Title: `"<story title> — Tasks"`.

**File**: `AutoTaskDetailScreen.kt` — restructure body:

```
[Story] <story.title snippet>

[datawatch] <task.task text>

▶ Running…          (if in_progress)
✓ Done              (if complete)
  [datawatch] <verification.summary>
✗ Failed (retries: N)   (if failed)
  Error: <task.error>
```

**Actions**: Reset Task (existing, kept for `failed`). Add Cancel Task: show only when
`status == "in_progress"` and `transport.cancelPrdTask(prdId, taskId)` is available (check API;
skip gracefully if absent).

**String resources added**: `auto_conv_datawatch`, `auto_conv_story` (shared with Phase 4).

**Test additions**:
- New `AutoStoryTasksListTest` — verify row count, status icon selection, click routing
- New `AutoTaskDetailBodyTest` — cover in_progress, complete (with verification), failed (with error)

**Commit**: `feat(auto): task list screen + task discussion detail (BL388 Phase 5)`

---

### Phase 6 — NotificationPoster: MessagingStyle [Planned]

**File**: `NotificationPoster.kt`

In `post()`, replace the `InputNeeded` style block:

```kotlin
// Before:
builder.setStyle(NotificationCompat.BigTextStyle().bigText(event.body))
builder.setCategory(NotificationCompat.CATEGORY_MESSAGE)

// After (Option B — decided 2026-09-15):
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

All three actions remain: `buildPlayLongAction`, `buildReplyAction` (with `RemoteInput`),
`extend(buildCarAppExtender)`.

Replace the comment at lines 74-80: the new comment should state that `MessagingStyle` is used
for proper OS categorisation; on Samsung/OEM skins body-tap opens the inline reply panel
(correct messaging UX, not a bug); the car head unit is unaffected because `CarAppExtender`
overrides the base style entirely.

**Test addition**: `NotificationPosterTest` (new file in `composeApp`) —
`MessagingStyle` body set, `Person` self-name = "Me", sender name = `event.title`,
one `Message` with correct text and non-zero timestamp, `CATEGORY_MESSAGE` set,
three actions present (Play, Reply, CarAppExtender).

**Commit**: `fix(push): InputNeeded notifications — MessagingStyle (BL388 Phase 6)`

---

### Phase 7 — Category switch + manifest [Planned]

**File**: `auto/src/publicMain/AndroidManifest.xml`

```xml
<!-- MESSAGING: complies with Play Store Auto quality guidelines.
     Conversation screens implemented in BL388 (v1.16.0).
     History: MESSAGING → OTHER (v1.9.3) → IOT (v1.14.1) → MESSAGING (v1.16.0, ADR-0049). -->
<category android:name="androidx.car.app.category.MESSAGING"/>
```

**Pre-submit gate** — all must be true before committing this file:
- [ ] Phase 2 committed and passing: session detail shows `[You]`/`[datawatch]` exchange
- [ ] Phase 3 committed and passing: PRD overview shows spec as `[You]`, status as `[datawatch]`
- [ ] Phase 4 committed and passing: story detail shows conversation format
- [ ] Phase 5 committed and passing: task list + task detail committed
- [ ] Phase 6 committed and passing: `MessagingStyle` in place
- [ ] DHU smoke test: tap "Reply" notification → `AutoSessionDetailScreen` (conversation context
      visible) → `VoiceRecordingScreen` on top; conversation exchange readable before recorder
- [ ] `./gradlew :auto:lintRelease` passes with no new warnings

**Commit**: `fix(auto): AndroidManifest — category IOT → MESSAGING (BL388 Phase 7, ADR-0049)`

---

### Phase 8 — Docs, locale, release [Planned]

This phase is a single release commit that bundles all documentation, version bump, and
artifacts. Follow the full release checklist below.

**Documentation updates (all in the release commit)**:

1. `docs/decisions/0049-auto-messaging-category.md` — write the ADR (MADR format, see Phase 1)
2. `docs/surfaces/auto.md` — update with the new conversation hierarchy diagram
3. `docs/testing-tracker.md` — add DHU rows:
   - Session conversation view (Tested / Validated)
   - PRD overview play + voice update (Tested / Validated)
   - Story sub-conversation (Tested / Validated)
   - Task list + task detail (Tested / Validated)
   - MessagingStyle phone notification (Tested / Validated on Pixel + Samsung)
4. `CHANGELOG.md` — promote `[Unreleased]` to `[1.16.0]` with all changes; include reuse audit
   line: "Reuse audit — extends existing `VoiceRecordingScreen` + `LastOutputDetailScreen` primitives;
   new `AutoStoryTasksListScreen` follows `AutoAutomataScreen` `ListTemplate` pattern."
5. `docs/plans/README.md` — mark BL388 `✅ Closed in v1.16.0`; move to Closed section
6. `docs/operations.md` — note category change and expected Play Console warning resolution
7. `docs/parity-status.md` — flip any rows that BL388 closes
8. `docs/implementation.md` — add `VoiceContext.PRD_UPDATE` entry under Auto voice commands
9. `README.md`:
   - Update `**Current release: v1.16.0 (DATE).**` line
   - Update AAOS section to mention conversation hierarchy (session exchanges, PRD overview,
     story sub-conversations, task detail)
10. `docs/plans/historical-plans/` — move `2026-09-15-bl388-auto-messaging-compliance.md`
    here after the release commit confirms shipping

---

## Locale Gate

Every string listed in the Scope section must appear in **all 5 locale files**
(`composeApp/src/androidMain/res/values[-de|-es|-fr|-ja]/strings.xml`) before the release
commit. This is a blocking requirement per the Per-Sprint Rules Audit.

| Resource key | EN default | Notes |
|---|---|---|
| `auto_conv_you` | `You` | Conversation prefix for driver's turn |
| `auto_conv_datawatch` | `datawatch` | Conversation prefix for AI turn |
| `auto_conv_story` | `Story` | Context header prefix |
| `auto_conv_plan` | `Plan` | Context header prefix |
| `auto_action_update` | `Update` | PRD ActionStrip button |
| `auto_action_tasks` | `Tasks` | Story ActionStrip button |
| `auto_action_play_overview` | `Play Overview` | PRD play button |

Translations for DE/ES/FR/JA: provide natural-language equivalents; do not machine-translate
without review. If uncertain, use EN values as placeholders and flag for translation review.

---

## Per-Phase Commit Discipline

Each phase = one logical commit (per AGENT.md "Every logical change gets its own commit").
Do not batch phases into a single commit. Commit message format: `type(scope): description (BL388 Phase N)`.

After every commit: `./gradlew test` must pass before pushing. After the Phase 7 (manifest)
commit: also run `./gradlew :auto:lintRelease`.

---

## Release Checklist (Phase 8 gate)

Run in order before tagging v1.16.0:

### 1. Pre-release security scan
```bash
./gradlew detekt ktlintCheck
./gradlew :composeApp:lintRelease :auto:lintRelease
./gradlew dependencyCheckAnalyze
```
All HIGH severity findings must be fixed or documented with justification.

### 2. Locale gate
```bash
for key in auto_conv_you auto_conv_datawatch auto_conv_story auto_conv_plan \
           auto_action_update auto_action_tasks auto_action_play_overview; do
  grep -r "$key" composeApp/src/androidMain/res/values*/strings.xml
done
```
Every key must appear in all 5 locale files (values, values-de, values-es, values-fr, values-ja).

### 3. Full test suite
```bash
./gradlew test connectedAndroidTest
```
All unit tests must pass. New tests from Phases 2-6 must be present and green.

### 4. Version bump — 2 files, same commit
```bash
# gradle.properties:  DATAWATCH_APP_VERSION=1.16.0  DATAWATCH_APP_VERSION_CODE=517
# Version.kt:         VERSION = "1.16.0"             VERSION_CODE = 517
grep -E 'DATAWATCH_APP_VERSION=|VERSION\s*=\s*"' \
  gradle.properties \
  shared/src/commonMain/kotlin/com/dmzs/datawatchclient/Version.kt
# Both lines must show 1.16.0
```

### 5. Build both release AABs + APK + mapping
```bash
./gradlew :composeApp:bundlePublicTrackRelease   # Play Store upload
./gradlew :composeApp:bundleDevRelease            # Internal testing only
./gradlew :composeApp:assemblePublicTrackRelease  # GitHub + sideload APK
# Verify artifacts exist:
ls composeApp/build/outputs/bundle/publicTrackRelease/composeApp-publicTrack-release.aab
ls composeApp/build/outputs/bundle/devRelease/composeApp-dev-release.aab
ls composeApp/build/outputs/apk/publicTrack/release/composeApp-publicTrack-release.apk
ls composeApp/build/outputs/mapping/publicTrackRelease/mapping.txt
```

### 6. SHA256SUMS
```bash
sha256sum \
  composeApp/build/outputs/bundle/publicTrackRelease/composeApp-publicTrack-release.aab \
  composeApp/build/outputs/bundle/devRelease/composeApp-dev-release.aab \
  composeApp/build/outputs/apk/publicTrack/release/composeApp-publicTrack-release.apk \
  composeApp/build/outputs/mapping/publicTrackRelease/mapping.txt \
  > SHA256SUMS
```

### 7. Tag and push
```bash
git tag -a v1.16.0 -m "v1.16.0 — Auto messaging compliance (BL388)"
git push && git push --tags
```

### 8. CI runner health check
```bash
rtk gh run list --repo dmz006/datawatch-app --limit 20
```
Wait for the release workflow to complete. If any run fails: fix root cause, push fix,
re-trigger. Do NOT advance to Play Console upload until runner list shows all green for this tag.
Delete fixed-but-failed runs: `gh run delete <run-id> --repo dmz006/datawatch-app`.

### 9. Play Console upload sequence
- Upload **public** AAB to Internal Testing track
- Upload **dev** AAB to Internal Testing track (internal variant only — never promote)
- Wait ≥ 1 hour on Internal Testing before promoting to Closed Testing
- Monitor Play Console for policy warnings — expect both "Message functionality" and
  "In-App Messaging Functionality" warnings to clear after review (can take 24-72 hours)

### 10. GitHub release
```bash
gh release create v1.16.0 \
  composeApp/build/outputs/bundle/publicTrackRelease/composeApp-publicTrack-release.aab \
  composeApp/build/outputs/apk/publicTrack/release/composeApp-publicTrack-release.apk \
  composeApp/build/outputs/mapping/publicTrackRelease/mapping.txt \
  SHA256SUMS \
  --title "v1.16.0 — Auto messaging compliance" \
  --notes-file /tmp/release-notes.md \
  --verify-tag
```

### 11. Asset retention
```bash
bash scripts/delete-past-minor-assets.sh
```
Keep: every major release, the latest minor (v1.16.0), and the latest patch on the latest minor.
Delete binary assets (not release notes) from superseded releases.

### 12. Background shell cleanup
```bash
pgrep -a -u "$USER" bash | grep 'shell-snapshots/snapshot-bash-'
# kill each watcher PID found
```

### 13. Per-Sprint Rules Audit
After the release commit, verify every line:
- [ ] AGENT.md rules re-read; applicable rules noted in the sprint commit message
- [ ] `docs/testing-tracker.md` updated with DHU rows for all new Auto screens
- [ ] Mobile-Parity: no server-side PWA change required by BL388 (all client-side)
- [ ] Locale gate: all 7 new string keys present in all 5 locale files
- [ ] Version bump: `gradle.properties` + `Version.kt` both show `1.16.0` / `517`
- [ ] `./gradlew build` passes clean (zero new lint/detekt/ktlint warnings)
- [ ] `./gradlew test` passes (all unit tests green including new Phase 2-6 tests)
- [ ] `CHANGELOG.md` updated with `[1.16.0]` block including reuse audit line
- [ ] `README.md` current-release line updated
- [ ] `docs/plans/README.md` BL388 marked closed, shipped items → closed section

### 14. Plan archival
After confirming v1.16.0 is ingested by Play Console and GitHub release is live:
```bash
mv docs/plans/2026-09-15-bl388-auto-messaging-compliance.md \
   docs/plans/historical-plans/
git add -A
git commit -m "docs(plans): archive BL388 — shipped in v1.16.0"
```

---

## Version Impact

- **v1.16.0** — minor bump (new screens + conversation-format bodies + category change)
- `DATAWATCH_APP_VERSION=1.16.0`, `DATAWATCH_APP_VERSION_CODE=517`
- Current: `gradle.properties` shows `1.15.0` / `516` — both files must be updated together

---

## Test Coverage Targets

| Screen / Component | Existing tests | New tests in BL388 |
|---|---|---|
| `AutoSessionDetailScreen` | `SessionDetailTest` | Conversation body: both turns, AI-only, neither |
| `AutoPrdDetailScreen` | `AutoPrdDetailBodyTest` | `[You]`/`[datawatch]` format, Play text, `[Plan]` header |
| `AutoStoryDetailScreen` | `AutoStoryDetailBodyTest` | Conversation format, Tasks strip condition |
| `AutoStoryTasksListScreen` | — | New: row count, status icon, click routing |
| `AutoTaskDetailScreen` | — | New `AutoTaskDetailBodyTest`: in_progress, complete+verification, failed+error |
| `NotificationPoster` | — | New `NotificationPosterTest`: `MessagingStyle` body, `Person` names, 3 actions present |
| `VoiceCommandProcessor` | `VoiceCommandTest` | `PRD_UPDATE` parse + dispatch |

Coverage target: ~90-100% on all new/changed logic per AGENT.md testing rules.
DHU live validation required for every new Auto screen before `Validated=Yes` in testing-tracker.

---

## Risk / Rollback

- If MESSAGING category causes a new Play Store rejection, revert manifest to `category.IOT`
  as a one-line emergency patch. File a Play Developer Support ticket with the specific
  violation text. The conversation-format body changes do not affect the category decision.
- The body-text restructuring is purely presentational — no data model, API, or navigation
  graph changes. One-commit revert restores the flat-string format.
- If `MessagingStyle` causes regression on a specific device (body-tap stops opening app),
  revert `NotificationPoster` to `BigTextStyle` as an emergency patch and reassess.
