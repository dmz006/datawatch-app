# Android Auto audit — 2026-04-21

**Source:** `/home/dmz/workspace/datawatch-app/auto/` module.
**User directive (2026-04-21):** Auto-ready for morning testing.

---

## Current state

- **1 placeholder screen** (`PreMvpPlaceholderScreen`) — static counts
  of 0 / 0 / 0 for running / waiting / total.
- **Manifest:** Messaging category CarAppService with
  `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` (placeholder; ADR-0031
  calls for strict allowlist).
- **No transport wiring.** The Auto module depends on the `:shared`
  module for domain types (ServerProfile, Session) but doesn't use
  the SessionRepository or TransportClient at runtime.
- **Flavours:** `publicMain` (the Play-compliant Messaging service) +
  `devMain` (`DatawatchPassengerService` — possibly a Navigation or
  free-form Template for developer testing).

---

## Play-compliance constraints (ADR-0031)

Messaging category, not Nav/POI:
- Conversation list + message bubbles (ConversationCallback /
  CarMessagingControllerCompat pattern)
- TTS announcement of inbound messages
- Voice reply (`CarMessageReplyCallback`)
- Quick replies
- **No free-form screens** (no terminal, no config editor)
- **No scrolling lists beyond messaging constraints**

---

## Ready-for-test minimum (Sprint T scope)

1. **Connect Auto to a server profile** — read
   `profileRepository.observeAll()` and either
   (a) pick the first enabled profile, or
   (b) show a one-time "Open phone to configure" placeholder when
   none enabled.
2. **Live session-counts screen** — replace `PreMvpPlaceholderScreen`
   with a screen that polls `/api/sessions` every 15 s and shows
   real counts (running / waiting / total). ListTemplate still, just
   with real numbers.
3. **Session-per-row list** — when user taps the screen, enter a
   second Screen listing the N most-recently-updated waiting-input
   sessions. Each row shows the prompt-context one-liner.
4. **Tap row → Reply screen** — Messaging template with voice-reply
   + Yes / No / Continue quick-reply chips. Hooks
   `TransportClient.replyToSession` and
   `TransportClient.transcribeAudio` under the hood.
5. **Foreground notification → TTS + Auto push** — when a
   `waiting_input` notification fires and user is in car, surface it
   via `CarNotificationManager.notify` so the car speaks the prompt
   and offers voice reply.

---

## Deferred for post-1.0

- Drive-safe truncation of long prompts (ADR-0031 specifies 3 lines)
- Strict host allowlist (only Google Automotive + OEM hosts)
- Passenger / rear-seat mode (the `devMain` variant exists but not
  fleshed out)
- Multi-server picker on Auto (first-enabled profile only in v1)

---

## Build readiness (current turn)

No changes this turn. The placeholder compiles and installs into
Auto's Desktop Head Unit simulator, so "ready to test on Android
Auto" in the user's sense = Sprint T lands the minimum above.

---

## Sprint T plan (next after terminal parity)

1. Wire `SessionRepository.observeForProfile` into Auto (in-process
   works since Auto service hosted by same app package).
2. Replace placeholder counts with reactive counts.
3. Add `WaitingSessionsListScreen` + `ReplyScreen` with message template.
4. Wire RemoteInput reply + voice transcribe via `:shared` transport.
5. Tighten manifest host validator.

Estimated: 1–2 full-length commits.
