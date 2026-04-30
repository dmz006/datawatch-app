# ADR-0027 — Offline Voice Refinement

## Status
Accepted

## Context
The voice pipeline requires server reachability; a UX policy was needed for what happens when a recording cannot be uploaded immediately.

## Decision
Recorded audio may persist in a retry UI — the user taps retry manually; no background replay.

## Consequences
Audio is never silently sent at an unintended time; the user controls every retry attempt, consistent with ADR-0013's fail-fast philosophy.
