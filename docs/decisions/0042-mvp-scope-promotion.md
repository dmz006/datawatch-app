# ADR-0042 — MVP Scope Promotion

## Status
Accepted

## Context
Five items previously deferred to post-MVP were judged essential enough to require inclusion in the v0.10.0 scope-close release (2026-04-18 decision; originally written as "v1.0.0" — see ADR-0043 for the version-label correction).

## Decision
The following five items are promoted from post-MVP backlog to v0.10.0 scope. Partially supersedes ADR-0011 (biometric deferred) and ADR-0028 (Wear Tile deferred):

1. **Home-screen widget** — session count + voice quick-action. Lands Sprint 3.
2. **Wear Tile (w2)** — at-a-glance session state on the watchface tile surface alongside the already-planned complication + rich app. Lands Sprint 4.
3. **Android Auto Tile** — parked-state dashboard for the internal (dev) build; public build stays Messaging-only per ADR-0031 Play compliance. Lands Sprint 4.
4. **Biometric unlock** — optional fingerprint / face gate on the token vault + app resume. Disabled by default, user opts in during onboarding or Settings. Single-user model from ADR-0011 stays. Lands Sprint 5 (hardening).
5. **3-finger-swipe-up server picker** — Home Assistant-style gesture for fast server switching, in addition to the tap-to-open tree drawer. Lands Sprint 2 alongside the multi-server picker work.

Sprint timeline effect: Sprint 2–5 budgets tightened; MVP target 2026-06-12 and production target 2026-07-10 held. If any of the five threatens those dates, the weakest (candidate: 3-finger gesture) slips back to post-MVP rather than pushing the release. User notified at sprint retro.

## Consequences
The v0.10.0 release is meaningfully more capable than the original MVP scope, and any feature from these five that cannot land on schedule slips cleanly rather than delaying the release.
