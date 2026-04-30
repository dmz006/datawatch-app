# ADR-0022 — Server Tabs

## Status
Accepted

## Context
The UI needed a policy for how many server connections could be open simultaneously and whether tabs could be pinned.

## Decision
Unlimited server tabs; no pinning in v1.

## Consequences
Users can connect to as many servers as needed without arbitrary limits, and tab management complexity is deferred to a later version.
