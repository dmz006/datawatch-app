# ADR-0025 — Voice Invocation

## Status
Accepted

## Context
Voice input needed to be reachable from multiple contexts, including from outside the app, to be genuinely useful.

## Decision
Four surfaces: global FAB, chat composer mic, Android quick-tile, and ASSIST intent for "Hey Google, talk to datawatch."

## Consequences
Voice is accessible from within the app, from the notification shade, and via Google Assistant, covering both in-app and ambient use cases.
