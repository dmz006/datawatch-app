# ADR-0020 — Server-First Navigation

## Status
Accepted

## Context
The app's information architecture needed to be defined to accommodate multiple servers while keeping primary actions discoverable.

## Decision
Bottom nav: Sessions / Channels / Stats / Settings. Server switcher as a top-bar pill with a tree drawer.

## Consequences
Primary navigation is stable across server switches; the server context is always visible in the top bar without consuming a bottom-nav slot.
