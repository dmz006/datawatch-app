# ADR-0013 — No Offline Queue

## Status
Accepted

## Context
A decision was needed on whether write operations should be queued and replayed when the server is unreachable, versus failing immediately.

## Decision
Writes fail fast; the user retries manually. Cached reads show a stale timestamp and are greyed out after 30 seconds of unreachability.

## Consequences
The offline experience is intentionally limited: users always know when data is stale, and there is no risk of replaying outdated commands.
