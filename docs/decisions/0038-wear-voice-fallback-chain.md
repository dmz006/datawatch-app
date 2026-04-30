# ADR-0038 — Wear Voice Fallback Chain

## Status
Accepted

## Context
A Wear OS watch may not have its own network path to the server, so voice commands needed a defined fallback chain.

## Decision
Priority order: (1) phone-proxy via Wearable Data Layer → phone → server Whisper; (2) direct watch-to-server if the watch has its own reachability; (3) native Wear RemoteInput STT → send transcript as text command. "Open on phone" prompt is never shown.

## Consequences
Voice always resolves on-watch without deflecting the user to the phone; degraded paths (direct, then STT-only) activate transparently.
