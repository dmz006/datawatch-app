# ADR-0049 — Android Auto MESSAGING Category Adoption

## Status
Accepted (2026-09-15) — supersedes ADR-0031 rev 2026-09-14

## Context

Play Store review flagged two policy warnings on v1.15.0 (versionCode 516):

1. **Auto App Quality Guidelines: Message functionality** — the app declares
   `CarAppExtender.addAction("Reply")` and `CarAppExtender.addAction("Play")` but is
   categorised as `category.IOT`. Play Store policy treats notification reply actions on an
   IOT app as undeclared messaging functionality.

2. **In-App Messaging Functionality** — `MessageTemplate` is used on session-detail and
   PRD-detail screens, triggering the messaging quality review checklist. IOT apps are not
   expected to use messaging templates; MESSAGING apps must fulfil the checklist.

ADR-0031 (rev 2026-09-14) switched from MESSAGING → IOT to escape the checklist after Play
Store review rejected the app for "no MessagingTemplate root screen showing historical
conversations". That change resolved the root-screen objection but introduced the new
notification-action warnings cited above.

The `CarAppExtender` Play and Reply notification buttons are critical to the app's voice-first
driving experience and cannot be removed. The session-detail, PRD-detail, story-detail, and
task-detail screens use `MessageTemplate` for its structured TTS-readable body and inline
action buttons, which is the correct template for the UX — these cannot be replaced.

## Decision

Switch `category.IOT` → `category.MESSAGING` and fulfil all MESSAGING quality requirements:

1. **Conversation body on every detail screen** — session detail, PRD detail (initial
   conversation: spec as `[You]`, status as `[datawatch]`), story detail (sub-conversation),
   and task detail (task instruction + result/error/verification) all show a
   `[You]` / `[datawatch]` conversation exchange before any reply flow. This satisfies the
   "historical conversation visible before reply" requirement.

2. **`MessagingStyle` phone notifications** — `NotificationPoster` switches from
   `BigTextStyle` to `NotificationCompat.MessagingStyle` with `androidx.core.app.Person`
   objects (self = "Me", sender = session title). The existing `RemoteInput` reply action is
   retained unchanged. Samsung/OEM body-tap may open the inline reply panel — this is
   accepted as correct messaging UX. The car head unit experience is unchanged because
   `CarAppExtender` overrides the base notification style entirely.

3. **Manifest category switch** — `AndroidManifest.xml` updated in Phase 7 of plan BL388,
   after all conversation-body changes are live, to avoid a partial compliance window.

### Why not keep IOT?

`category.IOT` requires removing the `CarAppExtender` Reply action or switching to a
non-messaging notification style. Both break the core voice-reply-from-car feature. IOT
cannot be retained without feature regression.

### Why not ConversationItem?

`ConversationItem` / `CarMessage` (Car App Library 1.7.0, `androidx.car.app.messaging.model`)
are present in the existing dependency but are not used. The session list is a status monitor
— urgency-sorted rows with coloured status dots and progress bars — not a message inbox.
`ItemList` cannot mix `Row` and `ConversationItem` items. The conversation-formatted
`[You]` / `[datawatch]` body in `MessageTemplate` satisfies policy without disrupting the
existing list UX.

## Consequences

- The app re-enters the MESSAGING quality checklist on next Play review.
- All session-detail, PRD-detail, story-detail, and task-detail screens show a
  conversation-formatted body, which is also better driver-facing UX (concise Q→A
  rather than a raw status dump).
- Phone notifications on Samsung and other OEM skins may open the inline reply panel on
  body-tap instead of launching the app. This is messaging-correct behaviour.
- The `CarAppExtender` Play and Reply actions continue to work identically; the car head
  unit is unaffected.
- Plan BL388 (`docs/plans/2026-09-15-bl388-auto-messaging-compliance.md`) tracks
  implementation across 8 phases; this ADR is the Phase 1 deliverable.
