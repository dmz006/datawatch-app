# ADR-0026 — Transcript Handling

## Status
Accepted

## Context
After server-side Whisper transcription, the app needed a policy for when to send the transcript automatically versus showing it for review.

## Decision
Default: auto-send for recognised prefixes (`new:`, `reply:`, `status:`); preview-before-send for free-text. User-configurable.

## Consequences
Structured voice commands are low-friction and immediate; ambiguous free-text always gives the user a chance to confirm before sending.
