# ADR-0039 — Upstream Parent Coordination

## Status
Accepted

## Context
Three mobile-required server endpoints did not yet exist in the parent datawatch project, requiring a coordination strategy.

## Decision
The three needed endpoints are tracked as upstream issues: [#1](https://github.com/dmz006/datawatch/issues/1) device registration, [#2](https://github.com/dmz006/datawatch/issues/2) voice transcribe, [#3](https://github.com/dmz006/datawatch/issues/3) federation fan-out. Mobile ships MVP workarounds until upstream lands; see `api-parity.md`.

## Consequences
Mobile development is unblocked by workarounds, and upstream progress is tracked via linked issues rather than in-repo backlog.
