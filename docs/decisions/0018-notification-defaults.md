# ADR-0018 — Notification Defaults

## Status
Accepted

## Context
Default notification behaviour needed to be defined across phone and Wear OS before the push integration was built.

## Decision
Server-level enable; per-session mute; always-notify on "input needed." Wear actions: Approve / Deny / Reply / Mute 10m.

## Consequences
Critical "input needed" events always surface to the user, while routine notifications can be silenced at the session level without disabling them globally.
